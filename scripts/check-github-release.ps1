#requires -Version 7.0
[CmdletBinding(DefaultParameterSetName = 'SelfTest')]
param(
  [Parameter(Mandatory = $true, ParameterSetName = 'Identity')][switch]$Identity,
  [Parameter(Mandatory = $true, ParameterSetName = 'Local')][switch]$Local,
  [Parameter(Mandatory = $true, ParameterSetName = 'Remote')][switch]$Remote,
  [Parameter(Mandatory = $true, ParameterSetName = 'Static')][switch]$Static,
  [Parameter(Mandatory = $true, ParameterSetName = 'SelfTest')][switch]$SelfTest,
  [Parameter(Mandatory = $true, ParameterSetName = 'Identity')]
  [Parameter(Mandatory = $true, ParameterSetName = 'Local')][string]$RepositoryRoot,
  [Parameter(Mandatory = $true, ParameterSetName = 'Identity')]
  [Parameter(Mandatory = $true, ParameterSetName = 'Local')]
  [Parameter(Mandatory = $true, ParameterSetName = 'Remote')][string]$TargetTag,
  [Parameter(Mandatory = $true, ParameterSetName = 'Identity')]
  [Parameter(Mandatory = $true, ParameterSetName = 'Local')]
  [Parameter(Mandatory = $true, ParameterSetName = 'Remote')][string]$ExpectedTagObject,
  [Parameter(Mandatory = $true, ParameterSetName = 'Identity')]
  [Parameter(Mandatory = $true, ParameterSetName = 'Local')]
  [Parameter(Mandatory = $true, ParameterSetName = 'Remote')][string]$ExpectedCommit,
  [Parameter(Mandatory = $true, ParameterSetName = 'Local')]
  [Parameter(Mandatory = $true, ParameterSetName = 'Remote')][string]$AssetRoot,
  [Parameter(Mandatory = $true, ParameterSetName = 'Local')]
  [Parameter(Mandatory = $true, ParameterSetName = 'Remote')][string]$NotesPath,
  [Parameter(Mandatory = $true, ParameterSetName = 'Local')]
  [Parameter(Mandatory = $true, ParameterSetName = 'Remote')][string]$ManifestPath,
  [Parameter(Mandatory = $true, ParameterSetName = 'Remote')][string]$Repository,
  [Parameter(Mandatory = $true, ParameterSetName = 'Remote')]
  [ValidateSet('Preflight', 'PublishedOnlyPreflight', 'Draft', 'Published')][string]$ExpectedState,
  [Parameter(ParameterSetName = 'Remote')][long]$ReleaseId,
  [Parameter(ParameterSetName = 'Remote')][string]$GitHubOutput,
  [Parameter(Mandatory = $true, ParameterSetName = 'Static')][string]$WorkflowRoot
)

$ErrorActionPreference = 'Stop'
$ExpectedAssetSuffixes = @('.jar', '-dev.jar', '-sources.jar', '-dev-preshadow.jar')
$ReleaseIdWasProvided = $PSBoundParameters.ContainsKey('ReleaseId')

function Assert-SafeTag([string]$Value) {
  if ([string]::IsNullOrWhiteSpace($Value) -or $Value -cnotmatch '^[0-9A-Za-z][0-9A-Za-z._-]*$') {
    throw 'target tag 不是安全的单段标签名'
  }
}

function Assert-Sha([string]$Value, [string]$Label) {
  if ($Value -cnotmatch '^[a-fA-F0-9]{40}$') { throw "$Label 必须是 40 位 SHA" }
}

function Get-HashLower([string]$Path) {
  (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-BytesHash([byte[]]$Bytes) {
  [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($Bytes)).ToLowerInvariant()
}

function Get-CanonicalNotes([string]$Text) {
  ($Text -replace "`r`n", "`n").TrimEnd("`r", "`n")
}

function Get-ExpectedAssetNames([string]$Tag) {
  @($ExpectedAssetSuffixes | ForEach-Object { "qz_uilib-$Tag$_" })
}

function Invoke-Git([string]$Root, [string[]]$Arguments) {
  $result = @(& git -C $Root @Arguments 2>&1)
  if ($LASTEXITCODE -ne 0) { throw "git $($Arguments -join ' ') 失败：$($result -join "`n")" }
  ($result -join "`n").Trim()
}

function Get-ModVersion([string]$Root) {
  $path = Join-Path $Root 'gradle.properties'
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw '缺少 gradle.properties' }
  $matches = @([IO.File]::ReadAllLines($path) | ForEach-Object {
      if ($_ -match '^\s*modVersion\s*=\s*(?<value>.*?)\s*$') { $Matches.value.Trim() }
    })
  if ($matches.Count -ne 1 -or [string]::IsNullOrWhiteSpace($matches[0])) {
    throw 'gradle.properties 必须包含且仅包含一个非空 modVersion'
  }
  $matches[0]
}

function Assert-IdentityContract([string]$Root, [string]$Tag, [string]$TagObject, [string]$Commit) {
  Assert-SafeTag $Tag
  Assert-Sha $TagObject 'expected tag object'
  Assert-Sha $Commit 'expected commit'
  $fullRoot = [IO.Path]::GetFullPath($Root)
  if (-not (Test-Path -LiteralPath $fullRoot -PathType Container)) { throw 'RepositoryRoot 不存在' }
  $type = Invoke-Git $fullRoot @('cat-file', '-t', "refs/tags/$Tag")
  if ($type -cne 'tag') { throw "标签 $Tag 必须是 annotated tag object" }
  $actualObject = Invoke-Git $fullRoot @('rev-parse', "refs/tags/$Tag")
  $actualCommit = Invoke-Git $fullRoot @('rev-parse', "refs/tags/$Tag^{commit}")
  $head = Invoke-Git $fullRoot @('rev-parse', 'HEAD')
  if ($actualObject -cne $TagObject.ToLowerInvariant()) { throw 'annotated tag object 不匹配' }
  if ($actualCommit -cne $Commit.ToLowerInvariant()) { throw 'peeled commit 不匹配' }
  if ($head -cne $Commit.ToLowerInvariant()) { throw 'HEAD 未精确停在 peeled commit' }
  if ((Get-ModVersion $fullRoot) -cne $Tag) { throw 'modVersion 与 target tag 不匹配' }
  $notes = Join-Path $fullRoot ".changelogs/$Tag.md"
  if (-not (Test-Path -LiteralPath $notes -PathType Leaf) -or (Get-Item -LiteralPath $notes).Length -le 0) {
    throw '目标 commit 缺少非空权威 release notes'
  }
  [pscustomobject]@{ TagObject = $actualObject; Commit = $actualCommit; Notes = $notes }
}

function Assert-Jar([string]$Path, [string]$Label) {
  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "缺少 $Label" }
  if ((Get-Item -LiteralPath $Path).Length -le 0) { throw "$Label 为空" }
  try {
    $archive = [IO.Compression.ZipFile]::OpenRead($Path)
    try { if ($archive.Entries.Count -eq 0) { throw "$Label 是空归档" } } finally { $archive.Dispose() }
  } catch { throw "$Label 不是有效的非空 JAR：$($_.Exception.Message)" }
}

function New-ReleaseManifest([string]$Tag, [string]$TagObject, [string]$Commit,
    [string]$Root, [string]$Notes) {
  $expected = Get-ExpectedAssetNames $Tag
  $actual = @(Get-ChildItem -LiteralPath $Root -File -Filter '*.jar' | ForEach-Object Name | Sort-Object)
  if (($actual -join "`n") -cne (($expected | Sort-Object) -join "`n")) {
    throw "四资产集合不精确；expected=$($expected -join ','); actual=$($actual -join ',')"
  }
  $assets = foreach ($name in $expected) {
    $path = Join-Path $Root $name
    Assert-Jar $path $name
    [ordered]@{ name = $name; size = (Get-Item -LiteralPath $path).Length; sha256 = Get-HashLower $path }
  }
  if (@($assets.sha256 | Sort-Object -Unique).Count -ne 4) { throw '四个 Release 资产的 SHA-256 必须互异' }
  if (-not (Test-Path -LiteralPath $Notes -PathType Leaf) -or (Get-Item -LiteralPath $Notes).Length -le 0) {
    throw 'release notes 缺失或为空'
  }
  [ordered]@{
    schema = 'qz-github-release-manifest/v1'
    targetTag = $Tag
    tagObject = $TagObject.ToLowerInvariant()
    commit = $Commit.ToLowerInvariant()
    notesSha256 = Get-HashLower $Notes
    assets = @($assets)
  }
}

function Assert-ManifestBinding($Manifest, [string]$Tag, [string]$TagObject, [string]$Commit,
    [string]$Root, [string]$Notes) {
  if ($Manifest.schema -cne 'qz-github-release-manifest/v1' -or
      $Manifest.targetTag -cne $Tag -or $Manifest.tagObject -cne $TagObject.ToLowerInvariant() -or
      $Manifest.commit -cne $Commit.ToLowerInvariant()) { throw 'manifest 的 tag object/commit 绑定不正确' }
  if ($Manifest.notesSha256 -cne (Get-HashLower $Notes)) { throw 'manifest 的 notes hash 不匹配' }
  $expected = Get-ExpectedAssetNames $Tag
  $entries = @($Manifest.assets)
  if ($entries.Count -ne 4 -or (($entries.name | Sort-Object) -join "`n") -cne (($expected | Sort-Object) -join "`n")) {
    throw 'manifest 资产集合不精确'
  }
  foreach ($entry in $entries) {
    $path = Join-Path $Root ([string]$entry.name)
    Assert-Jar $path ([string]$entry.name)
    if ([long]$entry.size -ne (Get-Item -LiteralPath $path).Length -or
        [string]$entry.sha256 -cne (Get-HashLower $path)) { throw "manifest 资产大小/hash 不匹配：$($entry.name)" }
  }
  if (@($entries.sha256 | Sort-Object -Unique).Count -ne 4) { throw 'manifest 中四资产 hash 必须互异' }
}

function Invoke-LocalContract {
  $identityResult = Assert-IdentityContract $RepositoryRoot $TargetTag $ExpectedTagObject $ExpectedCommit
  $authorityNotes = [IO.File]::ReadAllText($identityResult.Notes, [Text.Encoding]::UTF8)
  $bundleNotes = [IO.File]::ReadAllText($NotesPath, [Text.Encoding]::UTF8)
  if ((Get-CanonicalNotes $authorityNotes) -cne (Get-CanonicalNotes $bundleNotes)) {
    throw 'bundle release notes 与目标 commit 权威源不一致'
  }
  $manifest = New-ReleaseManifest $TargetTag $ExpectedTagObject $ExpectedCommit $AssetRoot $NotesPath
  if (Test-Path -LiteralPath $ManifestPath -PathType Leaf) {
    $existing = [IO.File]::ReadAllText($ManifestPath, [Text.Encoding]::UTF8) | ConvertFrom-Json -Depth 20
    Assert-ManifestBinding $existing $TargetTag $ExpectedTagObject $ExpectedCommit $AssetRoot $NotesPath
  }
  $json = $manifest | ConvertTo-Json -Depth 10
  [IO.File]::WriteAllText([IO.Path]::GetFullPath($ManifestPath), $json, [Text.UTF8Encoding]::new($false))
  Assert-ManifestBinding $manifest $TargetTag $ExpectedTagObject $ExpectedCommit $AssetRoot $NotesPath
  [pscustomobject]@{ status = 'LOCAL_RELEASE_CONTRACT_OK'; tag = $TargetTag; manifest = $ManifestPath }
}

function New-GitHubClient {
  if ([string]::IsNullOrWhiteSpace($env:GH_TOKEN)) { throw 'GH_TOKEN 不存在，无法执行 Remote 合同' }
  $client = [Net.Http.HttpClient]::new()
  $client.DefaultRequestHeaders.UserAgent.ParseAdd('Qz-UILib-release-contract/1')
  $client.DefaultRequestHeaders.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $env:GH_TOKEN)
  $client.DefaultRequestHeaders.Accept.ParseAdd('application/vnd.github+json')
  $client.DefaultRequestHeaders.Add('X-GitHub-Api-Version', '2022-11-28')
  $client
}

function Get-GitHubJson([Net.Http.HttpClient]$Client, [string]$Uri) {
  $result = Get-GitHubJsonResult $Client $Uri
  if (-not $result.Found) { throw 'GitHub API HTTP 404' }
  $result.Value
}

function Get-GitHubJsonResult([Net.Http.HttpClient]$Client, [string]$Uri) {
  $response = $Client.GetAsync($Uri).GetAwaiter().GetResult()
  try {
    $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if ([int]$response.StatusCode -eq 404) { return [pscustomobject]@{ Found = $false; Value = $null } }
    if (-not $response.IsSuccessStatusCode) { throw "GitHub API HTTP $([int]$response.StatusCode)" }
    [pscustomobject]@{ Found = $true; Value = ($text | ConvertFrom-Json -Depth 100) }
  } finally { $response.Dispose() }
}

function Get-GitHubAssetBytes([Net.Http.HttpClient]$Client, [long]$Id, [string]$Repo) {
  $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Get,
    "https://api.github.com/repos/$Repo/releases/assets/$Id")
  $request.Headers.Accept.Clear()
  $request.Headers.Accept.ParseAdd('application/octet-stream')
  $response = $Client.Send($request)
  try {
    if (-not $response.IsSuccessStatusCode) { throw "GitHub asset API HTTP $([int]$response.StatusCode)" }
    $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
  } finally { $response.Dispose(); $request.Dispose() }
}

