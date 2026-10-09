# UILib 核心骨架实现设计

状态：核心实现设计，按增量维护；行为权威为核心与对应模块规格。

共同状态、布局、生命周期、输入与平台边界遵循[核心职责规格](<2026-10-07-uilib-core-contract-design.md>)；按职责选择规格见[规格入口](<../../开发者文档/规格文档/README.md>)。

## 1. 核心骨架实现设计

### 1.1. 目标与设计状态

用户在整体方案及工程方向补充后已授权开始任务，本节作为实施依据；生产实现按增量推进，不能以设计完成替代运行验收；核心及对应模块已认可职责继续有效。用户补充授权：本任务重构优先，允许破坏式更新；旧实现缺陷或耦合过多时可直接舍弃并重建，目标可为 5.0.0，不要求旧公共 API/行为兼容。实际版本仍通过仓库版本机制确定，发布、push、tag 不随该授权自动执行。目标是页面只声明内容、尺寸意图和关系，操作业务数据；状态、逻辑树、布局、命中和绘制形成可运行闭环。数学专项暂停深入。

在 reactive/scene/layout/input/paint/host 职责内设计一套核心闭环。现有能力是可评估的实现资源，不是必须保留的代码或接口：满足合同且职责清晰的部分复用，缺陷密集或迁移负担超过重建价值的部分直接替换。不得为旧兼容保留双系统或缝补帧末 flush；允许重新组织类与接口，并记录被替换能力如何进入新闭环。

### 1.2. 已核对的实现偏差

| 证据 | 与目标的差距 | 设计动作 |
| --- | --- | --- |
| Signal.set → ReactiveScheduler.queueWrite | 写入后仍可能读旧值 | 写入直接生效，调度只管理通知与显式副作用 |
| Computed 内部使用 Effect 和输出 cell | 首次与后续值依赖 flush，占位可能成为业务读值 | 纯派生独立依赖追踪，读取时求当前结果 |
| SceneRuntime.bind 使用普通 Effect | 业务副作用与呈现绑定共用帧调度 | 分离业务 Effect 与呈现 Binding 调度 |
| SceneFramePipeline 含帧末 FLUSH 和 observer SETTLE | 框架排版依赖状态物化、布局读回再写声明 | 状态独立；普通排版在约束求解内完成，锚定在布局阶段处理 |
| SceneNode 公共结构/缓存 setter | 逻辑描述和框架结果写入边界不清 | 构建声明保留；挂载后由 runtime 协调结构，布局结果只读 |
| SceneHitTester 在父盒外提前返回 | 未裁剪溢出子树不可交互 | 分开父自身命中和子树遍历，统一裁剪与变换 |
| MountHandle.dispose 销毁 Owner 并摘树 | 卸载/重挂载与永久销毁缺少明确入口 | 保留节点声明，创建可释放的挂载期作用域 |
| PaintPlan 有 add/clear 等方法 | 只读命令列表不等于整个计划不可变 | 构建器与发布后的不可变计划分开 |

证据限于已读取方法；不据此宣称其余实现已合规。源码文件名不是冻结的公共 API。

### 1.3. 状态、纯派生与副作用

建议用 ReactiveDomain 表达合法状态访问上下文及独立副作用调度。每个域串行执行，状态访问发生在所属上下文；宿主及后台通过 execute(Runnable) 递交域内任务。UI 使用宿主串行执行上下文，独立业务域可使用调用方提供的串行执行器。后台不能直接改节点。首个切片的页面状态、派生与呈现绑定处于同一 UI 串行域；后台计算结果经该域 execute 递交后成为页面状态，不跨域直接 get 或建立依赖边。独立业务域照常可用，但跨域响应式绑定不属于本切片，不能将直接读另一域当成隐式支持。默认当前域保留简便构造方式，但不以全局 UI 帧为推进条件。

