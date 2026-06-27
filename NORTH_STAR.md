# UI 系统设计导向标（North Star）

> 这是本项目的**中心思想宪章**。任何架构决策、模块设计、性能优化、API 取舍，都必须先与本文件对照。
> 当代码与本文件冲突时，**先改文件再改代码**——要么说服自己遵守，要么显式记录一次"偏离"并说明理由（见文末《修订纪律》）。
> 它的存在不是为了好看，而是为了在项目长大、人员更替、需求摇摆时，**守住那条不能弯的脊柱**。

---

## 0. 如何使用本文件

- **写新功能前**：读《核心信条》和《决策检查清单》，确认你的方案没踩《反模式》。
- **做性能优化前**：确认你优化的是《分级失效》里正确的那一级，没把成本推给上层。
- **设计跨模块接口前**：确认它没有违反《层间契约》和《关键不变量》。
- **评审代码时**：用《关键不变量》当 checklist，任何一条被破坏都应阻断合并。
- **架构争论时**：回到《第一性原理》，多数争论本质是忘了"我们当初为什么这么定"。

---

## 1. 一句话中心思想

> **数据层负责"用最小代价算出哪些节点的哪些属性变了"；渲染层负责"用最小代价把变化刷上屏"。Display List 与分级脏标记，是这两层之间唯一的合同。**

整个系统的所有设计，都是这句话的展开。读不懂某个模块为何如此设计时，回到这句话。

---

## 2. 第一性原理

我们不发明范式，我们汇聚已被验证的范式的优点，并用一个明确的代价（内存）把它们粘合起来。

1. **UI 是状态的纯函数投影**：`界面 = f(状态)`。永远通过改数据来改界面，绝不命令式地操作控件对象。
2. **一次状态变化，只应触达它真正影响的那一层**。系统的全部性能努力，都是在压低"重算的起点"。
3. **空间换时间是本项目的既定国策**。凡是能用常驻缓存换来"跳过未变部分"的地方，默认用内存换。
4. **可预测性与高性能不是对立的**。局部高性能（signal）+ 全局可追溯（中央事务），两者必须同时拥有。
5. **数据层与渲染层互不认识对方的内部**。它们只通过契约通信，任何一方都可被独立替换、独立测试。

---

## 3. 核心信条（Tenets）

每条信条都标注了**它来自哪个范式的优点**，以及**我们为它付出的代价**。

### 信条一：UI = f(state)，声明式优先
- **是什么**：组件描述"在某状态下界面长什么样"，不描述"发生 X 时把界面改成 Y"。
- **来自**：React / Elm / Flutter 的声明式心智。
- **拒绝**：命令式持有控件并 `widget.setText()` 式地手动同步（Qt/GTK 老路），这是状态-界面不一致 bug 的根源。

### 信条二：signal 直驱保留树，免全局 diff
- **是什么**：组件函数**只在挂载时执行一次**，执行时建立"signal → 节点属性"的细粒度绑定（effect）。此后 signal 变化只重跑对应 effect，直接 patch 单个属性。
- **来自**：SolidJS / Floem 的细粒度响应式——更新粒度是"单个属性"，不是"组件子树"，更不是"整棵树"。
- **好处**：根除 React 那种"父组件重渲染带着子树重跑、需手动 memo 剪枝"的负担。
- **代价**：依赖图、订阅表常驻内存（已接受）。

### 信条三：列表才允许 diff，且必须 keyed 且局部
- **是什么**：唯一需要协调（reconciliation）的地方是动态列表（条件渲染 / 循环）。用 key 对齐新旧子节点，只增删移动变化项。
- **红线**：diff 的范围**只能**收窄在列表节点内部，**绝不允许**退化成"重新 diff 整棵树"。

### 信条四：所有状态写入收口到中央事务
- **是什么**：signal 的写入不立即生效，走中央调度器：记日志 → 应用 → 调度帧末刷新。
- **来自**：Elm 的全局单向 + 可追溯。
- **好处**：① 批处理（一帧多次写入合并为一次刷新）；② 时间旅行调试（日志+快照可回放）；③ 单一审计路径（永远能回答"谁、何时、因何改了它"）。
- **代价**：状态快照历史常驻内存（已接受，正是它换来调试能力）。

### 信条五：分级失效，变化只触达最低必要层
- **是什么**：每个 effect 触发时必须声明它影响哪一级，只打对应脏标记：
  - `LAYOUT`：文本/尺寸/增删节点 → 重布局 → 重绘 → 重合成
  - `PAINT`：颜色/背景/边框 → 跳过布局 → 重绘 → 重合成
  - `COMPOSITE`：transform/opacity → 跳过布局和绘制 → 仅 GPU 重新合成
- **来自**：浏览器引擎（Blink）的 layout/paint/composite 三级模型。
- **铁律**：动画应尽量只用 `COMPOSITE` 级属性。60fps 动画的绝大多数帧**不得触碰布局层**。

### 信条六：Display List 是数据层与渲染层的唯一契约
- **是什么**：Paint 层产出与平台无关的保留式绘制命令序列；渲染层只消费它，翻译成 GL 调用。
- **红线**：**渲染层绝不认识 signal/组件/DOM；数据层绝不认识 OpenGL。** 任何跨越此线的代码都是架构污染。
- **好处**：两层独立开发/测试；换 Vulkan/Metal/WebGPU 只重写渲染层；Display List 可双缓冲做线程并行。
- **两阶段已落地（2026-06-27，契约线阶段 1）**：渲染过程已显式切分为 **paint 阶段**（`ScenePaintEngine.paint()` 产出不可变 `PaintPlan`，属数据层尾端 ⑥）与 **replay 阶段**（`ScenePaintReplayer.replay()` 消费 `PaintPlan` 翻译为 `UiRenderBackend` 调用，属渲染层 ⑦）。
  「渲染」在本项目特指 **replay 阶段**——把状态刷上屏；paint 阶段只产数据契约、不碰任何 GL。
  `PaintPlan` 自包含：产出后可被任意延迟 replay（线程并行的必要条件），其纯净性由 I6 守卫。

### 信条七：保留式渲染，GPU 端场景增量更新
- **是什么**：渲染层自身也不每帧从零构建。维护常驻的 GPU 场景，靠批处理 + 纹理图集 + 分层合成 + 脏矩形做增量刷新。
- **代价**：合成层纹理(FBO)、图集、字形缓存常驻显存/内存（已接受）。

---

## 4. 分层架构与职责边界

从状态到屏幕，自上而下。`│` 上标注的是**层间如何传导失效**——这些箭头比层本身更重要。

