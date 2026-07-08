# 现代化配置模板页 UI/UX 设计方案

> 产出方：ui-designer 子代理。本文档只描述设计方案，不含实现代码落盘。
> 设计输入：新架构 P0+P1 现有能力（ConfigScreen / FormFieldShell / 4 个 FieldRenderer /
> DraftSignalAdapter / ConfigTheme）+ scene 控件库现状 + `NORTH_STAR.md` I1–I11。
> **本方案不参考任何旧栈（ModernConfigTemplateScreen / HTML-like 配置页已删除）。**
>
> 标注约定：
> - 【待主 Agent 拍板】= 需要架构裁决的分叉点
> - 【占位】= 本轮设计为占位/桩，下一轮接业务时替换
> - 代码片段来源：`读源码` = 已读现有源码确认；`查文档` = 查证；`推测` = 未直接验证，需 fixer/oracle 核实

---

## 1. 设计原则（对齐 NORTH_STAR 不变量）

本设计严格服从以下不变量，每条都映射到具体设计决策：

| 不变量 | 设计落地 |
|---|---|
| **I1**（只经 signal 改 UI） | 活动分类、save 反馈、搜索关键词等所有交互态一律新增**只读受控 signal**，UI 经 `rt.bind` 消费；导航控件零自持状态。 |
| **I3**（组件函数只跑一次、无副作用） | section 切换**不重建树**，靠 `rt.show(parent, condition, supplier)` 按条件挂卸；禁止在 Supplier 体内 `activeSection.get()` 做 if 分支建树。 |
| **I4**（最低失效级别） | 文本/增删节点 → `LAYOUT`；颜色/边框 → `PAINT`。导航高亮、卡片三态边框、save 反馈色全部 `PAINT` 级；error 文本出现/消失走 `LAYOUT`（撑高布局）。 |
| **I5**（diff 只在 keyed 列表内） | 多 section 列表导航、P2 动态字段列表，若用动态项必须 keyed；当前 section 数构建期固定，优先用 `rt.show` 而非 forEach diff。 |
| **I7/I8**（干净子树跳过 + 缓存复用） | section 内容用 `rt.show` 懒挂载：未激活 section 的字段卡片子树不参与布局/绘制。滚动只走 GEOMETRY（沿用 viewport 既有机制）。 |
| **I11**（输入 handler 只写 signal） | 所有导航点击、按钮点击的 handler 只 `signal.set(...)` 或调 adapter 方法（内部 set），不直接改 SceneNode 属性槽。 |

**核心设计取向**：把 ConfigScreen 当前「一次平铺全部 section 字段」改为「受控单 section 显示」，这是 Oracle 已裁决的 P2 第一优先「分类导航降 N」——把单页字段量从「全部」降到「单类」，直接受益于 I7 干净子树跳过。

---

## 2. 页面整体布局

保持 ConfigScreen 现有 5 区骨架（`读源码` ConfigScreen.java），但在 viewport 内部引入「导航 + 单 section 内容」的二级结构。

```
root (COLUMN, fillParentHeight, padding=20, gap=12, bg=ROOT_BG)
  ├ titleBar      (固定高 44)  标题 + modId + 【P2占位】搜索框槽
  ├ statusSummary (固定高 34)  dirty/error 徽标 + 【新增】save 反馈条
  ├ bodyRow       (ROW, fillParentHeight, gap=12)        ← 多 section 形态新增的横向布局
  │   ├ navPane   (固定宽 ~160, COLUMN)   分类导航（少量/多量两形态见 §3）
  │   └ viewport  (scrollable, fillParentHeight, clip, bg=VIEWPORT_BG, radius=10)
  │       └ content (COLUMN, gap=14)
  │           └ 对每个 section i：rt.show(content, activeSection==i, () -> sectionPanel(i))
  │                                  └ sectionPanel = COLUMN(字段卡片×N)
  └ actionBar     (固定高 46)  恢复默认 / 取消 / 保存 + 【新增】save 失败提示
```

