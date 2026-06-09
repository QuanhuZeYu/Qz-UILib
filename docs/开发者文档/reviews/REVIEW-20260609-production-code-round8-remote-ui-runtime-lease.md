# 全项目生产代码第八轮审查：远程 UI Runtime + Lease Protocol

## 审查范围

- 目标：从生产级工程质量角度只读审查当前项目代码，优先定位会导致行为错误、状态不一致、生命周期泄漏、异步竞态、协议边界污染、职责膨胀、测试覆盖缺口或未来维护风险的真实问题。
- 重点范围：`src/main/java` 生产代码，尤其是 `club.heiqi.uilib.ui.remote.RemoteUiProtocol`、`RemoteUiAssetStore`、`RemoteUiSessionManager`、`RemoteUiServerRuntime`、`RemoteUiClientRuntime`、`RemoteDocumentPages`、`RemoteDocumentClientBridge`、`RemoteHudOverlays`、`RemoteHudOverlayClientBridge`。
- 抽查范围：`net` 层通用基础设施边界、Stream / Channel 生命周期、HUD 宿主断连清理、远程 HTML 表单 submit、top-layer / HUD 生命周期相关边界。
- 已排除重复项：第三至第七轮已修的 text input cancel、HUD top-layer 命中、stylesheet 失效、select detach、HUD display:none、popup hover、远程 HUD submit dismiss、页面旧 stream、页面手动关闭后 expired、TTL 过期可见通知、runtime transform 与 scroll suppression 等历史 findings。

## 结论摘要

- 发现 4 个需要修复或明确收口的问题：2 个 P1、2 个 P2。
- P1：Net Stream/Fetch 超时只在下一次入站包到来时触发，网络空闲或远端无响应时 future 可能永久 pending。
- P1：远程 UI 固定 TTL 没有主动 lease 清扫，过期可见关闭/错误语义只在后续 open/submit/stream 路径上被动触发。
- P2：远程 UI 客户端异步失败与旧回调路径没有完整终止本地 mount，存在 pending 状态泄漏和失败 screen lifecycle 不一致风险。
- P2：新 lease 协议字段在 decode 阶段被补默认值，削弱 `surfaceId` / `contentRevision` / `closeScope` 显式校验边界。
- 未发现 `NetService` 被加入 keepalive / renew / remote UI 业务语义；固定 10 分钟 TTL 与不隐式续期仍是当前有意边界。

## Findings

### P1：Net Stream/Fetch 超时只在下一次入站包到来时才会触发

- 类型：真实 bug / 生命周期泄漏
- 位置：`src/main/java/club/heiqi/uilib/net/api/NetStreamDownloadRegistry.java:51`
- 位置：`src/main/java/club/heiqi/uilib/net/api/NetStreamDownloadRegistry.java:262`
- 位置：`src/main/java/club/heiqi/uilib/net/core/NetRequestRegistry.java:64`
- 位置：`src/main/java/club/heiqi/uilib/net/api/NetEnvelopeDispatcher.java:45`
- 问题现象：Stream/Future 记录了 deadline，也有 `expireTimedOut()`，但超时清理只在 `NetEnvelopeDispatcher.dispatch(...)` 收到入站包时被调用。网络空闲、服务端无响应或响应丢失时，pending Stream/Fetch 不会按 timeout 自行失败。
- 可复现或可推导路径：远程页面/HUD 调用 `RemoteDocumentPages.callPageStream(...)` 或 `RemoteHudOverlays.callOverlayStream(...)` 后，如果服务端没有返回 `STREAM_START` / `STREAM_ERROR`，且之后没有任何入站网络帧，`NetStreamCall.future()` 会一直 pending；远程页面停留 loading，HUD pending open 也不会按 60 秒 timeout 收口。
- 影响范围：所有 `NetStreamEndpoint` 和 `NetFetchEndpoint` 消费者，不限远程 UI；会导致 loading 卡死、pending map 泄漏、调用方误以为 timeout 已生效。
- 建议修复方向：在通用 net 层增加非业务语义的 tick/scheduled timeout 驱动，例如由已有 Forge tick 桥调用 `NetService` 的通用 `tickTimeouts()`，只处理 request/stream deadline，不加入 keepalive / renew / remote UI 语义。
- 建议测试：补 Net 层单元测试或集成测试，覆盖“没有任何后续入站包时 Stream/Fetch 仍按 timeout 失败”。

