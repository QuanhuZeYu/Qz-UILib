# Scene、Material 与 Motion 演进方案

> 状态：当前功能分支实施顺序。产品目标是改善 Config 视觉与交互，不重写整个 scene。

## 决策规则

只有已复现错误、当前切片确定触发的问题，或无需新抽象的小修进入实现。无真实触发的问题不增加 graph、Scope、frame transaction、RenderSnapshot、LayerTree、generation、回滚、重试或组合矩阵。

## 保留与新增

保留 retained scene、Signal/effect、`SceneRuntime`、layout、paint/replay、Config 三态和宿主入口。首批只新增：

- internal U0 projection/input composition；
- Breaking snapshot-only item icon seam；
- Config-scoped dark semantic theme、Setting Row 和页面布局；
- 基于 host frame timestamp 的最小 Motion；
- 由真实切片触发的局部 compositor/resource 修正。

详细合同见：

- `UI投影宿主语义.md`
- `物品视觉渲染接缝.md`
- `材质配置动效.md`

## 实施顺序

1. I0：snapshot copy、icon-only、square fit、typed outcome、resource lifetime characterization。
2. U0：两个 occurrence 的共享 state/隔离 scene，以及单 composition owner 的只读 claim。
3. M0/M1：真实 Config 静态 Material 页面，优先交付可见效果。
4. I1/I2：分离普通图片与 item coordinator，收敛 cache/target/guard ownership。
5. M2：Button/Toggle/Navigation/section 最小 Motion。
6. I3/U1：迁移 Qz-Miner item/HUD consumer 与 screen/overlay adapter。
7. 根据实际 public diff 落定 Breaking 版本为 `5.0.0`；UILib FML 范围为 `[5.0.0,5.1.0)`，Miner 编译坐标为 `5.0.0:dev`、运行依赖为 `[5.0.0,6.0.0)`，既有 wire 版本不变。

## 停止条件

- 一个切片需要同时引入 graph、driver、snapshot 和 theme 栈时停止并寻找更小实现。
- item seam 开始承载 Slot、inventory、tooltip、input 或业务 intent 时停止。
- content 出现具体 host 类型分支，或核心 input 出现 HUD priority/owner 时停止。
- consumer 需要调用 flush/layout/paint/replay，或控件直接接收 Future/network/storage 时停止。
