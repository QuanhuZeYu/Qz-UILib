# flex min-content 与运行时页面语义复查

## 审查结论

- 运行时页面在浏览器语义修复后暴露的主要显示风险，不应只归因于页面用法；库侧 row flex item 的 `min-width:auto` 仍存在语义偏差。
- `FlexLayoutHelper` 原先用元素整段固有宽度近似 auto 最小宽度；文本场景等价于 max-content，会让可换行文本的 flex item 比真实浏览器更难收缩。
- 正确方向是库侧按 CSS-like min-content 计算 auto 最小宽度，同时页面侧对确实需要等分收缩的 row flex 子项显式声明 `min-width:0`。

## 修复范围

- `TextLayoutHelper` 新增文本 min-content 测量，按 white-space、word-break、overflow-wrap、URL 断点与 CJK 断点识别最宽不可断片段。
- `DocumentLayoutEngine` 新增元素 min-content 宽度遍历，覆盖文本、inline 子元素、block 子元素、图片和嵌套 flex。
- `FlexLayoutHelper` 的 row flex item auto 最小宽度改用 min-content，而不是原先的 intrinsic/max-content 近似。
- 诊断页、运行时自检页、配置模板工具栏、数值滑块行和字体排序行对需要可收缩的 flex 子项显式设置 `min-width:0`。

## 验证

- `./gradlew.bat --no-configuration-cache test --tests "club.heiqi.uilib.ui.layout.FlexLayoutHelperBoundaryTest"`
- `./gradlew.bat --no-configuration-cache test --tests "club.heiqi.uilib.ui.layout.DocumentLayoutEngineTest" --tests "club.heiqi.uilib.ui.layout.FlexLayoutHelperBoundaryTest"`
- `./gradlew.bat --no-configuration-cache compileJava`
- `git diff --check`

## 后续注意

- 浏览器语义下，row flex 子项默认 `min-width:auto` 不等同于可任意压缩；页面作者若需要卡片、输入外壳、状态列在等分布局中继续收缩，应显式声明 `min-width:0` 或设置非 visible overflow。
- 当前 min-content 是 CSS-like 工程实现，已覆盖现有文本断点模型；不代表完整 CSS Text 所有国际化断行细节。
