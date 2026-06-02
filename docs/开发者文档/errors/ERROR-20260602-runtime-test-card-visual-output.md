# 运行时测试卡片视觉输出失真

## 错误现象

- `CSS-002` specificity 用例页面显示四个样例颜色均为默认文本色，自动测试失败，无法验证 type/class/id/source order 优先级。
- `PAINT-004` / `PAINT-005` 等运行时结果把 `UiStyleLength@...` 这类 Java 值对象地址直接展示到页面，人工验收文本不可读。
- `PAINT-003` opacity stacking context 样例缺少组内高 z-index 子元素，人工观察目标不够明确。

## 触发场景

- 在 `/qzuilib test` 进入 CSS 或 Paint 二级页。
- 点击 `CSS-002` / `PAINT-003` / `PAINT-004` / `PAINT-005` 的 `执行自动测试`。

## 根本原因

- `CSS-002` 复用的通用演示面板写入了 inline `textColor`，导致样式表规则永远被 inline 声明覆盖，测试样例本身破坏了被测 specificity 语义。
- 运行时摘要直接拼接 `UiTransform` / `UiStyleLength` 值对象，暴露默认对象字符串，而不是面向人工验收的短文本。
- opacity stacking context 样例只放置组和外部兄弟，缺少能证明“组内高 z-index 不越过外部兄弟”的内部子元素。

## 修复方案

- 为 `CSS-002` 新增专用样例构建逻辑，不写 inline 文本色；分别用标签、class、id 和同 specificity 后声明规则验证颜色优先级。
- 增加 transform 和 length 摘要格式化，输出 `translate/scale/rotate`、`px/%/auto/calc` 等可读文本。
- 为 `PAINT-003` 增加组内 `inner z=99` 子元素，让人工观察目标与预期一致。

## 预防措施

- 运行时卡片的演示节点不能用 inline 样式覆盖正在测试的 CSS 级联属性。
- 页面展示给人工验收的结果文本必须使用显式格式化，不直接拼接值对象。
- 视觉语义用例要把预期里提到的层级关系直接画在样例中，并用测试断言覆盖关键文本。
