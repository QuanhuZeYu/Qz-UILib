# Transform 内文本延迟批处理绕过父元素矩阵

## 错误现象

- `PAINT-005` transform 样例中蓝色元素背景、边框已经旋转，但内部文字仍按未旋转屏幕坐标绘制。
- 视觉上表现为父元素 transform 生效，文本或文本类子内容没有跟随父元素一起旋转，不符合浏览器中 transform 作用于元素及其后代渲染结果的语义。

## 触发场景

- 在 `/qzuilib test` 进入 Paint 二级页。
- 点击 `PAINT-005` 的 `执行自动测试`。
- 观察带有 `transform=translate(...) scale(...) rotate(...)` 的样例元素。

## 根本原因

- `DocumentPaintEngine` 已正确生成 `TRANSFORM_START` / `TRANSFORM_END`，并把背景、边框、文本命令包在 transform 栈内。
- `DocumentPaintRenderer` 会把连续 `TEXT` 命令送入字体延迟批处理。
- 延迟字体批处理收集的是最终屏幕坐标，并在 flush 时使用内部 UI 投影，不再依赖当前 OpenGL modelview 矩阵。
- 因此位于 transform 栈内的文本命令被批处理后绕过父元素矩阵，背景等普通绘制路径旋转，文本却不旋转。

## 修复方案

- 在 `DocumentPaintRenderer.RenderReplayState` 中记录当前 transform 栈深度。
- `TEXT` 命令仍保持可渲染判断，但只有不处于 transform 栈内时才允许进入延迟文本批处理。
- transform 栈内文本改为立即通过 `UiRenderContext.drawText` 回放，让字体 flush 在当前矩阵下执行，从而继承父元素 transform。
- 增加 `DocumentPaintRendererTest.shouldRenderTransformedTextWithoutDeferredBatching`，断言 transform 内文本不打开延迟批处理，transform 外文本仍可批处理。

## 预防措施

- 任何跨命令、跨时机的渲染批处理都必须明确记录或继承当前视觉上下文，包括 transform、clip、paint context、opacity 等。
- 对浏览器语义中的“父元素 effect 作用于完整子树”类能力，测试不能只看背景/边框，还要覆盖文本、文本阴影和普通后代内容。
- 优化渲染性能时，不能把局部坐标提前固化为屏幕坐标，除非批次本身能携带并回放等效矩阵。
