# 全项目生产代码第九轮审查

## 审查范围

- 目标：继续只读审查 `src/main/java` 生产代码，优先定位会导致行为错误、生命周期泄漏、状态不一致、异步竞态、主线程边界、资源泄漏、协议边界污染或测试缺口的真实问题。
- 重点范围：配置同步服务端会话、远程配置页面、网络 Store per-player 状态、vanilla/Forge 传输握手、`ui/input`、`ui/host` 与 `internal/devtools` 高风险入口。
- 已排除重复项：第三至第八轮已记录或已修的 text input cancel、HUD top-layer、stylesheet 失效、select detach、远程 UI runtime、lease protocol、timeout tick、远程 UI TTL 主动清扫和新 lease decode 显式字段校验等主线问题。

## 结论摘要

- 发现 2 个需要修复或明确收口的问题，均为 P2。
- P2：配置同步服务端会话与 per-player Store 状态没有跟随玩家离线、页面关闭或远程页面 TTL 清理，旧 session 与旧玩家状态会长期残留。
- P2：Forge/FML 回退传输没有发送 `META` capability handshake，切换到 `netTransport=forge` 后 capability-gated 功能会保持不可用。
- 额外抽查 `ui/input`、`ui/host`、`internal/devtools` 未形成新的可坐实 production finding。

## Findings

### P2：配置同步服务端会话与 per-player Store 状态没有生命周期清理

- 类型：生命周期泄漏 / 状态残留 / 测试缺口
- 位置：`src/main/java/club/heiqi/uilib/config/ConfigTemplateSyncManager.java:42`
- 位置：`src/main/java/club/heiqi/uilib/config/ConfigTemplateSyncManager.java:350`
- 位置：`src/main/java/club/heiqi/uilib/config/ConfigTemplateSyncManager.java:448`
- 位置：`src/main/java/club/heiqi/uilib/config/ConfigTemplateSyncManager.java:499`
- 位置：`src/main/java/club/heiqi/uilib/config/RemoteConfigDocumentPages.java:35`
- 位置：`src/main/java/club/heiqi/uilib/config/RemoteConfigDocumentPages.java:48`
- 位置：`src/main/java/club/heiqi/uilib/net/api/NetStore.java:21`
- 位置：`src/main/java/club/heiqi/uilib/net/api/NetStore.java:90`
- 位置：`src/main/java/club/heiqi/uilib/net/transport/vanilla/VanillaConnectionLifecycle.java:105`
- 位置：`src/main/java/club/heiqi/uilib/net/transport/vanilla/VanillaConnectionLifecycle.java:157`
- 问题现象：`ConfigTemplateSyncManager` 用全局 `sessions` 保存服务端配置会话，`openServerSession(...)` 只在同一玩家再次打开同一个 `screenId` 时通过 `invalidateExistingSession(...)` 被动删除旧会话；`publishState(...)` 又把 session snapshot 写入 `NetStore.setForPlayer(...)`。但生产代码里没有玩家离线、远程配置页关闭、远程页面 TTL 过期或本地配置模板页关闭时的服务端清理入口，也没有 `NetStore` per-player 状态移除 API。
- 可复现或可推导路径：玩家打开远程配置同步后，服务端创建 `ConfigTemplateRemoteSession` 并写入 per-player Store；玩家直接断开连接，`VanillaConnectionLifecycle.onServerDisconnected(...)` 和 `onServerPlayerLeft(...)` 只移除 handshake manager，没有通知配置同步管理器；旧 `ConfigTemplateRemoteSession` 继续持有 `ownerPlayer`、authoritative/draft configuration 和 state，`NetStore.playerStates` 继续以旧 player 对象为 key 保存 snapshot。远程配置页面走 `RemoteDocumentPages.open(...)` 后，远程页面自身 TTL 只会清理 remote page session 并通知客户端 expired，不会回调清理这个独立的配置同步 session。
- 影响范围：长服或多人使用配置同步时会累积旧 `EntityPlayerMP` 引用、配置草稿和 per-player NetBody；玩家关闭页面或离线后服务端仍保留不可见状态，后续排查 Store 状态和配置同步 session 时会看到与真实在线状态不一致的数据。当前客户端 `ConfigTemplateRemoteSyncController.onScreenClosed(...)` 只取消本地订阅并清本地字段，不会释放服务端 session。
- 建议修复方向：为配置同步增加显式服务端生命周期入口，例如 `closeServerSession(...)`、`onServerPlayerLeft(...)` 或按 player 清理所有 session；同时为 `NetStore` 补 per-player remove/reset 语义，清理时删除旧 player snapshot 或恢复默认初始状态。`RemoteConfigDocumentPages` 应把配置 session 与 remote page session 的关闭/过期生命周期绑定，至少在 remote page session 被移除时同步清理配置 session。
- 建议测试：补配置同步服务端测试，覆盖玩家离线后 session map 与 per-player Store 被清理；补远程配置页关闭或 TTL 过期后配置 session 不再可保存/变更；补重新登录新 player 对象不会看到旧 player snapshot。

