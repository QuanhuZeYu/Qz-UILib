# Tab焦点导航实施计划

> **面向 agent 执行者：** 使用executing-plans内联执行，最终一次全新上下文审查。用户已授权“开始任务，完成后让我审阅”。

**目标：** Tab/Shift+Tab在有效范围内按逻辑声明顺序循环，完成后供用户审阅；自动滚入视口作为下一独立增量。
**架构：** SceneFocusRouter复用既有范围/token/资格权威。SceneFramePipeline用同一队列保留指针与导航递交顺序，逐事件声明结算。SceneTabInputAdapter只保存可用性、Tab物理段与历史Shift，宿主仍唯一消费者。
**技术栈：** Java8、ReactiveDomain、SceneRuntime/PaintPlan、既有SoftwareHost与真实LWJGL3 FBO、LWJGL2/MC原生键盘历史适配。
**开发模式：** test-driven-development，最小真实闭环先运行，再相关反例；宿主边界自动适配测试与只读核对。
**Spec：** [唯一权威规格](../specs/2026-10-07-uilib-core-contract-design.md)6.1—6.3。

## Global Constraints

用户确认135+c9528c1df8实机全部通过。复用已验收自动化测试机和宿主基本路径，自动通过基本跳过重复实机。
本次收小为导航，不含显露；初始与模态恢复合同不改，非模态复合显式范围、激活、ESC、文本编辑及IME后续实现。
核心不依赖MC/Forge/LWJGL；无第二焦点/导航表，无UUID或几何排序。只读控件有focusable声明即可纳入，不新增编辑状态。
新原生键盘适配仍由父类唯一Keyboard.next消费者递交；Tab消费后不再fallback；其他键沿既有父类处理。
用户要求完成后审阅，不部署、不启动游戏。完整build通过后按仓约定本地提交，保留当前分支/worktree，无push/tag/release。

## Review Focus

检查共享队列的混合事件及失败后的物理尾部；导航Effect失败也要取消先前pointer owner，导航尾部不得执行。检查历史Shift而非未来快照，失焦后等历史队列耗尽及Tab物理释放才恢复，重复事件只能延续被接收的按压段。

### 任务1：导航与键盘意图闭环

**开发模式：** test-driven-development。
**文件/接口：** SceneFocusRouter.navigate(boolean backward)受控命令，true表示有候选（单项也true），false为空/已close。SceneNavigationEvent.NEXT/PREVIOUS不携原生键码；SceneFramePipeline.navigation与pointer同队列，关闭拒绝递交。

- [x] 正反循环及clear后首末选择，真实绑定与PaintPlan同帧观察；单项不重复通知，空范围无焦点，不激活控件。
- [x] 动态顺序、资格、模态隔离/空内层/返回、旧token、只读边界、交错输入逐事件结算及Effect失败收尾。固定字体仅代替度量，无mock焦点。

### 任务2：宿主适配与渲染

**开发模式：** 真实自动GL及平台无关适配自动运行；原生调用边界只读核对。
**文件/接口：** SceneTabInputAdapter.availability/shift/tab/historyDrained保持历史事件和物理基线；CoreLoopScreen.handleKeyboardInput映射历史键码，handleInput在super消费完队列后确认基线，与鼠标gate独立。ClientFontServicesScenario复用真实FBO观察。

- [x] 首次按下、UP、repeat、左右Shift、开屏持Tab、失焦恢复与窗口可用性过滤，sink指向真实帧管线；不因鼠标按压阻止键盘。
- [x] 核心通过后真实GPU导航背景/反向repeat/失焦尾部像素通过，再接入MC键盘消费者。不把自动注入称作原生实机反馈。

### 任务3：审查与交付

**开发模式：** 自动完整构建与独立只读审查。

- [x] 最终完整build15s通过；默认配置的gpt-6.1-sol代理06cbeac4-82db-43ff-8582-ede0c188004a只读最终审查完成，无确定Critical/Important。原Astra不可用且无报告，不计审查通过；未改变源码或重复构建。
- [x] 按仓约定本地提交并保留当前分支/worktree供用户审阅。当前源码验证日志与审查结果保存build/navigation-review/，不部署或启动；本次规格内新功能无对应issue。

执行复用agent-environment.mjs与Gradle wrapper --offline --no-configuration-cache --console=plain。focusNavigationScenario注册coreSceneCheck/check，GPU复用clientFontServicesScenario；日志build/navigation-review/。当前核心导航9场景与旧焦点/范围/指针回归通过，实际GL11场景通过；首次夹具编译误用包内idle已改为公开synthetic激活行为核验，未扩大生产接口，不计行为失败。
