# AI记忆文档

## 最高优先级信息

- 当前项目尚未发布，不存在需要维护的外部兼容承诺；除非用户明确要求，否则无需为旧式 API、旧式行为或旧式内部结构添加兼容层。
- 框架可围绕整体最优进行大幅重构、删除或替换，优先追求长期架构正确性、模型一致性和未来上限，而不是为了局部最小改动保留不合适的旧代码。

## 长期稳定信息

### 项目定位

- 本仓库是 Minecraft 1.7.10 / GTNH / LWJGL3ify 环境下的 Java UI 框架工程。
- 当前主线目标已经升级为实现一套完整的 HTML-like UI 渲染框架，而不是继续局部修补文档页控件。
- “完整 HTML-like”在本项目中指具备稳定的文档树、样式计算、盒模型、布局、绘制、裁剪、滚动、效果合成、命中测试与输入分发分层。
- 本项目不直接实现完整浏览器内核，不承诺 HTML5 全量解析、CSS 全量规范、JavaScript DOM API、网络加载或浏览器安全模型。

### 当前稳定架构边界

- 当前可运行链路仍以 retained `Widget` 树为渲染后端，screen host 根视口为 `ViewportWidget`，文档页主创建边界为 `UiDocumentScreens`。
- `UiDocumentScreens` 通过 `DocumentScreenEnvironment`、`DocumentScreenDefinition` 与 `DocumentScreenChromeResolver` 暴露显式页面创建入口。
- HTML-like 文档树最小骨架已在 `club.heiqi.uilib.ui.dom` 落地；`UiDocument` 是当前文档作者入口，`DocumentNode` 负责父子关系与 mutation/layout/paint 分层失效版本，tree/text/geometry 变更会提升 layout 与 paint version，paint-only style/custom renderer 变更只提升 paint version；`ElementNode` 与 `TextNode` 分别承载元素和文本；每个 `ElementNode` 创建时会分配进程内唯一的内部身份 `__getElementUid()`，该值只供测试、调试、缓存和内部追踪使用，不等同于 HTML `id` 属性，也不进入属性表或样式选择器；`ElementNode` 当前支持最小 active handler、click handler、focus handler、key handler、text input handler 与 focusable 标记，active/click/key/text input 事件会从目标元素向父元素冒泡，focus 事件会带 `focusVisible` 区分键盘可见焦点与鼠标普通焦点。
- HTML-like 基础控件适配已在 `club.heiqi.uilib.ui.dom.control` 起步；`DocumentButtonControl` 当前以 `ElementNode` 为根封装按钮行为，支持 action handler、enabled/disabled、click 激活、Enter/Space 键盘激活、focusable、active 按下态、focus-visible 描边态与基础视觉状态切换；`DocumentTextInputControl` 支持 placeholder、maxLength、文本输入（过滤控制字符）、Backspace 删除、enabled/disabled、focus 边框区分、change handler；`DocumentToggleSwitchControl` 以 flex row + justifyContent 切换 thumb 位置实现开关视觉，支持 click/Enter/Space 切换、enabled/disabled、focus-visible 与 toggle change handler；`DocumentSegmentedSelectorControl` 以一组 element-backed button 表达分段选择器，支持鼠标/键盘选择、enabled/disabled、选中态视觉和 selection handler；`DocumentInventorySlotGridControl` 以 `ElementNode` + 自定义渲染回调实现只读背包格子网格，复用 `club.heiqi.uilib.ui.inventory` 下的 `InventorySlotGridLayout` 与 `InventorySlotGridItemRenderer`，支持 contentProvider、slotGap、preferredSlotSize 等配置。
- HTML-like 样式系统初版已在 `club.heiqi.uilib.ui.style` 落地；`ElementNode.style()` 暴露 inline style 入口，`UiStyleResolver` 负责把元素样式解析为 `ComputedStyle`；`UiStyleDeclaration` 会通过 `UiStyleChangeImpact` 标注 layout 或 paint-only 影响，当前已支持最小 CSS-like `position: static/relative/absolute/fixed`、`top/right/bottom/left`、positioned sibling 的 `z-index` 语义与 `opacity`；transition 声明已支持 `transition-property`、duration、delay 与 timing function 的 Java API 表达，当前开放 background/border/text color、opacity 与 border-radius 这类 paint-only 属性。
- HTML-like 动画基础设施已在 `club.heiqi.uilib.ui.animation` 起步；`DocumentAnimationClock` 提供可注入时间源，`DocumentAnimationTimeline` 负责在 computed style 基准值变化时创建 paint-only transition 覆盖层，动画运行值不会写回作者侧 inline style；当前属性注册范围为 `BACKGROUND_COLOR`、`BORDER_COLOR`、`TEXT_COLOR`、`OPACITY` 与 `BORDER_RADIUS`，timing function 支持 linear、ease、ease-in、ease-out、ease-in-out 的简化插值。
- HTML-like 布局盒初版已在 `club.heiqi.uilib.ui.layout` 落地；`DocumentLayoutEngine` 当前支持元素级 block flow、box model、px/% 长度、auto 高度、`display: none` 过滤、子元素垂直流式排布、直接文本子节点基于 `TextMeasureService` 的测量与换行布局，以及 flex row/column、gap、align、justify、grow/shrink 的最小实现；`position: relative` 会保留普通流盒位置，仅在布局盒中记录视觉偏移，供绘制、命中与滚动几何阶段使用；`position: absolute` 当前会脱离普通流，不撑开父元素 auto 高度，也不参与 flex item 分配，并相对最近 non-static ancestor 的 content box 使用 top/left 或 right/bottom inset 定位；若没有 positioned ancestor，则回退到根元素 content box；`position: fixed` 当前会脱离普通流并相对 `HtmlLikeDocumentWidget` viewport containing block 定位，不随根滚动内容移动，绘制、命中测试与滚动命中在遇到 fixed 布局盒时会重置祖先滚动 offset；尚未实现 left+right/stretch 约束；`DocumentLayoutBox` 统一提供 CSS-like stacking phase 与 stacking context 判断，当前分为负 `z-index` positioned、普通流、positioned auto/0、正 `z-index` positioned 四段；`DocumentEffectChain` 当前集中解析单个布局盒的 paint context、backdrop-filter、overflow clip、stacking context 与局部排序边界，`opacity < 1`、positioned + 显式 `z-index`、`backdrop-filter` 会建立独立 stacking context，overflow clip 边界作为局部排序边界，非 context 祖先下的 positioned 后代可参与最近 context 排序，context/effect boundary 内后代不会逃出与外部 sibling 全局比较；`DocumentScrollState` 当前负责根据布局盒推导 `overflow: auto` 元素的可滚范围、滚动偏移、滚动条几何与 track/thumb 拖拽状态，并复用 `DocumentEffectChain` 与 stacking context 顺序选择滚轮/滚动条命中；`DocumentHitTestEngine` 当前负责在滚动、relative/absolute/fixed 定位、四阶段 stacking context 顺序与 overflow clip/effect boundary 语义下查找命中的最深元素。
- HTML-like 绘制命令初版已在 `club.heiqi.uilib.ui.paint` 落地；`DocumentPaintEngine` 当前能把布局盒树转换为 paint-context/backdrop-filter/background/border/text/clip/scrollbar 中立绘制命令，并通过 `DocumentEffectChain` 解析 paint context、backdrop-filter 与 overflow clip 的顺序和 stacking boundary 判定，effect command 会携带 `DocumentEffectType` 供 renderer 运行时 pass 复用同一套效果语义；保留父元素背后滤镜、父元素背景、父元素边框、结构 clip、滚动后的直接文本换行行、滚动后的子树、clip end、滚动条 track/thumb 的基础 paint order；绘制顺序按最近 CSS-like stacking context / effect boundary 排序，负 `z-index` positioned 子树绘制在 context 内容命令之前，普通流子树绘制在 context 内容之后，positioned auto/0 与正 `z-index` 子树位于普通流之上；非 stacking context 祖先不会错误吞掉 positioned 后代排序，stacking context 或 overflow clip effect boundary 会把子树作为整体隔离；relative 偏移和 absolute 脱流几何会应用到元素自身、子树、clip 与滚动条；根绘制上下文当前隐式存在，非根 `opacity < 1`、positioned 且显式 `z-index`、或启用 `backdrop-filter` 的元素会输出 `PAINT_CONTEXT_START` / `PAINT_CONTEXT_END` 边界；`opacity < 1` 的 paint context 当前会把本 context 的局部 opacity 留给 renderer 离屏层整体合成，非 context 祖先 opacity 仍会提前应用到标准颜色命令；根元素滚动条保持可见，嵌套 `overflow:auto` 滚动条只在最近有效滚动后的短暂窗口内绘制，空闲后隐藏以避免遮挡内容。
- `DocumentPaintRenderer` 已可把 paint-context/backdrop-filter/background/border/text/clip/custom/scrollbar paint command 投影到现有 `UiRenderContext`；renderer 当前按 `DocumentEffectType` 管理运行时 effect pass：`PAINT_CONTEXT` 与 `OVERFLOW_CLIP` 作为栈式 pass 开启/清理，`BACKDROP_FILTER` 作为 stateless pass 回放到 `UiRenderContext.drawBackdropFilter(...)`。`UiRenderContext.pushPaintContext(...)` / `popPaintContext()` 当前已接入首期 group opacity 离屏合成：opacity context 会借用 `PaintContextCompositor` 管理的 `UiRenderTarget` 层，继承当前 clip snapshot，子树绘制完成后按 group opacity 贴回父 framebuffer；FBO 不可用时 renderer 会回退为旧式命令级 alpha，避免破坏纯 JVM 测试或低能力运行时；positioned z-index 与单纯 backdrop context 暂时只作为边界，不强制创建离屏层。`UiRenderContext.drawBackdropFilter(...)` 当前只采样当前 UI 主层已经绘制到当前 framebuffer 的内容，不主动读取游戏世界 framebuffer；如果处在 opacity 离屏 context 内，backdrop 采样会临时从父 framebuffer 读取，避免读到当前空白离屏层；如果页面壳提前绘制了一张已模糊底图，它会作为普通 UI 背景被采样；当前 `UiMainLayerSnapshotService` 会在同帧内按 read framebuffer、128px block 对齐后的局部采样区域、UI 主层内容版本和 blur 级别复用快照纹理，已捕获的较大 block 区域可作为临时 atlas 覆盖后续较小区域，多个已捕获 tile 也可组装成新的局部 atlas，缺失 tile 才从当前 read framebuffer 复制，并为大半径 blur 生成降采样 + 横向/纵向 separable blur filter 纹理，`UiRenderContext` 会在 surface/text/backdrop/paint context 合成与 custom render 后推进内容版本，确保两次 backdrop 之间只要发生新的 UI 绘制写入就重新捕获最新主层，统一采样区域扩张、block-aligned 局部纹理复制、atlas 覆盖复用、multi tile atlas 组装、128px tile 覆盖计划诊断、downsample/separable blur filter pass、4096 边长/像素级尺寸保护和 snapshot 不可用降级；backdrop 主路径优先使用专用 GLSL shader 对 raw/downsampled 局部快照纹理做 mipmap-biased 多 tap 平滑采样与 saturation 调整，shader 不可用时回退到固定管线近似 blur，再失败时回退为半透明 tint 与高光描边伪玻璃；`UiRenderContext` 会记录最近一次 backdrop 实际渲染路径，Glass Lab 页会显示 `Backdrop path: shader/fixed-pipeline/tint-fallback / ... snapshot=captured/reused WxH @x,y ... rev=... region=exact/block128/atlas-block128/tile-atlas-block128 tiles=N covered=M missing=K reused=R copied=C filter=raw/downsampleN+sepBlurR ...` 用于确认当前路线、快照复用状态、采样局部区域、采样内容版本、block 对齐/atlas 覆盖状态、tile 覆盖/缺失状态、实际 tile 复用/复制数量和 filter pass 降采样/预模糊状态；`DocumentCustomRenderer` 允许控件在元素背景/边框之后、clip/子树之前注入自定义绘制回调，回调坐标表达元素内容盒，并会随元素自身滚动偏移，供背包格子等复杂控件使用。
- HTML-like 已支持类似 CSS `backdrop-filter` 的首期磨玻璃语义。目标语义是处理同一 UI 主层内元素背后的已绘制 UI 内容，而不是额外模糊游戏画面；宿主/页面壳层若能提供一次已经模糊的背景底图，则该底图只作为普通 UI 背景参与后续元素采样。本项目当前已在样式层暴露高层 `backdropBlurRadius` 与 `backdropSaturation` 语义，在 paint command 中表达 `BACKDROP_FILTER` 请求，并由 `UiRenderContext` + `UiMainLayerSnapshotService` 封装 UI 层同帧快照复用、shader-backed 平滑 blur、saturation 与降级路径；文档作者不得接触 OpenGL/FBO 细节。`HTML-like Smoke` 页当前使用同层采样舞台：先绘制彩色条纹与文字，再用 `position: relative` + `z-index` 让 glass card 覆盖这些 UI 内容，便于直接验收 backdrop 采样是否可见。
- `HtmlLikeDocumentWidget` 已把 `UiDocument -> style -> layout -> paint command -> UiRenderContext` 链路挂接到现有 retained `Widget` 后端；生产构造默认使用 `DefaultTextMeasureService`，测试可注入确定性 `TextMeasureService` 与动画时钟；组件现在会消费命中的 `overflow: auto` 元素滚轮事件并让内容区随 `DocumentScrollState` 偏移，同时记录最近有效滚动时间用于嵌套滚动条空闲隐藏，支持根视口或当前可见内部滚动条的 track 点击与 thumb 拖拽，也会将鼠标 active/click 分发给命中的 HTML-like 元素，并维护命中 focusable 元素的内部焦点，接收现有 `UiInputRouter` 转发的 key/text input 后向 HTML-like 元素冒泡；鼠标聚焦元素时不设置 focus-visible，Tab/Shift+Tab 进入或移动焦点时设置 focus-visible；当前 `UiInputRouter` 的 Tab 全局遍历会先让已聚焦 `Widget` 处理内部焦点遍历，HTML-like 组件会按布局树顺序在 focusable 元素之间移动，边界处再交回全局 widget 焦点；组件新增根视口滚动模式，启用后根元素 border box 固定为 widget 视口尺寸，整页滚动由根元素 `overflow:auto` 与 `DocumentScrollState` 承担，`position: fixed` 元素固定在 widget 视口内，避免旧页面壳因点击/聚焦触发外层滚动后随机移动；组件缓存按 layout version 与 paint version 分层失效，layout version 未变但 paint version 变化时只刷新缓存布局盒树上的 computed style，不重新执行文本测量和布局；组件会在存在 paint-only transition 时绕过静态 paint cache，每帧重建 paint commands 并在动画结束后回到缓存；`ui_test` 诊断菜单、`ui_layout_diagnostics` 布局诊断页、`html_like_smoke` 子页、`html_like_glass` 大面积磨玻璃测试页与 `inventory_overview` 背包页的作者层已迁移为单个 `HtmlLikeDocumentWidget` 承载的 HTML-like 文档，并启用根视口滚动。
- 当前 `UiDocumentScreens` 的 definition-backed 生产入口已切换为 `DefinitionBackedHtmlLikeDocumentScreen` + `DirectDocumentPageAuthoringSurface`：已迁移 HTML-like 页面直接把 `HtmlLikeDocumentWidget` 挂到根视口并由 direct surface 计算居中 frame，不再套旧 retained 页面壳；旧 `BaseDocumentScreen`、`ControllerBackedDocumentScreen`、`DocumentPageWidget` 与 `DocumentPageAuthoringSurface.adapt(...)` 已清退。
- `UiScreenHostSession` 在主 UI widget 树渲染前会统一准备稳定 2D GL 状态，避免世界渲染遗留的 depth/cull/alpha/light 状态导致 rounded fill 面片被剔除。
- 后续作者侧入口应继续迁移到 HTML-like 文档/元素/样式 API；底层 `Widget` 与 `ViewportWidget` 仍作为宿主后端保留。当前已完成键盘/文本输入、基础控件适配、诊断菜单/布局诊断页和背包业务页迁移，旧 public screen 作者入口、旧页面壳适配层、`DocumentUiScope` retained widget factory、旧 retained 文档控件、旧 retained control widget、旧 Div/ScrollViewport 兼容容器与旧 retained 主题样式对象已清退；不能误删 HTML-like 控件复用的背包格子底层算法与宿主适配能力。
- `UiSurfaceStyle` 是纯外观值对象，只负责 `fillColor`、`borderColor`、`cornerRadius`。
- `border-radius` 外观不得隐式控制 descendant clip；结构裁剪必须来自 `overflow`、viewport 或显式 clip 容器。
- 旧 `DivWidget`、`ScrollViewportWidget`、`RoundedScrollViewportWidget`、`OverflowScrollState`、`OverflowViewportLayout` 与 `UiScrollHost` 已删除；HTML-like 页面滚动必须使用 `HtmlLikeDocumentWidget` 根视口滚动模式或元素级 `overflow:auto`。
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
- 最近已验证通过的定向测试：`DocumentButtonControlTest`、`DocumentTextInputControlTest`、`DocumentToggleSwitchControlTest`、`DocumentSegmentedSelectorControlTest`、`DocumentInventorySlotGridControlTest`、`HtmlLikeDocumentWidgetTest`、`HtmlLikeSmokeDocumentPageControllerTest`、`UiInputRouterTest`、`DocumentPaintRendererTest`、`UiDocumentScreensTest`、`UiTestDocumentPageControllerTest`、`DocumentPaintEngineTest`、`DocumentLayoutEngineTest`、`UiStyleResolverTest`、`UiDocumentTest`、`UiSurfaceStyleTest`；本轮 `backdrop-filter` 定向验证覆盖 `UiStyleResolverTest`、`DocumentPaintEngineTest`、`DocumentPaintRendererTest` 与 `HtmlLikeSmokeDocumentPageControllerTest`。
- 当前 `ui.dom` / `ui.style` / `ui.layout` / `ui.paint` 已经接入 `HtmlLikeDocumentWidget`、诊断菜单、布局诊断页、`html_like_smoke` 子页与背包概览页；这些页面的外层滚动已切到 HTML-like 根元素 `overflow:auto`，且生产入口不再包旧 retained 页面壳，可从游戏内诊断菜单进入对应页面进行真实渲染验收。
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
- `src/main/java/club/heiqi/uilib/ui/animation/DocumentAnimationClock.java`
- `src/main/java/club/heiqi/uilib/ui/animation/SystemDocumentAnimationClock.java`
- `src/main/java/club/heiqi/uilib/ui/animation/DocumentAnimationProperty.java`
- `src/main/java/club/heiqi/uilib/ui/animation/DocumentAnimationTimingFunction.java`
- `src/main/java/club/heiqi/uilib/ui/animation/DocumentAnimationTimeline.java`
- `src/main/java/club/heiqi/uilib/ui/paint/DocumentCustomRenderer.java`
- `src/main/java/club/heiqi/uilib/ui/style/UiStyleDeclaration.java`
- `src/main/java/club/heiqi/uilib/ui/style/UiStyleResolver.java`
- `src/main/java/club/heiqi/uilib/ui/style/ComputedStyle.java`
- `src/main/java/club/heiqi/uilib/ui/style/UiPosition.java`
- `src/main/java/club/heiqi/uilib/ui/style/UiFlexDirection.java`
- `src/main/java/club/heiqi/uilib/ui/style/UiAlignItems.java`
- `src/main/java/club/heiqi/uilib/ui/style/UiJustifyContent.java`
- `src/main/java/club/heiqi/uilib/ui/layout/DocumentLayoutEngine.java`
- `src/main/java/club/heiqi/uilib/ui/layout/DocumentLayoutBox.java`
- `src/main/java/club/heiqi/uilib/ui/layout/DocumentEffectChain.java`
- `src/main/java/club/heiqi/uilib/ui/layout/DocumentEffectType.java`
- `src/main/java/club/heiqi/uilib/ui/layout/DocumentStackingPhase.java`
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
- `src/main/java/club/heiqi/uilib/ui/screen/UiScreenHostSession.java`
- `src/main/java/club/heiqi/uilib/ui/input/UiInputRouter.java`
- `src/main/java/club/heiqi/uilib/ui/render/UiRenderContext.java`
- `src/main/java/club/heiqi/uilib/ui/render/UiMainLayerSnapshotService.java`
- `src/main/java/club/heiqi/uilib/ui/render/UiBackdropShaderProgram.java`
- `src/main/java/club/heiqi/uilib/ui/widget/Widget.java`
- `src/main/java/club/heiqi/uilib/ui/widget/ViewportWidget.java`
- `src/main/java/club/heiqi/uilib/ui/runtime/UiRuntimeAdapters.java`
- `src/main/java/club/heiqi/uilib/ui/inventory/InventorySlotGridLayout.java`
- `src/main/java/club/heiqi/uilib/ui/inventory/InventorySlotGridItemRenderer.java`
- `src/main/java/club/heiqi/uilib/ui/inventory/InventorySlotSnapshot.java`
- `src/main/java/club/heiqi/uilib/ui/inventory/MinecraftInventorySlotGridItemRenderer.java`
- `src/main/java/club/heiqi/uilib/ui/theme/UiSurfaceStyle.java`

