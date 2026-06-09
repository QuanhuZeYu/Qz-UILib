# 审查报告

本文件作为审查报告索引，统一记录功能审查、开放化审查、易用性审查、代码评审结论等需要长期留档的结果。

详细报告存放在本目录下；本文件只保留指针、主题和必要摘要，避免单文件持续膨胀。

## 维护规则

- 新增审查报告后，必须在本索引增加条目，包含类型、详情文档和结论摘要。
- 后续复核发现旧审查存在误报、结论失效或能力边界变化时，必须同时更新原始审查报告正文和本索引。
- 原始报告正文使用 `后续复核（YYYY-MM-DD）` 批注标记，不删除历史判断；批注需说明当前正确口径和是否还需要修复。
- 只在本索引写摘要，不把完整修复过程或阶段流水账堆积到索引页。

## 2026-06-09-production-code-round8-remote-ui-runtime-lease
- 类型：全项目生产代码第八轮审查（远程 UI Runtime + Lease Protocol）
- 详情文档：[REVIEW-20260609-production-code-round8-remote-ui-runtime-lease.md](REVIEW-20260609-production-code-round8-remote-ui-runtime-lease.md)
- 结论摘要：只读审查当前远程 UI Runtime + Lease Protocol 第一阶段实现与高风险网络生命周期边界，发现 4 个问题：Net Stream/Fetch 超时只在下一次入站包到来时触发；远程 UI 固定 TTL 没有主动 lease 清扫，过期可见关闭只在后续操作时触发；客户端异步失败/旧回调路径没有完整终止本地 mount；新 lease 协议字段在 decode 阶段被补默认值，削弱显式 `surfaceId` / `contentRevision` / `closeScope` 校验。未发现 `NetService` 混入 keepalive / renew / remote UI 业务语义。
- 后续复核：2026-06-09 已修复 4 个 findings：Net 通用 timeout tick、`ui.remote` 主动 lease cleanup、客户端 mount discard / terminalize、协议 decode 显式字段校验均已落地并通过定向测试与 `compileJava`。

## 2026-06-09-production-code-round7
- 类型：全项目生产代码第七轮审查
- 详情文档：[REVIEW-20260609-production-code-round7.md](REVIEW-20260609-production-code-round7.md)
- 结论摘要：只读审查第六轮修复后的剩余高风险边界，发现并已修复 2 个问题：运行态 transform 统一参与 fixed containing block、clip chain、paint、hit-test 和 scroll metrics 口径；默认滚动和滚动条拖拽复用 hit-test suppression / `pointer-events:none` 口径，passthrough scrollable overlay 不再吃掉 wheel 或 scrollbar drag。remote UI session 本轮未形成新 finding，固定 TTL 与不隐式续期仍按现有决策处理。

## 2026-06-09-production-code-round6
- 类型：全项目生产代码第六轮审查修复
- 详情文档：[REVIEW-20260609-production-code-round6.md](REVIEW-20260609-production-code-round6.md)
- 结论摘要：修复 animation/scroll/remote/session/transition 生命周期 5 个问题：keyframe forwards fill 按 `animation-direction` 和最终迭代奇偶写入终值；transform 后滚轮与滚动条命中复用 hit-test 的 inverse transform 口径；`RemoteHudSubmitEvent.dismiss()` 改为 session 精确关闭；远程页面 screen 绑定 session/generation，手动关闭后旧 expired 不再弹错误页；`display:none` 中断运行中 transition 会派发 `transitioncancel`。

## 2026-06-08-production-code-round5
- 类型：全项目生产代码第五轮审查修复
- 详情文档：[REVIEW-20260608-production-code-round5.md](REVIEW-20260608-production-code-round5.md)
- 结论摘要：修复远程页面与远程 HUD session 生命周期 3 个问题：带 `sessionId` 的 HUD dismiss 不再按 `overlayId` 误关新 HUD；远程页面客户端用当前 session/generation 丢弃旧 stream 成功/失败回调；服务端远程 HTML session TTL 过期时向页面发送可见错误通知、向 HUD 发送 session-scoped dismiss，避免过期后提交静默丢弃或 sticky dialog 静默停留。

