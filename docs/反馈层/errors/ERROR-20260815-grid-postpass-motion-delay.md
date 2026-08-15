# 网格布局后置定位不得打 layout 级脏标记 + Motion 延时轨道帧外起点

> 日期：2026-08-15 · 关联提交：本阶段 SceneVirtualGrid/GridLayouts/SceneTooltip 基础设施提交

## 现象

为「创造物品栏式方块选择器」新增四项 scene 基础设施（网格布局、虚拟网格、Tooltip、ellipsis）时踩到两个会扩散成隐性回归的坑：

1. **网格布局后置步若打 layout 级脏标记，会与引擎 flex 定位逐帧振荡**。`GridLayouter` 不注册进 `SceneLayoutEngine`（本阶段只新增文件不改引擎），只能作为 `layoutDoneSignal` 驱动的定位后置步。若后置步改完 LayoutBox 后顺手 `markSelfLayout`，下一帧引擎会把网格位置盖回 flex 位置、后置步再改回网格位置，每帧互相覆盖、永不稳定。
2. **`SceneRuntime.__startMotion` 的 TimedTrack 在帧外创建时，延时起点在首次 `__sampleMotion` 时才钉定**。Tooltip 用 Motion 轨道做 hover 延时计时器，测试里「hover 后直接采样 600ms」断言不触发，实际是轨道在首次采样把起点钉成 600ms、再等 500ms 才完成。宿主逐帧采样时无害，但测试与「偶发采样」宿主会得到延长一帧的延时。

## 根因

1. `SceneLayoutEngine` 的主流程 flex 定位是唯一权威，后置步只能写 LayoutBox 与 geometry/paint 脏位；`markSelfLayout` 会点亮后代 layout 路标，引擎下帧必重跑容器 flex 定位（`positionChildren` 不读旧值、从头重算），与后置步形成两套互相覆盖的权威。`markGeometryDirty` 不触发布局重算，是唯一安全的失效级别。
2. `SceneMotionDriver.start` 在 `frameOpen == false` 时给 TimedTrack 的 `startNanos = UNSET_TIME`，`TimedTrack.sample` 首次采样才 `startNanos = nowNanos`——这是「帧外 retarget 等下一次 sample 才起计时」的既定语义（见 SceneMotionDriver 类注释），Tooltip 借用作延时计时器时必须接受该语义。

## 修复

1. `GridLayouter.positionChildren` 只走「几何闸门 + `markGeometryDirty`（尺寸变化补 `markSelfPaint`）」语义，与 `FlexLayouter` 步骤 C/D 逐位一致；`GridLayouts.attach` 的 effect 内不再出现任何 layout 级标脏。容器自身盒高仍以引擎为准（文档化：换行后真实高度低于引擎盒高时盒底留白，调用方钉定容器高度）。
2. Tooltip 延时测试统一在 hover 后先 `__sampleMotion(0)` 钉定起点再推进时间；生产语义不变（宿主逐帧采样，起点即下一帧）。

## 预防

- 任何「布局后置步 / layoutDoneSignal 驱动几何」设施，写回只能限定 geometry/paint 级；新增标脏级别前先回答「下一帧引擎是否会覆盖我写的位置」，两套权威即振荡。
- 用 `__startMotion` 做延时/计时时，先在测试里推进一帧采样再断言时间线；对非逐帧采样宿主，延时会比名义值多一段「到下一次采样」的等待，文档中须声明。

## 验证状态

- 静态与自动化：全量 build（2681 JUnit + checkstyle）全绿，新增 `GridLayouterTest`（9）、`SceneVirtualGridTest`（12）、`SceneTooltipTest`（9）、`TextEllipsizerTest`（14）。
- 运行态：未做真机运行态验证；`runClient*` 交 CI/用户。
