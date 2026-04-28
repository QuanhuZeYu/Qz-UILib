# AI记忆文档

## 长期稳定信息

### 项目定位

- 本仓库是 Minecraft 1.7.10 / GTNH / LWJGL3ify 环境下的 Java UI 框架工程。
- 当前主线目标已经升级为实现一套完整的 HTML-like UI 渲染框架，而不是继续局部修补文档页控件。
- “完整 HTML-like”在本项目中指具备稳定的文档树、样式计算、盒模型、布局、绘制、裁剪、滚动、效果合成、命中测试与输入分发分层。
- 本项目不直接实现完整浏览器内核，不承诺 HTML5 全量解析、CSS 全量规范、JavaScript DOM API、网络加载或浏览器安全模型。

### 当前稳定架构边界

- 当前可运行链路仍以 retained `Widget` 树为渲染后端，文档页主创建边界为 `UiDocumentScreens`。
- `UiDocumentScreens` 通过 `DocumentScreenEnvironment`、`DocumentScreenDefinition` 与 `DocumentScreenChromeResolver` 暴露显式页面创建入口。
- HTML-like 文档树最小骨架已在 `club.heiqi.uilib.ui.dom` 落地；`UiDocument` 是当前文档作者入口，`DocumentNode` 负责父子关系与 mutation version，`ElementNode` 与 `TextNode` 分别承载元素和文本；`ElementNode` 当前支持最小 active handler、click handler、focus handler、key handler、text input handler 与 focusable 标记，active/click/key/text input 事件会从目标元素向父元素冒泡，focus 事件会带 `focusVisible` 区分键盘可见焦点与鼠标普通焦点。
- HTML-like 基础控件适配已在 `club.heiqi.uilib.ui.dom.control` 起步；`DocumentButtonControl` 当前以 `ElementNode` 为根封装按钮行为，支持 action handler、enabled/disabled、click 激活、Enter/Space 键盘激活、focusable、active 按下态、focus-visible 描边态与基础视觉状态切换；`DocumentTextInputControl` 支持 placeholder、maxLength、文本输入（过滤控制字符）、Backspace 删除、enabled/disabled、focus 边框区分、change handler；`DocumentToggleSwitchControl` 以 flex row + justifyContent 切换 thumb 位置实现开关视觉，支持 click/Enter/Space 切换、enabled/disabled、focus-visible 与 toggle change handler；`DocumentSegmentedSelectorControl` 以一组 element-backed button 表达分段选择器，支持鼠标/键盘选择、enabled/disabled、选中态视觉和 selection handler；`DocumentInventorySlotGridControl` 以 `ElementNode` + 自定义渲染回调实现只读背包格子网格，复用现有 `InventorySlotGridLayout` 与 `InventorySlotGridItemRenderer`，支持 contentProvider、slotGap、preferredSlotSize 等配置。
- HTML-like 样式系统初版已在 `club.heiqi.uilib.ui.style` 落地；`ElementNode.style()` 暴露 inline style 入口，`UiStyleResolver` 负责把元素样式解析为 `ComputedStyle`。
- HTML-like 布局盒初版已在 `club.heiqi.uilib.ui.layout` 落地；`DocumentLayoutEngine` 当前支持元素级 block flow、box model、px/% 长度、auto 高度、`display: none` 过滤、子元素垂直流式排布、直接文本子节点基于 `TextMeasureService` 的测量与换行布局，以及 flex row/column、gap、align、justify、grow/shrink 的最小实现；`DocumentScrollState` 当前负责根据布局盒推导 `overflow: auto` 元素的可滚范围、滚动偏移、滚动条几何与 track/thumb 拖拽状态；`DocumentHitTestEngine` 当前负责在滚动与 overflow clip 语义下查找命中的最深元素。
- HTML-like 绘制命令初版已在 `club.heiqi.uilib.ui.paint` 落地；`DocumentPaintEngine` 当前能把布局盒树转换为 backdrop-filter/background/border/text/clip/scrollbar 中立绘制命令，并保留父元素背后滤镜、父元素背景、父元素边框、结构 clip、滚动后的直接文本换行行、滚动后的子树、clip end、滚动条 track/thumb 的基础 paint order；根元素滚动条保持可见，嵌套 `overflow:auto` 滚动条只在最近有效滚动后的短暂窗口内绘制，空闲后隐藏以避免遮挡内容。
- `DocumentPaintRenderer` 已可把 backdrop-filter/background/border/text/clip/custom/scrollbar paint command 投影到现有 `UiRenderContext`；`UiRenderContext.drawBackdropFilter(...)` 当前只采样当前 UI 主层已经绘制到当前 framebuffer 的内容，不主动读取游戏世界 framebuffer；如果页面壳提前绘制了一张已模糊底图，它会作为普通 UI 背景被采样；复制当前 UI 层区域失败时回退为半透明 tint 与高光描边伪玻璃；`DocumentCustomRenderer` 允许控件在元素背景/边框之后、clip/子树之前注入自定义绘制回调，回调坐标表达元素内容盒，并会随元素自身滚动偏移，供背包格子等复杂控件使用。
- HTML-like 已支持类似 CSS `backdrop-filter` 的首期磨玻璃语义。目标语义是处理同一 UI 主层内元素背后的已绘制 UI 内容，而不是额外模糊游戏画面；宿主/页面壳层若能提供一次已经模糊的背景底图，则该底图只作为普通 UI 背景参与后续元素采样。本项目当前已在样式层暴露高层 `backdropBlurRadius` 与 `backdropSaturation` 语义，在 paint command 中表达 `BACKDROP_FILTER` 请求，并由 `UiRenderContext` 封装 UI 层局部复制、近似 blur 和降级路径；文档作者不得接触 OpenGL/FBO 细节。`HTML-like Smoke` 页当前使用同层采样舞台：先绘制彩色条纹与文字，再用负 margin 让 glass card 覆盖这些 UI 内容，便于直接验收 backdrop 采样是否可见。
- `HtmlLikeDocumentWidget` 已把 `UiDocument -> style -> layout -> paint command -> UiRenderContext` 链路挂接到现有 retained `Widget` 后端；生产构造默认使用 `DefaultTextMeasureService`，测试可注入确定性 `TextMeasureService`；组件现在会消费命中的 `overflow: auto` 元素滚轮事件并让内容区随 `DocumentScrollState` 偏移，同时记录最近有效滚动时间用于嵌套滚动条空闲隐藏，支持根视口或当前可见内部滚动条的 track 点击与 thumb 拖拽，也会将鼠标 active/click 分发给命中的 HTML-like 元素，并维护命中 focusable 元素的内部焦点，接收现有 `UiInputRouter` 转发的 key/text input 后向 HTML-like 元素冒泡；鼠标聚焦元素时不设置 focus-visible，Tab/Shift+Tab 进入或移动焦点时设置 focus-visible；当前 `UiInputRouter` 的 Tab 全局遍历会先让已聚焦 `Widget` 处理内部焦点遍历，HTML-like 组件会按布局树顺序在 focusable 元素之间移动，边界处再交回全局 widget 焦点；组件新增根视口滚动模式，启用后根元素 border box 固定为 widget 视口尺寸，整页滚动由根元素 `overflow:auto` 与 `DocumentScrollState` 承担，避免旧 `DocumentPageWidget` 页面壳因点击/聚焦触发 `scrollDescendantIntoView` 后随机移动；`ui_test` 诊断菜单、`ui_layout_diagnostics` 布局诊断页、`html_like_smoke` 子页、`html_like_glass` 大面积磨玻璃测试页与 `inventory_overview` 背包页的作者层已迁移为单个 `HtmlLikeDocumentWidget` 承载的 HTML-like 文档，并启用根视口滚动。
- 当前 `UiDocumentScreens` 的 definition-backed 生产入口已切换为 `DefinitionBackedHtmlLikeDocumentScreen` + `DirectDocumentPageAuthoringSurface`：已迁移 HTML-like 页面直接把 `HtmlLikeDocumentWidget` 挂到根视口并由 direct surface 计算居中 frame，不再套旧 `DocumentPageWidget`/`ScrollViewportWidget` 页面壳。旧 `BaseDocumentScreen`、`ControllerBackedDocumentScreen`、`DocumentPageWidget` 与 `DocumentPageAuthoringSurface.adapt(...)` 只保留给未迁移/兼容入口和测试。
- `UiScreenHostSession` 在主 UI widget 树渲染前会统一准备稳定 2D GL 状态，避免世界渲染遗留的 depth/cull/alpha/light 状态导致 rounded fill 面片被剔除。
- 后续作者侧入口应继续迁移到 HTML-like 文档/元素/样式 API；底层 `Widget`、`DivWidget`、`ScrollViewportWidget` 应逐步退为 backend adapter 或兼容层。当前已完成键盘/文本输入、基础控件适配、诊断菜单/布局诊断页和背包业务页迁移，已具备开始清退旧 public screen 作者入口的基础；但旧非 DOM 后端和兼容 factory 仍需在替代测试覆盖完成前保留。
- `UiSurfaceStyle` 是纯外观值对象，只负责 `fillColor`、`borderColor`、`cornerRadius`。
- `border-radius` 外观不得隐式控制 descendant clip；结构裁剪必须来自 `overflow`、viewport 或显式 clip 容器。
- `DivWidget` 当前通过 overflow/viewport 盒提供矩形内容裁剪；`ScrollViewportWidget` 仍保留为兼容后端与旧页面壳视觉容器，但当前 HTML-like 页面级滚动不再依赖它，必须优先使用 `HtmlLikeDocumentWidget` 根视口滚动模式与 direct screen host。
- `RoundedScrollViewportWidget` 只保留为 rounded structural clip 探索容器；在真实 GL/stencil/FBO 状态链稳定前，不得接回 `DocumentPageWidget` 生产链路。
- UI 框架不向文档作者暴露 FBO、stencil、OpenGL 状态等宿主细节；背景模糊和磨玻璃由 `UiScreenHostSession`/`UiRenderContext`/宿主效果服务统一处理。HTML-like 样式层只暴露 CSS-like `backdrop-filter` 高层语义，元素级 backdrop 默认只处理当前 UI 主层已绘制内容；游戏画面模糊只允许作为页面壳层提前准备好的背景底图进入 UI 层。
- `UiRenderContext` 继续承载主渲染与 deferred post-main 回放的 clip snapshot；snapshot 表达显式结构裁剪结果，不继承 surface 外观。

