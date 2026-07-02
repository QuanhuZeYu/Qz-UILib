# 决策：lwjgl3ify 输入后端反射隔离

## 背景

`UiInputService` 原本直接 import、implements 并注册 `me.eigenraven.lwjgl3ify.api.InputEvents`，同时 `dependencies.gradle` 把 `lwjgl3ify` 声明为发布硬依赖。
这样会把输入层与 `lwjgl3ify` Mod API 绑定到源码和 Maven 元数据上，不利于后续将 UILib 作为更通用的 1.7.10 / GTNH UI 库发布。第一阶段完成后，控件、文档、remote、config、devtools 与测试代码仍大量只为 `Keyboard.KEY_*` 常量 import `org.lwjglx.input.Keyboard`，
继续把业务语义绑在底层输入类上；随后输入轮询后端、HUD 输入协调和系统光标宿主仍存在少量 `org.lwjglx` 静态 import，无法满足“无 `lwjgl3ify` 用户也可加载”的目标。

## 候选方案

1. 继续直接依赖 `InputEvents`：实现简单，但发布依赖和源码耦合不变。
2. 完全移除 `lwjgl3ify` 输入能力：能解除硬依赖，但会丢失现代文本输入和 IME。
3. 抽内部后端并反射接入 `InputEvents`：保留增强能力，同时让源码和发布依赖不再静态暴露该 API。
4. 继续让业务代码直接使用 `Keyboard.KEY_*`：改动少，但 `org.lwjglx` 常量依赖继续扩散。
5. 新增 UILib 自有键码常量层：保持 `UiKeyEvent.keyCode` 数值语义不变，同时让业务代码不再 import 底层输入类。
6. 保留输入源边界的 `org.lwjglx` 静态 import：代码简单，但缺少 `lwjgl3ify` / LWJGLX 的 1.7.10 环境可能在类加载阶段失败。
7. 新增内部输入运行时反射桥：集中选择 `org.lwjglx` 或 legacy `org.lwjgl`，保持输入源边界能力，同时避免源码静态绑定。

## 最终选择

采用方案 3、方案 5 与方案 7：`UiInputService` 保持 facade 与调用点不变，内部抽 `UiInputBackend`；优先创建 `Lwjgl3ifyInputBackend` 反射订阅 `InputEvents`，缺失时回退 `LwjglxPollingInputBackend`。
若 `InputEvents` 类存在但键盘监听注册失败，继续使用同一轮询后端启用键盘状态差分兜底，避免 collected 键盘输入静默丢失。新增 `UiKeyCodes` 承载当前 LWJGL2/MC 键码数值，业务、文档、remote、config、devtools 和测试侧统一引用该常量层。
新增 `LwjglInputRuntime` 集中反射访问键鼠运行时，优先 `org.lwjglx`，缺失时降级 legacy `org.lwjgl`。

## 选择原因

- 页面、HUD 和屏幕调用点不需要感知底层输入来源变化。
- 文本输入/IME 仍可在存在 `InputEvents` 的运行环境中保持原语义。
- 发布产物不再通过 Maven POM 强制声明 `lwjgl3ify`，避免把开发运行环境依赖扩散给下游。
- `UiKeyCodes` 不改变 `UiKeyEvent.keyCode` 对外数值，仅把键码常量来源从底层输入类收拢到 UILib 事件语义层。
- 输入轮询与宿主输入协调仍属于客户端输入源边界，但不再静态绑定 `org.lwjglx`，无 `lwjgl3ify` 时可尝试使用原版 LWJGL2 输入类。
- 当前阶段不触碰 OpenGL、字体 atlas 和渲染链路，避免扩大重构范围。

## 影响范围

- `dependencies.gradle` 中 `lwjgl3ify` 改为 `devOnlyNonPublishable`。
- 业务和测试源码统一通过 `UiKeyCodes` 使用当前 LWJGL2/MC 键码数值，不把 `lwjgl3ify` 或 legacy LWJGL 加回发布依赖；纯 JVM 渲染测试不应依赖真实 OpenGL 状态调用。
- `src/main/java/club/heiqi/uilib/ui/input/` 新增内部输入后端协作者。
- `src/main/java/club/heiqi/uilib/ui/event/UiKeyCodes.java` 记录业务层使用的 LWJGL2/MC 键码常量；控件、HTML-like 文档默认行为、remote 表单、配置页、devtools 断言和纯 JVM 测试不再直接 import `org.lwjglx.input.Keyboard`。
- `src/main/java/club/heiqi/uilib/ui/input/LwjglInputRuntime.java` 集中封装 `Keyboard` / `Mouse` 反射访问，不新增 legacy `org.lwjgl.lwjgl:lwjgl` 显式运行依赖。
- `LwjglxPollingInputBackend` fallback 承诺基础按键、鼠标、滚轮与 BMP 可打印字符输入；字符输入不读取 LWJGL 事件队列，而是复用 `BaseScreen.keyTyped(...)` 已翻译出的 `typedChar` 合成 `UiTextInputEvent`。
  IME、组合输入和补充平面字符仍依赖 `lwjgl3ify` `InputEvents`。
- 当前宿主字符桥接只接入 `BaseScreen.keyTyped(...)`，覆盖配置页等 `BaseScreen` 界面；HUD 文档宿主走独立 `handleKeyboardInput` 即时路由，不经过 `keyTyped(...)`，因此无 `lwjgl3ify` 时 HUD 内文本输入控件仍不会收到合成 `UiTextInputEvent`。
  本次只记录边界，不实现 HUD 文本输入桥接。
- **键盘事件语义降级**：fallback 模式不支持 `UiKeyEvent.Action.REPEATED`；`LwjglxPollingInputBackend` 只能检测按键状态变化（`PRESSED` / `RELEASED`），无法识别操作系统级别的按键重复事件。
  需要长按重复输入的控件（如文本框光标移动、数值调节）应在应用层自行实现定时器逻辑，或明确依赖 `InputEvents` 可用环境。
- `SystemDocumentCursorHost` 移除 `Display` 静态 import；SDL 系统光标仍依赖 lwjgl3ify / LWJGLX 光标桥，缺失时降级为 no-op。

## 后续注意事项

- HUD immediate 与 collected 输入去重仍依赖 `UiInputService.suppressNextCollectedKeyboardEvent(...)`，后续改输入链路时必须保留同帧去重窗口。
- 滚轮优先通过 `LwjglInputRuntime` 读取 `Mouse.totalScrollAmount`；无该扩展字段时再降级到事件滚轮。
- `LwjglxPollingInputBackend` 与 `UiHostInputCoordinator` 仍是客户端宿主输入事件源边界，但应通过 `LwjglInputRuntime` 访问键鼠运行时；不要在普通控件、remote、config、devtools 或输入源实现中重新引入 `org.lwjglx.input.*` 静态 import。
- 不要把 `LwjglxPollingInputBackend` 改成 `Keyboard.next()` / `getEventCharacter()` 事件迭代来获取文本字符；该队列同样由原版 `GuiScreen.handleKeyboardInput()` 消费，抢读会破坏宿主事件流。基础字符应继续走 `BaseScreen.keyTyped(...)` 宿主桥接。
- 输入后端测试应使用包内替身或纯映射 helper，避免纯 JVM 测试为了类加载真实 `org.lwjgl.sdl` / OpenGL 运行态而新增 legacy LWJGL 运行依赖。
- 后续若继续拆解渲染或字体依赖，应单独立项，不与 `lwjgl3ify` Mod API 解耦混在一起。