**关键结构变更点（对比现状）**：
- 现状 `renderFields()` 把所有 section 的 sectionNode 直接 append 进 content（`读源码` ConfigScreen.java，全平铺）。新方案改为：每个 section 一个 `rt.show` 条件挂载，只有 `activeSection` 命中的 section 字段才建树/布局。
- 少量 section（≤5）用**横向 SceneTab 在 viewport 上方**，不引入 navPane，保持单列纵向（见 §3.1）。
- 多 section（>5）用**左侧 navPane（SceneSimpleList 受控选择）**，bodyRow 变 ROW 双栏（见 §3.2）。

【待主 Agent 拍板】少量/多量形态切换阈值定在 **section 数 ≤5 用 Tab、>5 用侧栏**（与 ChoiceFieldRenderer 的 `SEGMENTED_THRESHOLD=4` 同源思路）。是否需要让 schema 显式声明导航形态、还是纯按数量自动选，待裁决。

---

## 3. 分类导航设计

### 3.0 共同基础：activeSectionSignal（受控源）

无论哪种形态，导航的「当前活动分类」都由一个新增受控 signal 驱动，导航控件零自持状态（守 I1/I8）。

**新增字段（ConfigScreen 内）**：

```java
// 来源：推测（新增）；语义照 SceneTab.Props.activeIndex 受控范式（读源码 SceneTab.java:82）
/** 当前活动 section 下标（受控源），导航控件唯一驱动 */
private Signal<Integer> activeSectionSignal;   // 初值 0
```

构造时在 uiOwner 作用域内创建：`this.activeSectionSignal = Signal.create(0);`
切换分类的唯一路径：`activeSectionSignal.set(i)`（点击 handler 内，守 I11）。

**内容区切换（所有形态共用）**——照 SceneTab R10 范式（`读源码` SceneTab.java）：

```java
// 来源：读源码（rt.show 三参签名见 SceneTab.java:218、SceneObjectField.java:346）
List<SectionSpec> sections = schema.sections();
for (int i = 0; i < sections.size(); i++) {
    final int idx = i;
    final SectionSpec section = sections.get(i);
    // condition：activeSection == idx；Supplier 体内只跑一次建该 section 字段卡片
    rt.show(content,
            Computed.create(() -> Integer.valueOf(idx).equals(activeSectionSignal.get())),
            () -> buildSectionPanel(rt, section));   // buildSectionPanel 内遍历 section.fields() 调 registry.render
}
```

> 铁律（照 SceneTab.java 注释）：**绝不**在 Supplier 体内 `activeSection.get()` 做 if 建树，**绝不**命令式 `clearChildren` 重挂。N 个独立 `rt.show` 各自管理挂卸。

### 3.1 少量 section（≤5）：横向 SceneTab 或 SceneSegmented

放在 statusSummary 与 viewport 之间，横向一排页签。**推荐 SceneSegmented 而非 SceneTab**：
- SceneTab 自带 contentPanel 单内容区（`读源码` SceneTab.java），但我们的内容区是 viewport 内的 content，已自管 `rt.show`，不需要 Tab 再套一层内容容器，否则双层 show 冗余。
- SceneSegmented 是纯「N 选 1 受控头」（`读源码` SceneSegmented.java），只产页签条、不管内容，正好当导航头，与外部 content 的 `rt.show` 解耦。

**API 片段（SceneSegmented，`读源码` SceneSegmented.java）**：

```java
// Props record 签名（已存在，照抄）：
// record Props(ReadableSignal<Integer> selectedIndex, List<String> options,
//              ReadableSignal<Boolean> enabled, Consumer<Integer> onSelect)

List<String> sectionTitles = sections.stream().map(SectionSpec::title).collect(...);
SceneSegmented.Props navProps = new SceneSegmented.Props(
        activeSectionSignal,                 // selectedIndex 受控源
        sectionTitles,                       // 各分类标题
        Signal.create(Boolean.TRUE),         // enabled 常驻
        idx -> activeSectionSignal.set(idx)  // onSelect 只写 signal（守 I11）
);
runtime.mount(navBarParent, SceneSegmented.create(runtime, navProps));
```

> 注意：SceneSegmented 段宽固定 72px（`读源码` SceneSegmented.java，scene 无 flex-grow）。section 标题较长时会截断。【待主 Agent 拍板】是否接受固定段宽，或需要 fixer 给 SceneSegmented 加可配段宽（属控件库改动，超出本设计范围）。

