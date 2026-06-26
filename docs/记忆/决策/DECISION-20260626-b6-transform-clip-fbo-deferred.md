# 决策：B6 transform+clip 叠加坐标错位 — FBO 方案评估与推迟

- 日期：2026-06-26
- 决策者：用户拍板（推迟实现，单独开专栏后续解决）
- 评估者：oracle（两轮新开 session）+ explorer（FBO 基础设施侦察）
- 状态：**推迟**（研究信息已保存，待真实需求触发后单独开专栏）

## 问题描述

节点同时设置非恒等 `transform` 与 `clipChildren/scrollable` 时，
CLIP 框使用未经 transform 变换的 `nodeAbsX/Y`，
与子树实际经 PUSH_TRANSFORM 变换后的视觉位置错位
（rotate 下 scissor 矩形裁剪失效）。

精确位置：
- `ScenePaintEngine.java:130` 注释已显式登记约束
- `:133-139` PUSH_TRANSFORM 用 nodeAbsX/Y
- `:161-167` CLIP_PUSH 同样用未变换的 nodeAbsX/Y + clipWidth/clipHeight

## 关键事实（oracle + explorer 已验证）

### 1. scissor 物理限制

`ClipStack.java:86-87`：clip 走 `glScissor`，
scissor 在窗口坐标系取轴对齐矩形，**完全无视 GL 模型/投影矩阵**。
即 `pushTransform` 的 `glRotatef` 只影响顶点变换，scissor 框不动。
rotate 下 scissor 裁剪在 GL 层面**物理无解**。

### 2. 圆角 clip 在 transform 下本就正确

`ClipStack.rebuildRoundedClipMask` 用 **stencil**，
stencil mask 按当前 GL 矩阵变换后的顶点写。
所以**圆角裁剪 + transform 实际上不冲突**（mask 和内容同矩阵旋转）。
真正冲突的只有**矩形 scissor + rotate** 这一个交集格。

### 3. transform 矩阵已完整化

`Transform.java:30-42`：translate/rotate/scale/origin 七分量俱全。
`UiRenderContext.java:885-895`：GL 矩阵 origin 三明治完整落地。
NORTH_STAR 偏离登记 2026-06-20「只 translate」**已与代码失同步**，
需更新。

### 4. 当前零生产触发

- `setClipChildren(true)` 的生产调用全是滚动视口/输入框裁剪
- `node.setTransform(...)` 在 `ui.scene` 全包**零生产调用**
- `ScenePaintEngine.java:130` 已在数据层主动禁止非恒等 transform + clipChildren

## 方案空间评估

| 方案 | rotate 正确？ | 代价 | 评价 |
|------|-------------|------|------|
| **A 禁用**（当前） | 回避 | 0 | 显式禁止该组合 |
| **B 反向变换 scissor 框** | ❌ 退化包围盒漏裁 | 中 | scissor 只接受轴对齐矩形，假修 |
| **C 调整 paint 顺序** | ❌ scissor 不读矩阵 | 中 | 物理无效，废案 |
| **D stencil 替代 scissor** | ✅ mask 可旋转 | 大 | 全屏 stencil clear + 重画，渲染层重构 |
| **F FBO 离屏+贴图**（用户方案） | ✅ FBO 内 scissor 正确 | 中高 | replayer 改造 + 全屏离屏 pass |

## FBO 方案详解（用户提出，oracle 评估架构成立）

### 原理

transform + clip 叠加时，先在未变换坐标系内把子树渲染到 FBO
（此时 clip 用 scissor 正确），然后把 FBO 作为贴图在 transform 矩阵下绘制。
这是浏览器合成器对 `transform` + `overflow:clip` 的标准处理。

### 基础设施现成度（explorer 侦察确认）

- `PaintContextCompositor` 已有 FBO 层池 + 嵌套 push/pop 栈，跨帧复用
- `UiRenderTarget` 封装单 FBO（颜色纹理+depth/stencil），窗口 resize 自动重建
- opacity 路径已在用这套 FBO（`PUSH_OPACITY` → `pushPaintContext` → borrow FBO + applyClipSnapshot）
- `drawHostImage`（`UiRenderContext.java:600-644`）是逐行同构模板：
  borrowIsolatedLayer → begin → 重设投影/模型矩阵 + applyClipSnapshot → 渲染 → end → compositeToCurrentFramebuffer 回贴
- **「进 FBO 后用 scissor 重放裁剪」机制已存在并在生产路径跑**

### 实施需要的改动（3 件）

1. **replayer 改造**（最大风险）：
   当前 `ScenePaintReplayer.replay` 是纯线性单趟遍历。
   FBO 方案需遇到「需 FBO 的 PUSH_TRANSFORM」时：
   - 先扫描找到配对的 POP_TRANSFORM（前看或深度计数）
   - 把 [push, pop] 之间的命令先 borrow FBO → begin →
     在恒等矩阵+正确 scissor 下 replay 这一段 → end
   - 再把 FBO 当贴图在 transform 矩阵下回贴
   - **把"单趟线性回放"变成"带前瞻的分段递归回放"**
   - 这是 scene 渲染层迄今最大的结构变更

