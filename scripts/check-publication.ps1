#requires -Version 7.0
[CmdletBinding(DefaultParameterSetName = 'Local')]
param(
  [Parameter(Mandatory = $true, ParameterSetName = 'Local')][switch]$Local,
  [Parameter(Mandatory = $true, ParameterSetName = 'Remote')][switch]$Remote,
  [Parameter(Mandatory = $true, ParameterSetName = 'SelfTest')][switch]$SelfTest,
  [Parameter(Mandatory = $true, ParameterSetName = 'Local')][string]$RepositoryRoot,
  [Parameter(Mandatory = $true, ParameterSetName = 'Local')][string]$BuildMainJar,
  [Parameter(Mandatory = $true, ParameterSetName = 'Local')][string]$BuildDevJar,
  [Parameter(Mandatory = $true, ParameterSetName = 'Local')][string]$BuildSourcesJar,
  [Parameter(Mandatory = $true, ParameterSetName = 'Local')]
  [ValidateSet('RequiredCorrect', 'Forbidden')][string]$ModuleMetadata,
  [Parameter(ParameterSetName = 'Local')][string]$ForbiddenGroupId,
  [Parameter(Mandatory = $true, ParameterSetName = 'Local')]
  [Parameter(Mandatory = $true, ParameterSetName = 'Remote')][string]$GroupId,
  [Parameter(Mandatory = $true, ParameterSetName = 'Local')]
  [Parameter(Mandatory = $true, ParameterSetName = 'Remote')][string]$ArtifactId,
  [Parameter(Mandatory = $true, ParameterSetName = 'Local')]
  [Parameter(Mandatory = $true, ParameterSetName = 'Remote')][string]$Version,
  [Parameter(Mandatory = $true, ParameterSetName = 'Remote')][string]$Commit,
  [Parameter(ParameterSetName = 'Remote')][ValidateRange(1, 15)][int]$RemoteTimeoutMinutes = 15,
  [Parameter(ParameterSetName = 'Remote')][string]$ExpectedMainSha256,
  [Parameter(ParameterSetName = 'Remote')][string]$ExpectedDevSha256,
  [Parameter(ParameterSetName = 'Remote')][string]$ExpectedSourcesSha256,
  [Parameter(ParameterSetName = 'Local')]
  [Parameter(ParameterSetName = 'Remote')][string]$GitHubOutput
)

$ErrorActionPreference = 'Stop'

function Assert-SafeCoordinate([string]$Value, [string]$Name, [bool]$AllowDots) {
  $pattern = if ($AllowDots) { '^[A-Za-z0-9_.-]+$' } else { '^[A-Za-z0-9_-][A-Za-z0-9_.-]*$' }
  if ([string]::IsNullOrWhiteSpace($Value) -or $Value -cnotmatch $pattern -or $Value.Contains('..')) {
    throw "$Name 不是安全的 Maven 坐标段"
  }
}

function Get-CoordinateDirectory([string]$Root, [string]$Group, [string]$Artifact, [string]$Release) {
  Assert-SafeCoordinate $Group 'GroupId' $true
  Assert-SafeCoordinate $Artifact 'ArtifactId' $false
  Assert-SafeCoordinate $Release 'Version' $false
  $path = [IO.Path]::GetFullPath($Root)
  foreach ($segment in @($Group.Split('.')) + @($Artifact, $Release)) { $path = Join-Path $path $segment }
  $path
}

function Get-HashLower([string]$Path, [string]$Algorithm = 'SHA256') {
  (Get-FileHash -LiteralPath $Path -Algorithm $Algorithm).Hash.ToLowerInvariant()
}

function Assert-File([string]$Path, [string]$Label) {
  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "缺少$Label：$Path" }
  if ((Get-Item -LiteralPath $Path).Length -le 0) { throw "$Label 为空：$Path" }
}

function Assert-Jar([string]$Path, [string]$Label) {
  Assert-File $Path $Label
  $archive = [IO.Compression.ZipFile]::OpenRead($Path)
  try { if ($archive.Entries.Count -eq 0) { throw "$Label 不是有效的非空 JAR" } } finally { $archive.Dispose() }
}

