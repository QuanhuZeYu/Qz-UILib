# AI记忆文档

## 最高优先级信息

- 当前项目尚未发布，不存在外部兼容承诺；除非用户明确要求，否则不为旧 API、旧行为或旧内部结构增加兼容层。
- 当前主线是 HTML-like UI 渲染框架与 CSS transition / animation 语义 MVP；inline formatting 只处理阻塞动画探针、真实页面迁移或控件展示的最小必要项。
- 清退 retained 作者入口、旧页面壳与兼容主题时，以 HTML-like 主线价值为准；没有复用价值的结构优先删除或重写。
- 清退不能误删当前 HTML-like 仍复用的后端/运行时能力：`Widget`、`ViewportWidget`、`UiInputRouter`、`UiRenderContext`、`UiRuntimeAdapters`、背包格子底层布局/渲染适配与 Minecraft 宿主会话能力。
- 作者层只暴露 HTML-like/CSS-like 语义；不得向页面作者暴露 Minecraft GUI 生命周期、OpenGL/FBO/shader/stencil、snapshot-only 诊断或宿主背景效果细节。

## 项目定位

- 本仓库是 Minecraft 1.7.10 / GTNH / LWJGL3ify 环境下的 Java UI 框架工程。
- 当前目标是一套 HTML-like UI 渲染框架，覆盖文档树、样式计算、盒模型、布局、绘制、裁剪、滚动、效果合成、命中测试与输入分发。
- 本项目不是完整浏览器内核，不承诺 HTML5 全量解析、CSS 全量规范、JavaScript DOM API、网络加载或浏览器安全模型。
- 当前可运行后端仍是 retained `Widget` 树；screen host 根视口是 `ViewportWidget`；文档页创建入口是 `UiDocumentScreens`。

## 作者层能力

### DOM 与事件

- `UiDocument` 是 HTML-like 文档作者入口；`ElementNode` 与 `TextNode` 分别承载元素和文本。
- `DocumentNode` 维护父子关系与 layout/paint 分层失效版本；tree/text/geometry 变更提升 layout 与 paint version，paint-only style/custom renderer/keyframes 注册变更只提升 paint version。
- `ElementNode.__getElementUid()` 是进程内唯一内部身份，只用于测试、调试、缓存和内部追踪；不等同于 HTML `id`，不进入属性表或选择器语义。
- 元素事件支持 active、click、focus、key、text input 与 focusable 标记；active/click/key/text input 从目标向父元素冒泡；focus 事件带 `focusVisible` 区分键盘可见焦点与鼠标普通焦点。

### 控件

- `DocumentButtonControl` 以 `ElementNode` 为根表达按钮，支持 action、enabled/disabled、鼠标 click、Enter/Space 激活、active、focus-visible 与基础视觉状态。
- `DocumentTextInputControl` 支持 placeholder、maxLength、控制字符过滤、Backspace 删除、enabled/disabled、focus 边框和 change handler。
- `DocumentToggleSwitchControl` 以 flex row + justifyContent 表达开关，支持 click/Enter/Space 切换、enabled/disabled、focus-visible 与 toggle change handler。
- `DocumentSegmentedSelectorControl` 以 element-backed button 组表达分段选择器，支持鼠标/键盘选择、enabled/disabled、选中态视觉和 selection handler。
- `DocumentInventorySlotGridControl` 使用 `ElementNode` + 自定义渲染回调表达只读背包格子网格，复用 `ui.inventory` 下的 `InventorySlotGridLayout` 与 `InventorySlotGridItemRenderer`。

### 样式

- 样式入口是 `ElementNode.style()` 的 Java inline style API；`UiStyleResolver` 输出 `ComputedStyle`。
- 当前样式支持 `display`、px/%/auto 长度、margin/padding/border、颜色、文本色、`opacity`、`overflow`、`position: static/relative/absolute/fixed`、`top/right/bottom/left`、`zIndex`、flex row/column、gap、align、justify、grow/shrink、`vertical-align: baseline/top/middle/bottom`、`backdropBlurRadius`、`backdropSaturation`。
- transition 声明支持 `transition-property`、duration、delay 与 timing function。
- keyframe animation 声明支持 `animation-name`、duration、delay、iteration count、fill mode 与 timing function。
- 样式系统当前不提供 CSS parser、样式表层叠、选择器匹配或媒体查询语义；作者侧使用 Java API 直接声明样式。

## 布局能力

