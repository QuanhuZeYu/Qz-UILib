# AI记忆文档

本文件只保留对后续协作长期稳定、跨任务高频复用的高层信息，作为“导航 + 边界”文档使用，不承担阶段流水账、类清单、修复日志、版本兼容试验记录等职责。遇到具体任务时，Agent 必须主动读取对应文档、源码和错误记录确认现状。

## 使用定位

- 只记录长期稳定边界、主线方向、文档分工和验证前提。
- 不记录具体类清单、示例页细节、已完成修复列表、按日期追加的阶段流水账。
- 有明确归属的信息优先写回原文档：接入说明写 `docs/使用文档/`，示例页专属规格写 `docs/specs/`，审查结论写 `docs/审查报告.md` / `docs/reviews/`，错误教训写 `docs/错误记录.md` / `docs/errors/`。

## 当前主线

- 项目处于第一版开发者入口固化阶段；新增对外能力、文档或 API 门面要优先审视开发者接入体验。
- 当前主线仍是 HTML-like UI 渲染框架；功能取舍以这条主线为准，不为补“完整浏览器语义”偏离主线。
- 作者层只暴露 HTML-like / CSS-like 语义，不向页面作者暴露 Minecraft GUI 生命周期或底层渲染实现细节。

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
- 固定宽度父容器中的 `width:100%` block/flex 子项，会把自身 padding/border 收进父内容盒，不再因子项自身盒模型把 border box 撑出父内容盒；若额外声明横向 margin，外侧空间仍需业务自行预留。
- HUD 文档层统一经 `UiHudDocumentHost` 承载，继续复用 `UiDocument` / `HtmlLikeDocumentWidget`；当前稳定分为 `PASSIVE` 与 `INTERACTIVE` 两层，而不是再开放独立 Widget 作者入口。
- HUD 浮窗若需要固定外框并承载长内容，优先在面板内部声明固定高度或剩余空间容器，并对子容器使用 `overflow-y:auto`；不要依赖外层 HUD 根节点滚动去撑大浮窗。
- `INTERACTIVE` HUD 仍可在纯游戏阶段渲染，但只在容器态接通命中与焦点输入；原版菜单页和 UILib 自身 `BaseScreen` 链路归为 `MENU` 并隐藏/清空该层，第三方非菜单自定义屏幕默认按容器态处理。
- 交互 HUD 的键盘接管契约是“先鼠标聚焦、后键盘接管”：必须先通过鼠标命中建立 HUD 焦点，不支持纯键盘首次进入 HUD。
- 交互 HUD 默认阻断命中区域继续落到底层宿主；若某个元素或其祖先需要显式放行空白区域，使用 `data-hit-test-passthrough="true"`。
- 多个交互 HUD 重叠时，输入只路由给最上层命中的那一层；按下后到抬起前的鼠标链路保持在同一层内，避免多层同时响应。
- `UiHudDocumentHost` 的输入分发需容忍回调内即时 `unregister()`；HUD 注册表遍历应基于快照或等效防御，不能在公开回调链中直接依赖可变列表的 fail-fast 迭代。
- 键盘输入需在宿主原生文本框与 UILib 焦点之间做隔离：原生文本框聚焦时 HUD/叠层不接管键盘；UILib 获得焦点后需阻断宿主原生键盘继续响应。
- HUD 键盘隔离的优先级高于原生页面处理链路：当前在 `handleKeyboardInput()` 阶段就会先让 UILib 尝试接管，再决定是否阻断宿主继续分发。
- HUD 的即时键盘抢占只处理按键语义，不提前注入可打印文本；可打印字符统一回落到常规收集链路，避免同一按键在 HUD 中重复落字。
- 浮窗/面板拖拽优先走 HTML-like 元素级能力（`setDragHandler(...)` / `DocumentDraggableSupport`），HUD 只复用该能力，不再单独扩一套 HUD 专属拖动 API。
- HTML-like 元素拖拽采用位移阈值激活：短点击保留 `click`，只有超过阈值后才进入真实拖拽并消费抬起事件。
- 结构节点优先直接写 `UiDocument` DOM-like 元素；标准交互节点优先使用 `Document*Control`。
- 面向作者的宿主贴图能力以 `DocumentHostImageControl` / `DocumentHostImageDecorations` 为主，不鼓励业务代码直接写底层 `custom renderer` 处理 Minecraft 物品或纹理。
- 新增 layout-affecting、宿主集成或对外能力时，要同步更新 `docs/使用文档/` 并保留最小必要验证。
- `ForgeConfigTemplateScreen` 已作为对外可复用模板；非列表字符串属性若声明 `validValues` 且当前值仍在候选集中，可走分段选择控件，遗留值应回退到文本输入，避免静默覆盖。

## 运行与验证

- Windows 环境使用 PowerShell。
- `GRADLE_USER_HOME` 需显式设置为 `D:\.MyApps\.ENV\gradle-home`。
- 常用验证命令：`git diff --check`、`$env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"; ./gradlew.bat --no-configuration-cache test`、`$env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"; ./gradlew.bat --no-configuration-cache compileJava`、`$env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"; ./gradlew.bat --no-configuration-cache runClient21`。
- 纯 JVM 测试不要直接实例化继承 `GuiScreen` / `BaseScreen` 的页面类；文本测量相关测试要注入确定性 `TextMeasureService`。

## 当前阶段

- 继续固化第一版开放文档与业务入口，优先保证真实页面、控件迁移和开发者接入体验。
- 诊断页与示例页只作为开发期工具，不回退到默认 UI 注入或全局热键入口。
- 是否继续扩展动画、布局或渲染能力，要以真实需求和验证结果决定，不默认扩面。