### P1：远程 UI TTL 过期没有主动清扫，固定 TTL 只在后续操作时才可见

- 类型：生产风险 / 生命周期一致性
- 位置：`src/main/java/club/heiqi/uilib/ui/remote/RemoteDocumentPages.java:136`
- 位置：`src/main/java/club/heiqi/uilib/ui/remote/RemoteDocumentPages.java:252`
- 位置：`src/main/java/club/heiqi/uilib/ui/remote/RemoteDocumentPages.java:314`
- 位置：`src/main/java/club/heiqi/uilib/ui/remote/RemoteHudOverlays.java:141`
- 位置：`src/main/java/club/heiqi/uilib/ui/remote/RemoteHudOverlays.java:410`
- 位置：`src/main/java/club/heiqi/uilib/ui/remote/RemoteHudOverlays.java:503`
- 问题现象：页面/HUD session 过期清理只在 `open(...)`、`submit`、stream validate 路径触发；没有远程 UI 自己的 lease sweeper。已经打开的远程页面或 sticky HUD dialog 超过 10 分钟后，如果用户不提交、服务端也不再打开新的远程 UI，客户端不会收到 expired/dismiss。
- 可复现或可推导路径：服务端打开一个远程 HUD dialog，客户端成功拉取并显示；等待超过 `DEFAULT_SESSION_TTL_MILLIS`，不做任何提交，也没有其他远程 UI 操作；服务端 session 和 HTML asset 仍留在 manager/store 中，客户端 HUD 仍可见，直到下一次提交或下一次远程 UI open 才触发清理。
- 影响范围：固定 10 分钟 TTL 的“过期可见语义”落地不完整；用户会继续操作已经无服务端处理能力的 UI，服务端也会保留过期 session/asset 到下一次相关操作。
- 建议修复方向：在 `ui.remote` 内部增加明确的 lease cleanup 调度器或 server tick hook，调用现有 `cleanupExpiredSessions(...)` 并复用 page expired / HUD session-scoped dismiss 通知；不要把 keepalive / renew / remote UI 语义放进 `NetService`，也不要隐式续期。
- 建议测试：补页面和 HUD 测试，覆盖“无后续 submit/open/stream 时，TTL 到期也会主动通知页面或关闭 HUD”。

### P2：客户端远程 UI 异步失败/旧回调路径没有完整终止本地 mount

- 类型：生产风险 / 异步竞态 / 状态泄漏
- 位置：`src/main/java/club/heiqi/uilib/ui/remote/RemoteDocumentClientBridge.java:85`
- 位置：`src/main/java/club/heiqi/uilib/ui/remote/RemoteDocumentClientBridge.java:88`
- 位置：`src/main/java/club/heiqi/uilib/ui/remote/RemoteDocumentClientBridge.java:99`
- 位置：`src/main/java/club/heiqi/uilib/ui/remote/RemoteDocumentClientBridge.java:197`
- 位置：`src/main/java/club/heiqi/uilib/ui/remote/RemoteHudOverlayClientBridge.java:156`
- 位置：`src/main/java/club/heiqi/uilib/ui/remote/RemoteHudOverlayClientBridge.java:164`
- 位置：`src/main/java/club/heiqi/uilib/ui/remote/RemoteHudOverlayClientBridge.java:175`
- 问题现象：`beginOpen(...)` 后，旧回调或失败回调经常直接 `return` 或打开错误 UI，但没有把对应 `sessionId + surfaceId + contentRevision + localMountToken` 的 pending mount 标记为 terminal/stale。页面错误屏还是无 session lifecycle 的 `openErrorScreen(null, 0L, ...)`，不会清理当前 offer。
- 可复现或可推导路径：页面 S1 开始拉取后，页面 S2 成为当前页面；S1 stream 后到达时 `!isCurrentOffer(...)` 直接返回，`RemoteUiClientRuntime.pendingBySession` 中的 S1 没有被移除。另一路径是当前页面下载失败或校验失败后打开 error screen，但 `currentSessionId/currentLocalMountToken` 仍指向失败 session，后续旧 expired 通知仍可能再次落地。
- 影响范围：本地 runtime pending 状态泄漏，长时间多次失败或快速替换页面会累积旧 mount；失败后的页面 session 状态与实际 screen 生命周期不一致，旧通知仍可能影响用户已经关闭的错误页。
- 建议修复方向：为 `RemoteUiClientRuntime` 增加显式“丢弃 pending/失败 terminalize”能力，失败、校验异常、旧回调都必须按 `sessionId + surfaceId + contentRevision + localMountToken` 收口；页面错误屏打开前应先清掉仍匹配的当前 offer，或让错误屏也绑定可清理 lifecycle。
- 建议测试：补旧 stream 不覆盖新页面同时旧 pending 被清理的断言，并补失败错误页关闭后旧 expired 不再重新落地的测试。