## 2026-06-08-production-code-round4
- 类型：全项目生产代码第四轮审查
- 详情文档：[REVIEW-20260608-production-code-round4.md](REVIEW-20260608-production-code-round4.md)
- 结论摘要：接续第三轮修复后继续审查 `src/main/java` 生产代码，发现 5 个问题：移除展开 select 子树后只清 top-layer 注册但未关闭控件状态；`UiStyleDeclaration.copyFrom(...)` 公开样式复制绕过 layout/paint 失效版本；transform 下 select top-layer popup 使用未变换布局坐标锚定；HUD 用 `visibility:hidden` 隐藏注册项可被显式 visible 后代在 shared scene 渲染时绕过；select popup 关闭后 hover/cursor 不立即刷新。

## 2026-06-08-production-code-round3
- 类型：全项目生产代码第三轮审查
- 详情文档：[REVIEW-20260608-production-code-round3.md](REVIEW-20260608-production-code-round3.md)
- 结论摘要：本轮避开 `/qzuilib test` 与测试文件主线，聚焦 `src/main/java` 生产代码。发现 4 个问题：`textInput.preventDefault()` 不能阻止内置 input/textarea 改值；HUD 交互预过滤漏掉 top-layer 后代导致 select popup option 点击不可可靠进入真实路由；挂载后的 `UiStyleSheet` 再变更不会触发布局/绘制缓存失效；移除已展开 select 子树后 top-layer 注册残留，造成状态泄漏和重挂载异常。

## 2026-06-08-ui-test-matrix-round2
- 类型：`/qzuilib test` 视觉/语义矩阵第二轮审查
- 详情文档：[REVIEW-20260608-ui-test-matrix-round2.md](REVIEW-20260608-ui-test-matrix-round2.md)
- 结论摘要：第二轮避开第一轮低优先级问题，转向断言强度和真实浏览器语义。发现 4 个问题：原生 `disabled` 表单元素仍可通过鼠标路径派发 `click`；Animation 自动断言可在未验证真实 transition/keyframe 生命周期和渲染样例时通过；`VIS-CTRL-007` 宣称 roving focus 但所有 tab 仍为 `tabindex="0"`；`VIS-CTRL-005` 选择 option 时直接调用 handler，绕过真实 top-layer 命中测试。

## 2026-06-08-ui-test-matrix-round1
- 类型：`/qzuilib test` 视觉/语义矩阵第一轮审查
- 详情文档：[REVIEW-20260608-ui-test-matrix-round1.md](REVIEW-20260608-ui-test-matrix-round1.md)
- 结论摘要：审查 registry、状态回写、批量运行、断言 runner 和测试覆盖一致性，发现 2 个中等优先级问题：`runAllAssertions()` 会污染各分组当前页索引，导致一键测试后打开分组默认停在最后一张样例；视觉状态模型已有人工通过/失败/已知缺口枚举和聚合逻辑，但页面没有人工结果回写入口，人工确认样例无法沉淀状态。

## 2026-06-06-ui-test-visual-assertions
- 类型：`/qzuilib test` 视觉样例断言代码审查与修复复核
- 详情文档：[REVIEW-20260606-ui-test-visual-assertions.md](REVIEW-20260606-ui-test-visual-assertions.md)
- 结论摘要：审查最新 CSS / Layout / Paint 视觉样例批次，发现 CSS 继承、overflow、block flow margin collapse、top-layer、scrollbar 与 host image fallback 多处样例/断言边界不一致。已修复可机器验证项；后续已将 scrollbar 的 overflow、scroll range 与 scrollTo 偏移升级为自动断言，track/thumb 几何与拖拽仍保留人工观察边界；host image fallback 仍为人工待确认诊断。

## 2026-06-01-capability-gap-recheck
- 类型：浏览器能力缺口复核（取代 2026-05-18 结论）
- 详情文档：[REVIEW-20260601-capability-gap-recheck.md](REVIEW-20260601-capability-gap-recheck.md)
- 结论摘要：以当前源码为准重新核实，确认 `REVIEW-20260518` 正文结论已严重滞后——其列为"待实现/部分实现"的 20+ 项（transform 主体、sticky、flex order、border-collapse、text-shadow/transform/indent、white-space 五模式、list-style、`!important`、`::before/::after`、结构伪类、后代/子代选择器、contextmenu、dblclick、transitionend/animationend、CustomEvent、cloneNode、DocumentFragment、`<a>` 链接、select/checkbox/radio 等）已完整落地。当前真实剩余缺口约 23 主项：12 项完全未实现、约 11 项部分实现，另发现 `DocumentScrollMetricsCalculator` 未跟随 fixed containing block 语义这一运行时一致性缺口。重点提示：剩余缺口需先按 A 类（已声明有意边界：Grid/float/gradient/transform 矩阵/baseline/完整 Web Animations/var() 等）与 B 类（性价比待评估真缺口）分级，避免把有意边界当待补缺陷。`REVIEW-20260518` 正文结论以本复核取代。

