# 决策：远程 UI Runtime 与 Lease 协议

## 背景

远程页面与远程 HUD 当前基于 Qz 网络层 `Channel + Stream` 实现。通用网络层的职责边界已经比较稳定：`NetService` 负责 route/key、content type、headers、body、Envelope、分片、Stream 进度/取消、Store 同步和传输适配，不理解远程 UI 业务语义。

远程 UI 业务侧已有 `RemoteHtmlSessionGateway` 共享 HTML session、HTML bytes、SHA-256、TTL、Stream 响应和玩家归属校验。页面与 HUD 分别在 `RemoteDocumentPages` / `RemoteDocumentClientBridge`、`RemoteHudOverlays` / `RemoteHudOverlayClientBridge` 中维护 open、submit、dismiss、expired、generation、overlayId 映射和客户端挂载生命周期。

第 5 到第 7 轮生产代码审查暴露出同一类结构风险：同一个远程 UI 运行时真相被多个路径各自维护，导致旧 session dismiss 误关新 HUD、旧 stream 回调覆盖新页面、TTL 过期后客户端仍可交互、延迟 submit event 关闭新 HUD、页面手动关闭后旧 expired 重新弹错误页等问题。现有修复已经让固定 10 分钟 TTL、非空 session 精确匹配、页面 generation guard 等口径自洽，但这些仍属于分散补强，不足以支撑长驻远程 UI、keepalive/renew、断线重连和跨维度/跨服状态同步。

## 候选方案

- 保持现状，只继续在 `RemoteHtmlSessionGateway` 和页面/HUD bridge 上修补边界。
- 在现有远程 HTML session 上追加 keepalive/renew 字段，把 TTL 从固定过期改成隐式续期。
- 新增内部 Remote UI Runtime 与显式 Lease 协议，把 session、surface、revision、asset、lease、submit、close 等概念分层建模；页面与 HUD 只是同一协议的 surface adapter。

## 最终选择

选择新增内部 Remote UI Runtime 与显式 Lease 协议，作为后续生产级重构目标。

`NetService` 继续保持通用网络基础设施定位，不新增远程 UI 专属语义。`RemoteHtmlSessionGateway` 不再继续膨胀为长期 runtime；后续应逐步拆成远程 UI session runtime、HTML asset store、protocol DTO、client runtime 与 page/HUD surface adapter。现有公开 API 默认行为保持不变：普通远程页面/HUD 仍是固定 10 分钟有效期，不做隐式续期；长驻、renew、resume 必须显式 opt-in。

## 选择原因

- 生产级远程 UI 需要显式状态机，而不是依赖 `sessionId`、客户端本地 generation、overlayId 映射和空 session sentinel 拼接语义。
- keepalive/renew 属于远程 UI 会话协议，不属于通用 `NetService` 能力；塞进网络层会污染基础设施边界。
- HTML 拉取资产生命周期与后续交互 lease 生命周期应拆开。HTML Stream 适合传输大内容，不应继续等同于长期交互 session。
- 页面与 HUD 的差异主要是 surface 挂载和关闭表现，不应各自重复 session、Stream、submit、expired、防旧回调逻辑。
- 显式 `contentRevision` 可以替代只有客户端知道的 generation，服务端和客户端都能判断旧 stream、旧 submit、旧 close 是否仍有效。
- 显式 `closeScope` 可以替代“空 `sessionId` 表示强制 overlayId 关闭”的隐式 sentinel，降低未来协议扩展风险。

## 目标分层

| 层 | 职责 | 不承担 |
|---|---|---|
| Net 基础设施 | Channel / Stream / Store / Envelope / 分片 / 进度 / 取消 / targetSide | 远程 UI session 或 UI 生命周期 |
| Remote UI Protocol | open、close、expired、submit、renew、resume、ack 的 wire contract | 直接创建 UI 节点 |
| Remote UI Session Runtime | session、lease、revision、surface、owner、状态机、过期扫描 | HUD 布局或 screen 打开 |
| Remote HTML Asset Store | HTML bytes、sha256、assetId、Stream 拉取、资源策略 | 长期交互 session |
| Surface Adapter | Page screen 与 HUD overlay 的挂载、关闭、错误显示、输入接入 | 判断 session 是否有效 |
| State Sync | 长驻状态、跨维度状态、恢复状态 | HTML Stream session 存储 |

## 核心身份模型

