# 语义展示页 focus 回写向非空 setter 传 null 导致崩溃

## 错误现象

- 在 `浏览器语义展示` 页点击 `:focus / :focus-visible` 卡片后，客户端直接崩溃。
- 崩溃栈落在 `UiStyleDeclaration.setOutline(...)`，异常为 `NullPointerException: outline`。

## 触发场景

- 示例页为了让 focus 状态更直观，在 `DocumentElementFocusHandler` 中手动回写样式。
- 元素失焦时，示例代码调用了 `style().setOutline(null)` 试图清除 outline。

## 根本原因

- `UiStyleDeclaration.setOutline(...)` 契约要求参数非空，内部显式 `Objects.requireNonNull(outline, "outline")`。
- 示例页把“清除属性”的意图错误地实现成“向 setter 传 null”，触发空指针。
- 这类问题属于作者侧 demo 对样式声明 API 的误用，不是事件系统或 focus 路由本身崩溃。

## 修复方案

- 将失焦分支的 `setOutline(null)` 改为 `clearOutline()`。
- 为 `HtmlLikeBrowserSemanticsShowcaseDocumentPageControllerTest` 增加回归测试，显式覆盖 hover/focus 回写流程，并断言 focus 退出后 outline 被正常清空且不抛异常。

## 预防措施

- 所有 `UiStyleDeclaration` 类型化 setter 都应视为“只接收合法值”，清空属性必须优先查找对应 `clearXxx()` API，而不是传 `null` 猜测语义。
- 示例页做事件驱动的样式回写时，先确认 setter/clearer 成对存在，再写焦点、hover、active 的退出分支。
- 遇到“作者态为了演示交互直接修改样式”的代码，必须补一个最小回归测试，至少覆盖进入态和退出态各一次。
