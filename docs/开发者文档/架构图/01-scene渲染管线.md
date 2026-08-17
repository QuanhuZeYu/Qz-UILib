# L1 scene 渲染管线：一帧真实数据流

一句话定位：从宿主 `AbstractSceneHostWidget.render` 到 GL 的一帧完整流水线，顺序与源码一致。

> 素材基线：源码实时状态（2026-08-13）。涉及类名可在 `src/main/java` 核对。

## 一帧数据流（主树）

```mermaid
flowchart TD
  A["宿主 AbstractSceneHostWidget.render<br>w / h / absX / absY 入口"]
  B["inputSource.drainFrame<br>一次性消费输入帧，空帧返回 EMPTY 单例"]
  C["layoutEngine.layout 第一遍<br>主树布局"]
  D["runtime.route<br>SceneInputRouter hit-test / 派发 / interactionState signal"]
  E["runtime.flush<br>ReactiveScheduler: signal 到 effect 到 SceneNode 脏标记<br>双通道至不动点，零标脏"]
  F["动作采样 motion<br>SceneMotionDriver 直接写 paint / composite 属性"]
  G["layoutEngine.layout 第二遍<br>加 layoutOverlays 与 settleLayoutObservers，最多 3 pass"]
  H["runtime.reconcileHoverAfterScroll<br>滚动后 hover 重算"]
  I["paintEngine.paint<br>生成自包含不可变 PaintPlan（纯数据）"]
  J["replayer.replay<br>把 PaintPlan 翻译为 UiRenderBackend 调用"]
  K["UiRenderBackend 的 MC 实现 UiRenderContext<br>焊到 Tessellator 与 GL 调用"]

  A --> B
  B --> C
  C --> D
  D --> E
  E --> F
  F --> G
  G --> H
  H --> I
  I --> J
  J --> K
```

## overlay 栈与 host 边界

```mermaid
flowchart TD
  H["SceneOverlayHost 有序栈"]
  H --> D["bottom-first 绘制<br>每棵 overlay 子树独立 paint + replay"]
  H --> HIT["top-first 命中<br>SceneInputRouter 从顶层向下 hit-test"]
  B["host 边界"]
  B --> P["内部全程 logical px<br>layout / paint / replay / 裁剪 / 输入共享同一坐标事实"]
  B --> S["GUI Scale 只在 host 边界成对转换"]
  B --> R["replay 必须恢复其触碰的 GL 状态<br>不污染 MC 与其他 mod 的后续渲染"]
```

## 图注（真值锚点）

- 顺序不可调换：先 route（只写 signal，不标脏），后 flush（物化全部 signal 与 effect）；Motion 采样在 flush 之后。
- paint 阶段只读 signal 与树结构，绝不写 signal；`PaintPlan` 是数据层与渲染层之间唯一的合同交付物，不持有上游可变状态引用。
- replay 永远在主线程执行，消费 `PaintPlan` 与 `UiRenderBackend`，不触碰 scene 上游可变状态；同一 plan 可延迟重放。
- overlay 与主树契约同构（per-tree 隔离），绘制顺序 bottom-first，命中顺序 top-first。
- host 边界转换：内部 logical px 闭环不允许混入 Minecraft GUI Scale；GUI Scale 只在 host 边界成对缩放。
