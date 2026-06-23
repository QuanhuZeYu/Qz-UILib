# 决策：qzui test 使用 scene 新栈承载容器

## 背景

用户已下达最高指示：旧 HTML-like / `ui.dom` 栈彻底退出实际业务接入，暂不删除，仅作为废弃参考代码。

此前 `/qzuilib test` 默认入口仍走旧链路：命令入口进入 `UiDiagnosticsScreens.createUiTest`，再经 `InternalHostedScreenFactory`、`UiTestDocumentPageController`、`UiDocument` 和 `HtmlLikeDocumentWidget` 承载旧视觉矩阵。
scene 新栈已有 `McScreenBridge`、`UiSurface`、`SceneHostWidget`、`SceneControlsHostWidget` 以及 Scene/Controls/Scroll/Table 独立 demo，可支撑新 test 首页/导航容器。
当前 `/qzuilib test` 默认入口已切到 `SceneTestHubScreen`，旧链通过 `/qzuilib legacy_test` 保留。

## 候选方案

- **继续沿用旧 test 承载**：改动最少，但违反旧栈退出实际业务接入的最高指示。
- **一次性迁移旧视觉矩阵和断言 runner**：目标彻底，但旧矩阵深度依赖 `ui.dom`、`HtmlLikeDocumentWidget` 和旧页面 authoring API，范围过大。
- **新增 scene test hub 并逐步切默认入口**：先用新栈做首页/导航容器，挂接现有 scene demo，旧矩阵暂留参考。

## 最终选择

采用“新增 scene test hub 并切换默认入口”。第一批只做新栈 qzui test 首页/导航容器，挂接已有 Scene/Controls/Scroll/Table demo；不迁旧 `UiTestMatrixRegistry`、`UiTestGroupVisualBuilder`、旧断言 runner 或 `HtmlLikeDocumentWidget` 页面体系。

旧栈代码暂不删除，但从决策生效后不再作为实际业务接入路径或新功能承载路径。

## 选择原因

- 对齐 `NORTH_STAR.md` 的 scene 新栈方向，避免继续把实际入口建立在旧栈上。
- 复用已跑通的 `McScreenBridge + UiSurface` 承载地基，第一批范围可控。
- 保留旧矩阵作为参考/回归资料，避免一次性重写旧断言体系带来不必要风险。
- 新容器可以用 signal 驱动导航状态，保持 I1/I2/I11，不引入命令式切 UI 路径。

## 影响范围

- `/qzuilib test` 默认入口已切到 scene test hub。
- `/qzuilib scene_test` 保留为过渡别名，继续打开同一新栈 hub。
- `/qzuilib legacy_test` 保留旧 HTML-like 视觉矩阵，仅供 legacy 参考回归。
- 旧 `UiTestDocumentPageController`、旧视觉矩阵、旧断言 runner 只保留为参考资料，不再继续扩展业务能力。
- 新 test hub 不得依赖 `ui.dom`、`HtmlLikeDocumentWidget`、`BaseScreen` 或旧 page authoring API。

## 后续注意事项

- `scene_test` 入口已经通过用户真机确认，GUI Scale != 1 下 hover/click 命中无异常；默认 `/qzuilib test` 复用同一新 hub。
- 导航 handler 只能写 signal，不能直接操作 scene 节点属性或树结构。
- 切换 MC screen 必须走 `UiScreenManager.enqueue` 延迟到帧外，不能在 `drawScreen` 渲染回调内直接 `displayGuiScreen`。
- 平台输入、文本桥和 MC/LWJGL 细节只能停留在宿主适配层，不进入 `ui.scene.input` 核心包。
- 若决定带着 scaleFactor 命中偏移先上线，需要按 `NORTH_STAR.md` 偏离纪律登记影响范围和回填计划。
