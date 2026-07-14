# Picker 成员行文本溢出与误命中

## 错误现象

`LIST_MEMBERS` 当前成员的长标签越过自身布局盒，覆盖问题提示与“编辑/删除”按钮；同时点击 label、图标、badge、按钮间隙或操作区透明预留也会触发编辑。

## 触发场景

- 360/480 logical px 管理 Portal 中展示中英混排、registry 或 canonical 长文案。
- 成员处于 normal、pending、invalid 或 duplicate 状态，问题 badge 与固定操作区共同占用行宽。
- 指针落在成员行任意非按钮区域，事件 bubble 到整行 CLICK handler。

## 根本原因

flex 已正确收缩 label 的 `LayoutBox`，但 composition 未在 label 节点显式建立 clip；Scene Paint 按合同保留完整 TEXT，因此 glyph 可继续画到外层 Portal clip。成员行又额外挂载 CLICK→Edit，令本应透明的结构容器和预留空间成为隐式编辑区。原子按钮、布局与 hit-test 引擎均无缺陷。

## 修复方案

- label 保留完整原文并启用自身 clip，使 TEXT 只在 label `LayoutBox` 内绘制。
- 行顺序固定为 icon→label→issue badge→actions；actions 保持固定宽并靠右。
- row/actions 退出叶命中候选，删除整行编辑 handler，仅保留可见 Edit/Delete 按钮根动作。
- 增加 360/480px 状态布局、PaintPlan clip 配对、非零 overlay anchor/rootAbs 真实坐标命中测试，并将 StructuredList 集成测试改为语义定位 action 容器。

## 预防措施

composition 新增可收缩长文本槽时，同时检查 layout、paint clip 与 hit 三份合同；固定操作 rail 必须用状态矩阵验证坐标稳定。命中测试不得只点节点中心，应覆盖所有视觉空白与按钮外 1px，防止结构容器 bubble 重新扩大动作区。
