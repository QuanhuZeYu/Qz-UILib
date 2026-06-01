# 决策：共享视觉遍历层统一 paint hit scroll 语义

## 背景

- 在浏览器语义修复推进到 `overflow/scroll` 与 `stacking context` 阶段后，`DocumentPaintEngine`、`DocumentHitTestEngine`、`DocumentScrollState` 都各自维护了一套近似但不完全一致的递归逻辑。
- 三处实现重复处理 `fixed/sticky` 偏移、scroll offset、overflow clip、stacking phase 与局部边界，但语义分叉已经开始影响浏览器契约一致性。
- 如果继续逐模块补丁式修复，会把错误行为固化在不同实现里，后续每修一个场景都要同时改三处以上代码。

## 候选方案

1. 继续分别修 `paint`、`hit-test`、`scroll`，只在出错位置补判断。
2. 把 `DocumentEffectChain` 继续扩成“既管单盒 effect，又管整棵树排序与递归”。
3. 新增独立共享视觉遍历层，只让 `DocumentEffectChain` 保持单盒 effect 解析职责。

## 最终选择

- 选择方案 3：新增 `DocumentVisualTraversal` 作为共享视觉遍历层。
- `DocumentVisualTraversal` 统一提供：
  - `fixed/sticky` 偏移解析
  - scroll offset 传播
  - 祖先 overflow clip 链传播
  - stacking phase 收集与局部递归截断
- `DocumentEffectChain` 保持“单盒 effect 解析”职责，不直接承载跨树遍历。

## 选择原因

- 这能把“浏览器语义模型”和“具体消费方实现”拆开，让 `paint`、`hit-test`、`scroll` 复用同一份视觉解释，而不是同步维护三套近似逻辑。
- `overflow clip` 与 `stacking context` 在浏览器语义里不是同一概念：
  - clip 负责裁剪像素输出与交互可达性
  - stacking context 负责 z-order 隔离
- 单独抽共享层后，可以明确表达：
  - `overflow:hidden/auto/scroll` 祖先提供 clip 链
  - clip 本身不自动等同于 CSS 规范意义上的 stacking context
  - `position:fixed` 脱离祖先 scroll/clip 链，以视口为基准

## 影响范围

- `DocumentHitTestEngine` 改为复用 `DocumentVisualTraversal`
- `DocumentScrollState` 的滚轮目标选择、滚动条命中与活动拖拽几何改为复用 `DocumentVisualTraversal`
- `DocumentPaintEngine` 改为复用 `DocumentVisualTraversal`，并按 clip 链差量切换输出 `CLIP_START/CLIP_END`
- 新增 `DocumentVisualTraversalTest` 作为共享层浏览器契约测试
- `DocumentPaintEngineTest` 中 `fixed` / `overflow clip` 相关预期调整为浏览器语义

## 后续注意事项

- 后续处理 `top-layer` 时，优先继续接入 `DocumentVisualTraversal`，不要重新在宿主层维护一套普通树/顶层树并行排序逻辑。
- `DocumentEffectChain.isStackingBoundary()` 现在保留工程遍历边界含义，不能直接当成规范语义上的 stacking context 使用；需要规范判定时应显式使用 `createsStackingContext()`。
- 如果后续引入更完整的浏览器绘制顺序分层，应继续扩展共享层，而不是回退到 `paint/hit/scroll` 各自收集 phase item 的方式。