```
①  State 层          signal 原子  +  中央事务日志（唯一写入收口）
        │  依赖追踪 / 订阅
②  Reactive 层       effect / computed，依赖图
        │  effect 触发，定向 patch + 打分级脏标记
③  视图/组件层        声明式描述；组件函数只跑一次，建立 signal→属性 绑定
        │  挂载建树 / 列表 keyed diff
④  Element 树(DOM)    保留的持久节点，数据层与渲染层的交汇点
        │  脏标记向上传边界、向下定范围
⑤  Layout 层         约束/flex 求解，每节点缓存布局结果（增量布局）
        │  脏标记传播
⑥  Paint 层          生成 Display List（保留的绘制命令，分片缓存）
        │  ← Display List 契约线（双缓冲可跨线程）
⑦  Render 层         手搓 OpenGL：批处理 + 图集 + 分层合成 + 脏矩形
```

- **数据层 = ①②③**：职责是"用最小代价算出哪些节点的哪些属性变了"。
- **交汇点 = ④**：DOM 树是双方共享的事实，但访问方式受《层间契约》约束。
- **渲染层 = ⑤⑥⑦**：职责是"用最小代价把变化刷上屏"。
- **契约线在 ⑥/⑦ 之间**：Display List 是唯一跨线信息，可双缓冲交给独立渲染线程。
- **视口容器是布局模型的一等例外**：绝大多数容器高度 = 子内容高（bottom-up shrink）；
  `scrollable` 视口容器高度由约束/preferredHeight 决定、主动忽略内容撑大，
  内容超出部分由 paint 层 CLIP + `scrollOffsetY` 几何平移处理。
  滚动只走 GEOMETRY 级（不重排不重绘），守 I7/I8。这是 viewport/content
  高度解耦的正式表达，非偏离。
- **top-layer 浮层是渲染管线的一等扩展**：select/dropdown 等需脱离父级裁剪的浮空内容，
  注册为额外 paint root，各自持独立 layout 引擎/paint/缓存（per-root 隔离约束，
  防串味），在主树 replay 后按 anchor 偏移叠加。这是「单一 ⑤→⑥→⑦ 链」的
  受控多实例化，每 root 内部仍严格遵循 I6/I7/I8。

---

## 4.5 输入半环：`UI = f(state)` 的入口侧（与渲染层对称）

第 4 节那条链是「state → 屏幕」的**出口半环**。输入层是它的镜像——「平台事件 → state」的**入口半环**。两条半环在架构上完全对称：

```
平台原始输入（LWJGL/GLFW/MC）
        │  ═══ 入口契约线 PlatformInputSource ═══（平台无关的标准化事件帧，I10）
⓪  平台适配层        翻译原生事件→RawInputEvent：Y轴翻转/键码→SceneKey/修饰键归一/坐标转逻辑像素
        │  drainFrame() 帧封板
Ⓐ  标准化事件帧      SceneInputFrame（不可变快照：key/pointer/text 三列表 + 帧级指针位置/修饰键）
        │  hit-test（消费上一帧 LayoutBox 几何）+ capture↓/target/bubble↑ 传播
Ⓑ  Hit-Test / 路由   SceneInputRouter：权威交互状态机（hovered/focused/pressed 真值）
        │  handler 只写 signal（I11）
①  State 层 ────────→（汇入出口半环，复用既有 ②~⑦ 全链）
```

- **入口契约线 = `PlatformInputSource`**（⓪↔Ⓐ 之间）：平台概念止于适配层，scene 输入核心既不认识 LWJGL/GLFW，也不认识 MC。这是信条六「Display List 契约线」的**入口侧对偶**——出口线把数据层变化翻译成平台绘制调用，入口线把平台事件翻译成对 state 的改写。换平台只改两端适配器，核心一行不动（拟立为 I10）。
- **输入永远只改 signal**：事件命中节点后，handler 唯一职责是 `signal.set(...)`，写入经中央事务批处理（I2/I9），帧末统一重跑 effect、属性槽自动打分级脏标记（I4），走既有 layout→paint→Display List 增量管线上屏。**输入层自身不打脏标记、不碰几何、不触碰节点结构**——只在 `f(state)` 源头注入变化。
  事件传播是沿命中链的**只读遍历**，脏标记只在 handler 改 signal 后才在数据层产生，故「脏标记只向上冒泡、绝不向下递归」在输入接入后依然成立（拟立为 I11）。
- **交互状态由权威状态机持真值、按需 signal 暴露**：hover/focus/pressed 的真值与几何强耦合、转换边界复杂（pointer capture 冻结、disabled 跳过、焦点全局唯一），必须由 `SceneInputRouter` 集中裁决（吸收 Floem/Masonry/GPUI 的「框架内部状态机」共识）；
  但交互状态就是 UI 状态，要驱动样式就必须经 signal 暴露、用 `bind` 消费（吸收 Compose `InteractionSource` 的「交互状态一等可观察」），绝不允许 Router 命令式改样式。节点默认零交互 signal、零开销，声明关心时才懒创建——这就是「按需 signal 化」。
- **节点保持纯数据，输入能力声明式附着**：输入响应不是 SceneNode 的字段，而是经 `runtime.on(node, type, handler)` 声明式登记到 Owner 作用域（与 `bind` 对偶：bind 是 signal→节点，on 是事件→signal），随组件卸载自动退订。SceneNode 始终是「纯数据 + 脏标记载体」，不背命令式 handler 负担。
- **逃生舱极窄且大多被收口**：命令式能力（requestFocus/requestPointerCapture）不是「绕过 signal 改界面」，而是「请求状态机改权威真值、再经 signal 暴露」的受控命令；真正的逃生舱只剩只读几何测量（不写故不破 I1）和宿主层第三方桥接（不入核心）。
  这比 React 的 ref 逃生舱模型更纯——signal-first 架构能把 focus/scroll 收口到状态机，是本项目相对 React ref 模型的纯度优势。
- **传播与手势：先简后繁**：Phase 1 只做 target+bubble + stopPropagation 阻止上溯（覆盖点击、键盘到焦点、滚轮冒泡到滚动容器）；事件数据不可变、控制能力收口到 `EventContext`。capture 阶段、pointer capture、手势竞技场全部预留接口、暂不实现（YAGNI）。
  未来要加 capture/多 Pass，只扩 EventContext，不动事件数据形状。**手势冲突用 consumed 标记 + 多 Pass 解决，不引入 Flutter 式 Gesture Arena**（既定反模式警戒）。
- **浮层优先命中**：存在 active overlay 时，hit-test 按 top-first 先探各 overlay root，
  未命中再退回主树（`SceneInputRouter.hitTestWithOverlays`）。这是 Ⓑ Hit-Test 层的
  受控多入口，命中后仍走同一 target+bubble + handler 只写 signal（I11），
  不破单向半环。

---

## 5. 关键不变量（Invariants）— 评审时逐条核对

这些是**任何提交都不得破坏**的硬约束。破坏其一即应阻断合并。