function Read-Pom([byte[]]$Bytes, [string]$ExpectedGroup, [string]$ExpectedArtifact, [string]$ExpectedVersion) {
  $settings = [Xml.XmlReaderSettings]::new()
  $settings.DtdProcessing = [Xml.DtdProcessing]::Prohibit
  $stream = [IO.MemoryStream]::new($Bytes, $false)
  $reader = [Xml.XmlReader]::Create($stream, $settings)
  try {
    $document = [Xml.XmlDocument]::new()
    $document.Load($reader)
  } finally { $reader.Dispose(); $stream.Dispose() }
  $namespace = [Xml.XmlNamespaceManager]::new($document.NameTable)
  $namespace.AddNamespace('m', 'http://maven.apache.org/POM/4.0.0')
  $actual = @(
    $document.SelectSingleNode('/m:project/m:groupId', $namespace).InnerText,
    $document.SelectSingleNode('/m:project/m:artifactId', $namespace).InnerText,
    $document.SelectSingleNode('/m:project/m:version', $namespace).InnerText)
  $expected = @($ExpectedGroup, $ExpectedArtifact, $ExpectedVersion)
  for ($i = 0; $i -lt 3; $i++) {
    if ($actual[$i] -cne $expected[$i]) { throw "POM GAV 不匹配：$($actual -join ':')" }
  }
}

function Assert-PomFile([string]$Path, [string]$ExpectedGroup, [string]$ExpectedArtifact, [string]$ExpectedVersion) {
  Assert-File $Path 'POM'
  Read-Pom ([IO.File]::ReadAllBytes($Path)) $ExpectedGroup $ExpectedArtifact $ExpectedVersion
}

function Get-ArtifactPaths([string]$Directory, [string]$Artifact, [string]$Release) {
  $base = "$Artifact-$Release"
  @{
    Pom = Join-Path $Directory "$base.pom"
    Main = Join-Path $Directory "$base.jar"
    Dev = Join-Path $Directory "$base-dev.jar"
    Sources = Join-Path $Directory "$base-sources.jar"
    Module = Join-Path $Directory "$base.module"
  }
}

function Assert-Module([string]$Path, [string]$Group, [string]$Artifact, [string]$Release,
    [hashtable]$Paths, [hashtable]$Hashes) {
  Assert-File $Path 'Gradle module metadata'
  $metadata = [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8) | ConvertFrom-Json -Depth 100
  if ($metadata.formatVersion -cne '1.1' -or $metadata.component.group -cne $Group -or
      $metadata.component.module -cne $Artifact -or $metadata.component.version -cne $Release) {
    throw 'Gradle module metadata 的 formatVersion/component 不正确'
  }
  $expectedUrls = @{
    apiElements = [IO.Path]::GetFileName($Paths.Dev)
    runtimeElements = [IO.Path]::GetFileName($Paths.Dev)
    reobfElements = [IO.Path]::GetFileName($Paths.Main)
    sourcesElements = [IO.Path]::GetFileName($Paths.Sources)
  }
  $urlHashes = @{}
  foreach ($variantName in $expectedUrls.Keys) {
    $variant = @($metadata.variants | Where-Object { $_.name -ceq $variantName })
    if ($variant.Count -ne 1 -or @($variant[0].files).Count -ne 1) { throw "variant $variantName 缺失或文件数不为 1" }
    $file = @($variant[0].files)[0]
    $url = [string]$file.url
    if ($url -cne $expectedUrls[$variantName] -or [IO.Path]::GetFileName($url) -cne $url) {
      throw "variant $variantName 未指向预期 classifier：$url"
    }
    $actualPath = Join-Path (Split-Path -Parent $Path) $url
    Assert-File $actualPath "variant $variantName 制品"
    $actualHash = Get-HashLower $actualPath
    $declaredHash = ([string]$file.sha256).ToLowerInvariant()
    if ($urlHashes.ContainsKey($url) -and $urlHashes[$url] -cne $declaredHash) {
      throw "同一 variant URL 声明了多个 hash：$url"
    }
    if ($declaredHash -cnotmatch '^[a-f0-9]{64}$' -or $declaredHash -cne $actualHash) {
      throw "variant $variantName 的 URL/hash 不匹配"
    }
    $urlHashes[$url] = $declaredHash
  }
  if ($Hashes.Main -cne $urlHashes[[IO.Path]::GetFileName($Paths.Main)] -or
      $Hashes.Dev -cne $urlHashes[[IO.Path]::GetFileName($Paths.Dev)] -or
      $Hashes.Sources -cne $urlHashes[[IO.Path]::GetFileName($Paths.Sources)]) {
    throw 'Gradle module metadata 未完整表达 main/dev/sources hash'
  }
}

function Write-Outputs([hashtable]$Hashes, [string]$Path) {
  if ([string]::IsNullOrWhiteSpace($Path)) { return }
  $parent = Split-Path -Parent ([IO.Path]::GetFullPath($Path))
  if (-not (Test-Path -LiteralPath $parent -PathType Container)) { throw 'GITHUB_OUTPUT 父目录不存在' }
  $text = "main_sha256=$($Hashes.Main)`ndev_sha256=$($Hashes.Dev)`nsources_sha256=$($Hashes.Sources)`n"
  [IO.File]::AppendAllText($Path, $text, [Text.UTF8Encoding]::new($false))
}