状态域当前接口如下：

    ReactiveDomain.execute(Runnable action) : void
    ReactiveDomain.batch(Runnable action) : void
    ReactiveDomain.signal(T initial) : Signal<T>
    ReactiveDomain.computed(Supplier<T> derivation) : Computed<T>
    ReactiveDomain.effect(Owner owner, Runnable action) : Effect
    Signal<T>.get() : T
    Signal<T>.set(T value) : void
    Computed<T>.get() : T

set 在域内立即写权威值，失效其依赖，再安排通知；batch 合并状态订阅及 Effect 执行，不隐藏写入，内部 get 仍见最新值。batch 不是业务事务，不提供回滚或隔离承诺。建议默认业务 Effect 在最外层域操作结束后执行，显式 batch 在结束后执行；读取 Computed 本身不推进 Effect。异步域在其执行器上执行这些边界，无 UI 帧参与。域内顺序操作保序；有顺序意义的输入/编辑/追加事件不能用状态合批吞掉。

Computed 与 Effect 共享依赖收集工具，但不是同一种执行单元。来源变化立即使派生链失效；get 在必要时递归求值并记录本次真实来源，分支变化解除旧依赖。缓存由当前依赖版本决定，首次读取直接求值，禁止注入占位模拟派生值。Owner 管理已建立的订阅；无 Owner 的独立 Computed 由持有者负责释放跟踪，接口应提供 dispose()。初值 get 的域访问与 set 使用同一合法上下文；每个域操作在依赖失效完成后才执行业务 Effect，避免它看到半失效的派生链。Effect 的状态合并不等同事件流，独立有序事件入口不去重。递归求值循环属于诊断，不自动补值或重试。Effect 创建后首次执行发生在当前最外层域操作结束，无在途操作时由域启动一次调度；执行中写状态立即生效，下游及需要再次运行的 Effect 在本次回调结束后的队列中推进，不递归重入同一回调。Computed.dispose 解除上游依赖并使对象终止，再读取报告已释放，不能返回缓存冒充有效结果；Owner 释放其名下派生/Effect，独立持有者的对象不被 UI Owner 释放。

现有 create/get/set 入口仅在清晰表达新语义时保留，名称和签名可破坏式更新。Computed.create(initial, derivation) 的占位语义、flush 推进值的语义及时间旅行日志需要调用点迁移；不承诺双语义运行模式，也不删除尚未评估的诊断能力。

### 1.4. 声明与挂载生命周期

SceneNode 承载稳定身份及逻辑声明，框架结果不成为另一份业务配置树。新增自动 UUID 的节点身份；调用方不提供去重键来保证身份正确。静态页面构建可直接组合声明；挂载后内容、属性、显隐及子关系通过正式绑定或状态声明更新，handler 只发状态/意图。

NodeBinding 保存可重新激活的声明描述，挂载时返回 BindingActivation（AutoCloseable）。SceneRuntime 创建挂载期 Owner，将输入、环境、资源及呈现订阅放入该作用域。独立业务 Owner 不作为其子作用域。

    NodeBinding.activate(SceneRuntime runtime, SceneNode node, Owner mountOwner) : BindingActivation
    BindingActivation.close() : void
    SceneRuntime.attach(SceneNode parent, SceneNode root) : MountHandle
    MountHandle.detach() : void
    MountHandle.moveTo(SceneNode parent) : void
    MountHandle.destroy() : void

detach 摘树、释放挂载期订阅/输入/capture，并保留节点及声明；再次 attach 创建新的挂载期 Owner、读取当前状态，节点 UUID 不变，不重复订阅。moveTo 在同 runtime 内由框架一次协调转移逻辑父、挂载 Owner 的父作用域、环境/输入范围及清理目标，沿旧/新祖先失效几何，保持挂载与身份。Owner 的挂载期父链接须可由 runtime 转移，清理从当前挂载记录读取实际父节点，不捕获构建时旧父；旧父销毁后不得释放已移出节点，新父销毁按当前归属释放它。继承主题/环境及输入资格随新父更新，原 scope 的无关订阅不得跟着保留；必要重新激活的绑定先释放旧激活，再建立一个新激活。跨 runtime 的转移先 detach 后 attach，释放原宿主绑定。destroy 永久销毁子树并释放它的声明引用、缓存及持有资源，不销毁独立业务状态或共享资源。

