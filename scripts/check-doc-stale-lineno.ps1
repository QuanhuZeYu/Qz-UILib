# scripts/check-doc-stale-lineno.ps1
# 文档行号引用门禁 — 扫指定目录下.md 文件中 "sourcefile.ext:NNN" 格式的悬空行号引用
# 控制论角色：传感层
# 依据：AGENTS.md §2.3；与 check-javadoc-stale-line-refs.ps1 职责互补、pattern 不重叠
# 退出码：命中即 1，否则 0

param(
    [string[]]$ScanDirs = @(
        "docs/开发者文档/specs",
        "docs/设定值层",
        "docs/传感层"
    )
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$violations = @()

# 匹配形如 SomeFile.java:123、path/to/File.kt:456 的文件行号引用
$rxFileLineRef = [regex] '(?<!\w)[\w./\\-]+\.(java|kt|groovy|md|ps1|py|ts|js):\d+'
# 排除 URL
$rxUrl = [regex] 'https?://'

foreach ($dir in $ScanDirs) {
    $absDir = Join-Path $root $dir
    if (-not (Test-Path $absDir)) { continue }

    Get-ChildItem $absDir -Filter *.md -Recurse -ErrorAction SilentlyContinue | ForEach-Object {
        $lines = Get-Content $_.FullName -ErrorAction SilentlyContinue
        if ($null -eq $lines) { return }
        $inFence = $false
        for ($i = 0; $i -lt $lines.Count; $i++) {
            $line = $lines[$i]
            if ($line -match '^\s*```') {
                $inFence = -not $inFence
                continue
            }
            if ($inFence) { continue }
            $cleaned = $rxUrl.Replace($line, '')
            foreach ($m in $rxFileLineRef.Matches($cleaned)) {
                $rel = $_.FullName.Substring($root.Length + 1) -replace '\\','/'
                $violations += "[doc行号引用] ${rel}:$($i + 1): $($m.Value)  <- $($line.Trim())"
            }
        }
    }
}

if ($violations.Count -gt 0) {
    Write-Host "文档行号引用门禁失败（悬空文件行号风险）：" -ForegroundColor Red
    $violations | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 1
}
Write-Host "文档行号引用门禁通过" -ForegroundColor Green
exit 0