### 3.2 多 section（>5）：左侧 navPane（SceneSimpleList 受控选择）

bodyRow 变 ROW 双栏，左侧 navPane 固定宽 ~160，纵向列出全部分类。**推荐 SceneSimpleList**（`读源码` 文件存在，create 签名 SceneSimpleList.java）做纵向受控单选列表；列表项多时 navPane 自身可设 scrollable 独立滚动。

> SceneSimpleList 的 Props 完整签名本轮未逐行读（只确认存在 + create 签名）。fixer 实现前需读 `SceneSimpleList.java` 确认 Props 字段（推测含 items + selectedIndex/onSelect + enabled）。若 SceneSimpleList 不支持「受控单选 + onSelect 下标回调」，**回退方案**：用 SceneSingleSelectPrimitive（SceneSegmented/SceneTab 的底层，`读源码` 二者均复用它）以 `Orientation.VERTICAL` 直接搭纵向导航条，Props 签名见 SceneSegmented.java：
> ```java
> // 来源：读源码 SceneSingleSelectPrimitive 用法（SceneSegmented.java）
> new SceneSingleSelectPrimitive.Props(
>     activeSectionSignal, sectionTitles, Signal.create(Boolean.TRUE),
>     idx -> activeSectionSignal.set(idx),
>     SceneSingleSelectPrimitive.Orientation.VERTICAL)
> ```

【待主 Agent 拍板】navPane 用 SceneSimpleList 还是直接用 SceneSingleSelectPrimitive(VERTICAL)。前者语义更高、需确认 Props 支持受控；后者已验证可用但偏底层。推荐先验证 SceneSimpleList，不满足再退 primitive。

### 3.3 嵌套分类预留

当前 `SectionSpec` 是扁平的（`读源码` SectionSpec.java，无子 section 字段）。嵌套是 schema 层扩展，本设计只留 UI 口子，不实现：

- **导航形态演进**：嵌套时 navPane 顶部加一条 **SceneBreadcrumb** 显示当前路径（`读源码` SceneBreadcrumb.java：纯展示 + onSelect(path) 回调，零状态），navPane 列表只显示当前层级的子分类。点击面包屑某段 → set 一个新增的 `navPathSignal`（受控）回退层级。
- **activeSection 升级**：扁平的 `activeSectionSignal: Signal<Integer>` 升级为 `navPathSignal: Signal<List<String>>`（路径栈），`rt.show` 的 condition 改为「当前路径前缀匹配」。
- **Breadcrumb API（`读源码` SceneBreadcrumb.java）**：
  ```java
  // record Props(List<Segment> segments, ReadableSignal<Boolean> enabled, Consumer<String> onSelect)
  // record Segment(String path, String label)
  new SceneBreadcrumb.Props(
      segments,                          // 当前路径各段
      Signal.create(Boolean.TRUE),
      path -> navPathSignal.set(truncateTo(path)))  // 点击回退到该段
  ```

【待主 Agent 拍板】嵌套属 P2/P3，schema 模型未支持。本轮只确认 UI 形态可用 Breadcrumb+show 演进，不动 schema。

---

## 4. 字段卡片视觉规范

基于现有 FormFieldShell（`读源码` FormFieldShell）优化，结构不变（header dot+title / helper / 控件槽 / error），细化视觉 token。

### 4.1 间距 / 内边距 / 圆角（沿用 ConfigTheme，`读源码` ConfigTheme.java）

| 维度 | 当前值 | 建议 | 说明 |
|---|---|---|---|
| 卡片圆角 | `CARD_RADIUS = RADIUS_LG(6)` | 保持 | 大容器档，OK |
| 卡片内边距 | `CARD_PAD = PAD_LG(10)` | 保持 | 宽松档 |
| 卡片内 gap | `FIELD_GAP = GAP_MD(8)` | 保持 | header/helper/控件/error 间距 |
| 卡片间距（section 内） | sectionNode gap=`FIELD_GAP(8)` | 保持 | |
| 边框宽度 | `borderWidth=1` | 保持 | 三态只改色不改宽（守 I4 PAINT 级，照 SceneStateColors.standardBorder 注释 paint.SceneStateColors.java） |

