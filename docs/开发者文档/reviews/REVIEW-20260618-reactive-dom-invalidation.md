# REVIEW-20260618 reactive→DOM 失效层接入架构审查 + P0 还债

- **审查对象**：`ui.reactive` 响应式层接入 `ui.dom` 命令式失效层（HTML-like 栈）的接缝，对照 `NORTH_STAR.md` 信条五 + I4/I7/I8/I9。
- **审查方**：oracle（ora-2 session），主 Agent 宪章基线裁决 + explorer 两轮侦察。
- **结论**：接入成熟度约 40%（早期可用），**发现 1 个 P0 架构债（双重标脏）已本轮还清**，COMPOSITE 断链经核实为过时注释（P2 文档债），I7 容器子树标脏债（行 242）紧迫性上升但排在 P0 之后。
- **代码改动**：合并提交 `50b4a167`（`--no-ff` 合 `refactor/reactive-dom-invalidation-p0-dedup`），单提交 `fa1fed61`，净删 12 行（4 文件 +37/-49）。

---

## 一、背景与接缝定位

`NORTH_STAR.md` 行 9 既定方向：「用 effect 标记替代现有 `layoutVersion/paintVersion` 命令式版本号模型」。审查首先澄清这句话的精确语义边界（见第四节决策），并定位真实接缝：

- reactive 层（Signal/Effect/Computed/Owner/ReactiveScheduler/TransactionLog）**零 DOM 依赖**，I6 守得干净。
- 唯一桥接层 = `UiComponentRuntime`（同时 import reactive 与 dom），`createEffect(impact, body)` / `bind(impact, source, applier)` / 6 个 `bind*` 便捷方法。
- 消费端 = `HtmlLikeDocumentWidget` 的 `resolveLayoutBox` / `resolvePaintLayoutBox` / `resolvePaintCommands`，做版本号比对命中缓存。

接入状态判定：**部分已落地，双轨并存**（effect 脏标记 + 版本号比对），非「未启动」。

---

## 二、P0 架构债：双重标脏（本轮已还清）

### 关键事实（explorer 侦察坐实）

属性 → impact 的映射**早已长在数据模型里**，分两套已落地：

1. `UiStyleDeclaration.updateProperty(.., impact)` 每个属性硬编码正确 impact（width/height/margin…→LAYOUT，backgroundColor/visibility…→PAINT，opacity/transform→COMPOSITE）。
2. `StyleDeclarationSlot` 构造带 impact，`update()` 自动用该 impact 调 `updateProperty`。
3. 标脏出口 `updateProperty → recordChange(impact) → changeListener.onStyleChanged(impact)`，`ElementNode` listener（ElementNode.java:49-62）**按 impact 精确分流**节点级标脏。
4. `TextNode.setText → markMutated()`（LAYOUT 级），`setAttribute`/`classList`/结构增删同样自带 LAYOUT 级自动标脏。

**即：`bind*` 的 applier 一调 `setXxx`，正确级别的节点级脏标记就已自动打出。impact 早已「长在属性上」，调用方无需知道。**

### 错误所在

`createEffect`（UiComponentRuntime.java:103-112）在 body 跑完后**又**按调用方手传 impact 调 `document.markLayoutDirty/markPaintDirty/markCompositeDirty`。后果：

- **双重标脏**：同一次属性写入被标两次脏。
- **第二次是全局粗粒度**：`markLayoutDirty → recordGlobalLayoutMutation`（UiDocument.java:1071-1074）→ **root 整树 `__markSubtreeLayoutDirty`**。任何一个 LAYOUT 级 bind 触发 → 整个文档树全部节点 layout 版本被刷。**比已登记的行 242 容器子树债还粗一个数量级**。

同时违反 **I4**（仅打正确级别）、**I7**（干净子树跳过）、**信条二**（更新粒度=单属性），是宪章反模式「万能脏标记」的活体标本。现有 `bind*` 手传值「碰巧」与属性槽一致，正确性靠自动链路兜底，手传 impact 实际是噪声 + 性能 bug。

### 修复（本轮 P0 还债）

- `createEffect(UiStyleChangeImpact, Runnable)` → `createEffect(Runnable)`：删 impact 参数 + 删末尾全局 bump 整段，退化为纯「依赖变→重跑 body」。
- `bind(UiStyleChangeImpact, ReadableSignal, Consumer)` → `bind(ReadableSignal, Consumer)`：删 impact 参数。
- 6 个 `bind*` 删传给 `bind` 的 impact 实参，applier 主体不变。
- 标脏完全交给属性 setter 自带的节点级精确自动链路。
- 同步更新调用方与测试（`UiComponentRuntimeBindExpandTest` 2 处删实参 + 删 import、`UiComponentRuntimeShowTest` 2 处纯副作用 effect 适配无参签名、`UiComponentRuntimeBindingTest` 断言 `+2→+1`）。

