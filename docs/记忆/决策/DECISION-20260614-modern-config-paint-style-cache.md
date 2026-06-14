# 决策：ModernConfig 绘制重放期样式备忘缓存

## 背景

`ModernConfigTemplateScreen`（`/qzuilib test` 的 MODCFG demo）展开大视图后稳态 ~3 FPS。三轮实测（用户开 `Config.useDebug` 实跑、日志见 `run/client/logs/fml-client-latest.log`）钉死根因：

- 瓶颈在「绘制重放」阶段，不在布局、不在离屏/blit、不在世界渲染（`present` 仅 0.06ms）。
- 脏子树布局缓存已生效：`resolve`≈33ms、`reusedSubtrees`=6520；每帧那 ~320ms 几乎全是重放整篇命令（`replay`≈300–420ms）。
- 真凶：`DocumentPaintRenderer` 重放时对几乎每条绘制命令都调 `UiStyleResolver.compute(element)`（`renderTextShadow`、BORDER/BOX_SHADOW/OUTLINE、`resolveCommandCornerRadii`、折叠表格判断），而 `compute(ElementNode)` **无缓存**：每次 `findMatchingRules`（整表选择器匹配）+ `computeParentStyle` **递归到文档根**重算整条祖先链；`ElementNode` 上没有 computed-style 缓存。
- 计数实测：大视图 `cmds≈727–753` 时每帧 `replayStyleComputes≈3787–3889`（≈命令数 5.2×，祖先被相邻命令反复重算）；小视图 `cmds≈164` 时 `replay` 仅 2.45ms。布局阶段用的是 `computeWithParentStyle`（单次遍历传父样式、不递归），绘制阶段却用裸 `compute()` 把这份递归每帧每命令重做。

## 候选方案

1. **绘制期按元素备忘 ComputedStyle（本次采用）**：在 `DocumentPaintRenderer.render` 一趟绘制内引入按 `ElementNode` 实例备忘的样式缓存，并经 `computeWithParentStyle` 自顶向下复用祖先链，使每个元素每趟只做一次单层级联。改动局限在单个文件、零接口变更、语义等价、风险最低。
2. **命令构建期写入样式值**：在 `DocumentPaintEngine.buildPaintCommands` 构建命令时就把渲染所需的样式量（圆角 / 边框 / text-shadow / outline / border-collapse 等）写进 `DocumentPaintCommand`，渲染器重放时完全不再 `compute()`。命令构建本身已被缓存（仅 ~33ms），收益更彻底，但需要扩展命令模型并同时改命令构建与渲染两侧，覆盖更多样式字段，改动面与回归面明显更大。
3. **在 `ElementNode` 上按 paintVersion 缓存 ComputedStyle**：跨阶段共享 computed-style 缓存，收益最大，但必须定义并维护完整失效协议（元素样式 / class / 属性、文档级样式表 / 变量、结构变更都要正确失效），与现有 `layoutVersion` / `paintVersion` 体系深度耦合，首轮风险高。

## 最终选择

采用方案 1。新增私有辅助 `resolveStyle(ElementNode element, Map<ElementNode, ComputedStyle> styleMemo)`：

- 命中备忘表直接返回；
- 伪元素回退到原始 `UiStyleResolver.compute(element)`（保留 origin/runtime 级联语义），结果同样写入备忘；
- 普通元素先递归解析父级样式（同一备忘表），再调 `UiStyleResolver.computeWithParentStyle(element, parentStyle)`，写入备忘。

每次 `render(...)` 开头新建一个 `IdentityHashMap<ElementNode, ComputedStyle>`，按参数串入所有会（间接）调用 `compute` 的 `render*` / 表格 / `resolveCommandCornerRadii` 辅助方法，替换其中全部 8 处 `UiStyleResolver.compute(...)` 调用。

## 选择原因

- 渲染器只用 1 参 `compute(element)`（不传 `activeStates`，交互伪类不参与），单趟内 DOM 与样式不变，因此按元素实例备忘是**确定性、语义等价**的。
- 对非伪元素，`computeWithParentStyle(element, compute(parent))` 与 `compute(element)` 逐字段等价（同一 `findMatchingRules(element)`，最终都进同一私有 `compute(element, parentStyle, matchingRules)`）。把祖先链也纳入备忘后，每个元素每趟只做一次单层级联，**消除了递归到根的整条祖先链重算**——这正是根因所在。
- 改动单文件、零接口变更、零新增失效协议，最契合「最小改动先落地验证」的节奏；方案 2/3 留作后续增量。
- 折叠表格边框路径收益尤其明显：原先每个 `td/th` 边框都要重算 `table` 样式并遍历所有行逐个 `compute`（近似 O(行×单元格) 乃至 O(行²×列)），共享备忘后按行 / 表实例去重。

## 影响范围

- 仅修改 `src/main/java/club/heiqi/uilib/ui/paint/DocumentPaintRenderer.java`：新增 `resolveStyle` 辅助方法与 `java.util.Map` / `java.util.IdentityHashMap` 导入，并把 `styleMemo` 串入 `renderTextBatch` / `renderCommand` / `renderTextCommand` / `renderBackgroundImage` / `renderTextShadow` / `pushEffectState` / `renderStatelessEffect` / `renderBorder` / `renderOutline` / `renderBoxShadow` / `resolveBorderWidths` / `applyCollapsedTableBorderOverride` / `isCollapsedTableCell` / `isLastTableRow` / `findLastVisibleRowInSection` / `findLastVisibleRowInTable` / `isVisibleTableRow` / `isTableRowGroup` / `resolveCommandCornerRadii`。
- `isLastTableColumn` / `resolveTableAncestor` 不含 `compute`，未改动。
- 行为不变，仅新增「单趟绘制」作用域的缓存层；不改任何 DOM / CSS / 绘制语义。

## 后续注意事项

- 备忘是「单趟绘制」作用域：每次 `render()` 新建、单趟内不失效，成立前提是同一趟绘制内 DOM 与样式不变。若未来在一趟 `render` 内修改 DOM / 样式，或引入依赖 `activeStates`（交互伪类状态）的绘制路径，必须重新评估按元素备忘的安全性。
- 诊断计数 `replayStyleComputes` 统计的是 1 参 `compute()` 入口；本修复主路径走 `computeWithParentStyle`（未计数），故修复后该计数会降到接近 0（仅伪元素回退计入），属预期。真正的收益基线看 `replay` 毫秒数与 fps，而非该计数。
- 诊断埋点（`UiStyleResolver.DIAG_COMPUTE_CALLS`、`文档绘制诊断` 日志）仅存在于 `perf/modern-config-paint-diagnostics` 分支，未并入 `4.0`。如需复测 compute 调用次数对比，把含本修复的 `4.0` 合并进该诊断分支再跑。
- 若后续要让命令重放完全不再 `compute()`，推进方案 2（命令构建期把样式量写进 `DocumentPaintCommand`）。