- `DocumentLayoutEngine` 支持 block flow、box model、px/% 长度、auto 高度、`display:none` 过滤、直接文本子节点测量/换行、text/span 首期 inline flow、flex row/column、gap、align、justify、grow/shrink。
- inline flow 通过 `DocumentLayoutInlineFragment` 输出 fragment 几何；支持同一 inline 元素同一行相邻片段合并、跨行分片、首片/末片标记、父 inline fragment 覆盖嵌套 inline 子内容。
- inline 元素左右 margin 参与行内流但不绘制；左右 border/padding 参与行内流；上下 border/padding 扩展行高与 fragment 表面；跨行 inline fragment 支持局部圆角切片。
- `position: relative` 保留普通流位置，仅记录视觉偏移；绘制、命中与滚动几何阶段应用该偏移。
- `position: absolute` 脱离普通流，不撑开父 auto 高度，不参与 flex item 分配；定位基准是最近 non-static ancestor 的 content box，没有 positioned ancestor 时回退根 content box。
- `position: fixed` 脱离普通流，相对 `HtmlLikeDocumentWidget` viewport containing block 定位，不随根滚动内容移动。
- absolute/fixed 在 `width:auto` 且 left+right 同时存在时横向 stretch，在 `height:auto` 且 top+bottom 同时存在时纵向 stretch，并扣除 inset、margin、border 与 padding 求解 content size。
- `DocumentLayoutBox` 提供 CSS-like stacking phase：负 `z-index` positioned、普通流、positioned auto/0、正 `z-index` positioned。
- `DocumentEffectChain` 是 paint context、backdrop-filter、overflow clip、stacking context 与局部排序边界的统一判定点。
- `DocumentScrollState` 根据布局盒推导 `overflow:auto` 的可滚范围、滚动偏移、滚动条几何和 track/thumb 拖拽状态。
- `DocumentHitTestEngine` 在滚动、relative/absolute/fixed、inline fragment、四阶段 stacking 与 overflow/effect boundary 语义下返回命中的最深元素。

## 绘制与效果能力

- `DocumentPaintEngine` 把布局盒树转换为中立 paint command：paint-context、backdrop-filter、background、border、text、clip、custom、scrollbar。
- 绘制顺序复用 `DocumentEffectChain` 与 stacking phase；stacking context 或 overflow clip effect boundary 会把子树作为整体隔离。
- 非根 `opacity < 1`、positioned + 显式 `z-index`、或 `backdrop-filter` 元素会输出 `PAINT_CONTEXT_START` / `PAINT_CONTEXT_END`。
- `DocumentPaintRenderer` 将 paint command 投影到 `UiRenderContext`；`PAINT_CONTEXT` 与 `OVERFLOW_CLIP` 是栈式 pass，`BACKDROP_FILTER` 是 stateless pass。
- group opacity 使用 `UiRenderContext.pushPaintContext(...)` / `popPaintContext()` 与 `PaintContextCompositor`；FBO 不可用时回退到命令级 alpha。
- `CUSTOM` 内容在元素背景/边框之后、clip/子树之前绘制，可参与 group opacity。
- `backdrop-filter` 语义只采样当前 UI 主层中元素背后的已绘制 UI 内容；不直接模糊游戏世界画面。若页面壳提供已模糊背景图，它只作为普通 UI 背景参与采样。
- `UiMainLayerSnapshotService` 按 read framebuffer、UI 主层内容版本、采样区域、blur 级别复用同帧局部快照；支持 128px block 对齐、较大 block atlas 覆盖、multi tile atlas 组装、downsample + separable blur filter pass。
- backdrop shader 路径优先使用 GLSL 平滑采样与 saturation；shader 或 FBO 不可用时回退固定管线近似或 tint fallback。
- 元素级 backdrop blur 半径由 `DocumentEffectChain.MAX_BACKDROP_BLUR_RADIUS` 限制为 48。
- 根滚动条保持可见；嵌套 `overflow:auto` 滚动条只在最近有效滚动后的短暂窗口内绘制，空闲后隐藏。

## 动画能力

