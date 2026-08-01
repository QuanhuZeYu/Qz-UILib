# 决策：fontSort / characterFontRules 配置内容深化

> **历史快照**：本文保留 `2026-07-06` 的初版拍板、实施路线和提交演进。文中的“档 A”、
> “硬约束 §5”、fontSort 走 `SimpleListFieldRenderer`、`Result.focused` 与 `suppressed` 均指当时版本，
> 不作为当前源码契约。当前 renderer、拖拽、焦点和 action 语义见
> [`fontsort-drag-signal-2026-07.md`](fontsort-drag-signal-2026-07.md) 与
> [`scene技术债.md`](../../诊断层/scene技术债.md)。P6 的完成范围是 autocomplete primitive 与
> `characterFontRules.fontName` 接入；fontSort 当前为固定 discovered 行，不提供字体名编辑或补全。

## 背景

迁移工程（`config-migration-modern.md`）闭合后，fontSort（字体排序）与 characterFontRules（字符字体规则）虽已接入新栈 `FieldType.SIMPLE_LIST`，但编辑体验粗糙：两字段共用通用 `SimpleListFieldRenderer` 裸 String 编辑，helper 仅 4 字（"字库排序"/"字符字体规则"），无拖拽排序、无字体名补全、无语法校验、characterFontRules 的 `selector=fontName` 语法完全不暴露给用户。

本会话用户拍板深化这两字段的配置内容，提升编辑体验与可用性。

## 用户拍板（3 次问询）

### 拍板 1：深化维度（6 项多选）

用户从 8 个候选维度中选 6 项：
1. helper 文案优化
2. selector 语法扩展 + 字符选择辅助
3. characterFontRules 结构化编辑器
4. fontSort 拖拽排序（已登记技术债）
5. fontSort 字体名自动补全
6. characterFontRules 字段拆分（YAML 结构化）

未选：语法校验错误透出 UI（后并入 P4 结构化编辑器）、DefaultFontOrderHints 配置化。

### 拍板 2：字段拆分路线（最高优先，决定工程量级）

- **选项 A（UI 结构化，推荐，已选）**：三栏编辑器在 UI 呈现，YAML 底层仍 `List<String>`（如 `["a=Arial"]`），复用现成 `FontCharacterRule.toConfigValue/parse` 双向翻译，零兼容破坏。
- 选项 B（YAML 物理结构化，否决）：破坏向后兼容 + 碰迁移工程 P0 核心 `Authority.extractTyped`，工程量升一个数量级。

**选择原因**：①`FontCharacterRule.toConfigValue(enabled,selector,fontName)` + `parse` 已是现成双向翻译对；②schema 层要动 YAML 格式违反"YAML 向后兼容"硬约束；③用户编辑体验两路线完全一致，schema 层纯负收益。此时维度 3（结构化编辑器）= 维度 6（字段拆分）合并为同一件事。

### 拍板 3：拖拽视觉档位（硬约束边界）

- **档 A（越界跳变，推荐，已选）**：拖拽时行直接跳到新位置，守硬约束§5「拖拽瞬态 signal 只写不读」（拖拽态存 handler 局部闭包变量不 signal 化）。
- 档 B（浮起跟手，否决）：违硬约束§5，需走偏离登记。

### 拍板 4（P4）：行树构建位置

- **选项 B（适配层自建行树，推荐，已选）**：CharacterRuleFieldRenderer 内自建行树（rt.forEach + 三栏节点），复用 SceneCheckbox/SceneTextInput。业务耦合（FontCharacterRule）留 config.ui.field 适配层。
- 选项 A（复用 SceneSimpleList，否决）：ListItem 单值 + buildRow 硬编码，改它波及已闭合 fontSort。
- 选项 C（新建 SceneCharacterRuleList 控件，否决）：scene 控件层引入 font 业务依赖，破坏业务中立性铁律。

**选择原因**：当时 SceneSimpleList 约 943 行且零 config/font 依赖，是 scene 控件层业务中立性铁律的体现。选项 B 把业务耦合留在适配层（ConfigValueBridge 同包邻域已在做 font 业务翻译），不污染通用控件层。

## 8 阶段路线（初版，oracle 出，用户确认范围）

| 阶段 | 内容 | 风险 | 状态 |
|---|---|---|---|
| **P0** | helper 文案优化（补格式说明+示例） | 极低 | ✅ |
| **P1** | FieldRendererRegistry path 分发基建 | 低 | ✅ |
| **P2** | SceneSimpleList draggable 拖拽基建（档A） | 中（scene 核心） | ✅ |
| **P3** | fontSort 接入拖拽（ConfigUI customizer hook） | 低 | ✅ |
| **P4** | CharacterRuleFieldRenderer 三栏编辑器 | 中（新写 renderer） | ✅ |
| **P5** | selector 逗号多点语法扩展（parse 展开形态A） | 中（改 parser 契约） | ✅ |
| **P6** | 字体名自动补全（SceneAutocompletePrimitive） | 中（新建控件+portal） | ✅ |
| **P7** | 字符选择辅助（字符网格 picker） | 中（新建控件） | ⏳（用户暂缓） |

