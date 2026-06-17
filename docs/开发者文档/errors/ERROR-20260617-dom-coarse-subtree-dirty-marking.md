# ERROR-20260617 — DOM 层粗粒度结构标脏：列表项增删污染未变兄弟子树

> 性质：**先验存在的地基性能债**，正确性无损，I7（干净子树被跳过）在「列表项增删」场景未达成。
> 发现于：控件层响应式重构批次 0（Breadcrumb 切口），forEach keyed 复用让该债首次可观测。

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
4. layout 层 `layoutElement`(:244) → `resolveReusableLayoutBox`(:404-434) 是跨帧跳过的**唯一闸门**；复用硬门(:422-423)：`previousBox.version != element.version` 或 `previousBox.subtreeVersion != element.subtreeVersion` → `return null`（复用失败）→ 完整重算。
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
