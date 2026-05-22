# ERROR-20260513-font-decoration-gl-state-leak

## 错误现象

- 启用自定义字体系统后，部分场景在文本后继续绘制带透明像素的贴图元素时，出现透明区域发白、白块或颜色异常。
- 该问题并非所有文本都会触发，通常更容易在带下划线、删除线等文本装饰后出现。

## 触发场景

- `MixinFontRenderer` 已接管原版 `drawString` 路径。
- 同一渲染链路内先执行字体绘制，再执行宿主贴图、GUI 贴图或其他依赖固定管线默认状态的纹理绘制。
- 文本包含 `§n`、`§m` 或等效 underline / strikethrough 样式时，问题更容易暴露。

## 根本原因

- 字体系统只给字形批次 `FontBatchRenderer.flush(...)` 建立了 OpenGL 状态保护边界，但没有把 `TextDecorationRenderer.flush()` 纳入同一边界。
- `TextDecorationRenderer.flush()` 直接修改 `GL_TEXTURE_2D`、当前颜色以及装饰线绘制所需的固定管线状态，结束后没有恢复，导致后续纹理绘制继承到脏状态。
- 旧版 `FontRenderStateGuard` 只保存“当前激活纹理单元”的 `GL_TEXTURE_BINDING_2D`，若调用前激活单元不是 `GL_TEXTURE0`，字体渲染期间对 `GL_TEXTURE0` 的绑定改动可能遗漏恢复，状态保护不完整。

## 修复方案

- 在 `DefaultFontRendererAdapter` 的一次完整文本 flush 尾声外层增加统一状态保护，确保字形批次与装饰线批次处于同一恢复边界内。
- 为 `TextDecorationRenderer` 增加独立状态保护与稳定 2D 文本渲染状态准备，避免其依赖前一个渲染阶段留下的 blend / alpha / texture 状态。
- 抽出共享的 `FontRenderStateSupport.prepareTextRenderState()`，统一收口文本二维渲染所需状态。
- 扩展 `FontRenderStateGuard`：同时保护 `MODELVIEW / PROJECTION / TEXTURE` 矩阵栈，并分别保存 `GL_TEXTURE0` 与原激活纹理单元的 2D 绑定，避免多纹理状态漏恢复。
- 新增纯 JVM 回归测试，锁定“字形批次和装饰线批次必须处于同一状态保护边界”这一调用契约。

## 预防措施

- 文本系统要按“整次 drawString 自包含”设计状态边界，不能只保护其中一个子批次。
- 任何自定义状态守卫都要明确区分“当前激活纹理单元状态”和“实际被修改的纹理单元状态”，不能默认二者相同。
- 涉及 immediate mode、fixed pipeline、shader、VAO/VBO 混用的渲染路径时，应优先建立完整的 enter/exit 边界，而不是依赖调用方事先处于某种默认 OpenGL 状态。
