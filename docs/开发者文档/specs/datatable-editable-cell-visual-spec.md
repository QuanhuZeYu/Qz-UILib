# DataTable 可编辑列视觉提示设计方案

> 类型：UI/UX 设计方案（只产方案，不含实现代码）
> 范围：`SceneDataTable` 编辑列（`Column.textInput` / `Column.select`）在 primitive 重构后的视觉提示
> 关联决策：`DECISION-20260624-scene-unstyled-primitives.md`
> 关联源码：`SceneDataTable.java`、`SceneTextInputPrimitive.java`、`SceneSelectPrimitive.java`、`ScenePalette.java`

---

## 1. 设计目标

**让玩家扫一眼就能区分「可编辑列」与「只读列」：只读 cell 与斑马纹背景平齐，可编辑 cell 用「嵌入式输入框槽位」（内凹底色 + 1px 边框 + 文字光标/下拉箭头）做出可交互暗示；hover/聚焦/编辑三态用边框升亮 + 聚焦蓝强调，全程不使用渐变与阴影，符合 MC 像素风。**

核心手法：把编辑列 cell 内的 primitive 渲染成一个**比斑马纹底色更暗的"输入槽"**（well/inset），与只读 cell 的"纯文本平铺"形成明显的明暗与轮廓对比。

---

## 2. 色值表

### 2.1 复用现有常量（已验证来源）

| 常量 | 色值 | 来源 | 用途 |
|---|---|---|---|
| `ROW_BG_EVEN` | `0xFF1E293B` | ScenePalette.java:9 | 偶数行斑马纹底 |
| `ROW_BG_ODD` | `0xFF243B53` | ScenePalette.java:11 | 奇数行斑马纹底 |
| `HEADER_BG` | `0xFF334155` | SceneDataTable.java:39 | 表头底 |
| `VIEWPORT_BG` | `0xFF0F172A` | SceneDataTable.java:41 | 视口底（最深） |
| `TEXT_COLOR` | `0xFFEAF1FF` | SceneDataTable.java:43 | 单元格文本 |
| `CELL_PADDING` | `4`px | SceneDataTable.java:35 | cell 内边距 |

### 2.2 新增常量（建议加入 `SceneDataTable` 私有常量区，集中在表格内，不污染 `ScenePalette`）

| 建议常量名 | 色值 | 用途 | 说明 |
|---|---|---|---|
| `EDIT_SLOT_BG` | `0xFF0F1A2E` | 可编辑 cell 内输入槽默认底色 | 比 `VIEWPORT_BG`(`0xFF0F172A`) 略偏蓝、比两种斑马纹都暗，制造"内凹"感；两种行都用同一槽底，槽与斑马纹的对比即"可编辑"信号 |
| `EDIT_SLOT_BG_HOVER` | `0xFF16243D` | hover 态槽底 | 比默认槽略亮一档，给"可点"反馈 |
| `EDIT_BORDER` | `0xFF3E5575` | 可编辑 cell 默认边框 | 中蓝灰，比 `HEADER_BG` 略亮，确保在两种斑马纹上都能看清轮廓 |
| `EDIT_BORDER_HOVER` | `0xFF5A7299` | hover 态边框 | 比默认边框亮一档 |
| `EDIT_BORDER_FOCUS` | `0xFF60A5FA` | 聚焦/编辑态边框（强调蓝） | **见 §2.3 拍板项**，本方案默认采用任务给定的 `0xFF60A5FA` |
| `EDIT_CARET` | `0xFF60A5FA` | TextInput caret 竖线色 | 与聚焦蓝同色，强化"正在编辑" |
| `EDIT_CARET_TRANSPARENT` | `0x00000000` | caret 不可见态 | 纯 PAINT 切换不重排，照搬 SceneTextInput.java:65 模式 |
| `EDIT_PLACEHOLDER` | `0xFF64748B` | 空值占位文本色 | 复用 SceneTextInput `TEXT_PLACEHOLDER` 同值，灰调区别真实文本 |
| `EDIT_ARROW` | `0xFFAEC4E8` | Select 下拉箭头默认色 | 比正文文本略暗的浅蓝，箭头是"这是下拉"的关键信号，不能用 disabled 灰 |
| `EDIT_ARROW_FOCUS` | `0xFF60A5FA` | Select 展开态箭头色 | 展开时箭头升为聚焦蓝 |

### 2.3 ⚠️ 待主 Agent 拍板：聚焦蓝取值不一致