### 清退原则

- 与 HTML-like UI 主线不对应的旧规划、示例和入口可以直接重写或移除。
- 不再把 JNI/MSDF 字体 native 方案作为当前 UI 框架主线规划；字体只作为文本渲染能力被 UI 模型消费。
- 不再新增扩大直接 `Widget` 作者入口的 API；如必须新增，应优先放在新文档模型或兼容适配层。
- 不再把 Minecraft GUI 生命周期、OpenGL/FBO/stencil 状态或宿主背景效果泄露给页面作者。
- 清退旧入口时必须保留可编译、可测试的迁移路径，不能在没有替代测试时破坏当前可运行页面。

### 运行与验证

- Windows 环境下使用 PowerShell。
- 当前编译门槛：`./gradlew.bat compileJava`。
- 当前本机 Java 环境已集中到 `D:\.MyApps\.ENV`；默认 `JAVA_HOME` 为 `D:\.MyApps\.ENV\jdk-21.0.10+7`，`PATH` 使用 `D:\.MyApps\.ENV\jdk-21.0.10+7\bin`。
- 当前用户级 `GRADLE_USER_HOME` 为 `D:\.MyApps\.ENV\gradle-home`；必须避开 `C:\Users\泉户 黑崎\.gradle` 这类包含中文与空格的路径，否则 Java 8 工具链启动 Gradle Worker 时可能无法加载 `gradle-worker.jar`。
- 当前 Agent shell 不一定继承用户级 `GRADLE_USER_HOME`；验证命令若发现未设置，应在命令前显式执行 `$env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"`。
- 当前用户级 Gradle 配置位于 `D:\.MyApps\.ENV\gradle-home\gradle.properties`，其中 `org.gradle.java.installations.paths` 显式包含 `D:/.MyApps/.ENV/zulu8.92.0.21-ca-jdk8.0.482-win_x64`、`D:/.MyApps/.ENV/jdk-21.0.10+7`、`D:/.MyApps/.ENV/jdk-25.0.2+10`、`D:/.MyApps/.ENV/jbr-21-intellij`。
- 当前构建环境在用户级 Gradle 配置生效后可直接使用：`./gradlew.bat --no-configuration-cache build`。
- 当前 `runClient21` 在用户级 Gradle 配置生效后可直接使用：`./gradlew.bat --no-configuration-cache runClient21`。
- 原因：GTNH 构建链要求 Azul Zulu JDK 8 工具链；Gradle 通过 Foojay 自动解析/下载 Zulu 8 在本环境下不稳定，因此需要显式提供本地 Zulu 8 路径。
- 本地 Zulu 8 已验证可执行路径：`D:\.MyApps\.ENV\zulu8.92.0.21-ca-jdk8.0.482-win_x64\bin\java.exe`；旧路径 `C:\temp\zulu8\zulu8.92.0.21-ca-jdk8.0.482-win_x64` 已保留为指向该目录的 junction。
- 本机 IDEA 自带 JetBrains JBR 21 + JCEF 通过集中入口 `D:\.MyApps\.ENV\jbr-21-intellij` 提供；该目录是指向 `D:\.MyApps\JetBrain\IntelliJ\jbr` 的 junction。若 Gradle 工具链路径不包含它，`runClient21` 会尝试从 Foojay 下载 `jbrsdk_jcef-21`，网络不稳定时会卡住或失败。
- 若并发启动多个 Gradle 构建，`decompileSrgJar` 可能会因共享 `build/tmp/decompileSrgJar/mc.jar` 触发 Windows 文件锁冲突；验证构建时应串行执行单个 Gradle 命令。
- 最近已验证通过：`./gradlew.bat --no-configuration-cache test` 与 `./gradlew.bat --no-configuration-cache compileJava`。
- 最近已验证通过：集中 Java 环境与 `GRADLE_USER_HOME=D:\.MyApps\.ENV\gradle-home` 下的 `javaToolchains`、`compileMcLauncherJava`、`runClient21 --dry-run`、`runClient21`、`processIdeaSettings`。
- 最近已验证通过的定向测试：`DocumentButtonControlTest`、`DocumentTextInputControlTest`、`DocumentToggleSwitchControlTest`、`DocumentSegmentedSelectorControlTest`、`DocumentInventorySlotGridControlTest`、`HtmlLikeDocumentWidgetTest`、`HtmlLikeSmokeDocumentPageControllerTest`、`UiInputRouterTest`、`DocumentPaintRendererTest`、`UiDocumentScreensTest`、`UiTestDocumentPageControllerTest`、`DocumentPaintEngineTest`、`DocumentLayoutEngineTest`、`UiStyleResolverTest`、`UiDocumentTest`、`UiSurfaceStyleTest`、`DocumentPageWidgetTest`、`InventorySlotGridWidgetTest`；本轮 `backdrop-filter` 定向验证覆盖 `UiStyleResolverTest`、`DocumentPaintEngineTest`、`DocumentPaintRendererTest` 与 `HtmlLikeSmokeDocumentPageControllerTest`。
- 当前 `ui.dom` / `ui.style` / `ui.layout` / `ui.paint` 已经接入 `HtmlLikeDocumentWidget`、诊断菜单、布局诊断页、`html_like_smoke` 子页与背包概览页；这些页面的外层滚动已切到 HTML-like 根元素 `overflow:auto`，且生产入口不再包旧 `DocumentPageWidget` 页面壳，可从游戏内诊断菜单进入对应页面进行真实渲染验收。
- 纯 JVM 测试不得直接触发 `DefaultTextMeasureService`/`FontService` 默认字体运行时；涉及 HTML-like 文本测量的测试应注入确定性 `TextMeasureService`，避免加载 LWJGL 相关类。

