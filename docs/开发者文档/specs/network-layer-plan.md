# 网络层实验性方案：HTTP-like 内容语义双端通信

> **状态：实验性方案（内容语义版已落地）**
> 本文档记录 4.1LTS 阶段网络层（Channel + Fetch + Store 三层 API）的设计决策。当前实现已从“每种请求一个 Java 类型”调整为 `route/key + contentType + headers + body` 的内容模型；POJO 反射 codec 仅作为可选辅助，不再是协议身份。

## 当前实现范围

已落地：

- `club.heiqi.uilib.net.api`：`NetService`、`NetBody`、`NetContentType`、`NetMessage`、`NetRequest`、`NetResponse`、Channel、Fetch、Store、id、上下文与异常。
- `club.heiqi.uilib.net.core`：envelope v2、分片重组、大小策略、request registry、能力握手、主线程派发队列。
- `club.heiqi.uilib.net.codec`：可选 POJO 二进制辅助 codec、字段布局、varint、`@NetField` / `@NetTransient`。
- `club.heiqi.uilib.net.transport.vanilla`：默认 vanilla custom payload 传输适配器与 mixin 入站分发。
- `club.heiqi.uilib.net.transport.forge`：Forge/FML 回退适配器与 tick bridge。
- `club.heiqi.uilib.mixin.early.network`：client/server NetHandler early mixin。
- `/qzuilib test` 诊断菜单中的 `net_self_check` 自检页。

配套文档：

- `docs/使用文档/02-控件/网络层入门.md`
- `docs/开发者文档/specs/net-codec-wire-format.md`
- `docs/开发者文档/specs/net-vanilla-mixin-strategy.md`
- `docs/开发者文档/specs/net-self-check.md`

## 背景

Qz-UILib 进入 4.1LTS 后需要补一个客户端-服务端通信子系统。Forge 1.7.10 自带的 `SimpleNetworkWrapper` + `IMessage` 写法繁琐：每条消息都要一个 message 类、一个 handler 类、手动注册 discriminator、自己处理线程切换，与本库其它 API（HTML-like `UiDocument`、链式 `ForgeConfigTemplateScreen.Spec`）的现代感格格不入。

新的目标不是复制 Forge 的“一种业务请求 = 一个协议类型”模型，而是在 Netty / vanilla custom payload 底座上提供一层类似 HTTP/Web 的内容语义：

```text
route/key + contentType + headers + body
```

业务侧可以选择 JSON、文本、紧凑二进制或自己的 MIME-like 类型。框架负责路由、方向、大小、分片、请求响应和主线程切换；具体 JSON 字段或二进制结构由业务 handler 自己解析。

## 设计原则

1. **Web 心智模型优先**：Channel / Fetch / Store 的协议身份是 route 与内容类型，不是 Java class。
2. **不复用 `ui` 包**：服务端 dedicated jar 没有 LWJGL/GL/Minecraft client 类，网络核心必须双端可加载，UI 绑定单独抽出客户端桥。
3. **上层不依赖 Forge 网络栈**：上层 API 0 个 `cpw.mods.fml.common.network` import；默认走 vanilla mixin 适配器，Forge 适配器作为兼容退路。
4. **大小限制分层**：32KB 是兼容物理帧下限，普通逻辑消息默认 16 MiB，超大内容进入后续 stream/chunk 路径。
5. **可选 codec 不支配协议**：`NetCodec` 可帮助业务做紧凑二进制 body，但不会决定路由、握手或消息身份。
6. **适配器仅启动期可选**：`NetService.bootstrap(ITransport)` 后不可热切换。

## 核心心智模型

### Channel

`NetChannel` 类似 WebSocket。发送的是 `NetMessage`：

```java
NetChannel chat = NetService.getInstance()
    .channel(NetChannelId.of("mymod", "chat"))
    .onReceive(new NetChannel.NetChannelHandler() {
        @Override
        public void onReceive(NetMessage message, NetReceiveContext context) {
            String json = message.getBody().asUtf8String();
            // 业务解析 JSON 或二进制 body。
        }
    })
    .register();

chat.toServer().sendJson("{\"text\":\"hello\"}");
chat.toPlayer(player).send(NetMessage.json("{\"text\":\"reply\"}")
        .withHeader("x-message-kind", "chat"));
```

### Fetch

`NetFetchEndpoint` 类似 `fetch` / RPC，目前仅 C2S：

