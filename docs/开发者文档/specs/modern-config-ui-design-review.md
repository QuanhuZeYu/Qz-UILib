# 现代化配置页布局 —— 设计审视报告

> 审视方：ui-designer 子代理。本文档只做当前实现的视觉/布局审视，不改源码、不重写设计方案。
> 审视输入：`ConfigScreen.java`（663 行实读）/ `FormFieldShell.java` / 4 个 FieldRenderer /
> `ConfigTheme.java` / `SceneScrollbar.java` / `SceneSegmented.java` /
> `SceneStateColors.java` / `SceneChromeTokens.java` / 设计方案 `modern-config-ui-design.md`。
> 证据标注：`读源码` = 已读行号确认；`推断` = 基于代码逻辑的真机视觉推理（未真机验证）。
>
> 对照基线：commit `bd07fead`，P0+P1+P1.5+4 项真机改进已落地，2502 测试绿。

---

## 1. 审视结论

**通过有条件 —— 存在 1 项必须重做的严重问题（字号层级缺失）+ 3 项严重布局问题，需改进后再上真机。**

核心判断：当前实现的**响应式骨架、三态机制、信号驱动都正确**（架构层面无问题，I1–I11 守得住）。但**视觉层面存在一个贯穿全页的致命缺陷：全页文本同字号 16px，没有任何字号层级**。这是用户反馈"看起来有严重问题"的最可能根因——页面在视觉上完全扁平，标题不像标题、分组不像分组、说明不像说明。叠加固定高开销过大、actionBar 未分区、save 反馈挤行三个严重布局问题，真机第一眼观感会显著偏"丑/挤/平"。

这些问题**都不是架构错误，是视觉规范缺失**——修复成本中等，不需要推翻骨架。

---

## 2. 严重问题清单（按优先级排序）

### S1【必修·重做级】字号层级完全缺失：全页文本同为 16px

**问题描述**：整个配置页所有文本节点都用 `SceneNode` 默认字号 16px，没有任何一处调用 `setFontSize`。标题、section 标题、字段 label、helper、error、徽标、按钮文案视觉上完全同级。

**证据**：
- `grep setFontSize/fontSize` 在 `src/main/java/club/heiqi/config` 全包 **零命中**（无任何字号设置）。
- `SceneNode` 默认 `fontSizePx = 16`（`读源码` SceneNode.java）。
- `setFontSize` API 存在且可用（`读源码` SceneNode.java，标 LAYOUT+PAINT）——是实现**没用**，不是控件不支持。
- 涉及的全部文本节点同字号：titleBar "配置编辑器"（ConfigScreen.java）、modId 行（:234）、sectionTitle（:423）、字段 label（FormFieldShell）、helper（FormFieldShell）、error（FormFieldShell）、徽标文本（ConfigScreen.java）、save 反馈（:271）、按钮文案（经 SceneButton）。

**真机视觉推断**：用户打开页面看到的是一面"文字墙"——主标题"配置编辑器"和字段里的 helper 小字一样大，section 分组标题和字段值一样大。没有视觉锚点，眼睛不知道先看哪、层级如何。这是"现代化配置页"和"朴素表单"最大的观感差距，也是最可能被一眼判定"很丑/很平"的点。

**应该是什么**：建立字号层级 token。建议梯度（UI 像素）：

| 角色 | 建议字号 | 当前 | 节点位置 |
|---|---|---|---|
| 页面主标题「配置编辑器」 | 20–22 | 16 | ConfigScreen.java |
| modId 副标题 | 12 | 16 | ConfigScreen.java |
| section 标题 | 16–18 | 16 | ConfigScreen.java |
| 字段 label | 14 | 16 | FormFieldShell |
| helper / error | 12 | 16 | FormFieldShell |
| 徽标 / 按钮 | 12–13 | 16 | ConfigScreen.java 等 |

**修复指引（给 fixer）**：在 `ConfigTheme` 新增字号常量（如 `FONT_TITLE=20`、`FONT_SECTION=17`、`FONT_LABEL=14`、`FONT_HELPER=12`），在各 `text(...)` 构建处补 `node.setFontSize(...)`。`ConfigScreen.text()`（:548）和 `FormFieldShell.text()` 可加一个字号参数重载，避免散落硬编码。