### 4.2 normal / dirty / error 三态（`读源码` FormFieldShell）

现有三态机制已正确（边框色 + header dot 色由 error/dirty Computed 派生，PAINT 级 bind）。保留并明确视觉口径：

| 态 | 边框色 | header dot 色 | 说明 |
|---|---|---|---|
| normal | `CARD_BORDER (0xFF2F4D87)` 暗蓝 | `MUTED_COLOR (0xFF8AA0C8)` 灰蓝 | 默认 |
| dirty | `CARD_BORDER_DIRTY (0xFF3B5BA5)` 提亮蓝 | `DIRTY_COLOR (0xFF60A5FA)` 蓝 | 有未保存改动 |
| error | `CARD_BORDER_ERROR (0xFFF87171)` 红 | `ERROR_COLOR (0xFFF87171)` 红 | 校验失败（优先级高于 dirty） |

**优先级**：error > dirty > normal（`读源码` FormFieldShell resolveCardBorder 已实现此优先级）。✅ 无需改。

**微调建议（可选）**：
- dirty 态增加**左侧色条**视觉（在 card 左边缘 2px 蓝条）比仅边框提亮更醒目。实现：card 内加一个 `preferredWidth=2` 的竖条子节点，bind 颜色到 dirty/error Computed。【占位】本轮可不做，作为 P2 视觉增强。
- error 态当前 dot 在 dirty 时显示 DIRTY_COLOR、非 dirty 时 ERROR_COLOR（`读源码` FormFieldShell 逻辑）——存在 dirty+error 同时为真时 dot 显示蓝而非红的小不一致。建议 dot 也统一为 error 优先：`error 非空 → ERROR_COLOR；dirty → DIRTY_COLOR；else MUTED`。给 fixer：见 §5 末。

### 4.3 helper / error 文本排版（`读源码` FormFieldShell）

- helper：`MUTED_COLOR` 次要色，置于 header 下、控件上。现状 OK。
- error：`ERROR_COLOR` 红，置于控件下。现状用 `bind(LAYOUT, errorSig, errorNode::setText)`——error 文本出现/消失会撑高卡片，走 LAYOUT 级正确（守 I4）。✅
- 建议：error 文本前加「⚠ 」前缀字符增强辨识（纯文本拼接，无新控件）。【占位】可选增强。

---

## 5. 4 个现有字段控件视觉微调建议

总体：4 个 renderer 控件选择合理（`读源码` 各 renderer），微调以视觉一致性为主，不动交互逻辑。

| 字段类型 | 控件 | 现状 | 微调建议 |
|---|---|---|---|
| STRING | SceneTextInput | placeholder 用 helper（`读源码` StringFieldRenderer.java） | OK。helper 既当 placeholder 又当卡片 helper 文本，**信息重复**——建议 placeholder 用独立简短提示或留空，helper 只在卡片 helper 区显示。【待拍板】 |
| NUMBER | 有 range→SceneSlider / 无 range→SceneTextInput | slider step=1 整数量化（`读源码` NumberFieldRenderer.java） | slider 旁建议加**当前数值读数**（readout 文本），否则用户不知精确值。SceneSlider 是否自带读数需 fixer 读 SceneSlider.java 确认；若无，FormFieldShell 控件槽旁加一个 bind 到 numValue 的文本节点。 |
| BOOLEAN | SceneToggle | label 传 spec.label（`读源码` BooleanFieldRenderer.java） | toggle 自带 label，与 FormFieldShell header title **重复显示**。建议 toggle 的 label 传空串、只靠 header title，或反之 header 隐藏 title。【待拍板】 |
| CHOICE | ≤4→SceneSegmented / >4→SceneSelect | 阈值 4（`读源码` ChoiceFieldRenderer.java,49） | OK。与 §3.1 导航同用 Segmented，注意 segment 固定宽 72，长选项截断同问题。 |

**给 fixer 的 dot 三态统一修正（§4.2 提及）**——把 FormFieldShell 的 dot 颜色 Computed 改为 error 优先：

