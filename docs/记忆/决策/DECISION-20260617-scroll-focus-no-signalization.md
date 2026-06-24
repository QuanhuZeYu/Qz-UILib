# 决策：滚动态与 focus 投影不做 signal 化（C4 否决 + focus 桥维持现状）

## 背景

第 28、29 次开发围绕 HtmlLikeDocumentWidget 的 input 热路径与响应式边界，出现两个反复被提起、且看似"应该响应式化"的诱惑点。为避免后续会话重复评估、再次走完整裁决流程，将结论沉淀于此。

1. **候选 C4**：把 `DocumentScrollState` 的滚动偏移从命令式 `version++` + version 影子，升级为 signal（接入数据层响应式图），让滚动变化通过 signal 传播驱动下游。
2. **focus 完整 signal 化**：把 `focusEpochSignal` 这座过渡桥升级为完整的 focus signal 投影，统一走响应式管线。

两者本质相同：在没有真实第二消费者、没有真实 I1（声明式数据→DOM 一致性）缺口的前提下，"为响应式而响应式"。

## 候选方案

### C4（滚动态 signal 化）
1. 维持现状：滚动偏移命令式 `version++` + version 影子，滚动归渲染层/合成线程。
2. 方案 A：全 DOM 改坐标——滚动时直接改每个节点的布局坐标属性。
3. 方案 B：虚拟视口——视口态独立于 DOM 节点属性，渲染层按视口裁剪/平移（Blink/WebKit 真实实现）。
4. 候选 C4：滚动偏移 signal 化，接入数据层响应式图。

### focus signal 化
1. 维持现状：`focusEpochSignal` 作为已登记的工作良好过渡桥，cursor effect 是其唯一真实消费者。
2. 把 focus 投影完整 signal 化，统一响应式管线。

## 最终选择

- **C4 否决，维持现状（方案 1）。方案 A 明确不回退。**
- **focus signal 化不做，维持 `focusEpochSignal` 过渡桥现状。**

二者均经 explorer + oracle + librarian 三方裁决（第 28 次）与第 29 次复盘确认。

## 选择原因

### 为什么 C4 不做
- **滚动偏移不改 DOM 节点属性**，它是渲染层视口态（对应信条"声明式数据驱动 DOM 结构，渲染/视口态归渲染层"的下半句）。因此 **I1 不存在缺口**——没有"数据变了但 DOM 没跟上"的一致性问题需要 signal 去弥合。
- signal 化会**撞 I6**（`UiComponentRuntime` 不可 import `ui.control` 的分层约束）。
- 会**倒退 I7/I8**（input 热路径性能与稳态零冗余目标）。
- 撞**帧末批处理铁律**（`Signal.set` 帧末批处理 + 对已应用值去重；滚动是高频连续量，进批处理图反而增加开销与时序复杂度）。
- 与项目第 10 节**"滚动归合成线程"**架构定位直接相悖。
- **方案 A（全 DOM 改坐标）是 pre-RenderingNG 已淘汰模型**，回退即倒退到现代浏览器早已抛弃的架构；**方案 B 虚拟视口才是 Blink/WebKit 真实实现**，当前命令式 version 影子机制已对齐方案 B 的视口态思路。

### 为什么 focus signal 化不做
- `focusEpochSignal` 是**工作良好的已登记过渡桥**，且有 cursor effect 这一**真实消费者**。
- 升级为完整 signal 投影**在唯一消费者上零收益**，与 C4 同性质，属"为响应式而响应式"。

## 关键边界与未来触发条件（YAGNI 纪律）

- **不预建空桥**：若未来真出现"需响应 scroll 的数据层 effect"，按 `focusEpochSignal` 模式**即时建桥**，不提前为假想需求铺设响应式通道。
- **focus 桥升级触发条件**：除非出现 cursor 之外的**第二个 focus 投影消费者**（例如 `:focus` 边框高亮 effect），否则不升级 `focusEpochSignal`。出现第二消费者时，才按真实需求评估是否完整 signal 化。

## 影响范围

- `DocumentScrollState` 保持命令式 `version++` + version 影子（滚动同步契约根因，**不可 signal 化**）。
- `dispatchLatestScrollIfChanged` 同步语义、scroll→hover 同步管线（HtmlLikeDocumentWidget:790-798，第 28 次测试守护）、enter/leave 同步配对、hover/press 影子契约、`commitInteractionSignals` 帧首时序（765-766，先于 flush）均为铁律，受本决策保护，
  不因"响应式统一"被改动。
- `focusEpochSignal` 维持过渡桥现状，不升级。
- `UiComponentRuntime` 不能 import `ui.control`（I6）保持。
