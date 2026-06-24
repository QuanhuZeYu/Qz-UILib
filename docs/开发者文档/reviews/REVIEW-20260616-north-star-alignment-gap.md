# 现状对齐差距评估：NORTH_STAR 宪章 vs 当前实现

> 类型：架构对齐差距评估（为「据宪章大型重构」提供起点地图）
> 日期：2026-06-16
> 基准：宪章 `NORTH_STAR.md`（仓库根目录）；代码现状以本评估当日 `src/main/java/club/heiqi/uilib` 源码为准
> 配套：背景与取舍见 `docs/记忆/决策/DECISION-20260616-north-star-charter.md`

本评估只描述「当前在哪、离宪章有多远」，不规定重构怎么做（重构据宪章另行分批立项）。所有结论以源码为证，避免「文档比实现更乐观」。本文是**活地图**：每完成一批对齐，应回到对应条目更新。

## 0. 评估方法

- 以宪章第 3 节《核心信条》和第 5 节《关键不变量》为对照轴，逐条给出现状判定。
- 判定分四档：`已对齐` / `部分对齐` / `未对齐` / `不适用`。
- 每条尽量给出可核对的源码证据（类名、关键方法、计数）。
- 计数类证据为评估当日 `Select-String` 统计，属量级参考，非逐行精确数。

## 1. 一句话结论

> **宪章的「渲染层」（④DOM ⑤Layout ⑥Paint/Display List ⑦GL Render）已大体成形并持续优化；「数据层」（①signal ②reactive/effect ③组件只跑一次 + ④中央事务）基本不存在。** 当前 UI 更新链路是「命令式改 DOM → `markSubtreeMutated()` →
> `layoutVersion++/paintVersion++` 版本号失效 → 下一帧按版本号决定是否重建布局/绘制」，而非「signal 写入 → 中央事务批处理 → effect 定向 patch + 分级脏标记」。

因此：

- **大型重构的主战场在数据层**：引入 signal 原子、中央事务（写入收口 + 批处理 + 可追溯）、effect 细粒度绑定、组件「只跑一次」心智。
- **渲染层主要是「补齐与提纯」**：补 `COMPOSITE` 失效级、把 Display List 与 DOM 解耦（提纯 I6 契约线）、把现有版本号失效迁移到分级脏标记。
- **风险提示**：数据层重构会改变作者侧编程模型（从命令式 DOM 操作转向 signal 驱动），影响面覆盖所有页面、控件和宿主接入，必须分批、可回退、每批可验证。

## 2. 层次现状速览

| 宪章层 | 当前对应物 | 状态 | 说明 |
|---|---|---|---|
| ① State（signal + 中央事务） | 无 | 未对齐 | 无 signal/atom 类型，无中央写入收口，无状态快照历史 |
| ② Reactive（effect/computed/依赖图） | 无 | 未对齐 | 无 effect/computed/依赖追踪；动态更新靠命令式调用 + 版本号 |
| ③ 视图/组件（只跑一次 + signal→属性绑定） | `UiDocument` + builder 命令式建树 | 未对齐 | 组件即「一段命令式构建 DOM 的代码」，无「只跑一次后由 effect 驱动」语义 |
| ④ Element 树（DOM 交汇点） | `dom/`（`UiDocument`/`ElementNode`/`DocumentNode`/`TextNode`…） | 已对齐 | 保留式持久节点树，结构完整，确为双方交汇点 |
| ⑤ Layout（增量布局 + 每节点缓存） | `layout/`（`DocumentLayoutEngine` + 脏子树布局缓存） | 部分对齐 | 已有节点级布局版本与静态子树平移复用；尚未由分级脏标记统一驱动 |
| ⑥ Paint（Display List 分片缓存） | `paint/`（`DocumentPaintCommand` 等） | 部分对齐 | `DocumentPaintCommand` 已是事实上的 Display List；但与 DOM 耦合、且整列表级缓存为主 |
| ⑦ Render（GL 批处理/图集/合成/脏矩形） | `render/` + `paint/DocumentPaintRenderer` | 部分对齐 | GL 收敛在此层、合成层/字形图集已有；保留式 GPU 场景与脏矩形未成体系 |
| 契约线（⑥/⑦ 之间 Display List） | `DocumentPaintCommand` → `DocumentPaintRenderer` | 部分对齐 | 渲染层零 DOM 引用（干净）；但命令本身携带 `ElementNode`，未完全解耦 |