```java
// 来源：推测（修正现有 FormFieldShell 的优先级小瑕疵）
rt.bind(Invalidation.PAINT,
    Computed.create(() -> {
        if (!safe(errorSig.get()).isEmpty()) return ConfigTheme.ERROR_COLOR;   // error 优先
        if (Boolean.TRUE.equals(dirtySig.get())) return ConfigTheme.DIRTY_COLOR;
        return ConfigTheme.MUTED_COLOR;
    }),
    dot::setTextColor);
```

---

## 6. 状态栏（statusSummary）设计

现状（`读源码` ConfigScreen.java）：两个胶囊徽标——dirty 徽标 + error 徽标，文案+色由 Computed 派生。机制正确，扩展信息密度：

**显示信息（建议）**：
1. **脏字段计数徽标**：「N 项未保存」。需 adapter 暴露脏字段计数（当前只有 `isDirtySignal` 布尔，`读源码` DraftSignalAdapter.java）。
2. **错误计数徽标**：「N 项校验错误」。同需计数派生。
3. **可保存状态**：可不单列徽标，由 actionBar 保存按钮 enabled 体现（`canSaveSignal` 已有）。

**视觉形态**：保持胶囊徽标（`读源码` ConfigScreen.java badge），不引入进度条（配置页无「进度」语义，进度条误导）。

**新增计数所需 adapter 能力【占位 → 接线】**：
```java
// 来源：推测（DraftSignalAdapter 当前无计数 API，DraftSignalAdapter.java:172-181 只有布尔聚合）
// 下一轮 fixer 在 DraftSignalAdapter 新增（接线点）：
public ReadableSignal<Integer> dirtyCountSignal();   // 遍历 dirtySignals 计 true 数
public ReadableSignal<Integer> errorCountSignal();   // 遍历 errorSignals 计非空数
```
本轮 statusSummary 文案先用现有布尔徽标（「有/无未保存更改」「存在/通过校验」）作【占位】，计数版下一轮接 `dirtyCountSignal/errorCountSignal`。

---

## 7. 操作栏（actionBar）设计

现状（`读源码` ConfigScreen.java）：恢复默认 / 取消(enabled=isDirty) / 保存(enabled=canSave) 三按钮横排，固定宽 110。

### 7.1 布局（主次区分）

建议**左右分区**：
```
actionBar (ROW, mainAxisAlign=SPACE_BETWEEN 或 用 spacer)
  ├ 左：恢复默认（次要，危险弱化）
  └ 右：取消（次要） + 保存（主按钮）
```
> scene 是否支持 `mainAxisAlign=SPACE_BETWEEN` 需 fixer 确认 MainAxisAlign 枚举（`读源码` 见 SceneSegmented import MainAxisAlign）。若不支持，用一个 `fillParentWidth` 的 spacer 节点占中间撑开。

主次视觉：保存为主按钮（建议 ACCENT 底色），取消/恢复默认为标准按钮。SceneButton 现状是统一 chrome（`读源码` SceneButton.java），**不支持 variant**。【待主 Agent 拍板】主按钮高亮是否需要 fixer 给 SceneButton 加 primary variant（控件库改动），还是本轮接受三按钮同视觉、仅靠位置区分。推荐本轮接受同视觉、位置区分，variant 留 P2。

### 7.2 按钮 enabled/disabled 视觉

沿用 SceneButton 自带 disabled 灰态（`读源码` SceneStateColors.standardBackground enabled=false → BG_DISABLED）。enabled 派生已正确接线：取消=isDirty、保存=canSave、恢复默认=常 true（`读源码` ConfigScreen.java）。✅

### 7.3 save 失败 UI 反馈【新增，补交接记录已知缺口】

现状缺口：`saveChanges()` 失败时 `lastSaveOutcome.isSuccess()` 为 false 就静默不同步（`读源码` ConfigScreen.java），**无任何 UI 反馈**。

**设计**：新增 `saveFeedbackSignal`，actionBar 上方或 statusSummary 区显示一条反馈文本。

