# 决策：HTML-like 文本绘制阶段保守裁剪

## 背景

HTML-like 文档当前使用全局 `layoutVersion` / `paintVersion` 缓存。任意文本变动都会导致整棵文档重排；即使没有文本变动，静态长文本仍会在每帧生成并回放大量 `TEXT` 命令，最终让字体后端逐字符收集 glyph。

本轮目标是框架层低心智负担优化，不能把主要责任留给页面作者手动节流、截断或改用专用组件。

## 候选方案

1. 直接截断长文本：实现简单，但会改变文本语义与可访问内容，不可接受。
2. 跨帧文本布局缓存：收益大，但需要 TextNode 身份、版本、样式关键字段、可用宽度和 font epoch 组合键，风险较高。
3. 绘制阶段按 clip 裁剪不可见文本：不改变布局和文本语义，改动面集中，能优先解决滚动容器外不可见文本和超长单行提交问题。

## 最终选择

采用方案 3：在 `DocumentPaintEngine` 生成文本绘制命令时传入当前 active clip chain，并由 `DocumentTextPaintClipper` 做保守裁剪。

## 选择原因

- 不修改 DOM 文本内容和布局结果，只减少不可见或不可必要绘制的 `TEXT` 命令。
- 对多行长文本滚动容器，先按 clip 交集跳过完全不可见的 `TextRun`。
- 对横向超长单行，只有在存在 clip、文本足够长且有 `TextMeasureService` 时才测量并生成可见片段，避免普通 paint-only 动画帧额外触发文本测量。
- transform active 时暂时禁用裁剪，避免坐标变换下误删可见文本。
- text-shadow 和 text-decoration 使用保守膨胀边距，降低误裁风险。

## 影响范围

- `HtmlLikeDocumentWidget` 现在调用带 `TextMeasureService` 的 paint command 构建入口，以支持横向可见片段计算。
- 旧的 `DocumentPaintEngine.buildPaintCommands(...)` 入口仍保留；未传入文本测量服务时只做不依赖测量的保守可见性裁剪。
- `/qzuilib test` 的 frame/render 统计文本改为按帧数节流，避免页面静止时每帧打脏普通 DOM 文本。

## 后续注意事项

- 该方案不是跨帧布局缓存，不能解决文本内容真实变动导致的全局 layoutVersion 失效问题。
- 后续可继续做 TextNode 级换行缓存或 VirtualTextBlock / LogView，但它们应是进一步优化和便利组件，不应成为长文本性能正确性的唯一手段。
