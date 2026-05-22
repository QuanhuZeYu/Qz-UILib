# Smoke 诊断文本被固定小高度裁切

## 错误现象

- `HTML-like Smoke` 页的 `Controls probe`、`Layout animation probe` 等诊断文案在游戏内出现下半部分被截断。
- 视觉上容易误判成“下面的组件把上面的文字盖住了”，尤其是在按钮行或卡片紧贴诊断行时更明显。

## 触发场景

- 诊断文本容器被作者侧写成固定 `height:14px`。
- 同时容器声明了 `overflow-y:hidden`。
- 实际运行时文本 UI 行高高于 14px 时，文本会被容器自身裁掉。

## 根本原因

- Smoke 页部分单行诊断文本沿用了过小的固定高度假设，没有跟随实际字体行高变化。
- `Controls probe` 和 `Layout animation probe` 外层 section 也使用固定高度，导致内部诊断行恢复真实行高后没有额外余量。
- 该问题属于作者侧 demo 样式约束错误，不是普通组件块混合链路或字体 alpha 混合本身出错。

## 修复方案

- 将 Smoke 页相关诊断文本容器的高度改为 `UiStyleLength.auto()`。
- 将承载这些诊断行的两个外层 section 同步改为 `UiStyleLength.auto()`，避免后续字体行高调整后再次压回裁切状态。
- 为 `HtmlLikeSmokeDocumentPageControllerTest` 增加回归测试，直接锁定这些诊断容器使用 `auto` 高度。

## 预防措施

- 诊断页、示例页中的文本行不要使用小于真实文本行高的固定高度魔数去“卡单行”。
- 若必须限制单行文本区域，优先显式对齐 `line-height` 与容器高度，或使用可验证的文本测量值，而不是硬编码历史像素值。
- 出现“文字像是被下面组件盖住”的实机现象时，先排查文本容器自身的 `height` 与 `overflow`，再判断是否是渲染链路混合问题。
