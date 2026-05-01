# Opacity context 首次进入时 framebuffer 绑定泄漏

## 错误现象

- HTML-like 元素首次进入 opacity paint context / FBO group opacity 合成路径时，可能造成整屏短暂闪烁。
- 在 Smoke 页中，之前表现为 `Click target` 的自动 keyframe 或第一次点击只要触发 opacity 动画，就可能出现屏闪。

## 触发场景

- `UiRenderTarget` 第一次创建或 resize 离屏 FBO。
- 创建发生在已有 Minecraft 主 UI framebuffer 绑定的渲染流程中。
- 随后 `PaintContextCompositor` 读取当前 framebuffer 作为 parent framebuffer，并在 pop 时把离屏层合成回该目标。

## 根本原因

- `UiRenderTarget.allocateAttachments()` 在分配 color texture、depth/stencil renderbuffer 并检查 FBO 完整性后，固定调用 `glBindFramebuffer(GL_FRAMEBUFFER, 0)`。
- 如果调用前绑定的是宿主主 UI FBO 而不是默认 framebuffer，创建离屏层会把当前 framebuffer 绑定泄漏改成 0。
- `PaintContextCompositor.pushPaintContext(...)` 随后记录到错误的 parent framebuffer，pop 时也会把 opacity layer 合成到错误目标，引发整屏闪烁或短暂错帧。

## 修复方案

- `allocateAttachments()` 进入时保存当前 framebuffer、texture 2D 和 renderbuffer 绑定。
- 无论分配成功还是失败，都在 finally 中恢复这些绑定。
- 失败时仍抛出原 FBO 完整性错误，但不再污染调用方 GL 绑定状态。

## 预防措施

- 所有底层 FBO/texture/renderbuffer 分配函数都必须恢复进入前 GL 绑定状态，不能假设调用方绑定的是默认 framebuffer。
- 以后排查“首次进入某个 FBO 路径才闪”的问题时，优先检查资源初始化阶段是否泄漏 framebuffer/viewport/texture/renderbuffer 状态。
