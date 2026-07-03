# scripts/check-scene-boundaries.ps1
# scene 结构边界门禁 — 替代 2026-07 移除的 ArchUnit（测试体系约定.md:86-97）
# 控制论角色：传感层自动传感器，把 I1-I12/R1-R12 等结构约束从人工评审拉回自动闭环
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
# 断言3-5 form/overlay 铁律
Check "form"    '(club\.heiqi\.config|org\.lwjgl|net\.minecraft|club\.heiqi\.uilib\.gl)\.' "form-dep"
Check "overlay" '(org\.lwjgl|net\.minecraft)' "overlay-platform"

if ($violations.Count -gt 0) {
  Write-Host "scene 边界门禁失败：" -ForegroundColor Red
  $violations | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
  exit 1
}
Write-Host "scene 边界门禁通过" -ForegroundColor Green
exit 0