任务上下文给定聚焦蓝为 `0xFF60A5FA`，但现网代码实际用的是 `0xFF4A90D9`：
- `SceneTextInput.BORDER_FOCUSED = 0xFF4A90D9`（SceneTextInput.java:51）
- `SceneSelect.ITEM_BG_SELECTED = 0xFF4A90D9`（SceneSelect.java:68）

**候选：**
- **候选 A（推荐）**：DataTable 编辑列统一用 `0xFF60A5FA`。理由：表格 cell 底色比独立控件场景更暗（斑马纹/视口都是深蓝黑），`0xFF60A5FA` 更亮、对比更强，可编辑提示更醒目；这正是本次"提示偏弱"反馈要解决的方向。代价：与 `SceneTextInput`/`SceneSelect` 独立控件的聚焦蓝不统一。
- **候选 B**：跟随现网 `0xFF4A90D9` 保持全局一致。代价：在深色斑马纹上对比偏弱，与本次改进诉求相悖。

**推荐 A**，但"是否接受双聚焦蓝并存"需主 Agent 拍板。若选 B，把上表所有 `0xFF60A5FA` 替换为 `0xFF4A90D9` 即可，方案其余不变。

---

## 3. 只读列 vs 可编辑列对比

| 维度 | 只读列 `Column.text` | 可编辑列 `textInput` / `select` |
|---|---|---|
| cell 底色 | 斑马纹（`rowBg`），文本直接平铺其上 | 斑马纹底 **+ 内层输入槽**（`EDIT_SLOT_BG`，比斑马纹暗） |
| 边框 | 无 | 输入槽 1px 边框（`EDIT_BORDER`） |
| 轮廓感 | 与整行融为一体，无独立矩形 | 槽形成独立内凹矩形，明确"这里能输入" |
| 文本色 | `TEXT_COLOR` 平铺 | 槽内文本 `TEXT_COLOR`；空值显示 placeholder 灰 |
| cursor | 默认 | TextInput=TEXT(I 形)，Select=POINTER(手型) |
| 交互反馈 | 无 | hover 槽底/边框升亮；聚焦/编辑边框变聚焦蓝 |
| 附加元素 | 无 | TextInput 有 caret；Select 有下拉箭头 ▼/▲ |

**关键区分点**：只读列"无框平铺文本"，可编辑列"有框内凹槽 + 交互元素"。即使静止不动、不 hover，玩家也能凭"凹陷输入框"轮廓识别可编辑列。

---

## 4. 状态矩阵

行=状态，列=控件类型。描述的是 cell 内 primitive 渲染出的视觉。

### 4.1 TextInput 列

| 状态 | 槽底色 | 边框色 | caret | 文本 |
|---|---|---|---|---|
| 默认（未 hover/未聚焦） | `EDIT_SLOT_BG` `0xFF0F1A2E` | `EDIT_BORDER` `0xFF3E5575` | 透明（不可见） | 真实值 `TEXT_COLOR`；空值显示 placeholder `0xFF64748B` |
| hover（鼠标悬停未聚焦） | `EDIT_SLOT_BG_HOVER` `0xFF16243D` | `EDIT_BORDER_HOVER` `0xFF5A7299` | 透明 | 同默认 |
| 聚焦/编辑（已聚焦，可输入） | `EDIT_SLOT_BG_HOVER` `0xFF16243D` | `EDIT_BORDER_FOCUS` `0xFF60A5FA` | `EDIT_CARET` `0xFF60A5FA` 竖线（按 `caretVisible`） | 真实值 `TEXT_COLOR`；聚焦时空值不显示 placeholder（primitive 既有行为，见 SceneTextInputPrimitive.java:294-296） |

> 注：TextInput 的"聚焦"即"编辑态"，无独立两态。caret 仅在 `caretVisible`（enabled && focused）为真时上色，见 SceneTextInputPrimitive.java:127-128。

### 4.2 Select 列

| 状态 | 槽底色 | 边框色 | 箭头 | 文本 |
|---|---|---|---|---|
| 默认 | `EDIT_SLOT_BG` `0xFF0F1A2E` | `EDIT_BORDER` `0xFF3E5575` | ▼ `EDIT_ARROW` `0xFFAEC4E8` | 选中项文本 `TEXT_COLOR`；无选中显示 placeholder 灰 |
| hover | `EDIT_SLOT_BG_HOVER` `0xFF16243D` | `EDIT_BORDER_HOVER` `0xFF5A7299` | ▼ `EDIT_ARROW` | 同默认 |
| 聚焦（trigger 获焦未展开） | `EDIT_SLOT_BG_HOVER` `0xFF16243D` | `EDIT_BORDER_FOCUS` `0xFF60A5FA` | ▼ `EDIT_ARROW` | 同默认 |
| 编辑（listbox 展开） | `EDIT_SLOT_BG_HOVER` `0xFF16243D` | `EDIT_BORDER_FOCUS` `0xFF60A5FA` | ▲ `EDIT_ARROW_FOCUS` `0xFF60A5FA` | 同默认（listbox 浮层另见 §7） |

