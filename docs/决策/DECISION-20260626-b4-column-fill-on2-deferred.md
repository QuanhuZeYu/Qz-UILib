# 决策：B4 COLUMN fill O(n²) 约束判定 — 缓做记录

- 日期：2026-06-26
- 决策者：用户拍板（缓做，留下原因以便未来遇到场景时方便找到改进点）
- 评估者：oracle（新开 session）
- 状态：**缓做**（记录原因与改进点，待真实场景触发后启动）

## 问题描述

`childConstraintsWouldChange` 判定「下传约束是否会变」时，
对每个子节点调 `buildChildConstraints` 两次（新约束+旧约束）比较。
COLUMN fill 场景下 `buildChildConstraints` 内部遍历全部子求 remainingHeight，
外层再遍历每个子 → O(n²)。

精确位置：`SceneLayoutEngine.java:430-441`

## 缓做原因

### 1. 触发面极窄

- `:431` `Objects.equals(cur, prev)` 短路：**约束不变的干净帧直接 O(1) 返回**，
  99% 帧不进 O(n²)
- 只有 **COLUMN 容器 + 有唯一 fill 子 + 约束真的变了** 才触发
- `findUniqueColumnFillChild` + `computeRemainingHeightForUniqueColumnFillChild`
  内部还各遍历一次子

### 2. 非热路径

仅在 `constraintsChanged==true`（约束真变）时进入，干净帧被 `:431` 短路。
当前无大 n COLUMN fill 容器频繁改约束的实际场景。

### 3. 正确性敏感

此方法是 I7「干净子树跳过」放行闸门的一部分。
改错会导致：
- 约束变化时该下沉的子树被误跳过 → 布局陈旧
- 该跳过的被误重算 → 破坏 I7 零开销

`buildChildConstraints` 与 `layoutInternal:237-242`/`performLayout`
的 childConstraints 口径有「双处务必同步」硬约束。

## 未来改进点（待真实场景触发后启动）

### 推荐优化方案（oracle 裁决）

**候选 1（预计算缓存）+ 候选 3（首子短路）组合**：

1. **首子短路**：非 fill 子的 childConstraints 只依赖 `innerWidth`
   （`buildChildConstraints:289-290` + COLUMN 非 fill 子 childHeight 恒为 UNCONSTRAINED）。
   先比较 cur/prev 的 innerWidth（O(1)）。
   若无 fill 子 → 所有子 childHeight 都是 UNCONSTRAINED，
   只需比 innerWidth，O(n) 退化为 O(1) 宽度比较 + O(n) 遍历确认无 fill。

2. **预计算缓存**：当确实有唯一 fill 子时，
   在循环外对 cur 和 prev 各算一次 `findUniqueColumnFillChild`（与子无关，算一次）+
   各算一次 `remainingHeight`（各 O(n)），
   循环内 fill 子直接复用预算值、非 fill 子只比 innerWidth → 总体 O(n)。

### 实施铁律

1. **等价化简**：预计算出的值必须与 `buildChildConstraints`
   对同一 (node, constraints, child) 的返回逐字段相等。
   任何短路分支都必须是 `buildChildConstraints` 逻辑的无损代数化简。

2. **口径同步**：`buildChildConstraints`、`layoutInternal:237-242`、
   `performLayout:477` 三处 childConstraints 口径必须同步。

3. **补测试**（正确性 > 性能）：
   - 等价性测试：优化前后 `childConstraintsWouldChange` 返回完全一致
   - I7 反证延续：`columnFillChildrenDoNotOverflowParent`、
     `depthFillChildGetsParentHeightThroughCleanMiddle`、
     `cleanDecoSiblingNeverRelayoutedOnConstraintChange` 保持绿
   - 口径同步守卫：断言「非 fill 子约束只依赖 innerWidth」
   - 性能测试（可选）：大 n COLUMN fill 容器约束变化帧
     buildChildConstraints 调用次数从 O(n²) 降到 O(n)

4. **reviewer 复核**：改完必须经独立 reviewer/oracle 复核 I7 闸门等价性

### 触发条件

当出现以下真实场景时启动优化：
- 大 n COLUMN fill 容器频繁改约束（如 Table/编辑区大列表 fill 视口频繁 resize）
- 帧率测试暴露 O(n²) 开销

## NORTH_STAR 影响

- **I7**：优化是 I7 放行闸门的等价化简，不改变跳过/下沉/重算判定结果，不破 I7
  （前提是等价性测试坐实）
- **I8**：缓存复用语义不变
- 不需登记偏离（纯性能优化，行为等价，不动任何不变量语义）

## 配套文档

- oracle 架构审核产出（历史审查报告已清除，结论沉淀于 `docs/架构/scene技术债.md`）
