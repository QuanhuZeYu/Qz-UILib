# scripts/check-scene-boundaries.ps1
# scene 结构边界门禁 — 替代 2026-07 移除的 ArchUnit（测试体系约定.md:86-97）
# 控制论角色：传感层自动传感器，检查可由 import/owner 模式机械识别的结构子集
# 零外部依赖：纯 PowerShell + Select-String
$ErrorActionPreference = "Stop"
$base = "src/main/java/club/heiqi/uilib/ui/scene"
$violations = @()

function Check($subdir, $pattern, $label) {
  $dir = Join-Path $base $subdir
  if (-not (Test-Path $dir)) { return }
  Get-ChildItem $dir -Filter *.java -Recurse | ForEach-Object {
    Select-String -Path $_.FullName -Pattern "^\s*import\s+$pattern" | ForEach-Object {
      $script:violations += "[$label] $($_.Path):$($_.LineNumber): $($_.Line.Trim())"
    }
  }
}

# 断言1 L2边界（layout禁runtime/input/paint/reactive；scene.node/scene.text合规不误杀）
Check "layout" 'club\.heiqi\.uilib\.ui\.(scene\.(runtime|input|paint)|reactive)\.' "L2-layout"
# 断言2 I10 平台契约（input禁lwjgl/minecraft）
Check "input"   '(org\.lwjgl|net\.minecraft)' "I10-input"
# 断言3 form零config依赖
Check "form"    'club\.heiqi\.config\.' "form-no-config"
# 断言4 form零MC/Forge/GL
Check "form"    '(org\.lwjgl|net\.minecraft|club\.heiqi\.uilib\.gl)\.' "form-no-platform"
# 断言5 overlay零平台依赖
Check "overlay" '(org\.lwjgl|net\.minecraft)' "overlay-platform"

# 断言6 McScreenBridge 是文本桥唯一 owner，所有子类禁止持有或注册第二个 listener。
Get-ChildItem "src/main/java" -Filter *.java -Recurse | ForEach-Object {
  $source = Get-Content -Raw -Path $_.FullName
  if ($source -match '\bextends\s+McScreenBridge\b' -and
      $source -match '\b(SceneLwjgl3ifyTextBridge|registerTextInputListener)\b') {
    $script:violations += "[mc-screen-text-owner] $($_.FullName): McScreenBridge 子类不得持有或注册文本桥"
  }
}

# 断言7 I13 机械子集：UILib-owned HUD/client scene host 禁止读取 Minecraft scaled 坐标。
$hudListenerPath = "src/main/java/club/heiqi/uilib/client/UiHudRenderListener.java"
$hudDirectory = "src/main/java/club/heiqi/uilib/client/hud"
$hudOwnedFiles = @()
@($hudListenerPath, $hudDirectory) | ForEach-Object {
  if (Test-Path $_ -PathType Container) { $files = Get-ChildItem $_ -Filter *.java -Recurse }
  else { $files = Get-Item $_ }
  $hudOwnedFiles += $files
  $files | Select-String -Pattern '\b(Scaled[_-]?Resolution|gui[_-]?Scale|get[_-]?Scale[_-]?Factor|get[_-]?Scaled[_-]?(Width|Height))\b' | ForEach-Object {
    $script:violations += "[I13-hud-framebuffer] $($_.Path):$($_.LineNumber): $($_.Line.Trim())"
  }
}

# 断言8 I13 正向边界：HUD viewport 的生产调用唯一且只消费 Minecraft display framebuffer 尺寸。
$listenerSource = Get-Content -Raw -Path $hudListenerPath
$viewportCalls = [regex]::Matches($listenerSource, 'FramebufferViewportFactory\s*\.\s*create\s*\([^;]*?\)', 'Singleline')
$displayViewportPattern = 'FramebufferViewportFactory\s*\.\s*create\s*\(\s*minecraft\s*\.\s*displayWidth\s*,\s*minecraft\s*\.\s*displayHeight\s*\)'
if ($viewportCalls.Count -ne 1 -or $viewportCalls[0].Value -notmatch $displayViewportPattern) {
  $violations += "[I13-hud-viewport-source] ${hudListenerPath}: HUD viewport 必须唯一从 minecraft.displayWidth/displayHeight 构造"
}

$viewportFactoryPath = Join-Path $hudDirectory "FramebufferViewportFactory.java"
$directViewportOwners = $hudOwnedFiles | Where-Object { $_.FullName -ne (Get-Item $viewportFactoryPath).FullName } |
  Select-String -Pattern '\bnew\s+HudViewportMetrics\s*\('
$directViewportOwners | ForEach-Object {
  $violations += "[I13-hud-viewport-owner] $($_.Path):$($_.LineNumber): HudViewportMetrics 只能由 FramebufferViewportFactory 构造"
}
$viewportFactorySource = Get-Content -Raw -Path $viewportFactoryPath
if ($viewportFactorySource -notmatch 'new\s+HudViewportMetrics\s*\(\s*Math\.max\s*\(\s*1\s*,\s*displayWidth\s*\)\s*,\s*Math\.max\s*\(\s*1\s*,\s*displayHeight\s*\)\s*\)') {
  $violations += "[I13-hud-viewport-factory] ${viewportFactoryPath}: viewport 尺寸必须只由 displayWidth/displayHeight 归一化产生"
}

if ($violations.Count -gt 0) {
  Write-Host "scene 边界门禁失败：" -ForegroundColor Red
  $violations | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
  exit 1
}
Write-Host "scene 边界门禁通过" -ForegroundColor Green
exit 0