### 当前关键文件

- `项目建议.md`
- `src/main/java/club/heiqi/uilib/ui/dom/UiDocument.java`
- `src/main/java/club/heiqi/uilib/ui/dom/DocumentNode.java`
- `src/main/java/club/heiqi/uilib/ui/dom/ElementNode.java`
- `src/main/java/club/heiqi/uilib/ui/dom/DocumentElementActiveEvent.java`
- `src/main/java/club/heiqi/uilib/ui/dom/DocumentElementActiveHandler.java`
- `src/main/java/club/heiqi/uilib/ui/dom/DocumentElementClickEvent.java`
- `src/main/java/club/heiqi/uilib/ui/dom/DocumentElementClickHandler.java`
- `src/main/java/club/heiqi/uilib/ui/dom/TextNode.java`
- `src/main/java/club/heiqi/uilib/ui/dom/control/DocumentButtonControl.java`
- `src/main/java/club/heiqi/uilib/ui/dom/control/DocumentButtonActionEvent.java`
- `src/main/java/club/heiqi/uilib/ui/dom/control/DocumentButtonActionHandler.java`
- `src/main/java/club/heiqi/uilib/ui/dom/control/DocumentTextInputControl.java`
- `src/main/java/club/heiqi/uilib/ui/dom/control/DocumentTextInputChangeEvent.java`
- `src/main/java/club/heiqi/uilib/ui/dom/control/DocumentTextInputChangeHandler.java`
- `src/main/java/club/heiqi/uilib/ui/dom/control/DocumentToggleSwitchControl.java`
- `src/main/java/club/heiqi/uilib/ui/dom/control/DocumentToggleChangeEvent.java`
- `src/main/java/club/heiqi/uilib/ui/dom/control/DocumentToggleChangeHandler.java`
- `src/main/java/club/heiqi/uilib/ui/dom/control/DocumentSegmentedSelectorControl.java`
- `src/main/java/club/heiqi/uilib/ui/dom/control/DocumentSegmentedSelectionEvent.java`
- `src/main/java/club/heiqi/uilib/ui/dom/control/DocumentSegmentedSelectionHandler.java`
- `src/main/java/club/heiqi/uilib/ui/dom/control/DocumentInventorySlotGridControl.java`
- `src/main/java/club/heiqi/uilib/ui/paint/DocumentCustomRenderer.java`
- `src/main/java/club/heiqi/uilib/ui/style/UiStyleDeclaration.java`
- `src/main/java/club/heiqi/uilib/ui/style/UiStyleResolver.java`
- `src/main/java/club/heiqi/uilib/ui/style/ComputedStyle.java`
- `src/main/java/club/heiqi/uilib/ui/style/UiFlexDirection.java`
- `src/main/java/club/heiqi/uilib/ui/style/UiAlignItems.java`
- `src/main/java/club/heiqi/uilib/ui/style/UiJustifyContent.java`
- `src/main/java/club/heiqi/uilib/ui/layout/DocumentLayoutEngine.java`
- `src/main/java/club/heiqi/uilib/ui/layout/DocumentLayoutBox.java`
- `src/main/java/club/heiqi/uilib/ui/layout/DocumentLayoutEdges.java`
- `src/main/java/club/heiqi/uilib/ui/layout/DocumentLayoutTextRun.java`
- `src/main/java/club/heiqi/uilib/ui/layout/DocumentHitTestEngine.java`
- `src/main/java/club/heiqi/uilib/ui/layout/DocumentScrollState.java`
- `src/main/java/club/heiqi/uilib/ui/paint/DocumentPaintEngine.java`
- `src/main/java/club/heiqi/uilib/ui/paint/DocumentPaintCommand.java`
- `src/main/java/club/heiqi/uilib/ui/paint/DocumentPaintCommandType.java`
- `src/main/java/club/heiqi/uilib/ui/paint/DocumentPaintRenderer.java`
- `src/main/java/club/heiqi/uilib/ui/document/HtmlLikeDocumentWidget.java`
- `src/main/java/club/heiqi/uilib/ui/screen/UiDocumentScreens.java`
- `src/main/java/club/heiqi/uilib/ui/screen/DirectDocumentPageAuthoringSurface.java`
- `src/main/java/club/heiqi/uilib/ui/screen/HtmlLikeSmokeDocumentPageController.java`
- `src/main/java/club/heiqi/uilib/ui/screen/HtmlLikeGlassDocumentPageController.java`
- `src/main/java/club/heiqi/uilib/ui/screen/BaseDocumentScreen.java`
- `src/main/java/club/heiqi/uilib/ui/screen/UiScreenHostSession.java`
- `src/main/java/club/heiqi/uilib/ui/input/UiInputRouter.java`
- `src/main/java/club/heiqi/uilib/ui/render/UiRenderContext.java`
- `src/main/java/club/heiqi/uilib/ui/widget/Widget.java`
- `src/main/java/club/heiqi/uilib/ui/control/DivWidget.java`
- `src/main/java/club/heiqi/uilib/ui/control/ScrollViewportWidget.java`
- `src/main/java/club/heiqi/uilib/ui/control/RoundedScrollViewportWidget.java`
- `src/main/java/club/heiqi/uilib/ui/theme/UiSurfaceStyle.java`
- `src/main/java/club/heiqi/uilib/ui/theme/UiDocumentThemes.java`