### P2：Forge 回退传输没有发送 META capability handshake

- 类型：协议兼容性 bug / capability-gated 功能失效 / 测试缺口
- 位置：`src/main/java/club/heiqi/uilib/Config.java:51`
- 位置：`src/main/java/club/heiqi/uilib/CommonProxy.java:35`
- 位置：`src/main/java/club/heiqi/uilib/net/transport/NetTransportFactory.java:25`
- 位置：`src/main/java/club/heiqi/uilib/net/api/NetService.java:295`
- 位置：`src/main/java/club/heiqi/uilib/net/api/NetService.java:304`
- 位置：`src/main/java/club/heiqi/uilib/net/api/NetService.java:383`
- 位置：`src/main/java/club/heiqi/uilib/net/api/NetEnvelopeDispatcher.java:46`
- 位置：`src/main/java/club/heiqi/uilib/net/transport/vanilla/VanillaConnectionLifecycle.java:69`
- 位置：`src/main/java/club/heiqi/uilib/net/transport/vanilla/VanillaConnectionLifecycle.java:79`
- 位置：`src/main/java/club/heiqi/uilib/net/transport/forge/ForgeTransport.java:42`
- 位置：`src/main/java/club/heiqi/uilib/config/ConfigTemplateRemoteSyncController.java:51`
- 问题现象：配置和文档明确允许用 `netTransport=forge` 或 `-Dqzuilib.net.transport=forge` 切到 Forge/FML 回退适配器，但 capability handshake 的生产调用点只在 `VanillaConnectionLifecycle`。`ForgeTransport.bootstrap(...)` 只注册 FML channel，发送方法也只是普通 `FMLProxyPacket` 分发，没有客户端连接建立、服务端玩家加入时发送 `NetEnvelope.Kind.META` 的等价 lifecycle。
- 可复现或可推导路径：切换到 `netTransport=forge` 后，`CommonProxy.preInit(...)` 会启动 `ForgeTransport`。连接建立时不会调用 `NetService.sendCapabilityHandshakeToServer()` 或 `sendCapabilityHandshakeToPlayer(...)`。客户端因此不会在 `NetEnvelopeDispatcher` 收到服务端 `META`，`ConfigTemplateSyncManager.clientRemoteAvailable` 保持 false，`ConfigTemplateRemoteSyncController.onScreenOpened(...)` 会直接返回并继续本地草稿模式。
- 影响范围：Forge 回退路径仍可能承载手动发起的普通 Channel/Fetch/Stream，但所有依赖 `META` advertised capability 的功能会被静默关闭。当前最直接受影响的是 Forge 配置模板远程同步；未来如果更多能力依赖 META 协商，也会在 Forge 回退路径上表现为“传输可选但能力不可用”。
- 建议修复方向：给 Forge 回退适配器补等价连接 lifecycle bridge，在 FML 客户端连接完成后发送 client-to-server `META`，在服务端解析到玩家连接后按服务端主线程发送 server-to-client `META`；同时保证断连时清理一次性发送标记，语义与 vanilla lifecycle 对齐。
- 建议测试：补 Forge transport 或独立 lifecycle 测试，断言选择 forge 后客户端和服务端连接建立各发送一次 `META`；补配置同步 gate 测试，模拟收到服务端 `META` 前不可用、Forge lifecycle 后可用。

## 补查未形成 finding 的范围

- `ui/input`：抽查 `UiInputService`、`UiHostInputCoordinator`、`UiInputRouter`、`UiNativeTextInputInspector`、`UiKeyboardCaptureState`。即时键盘去重、焦点仍在可交互树内的校验、screen/HUD 文本输入请求释放和反射扫描去重均有对应收口，未形成新 finding。
- `ui/host`：抽查 `DocumentHostInteractionSession`、`DocumentHostRenderSupport` 与 screen/HUD host 调用方。资源关闭、交互状态清理和 deferred pass 一次性 claim 语义基本闭合，未形成新 finding。
- `internal/devtools`：抽查 `NetSelfCheckRunner`、`NetSelfCheckRegistry`、`NetRuntimeSelfChecks`、`UiTestDocumentPageController`。自检 pending map 有 timeout/complete 清理，test 页面切换会清 top-layer；未看到足以写入生产代码 finding 的新问题。
- `NetStoreUiBridge.bind(...)` 只有订阅没有解绑返回值仍是低优先级可疑点，但当前生产使用面很窄，本轮不作为 finding。

## 验证

- 本轮为只读审查，未修改生产代码，未运行 Gradle 测试。
- 文档变更已执行 `git diff --check`，未发现空白错误。
- 审查依据：CodeGraph 符号定位与调用关系、Grep 全局调用点搜索、源码读取、现有测试覆盖搜索、最近第三至第八轮审查报告。
