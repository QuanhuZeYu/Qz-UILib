# 审核：scene 几何量与 clip 口径温床修复（B1 + B3/I7）

- 日期：2026-06-25
- 分支：`fix/scene-geometry-clip-bugbed`
- 审核 commit：`9ac1fd9c`（B1）、`9e46759f`（B3/I7）
- 审核方式：**主 Agent 自审**（reviewer 子代理两次空返回、原 session 上下文失效，按诚实原则标注未经独立子代理复核）
- 任务来源：oracle 架构审核报告（清单 B1/B3 + I7 邻域缺口）

## 审核结论：通过

两处修复均守 NORTH_STAR 关键不变量，数学等价性确认，测试全绿，无回归。
B3 实际危害经源码核实**低于 oracle 报告定性**（fixer-2 推测成立，详见下文），
本次修复价值为**防御性口径统一**，消除未来 transform/scrollOffset 解耦时的分裂温床。

## 不变量核对

| 不变量 | B1 | B3 | 结论 |
|---|---|---|---|
| I1 信号优先 | — | — | 不涉及（纯几何/只读） |
| I4 正确失效级别 | 守 | 守 | 改动不触 setter/标脏 |
| I7 干净子树跳过 | 守 | 守 | B3 正是补 I7 邻域缺口 |
| I10 paint 只读 | — | 守 | needClip 改用谓词，无副作用 |
| I11 逃生舱只读 | 守 | 守 | absoluteBox/hitTester 全程只读 |

## B1 修复审核（commit `9ac1fd9c`）

### 数学等价性（已确认）

- `SceneGeometry.absoluteBox`（`SceneGeometry.java:73-96`）沿 parent 链累加 LayoutBox.x/y，
  且每跳到 parent 时 `y = childYBase(parent, y)`（`:91`），注入所有 scrollable 祖先的 scrollOffsetY。
- 原 TextArea 私有 `absoluteY` = 裸累加 LayoutBox.y（不注入 scroll）；
  原 handler `relY = pointerY - absoluteY(content) + scroll`（手动补偿 content 父 viewport 一层）。
- 新 handler `relY = pointerY - absoluteBox(content,0,0).getY()`
  = `pointerY - (裸累加Y - scroll)` = `pointerY - 裸累加Y + scroll`，数学等价 ✓。
- **额外修复**：`absoluteBox` 注入**所有** scrollable 祖先的 scrollOffsetY，
  原手动 `+scroll` 只补偿一层。若 content 上方有多层 scrollable 祖先，原代码漏补偿，
  新代码正确补偿——这是修复潜在多层滚动漏补偿，非回归。

### 调用点完整性（已确认）

三个 primitive 内 `absoluteX`/`absoluteY` grep 已无残留（fixer-1 简报 + diff 净减 77 行印证）。
Slider/TextInput 的 `absoluteX` 改 `SceneGeometry.absoluteBox(track/root,0,0).getX()`，
未涉及 Y，当前无横向滚动，X 口径与 hit-test/paint 一致 ✓。

### SceneGeometry 未改（合理）

现有 `absoluteBox(node, 0, 0)` 签名满足所有调用点，无需新增权威方法 ✓。

## B3/I7 修复审核（commit `9e46759f`）

### 【关键】fixer-2 推测核实——成立

fixer-2 称"纯 clipChildren 非 scrollable 节点，hit-test 的 clip bounds 与节点自身
LayoutBox 绝对盒完全重合，指针进入子树前已被节点自身 bounds 检查挡住，
故 B3 描述的'视觉裁掉但仍可点击'在纯 clipChildren 场景下实际不成立"。

**读 `SceneHitTester.hitTestRecursive`（`SceneHitTester.java:64-150`）源码核实**：

1. `:82-90` 先做祖先 clip 交集裁剪（hasClip 时节点盒与祖先 clip 取交集，无交集返回空）
2. `:93-96` 再做节点自身 bounds 命中判定（指针不在节点盒内返回空）——**此检查在 clip 之前**
3. `:106-123` 若 `node.isClipWindow()`，为**子节点**建立/更新 clip bounds（= 节点自身 absX/Y/w/h）

