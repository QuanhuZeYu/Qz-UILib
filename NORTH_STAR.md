# Qz-UILib 设计导向标

## 中心思想

> 数据层以尽可能小的范围计算变化，渲染层以尽可能小的代价把变化刷上屏；两者通过平台无关、不可变的绘制计划协作。

本文件只保留跨模块、出错后果明确的长期原则。局部控件、布局和测试契约以源码、包级 Javadoc、测试及专题文档为准。

## 设计方向

### 状态驱动

- UI 是 state 的投影。正常变化通过 signal/state 进入，不靠外部命令式同步节点。
- 细粒度 effect、keyed 列表协调和分级失效用于缩小重算范围，但正确性优先于形式完整。
- 输入 handler 写 state；焦点和 pointer capture 等交互真值由路由器集中裁决，再按需暴露为 signal。
- content 不认识具体宿主；screen 与 game overlay 只是同一 content 的不同 projection/host adapter。多次投放可共享业务 state，但 live scene、focus、capture、hover、cursor 和 animation 必须逐 occurrence 隔离。
- 业务异步只经 state publication 与 semantic intent 进入 UI；content/consumer 不调用 layout、paint、replay 或底层 frame 阶段。

### 契约分层

- 数据、组件与 paint 不直接调用 GL；render/backend 不读取 signal、组件或可变 scene 节点来补齐绘制事实。
- paint 产出自包含、不可变的绘制计划，replay 只消费该计划并调用平台 backend。
- 平台原始输入只经 `PlatformInputSource` 等适配边界进入 scene core。core 不以 LWJGL、Minecraft 或 Forge 类型表达业务分支。
- 同一个 native input source 只由一个 composition owner 采集；projection 按最终 visual order 做只读 claim，只有胜者 dispatch，native cursor 也只由该 owner 提交。

### 单一坐标事实

- UILib 自有 layout、paint、Display List、replay、font、texture、scissor 和 input 统一使用 logical px。
- Minecraft backend 当前按 `1 logical px = 1 framebuffer/display pixel` 工作。Minecraft GUI Scale、`ScaledResolution` 和 `GuiScreen` scaled 尺寸不得进入内部闭环。
- 未来如引入独立 `UiScale`，只由 host 在边界做一次成对正逆变换，不跟随 Minecraft scale。
- 指针 raw 坐标用于平台/跨树边界，handler 默认消费框架按当前节点计算的 local 坐标，不自行混合坐标系。

### 宿主安全

- replay/backend 必须恢复其修改的 GL program、texture、blend、depth、scissor、matrix、buffer 及其他相关状态。
- capability 不可用时 fail-closed 或窄降级；不能因探测某项 legacy 状态而破坏仍可恢复的 server texture 等独立状态。
- 宿主关闭、重载或异常路径也必须释放 UILib 持有的资源和输入所有权。

### 增量性能

- 变化应从最低必要层开始：layout、paint、geometry、composite 按影响选择。
- 缓存的价值是跳过未变化工作；缓存必须有明确失效来源，不能以陈旧画面换取命中率。
- 动画优先使用不触发布局的属性。性能不足先测量重算起点和实际热点，不为假设中的负载增加框架。

## 数据流

```text
平台输入 -> 标准事件 -> 路由/handler -> state/signal
state/signal -> effect -> scene/layout -> immutable paint plan -> replay/backend -> screen
```

两条链在 state 处汇合。平台类型止于适配边界，GL 调用止于 replay/backend。

## 变更原则

- 修改平台隔离、坐标主权、绘制契约、GL 恢复、公共 API、持久数据或兼容承诺前，说明实际后果并取得用户确认。
- 性能理想不是机械阻断清单；局部取舍以可复现问题、测试或测量结果判断。
- 已知限制记录在下方或对应技术债；问题解决后直接移除，不维护形式化偏离状态机。

## 已知限制

- transform 与 clip 叠加时，FBO 离屏图层当前每帧重栅格化子树。它保证裁剪正确，但尚未跨帧复用纹理；在真实性能问题出现前不预先扩张实现。
- `SceneHitTester` 尚不感知非恒等 transform，rotate/scale 节点可能出现视觉与命中错位。当前无生产触发，真实交互需求出现时再引入逆变换命中。