function Get-GitHubReleasesForTag([Net.Http.HttpClient]$Client, [string]$Repo, [string]$Tag) {
  $matches = @()
  for ($page = 1; $page -le 100; $page++) {
    $batch = @(Get-GitHubJson $Client "https://api.github.com/repos/$Repo/releases?per_page=100&page=$page")
    $matches += @($batch | Where-Object { $_.tag_name -ceq $Tag })
    if ($batch.Count -lt 100) { return @($matches) }
  }
  throw 'GitHub Release 列表超过安全分页上限'
}

function Assert-RemoteRelease($Release, $Manifest, [string]$Tag, [string]$Notes,
    [string]$State, [long]$ExpectedReleaseId, [scriptblock]$Download) {
  $actualReleaseId = [long]$Release.id
  if ($actualReleaseId -le 0) { throw '远端 Release ID 缺失或无效' }
  if ($ExpectedReleaseId -gt 0 -and $actualReleaseId -ne $ExpectedReleaseId) {
    throw "远端 Release ID 漂移：expected=$ExpectedReleaseId actual=$actualReleaseId"
  }
  if ([string]$Release.tag_name -cne $Tag -or [string]$Release.name -cne $Tag) {
    throw '远端 Release tag/title 不匹配'
  }
  $expectedPrerelease = $Tag -match '-(beta|alpha|rc)([-.]?[0-9A-Za-z].*)?$'
  if ([bool]$Release.prerelease -ne $expectedPrerelease) { throw '远端 Release prerelease 状态不匹配'
  }
  if ($State -ceq 'Draft' -and ([bool]$Release.draft -ne $true -or $null -ne $Release.published_at)) {
    throw '远端 Release 不是未发布 draft'
  }
  if ($State -ceq 'Published' -and ([bool]$Release.draft -ne $false -or
      [string]::IsNullOrWhiteSpace([string]$Release.published_at))) { throw '远端 Release 尚未正式发布' }
  $expectedNotes = [IO.File]::ReadAllText($Notes, [Text.Encoding]::UTF8)
  if ((Get-CanonicalNotes ([string]$Release.body)) -cne (Get-CanonicalNotes $expectedNotes)) {
    throw '远端 Release body 与权威 notes 不一致'
  }
  $expectedNames = @($Manifest.assets.name | Sort-Object)
  $remoteAssets = @($Release.assets)
  if ($remoteAssets.Count -ne 4 -or (($remoteAssets.name | Sort-Object) -join "`n") -cne ($expectedNames -join "`n")) {
    throw '远端 Release 四资产集合不精确'
  }
  if (@($remoteAssets.id | Sort-Object -Unique).Count -ne 4) { throw '远端资产 ID 缺失或重复' }
  if (@($remoteAssets | Where-Object { [long]$_.id -le 0 }).Count -gt 0) { throw '远端资产 ID 必须是正整数' }
  foreach ($remoteAsset in $remoteAssets) {
    $entry = @($Manifest.assets | Where-Object { $_.name -ceq $remoteAsset.name })
    if ($entry.Count -ne 1 -or [long]$remoteAsset.size -ne [long]$entry[0].size) {
      throw "远端资产大小不匹配：$($remoteAsset.name)"
    }
    $bytes = & $Download ([long]$remoteAsset.id)
    if ($bytes.Length -ne [long]$entry[0].size -or (Get-BytesHash $bytes) -cne [string]$entry[0].sha256) {
      throw "按 asset ID 下载后的大小/hash 不匹配：$($remoteAsset.name)"
    }
  }
}

function Write-RemoteOutput([string]$Status) {
  if ($Status -cnotin @('absent', 'matching_published')) {
    throw "release_status 不在允许集合：$Status"
  }
  if ([string]::IsNullOrWhiteSpace($GitHubOutput)) { return }
  $parent = Split-Path -Parent ([IO.Path]::GetFullPath($GitHubOutput))
  if (-not (Test-Path -LiteralPath $parent -PathType Container)) { throw 'GITHUB_OUTPUT 父目录不存在' }
  [IO.File]::AppendAllText($GitHubOutput, "release_status=$Status`n", [Text.UTF8Encoding]::new($false))
}

function Get-RemoteReleaseSelection([string]$State, [long]$Id, [scriptblock]$ListForTag,
    [scriptblock]$GetById, [scriptblock]$GetPublishedByTag) {
  if ($State -ceq 'PublishedOnlyPreflight') {
    if ($Id -gt 0) { throw 'PublishedOnlyPreflight 不接受 ReleaseId' }
    $published = & $GetPublishedByTag
    if (-not [bool]$published.Found) {
      return [pscustomobject]@{ Status = 'absent'; Release = $null; VerifyState = $null; BoundId = 0L }
    }
    return [pscustomobject]@{ Status = 'verify'; Release = $published.Value; VerifyState = 'Published'; BoundId = 0L }
  }

  if ($State -ceq 'Preflight') {
    if ($Id -gt 0) { throw 'Preflight 不接受 ReleaseId' }
    $releases = @(& $ListForTag)
    if ($releases.Count -gt 1) { throw '同一 tag 存在多个 Release 记录' }
    if ($releases.Count -eq 0) {
      return [pscustomobject]@{ Status = 'absent'; Release = $null; VerifyState = $null; BoundId = 0L }
    }
    if ([bool]$releases[0].draft) { throw '目标 tag 已存在 draft；禁止覆盖或续传' }
    return [pscustomobject]@{ Status = 'verify'; Release = $releases[0]; VerifyState = 'Published'; BoundId = 0L }
  }

  if ($State -ceq 'Draft') {
    if ($Id -le 0) { throw 'Draft 状态验证必须提供正整数 ReleaseId' }
    return [pscustomobject]@{ Status = 'verify'; Release = (& $GetById $Id); VerifyState = 'Draft'; BoundId = $Id }
  }

  if ($Id -gt 0) {
    return [pscustomobject]@{ Status = 'verify'; Release = (& $GetById $Id); VerifyState = 'Published'; BoundId = $Id }
  }
  $releases = @(& $ListForTag)
  if ($releases.Count -ne 1) { throw '远端缺少预期 Published Release' }
  [pscustomobject]@{ Status = 'verify'; Release = $releases[0]; VerifyState = 'Published'; BoundId = 0L }
}

function Invoke-RemoteContract {
  Assert-SafeTag $TargetTag; Assert-Sha $ExpectedTagObject 'expected tag object'; Assert-Sha $ExpectedCommit 'expected commit'
  if ($Repository -cnotmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') { throw 'Repository 必须是 owner/name' }
  if ($ReleaseIdWasProvided -and $ReleaseId -le 0) { throw 'ReleaseId 必须是正整数' }
  if ($ExpectedState -ceq 'Draft' -and -not $ReleaseIdWasProvided) {
    throw 'Draft 状态验证必须提供正整数 ReleaseId'
  }
  $manifest = [IO.File]::ReadAllText($ManifestPath, [Text.Encoding]::UTF8) | ConvertFrom-Json -Depth 20
  Assert-ManifestBinding $manifest $TargetTag $ExpectedTagObject $ExpectedCommit $AssetRoot $NotesPath
  $client = New-GitHubClient
  try {
    $selection = Get-RemoteReleaseSelection $ExpectedState $ReleaseId {
      Get-GitHubReleasesForTag $client $Repository $TargetTag
    } {
      param($id) Get-GitHubJson $client "https://api.github.com/repos/$Repository/releases/$id"
    } {
      Get-GitHubJsonResult $client "https://api.github.com/repos/$Repository/releases/tags/$TargetTag"
    }
    if ($selection.Status -ceq 'absent') {
      Write-RemoteOutput 'absent'
      $status = if ($ExpectedState -ceq 'PublishedOnlyPreflight') {
        'REMOTE_PUBLISHED_RELEASE_ABSENT'
      } else { 'REMOTE_RELEASE_ABSENT' }
      return [pscustomobject]@{ status = $status }
    }
    Assert-RemoteRelease $selection.Release $manifest $TargetTag $NotesPath $selection.VerifyState $selection.BoundId {
      param($id) Get-GitHubAssetBytes $client $id $Repository
    }
    if ($ExpectedState -in @('Preflight', 'PublishedOnlyPreflight')) {
      Write-RemoteOutput 'matching_published'
      return [pscustomobject]@{ status = 'REMOTE_MATCHING_PUBLISHED' }
    }
    [pscustomobject]@{ status = "REMOTE_$($ExpectedState.ToUpperInvariant())_OK"; release_id = [long]$selection.Release.id }
  } finally { $client.Dispose() }
}

function Assert-PinnedActions([string]$Name, [string]$Text) {
  $lines = $Text -split "`n"
  for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '^\s*(?:-\s*)?uses:\s*(?<use>\S+)\s*$') {
      $use = $Matches.use
      if (-not $use.StartsWith('./') -and $use -cnotmatch '@[a-f0-9]{40}$') { throw "$Name 含未固定完整 SHA 的 action：$use" }
      if ($use -match '^actions/checkout@') {
        $window = ($lines[($i + 1)..([Math]::Min($i + 8, $lines.Count - 1))] -join "`n")
        if ($window -notmatch 'persist-credentials:\s*false') { throw "$Name 的 checkout 缺少 persist-credentials:false" }
      }
    }
  }
}

function Get-YamlStructuralLines([string]$Text) {
  $result = [Collections.Generic.List[object]]::new()
  $blockScalarIndent = $null
  $lineNumber = 0
  foreach ($line in ($Text -replace "`r", '') -split "`n") {
    $lineNumber++
    if ($line -match '^\s*$') { continue }
    if ($line -notmatch '^(?<spaces> *)') { throw "YAML 第 $lineNumber 行缩进无法解析" }
    $indent = $Matches.spaces.Length
    if ($null -ne $blockScalarIndent) {
      if ($indent -gt $blockScalarIndent) { continue }
      $blockScalarIndent = $null
    }
    $trimmed = $line.Trim()
    if ($trimmed.StartsWith('#')) { continue }
    $result.Add([pscustomobject]@{ Number = $lineNumber; Indent = $indent; Text = $line; Trimmed = $trimmed })
    $entry = Get-YamlMappingEntry $trimmed
    if ($null -eq $entry) { continue }
    $blockScalarHeader = (Remove-YamlTrailingComment $entry.Value).Trim()
    if ($blockScalarHeader -notmatch '^[|>]') { continue }
    if ($entry.Key -ceq 'permissions' -or $entry.Key -ceq 'contents') {
      throw "Static 权限声明禁止 block scalar：YAML 第 $lineNumber 行 key=$($entry.Key)"
    }
    $blockScalarIndent = $indent
  }
  @($result)
}

