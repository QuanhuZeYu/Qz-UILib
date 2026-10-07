# UILib 核心闭环实施计划

> **面向 agent 执行者：** 必需子技能：使用 executing-plans，按任务逐个实施本计划。步骤用复选框跟踪。

**目标：** 跑通动态面板的业务状态 → 声明 → 自动布局 → 输入 → 绘制闭环。

**架构：** 按唯一 spec 第 15 节的拟议设计重构 reactive 与 scene 接缝，保留现有布局、输入事件及 PaintCommand/backend。状态立即生效，纯派生按需求当前值；呈现绑定和业务 Effect 分离，布局/输入/绘制使用框架协调的几何。

**技术栈：** 目标仓现有 Java/GTNH Gradle 工程，Minecraft 1.7.10、GTNH/LWJGL3ify；不更改构建基线及版本策略。

**开发模式：** 稳定纯逻辑用 test-driven-development；宿主接入用 runtime-driven-development。各任务再次写明。

**Spec：** [唯一 spec](../specs/2026-10-07-uilib-core-contract-design.md)，第 2—10 节正式合同与第 15 节拟议接口。

**状态与授权：** 计划已编写，生产步骤均未执行。第 15 节尚待整体审阅；当前授权是核心设计，不把计划完成当作生产实现已获授权。数学专项暂缓。实机启动须按仓库授权边界执行。

## Global Constraints

- 页面只操作业务状态/意图与声明，不手动 flush/layout/paint，不直接改框架结果。
- 纯派生读取当前来源，不运行业务副作用；独立业务状态与 Effect 不依赖 UI 帧。
- 挂载后声明变化通过正式绑定或状态进入，普通 setter 不强制统一排队。
- 卸载与永久销毁分开；独立业务状态/共享资源不随控件销毁。
- 保留 scene、自有布局/输入/PaintCommand；物品宿主专用路径遵守既有例外。
- 不加兼容开关、重试、旧结果保留、补偿、回滚、恢复或并行 UI 框架。
- 不改版本，不 push/tag/release，不覆盖主仓既有改动；实现从隔离代码 worktree 开始。
- 计划是施工工具，功能目标和验收以唯一 spec 为准。

## Review Focus

- Signal/Computed 旧调用点依赖 flush 和注入初值 → 新语义可能使控件或工具的首次值/Effect 顺序变化 → 任务一检查实际消费者并跑完整 build。
- dispose 原挂载句柄同时摘树与释放 Owner → 重挂载可能重复输入/资源绑定 → 任务二用同一节点挂载、卸载、重挂载后只触发一次业务操作。
- 父盒外早退及 mutable PaintPlan → 画面/点击或先前计划可能漂移 → 任务三验证共同几何、溢出/裁剪和发布后不可变。
- GLFW/IME/GL 状态依赖真实宿主 → 软件检查不能证明实机 → 任务四明确真实操作，未执行就保留未验收状态。

## 文件与接缝

生产路径基于 src/main/java/club/heiqi/uilib/；标为“新建”的类型是拟议名称。

| 任务 | 文件 | 责任 |
| --- | --- | --- |
| 一 | reactive/Signal.java、Computed.java、Effect.java、ReactiveContext.java、ReactiveScheduler.java、Owner.java；新建 reactive/ReactiveDomain.java | 立即状态、纯派生、独立副作用及作用域 |
| 二 | scene/runtime/SceneRuntime.java、MountHandle.java、Binding.java；reactive/Owner.java；scene/node/SceneNode.java；新建 scene/runtime/NodeBinding.java、BindingActivation.java | 正式声明激活、身份、单归属与挂载生命周期 |
| 三 | scene/host/SceneFramePipeline.java；scene/layout/SceneLayoutEngine.java、SizingCalculator.java、FlexLayouter.java、SceneGeometry.java；scene/input/SceneHitTester.java、SceneInputRouter.java；scene/paint/PaintPlan.java、ScenePaintEngine.java、ScenePaintReplayer.java | 自动分配、共同几何、呈现/路由及封闭计划 |
| 三 | 新建 scene/host/ScenePresentation.java、scene/layout/SceneGeometryView.java、MeasuredSize.java、ChildPlacements.java、scene/control/SceneControl.java、scene/paint/PaintPlanBuilder.java | 窄接口类型，按 spec 第 15 节定义，不新建业务配置树 |
| 四 | 复用既有 host/control 的示例面板与诊断入口，入口从任务三的集成场景接入 | 宿主运行反馈；不另开产品 GUI 或业务仓 |