- **I1**　界面状态只能经由改 signal 来改变，不存在第二条改 UI 的路径。
- **I2**　所有 signal 写入都经过中央事务，没有任何"绕过调度器直接生效"的写入。
- **I3**　组件函数无副作用、且生命周期内只执行一次；动态行为一律落在 effect 里。
- **I4**　每个 effect 触发时必须打出且仅打出正确的失效级别（LAYOUT/PAINT/COMPOSITE）。
- **I5**　diff 只发生在列表节点内部，且必须 keyed。全树 diff = 违规。
- **I6**　渲染层代码中不出现 signal/组件/DOM 概念；数据层代码中不出现任何 GL 调用。
  - **I6 并行强化（2026-06-27，契约线阶段 1）**：`PaintPlan` 是 paint 与 replay 之间的**唯一交付物**。replay 阶段除 `PaintPlan` 与 `UiRenderBackend` 外，**不得读取任何上游可变状态**（节点、signal、measurer、布局缓存皆不可碰）。
    `PaintPlan`/`PaintCommand`/`TextStyle` 全字段不可变（`PaintCommand` 全 final、`TextStyle` 全 final 均已验证），构造后即可安全跨线程移交。
    **当前已知缺口（阶段 2 阻断项，已还清 2026-06-27）**：paint 阶段仍在 `ScenePaintEngine` 内调用 `SceneTextMeasurer.measureWidth()`（文本对齐计算 `:305,:309`），使 paint 产出依赖 measurer 共享可变状态（widthCache）。
    **已还清（measurer 加固三步）**：
    1. `GlyphPageManager.runtimeTables` 字段 volatile 化（消除表引用发布竞态，worker 读到一致快照）
    2. reload 路径冗余原地清移除（靠换引用失效旧表）
    3. `DefaultTextMeasureService:139` 的 `synchronized(fontService)` 拆除（ensureLayoutRuntimeReady 内部已 DCL + getTextLayoutService 返回 final 字段，外层锁冗余）
    **measurer 并行契约（2026-06-27 精确化，纠正「全无锁」过度承诺）**：
    - **稳态命中路径无锁**：measureWidth 缓存命中时走 DCL 快速返回 → final 字段读 → volatile 表引用读，全程无阻塞锁（唯一原子操作是统计计数器 AtomicLong CAS，非数据互斥）。worker 可并行 measureWidth。
    - **miss 路径有两处 synchronized**：首次遇某字符撞 widthCache NaN 时，`DerivedFontCache.getDerivedFont`（synchronized this）与 `CodepointTextCache.getText`（synchronized）会串行化。冷启动/新字符首现时 worker 在此排队，预热后稳态零锁。阶段 2 在帧循环启动前由主线程预热常用字符集消除运行期串行。
    - **widthCache 写幂等**：多 worker 对同一字符并发 miss 时各自计算同值并写入（float 写原子 + 同值），竞态结果幂等无害。**已由 `ConcurrentMeasureWidthIdempotenceTest` 验证（2026-06-27，冷启动 miss + 稳态命中两组 N 线程齐发结果全等）**。
    - **reload 不与 worker 并发**：字体 reload 只能由主线程执行（`FontService.isCurrentThreadAllowedToReload` 线程守卫硬拦非主线程），scene 管线零 reload 触发路径，reload apply 点全在 render 之外的帧间隙/主线程同步路径。前提见下方 worker render-scoped 不变量。
    **阶段 2 配套（已完成 2026-06-27 步骤 2.0）**：「N 线程并发 measureWidth 同串结果全等」幂等断言测试已落地绿。`measuredTextNodes` 已换 `ConcurrentHashMap.newKeySet()`（SceneNode 未重写 equals，默认 Object.equals = 引用相等，与原 IdentityHashMap 语义等价；SceneNode 类注释已锚定禁止重写 equals/hashCode）。
  - **worker render-scoped 不变量（2026-06-27，阶段 2 并行安全命门防线）**：worker 线程必须严格 **render-scoped**——主线程在 `render()` 内 fork-join，所有 worker 在 `render()` 返回前 join 完毕，**禁止 worker 任务跨帧存活**。这是 reload/worker 时序隔离的唯一前提：reload 只能主线程执行且 apply 点全在 render 之外，只要 worker 不跨出单次 render 调用，reload 与 worker 严格时序隔离。一旦违反（如引入跨帧持久 worker 池缓存未完成任务），命门重新成立，届时必须改为「reload 前 join 所有 worker」或「reload 与 worker 共用读写屏障」。volatile 引用发布（`GlyphPageManager.runtimeTables`）作为兜底防线已就位，即使时序假设被未来破坏，worker 最坏读到旧表（结果仍自洽），不会读到半切换状态。
- **I7**　干净（未标脏）的子树在布局、绘制、合成三个阶段都必须被跳过，不得重算。
  - **I7 并行强化（2026-06-27，阶段 2 契约预登记）**：子树并行 layout/paint **不改变 I7 跳过语义**。并行只是把「干净子树跳过、脏子树重算」的 DFS 分配到多 worker，每 worker 内部判定逻辑与串行完全一致。worker 间不共享可变判定状态：脏标记在并行前已冒泡定稿（并行中只读不写）；几何变化经返回值归并、join 点串行点亮，不跨 worker 写祖先路标（方案 1，Servo/Bevy 行业背书）。干净子树在 fork 决策前即被整棵跳过，根本不参与 fork。
- **I8**　布局结果、Display List 片段、合成层纹理都必须可缓存且按脏标记复用。
- **I9**　一帧内的多次状态写入必须合并为一次刷新（批处理），不得逐次触发重排。
- **I10**　平台原始输入只能经 `PlatformInputSource` 契约线进入；`ui.scene.input` 核心包不得出现任何 `org.lwjgl` / `org.lwjglx` / `GLFW` / `net.minecraft` / `net.minecraftforge` 的 import。
  键码在核心层只以平台无关的 `SceneKey` 枚举表达，按钮以 `SceneMouseButton` 枚举表达；原生键码/扫描码仅作 `RawInputEvent`/`SceneKeyEvent` 的逃生舱字段携带，**不得进入任何核心分支条件**。
  这是 I6 在输入入口侧的对偶——信条六让数据层与渲染层只经 Display List 通信，I10 让平台输入与 scene 核心只经 PlatformInputSource 通信，两条契约线把 scene 核心夹成平台无关。
- **I11**　输入事件 handler 只能通过 `signal.set(...)` 改变 UI 状态，不得直接操作 SceneNode 的属性槽或树结构。唯一允许的受控逃生舱：① 只读几何测量（读 `LayoutBox` 等，只读不写）；
  ② `EventContext` 受控命令（`requestFocus` / `requestPointerCapture` / `stopPropagation`——改的是 `SceneInputRouter` 权威状态机，结果仍经 signal 暴露）；③ 宿主层第三方桥接（如打开 MC 原版 GuiScreen，仅允许在宿主适配层，不入 scene 核心）。这是 I1 在输入入口的具化。

