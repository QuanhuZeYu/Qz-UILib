# AI记忆文档

本文件只保留对长期协作仍有持续价值的高层信息，不重复维护具体能力清单、属性细节、示例页行为和长文件索引。遇到具体任务时，Agent 必须主动读取对应文档、源码和错误记录确认现状。

## 最高优先级信息

- 项目处于第一版开发者入口固化阶段；新增对外能力、文档或 API 门面要按首版开发者体验审视。
- 当前主线仍是 HTML-like UI 渲染框架；功能取舍以这条主线为准，不为补“完整浏览器语义”偏离主线。
- 清退旧 retained 作者入口时，不要误删仍被 HTML-like 后端复用的 backend、runtime、inventory 宿主能力。
- 作者层只暴露 HTML-like/CSS-like 语义，不向页面作者暴露 Minecraft GUI 生命周期或底层渲染实现细节。

## 文档分工

- `docs/使用文档/`：对外开发使用文档。接入方式、能力边界、宿主集成、诊断入口都以这里为准。
- `docs/开放化调整.md`：开放化方案、调整范围、执行顺序与阶段状态。
- `docs/审查报告.md` 与 `docs/reviews/`：审查报告索引与详细归档，记录易用性、开放化、代码评审等需要长期留档的结论。
- `项目建议.md`：协作开发阶段的技术方向与阶段建议，不是正式使用文档。
- `docs/错误记录.md` 与 `docs/errors/`：历史错误索引与问题分析。
- `docs/使用文档/02-控件/背包Tooltip设计需求.md`：示例页专题需求，不属于通用接入必读项。

## 主动读取原则

- 涉及对外接入、作者能力、宿主入口时，先读 `docs/使用文档/README.md` 和对应分级文档。
- 涉及历史审查结论、开放化体验问题、长期评审结论时，先读 `docs/审查报告.md` 和对应 `docs/reviews/` 明细。
- 涉及命令入口、界面切换、输入路由、示例页或诊断入口时，先读 `docs/使用文档/03-宿主集成/Minecraft界面入口.md`、`docs/使用文档/04-诊断入口/指令触发方案.md`，再查相关源码与错误记录。
- 涉及动画、布局、绘制、背包槽位、tooltip、渲染运行时等具体能力时，不依赖本文件记忆，先读当前使用文档、相关源码和 `docs/错误记录.md`。
- 涉及旧 retained 结构清理、重命名或删除时，先搜索当前引用，再决定是否删除，不依赖历史印象。
- 需要知道具体类、入口、目录位置时，优先通过 `Glob`、`Grep`、`Read` 现查，不在本文件维护长文件清单。

## 长期稳定边界

- 业务文档入口以 `UiDocumentScreens.createDocumentScreen(...)` 为主；诊断入口不应回退成默认 UI 注入或全局热键。
- `UiDocumentScreens` 仅保留业务文档开屏门面；内建诊断页与示例页统一从 `UiDiagnosticsScreens` 进入。
- 不新增扩大直接 `Widget` 作者入口的 API；新增作者能力优先放在 `UiDocument`、DOM、样式系统、控件适配或 HTML-like 后端能力中。
- `createDocumentScreen(...)` 已默认补齐根元素 `width:100%`、`height:100%` 和 `overflow-y:auto`；文档与示例不应再把这组样板当作手动必填前置知识。
- 结构节点优先直接写 `UiDocument` DOM-like 元素；标准交互节点优先使用 `Document*Control`，其中 `DocumentTextInputControl` 现已使用真实 `input type="text"` 语义。
- `Widget`、`ViewportWidget`、`UiInputRouter`、`UiRenderContext`、`UiRuntimeAdapters` 与背包格子底层宿主适配仍可能是当前后端基础；动这些能力前必须先查源码引用。
- 新增 layout-affecting、宿主集成或对外能力时，要同步更新 `docs/使用文档/` 并保留最小必要验证。
- `club.heiqi.uilib.config.ForgeConfigTemplateScreen` 已作为对外可复用模板，用于替换 Forge 默认 `GuiConfig` 并直接消费 `Configuration` / `ConfigCategory` / `Property`。
- `ForgeConfigTemplateScreen.Spec` 已支持 `PropertyEditorFactory`、`Theme`、`TextSet` 三类扩展点；非列表字符串属性如果声明 `validValues`，默认走分段选择控件而不是文本输入。
- 继承 `GuiScreen` / `BaseScreen` 的页面类不应在纯 JVM 单测中直接实例化；相关教训记录在 `docs/errors/ERROR-20260508-jvm-test-guiscreen-static-init.md`。

## 运行与验证

- Windows 环境使用 PowerShell。
- `GRADLE_USER_HOME` 需显式设置为 `D:\.MyApps\.ENV\gradle-home`；验证命令按串行执行。
- 常用命令：`git diff --check`、`$env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"; ./gradlew.bat --no-configuration-cache test`、`$env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"; ./gradlew.bat --no-configuration-cache compileJava`、`$env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"; ./gradlew.bat --no-configuration-cache runClient21`。
- 纯 JVM 文本测量相关测试要注入确定性 `TextMeasureService`，不要依赖默认字体运行时。

## 当前阶段

- 继续固化第一版开放文档与业务入口，优先保证真实页面、控件迁移和开发者接入体验。
- 2026-05-08 HTML-like 开发者易用性审查提出的首轮入口收敛、控件心智和接入示例问题已完成整改；相关审查明细保留原始结论，并在文末追加后续修复状态，不直接改写原审查结论。
- 入门文档已新增 `01-入门/完整业务页面示例.md`，用于串联业务页面正常拼装路径；后续新增接入能力时优先接到这条连续体验链上。
- Forge 配置页已切换到 HTML-like 模板化实现，后续若继续扩展配置类页面，应优先复用模板而不是回退到 `GuiConfig`。
- 配置模板页若不走 `DocumentPageAuthoringSurface`，需要在 `BaseScreen.onResize(...)` 中显式给 `HtmlLikeDocumentWidget` 下发布局边界；仅设置 `UiLayoutSpec` 可能导致页面可开屏但内容区域为 `0x0`。
- 是否继续扩展动画、布局或渲染能力，要以真实需求和验证结果决定，不默认扩面。
- 非阻塞的底层细节优化后置，阶段目标变化时只在本文件保留高层边界，不回填大段实现细节。
