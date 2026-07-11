# Scene 图片绘制管线

## 边界

- `SceneImageSource` 是 scene 核心唯一可见的图片源契约；核心不解释内容，也不依赖 Minecraft、ItemStack 或 GL。
- `HostImageSource` 实现该契约并留在 host/client 包；`PaintCommand.IMAGE` 固化 source 身份与目标矩形，延迟 replay 不反查节点。
- `UiRenderBackend.drawImage` 默认空实现保持旧 backend/API 兼容；Minecraft adapter 由 `UiRenderContext` 转交既有 `MinecraftHostImageRenderer`，`McScreenBridge` 显式注入默认宿主适配器。
- 单张图片的运行时或链接失败在 replayer 隔离，不中断后续命令。

## 状态

平台图片管线已完成；Picker scene 控件与 StructuredList 接入未完成。本批不升版本。
