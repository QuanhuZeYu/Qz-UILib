# Scene 控件 Chrome 统一配色规范

> 适用范围：`club.heiqi.uilib.ui.scene.control.Scene*` 新栈控件层的 chrome（背景/边框/文本/选中强调/圆角/间距）。
> 本文档只定义设计 token 与各控件映射，不含 Java 实现。实现由 fixer 按本文档落地。
> 色值一律 `0xFF` 开头 ARGB，可直接传入现有 setter（`setBackgroundColor / setBorderColor / setTextColor`）。

---

## 1. 设计目标

**一句话**：以「深石板蓝（Slate）打底 + 高饱和天蓝（Sky/Blue）做强调」的单一冷色体系，替代当前深灰系与石板蓝系并存的混乱，让 scene 控件在 MC 像素风里既不违和、又通过清晰的层次与状态反馈凸显现代 UI 库的精致感。

**为什么选这套**：
- MC 1.7.10 原版 GUI 是深色半透明面板 + 浅色文字的冷灰基调。纯深灰（0xFF3A3A3A）太接近原版、缺乏识别度；而现代 UI 库（Tailwind/Radix/shadcn）普遍用 **Slate 中性冷灰蓝**做底、**单一品牌强调色**做状态高亮。Slate 色阶天然带一点蓝，比纯灰更「数码、现代」，又因为是低饱和冷色，不会和 MC 像素世界打架。
- 项目里最新的 `SceneDataTable` 已经自发往 Slate + 亮蓝（`0xFF0F172A` / `0xFF60A5FA`）方向走，本规范把这条既成事实收口为全控件统一标准，而不是另起炉灶。
- **如何体现现代感**：① 统一强调色（不再深灰系一个蓝、石板系另一个蓝）；② 四态背景有明确递进的亮度层次（hover 提亮、pressed 压暗），状态反馈清晰；③ 圆角分档而非一刀切 4px；④ 文本三档对比度拉开，信息层级一眼可辨。这些正是「现代化 UI 库」相对 MC 原版「平板一块色」的核心区分点。

---

## 2. 统一 Token 体系

> 命名建议：新建 `SceneChromeTokens` 常量类（见第 4 节）承载。下表「Token 名」即建议的 `public static final int` 常量名。
> 色阶参考 Tailwind Slate / Sky / Blue，但全部按 MC 像素 UI 需要做了取舍（无渐变、无半透明阴影、对比度拉满）。

### 2.1 背景四态（中性 Slate 系）

| Token 名 | 色值 | 用途说明 |
|---|---|---|
| `BG_DEFAULT` | `0xFF334155` | 控件默认态背景（Slate-700）。标准交互控件填充底色。 |
| `BG_HOVER` | `0xFF475569` | 鼠标悬停态背景（Slate-600，提亮一档）。 |
| `BG_PRESSED` | `0xFF1E293B` | 按下态背景（Slate-800，压暗一档，制造「按下去」的物理反馈）。 |
| `BG_DISABLED` | `0xFF1F2937` | 禁用态背景（冷灰，低对比、视觉「沉下去」）。 |

> 设计取舍：默认态用 Slate-700 而非更深的 800，是为了让 hover/pressed 两个方向都有亮度空间（hover 往亮走、pressed 往暗走），保证三态都能被肉眼区分。

### 2.2 选中/聚焦强调色（Sky/Blue 系，单一品牌色）

| Token 名 | 色值 | 用途说明 |
|---|---|---|
| `ACCENT` | `0xFF3B82F6` | 选中/聚焦/激活态主色（Blue-500）。checkbox 勾选框、radio 选中、tab active、segment 选中、select 选中项的填充色。统一的「品牌蓝」。 |
| `ACCENT_HOVER` | `0xFF60A5FA` | 选中态再悬停（Blue-400，提亮）。 |
| `ACCENT_PRESSED` | `0xFF2563EB` | 选中态按下（Blue-600，压暗）。 |

> 设计取舍：强调色从旧的 `0xFF4A90D9` 微调为 Tailwind Blue-500 `0xFF3B82F6`，色相更纯正、和 Slate 底的冷色调更协调，也是现代 UI 库最常见的「默认蓝」。Slider 的进度填充用更亮的 Sky 系（见 2.6 补充），保持「数据/进度」与「选中/激活」两类语义的细微区分。

### 2.3 边框

