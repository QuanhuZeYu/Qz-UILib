# HUD Demo 面板子控件溢出暴露布局缺陷

## 错误现象

- 右上角 `INTERACTIVE HUD` demo 面板中的输入框和按钮超出面板边框，视觉上像“跑出框”。

## 触发场景

- HUD demo 面板给容器设置了固定宽度和内边距。
- 内部输入框和按钮又声明 `width: 100%`。

## 根本原因

- 这不是刻意设计，而是旧版 HTML-like 宽度求解链路的实现缺陷。
- 旧实现里，子项 `width:100%` 会先按父内容宽度求得内容宽，再额外叠加子项自身 padding/border，导致 border box 被撑出父内容盒。
- 因此问题根因在布局引擎，而不是 HUD demo 结构本身。

## 修复方案

- 在 `DocumentLayoutEngine.resolveContentWidth(...)` 中收紧百分比宽度求解：`width:100%` 子项的最终内容宽不会超过父内容盒扣除自身 padding/border 后的可用宽度。
- 补充固定宽父容器下的 `width:100%` 回归测试，确保子项 border box 保持在父内容盒内。
- 当前无需为此单独引入 `box-sizing` 语义，现有 HTML-like 宽度契约已经可覆盖 HUD demo 与常见表单控件场景。

## 预防措施

- 后续新增通用表单控件或浮窗 demo 时，优先验证“固定宽度父容器 + 100% 宽子控件 + padding/border”组合，并同时覆盖带横向 margin 的边界场景。
- 若布局问题出现在多个宿主场景，应优先认定为 HTML-like 通用能力问题，而不是单独归因到 HUD。
