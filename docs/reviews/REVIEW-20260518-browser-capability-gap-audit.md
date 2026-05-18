# 浏览器常用能力差距审查

## 审查信息

- 审查日期：2026-05-18
- 审查主题：项目中浏览器常用能力的实际覆盖度与差距核实
- 审查视角：不依赖项目文档自述，通过源码逐项核实每项能力是否真正在布局、渲染、事件或样式链路中完整工作
- 审查范围：CSS 属性与布局、CSS 选择器与规则、事件系统、DOM 操作 API、表单控件、其他常见能力，共 65 项
- 核实方法：对每项能力，分别搜索样式声明（`UiStyleDeclaration`）、级联计算（`UiStyleResolver`）、布局引擎（`DocumentLayoutEngine`）、绘制引擎（`DocumentPaintEngine`）、事件分发（`HtmlLikeDocumentWidget`）和控件层（`Document*Control`）的实际代码，判断实现程度

---

## 审查结论

项目已经是一个真实可用的 HTML-like UI 渲染框架，核心能力（flex 布局、block 定位、常用 CSS 属性、三阶段事件、基础控件）已经过源码和测试的双重验证。但在 65 项被检查的浏览器常用能力中，**有 30 项完全没有实现、8 项声明与实现不一致（仅部分工作）**，与浏览器的完整能力差距仍然显著。

更值得注意的是，部分能力在 AI 记忆文档或使用文档中已列为"当前可用"，但源码核实发现实际仅完成了样式声明和级联计算链路，尚未接通到布局或渲染执行层，属于**文档比实现更乐观**的情形，需要特别标注边界。

---

## 一、完全没有实现的能力（30 项）

### CSS 布局与定位

| 能力 | 说明 | 关键证据 |
|------|------|----------|
| **CSS Grid 布局** | 无 `display:grid`，无任何 grid-template/column/row 实现 | `UiDisplay.java` 枚举中无 GRID 值；`DocumentLayoutEngine.java` 无 grid 分支 |
| **CSS transform** | 无 rotate/scale/translate/skew/matrix；`glTranslatef` 仅用于内部文本渲染缩放，非 CSS 属性 | `UiStyleDeclaration.java` 无 transform 字段；绘制引擎无变换矩阵链路 |
| **float 浮动布局** | 无 float:left/right，无环绕排列 | 全项目无 float 相关布局逻辑 |
| **position:sticky** | 无粘性定位 | `UiPosition.java` 枚举中无 STICKY 值 |
| **flex `order`** | 无法通过 order 改变 flex 子项视觉顺序 | `UiStyleDeclaration.java` 无 order 字段；布局引擎按 DOM 顺序固定排列 |

### CSS 样式与视觉

| 能力 | 说明 | 关键证据 |
|------|------|----------|
| **CSS calc()** | 长度值仅支持 AUTO/PX/PERCENT，无混合单位运算 | `UiStyleLength.java` 枚举仅三值；无 calc 表达式解析器 |
| **CSS gradient（渐变）** | 无 linear-gradient/radial-gradient 背景 | 全项目无渐变相关实现；背景仅支持纯色 |
| **text-shadow** | 无文本阴影（MC 原生 drop-shadow 仅是内部渲染细节，非 CSS text-shadow 属性） | `UiStyleDeclaration.java` 无 textShadow 字段 |
| **text-transform** | 无 uppercase/lowercase/capitalize 文本变换 | 全项目无文本大小写转换实现 |
| **text-indent** | 无首行缩进 | 全项目无 textIndent 相关实现 |
| **多 box-shadow / 多背景** | box-shadow 和 background 均只支持单值，无逗号分隔多值 | `UiStyleDeclaration.java` 各字段为单值类型 |
| **`white-space: pre / pre-wrap / pre-line`** | 仅支持 NORMAL 和 NOWRAP，不支持保留空白符/换行符的模式 | `UiWhiteSpace.java` 枚举仅 NORMAL/NOWRAP |
| **`border-collapse`** | 无表格边框合并模式 | 全项目无 border-collapse 相关实现 |
| **`inherit` / `initial` / `unset` 关键字** | 无显式继承/重置关键字；只能通过 clear 方法回退 | `UiStyleResolver.java` 无对应关键字处理逻辑 |
| **`list-style` / ul / ol / li** | 无列表标记渲染，无有序/无序列表语义 | 全项目无列表项目符号/编号渲染实现 |
| **`background-image` CSS 属性** | 背景图需通过控件 API 设置；不支持声明式 `background-image: url(...)` | `UiStyleDeclaration.java` 无 backgroundImage 字段；仅有 `DocumentHostImageDecorations` 控件 API |