function Remove-YamlTrailingComment([string]$Text) {
  $quote = ''
  for ($i = 0; $i -lt $Text.Length; $i++) {
    $character = $Text[$i]
    if ($quote -ceq "'") {
      if ($character -ceq "'" -and $i + 1 -lt $Text.Length -and $Text[$i + 1] -ceq "'") { $i++; continue }
      if ($character -ceq "'") { $quote = '' }
      continue
    }
    if ($quote -ceq '"') {
      if ($character -ceq '\' -and $i + 1 -lt $Text.Length) { $i++; continue }
      if ($character -ceq '"') { $quote = '' }
      continue
    }
    if ($character -ceq "'" -or $character -ceq '"') { $quote = [string]$character; continue }
    if ($character -ceq '#' -and ($i -eq 0 -or [char]::IsWhiteSpace($Text[$i - 1]))) {
      return $Text.Substring(0, $i).TrimEnd()
    }
  }
  $Text.TrimEnd()
}

function Get-YamlScalarValue([string]$Text) {
  $value = (Remove-YamlTrailingComment $Text).Trim()
  if ($value.Length -ge 2 -and $value[0] -ceq "'" -and $value[$value.Length - 1] -ceq "'") {
    return $value.Substring(1, $value.Length - 2).Replace("''", "'")
  }
  if ($value.Length -ge 2 -and $value[0] -ceq '"' -and $value[$value.Length - 1] -ceq '"') {
    $inner = $value.Substring(1, $value.Length - 2)
    if ($inner -match '["\\]') { return $null }
    return $inner
  }
  $value
}

function Get-YamlMappingEntry([string]$Text) {
  $value = (Remove-YamlTrailingComment $Text).Trim()
  $quote = ''
  for ($i = 0; $i -lt $value.Length; $i++) {
    $character = $value[$i]
    if ($quote -ceq "'") {
      if ($character -ceq "'" -and $i + 1 -lt $value.Length -and $value[$i + 1] -ceq "'") { $i++; continue }
      if ($character -ceq "'") { $quote = '' }
      continue
    }
    if ($quote -ceq '"') {
      if ($character -ceq '\' -and $i + 1 -lt $value.Length) { $i++; continue }
      if ($character -ceq '"') { $quote = '' }
      continue
    }
    if ($character -ceq "'" -or $character -ceq '"') { $quote = [string]$character; continue }
    if ($character -ceq ':') {
      $key = Get-YamlScalarValue $value.Substring(0, $i)
      if ([string]::IsNullOrWhiteSpace($key)) { return $null }
      return [pscustomobject]@{ Key = $key; Value = $value.Substring($i + 1).Trim() }
    }
  }
  $null
}

function Split-YamlFlowItems([string]$Text) {
  $items = [Collections.Generic.List[string]]::new()
  $start = 0
  $quote = ''
  $depth = 0
  for ($i = 0; $i -lt $Text.Length; $i++) {
    $character = $Text[$i]
    if ($quote -ceq "'") {
      if ($character -ceq "'" -and $i + 1 -lt $Text.Length -and $Text[$i + 1] -ceq "'") { $i++; continue }
      if ($character -ceq "'") { $quote = '' }
      continue
    }
    if ($quote -ceq '"') {
      if ($character -ceq '\' -and $i + 1 -lt $Text.Length) { $i++; continue }
      if ($character -ceq '"') { $quote = '' }
      continue
    }
    if ($character -ceq "'" -or $character -ceq '"') { $quote = [string]$character; continue }
    if ($character -in @('{', '[')) { $depth++; continue }
    if ($character -in @('}', ']')) { $depth--; continue }
    if ($character -ceq ',' -and $depth -eq 0) {
      $items.Add($Text.Substring($start, $i - $start))
      $start = $i + 1
    }
  }
  $items.Add($Text.Substring($start))
  @($items)
}

function Test-YamlFlowContentsAccess([string]$Text, [string]$Access) {
  $value = (Remove-YamlTrailingComment $Text).Trim()
  if ($value.Length -lt 2 -or $value[0] -cne '{' -or $value[$value.Length - 1] -cne '}') { return $false }
  $inner = $value.Substring(1, $value.Length - 2)
  foreach ($item in @(Split-YamlFlowItems $inner)) {
    $entry = Get-YamlMappingEntry $item
    if ($null -ne $entry -and $entry.Key -ceq 'contents' -and
        (Get-YamlScalarValue $entry.Value) -ceq $Access) { return $true }
  }
  $false
}

function Get-WorkflowJobs([object[]]$Lines) {
  $jobs = @{}
  $jobsStart = -1
  for ($i = 0; $i -lt $Lines.Count; $i++) {
    if ($Lines[$i].Indent -eq 0 -and $Lines[$i].Trimmed -eq 'jobs:') { $jobsStart = $i; break }
  }
  if ($jobsStart -lt 0) { return $jobs }
  for ($i = $jobsStart + 1; $i -lt $Lines.Count; $i++) {
    if ($Lines[$i].Indent -eq 0) { break }
    if ($Lines[$i].Indent -ne 2 -or $Lines[$i].Trimmed -notmatch '^(?<name>[A-Za-z0-9_-]+):\s*(?:#.*)?$') { continue }
    $name = $Matches.name
    if ($jobs.ContainsKey($name)) { throw "workflow job 重复：$name" }
    $block = [Collections.Generic.List[object]]::new()
    $block.Add($Lines[$i])
    for ($j = $i + 1; $j -lt $Lines.Count -and $Lines[$j].Indent -gt 2; $j++) { $block.Add($Lines[$j]) }
    $jobs[$name] = @($block)
  }
  $jobs
}

function Get-DirectJobValue([object[]]$Block, [string]$Key) {
  $escaped = [regex]::Escape($Key)
  $values = @($Block | Where-Object { $_.Indent -eq 4 -and $_.Trimmed -match "^${escaped}:\s*(?<value>.*?)\s*$" } |
      ForEach-Object { if ($_.Trimmed -match "^${escaped}:\s*(?<value>.*?)\s*$") { $Matches.value } })
  if ($values.Count -gt 1) { throw "job 属性重复：$Key" }
  if ($values.Count -eq 1) { return [string]$values[0] }
  $null
}

function Get-JobChildValue([object[]]$Block, [string]$Parent, [string]$Key) {
  $parentIndex = -1
  for ($i = 0; $i -lt $Block.Count; $i++) {
    if ($Block[$i].Indent -eq 4 -and $Block[$i].Trimmed -eq "${Parent}:") { $parentIndex = $i; break }
  }
  if ($parentIndex -lt 0) { return $null }
  for ($i = $parentIndex + 1; $i -lt $Block.Count -and $Block[$i].Indent -gt 4; $i++) {
    if ($Block[$i].Indent -eq 6 -and $Block[$i].Trimmed -match "^$([regex]::Escape($Key)):\s*(?<value>.*?)\s*$") {
      return [string]$Matches.value
    }
  }
  $null
}

function Get-ContentsAccessLocations([string]$Name, [string]$Text, [string]$Access) {
  $lines = @(Get-YamlStructuralLines $Text)
  $jobs = Get-WorkflowJobs $lines
  $locations = [Collections.Generic.List[string]]::new()
  for ($lineIndex = 0; $lineIndex -lt $lines.Count; $lineIndex++) {
    $line = $lines[$lineIndex]
    $entry = Get-YamlMappingEntry $line.Trimmed
    if ($null -eq $entry) { continue }
    $isContentsAccess = $entry.Key -ceq 'contents' -and (Get-YamlScalarValue $entry.Value) -ceq $Access
    $isInlineAccess = $entry.Key -ceq 'permissions' -and
      ((Test-YamlFlowContentsAccess $entry.Value $Access) -or
        ($Access -ceq 'write' -and (Get-YamlScalarValue $entry.Value) -ceq 'write-all'))
    if (-not $isContentsAccess -and -not $isInlineAccess) { continue }
    $permissionLine = if ($isInlineAccess) { $line } else {
      $candidate = $null
      for ($i = $lineIndex - 1; $i -ge 0; $i--) {
        if ($lines[$i].Indent -lt $line.Indent) { $candidate = $lines[$i]; break }
      }
      $candidate
    }
    $permissionEntry = if ($null -eq $permissionLine) { $null } else { Get-YamlMappingEntry $permissionLine.Trimmed }
    if ($null -eq $permissionEntry -or $permissionEntry.Key -cne 'permissions') {
      $locations.Add("$Name/<invalid-line-$($line.Number)>")
      continue
    }
    if ($permissionLine.Indent -eq 0) {
      $locations.Add("$Name/<top-level>")
      continue
    }
    $jobName = $null
    foreach ($entry in $jobs.GetEnumerator()) {
      if (@($entry.Value | Where-Object Number -eq $line.Number).Count -eq 1) { $jobName = $entry.Key; break }
    }
    if ($permissionLine.Indent -ne 4 -or [string]::IsNullOrWhiteSpace($jobName)) {
      $locations.Add("$Name/<invalid-line-$($line.Number)>")
    } else {
      $locations.Add("$Name/$jobName")
    }
  }
  @($locations)
}

function Get-ContentsWriteLocations([string]$Name, [string]$Text) {
  @(Get-ContentsAccessLocations $Name $Text 'write')
}

function Assert-WritePermissionStructure([hashtable]$Documents) {
  $actual = [Collections.Generic.List[string]]::new()
  foreach ($entry in $Documents.GetEnumerator()) {
    foreach ($location in @(Get-ContentsWriteLocations $entry.Key ([string]$entry.Value))) { $actual.Add($location) }
  }
  $expected = @(
    'release-tags.yml/release',
    'recover-4.6.2-release.yml/publish',
    'recover-4.6.2-release.yml/publish-existing-draft',
    '_github-release-publish.yml/publish-release')
  if ($actual.Count -ne $expected.Count -or
      (($actual | Sort-Object) -join "`n") -cne (($expected | Sort-Object) -join "`n")) {
    throw "contents:write 仅允许四个精确授权 job；actual=$($actual -join ',')"
  }

  $structures = @{}
  foreach ($name in @('release-tags.yml', 'recover-4.6.2-release.yml', '_github-release-contract.yml',
      '_github-release-publish.yml')) {
    $structures[$name] = Get-WorkflowJobs @(Get-YamlStructuralLines ([string]$Documents[$name]))
  }
  $tagRelease = $structures['release-tags.yml']['release']
  if ($null -eq $tagRelease -or
      (Get-DirectJobValue $tagRelease 'uses') -cne './.github/workflows/_github-release-publish.yml' -or
      (Get-DirectJobValue $tagRelease 'needs') -cne 'identity' -or
      $null -ne (Get-DirectJobValue $tagRelease 'if') -or
      $null -ne (Get-JobChildValue $tagRelease 'with' 'publish')) {
    throw 'tag release 授权 job 的 needs/condition/uses 结构不正确'
  }
  $recoveryVerify = $structures['recover-4.6.2-release.yml']['verify-only']
  $recoveryVerifyCondition = Get-DirectJobValue $recoveryVerify 'if'
  if ($null -eq $recoveryVerify -or
      (Get-DirectJobValue $recoveryVerify 'uses') -cne './.github/workflows/_github-release-contract.yml' -or
      (Get-DirectJobValue $recoveryVerify 'needs') -cne 'confirmation' -or
      $recoveryVerifyCondition -notmatch '^\$\{\{\s*inputs\.mode\s*==\s*''verify-only''\s*\}\}$' -or
      (Get-JobChildValue $recoveryVerify 'with' 'publish')) {
    throw 'recovery verify-only 必须以只读权限直调 read contract'
  }
  $recoveryPublish = $structures['recover-4.6.2-release.yml']['publish']
  $recoveryCondition = Get-DirectJobValue $recoveryPublish 'if'
  if ($null -eq $recoveryPublish -or
      (Get-DirectJobValue $recoveryPublish 'uses') -cne './.github/workflows/_github-release-publish.yml' -or
      (Get-DirectJobValue $recoveryPublish 'needs') -cne 'confirmation' -or
      $recoveryCondition -notmatch '^\$\{\{\s*inputs\.mode\s*==\s*''publish''\s*\}\}$' -or
      $null -ne (Get-JobChildValue $recoveryPublish 'with' 'publish')) {
    throw 'recovery publish 授权 job 的 needs/condition/uses 结构不正确'
  }
  $recoveryExisting = $structures['recover-4.6.2-release.yml']['publish-existing-draft']
  $recoveryExistingCondition = Get-DirectJobValue $recoveryExisting 'if'
  if ($null -eq $recoveryExisting -or
      (Get-DirectJobValue $recoveryExisting 'uses') -cne './.github/workflows/_github-release-publish.yml' -or
      (Get-DirectJobValue $recoveryExisting 'needs') -cne 'confirmation' -or
      $recoveryExistingCondition -notmatch '^\$\{\{\s*inputs\.mode\s*==\s*''publish-existing-draft''\s*\}\}$' -or
      (Get-JobChildValue $recoveryExisting 'with' 'existing-draft-id') -cne "'357902877'") {
    throw 'existing-draft recovery 必须以固定 ID 调用 write wrapper'
  }

  $wrapperVerify = $structures['_github-release-publish.yml']['verify']
  if ($null -eq $wrapperVerify -or
      (Get-DirectJobValue $wrapperVerify 'uses') -cne './.github/workflows/_github-release-contract.yml' -or
      $null -ne (Get-DirectJobValue $wrapperVerify 'needs') -or
      $null -ne (Get-DirectJobValue $wrapperVerify 'if')) {
    throw 'publish wrapper verify 必须以只读权限直调 read contract'
  }
  $wrapperPublish = $structures['_github-release-publish.yml']['publish-release']
  if ($null -eq $wrapperPublish -or $null -ne (Get-DirectJobValue $wrapperPublish 'uses') -or
      (Get-DirectJobValue $wrapperPublish 'needs') -cne 'verify' -or
      $null -ne (Get-DirectJobValue $wrapperPublish 'if') -or
      [string]::IsNullOrWhiteSpace((Get-DirectJobValue $wrapperPublish 'runs-on'))) {
    throw 'publish wrapper 的 publish-release 授权 job 结构不正确'
  }
}

function Assert-ExactJobNames([hashtable]$Jobs, [string[]]$Expected, [string]$Label) {
  $actual = @($Jobs.Keys | Sort-Object)
  if (($actual -join "`n") -cne (($Expected | Sort-Object) -join "`n")) {
    throw "$Label job 集合不精确；expected=$($Expected -join ',') actual=$($actual -join ',')"
  }
}

function Assert-ReusableIdentityInputs([string]$Text, [string]$Label) {
  $lines = @(Get-YamlStructuralLines $Text)
  $start = -1
  for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i].Indent -eq 4 -and $lines[$i].Trimmed -eq 'inputs:') { $start = $i; break }
  }
  if ($start -lt 0) { throw "$Label 缺少 workflow_call inputs" }
  $actual = [Collections.Generic.List[string]]::new()
  for ($i = $start + 1; $i -lt $lines.Count -and $lines[$i].Indent -gt 4; $i++) {
    if ($lines[$i].Indent -eq 6 -and $lines[$i].Trimmed -match '^(?<name>[A-Za-z0-9_-]+):\s*$') {
      $actual.Add($Matches.name)
    }
  }
  $expected = @('target-tag', 'expected-tag-object', 'expected-commit', 'existing-draft-id')
  if ((($actual | Sort-Object) -join "`n") -cne (($expected | Sort-Object) -join "`n")) {
    throw "$Label inputs 必须精确为三个 identity 字段和可选 existing-draft-id；actual=$($actual -join ',')"
  }
}

function Get-RawJobBlock([string]$Text, [string]$JobName) {
  $lines = @((($Text -replace "`r", '') -split "`n"))
  $start = -1
  $jobPattern = '^  ' + [regex]::Escape($JobName) + ':\s*(?:#.*)?$'
  for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match $jobPattern) { $start = $i; break }
  }
  if ($start -lt 0) { throw "缺少 workflow job：$JobName" }
  $end = $lines.Count
  for ($i = $start + 1; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '^  [A-Za-z0-9_-]+:\s*(?:#.*)?$') { $end = $i; break }
  }
  ($lines[$start..($end - 1)] -join "`n")
}