## 关键设计取舍（初版，oracle 裁决）

### 字段拆分：渲染层路线

YAML 仍 `List<String>`，复用 `FontCharacterRule.parse/toConfigValue` 双向翻译。renderer 读时 parse 拆三栏，写时 toConfigValue 拼回。Authority/Persistence/Bridge/FontConfig 全不动，零兼容层。

### 差异化 renderer：Registry path 分发

`FieldRendererRegistry` 加 `pathOverrides`（HashMap）+ `registerPath(String, FieldRenderer)`，`resolve` 先查 path 未命中回落 type。fontSort 走 `SimpleListFieldRenderer(true)`（draggable），characterFontRules 走 `CharacterRuleFieldRenderer`（三栏）。path 格式 "section.field"（如 `fontSystem.fontSort`）。

### 拖拽：档 A 越界跳变 + 四段式 + I5 keyed diff 依赖

SceneSimpleList 加 `draggable` Props（默认 false 向后兼容）。拖拽态（dragId/dragging）存 handler 局部闭包 final 容器，零实例字段、零 signal（守 R1 + 硬约束§5）。复刻 SceneScrollbar 四段式（POINTER_DOWN 记 dragId + requestPointerCapture / MOVE 算 pointerToRowIndex 越界 moveItem / UP/CANCEL 清理）。moveItem 移动同一 ListItem 引用（id 不变），依赖 I5 keyed diff 复用节点。

### CharacterRuleFieldRenderer：D2 桥 + normalize 防抖动 + I5 keyFn

- **CharacterRuleItem**：id（AtomicLong）+ enabled + selector + fontName + errorMessage（构造内 parse 派生）+ copyWith 保持 id（I5 锚）
- **D2 桥 reset 守卫**：`rt.bind(draftSig, applier)` 内 `normalize(incoming)`（parse→toRaw 规范化）与 `projectValues(localItems.get())` 同源比对，防 round-trip 抖动（`" a = font "` 不无限重建 id）
- **I5 keyFn**：`rt.forEach` 用带 keyFn 重载（`CharacterRuleItem::getId`，SceneRuntime.java:289），非 identity 重载（:264）—— copyWith 新对象引用变化时按 id 复用行节点
- **parse 错误透出**：行下方 `rt.show(errNonEmpty, () -> errorText)`，errNode setHitTestable(false)（R6）+ theme.errorColor()
- **无效规则写回**：无效也写回（不丢用户输入，运行时 isActive=false 天然不匹配）

### ConfigUI customizer hook（P3 架构阻碍解决方案）

P3 实施时发现 registry 在 `ConfigUI.buildScreen` 内部创建，uilib 接入层不可达。方案 A：ConfigUI 加 3 参 buildScreen 重载收 `Consumer<FieldRendererRegistry> registryCustomizer`（框架纯加法 DI 扩展点），ModernConfigEntry 用 customizer lambda 注入 registerPath。原 2 参委托 3 参传 `reg -> {}`（向后兼容）。

### selector 逗号多点：parse 展开形态 A

`FontCharacterRule.parse` 改返回 `List<FontCharacterRule>`（逗号拆段展开），`FontCharacterRuleSet.parse` 改 addAll。下游 `matches`/`resolveFontName` 零改动（形态 A 收益）。新增 `parseLine`（UI 专用，保留完整 selector 文本）。空段跳过，逗号作单字符 selector 不再支持（需 U+002C，P0 helper 已声明语法）。

### SceneAutocompletePrimitive：组合 TextInput + portal 浮层 + filtered keyed diff（P6）

