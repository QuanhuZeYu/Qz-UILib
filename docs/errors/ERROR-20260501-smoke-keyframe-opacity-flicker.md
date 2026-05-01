# Smoke keyframe opacity 阶段屏闪

## 错误现象

- `HTML-like Smoke` 页首个 `Click target` 的自动 `smokePulse` keyframe 在从亮绿色过渡到紫色小圆角时，视觉上可能带动整个屏幕短暂闪烁。
- 闪烁发生在多段 keyframe 的 50% 到 100% 区间，容易误判为多段 stop 插值本身有问题。

## 触发场景

- `smokePulse` 同时动画 background-color、border-radius 与 opacity。
- 0% 到 50% 期间 opacity 保持 `1.0`，50% 到 100% 期间 opacity 从 `1.0` 降到 `0.45`。
- 当运行态 opacity 跌破 `0.999`，绘制阶段会把该元素动态切入 opacity paint context / FBO group opacity 合成路径。

## 根本原因

- 多段 keyframe Smoke 探针原本目标是验收 stop 列表插值，但它同时混入了 opacity 动画。
- opacity 动画会触发更重的 renderer FBO 合成路径，与颜色/圆角 stop 插值不是同一层风险。
- 因此亮绿色到紫色区间的屏闪更可能来自运行态 opacity 触发的 paint context 路径切换，而不是 0%/50%/100% stop 插值本身。

## 修复方案

- 将 `smokePulse` 自动 keyframe 收口为纯 background-color + border-radius 三段 stop 探针。
- 保留 `Click target` 点击后的 opacity transition 和页面底部独立 `Group opacity probe`，继续作为 opacity/group opacity 的独立验收入口。
- 测试中固定 `smokePulse` 不再注册 `OPACITY` 轨道，避免后续再次把多段 keyframe 验收与 FBO 路径混在一起。

## 预防措施

- Smoke 探针应按风险域拆分：多段 keyframe stop 验收只覆盖颜色/圆角；opacity/FBO 合成单独由 group opacity probe 或专门页面验收。
- 以后新增视觉探针时，不要在同一个自动动画里同时混入基础插值能力和重型 renderer 路径切换，否则用户反馈会难以定位。
