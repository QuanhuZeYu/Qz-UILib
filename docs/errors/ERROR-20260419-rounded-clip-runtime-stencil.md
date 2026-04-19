# ERROR-20260419-rounded-clip-runtime-stencil

## 错误现象
- 为 `DocumentPageWidget` 接入 rounded structural clip 后，游戏内真实运行时出现“文档壳与滚动条仍正常显示，但正文子树整体不可见”的回归。
- `compileJava` 与 recording-based 单元测试均通过，导致问题一度被误判为几何建模错误而非真实渲染状态问题。

## 触发场景
- 在 `DocumentPageWidget` 生产链路中启用 `RoundedScrollViewportWidget`。
- 子树渲染发生在 `Widget.render()` 的 `drawSelf()` 之后、`pushClip()` 之后。
- 只要 live child pass 进入 rounded clip 分支，真实运行时内容就可能整体不可见。

## 根本原因
- 之前的测试只验证 clip snapshot 记录结果，没有覆盖真实 OpenGL `scissor + stencil` 状态链。
- rounded structural clip 一旦进入 live child pass，会触发 `UiRenderContext.applyClipSnapshot()` 的 rounded 分支，开启 `GL_STENCIL_TEST`。
- 当前 MC/GL 运行时下，这条 stencil/FBO 状态链与文档页主内容渲染不兼容，导致 pushClip 之后的主内容整段被拒绝，而 `drawSelf()` 阶段已绘制完成的壳和滚动条不受影响。

## 修复方案
- 将 `DocumentPageWidget` 从探索中的 `RoundedScrollViewportWidget` 生产接入回退到稳定的 `ScrollViewportWidget`。
- 保留 `Widget` 与 `RoundedScrollViewportWidget` 的探索性扩展点，但不再把它们接到文档页生产路径上。
- 更新 AI 记忆文档与测试，明确当前稳定边界仍是矩形 viewport clip。

## 预防措施
- 对任何涉及 `UiRenderContext.applyClipSnapshot()`、`GL_SCISSOR_TEST`、`GL_STENCIL_TEST`、FBO 切换的改动，不能只依赖 recording context 单测，必须保留一次真实游戏内渲染验收。
- 只有在确认 live rounded stencil 在当前运行时稳定可用后，才能把 rounded structural clip 重新接回 `DocumentPageWidget` 生产链路。
- 当“几何修复完全不改变运行时现象”时，优先怀疑真实 GL 状态链，而不是继续在 snapshot 形状上迭代。
