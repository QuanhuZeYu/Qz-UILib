# `/qzuilib test` 页面系统性重构规划

> 后续复核（2026-06-05）：新的运行时矩阵目标已调整为“视觉化展示功能优先，浏览器语义验证为重要目标”。后续运行时矩阵重建设计以 `docs/开发者文档/specs/qzuilib-test-page-visual-matrix-plan.md` 为准；本文保留历史语义编号和预期文本素材，不能直接作为“已接入卡片”的当前真相。

本文定义 `/qzuilib test` 重构后的页面体系、语义覆盖矩阵和运行时人工验收文本规范。当前旧 test 子页已清空，命令入口进入语义首页，运行时卡片按类型放入二级页。

## 总目标

- `/qzuilib test` 不再按旧页面名组织测试，而是按 DOM、CSS、布局、绘制、事件、控件、动画、宿主、远程和网络语义组织。
- 每个语义点必须有明确编号、测试目标、自动断言边界、运行时操作步骤和人工预期结果文本。
- 运行时页面必须直接展示 `预期结果：...` 文案，测试者不需要翻文档才能判断观察结果是否正确。
- 需要人工观察的视觉、输入、HUD、远程页面和服务端往返用例必须显示 `人工确认` 标记。
- 所有用例结果统一落到页面内状态文本：`未执行`、`执行中`、`通过：...`、`失败：...`。

## 首页结构

| 区域 | 内容 | 验收要点 |
|---|---|---|
| 总览 | 当前 test 系统版本、已实现用例数、通过数、失败数、人工待确认数 | 页面打开后不依赖旧子页跳转 |
| 分组导航 | DOM、CSS、Layout、Paint、Input、Controls、TextFont、Animation、RuntimeHost、RemoteNet、MODCFG | 每组显示覆盖范围和缺口数 |
| 最近失败 | 最近失败用例、失败文本、复现入口 | 失败信息可直接交接 |
| 人工任务 | 需要人工确认的用例列表 | 每项显示 `预期结果：...` |
| 环境信息 | Minecraft、Forge、LWJGL3ify、字体 epoch、窗口尺寸、网络传输模式 | 便于截图和日志关联 |

## 二级页结构

首页只承载总览、分组导航、最近失败、人工任务摘要、环境信息和统一展示规则。运行时用例卡片不直接放在首页，而是按类型进入二级页。

| 二级页 | 当前状态 | 验收要点 |
|---|---|---|
| DOM | 已接入 DOM-001 到 DOM-013 | 核心 DOM 操作、textContent、属性、classList、选择器与链接默认行为均有运行时卡片；属性选择器和分组选择器缺口明确标记，不伪造通过 |
| CSS | 已接入 CSS-001 到 CSS-015 | 级联、继承、display、box-sizing、四边值、尺寸、背景、outline、阴影、可见性、overflow、文本样式、wrap、cursor 均有运行时卡片 |
| Layout | 已接入 LAYOUT-001 到 LAYOUT-016 | block、inline、flex、table、position、fixed/sticky 与嵌套滚动均有运行时卡片；baseline 和部分滚动视觉保留人工确认 |
| Paint | 已接入 PAINT-001 到 PAINT-009 | 绘制层级、stacking、opacity、clip、transform、top-layer、scrollbar、custom renderer 与 host image 均有运行时卡片；视觉/命中类保留人工确认 |
| Input | 已接入 INPUT-001 到 INPUT-013 | 点击传播、默认行为、滚轮、指针序列、hover/active、焦点、Tab、键盘均可通过按钮断言；contextmenu 与 drag/drop 缺口明确标记 |
| Controls | 已接入 CTRL-001 到 CTRL-015 | button、input、textarea、checkbox、radio、select、slider、toggle、segmented、tab、table、slot/overlay 均有运行时卡片；overlay/slot 交互保留人工确认 |
| TextFont | 已接入 TEXT-001 到 TEXT-007 | raw/formatted 文本、测量、fallback、font reload、obfuscated、trim/wrap 均有运行时卡片；fallback/reload/动态视觉保留人工确认 |
| Animation | 已接入 ANIM-001 到 ANIM-008 | transition、per-property、keyframes、delay/duration/iteration、direction、fill-mode、timing、layout/paint impact 均有运行时卡片；视觉过程保留人工确认 |
| RuntimeHost | 已接入 HOST-001 到 HOST-007 | 开屏、resize、runtime stats、GL render context、HUD、容器态输入桥接、异常面板均有运行时卡片；宿主行为保留人工确认 |
| RemoteNet | 已接入 NET-001 到 NET-010 | Channel、分片、Fetch、Stream、Store、远程页面/HUD、安全集、配置同步、传输回退均有运行时卡片；服务端链路保留人工确认 |
| ModernConfig | 已接入 VIS-MODCFG-001 | 现代配置模板完整 demo 入口、12 入口预览、模块状态牌与独立屏幕跳转；屏幕内部交互保留游戏内人工确认 |

