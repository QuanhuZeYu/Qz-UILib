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
- HTML-like 文档树最小骨架已在 `club.heiqi.uilib.ui.dom` 落地；`UiDocument` 是当前文档作者入口，`DocumentNode` 负责父子关系与 mutation version，`ElementNode` 与 `TextNode` 分别承载元素和文本。
- 后续作者侧入口应逐步迁移到 HTML-like 文档/元素/样式 API；底层 `Widget`、`DivWidget`、`ScrollViewportWidget` 应逐步退为 backend adapter 或兼容层。
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
- 当前构建环境已验证可用的稳定命令为：`$env:GRADLE_USER_HOME="C:\temp\gradle-home"; ./gradlew.bat "-Dorg.gradle.java.installations.paths=C:\temp\zulu8\zulu8.92.0.21-ca-jdk8.0.482-win_x64,C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot,C:\Users\泉户 黑崎\.jdks\jdk-25.0.2+10" --no-configuration-cache build`。
- 原因：GTNH 构建链要求 Azul Zulu JDK 8 工具链；当前机器默认只有 Temurin 21，且 Gradle 通过 Foojay 自动解析/下载 Zulu 8 在本环境下不稳定，因此需要显式提供本地 Zulu 8 路径。
- 本地 Zulu 8 已验证可执行路径：`C:\temp\zulu8\zulu8.92.0.21-ca-jdk8.0.482-win_x64\bin\java.exe`。
- 若并发启动多个 Gradle 构建，`decompileSrgJar` 可能会因共享 `build/tmp/decompileSrgJar/mc.jar` 触发 Windows 文件锁冲突；验证构建时应串行执行单个 Gradle 命令。
- 最近已验证通过：`compileJava`。
- 最近已验证通过的定向测试：`UiDocumentTest`、`UiSurfaceStyleTest`、`UiDocumentScreensTest`、`DocumentPageWidgetTest`、`InventorySlotGridWidgetTest`。

### 当前关键文件

- `项目建议.md`
- `src/main/java/club/heiqi/uilib/ui/dom/UiDocument.java`
- `src/main/java/club/heiqi/uilib/ui/dom/DocumentNode.java`
- `src/main/java/club/heiqi/uilib/ui/dom/ElementNode.java`
- `src/main/java/club/heiqi/uilib/ui/dom/TextNode.java`
- `src/main/java/club/heiqi/uilib/ui/screen/UiDocumentScreens.java`
- `src/main/java/club/heiqi/uilib/ui/screen/BaseDocumentScreen.java`
- `src/main/java/club/heiqi/uilib/ui/screen/UiScreenHostSession.java`
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

### 当前阶段目标

- 阶段 0：完成规划与文档清理，保留现有可运行链路。
- 阶段 1：新增 HTML-like 文档树与作者入口最小骨架；当前最小骨架已完成，下一步应把样式入口挂到元素层。
- 阶段 2：新增样式系统与 computed style 初版。
- 阶段 3：建立 box/layout tree，并逐步把现有 Div-like 布局能力迁移到新模型。
- 阶段 4：建立 paint command、clip、scroll、deferred replay 的统一渲染模型。
- 阶段 5：迁移事件与控件适配。
- 阶段 6：清退旧 public screen 构造入口与直接 widget authoring 示例。

### 下一步执行项

- 新增 `ui.style` 或同等命名包，定义样式属性值对象、默认值、继承属性与 computed style 初版。
- 为样式继承、长度解析和布局失效补最小单元测试。
- 选择一个现有诊断页作为迁移试点，避免一次性重写全部页面。