### 磨玻璃效果规划

- 目标语义应优先模仿浏览器 `backdrop-filter`，而不是 `filter`：前者处理元素背后的已绘制内容，后者处理元素自身和子树。
- 当前已落地分层：style/computed style 保存 `backdropBlurRadius` 与 `backdropSaturation`；layout 提供元素 border/padding/clip 几何；paint 输出 `BACKDROP_FILTER` 命令并保持在元素 background/border/content 之前；renderer 调用 `UiRenderContext.drawBackdropFilter(...)`，该入口复制当前 UI 主层局部区域做近似 blur，失败时回退为半透明 tint 与高光描边伪玻璃。
- 后续宿主真实效果策略：同帧复用 UI 主层快照；按 blur radius 扩张 UI 层采样区域；优先局部 offscreen、downsample 与 separable blur/box blur 近似；限制最大 blur radius 和最大采样尺寸；FBO 或 shader 不可用时继续回退为半透明 tint、高光边和投影的伪玻璃视觉。不要把元素级 backdrop-filter 设计成直接模糊游戏世界画面。
- 验收重点：玻璃区域背后的同层 UI 内容被模糊，元素自身文字和边框保持清晰；如果需要世界背景参与，只能由页面壳层先提供一张已模糊的 UI 背景底图；滚动、resize、圆角裁剪、overflow clip 和多玻璃元素场景不出现错位、泄漏或明显性能抖动。

