# 决策：scene 新栈 TextInput 本批锁定档位 A + 全声明式 caret + β 否决/γ 前瞻

## 背景

scene 新栈 Phase 4 控件层重建批 3，迁移 TextInput。前两轮 oracle（已用尽 session）曾裁决「档位 B 一步到位」——给 TextInput 做 caret 可定位 + 方向键 + 选区 + 字符级定位，并提出方案 β（caret 下沉 SceneNode 属性槽 + paint 层注入 measurer 算前缀宽画竖线）。

explorer 侦察揭示两处关键事实，推翻了该裁决前提：

1. **旧栈 `DocumentTextInputControl` 极简**：caret 恒在末尾、无 caret 索引、无选区、无方向键、无 Home/End、无 Ctrl+A、无复制粘贴、无水平滚动跟随。「档位 B」的 caret 定位/选区是旧栈**从未有过的新功能**，超出 strangler「行为等价绞杀」范畴。
2. **字符级度量能力当前不存在**：`SceneTextMeasurer` 只有整行 `measureWidth`，底层 `TextMeasureService` 也只有整行 API，无 measurePrefix/charIndexAtX。
   且 `ScenePaintEngine.generateCommands` 自包含、手里没有 measurer（度量器只在 `SceneLayoutEngine`）——方案 β「paint 层算前缀宽」根本落不了地，前两轮 oracle 未核对 paint 引擎实际拿不到 measurer。

用户偏好：「优先靠近宪章信条**范式**、接受高风险但守最终信条」。

## 候选方案

- **档位 B（功能升级）**：caret 可定位 + 方向键 + 选区 + 字符级定位。需 measurer 能力，触发 β/γ 抉择。
- **档位 A（行为等价旧栈）**：caret 恒末尾、无选区/方向键/字符级定位/IME/剪贴板/闪烁，但用全声明式方式重写。
- **方案 β**：caret 下沉 SceneNode 属性槽（caretIndex/selectionStart/selectionEnd）+ paint 层注入 measurer 算前缀宽画竖线/选区高亮。
- **方案 γ**：开放控件层前缀宽度量窄端口（SceneRuntime 薄委托 measureTextWidth）+ caret/选区用普通子节点组合。

## 最终选择

**本批锁定档位 A，用全声明式方案实现。β 永久否决，γ 留作档位 B 字符级定位的唯一回填方向。本批无偏离，仅写本决策记录。**

档位 A 全声明式核心实现：
- caret 作为 textNode 的兄弟叶节点（preferredWidth=1 背景竖线），靠 ROW 布局自然推到文本末尾右侧——**零度量、零本地 signal**（caret 位置不是状态，是 `f(value 长度)` 布局派生，比 Slider 还简单，无 draggingValue）。
- caret 高度靠自设 preferredHeight≈行高（落地铁律①：无文本叶高=0 不可见）；root gap=0（落地铁律②：否则 caret 与文本多 1px gap）。
- caret 可见性 = 聚焦态，bind `interactionState(root).focused()` 切 CARET_COLOR↔透明（PAINT 级，照搬 SceneToggle 读 `is.pressed()` 范式换 focused）。
- value 受控（R7 文本版，确立契约 R9）：`ReadableSignal<String> value` + `Consumer<String> onChange`，handler 只 onChange 上抛真实 String，绝不自缓存/自改。

## 选择原因

**核心辨析（命门）：用户偏好被前两轮 oracle 误读。** 用户要「靠近信条**范式**」，不是「功能要多」。两者正交：
- 信条/不变量约束的是「**怎么实现**」（声明式、signal 直驱、分级失效），不是「实现多少功能」。
- caret 定位/方向键/选区是 **feature（功能维度）**，不是 **paradigm（范式维度）**。把「档位 B 能力升级」当成「靠近信条」是把功能维度冒充范式维度。
- 旧栈 TextInput 的真问题不是功能简陋，而是**范式落后**——它用 `DocumentCustomRenderer` 命令式 `fillRect` 直接画 caret 竖线（installCursorRenderer），绕过声明式 Display List。

