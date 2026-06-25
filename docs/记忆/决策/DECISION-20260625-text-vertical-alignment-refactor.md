# DECISION-20260625 文本垂直对齐模型（em-box 居中）

## 状态

**em-box 居中模型已落地（待真机验收）。** 用户拍板放弃 half-leading、改用 em-box 居中。

## 背景

按钮文字真机视觉偏上。经源码全链路追查 + oracle 数学复核，定位根因是
**paint 层对齐模型与字体渲染器 y 锚点语义错配**，而非单纯的 half-leading 缺失。

## 渲染器 y 锚点真相（已验证，带行号）

- `FontBatchRenderer.resolveGlyphQuadMetrics`（`FontBatchRenderer.java:259`）：
  `baselineY = y + (lineBaselineY * glyphScale)`，glyphScale = `fontSize/64`。
- `lineBaselineY = round(64 - descent)`（`GlyphGenerator.java:72`），量纲 atlas 像素（64 坐标系），
  语义是"**字符格 em-box 顶到基线的距离**"。
- 烘焙 em = 64 == 渲染缩放分母 64（commit `cb786955` 修复并真机验收，见
  `docs/开发者文档/errors/ERROR-20260625-glyph-coordinate-system-mismatch.md`）。
- **结论**：传入的 y 等价于 em-box 顶（cell top），不是 CSS content-area 顶（baseline−ascent）。
  em-box 显示高 = `64 * glyphScale = fontSize`，全链路 1:1 透传（scene 文本不经 UI_TEXT_SCALE，
  见 `UiRenderContext.java:551-556` 的 `drawBaselineAlignedStringPx` return 分支）。

## 对齐模型：em-box 居中（最终采用）

```
emHeight = fontSize                       // em-box 显示高 == 字号
CENTER : textTop = paddingTop + (innerHeight - emHeight) / 2
TOP    : textTop = paddingTop
BOTTOM : textTop = paddingTop + (innerHeight - emHeight)
```

- 不再用 ascent/descent/lineHeight/halfLeading，measurer 无需新增度量。
- 不做下边界钳制：em-box 高于 innerHeight 时 CENTER/BOTTOM 允许向上溢出
  （CSS overflow:visible 合法行为），TOP 恒贴 paddingTop。
- 仅单行模型：不处理 `\n` 多行（见"已知边界"）。

## 为什么 em-box 居中比 half-leading 更对（oracle 复核）

1. **锚点一致**：渲染器吃的就是 em-box 顶，paint 层直接输出 em-box 顶，无坐标系错配。
2. **误差对冲**：em-box 内字形 ink 天然偏上（ascent 区 > descent 区）；相对旧 (A+D) 行框
   居中，em-box 居中会下移约 `leading/2`，恰好部分抵消 ink 偏上，净视觉更居中。
3. **行业标准**：等价于 CSS `line-height` 行框居中，是浏览器/Flutter 按钮文字垂直居中的
   主流做法。真正的视觉居中需 cap-height，但 AWT 无直接 API 且会破坏 CJK/拉丁基线一致性，
   得不偿失（YAGNI）。9px 字号下拉丁微偏上 < 0.5px，真机不可感知。

## 被推翻的 half-leading 方案（历史，保留作教训）

首次重构（commit `31c7201f`）采用 CSS2.1 §10.8.1 half-leading 模型，核心假设是
"FontBatchRenderer y 语义 = content-area 顶，封装正确，不动"。

**此假设与源码不符**：`FontBatchRenderer.java:259` + `GlyphGenerator.java:72` 证明
y 语义是 em-box 顶。half-leading 模型把 paint 层基准对到了 content-area，
与渲染器 em-box 锚点错配，真机仍偏上。

教训：**坐标系契约改动前必须用源码行号验证渲染器真实锚点，不能只凭方法名
（`drawBaselineAlignedString`）或历史文档推断。** 测试桩 `FixedTextMeasurer`
设 `ascent+descent == lineHeight` 使 `halfLeading=0`，结构性掩盖了
"行框锚 vs em-box 锚"的偏差，导致"测试全绿但真机偏上"（与
ERROR-20260625 同类陷阱）。

## 测试桩防盲区要求

`FixedTextMeasurer` 保持不变（被 24 个测试用，改它波及面大）。在垂直对齐用例里
**显式设 fontSize ≠ measurer.lineHeight**（当前用 fontSize=20、lineHeight=16），
使 em-box 模型（用 fontSize）与旧 half-leading 模型（用 lineHeight）期望值分叉：
CENTER 9 vs 旧 11、BOTTOM 14 vs 旧 18。TOP 因 `halfLeading=0` 恰不分叉（已知盲区，
CENTER/BOTTOM 已充分覆盖）。

根防"测试绿真机偏"的原则：测试桩的 `ascent/descent/lineGap` 必须满足
`A+D+gap=em` 但 `A+D≠em`、`lineHeight≠fontSize`，并随 fontSize 缩放；
只要 `A+D=lineHeight` 或度量写死常量，"行框锚 vs em-box 锚"的 bug 就会被结构性掩盖。

## 已知边界

- **多行文本**：layout 层按 `countLines(\n)*lineHeight` 多行撑高
  （`SceneLayoutEngine.java:389/866`），paint 层 `calculateTextTop` 是单行模型
  （单个 emHeight=fontSize），且 paint 只发一条带原始 `\n` 的 TEXT 命令。
  这是改动前就存在的既有不一致，非本次引入；多行垂直对齐属独立后续工作。
- **padding 失效**：`setPadding` 只调 `markSelfLayout` 不调 `markSelfPaint`
  （`SceneNode.java:983`），固定尺寸节点动态改 padding 时 textTop 不刷新。
  既有问题，非本次引入。
- **scene 核心 main 侧 SceneTextMeasurer.ascent/descent/lineGap 已无消费者**
  （lineHeight 仍用于 layout 多行高度）。接口瘦身属独立架构决策。

## 验证

- JetBrains 编译通过；`ScenePaintEngineTest` 全绿；`club.heiqi.uilib.ui.scene.*` 全绿。
- reviewer 有条件通过（无阻断），epoch 失效链完整性反而提升
  （textTop 不再依赖运行时度量，固定尺寸节点 box 不变时不再隐性错位）。
- **真机视觉验收待用户跑 runClient21**：确认按钮等控件文字垂直居中效果。

## 实施记录

- 分支 `fix/text-vertical-alignment`。
- 改动文件：`ScenePaintEngine.calculateTextTop`、`TextVerticalAlign` 注释、
  `ScenePaintEngineTest` 垂直对齐用例 + `paintTextWithAlign` 辅助方法。
