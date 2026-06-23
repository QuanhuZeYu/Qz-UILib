# ERROR-20260427-jvm-test-render-context-gl

## 错误现象

- 在纯 JVM 测试 `DocumentButtonControlTest.shouldSeparateActiveStateFromFocusVisibleState` 中调用 `widget.render(...)` 后失败。
- Gradle 输出显示 `java.lang.NoClassDefFoundError`，失败点位于测试渲染阶段。
- 2026-04-28 增加 paint context FBO 合成时再次触发同类问题：`HtmlLikeSmokeDocumentPageControllerTest` 与 `HtmlLikeGlassDocumentPageControllerTest` 在 inactive paint context 弹出后调用默认 `UiRenderContext.applyCurrentClip()
  `，导致纯 JVM 测试加载 `org.lwjgl.opengl.GL11` 失败。

## 触发场景

- 测试创建了自定义 `RecordingUiRenderContext`，只覆写了 `drawSurface(...)` 与 `drawText(...)`。
- HTML-like 渲染链路包含文本、clip 或默认渲染上下文能力时，未覆写的方法会回落到 `UiRenderContext` 默认实现。
- paint context 这类“运行时可降级”的默认实现即使没有真正创建 FBO，也可能在清理/恢复路径触发 GL clip 状态。

## 根本原因

- `UiRenderContext` 默认实现会触达真实运行时依赖，例如 `DefaultFontRendererAdapter`、LWJGL/OpenGL clip 或字体测量路径。
- 纯 JVM 测试环境不应加载这些真实 Minecraft/LWJGL 运行时类。
- 本次新增的 `popPaintContext()` 无条件调用 `applyCurrentClip()`，没有区分真实离屏层与 inactive/fallback paint context，导致降级路径仍触发 GL。

## 修复方案

- 在测试用 `RecordingUiRenderContext` 中覆写：
  - `drawSurface(...)`
  - `drawText(...)`
  - `measureTextWidth(...)`
  - `getTextLineHeight()`
  - `pushClip(...)`
  - `popClip()`
- 让测试渲染上下文完全停留在记录/桩实现内，避免回落到真实 GL/字体路径。
- 对运行时代码修复：`PaintContextCompositor.popPaintContext()` 返回是否真的弹出了活跃离屏层，只有真实发生 FBO 切换时 `UiRenderContext.popPaintContext()` 才恢复 GL clip；inactive/fallback 路径不触碰 GL。

## 预防措施

- 纯 JVM 测试只要调用 `Widget.render(...)` 或 `DocumentPaintRenderer.render(...)`，测试上下文必须覆盖所有可能触发 GL/字体运行时的方法。
- 涉及 HTML-like 文本测量的测试继续注入确定性 `TextMeasureService`，不要使用默认字体服务。
- 新增渲染测试时优先复用已有完整 recording context，避免临时只覆写一两个方法。
- 新增可降级渲染能力时，降级路径和 no-op/inactive 清理路径也必须避免触达 GL；不能只把主执行路径包在 try/catch 内。