### CSS 选择器与规则

| 能力 | 说明 | 关键证据 |
|------|------|----------|
| **后代选择器 / 子代选择器（`A B` / `A > B`）** | 无组合器支持，选择器仅作用于单元素 | `UiSelector.java` 注释明确："不支持后代/子代/兄弟选择器" |
| **`@media` 媒体查询** | 无任何实现 | 全项目无 media query 相关代码 |
| **`!important`** | 级联计算无 important 优先级机制 | `UiStyleResolver.java` 按 inline > id > class > tag 特异性，无 important 通道 |
| **`::before` / `::after` 伪元素** | 仅有交互伪类（:hover/:focus/:active/:disabled），无伪元素 | `UiPseudoClass.java` 无 BEFORE/AFTER 值；DOM 树无伪元素节点 |
| **`:first-child` / `:last-child` / `:nth-child`** | 无结构性伪类，只有状态型伪类 | `UiPseudoClass.java` 枚举中无结构型伪类值 |

### 事件

| 能力 | 说明 | 关键证据 |
|------|------|----------|
| **contextmenu（右键菜单）事件** | 无独立右键菜单事件；click 事件有 button 字段可区分右键，但无 contextmenu 语义 | 全项目无 contextmenu 事件类/接口 |
| **dblclick 双击事件** | 无双击检测与事件 | 全项目无 dblclick/doubleClick 相关实现 |
| **transitionend / animationend 事件** | 动画系统内部有完成状态，但不向页面作者暴露回调事件 | `DocumentAnimationTimeline.java` 无对外事件接口；无 transitionend/animationend 事件类 |
| **自定义事件（CustomEvent / dispatchEvent）** | 无自定义事件分发机制 | 全项目无 CustomEvent 或 dispatchEvent 相关实现 |

### DOM

| 能力 | 说明 | 关键证据 |
|------|------|----------|
| **cloneNode** | 无节点克隆 | `ElementNode.java` / `DocumentNode.java` 无 clone 方法 |
| **DocumentFragment** | 无文档片段（批量 DOM 操作优化） | 全项目无 DocumentFragment 实现 |
| **`<a>` 链接 / 超链接语义** | 无链接元素，无导航跳转能力 | 全项目无 anchor/href/link 相关 DOM 元素 |

### 表单

| 能力 | 说明 | 关键证据 |
|------|------|----------|
| **textarea 多行文本输入** | 文本输入仅支持单行 | `DocumentTextInputControl.java` 无多行/换行输入逻辑；全项目无 textarea 相关实现 |
| **图片加载失败 / alt 文本回退** | img 加载失败时无 fallback 显示 | `DocumentImageElement.java` 无 alt 属性和加载失败分支处理 |

---

## 二、部分实现 / 声明与实现不一致的能力（8 项）

以下能力在文档中列为"已支持"，但源码核实后发现**未完全接通**，需要在文档中明确标注实际边界：

### 1. `cursor` 光标样式 — 仅声明，未映射到系统光标

- **文档现状**：使用文档和 AI 记忆文档均将 `cursor` 列为"当前可用样式属性"
- **源码事实**：`UiCursor.java` 枚举完整（13 种光标类型），`UiStyleDeclaration`/`UiStyleResolver`/`ComputedStyle` 的级联计算链路完整，但**没有任何代码将 `UiCursor` 映射到 Minecraft/LWJGL 真实鼠标光标**（无 `glfwSetCursor`、`Mouse.setCursor` 等调用）
- **实际效果**：设置 `cursor:pointer` 后鼠标外观不会改变；唯一与"光标"相关的 `DocumentCursorOverlayControl` 是用图片元素模拟物品拖拽的自定义覆层，不是系统级光标变更
- **关键文件**：`ui/style/UiCursor.java`、`ui/style/UiStyleResolver.java`（398-404 行）
- **建议**：在 `cursor` 属性说明中明确注明"声明与级联已支持，系统光标映射待实现"

### 2. `overflow-wrap` / `word-break` 实际断词 — 声明完整，布局未消费

