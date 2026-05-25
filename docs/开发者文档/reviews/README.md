# 审查报告

本文件作为审查报告索引，统一记录功能审查、开放化审查、易用性审查、代码评审结论等需要长期留档的结果。

详细报告存放在本目录下；本文件只保留指针、主题和必要摘要，避免单文件持续膨胀。

## 2026-05-25-project-code-structure-audit
- 类型：全项目代码结构深度审查（覆盖 UI / font / net / config / client / mixin / internal）
- 详情文档：[REVIEW-20260525-project-code-structure-audit.md](REVIEW-20260525-project-code-structure-audit.md)
- 结论摘要：当前主代码约 445 个 Java 文件、约 8 万行；项目主线设计仍然成立，对外入口与网络协议心智较克制，但内部能力扩张后出现第二层复用边界不足。审查时重点问题包括：稳定 API 清单与源码漂移、旧审查索引中的当前行数摘要过期、`ui.screen.example` 约 1 万行诊断/示例代码仍进主产物、远程页面与远程 HUD 的 session/Stream/提交逻辑重复、`ForgeConfigTemplateScreen` / `NetRuntimeSelfChecks` / `UiStyleDeclaration` 等子系统级大文件继续膨胀、public `__` 内部 API 与 input 反向依赖仍未收口。P0 文档漂移与旧索引过期摘要已完成整改；P1 已完成诊断页边界收口、远程页面/HUD session gateway 复用、配置模板绑定与文档构建拆分、网络自检注册/执行/远程 smoke 构造拆分；后续剩余重点转为 P2/P3 的样式声明、HUD/TextArea 二轮拆分、NetService 内部协作者和内部 API 收口。

## 2026-05-08-html-like-developer-usability
- 类型：HTML-like 框架开发者易用性审查
- 详情文档：[REVIEW-20260508-html-like-developer-usability.md](REVIEW-20260508-html-like-developer-usability.md)
- 后续整改：已完成根节点默认全视口、完整业务页面示例、结构节点与交互控件边界说明，以及诊断入口链路收敛；整改状态只在索引页保留摘要，不回写原始审查正文。

## 2026-05-12-first-version-entry-truthfulness-and-boundary-credibility
- 类型：第一版开发者入口真实性与边界可信度审查
- 详情文档：[REVIEW-20260512-first-version-entry-truthfulness-and-boundary-credibility.md](REVIEW-20260512-first-version-entry-truthfulness-and-boundary-credibility.md)
- 结论摘要：业务开屏入口与显式诊断命令已经真实落地，但 HUD 交互边界、诊断入口封闭性、配置模板扩展性、宿主图片能力前提以及业务 API 与内部页面体系的隔离程度，仍存在”文档比实现更乐观”的问题。

## 2026-05-18-browser-capability-gap-audit
- 类型：浏览器常用能力差距审查
- 详情文档：[REVIEW-20260518-browser-capability-gap-audit.md](REVIEW-20260518-browser-capability-gap-audit.md)
- 结论摘要：共核查 65 项浏览器常用能力（CSS 布局/样式/选择器、事件、DOM、表单），其中 27 项完整实现、8 项部分实现（声明与实现不一致）、30 项完全没有实现。发现 `cursor` 属性声明链路完整但系统光标从未映射、`overflow-wrap`/`word-break` 样式已声明但布局引擎未消费、`font-weight`/`font-style` 底层有能力但 CSS 属性层未暴露，三处属”文档比实现更乐观“。后续补齐状态：`cursor`、`overflow-wrap` / `word-break`、`focus()` / `blur()` / `scrollTo()` / `scrollIntoView()`、兄弟节点遍历、`font-weight` / `font-style`、`dblclick` / `contextmenu` / `transitionend` / `animationend`、`textarea`、最小 `select`、flex `order`、`calc()` 最小混合长度、`position:sticky` 首阶段闭环、`text-shadow`、`text-transform`、`text-indent`、`white-space:pre/pre-wrap/pre-line` 和单图 `background-image` 已落地；`font-family`、`display:grid`、`transform`、gradient、多背景、多重阴影、`float`、完整 `textarea` 软换行和完整浏览器原生下拉能力仍按详情文档边界处理。

## 2026-05-21-animation-capability-assessment
- 类型：动画系统能力评估与增强方案审查
- 详情文档：[REVIEW-20260521-animation-capability-assessment.md](REVIEW-20260521-animation-capability-assessment.md)
- 结论摘要：当前动画系统已完整实现 transition、keyframe animation（多段 stop、fill mode）、三级影响分层和事件派发；Phase 1 已补齐 transform（PAINT 级矩阵注入、命中测试反向映射）与标准 cubic-bezier，Phase 2 已补齐 animation-direction / infinite / 定位属性动画，Phase 3 首批已补齐 per-property transition timing、`steps(...)` 缓动、`ElementNode.animate(...)` 命令式入口，以及 `transitionstart` / `transitioncancel` / `animationstart` / `animationiteration` 事件。当前仍不承诺 keyframe per-stop timing、完整 Web Animations API 时间轴、暂停/反向播放等高级控制。布局引擎 `LayoutContext` 优化（pass-local 样式缓存、固有宽度缓存、positioned 预测量跳过）与动画方案完全兼容，不需要变更计划。

## 2026-05-20-ui-framework-structure-audit
- 类型：UI 部分代码框架结构审查（明确排除字体服务）
- 详情文档：[REVIEW-20260520-ui-framework-structure-audit.md](REVIEW-20260520-ui-framework-structure-audit.md)
- 结论摘要：该报告记录了当时 UI 分层、核心大类、示例/诊断代码、包结构、事件模板、input 反向依赖与命名规范等结构性风险，并给出 P0~P3 整改顺序。后续已完成死代码删除、事件取消语义统一、示例子包迁移、screen/style/control/host 包边界整理，以及布局、渲染、动画、Widget 等多处内部协作者提取。
- 当前状态说明：本条目只保留历史审查与整改方向；当前代码规模、热点文件和剩余风险以 [2026-05-25-project-code-structure-audit](#2026-05-25-project-code-structure-audit) 为新的基线，避免旧整改行数误导后续判断。
