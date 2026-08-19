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

## 教训

- **跨字号缩放公式必须先分清"共享基准"与"自身几何"**：任何"同基线/同网格"语义都要求
  共享量用统一基准换算。规划阶段的"预期自动成立"必须在渲染级用单测锁定
  （本次补 `FontBatchRendererGlyphQuadTest.shouldShareBaselineAcrossMixedCharSizes`）。
- 首版简化要在文档限制之外给出**真机可验证的具体表现**（"行距偏紧"不足以暴露侵入行）；
  涉及视觉侵占的简化应直接标记为待修而非接受。