> 箭头字符复用 primitive 既有绑定：展开 `▲` / 收起 `▼`，见 SceneSelectPrimitive.java:178。本方案只覆盖箭头颜色与 trigger chrome。

---

## 5. cell 结构建议

### 5.1 整体结构（不改 DataTable 既有 cell 外壳）

DataTable 外层 cell（SceneDataTable.java:511-533）维持现状：斑马纹底 + `CELL_PADDING=4` + clipChildren。**视觉槽做在 primitive root 自身**（即 renderer 返回的 child 节点），形成"斑马纹 cell → 4px padding → 输入槽"的内凹层次。

```
cell (斑马纹底, padding=4, clipChildren)        ← 既有，不动
  └─ primitive root (输入槽: 槽底 + 1px 边框 + 圆角 + 内 padding)   ← 本方案在此挂 chrome
       └─ ...（prefixText/caret/suffixText 或 label/arrow）
```

### 5.2 输入槽 chrome（TextInput 与 Select 的 root/trigger 共用规格）

| 属性 | 值 | 绑定方式 | 备注 |
|---|---|---|---|
| backgroundColor | 槽底色（按状态） | PAINT 绑定 | 见 §4 状态矩阵 |
| borderWidth | `1`px | 静态 set | 槽轮廓 |
| borderColor | 边框色（按状态） | PAINT 绑定 | 见 §4 |
| cornerRadius | `2`px | 静态 set | 比独立控件的 4px 小一档，cell 空间窄、像素风宜小圆角；也可设 0 走纯方角，见 §5.4 拍板项 |
| padding | 横向 `4`px / 纵向 `0` | 静态 set | cell 已有 4px 外 padding，槽内再留 4px 横向让文字不贴边；纵向靠 `contentHeight` + CrossAxisAlign.CENTER 垂直居中 |
| cursor | TextInput=TEXT / Select=POINTER | PAINT 绑定 enabled | 照搬 SceneTextInput.java:157-158、SceneSelect.java:162 |
| preferredHeight | `ctx.contentHeight()` | 静态 set | 既有逻辑 SceneDataTable.java:261/283，保持 |

### 5.3 与既有 cell padding 的协调

- cell 外层 `CELL_PADDING=4`（SceneDataTable.java:518）：槽四周留 4px 斑马纹"边距"，使内凹槽不顶满 cell，凹陷感更强。
- 槽内再加横向 4px padding：避免文字/caret 贴槽边框。
- 行高 `DEFAULT_ROW_HEIGHT=28`，`contentHeight = rowHeight - 2*CELL_PADDING = 20`px（SceneDataTable.java:527），槽高 20px，足够容纳单行文本 + 1px 边框，不溢出。

### 5.4 ⚠️ 待主 Agent 拍板：圆角取值

- **候选 A（推荐）**：`cornerRadius = 2`。窄 cell + 小圆角，既有输入框暗示又不过分占像素。
- **候选 B**：`cornerRadius = 0`（纯方角）。最贴 MC 原版方块风，但与独立控件 4px 圆角风格略有割裂。
- **候选 C**：`cornerRadius = 4`，与 `SceneTextInput`/`SceneSelect` 独立控件一致。代价：20px 高的窄槽上 4px 圆角偏圆，挤占内容。

**推荐 A（2px）**，但圆角是观感偏好项，建议主 Agent 真机扫一眼定。三选一只改一个常量。

---

## 6. caret 色

**`EDIT_CARET = 0xFF60A5FA`（聚焦蓝），不可见态 `0x00000000`。**

- primitive 的 caret 节点默认无色，**必须**由 cell chrome 按 `result.caretVisible()` 上色，否则聚焦时 caret 不可见（契约见 SceneTextInputPrimitive.java:28-30）。
- 绑定方式：`rt.bind(Invalidation.PAINT, Computed.create(() -> caretVisible.get() ? EDIT_CARET : EDIT_CARET_TRANSPARENT), caret::setBackgroundColor)`，照搬 SceneTextInput.java:154-156。
- 选聚焦蓝而非近白（SceneTextInput 用 `0xFFE2E8F0`）：表格场景要强化"正在编辑哪个 cell"，蓝色 caret + 蓝色边框形成一致的聚焦语言。

