# scripts/check-javadoc-stale-line-refs.ps1
# Javadoc/注释悬空行号引用门禁 — 从根源阻断「Method():NNN」「ClassName:NNN」类悬空溯源
# 控制论角色：传感层自动传感器。把 Javadoc 行号引用这类「随代码变化必然悬空」的写法
#   从「评审纪律劝说」升级为「机械门禁阻断」。
# 立此脚本的依据：AGENTS.md §4.3 上溯规则——Javadoc 行号引用出现第 2 次累积
#   （2026-07 C4/C5/C6 第一批 + 阶段 E.2 第二批），已上溯至传感层立机械门禁。
#   选项 B（传感层门禁）经 oracle 评估优于 A（不动 NORTH_STAR——I1-I12 是运行时架构约束、
#   维护卫生问题不混入宪章）与 C（不选仅通则——§4.3 要求上两层都不可行才退回通则，B 可行）。
# 零外部依赖：纯 PowerShell + 正则，不引入 jar / Gradle 任务。
#
# 检测策略：
#   1) 仅对注释/Javadoc 文本跑正则——先扫描源文件，识别 /* */、/** */、// 三类注释区，
#      命中行才执行正则匹配，避免误命中代码内 URL 端口、版本号、枚举名后跟数字等。
#   2) 正则模式：
#      - 模式1（方法名行号）：[a-z_$][\w$]*\(\):\d+  例如 Config.saveAndReload():64
#      - 模式2（类名/大写 token 长行号，防端口误报）：[A-Z]\w+:\d{3,}  例如 ConfigTemplateSyncManager:577
#        （模式2 顺带捕获 computeHeight:266 这类方法名尾部含大写字母的悬空引用）
#   3) 排除：https?:// URL 内部、mail 形式 `user@host`、版本号 `:N.N.N`（被 \d{3,} 自然排除，
#      因连续数字不足 3 位）。
# 报错格式参考既有门禁：file:line + 命中字符串 + 违规摘要。
# 退出码：命中即 1，否则 0（与 check-scene-boundaries.ps1 / check-doc-discipline.ps1 一致）。
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$violations = @()

# 收集扫描文件：src/main/java/**/*.java + src/test/**/*.java
$scanRoots = @(
  (Join-Path $root "src/main/java"),
  (Join-Path $root "src/test")
)
$files = @()
foreach ($r in $scanRoots) {
  if (Test-Path $r) {
    $files += Get-ChildItem $r -Filter *.java -Recurse -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
  }
}

# 正则：方法名带括号行号引用 / 大写 token 跟 3+ 位数行号引用
$rxMethod = [regex] '[a-z_$][\w$]*\(\):\d+'
$rxClass  = [regex] '[A-Z]\w+:\d{3,}'
$rxUrl    = [regex] 'https?://'
$rxMail   = [regex] '\b[\w.+-]+@[\w.-]+\.\w+'

foreach ($f in $files) {
  $lines = Get-Content $f -ErrorAction SilentlyContinue
  if ($null -eq $lines) { continue }
  $inBlock = $false
  for ($i = 0; $i -lt $lines.Count; $i++) {
    $line = $lines[$i]

    # 进入 /* 或 /** 块
    $blockStartIdx = -1
    if ($line -match '/\*') { $blockStartIdx = $line.IndexOf('/*') }
    $lineCommentIdx = -1
    if ($line -match '//') { $lineCommentIdx = $line.IndexOf('//') }

    # 判断本行是否含注释文本：
    # - 若处于块注释中（inBlock），整行均参与匹配
    # - 若本行开启新块注释，自 /* 起参与匹配
    # - 若本行含 // 单行注释，自 // 起参与匹配
    # - // 与 /* 同时存在时取先发生者
    $isComment = $inBlock
    if (-not $isComment -and $blockStartIdx -ge 0) { $isComment = $true }
    if (-not $isComment -and $lineCommentIdx -ge 0 -and -not $inBlock) { $isComment = $true }

    if ($line -match '\*/') {
      # 块结束稍后翻标志（先做本行匹配再翻）
      $endBlockAfter = $true
    } else {
      $endBlockAfter = $false
    }

    if ($isComment) {
      # 截取本行的注释片段（避免误命中代码内 URL/版本号）
      $comment = ''
      if ($inBlock) {
        $comment = $line
      } elseif ($blockStartIdx -ge 0 -and ($lineCommentIdx -lt 0 -or $blockStartIdx -le $lineCommentIdx)) {
        $comment = $line.Substring($blockStartIdx)
      } elseif ($lineCommentIdx -ge 0) {
        $comment = $line.Substring($lineCommentIdx)
      } else {
        $comment = $line
      }

      # 先在注释片段内剔除 URL / mail 内部，再跑检测正则，杜绝 https://host:8080 误报
      $cleaned = $rxUrl.Replace($comment, '')
      $cleaned = $rxMail.Replace($cleaned, '')

      $lineNo = $i + 1
      foreach ($m in $rxMethod.Matches($cleaned)) {
        $rel = $f.Substring($root.Length + 1) -replace '\\','/'
        $violations += ("[行号引用] ${rel}:${lineNo}: $($m.Value)  <-$($line.Trim())")
      }
      foreach ($m in $rxClass.Matches($cleaned)) {
        $rel = $f.Substring($root.Length + 1) -replace '\\','/'
        $violations += ("[行号引用] ${rel}:${lineNo}: $($m.Value)  <-$($line.Trim())")
      }
    }

    # 块注释状态翻转：本行有 /* 开启、有 */ 结束
    if ($blockStartIdx -ge 0) { $inBlock = $true }
    if ($endBlockAfter) {
      $closeIdx = $line.IndexOf('*/')
      # 若 /* 与 */ 在同行（如 /* short */），开闭同时——保持块外状态
      if ($closeIdx -ge 0 -and $blockStartIdx -ge 0 -and $closeIdx -gt $blockStartIdx) {
        $inBlock = $false
      } elseif ($closeIdx -ge 0) {
        $inBlock = $false
      }
    }
  }
}

if ($violations.Count -gt 0) {
  Write-Host "Javadoc 行号引用门禁失败（悬空溯源风险）：" -ForegroundColor Red
  $violations | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
  exit 1
}
Write-Host "Javadoc 行号引用门禁通过" -ForegroundColor Green
exit 0