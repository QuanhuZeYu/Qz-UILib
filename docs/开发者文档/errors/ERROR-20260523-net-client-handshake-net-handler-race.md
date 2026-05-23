# ERROR-20260523-net-client-handshake-net-handler-race

## 错误现象

客户端连接服务端时在 `GuiConnecting` tick 中崩溃：

```text
java.lang.IllegalStateException: 客户端 NetHandler 尚未建立
    at club.heiqi.uilib.net.transport.vanilla.VanillaPacketBuilders.resolveClientNetworkManager
    at club.heiqi.uilib.net.transport.vanilla.VanillaPacketBuilders.sendToServer
    at club.heiqi.uilib.net.api.NetService.sendCapabilityHandshakeToServer
    at club.heiqi.uilib.net.transport.vanilla.VanillaMixinTransport.onClientHandshakeReady
    at net.minecraft.client.network.NetHandlerPlayClient.<init>
```

## 触发场景

`MixinNetHandlerPlayClient` 在 `NetHandlerPlayClient.<init>` TAIL 触发客户端能力握手。此时构造参数中的 `NetworkManager` 已经可用，但 `Minecraft.getMinecraft().getNetHandler()` 尚未完成回填。

## 根本原因

握手首包使用了通用 `sendToServer()` 发送路径，而该路径通过反射读取 `Minecraft.getNetHandler()` 再取 `NetworkManager`。在 NetHandler 构造期，这个全局访问路径存在生命周期竞态。

## 修复方案

- 在 `VanillaPacketBuilders` 中缓存 mixin 已传入的 early `NetworkManager`。
- `sendToServer()` 优先使用该缓存；缓存不存在时再回退到 `Minecraft.getNetHandler()` 反射路径。
- 客户端断连和 transport shutdown 时清理缓存，避免复用旧连接。
- 增加 JVM 测试覆盖缓存路径会把 `C17PacketCustomPayload` 放入 early `NetworkManager` 的出站队列。

## 预防措施

早期 mixin 生命周期中已经拿到的 vanilla 对象应优先沿调用链传递或缓存使用，不要再从全局单例反查同一个对象。特别是 NetHandler 构造、GUI 打开、资源 reload 等阶段，全局入口常常晚于局部构造参数可用。