function Test-ForbiddenCoordinates([string]$Root, [string]$ForbiddenGroup, [string]$Artifact, [string]$Release) {
  if ([string]::IsNullOrWhiteSpace($ForbiddenGroup)) { return }
  $directory = Get-CoordinateDirectory $Root $ForbiddenGroup $Artifact $Release
  $paths = Get-ArtifactPaths $directory $Artifact $Release
  foreach ($path in $paths.Values) {
    if (Test-Path -LiteralPath $path) { throw "禁用坐标仍有残留：$path" }
  }
}

function Invoke-LocalCheck {
  $directory = Get-CoordinateDirectory $RepositoryRoot $GroupId $ArtifactId $Version
  $paths = Get-ArtifactPaths $directory $ArtifactId $Version
  Assert-PomFile $paths.Pom $GroupId $ArtifactId $Version
  Assert-Jar $paths.Main 'main JAR'
  Assert-Jar $paths.Dev 'dev JAR'
  Assert-Jar $paths.Sources 'sources JAR'
  $buildPaths = @{ Main = $BuildMainJar; Dev = $BuildDevJar; Sources = $BuildSourcesJar }
  $hashes = @{}
  foreach ($role in @('Main', 'Dev', 'Sources')) {
    Assert-Jar $buildPaths[$role] "build/libs $role JAR"
    $hashes[$role] = Get-HashLower $paths[$role]
    if ($hashes[$role] -cne (Get-HashLower $buildPaths[$role])) { throw "$role Maven/build/libs hash 不一致" }
  }
  if (@($hashes.Values | Sort-Object -Unique).Count -ne 3) { throw 'main/dev/sources hash 必须互异' }
  if ($ModuleMetadata -ceq 'RequiredCorrect') { Assert-Module $paths.Module $GroupId $ArtifactId $Version $paths $hashes }
  elseif (Test-Path -LiteralPath $paths.Module) { throw 'JitPack publication 禁止残留 Gradle module metadata' }
  Test-ForbiddenCoordinates $RepositoryRoot $ForbiddenGroupId $ArtifactId $Version
  Write-Outputs $hashes $GitHubOutput
  [pscustomobject]@{ status = 'LOCAL_PUBLICATION_OK'; gav = "$GroupId`:$ArtifactId`:$Version"; hashes = $hashes }
}

function New-RemoteClient {
  $handler = [Net.Http.HttpClientHandler]::new()
  $handler.AllowAutoRedirect = $false
  $client = [Net.Http.HttpClient]::new($handler)
  $client.Timeout = [Threading.Timeout]::InfiniteTimeSpan
  @{ Client = $client; Handler = $handler }
}

function Get-RemoteResponse([Net.Http.HttpClient]$Client, [string]$Uri, [Threading.CancellationToken]$OverallToken) {
  $parsed = [Uri]$Uri
  if ($parsed.Scheme -cne 'https' -or $parsed.Host -cne 'jitpack.io' -or $parsed.Query.Length -ne 0) {
    throw "Remote 只允许无 query 的 https://jitpack.io 普通 GET：$Uri"
  }
  $requestTimeout = [Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds(60))
  $linked = [Threading.CancellationTokenSource]::CreateLinkedTokenSource($OverallToken, $requestTimeout.Token)
  try {
    $response = $Client.GetAsync($parsed, [Net.Http.HttpCompletionOption]::ResponseContentRead, $linked.Token).GetAwaiter().GetResult()
    try {
      [pscustomobject]@{ Status = [int]$response.StatusCode; Bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult() }
    } finally { $response.Dispose() }
  } catch [OperationCanceledException] { throw "Remote GET 超时：$Uri" }
  catch [Net.Http.HttpRequestException] { throw "Remote GET 网络失败：$Uri；$($_.Exception.Message)" }
  finally { $linked.Dispose(); $requestTimeout.Dispose() }
}

function Assert-RemoteSha1([byte[]]$Bytes, [byte[]]$ShaBytes, [string]$Label) {
  $actual = [Convert]::ToHexString([Security.Cryptography.SHA1]::HashData($Bytes)).ToLowerInvariant()
  $declared = ([Text.Encoding]::UTF8.GetString($ShaBytes)).Trim().ToLowerInvariant()
  if ($declared -cnotmatch '^[a-f0-9]{40}$' -or $actual -cne $declared) { throw "$Label 的远端 sha1 不匹配" }
}

function New-PendingResult([string]$Reason) {
  [pscustomobject]@{ Complete = $false; PendingReason = $Reason; Hashes = $null }
}

