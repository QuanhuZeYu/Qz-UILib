# 决策：图层概念不上契约层 + 契约层 GL 术语清零

- 日期：2026-06-26
- 决策者：用户拍板（采纳 oracle 裁决）
- 评估者：oracle（两个新开 session）
- 状态：**已落地**（改名 + 契约测试合回 4.0）

## 问题缘起

用户在 B6（transform+clip 叠加）讨论中提出：
"如果想让框架能适应非 OpenGL 绘制——任何可能的绘制语言——
更加通用化，是否需要显式抽象图层概念？"

核心疑虑：当前 `pushPaintContext`/`pushTransform` 是否是
"GL 语义的数据层化"，而非真正的平台无关？

## oracle 裁决：不需要显式抽象图层

### 关键洞察：图层是答案，不是问题

跨后端契约的正确粒度是"语义意图"（这段绘制要整体施加
opacity/transform/clip），而不是"实现手段"（为此开一个离屏缓冲）。

"是否开图层"是各后端的自由：
- group opacity：所有后端都需离屏（数学必然）
- transform：所有后端都**不需**离屏（矩阵/CTM 够了）
- clip：都不需离屏

把这个决策提到数据层，等于把一种后端的实现细节焊死成
所有后端的义务。

### 显式 `PUSH_LAYER` 的三个损害

1. **焊死手段**：强制 Skia"必须开 layer"剥夺其自主优化权；
   SVG 根本没有显式离屏对象概念
2. **数据层背锅**：paint engine 要产 `PUSH_LAYER` 就得回答
   "何时需要图层"，而这个知识是后端相关的，数据层答不出来
3. **粒度无解**：GL FBO 全屏 / Skia layer 任意 bounds /
   SVG 无离屏对象——抽象到什么粒度都无法收敛

### 图层比 transform 更靠近 GL

用户直觉"图层是更通用的高层抽象"方向反了：
- `PUSH_TRANSFORM` 描述用户可见视觉意图（CSS/SVG 都有）
- `PUSH_LAYER` 描述渲染管线实现手段（GL FBO / Skia saveLayer）
  用户不可见、设计师不关心

把图层提到数据层 = 让数据层认识比 transform 更靠近 GL 的概念，
**违反 I6 精神**。

## 当前架构已做对

- 9 条 `UiRenderBackend` 方法里 8 条已是干净视觉意图原语
- 图层概念正确待在渲染层实现内部（`PaintContextCompositor`）
- 数据层（`PaintCommandType` 9 枚举）对图层无感知——I6 理想态

## 唯一瑕疵与修复

`pushPaintContext`/`popPaintContext` 的**语义**（group opacity
离屏合成）是跨后端通用真命题，但**命名**是 GL 离屏术语。

### 修复：纯改名（已落地）

- `pushPaintContext` → `pushGroupOpacity`
- `popPaintContext` → `popGroupOpacity`
- 14 文件改名（接口/实现/replayer/Javadoc/测试 mock）
- `PaintCommandType` 枚举名 `PUSH_OPACITY`/`POP_OPACITY` 保持不动
  （数据层 Display List 命令名本就干净）
- commit `1463028b` + merge `930b0b5a`

### 跨后端契约测试锚点（新增）

- `RecordingRenderBackend`：零 GL 副作用 mock 后端，
  记录每条调用的完整参数
- `SceneBackendContractTest`（7 场景）：端到端验证
  scene 核心 → replayer → UiRenderBackend 接口链路
  纯靠接口方法工作
- 场景 7 核心断言：所有调用方法名在接口白名单内
- 这是"换后端零改动"的测试锚点

## 与宪章对齐

- 信条六/I6 实质规则**不需要改**
- 显式图层抽象是**新方向**，且与 DECISION-20260620 已登记的
  "transform 走矩阵通路、不开新机制"回填方向抵触
- 在 `PaintCommandType` 加 `PUSH_LAYER` = 移动契约线本身
  （信条六重大变更），oracle **反对**

## 意图原语模式（未来扩展准则）

每出现一种"需要对一组绘制整体施加某效果"的新需求，
就加一个**描述该效果意图**的边界命令原语，
而非加一个**描述实现手段**的 `PUSH_LAYER`。

例：未来加滤镜 → `pushFilter(blurRadius)`（意图），
各后端自行决定是否离屏兑现。**YAGNI——现在不加**。

## 配套文档

- `docs/决策/DECISION-20260626-b6-transform-clip-fbo-deferred.md`
  （B6 推迟决策，含 FBO 方案评估）
- oracle 架构审核历史产出（审查报告已清除）