```java
// 来源：推测（新增）
/** 保存反馈：空串=无、成功提示、失败原因 */
private Signal<String> saveFeedbackSignal;        // 初值 ""
/** 反馈是否为错误（决定红/绿色），可由文案前缀或独立布尔承载 */
private Signal<Boolean> saveFeedbackIsErrorSignal; // 初值 false
```

saveChanges 改造（接线）：
```java
// 来源：读源码（改造 ConfigScreen.java:244-250）+ 推测（新增反馈写入）
private void saveChanges() {
    DraftBuffer draft = adapter.draft();
    lastSaveOutcome = manager.save(draft);
    if (lastSaveOutcome.isSuccess()) {
        adapter.afterSaveSync();
        saveFeedbackSignal.set("已保存");
        saveFeedbackIsErrorSignal.set(Boolean.FALSE);
    } else {
        // SaveOutcome 的失败原因字段需 fixer 读 SaveOutcome.java 确认（推测有 message/error）
        saveFeedbackSignal.set("保存失败：" + lastSaveOutcome.message());  // 字段名待核实
        saveFeedbackIsErrorSignal.set(Boolean.TRUE);
    }
}
```

反馈文本节点（actionBar 旁，`PAINT` 级 bind 色、`LAYOUT` 级 bind 文本）：
```java
// 来源：推测（照 ConfigScreen.text + bind 范式，ConfigScreen.java:313 / FormFieldShell）
SceneNode feedback = text("", ConfigTheme.MUTED_COLOR);
runtime.bind(Invalidation.LAYOUT, saveFeedbackSignal, feedback::setText);
runtime.bind(Invalidation.PAINT,
    Computed.create(() -> Boolean.TRUE.equals(saveFeedbackIsErrorSignal.get())
        ? ConfigTheme.ERROR_COLOR : ConfigTheme.OK_COLOR),
    feedback::setTextColor);
```

> 【接线】`SaveOutcome` 失败原因字段名（`message()` 还是 `error()` 还是别的）fixer 实现前必须读 `src/main/java/club/heiqi/config/runtime/SaveOutcome.java` 确认。本轮设计按「有可读失败原因」假设。

---

## 8. 标题栏（titleBar）设计

现状（`读源码` ConfigScreen.java）：COLUMN，两行文本——「配置编辑器」+「modId: xxx」，`hitTestable=false`。

**建议**：
- 改 ROW 主结构，左侧 mod 图标槽 + 标题，右侧搜索框槽（P2）。
```
titleBar (ROW, crossAxisAlign=CENTER, gap)
  ├ [P2占位] modIcon (preferredWidth=高度, 方形图标槽)
  ├ titleCol (COLUMN)  「配置编辑器」+「modId: xxx」
  ├ spacer (fillParentWidth)
  └ [P2占位] searchBox (SceneTextInput, 受控)   见 §9.2
```
- mod 图标【占位】：本轮留空方形节点（或不放），下一轮接 mod 元数据图标。MC 1.7.10 图标资源加载属宿主层，不入 config.ui 核心。
- 标题文案「配置编辑器」建议改为 schema 提供的人类可读标题（当前 schema 只有 modId，无 displayName，`读源码` ConfigSchema.java）。【待拍板】是否给 ConfigSchema 加 title 字段。

---

## 9. P2 扩展预留

### 9.1 八种复杂字段类型卡片形态

FieldType 预留 8 种（`读源码` FieldType.java 注释）。所有复杂类型**复用 FormFieldShell 外壳**（header/helper/error 不变），只换控件槽内容，且**卡片高度可变**（FormFieldShell 控件槽当前固定 INPUT_HEIGHT=30，`读源码` FormFieldShell——复杂类型需放开此固定高）。