> **注**：这同时是**设计方案的盲区**——`modern-config-ui-design.md` §4/§8 只规定了间距、颜色、圆角，**从未建立字号体系**。所以这不是"实现偏离设计"，是设计本身漏了字号维度。本报告 §5 单列。

---

### S2【必修】固定高开销过大，真机小 GUI scale 下 viewport 会被严重挤压甚至塌缩

**问题描述**：root 垂直方向的固定（非内容）高度开销累加过大，留给 viewport（真正放字段的区域）的高度在小屏/大 GuiScale 下可能所剩无几。

**证据**（全部 `读源码`）：
- root padding=20（上下共 40）：ConfigScreen.java
- root gap=12：:217，分隔 titleBar/statusSummary/navBar/scrollContainer/actionBar 共约 4 个间隙 = 48
- titleBar 固定高 44：ConfigTheme.java
- statusSummary 固定高 34：ConfigTheme.java
- actionBar 固定高 46：ConfigTheme.java
- navBar（SceneSegmented）行高 ≈ 文本 16 + 2×PAD_LG(10) = 36（`推断`，SceneSegmented.java padding + 默认字号）

固定开销合计 ≈ 40 + 48 + 44 + 34 + 46 + 36 ≈ **248px** 非内容高度。

**真机视觉推断**：MC 1.7.10 GUI 在 GuiScale=Auto/大 时，缩放后逻辑高度常落在 ~240–300px 区间（`推断`，未真机量）。此时 248px 固定开销几乎吃满全屏，viewport 仅剩几十像素，**只能露出半张字段卡片甚至塌缩成一条缝**。即使在中等屏，viewport 也明显偏窄，需要频繁滚动。这是"很挤"的直接来源。

**应该是什么**：
1. 压缩固定高：titleBar 44→36、statusSummary 34→26、actionBar 46→40，root padding 20→14，gap 12→8。合计可省 ~40–50px。
2. statusSummary 与 titleBar 可考虑合并到同一行（标题左、状态徽标右），省一整行 + 一个 gap。
3. **待主 Agent 拍板**：是否需要 fixer 实测几档 GuiScale 下 viewport 净高，确定一个最小可用基线。真机测量必交用户跑。

---

### S3【必修】actionBar 三按钮未左右分区，主次/危险操作挨在一起

**问题描述**：设计方案 §7.1 明确要求 actionBar 左右分区（恢复默认在左、取消+保存在右），当前实现是三按钮顺序左对齐挤在一起，无 spacer、无 MainAxisAlign。

**证据**：
- 三按钮顺序 append，row gap=10，无分区：ConfigScreen.java（`mountButton` 依次挂"恢复默认"/"取消更改"/"保存"）。
- 按钮固定宽 110：ConfigTheme.java。三按钮 + 2 gap = 350px，靠左。
- 设计要求左右分区：`modern-config-ui-design.md` §7.1（第 242-252 行）。

**真机视觉推断**：宽页面下三个 110px 按钮挤在左下角，右侧大片空白，重心失衡（"很空"的来源之一）。更要紧的是"恢复默认"（低频、破坏性）紧贴"保存"（高频、主操作），**误触风险高**——用户想点保存却点到旁边的恢复默认会丢改动。主按钮"保存"虽已用 PRIMARY variant 上色（:457），但位置上没和危险操作拉开。

**应该是什么**：左右分区。恢复默认置最左（弱化），取消+保存置最右，保存在最右末位（主操作落在视线终点）。

**修复指引（给 fixer）**：`createActionBar`（:449）在"恢复默认"和"取消"之间插一个 `fillParentWidth` 的 spacer 空节点撑开；若 `SceneNode` 支持 `MainAxisAlign.SPACE_BETWEEN` 则用对齐属性。`SceneSegmented.java` import 了 `MainAxisAlign`，说明枚举存在，fixer 需确认是否有 `SPACE_BETWEEN` 成员。

---

### S4【必修】save 反馈与 dirty/error 徽标挤在同一行，保存失败长文案空间不足

**问题描述**：save 反馈文本与两个状态徽标共用 statusSummary 这一行（固定高 34px），反馈在最右且无 flexGrow。保存失败的长文案（"保存失败：" + 原因）在剩余空间里可能溢出或被裁。