## 运行时用例卡片规范

每个运行时测试卡片必须显示下列字段，字段名保持中文固定文本。

| 字段 | 要求 |
|---|---|
| 用例编号 | 例如 `DOM-001`、`LAYOUT-014`、`NET-009` |
| 覆盖语义 | 简短写明被测语义，不写笼统页面名 |
| 自动断言 | 写明是否可由 JVM 或运行时按钮自动判断 |
| 操作步骤 | 人工或自动点击步骤，最多三句 |
| 预期结果 | 必须以 `预期结果：` 开头，写成可观察文本 |
| 实际结果 | 执行后由按钮或人工填写，失败时保留差异说明 |
| 状态 | `未执行`、`执行中`、`通过：观察结果与预期一致`、`失败：观察结果与预期不一致 - <差异说明>` |

## 运行时矩阵接入范围

P0 已建立分组索引、运行时测试结果模型和统一用例卡片契约。P1/P2/P3 已将规格矩阵中的 DOM-001 到 DOM-013、CSS-001 到 CSS-015、LAYOUT-001 到 LAYOUT-016、PAINT-001 到 PAINT-009、INPUT-001 到 INPUT-013、CTRL-001 到 CTRL-015、TEXT-001 到 TEXT-007、ANIM-001 到 ANIM-008、HOST-001 到 HOST-007、NET-001 到 NET-010 全部接入二级页运行时卡片；后续新增 MODCFG/VIS-MODCFG-001 作为完整现代配置模板 demo 入口。可由当前模型判断的用例接入自动断言；视觉、宿主、服务端链路、独立屏幕跳转和明确未实现能力保持执行中/人工确认或待实现缺口状态，不伪造通过。

| 二级页 | 已接入卡片 | 自动执行边界 | 人工确认 / 缺口边界 |
|---|---:|---|---|
| DOM | 13 | DOM 变更、布尔属性、classList、基础选择器和链接默认行为 | 属性选择器 `[attr]` / `[attr=value]` 与分组选择器完整支持标记为待实现缺口 |
| CSS | 15 | 级联、继承、display、盒模型、尺寸、背景值、可见性、overflow、文本样式和 cursor 声明 | 阴影真实层级、滚动条视觉和宿主光标切换保留人工确认 |
| Layout | 16 | block、margin collapse、inline、flex、table、relative/absolute/fixed 结构与嵌套滚动样例 | inline-block baseline、fixed/sticky 滚动视觉保留人工确认或已知缺口 |
| Paint | 9 | 绘制样例结构、stacking、opacity、clip、transform、top-layer、scrollbar、custom renderer 与 host image 入口 | 真实层级、命中、宿主图片和滚动条交互保留人工确认 |
| Input | 13 | click 传播、stopPropagation、preventDefault、wheel、pointer、hover/active、focus、Tab、keyboard/textInput | contextmenu 与 drag/drop 事件链标记为待实现缺口 |
| Controls | 15 | button、input、password、number、textarea、checkbox、radio、select、slider、toggle、segmented、tab、table 结构 | slot/inventory、tooltip、cursor overlay 和部分控件视觉交互保留人工确认 |
| TextFont | 7 | raw/formatted 文本、测量、font epoch、obfuscated 和 trim/wrap 摘要 | fallback、reload debounce 和动态视觉保留游戏内人工确认 |
| Animation | 8 | transition 声明、per-property、keyframes、delay/duration/iteration、direction、fill-mode、timing 与布局影响入口 | 事件日志和视觉过程保留人工确认 |
| RuntimeHost | 7 | 开屏状态、窗口信息、runtime stats、GL/HUD/容器输入/异常面板入口 | 聊天命令时序、resize、GL 状态、HUD 层级和容器桥接保留人工确认 |
| RemoteNet | 10 | 网络/远程能力入口、传输模式、分片、fetch 状态、stream/store/remote/config 摘要 | 服务端往返、远程页面/HUD、配置保存和传输回退保留人工确认 |
| ModernConfig | 1 | 现代配置模板 demo 入口、config 模块可用性状态牌与 12 入口预览 | `ModernConfigTemplateScreen` 独立屏幕、12 入口可见性、搜索/草稿/保存/返回链路保留 runClient21 人工确认 |

