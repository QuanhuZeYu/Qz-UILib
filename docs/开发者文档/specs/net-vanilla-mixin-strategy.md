# 网络层 vanilla mixin 策略

第一版网络层默认使用 `VanillaMixinTransport`，early mixin 负责拦截 vanilla custom payload。

## early loader

网络 mixin 继续复用：

- `src/main/resources/mixins.qz_uilib.early.json`
- `club.heiqi.uilib.mixin.early.EarlyMixins`

`EarlyMixins` 按运行侧返回：

- client：`network.MixinNetHandlerPlayClient`
- common/server：`network.MixinNetHandlerPlayServer`

客户端 mixin 不会在 dedicated server 返回，避免服务端加载 `net.minecraft.client.*`。该过滤逻辑已有 JVM 测试覆盖；当前默认 `runServer` 环境会被 LWJGL3ify relauncher 中止，完整 dedicated smoke 需换用不带该 relauncher 的服务端配置。

## 注入点

| Mixin | 注入方法 | 行为 |
|---|---|---|
| `MixinNetHandlerPlayClient` | `NetHandlerPlayClient.<init>` TAIL | 调 `VanillaMixinTransport.onClientHandshakeReady(...)` |
| `MixinNetHandlerPlayClient` | `handleCustomPayload` HEAD cancellable | channel 名以 `qz:` 开头时交给 Qz 网络层并 `cancel` |
| `MixinNetHandlerPlayClient` | `onDisconnect` HEAD | 清理 pending fetch 与分片状态 |
| `MixinNetHandlerPlayServer` | `NetHandlerPlayServer.<init>` TAIL | 玩家 NetHandler 就绪后发送协议能力握手 |
| `MixinNetHandlerPlayServer` | `processVanilla250Packet` HEAD cancellable | channel 名以 `qz:` 开头时交给 Qz 网络层并 `cancel` |
| `MixinNetHandlerPlayServer` | `onDisconnect` HEAD | 服务端玩家离开信号 |

非 `qz:` channel 完全放行，避免影响其它模组和 vanilla `MC|*` channel。

## 出站路径

- C2S：优先使用 `NetHandlerPlayClient.<init>` 注入时传入的 early `NetworkManager`，缓存为空时再反射拿客户端 `Minecraft.getMinecraft().getNetHandler().getNetworkManager()`，发送 `C17PacketCustomPayload`。
- S2C：从 `EntityPlayerMP.playerNetServerHandler.netManager` 发送 `S3FPacketCustomPayload`。

C2S 物理上限固定走 32766 bytes 兼容分片。S2C 默认按 GTNH/Hodgepodge 256 MiB 物理能力理解；普通逻辑消息仍受 16 MiB 默认上限保护。

## Forge 回退

`club.heiqi.uilib.net.transport.forge.ForgeTransport` 通过 `NetworkRegistry.INSTANCE.newChannel(...)` 提供兼容路径，仅用于调试与排障。公共 API 不 import `cpw.mods.fml.common.network`。启动期可通过配置项 `netTransport=forge` 或 JVM 参数 `-Dqzuilib.net.transport=forge` 切换到回退适配器。
