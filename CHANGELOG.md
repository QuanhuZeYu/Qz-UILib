# CHANGELOG

本项目采用 [Keep a Changelog](https://keepachangelog.com/) 风格记录变更，版本号遵循
`主.次.修订[-标签]` 格式：主版本号变更代表破坏性 API 调整，次版本号代表能力扩展，
修订号代表行为修复或文档调整。

## [4.1.0-LTS] 待发布

第一版长期支持版本。覆盖发布前 P0 / P1 / P2 阶段的全量审查与修补，公共 API 边界
确定，文档与实现完成对齐。后续 4.1.x 仅做兼容性修复，不引入破坏性变更。

### 新增

- 浏览器语义示例页扩展：补全 hover、focus、active、文本排版、滚动条与 ESC 默认行为
  等浏览器一致行为的展示与回归。
- 动画能力 Phase 2 / Phase 3：补齐 transform、box-shadow、backdrop-filter、cubic-bezier
  与 `steps()` 缓动；transition 通过 `DocumentTransitionSpec` 支持 per-property
  duration / delay / timing；keyframe 支持 `animation-direction`、无限迭代与
  `ElementNode.animate(...)` 命令式启动。新增 transitionstart / transitioncancel /
  animationstart / animationiteration 事件派发链路。
- 设置页核心控件四件套：复选框、单选组、滑块（含小数滑块自动附文本输入）、标签页。
  数值属性绑定支持自动滑条与文本输入兜底；`draggable=true` 元素默认应用
  `cursor: pointer`。
- HTML-like 语义元素：`document.a()` / `ul()` / `ol()` / `li()` / `img()` / `table()`
  提供最小可用语义闭环；`img` 支持 width / height 属性与远程位图缓存；`a[href]` 走
  片段跳转 + `setLinkActivationHandler(...)` 业务回调。
- 字体排序控件：可视化重排、分页、搜索、序号输入；写回到 Forge `Property` 列表。
- UI 框架结构审查展示页：以可滚动看板呈现分层链路、优先级、热区。
- 运行时自检页：在游戏内对 FontService reload / fallback / 异步线程拦截、
  ForgeConfigTemplate 冷构造、DocumentRemoteImageCache 关停做现场断言。失败立即抛出
  `IllegalStateException`，由 Minecraft 崩溃面板捕获完整堆栈。
- 远程位图缓存：`http(s)://` URL 通过 `DocumentRemoteImageCache` 异步下载并按 LRU 驱逐。
- LGPL v3 开源许可证。

### 修改

- 全部 god class 完成结构拆分：`UiRenderContext`、`ElementNode`、
  `DocumentLayoutEngine`、`DocumentAnimationTimeline`、`HtmlLikeDocumentWidget` 各拆为
  3-5 个协作类；layout helper 按 Flex / Table / Inline / Positioned / Text 分模块。
- 布局热路径：减少重复测量、提取测量缓存、关闭非必要的二次相对偏移计算。
- 公共 API 收口：诊断页工厂方法降为 package-private，仅 `/qzuilib test` 可调起；
  `internal` 包类加 `@apiNote` 标记 LTS 不承诺；`UiHudDocumentHost` 的钩子方法加
  `@apiNote 仅供框架内部 forge 事件钩子调用` 警告。
- HUD 文档层：`PASSIVE` / `INTERACTIVE` 两层语义在 javadoc 与文档中明确分列可见性
  与输入语义；INTERACTIVE 层只在 `GuiContainer` 子类宿主下且鼠标已释放时可交互。
- 字体重载链路：reload 拦截非渲染主线程调用，避免 worker 线程释放 GL 资源触发
  "No context is current" 致命崩溃。
- 文档体系重构为四条路线：使用文档（外部接入）、开发者文档（内部架构）、
  reviews（审查报告）、errors（错误记录）。
- README 默认提供英文版本，提供中文跳转。
- 默认控件附加 web 语义鼠标指针样式（pointer / text / move / not-allowed 等）。
- `flex align-items: baseline` 当前等价于 START，新增 `LOG.warn` 一次性提示。

### 修复

- 单行输入框强制 `white-space: nowrap` 修复光标错位。
- 字体排序还原至主配置页并修复拖拽时锁顶部条目。
- 滑动条拖拽释放后正确提交。
- 键盘默认行为取消语义（preventDefault / stopPropagation）。
- 语义展示页 focus 崩溃；hover 与文本排版的视觉细节。
- 字体生成调度器在重入时通过 `awaitTermination` + 代际隔离保证旧任务不会写入新
  `GlyphPageManager`；`GlyphPage` 零数据 buffer 不再跨实例共享。
- `FontShaderProgram.loadProgram` 在编译 / 链接异常时通过 try/finally 释放 vertex /
  fragment shader 与新建 program。
- `UiMainLayerSnapshotService` 增加 32 槽 snapshot 池上限与按帧驱逐策略，避免异常
  关屏导致 GL 纹理 / FBO 持续增长。
- `DocumentRemoteImageCache.trimCacheIfNeeded` 加 `AtomicBoolean` 守门防并发驱逐；
  FIFO 改为按 `lastAccessedAt` 最旧驱逐的 LRU 策略。
- `UiLayoutInvalidationRegistry` 改为显式 `LOCK` 对象 + 锁外触发 `invalidateLayoutTree`，
  避免持锁回调引发死锁。
- `CodepointTextCache` BMP 路径加 `synchronized` 保证并发可见性。
- `SystemDocumentCursorHost` 反射降级路径改为 `AtomicBoolean` + 一次性 `LOG.debug`，
  并修复字段声明顺序避免静态初始化 NPE。
- `UiInputService` / `UiNativeTextInputInspector` / `ForgeConfigTemplateScreen`
  反射 ignored 块改为按字段去重的 `LOG.debug` 一次性日志。

### 移除

- `DocumentLinkActivationEvent.markHandled()` / `isHandled()`：v4.0 已 `@Deprecated`，
  本版正式删除。改用 `preventDefault()` / `isDefaultPrevented()` 与浏览器原生事件保持
  一致。

### 资源生命周期

- 新增 `ClientProxy` 关停链路：JVM `Runtime.addShutdownHook` 先关停
  `DocumentRemoteImageCache` 再关停 `FontService`；客户端断连
  （`FMLNetworkEvent.ClientDisconnectionFromServerEvent`）触发 HUD 注册表清理。
- 三个内部线程池（`FontService`、`GlyphGenerationDispatcher`、
  `DocumentRemoteImageCache`）均提供显式 `shutdown()` + 2 秒 `awaitTermination`。
- `ShaderProgramSupport.compileShader` 在编译失败时通过 try/finally 调用
  `glDeleteShader`，避免 GL 对象泄漏。

### 测试

- 新增 `FlexLayoutHelperBoundaryTest` / `TableLayoutHelperBoundaryTest` /
  `InlineLayoutHelperBoundaryTest` / `PositionedLayoutHelperBoundaryTest`：覆盖
  helper 拆分后的负尺寸、嵌套、auto cross-size 边界用例。
- 新增 `FontServiceLayoutRuntimeSmokeTest`：覆盖 `ensureLayoutRuntimeReady`
  幂等性。
- ForgeConfigTemplate 冷构造、FontService reload 三场景由"运行时自检页"在真机
  GL context 下覆盖。

### 构建与发布

- `jitpack.yml` 补 `install: ./gradlew --no-configuration-cache --no-daemon -x test
  publishToMavenLocal` 步骤，验证 JitPack 真能完整跑通构建。
- 当前发布渠道：JitPack + GTNH Maven。Modrinth / CurseForge 项目 ID 暂留空，未来
  补丁版按需补全。

---

## [4.0.0] - [4.0.20-beta] 历史预发布

`4.0.0` ~ `4.0.20-beta` 为 4.0 系列的能力开发与 god class 拆分阶段，未对外承诺
LTS 稳定性。本仓库自 `4.1.0-LTS` 起开始按 LTS 标准维护。

[4.1.0-LTS]: https://github.com/QuanHu1995/Qz-UILib/releases/tag/4.1.0-LTS