function Get-CheckoutStepBlocks([string]$JobBlock) {
  $lines = @((($JobBlock -replace "`r", '') -split "`n"))
  $starts = [Collections.Generic.List[int]]::new()
  for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '^      -\s+') { $starts.Add($i) }
  }
  $result = [Collections.Generic.List[string]]::new()
  for ($index = 0; $index -lt $starts.Count; $index++) {
    $start = $starts[$index]
    $end = if ($index + 1 -lt $starts.Count) { $starts[$index + 1] } else { $lines.Count }
    $block = $lines[$start..($end - 1)] -join "`n"
    if ($block -match '(?m)^\s*uses:\s*actions/checkout@') { $result.Add($block) }
  }
  @($result)
}

function Assert-PatternCount([string]$Text, [string]$Pattern, [int]$Expected, [string]$Label) {
  $actual = [regex]::Matches($Text, $Pattern).Count
  if ($actual -ne $Expected) { throw "$Label 数量不正确；expected=$Expected actual=$actual" }
}

function Assert-LiteralCount([string]$Text, [string]$Literal, [int]$Expected, [string]$Label) {
  Assert-PatternCount $Text ([regex]::Escape($Literal)) $Expected $Label
}

function Assert-CheckoutStep([string]$Step, [hashtable]$Expected, [string]$Label) {
  foreach ($key in @('repository', 'ref', 'path', 'fetch-depth', 'persist-credentials')) {
    $literal = "          ${key}: $($Expected[$key])"
    Assert-LiteralCount $Step $literal 1 "$Label checkout $key"
    Assert-PatternCount $Step ("(?m)^\s{10}" + [regex]::Escape($key) + ':') 1 "$Label checkout $key 声明"
  }
}

function Assert-ControlTargetIsolation([string]$Contract, [string]$Publish) {
  $expectedJobs = @(
    @{ Document = $Contract; Name = 'gate'; Identity = 1; Local = 1; Remote = 0; RepositoryRoot = 2; Bundle = 1 },
    @{ Document = $Contract; Name = 'preflight'; Identity = 0; Local = 1; Remote = 1; RepositoryRoot = 1; Bundle = 2 },
    @{ Document = $Publish; Name = 'publish-release'; Identity = 0; Local = 1; Remote = 2; RepositoryRoot = 1; Bundle = 3 }
  )
  foreach ($expected in $expectedJobs) {
    $jobName = [string]$expected.Name
    $job = Get-RawJobBlock ([string]$expected.Document) $jobName
    $checkouts = @(Get-CheckoutStepBlocks $job)
    if ($checkouts.Count -ne 2) { throw "$jobName 必须恰有 control/target 两个 checkout" }
    Assert-CheckoutStep $checkouts[0] @{
      repository = '${{ job.workflow_repository }}'; ref = '${{ job.workflow_sha }}'; path = 'control'
      'fetch-depth' = '1'; 'persist-credentials' = 'false'
    } "$jobName control"
    Assert-CheckoutStep $checkouts[1] @{
      repository = '${{ github.repository }}'; ref = "refs/tags/`${{ inputs['target-tag'] }}"; path = 'target'
      'fetch-depth' = '0'; 'persist-credentials' = 'false'
    } "$jobName target"
    Assert-LiteralCount $job '        working-directory: target' 1 "$jobName target working-directory"
    foreach ($entry in @{
        CONTROL_CHECKER = '${{ github.workspace }}/control/scripts/check-github-release.ps1'
        TARGET_ROOT = '${{ github.workspace }}/target'
        BUNDLE_ROOT = '${{ github.workspace }}/target/build/release-contract/bundle'
        BUNDLE_NOTES = '${{ github.workspace }}/target/build/release-contract/bundle/release-notes.md'
        BUNDLE_MANIFEST = '${{ github.workspace }}/target/build/release-contract/bundle/manifest.json'
      }.GetEnumerator()) {
      Assert-LiteralCount $job ("      $($entry.Key): $($entry.Value)") 1 "$jobName 绝对路径 $($entry.Key)"
    }
    if ($jobName -ceq 'publish-release') {
      Assert-LiteralCount $job '      CONTROL_PUBLISHER: ${{ github.workspace }}/control/scripts/publish-github-release.ps1' 1 `
        'publish-release 绝对路径 CONTROL_PUBLISHER'
    }
    foreach ($mode in @('Identity', 'Local', 'Remote')) {
      if ($jobName -ceq 'publish-release' -and $mode -ceq 'Remote') {
        $actualRemoteCalls = [regex]::Matches($job, [regex]::Escape('pwsh -NoProfile -File $env:CONTROL_CHECKER -Remote')).Count +
          [regex]::Matches($job, [regex]::Escape('& $env:CONTROL_CHECKER -Remote')).Count
        if ($actualRemoteCalls -ne $expected[$mode]) {
          throw "$jobName control checker Remote 数量不正确；expected=$($expected[$mode]) actual=$actualRemoteCalls"
        }
      } else {
        Assert-LiteralCount $job "pwsh -NoProfile -File `$env:CONTROL_CHECKER -$mode" $expected[$mode] "$jobName control checker $mode"
      }
    }
    Assert-LiteralCount $job '-RepositoryRoot $env:TARGET_ROOT' $expected.RepositoryRoot "$jobName target RepositoryRoot"
    foreach ($parameter in @('AssetRoot', 'NotesPath', 'ManifestPath')) {
      $variable = if ($parameter -ceq 'AssetRoot') { 'BUNDLE_ROOT' } elseif ($parameter -ceq 'NotesPath') { 'BUNDLE_NOTES' } else { 'BUNDLE_MANIFEST' }
      $expectedCount = if ($jobName -ceq 'publish-release' -and $parameter -ceq 'ManifestPath') { 2 } else { $expected.Bundle }
      Assert-LiteralCount $job "-$parameter `$env:$variable" $expectedCount "$jobName target $parameter"
    }
    if ($jobName -ceq 'publish-release') {
      foreach ($literal in @('AssetRoot = $env:BUNDLE_ROOT', 'NotesPath = $env:BUNDLE_NOTES',
          'ManifestPath = $env:BUNDLE_MANIFEST')) {
        Assert-LiteralCount $job $literal 1 'final Published target bundle 参数'
      }
    }
  }

  Assert-LiteralCount $Contract 'check-github-release.ps1' 2 'read contract control checker 定义'
  Assert-LiteralCount $Publish 'check-github-release.ps1' 1 'publish wrapper control checker 定义'
  $combined = "$Contract`n$Publish"
  if ($combined -match '(?m)-RepositoryRoot\s+\.' -or
      $combined -match '(?i)(?:/control|\\control)[^\r\n]*(?:gradlew|build/libs|\.changelogs|check-scene-boundaries|check-doc-discipline)' -or
      $combined -match '(?i)TARGET_ROOT[^\r\n]*check-github-release\.ps1') {
    throw 'Release checker、RepositoryRoot 或业务构建来源跨越 control/target 边界'
  }

  $gate = Get-RawJobBlock $Contract 'gate'
  foreach ($literal in @(
      'chmod +x ./gradlew', './gradlew --no-configuration-cache --no-daemon test build',
      'pwsh -NoProfile -File "$env:TARGET_ROOT/scripts/check-scene-boundaries.ps1"',
      'pwsh -NoProfile -File "$env:TARGET_ROOT/scripts/check-doc-discipline.ps1"',
      'mkdir -p "${BUNDLE_ROOT}"',
      'cp "${TARGET_ROOT}/build/libs/qz_uilib-${TARGET_TAG}.jar" "${BUNDLE_ROOT}/qz_uilib-${TARGET_TAG}.jar"',
      'cp "${TARGET_ROOT}/build/libs/qz_uilib-${TARGET_TAG}-dev.jar" "${BUNDLE_ROOT}/qz_uilib-${TARGET_TAG}-dev.jar"',
      'cp "${TARGET_ROOT}/build/libs/qz_uilib-${TARGET_TAG}-sources.jar" "${BUNDLE_ROOT}/qz_uilib-${TARGET_TAG}-sources.jar"',
      'cp "${TARGET_ROOT}/build/libs/qz_uilib-${TARGET_TAG}-dev-preshadow.jar" "${BUNDLE_ROOT}/qz_uilib-${TARGET_TAG}-dev-preshadow.jar"',
      'cp "${TARGET_ROOT}/.changelogs/${TARGET_TAG}.md" "${BUNDLE_NOTES}"')) {
    Assert-LiteralCount $gate $literal 1 'gate target 业务来源'
  }
  foreach ($path in @(
      "            target/build/release-contract/bundle/qz_uilib-`${{ inputs['target-tag'] }}.jar",
      "            target/build/release-contract/bundle/qz_uilib-`${{ inputs['target-tag'] }}-dev.jar",
      "            target/build/release-contract/bundle/qz_uilib-`${{ inputs['target-tag'] }}-sources.jar",
      "            target/build/release-contract/bundle/qz_uilib-`${{ inputs['target-tag'] }}-dev-preshadow.jar",
      '            target/build/release-contract/bundle/release-notes.md',
      '            target/build/release-contract/bundle/manifest.json')) {
    Assert-LiteralCount $gate $path 1 'upload target bundle path'
  }
  Assert-LiteralCount $Contract '          path: target/build/release-contract/bundle' 1 'read contract download target bundle path'
  Assert-LiteralCount $Publish '          path: target/build/release-contract/bundle' 1 'publish wrapper download target bundle path'
  foreach ($suffix in @('.jar', '-dev.jar', '-sources.jar', '-dev-preshadow.jar')) {
    $assetPath = '"${BUNDLE_ROOT}/qz_uilib-${TARGET_TAG}' + $suffix + '"'
    Assert-LiteralCount $Contract $assetPath 1 'read contract staging target asset path'
  }
  Assert-LiteralCount $Publish '-AssetRoot $env:BUNDLE_ROOT -NotesPath $env:BUNDLE_NOTES' 1 'publisher 精确 bundle 参数'
}

function Assert-ReleaseSplitTopology([hashtable]$Documents) {
  $caller = [string]$Documents['release-tags.yml']
  $recovery = [string]$Documents['recover-4.6.2-release.yml']
  $contract = [string]$Documents['_github-release-contract.yml']
  $publish = [string]$Documents['_github-release-publish.yml']
  $contractJobs = Get-WorkflowJobs @(Get-YamlStructuralLines $contract)
  $publishJobs = Get-WorkflowJobs @(Get-YamlStructuralLines $publish)
  Assert-ExactJobNames $contractJobs @('gate', 'preflight') 'read contract'
  Assert-ExactJobNames $publishJobs @('verify', 'publish-release') 'publish wrapper'
  Assert-ReusableIdentityInputs $contract 'read contract'
  Assert-ReusableIdentityInputs $publish 'publish wrapper'

  if (@(Get-ContentsWriteLocations '_github-release-contract.yml' $contract).Count -ne 0) {
    throw 'read contract 必须零 contents:write'
  }
  foreach ($jobName in @('gate', 'preflight')) {
    $job = $contractJobs[$jobName]
    if ((Get-JobChildValue $job 'permissions' 'contents') -cne 'read') {
      throw "read contract/$jobName 必须显式 contents:read"
    }
  }
  $wrapperVerify = $publishJobs['verify']
  if ((Get-JobChildValue $wrapperVerify 'permissions' 'contents') -cne 'read') {
    throw 'publish wrapper/verify 必须显式 contents:read'
  }

  if ($contract -match '(?m)^concurrency:' -or $publish -match '(?m)^concurrency:') {
    throw 'reusable workflow 禁止声明 concurrency'
  }
  Assert-LiteralCount $caller '  group: qz-github-release-${{ github.ref_name }}' 1 'tag caller concurrency group'
  Assert-LiteralCount $recovery '  group: qz-github-release-4.6.2' 1 'recovery caller concurrency group'
  foreach ($document in @($caller, $recovery)) {
    Assert-LiteralCount $document '  cancel-in-progress: false' 1 'caller 非取消 concurrency'
  }

  Assert-LiteralCount $contract '        value: ${{ jobs.preflight.outputs.release_status }}' 1 'workflow release_status output'
  Assert-LiteralCount $contract '      release_status: ${{ steps.preflight.outputs.release_status }}' 1 'job release_status output'
  Assert-LiteralCount $contract '-ExpectedState $expectedState -GitHubOutput $env:GITHUB_OUTPUT' 1 'preflight output 生成'
  Assert-LiteralCount $publish '      RELEASE_STATUS: ${{ needs.verify.outputs.release_status }}' 1 'wrapper status 接线'
  Assert-LiteralCount $publish "        if: `${{ needs.verify.outputs.release_status == 'absent' }}" 3 'absent 写步骤条件'
  Assert-LiteralCount $publish '            absent|matching_published) ;;' 1 'release_status 白名单'

  Assert-LiteralCount $contract 'actions/upload-artifact@' 1 '本 run artifact 上传'
  Assert-LiteralCount $contract 'actions/download-artifact@' 1 'read preflight artifact 下载'
  Assert-LiteralCount $publish 'actions/upload-artifact@' 0 'wrapper 禁止复制 artifact 上传'
  Assert-LiteralCount $publish 'actions/download-artifact@' 1 'write publish artifact 下载'
  Assert-LiteralCount "$contract`n$publish" "          name: qz-github-release-`${{ inputs['target-tag'] }}-`${{ github.run_id }}" 3 '同 run artifact 名'
  foreach ($document in @($contract, $publish)) {
    if ($document -match '(?m)^\s{10}(?:run-id|github-token):' -or
        $document -match '(?ms)uses:\s*actions/download-artifact@.*?\n\s{10}repository:') {
      throw 'artifact 下载禁止跨 run/repository/token 参数'
    }
  }

  $nonContract = "$caller`n$recovery`n$publish"
  foreach ($literal in @('./gradlew --no-configuration-cache --no-daemon test build',
      'check-scene-boundaries.ps1', 'check-doc-discipline.ps1', 'Stage exact release bundle',
      'actions/upload-artifact@', '-ExpectedState Preflight')) {
    if ($nonContract.Contains($literal, [StringComparison]::Ordinal)) {
      throw "caller/publish wrapper 复制了 read contract gate/preflight 逻辑：$literal"
    }
  }
  Assert-LiteralCount "$contract`n$publish" 'gh release create' 0 'workflow 禁止 gh Release create'
  Assert-LiteralCount "$contract`n$publish" 'gh release edit' 0 'workflow 禁止 gh Release edit'
  Assert-LiteralCount $publish '$env:CONTROL_PUBLISHER -CreateDraft' 1 'wrapper Create API actuator'
  Assert-LiteralCount $publish '$env:CONTROL_PUBLISHER -PublishDraft' 1 'wrapper PATCH actuator'
  Assert-LiteralCount $publish '-ReleaseId $env:RELEASE_ID' 2 'Draft verify 与 PublishDraft 同 ID'
  Assert-LiteralCount $publish '$arguments.ReleaseId = [long]$env:RELEASE_ID' 1 'Published verify 同 ID'
  $localIndex = $publish.IndexOf('pwsh -NoProfile -File $env:CONTROL_CHECKER -Local', [StringComparison]::Ordinal)
  $whitelistIndex = $publish.IndexOf('absent|matching_published', [StringComparison]::Ordinal)
  $writeIndex = $publish.IndexOf('$env:CONTROL_PUBLISHER -CreateDraft', [StringComparison]::Ordinal)
  if ($localIndex -lt 0 -or $whitelistIndex -le $localIndex -or $writeIndex -le $whitelistIndex) {
    throw 'wrapper 必须在 Local 与 release_status 白名单通过后才写 Release'
  }

  Assert-ControlTargetIsolation $contract $publish
}