function Assert-NoRemotePermissionFailure($Response, [string]$Label) {
  if ($Response.Status -in @(401, 403)) { throw "JitPack 权限错误：$Label HTTP $($Response.Status)" }
}

function Test-IsPendingHttp([int]$Status) {
  $Status -in @(202, 404, 408, 425, 429) -or $Status -ge 500
}

function Test-RemoteSnapshot([hashtable]$Snapshot, [string]$ExpectedGroup, [string]$ExpectedArtifact,
    [string]$ExpectedVersion, [string]$ExpectedCommit, [hashtable]$ExpectedHashes) {
  foreach ($key in @('Api', 'Log', 'Pom', 'PomSha', 'Main', 'MainSha', 'Dev', 'DevSha',
      'Sources', 'SourcesSha', 'Module')) {
    if (-not $Snapshot.ContainsKey($key)) { throw "Remote snapshot 缺少 $key" }
    Assert-NoRemotePermissionFailure $Snapshot[$key] $key
  }

  $apiResponse = $Snapshot.Api
  if (Test-IsPendingHttp $apiResponse.Status) {
    return New-PendingResult "Build API HTTP $($apiResponse.Status)"
  }
  if ($apiResponse.Status -ne 200) { throw "JitPack Build API 返回确定性意外状态：$($apiResponse.Status)" }
  try { $build = [Text.Encoding]::UTF8.GetString($apiResponse.Bytes) | ConvertFrom-Json }
  catch { return New-PendingResult 'Build API 尚未返回完整 JSON' }
  if ($build.status -in @('error', 'failed')) { throw "JitPack build 明确失败：$($build.message)" }
  if ($build.status -cne 'ok') { return New-PendingResult "Build API status=$($build.status)" }
  if ($build.isTag -ne $true -or $build.private -ne $false -or
      ([string]$build.commit).ToLowerInvariant() -cne $ExpectedCommit.ToLowerInvariant()) {
    throw 'JitPack Build API 的 tag/public/commit 确定性不匹配'
  }

  $logResponse = $Snapshot.Log
  if (Test-IsPendingHttp $logResponse.Status) {
    return New-PendingResult "build.log HTTP $($logResponse.Status)"
  }
  if ($logResponse.Status -ne 200) { throw "JitPack build.log 返回确定性意外状态：$($logResponse.Status)" }
  $logText = [Text.Encoding]::UTF8.GetString($logResponse.Bytes)
  if ($logText -match '(?i)permission denied|authentication failed|not authorized') {
    throw 'JitPack 权限错误：build.log 包含明确权限失败'
  }
  $exitMatches = @([regex]::Matches($logText, '(?mi)^Exit code:\s*(-?\d+)\s*$'))
  $nonZero = @($exitMatches | Where-Object { $_.Groups[1].Value -ne '0' })
  if ($nonZero.Count -gt 0) { throw "JitPack build 明确非零退出：Exit code $($nonZero[0].Groups[1].Value)" }
  if (@($exitMatches | Where-Object { $_.Groups[1].Value -eq '0' }).Count -eq 0) {
    return New-PendingResult 'build.log 尚无成功终结尾行'
  }

  foreach ($role in @('Pom', 'PomSha', 'Main', 'MainSha', 'Dev', 'DevSha', 'Sources', 'SourcesSha')) {
    $response = $Snapshot[$role]
    if (Test-IsPendingHttp $response.Status) {
      return New-PendingResult "$role HTTP $($response.Status)"
    }
    if ($response.Status -ne 200) { throw "远端 $role 返回确定性意外状态：$($response.Status)" }
  }
  if ($Snapshot.Module.Status -eq 200) { throw '远端 Gradle module metadata 确定性污染' }
  if ((Test-IsPendingHttp $Snapshot.Module.Status) -and $Snapshot.Module.Status -ne 404) {
    return New-PendingResult "Module HTTP $($Snapshot.Module.Status)"
  }
  if ($Snapshot.Module.Status -ne 404) {
    throw "远端 module metadata 返回确定性意外状态：$($Snapshot.Module.Status)"
  }

  Assert-RemoteSha1 $Snapshot.Pom.Bytes $Snapshot.PomSha.Bytes 'Pom'
  Assert-RemoteSha1 $Snapshot.Main.Bytes $Snapshot.MainSha.Bytes 'Main'
  Assert-RemoteSha1 $Snapshot.Dev.Bytes $Snapshot.DevSha.Bytes 'Dev'
  Assert-RemoteSha1 $Snapshot.Sources.Bytes $Snapshot.SourcesSha.Bytes 'Sources'
  Read-Pom $Snapshot.Pom.Bytes $ExpectedGroup $ExpectedArtifact $ExpectedVersion
  $hashes = @{
    Main = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($Snapshot.Main.Bytes)).ToLowerInvariant()
    Dev = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($Snapshot.Dev.Bytes)).ToLowerInvariant()
    Sources = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($Snapshot.Sources.Bytes)).ToLowerInvariant()
  }
  foreach ($role in @('Main', 'Dev', 'Sources')) {
    if (-not [string]::IsNullOrWhiteSpace($ExpectedHashes[$role]) -and
        $hashes[$role] -cne ([string]$ExpectedHashes[$role]).ToLowerInvariant()) {
      throw "远端 $role 与 publication gate hash 确定性不匹配"
    }
  }
  if (@($hashes.Values | Sort-Object -Unique).Count -ne 3) { throw '远端 main/dev/sources hash 必须互异' }
  [pscustomobject]@{ Complete = $true; PendingReason = $null; Hashes = $hashes }
}