**证据**：
- statusSummary 是 ROW，gap=10，依次 append：dirty 徽标（ConfigScreen.java）、error 徽标（:260）、feedback 文本（:287）。
- feedback 文本节点无宽度约束、无 flexGrow（:271）。
- 失败文案可能很长："保存失败：" + `lastSaveOutcome.errorMessage()`（:479），IO 错误信息可能是完整文件路径/异常串。
- 设计 §7.3 给的位置是"actionBar 上方**或** statusSummary 区"（`modern-config-ui-design.md` 第 262 行）——实现选了 statusSummary 同行，是设计允许的选项之一，但没料到长文案挤行。

**真机视觉推断**：保存成功显示"已保存"短文案没问题；保存失败时长文案和两个徽标抢一行 34px 宽度，文字可能被右边界裁断，用户看不全失败原因。这是功能性的视觉缺陷（反馈看不全等于没反馈）。

**应该是什么**：save 反馈独立成行（在 actionBar 上方单独一条），或让 feedback 节点 `flexGrow=1` 占满徽标右侧全部剩余宽并允许换行。建议独立成行更稳。

**修复指引（给 fixer）**：`createStatusSummary`（:243）把 feedback 拆出，单独建一个 root 级固定行（在 scrollContainer 与 actionBar 之间），仅在 `saveFeedbackSignal` 非 none 时通过 `rt.show` 挂载（守 I7，无反馈时不占高）。

---

## 3. 中等问题清单（非阻断）

### M1【中】NUMBER slider 无数值读数，用户拖动不知精确值

**问题描述**：设计 §5 明确建议"slider 旁加当前数值读数（readout），否则用户不知精确值"。当前 `renderSlider` 没有任何读数文本。

**证据**：`NumberFieldRenderer.renderSlider`（:53-66）只 build 了 slider，无 readout 节点。设计要求见 `modern-config-ui-design.md` §5 NUMBER 行（第 197 行）。

**真机视觉推断**：用户拖动滑块时只能看到滑块位置，不知道当前是 7 还是 8（step=1 整数量化，:61）。配置项往往需要精确值，这是可用性缺口。

**应该是什么**：slider 右侧加一个 bind 到 numValue 的读数文本（如 "7"）。需 fixer 确认 `SceneSlider` 是否自带读数；若无，在 FormFieldShell 控件槽用 ROW 包 slider + 读数文本。**待主 Agent 拍板**：读数是否本轮补，还是留 P2。

### M2【中】滚动条 4px 过细 + 已知滚轮穿透问题未修

**问题描述**：滚动条宽仅 4px 且纯显示（不可拖拽、滚轮在其上无效），真机几乎不可见、不可操作。

**证据**：
- 默认宽 4px：SceneScrollbar.java（`DEFAULT_BAR_WIDTH`）。
- column 与 thumb 均 `hitTestable=false`：:144、:152。
- 已知缺陷 TODO 明确："鼠标悬在 scrollbar 4px 列上滚轮时……在滚动条上滚不动内容"：:155-163。
- gap=0，滚动条紧贴 viewport：ConfigScreen.java。

**真机视觉推断**：4px 宽的细蓝线（`DEFAULT_THUMB_COLOR=ACCENT`，:229）在深底上勉强可见，但极易被忽略；用户无法拖动它，鼠标移到上面滚轮还失效。属于"看起来像没有滚动条"。

**应该是什么**：宽度提到 6–8px（设计 §未规定，SceneScrollbar 注释建议 2–6，偏保守）。穿透/拖拽属控件库改动，超出本设计范围，**待主 Agent 拍板**是否交 fixer 给 SceneScrollbar 补 SCROLL 转发 + 拖拽 handle（TODO 已列 A/B/C 三方案）。

### M3【中】scrollbar 紧贴卡片右边缘（gap=0），视觉上压在卡片上

**问题描述**：scrollContainer 是 ROW、gap=0，viewport 和 scrollbar 列零间距，滚动条贴着 viewport 内卡片右侧。

**证据**：`createScrollContainer` gap=0（ConfigScreen.java）；viewport padding=14（:355）但 padding 是 viewport 内部，scrollbar 在 viewport 外侧紧贴。

**真机视觉推断**：滚动条与 viewport 边缘零间隙，加上 viewport 有圆角 10（:358），4px 直角滚动条贴在圆角容器右侧，视觉上略突兀。

**应该是什么**：scrollContainer gap 给 2–4px，让滚动条与 viewport 留一道缝。

### M4【中】徽标无固定高，胶囊形状依赖文本高，radius=999 在矮节点上效果弱