- **文档现状**：AI 记忆文档中列为"新增文本排版控制属性"
- **源码事实**：`UiOverflowWrap.java`（NORMAL/BREAK_WORD/ANYWHERE）和 `UiWordBreak.java`（NORMAL/BREAK_ALL/KEEP_ALL）枚举完整，`UiStyleResolver` 级联计算完整，但 `DocumentLayoutEngine` 的文本换行逻辑中**未检查和消费这两个属性**，实际断词行为固定
- **关键文件**：`ui/layout/DocumentLayoutEngine.java` 文本测量段，`ui/style/UiOverflowWrap.java`
- **建议**：在文档中明确注明"样式声明已支持，布局引擎待接通"

### 3. `font-weight` / `font-style` / `font-family` — 底层支持，CSS 属性层未暴露

- **文档现状**：未在能力列表中出现，但底层 `FontType` 已有 BOLD/ITALIC/BOLD_ITALIC 支持
- **源码事实**：`FontType.java` 和 `TextStyle.java` 底层有粗体/斜体/字体族支持，但 `UiStyleDeclaration` 中**无对应 CSS 属性字段**，页面作者无法通过样式声明控制字体粗细和斜体
- **关键文件**：`ui/text/FontType.java`、`ui/style/UiStyleDeclaration.java`
- **建议**：作为待补充的高优能力，底层代价较小

### 4. `scrollIntoView` / `scrollTo` 公开 API — 内部已有，未对外暴露

- **源码事实**：`DocumentScrollState.scrollTo()` 和 `scrollToReveal()` 方法存在且可用，但**未在 `ElementNode` 或 `UiDocument` 上暴露为公开 API**
- **关键文件**：`ui/layout/DocumentScrollState.java`

### 5. `focus()` / `blur()` 程序化聚焦 — 内部 private，无公开 API

- **源码事实**：焦点管理内部实现完整，但相关方法为 private，**页面作者无法程序化控制焦点**
- **关键文件**：`ui/document/HtmlLikeDocumentWidget.java` 焦点相关方法

### 6. 滚动条样式自定义 — 颜色硬编码，不可自定义

- **源码事实**：`DocumentPaintEngine.java:37-38` 有硬编码常量 `SCROLLBAR_TRACK_COLOR`/`SCROLLBAR_THUMB_COLOR`，**无对应 CSS 属性暴露**，页面作者不能自定义滚动条颜色或宽度
- **关键文件**：`ui/paint/DocumentPaintEngine.java`（37-38 行）

### 7. `select` / `dropdown` 下拉选择 — 有替代控件，无标准语义

- **源码事实**：`DocumentSegmentedSelectionControl` 提供类似单选功能，但**无下拉弹出面板**，候选项需全部预先展开，不等同于浏览器 `<select>` 的下拉体验
- **关键文件**：`ui/dom/control/DocumentSegmentedSelectionControl.java`

### 8. DOM 树遍历 API 不完整

- **源码事实**：`ElementNode` 已实现 `parentNode`/`children`/`childNodes`/`firstChild`/`lastChild`，但**缺少 `nextSibling` 和 `previousSibling`**，兄弟节点遍历需通过父节点 children 列表手动计算
- **关键文件**：`ui/dom/DocumentNode.java`、`ui/dom/ElementNode.java`

---

## 三、已完整实现的能力（27 项，经源码核实）

| 分类 | 已实现能力（括号内为核实关键文件） |
|------|-----------------------------------|
| **布局** | block、flex（flex-grow/shrink/basis/wrap/gap）、table、inline/inline-block、absolute/fixed/relative、margin collapse、`margin:0 auto` 水平居中、`box-sizing:border-box`、`min/max-width/height`（`DocumentLayoutEngine.java`） |
| **样式** | `z-index` + stacking context、`opacity` + group opacity、`overflow-x/y` 独立、`box-shadow`、`outline`、`border-radius`（分角+命中测试）、分边 border-width/color、虚线/点线/双线边框、`display:none`、`visibility:hidden`、`aspect-ratio`、`object-fit`（`DocumentPaintEngine.java`、`DocumentEffectChain.java`） |
| **文本** | `text-align`、`text-decoration`、`text-overflow:ellipsis`、`white-space:nowrap`、`line-height`（可继承）、`letter-spacing`、`vertical-align`（`DocumentLayoutEngine.java` 2225-2260 行） |
| **事件** | 三阶段传播（capture→target→bubble）、click/mousedown/mouseup/hover/focus/focusin/key/textinput/scroll、`stopPropagation`/`preventDefault`、事件委托（`HtmlLikeDocumentWidget.java`） |
| **DOM** | `getElementById`/`querySelector`/`querySelectorAll`、动态 `createElement`、`insertBefore`/`replaceChild`/`appendChild`/`removeChild`/`clearChildren`、`classList`（完整 DomTokenList API）、`setAttribute`/`getAttribute`、computed style 获取（`UiDocument.java`、`ElementNode.java`） |
| **控件** | 按钮、单行文本输入（含 placeholder）、开关/toggle、分段选择器、表格、背包槽位网格、tooltip（`ui/dom/control/` 包） |
| **动画** | transition（12 个属性，含 delay/fill-mode）、keyframe（含 fill-mode）、backdrop-filter:blur（`DocumentAnimationTimeline.java`） |
| **其他** | `tabindex`（-1/0/正整数完整语义）、`pointer-events:none` 命中穿透、样式表级联（inline > id > class > tag）、伪类（:hover/:focus/:active/:disabled/:focus-visible）、远程 HTTP 图片加载、`overflow:hidden` + border-radius 圆角裁剪 |

