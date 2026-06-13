# 决策：lwjgl3ify 输入后端反射隔离

## 背景

`UiInputService` 原本直接 import、implements 并注册 `me.eigenraven.lwjgl3ify.api.InputEvents`，同时 `dependencies.gradle` 把 `lwjgl3ify` 声明为发布硬依赖。这样会把输入层与 `lwjgl3ify` Mod API 绑定到源码和 Maven 元数据上，不利于后续将 UILib 作为更通用的 1.7.10 / GTNH UI 库发布。第一阶段完成后，控件、文档、remote、config、devtools 与测试代码仍大量只为 `Keyboard.KEY_*` 常量 import `org.lwjglx.input.Keyboard`，继续把业务语义绑在底层输入类上。

## 候选方案

1. 继续直接依赖 `InputEvents`：实现简单，但发布依赖和源码耦合不变。
2. 完全移除 `lwjgl3ify` 输入能力：能解除硬依赖，但会丢失现代文本输入和 IME。
3. 抽内部后端并反射接入 `InputEvents`：保留增强能力，同时让源码和发布依赖不再静态暴露该 API。
4. 继续让业务代码直接使用 `Keyboard.KEY_*`：改动少，但 `org.lwjglx` 常量依赖继续扩散。
5. 新增 UILib 自有键码常量层：保持 `UiKeyEvent.keyCode` 数值语义不变，同时让业务代码不再 import 底层输入类。

## 最终选择

采用方案 3 与方案 5：`UiInputService` 保持 facade 与调用点不变，内部抽 `UiInputBackend`；优先创建 `Lwjgl3ifyInputBackend` 反射订阅 `InputEvents`，缺失时回退 `LwjglxPollingInputBackend`。若 `InputEvents` 类存在但键盘监听注册失败，继续使用同一轮询后端启用键盘状态差分兜底，避免 collected 键盘输入静默丢失。新增 `UiKeyCodes` 承载当前 LWJGL2/MC 键码数值，业务、文档、remote、config、devtools 和测试侧统一引用该常量层。

## 选择原因

- 页面、HUD 和屏幕调用点不需要感知底层输入来源变化。
- 文本输入/IME 仍可在存在 `InputEvents` 的运行环境中保持原语义。
- 发布产物不再通过 Maven POM 强制声明 `lwjgl3ify`，避免把开发运行环境依赖扩散给下游。
- `UiKeyCodes` 不改变 `UiKeyEvent.keyCode` 对外数值，仅把键码常量来源从底层输入类收拢到 UILib 事件语义层。
- 第一阶段不触碰 OpenGL、字体 atlas、基础 `org.lwjgl` / `org.lwjglx` 客户端链路，避免扩大重构范围。

## 影响范围

- `dependencies.gradle` 中 `lwjgl3ify` 改为 `devOnlyNonPublishable`。
- 业务和测试源码统一通过 `UiKeyCodes` 使用当前 LWJGL2/MC 键码数值，不把 `lwjgl3ify` 或 legacy LWJGL 加回发布依赖；纯 JVM 渲染测试不应依赖真实 OpenGL 状态调用。
- `src/main/java/club/heiqi/uilib/ui/input/` 新增内部输入后端协作者。
- `src/main/java/club/heiqi/uilib/ui/event/UiKeyCodes.java` 记录业务层使用的 LWJGL2/MC 键码常量；控件、HTML-like 文档默认行为、remote 表单、配置页、devtools 断言和纯 JVM 测试不再直接 import `org.lwjglx.input.Keyboard`。
- `LwjglxPollingInputBackend` fallback 只承诺基础按键、鼠标与滚轮；复杂文本输入和 IME 明确降级。`InputEvents` 注册失败时键盘可兜底到轮询，文本事件仍不会由轮询后端合成。

## 后续注意事项

- HUD immediate 与 collected 输入去重仍依赖 `UiInputService.suppressNextCollectedKeyboardEvent(...)`，后续改输入链路时必须保留同帧去重窗口。
- 滚轮优先读取 `Mouse.totalScrollAmount`；无该扩展字段时再降级到事件滚轮。
- `LwjglxPollingInputBackend` 与 `UiHostInputCoordinator` 仍是客户端宿主输入事件源边界，可以继续直接调用 `org.lwjglx.input.Keyboard` / `Mouse`；不要在普通控件、remote、config 或 devtools 里重新引入底层 `Keyboard.KEY_*`。
- 输入后端测试应使用包内替身或纯映射 helper，避免纯 JVM 测试为了类加载真实 `org.lwjgl.sdl` / OpenGL 运行态而新增 legacy LWJGL 运行依赖。
- 后续若继续拆解渲染或字体依赖，应单独立项，不与 `lwjgl3ify` Mod API 解耦混在一起。