**问题描述**：徽标 `cornerRadius=999`（全圆角胶囊语义）但节点无固定高、仅 padding=8，实际高度 = 文本高(16) + padding，胶囊弧度取决于内容高。

**证据**：badge `padding=8`、`cornerRadius=999`、无 preferredHeight：ConfigScreen.java。

**真机视觉推断**：高度约 16+16=32px，radius=999 会被 clamp 到高度的一半，胶囊形状能成立，问题不大；但 dirty 态徽标用 borderColor=DIRTY_COLOR + 文本色 DIRTY_COLOR + 底色 READOUT_BG（深），蓝字蓝边深底**对比偏低**（:534-537）。

**应该是什么**：徽标文本色与底色对比拉开（如保持文本浅色、仅边框/底色用状态色），或给徽标设固定高统一胶囊高度。

---

## 4. 小问题清单（打磨级）

### m1【小·已关闭】error 空文本节点常驻问题已修复

**状态**：已由 `FormFieldShell.java` 的 `rt.show` 条件渲染修复（空 error 节点在 error 消息为空时不挂载），此问题已关闭。

**原问题描述**：早期实现每个字段卡片底部无条件 append 一个初始为空串的 error 文本节点，常驻树中。若空串仍按行高占位，每张卡片底部可能恒有一条空行高的空白。

**证据**：`FormFieldShell.java` 当前通过 `rt.show` 按 error 文案非空条件挂载 error 文本节点；不再无条件 append 空 error 节点。`SceneNode` 高度取 `Math.max(textHeight, preferredHeight)`（`读源码` SceneNode.java）。

### m2【小】titleBar 显示技术化 "modId: xxx"，应是人类可读标题

**问题描述**：titleBar 副行直接显示 "modId: " + schema.modId()（ConfigScreen.java），技术化文案面向最终用户不友好。

**证据**：ConfigScreen.java。设计 §8 已提"建议改为 schema 提供的人类可读标题"，并标"待拍板是否给 ConfigSchema 加 title 字段"（`modern-config-ui-design.md` 第 319 行）。属设计已知占位，非偏离。

### m3【小】viewport 宽度被 scrollbar 占 4px——实测影响极小，非阻断

**问题描述**：viewport flexGrow=1 占剩余宽，scrollbar 固定 4px，viewport 实际宽 = 容器宽 − 4。

**证据**：ConfigScreen.java（viewport flexGrow=1）、SceneScrollbar.java（column 固定 4px）。

**判断**：4px 占用对字段卡片布局影响可忽略（卡片宽度自适应），**非问题**。列出仅为回应审视清单的明确点名。

---

## 5. 设计方案对照差距（§1–§10 逐节）

| 设计节 | 要点 | 落地情况 | 证据 |
|---|---|---|---|
| §1 不变量映射 | I1/I3/I4/I7/I11 守 | ✅ 落地 | activeSectionSignal 受控（ConfigScreen.java）、rt.show 懒挂（:404）、bind 分级（FormFieldShell） |
| §2 五区骨架 | titleBar/status/nav/viewport/action | ✅ 落地 | ConfigScreen.java |
| §3.0 activeSectionSignal | 受控单 section + N 个 rt.show | ✅ 落地 | :152、:400-409 |
| §3.1 ≤5 横向 Segmented | 横向页签导航 | ✅ 落地，**优于设计** | createTabNav（:308）；设计 §3.1 担心固定段宽 72 截断长标题，实现已改段宽自适应（SceneSegmented.java），**解决了设计遗留风险** |
| §3.2 >5 左侧侧栏 | SceneNavList 纵向 | ✅ 落地 | createSidebarNav（:329），用 SceneNavList |
| §3.3 嵌套预留 | Breadcrumb + navPath | ⏸ 未落地（P2 预留，符合设计意图） | 设计明确 P2/P3 不实现 |
| §4 卡片三态 | 边框/dot 三态 + dot 优先级修正 | ✅ 落地 | FormFieldShell（边框、dot error 优先已修正，对应设计 §4.2 给 fixer 的修正） |
| §4.2 dirty 左色条 | 2px 蓝条增强 | ⏸ 未落地 | 设计标"【占位】本轮可不做"——**非偏离**，是设计指定的占位 |
| §5 STRING placeholder 去重 | placeholder 留空 | ✅ 落地 | StringFieldRenderer.java |
| §5 BOOLEAN label 去重 | toggle label 空串 | ✅ 落地 | BooleanFieldRenderer.java |
| §5 NUMBER slider 读数 | slider 旁加数值读数 | ❌ **偏离/未落地** | NumberFieldRenderer.java 无 readout（见 M1） |
| §5 CHOICE 阈值 4 | ≤4 Segmented >4 Select | ✅ 落地 | ChoiceFieldRenderer.java |
| §6 状态栏计数 | dirtyCount/errorCount 徽标 | ✅ 落地 | ConfigScreen.java，已接 dirtyCountSignal/errorCountSignal |
| §7.1 actionBar 左右分区 | 恢复默认左/取消+保存右 | ❌ **未落地** | 三按钮顺序左排（:451-458），见 S3 |
| §7.1 主按钮高亮 | 保存用 PRIMARY variant | ✅ 落地 | mountButton primary=true（:457），对应已拍板补 SceneButton variant |
| §7.3 save 反馈 | 新增 saveFeedbackSignal | ✅ 落地（位置欠佳） | createStatusSummary（:270-287），但挤在状态行（见 S4） |
| §8 titleBar ROW+图标+搜索 | ROW 主结构 + 图标 + 搜索槽 | ⏸ 未落地（仍 COLUMN 两行文本） | createTitleBar（:227-236）仍 COLUMN；图标/搜索是 P2 占位，**非偏离** |
| §9 P2 扩展 | 复杂字段/搜索/嵌套 | ⏸ 未落地（P2，符合设计） | — |
| **字号体系** | — | ❌ **设计盲区** | 设计全文未规定字号层级（见 S1） |

