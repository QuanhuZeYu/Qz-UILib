# HTML-like 页面点击后旧页面壳随机滚动

## 错误现象

- 当前页面作者层迁移为 `HtmlLikeDocumentWidget` 后，点击任意 HTML-like 元素或控件时，整页位置可能突然跳动。
- 该行为不符合 HTML-like 页面预期：点击只应改变焦点、active/click 状态，不应隐式改变页面滚动偏移。

## 触发场景

- `HtmlLikeDocumentWidget` 作为 `DocumentPageWidget` 的唯一子块挂载。
- HTML-like 文档内部存在 focusable 元素，导致 `HtmlLikeDocumentWidget#isFocusable()` 返回 true。
- 鼠标点击命中 HTML-like widget 后，`UiInputRouter` 将其设为全局焦点，并调用旧 `UiScrollHost#scrollDescendantIntoView(...)`。

## 根本原因

- 页面级滚动仍由旧 `DocumentPageWidget -> ScrollViewportWidget -> OverflowScrollState` 承担。
- `UiInputRouter.ensureWidgetVisible(...)` 会在焦点变化时让旧页面壳尝试把整个 `HtmlLikeDocumentWidget` 滚入可视区。
- 当 HTML-like widget 高度大于旧页面壳可视高度时，旧页面壳会为了“显示整个 widget”调整滚动偏移，表现为点击后页面随机跳动。

## 修复方案

- 新增 `DocumentLayoutEngine.layoutViewportRoot(...)`，让根元素 border box 固定为 widget 视口尺寸。
- 新增 `HtmlLikeDocumentWidget#setViewportRootScrollingEnabled(true)`，启用后整页滚动由根元素 `overflow:auto` 与 `DocumentScrollState` 承担。
- 四个当前 HTML-like 页面均把 widget 高度设置为 `UiLength.percent(1.0F)`，让旧页面壳内容高度等于可视区，不再产生页面级可滚范围。
- 页面根元素设置 `overflow-y:auto`，由 HTML-like 命中测试、滚动状态和 paint command 处理滚轮滚动。

## 预防措施

- 后续迁移页面时，如果页面内容需要整页滚动，应启用 `setViewportRootScrollingEnabled(true)` 并让根元素显式设置 `overflow-y:auto`。
- 不要通过增大 `HtmlLikeDocumentWidget` 自身高度来表达页面内容高度；内容高度应留在 DOM/layout 里，由 `DocumentScrollState` 推导。
- 测试需要覆盖：滚轮改变 HTML-like 根元素 scrollTop、点击 HTML-like focusable 元素后旧 `DocumentPageWidget#getScrollOffset()` 保持 0。