## 阶段性进度

### 当前已完成

- 已确认当前代码能通过 `compileJava`。
- 已确认文档入口、surface 外观、矩形 viewport clip 与 deferred clip snapshot 相关定向测试通过。
- 已确认 `docs/AI记忆文档.md` 原有稳定边界与当前代码大体一致，但项目长期目标需要升级到完整 HTML-like 渲染框架。
- 已将根目录旧 `项目建议.md` 从 JNI/MSDF 字体建议重写为 HTML-like UI 实施规划。
- 已同步清退原则：不符合当前 UI 渲染主线的旧规划和旧入口允许直接清退。
- 已新增 `ui.dom` 文档树最小骨架和 `UiDocumentTest`，固定元素/文本创建、append 移动语义、跨文档拒绝、循环拒绝、根元素保护与文本叶子节点契约。
- 已新增 `ui.style` 样式系统初版和 `UiStyleResolverTest`，固定 inline style mutation、默认 display、文本颜色继承、显式样式覆盖与长度解析契约。
- 已新增 `DocumentLayoutEngine` 布局盒初版和 `DocumentLayoutEngineTest`，固定 block flow、box model、百分比宽度、display none 过滤与独立 margin 排布契约。
- 已扩展 flex 样式与布局，固定 flex row/column、gap、主轴 grow/shrink、主轴分布与交叉轴对齐契约。
- 已新增 `DocumentPaintEngine` 绘制命令初版和 `DocumentPaintEngineTest`，固定 background/border 命令、paint order、透明/零宽跳过与 border radius clamp 契约。
- 已新增 `DocumentPaintRenderer` 和 `DocumentPaintRendererTest`，固定 paint command 到 `UiRenderContext` 的 background/border 投影契约。
- 已新增 `HtmlLikeDocumentWidget`、`HtmlLikeSmokeDocumentPageController` 和 `html_like_smoke` 页面定义，固定 HTML-like 文档模型到游戏内诊断子页的最小可见集成契约。
- 已根据游戏内截图修复 smoke 页 rounded fill 仅见边框的问题，错误记录见 `docs/errors/ERROR-20260426-ui-rounded-fill-cull-state.md`。
- 已为 HTML-like paint command 增加 `CLIP_START` / `CLIP_END` 和 renderer 回放，`overflow: hidden/auto` 会在子树绘制前后压入/弹出结构裁剪。
- 已为 HTML-like 直接文本子节点增加 `DocumentLayoutTextRun`、`TEXT` paint command 和 renderer 回放，当前可通过 `TextMeasureService` 把多行换行文本绘制进 HTML-like 布局盒。
- 已为 HTML-like `overflow: auto` 增加 `DocumentScrollState`、绘制阶段内容偏移与 `HtmlLikeDocumentWidget` 滚轮消费；当前滚动会保持元素自身背景、边框和裁剪框固定，只移动该元素的文本与子树内容。
- 已通过游戏内 `HTML-like Smoke` 页面确认 teal 卡片块内文本滚动操作符合预期：滚轮滚动时卡片背景、边框和裁剪区域保持固定，内部文本内容移动。
- 已为 HTML-like 增加 `DocumentHitTestEngine` 与元素 click handler 最小模型；当前可在滚动/overflow clip 后的视觉位置上查找最深元素，并由 `HtmlLikeDocumentWidget` 在鼠标 down/up 后把 click 事件从 target 向父元素冒泡。
- 已为 `HTML-like Smoke` 增加可见 click 验收目标：底部绿色 `Click target: 0` pill 被点击后应显示 `Click target: 1` 并切换为蓝色，便于游戏内验证元素 click 分发。
- 已为 HTML-like 增加最小焦点与键盘/文本输入模型：`ElementNode` 可标记 focusable 并注册 focus/key/text input handler；`HtmlLikeDocumentWidget` 点击命中元素后维护内部焦点，widget 失焦时清空内部焦点，key/text input 事件从聚焦元素向父元素冒泡。
- 已为 `HTML-like Smoke` 增加可见文本输入验收目标：底部红色 `Type target: click then type` pill 被点击后变为橙色，输入可见字符后显示 `Type target: ...`，Backspace 按下与长按重复事件都会删除最后一个 codepoint。
- 已为 HTML-like 增加内部 Tab 焦点遍历：`Widget` 提供焦点遍历进入/内部遍历钩子，`UiInputRouter` 在全局 Tab 切换前优先交给当前聚焦 widget 处理，`HtmlLikeDocumentWidget` 按布局树顺序遍历 focusable 元素；`HTML-like Smoke` 增加 `Tab target` pill 作为可见验收目标。
- 已为 HTML-like 增加首个 element-backed 控件适配：`DocumentButtonControl` 封装按钮 action、enabled/disabled、鼠标 click、Enter/Space 键盘激活与焦点视觉状态；`HTML-like Smoke` 增加 `Button ctrl` 按钮控件可见验收目标。
- 已区分 HTML-like 控件的普通焦点、focus-visible 键盘焦点与 active 按下态：鼠标聚焦不显示键盘描边，Tab/Shift+Tab 焦点显示描边，鼠标按下/键盘按下使用 active 背景，松开后恢复。
- 已记录纯 JVM 渲染测试未完整覆写 `UiRenderContext` 导致误触 GL/字体运行时的问题，错误记录见 `docs/errors/ERROR-20260427-jvm-test-render-context-gl.md`。
- 已记录纯 JVM 测试误触默认字体服务导致 LWJGL 类加载失败的问题，错误记录见 `docs/errors/ERROR-20260426-jvm-test-default-font-service.md`。
- 已确认 IDEA 环境下 `runClient21` 可运行，但本地 Gradle/IDEA 工具链必须显式包含 `D:\.MyApps\JetBrain\IntelliJ\jbr` 与 Zulu 8，否则会在配置阶段访问 GitHub manifest 或下载 JBR 21 时失败。
- 已整理本机 Java 环境到 `D:\.MyApps\.ENV`，并通过用户级 Gradle 配置固定 Zulu 8、JDK 21、JDK 25 与 IntelliJ JBR 21 的工具链路径。
- 已确认 `compileMcLauncherJava` 在 `GRADLE_USER_HOME=D:\.MyApps\.ENV\gradle-home` 下通过，修复 Java 8 Worker 读取中文用户名路径下 Gradle worker 缓存失败的问题。
- 已完成对 2026-04-26 23:57 以来 HTML-like 控件和背包诊断页提交的集中审查，并修复 CUSTOM paint 内容盒坐标、滚动偏移、custom renderer 变更缓存失效，以及迁移页测试未真实触发返回按钮的问题；错误记录见 `docs/errors/ERROR-20260427-custom-paint-content-box.md`。
- 本轮审查修复后已验证通过：`./gradlew.bat --no-configuration-cache test` 与 `./gradlew.bat --no-configuration-cache compileJava`。
- 已将可访问的诊断菜单页、布局诊断页、HTML-like Smoke 页与背包概览页作者层迁移为 HTML-like 文档，页面控制器不再直接拼装旧 `DocumentTextWidget`、`DocumentCardWidget`、`ButtonWidget`、`DocumentToolbarWidget` 或 `DocumentFlowRowWidget`；旧 `InventoryOverviewDocumentPageController` 及其测试已删除，背包入口改为 `HtmlLikeInventoryOverviewDocumentPageController`。
- 本轮 HTML-like 页面迁移后已验证通过：`./gradlew.bat --no-configuration-cache test` 与 `./gradlew.bat --no-configuration-cache compileJava`。
- 已新增 `DocumentLayoutEngine.layoutViewportRoot(...)` 与 `HtmlLikeDocumentWidget#setViewportRootScrollingEnabled(true)`，四个当前 HTML-like 页面均使用 percent 高度填满页面壳，并由文档根元素 `overflow:auto` 承担整页滚动；已补充点击 HTML-like 焦点元素不再触发旧页面壳滚动的回归测试，错误记录见 `docs/errors/ERROR-20260427-html-root-scroll-focus.md`。
- 已为 HTML-like `overflow:auto` 增加默认滚动条绘制命令与 renderer 投影，滚动条 track/thumb 由 paint command 绘制，避免根视口滚动迁移后页面能滚但没有任何可见滚动提示；错误记录见 `docs/errors/ERROR-20260427-html-scrollbar-missing.md`。
- 已将 HTML-like 嵌套 `overflow:auto` 滚动条改为空闲自动隐藏：根元素滚动条保持可见，内部滚动块只在最近有效滚动后的短暂窗口内绘制 track/thumb，避免 Smoke 页 teal 卡片这类内部滚动条长期遮挡文本；错误记录见 `docs/errors/ERROR-20260427-html-nested-scrollbar-idle-cover.md`。
- 已为 HTML-like 滚动条补齐 track 点击与 thumb 拖拽交互，`DocumentScrollState` 统一提供滚动条几何与拖拽状态，`DocumentPaintEngine` 复用同一份几何避免绘制/交互坐标漂移；真实输入路由测试已覆盖拖拽根滚动条和点击 track 不透传元素 click。
- 已新增 `DocumentSegmentedSelectorControl` 分段选择器控件，并将布局诊断页原本内嵌的临时 `SegmentControl` 迁移到该 HTML-like 控件层；真实 widget 适配测试覆盖鼠标选择、键盘选择、禁用态、程序化选择不触发事件和选中态视觉。
- 已为 HTML-like 增加 `backdropBlurRadius` 与 `backdropSaturation` 样式声明、computed style 默认值、`BACKDROP_FILTER` paint command 与 `UiRenderContext.drawBackdropFilter(...)` 渲染入口；当前默认渲染复制当前 UI 主层局部区域做近似 blur，复制失败时提供伪玻璃降级，HTML-like Smoke 页新增 `Backdrop glass overlap: blur 14px / saturate 140%` 同层重叠验收卡片，定向测试已覆盖 style resolver、paint order、renderer 投影与 smoke 页面集成。
- 已把 `UiDocumentScreens` 当前 definition-backed 生产入口切换到 direct HTML-like screen host，`ui_test`、`ui_layout_diagnostics`、`html_like_smoke` 与 `inventory_overview` 不再套旧 `DocumentPageWidget` 页面壳；旧页面壳类与 adapter 暂保留为兼容层。
- 已新增 `html_like_glass` 大面积磨玻璃测试页，可从诊断菜单进入；页面使用多行彩色 UI 采样场和覆盖大部分区域的 glass slab，固定 `blur 24px / saturate 160%`，用于放大观察 UI 层 backdrop 采样、裁剪泄漏、采样错位和 resize 下的稳定性。