二级页卡片必须完整显示 `用例编号`、`覆盖语义`、`自动断言`、`操作步骤`、`预期结果`、`实际结果`、`状态` 七个字段，并提供 `执行自动测试`、`人工通过`、`人工失败` 操作。新增或调整卡片时，仍必须先在本文对应分组表补齐编号、语义和 `预期结果：...` 文本。

## DOM 与选择器语义

| 编号 | 语义 | 运行时预期文本 |
|---|---|---|
| DOM-001 | `appendChild` 返回插入节点并移动已有节点 | 预期结果：点击执行后 A 节点移动到 B 节点后方，页面显示 `返回节点：A`。 |
| DOM-002 | `insertBefore` 同父移动先移除再计算参考索引 | 预期结果：点击执行后节点顺序变为 `C, A, B`，没有重复 A 节点。 |
| DOM-003 | `replaceChild` 返回被替换节点并保持新节点唯一归属 | 预期结果：点击执行后旧节点离开文档，新节点显示在原位置，结果文本为 `被替换：old`。 |
| DOM-004 | `removeChild` 只允许直接子节点并返回被移除节点 | 预期结果：点击执行后目标节点消失，错误按钮显示 `非直接子节点被拒绝`。 |
| DOM-005 | `DocumentFragment` 插入后自身清空 | 预期结果：点击执行后 fragment 内三个元素出现在目标容器，fragment 计数显示 0。 |
| DOM-006 | `textContent` 设置会替换子树文本 | 预期结果：点击执行后复杂子树被单一文本替换，页面显示 `textContent 已替换`。 |
| DOM-007 | 属性读写、删除和 HTML 布尔属性 | 预期结果：`disabled="false"` 的按钮仍不可点击，状态显示 `布尔属性禁用生效`。 |
| DOM-008 | `classList` add/remove/toggle/contains | 预期结果：点击 toggle 后卡片颜色和 class 状态同步切换。 |
| DOM-009 | `querySelector` 不返回内部 document root | 预期结果：执行 `querySelector('*')` 后结果为第一个作者元素，不是隐藏根节点。 |
| DOM-010 | type、class、id、后代、子代和分组选择器 | 预期结果：`domcase`、`.query-target`、`#query-target-id`、后代和子代选择器均显示 `1/1`，分组选择器未完成时显示待实现缺口。 |
| DOM-011 | 属性选择器 `[attr]` / `[attr=value]` | 预期结果：支持后 `[data-case]` 显示 `2/2`、`[data-case=match]` 显示 `1/1`；当前未支持时明确显示待实现缺口。 |
| DOM-012 | 结构伪类和交互伪类 | 预期结果：首项和末项结构伪类显示 `1/1`；移动、按下或键盘聚焦交互目标时只出现对应 hover、active、focus-visible 状态。 |
| DOM-013 | 链接默认行为和 `preventDefault` | 预期结果：普通链接触发导航记录，preventDefault 链接只记录事件不执行默认动作。 |

## CSS 级联与样式语义

