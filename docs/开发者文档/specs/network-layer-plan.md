# 网络层实验性方案：web 语义双端通信

> **状态：实验性方案（未实现）**
> 本文档记录 4.1LTS 阶段网络层（Channel + Fetch + Store 三层 API）的设计决策，作为后续分步实现的依据。LTS 期间公共面一旦定准不破坏，因此先固化文档边界，再进入代码落地。

## 背景

Qz-UILib 进入 4.1LTS 后需要补一个客户端-服务端通信子系统。Forge 1.7.10 自带的 `SimpleNetworkWrapper` + `IMessage` 写法繁琐：每条消息都要一个 message 类、一个 handler 类、手动注册 discriminator、自己处理线程切换，与本库其它 API（HTML-like `UiDocument`、链式 `ForgeConfigTemplateScreen.Spec`）的现代感格格不入。

目标：在 Netty 底座之上盖一层"像 web 一样"的通信 API，让作者写双端代码的体感接近 WebSocket（双向消息）+ fetch（请求-响应）+ Redux/Zustand（状态同步），并满足以下硬指标：

1. **不复用 `ui` 包**——服务端 dedicated jar 没有 LWJGL/GL/Minecraft client 类，网络核心必须双端可加载，UI 绑定单独抽出一个客户端独占的桥
2. **零样板**——作者写 POJO 直接发，框架反射出 codec，不让用户写一行 `encode/decode`
3. **比 SimpleNetworkWrapper 更省带宽**——紧凑二进制反射 codec + 类型/字段字典 + varint
4. **绕开 Forge 网络栈**——上层 API 0 个 `cpw.mods.fml.common.network` import；默认走 vanilla mixin 适配器，Forge 适配器作为兼容退路。考虑未来 1.20+/Fabric/Neo 主流 MC 版本可能没有 Forge，上层 API 与默认适配器都不应假设 Forge 存在

## 设计原则

- 与项目美学对齐：单例服务门面 + 链式建造者（参考 [`FontService.getInstance()`](../../../src/main/java/club/heiqi/uilib/font/FontService.java)、[`ForgeConfigTemplateScreen.Spec`](../../../src/main/java/club/heiqi/uilib/config/ForgeConfigTemplateScreen.java)）
- 双端隔离用**包名约定 + ClientProxy 引用守卫**，全库零 `@SideOnly`、无依赖注入框架
- LTS 期间 4.1.x 不破坏 API
- `usesShadowedDependencies = false`，**禁引入 Kryo / FST / 任何第三方序列化库**，自实现紧凑反射 codec
- 适配器**仅启动期可选**，bootstrap 后不可热切换

## 关键决策

### 决策 1：双适配器（Vanilla mixin 默认 + Forge 兼容）

`ITransport` SPI 提供两份实现，`NetService.bootstrap(ITransport)` 在 `CommonProxy.preInit` 选定，之后冻结：

| 适配器 | 包路径 | 默认 | 用途 |
|---|---|---|---|
| `VanillaMixinTransport` | `net.transport.vanilla` | ✅ | 主路径，mixin 拦 vanilla custom payload，绕开 Forge 网络栈 |
| `ForgeTransport` | `net.transport.forge` | 备选 | 兼容退路，用 `NetworkRegistry.newChannel` 挂 FML pipeline，调试期可切回排查问题 |

**默认 vanilla mixin** 的核心理由：
- 上层 API 与适配器实现严格分离 → `net.api / .codec / .core / .store` 全部 `cpw.mods.fml.common.network` 0 import
- 跨版本可移植：未来切到 1.20+ 或 Fabric/Neo，直接换 `ITransport` 实现，无需重写上层
- `VanillaMixinTransport` 自身只依赖 vanilla 包名（`net.minecraft.network.*`、`net.minecraft.network.play.*`），未来甚至可作为非 Forge 环境的参照实现

**Forge 适配器保留**：项目当前生态全部是 Forge 1.7.10，作者不熟悉 mixin 时可一行回退；同时让 `ITransport` SPI 真有两份实现，验证抽象切实站得住脚。

### 决策 2：Vanilla mixin 注入点（默认适配器）

网络 Mixin **必须走现有 early loader**：GTNHMixins 约定 early mixin 用于 vanilla / Forge 类，late mixin 用于 FML 加载后才能稳定查询的 mod 类。`NetHandlerPlayClient`、`NetHandlerPlayServer`、`S3FPacketCustomPayload`、`C17PacketCustomPayload` 都是 vanilla / Forge 1.7.10 运行期基础类，放 late 档语义不对，也容易错过类加载窗口。

