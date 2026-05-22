# 键盘事件默认行为未尊重 preventDefault

## 错误现象

- raw button 的 key handler 调用 `DocumentElementKeyEvent.preventDefault()` 后，Enter / Space 仍会触发默认 click。
- 若 Space pressed 已记录 raw button 按下状态，Space released 被 `preventDefault()` 取消后，旧按下状态没有被清理，后续释放事件仍可能误触发 click。

## 触发场景

- 页面作者使用 `document.button()` 创建原生语义按钮。
- 按钮或其祖先的 key handler 调用 `event.preventDefault()`，但返回 `false` 让事件继续传播。
- `DocumentKeyboardEventDispatcher.dispatchKeyAndDefault(...)` 在 key 传播未停止时继续执行 raw button 默认键盘行为。

## 根本原因

- key 事件分发只把 `stopPropagation()` / handler 返回 `true` 转换为“已消费”结果。
- 新增统一事件取消语义后，`preventDefault()` 只写入 `DocumentEventControl.defaultPrevented`，但 raw button 默认行为分支没有读取该状态。
- 默认行为状态机只关心 Space pressed / released，没有在默认行为被取消时清理 pending Space 状态。

## 修复方案

- 将 key 分发结果扩展为“传播是否停止”和“默认行为是否被阻止”。
- `dispatchKeyAndDefault(...)` 只有在传播未停止且默认行为未被阻止时才执行 raw button 默认键盘行为。
- Space 默认行为被 `preventDefault()` 取消时清理 raw button 的 pending Space 状态。
- 增加 `HtmlLikeDocumentWidgetTest.shouldPreventRawButtonDefaultKeyboardClick`，覆盖 Enter 取消、Space release 取消和 stale Space 状态清理。

## 预防措施

- 事件分发器拆分或新增默认行为时，必须同时覆盖 `stopPropagation()` 与 `preventDefault()` 两条语义路径。
- 默认行为如果维护跨事件状态，取消默认行为时也要清理对应状态，不能只跳过当前动作。
- 对 raw button、链接、表单控件这类带默认行为的元素，新增事件语义时要补“handler 返回 false 但 preventDefault 生效”的回归测试。
