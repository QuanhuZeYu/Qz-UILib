# 表单控件空值高度与 textarea 光标坐标混用

## 错误现象

- 单行 `DocumentTextInputControl` 在没有内容且没有 placeholder 时高度明显变矮，输入任意文本后又恢复正常高度。
- `DocumentTextAreaControl` 聚焦后内部状态与自动测试可判定为有光标，但游戏内看不到 textarea 光标。

## 触发场景

- `input` 未显式设置高度，内部文本为空，依赖 auto 高度布局。
- `textarea` 所在 `HtmlLikeDocumentWidget` 不在屏幕原点，例如位于页面卡片或面板内部，widget 有非零 `getAbsoluteX()` / `getAbsoluteY()`。

## 根本原因

- `input` 被实现为 `display:flex` 容器，内部 span 在空文本时按普通空内容布局为 0 高度；但浏览器原生文本输入框在 `height:auto` 下应至少保留一行编辑器内在高度。
- `textarea` 的 selection/caret 自定义渲染器通过 `getDocumentBounds()` 获取的是文档局部坐标，却直接传给 `UiRenderContext.fillRect(...)`；绘制管线传入 `DocumentCustomRenderer.render(...)` 的参数已经叠加 widget 屏幕偏移，测试长期使用 `(0, 0)`
  布局导致坐标混用未暴露。

## 修复方案

- 在 `DocumentLayoutEngine` 为原生 `input` 的 `height:auto` 内容高度增加 line-height 下限，保持显式高度仍由作者样式控制。
- 在 `DocumentTextAreaControl` 中分离文档局部内容坐标与屏幕绘制偏移：命中测试、光标定位继续使用文档坐标，selection/caret 绘制前转换到屏幕坐标。
- 增加回归测试覆盖空值 input 与有值 input 等高，以及 widget 非零偏移下 textarea 光标与文本行末对齐。

## 预防措施

- 表单控件问题优先按原生控件语义判断，不要把空文本行盒行为直接套到 `input` / `textarea` 上。
- 自定义渲染器必须明确区分文档局部坐标和屏幕坐标；只要调用 `fillRect` / `drawText`，输入坐标就应是绘制管线当前要求的屏幕坐标。
- 涉及 `HtmlLikeDocumentWidget` 绘制坐标的测试必须至少覆盖一个非零 widget 偏移场景，避免 `(0, 0)` 掩盖坐标空间错误。