实现时复用现有 `mixins.qz_uilib.early.json` 与 `club.heiqi.uilib.mixin.early.EarlyMixins`，新增类放在 `club.heiqi.uilib.mixin.early.network`，在 `EarlyMixins.getMixins(...)` 中按运行侧返回：

- 客户端：追加 `network.MixinNetHandlerPlayClient`
- 双端/服务端：追加 `network.MixinNetHandlerPlayServer`

当前 jar 的 `MANIFEST.MF` 由 GTNH convention 生成，已包含 `FMLCorePlugin: club.heiqi.uilib.mixin.early.EarlyMixins` 与 `MixinConfigs: mixins.qz_uilib.json`；不要新建静态 `src/main/resources/META-INF/MANIFEST.MF`。如果未来确实要拆独立配置，应新增第二个 early loader 或改造现有 loader，而不是把 vanilla 网络目标塞进 late 配置。

#### Mixin 类清单（`club.heiqi.uilib.mixin.early.network` 包）

| Mixin 目标 | 注入方法 | 用途 | 存放位置 |
|---|---|---|---|
| `NetHandlerPlayClient.handleCustomPayload`（SRG `func_147240_a`） | `@Inject(at=HEAD, cancellable=true)` | 客户端拦 `S3FPacketCustomPayload`，channel 名前缀 `qz:` 时自处理 + `ci.cancel()` | Client-only early |
| `NetHandlerPlayServer.processVanilla250Packet`（SRG `func_147349_a`） | `@Inject(at=HEAD, cancellable=true)` | 服务端拦 `C17PacketCustomPayload`，前缀 `qz:` 时自处理 + `ci.cancel()` | Common early |
| `NetHandlerPlayClient.<init>` | `@Inject(at=TAIL)` | 客户端"可发握手"信号 | Client-only early |
| `NetHandlerPlayClient.onDisconnect`（SRG `func_147231_a`） | `@Inject(at=HEAD)` | 客户端断连信号 | Client-only early |
| `NetHandlerPlayServer.<init>` | `@Inject(at=TAIL)` | 服务端"玩家可发包"信号 | Common early |
| `NetHandlerPlayServer.onDisconnect`（SRG `func_147231_a`） | `@Inject(at=HEAD)` | 服务端玩家离开信号 | Common early |

**不 mixin `NetworkManager.channelActive`**：操纵 Netty pipeline 在 lwjgl3ify 环境下有 Netty 版本兼容风险；拦 NetHandler 处理函数已经够纯净。vanilla 对未知 channel 的 custom payload 默认静默丢弃，`ci.cancel()` 仅做防御。

#### 收发实现

**接收**：mixin 在 HEAD 拦截，把 `S3F/C17` 解出 channel name + payload byte[]，交给 `VanillaMixinTransport.dispatchInbound(channelName, payload, side, sender)`。Transport 内部按 envelope 头分发到对应的 `FrameHandler`。

**发送**：直接构造 vanilla 包对象，调 `NetworkManager.scheduleOutboundPacket`（SRG `func_150725_a`）：

```java
// 客户端 → 服务端
NetworkManager nm = Minecraft.getMinecraft().getNetHandler().netManager;
nm.scheduleOutboundPacket(new C17PacketCustomPayload(channelName, payload));

// 服务端 → 客户端（vanilla 字段，无 Forge 依赖）
EntityPlayerMP p = ...;
p.playerNetServerHandler.netManager
    .scheduleOutboundPacket(new S3FPacketCustomPayload(channelName, payload));
```

**服务端 NetworkManager 收集**：不维护额外 registry，直接从 `EntityPlayerMP.playerNetServerHandler.netManager` 取。这两个字段都是 vanilla（SRG `field_7135_a` / `field_147371_a`），生命周期由 vanilla 保证。

#### 包大小处理

- C→S：vanilla 锁 `Short.MAX_VALUE - 1 = 32766` 字节，core 层强制分片
- S→C：Forge 在 1.7.10 把 S3F 限制放开到 ~2MB（`writeVarShort`），但走 vanilla 适配器不能依赖 Forge 这个改动 → core 层对 S→C 也按 `Short.MAX_VALUE - 1` 分片，保证两个适配器行为一致

