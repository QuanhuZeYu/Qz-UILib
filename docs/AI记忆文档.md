# AI记忆文档

## 长期稳定信息

### 项目定位
- 本仓库是 1.7.10 Minecraft UI 框架工程，当前主线目标是让文档型 UI 与通用控件模型更接近网页式 UI 心智模型。
- 当前文档界面的主创建边界已经收口到 `UiDocumentScreens`，并通过 `DocumentScreenEnvironment`、`DocumentScreenDefinition` 与 `DocumentScreenChromeResolver` 暴露显式入口。

### 当前稳定架构边界
- `UiSurfaceStyle` 现在是**纯外观值对象**，只负责 `fillColor`、`borderColor`、`cornerRadius`。
- 后代内容裁剪不再由 surface 外观隐式决定，而是由显式结构容器负责：
  - `Widget` 只保留 child clip 语义；
  - `DivWidget` 通过 overflow/viewport 盒决定矩形内容裁剪；
  - `ScrollViewportWidget` 继续负责滚动视口的结构性裁剪。
- `UiRenderContext` 继续承载主渲染与 deferred post-main 回放的 clip snapshot，但 snapshot 现在表达的是**显式结构裁剪结果**，不再继承 surface 外观带来的隐式 clip。
- 文档主题中的 shell/card rounded style 现在只表达 border-radius 外观，不再顺带控制 descendant clip。

### 运行与验证
- Windows 环境下使用 PowerShell。
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
- `src/main/java/club/heiqi/uilib/ui/render/UiRenderContext.java`
- `src/main/java/club/heiqi/uilib/ui/screen/UiScreenHostSession.java`

## 阶段性进度

### 当前已完成
- 文档型 screen 的显式 environment 主路径已经落地。
- screen chrome 策略已下沉到 `DocumentScreenDefinition`。
- rounded surface 能力已经接入默认文档 shell/card 外观。
- surface 与 descendant clip 已完成解耦，符合更接近 Web 的 `border-radius` / `overflow` 分层模型。
- rounded border 左侧线条不完整的问题已经通过像素中心描边修复。

### 当前待继续事项
- 如果后续需要“圆角外观 + 显式结构性内容裁剪”同时成立，应新增专门的结构性 rounded clip 容器，而不是回退到 surface 驱动裁剪。
- 磨玻璃 / backdrop effect 仍未实现，后续应基于独立 effect runtime 继续设计，而不是混入 surface 外观值对象。
