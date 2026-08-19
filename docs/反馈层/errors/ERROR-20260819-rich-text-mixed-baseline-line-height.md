# ERROR-20260819 富文本混排基线错位与行高侵入（真机目检修复）

## 现象

富文本组件真机目检发现两个视觉问题：

1. **字号混排非基线对齐而是顶部对齐感**：同一行内 `<size=24>` 大字与基准小字没有共享基线，
   大字整体下沉/错位，视觉呈"顶部对齐"。
2. **大字侵入下方相邻行**：混排行高未按最大字号计算，大字渲染超出本行行框覆盖下一行。

## 根因

### 1. 基线换算用错了缩放基准

`FontBatchRenderer.resolveGlyphQuadMetrics` 原公式：

```
glyphScale = charSize / defaultGlyphSize      // charSize 是 glyph 自身字号
baselineY  = y + lineBaselineY × glyphScale   // 基线随 glyph 字号缩放！
quadY      = baselineY + bearingY × glyphScale
```

基线位置随每个 glyph 的字号缩放——16px 字基线在 `y+51×1`，24px 字基线在 `y+51×1.5`，
**同一行不同字号的基线彼此错开**，根本不是共享基线。

正确公式是"共享基准"与"自身几何"分离：

```
glyphScale   = charSize / defaultGlyphSize       // glyph 自身几何（bearing/宽高）
baselineScale = baseCharSize / defaultGlyphSize  // 整段基准渲染尺寸（全行统一）
baselineY = y + lineBaselineY × baselineScale    // 共享基线
quadY     = baselineY + bearingY × glyphScale    // 大字在基线上方占更多空间
```

`collectBaselineAlignedGlyph` 早已收了 `baseCharSize` 参数（原用于 italic 几何），
但基线公式没用它——实施富文本时按"baseline-aligned 设计预期自动成立"的假设跳过渲染级验证，
只锁定了数据层（PreparedText 字号数组）。

### 2. 混排行高首版简化

首版把行高简化为"组件基准字号行高"（统一值），文档注明限制。真机大字 span 直接超出
行框侵入下行。修复为：行高按**行内最大显式字号**计算（`TextLayoutService.getLineHeight(text, style)`
解析各段取最大字号），绘制引擎逐行累计推进 textTop，布局侧文本叶拆行后逐行行高求和定高
（绘制与布局同口径）。

## 修复

- `FontBatchRenderer.resolveGlyphQuadMetrics` 增加 `baseCharSize` 重载；旧重载回落
  `baseCharSize=charSize`（旧语义零回归，旧测试逐位不动）。
- `DefaultFontRendererAdapter.drawPreparedText` 的 collectGlyph 基准参数从
  `settings.getCharSize()` 改为整段渲染尺寸（scaled/px 路径基线同步缩放）。
- 测量链路新增富文本感知行高：`TextLayoutService` / `TextMeasureService` /
  `SceneTextMeasurer` / `TextMeasureServiceSceneAdapter` 四级透传。
- `ScenePaintEngine` 逐行行高累计（单行无混排保持 em-box=fontSize 零回归）。
- `SizingCalculator.leafTextHeight` + `ConstraintResolver` 文本叶 wrap 感知高度/宽度。

## 第二轮（真机复验后）

首轮修复后基线位置已接近正确，但行高仍侵入。剩余两处缺口：

1. **基线缩放基准应为"行内最大字号"而非"整段基准字号"**：共享基线位置用基准字号换算时，
   大字 ascender（基线上方高度）超出按 max 字号计算的行框顶（CJK 大字向上溢出 2-4px 压到上一行）。
   烘焙恒等式 `lineBaselineY ≈ glyphSize - descent ≈ ascent` 使"基线按 Fmax 换算"与
   "行顶 + ascent(Fmax)"精确对齐。修复：`PreparedText` 统计 `maxFontSizePx`，
   `resolveBaselineCharSize` 按行内最大字号换算基线渲染尺寸。
