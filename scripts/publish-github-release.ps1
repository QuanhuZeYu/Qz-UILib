#requires -Version 7.0
[CmdletBinding(DefaultParameterSetName = 'SelfTest')]
param(
  [Parameter(Mandatory = $true, ParameterSetName = 'CreateDraft')][switch]$CreateDraft,
  [Parameter(Mandatory = $true, ParameterSetName = 'PublishDraft')][switch]$PublishDraft,
  [Parameter(Mandatory = $true, ParameterSetName = 'SelfTest')][switch]$SelfTest,
  [Parameter(Mandatory = $true, ParameterSetName = 'CreateDraft')]
  [Parameter(Mandatory = $true, ParameterSetName = 'PublishDraft')][string]$Repository,
  [Parameter(Mandatory = $true, ParameterSetName = 'CreateDraft')]
  [Parameter(Mandatory = $true, ParameterSetName = 'PublishDraft')][string]$TargetTag,
  [Parameter(Mandatory = $true, ParameterSetName = 'CreateDraft')][string]$AssetRoot,
  [Parameter(Mandatory = $true, ParameterSetName = 'CreateDraft')][string]$NotesPath,
  [Parameter(Mandatory = $true, ParameterSetName = 'PublishDraft')][long]$ReleaseId,
  [Parameter(ParameterSetName = 'CreateDraft')][string]$GitHubOutput
)

$ErrorActionPreference = 'Stop'
$ExpectedAssetSuffixes = @('.jar', '-dev.jar', '-sources.jar', '-dev-preshadow.jar')

