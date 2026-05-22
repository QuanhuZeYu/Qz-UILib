# HUD Demo 面板子控件溢出暴露布局缺陷

## 错误现象

- 右上角 `INTERACTIVE HUD` demo 面板中的输入框和按钮超出面板边框，视觉上像“跑出框”。

## 触发场景

- HUD demo 面板给容器设置了固定宽度和内边距。
- 内部输入框和按钮又声明 `width: 100%`。

## 根本原因

- 早期曾把该现象归因为 HTML-like 宽度求解缺陷，并一度把百分比宽度硬收紧为近似 border-box 行为。
- 重新对齐浏览器语义后，默认 `width:100%` 应按 content-box 解析；若元素自身还有 padding/border，border box 溢出父内容盒是浏览器默认盒模型下的预期结果。
- 因此 HUD demo 或业务卡片若要求“不溢出外框”，应显式选择 `box-sizing:border-box`，而不是依赖百分比宽度被引擎裁剪。

## 修复方案

- `DocumentLayoutEngine.resolveContentWidth(...)` 不再对百分比宽度做硬裁剪，恢复默认 content-box 语义。
- 新增最小 `box-sizing` 能力；需要把 padding/border 收进指定宽度时使用 `setBoxSizing(UiBoxSizing.BORDER_BOX)`。
- 回归测试同时覆盖默认 content-box 溢出与显式 border-box 不溢出，避免再次把浏览器默认语义误固化为错误契约。

## 预防措施

- 后续新增通用表单控件或浮窗 demo 时，优先验证“固定宽度父容器 + 100% 宽子控件 + padding/border”组合，并明确该场景是否需要 `box-sizing:border-box`。
- 不要为了保住某个 demo 的不溢出视觉而在布局引擎里重新裁剪百分比宽度。
