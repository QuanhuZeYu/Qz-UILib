# HTML-like 根视口滚动后缺少可见滚动条

## 错误现象

- HTML-like 页面根元素已经能通过滚轮滚动，但页面右侧没有任何滚动条提示。
- 布局诊断页滚动到中下部后仍看不到页面级或内部 `overflow:auto` 的 track/thumb。

## 触发场景

- 页面级滚动从旧 `ScrollViewportWidget` 切换到 `HtmlLikeDocumentWidget` 根元素 `overflow:auto`。
- `DocumentScrollState` 已记录 scrollTop/maxScrollTop，`DocumentPaintEngine` 只移动内容与裁剪，没有生成滚动条绘制命令。

## 根本原因

- 旧滚动条绘制逻辑在 retained `ScrollViewportWidget` / `OverflowScrollState` 中。
- HTML-like paint command 层此前只有 background/border/text/clip/custom，没有 scrollbar track/thumb 命令。
- 迁移页面级滚动时没有同步把“可滚状态的视觉反馈”迁移到 HTML-like paint 核心。

## 修复方案

- `DocumentPaintCommandType` 增加 `SCROLLBAR_TRACK` 与 `SCROLLBAR_THUMB`。
- `DocumentPaintEngine` 在可滚 `overflow:auto` 元素的内容与 clip 完成后追加滚动条命令，滚动条固定在元素内容盒右侧或底侧，不随内容滚动。
- `DocumentPaintRenderer` 将滚动条命令投影为 `UiRenderContext.drawSurface(...)`。
- 测试覆盖滚动条命令生成、renderer 投影、widget 渲染中 track/thumb 出现。

## 预防措施

- 后续新增 HTML-like 滚动能力时，必须同时考虑布局状态、输入滚动、paint command 可见反馈三部分。
- 不要依赖旧 `ScrollViewportWidget` 的滚动条作为 HTML-like 页面滚动的视觉反馈。
- 页面级滚动验收除了能滚动，还必须确认有可见 track/thumb，且滚动条不随内容偏移。