| Token 名 | 色值 | 用途说明 |
|---|---|---|
| `BORDER_DEFAULT` | `0xFF475569` | 默认边框（Slate-600）。比背景亮一档，勾出控件轮廓但不抢眼。 |
| `BORDER_FOCUS` | `0xFF60A5FA` | 聚焦/激活边框（Blue-400）。输入框聚焦、选中项描边的高亮蓝。 |
| `BORDER_DISABLED` | `0xFF334155` | 禁用边框（Slate-700，低对比融入背景）。 |

### 2.4 文本三档

| Token 名 | 色值 | 用途说明 |
|---|---|---|
| `TEXT_PRIMARY` | `0xFFE2E8F0` | 正常文本（Slate-200，近白带冷调）。控件主标签、输入内容、选中项文字。 |
| `TEXT_SECONDARY` | `0xFF94A3B8` | 次要文本/placeholder（Slate-400）。占位符、未选中段文字、辅助说明。 |
| `TEXT_DISABLED` | `0xFF64748B` | 禁用文本（Slate-500，明显变暗但仍可读）。 |
| `TEXT_ON_ACCENT` | `0xFFFFFFFF` | 强调底上的文本（纯白）。tab active、segment 选中、select 选中项等 ACCENT 背景上的文字，用纯白拉满对比。 |

> 设计取舍：放弃旧深灰系的纯白 `0xFFFFFFFF` 作为通用正文色。纯白在 Slate 底上偏「刺眼」，Slate-200 `0xFFE2E8F0` 带一点冷调，更柔和也更现代。但在 ACCENT 蓝底上仍保留纯白（`TEXT_ON_ACCENT`）保证可读性。

### 2.5 圆角档（像素）

| Token 名 | 值 | 用途说明 |
|---|---|---|
| `RADIUS_SM` | `3` | 小控件：checkbox box、radio circle、小标记。 |
| `RADIUS_MD` | `4` | 标准控件：button、textinput、select trigger、tab、segment、列表项。 |
| `RADIUS_LG` | `6` | 大容器：select listbox、tab 内容区、卡片面板。 |
| `RADIUS_PILL` | `999` | 全圆角胶囊：toggle track、toggle thumb、slider track/fill/thumb。 |

> 设计取舍：MC 像素 UI 的圆角是「切角近似」，档位别太多。3/4/6 三档拉出小/中/大层次，999 单独做胶囊。注意 radio circle 当前用的也是 `setCornerRadius(999)` 做圆——若 primitive 用正方形+大圆角模拟圆，radio circle 应归 `RADIUS_PILL`；若是小方块，则 `RADIUS_SM`。**此点待确认 primitive 实现，见接线指引。**

### 2.6 padding / gap 档（像素）

| Token 名 | 值 | 用途说明 |
|---|---|---|
| `PAD_SM` | `2` | 紧凑内边距：列表项、表格单元、小标记。 |
| `PAD_MD` | `6` | 标准内边距：textinput、button、select trigger。 |
| `PAD_LG` | `10` | 宽松内边距：tab 段、大按钮、面板。 |
| `GAP_SM` | `4` | 小间距：图标与文字、勾选框与标签。 |
| `GAP_MD` | `8` | 标准间距：toggle track 与 label、行内控件组。 |

> 补充强调色（仅 Slider 进度语义专用，归入 token 类）：
> | `ACCENT_PROGRESS` | `0xFF38BDF8` | Slider fill 进度色（Sky-400）。比 ACCENT 更亮更「数据感」，区分「进度量」与「选中态」。 |
> | `THUMB_DEFAULT` | `0xFFE0F2FE` | Slider/Toggle thumb 默认色（Sky-100，近白冷调）。 |
> | `THUMB_HOVER` | `0xFFFFFFFF` | thumb 悬停纯白。 |
> | `THUMB_PRESSED` | `0xFFBAE6FD` | thumb 按下（Sky-200）。 |

---

## 3. 各控件配色映射

> 下表每格给「Token 名（色值）」。fixer 把各 wrapper 里现有的 `private static final int XXX = 0x...;` 替换为引用 `SceneChromeTokens.TOKEN`，状态解析逻辑（`resolveXxx`）不变，只换常量来源。

### 3.1 Button（`SceneButton.java`）