function Assert-RecoveryDefaultBranchGuard([string]$Recovery) {
  $confirmation = Get-RawJobBlock $Recovery 'confirmation'
  Assert-LiteralCount $confirmation '          DEFAULT_BRANCH_REF: refs/heads/${{ github.event.repository.default_branch }}' 1 'recovery 默认分支 ref 定义'
  Assert-LiteralCount $confirmation '          test "${GITHUB_REF}" = "${DEFAULT_BRANCH_REF}"' 1 'recovery 默认分支 ref guard'
  $guardIndex = $confirmation.IndexOf('test "${GITHUB_REF}" = "${DEFAULT_BRANCH_REF}"', [StringComparison]::Ordinal)
  $confirmationIndex = $confirmation.IndexOf('test "${CONFIRMATION}" = "${expected}"', [StringComparison]::Ordinal)
  if ($guardIndex -lt 0 -or $confirmationIndex -lt 0 -or $guardIndex -ge $confirmationIndex) {
    throw 'recovery 默认分支 ref guard 必须先于确认串校验'
  }
}

function Assert-ReleaseIdBindingStructure([hashtable]$Documents) {
  $combined = ($Documents.Values | ForEach-Object { [string]$_ }) -join "`n"
  if ($combined -match '(?i)gh\s+release\s+(?:create|edit)' -or
      $combined -match '(?i)\bStart-Sleep\b|\bsleep\s+[0-9]' -or
      $combined -match '(?i)untagged-|--clobber') {
    throw 'workflow 含 tag-based Release 写入、固定 sleep、untagged URL 或覆盖路径'
  }

  $recovery = [string]$Documents['recover-4.6.2-release.yml']
  Assert-LiteralCount $recovery '          - publish-existing-draft' 1 '固定 existing-draft mode'
  Assert-LiteralCount $recovery '      existing-draft-id: ''357902877''' 1 '固定 existing draft ID 参数'
  Assert-LiteralCount $recovery 'PUBLISH EXISTING DRAFT 4.6.2 357902877 6155c157b823c928accc25b037f7a95e7e83d669 e86a731cf10c5fa9e0f3dd87fe52126646bf8ed1' 1 `
    'existing draft 精确 confirmation'
  $dispatchHeader = $recovery.Substring(0, $recovery.IndexOf('permissions:', [StringComparison]::Ordinal))
  if ($dispatchHeader -match '(?i)(?:existing-)?draft-id\s*:') { throw 'recovery dispatch 禁止动态 draft ID 输入' }

  $contract = [string]$Documents['_github-release-contract.yml']
  Assert-LiteralCount $contract "'PublishedOnlyPreflight'" 1 'existing draft published-only preflight'
  $publish = [string]$Documents['_github-release-publish.yml']
  Assert-LiteralCount $publish "if: `${{ needs.verify.outputs.release_status == 'absent' && inputs['existing-draft-id'] == '' }}" 1 `
    'existing draft 路径禁止 Create'
  Assert-LiteralCount $publish '-GitHubOutput $env:GITHUB_OUTPUT' 1 'Create 响应 Release ID 输出'
  Assert-LiteralCount $publish 'CREATED_RELEASE_ID: ${{ steps.create.outputs.release_id }}' 1 'Create ID 绑定'
  Assert-LiteralCount $publish 'EXISTING_DRAFT_ID: ${{ inputs[''existing-draft-id''] }}' 1 'existing draft ID 绑定'
  Assert-LiteralCount $publish "RELEASE_ID: `${{ steps.release.outputs.release_id || inputs['existing-draft-id'] }}" 1 `
    'final Published 复验保持 existing ID'
  Assert-LiteralCount ([string]$Documents['release-tags.yml']) 'existing-draft-id' 0 '普通 tag caller 禁止 recovery ID'
  $createIndex = $publish.IndexOf('$env:CONTROL_PUBLISHER -CreateDraft', [StringComparison]::Ordinal)
  $draftVerifyIndex = $publish.IndexOf('-ManifestPath $env:BUNDLE_MANIFEST -ExpectedState Draft', [StringComparison]::Ordinal)
  $patchIndex = $publish.IndexOf('$env:CONTROL_PUBLISHER -PublishDraft', [StringComparison]::Ordinal)
  $finalIndex = $publish.IndexOf("ManifestPath = `$env:BUNDLE_MANIFEST; ExpectedState = 'Published'", [StringComparison]::Ordinal)
  if ($createIndex -lt 0 -or $draftVerifyIndex -le $createIndex -or $patchIndex -le $draftVerifyIndex -or
      $finalIndex -le $patchIndex) {
    throw 'Create→Draft ID 复验→PATCH→Published ID 复验顺序不正确'
  }

  $publisherPath = Join-Path $PSScriptRoot 'publish-github-release.ps1'
  if (-not (Test-Path -LiteralPath $publisherPath -PathType Leaf)) { throw '缺少 GitHub Release write actuator' }
  $publisher = [IO.File]::ReadAllText($publisherPath, [Text.Encoding]::UTF8)
  foreach ($forbidden in @('gh release create', 'gh release edit', 'Get-GitHubReleasesForTag',
      'untagged-', 'Start-Sleep', '--clobber', 'html_url')) {
    if ($publisher.Contains($forbidden, [StringComparison]::OrdinalIgnoreCase)) {
      throw "write actuator 含禁止结构：$forbidden"
    }
  }
  foreach ($required in @('Assert-CreateResponse', 'ReleaseId', 'upload_url',
      '[Net.Http.HttpMethod]::Post', '[Net.Http.HttpMethod]::Patch', 'Assert-UploadedAsset')) {
    if (-not $publisher.Contains($required, [StringComparison]::Ordinal)) {
      throw "write actuator 缺少 ID 绑定结构：$required"
    }
  }
  Assert-LiteralCount ([string]$Documents['build-and-test.yml']) `
    'pwsh -NoProfile -File scripts/publish-github-release.ps1 -SelfTest' 1 'CI actuator SelfTest'
}

function Assert-StaticDocuments([hashtable]$Documents) {
  foreach ($required in @('release-tags.yml', '_github-release-contract.yml', '_github-release-publish.yml',
      'recover-4.6.2-release.yml', 'jitpack-advisory.yml', 'build-and-test.yml')) {
    if (-not $Documents.ContainsKey($required)) { throw "缺少 workflow：$required" }
  }
  foreach ($entry in $Documents.GetEnumerator()) {
    $text = [string]$entry.Value
    if ($text -match '(?m)^\s*secrets:\s*inherit\s*$') { throw "$($entry.Key) 禁止 secrets: inherit" }
    if ($text -match '@master(?:\s|$)' -or $text -match '(?m)^\s*continue-on-error:') { throw "$($entry.Key) 含浮动 master 或 continue-on-error" }
    if ($text -match 'gh\s+release\s+delete' -or $text -match '--clobber') { throw "$($entry.Key) 含 Release 删除/覆盖命令" }
    if ($text -match '(?i)[*?][^\r\n]*\.jar|\.jar[^\r\n]*[*?]') { throw "$($entry.Key) 含 wildcard JAR 路径" }
    Assert-PinnedActions $entry.Key $text
  }
  Assert-WritePermissionStructure $Documents
  $caller = [string]$Documents['release-tags.yml']
  $contract = [string]$Documents['_github-release-contract.yml']
  $publish = [string]$Documents['_github-release-publish.yml']
  $recovery = [string]$Documents['recover-4.6.2-release.yml']
  $advisory = [string]$Documents['jitpack-advisory.yml']
  if ($caller -match '(?i)jitpack|maven' -or $recovery -match '(?i)jitpack|maven' -or
      $caller -match '(?i)uses:\s*[^.\r\n]+/\.github/workflows/[^\r\n]*release' -or
      $contract -match '(?i)jitpack|maven' -or $publish -match '(?i)jitpack|maven') {
    throw 'GitHub Release workflow 禁止依赖 JitPack/Maven/外部 release reusable'
  }
  if ($caller -notmatch 'uses:\s*\./\.github/workflows/_github-release-publish\.yml' -or
      $recovery -notmatch 'uses:\s*\./\.github/workflows/_github-release-contract\.yml' -or
      $recovery -notmatch 'uses:\s*\./\.github/workflows/_github-release-publish\.yml' -or
      $publish -notmatch 'uses:\s*\./\.github/workflows/_github-release-contract\.yml') {
    throw 'Release caller/read contract/publish wrapper 接线不正确'
  }
  if ($recovery -match 'github\.ref_name' -or $recovery -notmatch "target-tag:\s*'4\.6\.2'" -or
      $recovery -notmatch '6155c157b823c928accc25b037f7a95e7e83d669' -or
      $recovery -notmatch 'e86a731cf10c5fa9e0f3dd87fe52126646bf8ed1') { throw 'recovery ref/身份常量不正确' }
  if ($caller -notmatch 'qz-github-release-' -or $recovery -notmatch 'qz-github-release-' -or
      $advisory -notmatch 'qz-jitpack-advisory-' -or $advisory -notmatch 'cancel-in-progress:\s*false') {
    throw 'Release/JitPack 缺少独立非取消并发策略'
  }
  foreach ($name in @('release-tags.yml', 'recover-4.6.2-release.yml')) {
    $text = [string]$Documents[$name]
    $readLocations = @(Get-ContentsAccessLocations $name $text 'read')
    if ($readLocations -notcontains "$name/<top-level>") { throw "$name 未实现 caller 默认只读、调用 job 最小写权限" }
  }
  Assert-ReleaseSplitTopology $Documents
  Assert-RecoveryDefaultBranchGuard $recovery
  Assert-ReleaseIdBindingStructure $Documents
}

function Invoke-StaticCheck {
  $root = [IO.Path]::GetFullPath($WorkflowRoot)
  $documents = @{}
  foreach ($path in Get-ChildItem -LiteralPath $root -File -Filter '*.yml') {
    $documents[$path.Name] = [IO.File]::ReadAllText($path.FullName, [Text.Encoding]::UTF8)
  }
  Assert-StaticDocuments $documents
  [pscustomobject]@{ status = 'STATIC_RELEASE_WORKFLOWS_OK'; workflows = $documents.Count }
}

function New-TestJar([string]$Path, [string]$Marker) {
  $archive = [IO.Compression.ZipFile]::Open($Path, [IO.Compression.ZipArchiveMode]::Create)
  try {
    $entry = $archive.CreateEntry('marker.txt')
    $writer = [IO.StreamWriter]::new($entry.Open(), [Text.UTF8Encoding]::new($false))
    try { $writer.Write($Marker) } finally { $writer.Dispose() }
  } finally { $archive.Dispose() }
}

function New-BundleFixture([string]$Root, [string]$Case, [string]$Tag = '1.2.3') {
  $directory = Join-Path $Root $Case
  [IO.Directory]::CreateDirectory($directory) | Out-Null
  foreach ($name in Get-ExpectedAssetNames $Tag) { New-TestJar (Join-Path $directory $name) $name }
  $notes = Join-Path $directory 'release-notes.md'
  [IO.File]::WriteAllText($notes, "# Fixture $Tag`n", [Text.UTF8Encoding]::new($false))
  $manifest = New-ReleaseManifest $Tag ('a' * 40) ('b' * 40) $directory $notes
  [pscustomobject]@{ Root = $directory; Notes = $notes; Manifest = $manifest; Tag = $Tag }
}