---

## 7. ListboxChrome 方案（Select 下拉浮层）

通过 `SceneSelectPrimitive.ListboxChrome` 在 overlay 构建栈内同步注入（接口见 SceneSelectPrimitive.java:99-113）。结构照搬 `SceneSelect.SceneSelectChrome`（SceneSelect.java:230-262）。

### 7.1 listbox 容器

| 属性 | 值 | 绑定 |
|---|---|---|
| backgroundColor | `0xFF1E293B`（复用 SceneSelect `LISTBOX_BG`） | 静态 set in `decorateListbox` |
| cornerRadius | `4` | 静态 set |
| borderWidth | `1` | 静态 set | 给浮层一个轮廓，浮在表格上更清晰
| borderColor | `EDIT_BORDER` `0xFF3E5575` | 静态 set |

> listbox 浮在视口之上，底色 `0xFF1E293B` 比 `VIEWPORT_BG 0xFF0F172A` 亮，自然浮起；加 1px 边框进一步与表格分离。

### 7.2 item 四态配色

每个 item 通过 `decorateItem(ItemHandle)` 装饰，背景按 selected > highlighted > hovered > 默认 优先级解析（照搬 SceneSelect.java:217-228 的 `resolveItemBackground`）。

| 态 | 背景色 | 来源/说明 |
|---|---|---|
| selected（当前选中项） | `0xFF60A5FA` | 选中蓝，**见 §2.3 拍板**；若选候选 B 则 `0xFF4A90D9`（SceneSelect `ITEM_BG_SELECTED`） |
| highlighted（键盘高亮） | `0xFF3B4E68` | 复用 SceneSelect `ITEM_BG_HIGHLIGHTED` |
| hovered（鼠标悬停） | `0xFF334155` | 复用 SceneSelect `ITEM_BG_HOVER` |
| 默认 | `0x00000000`（透明，透出 listbox 底） | 复用 SceneSelect `ITEM_BG` |

| item 其他属性 | 值 |
|---|---|
| padding | `6`（复用 SceneSelect `ITEM_PADDING`） |
| cursor | POINTER |
| item 文本色 | `TEXT_COLOR` `0xFFEAF1FF`（与表格文本统一；selected 态蓝底上白字对比足够） |

> selected 优先级最高：选中项即使被 hover，也保持选中蓝，避免"hover 盖掉选中"的歧义。

---

## 8. 与现有斑马纹的协调方式

1. **不改斑马纹本身**：cell 外层仍 `setBackgroundColor(rowBg)`，奇偶行交替不变，行级可读性不受影响。
2. **槽底统一不随行变**：`EDIT_SLOT_BG` 对奇偶行用同一深色。这样做的好处——可编辑列形成一条**视觉上连续的纵向"输入列带"**，比"槽底也跟着斑马纹变"更能强化"这一列可编辑"的列级语义。
3. **明度分层自上而下加深**：表头 `0xFF334155` > 奇数行 `0xFF243B53` > 偶数行 `0xFF1E293B` > 输入槽 `0xFF0F1A2E` ≈ 视口 `0xFF0F172A`。槽是除视口外最暗的层，"凹陷"语义清晰，且不会与任一行底撞色。
4. **解决"提示偏弱"根因**：现状即使不 flat，`SceneTextInput.BG_ENABLED=0xFF1E293B` 与偶数行 `ROW_BG_EVEN=0xFF1E293B` **完全同色**（输入框在偶数行上隐形）。本方案槽底改用更暗的 `0xFF0F1A2E`，在两种斑马纹上都有可见对比，彻底消除"输入框融进背景"问题。
5. **边框是第二道保险**：即使某些玩家对底色明暗不敏感，1px `EDIT_BORDER` 轮廓也独立给出"这是输入框"信号。

---

## 9. 实现提示（给 fixer）

### 9.1 改动定位

- 主改 `SceneDataTable.Column.textInput`（SceneDataTable.java:250-264）与 `Column.select`（SceneDataTable.java:274-286）两个 renderer。
- 迁移目标：renderer 内**直接调用 primitive**（`SceneTextInputPrimitive.create` / `SceneSelectPrimitive.create`），不再走 `SceneTextInput.create`/`SceneSelect.create` 的 `flat=true`，由 cell 自己挂 chrome。
- 新增常量集中放在 `SceneDataTable` 私有常量区（§2.2），不动 `ScenePalette`。