function Assert-Repository([string]$Value) {
  if ($Value -cnotmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') { throw 'Repository 必须是 owner/name' }
}

function Assert-SafeTag([string]$Value) {
  if ([string]::IsNullOrWhiteSpace($Value) -or $Value -cnotmatch '^[0-9A-Za-z][0-9A-Za-z._-]*$') {
    throw 'target tag 不是安全的单段标签名'
  }
}

function Assert-PositiveReleaseId([long]$Value) {
  if ($Value -le 0) { throw 'ReleaseId 必须是正整数' }
}

function Get-ExpectedAssetNames([string]$Tag) {
  @($ExpectedAssetSuffixes | ForEach-Object { "qz_uilib-$Tag$_" })
}

function Get-ExactAssets([string]$Root, [string]$Tag) {
  $fullRoot = [IO.Path]::GetFullPath($Root)
  if (-not (Test-Path -LiteralPath $fullRoot -PathType Container)) { throw 'AssetRoot 不存在' }
  $expected = @(Get-ExpectedAssetNames $Tag)
  $actual = @(Get-ChildItem -LiteralPath $fullRoot -File -Filter '*.jar' | ForEach-Object Name | Sort-Object)
  if (($actual -join "`n") -cne (($expected | Sort-Object) -join "`n")) {
    throw '待上传 JAR 资产集合不精确'
  }
  @($expected | ForEach-Object {
      $path = Join-Path $fullRoot $_
      if ((Get-Item -LiteralPath $path).Length -le 0) { throw "待上传资产为空：$_" }
      [pscustomobject]@{ Name = $_; Path = $path; Size = (Get-Item -LiteralPath $path).Length }
    })
}

function Get-ExpectedPrerelease([string]$Tag) {
  $Tag -match '-(beta|alpha|rc)([-.]?[0-9A-Za-z].*)?$'
}

function New-CreateBody([string]$Tag, [string]$Notes) {
  [ordered]@{
    tag_name = $Tag; name = $Tag; body = $Notes; draft = $true
    prerelease = (Get-ExpectedPrerelease $Tag)
  }
}

function Assert-CreateResponse($Release, [string]$Repo, [string]$Tag, [string]$Notes) {
  $id = [long]$Release.id
  Assert-PositiveReleaseId $id
  $expectedApiUrl = "https://api.github.com/repos/$Repo/releases/$id"
  $expectedUploadUrl = "https://uploads.github.com/repos/$Repo/releases/$id/assets{?name,label}"
  if ([string]$Release.url -cne $expectedApiUrl -or [string]$Release.upload_url -cne $expectedUploadUrl) {
    throw 'Create API 响应的 Release ID、url 与 upload_url 身份不一致'
  }
  if ([string]$Release.tag_name -cne $Tag -or [string]$Release.name -cne $Tag -or
      [bool]$Release.draft -ne $true -or [bool]$Release.prerelease -ne (Get-ExpectedPrerelease $Tag)) {
    throw 'Create API 响应的 tag/title/draft/prerelease 不匹配'
  }
  if ([string]$Release.body -cne $Notes) { throw 'Create API 响应的 Release body 不匹配' }
  $id
}

function Assert-PublishResponse($Release, [string]$Repo, [string]$Tag, [long]$Id) {
  Assert-PositiveReleaseId $Id
  if ([long]$Release.id -ne $Id -or [string]$Release.url -cne "https://api.github.com/repos/$Repo/releases/$Id" -or
      [string]$Release.tag_name -cne $Tag -or [string]$Release.name -cne $Tag) {
    throw 'Publish API 响应漂移到其他 Release 身份'
  }
  if ([bool]$Release.draft -ne $false -or [bool]$Release.prerelease -ne (Get-ExpectedPrerelease $Tag)) {
    throw 'Publish API 响应状态不匹配'
  }
}

function Get-UploadUri([string]$UploadTemplate, [string]$Name) {
  if ($UploadTemplate -cnotmatch '^https://uploads\.github\.com/repos/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/releases/[1-9][0-9]*/assets\{\?name,label\}$') {
    throw 'upload_url 不符合已验证的官方模板'
  }
  $base = $UploadTemplate.Substring(0, $UploadTemplate.IndexOf('{', [StringComparison]::Ordinal))
  "${base}?name=$([Uri]::EscapeDataString($Name))"
}

function Assert-UploadedAsset($Asset, $Expected) {
  if ([long]$Asset.id -le 0 -or [string]$Asset.name -cne [string]$Expected.Name -or
      [long]$Asset.size -ne [long]$Expected.Size -or [string]$Asset.state -cne 'uploaded') {
    throw "资产上传响应不匹配：$($Expected.Name)"
  }
}

function New-GitHubClient {
  if ([string]::IsNullOrWhiteSpace($env:GH_TOKEN)) { throw 'GH_TOKEN 不存在，无法执行 Release 写操作' }
  $client = [Net.Http.HttpClient]::new()
  $client.DefaultRequestHeaders.UserAgent.ParseAdd('Qz-UILib-release-publisher/1')
  $client.DefaultRequestHeaders.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $env:GH_TOKEN)
  $client.DefaultRequestHeaders.Accept.ParseAdd('application/vnd.github+json')
  $client.DefaultRequestHeaders.Add('X-GitHub-Api-Version', '2022-11-28')
  $client
}

function Send-GitHubJson([Net.Http.HttpClient]$Client, [Net.Http.HttpMethod]$Method,
    [string]$Uri, $Body) {
  $request = [Net.Http.HttpRequestMessage]::new($Method, $Uri)
  try {
    $json = $Body | ConvertTo-Json -Depth 10 -Compress
    $request.Content = [Net.Http.StringContent]::new($json, [Text.Encoding]::UTF8, 'application/json')
    $response = $Client.Send($request)
    try {
      $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
      if (-not $response.IsSuccessStatusCode) { throw "GitHub API HTTP $([int]$response.StatusCode)" }
      $text | ConvertFrom-Json -Depth 100
    } finally { $response.Dispose() }
  } finally { $request.Dispose() }
}

