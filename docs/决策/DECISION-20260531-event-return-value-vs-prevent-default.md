# 决策：事件返回值与 preventDefault 语义分离

## 背景

- HTML-like 事件系统历史上沿用了“handler 返回 `true` 表示已消费”的项目内约定。
- 随着链接激活、raw button 键盘默认 click 等默认行为逐步补齐，单纯依赖返回值已经不足以表达“停止传播”和“取消默认行为”这两类不同语义。
- 浏览器语义修复中已出现回归：target capture 返回 `true` 后错误跳过 target handler；raw button key handler 返回 `true` 时默认 keyboard click 被误取消。

## 候选方案

1. 保持现状：`return true` 同时表示停止传播和取消默认行为。
2. 继续保留 `return true` 只表示停止传播，取消默认行为统一依赖 `preventDefault()`。
3. 废弃返回值，只允许通过 `stopPropagation()` / `preventDefault()` 控制。

## 最终选择

- 选择方案 2。

## 选择原因

- 与现有公开接口和大量控件实现最兼容：`return true` 仍可作为“消费并停止传播”的简写，不需要全库推翻重写。
- 与浏览器语义更接近：默认行为是否执行由 `preventDefault()` 决定，而不是被“是否继续冒泡”隐式绑死。
- 便于在现有三阶段事件模型中逐步补齐更多默认行为，不会把传播与默认行为继续耦合在一起。

## 影响范围

- target 阶段必须区分 `stopPropagation()` 与 `stopImmediatePropagation()`：capture handler 停止传播后，target 自身的非 capture handler 仍应执行。
- raw button 键盘默认 click、链接默认激活等默认行为只检查 `isDefaultPrevented()`，不再因 `return true` / `stopPropagation()` 被隐式取消。
- 自己实现键盘激活的控件，如 `DocumentButtonControl`，若要阻止宿主默认行为，必须显式调用 `preventDefault()`。
- `hover` / `active` 这类状态通知不等同于会传播 DOM 事件；为保持 `mouseenter` / `mouseleave` 与 `:active` 祖先状态语义，handler 返回值不截断后续祖先通知。

## 后续注意事项

- 后续补 `dblclick` / `contextmenu` capture、表单默认行为、drag/drop 默认语义时，优先复用这条规则。
- 新增运行时自检项时，至少覆盖“返回 true 但默认行为仍执行”和“preventDefault 后默认行为取消”两条路径。
- 新增状态通知类 handler 时，先判断它是否真是可传播 DOM 事件；若只是运行态状态同步，不应机械套用 `return true` 停止传播。
