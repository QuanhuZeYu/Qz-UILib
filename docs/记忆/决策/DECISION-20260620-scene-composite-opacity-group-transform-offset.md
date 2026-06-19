# 决策：scene 合成级失效双通路——opacity 走 group 栈边界命令、transform 走 int offset

## 背景

Phase 3（分级失效全链路 + 合成级动画）要落地 NORTH_STAR 信条五铁律「60fps 合成级动画绝不触碰布局/绘制层」在 scene 新栈的实现。核心是让 `compositeDirty`（opacity/transform 变化）不再是「白标」——必须真正驱动上屏，且做到「纯 composite 帧零重排零 fragment 重建」。

关键约束（侦察坐实）：
- `PaintCommand` 全字段 final 不可变（双缓冲国策），不能逐命令塞可变 opacity/transform。
- `PaintPlan.addFragment` 把 fragment 内每条命令 `translatedBy(offset)` 后**摊平进全局命令流，fragment 边界在组装期被抹掉**——扁平流里子树边界已丢失。
- `ScenePaintReplayer` 类红线「禁止给 UiRenderContext 加任何方法」，且不得 import `Transform`/`SceneNode`（守 I6）。
- 渲染层 `UiRenderContext` 已有现成能力：`pushPaintContext(l,t,r,b,opacity)`/`popPaintContext()`（group opacity 栈，PaintContextCompositor 离屏层）+ `pushTransform/popTransform`（完整 GL 矩阵）。

## 候选方案

### opacity 通路
1. **方案甲（命令级 alpha）**：opacity 乘进每条命令的颜色 alpha 通道。
2. **方案乙（group opacity 栈）**：opacity 走 `pushPaintContext/popPaintContext` 离屏层 group 合成。

### transform 通路
1. **只 translate 走 int offset**：translate 累加进命令绝对坐标（复用 geometry offset 通路），rotation/scale 不实现。
2. **完整矩阵走 pushTransform**：调渲染层 GL 矩阵通路，支持 translate/rotate/scale。

## 最终选择

- **opacity = 方案乙（group opacity 栈）**：用户拍板 D1。
- **transform = 只 translate 走 int offset**：用户拍板 D2。
- **实现机制**：plan 命令流插入 `PUSH_OPACITY`/`POP_OPACITY` 边界命令，由 `ScenePaintEngine.paintNode` 递归骨架前后两句保证配对（PUSH 在递归子节点前、POP 在 for 循环后），`ScenePaintReplayer` 顺序转译为 `pushPaintContext`/`popPaintContext`。
- **opacity/transform 绝不存进 PaintFragment**，每帧由 paintNode 实时从 node 读取（比 oracle 原设计「fragment 携带 opacity 字段」更优——若存进 fragment，opacity 变化就要重建 fragment，反而破坏「纯 composite 帧零重建」铁律）。

## 选择原因

### 为什么 opacity 走 group 栈（方案乙）而非命令级 alpha（方案甲）
- **方案甲在嵌套半透明叠加处视觉不正确**：父 0.5 + 子 0.5 两层重叠区域，逐命令乘 alpha 会让重叠处变实（0.5×0.5=0.25 只在交集，非交集仍 0.5），与「整个子树作为一个半透明组」的语义不符。
- **方案乙嵌套相乘交给渲染层离屏层栈天然完成**：replayer 只传**该节点局部 opacity**（不自算 parent×child 累计），PaintContextCompositor 离屏层栈天然做正确 group 合成 → replayer 无状态、零相乘、守 I6。
- 因此**「嵌套 opacity 不支持相乘」的偏离消除，不再登记**——离屏层栈天然支持相乘。

### 为什么 transform 只 translate 走 int offset 而非完整矩阵
- **translate 复用现有 geometry int offset 通路零新机制**：translate 累加进命令绝对坐标（与 box.getX/Y 同处叠加），replayer 对 transform 完全无感知（坐标已编入命令），守 I6。
- **offset 复用语义对 rotation/scale 失效**：旋转/缩放需重核信条五铁律在矩阵变换下的成立性（矩阵变换下「不触碰绘制层」的保证需要 FBO 离屏渲染基建），本期避险，留未来 FBO 阶段。
- rotation/scale 走 `pushTransform` 是渲染层已有能力，但 Phase 3 故意不调它——`Transform` 类本身只有 `translateX/translateY` 两个字段（无 rotate/scale 入口），不存在「传入 rotate 被静默吞掉」的风险。

### 为什么不做 composite-only 回放缓存（3C）
- 要省的「fragment 重建成本」不存在——fragment 复用已天然满足零重建（paintNode 复用判据只看 `selfPaintDirty`，opacity/transform 走 compositeDirty 通路完全不进重建分支）。属伪需求，YAGNI 排除。

## 关键边界与未来触发条件（YAGNI 纪律）

- **transform 数据层整数量化**：offset 通路是整数像素，`paintNode` 用 `Math.round(translateX)`（非 `(int)` 截断，减少向零偏置使逐帧动画更平滑），但亚像素本身不支持。逐帧推进 translate 的动画在像素边界量化，平滑度受限于整数像素。
- **rotation/scale 回填触发条件**：出现真实旋转/缩放动画需求时启动，前置依赖渲染层 FBO 离屏渲染基建，须 oracle 重新评估信条五铁律在矩阵变换下的成立性。回填时 `Transform` 类扩字段 + `paintNode` 改走 `pushTransform` 通路。
- **opacity≈0 剪枝**：当前 opacity=0（完全透明）仍照常递归产出全部命令再交渲染层用 0 alpha 合成（功能正确但浪费）。若后续出现高频「隐藏动画」场景，可加 opacity≈0 整子树剪枝优化。当前登记为已知次优，不预做。

## 影响范围

- `PaintCommandType`：新增 `PUSH_OPACITY`/`POP_OPACITY` 两枚举。
- `PaintCommand`：新增 `pushOpacity`/`popOpacity` 工厂（复用 final 字段，保持不可变），`translatedBy` 对 PUSH/POP 防御性返回自身。
- `PaintPlan`：新增 `addPushOpacity`/`addPopOpacity`。
- `ScenePaintEngine.paintNode`：transform translate `Math.round` 累加进绝对坐标；opacity `< 1.0 - OPACITY_EPSILON`（1e-4f）且已布局（box != null）则包 PUSH/POP；末尾补 `clearCompositeDirty()`（3A 解耦后必须）。
- `ScenePaintReplayer.replayCommand`：PUSH_OPACITY→`pushPaintContext`（区域叠加 offset、传局部 opacity）/ POP_OPACITY→`popPaintContext()`；不破「禁止给 UiRenderContext 加方法」红线（所需 API 已存在）。
- `SceneNode`（3A 解耦）：新增 `descendantCompositeDirty` 路标 + `bubbleDescendantComposite()`，`markComposite` 删借道 `bubbleDescendantPaint` 改走独立路标，`clearPaintDirty` 移除清 composite，新增 `clearCompositeDirty`。
- 对应偏离登记：NORTH_STAR《偏离登记》2026-06-20「D2 transform 仅 translate + 整数量化」一条。