### 当前阶段目标

- 阶段 0：完成规划与文档清理，保留现有可运行链路。
- 阶段 1：新增 HTML-like 文档树与作者入口最小骨架；当前最小骨架已完成，下一步应把样式入口挂到元素层。
- 阶段 2：新增样式系统与 computed style 初版；当前 inline style 与基础 computed style 已完成，后续需要扩展样式属性集并接入 layout invalidation。
- 阶段 3：建立 box/layout tree，并逐步把现有 Div-like 布局能力迁移到新模型；当前 block flow、flex flow 与直接文本测量/换行布局最小闭环已完成，后续应推进更完整的 inline layout。
- 阶段 4：建立 paint command、clip、scroll、deferred replay 与效果合成的统一渲染模型；当前 background/border/text/clip/backdrop-filter paint command、`UiRenderContext` 投影、`overflow: auto` 滚动偏移、滚动条绘制/交互、命中测试与最小 smoke screen 集成已完成，下一步可推进更稳定的 UI 主层 backdrop 采样模糊服务或更完整 inline layout。
- 阶段 5：迁移事件与控件适配；当前已完成元素 active/click 冒泡、普通焦点/focus-visible 区分、键盘按键、文本输入、Tab/Shift+Tab 内部焦点遍历、按钮/文本输入框/开关/分段选择器/背包格子控件适配，后续需要更多基础控件适配、真实页面迁移与更完整的可访问性语义。
- 阶段 6：清退旧 public screen 构造入口与直接 widget authoring 示例；当前可访问诊断/业务页面的生产宿主已改为 direct HTML-like screen host，下一步应清点剩余 `BaseDocumentScreen`/`ControllerBackedDocumentScreen`/旧 retained widget 入口并按测试覆盖逐步删除或降级为兼容层。

