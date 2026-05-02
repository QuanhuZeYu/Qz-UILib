# ERROR-20260502-inventory-slot-opacity-workaround

## 错误现象
- 背包页 slot 半透明底板看起来直接混到游戏画面，视觉上像背包格子没有稳定 UI 底板。
- 初次处理时把 `DocumentInventorySlotGridControl` 默认槽位填充色改成不透明，掩盖了穿透问题。

## 触发场景
- `inventory_overview` 中背包格子使用 `DocumentCustomRenderer` 直接绘制 slot 背景与边框。
- 主 UI 离屏层最终再贴回屏幕，半透明区域如果没有正确保留 UI coverage alpha，会把游戏画面混进最终结果。

## 根本原因
- 处理方向错误：slot 底板属于普通 HTML-like 表面，应优先用 DOM 元素、flex 行列、标准 background/border 命令表达，而不是放进 CUSTOM 手绘。
- 底层表面绘制也存在风险：矩形表面使用 Minecraft `Gui.drawRect` 路径时，目标 alpha 可能被半透明源覆盖，导致主 UI FBO 的 coverage alpha 下降，最终 present 时露出底层游戏画面。

## 修复方案
- 恢复 slot 默认半透明底色，不再用不透明色规避。
- 将 `DocumentInventorySlotGridControl` 改为每行一个 flex row、每个 slot 一个普通 `div`，slot 背景和边框由标准 paint command 绘制。
- `CUSTOM` 只保留给 Minecraft 物品图标延迟回放。
- `UiRenderContext.fillRect`、圆角填充和圆角边框改用 source-over 独立 alpha 混合，避免半透明表面降低目标 UI coverage alpha。

## 预防措施
- 遇到半透明视觉穿透时，先排查绘制路径、FBO alpha 和合成语义，不要直接把颜色改成不透明。
- 普通 UI 表面必须优先用 DOM + 标准背景/边框表达；CUSTOM 只用于 HTML-like 标准命令无法表达的宿主绘制，例如 Minecraft 物品图标。
- 相关改动需要测试覆盖：slot 默认半透明颜色仍存在、slot 由 DOM 子元素构建、CUSTOM 只剩物品回放入口。