## 3. 逐信条判定

### 信条一：UI = f(state)，声明式优先 — 未对齐
- **现状**：界面通过命令式构建 DOM 并直接改节点属性/样式驱动。动态更新走 `ElementNode.setAttribute(...)`、样式声明写入等命令式入口，触发 `markSubtreeMutated()`。
- **证据**：`ElementNode` 多处 `markSubtreeMutated()`（属性/类/样式变更）；无「状态 → 界面」的纯函数投影层。
- **差距**：缺少「组件描述某状态下界面长什么样」的声明式表达；当前是「发生 X 就改 DOM 成 Y」的命令式心智，正是宪章信条一明确拒绝的形态。

### 信条二：signal 直驱保留树，免全局 diff — 未对齐
- **现状**：无 signal、无 effect、无依赖图。组件构建函数不是「只跑一次后由 effect 驱动」，重建依赖版本号失效后整段重算对应子树/命令。
- **证据**：全包搜索无 `Signal/Reactive/Effect/Computed/Observable/Atom` 业务类（仅匹配到 CSS 的 `DocumentEffectChain` 和 `ComputedStyle`，与响应式无关）。
- **差距**：更新粒度当前是「版本号失效驱动的子树/命令重算」，不是宪章要求的「单个属性级 effect patch」。这是数据层重构的核心目标。

### 信条三：列表才允许 diff，且必须 keyed 且局部 — 未对齐（无 keyed 协调）
- **现状**：无 keyed reconciliation 机制。列表更新当前靠命令式增删 DOM 节点。
- **证据**：全包搜索无 `keyed/reconcile/diffChildren/dataKey` 协调实现。
- **差距**：宪章把「列表」定为唯一允许 diff 的地方且必须 keyed；当前既无受控的 keyed diff，也就谈不上「把 diff 限制在列表内部」。重构引入响应式后需同步建立 keyed 列表协调，并守住 I5（不退化成全树 diff）。

### 信条四：所有状态写入收口到中央事务 — 未对齐
- **现状**：无中央事务调度器，无「记日志 → 应用 → 帧末刷新」收口，无状态快照历史/时间旅行。存在的 `WidgetBuildAttachmentTransaction` 只是「构建期挂载事务」，与状态写入收口无关。
- **证据**：`ui/widget/WidgetBuildAttachmentTransaction.java`（构建挂载用途）；批处理当前体现为「同帧多次 mutation 共用版本号、下一帧统一按版本决定重算」，而非显式事务合并。
- **差距**：缺少单一审计路径（谁/何时/因何改了状态）、缺少可回放快照。这是宪章信条四的全部价值所在，需在数据层重构时一并建立。

### 信条五：分级失效，变化只触达最低必要层 — 部分对齐（缺 COMPOSITE 级）
- **现状**：已有失效级别枚举，但**只有两级** `LAYOUT` 和 `PAINT`，**没有 `COMPOSITE`**。`TRANSFORM` 与 `OPACITY` 当前都归类为 `PAINT`。
- **证据**：`ui/style/UiStyleChangeImpact.java` 仅定义 `LAYOUT`、`PAINT`；`ui/style/UiStyleProperty.java` 中 `TRANSFORM(false, PAINT)`、`OPACITY(false, PAINT)`。
- **差距**：宪章铁律要求「动画尽量只用 COMPOSITE 级、60fps 动画绝大多数帧不碰布局/绘制」。当前 transform/opacity 变更走 PAINT → 触发绘制命令重建（与交接记录中「transform/hover 触发 paintVersion bump、~720 条命令全量重建」的性能痛点同源）。补齐 `COMPOSITE` 级并让
  transform/opacity 走合成层是信条五对齐的关键，且能直接缓解已知掉帧。