```java
NetFetchEndpoint getUser = NetService.getInstance()
    .fetch(NetEndpointId.of("mymod", "getUser"))
    .timeout(Duration.ofSeconds(5))
    .onRequest(new NetFetchEndpoint.NetFetchHandler() {
        @Override
        public void onRequest(NetRequest request, NetFetchEndpoint.NetFetchRequestContext context) {
            context.reply(NetResponse.json("{\"name\":\"Alex\"}"));
        }
    })
    .register();

CompletableFuture<NetResponse> future = getUser.callJson("{\"id\":\"p1\"}");
```

`NetResponse` 有 `statusCode`、headers 与 body。服务端主动推送请使用 Channel 或 Store。
返回的 `CompletableFuture` 支持本地 `cancel(false)`：取消会移除 pending 请求，远端迟到响应会被忽略，不再完成调用方 future。

### Store

`NetStore` 类似 Redux / SWR 的快照同步，状态也是 `NetBody`：

```java
NetStore counter = NetService.getInstance()
    .store(NetStoreId.of("mymod", "counter"))
    .scope(NetStoreScope.GLOBAL)
    .initialJson("{\"value\":0}")
    .register();

counter.set(NetBody.json("{\"value\":1}"));
counter.view().subscribe(new NetStoreView.NetStoreSubscriber() {
    @Override
    public void onSnapshot(NetBody snapshot) {
        redraw(snapshot.asUtf8String());
    }
});
```

`GLOBAL` 默认可广播；配置 `.accessControl(...)` 后会枚举在线玩家逐个过滤。`PER_PLAYER` 与 `DIMENSION` 通过 `setForPlayer(...)`、`mutateForPlayer(...)`、`getForPlayer(...)`、`setForDimension(...)`、`mutateForDimension(...)`、`getForDimension(...)` 表达隔离状态。

仅客户端的 `NetStoreUiBridge` 负责把 Store 视图绑定到 `ElementNode`，它是 net → ui 的唯一单向依赖点。

## 关键决策

### 决策 1：双适配器（Vanilla mixin 默认 + Forge 兼容）

`ITransport` SPI 提供两份实现，`NetService.bootstrap(ITransport)` 在 `CommonProxy.preInit` 选定：

| 适配器 | 包路径 | 默认 | 用途 |
|---|---|---|---|
| `VanillaMixinTransport` | `net.transport.vanilla` | ✅ | 主路径，mixin 拦 vanilla custom payload，绕开 Forge 网络栈 |
| `ForgeTransport` | `net.transport.forge` | 备选 | 兼容退路，用 FML pipeline 排查或兜底 |

启动期选择顺序为 JVM 参数 `-Dqzuilib.net.transport=<vanilla|forge>` 优先，其次读取 Forge 配置 `netTransport`，默认 `vanilla`。适配器在 `NetService.bootstrap(ITransport)` 后不可热切换。

默认 vanilla mixin 的核心理由：

- 上层 API 与适配器实现严格分离，`net.api / .codec / .core` 全部不 import Forge 网络包。
- 跨版本可移植，未来切到 1.20+ 或 Fabric/Neo 时可替换 `ITransport` 实现。
- `VanillaMixinTransport` 自身只依赖 vanilla 网络类与本库胶水。

### 决策 2：Vanilla mixin 注入点

网络 Mixin 必须走现有 early loader：`NetHandlerPlayClient`、`NetHandlerPlayServer`、`S3FPacketCustomPayload`、`C17PacketCustomPayload` 都是 vanilla / Forge 1.7.10 运行期基础类，放 late 档语义不对。

| Mixin 目标 | 注入方法 | 用途 |
|---|---|---|
| `NetHandlerPlayClient.handleCustomPayload` | `@Inject(at=HEAD, cancellable=true)` | 客户端拦 `S3FPacketCustomPayload`，channel 名前缀 `qz:` 时自处理 |
| `NetHandlerPlayServer.processVanilla250Packet` | `@Inject(at=HEAD, cancellable=true)` | 服务端拦 `C17PacketCustomPayload`，channel 名前缀 `qz:` 时自处理 |
| `NetHandlerPlayClient.<init>` | `@Inject(at=TAIL)` | 客户端可发能力握手信号 |
| `NetHandlerPlayClient.onDisconnect` | `@Inject(at=HEAD)` | 客户端断连信号 |
| `NetHandlerPlayServer.<init>` | `@Inject(at=TAIL)` | 服务端玩家可收包信号 |
| `NetHandlerPlayServer.onDisconnect` | `@Inject(at=HEAD)` | 服务端玩家离开信号 |