function Send-GitHubAsset([Net.Http.HttpClient]$Client, [string]$Uri, $Asset) {
  $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Post, $Uri)
  try {
    $request.Content = [Net.Http.ByteArrayContent]::new([IO.File]::ReadAllBytes($Asset.Path))
    $request.Content.Headers.ContentType = [Net.Http.Headers.MediaTypeHeaderValue]::new('application/java-archive')
    $response = $Client.Send($request)
    try {
      $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
      if (-not $response.IsSuccessStatusCode) { throw "GitHub asset upload HTTP $([int]$response.StatusCode)" }
      $text | ConvertFrom-Json -Depth 100
    } finally { $response.Dispose() }
  } finally { $request.Dispose() }
}

function Write-ReleaseId([long]$Id) {
  Assert-PositiveReleaseId $Id
  if ([string]::IsNullOrWhiteSpace($GitHubOutput)) { return }
  $parent = Split-Path -Parent ([IO.Path]::GetFullPath($GitHubOutput))
  if (-not (Test-Path -LiteralPath $parent -PathType Container)) { throw 'GITHUB_OUTPUT 父目录不存在' }
  [IO.File]::AppendAllText($GitHubOutput, "release_id=$Id`n", [Text.UTF8Encoding]::new($false))
}

function Invoke-CreateDraft {
  Assert-Repository $Repository; Assert-SafeTag $TargetTag
  $assets = @(Get-ExactAssets $AssetRoot $TargetTag)
  if (-not (Test-Path -LiteralPath $NotesPath -PathType Leaf) -or (Get-Item -LiteralPath $NotesPath).Length -le 0) {
    throw 'Release notes 缺失或为空'
  }
  $notes = [IO.File]::ReadAllText([IO.Path]::GetFullPath($NotesPath), [Text.Encoding]::UTF8)
  $client = New-GitHubClient
  try {
    $release = Send-GitHubJson $client ([Net.Http.HttpMethod]::Post) `
      "https://api.github.com/repos/$Repository/releases" (New-CreateBody $TargetTag $notes)
    $id = Assert-CreateResponse $release $Repository $TargetTag $notes
    foreach ($asset in $assets) {
      $uploaded = Send-GitHubAsset $client (Get-UploadUri ([string]$release.upload_url) $asset.Name) $asset
      Assert-UploadedAsset $uploaded $asset
    }
    Write-ReleaseId $id
    [pscustomobject]@{ status = 'DRAFT_CREATED_AND_ASSETS_UPLOADED'; release_id = $id }
  } finally { $client.Dispose() }
}

function Invoke-PublishDraft {
  Assert-Repository $Repository; Assert-SafeTag $TargetTag; Assert-PositiveReleaseId $ReleaseId
  $client = New-GitHubClient
  try {
    $release = Send-GitHubJson $client ([Net.Http.HttpMethod]::Patch) `
      "https://api.github.com/repos/$Repository/releases/$ReleaseId" ([ordered]@{ draft = $false })
    Assert-PublishResponse $release $Repository $TargetTag $ReleaseId
    [pscustomobject]@{ status = 'DRAFT_PUBLISHED'; release_id = $ReleaseId }
  } finally { $client.Dispose() }
}

function Assert-Throws([scriptblock]$Body, [string]$Label) {
  try { & $Body | Out-Null } catch { return }
  throw "SelfTest 未拒绝：$Label"
}

