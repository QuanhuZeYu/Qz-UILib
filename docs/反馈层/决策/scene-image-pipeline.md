# Scene 图片绘制管线

## 边界

- `SceneImageSource` 是 scene 核心唯一可见的图片源契约；核心不解释内容，也不依赖 Minecraft、ItemStack 或 GL。
- `HostImageSource` 实现该契约并留在 host/client 包；`PaintCommand.IMAGE` 固化 source 身份与目标矩形，延迟 replay 不反查节点。
- `UiRenderBackend.drawImage` 默认空实现保持旧 backend/API 兼容；Minecraft adapter 由 `UiRenderContext` 转交既有 `MinecraftHostImageRenderer`，`McScreenBridge` 显式注入默认宿主适配器。
- 非 HostImage fail-closed 信号的单图运行时或链接失败仍在 replayer 隔离；HostImage 状态恢复无法验证时抛出帧中止信号，不得继续污染后续命令。

## ItemStack 隔离与预算

- `HostImageSource.itemStack` 保持 LIVE：至少 500ms 可刷新一次；新增 `itemStackSnapshot` 在创建时复制，只有 source/session 纪元变化才重栅格化。
- 跨帧 `HostImageRenderSession` 按 source identity + 栅格尺寸维护 128 项 LRU；每帧最多发起 2 次、累计 2ms 的不可信 ItemStack 调用，等待项按持久 FIFO 公平补齐，失败冷却 5 秒。cache hit 只贴可信纹理，不执行 RenderItem 或完整状态查询。
- ItemStack 使用不超过 32×32 的独立小型 FBO；目标完全位于当前 clip 外时不排队、不占预算。TEXTURE/BUFFERED_IMAGE 不进入该预算和昂贵状态围栏。
- 完整状态围栏保存 server/client attrib、program、texture/client texture、VAO/VBO/EBO、read/draw FBO、renderbuffer、viewport/scissor、三矩阵栈与顶值，并验证 Tessellator idle/GL error；可选 API 按 context 能力调用。renderer 失败且恢复可信时占位/旧缓存并继续，恢复不可验证时 fail-closed。
- `UiRuntimeAdapters` 对任意宿主 renderer 统一施加幂等 `GuardedHostImageRenderer` 包装；包装器的 ItemStack 路径只调用 delegate `render`，不信任其 `renderGuarded` 覆盖。未经过真实 capture/restore/verify 的 ItemStack 默认合同 fail-closed，Minecraft delegate 不再自带第二重围栏；TEXTURE/BUFFERED_IMAGE 保持轻量异常隔离。

## 状态

平台图片管线、Picker scene 控件与 StructuredList 接入均已完成；Picker 产品投影见 `config-search-picker.md`。
