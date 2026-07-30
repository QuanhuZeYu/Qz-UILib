# 物品视觉渲染接缝规格

> 状态：Breaking major 实施规格。用户已选择清理旧兼容面，不维持 4.x LIVE/SNAPSHOT/Slot 双栈。

## 产品合同

UILib 只提供普通 UI 使用的静态 ItemStack icon：

- factory 创建时复制完整 ItemStack snapshot，包括 metadata/NBT；
- source 创建后不再读取调用方 mutable stack；
- 只调用 item/effect icon，不绘制 count/durability overlay；
- scene core 只认识 opaque `SceneImageSource`；
- host render owner lazy raster/cache，未就绪或可恢复失败显示 placeholder；
- cache hit 不调用 `RenderItem` 或完整状态查询，只建立 quad/Tessellator 实际触碰状态的窄围栏；
- host close/resource reload 清理 item cache 与普通 bitmap upload texture。

明确不包含 GuiContainer、Slot、inventory、carried、tooltip、input、worker/Future、全局资源图或跨 host GPU cache。

## 几何与缓存

- `destinationSide = min(targetWidth, targetHeight)`。
- `rasterSide = min(destinationSide, internalCap)`，cap 只限制 backing raster。
- raster 等比合成到 destination square，并在目标矩形中居中。
- 完全位于有效 clip 外的请求不排队。
- cache key 只使用 immutable source identity 与 `rasterSide`；reload 直接清空，不保留 epoch key。

## 执行与结果

一次 raster 由一个 internal coordinator 拥有：clip、budget、target、guard、renderer、restore/verify、publish/discard 和 cleanup。

| 结果 | 处理 |
|---|---|
| publishable | renderer 正常返回且支持范围内 host state 验证通过，允许写 cache |
| unavailable | 未调用、预算延后、unsupported 或恢复可信的 renderer failure，显示 placeholder |
| host-state-lost | 无法恢复/验证宿主状态，清理后中止当前 host frame |

- 不用像素回读证明视觉 no-op。
- recognized unsupported legacy 子能力返回 typed `UNAVAILABLE`，不调用不可信 renderer；未知 probe error 或恢复失败返回 `HOST_STATE_LOST`。
- 任意非 `LinkageError` 的 fatal `Error` 在 best-effort cleanup 后按原 identity 冒泡，不得转换为 typed outcome 或被 screen 帧中止边界消费；同线程围栏重入在任何 GL 查询前失败。
- profile 只覆盖 Tessellator/GL error、attrib/matrix、viewport/scissor、texture/program/buffer/FBO 等现有可验证状态，不承诺万能恢复。
- cache composite 不复用上述完整 item profile，只保存 server enable/blend/color/texture、client vertex array 与必要的 program/buffer binding，并验证 attrib depth、binding、Tessellator idle 和 GL error。

## Breaking 迁移

- 删除 LIVE alias、旧 snapshot overlay 语义和万能 `HostImageRenderer` public 合同。
- 普通 texture/bitmap 使用独立轻路径，不经过 item coordinator；自定义 plain renderer 是受信任窄委托，必须自行遵守 `HostImageRenderer` 的状态纪律。
- `UiRuntimeAdapters` 不再携带 inventory-slot renderer；`ui.inventory`、`ui.slot` 和 GuiContainer mixin 移出目标源码。
- Qz-Miner `BlockPickerVisualAdapter` 迁到新 icon factory；业务枚举、label、picker、codec 和 Config Draft 仍归 Miner。
- Breaking seam 的目标 UILib 版本为 `5.0.0`；Qz-Miner 编译坐标固定为 `5.0.0:dev`，运行依赖固定为 `[5.0.0,6.0.0)`。UILib FML 远端范围独立固定为 `[5.0.0,5.1.0)`，主信封 v2 与 Realtime v1 不变。

## 首片验收

- snapshot copy；icon-only；18x18、64x16、16x64、64x64、64x128 square fit。
- cache miss/placeholder、cache hit、resource clear 与 typed outcome。
- 普通 bitmap upload 在 host close 释放。
- 源码与测试不出现新的 GuiContainer、Slot、inventory 或 LIVE seam。