- `DocumentAnimationClock` 是可注入时间源；`DocumentAnimationTimeline` 维护 computed style 之上的运行时覆盖层。
- 运行值不写回作者侧 inline style。
- 运行值优先级固定为：`transition > keyframe animation > computed style`。
- 当前可动画属性：`BACKGROUND_COLOR`、`BORDER_COLOR`、`TEXT_COLOR`、`OPACITY`、`BORDER_RADIUS`、`BACKDROP_BLUR_RADIUS`、`WIDTH`、`HEIGHT`、`MARGIN_LEFT`、`MARGIN_RIGHT`、`PADDING_LEFT`、`PADDING_RIGHT`。
- 动画属性按影响范围分类：paint、effect、layout；按插值值类型分类：color、float。
- `DocumentAnimationProperty` 是属性枚举与值类型元数据来源；`DocumentAnimationTimeline` 内部通过属性运行语义表集中处理颜色/数值 base value、px-only transition 判定和 keyframe used value 归一化，并把 transition/keyframe/fill 的存在性判断、完成计数和完成清理集中到单元素状态 helper，避免新增属性时散落维护多套 if/else 白名单。
- `DocumentAnimationTimeline.DiagnosticsSnapshot` 是当前只读动画诊断快照，可按来源统计 transition、keyframe、forwards fill，并按 impact 区分 paint/effect/layout；诊断快照不推进或清理动画状态。
- transition 基于 computed style 基准值变化创建；清除 `transition-property` 或 duration 变为 0 时，运行中 transition 在下一次 timeline 刷新回到 computed style 基准值。
- `DocumentAnimationTimeline.hasRunningTransition(element, property)` 可按元素/属性查询 transition 运行状态。
- keyframe animation 通过 `UiDocument.registerKeyframes(...)` 注册命名 `DocumentKeyframes`，由元素的 `animation-name` 引用。
- keyframes 支持 color/float 轨道、多段 stop、delay、有限 iteration、fill-mode none/backwards/forwards/both 与 timing function；float 轨道可覆盖受控 layout 属性 `WIDTH/HEIGHT/MARGIN_LEFT/MARGIN_RIGHT/PADDING_LEFT/PADDING_RIGHT`。
- keyframe 声明重启条件：`animation-name`、keyframes 对象、duration、delay、iteration count、fill-mode、timing function 变化。
- 布局盒尺寸变化只刷新数值轨道 used value 归一化边界，不重启 keyframe 进度。
- 同名 keyframes 定义对象替换会让引用元素重启动画；定义移除会取消引用元素动画并清理对应 fill。
- forwards fill 后作者侧修改同属性 computed target 或同属性 transition 接管时，该属性 fill 让位给作者/transition 值；多属性 fill 只清理被作者改动的属性；layout keyframe 声明清除或 keyframes 定义移除会清理对应 layout 运行值。
- 数值 keyframe used value 归一化范围：opacity clamp 到 0..1，border-radius clamp 到当前布局盒半径上限，backdrop blur clamp 到 48。
- `BACKDROP_BLUR_RADIUS` 是 effect-affecting 长度 transition；退场期间即使目标 blur 为 0，只要 transition 仍运行，paint 仍保留 backdrop command。
- `WIDTH/HEIGHT/MARGIN_LEFT/MARGIN_RIGHT/PADDING_LEFT/PADDING_RIGHT` 是当前受控 layout-affecting transition/keyframe 属性；当前 transition 稳定承诺仅为 px-to-px，auto/% 不创建 width/height/margin/padding transition。
- `HtmlLikeDocumentWidget` 在 layout 动画活跃或存在 layout forwards fill 运行值时，先用静态 computed style 布局刷新 timeline，再用 `DocumentLayoutEngine.LayoutRuntimeValueResolver` 按运行态 `WIDTH/HEIGHT/MARGIN_LEFT/MARGIN_RIGHT/PADDING_LEFT/PADDING_RIGHT` 同帧重建布局并刷新滚动范围；绘制、hit-test、滚动交互、焦点遍历和端到端点击分发使用同一份运行态布局几何；收缩动画期间滚动偏移按运行态最大滚动范围夹取，不提前使用目标静态布局夹取；作者修改同属性目标后恢复作者布局值。
- timing function 当前支持 linear、ease、ease-in、ease-out、ease-in-out 的简化插值。

## Widget 适配与页面入口