- **组合而非包装**：内部 `SceneTextInputPrimitive.create` 拿输入行为 + Result（root/caretIndex/focused），autocomplete 在其上叠加候选浮层。不包装 `SceneTextInput` 成品（成品含 chrome，primitive 层要无样式）。
- **expanded 派生（守 R11，oracle F4 关键）**：纯 Computed 无法被 portalAnchored 的 dismissRequest 写（dismiss 要求只写 signal），故引入本地可写 `Signal<Boolean> suppressed`，expanded 派生自 `focused && !suppressed && !filtered.isEmpty() && !isExactSingleMatch`。dismissRequest lambda 只 `suppressed.set(TRUE)`。
- **suppressed 复位**：在 root 注册第二个 TEXT_INPUT handler 只做 `suppressed.set(FALSE)`（依赖 oracle F2：SceneInputRouter.dispatchToNode 同节点同事件多 handler 全跑无短路），否则 ESC 关一次后打字不复弹。
- **filtered 动态 → rt.forEach keyed diff**（关键差异点）：filtered 是 Computed<List>，候选列表必须用 `rt.forEach(filtered, Function.identity(), ...)` keyed diff（keyFn=候选字符串），不可照抄 SceneSelectPrimitive 静态 for 循环（options 构建期固定）。filtered 变化时按 key 复用/增删 item 节点（守 I5）。
- **键盘正交（oracle F1）**：primitive KEY_DOWN handler 只处理 ARROW_LEFT/RIGHT/HOME/END/BACKSPACE/DELETE，autocomplete 追加同节点 KEY_DOWN 只在 expanded 时处理 ARROW_DOWN/UP/ENTER/ESCAPE。键集不重叠，两 handler 各跑各键，无需 stopPropagation 对抗。
- **focus 时序（oracle F3）**：primitive.create 阶段已声明 `rt.interactionState(root).focused()` 关心；autocomplete 复用同一容器，expanded Computed 读 focused 自然顺序满足（primitive 在前，autocomplete 组合在后）。
- **Locale.ENGLISH 与 FontMatcher 真同源**（reviewer P1 反馈）：normalize 用 `trim().toLowerCase(Locale.ENGLISH)`（非 Locale.ROOT），与 FontMatcher.normalizeFontName（FontMatcher.java:269）真同源，锁定"用户配置的字体名能直接喂给 FontMatcher"承诺。字体名 ASCII 范围 ENGLISH 与 ROOT 行为一致，但 ENGLISH 消除承诺字面缺口。
- **接入范围（oracle §7）**：本轮只接 characterFontRules 的 fontNameInput（一处替换），fontSort 延后单列（行内输入在 SceneSimpleList 内部，接入需改通用控件层或自建行树，改动面大）。MatchMode 选 CONTAINS（用户拍板，宽容匹配适合记不清字体名完整开头）。

## 影响范围（初版）

### 新增文件
- `config/ui/field/CharacterRuleFieldRenderer.java`（P4 三栏编辑器 + CharacterRuleItem 内部类）
- `uilib/ui/scene/control/SceneAutocompletePrimitive.java`（P6 字体名自动补全 primitive，组合 SceneTextInputPrimitive + portal 浮层 + filtered keyed diff）

### 改动文件
- `uilib/config/modern/QzUiLibModernSchema.java`（P0 helper 文案）
- `config/ui/field/FieldRendererRegistry.java`（P1 path 分发）
- `config/ui/ConfigUI.java`（P3 customizer hook 3 参重载）
- `config/ui/field/SimpleListFieldRenderer.java`（P3 draggable 构造器）
- `uilib/config/modern/ModernConfigEntry.java`（P3/P4 customizer 双 registerPath）
- `uilib/ui/scene/control/SceneSimpleList.java`（P2 draggable Props + buildDragHandle + moveItem + pointerToRowIndex）
- `uilib/font/config/FontCharacterRule.java`（P5 parse 返回 List + parseLine）
- `uilib/font/config/FontCharacterRuleSet.java`（P5 addAll）
- `config/ui/field/CharacterRuleFieldRenderer.java`（P6 fontNameInput 接入 autocomplete + applyTextInputChrome 复刻 SceneTextInput 样式 + fontNameCandidateSnapshot 候选源）

### 不动（守迁移工程闭合状态）
- Config.java 4 字段 / CommonProxy 3 阶段时序 / FontConfig 无死方法 / QzUiLibModernSchema 无 slider
- schema 字段定义（characterFontRules 仍 simpleList，YAML 不变）
- toConfigValue（边界①，展开只在 parse 运行时）
- matches/resolveFontName（形态 A，零改动）

## 演进

