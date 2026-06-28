# DECISION-20260628 scene min/max clamp + percent + margin + align-self

## 状态

**已完成**（deepwork 四期全部合回 4.0，2026-06-28）。NORTH_STAR §偏离 2026-06-20
剩余债全部还清。133 SceneLayoutEngineTest + 714 全套 scene 测试零回归。
四期独立 reviewer 审核全部通过。

## 背景

NORTH_STAR §偏离 2026-06-20 登记 COLUMN 主轴 fill 子未完整实现，flexGrow 权重分配
已于上会话还清，剩余债为「min/max 高度 clamp + percent + margin + align-self」。
status 预警「未来补 maxHeight 时撞顶重分配需重新评估 I7 单 pass 边界」。

本决策记录 deepwork 四期的 oracle 8 项裁决 + 用户 5 项拍板，作为长期可引用的设计决策。

## oracle 8 项裁决

| # | 议题 | 裁决 |
|---|------|------|
| 1 | maxHeight 语义 | 声明式 int 元数据（父级先验可读，类比 preferredHeight）；容器 maxHeight 暂不支持（返回 UNCONSTRAINED） |
| 2 | minHeight vs preferredHeight | 合流（不新增字段，preferredHeight 既有 max(natural, pref) 已是 min-height 语义） |
| 3 | 撞顶重分配路线 | 路径甲（computeColumnGrowHeights 内 freeze do-while，守 I7） |
| 4 | I7 表述精化 | 补注「父级数值求解器内多轮迭代 ≠ 多 pass」 |
| 5 | margin 横切面 | 拆第三期（五处联动，不与 min/max 混做） |
| 6 | percent 基准 | 父先验内高 + fallback shrink + 与 grow 互斥 grow 优先 |
| 7 | align-self 枚举 | 方案 B（独立 AlignSelf 枚举 + AUTO，effectiveCrossAlign 回退在 FlexLayouter） |
| 8 | 分期 | 四期：① min/max ② align-self ③ margin ④ percent |

## 用户 5 项拍板

| 决策点 | 用户选择 |
|--------|---------|
| I7 补注是否纳入 | 纳入（不变量表述改动，用户已确认） |
| clamp 优先级（preferredHeight vs maxHeight 冲突） | min 赢（CSS 语义），clamp = max(preferredHeight, min(natural, maxHeight)) |
| 下界 freeze 范围 | 上界 + 下界对称（非推荐项，工作量扩大但语义完整） |
| 容器 maxHeight 范围 | 收窄（只对叶/grow 子先验生效） |
| percent 是否排期 | 也排期（四期全做） |

## 核心设计决策

### 1. 路径甲 freeze do-while 守 I7

maxHeight 是声明式 int 元数据（父级先验可读），使 `computeColumnGrowHeights` 在分配前
就知道每个 grow 子的上界。撞顶检测纯读元数据、不 layout 子，守 I7 铁律
「buildChildConstraints 严禁读子 cachedLayout」。

freeze do-while 全程只读 effectiveGrow / maxHeight / preferredHeight / priorKnownChildHeight，
不读任何子 cachedLayout、不 layout 子、不向下递归，收敛后一次性下传 tight 约束。

### 2. I7 数值求解器边界澄清补注

I7 约束的是「父→子约束下沉的次数」（必须恰好 1 次，定稿后不回看子 cache 重算），
**不约束父级在下沉前自己迭代几轮数值求解**。判据：求解器若需要「先 layout 子、读子结果、
再回头改父分配」即违反 I7；若全程父级数值迭代、子在收到最终约束后才首次 layout，则守 I7。

### 3. clamp 优先级 min 赢 CSS 语义

`clamp = max(preferredHeight, min(natural, maxHeight))`。preferredHeight 作下限、
maxHeight 作上限，矛盾配置时 min-height 赢（CSS 标准语义）。

### 4. align-self 独立枚举方案 B

独立 `AlignSelf` 枚举（AUTO/START/CENTER/END/STRETCH），AUTO 回退父级 crossAxisAlign。
`effectiveCrossAlign(parent, child)` 回退逻辑在 FlexLayouter 内。不污染 CrossAxisAlign 容器级语义。

### 5. margin 五处联动

