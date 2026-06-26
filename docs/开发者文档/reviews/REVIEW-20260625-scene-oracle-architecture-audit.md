# 审核：scene 新栈 oracle 架构审核报告（8 API 陷阱 + 10 BUG 温床 + 不变量核对）

- 日期：2026-06-25
- 审核对象：scene 声明式新栈（`ui.scene` 包：node/layout/paint/component/input/control）
- 审核者：oracle（架构裁决 subagent）
- 来源说明：**本文档重建自会话记录浓缩清单
  （`docs/记忆/当前态/当前上下文.md:319-340`）+ 逐条源码核实。**
  oracle 原始完整报告（8 API 陷阱 + 10 BUG 温床 + 不变量核对全文）
  仅存在于会话记录中，从未落盘。
  本次落盘版以「现存审查文档 + 当前真实源码状态」为准，
  对每条结论补精确文件行号与当前修复状态。
  原始报告中浓缩清单未覆盖的条目
  （API 陷阱 A2-A5/A7/A8、BUG 温床 B9/B10、
  部分不变量核对逐条结论）
  已随会话上下文失效无法精确重建，相应处显式标注「原文未落盘」。
- 配套已落盘文档：
  `docs/开发者文档/reviews/REVIEW-20260625-scene-geometry-clip-bugbed.md`
  （B1 + B3/I7 修复审核详版）

---

## 一、审核背景

oracle 对按 `NORTH_STAR.md` 模型重写的 scene 声明式新栈做系统性架构审核，
产出三类成果：

1. **8 个 API 陷阱**：API 表面语义与实际行为存在落差、易误用的设计点。
2. **10 个 BUG 温床**：当前可能无可观测 bug 但结构上埋雷、
   或已有正确性/性能缺陷的代码点。
3. **不变量核对清单**：逐条核对 NORTH_STAR I1-I11 在 scene 新栈的守恒情况。

本轮已修 P0 两项（B1、B3/I7）+ TextArea 真机暴露的 caret 撑满 bug，
全部合回 `4.0`，TextArea 真机验收通过。
其余条目按优先级排入「未修后续候选」。

---

## 二、BUG 温床 B1-B8（清单已落盘部分）

> 说明：浓缩清单明确记录的为 B1-B8。
> 原始报告标称「10 BUG 温床」，B9/B10 两条内容随会话失效，
> **原文未落盘**，无法精确重建。

### B1 — 三 primitive 私有绝对坐标裸累加不注入 scrollOffset 【已修 · commit `9ac1fd9c`】

- **现象**：TextArea/Slider/TextInput 三个 primitive 内各自持有私有
  `absoluteX/absoluteY` helper，沿 parent 链裸累加 `LayoutBox.x/y`，
  **不注入 scrollable 祖先的 scrollOffset**，
  与权威原子 `SceneGeometry.absoluteBox` 形成 4 套并存的绝对坐标口径。
- **危害**：primitive 嵌入 scrollable 容器后，点击坐标换算漏补偿滚动偏移
  （尤其多层 scrollable 祖先时），命中定位错位（m4 系统根因）。
- **修复**：删除三个私有 helper，统一改走 `SceneGeometry.absoluteBox(node,0,0)`，
  数学等价且额外修复多层 scrollable 漏补偿
  （`absoluteBox` 注入所有 scrollable 祖先的 scrollOffsetY，
  原手动 `+scroll` 只补偿一层）。
- **当前源码核实**：
  - `SceneSliderPrimitive.java:241` `SceneGeometry.absoluteBox(track,0,0).getX()`
  - `SceneTextAreaPrimitive.java:238` content 顶 / `:244` rowAbsX 均走 `absoluteBox`
  - `SceneTextInputPrimitive.java:159` `absoluteBox(root,0,0).getX()`
  - 三 primitive 内 `private int absoluteX/absoluteY` AST 搜索零残留 ✓
