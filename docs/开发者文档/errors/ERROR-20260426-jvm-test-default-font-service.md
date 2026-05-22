# JVM 测试误触默认字体服务

## 错误现象

- HTML-like 文本换行改造后，`HtmlLikeDocumentWidgetTest` 与 `HtmlLikeSmokeDocumentPageControllerTest` 在纯 JVM 测试中失败。
- 典型报错为 `java.lang.NoClassDefFoundError: org/lwjgl/BufferUtils`，调用链来自 `DefaultTextMeasureService.getEpoch()` -> `FontService` -> `FontBatchRenderer` -> `FontRenderStateGuard`。

## 触发场景

- `HtmlLikeDocumentWidget` 默认使用 `DefaultTextMeasureService`。
- 纯 JVM 单元测试渲染 HTML-like widget 时，布局缓存读取 `textMeasureService.getEpoch()`，从而初始化字体服务。
- 测试 classpath 中没有完整 LWJGL 运行时类，导致字体渲染相关类加载失败。

## 根本原因

- 默认字体测量服务虽然只打算服务布局测量，但当前 `FontService` 静态初始化会构造包含 LWJGL 依赖的渲染对象。
- 单元测试不应依赖真实字体/渲染运行时；HTML-like widget 测试需要使用可控的 `TextMeasureService` 测试替身。

## 修复方案

- 保留生产构造路径默认使用 `DefaultTextMeasureService`。
- 为 `HtmlLikeDocumentWidget` 和 `HtmlLikeSmokeDocumentPageController` 提供可注入 `TextMeasureService` 的构造路径。
- 纯 JVM 测试使用确定性的 `TextMeasureService`，隔离真实字体服务和 LWJGL 类加载。

## 预防措施

- 后续凡是布局、绘制或页面控制器测试需要文本测量，都应注入测试专用 `TextMeasureService`。
- 不要在纯 JVM 测试里直接调用默认字体服务、Minecraft 客户端或 LWJGL 相关路径。
- 若新增默认运行时依赖，应同时检查测试是否有可替换接口或构造注入点。