---

## 6. 内存预算花在哪（既定国策的落地清单）

"内存换性能"不是借口乱占内存，而是**只花在让各层能跳过未变部分的缓存上**：

| 缓存项 | 换来的能力 | 对应信条/不变量 |
|---|---|---|
| 状态快照历史 | 时间旅行调试、回放 | 信条四 |
| signal 依赖图 / 订阅表 | 免 diff 的定向更新 | 信条二 |
| 每节点布局结果 | 增量布局 | I7, I8 |
| 每节点 Display List 片段 | 增量绘制 | I7, I8 |
| 合成层纹理 (FBO) | COMPOSITE 级动画（绘制成本归零） | 信条五 |
| 字形 / 纹理图集常驻 | 批处理不被纹理切换打断 | 信条七 |
| Display List 双缓冲 | UI / 渲染线程并行 | 信条六 |

**判据**：要新增一处常驻缓存时，先回答"它让哪一层得以跳过什么重算？"答不上来就不是合法的内存开销。

---

## 7. 反模式（Anti-patterns）— 见到就应警觉

这些是会**悄悄侵蚀中心思想**的常见诱惑。它们往往"眼下更省事"，但每一个都在掏空某条信条。

- **命令式补丁**：绕过 signal，直接抓到节点 `node.setX()` 改界面。→ 破坏 I1，制造状态-界面不一致。
- **万能脏标记**：嫌分级麻烦，一律打 `LAYOUT` 全量重排。→ 架空信条五，性能假装在优化。
- **组件里塞副作用**：在组件函数里发请求/读文件/起定时器。→ 破坏 I3，组件不再是纯投影。
- **状态散养**：到处放裸可变状态，不走中央事务。→ 破坏 I2，丧失可追溯与批处理。
- **全树 diff 复辟**：列表 diff 逐渐扩散成"反正整棵重新比一遍"。→ 破坏 I5，退回 VDOM 的老开销。
- **契约穿透**：渲染层为了"方便"直接读组件状态，或数据层直接发 GL 调用。→ 破坏 I6，两层焊死，再不能独立替换。
- **动画走布局**：用改 width/top 实现移动动画，而非 transform。→ 违反信条五铁律，每帧触发重排。
- **缓存不失效或过度失效**：缓存了但脏标记逻辑错，导致要么显示陈旧、要么从不命中。→ 架空信条六/七。

> 经验法则：当你想"就这一次，先这样绕一下"时，**那一次就是偏离的起点**。要么按宪章做，要么走《修订纪律》显式登记偏离。

---

## 8. 决策检查清单（动手前过一遍）

新增/修改功能前，自问：

1. 我是通过改 signal 来驱动这个变化的吗？（I1）
2. 这次写入走中央事务了吗？会和同帧其他写入正确合并吗？（I2, I9）
3. 我的组件函数还是"只跑一次、无副作用"吗？动态部分在 effect 里吗？（I3）
4. 这个变化的最低失效级别是哪一级？我有没有打多了？（I4，信条五）
5. 如果涉及列表，我用 key 了吗？diff 范围被限制住了吗？（I5）
6. 我有没有让渲染层碰到数据层概念，或反之？（I6）
7. 干净子树会被正确跳过吗？我的缓存会被正确复用和失效吗？（I7, I8）
8. 我新增的内存占用，明确换来了哪一层的"跳过重算"？（第 6 节判据）

**八条全过，才动手。**

---

## 9. 性能心智模型（出问题时按此定位）

掉帧时，**从上往下问"重算的起点是不是太高了"**：

- 一次小改动触发了整树重算？→ 检查信条二是否被架空（是不是在重跑组件而非重跑 effect）。
- 动画掉帧？→ 检查它是不是误用了 LAYOUT/PAINT 级属性（信条五铁律）。
- draw call 爆炸？→ 检查批处理是否被纹理切换打断（信条七，图集没用好）。
- 滚动不跟手？→ 考虑把滚动/合成放到独立线程，UI 线程卡顿不应拖累合成。
- 内存涨但不快？→ 检查缓存是否"占了但没命中"（脏标记失效逻辑错，违反第 6 节判据）。

> 黄金标准：**理想帧里，绝大多数节点在布局、绘制、合成三阶段都被跳过。** 任何"全量"操作出现在热路径上，都是 bug 而非特性。

---

## 10. 可选进阶：线程模型

沿 Display List 契约线切分，进一步压延迟（非必须，但方向预留）：

- **UI 线程**：事件 → signal → effect → 布局 → 生成 Display List。
- **Render 线程**：消费 Display List → OpenGL 提交。两线程靠双缓冲 Display List 交接。
- **Compositor 线程（激进档）**：滚动与 transform/opacity 动画完全在合成线程跑。即使 UI 线程卡住，动画与滚动仍跟手——这正是浏览器滚动顺滑的原因。

前提：契约线（信条六）必须干净，否则无法切线程。**这是信条六的长期回报，现在就别污染它。**

### 10.1 已落地的契约线切分（2026-06-27，阶段 1）

线程模型的**第一步地基已落地，但尚未真正起线程**：

- 渲染管线已切为 **paint 子调用**（产 `PaintPlan`）+ **replay 子调用**（消费上屏）两段，二者在 `AbstractSceneHostWidget.render()` 内**仍主线程串行**（先 `paint()` 后 `replay()`）。
- `PaintPlan` 已是自包含不可变交付物，满足「可延迟到任意时刻 replay」——这是切线程的**必要条件**，已具备。
- **尚未落地**：双缓冲 `PaintPlan`、真 Render 线程、plan 跨帧增量保留。这些留待阶段 3/4，届时 `paint()` 写 back buffer、`replay()` 读 front buffer，靠原子引用切换。

当前阶段的价值**不在并行**（单线程串行无并行收益），而在**强制契约纯净**：把 paint/replay 切成两段独立子调用后，任何「replay 反查节点 / paint 直发 GL」的污染都会立刻暴露，为阶段 2 切线程扫清地基。这正是信条六「长期回报」的兑现起点。

### 10.2 阶段 2 已落地：子树并行 fork-join（2026-06-27，帧内同步并行）

线程模型的**第二步已落地**——子树并行 layout/paint 经 ForkJoinPool 分治并行生成 Display List，主线程 `pool.invoke` 同步等待，plan 完成后串行 replay。这是「**并行生成 + 串行消费**」，**非双缓冲**——收益来自子树解析的多核并行，不来自生产/消费跨帧重叠。