### 信条六：Display List 是数据层与渲染层的唯一契约 — 部分对齐
- **现状**：`DocumentPaintCommand` 是事实上的 Display List，`DocumentPaintRenderer` 消费它翻译成 GL；渲染层方向干净。
- **证据**：
  - 渲染层 `ui/render/*.java` 对 `ElementNode` 引用数 = **0**；GL 调用（GL11/GlStateManager）集中在此层（约 323 处）。→ 「渲染层不认识 DOM」基本成立。
  - 但 Paint 层 `ui/paint/*.java` 对 `ElementNode` 引用约 **135** 处，其中 `DocumentPaintCommand.java` 自身约 **21** 处携带 `ElementNode`（如 thumbReplay/custom bounds 等回放数据）。
- **差距**：契约的「渲染层 → 不碰 DOM」一侧已基本达成；但「Display List 自身 → 与平台/ DOM 无关」一侧未达成——命令仍携带 `ElementNode` 引用，使 Display List 无法独立于 DOM 存在，也阻碍宪章第 10 节「双缓冲跨线程」的长期目标。提纯方向：命令只携带值类型/句柄，不直接持有 DOM 节点。

### 信条七：保留式渲染，GPU 端场景增量更新 — 部分对齐
- **现状**：已有合成层（`PaintContextCompositor`、`MainLayerSnapshot`、`UiRenderTarget`/FBO 相关）、字形图集（font 系统 AWT ink-bounds atlas + shelf packing）、滚动期绘制命令免重建（delta 回放）。
- **证据**：`ui/render/`（合成/快照/裁剪/瓦片 `TileRegion`/`TileCoveragePlan`/`SampleRegion`）；`DECISION-20260611-awt-ink-bounds-atlas.md`；`DocumentPaintPlan`/`DocumentScrollbarThumbReplay`（滚动免重建）。
- **差距**：当前绘制命令缓存以「整列表 + 版本号失效 + 滚动 delta 回放」为主，尚无系统化的「常驻 GPU 场景 + 脏矩形增量刷新」。瓦片/采样区已有基础设施，但未形成「每帧只重刷脏矩形」的合成主循环。

## 4. 关键不变量（I1-I9）现状核对

| 不变量 | 状态 | 现状与证据 |
|---|---|---|
| I1 界面只能经改 signal 改变，无第二条改 UI 路径 | 未满足 | 当前唯一改 UI 路径就是命令式改 DOM；无 signal，所以谈不上「只经 signal」 |
| I2 所有 signal 写入经过中央事务 | 未满足 | 无 signal、无中央事务；状态散落在 DOM/控件实例上 |
| I3 组件函数无副作用且只跑一次 | 未满足 | 组件即命令式建树代码，可被重复调用重建子树，无「只跑一次」约束 |
| I4 effect 触发只打正确失效级别 | 部分（机制缺 COMPOSITE） | 已有 LAYOUT/PAINT 两级且属性已分级标注；但无 effect 概念，且缺 COMPOSITE 级 |
| I5 diff 只在列表内部且 keyed | 未满足（无 keyed diff） | 无 keyed 协调；列表靠命令式增删节点 |
| I6 渲染层无 DOM 概念、数据层无 GL 调用 | 部分满足 | 渲染层零 ElementNode 引用、GL 收敛于渲染层（达成）；但 Paint/Display List 仍携带 ElementNode（未达成） |
| I7 干净子树在布局/绘制/合成阶段被跳过 | 部分满足 | 布局已有脏子树跳过；绘制以整列表 + 滚动 delta 为主、子树级跳过不完整；合成阶段无独立脏跳过 |
| I8 布局/Display List/合成纹理可缓存且按脏复用 | 部分满足 | 布局结果、绘制命令、合成快照均有缓存；失效以全局版本号为主，非细粒度脏标记 |
| I9 一帧多次写入合并为一次刷新 | 部分满足 | 同帧多次 mutation 经版本号在下一帧统一重算（有合并效果），但非显式事务批处理、无可追溯日志 |