2. **矩阵下回贴**：
   现有 `compositeToCurrentFramebuffer` 是轴对齐 UV 回贴，无矩阵。
   需在 transform 矩阵已压栈状态下调用，让 GL 矩阵旋转四边形。
   需核对 `compositeToCurrentFramebuffer` 内部 `glPushAttrib`/`glDisable(SCISSOR)`
   不破坏外层矩阵。

3. **嵌套处理**：
   transform⊃opacity⊃clip 三层叠加时双层嵌套离屏。
   层池支持嵌套 borrow，但每多一层 +1 次全屏 glClear + 全屏回贴 fillrate。

### 性能代价

- scissor 路径：几乎零开销
- FBO 路径：每次 transform+clip 节点每帧 +1 次 FBO 绑定 + glClear 全屏 +
  子树重定向渲染 + 全屏回贴 fillrate
- FBO 对象跨帧复用（不每帧 new），但每帧 glClear 全屏 + 回贴全屏 fillrate 仍是开销
- `UiRenderTarget` 是全屏尺寸，即便子树很小也分配/清整屏纹理
  （后续可优化按子树包围盒裁小 FBO，现 API 不支持需扩展）
- 稀疏场景可忽略，密集嵌套需警惕

### 触发条件（精确门控）

不是所有 transform+clip 都需 FBO：
- **纯 translate + clip**：scissor 框同步平移即可，无需 FBO（当前未做平移，是小修）
- **纯 scale + clip**：scissor 框按 scale 同步缩放，可不用 FBO（复杂度中等）
- **rotate≠0 + 矩形 clip**：**只有这一格**必须 FBO
- **圆角 clip + 任意 transform**：走 stencil，**本就正确，永不需要 FBO**

触发条件应是 `transform.rotateDegrees ≈ 0 && node.isClipWindow() && cornerRadius == 0`

### 实施批次草案（待需求触发后启动）

- **批 1**：触发门控 + 全屏 FBO + PUSH_TRANSFORM 段在 replayer 里识别配对边界。
  先不碰 opacity 嵌套。
- **批 2**：矩阵下回贴（复用 compositeToCurrentFramebuffer，
  在 transform 矩阵已压栈状态下调用，让 GL 旋转四边形）。
- **批 3**：transform⊃opacity 嵌套离屏正确性 + 嵌套层栈测试。
- **批 4（可选，仅性能暴露后）**：按旋转 AABB 裁小 FBO，
  扩展 UiRenderTarget 支持子区域。
- 每批后必须有独立 reviewer 审核 + 真机 rotate 动画帧率实测。

## NORTH_STAR 偏离登记处理（待用户确认后执行）

### 1. 偏离登记 2026-06-20（transform 通路）需更新

该登记说「transform 通路只落地 translate，rotation/scale 未实现，translate 量化到整数」。
但代码现实：Transform 类持完整 7 分量，GL 矩阵 origin 三明治全部落地，
translateX/Y 是 float 不量化。**该登记的 what 已与代码矛盾**。
应更新为「✅ 已还清（rotate/scale/origin 矩阵通路已落地）」。

### 2. 补登记 ScenePaintEngine 主动禁止 rotate+clip 的偏离

`ScenePaintEngine.java:130-131` 已在数据层禁止了非恒等 transform + clipChildren，
但偏离登记里没有专门为此立项。按宪章「隐性偏离不可接受」，
这个主动禁止本身应被显式登记。

### 3. B6 偏离条目

更新后偏离登记核心 what 改为：
「rotate + 矩形 clip 裁剪不正确（scissor 物理限制），
已显式禁止该组合，待真实需求触发 FBO 方案（见本决策文档）」

## 决策

**推迟 B6 裁剪逻辑修复。** 用户拍板：先推迟实现，后面单独开专栏解决。

### 推迟理由

1. 零生产触发（数据层已主动禁止该组合）
2. replayer 改造是 scene 渲染层迄今最大结构变更
3. FBO 路径每帧全屏离屏 pass 性能代价
4. 为假想需求提前上重型机制违反 YAGNI

### 回填触发条件

当出现第一个真实「rotate + 矩形 clip」需求时
（如旋转卡片、转场动画带 overflow clip），
按本文档「实施批次草案」启动 FBO 方案，
经全新 oracle session 评估信条五铁律在
「transform+FBO clip」下的成立性与每帧 stencil 重建的性能代价。

## 配套文档

- `docs/开发者文档/reviews/REVIEW-20260625-scene-oracle-architecture-audit.md`
  （B6 原始审核条目 + 源码核实）
- `docs/开发者文档/reviews/REVIEW-20260625-scene-geometry-clip-bugbed.md`
  （B1+B3 详版，B6 遗留项 2 同源）
