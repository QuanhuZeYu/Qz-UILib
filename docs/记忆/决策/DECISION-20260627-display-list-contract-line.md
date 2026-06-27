# DECISION-20260627：Display List 契约线阶段 1 落地 + 并发框架方向

## 状态
已拍板（2026-06-27，用户全盘采纳 Oracle 推荐）。实施进行中。

## 决策缘起

SceneSlider 在用户使用中暴露「松手提交偶发丢失」缺陷，根因诊断定位到
**Signal 与渲染帧严重绑定**——`draggingValue` 走 `queueWrite` 帧末 flush，
但 UP handler 同帧读回依赖"写后同帧可见"，契约错配。

用户由此提出 NORTH_STAR 级架构方向：**在框架内部引入 120tick 消息事件总线
线程，明确分割数据态与渲染态**。经 Oracle 两轮严肃评估（含源码核实），
得出关键认知翻转：

- **契约错配与线程化正交**：slider 缺陷根因是"同帧写后读"的单线程时序问题，
  并非"数据态/渲染态分离"问题。线程化只把错配换成更难调试的跨线程内存可见性 race。
- **独立总线线程不可行**：reactive 内核是 `ThreadLocal<Effect>` 单线程依赖追踪
  + 裸 `LinkedHashSet` 订阅图，无并发原语；GL 上下文绑定 MC 主线程；
  测试套件数十处依赖单线程确定性 `set→flush→assert`。
- **slider 用控件级事件总线是错误方向**：破 R1/R7 + 是 reactive 劣化重造
  + 解决错问题。slider 修法甲（UP 用事件自身坐标当场算提交值，不读回刚 set
  的瞬态 signal）3-5 行可解，但用户拍板：**slider 不修，等并发重构后整体重写**。

## 真正可行的方向：Display List 契约线 + 子树并行

Oracle 第二轮评估的关键洞察：用户真正想要的是**目标 A（多核加速子树解析）**，
不是目标 B（数据态/渲染态独立线程）。目标 A 的 80% 收益可建立在
**单线程 reactive 内核 + 并行渲染管线**之上：

- layout/paint 并行的输入是「已 flush 定稿的 signal 值 + 树结构」，**只读不写 signal**
- flush 仍单线程 → **保住全部现有确定性测试**（set→flush→assert 不变）
- 不需要碰依赖图并发化、ThreadLocal 上下文跨线程、effect DAG 等高复杂度改造
- **这正是 NORTH_STAR 第 10 节预留的 Display List 契约线方向**：
  宪章第 75 行「Display List 可双缓冲做线程并行」、第 247 行「信条六的长期回报」
  ——用户的诉求不是另起炉灶，是兑现宪章预留的回报

## 阶段路径（已拍板）

| 阶段 | 内容 | 工作量 | 破坏不变量 |
|------|------|--------|------|
| 1 | Display List 契约线落地：paint/replay 两子调用 + 引擎无状态化（探针 per-call） | 3-5 天 | 不破坏 |
| 1.5 | epoch 失效链外置（方案 A：SceneNode 自持 lastMeasuredEpoch + 局部自查） | 5-8 天 | 不破坏 |
| 2 | 子树并行 layout/paint（ForkJoinPool 分治递归）——兑现多核加速 | 2-4 周 | I6 反被强化 |
| 2 前置 | measurer 加固（方案 B：只读快照 GlyphRuntimeTables + FontMatcher.RuntimeTableBinding） | - | - |
| 3 | reactive 并发化（多线程写 signal，目标 C，可选，收益存疑） | 1-2 月 | 需信条附注 |
| 4 | 独立数据源线程（目标 B 正确形态，可选） | 视场景 | - |

**阶段 0（修 slider）已取消**——slider 等并发重构后整体重写。

## 关键决策点

1. **双缓冲推迟到阶段 2**（不是阶段 1）：单线程串行 + plan 每帧 new 重建的现状下，
   双缓冲无消费者、无价值，是 YAGNI。阶段 1 只切 paint/replay 两子调用
   + 验证 PaintPlan 可延迟 replay。双缓冲随阶段 2 worker 一起落地。
2. **epoch 外置剥离为阶段 1.5**：牵动 SceneNode 数据结构 + 一帧两调语义
   + overlay 隔离，风险等级与探针 per-call（纯机械）不同，不应混批。
   归属方案 A（SceneNode 自持 `lastMeasuredEpoch` + 局部自查，与 `lastConstraints` 同构）。
3. **paint 阶段 measurer 依赖登记为阶段 2 阻断项**：`ScenePaintEngine` 在
   paint 时调 `measurer.measureWidth()`（`:305,:309`），使 paint 产出依赖
   measurer 共享可变状态（widthCache）。比 TextStyle 危险得多，已写入 I6 补注，
   防阶段 2 误判"值类型就能并行"。
4. **"渲染即重绘"非信条**：Oracle 发现 NORTH_STAR 无此信条（信条三是列表 diff）。
   用户拍板"Oracle 按真正受影响的信条六补注即可"。

## 宪章修订（已落地 NORTH_STAR.md）

| 条目 | 形式 | 要点 |
|------|------|------|
| 信条六 | 补注 | 两阶段已落地，"渲染"指 replay 阶段，PaintPlan 自包含可延迟 replay |
| 第 10 节 | 补注（新增 10.1） | 已落地点 = 契约线切分 + 无状态化，双缓冲仍标预留（推迟阶段 2） |
| I6 | 补注 | 并行强化（PaintPlan 唯一交付物）+ measurer 缺口登记阶段 2 阻断项 |
| I7/I8 | 不改 | 探针是测量手段不动结果约束 |

## 阶段 1 实施计划（第一批，3-5 天低风险）