---

## 四、优先补齐建议

按**实现代价低但收益高**排序：

| 优先级 | 能力 | 理由 |
|--------|------|------|
| 🔴 高 | **`cursor` 接通真实光标** | 声明链路已全部完成，只差宿主 LWJGL 映射一步；对交互体验影响极大 |
| 🔴 高 | **`overflow-wrap`/`word-break` 接通布局** | 样式已声明，仅需在 `DocumentLayoutEngine` 文本换行段消费这两个属性 |
| 🔴 高 | **`focus()`/`blur()` 公开 API** | 内部已完整实现，仅需将现有 private 方法升为 public 或在 `ElementNode` 上提供代理 |
| 🔴 高 | **`scrollIntoView` 公开 API** | `DocumentScrollState.scrollToReveal()` 已存在，仅需在 `ElementNode` 上暴露 |
| 🔴 高 | **`textarea` 多行文本** | 表单场景几乎必备，当前完全缺失，且单行文本输入控件可作为基础复用 |
| 🟡 中 | **`font-weight`/`font-style` CSS 属性** | 底层 `FontType` 已有 BOLD/ITALIC，仅需在样式声明和级联层添加字段并接通 `TextStyle` |
| 🟡 中 | **`transitionend` 事件** | 动画系统已有完成状态检测，仅需在 timeline 的 tick 结束判断点派发事件给页面作者 |
| 🟡 中 | **`dblclick` 事件** | 在 click handler 入口处增加时间间隔检测即可实现 |
| 🟡 中 | **后代/子代选择器** | 扩展 `UiSelector` 的解析器，增加组合器匹配逻辑；无需修改级联计算框架 |
| 🟡 中 | **`:first-child` / `:last-child` / `:nth-child`** | 在 `UiPseudoClass` 中添加结构伪类，并在 `UiStyleResolver.compute()` 中计算结构位置 |
| 🟡 中 | **`white-space: pre-wrap`** | 在 `UiWhiteSpace` 枚举中添加值，并在布局引擎文本测量中处理保留换行符的断行逻辑 |
| 🟡 中 | **scrollbar 样式可自定义** | 在 `UiStyleDeclaration` 增加 scrollbar-color/scrollbar-width 属性，`DocumentPaintEngine` 消费 |
| 🟠 低 | **CSS `calc()`** | 需要新建长度表达式解析器，改动面较大 |
| 🟠 低 | **CSS transform** | 需要在绘制层引入变换矩阵堆栈，改动较大但效果显著 |
| 🟠 低 | **gradient 渐变** | 需要新建渐变背景绘制路径，可作为独立功能渐进实现 |
| 🟠 低 | **`::before`/`::after` 伪元素** | 需要在布局阶段动态插入伪元素节点，架构改动较大 |

---

## 五、文档与实现不符的关键点（须回写文档）

以下项目**需要在使用文档或 AI 记忆文档中补充实际边界说明**，避免接入方依赖尚未工作的能力：

1. **`cursor` 属性**：使用文档列为"常用样式"，需补注"系统光标外观映射待实现，当前设置不影响鼠标外观"
2. **`word-break` / `overflow-wrap`**：AI 记忆文档列为"新增文本排版控制属性"，需补注"样式声明与级联已支持，布局引擎实际断词待接通"
3. **`font-weight`/`font-style`**：底层有能力但无 CSS 属性暴露，不应出现在"可用样式"列表中，应列为"待开放"
4. **拖拽 `drop`/`dragenter`/`dragleave` 事件**：AI 记忆文档已明确标注"尚未补齐"，此处核实一致，无需修改