公开生命周期入口区分 detach 与 destroy；旧 MountHandle.dispose 无保留或过渡兼容义务，调用点按新语义迁移。终止的挂载句柄不复用，重新挂载节点取得新句柄，不凭旧 dispose 名称推断永久删除。

### 1.5. 布局与扩展端口

SceneTextMeasurer、Constraints、LayoutBox 和现有布局策略按职责评估复用或替换，不冻结旧类型和签名。宽高使用一致尺寸语义：先求固定/自然/填充意图，再应用自身 min/max；固定尺寸不因父空间不足缩小。普通容器不隐含裁剪，滚动是显式视口策略。

首个切片针对列布局：标题、页签、操作区按自然尺寸，正文按剩余空间分配并滚动。填充分配先扣固定/自然占用及间距；多个参与者按权重分配并应用 min/max，触及边界者退出剩余分配。硬最小之和超过空间则按已声明溢出策略处理，不突破 min；取整余数仅向未达 max 的项目沿稳定子序分配，保持已分配总量一致。实现按所有 min/max 求共同水位，再对自由项使用整数有理分配；边界集合必须由最终水位确定，不能把暂时触及 min 的项目永久移出。数值由实际集成场景与独立探针验算。

自定义控件使用一个 Measure/Arrange/Paint 接口组合，测量声明依赖，内部排列返回子区域，框架保存结果：

    SceneControl.measure(SceneNode node, Constraints constraints, SceneTextMeasurer text) : MeasuredSize
    SceneControl.arrange(SceneNode node, LayoutBox allocated) : ChildPlacements
    SceneControl.paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) : void

MeasuredSize 为内容所需 logical 宽高；ChildPlacements 是对逻辑子节点的框架区域分配；SceneGeometryView 是只读几何查询。类型定义及布局策略复用均在 scene/layout 内完成，不把宿主句柄带入。输入仍复用 SceneInputRouter/事件，不另建自定义控件输入分发。测量/排列不写业务状态；绘制不重排。字体、语言、资源等实际依赖变化通知失效，缓存不靠每帧全树测量保持正确。

百分比参照、与自动尺寸的循环规则留给布局设计；首个切片不新增百分比能力，旧接口可替换，但现有百分比功能不因缺少细则静默丢失，须记录后续迁移范围。已有控件通过适配接入后再逐项对齐，不能以切片完成声称所有布局策略已完成。

### 1.6. 呈现批次与统一几何

SceneFramePipeline 只协调呈现，ReactiveDomain 不再由其 flush 物化值。呈现 Binding 的来源变更登记受影响绑定，runtime 在 prepare 时读取当前源并更新相关逻辑属性；不保存每条属性写请求，也不要求所有普通声明 setter 统一延迟。

拟议内部接缝：

    SceneRuntime.preparePresentation() : void
    SceneFramePipeline.prepare() : ScenePresentation
    ScenePresentation.geometry() : SceneGeometryView
    ScenePresentation.paintPlan() : PaintPlan
    SceneInputRouter.route(SceneEvent event, SceneGeometryView geometry) : void

ScenePresentation 持有同一次求解的只读几何与封闭绘制计划，输入范围与交互资格仍由路由器管理。几何查询使用该次发布的不可变区域/变换/裁剪条目，后续求解替换条目而不改已发布值；允许共享干净条目，不要求每批全树复制，也不能通过视图回读可变 cachedLayout 改变既有结果。几何查询由框架提供当前统一结果；不向业务公开版本选择，也不另立“最后已呈现结果”合同。