- **状态**：**已修**，52 测试全绿，m4 系统根因已消除。
  详版见 REVIEW-20260625-scene-geometry-clip-bugbed.md。

### B2 — TextArea O(N²) 文本几何 【已修 · commit `ae263575` + `7b7b1722` · 待真机帧率】

- **现象**：TextArea 行定位与点击前缀宽计算每帧对全文做平方级扫描（O(N²)）。
- **修复**：
  - Step1（`ae263575`）：行定位改用前缀和缓存，O(N²)→O(N)。
  - Step2（`7b7b1722`）：点击前缀宽数组加跨帧缓存。
- **状态**：**已修（理论 O(N) 推导）**，
  但 **O(N²)→O(N) 性能收益为理论推导，帧率实测交用户真机跑**（待真机验收）。

### B3 — paint/hit-test clip 裁剪口径分裂 【已修 · commit `9e46759f`（B3/I7）】

- **现象**：paint 阶段 `clipChildren` 与 `scrollable` 节点**都**建裁剪窗口；
  hit-test 阶段**只**对 `scrollable` 建 clip bounds，
  纯 `clipChildren` 节点不裁——两阶段口径分裂。
- **危害（oracle 原定性 vs 核实修正）**：oracle 报告定性为
  「视觉裁掉但仍可点击」。**经源码核实定性偏高**：
  纯 clipChildren 节点 clip bounds 与节点自身 LayoutBox 绝对盒重合，
  hit-test 中父节点自身 bounds 检查（`SceneHitTester.java:93-96`）
  在 clip 之前先行挡住指针，故纯 clipChildren 场景下
  clip 对 hit-test 无独立可观测作用——
  **当前无独立可观测正确性 bug**。
- **修复价值**：抽 `SceneNode.isClipWindow()`
  （= `isClipChildren() || isScrollable()`）谓词，
  paint（`ScenePaintEngine.java:161`）与 hit-test（`SceneHitTester.java:106`）共用，
  **防御性口径统一**——防止未来引入 transform 等
  使视觉位置与 LayoutBox 解耦的机制时两阶段再次分裂。
  新增 5 条回归锚点测试。
- **状态**：**已修**，补 I7 邻域缺口。
  详版见 REVIEW-20260625-scene-geometry-clip-bugbed.md。

### B4 — COLUMN fill O(n²) 约束判定 【未修】

- **现象**：约束变化订阅闸门 `childConstraintsWouldChange` 判定
  「下传约束是否会变」时，对**每个子节点**调 `buildChildConstraints` 两次
  （新约束一次、旧约束一次）逐一比较；
  在深 fill 链 / COLUMN fill 场景下，约束沿链下传时每层重复构建子约束，
  呈平方级开销趋势。
- **当前源码核实**：`SceneLayoutEngine.java:430-441`，
  `childConstraintsWouldChange(node, cur, prev)`：
  `:431` 约束未变短路、`:432` 无子短路是正确的 99% 干净帧快路；
  `:433-439` for 循环对每子双调 `buildChildConstraints` 并 `Objects.equals` 比较，
  确为约束变化帧的额外开销点。
- **状态**：**未修**。
  属性能温床（约束未变时已短路，仅约束变化帧付出代价），优先级低于 B2。

### B5 — paint LEFT 对齐仍调无谓 measureWidth 【未修】

- **现象**：文本左侧偏移计算 `calculateTextLeft` 在 `switch(align)` 之前
  **无条件**调用 `measurer.measureWidth(text, fontSize)`，
  但 `LEFT` 分支直接返回 `paddingLeft`、用不到 `textWidth`——
  LEFT 对齐（最常见）每帧白白量一次文本宽。
- **当前源码核实**：`ScenePaintEngine.java:284`
  `int textWidth = measurer.measureWidth(text, fontSize);` 在 `:286 switch` 之前；
  `:287-288` `case LEFT: return paddingLeft;` 不引用 `textWidth`。
  CENTER/RIGHT 才需要。
