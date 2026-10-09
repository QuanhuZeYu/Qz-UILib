# ESC范围消费与宿主关闭实施计划

> **面向 agent 执行者：** 使用executing-plans内联实施，最终一次全新上下文审查；用户已授权开始。

**目标：** ESC当前有效范围先处理，未消费才页面策略及宿主fallback；不提前关闭整页。
**架构：** SceneNode.onEscape(BooleanSupplier)为正式声明。SceneRuntime.dispatchEscape()复用当前activeModal及资格，模态未消费后仅调用首个非模态页面根策略，不派给后台外层模态。页面策略与初始焦点采用同一页面根选择。SceneFramePipeline.escape(Runnable fallback)共用输入队列，含ESC的整批输入经ReactiveDomain.enqueue递交独立顶层操作，Effects成功后下一独立操作递交未消费结果；旧纯pointer/nav批次保留既有execute同步行为。runtime.alive与管线closed共同过滤输入/结果/replay；关闭/失败丢弃fallback，不隐式replay。SceneEscapeInputAdapter保持一段ESC只发一次意图，repeat不逐层关页，失焦与开屏持键需历史耗尽和物理释放恢复。
**技术栈：** Java8、ReactiveDomain、SceneRuntime/NodeBinding/PaintPlan、现有软件宿主与真实FBO、MC唯一原生键盘消费者。
**开发模式：** test-driven-development，真实核心闭环与具体反例，再GPU与宿主；不制造RED。
**Spec：** 唯一权威2026-10-07-uilib-core-contract-design.md第6.1。当前文本/IME内部消费、完整快捷键/非模态复合显式范围不并入。

## Global Constraints

业务处理发布状态或意图，不直接改挂载树。宿主不持有第二套范围权威。禁PowerShell，使用agent-environment.mjs调用Git/Gradle。复用已验收测试机基本不重复MC实机，不自行启动；本轮不部署，完成后本地提交供审阅。无对应issue不伪造编号。

## Review Focus

fallback必须Effects成功后且仍同一有效页面/管线；失败保原Throwable、物理尾部记账，close无replay。混合队列指针/Tab/ESC逐事件结算；modal消费不能在同一事件传播外层。平台只采集历史ESC，父类唯一Keyboard.next，无keydown重复fallback。

### 任务1：核心真实闭环

**模式：** test-driven-development。

- [x] 具名场景：嵌套退出与恢复、未消费模态→页面策略、click/Tab/ESC交错、单段repeat/失焦、Effect失败保原异常且无fallback、排队域Effect关闭拦迟到结果、handler/只读写边界。ESC11、旧Focus8/Scope14/导航9/Pointer28通过；日志build/escape-review/core.log。

### 任务2：GPU及宿主

**模式：** 复用自动化真实GL及平台无关adapter。

- [x] 真实FBO消费ESC恢复原高亮，不fallback；repeat不关闭，新按压未消费才fallback。GPU12通过，gpu.log；自动注入不冒称原生实机。
- [x] 核心/GL通过后MC内外模态显式关闭声明，历史ESC适配在父唯一键盘消费者递交，super.handleInput后物理基线；fallback核验mc.currentScreen与session再super.keyTyped。

### 任务3：检查、审查与提交

**模式：** 完整自动build与独立只读审查。

- [x] 最终完整build56s、core/GL全通过；同一6.1sol代理a0fa0a9e-82a1-4a9f-8208-a60a975936d8复核完成。真实nested frame提前fallback失败（nested-failure.log）用ESC整批顶层递交修复；runtime单独关闭晚结果加alive过滤。全局enqueue曾使旧滚动域内同步观察失败，已限定含ESC批次，最新final-regression.log旧完整核心/GL通过。审查新反例同域连续frame后纯click越过前ESC，用pendingIndependent让待结算期间所有后继批次继续顶层递交，finally/失败时一次释放；具体双frame ESC→click场景已运行通过，同代理最终复核已通过，无剩余Critical/Important。
- [x] 按仓约定本地提交供审阅，证据build/escape-review/，不部署/启动/push/tag/release。

运行Gradle wrapper --offline --no-configuration-cache --console=plain coreSceneCheck/clientFontServicesScenario/build，ESC任务注册check及coreSceneCheck。所有结果日志build/escape-review/。
