# ERROR-20260428-smoke-absolute-probe-clipped

## 错误现象

- `HTML-like Smoke` 页新增的 absolute containing block 可视探针在 JVM 测试中通过，但游戏内截图看不到金色 `ABS card anchor` 标签。
- 该探针原本应作为内部 static wrapper 的子节点，仍贴在外层紫色 positioned 卡片右上角，用于肉眼确认 nearest positioned ancestor 语义。

## 触发场景

- 在 Smoke 页左侧紫色卡片内，把 nested absolute 元素挂到一个深色 static wrapper 下。
- 深色 static wrapper 同时设置了 `overflow-x: hidden` 与 `overflow-y: hidden`。

## 根本原因

- absolute 元素布局坐标已经相对最近 positioned ancestor 计算，但绘制仍在 DOM 父子树中递归发生。
- static wrapper 的 overflow 裁剪会裁掉位于 wrapper 边界外的 absolute 子元素。
- 原 JVM 测试只断言了绘制命令坐标，没有断言 wrapper 不会对探针产生 clip，因此漏掉了游戏内不可见问题。

## 修复方案

- 移除 static wrapper 探针容器的 overflow hidden，让 absolute 子元素能越出 wrapper 显示。
- 将标签文案缩短为 `ABS OK` 并保持高对比金色背景，提升游戏内可读性。
- Smoke 页测试增加 wrapper 边界不产生 clip 的断言。

## 预防措施

- 以后添加“可观测”UI 探针时，测试不能只看布局/绘制坐标，还要覆盖影响实际可见性的 clip、opacity、stacking 与颜色对比。
- 对“absolute 子元素挂在 static wrapper 下但锚定外层 positioned ancestor”的场景，若子元素要越出 wrapper，可视探针容器不能设置 overflow hidden。
