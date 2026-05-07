# AI记忆文档

## 最高优先级信息

- 当前项目正准备固化第一版面向其他开发者的开放入口；旧 API、旧行为或旧内部结构仍无外部兼容承诺，但新增开放文档与后续 API 门面需要按首版开发者体验审视。
- 当前主线是 HTML-like UI 渲染框架与 CSS transition / animation 语义 MVP；inline formatting 只处理阻塞动画探针、真实页面迁移或控件展示的最小必要项。
- 清退 retained 作者入口、旧页面壳与兼容主题时，以 HTML-like 主线价值为准；没有复用价值的结构优先删除或重写。
- 清退不能误删当前 HTML-like 仍复用的后端/运行时能力：`Widget`、`ViewportWidget`、`UiInputRouter`、`UiRenderContext`、`UiRuntimeAdapters`、背包格子底层布局/渲染适配与 Minecraft 宿主会话能力。
- 作者层只暴露 HTML-like/CSS-like 语义；不得向页面作者暴露 Minecraft GUI 生命周期、OpenGL/FBO/shader/stencil、snapshot-only 诊断或宿主背景效果细节。

## 项目定位

- 本仓库是 Minecraft 1.7.10 / GTNH / LWJGL3ify 环境下的 Java UI 框架工程。
- 当前目标是一套 HTML-like UI 渲染框架，覆盖文档树、样式计算、盒模型、布局、绘制、裁剪、滚动、效果合成、命中测试与输入分发。
- 本项目不是完整浏览器内核，不承诺 HTML5 全量解析、CSS 全量规范、JavaScript DOM API、网络加载或浏览器安全模型。
- 当前可运行后端仍是 retained `Widget` 树；screen host 根视口是 `ViewportWidget`；宿主业务文档入口是 `UiDocumentScreens.createDocumentScreen(...)`；开放化调整文档入口是 `docs/开放化调整.md`，具体开发者使用文档按分级目录存放在 `docs/使用文档/`。

## 作者层能力

- 具体 DOM、事件、控件、样式、表格与背包槽位能力不再在 AI 记忆中重复维护；以 `docs/使用文档/README.md` 及其分级文档为准。
- 关键入口说明见 `docs/使用文档/01-入门/最小文档页面.md`。
- 基础控件说明见 `docs/使用文档/02-控件/基础控件.md`。
- 表格与背包槽位边界见 `docs/使用文档/02-控件/表格与背包槽位.md`。
- 背包页 tooltip 重设计需求见 `docs/使用文档/02-控件/背包Tooltip设计需求.md`。

## 布局能力

- 具体布局能力、非目标与普通表格边界不再在 AI 记忆中重复维护；以 `docs/使用文档/01-入门/项目定位与能力边界.md` 和 `docs/使用文档/02-控件/表格与背包槽位.md` 为准。
- 若后续新增 layout-affecting 能力，必须同步更新使用文档并保留最小必要验证。

## 绘制与效果能力

- 具体绘制、效果、custom renderer 与宿主边界不再在 AI 记忆中重复维护；以 `docs/使用文档/01-入门/项目定位与能力边界.md`、`docs/使用文档/02-控件/表格与背包槽位.md` 和 `docs/使用文档/03-宿主集成/Minecraft界面入口.md` 为准。
- 普通 UI 表面仍应优先用 DOM 元素和标准样式表达，不应为了方便落到 `CUSTOM` 手绘。

## 动画能力

- 具体动画属性、运行边界和诊断能力不再在 AI 记忆中重复维护；对外能力以 `docs/使用文档/01-入门/项目定位与能力边界.md` 为准，阶段性验收结论继续保留在本文件“下一步边界”。
- 不一次性开放全量 layout 动画；新增动画能力前必须先证明必要性，并同步补使用文档与验证。

## Widget 适配与页面入口

- 具体 Widget 适配和 Minecraft 宿主接入说明不再在 AI 记忆中重复维护；以 `docs/使用文档/01-入门/最小文档页面.md` 和 `docs/使用文档/03-宿主集成/Minecraft界面入口.md` 为准。
- 宿主层已新增业务文档 screen 创建入口 `UiDocumentScreens.createDocumentScreen(...)`，用于从 Minecraft 入口直接传入 `UiDocument` 构建回调并打开 UI。
- 当前可访问页面仍包括 `ui_test`、`ui_test_layout`、`html_like_smoke`、`html_like_glass`、`inventory_overview`；首版开放化目标是把测试期右 Shift 入口和原版背包注入按钮迁移为 `/qzuilib test` 触发的诊断跳转菜单。