#### Channel 名约定

所有逻辑 channel 都映射到一个 vanilla channel name `qz:0`（vanilla 单 channel 名长度 ≤ 20），envelope 头携带真实 `NetChannelId(namespace, name)`。这样 mixin 拦截前缀只需对 `qz:` 单独判断一次。

#### GTNH 2.8.4 兼容性初筛

以项目当前基准 `setGtnhVersion("2.8.4")` 为准，读取 GTNH 2.8.4 release 包内 README 模组表：共 231 个模组，其中 200 个指向 GTNewHorizons GitHub 仓库，27 个非 GitHub/CurseForge 等外部来源，4 个非 GTNH GitHub 仓库。对 200 个 GTNH 仓库按 `com.github.GTNewHorizons:<repo>:<version>:dev` 做 Maven 初筛，192 个可解析，8 个需要手工坐标或外部包兜底。

机器初筛结果：123 个低关注、45 个中关注、10 个仅观察、10 个 Critical、4 个 High、8 个 Manual。Critical / High 并不等于冲突，只表示存在网络/包处理相关 Mixin 或 coremod，需要实现期重点复核。

| 关注项 | 发现 | 对本方案影响 |
|---|---|---|
| `Hodgepodge` | early mixin 修改 `S3FPacketCustomPayload` 构造、读写长度、channel 名校验，并修补 `NetworkDispatcher` fallback | 保留 32KB 分片上限，不依赖 Hodgepodge 放宽后的 S3F 大包；`qz:0` channel 名满足 20 字符限制 |
| `ServerUtilities` | `MixinNetHandlerPlayServer` 在 `onDisconnect` 包装 vanish 断开消息 | 我方 `onDisconnect` 只做 HEAD 信号，不取消、不改返回，保持幂等，避免和 vanish 消息逻辑争抢 |
| `ModularUI` / `ModularUI2` | 分别改 `PacketBuffer`、`NetHandlerPlayClient.handleSetSlot` 与 `SimpleNetworkWrapper` | 默认 vanilla transport 不碰 `SimpleNetworkWrapper`；Forge 兼容适配器要单独跑自检 |
| `Backhand`、`NotEnoughIds`、`Angelica`、`CoreTweaks` | 都 mixin `NetHandlerPlayClient/Server`，但集中在 held item、multiblock、respawn/join 等其它方法 | 我方只注入 custom payload、构造和断连，当前源审计未发现同方法 HEAD cancellable 冲突 |
| `GT5-Unofficial`、`Gadomancy`、`LogisticsPipes`、`ThaumicTinkerer`、`Salis-Arcana`、`Gravitation-Suite-Neo` | 主要是自有 `SimpleNetworkWrapper`/packet 或 mod 类网络 mixin | 与 vanilla custom payload 拦截无直接冲突；需在整包环境确认 channel 名和包大小行为 |

结论：默认 vanilla mixin transport **可实现**，但实现期必须遵守三条约束：

1. 网络 Mixin 走 early loader，不新增 late 档 vanilla 目标。
2. `handleCustomPayload` / `processVanilla250Packet` 的 `ci.cancel()` 只在 `qz:` 前缀命中后执行，其它 channel 完全放行。
3. Forge 适配器仅作为回退路径，不能成为默认路径；它需要额外验证 ModularUI2 对 `SimpleNetworkWrapper` 的 overwrite 行为。

### 决策 3：Forge 适配器（兼容层）

`ForgeTransport` 用 `cpw.mods.fml.common.network.NetworkRegistry.INSTANCE.newChannel("qz", clientHandler, serverHandler)` 把 `SimpleChannelInboundHandler<FMLProxyPacket>` 挂 FML pipeline。出向同样构造 `S3F/C17PacketCustomPayload`，内部用 `FMLEmbeddedChannel.writeAndFlush`。

放在独立子包 `net.transport.forge`，`net.transport.vanilla` 与上层 API **永不 import 它**。LTS 期间标记 ⚠️ 进阶/兼容，不作为推荐路径。

### 决策 4：紧凑反射 Codec（自实现）

不引入 Kryo/FST/protobuf，自己写约 300 行反射 codec：