### 下一步执行项

- 下一步可优先推进更稳定的 UI 主层 backdrop 采样模糊服务：在 `UiRenderContext`/宿主效果层接入可复用 UI 层快照、圆角裁剪、blur/saturate 合成、采样尺寸限制和 FBO 不可用降级；也可继续清点剩余旧作者入口、补齐更完整 inline layout、列表/下拉类基础控件与可访问性语义。
- 旧非 DOM 后端暂时不能整体舍弃；现在已达到进入旧作者入口清退阶段的最低条件，但 `DocumentUiScope` 旧 factory、基础 retained widget、`DocumentPageWidget` 兼容壳、测试夹具和兼容页面仍需保留到替代覆盖完成。
- 游戏内实际验证入口已就绪：按右 Shift 打开诊断菜单页，可直接验证 HTML-like 诊断菜单、布局诊断页、HTML-like Smoke 子页、Large Glass Lab 子页和背包概览页。Smoke 页重点观察实心填充、圆角边框、overflow-hidden 裁剪、文本换行、可滚动 teal 卡片、同层条纹/文字被 glass card 覆盖后的 backdrop blur/saturate、click/text input/Tab/button/toggle 交互；Glass Lab 页重点观察大面积 glass slab 覆盖彩色采样场后的 blur/saturate、边缘圆角、裁剪泄漏、采样错位和 resize 稳定性；布局诊断页重点观察页面宽度、HTML-like 页面滚动偏移、HTML-like 自滚动探针、滚动条 track/thumb、性能文案和高频变更探针；背包页重点观察 hotbar/backpack 网格、自定义格子绘制和返回按钮交互。重点回归：点击任意 HTML-like 控件或卡片不应导致整个页面随机跳动，只有滚轮命中的 HTML-like `overflow:auto` 元素才应改变滚动偏移，内部滚动块的滚动条应在停止滚动后自动隐藏，可见滚动条的 track 点击与 thumb 拖拽应能改变对应元素滚动偏移且不触发底层元素 click。