- **状态**：**未修**。可优化为仅 CENTER/RIGHT 分支内惰性测量。属微优化温床。

### B6 — transform + clip 叠加坐标错位 【未修】

- **现象**：节点同时设置非恒等 `transform` 与 `clipChildren/scrollable` 时，
  CLIP 框使用**未经 transform 变换的 nodeAbsX/Y**，
  与子树实际经 PUSH_TRANSFORM 变换后的视觉位置错位
  （rotate 下 scissor 矩形裁剪本就失效）。
- **当前源码核实**：`ScenePaintEngine.java:130` 注释已显式登记约束
  「本期非恒等 transform 节点不支持 clipChildren
  （rotate 下 scissor 矩阵裁剪失效），已登记约束」；
  `:133-139` PUSH_TRANSFORM 用 `nodeAbsX/Y`，
  `:161-167` CLIP_PUSH 同样用未变换的 `nodeAbsX/Y` + `clipWidth/clipHeight`，
  二者叠加时 clip 框不随 transform 移动。
- **状态**：**未修**（已在源码注释登记为已知约束）。
  B3 本轮 clip 口径统一**未覆盖**该 transform+clip 组合。
  建议后续单独处置
  （与 REVIEW-20260625-scene-geometry-clip-bugbed.md 遗留项 2 一致）。

### B7 — caret 高度不响应 fontSize 【已隐式还清】

- **现象（原报告）**：TextArea 光标（caret）高度写死、
  不随 `fontSize` 变化（m5 标记）。
- **当前源码核实**：`SceneTextAreaPrimitive.java:355`
  `caret.setPreferredHeight(rt.lineHeight(caret.getFontSize()))`——
  caret 高度**已动态取 `caret.getFontSize()` 经 `rt.lineHeight` 换算**，随字号变化。
- **状态**：**已隐式还清**。
  在后续 caret 相关改动（含 commit `4fc55772` 撑满 bug 修复一带）中，
  caret 高度已改为基于 fontSize 计算，
  原 B7 描述的「写死高度」在当前源码不再成立。
  **非本轮显式立项修复，属隐式消除，
  未单立回归锚点专门锁 fontSize→高度联动**（建议后续补一条）。

### B8 — 滚动后 hover 滞留 【未修】

- **现象**：滚动容器内容滚动后，指针下方的实际节点已变，
  但 hover 状态不更新——因为 hover 只由指针**移动**事件驱动，
  纯滚轮滚动（指针不动）不触发 hover 重算，旧 hover 节点滞留为 true。
- **当前源码核实**：`SceneInputRouter.java:147-168`，
  hover 状态更新整段被 `if (type == SceneEventType.POINTER_MOVE)`（`:155`）包裹，
  仅 `POINTER_MOVE` 驱动 `hoveredNode` 切换。
  SCROLL 事件（`SceneEventType` 另有滚轮类型）不进此分支，滚动后不重算 hover。
  注释 `:147` 明确「hover 状态更新（仅 MOVE 驱动…）」。
- **状态**：**未修**。需在滚动后补一次 hover 重算（或滚动事件也驱动 hover）。

---

## 三、API 陷阱 A1 / A6（清单已落盘部分）

> 说明：浓缩清单仅明确记录 A1 与 A6 两条。
> 原始报告标称「8 API 陷阱」，A2-A5、A7、A8 共 6 条内容随会话失效，
> **原文未落盘**，无法精确重建。

### A1 — effect 内 set 慢一帧 【大部分被 ReactiveScheduler 不动点覆盖 · 残留语义待原报告确认】

- **现象（原报告）**：在 effect 内部再 `signal.set(...)`，
  新写入可能延迟到下一帧才生效（"慢一帧"），违反同帧收敛直觉。
- **核实状态**：根据 REVIEW-20260618-scene-input-i4-merge.md 记录，
  reactive 地基去重已从 `Signal.set` 移到 `ReactiveScheduler.flush` 阶段、
  且 scheduler 具备**不动点（fixpoint）迭代**——
  同帧内 effect 触发的二次 set 会在同一 flush 内继续迭代至收敛，
  故「effect 内 set 慢一帧」**大部分场景已被 ReactiveScheduler 不动点机制覆盖**。