- 2026-07-06：初始决策（本会话）。用户拍板 6 维度深化 + 字段拆分走渲染层 + 拖拽走档A + P4 行树走选项 B。oracle 出 8 阶段路线（P0-P7）。
- 2026-07-06：P0-P5 完成（6 commit）。6 阶段全部 reviewer 通过 + 全量测试绿。
  - **P0 helper 文案**（`9fcfea5b`）：fontSort 补"每行一个字体名/靠前优先/空则系统默认提示"；characterFontRules 补"选择器=字体名/单字符/U+XXXX/范围/disabled:前缀"语法说明。
  - **P1 Registry path 分发**（`3561a9c4`）：FieldRendererRegistry 加 pathOverrides + registerPath + resolve 改造（path 优先 type）。path 格式 "section.field" 确认（fontSystem.fontSort / fontSystem.characterFontRules）。
  - **P2 SceneSimpleList 拖拽基建**（`f678d19f`）：Props 加 draggable（默认 false）+ buildDragHandle 四段式 + moveItem + pointerToRowIndex。档 A 越界跳变，拖拽态存闭包 final 容器零 signal（守 R1+§5）。moveItem 移动同引用 id 不变（依赖 I5）。reviewer R1-R12+I5+§5 逐条全过。
  - **P3 fontSort 接入拖拽**（`5a2af136`）：ConfigUI 加 3 参 buildScreen customizer hook（方案 A，解决 registry 不可达架构阻碍）+ SimpleListFieldRenderer 加 draggable 构造器 + ModernConfigEntry registerPath fontSort。
  - **P4 CharacterRuleFieldRenderer 三栏**（`9cc39ed6`）：新建 renderer（选项 B 适配层自建行树）+ CharacterRuleItem（id+enabled+selector+fontName+errorMessage 派生）+ D2 桥 normalize 防抖动 + I5 keyFn（CharacterRuleItem::getId）+ parse 错误透出（rt.show 行下方红字）+ 无效规则写回。SceneCheckbox widthSizing=SHRINK 修复吞主轴坑（L3 harness 发现）。
  - **P5 selector 逗号多点**（`dc3bb87c`）：FontCharacterRule.parse 改返回 List（逗号拆段展开形态 A）+ 新增 parseLine（UI 专用）+ FontCharacterRuleSet addAll + CharacterRuleFieldRenderer 适配 errorMessage 派生。matches/resolveFontName 零改动（形态 A 收益）。空段跳过，逗号作单字符 selector 不再支持（需 U+002C）。
- **遗留 P-1**（P2 级，下会话首修）：UI 输入 `,,=Font`（全段空）时 CharacterRuleItem 构造内 parse 返回空 list → errorMessage=null，与 parseLine 视为 invalid 语义不一致。仅 UI 提示，运行时无害。reviewer 建议构造改用 parseLine 统一。
- 2026-07-06：**P-1 完成**（commit `6751e329`，前置 Docs `2145fdc9` 回写 P0-P5）。CharacterRuleItem 构造内 errorMessage 派生从 `parse`（返回 List，全段空时空 list → errorMessage=null）改为 `parseLine`（与 fromRaw/normalize 三路径同源，统一对"全段空 selector"的 invalid 裁决）。新增 P-1 防回归用例 `allEmptySegmentsProducesErrorConsistentWithParseLine`（fromRaw + withSelector 两路径断言 errorMessage 非空）；顺手清理 3 处历史 Javadoc（P5 fromRaw 改 parseLine 时遗留的 `{@link #parse}` 引用）。CharacterRuleFieldRendererTest 15 用例绿；reviewer 全过（I5 keyed diff 未破坏 / D2 normalize 防抖动未破坏 / R 系列 renderer 内部数据派生方式调整不碰 scene 节点装配）。
- **当时下一步**：P6 完成（commit `df73117f`+`daebbd55`，含 reviewer P1 反馈 Locale 修订）。剩 P7 字符选择辅助（用户本会话拍板"暂缓先收尾文档"）+ 真机验证 P0-P6 已落地部分。P7 启动时需派 explorer 侦察既有网格/表格控件范式 + oracle 出字符网格 picker 方案。fontSort 字体名补全在当时仍列为延后评估项；当前范围见文首历史说明。

## 不变量对齐（初版）

- **I1 handler signal 化**：所有交互 handler 只写 signal（localItems.set + onFieldEdit），不命令式改节点
- **I3 Computed 纯函数**：所有 Computed 体只读 signal 无副作用
- **I5 keyed diff**：SceneSimpleList moveItem 移动同引用 id 不变；CharacterRuleFieldRenderer forEach 用 keyFn 重载（CharacterRuleItem::getId）
- **I11 handler 只写 signal**：拖拽/编辑 handler 只 localItems.set + onFieldEdit，不碰节点属性槽
- **R1-R12**：SceneSimpleList 拖拽守 R1（零实例字段）/R3（effect 落 rt.bind）/R4（外观经 bind）/R5（读 interactionState）/R6（装饰 hitTestable false）/R7（受控零缓存）；CharacterRuleFieldRenderer 复用 SceneCheckbox/SceneTextInput（自身已守 R1-R12），renderer 守 I 系列 + R6 精神
- **硬约束§5 拖拽瞬态 signal 只写不读**：拖拽态存 handler 局部闭包 final 容器，不 signal 化
- **YAML 向后兼容**：字段拆分走渲染层，schema 字段定义不变，YAML 仍 List<String>
- **不动迁移工程闭合状态**：Config.java/FontConfig/CommonProxy/QzUiLibModernSchema 各边界保持