function Invoke-RemoteCheck {
  Assert-SafeCoordinate $GroupId 'GroupId' $true
  Assert-SafeCoordinate $ArtifactId 'ArtifactId' $false
  Assert-SafeCoordinate $Version 'Version' $false
  if ($Commit -cnotmatch '^[a-fA-F0-9]{40}$') { throw 'Commit 必须是 peeled 40 位 SHA' }
  $groupPath = $GroupId.Replace('.', '/')
  $artifactBase = "https://jitpack.io/$groupPath/$ArtifactId/$Version"
  $api = "https://jitpack.io/api/builds/$GroupId/$ArtifactId/$Version"
  $baseName = "$ArtifactId-$Version"
  $deadline = [DateTimeOffset]::UtcNow.AddMinutes($RemoteTimeoutMinutes)
  $overall = [Threading.CancellationTokenSource]::new([TimeSpan]::FromMinutes($RemoteTimeoutMinutes))
  $holder = New-RemoteClient
  try {
    $lastPending = '尚未开始收敛检查'
    $complete = $null
    while (-not $overall.IsCancellationRequested) {
      try {
        $snapshot = @{
          Api = Get-RemoteResponse $holder.Client $api $overall.Token
          Pom = Get-RemoteResponse $holder.Client "$artifactBase/$baseName.pom" $overall.Token
          PomSha = Get-RemoteResponse $holder.Client "$artifactBase/$baseName.pom.sha1" $overall.Token
          Main = Get-RemoteResponse $holder.Client "$artifactBase/$baseName.jar" $overall.Token
          MainSha = Get-RemoteResponse $holder.Client "$artifactBase/$baseName.jar.sha1" $overall.Token
          Dev = Get-RemoteResponse $holder.Client "$artifactBase/$baseName-dev.jar" $overall.Token
          DevSha = Get-RemoteResponse $holder.Client "$artifactBase/$baseName-dev.jar.sha1" $overall.Token
          Sources = Get-RemoteResponse $holder.Client "$artifactBase/$baseName-sources.jar" $overall.Token
          SourcesSha = Get-RemoteResponse $holder.Client "$artifactBase/$baseName-sources.jar.sha1" $overall.Token
          Module = Get-RemoteResponse $holder.Client "$artifactBase/$baseName.module" $overall.Token
          Log = Get-RemoteResponse $holder.Client "$artifactBase/build.log" $overall.Token
        }
        $expected = @{ Main = $ExpectedMainSha256; Dev = $ExpectedDevSha256; Sources = $ExpectedSourcesSha256 }
        $decision = Test-RemoteSnapshot $snapshot $GroupId $ArtifactId $Version $Commit $expected
        if ($decision.Complete) { $complete = $decision; break }
        $lastPending = $decision.PendingReason
      } catch [OperationCanceledException] {
        $lastPending = '整体等待已取消'
      } catch {
        if ($_.Exception.Message -match '^Remote GET (超时|网络失败)') { $lastPending = $_.Exception.Message }
        else { throw }
      }
      $remainingSeconds = ($deadline - [DateTimeOffset]::UtcNow).TotalSeconds
      if ($remainingSeconds -le 0) { break }
      Start-Sleep -Seconds ([Math]::Min(15.0, $remainingSeconds))
    }
    if (-not $complete) { throw "JitPack Remote 未在最长等待时间内整体收敛；最后 pending 原因：$lastPending" }
    Write-Outputs $complete.Hashes $GitHubOutput
    [pscustomobject]@{ status = 'REMOTE_PUBLICATION_OK'; gav = "$GroupId`:$ArtifactId`:$Version"; commit = $Commit; hashes = $complete.Hashes }
  } finally {
    $holder.Client.Dispose(); $holder.Handler.Dispose(); $overall.Dispose()
  }
}