## 5. 重构优先级建议（据宪章，待用户立项）

> 以下只是「按差距推导的方向与顺序」，不是已批准的实施计划。每一项落地都需单独立项、分批、可验证，并在动手前过宪章第 8 节《决策检查清单》。

1. **数据层地基（最高优先，主战场）**：引入 signal 原子 + 中央事务（写入收口 / 同帧批处理 / 状态快照），这是 I1/I2/I9 与信条一/四的前提。建议先在一个隔离子系统试点，再向控件与页面铺开。
2. **响应式绑定与组件「只跑一次」**：在 signal 之上建立 effect/computed 与依赖图，让组件函数挂载时建立「signal → 节点属性」绑定（信条二/三、I3）。同步建立 keyed 列表协调（I5）。
3. **补齐 COMPOSITE 失效级**：`UiStyleChangeImpact` 增加 `COMPOSITE`，把 `TRANSFORM`/`OPACITY` 迁出 `PAINT`，让其走合成层而非重建绘制命令（信条五铁律、I4）。此项相对独立，且直接缓解已知 transform/hover 掉帧。
4. **提纯 Display List 契约线（I6）**：剥离 `DocumentPaintCommand` 对 `ElementNode` 的直接持有，改为值类型/句柄，为「Display List 双缓冲跨线程」（宪章第 10 节）扫清障碍。
5. **失效模型迁移**：把全局 `layoutVersion/paintVersion` 版本号失效，逐步迁移到「effect 定向 patch + 分级脏标记」，让干净子树在三个阶段都被跳过（I7/I8）。
6. **保留式 GPU 场景 + 脏矩形**（最后）：在前述契约/合成基础稳定后，把渲染层推进到「常驻场景 + 脏矩形增量刷新」（信条七）。

## 6. 已对齐、可作为重构地基的资产

重构不是从零开始，以下现有资产与宪章同向，应复用而非推倒：

- **保留式 DOM 树**（`dom/`）：宪章④层交汇点，结构完整，是数据层与渲染层的天然分界。
- **事实上的 Display List**（`DocumentPaintCommand` + `DocumentPaintRenderer`）：宪章⑥/⑦契约线雏形，渲染层方向已干净。
- **脏子树布局缓存**（`DECISION-20260606`）：节点级布局版本与静态子树平移复用，是 I7/I8 在布局层的先行实现。
- **样式分级标注**（`UiStyleProperty` 的 LAYOUT/PAINT 标注）：I4 的现成基础，补一个 COMPOSITE 级即可扩展。
- **合成层 / 字形图集 / 滚动免重建**（`render/` + `DocumentPaintPlan`）：信条七的局部兑现。

## 7. 风险与注意事项

- **编程模型变更**：数据层重构会把作者侧从「命令式操作 DOM」转向「signal 驱动」，影响所有页面、控件、HUD 与远程 UI 接入。必须分批、保留回退路径、每批最小验证。
- **诚实口径**：本评估刻意避免把「部分对齐」写成「已对齐」。后续每次声称某条对齐时，必须有可核对的源码证据，沿用项目「文档不得比实现更乐观」红线。
- **偏离登记**：重构过程中若需暂时违反某条信条/不变量（例如过渡期命令式与 signal 并存），必须在 `NORTH_STAR.md` 第 11 节《偏离登记》显式登记，禁止隐性偏离。
- **本评估是活地图**：每完成一批对齐，回到第 3/4 节更新对应判定；稳定下来的边界写回 `docs/记忆/长期事实/架构边界.md`。

## 8. 后续复核

- 暂无。后续按重构批次回填对齐进度。