| FieldType | 推荐 scene 控件 | 卡片形态 | 现存控件确认 |
|---|---|---|---|
| LONG_TEXT | SceneTextArea | 内嵌多行编辑区，viewportHeight≈120（`读源码` SceneTextArea.java） | ✅ 存在 |
| SIMPLE_LIST | SceneSimpleList | 内嵌可增删列表 | ✅ 存在（create SceneSimpleList.java） |
| TABLE | SceneDataTable | 内嵌表格，行高 ROW_HEIGHT_TABLE=28 | ✅ 存在（create SceneDataTable.java） |
| OBJECT | SceneObjectField | 可折叠嵌套对象编辑（自带 expand，`读源码` SceneObjectField.java show） | ✅ 存在 |
| KEY_VALUE_MAP | SceneKeyValueMap | 内嵌键值对增删 | ✅ 存在（create SceneKeyValueMap.java） |
| PRESET_SELECTOR | SceneSelect + 按钮 | 下拉预设 + 应用按钮 | 组合现有控件 |
| RAW_EDITOR | SceneTextArea（等宽/只读切换） | 原始文本编辑区 | 复用 TextArea |
| ENHANCED_PICKER | SceneSelect / 自定义 | 增强选择器 | 待 P2 评估 |

**FormFieldShell 放开固定高（给 fixer）**：复杂类型 renderer 不应让 FormFieldShell 强制 `controlRoot.setPreferredHeight(INPUT_HEIGHT)`（`读源码` FormFieldShell）。建议 FormFieldShell.build 增加重载/参数让 caller 决定是否设固定高，标量类型设、复杂类型不设（用控件自身高度）。

### 9.2 全局搜索位置

**位置**：titleBar 右侧（§8 searchBox 槽）。受控 `searchKeywordSignal: Signal<String>`。
**交互**：搜索过滤走「字段可见性」——给每个字段卡片的 `rt.show` condition 增加「匹配关键词」与门。**不重建树**，靠 show 挂卸不匹配卡片（守 I3/I7）。
```java
// 来源：推测（P2 预留）
Signal<String> searchKeyword = Signal.create("");
// 字段卡片 show condition = activeSection 命中 && 字段匹配关键词
rt.show(sectionPanel,
    Computed.create(() -> matchesKeyword(field, searchKeyword.get())),
    () -> registry.render(...));
```
【待拍板】搜索是「跨 section 全局」还是「当前 section 内」。全局搜索可能需要临时打破单 section 显示（显示所有匹配字段），与 §3 单 section 模型有张力，待裁决。

### 9.3 嵌套分类演进

见 §3.3。核心：`activeSectionSignal: Signal<Integer>` → `navPathSignal: Signal<List<String>>`，导航加 SceneBreadcrumb，`rt.show` condition 改前缀匹配。schema 需先支持子 section（属 schema 层扩展，不在本设计）。

---

## 10. 对 fixer 的实现建议

### 10.1 实现顺序（增量、每步可测）

1. **第一步：activeSection + rt.show 单 section 显示**（核心降 N）
   - 改 `ConfigScreen`：新增 `activeSectionSignal`；`renderFields()` 从「全平铺 append」改为「每 section 一个 `rt.show`」（§3.0 片段）。
   - 加 SceneSegmented 导航头（§3.1，≤5 section 形态先做）。
   - 验证：切换分类只显示对应 section 字段，未激活 section 子树不布局（可测 content 子节点数）。
2. **第二步：save 失败反馈**（补已知缺口）
   - 读 `SaveOutcome.java` 确认失败原因字段。
   - 新增 `saveFeedbackSignal` + `saveFeedbackIsErrorSignal`，改造 `saveChanges()`（§7.3），加反馈文本节点。
3. **第三步：状态栏计数**
   - `DraftSignalAdapter` 新增 `dirtyCountSignal/errorCountSignal`（§6），statusSummary 文案改计数版。
4. **第四步：FormFieldShell dot 三态修正 + 视觉微调**（§4.2/§5，低风险小改）。
5. **第五步（条件性）：多 section 侧栏导航**（§3.2）——section 数确实 >5 时再做。
6. **P2 预留（不在本轮）**：复杂字段类型、搜索、嵌套分类。

### 10.2 改动文件清单

| 文件 | 改动 | 步骤 |
|---|---|---|
| `config/ui/ConfigScreen.java` | 新增 activeSectionSignal、rt.show 重构 renderFields、导航头 mount、save 反馈、actionBar 布局 | 1,2,7 |
| `config/ui/DraftSignalAdapter.java` | 新增 dirtyCountSignal/errorCountSignal | 3 |
| `ui/scene/form/FormFieldShell.java` | dot 三态优先级修正、复杂类型放开固定高 | 4,9.1 |
| `config/ui/theme/ConfigTheme.java` | （可选）新增 dirty 左色条/反馈色 token | 4,7 |
| 4 个 FieldRenderer | label/placeholder 重复消解（§5） | 4 |

