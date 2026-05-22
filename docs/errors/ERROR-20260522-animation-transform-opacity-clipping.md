# 动画展示页 transform 与 opacity 混合导致内容裁切

## 错误现象

- `动画能力成功展示` 页的 Keyframe / animate() 区域在运行后出现大块斜向裁切，后续日志与“当前边界”区块被遮挡或部分不可见。
- 问题视觉上像普通布局高度不足，但截图中裁切边界带有 transform 角度，说明更接近动画绘制/离屏合成路径干扰。

## 触发场景

- `phasePulse` 同时动画 `opacity`、`translate`、`scale` 和 `rotate`。
- Transition 样例同时切换 `opacity` 与 transform 子属性。
- 这些元素位于可滚动诊断页内部，外层存在滚动视口与局部裁剪边界。

## 根本原因

- opacity 小于 `1.0` 会让元素进入 paint context / FBO group opacity 合成路径。
- transform 会修改元素子树绘制矩阵；当同一元素同时承担 opacity 合成与 transform 动画时，离屏层边界、当前矩阵和外层裁剪很容易互相放大，导致视觉内容被错误裁掉。
- 展示页原本是成功能力展示，不应该把多个高风险渲染路径混在同一个样例里，否则用户看到的是裁切缺陷而不是能力闭环。

## 修复方案

- 将 `phasePulse` 收口为纯 transform + background-color 样例，不再包含 `OPACITY` track。
- 将 Transition 样例收口为 width / transform / border-radius / box-shadow，不再同时 transition opacity。
- 新增独立 `opacityBreath` keyframe 条，单独展示 opacity-only 动画能力。
- 给 transition、keyframe、imperative transform 样例增加固定舞台与 `overflow:visible`，为旋转/缩放预留安全边界。

## 预防措施

- 成功展示页应按风险域拆分样例：transform、opacity/FBO、layout-affecting 属性不要默认混在同一个元素上展示。
- 如果必须验证 `opacity + transform` 组合，应作为专门的渲染回归探针，并先确认 `PaintContextCompositor` 对矩阵和裁剪状态的隔离策略。
- 对会旋转或缩放的示例元素，应使用显式 stage 容器预留视觉外扩空间，避免把 transform 视觉外扩误判为布局能力问题。
