# 决策：通用被动 HUD

## 背景

旧 document HUD 已随旧栈删除，但多个客户端模组仍需要不依赖业务类型的状态 HUD。

## 最终选择

- 公共 API 只暴露不可变规格、快照、语义 tone 与幂等注册句柄。
- 单一 Forge `Post(ALL)` bridge 驱动无输入 `SceneHudHost`，内部复用 scene 的 layout→paint→PaintPlan→replay。
- 同锚点按 `stackOrder`、再按 `registrationOrder` 稳定堆叠；安全区只接收显式占位，不猜测 F3 或第三方绘制。
- provider 在 render 主线程读取并逐项隔离异常；注册表先封板再遍历。
- registration 跨断线/世界卸载保留；此时只释放 session scene，重连自动重建。只有显式关闭 registration 或 registry shutdown 才注销。
- 首版只做被动展示，交互、拖拽与任意坐标回调不进入 API。
- HUD 的目标坐标契约遵循 I13：layout/paint/Display List/replay/font/texture/scissor/input 统一使用 UILib logical px；Minecraft backend 当前按 `1 logical px = 1 framebuffer/display pixel`，不接收 MC GUI Scale、`ScaledResolution` 或 `GuiScreen` scaled 尺寸。未来独立 `UiScale` 只由 host 在边界单次正逆变换，默认 `1.0` 且与 MC scale 无关。
- 上述坐标契约已在通用 HUD 路径落地：bridge 统一使用 framebuffer 尺寸，host 独立 scale 默认 `1.0` 并支持 `1/1.25/1.5/1.75/2`，在边界恰好一次映射 layout、clip、paint 与字体；不读取 Minecraft GUI scale。
- HUD 外框按最长内容行加水平 padding 收缩，progress 槽至少跟随自身 label 宽；只有超过 safeInsets、margin 与可选 `maxWidth` 后才 clamp/clip。动态文本经既有 signal `LAYOUT` 失效重新测量，左右锚点与同锚点 stack 均消费实际宽高。
- Compact/Normal 默认字号分别为 12/14 logical px，行盒与行高分别为 14/16、16/19；18px 仅作为未来语义强调 token 上限，公共 API 不开放任意文本样式。`HudSpec.minWidth/maxWidth` 提供领域无关宽度约束，默认最小宽只保障基本可读性。

## 选择原因

该边界让调用方保持几行接入，同时守住 Display List 契约、keyed 列表协调和客户端平台隔离，
也避免为被动 HUD 引入完整 Widget/Input 生命周期。

## 影响范围

稳定 API 位于 `club.heiqi.uilib.ui.hud.api`，Minecraft 实现位于 `client.hud`。
断线或世界卸载只释放 session scene，不要求调用方重新注册。