## 2026-06-02-flex-min-content-runtime-pages
- 类型：运行时页面浏览器语义回归复查与修复
- 详情文档：[REVIEW-20260602-flex-min-content-runtime-pages.md](REVIEW-20260602-flex-min-content-runtime-pages.md)
- 结论摘要：运行时页面在浏览器语义修复后暴露的主要显示风险源于库侧 row flex item `min-width:auto` 仍用 max-content 近似，导致可换行文本比真实浏览器更难收缩。已将 auto 最小宽度改为 CSS-like min-content 测量，并将诊断页、配置模板和字体排序等确需等分收缩的 row flex 子项显式设置 `min-width:0`。

## 2026-06-01-browser-semantics-phase2-audit
- 类型：浏览器语义一致性审查（Phase 2）
- 详情文档：[REVIEW-20260601-browser-semantics-phase2-audit.md](REVIEW-20260601-browser-semantics-phase2-audit.md)
- 结论摘要：Phase 1 合并后系统性检查全子系统，发现 28 处与浏览器标准不一致（高 9 / 中 13 / 低 6）。高严重度集中在：min/max 约束应用顺序错误、负 margin collapse 不完整、flex item min-width 默认值为 0 而非 auto、flex-basis box-sizing 转换条件错误、insertBefore/replaceChild 同父节点索引偏移 bug、position:fixed 不创建 stacking context、overflow+border-radius 裁剪缺失、disabled 布尔属性语义错误。P0 修复代价低且影响面大，建议优先处理。
- 首批 P0/P1 修复复核（2026-06-01，提交 `2d1bffa`）：逐项对照 DOM / CSS 2.1 / Flexbox / Positioned Layout 规范确认 8 项修复方向均向浏览器语义靠拢，离线复跑相关测试集全绿。明确三项后续未尽边界（非缺陷）：负 margin collapse 仅相邻兄弟完整、父子折叠路径仍为 max 近似（属 1.6 P3）；圆角 clip 为圆形近似且仅双向 overflow 都裁剪时生效；fixed 仍无条件清空祖先 clip chain（属 2.3 P2/P3），均划入后续批次。
- 第二批 P2/低风险语义修复（2026-06-01）：按工程化分组收口 DOM、事件、样式、绘制四类作者可见契约：`removeChild` 返回/异常语义、`querySelector*` 排除内部根节点、`focusout` 独立冒泡与焦点切换顺序、hover/active 状态通知不中断祖先、`border-collapse` 继承、`font-style` 布局失效、inset box-shadow 绘制层级。复核时确认报告中 `text-shadow` "非继承"结论与 CSS 标准不符，已作为审查误报保留继承语义。
- 第三批 P2 事件语义修复（2026-06-01）：补齐 `wheel` DOM-like 事件分发，滚轮输入先按 capture → target → bubble 触发 `DocumentElementWheelEvent`，再执行默认滚动；handler 返回 `true` 只停止传播，不取消默认滚动，`preventDefault()` 会阻止默认滚动。仍未收口项包括 fixed clip chain、父子 margin collapse 递归等影响面更大的布局/视觉语义。
- 剩余语义工程化修复（2026-06-02）：详情见 [REVIEW-20260602-phase2-remaining-semantics.md](REVIEW-20260602-phase2-remaining-semantics.md)。已收口空块/递归 margin collapse、row flex `align-content`、flex 交叉轴 auto margin、absolute auto margin 居中、table auto 内容列宽、textInput capture 阶段、transform fixed containing block 下滚动范围一致性；复核确认 sticky stacking context 为审查误报。遗留：`inline-block baseline` 需要行内布局盒延迟落位，单独处理。

