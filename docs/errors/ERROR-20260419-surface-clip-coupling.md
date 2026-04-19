# ERROR-20260419-surface-clip-coupling

## 错误现象
- 给文档 shell/card 接入圆角后，出现了正文、指标、背包网格或图标内容消失的问题。
- 在修复内容消失后，又出现圆角边框左侧线条看起来不完整、像被吞掉的问题。

## 触发场景
- 把圆角外观与内容裁剪都放进 `UiSurfaceStyle`，并让 `Widget` 自动把 surface 变成 visual clip。
- rounded border 继续以整数边界使用 `GL_LINE_LOOP` 描边。

## 根本原因
- `border-radius` 外观语义与 descendant clip 结构语义被错误耦合，导致祖先 surface 会误伤后代内容与 deferred pass。
- rounded border 画在整数边界上时，左/上边的描边中心会部分落在像素边界外，视觉上容易出现半裁切。

## 修复方案
- 移除 `UiSurfaceStyle.clipContent`，让 `UiSurfaceStyle` 退回纯外观值对象。
- 移除 `Widget` 的 surface-driven visual clip 入口，把后代裁剪收回到显式结构容器。
- `DivWidget` / `ScrollViewportWidget` 继续基于 overflow/viewport 盒提供结构性 clip。
- rounded border 改为在 `0.5` 像素中心描边，确保四条边以内缩一致方式落在边框盒内部。

## 预防措施
- 以后凡是涉及 Web-like 视觉模型时，先区分清楚“appearance”和“structure/overflow”两类职责，再决定抽象落点。
- 如果未来需要“圆角内容裁剪”，应新增显式结构性 rounded clip 容器，而不是回退到 surface 自动裁剪。
- OpenGL 线框描边需要优先检查像素中心对齐，避免把渲染瑕疵误判成裁剪逻辑问题。