- 启动期一次性扫描 POJO：`Class<T>` → `FieldLayout`（按 `Comparator.comparing(Field::getName)` 稳定排序，避免 JVM 字段顺序漂移），缓存到 `ClassValue<FieldLayout>`
- 写入：基本类型直写、`String` 走 UTF-8 + varint 长度、`List/Set` varint 长度 + 元素递归、`Map` 同理、嵌套对象递归、`Enum` 写 `ordinal()` varint、`null` 用 1 byte 标志位
- **省带宽四件套**（vs SimpleNetworkWrapper + 用户手写 IMessage / vs GSON）：
  1. **类型字典**：握手时双端协商 type id ↔ class fqn ↔ schema hash，发送时只发 2 byte type id
  2. **字段字典**：`FieldLayout` 在握手时同步，后续只发 `fieldIndex` 不发字段名
  3. **Varint** 编码所有长度与小整数
  4. **字段索引 diff**（Store 层）：按字段索引算增量，发 `FieldDeltaFrame(fieldIndex, newValue)*`，比文本 JSON Patch 字节小一个量级

用户视角：`NetService.getInstance().channel(NetChannelId.of("mymod", "chat"), ChatMsg.class).register()` 一行，不写一字节 codec。

高级出口：`@NetTransient` 字段排除、`@NetField(name="x", since=1)` 重命名 + 版本、`NetCodec.custom(Class<T>, EncoderFn, DecoderFn)` 完全手写。

### 决策 5：双端隔离（包名约定）

| 包 | 加载侧 | 内容 | 是否依赖 Forge |
|---|---|---|---|
| `club.heiqi.uilib.net.api` | 双端 | `NetService` 门面、三层 API、建造者 | ❌ |
| `club.heiqi.uilib.net.codec` | 双端 | 反射 codec、字段布局、varint、schema | ❌ |
| `club.heiqi.uilib.net.core` | 双端 | 信封、requestId、超时、分片、握手协议 | ❌ |
| `club.heiqi.uilib.net.transport` | 双端 | `ITransport` SPI、`NetChannelId`、`NetTarget`、`NetSide` | ❌ |
| `club.heiqi.uilib.net.transport.vanilla` | 双端 | mixin 入口胶水（`VanillaMixinTransport`、Inbound dispatcher） | ❌（仅 vanilla MC 类） |
| `club.heiqi.uilib.mixin.early.network` | 双端 | early mixin 类（拦 NetHandler*） | ❌ |
| `club.heiqi.uilib.net.transport.forge` | 双端 | `ForgeTransport`（兼容层） | ✅ |
| `club.heiqi.uilib.net.store` | 双端 | Store 引擎、订阅、增量 | ❌ |
| `club.heiqi.uilib.net.client` | **仅客户端** | `NetStoreUiBridge`（Store ↔ ElementNode） | ❌ |
| `club.heiqi.uilib.internal.devtools` | 仅客户端 | 自检页 | ❌ |

`CommonProxy.preInit` 仅初始化双端公共部分（`NetService.bootstrap(new VanillaMixinTransport())`）。`ClientProxy.preInit` 多调 `NetStoreUiBridge.getInstance().initialize()`。**网络层公共类绝不 import `net.minecraft.client.*` / `org.lwjgl.*`**。

### 决策 6：三层 web 语义 API

#### Channel — WebSocket

```java
NetChannel<ChatMsg> chat = NetService.getInstance()
    .channel(NetChannelId.of("mymod", "chat"), ChatMsg.class)
    .onReceive((msg, ctx) -> ctx.runOnMainThread(() -> handle(msg, ctx)))
    .register();

chat.toServer().send(msg);
chat.toPlayer(player).send(msg);
chat.toPlayers(list).send(msg);
chat.toDimension(0).send(msg);
chat.toAll().send(msg);
```

错方向直接 `IllegalStateException`。`NetReceiveContext` 提供 `getSenderPlayer / getSide / runOnMainThread`。

#### Fetch — fetch / RPC（仅 C2S）

```java
NetFetchEndpoint<GetUserReq, GetUserResp> getUser = NetService.getInstance()
    .fetch(NetEndpointId.of("mymod", "getUser"), GetUserReq.class, GetUserResp.class)
    .timeout(Duration.ofSeconds(5))
    .onRequest((req, ctx) -> ctx.reply(loadUser(req.id)))
    .register();

CompletableFuture<GetUserResp> f = getUser.call(new GetUserReq("p1"));
f.thenAcceptAsync(this::renderUser, NetService.mainThreadExecutor());
```