| 编号 | 语义 | 运行时预期文本 |
|---|---|---|
| CSS-001 | inline style 高于样式表规则 | 预期结果：同一元素最终显示为 inline 指定颜色。 |
| CSS-002 | specificity 顺序：id、class、type、声明顺序 | 预期结果：四个样例最终颜色依次符合页面旁边标注。 |
| CSS-003 | 可继承属性和不可继承属性 | 预期结果：子元素继承文本颜色，不继承父元素 border。 |
| CSS-004 | `display:none/block/inline/inline-block/flex/table` | 预期结果：none 项不占位，其余项按标注布局形态显示。 |
| CSS-005 | `box-sizing:content-box/border-box` | 预期结果：两张卡片外框宽度相同，但内容区宽度按盒模型不同。 |
| CSS-006 | margin、padding、border 四边独立值 | 预期结果：四边间距和边框颜色按标注方向呈现。 |
| CSS-007 | width/height、min/max、percent、auto | 预期结果：min 大于 max 时 min 胜出，百分比盒跟随父容器变化。 |
| CSS-008 | background-color、background-image `url(...)`、none | 预期结果：资源图区域显示贴图，none 区域只显示底色。 |
| CSS-009 | border-style、border-radius、outline | 预期结果：圆角边框、焦点 outline 和普通边框互不覆盖。 |
| CSS-010 | box-shadow outset/inset 绘制层级 | 预期结果：外阴影在卡片外侧，内阴影位于背景之上且不盖住边框。 |
| CSS-011 | opacity、visibility、pointer-events | 预期结果：不可见项不显示，透明项可见但半透明，pointer-events:none 区域点击穿透。 |
| CSS-012 | overflow hidden/auto/scroll 与 scrollbar-width | 预期结果：hidden 裁剪无滚动条，auto 只在溢出时显示滚动条，scroll 始终保留滚动能力。 |
| CSS-013 | text-align、text-decoration、text-transform | 预期结果：文本按左中右对齐，修饰线和大小写转换符合标注。 |
| CSS-014 | white-space、overflow-wrap、word-break | 预期结果：nowrap 不换行，pre 保留空格换行，break 样例在窄列内断行。 |
| CSS-015 | cursor 与 hover 状态 | 预期结果：鼠标移入不同区域时页面内光标状态标签同步变化。 |

## 布局与尺寸语义

| 编号 | 语义 | 运行时预期文本 |
|---|---|---|
| LAYOUT-001 | block normal flow 垂直布局 | 预期结果：三块内容从上到下排列，垂直间距与标尺一致。 |
| LAYOUT-002 | 相邻 margin collapse | 预期结果：相邻块之间间距等于较大 margin，不是两者相加。 |
| LAYOUT-003 | 空块与递归 margin collapse | 预期结果：空块不产生额外高度，父子 margin collapse 后标尺吻合。 |
| LAYOUT-004 | inline 文本、inline 元素、inline-block | 预期结果：文本和 inline 元素同一行排列，inline-block 保持自身盒宽高。 |
| LAYOUT-005 | inline-block baseline | 预期结果：inline-block 底部基线与相邻文本基线对齐，若未完成则标记 `已知缺口`。 |
| LAYOUT-006 | flex row/column 主轴分配 | 预期结果：flex-grow 项占满剩余空间，固定项宽度不被挤压。 |
| LAYOUT-007 | flex-basis、min-width:auto、min-width:0 | 预期结果：默认项按 min-content 阻止过度收缩，min-width:0 项可压缩。 |
| LAYOUT-008 | flex-wrap 与 align-content | 预期结果：多行 flex 在交叉轴按 space-between/center 等规则分布。 |
| LAYOUT-009 | flex 交叉轴 auto margin | 预期结果：auto margin 项在交叉轴居中或贴边，符合旁边标注。 |
| LAYOUT-010 | table fixed/auto 列宽 | 预期结果：auto 表格内容列根据最长内容扩展，fixed 表格按声明宽度分配。 |
| LAYOUT-011 | position relative | 预期结果：relative 元素视觉偏移，但原始占位仍保留。 |
| LAYOUT-012 | absolute containing block 和 auto margin 居中 | 预期结果：absolute 子元素相对最近 containing block 定位，auto margin 样例居中。 |
| LAYOUT-013 | fixed 默认相对视口 | 预期结果：滚动页面时 fixed 标记停留在视口右上角。 |
| LAYOUT-014 | transform 祖先建立 fixed containing block | 预期结果：fixed 子元素在 transform 祖先内固定，并随祖先滚动裁剪。 |
| LAYOUT-015 | sticky 阈值与滚动范围 | 预期结果：sticky 标题到达阈值后吸顶，离开容器边界后停止。 |
| LAYOUT-016 | 根滚动与嵌套滚动 | 预期结果：嵌套滚动优先消费滚轮，滚到底后再由外层滚动。 |