| 状态 | 背景 | 文本 | 边框 |
|---|---|---|---|
| default | `BG_DEFAULT` (0xFF334155) | `TEXT_PRIMARY` (0xFFE2E8F0) | `BORDER_DEFAULT` (0xFF475569) |
| hover | `BG_HOVER` (0xFF475569) | `TEXT_PRIMARY` | `BORDER_DEFAULT` |
| pressed | `BG_PRESSED` (0xFF1E293B) | `TEXT_PRIMARY` | `BORDER_DEFAULT` |
| disabled | `BG_DISABLED` (0xFF1F2937) | `TEXT_DISABLED` (0xFF64748B) | `BORDER_DISABLED` (0xFF334155) |

圆角 `RADIUS_MD`(4)，内边距 `PAD_MD`(6)。
> 注：旧 `SceneButton` 用 `CAPSULE_RADIUS`(999) 做胶囊按钮。**是否保留胶囊形待主 Agent 拍板**——若保留品牌胶囊按钮风格用 `RADIUS_PILL`，若统一为标准矩形圆角用 `RADIUS_MD`。推荐统一 `RADIUS_MD`，胶囊感留给 toggle/slider。

### 3.2 Checkbox（`SceneCheckbox.java`）

| 部位/状态 | Token |
|---|---|
| box 未选中 default | `BG_DEFAULT` (0xFF334155) |
| box 未选中 hover | `BG_HOVER` (0xFF475569) |
| box 未选中 pressed | `BG_PRESSED` (0xFF1E293B) |
| box 选中 default | `ACCENT` (0xFF3B82F6) |
| box 选中 hover | `ACCENT_HOVER` (0xFF60A5FA) |
| box 选中 pressed | `ACCENT_PRESSED` (0xFF2563EB) |
| box disabled | `BG_DISABLED` (0xFF1F2937) |
| box 边框 | `BORDER_DEFAULT` (0xFF475569) |
| 勾号色（check mark） | `TEXT_ON_ACCENT` (0xFFFFFFFF) |
| 标签文本 enabled | `TEXT_PRIMARY` (0xFFE2E8F0) |
| 标签文本 disabled | `TEXT_DISABLED` (0xFF64748B) |

box 圆角 `RADIUS_SM`(3)。

### 3.3 Toggle（`SceneToggle.java`）

| 部位/状态 | Token |
|---|---|
| track off default | `BG_DEFAULT` (0xFF334155) |
| track off hover | `BG_HOVER` (0xFF475569) |
| track off pressed | `BG_PRESSED` (0xFF1E293B) |
| track on default | `ACCENT` (0xFF3B82F6) |
| track on hover | `ACCENT_HOVER` (0xFF60A5FA) |
| track on pressed | `ACCENT_PRESSED` (0xFF2563EB) |
| track disabled | `BG_DISABLED` (0xFF1F2937) |
| track 边框 | `BORDER_DEFAULT` (0xFF475569) |
| thumb enabled | `THUMB_DEFAULT` (0xFFE0F2FE) |
| thumb disabled | `TEXT_DISABLED` (0xFF64748B) |
| 标签文本 enabled / disabled | `TEXT_PRIMARY` / `TEXT_DISABLED` |

track/thumb 圆角 `RADIUS_PILL`(999)，内边距 `PAD_SM` 附近（保持现有 3px 视觉，可保留或归 PAD_SM=2 微调）。

### 3.4 RadioGroup（`SceneRadioGroup.java`）

| 部位/状态 | Token |
|---|---|
| circle 未选中 default | `BG_DEFAULT` (0xFF334155) |
| circle 未选中 hover | `BG_HOVER` (0xFF475569) |
| circle 未选中 pressed | `BG_PRESSED` (0xFF1E293B) |
| circle 选中 default | `ACCENT` (0xFF3B82F6) |
| circle 选中 hover | `ACCENT_HOVER` (0xFF60A5FA) |
| circle 选中 pressed | `ACCENT_PRESSED` (0xFF2563EB) |
| circle disabled | `BG_DISABLED` (0xFF1F2937) |
| circle 边框 | `BORDER_DEFAULT` (0xFF475569) |
| 内圆点 dot | `TEXT_ON_ACCENT` (0xFFFFFFFF) |
| option 文本 enabled / disabled | `TEXT_PRIMARY` / `TEXT_DISABLED` |

circle 圆角 `RADIUS_PILL`(999)，option 行内边距 `PAD_SM`、gap `GAP_SM`。

### 3.5 Segmented（`SceneSegmented.java`）