`CompletableFuture` 默认网络线程 complete，超时 `NetTimeoutException`，远端异常 `NetRemoteException`，`cancel(true)` 仅本地标记 requestId 作废丢弃响应。**仅 C→S 方向**——服务端要主动推用 Channel 或 Store。

#### Store — Redux / SWR

```java
NetStore<Counter> counter = NetService.getInstance()
    .store(NetStoreId.of("mymod", "counter"), Counter.class)
    .scope(NetStoreScope.GLOBAL)
    .initial(new Counter(0))
    .accessControl((player, store) -> player.canCommandSenderUseCommand(2, ""))
    .register();

// 服务端
counter.mutate(c -> c.value++);

// 客户端
NetStoreView<Counter> view = counter.view();
view.subscribe(snapshot -> redrawUi(snapshot));
```

`NetStoreScope`：`GLOBAL` / `PER_PLAYER` / `DIMENSION`。

**与 UI 库衔接**（仅客户端 `net.client`）：

```java
view.bind(elementNode, (el, snapshot) -> {
    el.querySelector(".value").firstChild().setText(String.valueOf(snapshot.value));
});
```

`bind` 内部 `subscribe` 一次，每次到货切主线程后 renderer 显式改 DOM，复用 `UiLayoutInvalidationRegistry` 既有 dirty 机制。net → ui 的**唯一**单向依赖点。

### 决策 7：生命周期挂接（vanilla 信号）

`VanillaMixinTransport` 提供 6 个 hook，由 mixin 注入函数调用：

| Hook | 触发时机 |
|---|---|
| `onClientHandshakeReady(NetworkManager)` | client `NetHandlerPlayClient.<init>` TAIL — 此时连接已建立、`S01PacketJoinGame` 尚未到达，是最早可发握手帧的时机 |
| `onClientDisconnected(IChatComponent)` | client `NetHandlerPlayClient.onDisconnect` HEAD |
| `onServerPlayerJoined(EntityPlayerMP)` | server `NetHandlerPlayServer.<init>` TAIL — 玩家连接已建立，可推 schema 与 store snapshot |
| `onServerPlayerLeft(EntityPlayerMP)` | server `NetHandlerPlayServer.onDisconnect` HEAD |
| `onClientCustomPayload(channelName, payload)` | mixin 拦 `handleCustomPayload`，`qz:` 前缀触发 |
| `onServerCustomPayload(player, channelName, payload)` | mixin 拦 `processVanilla250Packet`，`qz:` 前缀触发 |

**`ClientProxy.onClientDisconnect` 监听 `FMLNetworkEvent.ClientDisconnectionFromServerEvent` 可保留** —— Forge 事件总线独立于 FML 网络栈，事件仍由 `FMLClientHandler` 在 vanilla `onDisconnect` 中触发，老代码无需改。

### 决策 8：握手与 schema 协商

客户端 `onClientHandshakeReady` 触发后立刻发 `qz:meta` 帧推送本端注册的所有 type id ↔ class fqn ↔ schema hash 列表。服务端收到后 5s 内回 ack（含服务端清单与共集）。schema 不一致的类型仅拒绝该类型注册并 LOG.error，避免恶意客户端发畸形结构骗服务端反射；完全无法通信才断连。

### 决策 9：线程模型

| 入口 | 默认线程 | 切换 |
|------|---------|-----|
| Channel `onReceive` | 网络线程（Netty IO） | `.onMainThread()` |
| Fetch `onRequest`（服务端） | 主线程（多半要读世界） | `.onNetworkThread()` |
| Fetch future complete（客户端） | 网络线程 | `thenAcceptAsync(.., mainThreadExecutor())` |
| Store mutate（服务端） | 调用方线程 | diff 计算串行入队网络线程 |
| Store subscriber（客户端） | 主线程 | `.onNetworkThread()` |
| `NetStoreView.bind(ElementNode, ..)` | 主线程（强保证） | 不可改 |

`MainThreadDispatcher` 单例挂 `TickEvent.ClientTickEvent` / `TickEvent.ServerTickEvent` drain 队列。注意：tick 事件由 Forge 派发，但等价 vanilla 信号（`Minecraft.runTick`、`MinecraftServer.tick`）未来切到非 Forge 环境时也能 mixin 拿到，SPI 不变。

### 决策 10：安全与防滥用