- **专用常驻 `ForkJoinPool`**（`SceneParallelExecutor`）：进程级单例，并行度 `cores-1`（留一核给主线程跑 MC 循环 + GL replay，Unity/Unreal/Naughty Dog 游戏引擎强共识），worker 线程命名 `scene-layout-worker-N`，专用 pool 不用 `commonPool()`。行业背书：Servo/Flutter/Unity/Unreal/Rayon 全部常驻。
- **fork-join 分治**：layout/paint 子循环按子树节点数阈值分治（整树 <256 全串行 + 单子树 ≥64 才 fork，起步值真机校准）。worker 各自持局部探针片段/独立 PaintPlan 片段，join 点主线程串行归并。两遍遍历保证 appendAll/探针归并严格按 children 顺序（z-order / LinkedHashSet 确定性）。行业背书：Rayon/Java ForkJoinPool 用精确数据规模判断，Servo 用 chunk size 16-64。
- **D1 命门消除**：worker 内只设 self 脏位不 bubble，bubble 延迟到父 join 点串行补（方案 1，Servo/Bevy 行业背书）。paint 子树各产独立 plan 片段，父按 children 顺序 `appendAll` 合并，保持「父 PUSH→子片段→父 POP」嵌套 z-order。
- **worker render-scoped 不变量**：worker 必须 `render()` 内 fork、返回前 join，禁止跨帧存活。这是 reload/worker 时序隔离的唯一前提（字体 reload 只能主线程执行，scene 管线零 reload 触发路径）。
- **回退开关**：`PARALLEL_ENABLED` 默认 false，一键回退串行无需改代码。determinism 闸门测试验证并行开/关结果完全一致。
- **双缓冲继续预留**：双缓冲（异步：主线程 replay 帧 N 的同时 worker 生成帧 N+1）需 reactive 数据态快照（目标 B/C），属阶段 3/4。阶段 2 的并行边界是「单帧内 layout/paint 多核」，不跨帧。

---

## 11. 修订纪律

- 本文件是**活的宪章**，可以改，但改的成本应当被有意抬高，以免随意妥协。
- **允许偏离**，但偏离必须显式：在下方《偏离登记》追加一条，写明"违反了哪条信条/不变量、为什么、影响范围、何时回填"。隐性偏离（不登记就绕过）是唯一不可接受的行为。
- 信条（第 3 节）和不变量（第 5 节）的改动，应被视为重大架构变更，需要比改代码更慎重的讨论。
- 每次大版本，回看《偏离登记》：要么把偏离转正（改宪章），要么把债还掉（改代码）。

### 偏离登记（Deviation Log）

<deviation-log>

<deviation id="2026-06-17">
  <what>I7（干净子树在三阶段被跳过）在「列表项增删」场景未达成</what>
  <why>DOM 层 `markSubtreeLayoutMutation` 对容器 append/removeChild 无条件向下递归标脏全部后代（含 forEach keyed 复用、几何未变的稳定兄弟），layout 层 `resolveReusableLayoutBox` 的 version 闸门据此判定复用失败、真实重算。
  先验地基债（原全量重建模式同样整树标脏，被「反正都要重建」淹没；
  forEach 复用节点对象后首次暴露），非控件层/方向 A 引入。
  正确性无损（重算结果与跳过一致），属性能局部债。</why>
  <scope>DOM 层 `DocumentNode.recordStructuralMutation`/`markSubtreeLayoutMutation`；
  影响所有经容器增删的列表场景（控件层响应式重构方向 A 的 forEach 迁移控件）。
  批次 2（TreeView/Table/DataTable 列表密集型）受影响最大。</scope>
  <status>**✅ 已还清（2026-06-18，方案 X）**：
  oracle 裁决否决原「方向 1 批量 API」（过度设计），改用方案 X——
  `recordStructuralMutation` 把递归标脏降级为「只标容器自身 self+subtree + 向上冒泡」（< 10 行，reconciler/layout/删除路径零改动），「兄弟几何是否真变」下放给 layout 复用闸门（flex forced / block translatedTo / table 列宽维度已完备），DOM 层零 layout 语义守住 I6。
  回归锚点 `DocumentBreadcrumbControlTest` 已翻转为 I7 正向断言 `stableSegmentSubtreeIsNotDirtiedByListMutation`，新增 `DocumentNodeStructuralMutationDirtyTest`（5）+ `DocumentLayoutEngineTest`（3 端到端）。
  详见 `docs/开发者文档/errors/ERROR-20260617-dom-coarse-subtree-dirty-marking.md` 修复结案。</status>
</deviation>

<deviation id="2026-06-17">
  <what>scene 布局引擎 `fillParentHeight`（顶层填满父高）只支持 root，深层 fill 子节点的 top-down 约束传播未实现</what>
  <why>demo 需求只是 root 背景铺满 host 全高。
  深层 fill 需要把「约束高度」沿 fill 链向下传播 + 受约束节点订阅约束变化，而现有脏标记模型是 bottom-up（父高=子高之和）且 SceneNode 灵魂为「绝不向下递归标脏」。
  先前误判为「硬塞 top-down 约束下传会污染单向冒泡核心不变量」，oracle 复核后澄清：
  脏标记传播（I7 管，必须向上冒）与约束传播（本就是布局计算输入）正交，约束变化触发重算可靠「布局遍历时局部比较缓存约束」而非向下标脏。</why>
  <scope>scene 层 `SceneLayoutEngine.layoutInternal`（子约束构造）；
  影响所有「深层容器需填满父高」的布局场景（如嵌套面板、分栏布局填满，批 4 Table 滚动视口/批 5 编辑区填满强依赖）。</scope>
  <status>**✅ 已还清（2026-06-20，方案甲）**：
  实现 per-node `lastConstraints` 约束快照 + 两段式闸门（`childConstraintsWouldChange` 决定是否为后代下沉递归、`selfConsumesConstraint` 决定本节点是否因约束变化重算），约束沿 ROW 交叉轴向深层 fill 链下传，全程零向下标脏（I7 守住，干净装饰兄弟反证不被污染）、
  `priorKnownInnerHeight` 不回看子 cache（无循环依赖）。
  同轮修两角落缺陷：
  约束失高时深层叶 fill 回退 shrink（防缓存陈旧）、fill+大 preferredHeight 下传口径与 `computeHeight` 对齐（防留白）。
  回归锚点 `SceneLayoutEngineTest`：
  `depthFillChildGetsParentHeightThroughCleanMiddle`/`cleanDecoSiblingNeverRelayoutedOnConstraintChange`（I7 反证）
  /`columnFillChildrenDoNotOverflowParent`/`unchangedConstraintStillFullSkip` + 5 条边界反证（约束失高回退/fill+preferredHeight/混合链截断/非fill中间层截断/paint联动），scene 全套 328 测试绿。</status>
</deviation>