| 部位/状态 | Token |
|---|---|
| segment 未选中 default | `BG_DEFAULT` (0xFF334155) |
| segment 未选中 hover | `BG_HOVER` (0xFF475569) |
| segment 未选中 pressed | `BG_PRESSED` (0xFF1E293B) |
| segment 选中 default | `ACCENT` (0xFF3B82F6) |
| segment 选中 pressed | `ACCENT_PRESSED` (0xFF2563EB) |
| segment disabled | `BG_DISABLED` (0xFF1F2937) |
| 选中文本 | `TEXT_ON_ACCENT` (0xFFFFFFFF) |
| 未选中文本 | `TEXT_SECONDARY` (0xFF94A3B8) |

segment 圆角 `RADIUS_MD`(4)，内边距 `PAD_LG`（横向）。

### 3.6 Tab（`SceneTab.java`）

| 部位/状态 | Token |
|---|---|
| tab inactive default | `BG_DEFAULT` (0xFF334155) |
| tab inactive hover | `BG_HOVER` (0xFF475569) |
| tab inactive pressed | `BG_PRESSED` (0xFF1E293B) |
| tab active default | `ACCENT` (0xFF3B82F6) |
| tab active pressed | `ACCENT_PRESSED` (0xFF2563EB) |
| tab disabled | `BG_DISABLED` (0xFF1F2937) |
| active 文本 | `TEXT_ON_ACCENT` (0xFFFFFFFF) |
| inactive 文本 | `TEXT_SECONDARY` (0xFF94A3B8) |
| 内容区背景 | `BG_PRESSED` (0xFF1E293B) |

tab 圆角 `RADIUS_MD`(4)，内容区圆角 `RADIUS_LG`(6)。
> 内容区用 `BG_PRESSED`(0xFF1E293B) 做「比 tab 略深」的承托面，制造层次（见第 5 节）。

### 3.7 TextInput（`SceneTextInput.java`）

| 部位/状态 | Token |
|---|---|
| 背景 enabled | `BG_PRESSED` (0xFF1E293B) |
| 背景 disabled | `BG_DISABLED` (0xFF1F2937) |
| 边框 default | `BORDER_DEFAULT` (0xFF475569) |
| 边框 focused | `BORDER_FOCUS` (0xFF60A5FA) |
| 边框 disabled | `BORDER_DISABLED` (0xFF334155) |
| 文本 enabled | `TEXT_PRIMARY` (0xFFE2E8F0) |
| 文本 disabled | `TEXT_DISABLED` (0xFF64748B) |
| placeholder | `TEXT_SECONDARY` (0xFF94A3B8) |
| caret | `BORDER_FOCUS` (0xFF60A5FA) |

圆角 `RADIUS_MD`(4)，内边距 `PAD_MD`(6)。
> 设计取舍：输入框背景用 `BG_PRESSED`(0xFF1E293B) 这一更深档，是 UI 惯例——输入区应「凹下去」，比周围按钮/面板深，暗示「可填入」。caret 与 focus 边框统一用 `BORDER_FOCUS`，比旧的两个不同蓝更协调。
> 注：旧 placeholder 用 `0xFF64748B`（=TEXT_DISABLED），本规范改用更亮的 `TEXT_SECONDARY`(0xFF94A3B8) 区分「占位提示」与「禁用」两种语义。**此点是有意提升，待主 Agent 确认是否接受。**

### 3.8 Select（`SceneSelect.java`）

| 部位/状态 | Token |
|---|---|
| trigger default | `BG_DEFAULT` (0xFF334155) |
| trigger hover | `BG_HOVER` (0xFF475569) |
| trigger pressed | `BG_PRESSED` (0xFF1E293B) |
| trigger disabled | `BG_DISABLED` (0xFF1F2937) |
| trigger focus 边框 | `BORDER_FOCUS` (0xFF60A5FA) |
| listbox 背景 | `BG_PRESSED` (0xFF1E293B) |
| listbox 边框 | `BORDER_DEFAULT` (0xFF475569) |
| item default | 透明（继承 listbox 背景） |
| item hover | `BG_HOVER` (0xFF475569) |
| item highlighted（键盘高亮） | `BG_DEFAULT` (0xFF334155) |
| item selected | `ACCENT` (0xFF3B82F6) |
| 文本 enabled | `TEXT_PRIMARY` (0xFFE2E8F0) |
| selected 文本 | `TEXT_ON_ACCENT` (0xFFFFFFFF) |
| 文本 disabled | `TEXT_DISABLED` (0xFF64748B) |