- `sessionId`：服务端远程 UI 会话，承载权限、handler、lease 和状态机。
- `surfaceType`：`PAGE` 或 `HUD`。
- `surfaceId`：UI 表面标识；HUD 映射现有 `overlayId`，页面可使用 `primary` 或内部生成 id。
- `contentRevision`：服务端单调递增版本，所有 stream、submit、close、expired 都必须携带或校验。
- `assetId`：HTML 内容资产 id，Stream 拉取使用 `sessionId + assetId + contentRevision`，避免 `sessionId` 同时代表会话和 HTML bytes。
- `pageId` / `overlayId`：保留为业务字段，不再承担协议层主身份职责。

## Wire 协议方向

控制面继续走 Channel，HTML 大内容继续走 Stream。

| 方向 | 操作 | 说明 |
|---|---|---|
| S2C | `OPEN_SURFACE` | 下发 `sessionId/surfaceType/surfaceId/contentRevision/assetId/sha256/htmlBytes/leaseExpiresAt` |
| C2S | `FETCH_HTML` | Stream 请求携带 `sessionId/assetId/contentRevision` |
| C2S | `MOUNT_ACK` | 客户端成功解析并挂载后回执 |
| C2S | `MOUNT_ERROR` | 客户端校验、解析或挂载失败时回传 |
| C2S | `SUBMIT` | 表单提交携带 `sessionId/surfaceId/contentRevision/pageId/action/formId/values/submitId` |
| S2C | `CLOSE_SURFACE` | 显式关闭，携带 `closeScope` 和 `reason` |
| S2C | `SESSION_EXPIRED` | lease 失效，客户端按 surface adapter 显示错误或关闭 HUD |
| C2S | `RENEW` | 仅 renewable session 使用，不做隐式续期 |
| S2C | `RENEW_ACK` / `RENEW_DENIED` | 返回新 lease 或拒绝原因 |
| C2S | `RESUME` | 仅未来 resumable session 使用 |

`closeScope` 建议至少包含：

- `SESSION`：必须匹配 `sessionId` 与当前 `contentRevision`，适合延迟事件对象和 session-scoped 关闭。
- `SURFACE`：关闭当前 `surfaceId`，适合公开管理入口 `RemoteHudOverlays.dismiss(player, overlayId)`。
- `PLAYER_ALL`：预留给未来关闭玩家所有远程 UI。

## Lease 策略

远程 UI 生命周期应拆分为：

- Asset TTL：HTML bytes 可被 Stream 拉取的时间，拉取完成后可提前释放。
- Interaction Lease：表单提交、关闭、renew 的会话有效期。
- Max Lifetime：防止 renewable session 永久泄漏。
- Idle Timeout：长驻 UI 无交互时自动失效。

默认策略保持当前行为：固定 10 分钟，不做 renew。未来可显式增加：

- `LeasePolicy.FIXED`
- `LeasePolicy.RENEWABLE`
- `LeasePolicy.RESUMABLE`

普通远程页面/HUD 不允许隐式续期。长驻远程 UI 必须由服务端 builder 或内部入口显式 opt-in，并在 wire capabilities 中体现。

## 状态机

服务端 session 建议状态：

```text
CREATED -> OFFER_SENT -> ACTIVE -> CLOSING -> CLOSED
                    \-> EXPIRED
                    \-> FAILED
```

客户端 surface 建议状态：

```text
PENDING_OPEN -> FETCHING -> MOUNTING -> ACTIVE -> CLOSING -> CLOSED
                         \-> STALE
                         \-> ERROR
```

所有异步落地必须校验 `sessionId + surfaceId + contentRevision + localMountToken`。`localMountToken` 可作为客户端内部 generation 保留，但 wire 层必须以服务端 `contentRevision` 为跨端版本事实。

## 建议内部类边界

- `RemoteUiServerRuntime`：注册远程 UI 控制通道，统一调度 open / submit / close / renew。
- `RemoteUiSessionManager`：管理 session、lease、revision、过期扫描和关闭。
- `RemoteUiAssetStore`：保存 HTML bytes、SHA-256、assetId 和 Stream 响应。
- `RemoteUiProtocol`：DTO、JSON 编解码、协议版本和字段校验。
- `RemoteUiSurfaceAdapter`：页面/HUD 差异适配接口。
- `RemotePageSurfaceAdapter`：页面 screen 打开、错误页和关闭 lifecycle。
- `RemoteHudSurfaceAdapter`：HUD overlay 打开、替换、dismiss 和 duration。
- `RemoteUiClientRuntime`：客户端 pending / active registry、stream guard 和 mount ack。
- `RemoteUiSubmitDispatcher`：构造现有 `RemoteDocumentSubmitEvent` / `RemoteHudSubmitEvent`，保持公开事件模型。