## 2026-06-01-browser-semantics-phase2-followup
- 类型：浏览器语义修复代码审查（Phase 2 后续批次）
- 详情文档：[REVIEW-20260601-browser-semantics-phase2-followup.md](REVIEW-20260601-browser-semantics-phase2-followup.md)
- 审查提交：`7371007`（合并入 `73a46e1`）
- 结论摘要：**通过**。7 项修复全部方向正确，代码质量达到工程化标准，测试覆盖完整，回归测试全绿。修复项：`removeChild` 返回值与异常语义（WHATWG DOM §4.2.6）、`querySelector*` 排除内部根节点（WHATWG DOM §4.5.6）、`focusout` 独立冒泡事件与焦点切换顺序（W3C UI Events §4.3.7，顺序为 focusout→focusin→blur→focus）、hover/active 状态通知不中断祖先（CSS 伪类状态与事件分发层正确分离）、`border-collapse` 继承标记修正（CSS 2.1 §17.6）、`font-style` 变更影响级别从 PAINT 改为 LAYOUT、inset box-shadow 绘制层级修正（CSS Backgrounds Level 3 §9，顺序为 outset shadow→background→inset shadow→border）。遗留：`focusin`/`focusout` 的 `cancelable: false` 语义未区分（已知取舍）；wheel 事件 DOM 分发（3.4 P2）本批未覆盖。

## 2026-06-01-wheel-dom-event
- 类型：wheel DOM 事件语义修复代码审查（Phase 2 第三批）
- 详情文档：[REVIEW-20260601-wheel-dom-event.md](REVIEW-20260601-wheel-dom-event.md)
- 审查提交：`f846cce`（合并入 `9a283f3`）
- 结论摘要：**通过**。对应 audit 报告 3.4，方向正确、达到工程化质量而非临时补丁。核对要点：`DocumentElementWheelEvent` 同时暴露原始 `wheelDelta` 与浏览器式 `deltaY`（取反正确）；`dispatchWheel` 与 `mousedown`/`mouseup` 同构，capture→target→bubble 顺序正确；严格遵守 `DECISION-20260531`（返回 true 仅停止传播、`preventDefault()` 才取消默认滚动）；wheel target 由命中测试给出且不依赖 handler 注册，与默认滚动目标（最深可滚盒）解耦，符合浏览器 scroll chaining；滚动后 hover 刷新口径由 `consumed` 改为 `scrollState.getVersion()` 变化，更精确（修正 `ERROR-20260509` 根因）。遗留边界（非缺陷）：未实现 `deltaX` 横向滚轮、`deltaMode`/`deltaZ`、passive listener；fixed clip chain、父子 margin collapse 递归仍属后续批次。验证：`git diff --check` 通过；本轮离线 `test` 因 `ERROR-20260601` GitHub manifest 外部波动未复跑，待网络恢复补跑收口。

## 2026-05-25-project-code-structure-audit
- 类型：全项目代码结构深度审查（覆盖 UI / font / net / config / client / mixin / internal）
- 详情文档：[REVIEW-20260525-project-code-structure-audit.md](REVIEW-20260525-project-code-structure-audit.md)
- 结论摘要：当前主代码约 445 个 Java 文件、约 8 万行；项目主线设计仍然成立，对外入口与网络协议心智较克制，但内部能力扩张后出现第二层复用边界不足。审查时重点问题包括：稳定 API 清单与源码漂移、旧审查索引中的当前行数摘要过期、`ui.screen.example` 约 1 万行诊断/示例代码仍进主产物、远程页面与远程 HUD 的 session/Stream/提交逻辑重复、`ForgeConfigTemplateScreen` / `NetRuntimeSelfChecks` / `UiStyleDeclaration` 等子系统级大文件继续膨胀、public `__` 内部 API 与 input 反向依赖仍未收口。P0 文档漂移与旧索引过期摘要已完成整改；P1 已完成诊断页边界收口、远程页面/HUD session gateway 复用、配置模板绑定与文档构建拆分、网络自检注册/执行/远程 smoke 构造拆分；P2 已完成样式声明 paint-only slot 试点、HUD/TextArea/UiDocument 二轮拆分、`NetService` 内部协作者拆分和字体 mixin fallback 收口；P3 已完成兼容性收口：`__` public 内部 API 保持君子协定并补充文档边界，input 反向依赖改为注册抽象，远程图片缓存拆分 clear/shutdown，CUSTOM renderer 使用边界已补充到文档与 Javadoc。

## 2026-05-08-html-like-developer-usability
- 类型：HTML-like 框架开发者易用性审查
- 详情文档：[REVIEW-20260508-html-like-developer-usability.md](REVIEW-20260508-html-like-developer-usability.md)
- 后续整改：已完成根节点默认全视口、完整业务页面示例、结构节点与交互控件边界说明，以及诊断入口链路收敛；整改状态只在索引页保留摘要，不回写原始审查正文。