所以「靠近信条范式」的正确落点是：**功能等价旧栈，但把命令式 render 重写成声明式 caret 子节点 + signal 驱动**。升级范式，不升级功能。这同时满足 strangler「行为等价绞杀」+ 用户「不为旧栈范式妥协」。

**档位 A 全声明式零核心侵入、不违反任何信条/不变量，反而还清旧栈一处范式债**（命令式 render caret → 声明式 caret 节点），故不构成偏离。

**β 否决理由**：① paint 注入 measurer 让数据层 paint 引擎持有文本度量依赖，撞 I6 精神；② caret 位置依赖 value+caretIndex，进 fragment 后 value 一变 fragment 必重建，破坏 I7/I8 fragment 复用；
③ 把 caretIndex/selection 下沉为通用 SceneNode 属性槽，是把「文本编辑」控件级概念污染进纯数据地基（SceneNode 灵魂=纯数据+脏标记载体）。

**γ 前瞻（档位 B 启动时）**：SceneRuntime 已有成熟「持有协作者→暴露薄委托只读方法」范式（interactionState/requestFocus/cursorSignal）。加 measureTextWidth 薄委托完全对仗，且文本度量只读不写，落进 I11 受控逃生舱第①类「只读几何测量」类比扩展。
caret 位置经 `computed(measureWidth(value.substring(0, caret)))` 算出 bind 到 caret 节点偏移，核心 node/paint/layout 零侵入。**即便将来走 γ 也不算偏离 I6/I11**（I11 明确把只读几何测量列为受控逃生舱），届时仍只需更新本决策记录，不需偏离登记。

## 影响范围

- 新建 `SceneTextInput.java`（受控文本输入，确立 R9）+ `SceneInputType.java`（轻量枚举 TEXT/PASSWORD/NUMBER，strangler 隔离不 import 旧栈 DocumentInputType）。
- `package-info.java` 契约红线 8→9 条，追加 R9（受控文本输入零内部状态，R7 从布尔/连续值到 String 推广）。
- demo 接入 `SceneControlsHostWidget`（TEXT + PASSWORD 受控闭环）。
- 字符级度量能力缺口（measureWidth 整行 only）作为档位 B 的前置基建，回填方向走 γ。

## 后续注意事项

- **档位 B（caret 定位/方向键/选区）启动时走 γ**：装配层需把 measurer 同时注入 SceneRuntime（当前只有 SceneLayoutEngine 持有），SceneRuntime 加 measureTextWidth 薄委托。caret/选区用普通子节点组合。本批不执行。
- **IME/组合输入**：YAGNI 排后续批，属输入半环扩展（事件模型加 compositionStart/update/end），不属控件层，应单独立项。
- **剪贴板**：YAGNI 排后续批，属 I11 第③类宿主层桥接逃生舱，且依赖选区（档位 A 无选区时无意义），应与档位 B 选区一起做。
- **caret 闪烁**：本批 caret 聚焦恒亮。旧栈闪烁靠命令式每帧 poll 时钟（拉模式）；scene 应排到「动画时钟 signal 化」基建批，届时 bind 周期 boolean signal 到 caret opacity（COMPOSITE 级推模式，又一处范式升级），零控件改动。
- **I-beam 光标缺口**：scene 的 SceneCursor 枚举暂无 TEXT/I-beam 值，本批统一用 POINTER（enabled）/NOT_ALLOWED（disabled）。如需 I-beam 需先扩 SceneCursor 枚举，不在本批范围。
- **maxLength/inputType 受控范式**：作为不可变常量 prop 传入（R2 允许不可变常量），控件内做纯过滤（无可变状态，守 R1），保行为等价旧栈又不引入内部可变状态。
