# 决策：通用被动 HUD

## 背景

旧 document HUD 已随旧栈删除，但多个客户端模组仍需要不依赖业务类型的状态 HUD。

## 最终选择

- 公共 API 只暴露不可变规格、快照、语义 tone 与幂等注册句柄。
- 单一 Forge `Post(ALL)` bridge 驱动无输入 `SceneHudHost`，内部复用 scene 的 layout→paint→PaintPlan→replay。
- 同锚点按 `stackOrder`、再按 `registrationOrder` 稳定堆叠；安全区只接收显式占位，不猜测 F3 或第三方绘制。
- provider 在 render 主线程读取并逐项隔离异常；注册表先封板再遍历。
- 首版只做被动展示，交互、拖拽与任意坐标回调不进入 API。

## 选择原因

该边界让调用方保持几行接入，同时守住 Display List 契约、keyed 列表协调和客户端平台隔离，
也避免为被动 HUD 引入完整 Widget/Input 生命周期。

## 影响范围

稳定 API 位于 `club.heiqi.uilib.ui.hud.api`，Minecraft 实现位于 `client.hud`。
断线或世界卸载会释放注册资源，调用方须在新世界重建。
