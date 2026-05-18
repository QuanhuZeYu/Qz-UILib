# AI记忆文档

本文件只保留对后续协作长期稳定、跨任务高频复用的高层信息，作为“导航 + 边界”文档使用，不承担阶段流水账、类清单、修复日志、版本兼容试验记录等职责。遇到具体任务时，Agent 必须主动读取对应文档、源码和错误记录确认现状。

## 使用定位

- 只记录长期稳定边界、主线方向、文档分工和验证前提。
- 不记录具体类清单、示例页细节、已完成修复列表、按日期追加的阶段流水账。
- 有明确归属的信息优先写回原文档：接入说明写 `docs/使用文档/`，示例页专属规格写 `docs/specs/`，审查结论写 `docs/审查报告.md` / `docs/reviews/`，错误教训写 `docs/错误记录.md` / `docs/errors/`。

## 当前主线

- 项目处于第一版开发者入口固化阶段；新增对外能力、文档或 API 门面要优先审视开发者接入体验。
- 当前主线是符合浏览器语义的 UI 渲染框架；功能取舍以浏览器标准行为为参照，逐步补齐常用浏览器能力。
- 作者层暴露浏览器语义（DOM / CSS / 事件模型），不向页面作者暴露 Minecraft GUI 生命周期或底层渲染实现细节。
- 需要增强可发现性或交互提示时，可优先从阿里巴巴矢量图标库寻找合适图标，再结合页面既有排版判断是否使用。

## 文档分工

- `docs/使用文档/`：对外开发使用文档，接入方式、能力边界、宿主集成、诊断入口以这里为准。
- `docs/specs/`：示例页或专项能力的视觉、交互和验收规格，不等同于通用接入文档。
- `docs/开放化调整.md`：开放化方案、调整范围、执行顺序与阶段状态。
- `docs/审查报告.md` 与 `docs/reviews/`：长期留档的审查结论。
- `docs/错误记录.md` 与 `docs/errors/`：历史错误索引与详细问题分析。
- `项目建议.md`：协作阶段的方向性建议，不是正式使用文档。

## 主动读取原则

- 涉及对外接入、作者能力、宿主入口时，先读 `docs/使用文档/README.md` 和对应分级文档。
- 涉及示例页专属视觉、交互或验收规格时，先读 `docs/specs/README.md` 和对应规格文档，再决定是否需要同步回写正式使用文档。
- 涉及命令入口、界面切换、输入路由、示例页或诊断入口时，先读 `docs/使用文档/03-宿主集成/Minecraft界面入口.md`、`docs/使用文档/03-宿主集成/Minecraft原版输入链路.md` 与 `docs/使用文档/04-诊断入口/指令触发方案.md`，再查源码与错误记录。
- 涉及历史审查结论、开放化体验问题或长期评审结论时，先读 `docs/审查报告.md` 与对应 `docs/reviews/` 明细。
- 涉及错误复现、回归风险或过往踩坑时，先读 `docs/错误记录.md` 与对应 `docs/errors/`。
- 需要知道具体类、入口、目录位置时，优先通过 `Glob`、`Grep`、`Read` 现查，不在本文件维护长文件索引。

## 长期稳定边界