trigger 圆角 `RADIUS_MD`(4)，listbox 圆角 `RADIUS_LG`(6)，item 内边距 `PAD_SM`/`PAD_MD`。
> highlighted（键盘导航高亮）与 hover（鼠标）刻意用不同档：highlighted 用 `BG_DEFAULT`、hover 用更亮的 `BG_HOVER`，避免键鼠两种高亮混淆。**若希望两者一致，待主 Agent 拍板统一为 `BG_HOVER`。**

### 3.9 Slider（`SceneSlider.java`）

| 部位/状态 | Token |
|---|---|
| track enabled | `BG_DEFAULT` (0xFF334155) |
| track disabled | `BG_DISABLED` (0xFF1F2937) |
| fill enabled | `ACCENT_PROGRESS` (0xFF38BDF8) |
| fill disabled | `BG_DISABLED` (0xFF1F2937) |
| thumb default | `THUMB_DEFAULT` (0xFFE0F2FE) |
| thumb hover | `THUMB_HOVER` (0xFFFFFFFF) |
| thumb pressed | `THUMB_PRESSED` (0xFFBAE6FD) |
| thumb disabled | `TEXT_DISABLED` (0xFF64748B) |

track/fill/thumb 圆角 `RADIUS_PILL`(999)。
> Slider 当前配色（Sky 系）已经最贴近本规范方向，基本只需把字面量换成 token 引用。fill 用 `ACCENT_PROGRESS`(Sky-400) 而非 `ACCENT`(Blue-500)，是为了把「进度量」和「选中态」做语义区分；**若希望 slider 也用统一 ACCENT，待主 Agent 拍板。**

---

## 4. 与现有 ScenePalette 的关系

**现状**：`ScenePalette`（`src/main/java/club/heiqi/uilib/ui/scene/paint/ScenePalette.java`）只有斑马纹两色 `ROW_BG_EVEN=0xFF1E293B` / `ROW_BG_ODD=0xFF243B53` 和 `rowBg(int)` 方法，定位是「数据行背景」的窄域调色。

**建议：新建 `SceneChromeTokens`，不要往 `ScenePalette` 塞。** 理由：
1. **职责分离**：`ScenePalette` 语义是「数据表斑马纹/行背景」；chrome token 语义是「交互控件外观」。两者关注点不同，混在一个类会让命名空间混乱。
2. **演进独立**：斑马纹将来可能跟数据密度走，chrome token 跟交互状态走，分开各自演进更清晰。
3. **轻量收口**：`SceneChromeTokens` 是个纯静态常量类（同 `ScenePalette` 的 `private` 构造 + `static final` 字段模式），不是主题引擎，符合「轻量常量收口、不引入主题引擎」的约束。

**建议落点**：`src/main/java/club/heiqi/uilib/ui/scene/paint/SceneChromeTokens.java`（与 `ScenePalette` 同包）。

**ScenePalette 是否对齐**：`ROW_BG_EVEN=0xFF1E293B` 恰好等于本规范 `BG_PRESSED`，`ROW_BG_ODD=0xFF243B53` 是其间的过渡蓝。两者已天然落在同一 Slate 冷色体系内，**无需改动 ScenePalette**，斑马纹与 chrome 视觉自洽。可选优化：`ScenePalette` 内部改为引用 `SceneChromeTokens.BG_PRESSED`，消除重复字面量——**此为可选项，待主 Agent 拍板，非必须。**

`SceneChromeTokens` 骨架（仅结构示意，fixer 据此填全 token，色值见第 2 节）：

