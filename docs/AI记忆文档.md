# AI记忆文档

## 长期稳定信息

### 项目定位

- 本仓库是 Minecraft 1.7.10 / GTNH / LWJGL3ify 环境下的 Java UI 框架工程。
- 当前主线目标已经升级为实现一套完整的 HTML-like UI 渲染框架，而不是继续局部修补文档页控件。
- “完整 HTML-like”在本项目中指具备稳定的文档树、样式计算、盒模型、布局、绘制、裁剪、滚动、命中测试与输入分发分层。
- 本项目不直接实现完整浏览器内核，不承诺 HTML5 全量解析、CSS 全量规范、JavaScript DOM API、网络加载或浏览器安全模型。

### 当前稳定架构边界

- 当前可运行链路仍以 retained `Widget` 树为渲染后端，文档页主创建边界为 `UiDocumentScreens`。
- `UiDocumentScreens` 通过 `DocumentScreenEnvironment`、`DocumentScreenDefinition` 与 `DocumentScreenChromeResolver` 暴露显式页面创建入口。
- HTML-like 文档树最小骨架已在 `club.heiqi.uilib.ui.dom` 落地；`UiDocument` 是当前文档作者入口，`DocumentNode` 负责父子关系与 mutation version，`ElementNode` 与 `TextNode` 分别承载元素和文本；`ElementNode` 当前支持最小 active handler、click handler、focus handler、key handler、text input handler 与 focusable 标记，active/click/key/text input 事件会从目标元素向父元素冒泡，focus 事件会带 `focusVisible` 区分键盘可见焦点与鼠标普通焦点。
- HTML-like 基础控件适配已在 `club.heiqi.uilib.ui.dom.control` 起步；`DocumentButtonControl` 当前以 `ElementNode` 为根封装按钮行为，支持 action handler、enabled/disabled、click 激活、Enter/Space 键盘激活、focusable、active 按下态、focus-visible 描边态与基础视觉状态切换；`DocumentTextInputControl` 支持 placeholder、maxLength、文本输入（过滤控制字符）、Backspace 删除、enabled/disabled、focus 边框区分、change handler；`DocumentToggleSwitchControl` 以 flex row + justifyContent 切换 thumb 位置实现开关视觉，支持 click/Enter/Space 切换、enabled/disabled、focus-visible 与 toggle change handler；`DocumentInventorySlotGridControl` 以 `ElementNode` + 自定义渲染回调实现只读背包格子网格，复用现有 `InventorySlotGridLayout` 与 `InventorySlotGridItemRenderer`，支持 contentProvider、slotGap、preferredSlotSize 等配置。
- HTML-like 样式系统初版已在 `club.heiqi.uilib.ui.style` 落地；`ElementNode.style()` 暴露 inline style 入口，`UiStyleResolver` 负责把元素样式解析为 `ComputedStyle`。
- HTML-like 布局盒初版已在 `club.heiqi.uilib.ui.layout` 落地；`DocumentLayoutEngine` 当前支持元素级 block flow、box model、px/% 长度、auto 高度、`display: none` 过滤、子元素垂直流式排布、直接文本子节点基于 `TextMeasureService` 的测量与换行布局，以及 flex row/column、gap、align、justify、grow/shrink 的最小实现；`DocumentScrollState` 当前负责根据布局盒推导 `overflow: auto` 元素的可滚范围和滚动偏移；`DocumentHitTestEngine` 当前负责在滚动与 overflow clip 语义下查找命中的最深元素。
- HTML-like 绘制命令初版已在 `club.heiqi.uilib.ui.paint` 落地；`DocumentPaintEngine` 当前能把布局盒树转换为 background/border/text/clip 中立绘制命令，并保留父元素背景、父元素边框、结构 clip、滚动后的直接文本换行行、滚动后的子树、clip end 的基础 paint order。
- `DocumentPaintRenderer` 已可把 background/border/text/clip/custom paint command 投影到现有 `UiRenderContext`；`DocumentCustomRenderer` 允许控件在元素背景/边框之后、clip/子树之前注入自定义绘制回调，供背包格子等复杂控件使用。
- `HtmlLikeDocumentWidget` 已把 `UiDocument -> style -> layout -> paint command -> UiRenderContext` 链路挂接到现有 retained `Widget` 后端；生产构造默认使用 `DefaultTextMeasureService`，测试可注入确定性 `TextMeasureService`；组件现在会消费命中的 `overflow: auto` 元素滚轮事件并让内容区随 `DocumentScrollState` 偏移，也会将鼠标 active/click 分发给命中的 HTML-like 元素，并维护命中 focusable 元素的内部焦点，接收现有 `UiInputRouter` 转发的 key/text input 后向 HTML-like 元素冒泡；鼠标聚焦元素时不设置 focus-visible，Tab/Shift+Tab 进入或移动焦点时设置 focus-visible；当前 `UiInputRouter` 的 Tab 全局遍历会先让已聚焦 `Widget` 处理内部焦点遍历，HTML-like 组件会按布局树顺序在 focusable 元素之间移动，边界处再交回全局 widget 焦点；`html_like_smoke` 子页已可通过诊断菜单进入，用于游戏内真实可见渲染验收，当前页面包含 overflow-hidden 裁剪样例、HTML-like 文本换行样例、可滚动 teal 卡片样例、可点击 `Click target` pill 样例、可输入 `Type target` pill 样例、可 Tab 聚焦 `Tab target` pill 样例、`Button ctrl` 按钮控件样例与 `Toggle ctrl` 开关控件样例。
- `UiScreenHostSession` 在主 UI widget 树渲染前会统一准备稳定 2D GL 状态，避免世界渲染遗留的 depth/cull/alpha/light 状态导致 rounded fill 面片被剔除。
- 后续作者侧入口应逐步迁移到 HTML-like 文档/元素/样式 API；底层 `Widget`、`DivWidget`、`ScrollViewportWidget` 应逐步退为 backend adapter 或兼容层。当前旧非 DOM 代码尚不能正式舍弃，至少需要完成键盘/文本输入、基础控件适配和一个真实诊断/业务页面迁移后再通知用户进入清退阶段。
- `UiSurfaceStyle` 是纯外观值对象，只负责 `fillColor`、`borderColor`、`cornerRadius`。
- `border-radius` 外观不得隐式控制 descendant clip；结构裁剪必须来自 `overflow`、viewport 或显式 clip 容器。
- `DivWidget` 当前通过 overflow/viewport 盒提供矩形内容裁剪；`ScrollViewportWidget` 负责滚动视口结构裁剪。
- `RoundedScrollViewportWidget` 只保留为 rounded structural clip 探索容器；在真实 GL/stencil/FBO 状态链稳定前，不得接回 `DocumentPageWidget` 生产链路。
- UI 框架本身不暴露 backdrop/effect/blur 给文档作者层；背景模糊由 `UiScreenHostSession` 在 UI 主渲染前通过宿主级 `UiHostBackgroundBlurRenderer` 统一处理。
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
- 最近已验证通过：`compileJava`。
- 最近已验证通过：集中 Java 环境与 `GRADLE_USER_HOME=D:\.MyApps\.ENV\gradle-home` 下的 `javaToolchains`、`compileMcLauncherJava`、`runClient21 --dry-run`、`runClient21`、`processIdeaSettings`。
- 最近已验证通过的定向测试：`DocumentButtonControlTest`、`DocumentTextInputControlTest`、`DocumentToggleSwitchControlTest`、`DocumentInventorySlotGridControlTest`、`HtmlLikeDocumentWidgetTest`、`HtmlLikeSmokeDocumentPageControllerTest`、`UiInputRouterTest`、`DocumentPaintRendererTest`、`UiDocumentScreensTest`、`UiTestDocumentPageControllerTest`、`DocumentPaintEngineTest`、`DocumentLayoutEngineTest`、`UiStyleResolverTest`、`UiDocumentTest`、`UiSurfaceStyleTest`、`DocumentPageWidgetTest`、`InventorySlotGridWidgetTest`。
- 当前 `ui.dom` / `ui.style` / `ui.layout` / `ui.paint` 已经接入 `HtmlLikeDocumentWidget` 和 `html_like_smoke` 子页；可从游戏内诊断菜单进入 smoke 页进行真实渲染验收。
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
- `src/main/java/club/heiqi/uilib/ui/screen/HtmlLikeSmokeDocumentPageController.java`
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

