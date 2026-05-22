# Backdrop 旧快照过度复用

## 错误现象

- `Large Glass Lab` 中，上方大玻璃先触发主层 snapshot 捕获后，下面嵌套玻璃继续显示 `snapshot=reused`。
- 视觉上后续嵌套玻璃没有采样自己背后的新绘制色块，而是复用了更早场景的 UI 内容，导致采样错层。

## 触发场景

- 同一帧内存在多个 `backdrop-filter` 元素。
- 第一个 backdrop 捕获主 UI 层快照后，后续已经绘制了新的背景、文本或其它 glass 元素。
- 快照服务只按 read framebuffer、宽高和 frame id 复用，没有区分两次 backdrop 之间主层内容是否已经变化。

## 根本原因

- `UiMainLayerSnapshotService` 的复用 key 缺少 UI 主层内容版本。
- CSS-like backdrop 语义要求采样当前 paint order 下“已经绘制完成”的背后内容；只按帧复用会把第一张快照错误扩散到后面的 backdrop。

## 修复方案

- 在 `UiRenderContext` 中维护 UI 主层内容版本。
- surface、text、backdrop 成功绘制、paint context 合成、deferred pass 合成和 custom renderer 回调后推进内容版本。
- `UiMainLayerSnapshotService` 按 read framebuffer、尺寸、frame id 与内容版本共同判断是否可复用；内容版本变化时重新捕获快照。
- Glass Lab 诊断中的 `snapshot=captured/reused ... rev=...` 可用于观察当前采样版本。

## 预防措施

- 后续优化 snapshot 复用时，不能只按帧或 FBO 复用；必须保留 paint order 对 backdrop 采样语义的约束。
- 新增多 glass 场景时，应覆盖“backdrop 之间有绘制写入”的用例。
- 自定义渲染器如果绕过 `UiRenderContext` 的标准绘制封装直接写 GL，必须通过上下文通知内容已变化。