function New-TestJar([string]$Path, [string]$Marker) {
  $archive = [IO.Compression.ZipFile]::Open($Path, [IO.Compression.ZipArchiveMode]::Create)
  try {
    $entry = $archive.CreateEntry('marker.txt')
    $writer = [IO.StreamWriter]::new($entry.Open(), [Text.UTF8Encoding]::new($false))
    try { $writer.Write($Marker) } finally { $writer.Dispose() }
  } finally { $archive.Dispose() }
}

function New-LocalFixture([string]$Root, [string]$CaseName) {
  $repository = Join-Path $Root "$CaseName/repository"
  $libs = Join-Path $Root "$CaseName/libs"
  [IO.Directory]::CreateDirectory($repository) | Out-Null
  [IO.Directory]::CreateDirectory($libs) | Out-Null
  $group = 'example.fixture'; $artifact = 'fixture'; $release = '1.0.0'
  $directory = Get-CoordinateDirectory $repository $group $artifact $release
  [IO.Directory]::CreateDirectory($directory) | Out-Null
  $paths = Get-ArtifactPaths $directory $artifact $release
  [IO.File]::WriteAllText($paths.Pom, "<project xmlns=`"http://maven.apache.org/POM/4.0.0`"><modelVersion>4.0.0</modelVersion><groupId>$group</groupId><artifactId>$artifact</artifactId><version>$release</version></project>")
  foreach ($role in @('Main', 'Dev', 'Sources')) {
    New-TestJar $paths[$role] $role
    [IO.File]::Copy($paths[$role], (Join-Path $libs ([IO.Path]::GetFileName($paths[$role]))))
  }
  $hashes = @{ Main = Get-HashLower $paths.Main; Dev = Get-HashLower $paths.Dev; Sources = Get-HashLower $paths.Sources }
  $variants = foreach ($item in @(@('apiElements', 'Dev'), @('runtimeElements', 'Dev'), @('reobfElements', 'Main'), @('sourcesElements', 'Sources'))) {
    @{ name = $item[0]; files = @(@{ name = [IO.Path]::GetFileName($paths[$item[1]]); url = [IO.Path]::GetFileName($paths[$item[1]]); sha256 = $hashes[$item[1]] }) }
  }
  @{ formatVersion = '1.1'; component = @{ group = $group; module = $artifact; version = $release }; variants = $variants } |
    ConvertTo-Json -Depth 10 | ForEach-Object { [IO.File]::WriteAllText($paths.Module, $_, [Text.UTF8Encoding]::new($false)) }
  @{ Repository = $repository; Libs = $libs; Group = $group; Artifact = $artifact; Version = $release; Paths = $paths; Hashes = $hashes }
}

function Assert-Throws([scriptblock]$Body, [string]$Label) {
  $thrown = $false
  try { & $Body | Out-Null } catch { $thrown = $true }
  if (-not $thrown) { throw "SelfTest 未拒绝：$Label" }
}

function Invoke-FixtureLocal($Fixture, [string]$Policy, [string]$ForbiddenGroup = $null) {
  $script:RepositoryRoot = $Fixture.Repository
  $script:BuildMainJar = Join-Path $Fixture.Libs ([IO.Path]::GetFileName($Fixture.Paths.Main))
  $script:BuildDevJar = Join-Path $Fixture.Libs ([IO.Path]::GetFileName($Fixture.Paths.Dev))
  $script:BuildSourcesJar = Join-Path $Fixture.Libs ([IO.Path]::GetFileName($Fixture.Paths.Sources))
  $script:GroupId = $Fixture.Group; $script:ArtifactId = $Fixture.Artifact; $script:Version = $Fixture.Version
  $script:ModuleMetadata = $Policy; $script:ForbiddenGroupId = $ForbiddenGroup; $script:GitHubOutput = $null
  Invoke-LocalCheck
}

function New-TestRemoteResponse([int]$Status, [byte[]]$Bytes = @()) {
  [pscustomobject]@{ Status = $Status; Bytes = $Bytes }
}

function Get-TestSha1Bytes([byte[]]$Bytes) {
  [Text.Encoding]::UTF8.GetBytes(
    [Convert]::ToHexString([Security.Cryptography.SHA1]::HashData($Bytes)).ToLowerInvariant())
}

function New-TestRemoteSnapshot {
  $group = 'example.fixture'; $artifact = 'fixture'; $release = '1.0.0'; $commit = 'a' * 40
  $pom = [Text.Encoding]::UTF8.GetBytes("<project xmlns=`"http://maven.apache.org/POM/4.0.0`"><modelVersion>4.0.0</modelVersion><groupId>$group</groupId><artifactId>$artifact</artifactId><version>$release</version></project>")
  $main = [Text.Encoding]::UTF8.GetBytes('remote-main')
  $dev = [Text.Encoding]::UTF8.GetBytes('remote-dev')
  $sources = [Text.Encoding]::UTF8.GetBytes('remote-sources')
  $api = @{ status = 'ok'; isTag = $true; private = $false; commit = $commit } | ConvertTo-Json -Compress
  $snapshot = @{
    Api = New-TestRemoteResponse 200 ([Text.Encoding]::UTF8.GetBytes($api))
    Log = New-TestRemoteResponse 200 ([Text.Encoding]::UTF8.GetBytes("building`nExit code: 0`n"))
    Pom = New-TestRemoteResponse 200 $pom; PomSha = New-TestRemoteResponse 200 (Get-TestSha1Bytes $pom)
    Main = New-TestRemoteResponse 200 $main; MainSha = New-TestRemoteResponse 200 (Get-TestSha1Bytes $main)
    Dev = New-TestRemoteResponse 200 $dev; DevSha = New-TestRemoteResponse 200 (Get-TestSha1Bytes $dev)
    Sources = New-TestRemoteResponse 200 $sources; SourcesSha = New-TestRemoteResponse 200 (Get-TestSha1Bytes $sources)
    Module = New-TestRemoteResponse 404
  }
  $hashes = @{
    Main = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($main)).ToLowerInvariant()
    Dev = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($dev)).ToLowerInvariant()
    Sources = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($sources)).ToLowerInvariant()
  }
  [pscustomobject]@{ Snapshot = $snapshot; Group = $group; Artifact = $artifact; Version = $release; Commit = $commit; Hashes = $hashes }
}