新增测试只用两份行为场景文件：src/test/java/club/heiqi/uilib/ui/reactive/CoreReactiveContractTest.java 与 src/test/java/club/heiqi/uilib/ui/scene/integration/SceneCoreLoopTest.java。已有相关测试按实际冲突调整，逐条以 spec 判断，不保留 flush 时机快照来抵抗新语义，也不批量删除未评估测试。

## 命令入口

在代码 worktree 使用已加载环境的 execWithEnvironment 直接运行 java，参数数组如下，避免 Windows bat 或 PowerShell：

    java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --no-configuration-cache test --tests club.heiqi.uilib.ui.reactive.CoreReactiveContractTest
    java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --no-configuration-cache test --tests club.heiqi.uilib.ui.scene.integration.SceneCoreLoopTest
    java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --no-configuration-cache build

每次命令先检查真实环境和 Git，非零结果先诊断再继续；这些命令本轮尚未运行。实机使用文档已有 runClient21 任务，需要明确启动授权。

### 任务一：没有 UI 帧也能使用的状态与派生

**开发模式：** test-driven-development。该测试替代状态语义的手工探针，防住已观察到的首值与旧值回归。

**文件：** 文件表任务一及 CoreReactiveContractTest。

**接口产出：** ReactiveDomain 的 execute/batch/signal/computed/effect；Signal.get/set、Computed.get/dispose，签名与 spec 第 15.3 节一致。Owner 纳入派生订阅清理。默认业务 Effect 在最外层操作/batch 结束执行，不由 UI 帧驱动。

- [ ] 在隔离代码 worktree 检查 Git、读取 spec/入口并定位旧 create(initial, derivation)、flush 及时间旅行调用点；保留未覆盖消费者的能力。
- [ ] 写行为场景：状态从 1 改为 2 立即读 2；派生为来源加一，再乘二，多级值立即更新；首次读取不用初值；batch 内连续递增均见当前值。数值 oracle 用测试表达式计算。
- [ ] 补同文件场景：派生分支切换解除旧依赖；只读 Computed 不触发 Effect；业务 Effect 在无 runtime 时仍执行；Effect 初次运行不等 UI 帧，回调内写状态在回调结束后推进下游；Owner/独立 Computed dispose 后退订且读取已终止派生报告释放。运行聚焦 test，确认失败来自旧语义。
- [ ] 让 set 直接生效；将依赖追踪从 Effect-only 改为派生/副作用共用工具；实现按需当前值及域内通知。迁移实际消费者，不设旧/新切换开关，不把业务日志当作状态事务。
- [ ] 聚焦 test 通过后运行完整 build；若旧测试失败，区分真实回归与旧时机断言，以 spec 修正。首个面板的状态/派生/呈现绑定使用同一 UI 串行域，后台结果经 execute 递交；禁止跨域 get/依赖和后台写节点，不预建跨域响应式同步系统。
- [ ] 检查 diff 及任务文件，关联实际 issue 后本地提交；确无对应 issue 时记录原因，不伪造编号。

### 任务二：同一节点卸载、重挂载与永久销毁

**开发模式：** test-driven-development。生命周期场景防止真实重复操作和卸载后通知访问节点。

**文件：** 文件表任务二及 SceneCoreLoopTest。

**消费：** 任务一 ReactiveDomain 与 Owner；**产出：** attach/detach/moveTo/destroy、NodeBinding.activate → BindingActivation，按 spec 第 15.4 节。