- **残留**：是否存在不动点未覆盖的残留「慢一帧」语义边界，
  **oracle 原始报告对该残留的精确界定未落盘**，
  需查会话记录或重新核 `ReactiveScheduler.flush` 收敛终止条件方能确认。
  本条标注「**残留语义待原报告/源码二次核实**」，不下「已完全消除」的结论。
- **状态**：**大部分已覆盖 · 残留待确认**（非本轮立项）。

### A6 — bind impact 死参数 【未修】

- **现象**：`SceneRuntime.bind(Invalidation impact, ...)` 的首个 `impact` 参数
  **实际不参与失效级别决策**——
  真正的失效级别由 `SceneNode` 强类型属性槽 setter 内部自动打出
  （如 `setBackgroundColor` 自动 `markSelfPaint`）。
  `impact` 仅用于「校验/文档」，是事实上的死参数，
  调用方误以为它控制失效级别。
- **当前源码核实**：`SceneRuntime.java:178` 参数 javadoc 自述
  「@param impact 声明的失效级别（用于校验/文档，真正打级靠属性槽）」；
  `:183-193` 方法体 `bind(Invalidation impact, ...)` 内
  **完全未引用 `impact`**——
  只用 `src`/`applier` 创建 effect
  （`:191 targetOwner.createEffect(() -> applier.accept(src.get()))`）。
  `impact` 形参进入后即被丢弃。
- **关联**：与已落地的 REVIEW-20260618-reactive-dom-invalidation.md /
  `fa1fed61` 删 `createEffect`/`bind` 的 impact 参数同型问题
  （DOM 侧已删，scene 侧 `SceneRuntime.bind` 仍保留死参数）。
- **状态**：**未修**。
  建议删除 `impact` 形参或降级为纯文档枚举
  （保留 bindText 等语义化封装）。

---

## 四、已修项 I6（清单已落盘部分）

### I6 — scene replayer 反向依赖 ui.style 【已修 · commit `c9c53208`】

- **现象**：scene paint replayer（渲染层）反向
  `import club.heiqi.uilib.ui.style.*`，
  渲染层认识了数据层/样式层概念，
  违反 I6「渲染层代码中不出现 signal/组件/DOM 概念」。
- **修复**：切断 replayer 对 `ui.style` 的反向依赖。
  oracle 裁决选「scene 内自建轻量缓存」方案，
  否决整包复用旧栈 TextLayoutEngine（撞 I6/I10——scene 核心严禁认识 ui.text）。
- **当前源码核实**：全 `ui.scene` 包内 `ui.style` / `ui.text` import 搜索结果——
  - 核心层（layout/paint/input/component/control）**零反向 import** ✓
  - 唯一 `ui.text.*` import 在 `scene/text/TextMeasureServiceSceneAdapter.java:3-4`，
    该类自述「scene 核心与 ui.text 之间的合法接缝（I6/I10）」
    「位于 scene/text 装配子包，允许 import ui.text.*」——
    属设计内允许的装配层接缝，非核心层污染。
  - `SceneCursor.java:7` 仅注释提及「值照抄旧栈 ui.style.props.UiCursor」，非 import。
- **状态**：**已修**，I6 在 scene 渲染层守恒。

---

## 五、未立项 P2：chrome 主题层

- **现象**：scene 新栈缺统一的 chrome（控件外观皮肤 / 主题）层，
  wrapper 在 primitive root 上挂 chrome 的机制尚未抽象为可换肤主题系统。
- **评估**：属 P2 大工程，**未立项**，需单独排期与方案设计。
- **状态**：**未立项**（不在本轮及近期候选范围）。

---

## 六、未修后续候选汇总（优先级排序）