### 当前阶段目标

- 阶段 0：完成规划与文档清理，保留现有可运行链路。
- 阶段 1：新增 HTML-like 文档树与作者入口最小骨架；当前最小骨架已完成，下一步应把样式入口挂到元素层。
- 阶段 2：新增样式系统与 computed style 初版；当前 inline style 与基础 computed style 已完成，后续需要扩展样式属性集并接入 layout invalidation。
- 阶段 3：建立 box/layout tree，并逐步把现有 Div-like 布局能力迁移到新模型；当前 block flow、flex flow 与直接文本测量/换行布局最小闭环已完成，后续应推进更完整的 inline layout。
- 阶段 4：建立 paint command、clip、scroll、deferred replay 的统一渲染模型；当前 background/border/text/clip paint command、`UiRenderContext` 投影、`overflow: auto` 最小滚动偏移、命中测试与最小 smoke screen 集成已完成，下一步可推进滚动条可视化或更完整 inline layout。
- 阶段 5：迁移事件与控件适配；当前已完成元素 active/click 冒泡、普通焦点/focus-visible 区分、键盘按键、文本输入、Tab/Shift+Tab 内部焦点遍历、按钮/文本输入框/开关/背包格子控件适配，后续需要选择器等更多基础控件适配、真实页面迁移与更完整的可访问性语义。
- 阶段 6：清退旧 public screen 构造入口与直接 widget authoring 示例。