**落地质量小结**：核心骨架、信号、三态、导航、计数、主按钮全部按设计落地，质量好；段宽自适应甚至优于设计。**两处实质偏离**：§5 NUMBER 读数缺失（M1）、§7.1 actionBar 分区缺失（S3）。**一处设计盲区**：字号体系（S1）。其余未落地项都是设计明确标注的 P2 占位，非偏离。

---

## 6. 整体视觉规范评估（颜色/字号/间距/层级体系）

**颜色体系：成体系 ✅**
- config 层有完整语义色板：ROOT_BG/VIEWPORT_BG/CARD_BG 三档深蓝背景（ConfigTheme.java）、CARD_BORDER 三态（:38-42）、TITLE/TEXT/MUTED/ERROR/OK/DIRTY 文本色（:45-55）。
- 状态色语义清晰：dirty=蓝(0xFF60A5FA)、error=红(0xFFF87171)、ok=绿(0xFF34D399)，与 scene 控件层 `SceneChromeTokens.ACCENT` 蓝系协调（SceneChromeTokens.java）。
- **唯一瑕疵**：config 自定义色板（ConfigTheme）与控件库 `SceneChromeTokens` 是两套独立色值，控件（toggle/slider/segmented）走 SceneStateColors→ChromeTokens 的 Slate/Blue 系，卡片外壳走 ConfigTheme 的深蓝系。两套蓝不完全同值（ACCENT=0xFF3B82F6 vs DIRTY=0xFF60A5FA），真机可能有轻微色差。属可接受范围，**非阻断**。

**字号体系：不成体系 ❌（见 S1）**
- 零字号分级，全 16px。这是视觉规范最大的缺失维度。

**间距体系：基本成体系 ✅**
- 复用 SceneChromeTokens 的 PAD/GAP/RADIUS 档位（ConfigTheme.java 委托）。
- 卡片 padding=PAD_LG(10)、gap=GAP_MD(8)、radius=RADIUS_LG(6)，档位一致。
- **瑕疵**：root padding=20、gap=12 是硬编码裸数字（ConfigScreen.java），没走 token 档位，与卡片间距体系脱节。建议归入 ConfigTheme 常量。

**层级体系：弱 ⚠️**
- 颜色有层级（TITLE_COLOR 比 MUTED_COLOR 亮），但**没有字号层级 + 没有粗细层级**（SceneNode 无 bold 概念，仅字号），导致层级仅靠颜色明暗单一维度承载，区分度不足。补字号后层级体系才完整。

---

## 7. 真机视觉推断（用户打开页面看到什么）

> 以下为基于代码逻辑的视觉推理，**未真机验证**，真机实测需交用户跑。