- **C→S/S→C 分片**：core 层强制 32KB 切片（双方向同步上限，避免依赖 Forge `writeVarShort`），`NetChunkFrame(chunkSeq, totalChunks, chunkBytes)`，30s 重组超时丢弃
- **方向校验**：mixin 客户端/服务端各一份，envelope 头 1 byte direction 字段对不上 LOG.warn 丢弃
- **Store 访问控制**：`.accessControl(BiPredicate<EntityPlayerMP, NetStore>)` 玩家初次订阅时调用，false 不进订阅表。`PER_PLAYER` 强制只能订阅自己那份
- **Fetch 限流**：每玩家滑动窗口默认 100 req/s，超额 `NetRateLimitException`
- **Schema 校验**：见决策 8

### 决策 11：注册时机

下游 mod 在 `preInit` 调 `NetService.getInstance().channel/fetch/store(...).register()`，注册时反射 schema 并分配 type id。**所有注册必须在 `FMLPostInitializationEvent` 之前完成**，之后冻结。Qz-UILib 在 `CommonProxy.postInit` 末尾把 `NetService` 切到"已冻结"状态，再注册抛 `IllegalStateException`。

## 关键文件

新建：

| 路径 | 职责 |
|---|---|
| `src/main/java/club/heiqi/uilib/net/api/NetService.java` | 单例门面，建造者根，bootstrap |
| `src/main/java/club/heiqi/uilib/net/api/NetChannel.java`、`NetFetchEndpoint.java`、`NetStore.java`、`NetStoreView.java` | 三层 API |
| `src/main/java/club/heiqi/uilib/net/api/NetReceiveContext.java`、`NetTarget.java`、`NetChannelId.java` 等 | 公共类型 |
| `src/main/java/club/heiqi/uilib/net/codec/NetCodec.java` | 反射 codec 入口 |
| `src/main/java/club/heiqi/uilib/net/codec/FieldLayout.java`、`Varint.java`、`PrimitiveCodecs.java`、`SchemaRegistry.java`、`NetField.java`、`NetTransient.java` | codec 内部 + 注解 |
| `src/main/java/club/heiqi/uilib/net/transport/ITransport.java`、`FrameHandler.java`、`NetReceiveOrigin.java`、`NetSide.java` | SPI |
| `src/main/java/club/heiqi/uilib/net/transport/vanilla/VanillaMixinTransport.java` | 默认适配器实现 |
| `src/main/java/club/heiqi/uilib/net/transport/vanilla/VanillaPacketBuilders.java` | 构造 S3F/C17 包、取 NetworkManager（仅 vanilla 字段） |
| `src/main/java/club/heiqi/uilib/net/transport/vanilla/VanillaInboundDispatcher.java` | 接收 mixin 回调，按 channel 分发到 FrameHandler |
| `src/main/java/club/heiqi/uilib/net/transport/forge/ForgeTransport.java` | 兼容适配器（NetworkRegistry.newChannel） |
| `src/main/java/club/heiqi/uilib/mixin/early/network/MixinNetHandlerPlayClient.java` | 客户端 early mixin，由 `EarlyMixins` 按 client side 返回 |
| `src/main/java/club/heiqi/uilib/mixin/early/network/MixinNetHandlerPlayServer.java` | 服务端/common early mixin，由 `EarlyMixins` 返回 |
| `src/main/java/club/heiqi/uilib/net/core/NetEnvelope.java`、`NetRequestRegistry.java`、`NetChunkAssembler.java`、`MainThreadDispatcher.java`、`SchemaHandshake.java` | 框架内核 |
| `src/main/java/club/heiqi/uilib/net/store/StoreEngine.java`、`FieldDeltaEncoder.java`、`StoreSubscriptionRegistry.java` | Store 引擎 |
| `src/main/java/club/heiqi/uilib/net/client/NetStoreUiBridge.java` | **仅客户端**：Store ↔ ElementNode |
| `src/main/java/club/heiqi/uilib/internal/devtools/NetSelfCheckPage.java` | 自检页 |

修改：