```java
package club.heiqi.uilib.ui.scene.paint;

/**
 * SceneChromeTokens 集中维护 scene 交互控件 chrome（背景/边框/文本/强调/圆角/间距）统一配色 token。
 * 纯静态常量收口，非主题引擎。色值一律 0xFF ARGB，直接传 SceneNode setter。
 */
public final class SceneChromeTokens {

    // ===== 背景四态（Slate 中性系）=====
    /** 默认态背景（Slate-700）。 */
    public static final int BG_DEFAULT = 0xFF334155;
    /** 悬停态背景（Slate-600 提亮）。 */
    public static final int BG_HOVER = 0xFF475569;
    /** 按下态背景（Slate-800 压暗）。 */
    public static final int BG_PRESSED = 0xFF1E293B;
    /** 禁用态背景（冷灰沉底）。 */
    public static final int BG_DISABLED = 0xFF1F2937;

    // ===== 选中/聚焦强调色（Blue 品牌系）=====
    /** 选中/聚焦/激活主色（Blue-500）。 */
    public static final int ACCENT = 0xFF3B82F6;
    /** 选中态悬停（Blue-400）。 */
    public static final int ACCENT_HOVER = 0xFF60A5FA;
    /** 选中态按下（Blue-600）。 */
    public static final int ACCENT_PRESSED = 0xFF2563EB;
    /** Slider 进度填充（Sky-400，区分进度量与选中态）。 */
    public static final int ACCENT_PROGRESS = 0xFF38BDF8;

    // ===== 边框 =====
    public static final int BORDER_DEFAULT = 0xFF475569;
    public static final int BORDER_FOCUS = 0xFF60A5FA;
    public static final int BORDER_DISABLED = 0xFF334155;

    // ===== 文本 =====
    public static final int TEXT_PRIMARY = 0xFFE2E8F0;
    public static final int TEXT_SECONDARY = 0xFF94A3B8;
    public static final int TEXT_DISABLED = 0xFF64748B;
    public static final int TEXT_ON_ACCENT = 0xFFFFFFFF;

    // ===== thumb（toggle/slider 滑块）=====
    public static final int THUMB_DEFAULT = 0xFFE0F2FE;
    public static final int THUMB_HOVER = 0xFFFFFFFF;
    public static final int THUMB_PRESSED = 0xFFBAE6FD;

    // ===== 圆角（像素）=====
    public static final int RADIUS_SM = 3;
    public static final int RADIUS_MD = 4;
    public static final int RADIUS_LG = 6;
    public static final int RADIUS_PILL = 999;

    // ===== padding / gap（像素）=====
    public static final int PAD_SM = 2;
    public static final int PAD_MD = 6;
    public static final int PAD_LG = 10;
    public static final int GAP_SM = 4;
    public static final int GAP_MD = 8;

    private SceneChromeTokens() {
    }
}
```
> 来源：结构沿用已读的 `ScenePalette.java`（`private` 构造 + `static final` 字段）；色值为本规范设计值（参考 Tailwind Slate/Sky/Blue 色阶 + MC 像素需要取舍）。

---

## 5. 明暗层次体系

整套配色按「越深越靠后、越亮越靠前/越强调」的物理直觉分 5 层（由深到浅）：

| 层级 | 元素 | 色值 | 语义 |
|---|---|---|---|
| L0 最深（容器/凹陷面） | TextInput 背景、Select listbox、Tab 内容区、Slider track、表格 viewport | `0xFF1E293B`(BG_PRESSED) / 更深的 `0xFF0F172A` | 「承托面/可填入区」，视觉上凹下去 |
| L1 禁用沉底 | 各控件 disabled 背景 | `0xFF1F2937`(BG_DISABLED) | 「不可用」，对比度刻意压低 |
| L2 标准控件面 | Button/Checkbox/Toggle track off/Tab inactive/Segment 默认 | `0xFF334155`(BG_DEFAULT) | 可交互的「实心面」，比 L0 亮，浮在容器之上 |
| L3 悬停提亮 | 各控件 hover | `0xFF475569`(BG_HOVER) | 鼠标所指，主动提亮反馈 |
| L4 强调/激活（最跳） | 选中/聚焦/active 的 ACCENT 蓝、focus 边框、caret | `0xFF3B82F6` / `0xFF60A5FA` | 用高饱和蓝从冷灰底里「跳出来」，最高视觉优先级 |

**过渡逻辑**：
- 同一控件四态在 L1↔L2↔L3 之间用亮度递进（pressed 比 default 暗、hover 比 default 亮），制造「按下/抬起」的体积感。
- 选中态整体切到 L4 蓝色通道，与未选中的灰色通道形成**色相对比**（不只是明暗），这是「选没选中」一眼可辨的关键。
- 文本三档 `0xFFE2E8F0 → 0xFF94A3B8 → 0xFF64748B` 在灰阶上等距递减，信息层级清晰。

**最深 = Tab 内容区 / 输入区 / 表格 viewport（容器面）；最浅最跳 = 选中态 ACCENT 蓝。** 中间靠 Slate 三档背景递进过渡。

---

## 6. 与 MC 原版 GUI 的视觉协调

MC 1.7.10 原版 GUI 特征：深色半透明黑灰面板（约 `0xC0101010` 一类）+ 浅灰白文字（`0xFFFFFFFF` / `0xFFA0A0A0`）+ 凸起/凹陷的石质按钮纹理。