function Invoke-SelfTest {
  $repo = 'owner/repo'; $tag = '1.2.3'; $id = 123
  $notes = "# Notes`n"
  $create = [pscustomobject]@{
    id = $id; url = "https://api.github.com/repos/$repo/releases/$id"
    upload_url = "https://uploads.github.com/repos/$repo/releases/$id/assets{?name,label}"
    tag_name = $tag; name = $tag; body = $notes; draft = $true; prerelease = $false
  }
  $body = New-CreateBody $tag $notes
  if ($body.tag_name -cne $tag -or $body.name -cne $tag -or $body.body -cne $notes -or
      $body.draft -ne $true -or $body.prerelease -ne $false) { throw 'SelfTest：Create 请求体错误' }
  if ((New-CreateBody '1.2.3-rc1' $notes).prerelease -ne $true) { throw 'SelfTest：prerelease 请求体错误' }
  if ((Assert-CreateResponse $create $repo $tag $notes) -ne $id) { throw 'SelfTest：Create ID 未返回' }
  if ((Get-UploadUri $create.upload_url 'qz_uilib-1.2.3-dev.jar') -cne
      "https://uploads.github.com/repos/$repo/releases/$id/assets?name=qz_uilib-1.2.3-dev.jar") {
    throw 'SelfTest：upload URL 构造错误'
  }
  $asset = [pscustomobject]@{ Name = 'qz_uilib-1.2.3.jar'; Size = 42 }
  Assert-UploadedAsset ([pscustomobject]@{ id = 7; name = $asset.Name; size = 42; state = 'uploaded' }) $asset
  $published = [pscustomobject]@{
    id = $id; url = "https://api.github.com/repos/$repo/releases/$id"; tag_name = $tag; name = $tag
    draft = $false; prerelease = $false
  }
  Assert-PublishResponse $published $repo $tag $id

  foreach ($case in @('missing-id', 'id-url-drift', 'upload-url-drift', 'wrong-tag', 'wrong-draft',
      'wrong-prerelease', 'asset-id', 'asset-name', 'asset-size', 'asset-state', 'publish-id', 'publish-state')) {
    switch ($case) {
      'missing-id' { Assert-Throws { Assert-PositiveReleaseId 0 } $case }
      'id-url-drift' { $bad = $create.PSObject.Copy(); $bad.url = "https://api.github.com/repos/$repo/releases/999"; Assert-Throws { Assert-CreateResponse $bad $repo $tag $notes } $case }
      'upload-url-drift' { $bad = $create.PSObject.Copy(); $bad.upload_url = "https://uploads.github.com/repos/$repo/releases/999/assets{?name,label}"; Assert-Throws { Assert-CreateResponse $bad $repo $tag $notes } $case }
      'wrong-tag' { $bad = $create.PSObject.Copy(); $bad.tag_name = '9.9.9'; Assert-Throws { Assert-CreateResponse $bad $repo $tag $notes } $case }
      'wrong-draft' { $bad = $create.PSObject.Copy(); $bad.draft = $false; Assert-Throws { Assert-CreateResponse $bad $repo $tag $notes } $case }
      'wrong-prerelease' { $bad = $create.PSObject.Copy(); $bad.prerelease = $true; Assert-Throws { Assert-CreateResponse $bad $repo $tag $notes } $case }
      'asset-id' { Assert-Throws { Assert-UploadedAsset ([pscustomobject]@{ id = 0; name = $asset.Name; size = 42; state = 'uploaded' }) $asset } $case }
      'asset-name' { Assert-Throws { Assert-UploadedAsset ([pscustomobject]@{ id = 7; name = 'wrong'; size = 42; state = 'uploaded' }) $asset } $case }
      'asset-size' { Assert-Throws { Assert-UploadedAsset ([pscustomobject]@{ id = 7; name = $asset.Name; size = 41; state = 'uploaded' }) $asset } $case }
      'asset-state' { Assert-Throws { Assert-UploadedAsset ([pscustomobject]@{ id = 7; name = $asset.Name; size = 42; state = 'new' }) $asset } $case }
      'publish-id' { $bad = $published.PSObject.Copy(); $bad.id = 999; Assert-Throws { Assert-PublishResponse $bad $repo $tag $id } $case }
      'publish-state' { $bad = $published.PSObject.Copy(); $bad.draft = $true; Assert-Throws { Assert-PublishResponse $bad $repo $tag $id } $case }
    }
  }
  [pscustomobject]@{
    status = 'SELF_TEST_OK'
    covered = @('create-response-id-and-upload-url', 'exact-upload-response', 'publish-same-id',
      'missing-and-drifted-id', 'wrong-tag-state-prerelease', 'no-network')
  }
}

if ($CreateDraft) { Invoke-CreateDraft }
elseif ($PublishDraft) { Invoke-PublishDraft }
else { Invoke-SelfTest }