- `EarlyMixins.java`：追加网络 early mixin 名，并按运行侧隔离客户端目标
- `src/main/resources/mixins.qz_uilib.early.json`：保持现有 package，网络 mixin 使用 `network.*` 相对类名由 `EarlyMixins` 动态返回
- `CommonProxy.java` `preInit` 末尾：`NetService.getInstance().bootstrap(new VanillaMixinTransport())`
- `CommonProxy.java` `postInit` 末尾：`NetService.getInstance().freeze()`
- `ClientProxy.java` `preInit`：`NetStoreUiBridge.getInstance().initialize()`
- `ClientProxy.java` `onClientDisconnect`：链上加 `NetService.getInstance().onClientDisconnected()`（这条监听仍走 Forge 事件总线，保留）
- `ClientProxy.java` `onJvmShutdown`：链上加 `NetService.getInstance().shutdown()`
- `DevToolsClientBootstrap.java`：注册 `NetSelfCheckPage`

文档：

- `docs/使用文档/02-控件/网络层入门.md`（新）— Channel / Fetch / Store 三个最小示例
- `docs/使用文档/v4.x-LTS-稳定API清单.md` — 增加"网络层"小节（标记 `ITransport` 为 ⚠️ 不稳定但暴露，便于未来加新适配器）
- `docs/开发者文档/specs/net-codec-wire-format.md`（新）— 反射 codec 帧格式与 schema 协商协议
- `docs/开发者文档/specs/net-vanilla-mixin-strategy.md`（新）— mixin 注入点 SRG/MCP 名清单、生命周期 hook 表、未来跨版本切换备忘
- `docs/开发者文档/specs/net-self-check.md`（新）— 自检场景规格

## 验证

新增 `NetSelfCheckPage` 挂 `/qzuilib test`，五个场景 + 一个适配器对比：

1. **Channel ping**：客户端按钮发 `NetPingMsg(localNanos)`，服务端回 `NetPongMsg(serverTick, echoNanos)`，UI 显示往返延迟与单帧字节数
2. **Fetch slow**：内部 endpoint `qz:devSlow` 接 `Duration`，服务端 sleep 后 reply。`1500ms` 验 future 在 1.5s 完成；`10s` 同时点取消 → `cancel(true)` → future cancelled
3. **Store counter**：内部 `NetStore<DevCounter>` GLOBAL，服务端 1Hz 自增，客户端 `view.bind(elem, renderer)` 写文本节点，UI 旁显示**字段增量字节数** vs **全量快照字节数**
4. **断连恢复**：客户端登出再登入，store 重新 snapshot、所有 pending future 以 `NetDisconnectedException` 终结
5. **C→S/S→C 分片**：客户端发 100KB 字符串 `NetBigMsg`，core 自动切 4 片，服务端拼回校验内容；反向同
6. **适配器一致性**：自检页有按钮"切换到 Forge 适配器并重启"，重启后跑同样 5 场景，结果应一致（验证 `ITransport` SPI 站得住）

构建验证：
- `gradlew build` 必须通过
- `runClient21`（lwjgl3ify+JBR 21）人工跑一遍 6 场景
- 多人服联机用 `runServer` + `runClient21` 两实例，重点验证 mixin 在 dedicated server 上的加载（确保 `EarlyMixins` 不会在服务端返回 `MixinNetHandlerPlayClient`）

带宽对比基准（场景 1 与 3 显示）：

- 目标 A：Channel ping 单帧 ≤ 等价 SimpleNetworkWrapper IMessage 朴素实现帧
- 目标 B：Store 字段增量帧 ≤ 50% 全量快照帧
- 目标 C：Vanilla 适配器与 Forge 适配器单帧字节差 ≤ 16 byte（Forge `FMLProxyPacket` 包装开销）

## 后续待解的边界问题

实现期需要进一步澄清的细节，先记录在此：

- **复合泛型字段的反射**：`Map<String, List<Foo>>` 这种嵌套泛型 schema 反射如何稳定标识，是否需要在 `@NetField` 上显式声明
- **循环引用**：POJO 自指或互指时反射 codec 是否检测并报错，还是支持循环展开
- **proxy 服务器**：BungeeCord/Velocity 之类代理是否会无脑转发 `qz:0` channel，需要联机实测
- **schema 演化**：`@NetField(since=1)` 的语义在 LTS 期间需要进一步规格化
- **字段顺序与字节码兼容**：`Comparator.comparing(Field::getName)` 排序后字段位置不依赖编译器顺序，但子类继承时的字段顺序仍要规格化（先父后子？fqn 字典序？）

这些问题在第一版实现前需要逐项落到 `net-codec-wire-format.md` 与 `net-vanilla-mixin-strategy.md` 两份配套规格里。
