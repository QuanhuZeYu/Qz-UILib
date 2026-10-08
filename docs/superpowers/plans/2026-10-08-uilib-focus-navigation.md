# Tab导航与目标显露实施计划

> **面向 agent 执行者：** 必需子技能executing-plans。当前仅准备下一个目标，尚未开始生产实现；开工后沿既有refactor/uilib-core worktree执行，最终一次独立审查。

**目标：** Tab/Shift+Tab在有效范围内按逻辑声明顺序循环，目标离开视口时框架以最小必要滚动显露。
**架构：** SceneFocusRouter沿已验收的范围/token/资格权威选择。SceneFramePipeline复用逐事件结算，把指针与导航放入同一顺序队列；导航后使用真实SceneGeometryView和runtime已有ScrollPosition逐层显露，不用业务补滚动，不模拟滚轮事件。
**技术栈：** Java8、ReactiveDomain、SceneRuntime/SceneGeometryView/PaintPlan、既有SoftwareHost与真实LWJGL3 FBO；LWJGL2/MC只做键盘宿主适配。
**开发模式：** test-driven-development，先最小真实闭环，再合同反例；宿主新路径按具体证据核验。
**Spec：** [唯一权威规格](../specs/2026-10-07-uilib-core-contract-design.md)第6.1—6.3节及焦点生命周期。

## Global Constraints

用户确认135+c9528c1df8双实例实机验收全部通过。后续复用已验收测试机和宿主基本路径，自动化通过基本可跳过重复实机，不默认每次移入MC测试。
当前是目标准备稿，不将准备授权扩成实现授权。标识符接口是拟定设计，正式实现沿规格作局部可逆判断。
核心不依赖MC/Forge/LWJGL；宿主不持有第二套焦点目标、导航顺序或滚动权威。
原生输入仍只有一个消费者，不新增Keyboard.next或Mouse.next轮询；页面不先消费再fallback导致重复处理。
Tab顺序来自逻辑树，UUID、布局换行及几何绘制顺序不参与。隐藏/禁用/失活跳过；只读资格不因不可编辑而排除。
当前切片只覆盖页面/模态导航与显露；非模态复合控件显式范围、Enter/Space激活、ESC消费、文本编辑、制表符插入和IME分后续目标。
不主动启动实例、不push/tag/release；不修改版本与公共兼容策略。

## 文件与职责

- SceneFocusRouter：拟增加受控navigate(boolean backward)，复用有效范围和挂载token；无焦点时正向取首项、反向取末项，单项保持，空范围无。导航不执行初始/恢复目标策略，不激活点击或提交草稿。
- input/SceneNavigationEvent：只表达NEXT/PREVIOUS导航意图，不携带LWJGL键码；SceneFramePipeline同队列保留指针与导航输入先后、逐事件结算、失败后丢弃剩余意图并维持物理指针尾部记账。
- runtime/SceneRuntime：复用既有ScrollPosition、effectiveScrollOffset与prepareScrollPositions，拟提供像素位置受控更新入口，裁剪到当前边界、无变化不重复失效；挂载释放不留下滚动状态。导航显露不伪装dispatchScroll，不触发作者滚轮处理。
- SceneFramePipeline及必要的局部显露协作：由最终几何/clip/祖先链计算位置；按近到远滚动祖先逐层结算，用更新后几何协调外层。已完整可见无变化，大目标优先显露目标起点；编辑位置在未来编辑专题扩展。
- integration/FocusNavigationScenario与ClientFontServicesScenario：真实状态、声明、布局与Plan/FBO验证；CoreLoopScreen仅在核心及GL通过后接入首次Tab/Shift+Tab宿主意图。

## Review Focus

导航与指针不能分队列而改变先后顺序；挂载资格在每次导航前重新核验，不保留永久导航表。重点看嵌套滚动是否使用更新后几何，以及框架显露是否误触滚轮业务handler。具体反例在任务内验证，不另造通用输入框架或恢复机制。

### 任务1：逻辑顺序导航真实闭环

**开发模式：** test-driven-development。

- [ ] 在真实Scene/ReactiveDomain/NodeBinding/PaintPlan链中运行A→B→A、反向循环，以及clear后的首/末选择；焦点投影与回放同帧一致。
- [ ] 围堵当前范围模态隔离、空/单项、显隐启禁、卸载重挂载与逻辑children重排；窗口缩放/换行保持逻辑顺序，不调用业务激活。
- [ ] 接入同一事件队列，具体交错输入证明先前handler状态影响下一次导航；readOnly/关闭/失败与旧指针尾部合同保留。复用已有相关场景，避免重复测试同一实现。

### 任务2：最小必要显露闭环

**开发模式：** test-driven-development。

- [ ] 实际滚动区域中导航至视口外目标，观察runtime有效偏移、几何clip与PaintPlan；显露后可见，不用作者补位置。
- [ ] 已完整可见不滚动；嵌套区域按层协调；目标大于视口优先显露起点；内容/字号/窗口变化使用最新几何和边界，不积攒滚动债务。
- [ ] 使用具体布局输入并以代码验算预期偏移，再检查真实几何；导航不触发滚轮业务处理，失活/旧token不接受延迟显露。

### 任务3：渲染与键盘适配交付

**开发模式：** 自动化为主；仅新宿主输入边界按具体覆盖缺口采用runtime-driven-development。

- [ ] 复用现有真实GL/FBO观测导航高亮与显露，不另建测试机。核心与GL通过后才接入宿主Tab/Shift+Tab，保持原唯一事件消费者。
- [ ] 新键盘适配核验方向、Shift修饰、重复事件、窗口可用性及输入消费先后；自动化已覆盖的宿主基本路径不重复要求MC实测，确有未覆盖新路径时只补对应短步骤。
- [ ] 完整build、最终一次独立审查；满足验收后本地增量提交和交付，保留现有分支。无需因文档计数或“曾经失败”重跑正确实现。

执行入口复用agent-environment.mjs与Gradle wrapper --offline --no-configuration-cache --console=plain；核心任务注册入coreSceneCheck/check，GL复用clientFontServicesScenario，日志按目标集中保存。当前尚未实施或运行导航测试。
