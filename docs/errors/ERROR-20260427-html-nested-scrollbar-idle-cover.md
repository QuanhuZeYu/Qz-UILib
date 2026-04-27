# HTML-like 嵌套滚动条空闲时遮挡内容

## 错误现象

- HTML-like Smoke 页的 teal 文本卡片内部滚动条在不滚动时仍持续显示。
- 内部滚动条覆盖在内容盒右侧，文本较窄时会遮挡可读内容。

## 触发场景

- `DocumentPaintEngine` 为所有可滚 `overflow:auto` 元素无条件生成 `SCROLLBAR_TRACK` 与 `SCROLLBAR_THUMB` 命令。
- 页面包含根视口滚动和内部滚动块时，内部滚动块的滚动条也会长期显示。

## 根本原因

- 滚动条可见性只依赖可滚范围，没有区分页面级滚动条与内容块内的临时滚动条。
- `DocumentScrollState` 此前没有记录最近有效滚动时间，paint 层无法判断内部滚动条是否已经空闲。
- `HtmlLikeDocumentWidget` 的 paint command 缓存只关注滚动偏移版本，没有考虑临时滚动条从可见到隐藏的时间状态切换。

## 修复方案

- `DocumentScrollState` 在有效滚轮滚动后记录元素最近滚动时间，并暴露临时滚动条可见性查询。
- `DocumentPaintEngine` 保持根元素滚动条可见；嵌套 `overflow:auto` 元素只有处于最近滚动窗口内才输出滚动条命令。
- `HtmlLikeDocumentWidget` 将临时滚动条活跃状态纳入 paint command 缓存条件，确保空闲超时后会重建命令并隐藏内部滚动条。
- `DocumentPaintEngineTest` 覆盖嵌套滚动块初始隐藏、滚动后显示、空闲后隐藏的行为。

## 预防措施

- 后续调整滚动条行为时，应明确区分根视口滚动条和内部块滚动条，避免把页面级可见提示策略套用到内容块。
- 任何依赖时间窗口的 paint command 都必须同步考虑 widget 侧缓存失效条件。
- 游戏内验收内部滚动块时，除了确认滚动生效，还要确认停止滚动后滚动条不会长期覆盖内容。