推理链：
- clipChildren 节点（非 scrollable）的 clip bounds = 节点自身盒（`:118-121`）
- 指针要进入子树，必先通过父节点自身 bounds 检查（`:93-96`），即指针必在父盒内
- 指针在父盒内 → 子节点 clip bounds（=父盒）必包含该指针位置
- 故纯 clipChildren 场景，clip 对 hit-test 无独立可观测作用，父 bounds 先行兜底

**结论**：fixer-2 推测成立。B3 当前**无独立可观测正确性 bug**，
oracle 报告"视觉裁掉但仍可点击"的定性偏高。
本次修复价值是**防御性口径统一**：防止未来引入 transform/其他使视觉位置
与 LayoutBox 解耦的机制时，paint/hit-test 再次分裂（与已登记的 scrollOffset
三处独立累加同型温床）。测试 `clipChildrenShouldClipChildOutsideBounds`
的"超出部分不命中"主要由节点自身 bounds 兜底，但仍作为回归锚点锁定未来口径。

### 谓词语义与口径一致（已确认）

- `SceneNode.isClipWindow()` = `isClipChildren() || isScrollable()`（diff 确认）
- `ScenePaintEngine:161` needClip 改用 `node.isClipWindow()` ✓
- `SceneHitTester:106` clip 递归改用 `node.isClipWindow()` ✓
- paint 与 hit-test 现共用同一谓词，口径完全一致 ✓

### hit-test 无状态性（已确认）

改动未引入可变成员，`SceneHitTester` 仍无状态，符合 I7/I11 ✓。

### 嵌套 clip 交集（已确认）

`:107-115` 嵌套时取已有 clip 与节点盒的交集（Math.max/min），逻辑正确 ✓。

### 测试质量（合格）

新增 5 条用例：
- `clipChildrenShouldClipChildOutsideBounds`：超出边界不命中（回归锚点，当前由自身 bounds 兜底）
- `clipChildrenVisibleHitChainIncludesClipNode`：命中链含 clip 节点
- `nestedClipChildrenIntersectionShouldClip`：嵌套交集裁剪
- `clipChildrenAndScrollableBothActAsClipWindow`：组合场景
- `isClipWindowPredicateSemantics`（SceneNodeTest）：谓词四态

覆盖口径统一语义，作为未来防回归锚点合格 ✓。

### 回归风险（已确认无）

clipChildren 节点现在 hit-test 也建 clip bounds，但因父自身 bounds 先行检查，
实际 hit-test 行为对纯 clipChildren 场景零变化。scrollable 场景原本就建 clip，
改谓词后语义不变。无误裁风险 ✓。

## 偏离登记必要性

- **B1**：消除温床、对齐已有权威原子 `SceneGeometry.absoluteBox`，属于"补全"非"偏离"，无需登记。
- **B3**：补 I7 邻域缺口（paint/hit-test 口径统一），属于"补全"非"偏离"，无需登记。
- 两条均为温床消除，不改变 NORTH_STAR 信条与不变量本身，无需登记偏离。

## 遗留与建议

1. **B1 反例覆盖**（低）：当前测试套未覆盖"primitive 嵌入外层 scrollable 容器后点击坐标正确"
   的场景。建议后续补一条 Slider/TextInput 嵌入 scrollable 的回归锚点（推测：非本次回归）。
2. **B6 未修**（中）：transform + clipChildren/scrollable 叠加时 CLIP 框用未变换坐标，
   本次 B3 修复未覆盖该组合（oracle 报告 B6，未在本轮范围）。建议后续单独处置。
3. **真机验收**（中）：clip 口径统一 + primitive 几何统一虽无行为变化，
   建议下次真机时顺带确认 Select/Slider/TextInput 在滚动容器内的点击定位无异常。

## 验证状态

- `jetbrainsBuildProject` 全量编译通过，0 problems
- `./gradlew.bat test --tests "*scene*"` BUILD SUCCESSFUL，569 测试 0 失败 0 错误
- 含 SceneHitTesterTest 20（原 16 + 新 4）、SceneNodeTest 35（原 34 + 新 1）
