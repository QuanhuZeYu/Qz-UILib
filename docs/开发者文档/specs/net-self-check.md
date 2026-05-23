# 网络层自检规格

网络自检页挂在 `/qzuilib test` 的诊断菜单中，页面 id 为：

```text
net_self_check
```

当前自检覆盖两类场景：不依赖真实联机的本地运行时断言，以及需要客户端已连接服务端的真实网络往返。页面支持逐项执行和“全部执行”聚合验收。

| 场景 | 覆盖内容 |
|---|---|
| 大小策略 | 32KB 兼容帧、8 MiB 大消息提示、16 MiB 默认逻辑上限、256 MiB 默认物理能力、1 GiB 硬上限 |
| 内容信封 | `route/key + contentType + headers + body` 的 envelope 编解码 |
| Header 规则 | header 名大小写归一、token 校验、数量上限、CR/LF 拒绝 |
| 可选 POJO codec | enum、List、Map、嵌套对象、`@NetTransient`，仅作为业务二进制辅助 |
| 分片重组 | 100KB envelope 在 32KB 兼容帧下分片并重组 |
| 主线程队列 | client/server 主线程任务入队与 drain |
| Store DOM bridge | Store 视图绑定 DOM 后投递到客户端主线程渲染 |
| 运行时 Channel 往返 | 预注册内部 Channel 执行 C2S ping 与 S2C pong |
| 运行时分片 Channel | 超过 32KB 的二进制 body 走真实 C2S 分片并在服务端重组 |
| 运行时 Fetch 往返 | 预注册内部 Fetch endpoint 执行 C2S 请求与响应 |
| 运行时 Fetch 错误 | `context.fail(...)` 经真实网络返回 500 错误响应 |
| 运行时 Fetch 超时 | 短超时 endpoint 不回复，由后续网络帧触发 pending timeout |
| 运行时 Fetch 取消 | 本地 `cancel(false)` 移除 pending，服务端迟到响应被忽略 |
| 运行时 Fetch 限流 | 同一玩家连续请求受限 endpoint，第二次返回 429 与 `retry-after-ms` |
| 运行时 Stream 大内容 | 预注册内部 Stream endpoint 下载超过 16 MiB 的二进制响应，并验证进度 |
| 运行时 Store 快照 | 预注册内部 Fetch 触发服务端 Store set，再等待客户端 Store snapshot |
| 运行时 Store 增量 | 预注册内部 Fetch 触发服务端 Store delta，再等待客户端按业务 applier 计算新快照 |
| 运行时玩家 Store | `PER_PLAYER` Store + `accessControl` + `setForPlayer` 定向快照 |

已人工验收：

- GTNH / ModularUI2 服务端环境默认 vanilla 传输路径下，玩家可正常进服，`NetHandlerPlayServer` 同目标 mixin 未阻断连接。
- 网络层自检全部执行通过：18 项通过、0 项失败。
- `运行时 Fetch 限流` 会在服务端留下 `Qz Fetch 请求被限流` warn，这是预期的限流可观测日志。