### 下一步执行项

- 下一步可优先推进 HTML-like 选择器/背包格子等基础控件适配，或为 `overflow: auto` 增加滚动条绘制/拖拽交互。
- 旧非 DOM 代码暂时不能舍弃；达到“可正式舍弃旧代码”的最低条件是：HTML-like 输入/控件链路可替代现有作者侧常用控件、至少一个真实诊断/业务页面迁移完成且相关测试与游戏内验收通过。达到该条件时必须明确通知用户。
- 游戏内实际验证入口已就绪：按右 Shift 打开诊断菜单页，再进入 `HTML-like Smoke` 子页，观察 HTML-like 色块是否按实心填充、圆角边框、header 内部超宽粉色条被裁剪、卡片内 HTML-like 文本被绘制并在窄卡片中换行；teal 卡片块内文本滚动已验证通过；底部绿色 `Click target: 0` pill 可用于验证 click 分发，点击后应变为 `Click target: 1` 且背景变蓝；文本输入框（`DocumentTextInputControl`）显示 placeholder，点击聚焦后可输入文本（过滤控制字符），Backspace 删除，聚焦时显示蓝色边框；`Tab target` 聚焦时显示浅紫描边并显示 `Tab target: focused`；`Button ctrl` 可通过鼠标点击或 Enter/Space 激活，文本计数递增，按下显示 active 深色背景；开关控件（`DocumentToggleSwitchControl`）默认开启，点击或 Enter/Space 可切换，轨道颜色在线性灰/绿之间切换；Tab/Shift+Tab 可在所有 focusable 控件间遍历，`Tab target` 与开关选项卡焦点时显示浅蓝描边；若只见边框不见填充，优先检查主 UI GL 状态隔离。
- 选择一个现有诊断页作为迁移试点，避免一次性重写全部页面。当前首个迁移目标 `HtmlLikeInventoryOverviewDocumentPageController` 已完成基础结构，包含 HTML-like 卡片的 div 布局、TextNode 指标、`DocumentInventorySlotGridControl` 网格（hotbar 9 格 + backpack 27 格）、`DocumentButtonControl` 返回按钮，页面通过 `HtmlLikeDocumentWidget` 挂接到现有页面壳；后续可补充物品图标渲染链与游戏内验收。
