# 字体样式文本绘制重载递归

## 错误现象

- 客户端在渲染 screen 时崩溃，崩溃类型为 `StackOverflowError: Rendering screen`。
- 崩溃堆栈在 `UiRenderContext.drawText(..., TextContentMode)` 与 `UiRenderContext.drawText(..., TextContentMode, UiFontWeight, UiFontStyle)` 两个重载之间反复循环。

## 触发场景

- 文本 paint command 携带 `UiFontWeight.NORMAL` 与 `UiFontStyle.NORMAL`。
- `DocumentPaintRenderer` 统一调用带字体样式的 `drawText` 重载。
- 带字体样式的重载为了复用普通文本路径，在普通字体样式时回调不带字体样式的重载。

## 根本原因

- 不带字体样式的 `drawText` 重载会补上默认 `NORMAL/NORMAL` 后调用带字体样式重载。
- 带字体样式的重载在 `NORMAL/NORMAL` 时又回调不带字体样式重载，形成双向委托。
- 字体样式能力补齐时没有为默认样式绘制入口增加直接回归测试，导致普通文本路径进入无限递归。

## 修复方案

- 将实际文本绘制逻辑收敛到 `drawTextResolved(...)`。
- 不带字体样式的重载直接调用 `drawTextResolved(..., NORMAL, NORMAL)`。
- 带字体样式的重载归一化空值后，普通样式沿用不带字体样式重载，非普通样式走 `drawTextResolved(...)`。
- 增加 `UiRenderContextTest.shouldDrawNormalFontStyleWithoutRecursiveOverload`，锁定普通字体样式不会在重载之间递归。

## 预防措施

- 新增带默认参数的重载时，必须明确单向委托关系，最终只能落到一个无回跳的实际执行方法。
- 同一组重载中如果存在“补默认值”和“默认值回退旧入口”两种逻辑，必须加回归测试覆盖默认值路径。
- 字体、文本、绘制入口的重载变更要优先验证 `NORMAL` / 默认路径，因为这是大多数页面文本都会命中的路径。