## 2026-05-12-first-version-entry-truthfulness-and-boundary-credibility
- 类型：第一版开发者入口真实性与边界可信度审查
- 详情文档：[REVIEW-20260512-first-version-entry-truthfulness-and-boundary-credibility.md](REVIEW-20260512-first-version-entry-truthfulness-and-boundary-credibility.md)
- 结论摘要：业务开屏入口与显式诊断命令已经真实落地，但 HUD 交互边界、诊断入口封闭性、配置模板扩展性、宿主图片能力前提以及业务 API 与内部页面体系的隔离程度，仍存在"文档比实现更乐观"的问题。

## 2026-05-18-browser-capability-gap-audit
- 类型：浏览器常用能力差距审查
- 详情文档：[REVIEW-20260518-browser-capability-gap-audit.md](REVIEW-20260518-browser-capability-gap-audit.md)
- **结论已失效**：正文"30 项完全没有实现"等数字与清单已严重滞后，现状以 [2026-06-01-capability-gap-recheck](#2026-06-01-capability-gap-recheck) 为准；本条目仅保留历史审查价值。
- 结论摘要：共核查 65 项浏览器常用能力（CSS 布局/样式/选择器、事件、DOM、表单），其中 27 项完整实现、8 项部分实现（声明与实现不一致）、30 项完全没有实现。发现 `cursor` 属性声明链路完整但系统光标从未映射、`overflow-wrap`/`word-break` 样式已声明但布局引擎未消费、`font-weight`/`font-style` 底层有能力但 CSS 属性层未暴露，三处属"文档比实现更乐观"。后续补齐状态：`cursor`、`overflow-wrap` / `word-break`、`focus()` / `blur()` / `scrollTo()` / `scrollIntoView()`、兄弟节点遍历、`font-weight` / `font-style`、`dblclick` / `contextmenu` / `transitionend` / `animationend`、`textarea`（含软换行两级行模型）、最小 `select`、flex `order`、`calc()` 最小混合长度、`position:sticky` 首阶段闭环、`text-shadow`、`text-transform`、`text-indent`、`white-space:pre/pre-wrap/pre-line` 和单图 `background-image` 已落地；`font-family`、`display:grid`、`transform`、gradient、多背景、多重阴影、`float` 和完整浏览器原生下拉能力仍按详情文档边界处理。

## 2026-05-21-animation-capability-assessment
- 类型：动画系统能力评估与增强方案审查
- 详情文档：[REVIEW-20260521-animation-capability-assessment.md](REVIEW-20260521-animation-capability-assessment.md)
- 结论摘要：当前动画系统已完整实现 transition、keyframe animation（多段 stop、fill mode）、三级影响分层和事件派发；Phase 1 已补齐 transform（PAINT 级矩阵注入、命中测试反向映射）与标准 cubic-bezier，Phase 2 已补齐 animation-direction / infinite / 定位属性动画，Phase 3 首批已补齐 per-property transition timing、`steps(...)` 缓动、`ElementNode.animate(...)` 命令式入口，以及 `transitionstart` / `transitioncancel` / `animationstart` / `animationiteration` 事件。当前仍不承诺 keyframe per-stop timing、完整 Web Animations API 时间轴、暂停/反向播放等高级控制。布局引擎 `LayoutContext` 优化（pass-local 样式缓存、固有宽度缓存、positioned 预测量跳过）与动画方案完全兼容，不需要变更计划。

## 2026-05-20-ui-framework-structure-audit
- 类型：UI 部分代码框架结构审查（明确排除字体服务）
- 详情文档：[REVIEW-20260520-ui-framework-structure-audit.md](REVIEW-20260520-ui-framework-structure-audit.md)
- 结论摘要：该报告记录了当时 UI 分层、核心大类、示例/诊断代码、包结构、事件模板、input 反向依赖与命名规范等结构性风险，并给出 P0~P3 整改顺序。后续已完成死代码删除、事件取消语义统一、示例子包迁移、screen/style/control/host 包边界整理，以及布局、渲染、动画、Widget 等多处内部协作者提取。
- 当前状态说明：本条目只保留历史审查与整改方向；当前代码规模、热点文件和剩余风险以 [2026-05-25-project-code-structure-audit](#2026-05-25-project-code-structure-audit) 为新的基线，避免旧整改行数误导后续判断。