function Assert-Throws([scriptblock]$Body, [string]$Label, [string]$MessagePattern = '') {
  $thrown = $false
  try { & $Body | Out-Null } catch {
    $thrown = $true
    if (-not [string]::IsNullOrWhiteSpace($MessagePattern) -and $_.Exception.Message -notmatch $MessagePattern) {
      throw "SelfTest 拒绝原因错误：$Label；actual=$($_.Exception.Message)"
    }
  }
  if (-not $thrown) { throw "SelfTest 未拒绝：$Label" }
}

function New-RemoteFixture($Fixture, [bool]$Draft = $false) {
  $bytes = @{}; $assets = @(); $id = 10
  foreach ($entry in $Fixture.Manifest.assets) {
    $content = [IO.File]::ReadAllBytes((Join-Path $Fixture.Root $entry.name))
    $bytes[[long]$id] = $content
    $assets += [pscustomobject]@{ id = $id; name = $entry.name; size = $content.Length }
    $id++
  }
  $release = [pscustomobject]@{
    id = 456
    tag_name = $Fixture.Tag; name = $Fixture.Tag; body = [IO.File]::ReadAllText($Fixture.Notes)
    draft = $Draft; prerelease = $false
    published_at = if ($Draft) { $null } else { '2026-07-22T00:00:00Z' }
    assets = $assets
  }
  [pscustomobject]@{ Release = $release; Bytes = $bytes }
}