<deviation id="2026-06-20">
  <what>scene 布局引擎 COLUMN 容器**主轴**（高度）方向的 fill 子节点未完整实现；
  多个 fill 子仍回退 shrink-to-fit</what>
  <why>深层 fill 约束下传还清时（上一条方案甲），ROW 交叉轴高可多子共享同一下传高、无冲突；
  COLUMN 主轴高多个 fill 子需按比例分配父高，必须引入 flex-grow 求解器才能正确分配，超出本期范围。
  2026-06-23 P1-a 已放行「唯一 fill 子 + 固定兄弟高度可先验」场景：
  父侧不直接改写子盒高，而是在 `layoutInternal` 按子节点构造约束，把 `priorKnownInnerHeight - 固定兄弟先验高 - gap` 的剩余高下传给唯一 fill 子；
  若兄弟不可先验、无父高约束或存在多个 fill 子则继续回退 shrink。</why>
  <scope>scene 层 `SceneLayoutEngine.buildChildConstraints` / `childConstraintsWouldChange`；
  影响 COLUMN 中「固定标题 + 唯一 fill 视口」等场景。
  当前仍不支持多 fill 子权重分配、flex-grow、min/max、percent、margin、align-self。
  反证锚点 `columnFillChildrenDoNotOverflowParent` 保持多个 fill 子不溢出。</scope>
  <status>**部分还清**：
  单个 COLUMN fill 子吃剩余高已落地；
  剩余债为「COLUMN 多 fill 子按权重分配/flex-grow 求解器」。
  待出现真实多 fill 主轴分配需求时再引入 grow 权重与求解器，并重新核对 I7 约束变化订阅边界。</status>
</deviation>

<deviation id="2026-06-20">
  <what>scene 合成层 transform 通路原登记「只落地 translate（rotation/scale/skew 未实现），且 translate 量化到整数像素」——该 what 已与代码现实矛盾</what>
  <why>D2 用户拍板时的避险排除项。后续已逐步还清：Transform 类持完整 7 分量（translate/rotate/scale/origin），
  GL 矩阵 origin 三明治全部落地（UiRenderContext.pushTransform :885-895），translateX/Y 为 float 不量化。
  B6 FBO 方案落地后，transform+clip 叠加走 PUSH_TRANSFORM_LAYER 离屏图层路径正确处理 rotate 下 scissor 裁剪。</why>
  <scope>scene 层 ScenePaintEngine.paintNode（transform 门控判定）+ Transform 类（字段集）+
  UiRenderContext.pushTransform/popTransform（GL 矩阵）+ PaintContextCompositor.pushTransformLayer/popTransformLayer（FBO 离屏图层）。</scope>
  <status>**✅ 矩阵完整化已还清（2026-06-26）**：Transform 7 分量 + GL 矩阵 origin 三明治 + B6 FBO 离屏图层路径均落地。
  矩阵完整化本身不破坏信条五（无 clip 的 transform 走 GL 矩阵纯顶点变换，零重栅格化）。
  **但 B6 FBO 路径引入新的 I7 合成阶段偏离**，见下方 2026-06-26 B6 偏离登记。
  ——以下为登记时原文，保留作历史记录——
  **回填方向**：引入完整 Transform 矩阵字段（rotate/scale/skew）时，transform 改走渲染层现成的
  UiRenderContext.pushTransform/popTransform（GL 矩阵浮点通路，已支持 rotate/scale/origin）；
  届时须重核信条五铁律「合成动画绝不触碰布局/绘制层」在矩阵变换下仍成立（fragment 仍可跨帧复用、零重建）。
  决策依据见 docs/记忆/决策/DECISION-20260620-scene-composite-opacity-transform-dual-channel.md。</status>
</deviation>

<deviation id="2026-06-21">
  <what>scene 布局引擎容器节点无 shrink-to-fit（`computeWidth` 对有子节点容器恒返回 fill 满宽），批 2 `SceneBreadcrumb` 的 segBtn 被迫用 `label.length() * APPROX_CHAR_WIDTH(9) + padding` 估算段宽替代真实文本度量</what>
  <why>控件层 `create(rt, props)` 建树期访问不到 `SceneTextMeasurer`（仅 `SceneLayoutEngine` 持有，`SceneRuntime` 不持有），无法做真实度量；
  引擎容器 shrink-to-fit 未实现。
  若 segBtn（含 label 子节点的 ROW 容器）不设 preferredWidth，则 `computeWidth` 对有子容器恒返回 fill 满宽 → 首段占满父宽把后续段挤出画布。
  **与 Segmented 固定段宽非同性质**：
  Segmented 等宽是视觉设计规格（无错位概念），而 Breadcrumb 字符估算是对真实渲染宽的**估算替代**（估算字宽 9 ≠ 测试 stub 8 ≠ 真实非等宽字体），非等宽/长 label 下段宽与文字实际宽错位，盒宽<文本宽时存在文字裁剪风险。
  属引擎能力缺口的控件层绕行。</why>
  <scope>scene 层 `SceneBreadcrumb.segBtn` preferredWidth（`APPROX_CHAR_WIDTH` 估算）；
  影响所有「内容驱动宽度的横向容器」控件。
  当前 demo 短 label 正确性可接受，长/宽字符 label 有错位与裁剪风险。</scope>
  <status>**✅ 已还清（2026-06-22）**：
  新增 `SceneNode.WidthSizing { FILL, SHRINK }`，默认 `FILL` 零回归；
  `preferredWidth` 仍最高优先级。
  `SceneLayoutEngine` 在下传约束/约束变化判断阶段不读取子 cache，SHRINK 容器保守回退外部约束宽；
  子节点布局完成后再用缓存回收内容宽（ROW=子宽之和+gap+paddingH，COLUMN=子最大宽+paddingH，并 clamp 到 available outerWidth）。
  `SceneBreadcrumb` 删除 `APPROX_CHAR_WIDTH` 与 `setPreferredWidth(...)`，segBtn 改用 `WidthSizing.SHRINK`。
  回归锚点：
  ROW shrink、COLUMN shrink、available clamp、默认 FILL、preferredWidth 优先、Breadcrumb 段宽按 `STUB_CHAR_WIDTH=8` 真实测量、Breadcrumb 交互态零重排。</status>
</deviation>