### 第一步：TextStyle 验证（零工作量）
已由 Oracle 直接读源码确认 `TextStyle.java:14-31` 全 final 不可变。
**完成态，无需改动**。

### 第二步：探针 per-call 化

#### 2.1 ScenePaintEngine（单字段，先做）
- 新增 `PaintResult { PaintPlan plan; int regeneratedFragmentCount; }` final 不可变
- `paint()` 返回 `PaintResult`，移除实例字段 `regeneratedFragmentCount`（`ScenePaintEngine.java:56`）
- 探针在 paint 过程中累加进局部计数器，末尾打包进 PaintResult
- 调用点 `AbstractSceneHostWidget.java:112`：`PaintResult result = paintEngine.paint(root); PaintPlan plan = result.getPlan();`，后续 replay 用 plan
- 生产读探针点 `SceneStressTestHostWidget.java`（3 处）改为保存 LayoutResult/PaintResult 引用读探针
- 测试断言 30+ 处 `engine.__getRegeneratedFragmentCount()` → `result.getRegeneratedFragmentCount()`

#### 2.2 SceneLayoutEngine（5 字段，工作量大但纯机械）
- 新增 `LayoutResult { int relayoutCount; Set<SceneNode> relayoutedNodes; Set<SceneNode> constraintRelayoutedNodes; }` final 不可变
- `layout()` 返回 `LayoutResult`，移除实例字段：
  - `relayoutCount`（`:83`）
  - `relayoutedNodes`（`:89`）
  - `constraintRelayoutedNodes`（`:98`）
- **保留**：`measurer`（注入依赖）、`lastMeasureEpoch`、`measuredTextNodes`、`lastRootConstraints`
  （epoch 失效链 + 约束变化属阶段 1.5，本期不动）
- 注意 host 一帧调两次 `layout()`（:98, :104）+ overlay 各自 engine 调 layout:
  - 主树第一次 layout 的 result 不被消费，第二次才是有效探针
  - overlay 的 result 当前不被消费，保持原状即可
- 测试断言 100+ 处机械改：`engine.__getRelayoutCount()` → `result.getRelayoutCount()`
  等。用 ast-grep 批量改写降低成本

### 第三步：paint/replay 契约边界固化
- 在 `AbstractSceneHostWidget.render()` 内 paint 子调用 + replay 子调用前后
  加注释边界，明确两段独立子调用契约
- 新增「paint 产 plan 后做无关操作再 replay，结果一致」的测试锚点
  验证 PaintPlan 可延迟 replay（单线程内验证，为阶段 2 跨线程铺路）
- **几乎零代码改动**，主要是注释 + 测试

## 阶段 1.5 实施计划（后续，5-8 天中风险）

### epoch 失效链外置（方案 A）
- SceneNode 新增字段 `lastMeasuredEpoch: int`
- layout 遍历到文本叶时局部判断「`node.lastMeasuredEpoch != currentEpoch`」→ 自标脏
- 移除 `SceneLayoutEngine` 的 `lastMeasureEpoch` + `measuredTextNodes` 实例字段
- 保持 epoch 失效链"只标自己 + 向上冒泡、绝不向下递归"（守 I7）
- 保持主树 / overlay 的 per-tree 隔离
- 回归锚点：epoch 变化触发文本叶重测、一帧两调不重复标脏、overlay 文本不被主树 epoch 误标

## 阶段 2 前置：measurer 加固（方案 B）
- 只读快照 GlyphRuntimeTables：reload 时整体换引用而非 Arrays.fill 原地清
- 利用 FontMatcher 现有 volatile 换绑模式（`:32, :53-55`）
- 解决 paint 阶段调 measurer 的并发安全隐患，扫除 I6 已登记的「阶段 2 阻断项」

## 已知风险（按优先级）

1. **测试批量改写遗漏风险**：100+ 处 `__getXxx()`，ast-grep 改写后需全量跑测试
   确认无遗漏。`SceneLayoutEngineTest`（41 处）是重灾区。
2. **生产 host 读探针的迁移**：`SceneStressTestHostWidget` 3 处易漏。
3. **一帧两调的 PaintResult 语义**：host 一帧两次 layout，第一次 result 丢弃。
   需 fixer 核对集成测试是否依赖累积语义。
4. **overlay engine 的 LayoutResult**：当前不被消费，确认是否需保留接口。
5. **小树负优化（阶段 2 风险）**：<100 节点 fork/join 开销可能 > 收益，
   必须设规模阈值 + 真机帧率实测。
6. **GL 单线程硬墙**：replay 永远主线程（阶段 2 已绕开：worker 生成 Display List，
   主线程 replay）。

## 决策来源
- 触发：SceneSlider draggingValue 缺陷 → 用户提出独立总线方向 → Oracle 两轮评估
- 第一轮 Oracle：否决独立总线，建议局部修法甲
- 用户拒绝"修 slider 不动架构"，明确"喜欢并发框架，希望并发加速子树解析，已写三个月不是小型库"
- 第二轮 Oracle：认知翻转——目标 A 不依赖目标 C，正确落地形态是 Display List 契约线
- 用户全盘采纳 Oracle 推荐：双缓冲推迟 + epoch 剥离阶段 1.5 + 三处宪章补注

## 关键约束
- 此决策属 NORTH_STAR 重大架构变更（信条六/I6 补注），已由用户拍板
- 帧率/真机实测必交用户跑（沙箱无 GUI）
- 阶段进展更新到 `.slim/deepwork/concurrent-framework.md`（持久进度文件）
- 宪章修订由 Oracle 评估给草案 + 主 Agent 整合 + 用户拍板 + 主 Agent 落地写入