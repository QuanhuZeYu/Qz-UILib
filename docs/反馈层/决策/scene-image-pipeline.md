# Scene 图片绘制管线

## 边界

- `SceneImageSource` 是 scene 核心唯一可见的图片源契约；核心不解释内容，也不依赖 Minecraft、ItemStack 或 GL。
- `HostImageSource` 实现该契约并留在 host/client 包；`PaintCommand.IMAGE` 固化 source 身份与目标矩形，延迟 replay 不反查节点。
- `UiRenderBackend.drawImage` 默认空实现保持旧 backend/API 兼容；Minecraft adapter 由 `UiRenderContext` 转交既有 `MinecraftHostImageRenderer`，`McScreenBridge` 显式注入默认宿主适配器。
- 非 HostImage fail-closed 信号的单图运行时或链接失败仍在 replayer 隔离；HostImage 状态恢复无法验证时抛出帧中止信号，不得继续污染后续命令。

## Item icon 隔离与预算

- `HostImageSource.itemIcon(ItemStack)` 在 factory 创建时复制完整 ItemStack；source 创建后不再读取调用方 mutable stack。LIVE 与 `itemStackSnapshot` 双栈均不属于当前合同。
- 跨帧 `HostImageRenderSession` 按 source identity + 栅格尺寸维护 128 项 LRU；每帧最多发起 2 次、累计 2ms 的不可信 ItemStack 调用，等待项按持久 FIFO 公平补齐。`UNAVAILABLE` 可短暂 cooldown；`HOST_STATE_LOST` 必须中止当前帧并在下一帧重新探测，不得缓存成 placeholder cooldown。cache hit 只贴可信纹理，不执行 RenderItem 或完整状态查询；回贴仅建立覆盖 quad/Tessellator 实际触碰状态的窄围栏。
- ItemStack 使用不超过 32×32 的独立小型 FBO；目标完全位于当前 clip 外时不排队、不占预算。TEXTURE/BUFFERED_IMAGE 不进入该预算和昂贵状态围栏。
- 完整状态围栏由 item coordinator 在 FBO begin 到 end 的整个事务外层统一拥有，保存 server/client attrib、program、texture/client texture、VAO/VBO/EBO、read/draw FBO、renderbuffer、viewport/scissor、三矩阵栈与顶值，并验证 Tessellator idle/GL error；合法 attrib depth `0` 仍尝试 push/pop。recognized unsupported legacy 围栏能力返回 `UNAVAILABLE` 且不调用 renderer；未知 probe error 或恢复不可验证返回 `HOST_STATE_LOST`。普通图片使用独立轻路径，自定义 plain renderer 必须遵守受信任窄委托状态纪律。
- 围栏入口 error 预检先于线程局部首错记录；单次 run 内按 capture/delegate/restore/verify 切换阶段，关键 GL 操作后以稳定 operation 名检查并锁存首个错误，后续不得覆盖，finally 清理。首错被消费后仍强制 `recovered=false`；能力 probe 的预期错误继续自行排空隔离。
- 围栏禁止同线程重入，且必须在 Tessellator/GL 入口查询前拒绝；非 `LinkageError` 的 fatal `Error` 只在 best-effort restore/cleanup 后按原 identity 冒泡，恢复异常作为 suppressed，不进入 typed frame-abort 路径。
- raster、离屏层或动态 bitmap texture 删除失败时保留唯一 owner 并在下一帧/下一次 close 重试；待清理资源不得回到复用池，item cleanup 未成功前不得继续创建新 raster。
- 不可恢复 outcome 只抛专用帧中止信号。Display List 回放器先按 LIFO 尽力关闭已进入的 clip/opacity/transform/transform-layer；全部清理成功才原样传播信号，任一清理失败则转普通异常。唯一 screen 帧边界只消费专用信号并保留宿主 finally，普通 RuntimeException/LinkageError 继续冒泡。
- 失败冷却帧没有真实栅格 outcome，不重复记录 `missing-outcome`；真实栅格尝试仍记录一次详细 warning，预算与五秒限频不变。

## 状态

平台图片管线、Picker scene 控件与 StructuredList 接入均已完成；Picker 产品投影见 `config-search-picker.md`。