## 游戏内验收边界

- 测试入口迁移为 `/qzuilib test` 打开诊断跳转菜单；右 Shift 诊断热键与原版背包页 `背包UI` 按钮不再作为默认入口。
- 客户端聊天命令内不要直接 `displayGuiScreen(...)`；需要延后到当前帧聊天界面关闭之后再切换，否则新界面会被聊天关闭流程覆盖。
- 诊断页、Smoke 页、Glass Lab 和背包示例页的具体覆盖能力不再在 AI 记忆中重复维护；开放入口与使用边界以 `docs/使用文档/` 为准。
- 当前已完成的阶段性验收结论：动画 MVP、layout 动画缓存边界、Smoke 诊断路径、Glass Lab 采样诊断路径和背包页生产级前收口均已过一轮游戏内确认；后续若变更相关能力，应同步更新使用文档和对应测试。

## 清退边界

- 不新增扩大直接 `Widget` 作者入口的 API；新增作者能力优先放在 `UiDocument`、`ElementNode`、样式系统、控件适配或 HTML-like 后端能力中。
- `Widget`、`ViewportWidget`、`UiInputRouter`、`UiRenderContext` 仍是当前 backend/runtime 基础，不能作为旧作者入口残留直接删除。
- `InventorySlotGridLayout`、`InventorySlotGridItemRenderer`、`InventorySlotSnapshot`、`MinecraftInventorySlotGridItemRenderer`、`InventoryOverviewModel` 与 `UiRuntimeAdapters` 是 HTML-like 背包格子控件和真实背包交互复用能力，不能误删。
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
- 开放化调整：`docs/开放化调整.md`。
- 开放化使用文档：`docs/使用文档/README.md`。
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

- CSS transition / animation MVP 已完成游戏内收口验收：transition、keyframe、forwards fill 均具备 Smoke 可见诊断路径，`WIDTH/HEIGHT/MARGIN_LEFT/MARGIN_RIGHT/PADDING_LEFT/PADDING_RIGHT` layout 动画已完成纯 JVM、Smoke 探针、游戏内视觉、诊断与缓存边界验收。
- 下一阶段暂停继续扩展动画属性和动画诊断显示；若后续确需新增 layout-affecting 属性，必须重新证明必要性，并继续限制在少量可控属性与明确 fallback。
- HTML-like table 与 `inventory_overview` 背包页能力详情以 `docs/使用文档/02-控件/表格与背包槽位.md` 为准；后续优先在真实背包 UI 中收口槽位视觉、焦点、滚动、命中与真实数据渲染问题，其次再评估 dirty subtree / 细粒度缓存或 effect chain 后续优化。
- 背包页 tooltip 已单独固化为设计需求文档；后续实现应按 Apple 风格鼠标跟随浮层、原版 tooltip 内容优先、拖拽时完全隐藏的口径执行。
- 背包页 tooltip 第一阶段代码已开始落地：当前已接入页面级跟随鼠标定位、方向回退、宽度压缩与拖拽隐藏；定位计算收敛在 `InventoryTooltipLayoutResolver`，宿主鼠标坐标通过 `DocumentPageRuntimeView` 暴露给页面层。已确认可用空间预留应按 32px 鼠标清空带处理，而不是继续沿用对角分量近似。
- 开放化文档已开始固化：根索引为 `docs/开放化调整.md`，分级使用文档位于 `docs/使用文档/`，覆盖项目定位、最小文档页面、基础控件、表格与背包槽位、Minecraft 界面入口和指令触发方案；宿主业务文档 screen 创建入口已完成首轮代码收口；测试入口已迁移为 `/qzuilib test` 诊断跳转菜单，后续优先游戏内验证该命令与菜单跳转。
- 不一次性开放全量布局动画。
- paint/effect 动画不能触发布局；layout 动画可以重布局，但结束后必须恢复静态缓存。
- inline formatting、effect chain、snapshot atlas 和 blur/filter 优化只在阻塞动画探针、真实页面迁移或控件展示时优先处理。