### 磨玻璃效果规划

- 目标语义应优先模仿浏览器 `backdrop-filter`，而不是 `filter`：前者处理元素背后的已绘制内容，后者处理元素自身和子树。
- 当前已落地分层：style/computed style 保存 `backdropBlurRadius` 与 `backdropSaturation`；layout 提供元素 border/padding/clip 几何；paint 输出 `BACKDROP_FILTER` 命令并保持在元素 background/border/content 之前；renderer 调用 `UiRenderContext.drawBackdropFilter(...)`，该入口通过 `UiMainLayerSnapshotService` 按 read framebuffer 与 UI 主层内容版本复用同帧快照做 shader-backed blur/saturate，失败时回退为半透明 tint 与高光描边伪玻璃。
- 后续宿主真实效果策略：在现有同帧 block-aligned 局部 UI 主层快照、较大 block 区域 atlas 覆盖复用、multi tile atlas 组装、tile 覆盖计划诊断和 downsample + separable blur filter pass 基础上继续推进更完整 blur/effect pass；继续限制最大 blur radius 和最大采样尺寸；FBO 或 shader 不可用时继续回退为 raw snapshot、半透明 tint、高光边和投影的伪玻璃视觉。不要把元素级 backdrop-filter 设计成直接模糊游戏世界画面。
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
- 已将可访问的诊断菜单页、布局诊断页、HTML-like Smoke 页与背包概览页作者层迁移为 HTML-like 文档，页面控制器不再直接拼装旧 retained widget；旧 `InventoryOverviewDocumentPageController` 及其测试已删除，背包入口改为 `HtmlLikeInventoryOverviewDocumentPageController`。
- 本轮 HTML-like 页面迁移后已验证通过：`./gradlew.bat --no-configuration-cache test` 与 `./gradlew.bat --no-configuration-cache compileJava`。
- 已新增 `DocumentLayoutEngine.layoutViewportRoot(...)` 与 `HtmlLikeDocumentWidget#setViewportRootScrollingEnabled(true)`，四个当前 HTML-like 页面均使用 percent 高度填满页面壳，并由文档根元素 `overflow:auto` 承担整页滚动；已补充点击 HTML-like 焦点元素不再触发旧页面壳滚动的回归测试，错误记录见 `docs/errors/ERROR-20260427-html-root-scroll-focus.md`。
- 已为 HTML-like `overflow:auto` 增加默认滚动条绘制命令与 renderer 投影，滚动条 track/thumb 由 paint command 绘制，避免根视口滚动迁移后页面能滚但没有任何可见滚动提示；错误记录见 `docs/errors/ERROR-20260427-html-scrollbar-missing.md`。
- 已将 HTML-like 嵌套 `overflow:auto` 滚动条改为空闲自动隐藏：根元素滚动条保持可见，内部滚动块只在最近有效滚动后的短暂窗口内绘制 track/thumb，避免 Smoke 页 teal 卡片这类内部滚动条长期遮挡文本；错误记录见 `docs/errors/ERROR-20260427-html-nested-scrollbar-idle-cover.md`。
- 已为 HTML-like 滚动条补齐 track 点击与 thumb 拖拽交互，`DocumentScrollState` 统一提供滚动条几何与拖拽状态，`DocumentPaintEngine` 复用同一份几何避免绘制/交互坐标漂移；真实输入路由测试已覆盖拖拽根滚动条和点击 track 不透传元素 click。
- 已新增 `DocumentSegmentedSelectorControl` 分段选择器控件，并将布局诊断页原本内嵌的临时 `SegmentControl` 迁移到该 HTML-like 控件层；真实 widget 适配测试覆盖鼠标选择、键盘选择、禁用态、程序化选择不触发事件和选中态视觉。
- 已为 HTML-like 增加 `backdropBlurRadius` 与 `backdropSaturation` 样式声明、computed style 默认值、`BACKDROP_FILTER` paint command 与 `UiRenderContext.drawBackdropFilter(...)` 渲染入口；当前默认渲染复制当前 UI 主层局部区域做近似 blur，复制失败时提供伪玻璃降级，HTML-like Smoke 页新增 `Backdrop glass overlap: blur 14px / saturate 140%` 同层重叠验收卡片，定向测试已覆盖 style resolver、paint order、renderer 投影与 smoke 页面集成。
- 已将 HTML-like backdrop 主路径升级为 shader-backed 平滑采样：当前 `UiRenderContext` 复制局部 UI 纹理后，会优先走专用 GLSL fragment shader 完成多 tap blur 与 saturation；固定管线多重偏移叠画只保留为 shader 不可用时的兼容降级。
- 已把 `UiDocumentScreens` 当前 definition-backed 生产入口切换到 direct HTML-like screen host，`ui_test`、`ui_layout_diagnostics`、`html_like_smoke` 与 `inventory_overview` 不再套旧 retained 页面壳。
- 已新增 `html_like_glass` 大面积磨玻璃测试页，可从诊断菜单进入；页面使用多行彩色 UI 采样场和覆盖大部分区域的 glass slab，当前固定 `blur 36px / saturate 125%` 并显示上一帧 `Backdrop path`，用于放大观察 UI 层 backdrop 采样、shader/降级路线、裁剪泄漏、采样错位和 resize 下的稳定性；原大玻璃测试下方另有 6 个新增磨玻璃块组成的层级回归区，覆盖三层嵌套 glass shell 与位于不同层级的独立 glass sibling；页面底部新增 `Tile atlas probe / block128 tile diagnostics` 与就地 `Tile probe Backdrop path`，用于在滚动到底部时直接观察 `region=... tiles=N covered=M missing=K reused=R copied=C filter=...` 的 tile 覆盖计划、单块 atlas 覆盖和 multi tile atlas 组装诊断。之前临时的 snapshot-only probe 已移除，避免把只读 snapshot 采样能力暴露成公开渲染 API。
- 已为 HTML-like 增加最小 CSS-like `position: relative` 与 positioned sibling `z-index` 能力：样式层新增 `UiPosition`、`top/right/bottom/left` 和 `zIndex`，布局盒记录 relative 视觉偏移但不改变普通流，绘制、命中与滚动条几何会应用该偏移，同级子元素按 z-index 稳定排序；Smoke 页 glass card 与 Glass Lab 主 glass slab 已改用 relative + z-index 制造同层覆盖，减少对负 margin 叠放 hack 的依赖。
- 已将同级子盒排序从单纯 z-index 数值排序扩展为最小 CSS-like stacking phase：负 `z-index` positioned 子树、普通流子树、positioned auto/0 子树、正 `z-index` positioned 子树；`DocumentPaintEngine`、`DocumentHitTestEngine` 与 `DocumentScrollState` 共用 `DocumentLayoutBox` 提供的顺序，保证绘制、命中、滚动条命中看到一致层级。
- 已为 HTML-like 搭建首期动画系统基础设施：新增可注入动画时钟、paint-only 动画属性注册、timing function 与 `DocumentAnimationTimeline`，样式层新增 transition property/duration/delay/timing function 声明；`HtmlLikeDocumentWidget` 会在 paint-only transition 活跃时持续重建 paint commands，完成后自动清理并回到缓存；Smoke 页 `Click target` pill 已配置 background-color transition，游戏内点击后可观察绿色/蓝色之间平滑过渡。
- 已为 HTML-like 增加 `opacity` 样式与 transition 支持：`ComputedStyle` 将 opacity clamp 到 0..1，`DocumentAnimationTimeline` 支持 float transition，`DocumentPaintEngine` 会把元素自身与祖先的 opacity 累积到 background/border/text/scrollbar 颜色 alpha；Smoke 页 `Click target` pill 已同时配置 background-color 与 opacity transition，游戏内点击后可观察颜色切换和整体淡入淡出；当前暂不对 `CUSTOM` 与 `BACKDROP_FILTER` 做真正 group opacity 或 stacking context isolation。
- 已为 HTML-like 增加 `position: absolute` 基础能力：absolute 元素脱离 block/flex 普通流，不撑开父元素 auto 高度，也不参与 flex item 分配；当前相对最近 non-static ancestor 的 content box 按 top/left 或 right/bottom inset 定位，若没有 positioned ancestor 则回退到根元素 content box；absolute 子树继续参与现有 positioned stacking phase、绘制、命中与滚动几何；Smoke 页 `ABS badge` 浮动徽标可用于游戏内观察 absolute 覆盖效果，左侧 `ABS containing probe` 紫色卡片可观察 nested absolute containing block：金色 `ABS OK` 标签是内部 static wrapper 的子节点，但应贴在紫色 positioned 卡片右上角而不是深色 wrapper 内。
- 已为 `ElementNode` 增加内部唯一身份 `__getElementUid()`：该值由框架创建元素时自动分配，命名刻意避开 HTML 原生 `id`，不参与属性表和样式语义；Smoke 页探针测试已改为通过 paint command 上的元素 uid 定位具体元素，避免用颜色或坐标猜测元素导致伪阳性。
- 已为 HTML-like 增加 `border-radius` transition 支持：`DocumentAnimationTimeline` 会按布局盒解析后的圆角像素值创建 paint-only 数值覆盖，`DocumentPaintEngine` 会把动画圆角应用到 background、border、backdrop-filter 与 overflow clip 绘制命令；Smoke 页 `Click target` pill 已配置 background-color、opacity 与 border-radius 组合 transition，游戏内点击后可观察颜色、淡入淡出和圆角形变同步过渡。
- 已将 HTML-like 文档失效机制拆为 layout/paint 分层版本：`UiStyleDeclaration` 通过 `UiStyleChangeImpact` 区分布局级与 paint-only 样式变更，`UiDocument` 暴露 `getLayoutVersion()` 与 `getPaintVersion()`；`HtmlLikeDocumentWidget` 在 paint-only 样式变更时复用已有布局几何并通过 `DocumentLayoutBox.refreshComputedStyles()` 刷新样式快照，避免 background/text color、opacity、border-radius、backdrop 参数和 transition 声明变更触发重新文本测量与全量 layout。
- 已为 HTML-like 绘制链路增加显式 paint context 边界：`DocumentPaintCommandType` 新增 `PAINT_CONTEXT_START` / `PAINT_CONTEXT_END`，非根 opacity、positioned z-index 与 backdrop-filter 元素会被上下文命令包裹，`DocumentPaintRenderer` 会按命令顺序回放到 `UiRenderContext.pushPaintContext(...)` / `popPaintContext()`；该边界已用于首期 group opacity 与 stacking context 隔离，后续仍可继续承载更完整元素级 backdrop/effect 合成。
- 已将根目录 `项目建议.md` 从早期 HTML-like 阶段规划替换为当前可执行建议，重点记录待添加底层基础建设与待清理旧代码清单。
- 已按 `项目建议.md` 优先完成旧入口清理：删除 `UiTestScreen`、`InventoryOverviewScreen`、`ControllerBackedDocumentScreen`、`BaseDocumentScreen`、`DocumentPageWidget` 与 `DocumentPageWidgetTest`；移除 `DocumentPageAuthoringSurface.adapt(DocumentPageWidget)` 和 `DocumentPageWidgetAuthoringAdapter`；收缩 `DocumentUiScope`，移除旧 retained widget factory；删除 `DocumentTextWidget`、`DocumentCardWidget`、`DocumentToolbarWidget`、`DocumentFlowRowWidget`、`DocumentSectionWidget`、`DocumentFormRowWidget`。`HtmlLikeDocumentWidgetTest.shouldKeepViewportRootScrollStableWhenFocusableHtmlElementIsClicked()` 已改为直接验证 HTML-like 根视口滚动稳定性，不再依赖旧页面壳夹具。
- 已继续清退旧兼容代码：删除 `ButtonWidget`、`ToggleSwitchWidget`、`SegmentedSelectorWidget`、`TextInputWidget`、`InventorySlotGridWidget`、`LabelWidget` 及旧 widget 测试；删除旧 `DivWidget`、`ScrollViewportWidget`、`RoundedScrollViewportWidget`、`OverflowScrollState`、`OverflowViewportLayout`、`UiScrollHost`；删除旧 retained 控件主题 `UiControlTheme`、`UiDocumentTheme`、`UiDocumentThemes`；`DocumentScreenEnvironment` 不再携带主题，`DocumentUiScope` 只保留 `getTextMeasureService()` 与 `getRuntimeAdapters()`。
- 已完成定向命名迁移：背包格子底层算法与渲染委托从 `club.heiqi.uilib.ui.control` 迁到 `club.heiqi.uilib.ui.inventory`；`UiControlRuntimeAdapters` 重命名并迁移为 `club.heiqi.uilib.ui.runtime.UiRuntimeAdapters`；screen host 根视口 `ViewportWidget` 迁移到 `club.heiqi.uilib.ui.widget`。源码中已无 `club.heiqi.uilib.ui.control` 包引用。
- 已在 paint context 边界上落地首期 group opacity FBO 合成：`UiScreenHostSession` 复用 `UiRenderContext.PaintContextCompositor`，opacity context 子树先绘制到离屏 `UiRenderTarget`，再按局部 opacity 合成回父 framebuffer；`CUSTOM` 内容随子树一起参与整体透明度；FBO 不可用时保持命令级 alpha 降级。当前已接入首期 `DocumentEffectChain`，后续仍需扩展更多 effect pass。
- 已为 Smoke 页增加游戏内可观测的 group opacity probe：页面底部 `Group opacity probe: overlap should stay flat blue, not dark purple` 区域用 55% opacity 父 context 包裹重叠红/蓝块并放在彩色条纹背景上；正确 group opacity 下，重叠区域应与蓝块非重叠区域保持同样的半透明蓝，不应因为红蓝分别半透明叠加而变成更深的紫/暗色。
- 已完成首期 CSS-like stacking context 隔离：`DocumentPaintEngine`、`DocumentHitTestEngine` 与 `DocumentScrollState` 现在按最近 stacking context 收集并排序 positioned 后代，非 context 祖先不再错误隔离高 z-index 后代，`opacity < 1`、positioned + 显式 `z-index`、`backdrop-filter` 和 overflow clip 边界会阻止后代逃出局部上下文；Smoke 页底部新增 `Stacking context probe: blue cover must stay above red z-99 child`，用于观察外部蓝色 sibling 是否盖住 isolated shell 内的红色高 z-index 子元素。
- 已完成首期可复用 UI 主层快照服务并修正旧快照过度复用问题：`UiScreenHostSession` 跨帧复用 `UiMainLayerSnapshotService`，`UiRenderContext.drawBackdropFilter(...)` 在同一帧内按 read framebuffer、128px block 对齐后的局部采样区域、UI 主层内容版本与 blur 级别复用 snapshot；相近 glass 元素更容易复用同一块 snapshot，已捕获的较大 block 区域也可作为临时 atlas 覆盖后续较小 block 区域，多个同版本已捕获 tile 也可组装成新的局部 atlas，缺失 tile 才从当前 read framebuffer 复制；大半径 blur 会在局部 snapshot 后生成降采样 filter 纹理，并通过横向/纵向 separable blur pass 预模糊，shader 半径和 mip lod 会按降采样倍率折算，FBO 不可用时回退到 raw snapshot；Glass Lab 的 `Backdrop path` 诊断会显示 `snapshot=captured/reused WxH @x,y fbo=N rev=R region=exact/block128/atlas-block128/tile-atlas-block128 tiles=N covered=M missing=K reused=R copied=C filter=raw/downsampleN+sepBlurR ...`，多个 glass 元素只有在两次采样之间没有新的 UI 绘制写入且采样区域/blur 级别同属同一 block、被已捕获大 block 区域覆盖或能由同版本 tile 组装时才复用，否则必须重新捕获；当前保留 tile atlas probe 观察真实 backdrop 绘制路径，不再暴露 snapshot-only 诊断 API。
- 已完成首期 clip / effect chain 显式建模：新增 `DocumentEffectChain` 与 `DocumentEffectType`，集中表达 paint context、backdrop-filter、overflow clip、stacking context 与 effect boundary；`DocumentPaintEngine`、`DocumentHitTestEngine` 与 `DocumentScrollState` 已改为复用该模型，`DocumentPaintCommand` 与 `DocumentPaintRenderer` 也已用 `DocumentEffectType` 标记和回放 renderer 运行时 effect pass；新增测试覆盖 effect 顺序、axis-aware clip bounds、effect command 类型标记、runtime pass 清理，以及 overflow clip boundary 对高 z-index 后代的绘制、命中和滚动隔离。

