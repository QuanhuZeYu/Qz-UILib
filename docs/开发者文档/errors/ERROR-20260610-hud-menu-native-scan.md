# HUD 菜单页原生输入反射深扫崩溃

## 错误现象

Qz-Team 在 Java 21 客户端启动后注册交互 HUD，点击多人游戏进入 `GuiMultiplayer` 时，Qz-UILib 在 render tick 的 HUD 输入链路中崩溃：

```text
java.lang.reflect.InaccessibleObjectException: Unable to make field private final java.util.Map java.security.SecureClassLoader.pdcache accessible
```

崩溃栈落在 `UiNativeTextInputInspector.hasFocusedTextInputReflectively(...)` 的 `field.setAccessible(true)`。

## 触发场景

- Java 21 运行 Minecraft 1.7.10 / GTNH 类环境。
- 有任意 HUD entry 注册，使 `UiHudDocumentHost.handleInputFrame(...)` 每帧参与输入刷新。
- 当前屏幕切到多人游戏菜单页 `GuiMultiplayer`。

## 根本原因

`GuiMultiplayer` 已被 `UiHudDocumentHost.classifyScreen(...)` 识别为 `MENU`，理论上 HUD 不应显示也不应接通输入。但旧实现会在 `createInputContext(...)` 中先调用 `UiNativeTextInputInspector.hasFocusedTextInput(currentScreen)`，再判断
`interactiveInputEnabled`。

因此菜单页虽然后续会隐藏 HUD，仍然提前触发了原生文本框焦点反射深扫。多人游戏页对象图包含 `OldServerPinger`、LAN 探测线程、`Minecraft mc` 等复杂宿主对象，反射扫描继续进入 JDK 内部对象后，在 Java 21 模块封装下访问 `SecureClassLoader.pdcache` 失败并直接抛出运行时异常。

## 修复方案

`UiHudDocumentHost.createInputContext(...)` 先计算 HUD 交互输入是否接通，只有 `CONTAINER` 且交互输入启用时才调用 `UiNativeTextInputInspector.hasFocusedTextInput(...)`。菜单页和纯游戏 HUD 场景直接将 `nativeTextInputFocused` 视为 `false`，
避免无意义的原生文本框反射扫描。

新增 `shouldInspectNativeTextInput(...)` 包内判断，并补充单元测试覆盖 `MENU` / `INGAME` 即使误传交互启用也不能触发扫描。

## 预防措施

- HUD 显示黑名单和 HUD 输入探测必须同时生效，不能只隐藏渲染层。
- 任何 render tick 高频路径都不应对菜单页、线程、类加载器、网络组件等宿主设施做反射深扫。
- 后续若继续保留 `UiNativeTextInputInspector` 的兜底反射扫描，还应单独补 `RuntimeException` 捕获和 JDK / ClassLoader / Thread 对象跳过，防止容器类界面出现同类 Java 21 模块封装问题。