## 绘制、命中与视觉语义

| 编号 | 语义 | 运行时预期文本 |
|---|---|---|
| PAINT-001 | background、border、text 绘制顺序 | 预期结果：背景在最底层，边框压住背景边缘，文本位于最上层。 |
| PAINT-002 | stacking context 顺序 | 预期结果：z-index 高的 stacking context 覆盖低层，子元素不能越出父 stacking context。 |
| PAINT-003 | opacity stacking context | 预期结果：半透明组整体混合，组内高 z-index 不越过外部兄弟。 |
| PAINT-004 | overflow clip 与圆角 clip | 预期结果：子元素超出圆角容器部分被裁剪，命中也不可达。 |
| PAINT-005 | transform 平移、缩放、旋转命中 | 预期结果：视觉位置与点击命中位置一致，未变换原位置点击无效。 |
| PAINT-006 | top-layer 绘制与命中 | 预期结果：tooltip/select 弹层覆盖普通内容，并优先接收点击。 |
| PAINT-007 | scrollbar 几何和命中 | 预期结果：拖动滚动条 thumb 时内容同步滚动，点击轨道按页滚动。 |
| PAINT-008 | custom renderer 逃生口边界 | 预期结果：自定义绘制区域只绘制标注图形，不影响标准背景、边框和文本。 |
| PAINT-009 | host image 与资源缺失 fallback | 预期结果：有效资源显示图片，缺失资源显示 fallback 占位。 |

## 输入与事件语义

| 编号 | 语义 | 运行时预期文本 |
|---|---|---|
| INPUT-001 | capture、target、bubble 顺序 | 预期结果：点击子节点后事件日志顺序为 `root capture -> parent capture -> target -> parent bubble -> root bubble`。 |
| INPUT-002 | `stopPropagation` 只停止后续传播 | 预期结果：目标处理后祖先 bubble 不再记录，但默认动作仍执行。 |
| INPUT-003 | `preventDefault` 阻止默认行为 | 预期结果：链接或滚动默认动作不执行，事件日志仍完整显示。 |
| INPUT-004 | handler 返回 true 与默认行为分离 | 预期结果：返回 true 后传播停止，但未 preventDefault 的默认滚动仍执行。 |
| INPUT-005 | mousedown、mouseup、click、doubleclick | 预期结果：单击日志为 down/up/click，双击额外记录 doubleclick。 |
| INPUT-006 | contextmenu | 预期结果：右键目标区域记录 contextmenu，preventDefault 区域不弹出默认动作。 |
| INPUT-007 | hover/active 状态传播 | 预期结果：子元素 hover 时祖先 hover 状态也更新，不被子 handler 返回值截断。 |
| INPUT-008 | focus、blur、focusin、focusout | 预期结果：焦点切换日志显示 `focusout -> focusin -> blur -> focus` 的项目约定顺序。 |
| INPUT-009 | Tab 顺序与 focus-visible | 预期结果：按 Tab 后焦点按 DOM 顺序移动，键盘焦点显示 focus-visible 样式。 |
| INPUT-010 | wheel 事件与默认滚动 | 预期结果：wheel 日志先出现，未取消区域随后滚动，取消区域不滚动。 |
| INPUT-011 | keyboard keydown/keyup/textInput | 预期结果：输入字符时 key 与 textInput 日志都出现，组合键不错误插入文本。 |
| INPUT-012 | dragstart、drag、dragover、dragend | 预期结果：拖拽卡片时 ghost/目标提示随鼠标移动，放开后顺序更新。 |
| INPUT-013 | dragenter、dragleave、drop | 预期结果：进入目标区显示 enter，离开显示 leave，释放显示 drop；若未实现则标记 `待实现缺口`。 |

## 控件与表单语义

