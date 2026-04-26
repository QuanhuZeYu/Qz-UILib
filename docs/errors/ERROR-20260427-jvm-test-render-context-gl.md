# ERROR-20260427-jvm-test-render-context-gl

## 错误现象

- 在纯 JVM 测试 `DocumentButtonControlTest.shouldSeparateActiveStateFromFocusVisibleState` 中调用 `widget.render(...)` 后失败。
- Gradle 输出显示 `java.lang.NoClassDefFoundError`，失败点位于测试渲染阶段。

## 触发场景

- 测试创建了自定义 `RecordingUiRenderContext`，只覆写了 `drawSurface(...)` 与 `drawText(...)`。
- HTML-like 渲染链路包含文本、clip 或默认渲染上下文能力时，未覆写的方法会回落到 `UiRenderContext` 默认实现。

## 根本原因

- `UiRenderContext` 默认实现会触达真实运行时依赖，例如 `DefaultFontRendererAdapter`、LWJGL/OpenGL clip 或字体测量路径。
- 纯 JVM 测试环境不应加载这些真实 Minecraft/LWJGL 运行时类。

## 修复方案

- 在测试用 `RecordingUiRenderContext` 中覆写：
  - `drawSurface(...)`
  - `drawText(...)`
  - `measureTextWidth(...)`
  - `getTextLineHeight()`
  - `pushClip(...)`
  - `popClip()`
- 让测试渲染上下文完全停留在记录/桩实现内，避免回落到真实 GL/字体路径。

## 预防措施

- 纯 JVM 测试只要调用 `Widget.render(...)` 或 `DocumentPaintRenderer.render(...)`，测试上下文必须覆盖所有可能触发 GL/字体运行时的方法。
- 涉及 HTML-like 文本测量的测试继续注入确定性 `TextMeasureService`，不要使用默认字体服务。
- 新增渲染测试时优先复用已有完整 recording context，避免临时只覆写一两个方法。