- `HtmlLikeDocumentWidget` 承载 `UiDocument -> style -> layout -> paint command -> UiRenderContext` 链路；生产默认使用 `DefaultTextMeasureService`，测试可注入确定性 `TextMeasureService` 和动画时钟。
- `HtmlLikeDocumentWidget` 支持根视口滚动模式：根元素 border box 固定为 widget 视口尺寸，页面级滚动由根元素 `overflow:auto` 和 `DocumentScrollState` 承担。
- `HtmlLikeDocumentWidget` 缓存按 layout version、paint version、text measure epoch、widget 尺寸、scroll version 与动画状态分层失效。
- paint-only 样式变更复用已有布局几何，通过 `DocumentLayoutBox.refreshComputedStyles()` 刷新 computed style 快照，不重新文本测量。
- paint/effect 动画期间每帧重建 paint commands，但不重建 layout；layout 动画期间允许重建 runtime layout；动画结束后回到静态缓存。
- `HtmlLikeDocumentWidget.getActiveAnimationCount()`、`getAnimationDiagnosticsSnapshot()`、`getPerformanceDiagnosticsSnapshot()` 与 `hasLayoutRuntimeValueForDiagnostics()` 是当前只读诊断入口，用于 Smoke 页和测试展示未完成动画数量、transition/keyframe/fill 来源计数、paint/effect/layout 影响范围计数、layout 运行态覆盖是否存在，以及 `paintGen/staticLayout/runtimeLayout/textEpoch` 缓存边界状态，不作为作者层业务 API。
- 当前可访问页面：`ui_test` 诊断菜单、`ui_test_layout` 布局诊断页、`html_like_smoke` Smoke 页、`html_like_glass` 大面积磨玻璃页、`inventory_overview` 背包页。
- 当前 definition-backed 生产入口使用 `DefinitionBackedHtmlLikeDocumentScreen` + `DirectDocumentPageAuthoringSurface`；HTML-like 页面直接挂载 `HtmlLikeDocumentWidget`，不套旧 retained 页面壳。

## 游戏内验收边界

- 入口：右 Shift 打开诊断菜单，可进入布局诊断页、HTML-like Smoke 页、Large Glass Lab 页和背包页。
- Smoke 页覆盖：控件交互、文本输入、Tab 焦点、按钮、开关、overflow auto 滚动、absolute/fixed 定位、absolute stretch、inline fragment/vertical-align、group opacity、stacking context、backdrop-filter、opacity FBO、`WIDTH/HEIGHT/MARGIN_LEFT/MARGIN_RIGHT/PADDING_LEFT/PADDING_RIGHT` layout transition 和 layout keyframe/forwards fill；`PADDING_LEFT/PADDING_RIGHT` 游戏内 Smoke 已验收正确；layout 动画区会显示覆盖属性清单、active 总数、transition/keyframe/fill 来源计数、paint/effect/layout 分 impact 运行态状态，以及 `Cache runtime: paintGen/staticLayout/runtimeLayout/textEpoch` 缓存诊断；当前 layout `t/k/f` 游戏内诊断已验收正确。
- Smoke 页 `Layout animation probe`：点击蓝色 `Layout card`，宽高在 92x34 与 190x58 间过渡，右侧绿色 sibling 应随动画被推开或回收；点击琥珀色 `Margin card`，左右 margin 在 tight/wide 间过渡，右侧棕色 sibling 应随 margin 动画位移；点击紫色 `Padding card`，左右 padding 在 tight/wide 间过渡，卡片内容和右侧紫色 sibling 应随 padding 动画位移；点击青色 `Keyframe card` 启动 `layoutFillProbe` width keyframe，运行时 layout `k` 应增加，结束后 layout `f` 应保留，再次点击清除 animation 后 `f` 应归零并恢复作者宽度。当前游戏内已确认 transition、keyframe、forwards fill 均有可见诊断路径，padding 动画平滑、内容和 sibling 同步位移、`t/k/f` 计数进入与退出符合预期。
- Glass Lab 覆盖：大面积 backdrop、shader/fallback 路径、snapshot captured/reused、block/atlas/tile 诊断、downsample/separable blur filter 诊断、嵌套/同级多 glass 采样稳定性。
- 背包页覆盖：hotbar/backpack 网格、自定义格子绘制与返回按钮交互。

## 清退边界

- 不新增扩大直接 `Widget` 作者入口的 API；新增作者能力优先放在 `UiDocument`、`ElementNode`、样式系统、控件适配或 HTML-like 后端能力中。
- `Widget`、`ViewportWidget`、`UiInputRouter`、`UiRenderContext` 仍是当前 backend/runtime 基础，不能作为旧作者入口残留直接删除。
- `InventorySlotGridLayout`、`InventorySlotGridItemRenderer`、`InventorySlotSnapshot`、`MinecraftInventorySlotGridItemRenderer` 与 `UiRuntimeAdapters` 是 HTML-like 背包格子控件复用能力，不能误删。
- 旧 retained 页面壳、旧 retained 文档控件、旧 retained control widget、旧兼容容器与旧 retained 主题样式对象不作为新能力扩展方向。

## 运行与验证

