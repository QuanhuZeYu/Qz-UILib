# 2026-05-01 keyframe 圆角 clamp 跳变

## 错误现象

- `HTML-like Smoke` 页首个 `Click target` pill 的自动 keyframe animation 可见，但从胶囊圆角变成小圆角时出现跳变。
- 当 pill 仍处于胶囊形状时，点击其他交互组件（例如磨玻璃卡片或输入框）会触发重绘，并让该 pill 突然跳到小圆角状态。
- 修复圆角 clamp 后又发现第二层现象：当 `Click target` 已被点击成蓝色小圆角或绿色胶囊时，点击其他组件会把它重新盖回 keyframe 末帧的紫色小圆角。

## 触发场景

- keyframes 中把 `BORDER_RADIUS` 的起始值写成 `999px` 表达胶囊形状。
- 实际绘制阶段会按元素尺寸把圆角 clamp 到 `min(width, height) / 2`。
- 动画运行期间其他元素交互导致 paint 刷新，更容易暴露该跳变。
- keyframe 使用 `fill-mode: both` 保留末帧，用户点击 `Click target` 后又通过 transition 改变同一个 background/radius 属性。

## 根本原因

- 声明式 keyframe 数值直接使用作者给出的原始值插值。
- `999px -> 12px` 的大部分插值结果仍会在绘制阶段被 clamp 成胶囊半径，直到接近末尾才跌破 clamp 上限，因此视觉上会突然从胶囊跳到小圆角。
- transition 路径此前使用的是布局盒解析后的基准圆角，keyframe 路径没有同样归一化。
- 同属性作者侧目标值改变后，旧 keyframe fill 仍保留在运行覆盖层里；transition 结束后下一次重绘又按 `transition > keyframe > computed style` 把旧 keyframe 末帧盖回去。

## 修复方案

- 在 `DocumentAnimationTimeline` 启动声明式 keyframe animation 时，对数值属性按布局约束归一化。
- `BORDER_RADIUS` 先 clamp 到当前布局盒 `min(width, height) / 2`，再创建 keyframe 插值。
- `OPACITY` clamp 到 `0..1`，`BACKDROP_BLUR_RADIUS` clamp 到 effect chain 支持的最大 blur 半径。
- keyframe 声明签名纳入布局盒宽高，尺寸变化时会重建归一化后的 keyframe 覆盖层。
- 当 computed style 目标值发生变化并由 transition 接管同一属性时，清除该属性的旧 keyframe 运行覆盖和 fill 覆盖，避免后续其他组件重绘时旧末帧再次盖回。

## 预防措施

- 后续新增 keyframe 数值属性时，不要直接用作者声明值插值；应先转成与 paint/layout 实际消费一致的 used value。
- 对所有会在绘制阶段 clamp 的属性补 keyframe 回归测试，避免插值路径和最终绘制路径使用不同数值空间。
- Smoke 探针中使用 `999px` 胶囊值时，必须确认动画层已经转成当前布局尺寸下的实际半径。
- 对 keyframe fill 与 transition 的交互补测试：作者侧同属性目标变化后，fill-mode 保留值不得继续越权覆盖后续 computed/transition 结果。
