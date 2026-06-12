# 决策：lwjgl3ify 输入后端反射隔离

## 背景

`UiInputService` 原本直接 import、implements 并注册 `me.eigenraven.lwjgl3ify.api.InputEvents`，同时 `dependencies.gradle` 把 `lwjgl3ify` 声明为发布硬依赖。这样会把输入层与 `lwjgl3ify` Mod API 绑定到源码和 Maven 元数据上，不利于后续将 UILib 作为更通用的 1.7.10 / GTNH UI 库发布。

## 候选方案

1. 继续直接依赖 `InputEvents`：实现简单，但发布依赖和源码耦合不变。
2. 完全移除 `lwjgl3ify` 输入能力：能解除硬依赖，但会丢失现代文本输入和 IME。
3. 抽内部后端并反射接入 `InputEvents`：保留增强能力，同时让源码和发布依赖不再静态暴露该 API。

## 最终选择

采用方案 3：`UiInputService` 保持 facade 与调用点不变，内部抽 `UiInputBackend`；优先创建 `Lwjgl3ifyInputBackend` 反射订阅 `InputEvents`，缺失时回退 `LwjglxPollingInputBackend`。

## 选择原因

- 页面、HUD 和屏幕调用点不需要感知底层输入来源变化。
- 文本输入/IME 仍可在存在 `InputEvents` 的运行环境中保持原语义。
- 发布产物不再通过 Maven POM 强制声明 `lwjgl3ify`，避免把开发运行环境依赖扩散给下游。
- 第一阶段不触碰 OpenGL、字体 atlas、基础 `org.lwjgl` / `org.lwjglx` 客户端链路，避免扩大重构范围。

## 影响范围

- `dependencies.gradle` 中 `lwjgl3ify` 改为 `devOnlyNonPublishable`。
- 测试源码通过 `testCompileOnly` 读取 `Keyboard.KEY_*` 常量，不把 `lwjgl3ify` 加回发布依赖；纯 JVM 渲染测试不应依赖真实 OpenGL 状态调用。
- `src/main/java/club/heiqi/uilib/ui/input/` 新增内部输入后端协作者。
- `LwjglxPollingInputBackend` fallback 只承诺基础按键、鼠标与滚轮；复杂文本输入和 IME 明确降级。

## 后续注意事项

- HUD immediate 与 collected 输入去重仍依赖 `UiInputService.suppressNextCollectedKeyboardEvent(...)`，后续改输入链路时必须保留同帧去重窗口。
- 滚轮优先读取 `Mouse.totalScrollAmount`；无该扩展字段时再降级到事件滚轮。
- 后续若继续拆解渲染或字体依赖，应单独立项，不与 `lwjgl3ify` Mod API 解耦混在一起。