<deviation id="2026-06-21-扩展">
  <what>scene 布局引擎 `computeHeight` 对 `scrollable==true` 视口节点**主动忽略内容高、直接钉死视口高**，打破「容器高 = 子内容高（shrink-to-fit / `max(natural, preferredHeight)`）」的纯 bottom-up 布局模型——
  首次让某类容器高度**不由子内容决定**</what>
  <why>滚动的本质是「固定视口 + 超长内容」，必须区分 viewport/content 两个高度。
  纯 bottom-up「容器永远 shrink 到包住内容」模型无法表达「容器不被内容撑大、超出部分裁剪滚动」。
  批 4 步骤 B 滚动地基（Tab 之后、Table 之前的独立前置步骤）引入。
  不破任何 I：
  `scrollOffsetY` 走 geometry 级（与 transform-translate 同构，layout 零重算 paint 零重绘）、滚轮 handler 只写 signal、CLIP 复用现有 clipChildren 通路——
  经主 Agent 亲读源码 + 7 测试反证验真（滚动帧 `relayoutCount==0`、`regeneratedFragmentCount==0`、CLIP 坐标恒定、content 绝对 top 0→-100）。
  仅 `scrollable` 让 `computeHeight` 主动忽略内容是布局**计算语义**的有意例外口子（类比 preferredWidth/Height 当初判断但更进一步——
  那是加优先级分支，本条是打破 bottom-up），按修订纪律显式登记。</why>
  <scope>scene 层 `SceneNode`（新增 `scrollOffsetY` geometry 级 + `scrollable` LAYOUT 级两字段）；
  `SceneLayoutEngine.computeHeight`（scrollable 分支优先级：
  preferredHeight 钉死 > fillParentHeight 吃满约束高 > 回退内容高且**有高度约束时 cap 到约束高**）；
  `SceneLayoutEngine.selfConsumesConstraint`（约束变化订阅闸门识别 fill 节点 + scrollable 回退分支节点，守 I8）；
  `ScenePaintEngine.paintNode`（scrollable 节点 CLIP 窗口用不含 offset 的绝对坐标 + 递归后代注入 `-scrollOffsetY` Y 基准）；
  **2026-06-23 补完几何探针对齐**：
  原仅 paint 注入 `-scrollOffsetY`，hit-test 与 `absoluteBox` 只读 LayoutBox 对滚动零感知，致外层 viewport 滚动后主树节点 hit 不跟随、overlay anchor 停在旧坐标（真机发现）。
  修复抽出单层注入原子 `SceneGeometry.childYBase(parent, parentAbsY)`（判 `parent.isScrollable()` 而非 self——
  scrollable 节点自己不被自己 offset 位移），`ScenePaintEngine`/`SceneHitTester.hitTestRecursive`/`SceneGeometry.absoluteBox`（回溯式沿 parent 链注入）三方共用同一原子定义，消除「三处独立累加」温床；
  `SceneHitTester` 新增 clip bounds 递归参数传递（进入 scrollable 子树用其 LayoutBox 绝对盒作 clip，嵌套取交集，类保持无状态），与 paint CLIP 对称（滚出视口不可点）；
  `AbstractSceneHostWidget.dismissOverlaysWithInvisibleAnchor` 独立步骤（trigger 滚出所有 scrollable 祖先视口零交集时 `entry.requestDismiss()` 走 signal 回调 `expanded.set(false)`，守 I11 逃生舱②；
  有交集即保持防半遮闪关；
  单帧绘制延迟可接受不特判）。
  影响所有 `isScrollable()==true` 的滚动容器（批 4 Table、批 5 编辑区、SceneSelect listbox、未来长列表）；
  非 scrollable 节点行为零变化。
  横向 `scrollOffsetX` 本期未做（YAGNI），滚动条排后续批，contentSize/viewportSize/maxScroll 全派生不存。
  **2026-06-23 maxScrollY 口径统一收口**：
  原外层 viewport 用 `contentBox.height - viewportBox.height` 少算 padTop+padBottom 致矮窗滚不到底（真机发现），内层 listbox 用 `max(child.getY()+height)` 另一套口径（隐患）。
  修复抽 `SceneGeometry.maxScrollY(scrollable)` 闭式 `max(0, maxChildBottom + padBottom - boxH)`（maxChildBottom=子节点 getY()+getHeight() 最大值，含 padTop 偏移），外层 viewport 与内层 listbox 同一调用，padBottom 显式参与根治。
  只读不标脏（I11 逃生舱①）。</scope>
  <status>**✅ 已转正（2026-06-24，大版本回看，oracle 评估通过 + 用户确认）**：
  scrollable 视口升为 §4 正式布局能力。回填方向（显式 viewport/content 双节点）
  降级为「未来可选优化」，非必须。回归锚点保留。
  ——以下为登记时原文，保留作历史记录——
  **更接近「应转正而非债」**——
  滚动是 UI 库固有一等能力。
  登记为「待评估转正为正式布局能力（scrollable 视口为一等公民）」。
  回填方向：
  未来若引入显式 viewport/content 双节点结构可消除此 computeHeight 特例。
  须经 oracle 评估后转正或保留。
  回归锚点：
  `SceneScrollViewportTest` 7 测试——
  视口高钉死 / 滚动帧 `relayoutCount==0`（I7）/ fragment 复用 `regeneratedFragmentCount==0`（信条七）/ CLIP 固定 / clamp / 几何偏移 top 0→-100 / 滚轮 handler 端到端；
  `SceneLayoutEngineTest` scrollable 分支3 cap/shrink/unconstrained + 约束变化重算（I8）。
  2026-06-23 探针对齐补完回归锚点：
  `SceneHitTesterTest`（判 parent 不判 self 专杀 / 滚动后 hit 命中跟随 / 滚出 clip 不可点 / 部分在可点 / 嵌套 scrollable 交集）、`SceneScrollViewportTest`（absoluteBox 嵌套 scrollable 回溯注入 / anchor 跟随滚动）、
  `ScenePaintEngineTest`（paint 下沉式与 absoluteBox 回溯式结果相等，单层注入原子一致）、`SceneAnchoredOverlayPipelineTest`（trigger 滚出零交集 dismiss / 部分可见保持 / 全可见保持）。</status>
</deviation>

<deviation id="2026-06-23">
  <what>第 4 节渲染管线模型（单一 ⑤Layout→⑥Paint→⑦Render 链）与第 4.5 节输入半环（单一 hit-test 入口）——
  P0 overlay 引入**多 paint root**（额外 layout/paint/replay pass）+ **独立 overlay hit-test 入口**（top-first 优先命中），超出宪章当前覆盖范围</what>
  <why>select/dropdown 等浮空控件需脱离父级裁剪、置于 top-layer，单一 paint root + 单一 hit-test 模型无法表达。
  overlay 是 UI 库固有一等能力（类比 scrollable 视口），P0 已实现多 root layout/paint/replay（per-root layout engine 防约束串味）+ overlay top-first 优先命中 + outside-click/ESC dismiss。
  不破 I1/I2/I6/I7/I11：
  overlay root 各自独立布局/绘制/缓存、干净子树照常跳过、dismiss 只写 signal、anchor 读取属 I11 逃生舱①只读几何测量。</why>
  <scope>scene 层 `AbstractSceneHostWidget`（多 root layout/paint/replay，per-root `SceneLayoutEngine`）、`SceneInputRouter`（overlay top-first 优先命中 + outside-click/ESC dismiss）、
  `SceneOverlayHost`、`SceneRuntime.portal`；
  影响所有 overlay 消费者（首个为 SceneSelect）。</scope>
  <status>**✅ 已转正（2026-06-24，大版本回看，oracle 评估通过 + 用户确认，
  SceneSelect 三批真机缺陷验收通过）**：overlay 多 paint root + 独立 hit-test
  升为 §4/§4.5 正式能力。
  ——以下为登记时原文，保留作历史记录——
  **更接近「应转正而非债」**——
  top-layer 浮空是 UI 库固有一等能力。
  回填方向：
  待 overlay 能力经 SceneSelect 等消费者验证稳定后，在第 4 节补「top-layer 作为额外 paint root + 独立 hit-test 入口」正式段落，转正为宪章一等能力。
  须经 oracle 评估 + 用户确认。
  回归锚点：
  `SceneOverlayPipelineTest`/`SceneOverlayHitTestTest`/`SceneOverlayDismissTest`/`SceneOverlayPortalTest`。</status>