### 删除前安全核实（explorer 全新会话侦察）

5 类写入（style setter / setText / setAttribute / classList / 结构增删）**全部自带自动节点级标脏**。唯一 B 类反例 = `createEffect` body 纯副作用、完全不碰 DOM（仅测试 ShowTest:200/296），这类本就不需要脏标记，删全局 bump 对其「漏」的只是冗余标脏，正确性无害。**未来若新增「改视觉却无自动标脏的 setter」**，正确做法是让该 setter 自带节点级标脏，而非靠桥接层全局 bump 兜底（不预造纯订阅 API，YAGNI）。

---

## 三、其余焦点裁定

### 焦点 2：COMPOSITE 断链 —— 过时注释（P2 文档债，未处理）

`UiStyleChangeImpact.COMPOSITE` 注释（:22-24）说「当前降级为 PAINT」，但实际：`ElementNode` listener 对 COMPOSITE **已调** `markCompositeMutated`（仅 bump compositeVersion），消费端 composite-only 回放路径 `tryApplyCompositeReplayOnCache` **已完整存在**。即基础设施在「属性槽自动标脏 → 消费端回放」链上**已连通**，注释过时。

- 待办：写一个 transform-only 动画测试，验证连续帧是否真命中 `tryApplyCompositeReplayOnCache`；若命中，仅需更新过时注释（P2）；若未命中，排查是否有把 transform 当 PAINT 处理的旁路。
- **真机帧率必须由用户跑**。

### 焦点 3：双轨冗余 —— 与 P0 同根，已随 P0 收敛

宪章行 9 要替代的是**写侧命令式 bump 心智**，不是读侧版本比对。读侧版本比对是 I8「按脏标记复用缓存」的合法实现，**保留**。真冗余就是 P0 那次全局二次标脏，删除后：effect 职责回归纯粹（标脏是 setXxx 的副作用，effect 不再关心级别），版本号退化为纯读侧缓存键、不再是控件手动维护的命令式状态——恰好兑现行 9 本意。

### 焦点 4：I7 容器子树标脏债（行 242）—— 紧迫性上升，排 P0 之后

行 242 债（`recordStructuralMutation → markSubtreeLayoutMutation` 对容器 append/removeChild 无条件向下递归标脏全部后代）紧迫性随 reactive 细粒度落地上升，但粒度比 P0 全树标脏小一个数量级。**修了 P0 后它才成为剩余唯一的粗粒度标脏点**，那时真机 ROI（批次 2 列表密集型 60→<45）才测得准。优先级维持 P1，触发条件依原登记，度量前提=P0 已修（本轮已满足）。

---

## 四、决策：版本号比对作为 I8 缓存实现保留（需在决策层固化）

oracle 标注此点触及不变量解释边界，经用户拍板登记决策：**写侧标脏由属性自动推导，读侧版本号比对作为 I8 缓存实现保留**，行 9「替代命令式版本号」指替代**写侧命令式 bump**，非读侧版本比对。详见 `docs/记忆/决策/DECISION-20260618-reactive-dom-invalidation-version-as-cache-key.md`。

---

## 五、分阶段收口路线图

| 阶段 | 内容 | 优先级 | 状态 |
|---|---|---|---|
| 阶段 0 | 宪章语义澄清（版本号=读侧缓存键）写入决策层 | — | 本轮已登记 |
| 阶段 1 | 根除双标脏（删 createEffect/bind 的 impact + 全局 bump） | P0 | 本轮已还清（`fa1fed61`） |
| 阶段 2 | COMPOSITE 连通验证 + 过时注释修正 | P1/P2 | 待办（需真机帧率） |
| 阶段 3 | 还行 242 容器子树标脏债（reconcileChildren 批量提交） | P1 | 待办（批次 2 真机 ROI 触发） |
| 阶段 4 | 宪章偏离登记结算（行 9 转正、行 242 翻转） | — | 阶段 1/3 完成后 |

---

## 六、验证

- `compileJava` BUILD SUCCESSFUL。
- 定向测试 `club.heiqi.uilib.ui.component.* + ui.reactive.*`：116 tests / 0 failures / 0 errors。
- grep 全代码库旧签名 `createEffect(UiStyleChangeImpact` / `bind(UiStyleChangeImpact`：零残留。
- `git diff --check` 干净。
- 未碰 `SceneRuntime` 的 `bind(Invalidation...)`（另一套 runtime）与 `HtmlLikeDocumentWidget:250` 裸 `Owner.createEffect`（故意避开桥接层）。