| 编号 | 语义 | 运行时预期文本 |
|---|---|---|
| CTRL-001 | button enabled/disabled/action | 预期结果：可用按钮点击计数加 1，disabled 按钮点击无变化。 |
| CTRL-002 | text input value、selection、caret | 预期结果：输入、删除、选择替换后 value 与光标位置文本一致。 |
| CTRL-003 | password input 掩码 | 预期结果：页面只显示掩码字符，结果区保存真实 value 长度。 |
| CTRL-004 | number input 解析、非法值、step | 预期结果：有效数字按 step 调整，非法输入显示错误状态且不提交。 |
| CTRL-005 | textarea 逻辑行与视觉软换行 | 预期结果：长文本自动软换行，value 中只保留真实换行。 |
| CTRL-006 | checkbox checked/indeterminate/disabled | 预期结果：三态视觉与结果文本同步，disabled 项不能切换。 |
| CTRL-007 | radio group 单选互斥 | 预期结果：同组只保持一个选中，不同组互不影响。 |
| CTRL-008 | select top-layer 弹层 | 预期结果：打开下拉后选项覆盖普通内容，选择后 value 与显示文本同步。 |
| CTRL-009 | slider horizontal/vertical/min/max/step | 预期结果：拖动 thumb 后数值按 step 对齐，超出范围被 clamp。 |
| CTRL-010 | toggle switch | 预期结果：开关点击后视觉滑块移动，状态文本在 on/off 间切换。 |
| CTRL-011 | segmented selector | 预期结果：点击分段后只有当前段高亮，change 日志记录旧值和新值。 |
| CTRL-012 | tab control | 预期结果：切换 tab 后只显示当前面板，键盘切换顺序正确。 |
| CTRL-013 | table control | 预期结果：表头、行、单元格边框和列宽符合表格语义。 |
| CTRL-014 | slot、slot grid、inventory slot grid | 预期结果：槽位 hover 显示 tooltip，点击槽位后 carried item 状态按标注变化。 |
| CTRL-015 | tooltip、cursor overlay、overlay host | 预期结果：tooltip 不参与普通布局，随鼠标移动且不被底层内容遮挡。 |

## 文本、字体与国际化语义

| 编号 | 语义 | 运行时预期文本 |
|---|---|---|
| TEXT-001 | `TextContentMode.UILIB_RAW` | 预期结果：`§a` 等字符按普通文本显示，不触发 Minecraft 格式化。 |
| TEXT-002 | `TextContentMode.MINECRAFT_FORMATTED` | 预期结果：`§a绿色` 显示为绿色，结果区仍能显示原始文本长度。 |
| TEXT-003 | 字符宽度、line-height、baseline | 预期结果：中英文混排行高稳定，标尺线与文本基线位置一致。 |
| TEXT-004 | 字体 fallback | 预期结果：缺字字符使用 fallback 字体显示，不出现空白方块。 |
| TEXT-005 | font reload debounce | 预期结果：连续 reload 后只显示最终 epoch，页面不崩溃。 |
| TEXT-006 | obfuscated 格式码动态文本 | 预期结果：启用动态文本后字符变化但布局宽度不抖动。 |
| TEXT-007 | trim 与 wrap | 预期结果：窄容器文本按测量服务裁剪或换行，不遮挡相邻控件。 |

## 动画与 Transition 语义

| 编号 | 语义 | 运行时预期文本 |
|---|---|---|
| ANIM-001 | transition start/end/cancel | 预期结果：触发动画后日志出现 start/end，中途反向触发 cancel。 |
| ANIM-002 | per-property transition | 预期结果：颜色和 transform 按不同 duration 结束，日志分属性记录。 |
| ANIM-003 | keyframes from/to 与百分比帧 | 预期结果：盒子按关键帧路径移动，最终停在标注终点。 |
| ANIM-004 | delay、duration、iteration-count | 预期结果：延迟期不移动，循环次数与日志 iteration 数一致。 |
| ANIM-005 | direction normal/reverse/alternate | 预期结果：reverse 从终点开始，alternate 每轮方向翻转。 |
| ANIM-006 | fill-mode none/forwards/backwards/both | 预期结果：动画前后样式保持方式与卡片标注一致。 |
| ANIM-007 | steps() 与 cubic-bezier | 预期结果：steps 样例阶梯跳动，bezier 样例平滑加减速。 |
| ANIM-008 | layout-affecting 与 paint-only 属性 | 预期结果：width 动画触发布局变化，opacity 动画只改变绘制透明度。 |