### 当前阶段目标

- 阶段 0：完成规划与文档清理，保留现有可运行链路。
- 阶段 1：新增 HTML-like 文档树与作者入口最小骨架；当前最小骨架已完成，下一步应把样式入口挂到元素层。
- 阶段 2：新增样式系统与 computed style 初版；当前 inline style、基础 computed style、relative/absolute 定位声明、positioned z-index、transition 声明与 layout/paint 分层失效已完成，后续需要继续扩展样式属性集并把分层失效推进到更细粒度 style/layout/paint cache。
- 阶段 3：建立 box/layout tree，并逐步把现有 Div-like 布局能力迁移到新模型；当前 block flow、flex flow、relative 视觉偏移、absolute 脱流定位、fixed 视口定位、最近 positioned ancestor containing block、viewport containing block、最小 stacking phase 顺序与直接文本测量/换行布局最小闭环已完成，后续应推进更完整的 inline layout 与 left+right/stretch 约束。
- 阶段 4：建立 paint command、clip、scroll、deferred replay 与效果合成的统一渲染模型；当前 background/border/text/clip/backdrop-filter/paint-context command、首期 `DocumentEffectChain`、renderer 运行时 effect pass 类型化回放、四阶段 stacking context/effect boundary 绘制/命中/滚动命中排序、fixed 元素祖先滚动 offset 隔离、paint-only transition 动画覆盖、paint-only 样式刷新不重排、首期 group opacity FBO 合成、首期 block-aligned 可复用局部 UI 主层 snapshot、较大 block 区域 atlas 覆盖复用、multi tile atlas 组装、tile 覆盖计划诊断、首期 downsample + separable blur filter pass、border-radius 运行圆角、`UiRenderContext` 投影、`overflow: auto` 滚动偏移、滚动条绘制/交互、命中测试与最小 smoke screen 集成已完成，下一步可继续推进 absolute stretch、inline layout、更完整 blur/filter pass 或更多 effect pass。
- 阶段 5：迁移事件与控件适配；当前已完成元素 active/click 冒泡、普通焦点/focus-visible 区分、键盘按键、文本输入、Tab/Shift+Tab 内部焦点遍历、按钮/文本输入框/开关/分段选择器/背包格子控件适配，后续需要更多基础控件适配、真实页面迁移与更完整的可访问性语义。
- 阶段 6：清退旧 public screen 构造入口与直接 widget authoring 示例；当前可访问诊断/业务页面的生产宿主已改为 direct HTML-like screen host，旧 screen host、旧页面壳适配层、`DocumentUiScope` retained factory、旧 retained 文档控件、旧 retained control widget、旧兼容容器与旧 retained 主题样式对象已删除，后续仅需持续清点是否还有无 HTML-like 复用价值的旧兼容残留。

