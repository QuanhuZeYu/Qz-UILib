# Minecraft 原版输入链路

本文整理 Minecraft 1.7.10 客户端在 `GuiScreen` 打开状态下的原版输入分发链路，并据此回看 Qz UILib 当前的输入注入层级是否合理。

本文主要基于以下源码观察：

- `run/client/.mixin.out/java/net/minecraft/client/Minecraft.java`
- `run/client/.mixin.out/java/net/minecraft/client/gui/GuiScreen.java`
- `run/client/.mixin.out/java/net/minecraft/client/gui/inventory/GuiContainer.java`

`.mixin.out` 是运行时实际混入后的结果，因此既能看到原版主链路，也能看到当前运行环境下第三方 mixin 对输入链的改写位置。

## 总览

原版输入系统可以分成三层：

1. 原始事件轮询层：`Mouse.next()` / `Keyboard.next()` 推进底层事件队列游标。
2. 屏幕语义分发层：`handleInput()`、`handleMouseInput()`、`handleKeyboardInput()` 把当前原始事件翻译成 GUI 语义。
3. 组件行为层：`mouseClicked(...)`、`mouseMovedOrUp(...)`、`mouseClickMove(...)`、`keyTyped(...)` 等具体页面行为。

如果要让叠层 UI 先消费、原生界面后消费，最稳妥的做法通常不是只拦最末端的 `keyTyped(...)` 或 `mouseClicked(...)`，而是优先在“屏幕语义分发层”之前或之中决定这条原始事件是否还要继续流向宿主。

## `Minecraft` 级入口

`Minecraft` 在主循环里先处理当前打开界面的输入，再决定是否继续跑全局键鼠逻辑：

1. `currentScreen != null` 时，先调用 `currentScreen.handleInput()`。
2. 然后调用 `currentScreen.updateScreen()`。
3. 只有在 `currentScreen == null` 或 `currentScreen.allowUserInput == true` 时，才继续进入 `Minecraft` 自己的全局 `mouse` / `keyboard` 循环。

这意味着：

- 对绝大多数普通 `GuiScreen`，界面打开期间的输入主入口就是 `GuiScreen.handleInput()`。
- `Minecraft` 后半段的全局键鼠循环主要服务于“无界面”状态，以及少数允许背景输入的界面。
- 现在 `.mixin.out` 中能直接看到 `GuiContainerCreative` 会把 `allowUserInput` 设为 `true`；这类界面需要额外留意是否存在“双路径”输入风险。

## `GuiScreen.handleInput()` 的职责

`GuiScreen.handleInput()` 自己并不解释业务语义，它只负责逐个推进事件队列：

```java
if (Mouse.isCreated()) {
    while (Mouse.next()) {
        this.handleMouseInput();
    }
}

if (Keyboard.isCreated()) {
    while (Keyboard.next()) {
        this.handleKeyboardInput();
    }
}
```

这里有两个关键事实：

- `handleInput()` 的核心控制点其实是 `Mouse.next()` / `Keyboard.next()`。
- 一旦某个方案能在这里决定“当前原始事件是否继续流向宿主”，它就能比 `handleMouseInput()` / `handleKeyboardInput()` 内部逻辑更早地做输入仲裁。

在当前运行环境中，`handleInput()` 的循环体还被其他模组包了一层事件包装，例如 `.mixin.out` 可见的 ModularUI `KeyboardInputEvent.Pre/Post` 与 `MouseInputEvent.Pre/Post`。因此，如果某个事件已经应该被 HUD 完整吃掉，最好连这层包装事件都不要再触发。

## `GuiScreen.handleMouseInput()` 的语义

`handleMouseInput()` 会把当前原始鼠标事件翻译成 GUI 坐标和页面回调：

1. 读取 `Mouse.getEventX/Y/Button/ButtonState()`。
2. 把原生显示坐标换算成当前 GUI 坐标。
3. 根据按下、抬起、移动/拖拽三类状态，分别调用：
   `mouseClicked(...)`、`mouseMovedOrUp(...)`、`mouseClickMove(...)`。

因此，对鼠标事件来说：

- 只要在 `handleMouseInput()` 之前完成命中判断，就能阻断原生按钮、拖拽、滚轮等后续语义。
- 如果等到 `mouseClicked(...)` 才拦截，就已经太晚了，前面的包装逻辑、状态记录乃至某些第三方前置事件都已经发生。

## `GuiScreen.handleKeyboardInput()` 的语义

`handleKeyboardInput()` 对当前原始键盘事件做的事相对更少：

1. 只在 `Keyboard.getEventKeyState()` 为 `true` 时调用 `keyTyped(Keyboard.getEventCharacter(), Keyboard.getEventKey())`。
2. 每次事件末尾都会调用 `mc.func_152348_aa()`。

这里要注意两点：

- 原版 `keyTyped(...)` 同时拿到字符和键码，所以“文本输入”和“功能键/热键”并不是两条完全分离的原版链路。
- 如果要避免宿主文本框、容器热键或关闭页面逻辑继续响应，同样要尽量在 `keyTyped(...)` 之前决定该事件是否还应继续下传。

## `GuiContainer` 是特殊分支

容器页没有直接沿用 `GuiScreen.handleKeyboardInput()` 的默认实现。

在当前 `.mixin.out` 里可以看到：