## 宿主运行时语义

| 编号 | 语义 | 运行时预期文本 |
|---|---|---|
| HOST-001 | `/qzuilib test` 延后开屏时序 | 预期结果：从聊天框执行命令后页面稳定打开，不被聊天关闭流程吞掉。 |
| HOST-002 | resize 与 viewport fill | 预期结果：调整窗口后卡片重新排布，滚动位置不异常跳变。 |
| HOST-003 | runtime stats 与帧耗时 | 预期结果：页面显示 host 尺寸、鼠标坐标、draw/update 指标且数值持续刷新。 |
| HOST-004 | GL-backed render context | 预期结果：标准背景、边框、文本、图片和自定义绘制都可见，无 GL 状态污染。 |
| HOST-005 | HUD 纯显示层与交互层 | 预期结果：纯 HUD 在容器界面中隐藏，交互 HUD 可接收点击和键盘焦点。 |
| HOST-006 | 容器态输入桥接 | 预期结果：点击 HUD 输入框后键盘输入进入 HUD，点击外部后焦点还给原生界面。 |
| HOST-007 | 异常面板 | 预期结果：故意失败用例显示可读错误，不导致客户端无提示退出。 |

## 远程、配置与网络语义

| 编号 | 语义 | 运行时预期文本 |
|---|---|---|
| NET-001 | Channel C2S/S2C 往返 | 预期结果：点击执行后显示 `通过：Channel 往返完成`。 |
| NET-002 | C2S 分片与重组 | 预期结果：超过 32KB 的消息成功分片重组，结果显示原始长度一致。 |
| NET-003 | Fetch 成功、错误、超时、取消、限流 | 预期结果：五个按钮分别显示 200、500、timeout、cancelled、429。 |
| NET-004 | Stream 大内容下载 | 预期结果：下载进度递增到 100%，最终校验大小通过。 |
| NET-005 | Store snapshot/delta/player store | 预期结果：Store 视图按服务端推送更新，玩家定向 Store 只影响当前玩家。 |
| NET-006 | RemoteDocumentPages.open | 预期结果：远程页面打开后点击提交，服务端返回结果页并显示 `远程页面提交通过`。 |
| NET-007 | RemoteHudOverlays.open | 预期结果：远程 HUD 显示浮层，点击提交后 HUD 显示 `远程 HUD 提交通过`。 |
| NET-008 | Remote CSS/HTML 安全集 | 预期结果：允许的标签和样式生效，禁止内容被忽略并显示安全提示。 |
| NET-009 | Config sync 草稿与保存 | 预期结果：修改配置草稿后服务端收到同步，点击保存后显示 `配置保存通过`。 |
| NET-010 | vanilla/forge 传输回退 | 预期结果：当前传输模式显示正确，切换配置后网络自检仍可通过。 |

## 重建顺序

| 阶段 | 目标 | 完成标准 |
|---|---|---|
| P0 | 首页、分组索引、结果模型、人工预期规范 | `/qzuilib test` 可打开首页，页面显示本规格的分组和预期文本规则 |
| P1 | DOM、CSS、Layout、Paint 核心语义页 | 每页至少有 JVM 测试和 5 个运行时卡片 |
| P2 | Input、Controls、TextFont、Animation 语义页 | 每个控件和事件都有独立卡片，不再混在 Smoke 页中 |
| P3 | RuntimeHost、RemoteNet 语义页 | HUD、远程页面、配置同步和网络往返恢复可运行入口 |
| P4 | 结果持久化与交接 | 失败结果可复制，截图或日志路径可记录，交接文档能指向失败用例编号 |

## 文档维护规则

- 新增或删除 `/qzuilib test` 分组时，同步更新本文、`docs/使用文档/04-诊断入口/指令触发方案.md` 和 AI 记忆当前态。
- 每个运行时用例必须先写入本规格的编号、语义和 `预期结果：...` 文本，再实现页面卡片。
- 已知缺口可以进入页面，但必须标注 `已知缺口` 或 `待实现缺口`，不能伪装为通过。
- 旧页面名可以作为历史来源备注，但不能成为新体系的主分组名称。