`ci.cancel()` 只在 `qz:` 前缀命中后执行，其它 channel 完全放行。

### 决策 3：Envelope v2

所有逻辑 channel 都映射到 vanilla channel name `qz:0`。真实业务 route 放进 envelope：

| 字段 | 含义 |
|---|---|
| `kind` | Channel / Fetch request / Fetch response / Store snapshot / Meta / Chunk |
| `targetSide` | 接收侧方向校验 |
| `key` | 业务 route，例如 `mymod:chat` |
| `contentType` | MIME-like 内容类型 |
| `requestId` | Fetch 请求 id |
| `statusCode` | Fetch response 状态码 |
| `headers` | 业务 headers |
| `payload` | body 字节 |

`NetEnvelope` 不再携带 Java `typeId`。`NetMessage`、`NetRequest`、`NetResponse` 都只是 headers + body 的业务容器。

Header 采用轻量 HTTP-like 规则：名称大小写不敏感并归一为小写 token，单帧最多 32 个 header，单名最多 64 字节，单值最多 1024 字节，总 header 字节最多 8192，值不允许 CR/LF。Header 只用于元数据，不承担大内容传输。

### 决策 4：能力握手，不做类型握手

客户端连接就绪与服务端玩家加入时发送 `META` 帧，当前内容是 JSON：

```json
{
  "protocol": 2,
  "contentTypes": ["application/json", "application/octet-stream", "text/plain; charset=utf-8"],
  "ordinaryLogicalLimit": 16777216
}
```

该握手用于协议版本、内容能力与大小边界，不同步 Java class 清单，也不因为 POJO schema 差异阻断主路径通信。

### 决策 5：可选 POJO codec

不引入 Kryo/FST/protobuf，保留自实现反射 codec 作为业务二进制 body 辅助：

- 启动或首次使用时扫描 POJO：`Class<T>` → `FieldLayout`。
- 支持基础类型、包装类型、`String`、`byte[]`、`Enum`、`List`、`Set`、`Map`、嵌套 POJO。
- `@NetTransient` 字段排除，`@NetField(name="x", since=1)` 固定协议字段名。
- `NetCodec.custom(Class<T>, EncoderFn, DecoderFn)` 支持完全手写。

推荐用法：

```java
byte[] bytes = NetCodec.of(MyState.class).encode(state);
NetBody body = NetBody.of(NetContentType.of("application/x-mymod-state"), bytes);
channel.toServer().send(body);
```

### 决策 6：大小策略

网络层采用“兼容物理帧 + 普通逻辑消息 + 大内容路径”三层限制：

| 限制项 | 默认值 | 用途 |
|---|---:|---|
| `COMPAT_PHYSICAL_FRAME_LIMIT` | `32766` bytes | 最低兼容物理帧上限，覆盖原生 C17 与无扩展环境 |
| `LARGE_MESSAGE_WARN_THRESHOLD` | `8 MiB` | 普通消息进入大消息提示的阈值 |
| `DEFAULT_LOGICAL_MESSAGE_LIMIT` | `16 MiB` | Channel / Fetch / Store 普通逻辑消息默认上限 |
| `GTNH_DEFAULT_PHYSICAL_LIMIT` | `256 MiB` | GTNH/Hodgepodge 环境可理解的默认物理包能力 |
| `GTNH_HARD_PHYSICAL_LIMIT` | `1 GiB` | 物理包硬上限，只作为防滥用边界 |

- C→S：原生 C17 单帧仍按 `32766` 字节兼容下限处理；超过当前物理能力但低于逻辑上限时自动分片。
- S→C：GTNH/Hodgepodge 环境可理解为 256 MiB 默认物理能力，但普通逻辑消息仍受 16 MiB 默认上限约束。
- 超过 16 MiB 的数据不作为普通 Channel / Fetch / Store 消息发送，必须走后续 stream/chunk API。

### 决策 7：GTNH 2.8.4 兼容性约束

以项目当前基准 `setGtnhVersion("2.8.4")` 为准，默认 vanilla mixin transport 可实现，但实现期必须遵守：