本配色与原版**共存不违和**的设计理由：
1. **同为冷暗基调**：本规范 Slate 系（0xFF1E293B~0xFF475569）整体明度、冷调和原版深灰面板同一区间，叠在原版半透明黑底上不会「亮瞎」或「色相打架」。Slate 比纯灰多一丝蓝，恰好成为「这是 Qz-UILib 控件、不是原版」的温和识别标记，但不夸张。
2. **文本沿用浅冷白**：`TEXT_PRIMARY`(0xFFE2E8F0) 与原版浅白文字同属高明度冷色，并排不突兀。
3. **强调蓝克制**：ACCENT 蓝只用在「选中/聚焦」这类需要明确反馈的小面积部位，不做大面积蓝底，避免盖过原版的中性基调。即便 UI 库面板叠在原版 inventory 旁，蓝色也只是「点睛」而非「刷墙」。
4. **无渐变/无投影/无半透明阴影**：全部纯色块 + 1px 实色边框 + 切角圆角，完全是像素 UI 的画法，和原版的硬边像素风同语言。没有任何需要 alpha 混合渐变或柔和阴影的地方（caret 的 `0x00000000` 仅用于「隐藏」而非视觉效果）。
5. **现代感来自层次而非材质**：不靠拟物石纹/高光去「现代」，而是靠清晰的明暗分层（第 5 节）和统一强调色——这让它既明显比原版「干净、有层次」，又不会因为引入塑料质感/玻璃拟态而和像素世界割裂。

> 唯一需注意：本规范控件**不透明**（0xFF），叠在原版半透明面板上时是实心块。这是有意的——实心块让控件边界清晰、可读性高，符合现代 UI「明确的卡片/控件边界」。若某些场景需要半透明融入原版面板，属于单独的「容器背景」议题，不在本 chrome 规范内，**待主 Agent 视需要另立。**

---

## 附：待主 Agent 拍板清单

1. **Button 形状**：保留旧胶囊形（`RADIUS_PILL`）还是统一标准圆角（`RADIUS_MD`，推荐）。
2. **placeholder 色**：是否接受从旧 `0xFF64748B` 提亮为 `TEXT_SECONDARY`(0xFF94A3B8)，区分占位与禁用语义（推荐接受）。
3. **Select item highlighted vs hover**：键盘高亮与鼠标 hover 是否用不同档（本规范默认不同，推荐保留区分）。
4. **Slider fill 色**：用专用 `ACCENT_PROGRESS`(Sky-400) 还是统一 `ACCENT`(Blue-500)（推荐专用，保留语义区分）。
5. **ScenePalette 是否内部改引 token**：消除 `0xFF1E293B` 重复字面量（可选优化，非必须）。
6. **radio circle 圆角档归属**：取决于 primitive 是「方块+大圆角模拟圆」还是「真圆」，确认后归 `RADIUS_PILL` 或 `RADIUS_SM`。

## 附：给 fixer 的接线指引清单

> 实现方式：在各 wrapper 顶部 `import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;`，把现有 `private static final int XXX = 0x...;` 的字面量替换为 `SceneChromeTokens.TOKEN`。**状态解析逻辑（`resolveXxxColor` 方法、`rt.bind(Invalidation.PAINT, ...)` 派生链）一律不动**，只换颜色来源。

