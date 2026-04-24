# AI记忆文档

## 长期稳定信息

### 项目定位
- 本仓库是 1.7.10 Minecraft UI 框架工程，当前主线目标是让文档型 UI 与通用控件模型更接近网页式 UI 心智模型。
- 当前文档界面的主创建边界已经收口到 `UiDocumentScreens`，并通过 `DocumentScreenEnvironment`、`DocumentScreenDefinition` 与 `DocumentScreenChromeResolver` 暴露显式入口。

### 当前稳定架构边界
- `UiSurfaceStyle` 现在是**纯外观值对象**，只负责 `fillColor`、`borderColor`、`cornerRadius`。
- UI 框架本身不再拥有 backdrop effect / 磨玻璃能力；如需背景模糊，只能由宿主在进入 UI 主渲染前统一捕获并处理背景，`Widget`、文档页与控制器保持无感知。
- 后代内容裁剪不再由 surface 外观隐式决定，而是由显式结构容器负责：
  - `Widget` 只保留 child clip 语义；
  - `DivWidget` 通过 overflow/viewport 盒决定矩形内容裁剪；
  - `ScrollViewportWidget` 继续负责滚动视口的结构性裁剪；
  - 如需 rounded structural clip，应通过专用结构容器（当前探索版为 `RoundedScrollViewportWidget`）显式接入，而不是让 `UiSurfaceStyle` 回收 descendant clip 语义；但在当前 MC/GL 运行时中，live rounded stencil 会让文档页子树内容整体不可见，因此该能力尚未重新接入 `DocumentPageWidget` 生产链路。
- `UiRenderContext` 继续承载主渲染与 deferred post-main 回放的 clip snapshot，但 snapshot 现在表达的是**显式结构裁剪结果**，不再继承 surface 外观带来的隐式 clip。
- 文档主题中的 shell/card rounded style 现在只表达 border-radius 外观，不再顺带控制 descendant clip；文档页壳若需要圆角内容裁剪，只能把 theme 中的 `cornerRadius` 数值显式映射到结构容器配置。

### 运行与验证
- Windows 环境下使用 PowerShell。
- 当前协作环境下，已实际验证 `lsp_diagnostics` 可对指定 Java 文件返回结果；仓库根目录级别是否自动覆盖 Java 诊断，仍需按实际调用结果确认。
- 当前编译门槛：`./gradlew.bat compileJava`
- 典型定向验证命令：
  - `./gradlew.bat test --tests "club.heiqi.uilib.ui.theme.UiSurfaceStyleTest"`
  - `./gradlew.bat test --tests "club.heiqi.uilib.ui.screen.UiDocumentScreensTest"`
  - `./gradlew.bat test --tests "club.heiqi.uilib.ui.screen.UiTestDocumentPageControllerTest"`
  - `./gradlew.bat test --tests "club.heiqi.uilib.ui.screen.InventoryOverviewDocumentPageControllerTest"`
  - `./gradlew.bat test --tests "club.heiqi.uilib.ui.control.InventorySlotGridWidgetTest"`

### 当前关键文件
- `src/main/java/club/heiqi/uilib/ui/screen/UiDocumentScreens.java`
- `src/main/java/club/heiqi/uilib/ui/screen/BaseDocumentScreen.java`
- `src/main/java/club/heiqi/uilib/ui/theme/UiSurfaceStyle.java`
- `src/main/java/club/heiqi/uilib/ui/theme/UiDocumentThemes.java`
- `src/main/java/club/heiqi/uilib/ui/widget/Widget.java`
- `src/main/java/club/heiqi/uilib/ui/control/DivWidget.java`
- `src/main/java/club/heiqi/uilib/ui/control/ViewportWidget.java`
- `src/main/java/club/heiqi/uilib/ui/control/RoundedScrollViewportWidget.java`
- `src/main/java/club/heiqi/uilib/ui/render/UiRenderContext.java`
- `src/main/java/club/heiqi/uilib/ui/screen/UiScreenHostSession.java`

## 阶段性进度

### 当前已完成
- 文档型 screen 的显式 environment 主路径已经落地。
- screen chrome 策略已下沉到 `DocumentScreenDefinition`。
- rounded surface 能力已经接入默认文档 shell/card 外观。
- surface 与 descendant clip 已完成解耦，符合更接近 Web 的 `border-radius` / `overflow` 分层模型。
- 宿主渲染链现在负责在 UI 主渲染前插入统一的背景模糊步骤；该步骤只处理背景，不再由 UI 层声明或驱动任何 backdrop/effect 请求。
- rounded border 左侧线条不完整的问题已经通过像素中心描边修复。
- `Widget` 已具备复合结构裁剪扩展点，`RoundedScrollViewportWidget` 也保留为后续 rounded structural clip 的探索容器。
- 但 `DocumentPageWidget` 当前已回退为稳定的矩形 viewport clip 路径；原因是只要 live child pass 启用 rounded stencil，真实运行时就会出现“壳和滚动条正常、正文子树整体不可见”的回归，而 recording test 无法发现该问题。
- deferred post-main replay 与 rounded structural clip 的 snapshot/hit-test 逻辑目前只在测试/探索层成立，尚未重新接回文档页生产路径。

### 当前待继续事项
- rounded structural clip 当前仍处于运行时问题排查阶段；若后续继续推进，优先级应放在修复真实 GL/stencil/FBO 状态链，再考虑重新接入文档页或扩展更多 clip shape。
- 如需继续优化磨玻璃观感，应直接调整宿主级背景模糊实现，而不是重新把 backdrop/effect 声明能力接回 UI 框架。