function Get-GoodStaticDocuments {
  $contractFixturePath = Join-Path $PSScriptRoot '../.github/workflows/_github-release-contract.yml'
  if (-not (Test-Path -LiteralPath $contractFixturePath -PathType Leaf)) { throw 'SelfTest 缺少 Release contract 正例 fixture' }
  $contractFixture = [IO.File]::ReadAllText($contractFixturePath, [Text.Encoding]::UTF8)
  $publishFixturePath = Join-Path $PSScriptRoot '../.github/workflows/_github-release-publish.yml'
  if (-not (Test-Path -LiteralPath $publishFixturePath -PathType Leaf)) { throw 'SelfTest 缺少 Release publish wrapper 正例 fixture' }
  $publishFixture = [IO.File]::ReadAllText($publishFixturePath, [Text.Encoding]::UTF8)
  @{
    'release-tags.yml' = @"
permissions:
  contents: read
concurrency:
  group: qz-github-release-`${{ github.ref_name }}
  cancel-in-progress: false
jobs:
  identity:
    permissions:
      contents: read
  release:
    needs: identity
    permissions:
      contents: "write" # 合法尾注释
    uses: ./.github/workflows/_github-release-publish.yml
    with:
      target-tag: `${{ github.ref_name }}
"@
    'recover-4.6.2-release.yml' = @"
on:
  workflow_dispatch:
    inputs:
      mode:
        type: choice
        options:
          - verify-only
          - publish
          - publish-existing-draft
      confirmation:
        type: string
permissions:
  contents: read
concurrency:
  group: qz-github-release-4.6.2
  cancel-in-progress: false
jobs:
  confirmation:
    permissions:
      contents: read
    steps:
      - name: Require exact mode-specific confirmation
        shell: bash
        env:
          MODE: `${{ inputs.mode }}
          CONFIRMATION: `${{ inputs.confirmation }}
          DEFAULT_BRANCH_REF: refs/heads/`${{ github.event.repository.default_branch }}
        run: |
          test "`${GITHUB_REF}" = "`${DEFAULT_BRANCH_REF}"
          expected="VERIFY 4.6.2 6155c157b823c928accc25b037f7a95e7e83d669 e86a731cf10c5fa9e0f3dd87fe52126646bf8ed1"
          if [[ "`${MODE}" == publish ]]; then
            expected="PUBLISH 4.6.2 6155c157b823c928accc25b037f7a95e7e83d669 e86a731cf10c5fa9e0f3dd87fe52126646bf8ed1"
          fi
          if [[ "`${MODE}" == publish-existing-draft ]]; then
            expected="PUBLISH EXISTING DRAFT 4.6.2 357902877 6155c157b823c928accc25b037f7a95e7e83d669 e86a731cf10c5fa9e0f3dd87fe52126646bf8ed1"
          fi
          test "`${CONFIRMATION}" = "`${expected}"
  verify-only:
    if: `${{ inputs.mode == 'verify-only' }}
    needs: confirmation
    permissions:
      contents: read
    uses: ./.github/workflows/_github-release-contract.yml
    with:
      target-tag: '4.6.2'
      expected-tag-object: '6155c157b823c928accc25b037f7a95e7e83d669'
      expected-commit: 'e86a731cf10c5fa9e0f3dd87fe52126646bf8ed1'
  publish-existing-draft:
    if: `${{ inputs.mode == 'publish-existing-draft' }}
    needs: confirmation
    permissions:
      contents: write
    uses: ./.github/workflows/_github-release-publish.yml
    with:
      target-tag: '4.6.2'
      expected-tag-object: '6155c157b823c928accc25b037f7a95e7e83d669'
      expected-commit: 'e86a731cf10c5fa9e0f3dd87fe52126646bf8ed1'
      existing-draft-id: '357902877'
  publish:
    if: `${{ inputs.mode == 'publish' }}
    needs: confirmation
    permissions: { "contents": 'write' }
    uses: ./.github/workflows/_github-release-publish.yml
    with:
      target-tag: '4.6.2'
      expected-tag-object: '6155c157b823c928accc25b037f7a95e7e83d669'
      expected-commit: 'e86a731cf10c5fa9e0f3dd87fe52126646bf8ed1'
"@
    '_github-release-contract.yml' = $contractFixture
    '_github-release-publish.yml' = $publishFixture
    'jitpack-advisory.yml' = @"
permissions:
  contents: read
concurrency:
  group: qz-jitpack-advisory-tag
  cancel-in-progress: false
jobs:
  canonical-local:
    permissions:
      contents: read
"@
    'build-and-test.yml' = @"
permissions:
  contents: read
jobs:
  build:
    permissions:
      contents: read
    steps:
      - run: pwsh -NoProfile -File scripts/publish-github-release.ps1 -SelfTest
      - run: echo 'contents: write # shell string'
      - run: |
          echo 'permissions: {"contents":"write"}'
          echo 'permissions: >-'
          echo 'contents: |2+'
      - run: >-
          echo 'permissions: |+'
          echo 'contents: >2-'
"@
  }
}

function Set-WorkflowJobLiteralMutation([hashtable]$Documents, [string]$DocumentName, [string]$JobName,
    [string]$OldValue, [string]$NewValue, [string]$Label) {
  $document = [string]$Documents[$DocumentName]
  $normalized = $document -replace "`r", ''
  $job = Get-RawJobBlock $normalized $JobName
  $index = $job.IndexOf($OldValue, [StringComparison]::Ordinal)
  if ($index -lt 0) { throw "SelfTest fixture 未生效：$Label" }
  $mutatedJob = $job.Substring(0, $index) + $NewValue + $job.Substring($index + $OldValue.Length)
  $Documents[$DocumentName] = $normalized.Replace($job, $mutatedJob)
}

function Set-ContractJobLiteralMutation([hashtable]$Documents, [string]$JobName,
    [string]$OldValue, [string]$NewValue, [string]$Label) {
  Set-WorkflowJobLiteralMutation $Documents '_github-release-contract.yml' $JobName $OldValue $NewValue $Label
}

function Set-PublishJobLiteralMutation([hashtable]$Documents, [string]$JobName,
    [string]$OldValue, [string]$NewValue, [string]$Label) {
  Set-WorkflowJobLiteralMutation $Documents '_github-release-publish.yml' $JobName $OldValue $NewValue $Label
}

function Set-DocumentLiteralMutation([hashtable]$Documents, [string]$DocumentName,
    [string]$OldValue, [string]$NewValue, [string]$Label) {
  $document = [string]$Documents[$DocumentName]
  $index = $document.IndexOf($OldValue, [StringComparison]::Ordinal)
  if ($index -lt 0) { throw "SelfTest fixture 未生效：$Label" }
  $Documents[$DocumentName] = $document.Substring(0, $index) + $NewValue +
    $document.Substring($index + $OldValue.Length)
}

function Switch-ContractCheckoutOrder([hashtable]$Documents, [string]$JobName, [string]$Label) {
  $contract = [string]$Documents['_github-release-contract.yml']
  $job = Get-RawJobBlock $contract $JobName
  $checkouts = @(Get-CheckoutStepBlocks $job)
  if ($checkouts.Count -ne 2) { throw "SelfTest fixture 未生效：$Label" }
  $marker = "__QZ_CHECKOUT_SWAP_$([Guid]::NewGuid().ToString('N'))__"
  $mutatedJob = $job.Replace($checkouts[0], $marker).Replace($checkouts[1], $checkouts[0]).Replace($marker, $checkouts[1])
  $Documents['_github-release-contract.yml'] = $contract.Replace($job, $mutatedJob)
}

function Set-RecoveryLiteralMutation([hashtable]$Documents, [string]$OldValue,
    [string]$NewValue, [string]$Label) {
  $recovery = [string]$Documents['recover-4.6.2-release.yml']
  $index = $recovery.IndexOf($OldValue, [StringComparison]::Ordinal)
  if ($index -lt 0) { throw "SelfTest fixture 未生效：$Label" }
  $Documents['recover-4.6.2-release.yml'] =
    $recovery.Substring(0, $index) + $NewValue + $recovery.Substring($index + $OldValue.Length)
}

function Set-RecoveryDefaultBranchGuardOrderMutation([hashtable]$Documents, [string]$Label) {
  $recovery = [string]$Documents['recover-4.6.2-release.yml']
  $guard = '          test "${GITHUB_REF}" = "${DEFAULT_BRANCH_REF}"'
  $confirmation = '          test "${CONFIRMATION}" = "${expected}"'
  $guardCount = [regex]::Matches($recovery, [regex]::Escape($guard)).Count
  $confirmationCount = [regex]::Matches($recovery, [regex]::Escape($confirmation)).Count
  if ($guardCount -ne 1 -or $confirmationCount -ne 1) {
    throw "SelfTest fixture 未生效：$Label guard=$guardCount confirmation=$confirmationCount"
  }

  $guardIndex = $recovery.IndexOf($guard, [StringComparison]::Ordinal)
  $confirmationIndex = $recovery.IndexOf($confirmation, [StringComparison]::Ordinal)
  if ($guardIndex -ge $confirmationIndex) { throw "SelfTest fixture 初始顺序不正确：$Label" }

  $guardEnd = $guardIndex + $guard.Length
  $eol = if ($recovery.Substring($guardEnd).StartsWith("`r`n", [StringComparison]::Ordinal)) {
    "`r`n"
  } elseif ($recovery.Substring($guardEnd).StartsWith("`n", [StringComparison]::Ordinal)) {
    "`n"
  } else {
    throw "SelfTest fixture guard 行缺少换行：$Label"
  }
  $withoutGuard = $recovery.Substring(0, $guardIndex) + $recovery.Substring($guardEnd + $eol.Length)
  $confirmationIndex = $withoutGuard.IndexOf($confirmation, [StringComparison]::Ordinal)
  $confirmationEnd = $confirmationIndex + $confirmation.Length
  if (-not $withoutGuard.Substring($confirmationEnd).StartsWith($eol, [StringComparison]::Ordinal)) {
    throw "SelfTest fixture 换行不一致：$Label"
  }

  $mutated = $withoutGuard.Insert($confirmationEnd, $eol + $guard)
  $mutatedGuardCount = [regex]::Matches($mutated, [regex]::Escape($guard)).Count
  $mutatedConfirmationCount = [regex]::Matches($mutated, [regex]::Escape($confirmation)).Count
  $mutatedGuardIndex = $mutated.IndexOf($guard, [StringComparison]::Ordinal)
  $mutatedConfirmationIndex = $mutated.IndexOf($confirmation, [StringComparison]::Ordinal)
  if ($mutated -ceq $recovery -or $mutatedGuardCount -ne 1 -or $mutatedConfirmationCount -ne 1 -or
      $mutatedGuardIndex -le $mutatedConfirmationIndex) {
    throw "SelfTest fixture 未生效：$Label"
  }
  $mutatedWithoutGuard = $mutated.Substring(0, $mutatedGuardIndex) +
    $mutated.Substring($mutatedGuardIndex + $guard.Length + $eol.Length)
  if ($mutatedWithoutGuard -cne $withoutGuard) { throw "SelfTest fixture 修改了 guard 以外逻辑：$Label" }
  $Documents['recover-4.6.2-release.yml'] = $mutated
}

function Invoke-SelfTest {
  $parent = [IO.Path]::GetTempPath()
  if (-not (Test-Path -LiteralPath $parent -PathType Container)) { throw '系统临时目录不可用' }
  $root = Join-Path $parent "qz-github-release-$([Guid]::NewGuid().ToString('N'))"
  [IO.Directory]::CreateDirectory($root) | Out-Null
  try {
    $correct = New-BundleFixture $root 'correct'
    Assert-ManifestBinding $correct.Manifest $correct.Tag ('a' * 40) ('b' * 40) $correct.Root $correct.Notes
    Assert-Throws { Write-RemoteOutput 'unknown' } 'release_status 白名单' '^release_status 不在允许集合：unknown$'

    foreach ($case in @('missing', 'extra', 'empty', 'damaged', 'wrong-name', 'duplicate-hash')) {
      $fixture = New-BundleFixture $root $case
      switch ($case) {
        'missing' { [IO.File]::Delete((Join-Path $fixture.Root (Get-ExpectedAssetNames $fixture.Tag)[0])) }
        'extra' { New-TestJar (Join-Path $fixture.Root "qz_uilib-$($fixture.Tag)-extra.jar") 'extra' }
        'empty' { [IO.File]::WriteAllBytes((Join-Path $fixture.Root (Get-ExpectedAssetNames $fixture.Tag)[0]), @()) }
        'damaged' { [IO.File]::WriteAllText((Join-Path $fixture.Root (Get-ExpectedAssetNames $fixture.Tag)[0]), 'not zip') }
        'wrong-name' {
          $old = Join-Path $fixture.Root (Get-ExpectedAssetNames $fixture.Tag)[0]
          [IO.File]::Move($old, (Join-Path $fixture.Root "qz_uilib-$($fixture.Tag)-wrong.jar"))
        }
        'duplicate-hash' {
          [IO.File]::Copy((Join-Path $fixture.Root (Get-ExpectedAssetNames $fixture.Tag)[0]),
            (Join-Path $fixture.Root (Get-ExpectedAssetNames $fixture.Tag)[1]), $true)
        }
      }
      Assert-Throws { New-ReleaseManifest $fixture.Tag ('a' * 40) ('b' * 40) $fixture.Root $fixture.Notes } $case
    }

    foreach ($field in @('targetTag', 'tagObject', 'commit', 'notesSha256')) {
      $fixture = New-BundleFixture $root "wrong-$field"
      $fixture.Manifest.$field = if ($field -eq 'targetTag') { '9.9.9' } elseif ($field -eq 'notesSha256') { '0' * 64 } else { 'c' * 40 }
      Assert-Throws { Assert-ManifestBinding $fixture.Manifest $fixture.Tag ('a' * 40) ('b' * 40) $fixture.Root $fixture.Notes } "wrong $field"
    }

    $remote = New-RemoteFixture $correct
    Assert-RemoteRelease $remote.Release $correct.Manifest $correct.Tag $correct.Notes 'Published' 0 { param($id) $remote.Bytes[$id] }
    $draft = New-RemoteFixture $correct $true
    Assert-Throws { Assert-RemoteRelease $draft.Release $correct.Manifest $correct.Tag $correct.Notes 'Published' 0 { param($id) $draft.Bytes[$id] } } 'draft/published 不符'
    $draft.Release.draft = $false
    Assert-Throws { Assert-RemoteRelease $draft.Release $correct.Manifest $correct.Tag $correct.Notes 'Draft' 0 { param($id) $draft.Bytes[$id] } } 'published/draft 不符'
    foreach ($case in @('wrong-tag', 'wrong-notes', 'wrong-prerelease', 'asset-set', 'asset-size', 'asset-hash')) {
      $fixture = New-RemoteFixture $correct
      switch ($case) {
        'wrong-tag' { $fixture.Release.tag_name = '9.9.9' }
        'wrong-notes' { $fixture.Release.body = 'wrong' }
        'wrong-prerelease' { $fixture.Release.prerelease = $true }
        'asset-set' { $fixture.Release.assets = @($fixture.Release.assets | Select-Object -Skip 1) }
        'asset-size' { $fixture.Release.assets[0].size++ }
        'asset-hash' { $fixture.Bytes[[long]10] = [Text.Encoding]::UTF8.GetBytes('wrong') }
      }
      Assert-Throws { Assert-RemoteRelease $fixture.Release $correct.Manifest $correct.Tag $correct.Notes 'Published' 0 { param($id) $fixture.Bytes[$id] } } $case
    }

    Assert-Throws {
      Get-RemoteReleaseSelection 'Draft' 0 { throw 'list 不应执行' } { param($id) $draft.Release } {
        [pscustomobject]@{ Found = $false; Value = $null }
      }
    } 'Draft 缺少 ID' '^Draft 状态验证必须提供正整数 ReleaseId$'
    $draft.Release.draft = $true
    $idSelection = Get-RemoteReleaseSelection 'Draft' 456 { throw 'ID 路径禁止 list 再发现' } {
      param($id) if ($id -ne 456) { throw '错误 ID' }; $draft.Release
    } { throw 'ID 路径禁止 published-by-tag' }
    if ($idSelection.BoundId -ne 456 -or $idSelection.Release.id -ne 456) {
      throw 'SelfTest：list 空/不可见时未按 ID 绑定 draft'
    }
    Assert-Throws {
      Assert-RemoteRelease $idSelection.Release $correct.Manifest $correct.Tag $correct.Notes 'Draft' 999 {
        param($id) $draft.Bytes[$id]
      }
    } 'Release ID 漂移' '^远端 Release ID 漂移：'
    $published404 = Get-RemoteReleaseSelection 'PublishedOnlyPreflight' 0 { throw 'published-only 禁止 list' } {
      throw 'published-only 禁止 ID GET'
    } { [pscustomobject]@{ Found = $false; Value = $null } }
    if ($published404.Status -cne 'absent') { throw 'SelfTest：published-by-tag 对 draft 的 404 未分类为 absent' }

    $good = Get-GoodStaticDocuments
    Assert-StaticDocuments $good
    foreach ($fixture in @(
        [pscustomobject]@{ Label = 'LF'; Eol = "`n" },
        [pscustomobject]@{ Label = 'CRLF'; Eol = "`r`n" })) {
      $documents = Get-GoodStaticDocuments
      $documents['recover-4.6.2-release.yml'] = [regex]::Replace(
        [string]$documents['recover-4.6.2-release.yml'], "`r`n|`r|`n", $fixture.Eol)
      Set-RecoveryDefaultBranchGuardOrderMutation $documents "default-ref-guard-order-$($fixture.Label)"
      Assert-Throws { Assert-StaticDocuments $documents } "default-ref-guard-order $($fixture.Label)" `
        '^recovery 默认分支 ref guard 必须先于确认串校验$'
    }
    if ((Get-YamlScalarValue '"write#literal" # trailing comment') -cne 'write#literal') {
      throw 'SelfTest：YAML 引号内 # 被误识别为注释'
    }
    foreach ($danger in @('jitpack', 'external-release', 'continue', 'inherit', 'master', 'delete', 'tag-edit',
        'fixed-sleep', 'untagged', 'dynamic-recovery-id', 'clobber', 'wildcard', 'credentials',
        'recovery-ref', 'write', 'concurrency')) {
      $documents = Get-GoodStaticDocuments
      switch ($danger) {
        'jitpack' { $documents['release-tags.yml'] += "`n# jitpack" }
        'external-release' { $documents['release-tags.yml'] = $documents['release-tags.yml'] -replace
            'uses: \.\/\.github/workflows/_github-release-publish.yml', 'uses: owner/repo/.github/workflows/release.yml@aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' }
        'continue' { $documents['build-and-test.yml'] += "`n    continue-on-error: true" }
        'inherit' { $documents['build-and-test.yml'] += "`n    secrets: inherit" }
        'master' { $documents['build-and-test.yml'] += "`n    uses: owner/repo/x.yml@master" }
        'delete' { $documents['_github-release-contract.yml'] += "`n# gh release delete x" }
        'tag-edit' { $documents['_github-release-publish.yml'] += "`n# gh release edit 1.2.3" }
        'fixed-sleep' { $documents['_github-release-publish.yml'] += "`n# sleep 5" }
        'untagged' { $documents['_github-release-publish.yml'] += "`n# untagged-deadbeef" }
        'dynamic-recovery-id' {
          Set-RecoveryLiteralMutation $documents "      existing-draft-id: '357902877'" `
            '      existing-draft-id: ${{ inputs.confirmation }}' $danger
        }
        'clobber' { $documents['_github-release-contract.yml'] += "`n# --clobber" }
        'wildcard' { $documents['_github-release-contract.yml'] += "`n# build/libs/*.jar" }
        'credentials' { $documents['build-and-test.yml'] += "`njobs:`n  unsafe:`n    steps:`n      - uses: actions/checkout@fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09`n      - run: echo unsafe`n" }
        'recovery-ref' { $documents['recover-4.6.2-release.yml'] += "`n# github.ref_name" }
        'write' { $documents['release-tags.yml'] = $documents['release-tags.yml'] -replace
            '(?m)^permissions:\r?\n  contents: read\s*$', 'permissions: "write-all" # top-level quoted scalar' }
        'concurrency' { $documents['release-tags.yml'] = $documents['release-tags.yml'] -replace 'cancel-in-progress: false', 'cancel-in-progress: true' }
      }
      Assert-Throws { Assert-StaticDocuments $documents } "dangerous workflow $danger"
    }
    foreach ($danger in @('single-checkout', 'root-checkout', 'checkout-order', 'control-repository',
        'control-ref', 'control-fetch-depth', 'target-ref', 'target-path', 'target-fetch-depth', 'target-credentials',
        'working-directory', 'checker-source', 'repository-root', 'gradle-source', 'scene-source',
        'staging-asset-source', 'staging-notes-source', 'asset-root', 'notes-path', 'manifest-path',
        'upload-path', 'download-path', 'gh-asset-path', 'gh-notes-path', 'default-ref-guard', 'default-ref-guard-order')) {
      $documents = Get-GoodStaticDocuments
      switch ($danger) {
        'single-checkout' {
          Set-ContractJobLiteralMutation $documents 'gate' '        uses: actions/checkout@fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09' '        run: echo missing-control-checkout' $danger
        }
        'root-checkout' {
          Set-ContractJobLiteralMutation $documents 'gate' '          path: control' '          path: .' $danger
        }
        'checkout-order' { Switch-ContractCheckoutOrder $documents 'gate' $danger }
        'control-repository' {
          Set-ContractJobLiteralMutation $documents 'preflight' '          repository: ${{ job.workflow_repository }}' '          repository: ${{ github.repository }}' $danger
        }
        'control-ref' {
          Set-ContractJobLiteralMutation $documents 'preflight' '          ref: ${{ job.workflow_sha }}' '          ref: ${{ github.sha }}' $danger
        }
        'control-fetch-depth' {
          Set-ContractJobLiteralMutation $documents 'preflight' '          fetch-depth: 1' '          fetch-depth: 0' $danger
        }
        'target-ref' {
          Set-PublishJobLiteralMutation $documents 'publish-release' '          ref: refs/tags/${{ inputs[''target-tag''] }}' '          ref: ${{ github.ref }}' $danger
        }
        'target-path' {
          Set-PublishJobLiteralMutation $documents 'publish-release' '          path: target' '          path: .' $danger
        }
        'target-fetch-depth' {
          Set-PublishJobLiteralMutation $documents 'publish-release' '          fetch-depth: 0' '          fetch-depth: 1' $danger
        }
        'target-credentials' {
          Set-PublishJobLiteralMutation $documents 'publish-release' ("          fetch-depth: 0`n" +
            '          persist-credentials: false') ("          fetch-depth: 0`n" +
            '          persist-credentials: true') $danger
        }
        'working-directory' {
          Set-ContractJobLiteralMutation $documents 'gate' '        working-directory: target' '        working-directory: .' $danger
        }
        'checker-source' {
          Set-ContractJobLiteralMutation $documents 'gate' 'pwsh -NoProfile -File $env:CONTROL_CHECKER -Identity' 'pwsh -NoProfile -File "$env:TARGET_ROOT/scripts/check-github-release.ps1" -Identity' $danger
        }
        'repository-root' {
          Set-ContractJobLiteralMutation $documents 'gate' '-RepositoryRoot $env:TARGET_ROOT' '-RepositoryRoot .' $danger
        }
        'gradle-source' {
          Set-ContractJobLiteralMutation $documents 'gate' './gradlew --no-configuration-cache --no-daemon test build' '../control/gradlew --no-configuration-cache --no-daemon test build' $danger
        }
        'scene-source' {
          Set-ContractJobLiteralMutation $documents 'gate' '$env:TARGET_ROOT/scripts/check-scene-boundaries.ps1' '$env:CONTROL_ROOT/scripts/check-scene-boundaries.ps1' $danger
        }
        'staging-asset-source' {
          Set-ContractJobLiteralMutation $documents 'gate' '${TARGET_ROOT}/build/libs/qz_uilib-${TARGET_TAG}.jar' '${CONTROL_ROOT}/build/libs/qz_uilib-${TARGET_TAG}.jar' $danger
        }
        'staging-notes-source' {
          Set-ContractJobLiteralMutation $documents 'gate' '${TARGET_ROOT}/.changelogs/${TARGET_TAG}.md' '${CONTROL_ROOT}/.changelogs/${TARGET_TAG}.md' $danger
        }
        'asset-root' {
          Set-ContractJobLiteralMutation $documents 'preflight' '-AssetRoot $env:BUNDLE_ROOT' '-AssetRoot build/release-contract/bundle' $danger
        }
        'notes-path' {
          Set-ContractJobLiteralMutation $documents 'preflight' '-NotesPath $env:BUNDLE_NOTES' '-NotesPath build/release-contract/bundle/release-notes.md' $danger
        }
        'manifest-path' {
          Set-PublishJobLiteralMutation $documents 'publish-release' '-ManifestPath $env:BUNDLE_MANIFEST' '-ManifestPath build/release-contract/bundle/manifest.json' $danger
        }
        'upload-path' {
          Set-ContractJobLiteralMutation $documents 'gate' '            target/build/release-contract/bundle/manifest.json' '            build/release-contract/bundle/manifest.json' $danger
        }
        'download-path' {
          Set-ContractJobLiteralMutation $documents 'preflight' '          path: target/build/release-contract/bundle' '          path: build/release-contract/bundle' $danger
        }
        'gh-asset-path' {
          Set-PublishJobLiteralMutation $documents 'publish-release' `
            '-AssetRoot $env:BUNDLE_ROOT -NotesPath $env:BUNDLE_NOTES' `
            '-AssetRoot build/release-contract/bundle -NotesPath $env:BUNDLE_NOTES' $danger
        }
        'gh-notes-path' {
          Set-PublishJobLiteralMutation $documents 'publish-release' `
            '-AssetRoot $env:BUNDLE_ROOT -NotesPath $env:BUNDLE_NOTES' `
            '-AssetRoot $env:BUNDLE_ROOT -NotesPath build/release-contract/bundle/release-notes.md' $danger
        }
        'default-ref-guard' {
          Set-RecoveryLiteralMutation $documents '          test "${GITHUB_REF}" = "${DEFAULT_BRANCH_REF}"' '          echo no-default-ref-guard' $danger
        }
        'default-ref-guard-order' {
          Set-RecoveryDefaultBranchGuardOrderMutation $documents $danger
        }
      }
      $messagePattern = if ($danger -ceq 'default-ref-guard-order') {
        '^recovery 默认分支 ref guard 必须先于确认串校验$'
      } else { '' }
      Assert-Throws { Assert-StaticDocuments $documents } "control/target mutation $danger" $messagePattern
    }
    foreach ($danger in @('advisory-write', 'build-write', 'recovery-confirmation-write',
        'recovery-verify-only-write', 'tag-identity-write', 'tag-extra-job-write',
        'contract-gate-write', 'contract-preflight-write', 'contract-extra-job-write',
        'wrapper-verify-write', 'wrapper-extra-job-write')) {
      $documents = Get-GoodStaticDocuments
      switch ($danger) {
        'advisory-write' {
          $documents['jitpack-advisory.yml'] = $documents['jitpack-advisory.yml'] -replace
            '(?m)^(\s{6}contents:)\s*read\s*$', '$1 ''write'' # quoted scalar'
        }
        'build-write' {
          $documents['build-and-test.yml'] = $documents['build-and-test.yml'] -replace
            '(?m)^(\s{6}contents:)\s*read\s*$', '$1 "write"'
        }
        'recovery-confirmation-write' {
          $documents['recover-4.6.2-release.yml'] = $documents['recover-4.6.2-release.yml'] -replace
            '(?ms)(  confirmation:.*?contents:) read', '$1 ''write'''
        }
        'recovery-verify-only-write' {
          $documents['recover-4.6.2-release.yml'] = $documents['recover-4.6.2-release.yml'] -replace
            '(?ms)(  verify-only:.*?)(    permissions:\r?\n      contents: read)', '$1    permissions: { ''contents'': "write" }'
        }
        'tag-identity-write' {
          $documents['release-tags.yml'] = $documents['release-tags.yml'] -replace
            '(?ms)(  identity:.*?)(      contents:) read', '$1      "contents": ''write'''
        }
        'tag-extra-job-write' {
          $documents['release-tags.yml'] += "`n  extra:`n    permissions: {`"contents`":`"write`"}`n"
        }
        'contract-gate-write' {
          $documents['_github-release-contract.yml'] = $documents['_github-release-contract.yml'] -replace
            '(?ms)(  gate:.*?)(    permissions:\r?\n      contents: read)', '$1    permissions: ''write-all'''
        }
        'contract-preflight-write' {
          $documents['_github-release-contract.yml'] = $documents['_github-release-contract.yml'] -replace
            '(?ms)(  preflight:.*?)(    permissions:\r?\n      contents: read)', '$1    permissions: {contents: ''write''}'
        }
        'contract-extra-job-write' {
          $documents['_github-release-contract.yml'] += "`n  extra:`n    permissions: {'contents': `"write`"}`n"
        }
        'wrapper-verify-write' {
          $documents['_github-release-publish.yml'] = $documents['_github-release-publish.yml'] -replace
            '(?ms)(  verify:.*?)(    permissions:\r?\n      contents: read)', '$1    permissions: {contents: ''write''}'
        }
        'wrapper-extra-job-write' {
          $documents['_github-release-publish.yml'] += "`n  extra:`n    permissions: {'contents': `"write`"}`n"
        }
      }
      Assert-Throws { Assert-StaticDocuments $documents } "unauthorized contents write $danger"
    }
    $permissionBlockScalarCases = @(
      @{
        Label = 'unauthorized contents literal chomp'
        Key = 'contents'
        File = 'build-and-test.yml'
        Pattern = '(?m)^(\s{6}contents:)\s*read\s*$'
        Replacement = '${1} |-' + "`n        write"
      },
      @{
        Label = 'top-level permissions folded chomp'
        Key = 'permissions'
        File = 'release-tags.yml'
        Pattern = '(?m)^permissions:\r?\n  contents: read\s*$'
        Replacement = 'permissions: >-' + "`n  write-all"
      },
      @{
        Label = 'authorized tag release permissions literal indent chomp'
        Key = 'permissions'
        File = 'release-tags.yml'
        Pattern = '(?m)^    permissions:\r?\n      contents: "write" # 合法尾注释\s*$'
        Replacement = '    permissions: |2+' + "`n      write-all"
      },
      @{
        Label = 'authorized recovery publish permissions folded chomp indent'
        Key = 'permissions'
        File = 'recover-4.6.2-release.yml'
        Pattern = '(?m)^    permissions: \{ "contents": ''write'' \}\s*$'
        Replacement = '    permissions: >+2' + "`n      write-all"
      },
      @{
        Label = 'authorized publish wrapper permissions literal chomp indent'
        Key = 'permissions'
        File = '_github-release-publish.yml'
        Pattern = '(?m)^    permissions:\r?\n      contents: write\s*$'
        Replacement = '    permissions: |+2' + "`n      write-all"
      }
    )
    foreach ($case in $permissionBlockScalarCases) {
      $documents = Get-GoodStaticDocuments
      $original = [string]$documents[$case.File]
      $documents[$case.File] = $original -replace $case.Pattern, $case.Replacement
      if ($documents[$case.File] -ceq $original) { throw "SelfTest fixture 未生效：$($case.Label)" }
      $messagePattern = '^Static 权限声明禁止 block scalar：YAML 第 \d+ 行 key=' + [regex]::Escape($case.Key) + '$'
      Assert-Throws { Assert-StaticDocuments $documents } "permission block scalar $($case.Label)" $messagePattern
    }
    foreach ($danger in @('read-caller-write-graph', 'wrapper-copy-gate', 'workflow-output', 'job-output',
        'wrapper-output', 'status-whitelist', 'status-write-condition', 'duplicate-upload', 'missing-upload',
        'cross-run-download', 'cross-repository-download', 'artifact-name', 'contract-concurrency',
        'wrapper-concurrency', 'tag-concurrency', 'recovery-cancel', 'contract-extra-read-job',
        'wrapper-extra-read-job', 'contract-publish-input', 'draft-id-chain', 'asset-before-patch')) {
      $documents = Get-GoodStaticDocuments
      switch ($danger) {
        'read-caller-write-graph' {
          Set-WorkflowJobLiteralMutation $documents 'recover-4.6.2-release.yml' 'verify-only' `
            '    uses: ./.github/workflows/_github-release-contract.yml' `
            '    uses: ./.github/workflows/_github-release-publish.yml' $danger
        }
        'wrapper-copy-gate' {
          Set-PublishJobLiteralMutation $documents 'publish-release' 'Download current-run bundle' `
            'Download current-run bundle and ./gradlew --no-configuration-cache --no-daemon test build' $danger
        }
        'workflow-output' {
          Set-DocumentLiteralMutation $documents '_github-release-contract.yml' `
            '${{ jobs.preflight.outputs.release_status }}' '${{ jobs.gate.outputs.release_status }}' $danger
        }
        'job-output' {
          Set-DocumentLiteralMutation $documents '_github-release-contract.yml' `
            '${{ steps.preflight.outputs.release_status }}' '${{ steps.missing.outputs.release_status }}' $danger
        }
        'wrapper-output' {
          Set-PublishJobLiteralMutation $documents 'publish-release' `
            'RELEASE_STATUS: ${{ needs.verify.outputs.release_status }}' `
            'RELEASE_STATUS: ${{ needs.missing.outputs.release_status }}' $danger
        }
        'status-whitelist' {
          Set-PublishJobLiteralMutation $documents 'publish-release' 'absent|matching_published) ;;' `
            'absent|unknown) ;;' $danger
        }
        'status-write-condition' {
          Set-PublishJobLiteralMutation $documents 'publish-release' `
            "if: `${{ needs.verify.outputs.release_status == 'absent' }}" 'if: ${{ always() }}' $danger
        }
        'duplicate-upload' { $documents['_github-release-publish.yml'] += "`n# actions/upload-artifact@duplicated`n" }
        'missing-upload' {
          Set-ContractJobLiteralMutation $documents 'gate' 'actions/upload-artifact@' 'actions/not-upload-artifact@' $danger
        }
        'cross-run-download' {
          Set-PublishJobLiteralMutation $documents 'publish-release' `
            '          path: target/build/release-contract/bundle' `
            "          path: target/build/release-contract/bundle`n          run-id: 123" $danger
        }
        'cross-repository-download' {
          Set-ContractJobLiteralMutation $documents 'preflight' `
            '          path: target/build/release-contract/bundle' `
            "          path: target/build/release-contract/bundle`n          repository: owner/repo" $danger
        }
        'artifact-name' {
          Set-ContractJobLiteralMutation $documents 'gate' `
            "          name: qz-github-release-`${{ inputs['target-tag'] }}-`${{ github.run_id }}" `
            "          name: qz-github-release-`${{ inputs['target-tag'] }}-old-run" $danger
        }
        'contract-concurrency' {
          Set-DocumentLiteralMutation $documents '_github-release-contract.yml' 'jobs:' `
            "concurrency:`n  group: forbidden`n  cancel-in-progress: false`n`njobs:" $danger
        }
        'wrapper-concurrency' {
          Set-DocumentLiteralMutation $documents '_github-release-publish.yml' 'jobs:' `
            "concurrency:`n  group: forbidden`n  cancel-in-progress: false`n`njobs:" $danger
        }
        'tag-concurrency' {
          Set-DocumentLiteralMutation $documents 'release-tags.yml' `
            '  group: qz-github-release-${{ github.ref_name }}' '  group: qz-github-release-wrong' $danger
        }
        'recovery-cancel' {
          Set-DocumentLiteralMutation $documents 'recover-4.6.2-release.yml' `
            '  cancel-in-progress: false' '  cancel-in-progress: true' $danger
        }
        'contract-extra-read-job' {
          $documents['_github-release-contract.yml'] += "`n  extra:`n    permissions:`n      contents: read`n"
        }
        'wrapper-extra-read-job' {
          $documents['_github-release-publish.yml'] += "`n  extra:`n    permissions:`n      contents: read`n"
        }
        'contract-publish-input' {
          Set-DocumentLiteralMutation $documents '_github-release-contract.yml' '    outputs:' `
            "      publish:`n        required: true`n        type: boolean`n    outputs:" $danger
        }
        'draft-id-chain' {
          Set-PublishJobLiteralMutation $documents 'publish-release' '-ReleaseId $env:RELEASE_ID' `
            '-ReleaseId 999' $danger
        }
        'asset-before-patch' {
          Set-PublishJobLiteralMutation $documents 'publish-release' `
            '-ManifestPath $env:BUNDLE_MANIFEST -ExpectedState Draft' `
            '-ManifestPath $env:BUNDLE_MANIFEST -ExpectedState Published' $danger
        }
      }
      Assert-Throws { Assert-StaticDocuments $documents } "permission split mutation $danger"
    }
    [pscustomobject]@{
      status = 'SELF_TEST_OK'
      covered = @('four-assets', 'missing-extra-empty-damaged-wrong-name', 'unique-hash', 'tag-object-commit-notes',
        'draft-published-prerelease', 'release-id-required-and-drift', 'published-by-tag-draft-404',
        'list-invisible-id-get', 'remote-asset-set-size-hash', 'dangerous-workflow-structure',
        'control-target-checkout-and-path-mutations', 'default-branch-dispatch-mutations',
        'exact-contents-write-authorization', 'quoted-and-flow-permissions', 'permission-block-scalars',
        'yaml-comment-and-block-scalar-decoys', 'read-write-wrapper-routing', 'release-status-output-and-whitelist',
        'same-run-artifact-upload-download', 'fixed-recovery-id', 'tag-edit-sleep-and-untagged-negative',
        'asset-verify-before-patch', 'top-level-concurrency-only')
    }
  } finally {
    if (Test-Path -LiteralPath $root) { [IO.Directory]::Delete($root, $true) }
  }
}

if ($Identity) {
  Assert-IdentityContract $RepositoryRoot $TargetTag $ExpectedTagObject $ExpectedCommit | ForEach-Object {
    [pscustomobject]@{ status = 'IDENTITY_OK'; tagObject = $_.TagObject; commit = $_.Commit }
  }
} elseif ($Local) { Invoke-LocalContract }
elseif ($Remote) { Invoke-RemoteContract }
elseif ($Static) { Invoke-StaticCheck }
else { Invoke-SelfTest }