### 9.2 PAINT 绑定 vs 静态 set 分工

**走 `rt.bind(Invalidation.PAINT, ...)`（随状态变）：**
- 槽 backgroundColor（依赖 hover/focus → 用 `rt.interactionState(root)` 取 `hovered()`/`focused()`）
- 槽 borderColor（依赖 hover/focus）
- caret backgroundColor（依赖 `result.caretVisible()`）—— **必做，否则 caret 不可见**
- Select 箭头 textColor（依赖 `result.expanded()`）
- cursor（依赖 enabled）

**静态 `set`（构建期一次性，不随状态变）：**
- borderWidth=1、cornerRadius=2、padding
- preferredHeight = `ctx.contentHeight()`
- listbox 的 bg/cornerRadius/border（在 `decorateListbox` 内静态 set）
- item 的 padding/cursor/文本色（在 `decorateItem` 内静态 set；唯背景走 PAINT）

### 9.3 状态信号来源（已验证可用）

- TextInput：`SceneTextInputPrimitive.Result` 暴露 `caretVisible()`、`isPlaceholder()`；root 的 hover/focus 用 `rt.interactionState(result.root())` 取（primitive 内已 `rt.focusable(root)`，SceneTextInputPrimitive.java:141）。
- Select：`SceneSelectPrimitive.Result` 暴露 `expanded()`；trigger hover/focus 用 `rt.interactionState(result.trigger())`（primitive 内已 `rt.focusable(trigger)`，SceneSelectPrimitive.java:195）。
- listbox item：`ItemHandle` 已暴露 `selected()`/`highlighted()`/`interaction()`（SceneSelectPrimitive.java:126-133），直接照 SceneSelect.java:251-261 的写法。

### 9.4 边框色三态解析建议（伪逻辑，照 SceneTextInput.java:205-216 模式写）

```
resolveEditBorder(enabled, focusedOrExpanded, hovered):
    if !enabled        -> EDIT_BORDER            // 表格场景编辑列恒 enabled，此分支保险用
    if focusedOrExpanded -> EDIT_BORDER_FOCUS     // 0xFF60A5FA
    if hovered         -> EDIT_BORDER_HOVER
    else               -> EDIT_BORDER
槽底同理：focus/hover -> EDIT_SLOT_BG_HOVER，else -> EDIT_SLOT_BG
```
- TextInput 的 "focusedOrExpanded" = `result.caretVisible()`（已含 enabled && focused）。
- Select 的 "focusedOrExpanded" = `expanded || trigger.focused`，二者任一为真即聚焦蓝。

### 9.5 placeholder 接线（桩说明）

- 本方案 placeholder 文本本轮传**空串**（与现状 SceneDataTable.java:256 一致），仅指定空值时的**颜色**为 `EDIT_PLACEHOLDER 0xFF64748B`。
- 若后续要给编辑列加占位提示文案（如 "输入..."、"请选择"），下一轮在 `Column.textInput(header, width)` 增加 placeholder 入参，透传到 primitive `Props.placeholder`（SceneTextInputPrimitive.java:61）。当前为占位，**下一轮换成业务列定义传入的占位文案**。
- Select 无原生 placeholder：当 `selectedIndex` 越界（indexOf 返回 -1，SceneDataTable.java:278），primitive `selectedText` 返回空串（SceneSelectPrimitive.java:317-326），label 显示空。如需"未选择"提示，下一轮在 cell label 层叠加占位文本绑定，本轮不接。

### 9.6 验证要点（交 reviewer / 真机）

- 偶数行（`0xFF1E293B`）上输入槽必须可见（这是本次反馈的核心 bug，重点核对）。
- caret 聚焦时可见、失焦消失。
- Select 展开时箭头变 ▲ 且变蓝，listbox 浮层底色比视口亮、有边框。
- 只读列与编辑列并排时，编辑列"有框内凹"一眼可辨。
- hover 反馈在两种斑马纹行上都成立。

---

## 10. 本方案待拍板项汇总

| 编号 | 拍板点 | 推荐 | 位置 |
|---|---|---|---|
| D1 | 聚焦蓝 `0xFF60A5FA`(新) vs `0xFF4A90D9`(现网一致) | 候选 A：`0xFF60A5FA` | §2.3 |
| D2 | 输入槽圆角 2 / 0 / 4 | 候选 A：`2`px | §5.4 |

其余色值与结构方案为确定项，fixer 可直接照 §2~§9 实现。
