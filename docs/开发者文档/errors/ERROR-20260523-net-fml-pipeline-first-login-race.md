# ERROR-20260523-net-fml-pipeline-first-login-race

## 错误现象

客户端首次连接服务端时，服务端偶发在 FML 登录握手阶段报错并断开连接：

```text
java.util.NoSuchElementException: packet_handler
    at io.netty.channel.DefaultChannelPipeline.addBefore(...)
    at cpw.mods.fml.common.network.handshake.NetworkDispatcher.insertIntoChannel(...)
...
lost connection: Internal Exception: io.netty.handler.codec.DecoderException: java.io.IOException: Bad packet id 23
```

同一个客户端后续再次连接通常可以进入世界。

## 触发场景

冷启动服务端和客户端后，第一次登录会同时触发 Forge/FML 握手、Netty pipeline 调整和大量首次类转换。旧实现中 Qz vanilla transport 在 `NetHandlerPlayClient` / `NetHandlerPlayServer` 构造尾部立即发送能力握手，早于 FML `ClientConnectedToServerEvent` /
`ServerConnectionFromClientEvent`。

## 根本原因

Play NetHandler 构造完成不等价于 FML 网络连接建立完成。此时 FML 的 `NetworkDispatcher` 仍可能处在向 pipeline 插入或移除 `fml:packet_handler` 的登录握手阶段，Qz 过早发送 vanilla custom payload 会把自定义协议流量混入 FML 自身握手窗口。冷启动首次类转换让该时序更容易暴露，表现为
`packet_handler` 缺失或坏包。

## 修复方案

- `NetHandlerPlayClient.<init>` 注入只缓存构造参数中的 `NetworkManager`，不再发送 Qz 能力握手。
- `NetHandlerPlayServer.<init>` 注入只记录 Play handler 构造就绪，不再发送 Qz 能力握手。
- 新增 `VanillaConnectionLifecycle` 监听 FML connected/disconnected 事件。
- 客户端在 `ClientConnectedToServerEvent` 后发送一次 C2S 能力握手。
- 服务端在 `ServerConnectionFromClientEvent` 后解析 `NetHandlerPlayServer.playerEntity`，再排到服务端主线程队列发送一次 S2C 能力握手，避开 `initializeConnectionToPlayer` 前 `player.playerNetServerHandler` 被临时置空的窗口。

## 预防措施

网络协议首包不要以 Minecraft Play handler 构造期作为“连接已建立”边界。涉及 Forge/FML 握手、Channel 注册或跨模组 custom payload 的逻辑，应以 FML connection-established 事件作为语义起点；如果 vanilla 玩家字段在事件触发点仍处于迁移中，应排到下一次主线程 tick 使用常规出站路径。