现有 `RemoteDocumentPages` 和 `RemoteHudOverlays` 应最终退化为薄 facade，保留公开 API 和文档承诺。

## 迁移路径

1. 先新增内部 protocol DTO、身份模型、状态机与纯 JVM 测试，不改公开 API。
2. 抽 `RemoteUiAssetStore`，让 HTML Stream 请求从仅 `sessionId` 升级为 `sessionId + assetId + contentRevision`。
3. 抽 `RemoteUiSessionManager`，统一页面/HUD 的 session 创建、过期、关闭、提交校验。
4. 抽 `RemoteUiClientRuntime`，页面/HUD 客户端 bridge 只负责 surface adapter 挂载。
5. 内部 wire 增加 `protocolVersion` 和 feature，例如 `remote-ui-lease-v1`。
6. 默认仍按当前固定 TTL 行为运行，完成等价迁移后再增加显式 renewable / resumable 能力。
7. 长驻状态、跨维度状态和恢复状态优先接 `NetStore` / `Channel`，不塞回 HTML Stream session。

## 后续注意事项

- 不要把 keepalive / renew 加进 `NetService`。
- 不要继续扩大 `RemoteHtmlSessionGateway` 的职责；它只能作为当前过渡实现的公共网关。
- 不要用 `overlayId` 或 `pageId` 替代 `sessionId + surfaceId + contentRevision` 进行协议归属判断。
- 不要用空 `sessionId` 表达强制关闭；新增协议应使用显式 `closeScope`。
- 不要让 submit、stream 成功或任意用户交互自动续期；续期必须是显式协议。
- 跨服同步不属于当前 custom payload 连接能力，必须依赖上层服务端集群状态或外部后端；Qz 当前网络层只负责当前连接内通信。

## 后续重构提示词

```text
请先阅读 AGENTS.md、docs/AI记忆文档.md、docs/记忆/决策/DECISION-20260609-remote-ui-runtime-lease-protocol.md、docs/记忆/决策/DECISION-20260608-remote-html-session-ttl.md，以及远程页面/HUD 使用文档和第 5-7 轮审查记录。

任务目标：按生产级工程方向重构远程 UI 网络/会话层，不做短期补丁。以“Remote UI Runtime + Lease Protocol”为目标模型，在不破坏现有公开 API 的前提下，先搭建内部协议、状态机、asset store、session manager 和客户端 runtime 骨架，再逐步把 RemoteDocumentPages / RemoteHudOverlays 收敛为薄 facade。

硬性边界：
- 不把 keepalive / renew / remote UI 业务语义加入 NetService。
- 不继续扩大 RemoteHtmlSessionGateway 职责；它只能作为过渡参考或待替换的公共网关。
- 不改变 RemoteDocumentPages.open(...)、RemoteHudOverlays.open(...)、dismiss(...)、dismissSession(...) 等公开 API 默认语义。
- 默认仍保持固定 10 分钟 TTL 且不隐式续期；renew/resume 只做显式 opt-in 设计或后续阶段实现。
- 所有异步落地必须校验 sessionId + surfaceId + contentRevision + localMountToken。
- close 不再依赖空 sessionId sentinel，内部新协议使用显式 closeScope。

建议第一阶段只做等价重构：
1. 新增 RemoteUiProtocol DTO / enum / 字段校验与 JSON 编解码。
2. 新增 RemoteUiAssetStore，负责 assetId、HTML bytes、sha256、Stream 响应数据。
3. 新增 RemoteUiSessionManager，负责 session、surface、contentRevision、lease、状态机、过期清理。
4. 新增 RemoteUiServerRuntime / RemoteUiClientRuntime 骨架，页面/HUD 先通过 adapter 接入。
5. 保留现有行为测试，并新增 session manager、asset store、stale stream、stale submit、session-scoped close、surface-scoped close 的纯 JVM 测试。

验证要求：至少运行 git diff --check、compileJava、club.heiqi.uilib.ui.remote.* 相关测试；如果改动影响 NetService 或 Stream，再运行网络层相关测试。
```