1. 网络 Mixin 走 early loader，不新增 late 档 vanilla 目标。
2. `handleCustomPayload` / `processVanilla250Packet` 只拦截 `qz:` 前缀，其它 channel 放行。
3. 32KB 只是兼容下限，普通逻辑消息默认 16 MiB，GTNH 物理能力按 256 MiB 默认、1 GiB 硬上限处理。
4. Forge 适配器仅作为回退路径，需要额外验证 ModularUI2 对 `SimpleNetworkWrapper` 的 overwrite 行为。

重点关注来源：

| 关注项 | 发现 | 对本方案影响 |
|---|---|---|
| `Hodgepodge` | 修改 `S3FPacketCustomPayload` 长度、channel 名校验与 fallback | `qz:0` 满足 channel 名限制；32KB 保留为兼容下限 |
| `ServerUtilities` | `NetHandlerPlayServer.onDisconnect` 包装 vanish 断开消息 | 我方 onDisconnect 只做信号，不取消、不改返回 |
| `ModularUI` / `ModularUI2` | 改 `PacketBuffer`、`handleSetSlot` 与 `SimpleNetworkWrapper` | 默认路径不碰 `SimpleNetworkWrapper`，Forge 回退需单独跑自检 |
| `Backhand`、`NotEnoughIds`、`Angelica`、`CoreTweaks` | 存在 NetHandler 相关 mixin，但集中在其它方法 | 当前注入点未发现同方法 HEAD cancellable 冲突 |

### 决策 8：双端隔离

| 包 | 加载侧 | 内容 | 是否依赖 Forge |
|---|---|---|---|
| `club.heiqi.uilib.net.api` | 双端 | `NetService` 门面、三层 API、内容语义类型 | ❌ |
| `club.heiqi.uilib.net.codec` | 双端 | 可选 POJO codec、字段布局、varint | ❌ |
| `club.heiqi.uilib.net.core` | 双端 | envelope、requestId、超时、分片、能力握手 | ❌ |
| `club.heiqi.uilib.net.transport` | 双端 | `ITransport` SPI、`NetSide`、`NetReceiveOrigin` | ❌ |
| `club.heiqi.uilib.net.transport.vanilla` | 双端 | mixin 入口胶水 | ❌（仅 vanilla MC 类） |
| `club.heiqi.uilib.mixin.early.network` | 双端 | early mixin 类 | ❌ |
| `club.heiqi.uilib.net.transport.forge` | 双端 | Forge 兼容层 | ✅ |
| `club.heiqi.uilib.net.client` | 仅客户端 | `NetStoreUiBridge` | ❌ |
| `club.heiqi.uilib.internal.devtools` | 仅客户端 | 自检页 | ❌ |

`CommonProxy.preInit` 仅初始化双端公共部分。`ClientProxy.preInit` 多调 `NetStoreUiBridge.getInstance().initialize()`。网络公共类绝不 import `net.minecraft.client.*` / `org.lwjgl.*`。

### 决策 9：线程模型

| 入口 | 默认线程 | 切换 |
|------|---------|-----|
| Channel `onReceive` | 网络线程 | `NetReceiveContext.runOnMainThread(...)` |
| Fetch `onRequest` | 网络线程 | 需要读世界时显式切主线程 |
| Fetch future complete | 网络线程 | `thenAcceptAsync(.., NetService.mainThreadExecutor())` |
| Store mutate / set | 调用方线程 | 后续可收敛到服务端主线程约束 |
| Store subscriber | 当前实现为收到快照时的调用线程 | `NetStoreUiBridge` 会投递到客户端主线程 |

`MainThreadDispatcher` 单例挂 tick bridge drain 队列。未来切到非 Forge 环境时可替换 tick 信号，SPI 不变。

### 决策 10：安全与防滥用

- **大小限制与分片**：超过当前物理帧能力但低于逻辑上限时切 `CHUNK`，30 秒重组超时丢弃；超过逻辑上限拒绝并提示改用大内容路径。
- **方向校验**：envelope 头携带 target side，方向对不上 LOG.warn 丢弃。
- **Store 访问控制**：`.accessControl(...)` 决定玩家是否可访问 Store；存在访问控制时不会走盲目广播，而是逐个在线玩家过滤。
- **Fetch 限流**：保留后续滑动窗口限流规划。
- **内容解析责任**：框架不解析业务 JSON，不信任远端 body；业务 handler 必须做输入校验。

### 决策 11：注册时机