</deviation>

<deviation id="2026-06-26">
  <what>B6 FBO 离屏图层路径（transform+clip 叠加）每帧重栅格化子树到 FBO，破坏 I7 合成阶段「干净子树跳过」+ 信条五「COMPOSITE 级动画绘制成本归零」承诺</what>
  <why>B6 问题：节点同时设置非恒等 transform 与 clipChildren 时，CLIP 框用未经 transform 变换的坐标，
  rotate 下 scissor 矩形裁剪失效（glScissor 无视 GL 矩阵，物理限制）。
  方案：transform+clip 叠加走 FBO 离屏图层（PUSH_TRANSFORM_LAYER/POP_TRANSFORM_LAYER），
  FBO 内 MODELVIEW=I 使 scissor 轴对齐正确裁剪，POP 时切回父 FBO + 压 T 矩阵 + 回贴贴图（吃 T 旋转，父 clip 二次裁切）。
  无 clip 的 transform 走现有 GL 矩阵纯顶点变换路径（零重栅格化，守信条五）。
  用户拍板范围：有 clip 才 FBO（统一处理 transform+clip）。
  **代价**：每个 transform+clip 节点每帧 FBO begin（全屏 glClear）+ 子树重定向渲染 + 全屏回贴 fillrate。
  纯 transform 变化帧（如 rotate 动画），FBO 纹理每帧从零重画——干净子树在合成阶段未跳过，
  信条五「合成层纹理换取绘制成本归零」对该类节点未达成。
  命令层（CPU）守 I7（fragment 引用不变，replayer 只是回放已有命令到 FBO）；
  像素层（GPU）批 1 未守 I7（FBO 纹理无跨帧缓存，每帧重栅格化）。</why>
  <scope>scene 层 ScenePaintEngine.paintNode（门控判定 needTransform&&needClip→PUSH_TRANSFORM_LAYER）+
  PaintContextCompositor.pushTransformLayer/popTransformLayer（FBO 借还 + GL 状态管理）+
  UiRenderContext.pushTransformLayer/popTransformLayer（转发）+
  ScenePaintReplayer（PUSH_TRANSFORM_LAYER/POP_TRANSFORM_LAYER case）。
  影响所有 transform+clip 叠加节点（当前零生产触发，setTransform 在 ui.scene 全包零生产调用）。
  无 clip 的 transform 不受影响（走 GL 矩阵纯顶点变换，零重栅格化）。</scope>
  <status>**偏离已登记，待回填**：
  回填方向 = FBO 纹理脏标记跨帧复用：子树 paint/layout 脏才重画 FBO，纯 transform 帧复用上一帧 FBO 纹理在新 T 矩阵下回贴。
  这才是浏览器合成器的真实行为，兑现信条五「绘制成本归零」。
  优先级：待性能暴露后启动（当前零生产触发，YAGNI）。
  降级语义：FBO 不可用时（disabledForFrame/零面积）保留 clip 放弃 transform（不压 T，子树未变换坐标直画，clip 正确但 transform 失效）。
  决策依据见 docs/记忆/决策/DECISION-20260626-b6-transform-clip-fbo-deferred.md。
  oracle 实施前评估存于会话记录（信条五铁律成立性 + 矩阵基线 + T 矩阵时序 + inactive 降级）。</status>
</deviation>

<deviation id="2026-06-26-hit-test">
  <what>scene 新栈 SceneHitTester 对 transform 零感知——命中判定用纯轴对齐 LayoutBox 矩形，
  不读 node.getTransform()。rotate/scale 下视觉位置（经 FBO/GL 矩阵变换）与命中位置（轴对齐盒）错位。
  B6 FBO 方案使 transform+clip 的视觉渲染正确后，放大了该不一致（此前"视觉和命中都错"反而一致地错）。</what>
  <why>SceneHitTester.hitTestRecursive（:64-96）只读 LayoutBox 的 getX/getY/getWidth/getHeight + isClipWindow，
  完全不读 getTransform()。这是 transform 通路自始存在的既存缺口，非 B6 引入。
  B6 FBO 方案让 rotate+clip 的视觉正确（FBO 内 scissor 轴对齐裁剪 + T 矩阵回贴旋转），
  但 hit-test 仍按未旋转的轴对齐盒判定——视觉旋转 45° 的按钮，点击判定仍是未旋转矩形。
  B6 前 translate-only 时视觉和命中都是轴对齐平移尚能对齐；B6 后 rotate 视觉正确命中未跟，错位更明显。</why>
  <scope>scene 层 SceneHitTester.hitTestRecursive（不读 getTransform）；
  影响所有 transform 非恒等节点的命中判定（当前零生产触发，setTransform 在 ui.scene 全包零生产调用）。
  注：旧栈 DOM 的 UiTransform 能力边界文档（docs/使用文档/01-入门/项目定位与能力边界.md:77）
  称"transform 影响命中测试"，这是旧栈描述，scene 新栈不适用——需后续核实旧栈是否真的读 transform。</scope>
  <status>**偏离已登记，待回填**：
  回填方向 = SceneHitTester 增加 transform 感知：命中判定时将指针坐标逆变换到未变换坐标系
  （用 transform 的逆矩阵反变换 pointerX/Y），再与轴对齐 LayoutBox 比对。
  这与 paint 侧 FBO 方案正交（paint 用正向变换渲染，hit-test 用逆向变换命中）。
  优先级：待真实 rotate 交互需求触发后启动。
  决策依据见 docs/记忆/决策/DECISION-20260626-b6-transform-clip-fbo-deferred.md（hit-test 对偶单列遗留）。</status>
</deviation>

</deviation-log>

---

> **最后一句**：这套系统的价值不在任何单项技术，而在"每一层都只为变化付出最小代价"这条贯穿始终的纪律。
> 当你迷茫时，只需回到第 1 节那句话。守住它，系统就不会跑偏。
