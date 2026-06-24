# ERROR-20260617 — DOM 层粗粒度结构标脏：列表项增删污染未变兄弟子树

> 性质：**先验存在的地基性能债**，正确性无损，I7（干净子树被跳过）在「列表项增删」场景未达成。
> 发现于：控件层响应式重构批次 0（Breadcrumb 切口），forEach keyed 复用让该债首次可观测。
> **状态（2026-06-18）：已还清。** 用「方案 X」（递归标脏降级为非递归）修复并合回 `4.0`（merge `a4290a2d`），NORTH_STAR 偏离登记已结算转正。修复经过与方向纠偏见下方《修复结案》。

## 错误现象

控件用 `UiComponentRuntime.forEach` 做 keyed 列表渲染后，列表项局部增删（如面包屑 `a.b.c → a.b.d`，仅末段变化）时：

- forEach **正确复用**了未变段（根/`a`/`a.b`）的 wrapper 节点对象（`assertSame` 全过，reconciler 稳定项零销毁重建）。
- **但**未变段 wrapper 的 `__getSubtreeLayoutMutationVersion()` 在 flush 前后被刷新（实测 90 → 112）。
- 后果：layout 层 `resolveReusableLayoutBox` 的 version 闸门判定这些未变段「复用失败」，对它们及其全部后代执行**真实重算**。理想的 I7（干净子树三阶段跳过）在该场景未达成。

## 触发场景

任何经由容器 `appendChild` / `removeChild` / `insertBefore` 的列表项增删，无论：
- 命令式 `clearChildren()` + for 循环全量重建（旧模式），还是
- `forEach` keyed 复用（新模式）。

旧模式下所有兄弟本就要重建，污染被「反正都要重建」淹没看不见；forEach 复用了节点对象后，债才浮出水面。**该债不是方向 A / 控件层响应式重构引入的，而是先验存在、被首次清晰暴露。**

## 根本原因

证据链（`ui/dom/DocumentNode.java` + `ui/layout/DocumentLayoutEngine.java`）：

1. 容器 `removeChild`(:196) / `appendChild`(:177) / `insertBefore`(:239) 均调 `recordStructuralMutation`(:517)。
2. `recordStructuralMutation` → `markSubtreeLayoutMutation(version)`(:519)，接收者是**被操作的容器**。
3. `markSubtreeLayoutMutation`(:540-546) **无条件递归**把容器下**所有 children**（含未变兄弟）及其全部后代的 `layoutMutationVersion`(:541) **和** `subtreeLayoutMutationVersion`(:542) 都重置为新 version。
4. layout 层 `layoutElement`(:244) → `resolveReusableLayoutBox`(:404-434) 是跨帧跳过的**唯一闸门**；复用硬门(:422-423)：`previousBox.version != element.version` 或 `previousBox.subtreeVersion != element.subtreeVersion`
   → `return null`（复用失败）→ 完整重算。
5. 无任何下层兜底缓存：`LayoutContext` 的缓存(:1309-1319)都是 pass 内 memo（以 ElementNode 为 key），**不跨帧**，无法阻止重算本身发生。

一句话：**DOM 层把「容器子节点集合增删」翻译成「容器全部后代 layout 失效」，layout 层忠实按 version 执行真实重算。这是粗粒度结构标脏，不是保守信号——下游没有东西能把它收窄回去。**

`KeyedListReconciler` 侧已是最优（LIS 求稳定项，稳定项零 DOM 操作，:133-146 只对 `needsMove` 项调 `insertBefore`），污染**完全**来自 DOM 层标脏粒度，非 reconciler 责任。

## 修复方案（方向，待真机 ROI 触发后实施）

**方向 1（推荐，最小侵入）**：让 reconciler 通过专用批量 API 提交结构变更，绕过逐次 append/remove 的粗粒度标脏：
- 新增 `ElementNode.reconcileChildren(List<ChildOperation> ops)`，`ChildOperation` 描述 `{type: INSERT|REMOVE|MOVE, node, anchor}`。
- `KeyedListReconciler.reorder` 收集所有操作，最后一次性提交。
- 提交内部：先改 parent/children 引用，再精确标脏——结构上新增/移除/移动的节点必标脏；引用复用、未 MOVE 的稳定项**只标脏容器自身**，不递归向下。
- 容器自身 version 仍自增（子节点集合变化，容器确需重排子项），但不对稳定复用子节点调 `markSubtreeLayoutMutation`。
- layout 层无需改（稳定子节点 version 未被刷新 → version 匹配 → 复用成立）。

成本粗估：`DocumentNode` + `KeyedListReconciler` 改动约 200-400 行 + 测试，中等工程量，当前架构上可增量修。
风险：中——需覆盖 flex/block/inline 各 display 模式下「兄弟增删是否影响未变兄弟几何」的全部分支，不能漏标脏真正变化的节点。

**方向 2（不推荐）**：重构 `markSubtreeLayoutMutation` 为按需传播——需 DOM 层理解 layout 语义，跨层耦合，与 I6 冲突。

## 预防措施 / 优先级