| 编号 | 描述 | 优先级 | 精确位置 | 状态 |
|---|---|---|---|---|
| B2 | TextArea O(N²)→O(N) 帧率验证 | P1 | — | 已修 · 待真机帧率 |
| I6 | replayer ui.style 反向依赖 | P1 | — | **已修** `c9c53208` |
| B4 | COLUMN fill O(n²) 约束判定 | P2 | `SceneLayoutEngine.java:430-441` | 未修 |
| B5 | paint LEFT 无谓 measureWidth | P3 | `ScenePaintEngine.java:284` | 未修 |
| B6 | transform+clip 叠加坐标错位 | P2 | `ScenePaintEngine.java:130,161-167` | 未修（已登记约束） |
| B7 | caret 高度不响应 fontSize | — | `SceneTextAreaPrimitive.java:355` | **已隐式还清** |
| B8 | 滚动后 hover 滞留 | P2 | `SceneInputRouter.java:147-168` | 未修 |
| A1 | effect 内 set 慢一帧 | — | ReactiveScheduler.flush | 大部分覆盖 · 残留待确认 |
| A6 | bind impact 死参数 | P3 | `SceneRuntime.java:178,183` | 未修 |
| chrome 主题层 | 统一换肤主题系统 | P2（大工程） | — | 未立项 |

---

## 七、不变量核对（I1-I11）

> **重建说明**：当前上下文记录 oracle 产出「7 不变量核对清单」，
> 但**逐条核对结论原文未落盘**。
> NORTH_STAR 现行不变量为 I1-I11 共 11 条
> （I10/I11 为 scene 输入层新立）。
> 以下为**基于 NORTH_STAR.md:156-169 定义 + 已落盘审查文档 + 本轮源码核实重建**的核对，
> **非 oracle 原始 7 项逐字复刻**。
> 原始报告具体核对哪 7 条、各条原始判语，已随会话失效。

| 不变量 | 定义（NORTH_STAR:156-169） | scene 新栈核对结论（重建） |
|---|---|---|
| **I1** 信号优先 | 界面状态只能经改 signal 改变，不存在第二条改 UI 路径 | **守**。primitive handler 只 `signal.set()` / 调 `props.onChange()`；逃生舱仅只读几何测量。B1 改动纯几何只读，不破 I1。 |
| **I2** 中央事务 | 所有 signal 写入都经中央事务，无绕过调度器的写入 | **守**。写入经 ReactiveScheduler 批处理。本轮改动不新增直接写入路径。 |
| **I3** 组件纯投影 | 组件函数无副作用、生命周期内只执行一次；动态行为落 effect | **守**。primitive 构建函数一次性建树，动态行为经 `rt.bind`/Computed。 |
| **I4** 正确失效级别 | 每个 effect 触发须打且仅打正确失效级别 | **守（带 A6 噪声）**。失效级别由属性槽 setter 自动打出；但 `SceneRuntime.bind` 的 `impact` 形参为死参数（A6），手传级别是噪声不影响正确性。本轮 B1/B3 改动不触 setter/标脏。 |
| **I5** keyed diff | diff 只发生在列表节点内部且必须 keyed | **部分缺口（非本轮）**。当前上下文记录 TextArea 行曾用裸 for 无 key（违 I5），已迁 forEach+keyed / SceneDataTable 取代；本审核条目无关。 |
| **I6** 双层契约线 | 渲染层不出现 signal/组件/DOM 概念；数据层不出现 GL 调用 | **守（本轮还清反向依赖）**。replayer 对 ui.style 反向依赖已由 `c9c53208` 切断；唯一 ui.text import 在 scene/text 合法接缝 adapter。 |
| **I7** 干净子树跳过 | 干净子树在布局/绘制/合成三阶段都须被跳过 | **守（B3 补邻域缺口）**。B3 统一 paint/hit-test clip 谓词正是补 I7 邻域；hit-test 改动未引入可变状态，仍无状态。B4 约束闸门「约束未变短路」也守 I7（约束传播与脏标记正交）。 |
| **I8** 缓存可复用 | 布局结果/Display List 片段/合成层纹理须可缓存按脏复用 | **守**。fragment 复用（`regeneratedFragmentCount` 不增）、selfConsumesConstraint 约束变化订阅闸门守 I8。 |
| **I9** 批处理 | 一帧内多次写入须合并为一次刷新 | **守**。ReactiveScheduler 单点 flush + 不动点迭代（关联 A1）。 |
| **I10** 平台输入契约线 | 平台原始输入只经 PlatformInputSource；scene.input 核心包不得出现 lwjgl/GLFW/minecraft import | **守**。input 核心键码用平台无关枚举（SceneKey/SceneCursor 照抄值不 import）；B8 所在 SceneInputRouter 不引平台类型。 |
| **I11** handler 只写 signal | 输入 handler 只能经 signal.set 改 UI，不得直接操作 SceneNode 属性槽/树结构；逃生舱仅只读几何测量 / EventContext 受控命令 / 宿主桥接 | **守**。B1 涉及的 absoluteBox / hitTester 全程只读（逃生舱①）；A6 的 bind 属构建期绑定非 handler。 |