| 控件文件 | 改动点（行号为当前字面量定义处，替换为对应 token） |
|---|---|
| `SceneButton.java` | L36 `BG_ENABLED`→`BG_DEFAULT`、L40 `BG_HOVER`→`BG_HOVER`、L44 `BG_PRESSED`→`BG_PRESSED`、L48 `BG_DISABLED`→`BG_DISABLED`、L53 `BORDER_COLOR`→`BORDER_DEFAULT`、L58 `TEXT_ENABLED`→`TEXT_PRIMARY`、L62 `TEXT_DISABLED`→`TEXT_DISABLED`；L118 胶囊圆角按拍板项 1 处理 |
| `SceneCheckbox.java` | L40-52 box 六态→`BG_*`/`ACCENT_*`、L52 `BOX_DISABLED`→`BG_DISABLED`、L55 `BORDER_COLOR`→`BORDER_DEFAULT`、L58/60 文本→`TEXT_PRIMARY`/`TEXT_DISABLED`；勾号色用 `TEXT_ON_ACCENT`；box 圆角→`RADIUS_SM` |
| `SceneToggle.java` | L43-55 track 七态→`BG_*`/`ACCENT_*`/`BG_DISABLED`、L58 `BORDER_COLOR`→`BORDER_DEFAULT`、L61 `THUMB_ENABLED`→`THUMB_DEFAULT`、L63 `THUMB_DISABLED`→`TEXT_DISABLED`、L66/68 文本→`TEXT_PRIMARY`/`TEXT_DISABLED`；L81 `CAPSULE_RADIUS`→`RADIUS_PILL`、L83 `GAP`→`GAP_MD` |
| `SceneRadioGroup.java` | L55-67 circle 七态→`BG_*`/`ACCENT_*`/`BG_DISABLED`、L70 `BORDER_COLOR`→`BORDER_DEFAULT`、L73 `DOT_COLOR`→`TEXT_ON_ACCENT`、L78/80 文本→`TEXT_PRIMARY`/`TEXT_DISABLED`；circle 圆角按拍板项 6 |
| `SceneSegmented.java` | L55-63 五态→`BG_DEFAULT`/`BG_PRESSED`/`ACCENT`/`ACCENT_PRESSED`/`BG_DISABLED`、L66 `TEXT_SELECTED`→`TEXT_ON_ACCENT`、L68 `TEXT_UNSELECTED`→`TEXT_SECONDARY` |
| `SceneTab.java` | L63-71 五态→`BG_DEFAULT`/`BG_PRESSED`/`ACCENT`/`ACCENT_PRESSED`/`BG_DISABLED`、L74 `TEXT_ACTIVE`→`TEXT_ON_ACCENT`、L76 `TEXT_INACTIVE`→`TEXT_SECONDARY`；tab 圆角→`RADIUS_MD`、内容区圆角→`RADIUS_LG`、内容区背景→`BG_PRESSED` |
| `SceneTextInput.java` | L42 `BG_ENABLED`→`BG_PRESSED`、L44 `BG_DISABLED`→`BG_DISABLED`、L46 `BORDER_ENABLED`→`BORDER_DEFAULT`、L48 `BORDER_FOCUSED`→`BORDER_FOCUS`、L50 `BORDER_DISABLED`→`BORDER_DISABLED`、L53 `TEXT_ENABLED`→`TEXT_PRIMARY`、L55 `TEXT_DISABLED`→`TEXT_DISABLED`、L57 `TEXT_PLACEHOLDER`→`TEXT_SECONDARY`（拍板项 2）、L60 `CARET_COLOR`→`BORDER_FOCUS`、L67 圆角→`RADIUS_MD`、L69 padding→`PAD_MD` |
| `SceneSelect.java` | L32-44 trigger 四态→`BG_*`、L48 `LISTBOX_BG`→`BG_PRESSED`、L56 `ITEM_BG_HOVER`→`BG_HOVER`、L60 `ITEM_BG_HIGHLIGHTED`→`BG_DEFAULT`（拍板项 3）、L64 `ITEM_BG_SELECTED`→`ACCENT`、L68 `TEXT_ENABLED`→`TEXT_PRIMARY`（selected 文本另用 `TEXT_ON_ACCENT`）、L72 `TEXT_DISABLED`→`TEXT_DISABLED`；listbox 边框→`BORDER_DEFAULT`、trigger 圆角→`RADIUS_MD`、listbox 圆角→`RADIUS_LG` |
| `SceneSlider.java` | L71 `TRACK_ENABLED`→`BG_DEFAULT`、L73 `TRACK_DISABLED`→`BG_DISABLED`、L76 `FILL_ENABLED`→`ACCENT_PROGRESS`（拍板项 4）、L78 `FILL_DISABLED`→`BG_DISABLED`、L81 `THUMB_ENABLED`→`THUMB_DEFAULT`、L83 `THUMB_HOVER`→`THUMB_HOVER`、L85 `THUMB_PRESSED`→`THUMB_PRESSED`、L87 `THUMB_DISABLED`→`TEXT_DISABLED`；圆角→`RADIUS_PILL` |

> 非 scene/control 范围（config 包、devtools demo、SceneDataTable/SceneSimpleList/SceneKeyValueMap/SceneObjectField 等复合控件）本轮不在统一范围内，含大量业务态色（删除红 `0xFF7F1D1D`、警告黄 `0xFFFBBF24` 等）。**建议下一轮单独处理「语义色 token（success/warning/danger）」，本规范先收口基础 chrome 九控件。待主 Agent 拍板是否纳入。**
> 测试文件（`src/test/.../Scene*Test.java`）中的期望色常量（如 `SceneButtonTest.BG_ENABLED=0xFF3A3A3A`）会随实现色值变化而失败，需 fixer 同步更新测试期望值为新 token 色值——此为实现连带项，由 fixer 在实现时一并处理。
