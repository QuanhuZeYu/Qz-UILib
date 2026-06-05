# 决策：`/qzuilib test` 视觉矩阵协作者拆分

## 背景

`/qzuilib test` 旧运行时矩阵已清空，后续目标调整为“视觉化展示功能优先，浏览器语义验证为重要目标”。原 `UiTestDocumentPageController` 曾长期承载入口、分组、样例构建、断言、结果状态和大量旧 demo helper，文件规模超过 2000 行，不适合继续堆叠新矩阵逻辑。

## 候选方案

- 继续在 `UiTestDocumentPageController` 内恢复旧卡片矩阵和运行时断言。
- 只把用例字段抽成模型，页面构建和状态刷新仍留在控制器内。
- 将 registry、分组视觉 builder、语义 checker 和结果 state 拆成独立协作者，控制器只保留生命周期、导航和环境刷新。

## 最终选择

采用第三种方案：`UiTestDocumentPageController` 只负责页面生命周期、`HtmlLikeDocumentWidget` 挂载、首页/分组页导航和环境信息刷新；测试矩阵由 `UiTestMatrixRegistry`、`UiTestGroupVisualBuilder`、`UiTestSemanticChecker`、`UiTestMatrixState` 及视觉/语义/汇总状态模型协作承载。

## 选择原因

- 避免在超大控制器中继续累积新逻辑，后续样例接入时按真实职责扩展。
- registry 固定十组入口、首轮计划数量、功能画廊和样例清单，避免重新散落硬编码。
- builder 负责首页和二级页视觉结构，后续 CSS / Layout / Paint 样例可按分组逐步接入。
- checker 固定自动语义与人工确认边界，避免视觉展示通过掩盖浏览器语义错误。
- state 将视觉状态、语义状态和汇总状态拆开，避免旧单一状态模型误导结果判断。

## 影响范围

- `/qzuilib test` 仍保留 DOM / CSS / Layout / Paint / Input / Controls / TextFont / Animation / RuntimeHost / RemoteNet 十组导航。
- 首轮计划数量按视觉矩阵规格表合计为 59 张；当前 P0 仅接入模型和首页/二级页框架，真实样例列表仍为空。
- 旧 `RuntimeTestCase` / `RuntimeTestResult` / `RuntimeTestStatus` 嵌套模型和旧 demo helper 不再作为后续恢复入口。

## 后续注意事项

- 后续新增样例应优先写入 registry，再由分组 builder 渲染，不要回到控制器内硬编码卡片。
- 样例编号使用新前缀，如 `VIS-CSS-001`、`SEM-LAYOUT-003`，不要恢复旧编号矩阵。
- 观察要点必须以 `预期结果：` 开头；语义检查失败时由 state 分别更新视觉状态和语义状态。
- 涉及游戏内 UI、HUD、输入、远程页面或网络 smoke 的样例仍需 `runClient21` 验证。
