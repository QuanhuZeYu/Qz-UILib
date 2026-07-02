# 决策：scene 必须建立无样式 primitive 基础层

## 背景

SceneDataTable 真机验证通过，但用户反馈可编辑视觉提示偏弱，玩家可能误判为只读组件。
此前用 `flat` 变体让 `SceneTextInput` / `SceneSelect` 去掉背景、边框、圆角和 padding，
解决了 DataTable 中“独立控件堆在表格里”的短期问题。

用户确认：作为 UI 库，**无样式基础组件库是必须能力**。因此 primitive 不再只是
“等第二个消费者再抽象”的可选优化，而是 scene 控件层的基础架构方向。

## 候选方案

### 方案 A：继续依赖 `flat` 变体

- 优点：短期最小改动，现有 DataTable 已可用。
- 缺点：行为与 chrome 仍耦合在 `SceneTextInput.create` / `SceneSelect.create` 内，
  后续容易继续堆 `flat` / `compact` / `borderless` 等 variant。

### 方案 B：只做私有 behavior/chrome 分段

- 优点：不改 public API，能提升内部可读性。
- 缺点：没有给 UI 库使用者提供真正的无样式基础控件，无法满足产品定位。

### 方案 C：建立 public unstyled primitive + styled wrapper

- `SceneTextInputPrimitive` / `SceneSelectPrimitive` 只提供行为、状态、输入、overlay、anchor。
- `SceneTextInput` / `SceneSelect` 保持为默认样式 wrapper，继续提供现有外观与兼容 API。
- 高级控件（DataTable、后续 KeyValueMap/ObjectField 等）可直接消费 primitive。

## 最终选择

选择 **方案 C：建立 public unstyled primitive + styled wrapper**。

`flat` 保留为短期兼容补丁，但不作为长期样式体系。primitive 稳定后，再评估 `flat` 是否
保留兼容、标记过渡，或内部收口为 wrapper 预设。

## 选择原因

- UI 库需要可组合的无样式基础组件，供高级控件复用行为而不继承默认 chrome。
- 现有 TextInput/Select 将行为逻辑与背景、边框、圆角、padding、hover/open 色耦合，
  导致 DataTable 这类高级控件必须靠 `flat` 绕过外观。
- primitive 层能避免 variant 膨胀，让行为能力与视觉包装独立演进。
- 保留 styled wrapper 可兼容现有调用方，避免破坏 SceneControls/Form/ObjectField 等页面。

## 影响范围

- 第一批影响：`SceneTextInput`、`SceneSelect`、`SceneDataTable` 及对应测试。
- 后续可评估：`SceneKeyValueMap`、`SceneObjectField`、`SceneSimpleList` 是否迁移到 primitive。
- 不引入完整 theme/token 系统；本决策只建立无样式行为层，不解决全局主题切换。

## 任务表

1. **TextInput primitive**：抽 `SceneTextInputPrimitive` 或等价无样式行为核心；
   `SceneTextInput` 改为默认 chrome wrapper，现有 Props 兼容。
2. **Select primitive**：抽 `SceneSelectPrimitive`，保留 portalAnchored、AnchorProvider、
   键盘导航、滚动、dismiss 行为；`SceneSelect` 改为默认 chrome wrapper。
3. **DataTable 迁移**：`Column.textInput` / `Column.select` 使用 primitive，cell 自己决定样式。
4. **flat 回收**：primitive 消费方稳定后，评估 `flat` 字段的兼容策略。
5. **二批迁移评估**：根据真机设计结果，决定 KeyValueMap/ObjectField/SimpleList 是否迁 primitive。

## 不变量约束

- I1：primitive handler 仍只能写 signal 或调用受控 onChange/onSelect，不直接改宿主 UI 状态。
- I4：chrome wrapper 的颜色/边框 effect 必须打 PAINT；文本变化仍走 LAYOUT。
- I7：primitive 不应新增多余 wrapper 层导致布局/测试结构大范围变化；必要时逐步迁移测试。
- I10/I11：Select primitive 继续使用现有 overlay/anchor/dismiss 逃生舱，不引入新输入通道。

## 后续注意事项

- 不做大爆炸重构；每个 primitive 单独 commit、单独测试、单独 review。
- styled wrapper 的现有 public API 必须兼容，除非用户单独拍板破坏性变更。
- DataTable 真机已通过，但可编辑提示偏弱；primitive 迁移后仍需真机复验视觉可发现性。