2. **非 wrap 单行混排的布局高度未走富文本感知路径**：首轮只在 `wrapWidth>0` 分支做了感知行高，
   无 wrap 的混排行（演示页"字号混排"行）布局高度仍按基准字号 → 大字溢出布局盒侵入相邻元素。
   修复：`leafTextHeight` 非 wrap 分支对 RICH 模式按整段最大字号行高。

## 第三轮（真机复验：混排行与换行行贴在一起，基线下侧空间异常）

**根因是 px 绘制路径的 span 字号双重放大，不是基线公式。**

`drawBaselineAlignedStringPx`（scene 文本 15px 路径）以 `charSize=15、renderScale=15/9`
表达"15px 渲染"（9 为引擎默认显示字号）。而 per-glyph 尺寸公式
`glyphCharSize = charSize × spanFontSizePx / settings.charSize` 把 `<size=24>` 换算成
`15 × 24 / 9 = 40px`——**24px 的 span 被渲染成 40px**，字形底部超出按 24px 计算的行框
16px，与下一行贴在一起（顶部因基线按 max 字号换算恰好对齐，掩盖了尺度错误）。

修复：span 字号语义定为"绝对 UI 像素（以调用方 px 字号为基准）"——px 路径 `renderScale` 恒 1.0，
`prepareGlyphs` 显式接收调用方基准字号（px 路径 = 目标 px 字号；引擎/scaled 路径 =
settings.charSize），per-glyph 尺寸 = 有效字号 × renderScale（`resolveGlyphCharSize` /
`resolveBaselineCharSize` 改为该语义）。scaled 路径（HUD GUI Scale）行为不变（基准即
settings.charSize，缩放统一由 renderScale 表达）。px 路径副作用：阴影偏移不再随字号放大
（从 1×15/9 变为 1px 绝对值，与 FontConfig.shadowOffsetX 的绝对像素语义一致）。

教训："渲染尺寸 = 基准渲染尺寸 × 段字号 / 引擎基准字号"这类公式里，**段字号的参照系必须
与调用方基准字号一致**；px 路径用 renderScale 表达字号缩放时，span 相对缩放与路径缩放
发生隐性双重相乘，而整段单字号场景两者恰好抵消、测试无法暴露——必须补"span 字号渲染
尺寸"的显式单测（`resolveGlyphCharSize` 语义测试）。

## 第四轮（真机回归：横排文字全部挤在一起）

第三轮把 px 路径 renderScale 改 1.0、基准字号改调用方 px（15）后，`prepareGlyphs` 里
"段字号 == 基准字号 → 走无字号测量"的三元分支被触发：普通文本段字号 15 == 基准 15 →
回落 `getCodepointWidth(codepoint, style)`（返回 settings.charSize 坐标系的缓存宽，9px 宽），
而 renderScale 已不再补偿 → 所有横排文字 advance 缩成 9/字号 比例，挤在一起。

修复：统一走带字号测量 `getCodepointWidth(codepoint, style, segmentFontSizePx)`
（= 缓存宽 × 段字号 / settings.charSize，三个路径恒正确），删除回落分支；抽取
`resolveSegmentCodepointWidth` 包级函数 + 语义单测锁定。

教训：这类"基准相等走快路径"的微优化分支，在基准语义变化后是隐性炸弹——基准不再是
settings.charSize 后快路径的坐标系假设全部失效。快路径必须有显式单测锁定其等价前提；
当等价前提消失时应直接删除快路径，而不是保留条件式回落。

## 教训

- **跨字号缩放公式必须先分清"共享基准"与"自身几何"**：任何"同基线/同网格"语义都要求
  共享量用统一基准换算。规划阶段的"预期自动成立"必须在渲染级用单测锁定
  （本次补 `FontBatchRendererGlyphQuadTest.shouldShareBaselineAcrossMixedCharSizes`）。
- 首版简化要在文档限制之外给出**真机可验证的具体表现**（"行距偏紧"不足以暴露侵入行）；
  涉及视觉侵占的简化应直接标记为待修而非接受。
