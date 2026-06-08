# `/qzuilib test` 视觉样例断言审查与修复复核

## 审查范围

- 最新合并批次中 CSS / Layout / Paint 首轮 19 张视觉样例、自动断言与人工边界。
- 重点文件：`UiTestSampleVisualFactory`、`UiTestAssertionRunner`、`UiTestMatrixRegistry`、`UiTestSemanticChecker`、`UiTestDocumentPageControllerTest`。

## 原始发现

- `VIS-CSS-004` 子节点使用通用面板后覆盖了父级文本色，继承样例与断言目标不一致。
- `VIS-CSS-006` overflow 断言取到外层 row，且 hidden/visible 样例缺少越界子内容。
- `VIS-LAYOUT-001` 使用 flex column 搭建样例，无法验证普通块流相邻 margin collapse。
- `VIS-PAINT-005` 用普通高 `z-index` 元素冒充 top-layer，自动断言只检查 `z-index`。
- `VIS-PAINT-006` 与 `VIS-PAINT-007` 仅凭 overflow / background-image 声明自动通过，无法证明 scrollbar 几何、拖拽命中或 host image fallback。
- 目标测试此前只执行 `VIS-CSS-001` 与 `VIS-PAINT-003`，未覆盖上述后续样例断言路径。

## 修复结论

- 已修复。CSS 继承、overflow、block flow margin collapse 与 top-layer 注册现在由真实样例和对应断言覆盖。
- `VIS-PAINT-006` scrollbar 与 `VIS-PAINT-007` host image fallback 调整为人工待确认样例，仅输出机器诊断摘要，不再误报自动通过。
- PAINT 分组首轮计划边界调整为自动 3 张、人工 4 张；当前已接入 19 张样例中自动断言 14 张、人工待确认 5 张。
- 新增目标测试覆盖 `VIS-CSS-004`、`VIS-CSS-006`、`VIS-LAYOUT-001`、`VIS-PAINT-005`、`VIS-PAINT-006`、`VIS-PAINT-007`。

## 后续复核（2026-06-08）

- `VIS-PAINT-006` 已补强为自动断言：检查 `overflow:auto`、正向 scroll range 和 `scrollTo` 运行态偏移变化。
- 当前正确口径：scrollbar track/thumb 的真实几何、拖拽命中和截图观感仍保留人工观察边界，但该样例不再占用矩阵人工待确认状态。
- PAINT 分组首轮计划边界随之调整为自动 4 张、人工 3 张。

## 验证

- `./gradlew.bat --no-configuration-cache test --tests "club.heiqi.uilib.internal.devtools.pages.UiTestDocumentPageControllerTest" --rerun-tasks`
- `./gradlew.bat --no-configuration-cache compileJava`
- `git diff --check`
