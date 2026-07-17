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

function Assert-RemoteStatus($Response, [int]$Expected, [string]$Label) {
  if ($Response.Status -ge 500) { throw "$Label 返回 5xx：$($Response.Status)" }
  if ($Response.Status -ne $Expected) { throw "$Label HTTP 状态应为 $Expected，实际 $($Response.Status)" }
}

function Assert-RemoteSha1([byte[]]$Bytes, [byte[]]$ShaBytes, [string]$Label) {
  $actual = [Convert]::ToHexString([Security.Cryptography.SHA1]::HashData($Bytes)).ToLowerInvariant()
  $declared = ([Text.Encoding]::UTF8.GetString($ShaBytes)).Trim().ToLowerInvariant()
  if ($declared -cnotmatch '^[a-f0-9]{40}$' -or $actual -cne $declared) { throw "$Label 的远端 sha1 不匹配" }
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
  $overall = [Threading.CancellationTokenSource]::new([TimeSpan]::FromMinutes($RemoteTimeoutMinutes))
  $holder = New-RemoteClient
  try {
    $build = $null
    while (-not $overall.IsCancellationRequested) {
      $trigger = Get-RemoteResponse $holder.Client "$artifactBase/$baseName.pom" $overall.Token
      if ($trigger.Status -ge 500) { throw "JitPack POM 触发请求返回 5xx：$($trigger.Status)" }
      if ($trigger.Status -notin @(200, 404)) { throw "JitPack POM 触发请求返回意外状态：$($trigger.Status)" }
      $response = Get-RemoteResponse $holder.Client $api $overall.Token
      if ($response.Status -ge 500) { throw "JitPack Build API 返回 5xx：$($response.Status)" }
      if ($response.Status -eq 200) {
        $candidate = [Text.Encoding]::UTF8.GetString($response.Bytes) | ConvertFrom-Json
        if ($candidate.status -ceq 'ok') { $build = $candidate; break }
        if ($candidate.status -in @('error', 'failed')) { throw "JitPack build 失败：$($candidate.message)" }
      } elseif ($response.Status -ne 404) { throw "JitPack Build API 返回意外状态：$($response.Status)" }
      Start-Sleep -Seconds 15
    }
    if (-not $build) { throw 'JitPack Remote 检查超过最长等待时间' }
    if ($build.isTag -ne $true -or $build.private -ne $false -or ([string]$build.commit).ToLowerInvariant() -cne $Commit.ToLowerInvariant()) {
      throw 'JitPack Build API 的 isTag/public/commit 不匹配'
    }
    $remote = @{}
    foreach ($role in @('Pom', 'Main', 'Dev', 'Sources')) {
      $suffix = switch ($role) { Pom { '.pom' } Main { '.jar' } Dev { '-dev.jar' } Sources { '-sources.jar' } }
      $uri = "$artifactBase/$baseName$suffix"
      $body = Get-RemoteResponse $holder.Client $uri $overall.Token
      Assert-RemoteStatus $body 200 "远端 $role"
      $sha = Get-RemoteResponse $holder.Client "$uri.sha1" $overall.Token
      Assert-RemoteStatus $sha 200 "远端 $role sha1"
      Assert-RemoteSha1 $body.Bytes $sha.Bytes $role
      $remote[$role] = $body.Bytes
    }
    Read-Pom $remote.Pom $GroupId $ArtifactId $Version
    $module = Get-RemoteResponse $holder.Client "$artifactBase/$baseName.module" $overall.Token
    Assert-RemoteStatus $module 404 '远端 module metadata'
    $log = Get-RemoteResponse $holder.Client "$artifactBase/build.log" $overall.Token
    Assert-RemoteStatus $log 200 'JitPack build.log'
    $logText = [Text.Encoding]::UTF8.GetString($log.Bytes)
    if ($logText -match 'Permission denied' -or $logText -notmatch '(?m)^Exit code:\s*0\s*$') {
      throw 'JitPack build.log 包含权限错误或 exit code 非 0'
    }
    $hashes = @{
      Main = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($remote.Main)).ToLowerInvariant()
      Dev = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($remote.Dev)).ToLowerInvariant()
      Sources = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($remote.Sources)).ToLowerInvariant()
    }
    foreach ($pair in @(@('Main', $ExpectedMainSha256), @('Dev', $ExpectedDevSha256), @('Sources', $ExpectedSourcesSha256))) {
      if (-not [string]::IsNullOrWhiteSpace($pair[1]) -and $hashes[$pair[0]] -cne ([string]$pair[1]).ToLowerInvariant()) {
        throw "远端 $($pair[0]) 与 publication gate hash 不匹配"
      }
    }
    if (@($hashes.Values | Sort-Object -Unique).Count -ne 3) { throw '远端 main/dev/sources hash 必须互异' }
    Write-Outputs $hashes $GitHubOutput
    [pscustomobject]@{ status = 'REMOTE_PUBLICATION_OK'; gav = "$GroupId`:$ArtifactId`:$Version"; commit = $Commit; hashes = $hashes }
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

function Invoke-FixtureLocal($Fixture, [string]$Policy) {
  $script:RepositoryRoot = $Fixture.Repository
  $script:BuildMainJar = Join-Path $Fixture.Libs ([IO.Path]::GetFileName($Fixture.Paths.Main))
  $script:BuildDevJar = Join-Path $Fixture.Libs ([IO.Path]::GetFileName($Fixture.Paths.Dev))
  $script:BuildSourcesJar = Join-Path $Fixture.Libs ([IO.Path]::GetFileName($Fixture.Paths.Sources))
  $script:GroupId = $Fixture.Group; $script:ArtifactId = $Fixture.Artifact; $script:Version = $Fixture.Version
  $script:ModuleMetadata = $Policy; $script:ForbiddenGroupId = $null; $script:GitHubOutput = $null
  Invoke-LocalCheck
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

    $body = [Text.Encoding]::UTF8.GetBytes('remote-body')
    $badSha = [Text.Encoding]::UTF8.GetBytes('0000000000000000000000000000000000000000')
    Assert-Throws { Assert-RemoteSha1 $body $badSha 'fixture' } '远端 hash 不匹配'
    [pscustomobject]@{ status = 'SELF_TEST_OK'; covered = @('correct-gmm', 'api-runtime-dev', 'duplicate-url-hash', 'missing-dev', 'wrong-gav', 'forbidden-module', 'remote-hash-mismatch') }
  } finally { if (Test-Path -LiteralPath $root) { Remove-Item -LiteralPath $root -Recurse -Force } }
}

if ($SelfTest) { Invoke-SelfTest }
elseif ($Local) { Invoke-LocalCheck }
else { Invoke-RemoteCheck }
