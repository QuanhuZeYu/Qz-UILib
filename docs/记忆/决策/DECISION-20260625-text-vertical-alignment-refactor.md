# DECISION-20260625 文本垂直对齐重构到行业标准（half-leading 模型）

## 状态

方案已裁决，待实施。用户拍板"不惜代价重构到符合行业标准，允许大幅修改错误逻辑"。

## 背景

前期文本垂直对齐实现存在两类问题：
1. **模型缺失**：paint 层 `calculateTextTop` 算出 lineBoxTop 后直接当 content-area 顶喂渲染器，漏了 CSS2.1 §10.8.1 half-leading 这一步，文字贴行框顶部偏上。
2. **lineHeight 口径错**：`lineHeight = ceil(charSize*(1+lineSpacing))` 纯算式，与字体真实 ascent/descent 无关，是 16~32px 量级偏差的主因。

## 行业标准依据（librarian 调研，附权威来源）

### half-leading 模型（CSS2.1 §10.8.1 规范性定义）
- `L = lineHeight - (A + D)`（leading 总量，A=ascent D=descent）
- `halfLeading = L / 2`（上下各分一半）
- `contentAreaTop = lineBoxTop + halfLeading`
- `baseline = contentAreaTop + A`
- 来源：https://www.w3.org/TR/CSS2/visudet ；CSS Inline Layout 3 §5.3 https://www.w3.org/TR/css-inline-3/

### lineHeight 应来自字体 metrics
- 行业规范：`lineHeight = A + D + lineGap`（CSS `line-height: normal`）
- 来源：MDN https://developer.mozilla.org/en-US/docs/Web/CSS/line-height ；Google Fonts Metrics Guide https://googlefonts.github.io/gf-guide/metrics.html

### AWT LineMetrics 取值链路（已验证）
- `LineMetrics.getAscent/getDescent/getLeading` 间接来自 OpenType 字体表
- 取哪组取决于字体 fsSelection bit 7（USE_TYPO_METRICS）：置位→sTypo*，否则→hhea
- 来源：OpenJDK freetypeScaler.c 用 `face->ascender/descender/height`

### 浏览器/Chromium 实现
- Blink `InlineTextBoxPainter`：text origin = `box_top + ascent`（half-leading 模型）
- 来源：chromium ng_inline_box_state.cc / ng_physical_line_box_fragment.cc

## 本项目现状（Oracle 已验证，带行号）

- `ScenePaintEngine.calculateTextTop`：输出 `(innerHeight - lineHeight)/2`（lineBoxTop，漏 halfLeading）
- `TextLayoutService.getLineHeight`：`ceil(charSize*(1+lineSpacing))`（纯算式，与 A+D 无关）
- `SceneTextMeasurer` 已暴露 `ascent/descent`（atlas 像素经 awtCharSize=64 缩放），但 paint 层未调用
- `FontBatchRenderer` y 语义 = content-area 顶，baseline 由内部 `y + lineBaselineY*glyphScale` 算（封装正确，不动）
- `GlyphGenerator:66` 已能取 `LineMetrics.getLeading()`，但未存

## 修正后的模型

### 概念公式（half-leading + 字体驱动 lineHeight）
```
A            = measurer.ascent(fontSize)        // 字体上升量（UI 像素）
D            = measurer.descent(fontSize)       // 字体下降量（UI 像素）
lineGap      = measurer.lineGap(fontSize)       // 字体行隙（UI 像素）
lineHeight   = A + D + lineGap                  // 字体驱动行高（CSS line-height: normal）
L            = lineHeight - (A + D)             // 总 leading = lineGap
halfLeading  = L / 2                             // 半行距

// CENTER（默认）：
lineBoxTop      = paddingTop + max(0, (innerHeight - lineHeight)/2)
contentAreaTop  = lineBoxTop + halfLeading       // ← 当前漏的就是这一步
// baseline 由 FontBatchRenderer 内部算，paint 层只输出 contentAreaTop

// TOP：
contentAreaTop  = paddingTop + halfLeading       // 行框贴顶，但 content area 仍留 halfLeading

// BOTTOM：
lineBoxBottom   = paddingTop + innerHeight
contentAreaTop  = lineBoxBottom - lineHeight + halfLeading
```

### lineSpacing 已删除（用户拍板）
- 旧 `lineSpacing=0.1` 是 hack，lineHeight 不应靠用户调乘数，完全由字体度量驱动
- `FontConfig.lineSpacing` 字段、load/affectsFontRuntime/onConfigReload 引用全部清除
- `TextLayoutService.getLineHeight` 公式从 `(A+D)*(1+lineSpacing)+lineGap` 简化为 `A+D+lineGap`

### 不改的部分
- `FontBatchRenderer` y 语义（content-area 顶）——封装正确
- `TextVerticalAlign{TOP/CENTER/BOTTOM}` 枚举值与默认 CENTER
- `TextHorizontalAlign{LEFT/CENTER/RIGHT}` 枚举值与默认 LEFT
- `PaintCommand.text` 契约

## 实施路径

### P1：补 half-leading（paint 层接线 ascent/descent）
- `ScenePaintEngine.calculateTextTop`：调 `measurer.ascent/descent`，输出 `lineBoxTop + halfLeading`
- TOP/BOTTOM 同步补 halfLeading 口径
- 风险：低（纯 paint 算式）
- 不变量：I4/I6 守住（PAINT 级，scene 核心端口）

### P2：lineHeight 字体驱动 + lineGap 暴露
- `GlyphGenerator` 取 `lineMetrics.getLeading()`
- `GlyphInfo` 加 leading 字段
- `GlyphRuntimeTables` 加 leadingNormal/leadingBold 标量 + getter + reset 清零
- `GlyphPageManager.cacheGlyphGeometry/cacheFontMetrics` 写入
- `TextLayoutService`：新增 `getLineGap(fontSizePx)`，改 `getLineHeight` 为 `(A+D)*(1+lineSpacing)+lineGap`
- `SceneTextMeasurer` 加 `default int lineGap(int fontSizePx)`
- `TextMeasureService` 加 default
- 测试替身 FixedTextMeasurer/CountingTextMeasurer 加 lineGap
- 风险：中（改变所有文本垂直位置 + 多行行距 + 触发文本节点重布局，合法 LAYOUT 失效）
- 不变量：I4（lineHeight 影响 layout 测量高，合法 LAYOUT 级）、I7/I8（失效一次后正确复用）

### P3：lineSpacing 语义登记
- lineSpacing 从"乘在 charSize 上"改为"乘在 (A+D) 上"
- 视觉接近旧行为（因为 A+D ≈ charSize）
- 在本文档登记口径变更

## 验证策略

- P1/P2 各自编译通过 + scene/font 测试全绿
- 存量测试断言（textTop 期望值、lineHeight 期望值）需同步更新
- **真机验收必交用户**：垂直位置和行距是视觉结论，测试只能保证算式
- reviewer 独立审核

## 已知边界

- 多行文本（`\n`）未专门处理，lineHeight 改动会影响多行行距（合法）
- bold 字重 leading 标量已存但端口签名无 fontType 参数（预留）
- MC fallback 字体 A/D 可靠性未验证（P0/P1 只用 NORMAL）