**核对总结（重建）**：本轮审核范围内（B1/B3/I6 已修，B4-B8/A1/A6 候选），
**无任一不变量被破坏**。
I6 反向依赖、I7 clip 邻域缺口两处历史缺口本轮已还清/补全；
A6 死参数为 I4 噪声但不破坏正确性；
其余候选项为性能温床（B2/B4/B5）或局部行为缺陷（B6/B8），
均不触及不变量根基。

---

## 八、结论

- **本轮已落地**：B1（绝对坐标口径统一）、B3/I7（clip 谓词统一）、
  I6（replayer 反向依赖切断）、
  B2（文本几何 O(N²)→O(N)，待真机帧率）、
  TextArea caret 撑满 bug（`4fc55772`，真机验收通过），
  全部合回 `4.0`。
- **B7 隐式还清**：caret 高度已随 fontSize 计算（`:355`），
  建议补一条 fontSize→高度联动回归锚点。
- **A1 大部分覆盖**：effect 内 set「慢一帧」大部分被
  ReactiveScheduler 不动点覆盖，
  残留语义边界**原文未落盘待确认**。
- **未修候选**：B4/B5/B6/B8（BUG/性能温床）、
  A6（bind 死参数）、
  chrome 主题层（P2 大工程未立项），
  均附精确行号见第六节表。
- **不变量**：scene 新栈 I1-I11 在本轮范围内全部守恒，无阻断合并项。

---

## 附：核实方法与可信度声明

- **本文档重建自会话记录浓缩清单 + 逐条源码核实**，
  oracle 原始完整报告（8 API 陷阱全集、10 BUG 温床全集、
  7 不变量核对逐条原语）**从未落盘**，
  部分条目（A2-A5/A7/A8、B9/B10、不变量原始 7 项判语、A1 残留界定）
  随会话上下文失效，已在对应处显式标注「原文未落盘」，**未臆测补全**。
- 已落盘部分（B1-B8、A1、A6、I6、chrome）的
  **当前真实状态、文件行号、commit 哈希均经本次源码读取与 git 核实**：
  - commit 哈希全部验证存在且描述匹配：
    `9ac1fd9c` / `9e46759f` / `ae263575` / `7b7b1722` / `c9c53208` / `4fc55772`。
  - 未修项行号经当前 HEAD 源码读取确认：
    `SceneLayoutEngine.java:430-441`、
    `ScenePaintEngine.java:130/161-167/284`、
    `SceneRuntime.java:178/183`、
    `SceneInputRouter.java:147-168`、
    `SceneTextAreaPrimitive.java:355`。
  - I6 反向依赖经全 `ui.scene` 包 import 搜索确认核心层零残留。
- 不变量核对第七节为**重建**，非 oracle 原始逐字结论，已在节首声明。
