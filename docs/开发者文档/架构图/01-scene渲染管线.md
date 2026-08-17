# L1 scene 渲染管线：一帧真实数据流

一句话定位：从宿主 `AbstractSceneHostWidget.render` 到 GL 的一帧完整流水线，顺序与源码一致。

> 素材基线：源码实时状态（2026-08-18）。涉及类名可在 `src/main/java` 核对。
> 帧级重构规划（显式 `SceneFramePipeline` 状态机）见 [10-帧管线时序与调度.md](10-帧管线时序与调度.md)。

## 一帧数据流（主树）

```mermaid
flowchart TD
  A["宿主 AbstractSceneHostWidget.render<br>w / h / absX / absY 入口"]
  B["inputSource.drainFrame<br>一次性消费输入帧，空帧返回 EMPTY 单例"]
  C["layoutEngine.layout 第一遍 + layoutOverlays<br>route 前布局，hit-test 用最新几何"]
  D["runtime.route<br>SceneInputRouter hit-test / 派发 / 只写 signal"]
  E["runtime.flush<br>ReactiveScheduler: signal 到 effect 到 SceneNode 脏标记<br>双通道至不动点，零标脏"]
  F["__sampleMotion<br>SceneMotionDriver 直接写 paint / composite 属性<br>completion 时内部再 flush 一次"]
  G["layoutEngine.layout 第二遍 + layoutOverlays<br>flush 后新挂载树成型"]
  H["settleLayoutObservers<br>bridgeLayoutEpoch → flush → 探脏 → relayout<br>最多 3 pass，超限保留下一帧"]
  I["reconcileHoverAfterScroll +<br>dismissOverlaysWithInvisibleAnchor<br>滚动 hover 重算；只读几何请求关闭锚点不可见 overlay"]
  J["paintEngine.paint<br>生成自包含不可变 PaintPlan（纯数据）"]
  K["replayer.replay<br>把 PaintPlan 翻译为 UiRenderBackend 调用<br>主树 + overlay bottom-first"]
  L["UiRenderBackend 的 MC 实现 UiRenderContext<br>焊到 Tessellator 与 GL 调用"]

  A --> B --> C --> D --> E --> F --> G --> H --> I --> J --> K --> L
```

## overlay 栈与 host 边界

```mermaid
flowchart TD
  H["SceneOverlayHost 有序栈"]
  H --> D["bottom-first 绘制<br>每棵 overlay 子树独立 paint + replay"]
  H --> HIT["top-first 命中<br>SceneInputRouter 从顶层向下 hit-test"]
  H --> LAY["per-root 独立布局引擎<br>anchor 解析两遍 layout：先量高后定锚"]
  B["host 边界"]
  B --> P["内部全程 logical px<br>layout / paint / replay / 裁剪 / 输入共享同一坐标事实"]
  B --> S["GUI Scale 只在 host 边界成对转换"]
  B --> R["replay 必须恢复其触碰的 GL 状态<br>不污染 MC 与其他 mod 的后续渲染"]
```

## 图注（真值锚点）

- 顺序不可调换：先 route（只写 signal，不标脏），后 flush（物化全部 signal 与 effect）；Motion 采样在 flush 之后，其 completion 路径会内部再 flush 一次。
- settle 循环是「signal 层与 layout 层」之间的跨层反馈环：observer 订阅 `layoutDoneSignal` 读 LayoutBox 写节点 → 打脏 → 需要再 layout → 再 bridge epoch → 再 flush；`MAX_LAYOUT_OBSERVER_SETTLE_PASSES = 3`，超限由脏标记存活跨帧延续（当前实现，重构方案见 10 号文档）。
- paint 阶段只读 signal 与树结构，绝不写 signal；`PaintPlan` 是数据层与渲染层之间唯一的合同交付物，不持有上游可变状态引用。
- replay 永远在主线程执行，消费 `PaintPlan` 与 `UiRenderBackend`，不触碰 scene 上游可变状态；同一 plan 可延迟重放。
- overlay 与主树契约同构（per-tree 隔离），绘制顺序 bottom-first，命中顺序 top-first。
- host 边界转换：内部 logical px 闭环不允许混入 Minecraft GUI Scale；GUI Scale 只在 host 边界成对缩放。