### 10.3 需新增的类（P2，本轮不写）

- 复杂字段 renderer：`LongTextFieldRenderer`、`SimpleListFieldRenderer`、`TableFieldRenderer`、`ObjectFieldRenderer`、`KeyValueMapFieldRenderer` 等，各实现 `FieldRenderer` 接口、注册进 `FieldRendererRegistry`（`读源码` FieldRendererRegistry.java defaultRegistry 范式照抄）。
- 导航若抽象：可选 `SectionNavigator` 协作者类（封装 activeSectionSignal + 形态选择），避免 ConfigScreen 膨胀（ConfigScreen 已 403 行，接近拆分阈值）。

### 10.4 fixer 实现前必须核实的点（本轮未直接验证）

1. `SaveOutcome` 失败原因字段名（§7.3）—— 读 `config/runtime/SaveOutcome.java`。
2. `SceneSimpleList.Props` 是否支持受控单选 + onSelect 下标（§3.2）—— 读 `SceneSimpleList.java`。
3. `MainAxisAlign` 是否有 `SPACE_BETWEEN`（§7.1）—— 读 layout 枚举。
4. `SceneSlider` 是否自带数值读数（§5 NUMBER）—— 读 `SceneSlider.java`。
5. `SceneSegmented` 固定段宽 72 对长 section 标题的截断影响（§3.1）—— 实测或接受。

---

## 附：核心决策一句话摘要

- **§3 导航**：activeSectionSignal 受控 + N 个 rt.show 切 section（守 I3/I7 降 N），≤5 用横向 SceneSegmented、>5 用左侧 SceneSimpleList/SingleSelectPrimitive(VERTICAL)。
- **§4 卡片**：保留 FormFieldShell 三态边框机制，仅修正 dot 优先级 + 放开复杂类型固定高。
- **§6 状态栏**：胶囊徽标升级为脏/错计数（需 adapter 加计数 signal）。
- **§7 操作栏**：左右分区主次按钮 + 新增 saveFeedbackSignal 补 save 失败反馈缺口。
- **§9 P2**：8 复杂类型全复用 FormFieldShell 换控件槽、搜索走 show condition 与门、嵌套走 navPath + Breadcrumb。

### 待主 Agent 拍板清单（已拍板，2026-06-29）

1. ✅ **少量/多量 section 形态阈值**：≤5 Tab / >5 侧栏，纯按数量自动选，schema 不显式声明。
2. ⚠️ **SceneSegmented 固定段宽**：本轮接受固定段宽，section 标题过长时截断由 fixer 评估是否给 SceneSegmented 加可配段宽（控件库改动，若实现成本低可一并补，否则留 P2）。
3. ✅ **navPane 控件选型**：允许补样式包/控件库扩展来满足 config 需求。fixer 优先验证 `SceneSimpleList` 是否支持受控单选；若不满足，可给 scene 控件库补一个受控单选 List 变体（控件库改动允许）。
4. ✅ **SceneButton 主按钮高亮**：允许补样式包。fixer 给 `SceneButton` 加 `primary` variant（ACCENT 底色），保存按钮用 primary，取消/恢复默认用标准。
5. ⚠️ **STRING placeholder/helper 重复、BOOLEAN toggle label/title 重复**：由 fixer 实现时按字段实际语义消解，优先去 placeholder 留 helper，BOOLEAN 去 toggle label 留 FormFieldShell title。
6. ⚠️ **ConfigSchema 加人类可读 title 字段**：本轮不加，section/field 的 title 由 FieldSpec.label()/SectionSpec.title() 现有能力承载（如已存在）；若当前 API 没有，fixer 评估是否补最小字段。
7. ✅ **全局搜索范围**：本轮不做，仅在设计文档预留接口位置，P2 实现时再决定跨 section 还是 section 内。
