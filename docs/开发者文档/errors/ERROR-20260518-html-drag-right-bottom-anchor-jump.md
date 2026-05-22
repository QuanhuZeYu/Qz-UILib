# HTML-like 浮窗首次拖拽时因 right/bottom 锚点跳位

## 错误现象

- 使用 `DocumentDraggableSupport` 的浮窗在第一次拖拽时没有紧跟鼠标，而是瞬间跳到其他位置。
- 该现象在 `fixed + right/top` 或 `fixed + right/bottom` 的浮窗上最明显，看起来像坐标系突然切换到左上角基线。

## 触发场景

- 浮窗通过 `DocumentDraggableSupport.attach(...)` 挂接 HTML-like 拖拽能力。
- 目标元素初始定位不是 `left/top`，而是使用像素级 `right`、`bottom` 中的一个或两个进行锚定。
- 用户首次按下把手并开始拖动浮窗。

## 根本原因

- 这次问题不在于 Minecraft GUI 缩放坐标和 UILib 原生像素坐标混用；`HtmlLikeDocumentWidget` 的拖拽事件链路始终使用原生像素坐标并在 widget 内做 document 局部化。
- 真正的问题在 `DocumentDraggableSupport`：初始化时只读取 `left/top` 作为累计位移基线，没有识别 `right/bottom` 锚点。
- 当元素原本靠 `right/bottom` 定位时，首次拖拽会把样式强行改写成 `left/top`，并从错误基线开始累加，导致浮窗瞬间跳位。
- 同时，`right/bottom` 锚点的位移方向与 `left/top` 相反；鼠标向右/向下拖动时，对应的 `right/bottom` 数值应减小而不是增大。

## 修复方案

- `DocumentDraggableSupport` 初始化时会优先保留已有的像素级锚点语义：若没有 `left/top` 但存在 `right/bottom`，则继续沿用 `right/bottom` 作为拖拽基线。
- 拖拽过程中按锚点类型分别更新位移方向：
  - `left/top` 锚点继续按正向累加。
  - `right/bottom` 锚点按反向累加，并保持对应锚点，不再强制改写成 `left/top`。
- 新增回归测试覆盖 `right/bottom` 锚定的 `fixed` 浮窗首次拖拽场景，锁住“不跳位且保持锚点语义”的行为。

## 预防措施

- 后续新增通用拖拽、缩放或吸附能力时，不要默认所有定位元素都以 `left/top` 为唯一基线；必须同时审视 `right/bottom`、`absolute/fixed` 等锚点组合。
- 诊断“浮窗首次拖拽跳位”时，先区分是坐标系统混用，还是锚点语义在状态迁移时丢失，避免把布局锚点问题误判为输入坐标问题。
- 新增拖拽回归测试时，至少同时覆盖 `left/top` 与 `right/bottom` 两类初始定位。