**第一眼看到**：顶部一行偏暗的标题"配置编辑器"+下面一行更小但同字号的灰字"modId: xxx"；紧接一行两个胶囊徽标（"N 项未保存""校验通过"）；再一行横向页签（section 名）；中间一块深色圆角区域（viewport）里堆着若干深蓝边框卡片，每张卡片一个圆点+标题+控件；底部一行三个等宽按钮挤在左侧。

**视觉重心**：当前重心模糊——因为全页同字号，没有任何元素在尺寸上跳出来。理论上重心**应该**在 viewport 的字段区（用户来这里是改配置的）和右下角的"保存"主按钮。实际上 PRIMARY variant 的蓝色保存按钮是唯一有颜色跳出的元素，所以**唯一的视觉锚点是保存按钮**——但它被挤在左下角（S3），锚点位置也不理想。

**"挤"的地方**：
- 顶部——titleBar+statusSummary+navBar 三行横条堆叠（44+34+36+gap），上半屏全是 chrome，真正内容区被压低（S2）。
- viewport——小 GuiScale 下可能只露半张卡片（S2）。
- statusSummary 行——保存失败时长文案和徽标抢空间（S4）。

**"空"的地方**：
- actionBar 右侧——三按钮靠左，右侧大片留白（S3/M4）。

**"丑/平"的地方**：
- 全页字号无层级（S1）——这是最致命的"平"，让整个页面像未排版的纯文本表单，而非"现代化"配置页。
- 4px 滚动条几乎不可见（M2）。

**整体观感判定**：架构正确但"未排版"。就像一篇内容齐全、但全文同一字号、没有标题分级的文档——信息都在，但读起来累、看起来糙。

---

## 8. 修复优先级建议

**必修（上真机前）**：
1. **S1 字号层级** —— 重做级，影响全页观感，最高优先。在 ConfigTheme 建字号 token，各 text 节点补 setFontSize。
2. **S2 固定高压缩** —— 压 titleBar/status/action 高度 + root padding/gap，必要时 title 与 status 合并一行。真机量净高交用户。
3. **S3 actionBar 左右分区** —— 恢复默认左、保存最右末位，spacer 或 MainAxisAlign 撑开，降误触。
4. **S4 save 反馈独立成行** —— 拆出 statusSummary，用 rt.show 按需挂载，避免长文案挤裁。

**应修（可同批或紧随）**：
5. M1 NUMBER slider 读数 —— 补 readout（设计明确要求，属偏离回填）。
6. M2 滚动条加宽到 6–8px —— 低成本可见性提升；拖拽/穿透属控件库改动待拍板。
7. M3 scrollContainer gap 给 2–4px。

**可留（打磨）**：
8. M4 徽标对比度、m2 modId 人类可读标题（需 ConfigSchema 加 title，待拍板）、m1 error 空节点（先确认空串测量）。

**重做**：无需推翻骨架。所有问题都是视觉规范/布局参数层面，骨架与信号架构保留。

**待主 Agent 拍板清单**：
- S2：是否交 fixer 真机实测各 GuiScale 档 viewport 净高（真机测试交用户）。
- M1：NUMBER slider 读数本轮补还是留 P2。
- M2：SceneScrollbar 加宽 + 是否补滚轮转发/拖拽（控件库改动）。
- m2：是否给 ConfigSchema 加人类可读 title 字段。
- S1 字号 token 的具体梯度数值需主 Agent / fixer 确认与真机字号渲染匹配。

**给 fixer 的接线指引**：
- 字号 token：`ConfigTheme.java` 新增 FONT_* 常量 → `ConfigScreen.text()`（:548）与 `FormFieldShell.text()` 加字号参数重载 → 调用处（ConfigScreen.java、FormFieldShell）传对应字号。
- 固定高：`ConfigTheme.java` 改 TITLE_BAR_HEIGHT/STATUS_HEIGHT/ACTION_BAR_HEIGHT；`ConfigScreen.java` root padding/gap。
- actionBar 分区：`ConfigScreen.createActionBar()`（:449）插 spacer 或设 MainAxisAlign（确认 `MainAxisAlign.SPACE_BETWEEN` 存在）。
- save 反馈拆行：`ConfigScreen.createStatusSummary()`（:243）拆出 feedback，新建 root 级 rt.show 行挂在 scrollContainer 与 actionBar 之间。
- NUMBER 读数：`NumberFieldRenderer.renderSlider()`（:53）控件槽用 ROW 包 slider + 读数文本，或确认 SceneSlider 自带读数。
