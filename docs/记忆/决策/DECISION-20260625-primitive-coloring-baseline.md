# DECISION-20260625 primitive 上色基调 + TextArea caret/文本色范式对齐

## 状态

**已完成**（commit `b52f77a3`，合回 4.0）。TextArea caret 上色对齐 TextInput 范式，
文本三态色补齐，SceneTextAreaTest 34 用例全绿。

## 背景

TextArea 的 caret 上色采用"wrapper 反向注入可写 caretColor signal 到 primitive"模式，
与 TextInput 的"primitive 暴露只读 caretVisible、wrapper 自己 bind 上色"模式相反。
TextArea 文本色完全无法由 wrapper 控制（文本节点分散在 forEach 动态行内）。
两套范式不对称，直接消费 primitive 的作者必踩。

## oracle 裁决

### 基调：primitive 暴露只读状态 + wrapper 单向供 token，禁止可写颜色 signal 反灌

1. primitive 暴露的交互/派生状态一律只读（ReadableSignal）：caretVisible、isPlaceholder 等
2. 样式颜色不在 primitive 内硬编码，但 primitive 可以接收 wrapper 传入的颜色 token
   （作为 Props 字段或只读 signal）用于给自己管理的动态子节点上色
3. 禁止 primitive 暴露可写 Signal<color> 供 wrapper set——这是反向数据流，
   wrapper 成了 primitive 内部节点的颜色驱动源，职责倒置

### 与"primitive 无样式"原则的调和

原则本意是"primitive 不决定长什么样、不硬编码 chrome"，不是"primitive 不准碰任何颜色字节"。
primitive 给自己动态生成的行 caret/文本上色，用的是 wrapper 传入的 token，
决定权仍在 wrapper。与 TextInput "wrapper 直接 bind 静态节点"在决定权归属上一致，
只是 bind 的执行位置因动态行而下沉。

## 选型对比

| 维度 | A'（选） | B（TextInput 改注入） | C（新抽象） |
|---|---|---|---|
| caret 颜色由 primitive 自解析 | 是 | — | 是 |
| 与"primitive 无样式"张力 | caret 色是行为必需非装饰 | 引入可写颜色 signal 张力最大 | 看抽象设计 |
| 改造量 | 小（只动 TextArea） | 中（动 TextInput+DataTable+TextArea） | 大（新接口+两控件迁移） |
| YAGNI | 通过 | 通过 | 违反（只 2 个消费控件） |

## 实施

### TextArea primitive
- Props 新增 4 颜色 token：caretVisibleColor / textNormalColor / textPlaceholderColor / textDisabledColor
- Result 删除可写 Signal<Integer> caretColor
- buildRow caret 上色改读 inRow + caretVisible + props.caretVisibleColor（单层 signal 链）
- 文本节点新增 textColor bind（按 isPlaceholder/enabled 解析三态色）

### TextArea wrapper
- Props 构造传入 4 token（BORDER_FOCUS / TEXT_PRIMARY / TEXT_SECONDARY / TEXT_DISABLED）
- 删除 caretColor 反向注入
- 删除"无法控制文本色"注释（能力补齐）

### TextInput / DataTable：零改动

## 风险

- 颜色 token 裸值（static final int），主题切换不响应——scene 栈现状，TextInput 同样，非本次引入
- TextArea primitive 零第三方消费者，改 Result 形状安全

## 参考

- oracle 裁决全文：会话记录（task ses_100c008e...）
- 关联：DECISION-20260624-scene-unstyled-primitives（primitive+wrapper 范式）
