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
  [ValidateSet('Preflight', 'Draft', 'Published')][string]$ExpectedState,
  [Parameter(ParameterSetName = 'Remote')][string]$GitHubOutput,
  [Parameter(Mandatory = $true, ParameterSetName = 'Static')][string]$WorkflowRoot
)

$ErrorActionPreference = 'Stop'
$ExpectedAssetSuffixes = @('.jar', '-dev.jar', '-sources.jar', '-dev-preshadow.jar')

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
  $response = $Client.GetAsync($Uri).GetAwaiter().GetResult()
  try {
    $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) { throw "GitHub API HTTP $([int]$response.StatusCode)" }
    $text | ConvertFrom-Json -Depth 100
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
    [string]$State, [scriptblock]$Download) {
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
  if ([string]::IsNullOrWhiteSpace($GitHubOutput)) { return }
  $parent = Split-Path -Parent ([IO.Path]::GetFullPath($GitHubOutput))
  if (-not (Test-Path -LiteralPath $parent -PathType Container)) { throw 'GITHUB_OUTPUT 父目录不存在' }
  [IO.File]::AppendAllText($GitHubOutput, "release_status=$Status`n", [Text.UTF8Encoding]::new($false))
}

function Invoke-RemoteContract {
  Assert-SafeTag $TargetTag; Assert-Sha $ExpectedTagObject 'expected tag object'; Assert-Sha $ExpectedCommit 'expected commit'
  if ($Repository -cnotmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') { throw 'Repository 必须是 owner/name' }
  $manifest = [IO.File]::ReadAllText($ManifestPath, [Text.Encoding]::UTF8) | ConvertFrom-Json -Depth 20
  Assert-ManifestBinding $manifest $TargetTag $ExpectedTagObject $ExpectedCommit $AssetRoot $NotesPath
  $client = New-GitHubClient
  try {
    $releases = @(Get-GitHubReleasesForTag $client $Repository $TargetTag)
    if ($releases.Count -gt 1) { throw '同一 tag 存在多个 Release 记录' }
    if ($ExpectedState -ceq 'Preflight') {
      if ($releases.Count -eq 0) { Write-RemoteOutput 'absent'; return [pscustomobject]@{ status = 'REMOTE_RELEASE_ABSENT' } }
      if ([bool]$releases[0].draft) { throw '目标 tag 已存在 draft；禁止覆盖或续传' }
      Assert-RemoteRelease $releases[0] $manifest $TargetTag $NotesPath 'Published' {
        param($id) Get-GitHubAssetBytes $client $id $Repository
      }
      Write-RemoteOutput 'matching_published'
      return [pscustomobject]@{ status = 'REMOTE_MATCHING_PUBLISHED' }
    }
    if ($releases.Count -ne 1) { throw "远端缺少预期 $ExpectedState Release" }
    Assert-RemoteRelease $releases[0] $manifest $TargetTag $NotesPath $ExpectedState {
      param($id) Get-GitHubAssetBytes $client $id $Repository
    }
    [pscustomobject]@{ status = "REMOTE_$($ExpectedState.ToUpperInvariant())_OK" }
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
    if ($trimmed -match ':\s*[|>][+-]?\s*(?:#.*)?$') { $blockScalarIndent = $indent }
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
    '_github-release-contract.yml/publish-release')
  if ($actual.Count -ne $expected.Count -or
      (($actual | Sort-Object) -join "`n") -cne (($expected | Sort-Object) -join "`n")) {
    throw "contents:write 仅允许三个精确授权 job；actual=$($actual -join ',')"
  }

  $structures = @{}
  foreach ($name in @('release-tags.yml', 'recover-4.6.2-release.yml', '_github-release-contract.yml')) {
    $structures[$name] = Get-WorkflowJobs @(Get-YamlStructuralLines ([string]$Documents[$name]))
  }
  $tagRelease = $structures['release-tags.yml']['release']
  if ($null -eq $tagRelease -or
      (Get-DirectJobValue $tagRelease 'uses') -cne './.github/workflows/_github-release-contract.yml' -or
      (Get-DirectJobValue $tagRelease 'needs') -cne 'identity' -or
      $null -ne (Get-DirectJobValue $tagRelease 'if') -or
      (Get-JobChildValue $tagRelease 'with' 'publish') -cne 'true') {
    throw 'tag release 授权 job 的 needs/condition/uses/publish 结构不正确'
  }
  $recoveryPublish = $structures['recover-4.6.2-release.yml']['publish']
  $recoveryCondition = Get-DirectJobValue $recoveryPublish 'if'
  if ($null -eq $recoveryPublish -or
      (Get-DirectJobValue $recoveryPublish 'uses') -cne './.github/workflows/_github-release-contract.yml' -or
      (Get-DirectJobValue $recoveryPublish 'needs') -cne 'confirmation' -or
      $recoveryCondition -notmatch '^\$\{\{\s*inputs\.mode\s*==\s*''publish''\s*\}\}$' -or
      (Get-JobChildValue $recoveryPublish 'with' 'publish') -cne 'true') {
    throw 'recovery publish 授权 job 的 needs/condition/uses/publish 结构不正确'
  }
  $contractPublish = $structures['_github-release-contract.yml']['publish-release']
  $contractCondition = Get-DirectJobValue $contractPublish 'if'
  if ($null -eq $contractPublish -or $null -ne (Get-DirectJobValue $contractPublish 'uses') -or
      (Get-DirectJobValue $contractPublish 'needs') -cne 'gate' -or
      $contractCondition -notmatch '^\$\{\{\s*inputs\.publish\s*\}\}$' -or
      [string]::IsNullOrWhiteSpace((Get-DirectJobValue $contractPublish 'runs-on'))) {
    throw 'Reusable publish-release 授权 job 的 needs/condition/执行结构不正确'
  }
}

function Assert-StaticDocuments([hashtable]$Documents) {
  foreach ($required in @('release-tags.yml', '_github-release-contract.yml', 'recover-4.6.2-release.yml', 'jitpack-advisory.yml', 'build-and-test.yml')) {
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
  $recovery = [string]$Documents['recover-4.6.2-release.yml']
  $advisory = [string]$Documents['jitpack-advisory.yml']
  if ($caller -match '(?i)jitpack|maven' -or
      $caller -match '(?i)uses:\s*[^.\r\n]+/\.github/workflows/[^\r\n]*release' -or
      $contract -match '(?i)jitpack|maven') { throw 'GitHub Release workflow 禁止依赖 JitPack/Maven/外部 release reusable' }
  if ($caller -notmatch 'uses:\s*\./\.github/workflows/_github-release-contract\.yml' -or
      $recovery -notmatch 'uses:\s*\./\.github/workflows/_github-release-contract\.yml') { throw 'tag caller 与 recovery 必须复用本地 Release 合同' }
  if ($recovery -match 'github\.ref_name' -or $recovery -notmatch "target-tag:\s*'4\.6\.2'" -or
      $recovery -notmatch '6155c157b823c928accc25b037f7a95e7e83d669' -or
      $recovery -notmatch 'e86a731cf10c5fa9e0f3dd87fe52126646bf8ed1') { throw 'recovery ref/身份常量不正确' }
  if ($contract -notmatch 'qz-github-release-' -or $contract -notmatch 'cancel-in-progress:\s*false' -or
      $advisory -notmatch 'qz-jitpack-advisory-' -or $advisory -notmatch 'cancel-in-progress:\s*false') { throw 'Release/JitPack 缺少独立非取消并发策略' }
  foreach ($name in @('release-tags.yml', 'recover-4.6.2-release.yml')) {
    $text = [string]$Documents[$name]
    $readLocations = @(Get-ContentsAccessLocations $name $text 'read')
    if ($readLocations -notcontains "$name/<top-level>") { throw "$name 未实现 caller 默认只读、调用 job 最小写权限" }
  }
  $contractWrites = @(Get-ContentsWriteLocations '_github-release-contract.yml' $contract)
  if ($contractWrites.Count -ne 1 -or $contractWrites[0] -cne '_github-release-contract.yml/publish-release') {
    throw 'Reusable 合同必须仅 publish-release job 拥有 contents:write'
  }
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

function Assert-Throws([scriptblock]$Body, [string]$Label) {
  $thrown = $false
  try { & $Body | Out-Null } catch { $thrown = $true }
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
    tag_name = $Fixture.Tag; name = $Fixture.Tag; body = [IO.File]::ReadAllText($Fixture.Notes)
    draft = $Draft; prerelease = $false
    published_at = if ($Draft) { $null } else { '2026-07-22T00:00:00Z' }
    assets = $assets
  }
  [pscustomobject]@{ Release = $release; Bytes = $bytes }
}

function Get-GoodStaticDocuments {
  $checkout = 'actions/checkout@fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09'
  @{
    'release-tags.yml' = @"
permissions:
  contents: read
jobs:
  identity:
    permissions:
      contents: read
  release:
    needs: identity
    permissions:
      contents: "write" # 合法尾注释
    uses: ./.github/workflows/_github-release-contract.yml
    with:
      publish: true
"@
    'recover-4.6.2-release.yml' = @"
permissions:
  contents: read
jobs:
  confirmation:
    permissions:
      contents: read
  verify-only:
    if: `${{ inputs.mode == 'verify-only' }}
    needs: confirmation
    permissions:
      contents: read
    uses: ./.github/workflows/_github-release-contract.yml
    with:
      publish: false
  publish:
    if: `${{ inputs.mode == 'publish' }}
    needs: confirmation
    permissions: { "contents": 'write' }
    uses: ./.github/workflows/_github-release-contract.yml
    with:
      target-tag: '4.6.2'
      expected-tag-object: '6155c157b823c928accc25b037f7a95e7e83d669'
      expected-commit: 'e86a731cf10c5fa9e0f3dd87fe52126646bf8ed1'
      publish: true
"@
    '_github-release-contract.yml' = @"
concurrency:
  group: qz-github-release-tag
  cancel-in-progress: false
jobs:
  gate:
    permissions:
      contents: read
    steps:
      - uses: $checkout
        with:
          persist-credentials: false
  verify-only:
    if: `${{ !inputs.publish }}
    needs: gate
    runs-on: ubuntu-24.04
    permissions:
      contents: read
  publish-release:
    if: `${{ inputs.publish }}
    needs: gate
    runs-on: ubuntu-24.04
    permissions: {'contents': "write"}
"@
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
      - run: echo 'contents: write # shell string'
      - run: |
          echo 'permissions: {"contents":"write"}'
"@
  }
}

function Invoke-SelfTest {
  $parent = [IO.Path]::GetTempPath()
  if (-not (Test-Path -LiteralPath $parent -PathType Container)) { throw '系统临时目录不可用' }
  $root = Join-Path $parent "qz-github-release-$([Guid]::NewGuid().ToString('N'))"
  [IO.Directory]::CreateDirectory($root) | Out-Null
  try {
    $correct = New-BundleFixture $root 'correct'
    Assert-ManifestBinding $correct.Manifest $correct.Tag ('a' * 40) ('b' * 40) $correct.Root $correct.Notes

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
    Assert-RemoteRelease $remote.Release $correct.Manifest $correct.Tag $correct.Notes 'Published' { param($id) $remote.Bytes[$id] }
    $draft = New-RemoteFixture $correct $true
    Assert-Throws { Assert-RemoteRelease $draft.Release $correct.Manifest $correct.Tag $correct.Notes 'Published' { param($id) $draft.Bytes[$id] } } 'draft/published 不符'
    $draft.Release.draft = $false
    Assert-Throws { Assert-RemoteRelease $draft.Release $correct.Manifest $correct.Tag $correct.Notes 'Draft' { param($id) $draft.Bytes[$id] } } 'published/draft 不符'
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
      Assert-Throws { Assert-RemoteRelease $fixture.Release $correct.Manifest $correct.Tag $correct.Notes 'Published' { param($id) $fixture.Bytes[$id] } } $case
    }

    $good = Get-GoodStaticDocuments
    Assert-StaticDocuments $good
    if ((Get-YamlScalarValue '"write#literal" # trailing comment') -cne 'write#literal') {
      throw 'SelfTest：YAML 引号内 # 被误识别为注释'
    }
    foreach ($danger in @('jitpack', 'external-release', 'continue', 'inherit', 'master', 'delete', 'clobber', 'wildcard', 'credentials', 'recovery-ref', 'write', 'concurrency')) {
      $documents = Get-GoodStaticDocuments
      switch ($danger) {
        'jitpack' { $documents['release-tags.yml'] += "`n# jitpack" }
        'external-release' { $documents['release-tags.yml'] = $documents['release-tags.yml'] -replace
            'uses: \.\/\.github/workflows/_github-release-contract.yml', 'uses: owner/repo/.github/workflows/release.yml@aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' }
        'continue' { $documents['build-and-test.yml'] += "`n    continue-on-error: true" }
        'inherit' { $documents['build-and-test.yml'] += "`n    secrets: inherit" }
        'master' { $documents['build-and-test.yml'] += "`n    uses: owner/repo/x.yml@master" }
        'delete' { $documents['_github-release-contract.yml'] += "`n# gh release delete x" }
        'clobber' { $documents['_github-release-contract.yml'] += "`n# --clobber" }
        'wildcard' { $documents['_github-release-contract.yml'] += "`n# build/libs/*.jar" }
        'credentials' { $documents['build-and-test.yml'] += "`njobs:`n  unsafe:`n    steps:`n      - uses: actions/checkout@fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09`n      - run: echo unsafe`n" }
        'recovery-ref' { $documents['recover-4.6.2-release.yml'] += "`n# github.ref_name" }
        'write' { $documents['release-tags.yml'] = $documents['release-tags.yml'] -replace
            '(?m)^permissions:\r?\n  contents: read\s*$', 'permissions: "write-all" # top-level quoted scalar' }
        'concurrency' { $documents['_github-release-contract.yml'] = $documents['_github-release-contract.yml'] -replace 'cancel-in-progress: false', 'cancel-in-progress: true' }
      }
      Assert-Throws { Assert-StaticDocuments $documents } "dangerous workflow $danger"
    }
    foreach ($danger in @('advisory-write', 'build-write', 'recovery-confirmation-write',
        'recovery-verify-only-write', 'tag-identity-write', 'tag-extra-job-write',
        'contract-gate-write', 'contract-verify-only-write', 'contract-extra-job-write')) {
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
        'contract-verify-only-write' {
          $documents['_github-release-contract.yml'] = $documents['_github-release-contract.yml'] -replace
            '(?ms)(  verify-only:.*?)(    permissions:\r?\n      contents: read)', '$1    permissions: {contents: ''write''}'
        }
        'contract-extra-job-write' {
          $documents['_github-release-contract.yml'] += "`n  extra:`n    permissions: {'contents': `"write`"}`n"
        }
      }
      Assert-Throws { Assert-StaticDocuments $documents } "unauthorized contents write $danger"
    }
    [pscustomobject]@{
      status = 'SELF_TEST_OK'
      covered = @('four-assets', 'missing-extra-empty-damaged-wrong-name', 'unique-hash', 'tag-object-commit-notes',
        'draft-published-prerelease', 'remote-asset-set-size-hash', 'dangerous-workflow-structure',
        'exact-contents-write-authorization', 'quoted-and-flow-permissions', 'yaml-comment-and-block-scalar-decoys')
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