批次流程：宿主递交环境/完成通知 → 应用当前呈现绑定 → 对脏依赖测量与布局（含滚动/锚定） → 生成同几何绘制计划。路由几何相关输入前调用 prepare，必要时刷新；事件按宿主原顺序路由，前一事件修改状态后，下一几何事件所需的脏结果再 prepare。事件序列结束后 prepare 并 replay。干净状态复用结果，paint-only 变化不重测布局。后台完成不在布局或 replay 中途直接改树。

滚动位置属于控件呈现激活，不由业务保存像素累计。SceneGeometry封闭实际offset/maximum；布局只读，prepare后向运行时发布边界；输入在当前边界内移动，末尾不累计不可见偏移，内容或视口缩小时立即夹取。显式scrollOffset声明的变化作为跳转目标，滚动步长由组件声明；卸载释放所属状态。

布局、绘制和命中共同读取 SceneGeometry 的变换/裁剪结果，覆盖父盒外未裁剪子节点、滚动及浮层。命中遍历仅依据有效祖先裁剪/输入资格裁掉子树，父自身是否命中单独判断；重叠顺序沿绘制次序反向，portal 绘制层与逻辑归属分别处理。

PaintPlanBuilder 接收 add，build() 发布不可变 PaintPlan；发布后不再修改命令或片段。封闭范围包含命令中的列表、TextSegment、TextStyle 等可变载荷：构建时复制为不可变值或固定资源引用，读取端不返回可修改的样式对象。宿主对象例外仍通过[图像规格](<2026-10-07-uilib-image-spec.md>)专用适配声明内容；宿主内容及资源版本归适配器管理，不能据此放开普通文本样式的可变引用。复用现有 PaintCommand 和 backend/replayer，计划不能在 replay 时回读当前可变节点来重建布局。物品宿主专用路径仍按[图像规格](<2026-10-07-uilib-image-spec.md>)执行。

布局观察用途按实际调用点迁移：普通测量补偿移入声明/约束，锚定移入框架布局，必要只读观察保留正式入口。首个切片移除其所依赖的 observer 读回反写闭环，不批量删除尚未评估的现有观察能力。

### 1.7. 诊断与迁移边界

无效声明、派生循环及非法布局按已有诊断职责关联节点/规则/阶段，并将失败返回宿主本次调用。未完成的计算不能发布为有效几何或计划；本设计不加入旧结果继续呈现、替代布局、重试、补偿、回滚或恢复。已认可图像/数学失败合同保持其原范围，不扩大到骨架所有错误。

首次实现应在独立代码 worktree 按依赖迁移已有调用点，不覆盖主仓协作者改动。目前生产代码、测试及下游适配尚未变更。用户已开放 UILib 破坏式重构及可达 5.0.0 的目标；旧 API、类和测试不是保留门槛，不预建双 scheduler 或旧行为开关。下游迁移记录实际影响，业务仓修改仍按跨仓授权执行；发布、push、tag 仍独立授权。

### 1.8. 首个贯通切片及剩余范围

切片使用动态面板：业务列表和选择状态 → 多级派生标题/可用操作 → 自然标题与操作区 + 填充滚动正文 → 按钮事件改数据 → 自动重排和命中更新。再覆盖无裁剪溢出点击、模态隔离、卸载后重挂载以及永久销毁。验证先采用无需 Minecraft/Forge/LWJGL/GL 上下文的可自动化场景，再进入 MC 宿主。优先集成真实状态、声明、布局、输入及绘制计划，检查外部可观察结果；不把 mock 分层测试或方法级单元测试当默认流程，不要求 TDD。尽可能不新增单元测试，仅确有集成场景无法经济覆盖的高价值缺口才考虑。平台替身仅位于宿主边界，不替换核心机制；无平台采集不冒充字体真实光栅、GPU 或游戏验收。现有测试按行为价值评估，旧结构/时机快照不约束重构，也不因本次偏好无差别删除已有测试。

切片完成不等于全库完成。完整布局策略、IME/编辑器、全部标准控件、动效、资源后端、配置/网络/聊天/HUD 等继续按各自合同推进；本节已选接口应允许这些现有消费者迁移，不以尚未完成专项为由阻断核心闭环。
