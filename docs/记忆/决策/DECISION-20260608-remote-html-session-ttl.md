# 决策：远程 HTML session TTL 统一覆盖拉取与交互

## 背景

远程页面与远程 HUD 共用 `RemoteHtmlSessionGateway` 保存服务端 HTML session。原先 10 分钟 TTL 既会影响 HTML stream 拉取，也会影响后续表单提交，但客户端页面/HUD 已渲染后仍可交互，过期提交只在服务端日志中丢弃，用户看不到失败语义。

## 候选方案

- 将 TTL 仅解释为 HTML 拉取窗口，stream 成功后另建长期交互 session。
- 延长或关闭 TTL，让客户端 UI 生命周期决定服务端 session 存活。
- 保持一个 session，但明确 TTL 同时覆盖 HTML 拉取与交互提交，过期时服务端主动通知客户端失败或关闭。

## 最终选择

保持一个远程 HTML session，`RemoteHtmlSessionGateway.DEFAULT_SESSION_TTL_MILLIS` 同时覆盖 HTML 拉取与后续表单提交。过期清理必须产生客户端可见语义：远程页面发送 `remote_page_expired` 并打开错误页；远程 HUD 发送带 `sessionId` 的 dismiss，关闭对应 session 的浮层。

## 选择原因

- 最小修复，不引入第二套交互 session registry。
- 保留现有 sessionId 作为页面/HUD 表单提交的安全归属校验。
- 让过期从“服务端静默丢弃”变为“客户端可见失败/关闭”，避免用户继续操作已经无服务端处理能力的 UI。

## 影响范围

- `RemoteDocumentPages` 新增客户端失效通知通道 `remote_page_expired`。
- `RemoteHudOverlays` 在过期清理时发送 session-scoped dismiss。
- `RemoteHudOverlayClientBridge` 与服务端 dismiss 路径均要求非空 `sessionId` 只作用于同 session，只有空 session 才允许按 `overlayId` 强制回退。
- `RemoteHudSubmitEvent.dismiss()` 属于延迟事件对象，必须走 session 精确关闭；`RemoteHudOverlays.dismiss(player, overlayId)` 保留强制关闭当前 overlay 的管理语义，新增 `dismissSession(player, overlayId, sessionId)` 供持有 session 的延迟逻辑使用。
- 远程页面客户端 screen 必须绑定 session/generation close lifecycle：用户手动关闭当前远程页面后，旧 session 失效通知不再重新打开错误页；服务端刷新页面期间的旧 screen 关闭回调不得误清新 session。

## 后续注意事项

- 若未来需要长驻远程 UI，应新增明确的 keepalive/renew 或交互 session 协议，而不是隐式延长 HTML stream session。
- 任何新增远程关闭/失效协议都应保持“非空 session 精确匹配，空 session 才允许强制 overlay/page 回退”的边界。
- 新增远程页面 screen seam 时优先保持 `UiDocumentScreens.createDocumentScreen(builder)` 入口可用，只在需要 close lifecycle 的链路使用 provision overload，避免把远程 session 细节扩散到普通业务页面作者侧。