margin 是跨三协作者的横切改动：SizingCalculator(computeContentHeight/computeShrinkContainerWidth)
+ ConstraintResolver(computeColumnGrowHeights/buildChildConstraints) + FlexLayouter(positionChildren)
+ SceneGeometry(maxScrollY)。改任一处需同步审视其他四处。

### 6. percent 子隐式 fill + grow 优先

percent 子作固定子（高 = innerH × pct / 100），不参与 grow 分配。percent 子隐式 fill
（isHeightConsumingConstraint + computeHeight 条件同步扩展），与 grow 子隐式 fill 对称。
grow 优先：effectiveGrow > 0 时忽略 percent。percentHeight 仅 COLUMN 主轴生效，ROW 下不生效。

## 选型对比

### 撞顶重分配路线

| 维度 | 路径甲（Qt 变体，选） | 路径乙（Flutter） | 路径丙（Yoga 两 pass） |
|---|---|---|---|
| 撞顶多余空间 | 重分配给未冻结子 | 变 free space 由 mainAxisAlign 分配 | 重分配（仅一轮） |
| pass 数 | 父级内 do-while 变长 ≤n | 1 | 固定 2 |
| I7 兼容性 | 守 I7（数值迭代不破单 pass） | 守 I7 | 部分兼容 |
| 语义正确性 | 最正确（Qt/CSS freeze 语义） | 弱（撞顶不回流） | 中（只处理一轮） |
| 前提 | maxHeight 是声明式元数据 | 无前提 | 无前提 |

核心判定条件：子的 maxHeight 是否为父级可读的静态元数据。
若是 → 路径甲可行（守 I7）；若否 → 只能走路径乙（守 I7 但语义弱）。
本项目 maxHeight 设计为声明式 int，路径甲可行。

### align-self 枚举

| 维度 | 方案 B（独立枚举，选） | 方案 A（CrossAxisAlign 加 AUTO） |
|---|---|---|
| 容器级语义污染 | 无（AUTO 隔离在 AlignSelf） | 有（容器无父级可继承） |
| switch 扩散 | 无（只改 FlexLayouter 一处） | 有（所有读 CrossAxisAlign 的 switch 补 AUTO） |
| CSS 模型对齐 | 是（align-items vs align-self 双枚举） | 否 |

## 实施分期

| 期 | 内容 | commit | 测试 |
|----|------|--------|------|
| 一 | min/max clamp + I7 补注 | 8f3cb20b + c1c7a37a + 1f7d3b05 + db85c936 + merge 060c6aa3 | M1-M12 |
| 二 | align-self + 边界 2 回填 | 0ecfab24 + merge fadf36f0 | A1-A9 |
| 三 | margin 五处联动 + maxScrollY | fefd6bd3 + 156317ab + 59f3e219 + merge 6911faa2 | G1-G11 |
| 四 | percent + maxHeight clamp + 内容撑大 | 993894f0 + 0c1c93fd + merge ba3635c3 | P1-P12 |

## 已知边界

- 嵌套 grow 子容器场景（容器 X 是父的 grow 子但非 fill 时，X 内 grow 子回退 shrink）未覆盖，
  待真实需求触发再扩展
- childConstraintsWouldChange 逐子调 buildChildConstraints 叠加 freeze do-while 使脏判定
  为 O(n²)，待性能暴露再评估记忆化
- percentHeight 在 ROW 容器下不生效（有意边界，字段 Javadoc 已明确）
- 容器节点 maxHeight 收窄范围（只对叶/grow 子先验生效，容器先验高仍走「有子→UNCONSTRAINED」）

## 关键文件引用

- 宪章：NORTH_STAR.md §I7 数值求解器边界澄清补注 + §偏离 2026-06-20（已还清）
- 代码：ConstraintResolver.computeColumnGrowHeights（freeze do-while + percent 分支）
- 代码：SizingCalculator.clampHeight/clampWidth + computeHeight/computeWidth percent 分支
- 代码：FlexLayouter.effectiveCrossAlign + STRETCH maxWidth/percentWidth 豁免 + margin 偏移
- 代码：SceneGeometry.maxScrollY（含子 marginBottom）
- 代码：SceneNode maxHeight/maxWidth/percentHeight/percentWidth/margin 四向/alignSelf 字段族
- 代码：AlignSelf 枚举（`club.heiqi.uilib.ui.scene.layout` 包）
- 测试：SceneLayoutEngineTest M1-M12 + A1-A9 + G1-G11 + P1-P12（共 40 新测试）