### 下一步执行项

- 下一步不应继续优先添加静态、易实现的动画属性；`position: fixed` 与 viewport containing block 首期已完成，下一步优先补 absolute left+right/stretch 约束与 inline formatting 初版。之后再继续 effect chain 的 multi tile atlas 批量路径、圆角裁剪、更完整 blur/saturate filter pass、采样尺寸限制和 FBO 不可用降级；也可继续清点剩余旧作者入口、列表/下拉类基础控件与可访问性语义。
- 旧非 DOM 后端暂时不能整体舍弃；`Widget`、`ViewportWidget`、`UiInputRouter`、`UiRenderContext` 仍应作为 backend/runtime 基础保留，直到 HTML-like 后端完全替代对应能力。
- 旧代码清理的下一步不再聚焦已删除的 retained widget，而是持续清点旧兼容残留；`InventorySlotGridLayout`、`InventorySlotGridItemRenderer`、`InventorySlotSnapshot`、`MinecraftInventorySlotGridItemRenderer` 与 `UiRuntimeAdapters` 等 HTML-like 背包格子控件仍复用的底层能力不得删除。
- 游戏内实际验证入口已就绪：按右 Shift 打开诊断菜单页，可直接验证 HTML-like 诊断菜单、布局诊断页、HTML-like Smoke 子页、Large Glass Lab 子页和背包概览页。Smoke 页重点观察实心填充、圆角边框、overflow-hidden 裁剪、文本换行、可滚动 teal 卡片、同层条纹/文字被 glass card 覆盖后的 backdrop blur/saturate、右上角 `ABS badge` absolute 浮动徽标、右上 `FIXED viewport probe` 是否在页面滚动时保持相对视口固定、左侧 `ABS containing probe` 内金色 `ABS OK` 标签是否贴在紫色卡片右上角而不是深色 `static wrapper` 内、`Click target` pill 点击后的绿色/蓝色 background-color、opacity 淡入淡出与 border-radius 圆角形变平滑 transition、click/text input/Tab/button/toggle 交互；Smoke 页向下滚动到底部还应观察 `Group opacity probe`：红蓝重叠块位于黄色/青色条纹背景上，蓝块与红块重叠区域应和蓝块右侧非重叠区域一样是均匀半透明蓝，不应出现明显更深/发紫的叠加痕迹；继续向下观察 `Stacking context probe`：紫色 isolated shell 内的红色 `red child z=99` 与外部蓝色 `blue sibling z=1 should win` 重叠时，蓝色 sibling 必须盖住红色高 z-index 子元素，红色不应逃出父 stacking context；Glass Lab 页重点观察大面积 glass slab 覆盖彩色采样场后的 blur/saturate、顶部 `Backdrop path` 或底部 `Tile probe Backdrop path` 是否为 `shader` 且诊断详情是否出现 `snapshot=captured` 或 `snapshot=reused`、`rev=...`、`region=exact/block128/atlas-block128/tile-atlas-block128`、`tiles=N covered=M missing=K reused=R copied=C` 与 `filter=raw/downsampleN+sepBlurR ...`、下方 6 块层级磨玻璃的三层叠套和不同层级 sibling 采样、底部 `Tile atlas probe / block128 tile diagnostics` 是否能显示 tile 覆盖/缺失、实际复用/复制和 downsample 诊断，尤其要确认嵌套玻璃采样的是自己背后的新色块而不是顶部大玻璃第一次捕获的旧采样场、边缘圆角、裁剪泄漏、采样错位和 resize 稳定性；布局诊断页重点观察页面宽度、HTML-like 页面滚动偏移、HTML-like 自滚动探针、滚动条 track/thumb、性能文案和高频变更探针；背包页重点观察 hotbar/backpack 网格、自定义格子绘制和返回按钮交互。重点回归：点击任意 HTML-like 控件或卡片不应导致整个页面随机跳动，只有滚轮命中的 HTML-like `overflow:auto` 元素才应改变滚动偏移，内部滚动块的滚动条应在停止滚动后自动隐藏，可见滚动条的 track 点击与 thumb 拖拽应能改变对应元素滚动偏移且不触发底层元素 click。