下游 mod 在 `preInit` 调 `NetService.getInstance().channel/fetch/store(...).register()`。所有注册必须在 `FMLPostInitializationEvent` 之前完成，之后冻结。Qz-UILib 在 `CommonProxy.postInit` 末尾把 `NetService` 切到已冻结状态，再注册抛 `IllegalStateException`。

## 关键文件

| 路径 | 职责 |
|---|---|
| `src/main/java/club/heiqi/uilib/net/api/NetBody.java`、`NetContentType.java`、`NetMessage.java`、`NetRequest.java`、`NetResponse.java` | 内容语义公共模型 |
| `src/main/java/club/heiqi/uilib/net/api/NetService.java` | 单例门面、注册表、发送/接收分发 |
| `src/main/java/club/heiqi/uilib/net/api/NetChannel.java`、`NetFetchEndpoint.java`、`NetStore.java`、`NetStoreView.java` | 三层 API |
| `src/main/java/club/heiqi/uilib/net/core/NetEnvelope.java` | v2 内容 envelope |
| `src/main/java/club/heiqi/uilib/net/core/NetChunkAssembler.java`、`NetPayloadLimits.java`、`NetRequestRegistry.java`、`MainThreadDispatcher.java` | 分片、大小、请求与线程队列 |
| `src/main/java/club/heiqi/uilib/net/codec/NetCodec.java`、`FieldLayout.java`、`Varint.java`、`NetField.java`、`NetTransient.java` | 可选二进制 codec |
| `src/main/java/club/heiqi/uilib/net/transport/ITransport.java`、`FrameHandler.java`、`NetReceiveOrigin.java`、`NetSide.java` | 传输 SPI |
| `src/main/java/club/heiqi/uilib/net/transport/vanilla/VanillaMixinTransport.java` | 默认 vanilla 适配器 |
| `src/main/java/club/heiqi/uilib/net/transport/forge/ForgeTransport.java` | Forge 兼容适配器 |
| `src/main/java/club/heiqi/uilib/mixin/early/network/MixinNetHandlerPlayClient.java`、`MixinNetHandlerPlayServer.java` | early mixin 注入 |
| `src/main/java/club/heiqi/uilib/net/client/NetStoreUiBridge.java` | 仅客户端 Store ↔ DOM 桥 |
| `src/main/java/club/heiqi/uilib/internal/devtools/NetSelfCheckPage.java` | 自检页 |

## 验证

已纳入纯 JVM 测试的重点：

- `NetBody` 与 `NetContentType`：JSON / binary / custom MIME-like 内容语义。
- `NetEnvelope`：contentType、headers、statusCode、body 往返。
- `NetService`：Channel 发送内容 envelope、注册冻结、超过物理帧自动分片。
- `NetService`：Store accessControl 过滤、per-player/dimension 定向快照。
- `NetChunkAssembler`：大 envelope 分片重组。
- `NetRequestRegistry`：Fetch cancel 移除 pending、超时与断连清理。
- `NetStoreUiBridge`：Store DOM renderer 投递到客户端主线程。
- `NetTransportFactory`：配置和 JVM 参数选择 vanilla/forge 传输适配器。
- `EarlyMixins`：SERVER 侧不返回客户端 mixin。

已纳入运行时自检页的重点：

- 内容信封与 Header 规则运行时断言。
- Channel C2S/S2C ping/pong。
- 超过 32KB 的 Channel C2S 分片与服务端重组。
- Fetch C2S 请求响应。
- Fetch 远端错误响应、pending timeout 与本地 cancel。
- Store snapshot 从服务端到客户端视图同步。
- PER_PLAYER Store + accessControl + setForPlayer 定向 snapshot。
- Store DOM bridge 主线程渲染。

仍需人工验证：

- dedicated server 完整启动 smoke：当前默认 `runServer` 已确认 Mixin 环境为 `SERVER`，但会被 LWJGL3ify relauncher 中止，需换用不带该 relauncher 的服务端配置后复跑。
- Forge 回退适配器与 ModularUI2 环境的兼容性。

## 后续待解边界

- stream/chunk 大内容 API：超过 16 MiB 的资源、文件或大快照要走独立生命周期、进度与取消模型。
- Fetch 限流：每玩家滑动窗口、错误状态码、可观测日志仍需补全。
- Store 增量：内容模型下可以选择 JSON Patch、业务二进制 delta 或全量快照，需要单独规格化。
- 代理服务器：BungeeCord/Velocity 类代理对 `qz:0` custom payload 的转发行为仍需联机实测。
