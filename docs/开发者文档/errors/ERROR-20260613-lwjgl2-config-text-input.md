# 非 lwjgl3ify 环境配置页文本框无法输入

## 错误现象

Issue #62：未安装 `lwjgl3ify` 的玩家打开 Qz-UILib 配置界面后，文本框聚焦正常，但无法输入数字或普通字符。

## 触发场景

- Minecraft 1.7.10 / GTNH 客户端未提供 `lwjgl3ify` `InputEvents`。
- 打开继承 `BaseScreen` 的配置页，例如 `ForgeConfigTemplateScreen`。
- 点击 HTML-like 文档中的 `input` / 数值属性文本框后键入数字或字母。

## 根本原因

配置页输入事件链路为 `UiInputTickListener` -> `UiInputService.tick/collectFrame` -> `UiScreenManager.tick` -> `BaseScreen.handleInputFrame` -> `UiInputRouter` -> `HtmlLikeDocumentWidget` -> `DocumentKeyboardEventDispatcher`。

`DocumentTextInputControl` 只在 `onTextInput(UiTextInputEvent)` 中插入字符，`onKey(...)` 只处理退格等按键默认行为。有 `lwjgl3ify` 时，`Lwjgl3ifyInputBackend.handleTextEvent(...)` 会收集 `UiTextInputEvent`；无 `lwjgl3ify` 时，`UiInputService` 回退到 `LwjglxPollingInputBackend`，旧实现只通过键盘状态差分产生 `UiKeyEvent`，没有文本事件，导致文本框永远收不到字符。

不能简单把 `LwjglxPollingInputBackend` 改成 `Keyboard.next()` / `getEventCharacter()` 事件迭代，因为原版 `GuiScreen.handleKeyboardInput()` 也消费同一事件队列，轮询后端抢读会破坏 vanilla 键盘事件流。

## 修复方案

保留原版事件队列消费方式，复用 `BaseScreen.keyTyped(char typedChar, int keyCode)` 已翻译好的字符：

- `UiInputBackend` 新增 `handleHostTypedCharacter(...)`，`UiInputService` 暴露 `submitHostTypedCharacter(...)`。
- `BaseScreen.keyTyped(...)` 先调用 `super.keyTyped(...)` 保持 vanilla ESC / 关闭等行为，再提交宿主字符。
- `LwjglxPollingInputBackend` 仅接受 `typedChar >= 32 && typedChar != 127` 的基础可打印字符，合成 `UiTextInputEvent`，并复用 collected 文本去重窗口。
- `Lwjgl3ifyInputBackend` 在键盘监听注册成功时忽略宿主字符，避免与 `InputEvents.TextEvent` 双输入；注册失败时转交轮询后端兜底。
- `LwjglInputRuntime.KeyboardRuntime` 反射封装 `enableRepeatEvents(boolean)`，`BaseScreen` 打开时开启、关闭时关闭，保留长按重复输入体验。

## 预防措施

- 文本控件回归测试必须同时覆盖增强输入事件与无增强输入后端兜底路径。
- 后续改输入层时，不能只验证 `UiKeyEvent`，还必须验证 `UiTextInputEvent` 是否能到达 `DocumentTextInputControl`。
- 避免在轮询后端抢读 LWJGL 键盘事件队列；基础字符输入应走宿主 `keyTyped` 桥接，复杂文本输入继续依赖增强输入 API。