- 正确的 I7 度量是 `LayoutContext.getReusedLayoutSubtreeCount()`(:1345)，比 version 字段更贴近「干净子树被跳过」真义。
- **优先级由批次 2（TreeView/Table/DataTable 列表密集型）真机帧率实测决定**：100 项列表增删一项时未变 99 项被重算，若帧率可感（如 60→45 以下）则地基债插队修复；若轻微（60→55 以上）则拖到批次 3/4 后作为性能优化专项。
- 即便有帧率回退，也**比原全量重建轻**（原来销毁重建 N 项 + N 次 new + GC，现在复用 N-1 项对象 + 重算 N-1 项 layout）。
- 回归锚点：`DocumentBreadcrumbControlTest.documentsKnownCoarseSubtreeDirtyMarkingDebt` 断言「债当前存在」，DOM 层修复后该断言会翻转失败，提示改为 I7 正向断言并清理 NORTH_STAR 偏离登记。

## 修复结案（2026-06-18，方案 X）

> 本轮 reactive→DOM 失效层系统性还债（P0 双重标脏 → COMPOSITE 连通验证 → 本债）第三阶段。**不管真机帧率、直接还清**（用户拍板）。

### 关键纠偏：方向 1（批量 API）被 oracle 否决，改用方案 X

上方《修复方案》的「方向 1（reconcileChildren 批量提交 + ChildOperation）」经 oracle（ses_12639880cffe）架构裁决判定为**过度设计，否决**。核心判断：

**债的根因不是「逐次提交」，而是 `markSubtreeLayoutMutation` 的「无条件递归」。** 把递归标脏降级为「只标自己 self+subtree + 向上冒泡」（复用既有 `markLayoutMutation` 语义）即可在**所有**结构变更入口根除债。方案 X 相对方向 1：

- **不需要** ChildOperation / reconcileChildren 新 API / 改 reconciler / 碰删除路径（约 200-400 行 → < 10 行）。
- **风险 A（删除双重移除）直接消失**：不引入批量删除，`owner.dispose → onCleanup → removeChild` 链路零改动。removeChild 单次株连也被同一处改动同步修复。
- **风险 B（分模式漏标）是陷阱**：方向 1 若按风险 B 要求「分 display 模式连带标兄弟」，就会让 DOM 层理解 flex/table 几何传播规则——**这正是方向 2 被否的撞 I6 错误**。正确做法是 DOM 层一律只标容器自己，「兄弟几何是否真变」100% 下放给 layout 复用闸门，而闸门维度已完备：
  - **flex**：grow/shrink 后最终主轴尺寸作 `forcedContentWidth/Height` 传入 → 闸门 forced 维度捕获重算。
  - **block**：只改 flowTop，不在闸门比对维度 → 走 `translatedTo` 平移复用，位置正确且子树跳过。
  - **table**：cell forcedWidth 基于列宽 → 闸门捕获。
  - **inline**：不走元素级 box 复用，无「稳定兄弟」概念，非债。

这印证了一个深层架构事实：**SceneNode 新模型的 `descendantLayoutDirty` 路标下沉，在旧 DOM 这里有 version 版等价物**——容器标 self + 冒泡刷祖先 subtree，兄弟支 version 未碰即平移复用。

### 实际改动（方案 X，保守版）

`DocumentNode.java` 共 6 处，核心是 `recordStructuralMutation`：
- `markSubtreeLayoutMutation(version)` → `markLayoutMutation(version)`（容器只标自身 self+subtree + 内部冒泡）。
- 删除冗余 `propagateSubtreeLayoutMutationToAncestors`（`markLayoutMutation` 内已含冒泡）。
- 旧父合并为单行 `markLayoutMutation`。
- `changedSubtree` 用**保守版** `markLayoutMutation`（标被移动/插入节点 self、不递归子树，正确性绝对安全；放弃 MOVE 项自身平移复用这层优化，待边界全绿后再评估激进版）。
- `replaceChild` 旧节点、`clearChildren` 同步改为「标自己不递归」。
- **`markSubtreeLayoutMutation` 私有方法保留**：`markSubtreeMutated` / `__markSubtreeLayoutDirty(int)` 仍合法调用（全局样式表/变量失效确需递归全标），非死代码。
- **reconciler / UiComponentRuntime / DocumentLayoutEngine / 删除路径全部零改动**——正面验证 oracle「根因是递归、layout 闸门已完备」的判断。

### 验证

- 回归锚点翻转：`documentsKnownCoarseSubtreeDirtyMarkingDebt` → `stableSegmentSubtreeIsNotDirtiedByListMutation`，断言 `assertTrue(after != before)`（债存在）翻转为 `assertEquals(before, after)`（I7 达成）。
- 新增 `DocumentNodeStructuralMutationDirtyTest`（5 用例）：INSERT/REMOVE 稳定兄弟零株连、嵌套子树不株连、MOVE 保守版子树保护、跨容器移动旧父株连隔离。
- `DocumentLayoutEngineTest` 追加 3 端到端正确性用例：flex forced 维度兜底、block translatedTo 平移兜底、table 列宽重算——验证闸门兜底在各模式下不显示陈旧。
- 全量 1737 测试，9 失败=历史预存环境集（`git stash` 隔离确认 baseline 同失败），零回归。compileJava 通过。