- 业务文档入口以 `UiDocumentScreens.createDocumentScreen(...)` 为主；诊断页与示例页只保留内部开发工具入口，不对外暴露页面工厂类名。
- `UiDocumentScreens` 已收窄为业务作者公开门面，只保留 `DocumentScreenEnvironment`、`DocumentScreenContentBuilder` 与 `createDocumentScreen(...)`；definition-backed 托管页面、诊断页注册表与 screen identity 已下沉到 `ui.screen` 包内内部实现。
- 不新增扩大直接 `Widget` 作者入口的 API；新增作者能力优先放在 `UiDocument`、DOM、样式系统、控件适配或 HTML-like 后端能力中。
- `createDocumentScreen(...)` 已默认补齐根元素 `width:100%`、`height:100%` 和 `overflow-y:auto`；文档与示例不应再把这组样板当作手动必填前置知识。
- `flex-direction:column` 容器下，非 `stretch` 子项的 `width:auto` 走固有内容宽度测量，并受父内容宽度裁剪；auto 高文本块会按真实换行高度参与兄弟项排布，若业务要求整行占满，仍应显式写 `width:100%` 或继续使用 `stretch`。
- `flex-direction:column` 固定高度容器下，普通 `height:auto` 且 `overflow-y:visible` 的直接 flex item 会按接近浏览器 `min-height:auto` 的语义以自然内容高度作为收缩下限；若 item 自身声明 `overflow-y:auto` 或非 visible 裁切语义，则允许压缩并由自身滚动/裁切承接内容。
- HTML-like 布局默认按浏览器 content-box 心智处理 `width:100%`：子项自身 padding/border 会叠加到 border box 并可能溢出父内容盒；若业务需要把 padding/border 收进指定宽度，应显式声明 `box-sizing:border-box`（Java API 为 `setBoxSizing(UiBoxSizing.BORDER_BOX)`）。
- `position:absolute` 的 containing block 锚点按更接近浏览器的 positioned ancestor padding box 处理；relative 定位中 `top/bottom:%` 按 containing block 高度解析，不再按元素自身高度解析。
- 样式系统新增属性：`line-height`（auto=跟随字体）、`text-align`（START/CENTER/END，可继承）、`white-space`（NORMAL/NOWRAP）、`text-overflow`（CLIP/ELLIPSIS，配合 NOWRAP 生效）、`visibility`（VISIBLE/HIDDEN，可继承，HIDDEN 保留布局空间但不绘制不命中）、`min-width`/`max-width`/`min-height`/`max-height`（约束布局尺寸）、`flex-basis`（主轴初始尺寸，auto 退回 width/height）、`align-self`（覆盖父容器 align-items，AUTO=跟随父）、`flex-wrap`（NOWRAP/WRAP）。
- `justify-content` 已支持 SPACE_AROUND 和 SPACE_EVENLY；`align-items` 已支持 BASELINE（暂按 START 处理）；`overflow` 已支持 SCROLL（始终显示滚动条）。
- `border-radius` 现在参与命中测试（圆角外侧不命中）；`visibility:hidden` 同时跳过绘制和命中测试；非等值分角圆角已进入 `UiRenderContext` 表面绘制、clip/backdrop-filter 与命中测试链路。
- flex item 的 `margin:auto` 会吸收主轴/交叉轴剩余空间；flex shrink 权重已修正为使用 flex-basis。
- `flex-wrap:wrap` 已支持多行换行布局（row 方向）。
- 事件系统新增独立 `mousedown`/`mouseup` DOM 事件（`DocumentElementMouseDownHandler`/`DocumentElementMouseUpHandler`）；hover 父子切换时父元素不再收到多余 leave；新增 `focusin` 冒泡事件（`DocumentElementFocusInHandler`）。
- 事件系统已支持标准 DOM 三阶段传播模型（capture → target → bubble）：所有事件类提供 `stopPropagation()`、`stopImmediatePropagation()`、`preventDefault()`；`ElementNode` 支持 capture handler 注册（`setCaptureClickHandler`/`setCaptureKeyHandler`/`setCaptureMouseDownHandler`/`setCaptureMouseUpHandler`）；`HtmlLikeDocumentWidget` 的 click/key/mousedown/mouseup 分发已按三阶段执行。
- 样式系统已支持 CSS-like 选择器和样式表级联：`ElementNode` 提供 `className`/`classList`（`DomTokenList`）和 `id` 便捷方法；`UiSelector` 支持 tag/class/id/通配符/复合选择器及特异性计算；`UiStyleSheet` 容器通过 `UiDocument.addStyleSheet(...)` 挂载；`UiStyleResolver` 按 inline > id > class > tag 特异性级联计算所有属性。
- `UiSelector` 已支持伪类条件（`:hover`/`:focus`/`:focus-visible`/`:active`/`:disabled`）；伪类在特异性中计入 class 级别；`UiStyleResolver.compute(element, activeStates)` 接受 `Set<UiPseudoClass>` 进行状态感知样式计算。
- `UiDocument` 提供标准 DOM 查询：`getElementById(id)`、`querySelector(selectorText)`、`querySelectorAll(selectorText)`、`getElementsByTagName(tagName)`、`getElementsByClassName(className)`，均按深度优先遍历并复用 `UiSelector` 匹配。
- 样式系统新增视觉增强属性：`box-shadow`、`border-style`、`cursor`、`border-radius` 分角、`text-decoration`、`pointer-events`、`outline` 均已进入级联计算；当前运行时已接通 `text-decoration` 绘制、`pointer-events:none` 命中穿透、分角圆角绘制/clip/backdrop-filter/命中测试，以及 `box-shadow`、`outline`、虚线/点线/双线边框的基础绘制链路。
- `UiStyleVariables` 提供命名颜色/长度/字符串变量容器，挂载在 `UiDocument` 上作为文档级变量作用域；变量值变更会触发布局失效，但当前不支持 CSS `var(...)` 声明级自动解析，页面若要响应主题变量变化仍需读取变量并回写样式。
- `aspect-ratio` 已在高度 auto 且宽度可解析的普通盒布局中推导内容高度；普通 `img` 绘制阶段已支持 `object-fit` 的 fill/contain/cover/none/scale-down。
- 诊断页与示例页只展示已接入运行时并有最小验证的能力；仅完成级联解析、值类型承载或手动同步的能力必须明确写成边界，不得包装成浏览器语义已完整支持。
- `DocumentElementScrollHandler` / `DocumentElementScrollEvent` 提供元素滚动事件监听能力，通过 `ElementNode.setScrollHandler(...)` 注册。
- `DocumentNode` 已补齐标准 DOM 操作：`insertBefore(newChild, referenceChild)`、`replaceChild(newChild, oldChild)`，配合已有的 `appendChild`/`removeChild`/`clearChildren` 构成完整 DOM 操作集。
- 样式系统新增 border 分边控制：`setBorderWidthSides(UiStyleInsets)` 支持四边独立 border-width；`setBorderColors(UiBorderColors)` 支持四边独立 border-color；分边值设置后优先于统一 borderWidth/borderColor 生效。
- 样式系统新增文本排版控制：`letter-spacing`（字间距，可继承）、`word-break`（NORMAL/BREAK_ALL/KEEP_ALL，可继承）、`overflow-wrap`（NORMAL/BREAK_WORD/ANYWHERE，可继承）。
- `height` 百分比相对包含块高度解析；包含块高度为 auto 时百分比高度视为 auto（由内容撑开），不再静默解析为 0。
- Block 元素 `margin: 0 auto` 支持水平居中：有明确宽度的 block 元素，auto left/right margin 会平分剩余空间。
- `line-height` 是可继承属性：父元素设置后子元素自动继承，除非子元素自行覆盖。
- `min-height`/`max-height` 百分比相对包含块高度解析（而非宽度）；包含块高度为 auto 时百分比约束不生效。
- 相邻兄弟元素的垂直 margin 会折叠为两者中较大值（margin collapse），不再简单叠加。
- `border-box` 下 height 计算中的 padding 以 `contentWidth` 为基准解析百分比，不再以 0 解析。
- inline 元素的垂直 padding/border 只影响视觉渲染，不撑大行盒高度（行盒高度仅由 line-height 决定）。
- HUD 文档层统一经 `UiHudDocumentHost` 承载，继续复用 `UiDocument` / `HtmlLikeDocumentWidget`；当前稳定分为 `PASSIVE` 与 `INTERACTIVE` 两层，而不是再开放独立 Widget 作者入口。
- HUD 浮窗若需要固定外框并承载长内容，优先在面板内部声明固定高度或剩余空间容器，并对子容器使用 `overflow-y:auto`；不要依赖外层 HUD 根节点滚动去撑大浮窗。
- `INTERACTIVE` HUD 仍可在纯游戏阶段渲染，但只在容器态接通命中与焦点输入；当前稳定隐藏黑名单是游戏主页（含原版 `GuiMainMenu`、新版 `TitleScreen`、第三方主页 `galaxyspace.core.gui.GSGuiMainMenu`）、选图页、服务器列表、游戏内菜单和 Forge 配置页，其余第三方与大多数原版 `GuiScreen` 默认按容器态处理。
- `Config.GENERAL.uiDebug` 会在屏幕右上角显示当前 `GuiScreen` 类名，并自动裁剪到视口内，便于定位 HUD 黑名单命中情况与实际页面类名。
- 交互 HUD 的键盘接管契约是“先鼠标聚焦、后键盘接管”：必须先通过鼠标命中建立 HUD 焦点，不支持纯键盘首次进入 HUD。
- 交互 HUD 默认阻断命中区域继续落到底层宿主；若某个元素或其祖先需要显式放行空白区域，使用 `data-hit-test-passthrough="true"`。
- 多个交互 HUD 重叠时，输入只路由给最上层命中的那一层；按下后到抬起前的鼠标链路保持在同一层内，避免多层同时响应。
- `UiHudDocumentHost` 的输入分发需容忍回调内即时 `unregister()`；HUD 注册表遍历应基于快照或等效防御，不能在公开回调链中直接依赖可变列表的 fail-fast 迭代。
- 键盘输入需在宿主原生文本框与 UILib 焦点之间做隔离：原生文本框聚焦时 HUD/叠层不接管键盘；UILib 获得焦点后需阻断宿主原生键盘继续响应。
- HUD 键盘隔离的优先级高于原生页面处理链路：当前在 `handleKeyboardInput()` 阶段就会先让 UILib 尝试接管，再决定是否阻断宿主继续分发。
- HUD 的即时键盘抢占只处理按键语义，不提前注入可打印文本；可打印字符统一回落到常规收集链路，避免同一按键在 HUD 中重复落字。
- 浮窗/面板拖拽优先走 HTML-like 元素级能力（`setDragHandler(...)` / `DocumentDraggableSupport`），HUD 只复用该能力，不再单独扩一套 HUD 专属拖动 API。
- HTML-like 元素拖拽采用位移阈值激活：短点击保留 `click`，只有超过阈值后才进入真实拖拽并消费抬起事件。
- HTML-like 拖拽事件沿用 UILib 原生像素坐标体系，事件内 document 坐标只做 widget/document 局部化，不转换为 Minecraft GUI 缩放坐标。
- 浏览器式拖拽首版支持 `draggable="true"`、`dragstart`、`dragover`、`dragend`；`drop`、`dragenter`、`dragleave`、`DataTransfer` 与 `preventDefault()` 尚未补齐。
- 结构节点优先直接写 `UiDocument` DOM-like 元素；标准交互节点优先使用 `Document*Control`。`document.button()` / `document.input()` 默认可聚焦并参与正常 Tab 顺序，`document.img()` 默认 inline-block 且不可聚焦；`tabindex="-1"` 会跳过正常 Tab 遍历但仍允许鼠标/程序聚焦。
- HTML-like 文本文档默认按 `UILIB_RAW` 处理：页面作者写入的 `§a`、`§k` 等内容会按普通字符原样显示，不再隐式套用 Minecraft 文本格式码；如需兼容旧 `§` 语义，优先使用 `appendMinecraftText(...)`、`minecraftText(...)` 或 Minecraft 文本模式环境。
- 普通位图优先走浏览器式 `document.img()` / `img[src]`，支持本地 `ResourceLocation` 与远程 HTTP/HTTPS 位图；Minecraft 物品栈、纹理区域和背景装饰继续使用 `DocumentHostImageControl` / `DocumentHostImageDecorations`，不鼓励业务代码直接写底层 `custom renderer`。
- 新增 layout-affecting、宿主集成或对外能力时，要同步更新 `docs/使用文档/` 并保留最小必要验证。
- `ForgeConfigTemplateScreen` 已作为对外可复用模板；非列表字符串属性若声明 `validValues` 且当前值仍在候选集中，可走分段选择控件，遗留值应回退到文本输入，避免静默覆盖。
- `ForgeConfigTemplateScreen` 的列表属性在摘要、默认值和占位展示中默认取“前 5 项摘要”和“200 字符截断”中更长的一种，避免长列表直接撑爆配置页；实际文本编辑、保存和恢复默认仍使用完整列表值。
- 字体系统启动时会把本次已发现字体整理进 `FontConfig.fontSort`：已有 `fontSort` 配置时先按配置提示顺序吸收存在项，再把未配置字体按自然顺序追加，配置中缺失的字体只保留在内存缺失态；首次启动且尚无 `fontSort` 配置时，会按当前平台的常见多语种字体提示优先吸收已安装字体，避免自然排序先命中 CAD 等窄用途字体。
- 原版资源包重载会通过 `FontRenderer.onResourceManagerReload` 的 Mixin 触发字体系统重载；字体 GL 资源、字符页、后台字形任务和布局缓存必须整体重建，避免继续使用资源包重载前的 GL 状态。
- UILib 文档主渲染链路的字体批处理边界只覆盖 `DocumentPaintRenderer` 回放中的连续 `TEXT` 命令；遇到 `CUSTOM`、`BACKDROP_FILTER`、`PAINT_CONTEXT`、`CLIP` 以及其他非文本绘制命令前必须结束 scope 并 flush，避免文本跨越依赖即时可见内容或会改写 GL/FBO 状态的边界。
- `FontConfig.replaceOrigin=true` 会让原版 `FontRenderer` 在 SplashProgress 加载线程和客户端主线程都可能进入 UILib 字体管线；字体资源重载、shader/批渲染器重建与 `drawString` 必须在字体运行时锁下串行化，不能用“只允许主线程接管”规避 Splash 字体渲染。
- SplashProgress 期间仍使用 UILib 自定义字体和批渲染路径；不要为非客户端主线程切换第二套 immediate 字体绘制路径。Splash 特例只保留运行时锁、Mixin 异常保护、Splash reload guard 和资源重载入口跳过。
- 字体页纹理创建时必须先显式清为透明，再上传单字形并生成 mipmap；不要依赖驱动对未初始化纹理内容的默认值，否则资源重载后的 Splash 文本可能出现整格纯色块。
- 字体异步字形生成链路必须以运行时版本隔离并支持 generation handoff：任务、结果、待上传队列、字符缓存键、字体匹配缓存和宽度缓存都要区分 runtimeVersion；资源重载或字体排序变化后，reload 必须先进入生成链路屏障，暂停并废弃外部提交、清空旧 pending/upload/批渲染资源和旧 GL 字形页，再按新 runtimeVersion 重建字体目录、字符页和 dispatcher，旧 worker 迟到结果不能写入新页；旧 generation 中仍在 `GENERATING` / `UPLOAD_PENDING` 的字符需求必须迁移或重新提交到新 generation，不能静默丢弃；同一 runtime 内同码点取消后重提交还必须用 per-request generationId 隔离旧 pending 与新 pending，避免旧图像写成当前 ready glyph；快速连续 reload 必须 debounce/coalesce，否则每帧绘制请求也会被持续重建清空，导致字形生成和上传饥饿。
- 字体 CPU 热路径已固定为 direct-index + 页索引数组批次：文本 flush 默认不再依赖 `Map<GlyphPage,...>`、页命令对象或 `GlyphPage` 作为批次 key；`FontMatcher`、`TextLayoutService` 与 `GlyphGenerator` 共享派生字体缓存与单字符字符串缓存；UILib 内部 deferred text scope 会复用内部正交矩阵，默认关闭 flush/upload GL 诊断查询，只有显式调试开启时才读取 `glGet*` / `glGetError` 状态。
- 字体绘制入口必须用真实 GL 状态保存/恢复包裹完整字符渲染生命周期，包括 draw-stage 上传、字符页纹理创建/上传和最终 flush；替换原版 `FontRenderer` 后不能再通过硬编码 `glEnable`、blend 或颜色状态模拟原版结束状态。
- `ForgeConfigTemplateScreen` 的列表能力允许通过 `PropertyEditorFactory` 派生专用列表控件；当前 `fontSystem.fontSort` 使用专用二级字体排序页，面向 300+ 字体列表采用分页、搜索、全局序号跳转、目标序号移动、当前页内拖拽微调与立即保存应用入口，变更会先回写上级配置页草稿再复用模板保存链路；字体配置运行时读写必须解析到实际承载 `fontSort` 的 Forge 分类，避免 `fontSystem` / `fontsystem` 大小写差异导致 UI 保存与运行时重载读写不同属性。

## 运行与验证

- Windows 环境使用 PowerShell。
- `GRADLE_USER_HOME` 需显式设置为 `D:\.MyApps\.ENV\gradle-home`。
- 常用验证命令：`git diff --check`、`$env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"; ./gradlew.bat --no-configuration-cache test`、`$env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"; ./gradlew.bat --no-configuration-cache compileJava`、`$env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"; ./gradlew.bat --no-configuration-cache runClient21`。
- 纯 JVM 测试不要直接实例化继承 `GuiScreen` / `BaseScreen` 的页面类；文本测量相关测试要注入确定性 `TextMeasureService`。

## 当前阶段

- 继续固化第一版开放文档与业务入口，优先保证真实页面、控件迁移和开发者接入体验。
- 诊断页与示例页只作为开发期工具，不回退到默认 UI 注入或全局热键入口。
- 是否继续扩展动画、布局或渲染能力，要以真实需求和验证结果决定，不默认扩面。