### P2：新 lease 协议字段在 decode 阶段被补默认值，削弱显式校验

- 类型：协议边界污染 / 测试缺口 / 未来维护风险
- 位置：`src/main/java/club/heiqi/uilib/ui/remote/RemoteDocumentPages.java:181`
- 位置：`src/main/java/club/heiqi/uilib/ui/remote/RemoteDocumentPages.java:373`
- 位置：`src/main/java/club/heiqi/uilib/ui/remote/RemoteDocumentPages.java:394`
- 位置：`src/main/java/club/heiqi/uilib/ui/remote/RemoteHudOverlays.java:332`
- 位置：`src/main/java/club/heiqi/uilib/ui/remote/RemoteHudOverlays.java:346`
- 位置：`src/main/java/club/heiqi/uilib/ui/remote/RemoteHudOverlays.java:577`
- 位置：`src/main/java/club/heiqi/uilib/ui/remote/RemoteHudOverlays.java:598`
- 问题现象：`decodeSubmitPayload(...)` / `decodeDismissPayload(...)` 在调用 `RemoteUiProtocol.validateSubmit(...)` / `validateClose(...)` 前，会把缺失的 `surfaceType`、`surfaceId`、`contentRevision` 补成默认值。结果是新协议要求的显式字段缺失不会被 validate 捕获。
- 可复现或可推导路径：客户端发送只包含 `sessionId/pageId/values` 的 page submit，`normalizeSubmitPayload(...)` 会补 `PAGE + primary + 1`；当前服务端 session revision 固定为 1 时，该 malformed/旧格式 payload 仍可能通过。HUD session dismiss 如果带 `sessionId` 但缺少 `contentRevision`，也会被补成 1 后进入 session 精确关闭路径。
- 影响范围：协议边界不够硬，测试很容易在缺字段情况下误通过；未来一旦 `contentRevision` 真正递增或同 surface 多 revision 并存，这种默认补值会变成旧状态落地入口。
- 建议修复方向：新 lease protocol 的 submit/session-close/open/expired 解码应先校验显式字段，缺少 `surfaceId/contentRevision` 直接拒绝；如确有旧 wire 兼容需求，应隔离成显式 legacy adapter，不应污染 `RemoteUiProtocol` 的新协议校验路径。
- 建议测试：补 submit、session close、expired payload 缺失 `surfaceId/contentRevision` 必须拒绝的协议测试。

## 补查未形成 finding 的范围

- `NetService` 未发现 keepalive / renew / remote UI lease 业务语义污染；当前 P1 timeout 问题属于通用基础设施生命周期缺口。
- 固定 10 分钟 TTL、不隐式续期本身是当前有意能力边界；本轮只指出过期清理触发时机不完整。
- HUD 断连清理已有 `ClientProxy.onClientDisconnect(...)` 调用 `RemoteHudOverlayClientBridge.clearAll()` 与 `UiHudDocumentHost.clearAllRegistrations()`；本轮未发现旧 HUD 跨世界残留的新问题。
- close 新协议已使用显式 `closeScope`；问题不在空 session sentinel 依赖，而在 decode 预填默认值弱化了显式字段校验。

## 验证

- 本轮为只读审查，未修改生产代码，未运行 Gradle 测试。
- 审查依据：源码读取、最近第六/第七轮审查记录、当前记忆文档与远程 UI lease protocol 边界要求。
