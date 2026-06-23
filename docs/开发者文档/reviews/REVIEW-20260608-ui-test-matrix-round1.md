# `/qzuilib test` 矩阵第一轮审查

## 审查范围

- 目标：第一轮只审查 `/qzuilib test` 视觉/语义矩阵的注册、状态回写、批量运行和测试覆盖一致性，不修源码。
- 重点文件：`UiTestMatrixRegistry`、`UiTestDocumentPageController`、`UiTestGroupVisualBuilder`、`UiTestMatrixState`、`UiTestAssertionRunner` 及各专项 assertion runner。
- 覆盖测试：`UiTestDocumentPageControllerTest`、`UiTestRuntimeHostVisualMatrixTest`。

## 结论摘要

- 发现 2 个中等优先级问题。
- 当前自动/人工样例数量口径基本一致：已接入 53 张，自动 42 张，人工 11 张；RemoteNet 6 张仍为首轮缺口。
- 主要风险不是断言 runner 漏接，而是批量运行后的页面状态污染，以及视觉状态模型已有状态枚举但缺少人工结果回写入口。

## Findings

### P1：一键测试全部会把所有分组页停在最后一张样例

- 位置：`src/main/java/club/heiqi/uilib/internal/devtools/pages/UiTestDocumentPageController.java:257`
- 位置：`src/main/java/club/heiqi/uilib/internal/devtools/pages/UiTestDocumentPageController.java:264`
- 位置：`src/main/java/club/heiqi/uilib/internal/devtools/pages/UiTestDocumentPageController.java:266`
- 现象：`runAllAssertions()` 为了逐个渲染样例，会对每个分组取 `resolveGroupPageState(group)` 并在循环中执行 `pageState.setCaseIndex(index, cases.size())`。循环结束后没有恢复原先页内索引，因此首页点击“一键测试全部”后再打开任意已有样例的分组，默认展示的是该分组最后一张样例，
  而不是用户原先停留位置或第一张样例。
- 影响：批量测试结束后，用户进入 DOM / CSS / Controls / RuntimeHost 等分组时会看到最后一张，容易误以为前面的样例消失或当前分组默认入口异常。二级页里的“最近”上下文也会带着最后一次执行的 page 信息，降低人工复核效率。
- 测试缺口：`UiTestDocumentPageControllerTest.shouldRunAllCaseAssertionsFromHome()` 只验证首页统计和通过/人工数量，没有在批量运行后重新进入分组确认 `当前样例` 是否仍为预期索引。
- 建议：批量运行前保存每个 `UiTestGroupPageState` 的 `caseIndex`，运行后恢复；或让批量运行使用临时渲染索引，不复用交互页状态。补回归测试：从首页运行全部后打开 CSS/DOM，断言仍显示 `1 / n` 和首张样例。

### P2：视觉状态枚举有人工结果，但页面没有人工通过/失败回写路径

- 位置：`src/main/java/club/heiqi/uilib/internal/devtools/pages/UiTestVisualStatus.java:7`
- 位置：`src/main/java/club/heiqi/uilib/internal/devtools/pages/UiTestGroupVisualBuilder.java:380`
- 位置：`src/main/java/club/heiqi/uilib/internal/devtools/pages/UiTestMatrixState.java:223`
- 现象：状态模型定义了 `MANUAL_PASSED`、`VISUAL_FAILED`、`KNOWN_VISUAL_GAP`，矩阵聚合逻辑也会识别这些视觉状态；但当前分组操作区只有“上一张 / 运行当前样例断言 / 下一张 / 一键测试全部”，没有“人工通过 / 人工失败 / 标记已知缺口”入口。`UiTestDocumentPageController` 也没有相应 handler 写回
  `UiTestCaseResult`。
- 影响：11 张人工待确认样例即使已经通过游戏内截图或交互验证，也只能长期停留在 `MANUAL_PENDING`。这会让首页“人工确认”列表、分组汇总状态和交接记录无法反映真实完成情况，后续审查会反复重复同一人工项。
- 测试缺口：当前 JVM 测试覆盖了人工样例保持 `MANUAL_PENDING`，但没有覆盖人工结果写回，因为实现入口不存在。
- 建议：补最小人工回写能力。优先给当前样例增加“人工通过”“人工失败”两个按钮，写入 `UiTestVisualStatus.MANUAL_PASSED` / `VISUAL_FAILED`，语义维度可保持原值或按明确规则保留 `MANUAL_PENDING`；后续再考虑已知缺口入口。补测试验证按钮存在、点击后 case 与 group 状态刷新。

## 非问题确认

- `VIS-CTRL-002` 对 password 的断言要求 `value` 属性显示掩码而非明文，看起来与浏览器原生 `input.value` 不同；但当前 `DocumentTextInputControl` 的既有契约明确“真实值通过 `getText()` 返回，`value` 属性不暴露明文”，并有
  `DocumentTextInputControlTest.shouldMaskPasswordButKeepRealValue()` 覆盖。因此本轮不把它判为 bug，只记录为项目安全取舍。
- `UiTestGroupSpec` 的计划自动/人工总数为 43/16，而首页显示已接入自动/人工为 42/11。该差异来自 RemoteNet 6 张未接入缺口，当前首页文案使用已接入口径，状态页总览使用计划/已接入/缺口组合，未构成 bug。

## 后续建议

1. 先修 P1，避免批量测试改变用户浏览位置。
2. 再补 P2 的最小人工结果回写，否则后续人工确认无法沉淀到矩阵状态。
3. 下一轮审查可转向 HTML-like 文档控件与测试矩阵断言的真实语义强度，重点查自动断言是否只检查“样例自证数据”而没有覆盖底层行为。
