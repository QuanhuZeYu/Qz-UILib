# DECISION-20260628：L1 嵌套 grow 子容器场景修复（priorKnownInnerHeight 不对称判定）

## 状态

已落地（2026-06-28，第 129 次会话）。编译零问题，144 测试全绿（含 11 新增回归测试）。
reviewer 有条件通过（P1 测试 4 反证力度不足已修复）。Oracle 测试充分性评估后补齐
5 个遗漏项（padding/preferredHeight/ROW/percent 嵌套/scrollable+fill）。

## 决策缘起

技术债 L1「嵌套 grow 子容器场景」原登记为"reviewer 建议级已知边界，待真实需求触发"
（DECISION-20260628-scene-min-max-clamp.md:114-115）。本轮系统性清理技术债时，
派 librarian 研究行业解法 + explorer 侦察源码现状，产生矛盾结论：

- **Librarian**：所有主流框架（CSS/Flutter/Qt/Yoga）都不解嵌套 grow，回退 shrink 是业界一致行为
- **Explorer**：L1 的精确机理与主流框架不同——X 已收到父级 grow 分配的确定高约束，
  但 `priorKnownInnerHeight` 只认 fill 不认 grow，导致确定高无法下传

经 Oracle 裁决确认：**L1 是可实现缺陷（不对称判定），非有意边界**。

## 核心分析

### 主流框架的场景区分

| 场景 | 容器 X 的高度状态 | 主流框架行为 |
|------|------------------|-------------|
| 场景 A（真未确定） | X height:auto 且父无确定高 | 内层 grow 回退 shrink-to-fit |
| 场景 B（已由父分配确定） | 父有确定高，X 是 grow 子分到确定空间 | **内层 grow 正常生效** |

CSS Flexbox §9.8 "Definite and Indefinite Sizes"：flex item 的 main size 被 §9.7
算法解析定稿后视为 **definite**，内层 grow 正常生效。Librarian 的"主流都不解"
混淆了场景 A 和场景 B。我们 L1 命中的是场景 B。

### 不对称判定机理

- `SizingCalculator.computeHeight:266`（自身高）：认 fill/grow/percent 三者合流
- `ConstraintResolver.priorKnownInnerHeight:200`（下传先验）：只认 fill
- **X 自己高度对（computeHeight 认 grow），但传不下去（priorKnownInnerHeight 不认 grow）**

## 最终选择

放宽 `priorKnownInnerHeight` 闸门，对齐 `computeHeight:266` 三合流口径：

```java
// 从
if (node.isFillParentHeight() && constraints.hasHeightConstraint())
// 改为
if ((node.isFillParentHeight() || node.getFlexGrow() > 0 || node.getPercentHeight() > 0)
        && !node.isScrollable()
        && constraints.hasHeightConstraint())
```

### 设计要点

1. **加 grow/percent**：修 L1 不对称，对齐 computeHeight:266 口径
2. **排除 scrollable**：viewport 语义主动忽略内容撑大，内高不作子先验；
   通过 `!isScrollable()` 保持与 `viewportHeight:334` 的跨类契约 2 一致
3. **守 I7**：纯读静态元数据 + 入参，不回看子 cache，不向下递归
4. **保留 `max(available, preferredHeight) - padV`**：对 grow/percent 子同样适用

### 不需改的 4 处同构判定点

- `SizingCalculator:266`（computeHeight）—— 已认 fill/grow/percent
- `SizingCalculator:334`（viewportHeight）—— scrollable 专用，通过 !isScrollable() 排除保持一致
- `SizingCalculator:368`（isHeightConsumingConstraint）—— 已认 fill/grow/percent/scrollable
- `FlexLayouter:366`（containerMainExtent）—— 主轴对齐 offset 非先验，非同一缺陷

## 回归测试（11 个）

### 首批 6 个（主修复 + scrollable 排除 + I7 + maxHeight）

1. `nestedGrowChildFillsInnerHeightFromGrowParent`——L1 主修复反证
2. `nestedGrowChildFillsInnerHeightFromPercentParent`——percent 变体
3. `nestedTwoLevelGrowDefinitePropagatesDown`——两层嵌套 definite 下传不断链
4. `scrollableGrowContainerDoesNotPassInnerAsGrowPrior`——scrollable 排除反证
   （reviewer P1：原用固定子反证力度不足，改为 grow 子，去掉排除则 grow 子=200 而非 16）
5. `nestedGrowTreeCleanFrameFullSkipOnSameConstraints`——I7 干净帧
6. `nestedGrowChildClampedByMaxHeightInGrowParent`——maxHeight 撞顶

### 补齐 5 个（Oracle 测试充分性评估后补）

7. `nestedGrowChildRespectsParentPadding`——grow 父带 padding，padV 扣减路径（高风险）
8. `nestedGrowChildUsesPreferredHeightWhenLargerThanConstraint`——grow 父带大 preferredHeight，max 下界下传（高风险）
9. `nestedRowGrowContainerPassesCrossHeightToChildren`——ROW grow 容器作中间层，cross 高下传（中风险）
10. `nestedPercentParentPassesDefiniteToPercentGrandchild`——percent 父 + percent 孙子，definite innerH 喂 pctH（中风险）
11. `scrollableFillContainerDoesNotPassInnerAsFillPrior`——scrollable+fill 容器，`!isScrollable()` 排除对 fill 也生效（中风险）

## 影响范围

- `ConstraintResolver.java`：`priorKnownInnerHeight` 闸门放宽 + Javadoc 同步（+32 / -6）
- `SceneLayoutEngineTest.java`：6 回归测试（+219）
- 无现有测试受影响（Explorer + reviewer 确认）

## 后续注意事项

- **三处同步关系**：改 `priorKnownInnerHeight` 闸门时必须同步检查 `computeHeight:266`
  和 `isHeightConsumingConstraint:368` 的口径一致性
- **跨类契约 2**：与 `viewportHeight:334` 的耦合不变量通过 `!isScrollable()` 排除保持一致
- **containerMainExtent 对齐 offset**：`FlexLayouter:366` 只认 fill，grow 容器在 maxHeight
  撞顶时 CENTER/END offset 可能偏——原有行为，非本次引入，属已知边界
- **P3 建议补测**：grow+shrink 混合 / grow+preferredHeight / percent 双层 / padding 非零
  场景（reviewer 建议级，非阻断）

## 决策来源

- 触发：技术债伪债务清理 → L1 重新审视 → librarian/explorer 矛盾结论 → Oracle 裁决
- Oracle：确认 L1 是缺陷（非有意边界），修正 Explorer 补丁（漏 percent + 需排除 scrollable）
- Explorer 补查：确认 FlexLayouter:366 非对称点，全量 5 处同构判定点穷尽
- 关联决策：`DECISION-20260628-scene-min-max-clamp.md`（L1 原登记为"已知边界"，已重新定性）