- `GuiContainer.handleKeyboardInput()` 被覆写为 `this.manager.handleKeyboardInput()`。
- `GuiContainer.handleMouseInput()` 则是 `super.handleMouseInput(); this.manager.handleMouseWheel();`。

这说明：

- 只在 `GuiScreen.handleKeyboardInput()` 上做拦截，并不能天然覆盖所有容器页键盘逻辑。
- 容器页的键盘路径里还存在一层管理器或第三方扩展链，最终不一定只经过 `GuiScreen` 默认实现。

因此，容器页往往需要补一个更贴近最终行为的位置，例如 `keyTyped(...)` 或容器管理器自己的输入入口。

## `allowUserInput` 与全局循环的关系

`Minecraft` 在 `currentScreen == null || currentScreen.allowUserInput` 时还会继续跑全局 `Mouse.next()` / `Keyboard.next()` 循环。

但这不代表会天然产生“双处理”：

- 对普通 `GuiScreen`，事件通常已经在更早的 `currentScreen.handleInput()` 阶段被队列轮询消费掉了。
- 真正要留意的是少数特殊界面或第三方改写路径，它们可能在“是否消费队列”“是否额外派发包装事件”上与普通界面不同。

对 UILib 当前场景来说，这条信息主要用于说明为什么注入点应优先落在 `GuiScreen.handleInput()` 及其子路径，而不是直接去改 `Minecraft` 全局键鼠循环。

## 回看 UILib 当前注入点

### `MixinGuiScreenKeyboardIsolation`

当前注入点包括：

- `@Redirect` 到 `GuiScreen.handleInput()` 内部的 `Keyboard.next()` / `Mouse.next()`
- `@Inject(HEAD, cancellable = true)` 到 `GuiScreen.handleKeyboardInput()`
- `@Inject(HEAD, cancellable = true)` 到 `GuiScreen.handleMouseInput()`

这组注入总体是合理的，原因如下。

1. 在 `handleInput()` 的 `next()` 层做重定向是对的。

原因：

- 这是原始事件轮询层，能在当前事件真正进入宿主前先让 HUD 判断是否要抢占。
- 对已经被 HUD 完整消费的事件，可以直接跳过后续 `handleMouseInput()` / `handleKeyboardInput()`。
- 更重要的是，这还能连带跳过第三方在循环体上包裹的 `Pre/Post` 事件，避免“HUD 已吃掉，但宿主包装事件仍被触发”。

2. 在 `handleMouseInput()` / `handleKeyboardInput()` 开头再做一次可取消拦截也是对的。

原因：

- 这是屏幕语义分发层的兜底防线。
- 即便某些路径没有经过当前的 `next()` 重定向，或者后续第三方改写改变了 `handleInput()` 的循环体，只要还会落到 `handleMouseInput()` / `handleKeyboardInput()`，UILib 仍有机会阻断原生界面继续处理当前事件。

3. 对 `BaseScreen` 显式旁路是合理的。

原因：

- `BaseScreen` 并不依赖原版 `GuiScreen.handleInput()` 做正式输入分发。
- 它走的是 `UiInputTickListener -> UiInputService.collectFrame() -> UiScreenManager/BaseScreen.handleInputFrame(...)` 这条 UILib 自己的帧输入链。
- 如果不排除 `BaseScreen`，同一份输入会同时经过原版即时拦截链和 UILib 帧输入链，反而更容易重复分发。

### `MixinGuiContainerKeyTypedIsolation`

这个补充注入也是合理的。

原因：

- `GuiContainer` 覆写了 `handleKeyboardInput()`，动态派发后不会自然回到 `GuiScreen.handleKeyboardInput()` 的默认实现。
- 因此，只在 `GuiScreen.handleKeyboardInput()` 上做拦截，不足以覆盖容器页的最终键盘行为。
- 在 `GuiContainer.keyTyped(...)` 开头取消，可以拦住数字键切槽、Esc 关闭容器等最终落到容器页面行为层的逻辑。

### 当前结论

基于现有源码和 `.mixin.out` 观察，UILib 当前输入注入层级总体合理：

- 它把“HUD 先消费、宿主后消费”的主仲裁点放在了 `GuiScreen.handleInput()` 的原始事件轮询层。
- 它又在 `handleMouseInput()` / `handleKeyboardInput()` 留了语义层兜底。
- 对 UILib 自己的 `BaseScreen` 没有重复套用宿主抢占逻辑。
- 对容器页这种特殊分支补了额外隔离点。

## 当前边界与后续排查建议

当前实现虽然层级合理，但仍有两个需要长期记住的边界：

1. 这套注入默认覆盖的是 `GuiScreen` 体系。

如果后续遇到某个模组完全绕过 `GuiScreen.handleInput()` / `handleKeyboardInput()` / `handleMouseInput()` 自己轮询输入队列，就需要按它自己的入口单独补宿主隔离，而不是继续堆叠在现有 mixin 上。

2. 容器页的键盘链路有额外管理器参与。

如果未来再出现“HUD 已聚焦，但容器页仍有热键泄漏”，优先检查 `GuiContainer.handleKeyboardInput()` 背后的管理器链，而不是默认认为 `GuiScreen` 层拦截失效。

换句话说：现有注入点选层没有明显问题；后续如果再出输入串扰，更可能是个别特殊宿主分支没有完全覆盖，而不是当前主干思路本身错误。