- [ ] 写同一节点的场景：自动身份稳定；跨父移动始终只有一个父；移动后销毁旧父，节点仍有效并按新父环境/输入范围工作，新父销毁才释放它；detach 后业务状态仍可改；attach 后读当前值；再次点击只调用一次；destroy 子树释放其绑定但独立业务状态继续可读写。
- [ ] 运行集成 test 确认真实失败；建立节点声明描述与挂载期 Owner，输入/环境/资源订阅由该作用域管理；同 runtime 移动同步迁移 Owner 父链接、当前清理目标及继承环境/输入资格，释放需替换的旧绑定激活再建立新激活，跨 runtime 迁移释放旧宿主归属。
- [ ] 保留可表达新语义的入口，dispose 作为终止挂载句柄的 detach 过渡入口；旧句柄结束，重挂载取得新句柄。handler 发布状态，框架按状态 reconcile 树，不在 handler 直接 append/remove。
- [ ] 聚焦 test 及完整 build 通过后提交；挂载失败复用原有清理和诊断，不增加恢复或自动重建。

### 任务三：动态面板与一致的布局、命中、绘制

**开发模式：** test-driven-development。仅测试闭环可观察行为，不锁内部阶段数量、字段或缓存布局。

**文件：** 文件表任务三及 SceneCoreLoopTest。

**消费：** 任务一状态与任务二挂载；**产出：** preparePresentation、prepare → ScenePresentation、geometry/paintPlan，以及 SceneControl.measure/arrange/paint。MeasuredSize、ChildPlacements、SceneGeometryView、PaintPlanBuilder 按 spec 第 15.5—15.6 节定义。

- [ ] 建立由状态驱动的面板：自然标题/页签/操作区，剩余正文独立滚动；按钮更新列表或选择，派生文案变长后自动重排。用既有 SceneTextMeasurer 的确定性度量构造软件场景，尺寸 oracle 用代码计算。
- [ ] 加必要边界到同一场景文件：未裁剪父盒外子节点可点；相同区域裁剪后不可点；窗口/字体变化后 paint 与 hit 坐标同源；连续事件按原顺序处理；模态阻止后台按钮。
- [ ] 运行 test 确认失败；迁移呈现 Binding，删去该切片对帧末状态物化与布局读回补偿的依赖。只处理实际受影响声明，不每帧全树重建。
- [ ] 固定/自然/填充一致应用 min/max，按 spec 分配剩余空间及取整余数。建立 framework-only 布局写端口与 SceneGeometryView 读端口，路由前 prepare 脏几何，事件后最终 prepare/replay。
- [ ] PaintPlanBuilder.build 返回封闭计划；保存已发布计划，再更新节点并生成新计划，验证前一计划内容不变；再修改构建时传入的文本段列表及嵌套 TextStyle，验证旧计划仍不变且读取端无可变样式泄露。共享片段与命令不引用可变节点重建布局，物品宿主例外由既有适配职责处理。
- [ ] 聚焦 test 和完整 build 通过；检查实际标准控件和浮层消费者迁移，保留未评估百分比/动画能力，不能按切片覆盖率宣称全库完成。提交可运行软件闭环。

### 任务四：真实宿主下复核闭环

**开发模式：** runtime-driven-development。该任务依赖真实输入、GL 及宿主缩放。

**消费：** 任务三动态面板及现有 SceneHostWindow/backend；**产出：** 运行反馈记录及交付配置复测，不增加新的业务公共接口。

- [ ] 检查实际示例面板与现有 host 接入口，将同一闭环场景接入；取得 runClient21 启动授权后启动目标实例，记录 commit、环境与操作入口。
- [ ] 在状态变化、布局输出及路由目标处加仅开发态日志，观察变更后自动重排/点击，调整窗口、缩放、字号；按闭环结果修正，而不新增 UI 帧状态调度。
- [ ] 操作未裁剪溢出按钮、滚动正文、打开模态并点击后台、卸载/重挂载，再验证独立业务状态与绑定次数。IME/编辑器全量验收属后续专题，此处不宣称已覆盖。
- [ ] 关闭临时诊断，在交付配置下复测；运行完整 build 并记录实际通过/失败。未获得实机反馈时明确软件已验证与宿主未验收，不能以 headless 替代游戏运行。
- [ ] 本地提交已验证增量；无 push、tag、release 或自动下游升级。

## 完成判据与后续

任务三完成得到软件贯通切片，任务四完成才有该切片的宿主运行证据。全库专项不在本计划完成声明中。完整布局策略、编辑/IME、全部控件、动效、资源与配置/网络/聊天/HUD 按唯一 spec 独立继续；暂不深入数学。当前没有执行任何代码任务或构建。