- Windows 环境使用 PowerShell。
- 当前本机 Java 环境集中在 `D:\.MyApps\.ENV`；默认 `JAVA_HOME` 为 `D:\.MyApps\.ENV\jdk-21.0.10+7`。
- 当前用户级 `GRADLE_USER_HOME` 为 `D:\.MyApps\.ENV\gradle-home`；Agent shell 不一定继承该变量，验证命令应显式设置。
- 标准验证命令：`$env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"; ./gradlew.bat --no-configuration-cache test`。
- 标准编译命令：`$env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"; ./gradlew.bat --no-configuration-cache compileJava`。
- diff 空白检查：`git diff --check`。
- `runClient21` 可用于游戏内验收：`$env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"; ./gradlew.bat --no-configuration-cache runClient21`。
- 纯 JVM 测试不得直接触发 `DefaultTextMeasureService`/`FontService` 默认字体运行时；涉及 HTML-like 文本测量的测试应注入确定性 `TextMeasureService`。
- 并发 Gradle 构建可能因 `build/tmp/decompileSrgJar/mc.jar` 触发 Windows 文件锁冲突；验证构建按串行执行。

## 关键文件

- 规划：`项目建议.md`。
- DOM：`src/main/java/club/heiqi/uilib/ui/dom/UiDocument.java`、`DocumentNode.java`、`ElementNode.java`、`TextNode.java`。
- 控件：`src/main/java/club/heiqi/uilib/ui/dom/control/`。
- 样式：`src/main/java/club/heiqi/uilib/ui/style/UiStyleDeclaration.java`、`UiStyleResolver.java`、`ComputedStyle.java`、`UiStyleLength.java`、`UiStyleInsets.java`。
- 动画：`src/main/java/club/heiqi/uilib/ui/animation/DocumentAnimationTimeline.java`、`DocumentAnimationProperty.java`、`DocumentKeyframes.java`。
- 布局：`src/main/java/club/heiqi/uilib/ui/layout/DocumentLayoutEngine.java`、`DocumentLayoutBox.java`、`DocumentEffectChain.java`、`DocumentHitTestEngine.java`、`DocumentScrollState.java`。
- 绘制：`src/main/java/club/heiqi/uilib/ui/paint/DocumentPaintEngine.java`、`DocumentPaintCommand.java`、`DocumentPaintRenderer.java`。
- Widget 适配：`src/main/java/club/heiqi/uilib/ui/document/HtmlLikeDocumentWidget.java`。
- 页面：`src/main/java/club/heiqi/uilib/ui/screen/UiDocumentScreens.java`、`HtmlLikeSmokeDocumentPageController.java`、`HtmlLikeGlassDocumentPageController.java`、`HtmlLikeInventoryOverviewDocumentPageController.java`、`UiLayoutDiagnosticsDocumentPageController.java`。
- 渲染运行时：`src/main/java/club/heiqi/uilib/ui/render/UiRenderContext.java`、`UiMainLayerSnapshotService.java`、`UiRenderTarget.java`、`UiBackdropShaderProgram.java`。
- 后端与宿主：`src/main/java/club/heiqi/uilib/ui/widget/Widget.java`、`ViewportWidget.java`、`src/main/java/club/heiqi/uilib/ui/input/UiInputRouter.java`、`src/main/java/club/heiqi/uilib/ui/runtime/UiRuntimeAdapters.java`。
- 背包能力：`src/main/java/club/heiqi/uilib/ui/inventory/`。

## 下一步边界

- CSS transition / animation MVP 已完成首轮收口：transition、keyframe、forwards fill 均具备 Smoke 可见诊断路径，`WIDTH/HEIGHT/MARGIN_LEFT/MARGIN_RIGHT/PADDING_LEFT/PADDING_RIGHT` layout 动画已完成纯 JVM、Smoke 探针与游戏内视觉/诊断验收。
- 下一阶段暂停继续扩展 layout-affecting 属性；若后续确需新增属性，必须重新证明必要性，并继续限制在少量可控属性与明确 fallback。
- 缓存与性能诊断可见性一期已接入：Smoke 页显示 `paintGen/staticLayout/runtimeLayout/textEpoch`，纯 JVM 测试覆盖 paint/effect 动画不增长 layout 计数、layout 动画增长 runtime layout 计数，以及动画结束后重复绘制回到静态缓存。
- 不一次性开放全量布局动画。
- paint/effect 动画不能触发布局；layout 动画可以重布局，但结束后必须恢复静态缓存。
- inline formatting、effect chain、snapshot atlas 和 blur/filter 优化只在阻塞动画探针、真实页面迁移或控件展示时优先处理。