function Invoke-SelfTest {
  $parent = [IO.Path]::GetTempPath()
  if (-not (Test-Path -LiteralPath $parent -PathType Container)) { throw '系统临时目录不可用' }
  $root = Join-Path $parent "qz-publication-$([Guid]::NewGuid().ToString('N'))"
  [IO.Directory]::CreateDirectory($root) | Out-Null
  try {
    $correct = New-LocalFixture $root 'correct'
    Invoke-FixtureLocal $correct 'RequiredCorrect' | Out-Null

    $wrongRole = New-LocalFixture $root 'wrong-role'
    $metadata = [IO.File]::ReadAllText($wrongRole.Paths.Module) | ConvertFrom-Json -Depth 20
    foreach ($variant in @($metadata.variants | Where-Object { $_.name -in @('apiElements', 'runtimeElements') })) {
      $variant.files[0].url = [IO.Path]::GetFileName($wrongRole.Paths.Main); $variant.files[0].sha256 = $wrongRole.Hashes.Main
    }
    $metadata | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $wrongRole.Paths.Module -Encoding utf8NoBOM
    Assert-Throws { Invoke-FixtureLocal $wrongRole 'RequiredCorrect' } 'api/runtime 未指 dev'

    $collision = New-LocalFixture $root 'collision'
    $metadata = [IO.File]::ReadAllText($collision.Paths.Module) | ConvertFrom-Json -Depth 20
    ($metadata.variants | Where-Object name -eq 'runtimeElements').files[0].sha256 = $collision.Hashes.Main
    $metadata | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $collision.Paths.Module -Encoding utf8NoBOM
    Assert-Throws { Invoke-FixtureLocal $collision 'RequiredCorrect' } '同 URL 多 hash'

    $missingDev = New-LocalFixture $root 'missing-dev'
    [IO.File]::Delete($missingDev.Paths.Dev)
    Assert-Throws { Invoke-FixtureLocal $missingDev 'RequiredCorrect' } '缺 dev'

    $wrongGav = New-LocalFixture $root 'wrong-gav'
    [IO.File]::WriteAllText($wrongGav.Paths.Pom, '<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>wrong</groupId><artifactId>fixture</artifactId><version>1.0.0</version></project>')
    Assert-Throws { Invoke-FixtureLocal $wrongGav 'RequiredCorrect' } '错 GAV'

    $forbidden = New-LocalFixture $root 'forbidden'
    Assert-Throws { Invoke-FixtureLocal $forbidden 'Forbidden' } 'Forbidden 残留 module'

    $forbiddenGroup = 'legacy.fixture'
    $canonical = New-LocalFixture $root 'forbidden-group'
    [IO.File]::Delete($canonical.Paths.Module)
    $forbiddenDirectory = Get-CoordinateDirectory $canonical.Repository $forbiddenGroup $canonical.Artifact $canonical.Version
    [IO.Directory]::CreateDirectory($forbiddenDirectory) | Out-Null
    $forbiddenPaths = Get-ArtifactPaths $forbiddenDirectory $canonical.Artifact $canonical.Version
    [IO.File]::WriteAllText($forbiddenPaths.Pom, 'legacy coordinate residue')
    Assert-Throws { Invoke-FixtureLocal $canonical 'Forbidden' $forbiddenGroup } '同版本 ForbiddenGroup 残留'
    [IO.Directory]::Delete($forbiddenDirectory, $true)
    Invoke-FixtureLocal $canonical 'Forbidden' $forbiddenGroup | Out-Null

    $body = [Text.Encoding]::UTF8.GetBytes('remote-body')
    $badSha = [Text.Encoding]::UTF8.GetBytes('0000000000000000000000000000000000000000')
    Assert-Throws { Assert-RemoteSha1 $body $badSha 'fixture' } '远端 hash 不匹配'

    $complete = New-TestRemoteSnapshot
    $decision = Test-RemoteSnapshot $complete.Snapshot $complete.Group $complete.Artifact $complete.Version $complete.Commit $complete.Hashes
    if (-not $decision.Complete) { throw 'SelfTest：完整 Remote snapshot 未收敛' }
    foreach ($case in @('api-404', 'api-building', 'log-incomplete', 'artifact-404', 'transient-5xx')) {
      $fixture = New-TestRemoteSnapshot
      switch ($case) {
        'api-404' { $fixture.Snapshot.Api.Status = 404 }
        'api-building' {
          $fixture.Snapshot.Api.Bytes = [Text.Encoding]::UTF8.GetBytes(
            (@{ status = 'building'; isTag = $true; private = $false; commit = $fixture.Commit } | ConvertTo-Json -Compress))
        }
        'log-incomplete' { $fixture.Snapshot.Log.Bytes = [Text.Encoding]::UTF8.GetBytes('still building') }
        'artifact-404' { $fixture.Snapshot.Dev.Status = 404 }
        'transient-5xx' { $fixture.Snapshot.Module.Status = 503 }
      }
      $pending = Test-RemoteSnapshot $fixture.Snapshot $fixture.Group $fixture.Artifact $fixture.Version $fixture.Commit $fixture.Hashes
      if ($pending.Complete -or [string]::IsNullOrWhiteSpace($pending.PendingReason)) {
        throw "SelfTest：$case 未分类为 pending"
      }
    }
    foreach ($case in @('permission-http', 'permission-log', 'nonzero-exit', 'wrong-identity', 'module-pollution', 'gate-hash', 'wrong-remote-gav')) {
      $fixture = New-TestRemoteSnapshot
      switch ($case) {
        'permission-http' { $fixture.Snapshot.Main.Status = 403 }
        'permission-log' { $fixture.Snapshot.Log.Bytes = [Text.Encoding]::UTF8.GetBytes("Permission denied`nExit code: 0") }
        'nonzero-exit' { $fixture.Snapshot.Log.Bytes = [Text.Encoding]::UTF8.GetBytes('Exit code: 1') }
        'wrong-identity' {
          $fixture.Snapshot.Api.Bytes = [Text.Encoding]::UTF8.GetBytes(
            (@{ status = 'ok'; isTag = $true; private = $false; commit = ('b' * 40) } | ConvertTo-Json -Compress))
        }
        'module-pollution' { $fixture.Snapshot.Module.Status = 200 }
        'gate-hash' { $fixture.Hashes.Main = '0' * 64 }
        'wrong-remote-gav' {
          $wrongPom = [Text.Encoding]::UTF8.GetBytes('<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>wrong</groupId><artifactId>fixture</artifactId><version>1.0.0</version></project>')
          $fixture.Snapshot.Pom.Bytes = $wrongPom
          $fixture.Snapshot.PomSha.Bytes = Get-TestSha1Bytes $wrongPom
        }
      }
      Assert-Throws {
        Test-RemoteSnapshot $fixture.Snapshot $fixture.Group $fixture.Artifact $fixture.Version $fixture.Commit $fixture.Hashes
      } "Remote 确定性错误 $case"
    }
    [pscustomobject]@{ status = 'SELF_TEST_OK'; covered = @('correct-gmm', 'api-runtime-dev', 'duplicate-url-hash', 'missing-dev', 'wrong-gav', 'forbidden-module', 'forbidden-group-residue', 'remote-hash-mismatch', 'remote-overall-convergence', 'remote-pending-state', 'remote-permission', 'remote-nonzero-exit', 'remote-deterministic-mismatch') }
  } finally { if (Test-Path -LiteralPath $root) { Remove-Item -LiteralPath $root -Recurse -Force } }
}

if ($SelfTest) { Invoke-SelfTest }
elseif ($Local) { Invoke-LocalCheck }
else { Invoke-RemoteCheck }
