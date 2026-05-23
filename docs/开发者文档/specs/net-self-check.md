# 网络层自检规格

网络自检页挂在 `/qzuilib test` 的诊断菜单中，页面 id 为：

```text
net_self_check
```

当前自检覆盖两类场景：不依赖真实联机的本地运行时断言，以及需要客户端已连接服务端的真实网络往返。

| 场景 | 覆盖内容 |
|---|---|
| 大小策略 | 32KB 兼容帧、8 MiB 大消息提示、16 MiB 默认逻辑上限、256 MiB 默认物理能力、1 GiB 硬上限 |
| 内容信封 | `route/key + contentType + headers + body` 的 envelope 编解码 |
| Header 规则 | header 名大小写归一、token 校验、数量上限、CR/LF 拒绝 |
| 可选 POJO codec | enum、List、Map、嵌套对象、`@NetTransient`，仅作为业务二进制辅助 |
| 分片重组 | 100KB envelope 在 32KB 兼容帧下分片并重组 |
| 主线程队列 | client/server 主线程任务入队与 drain |
| 运行时 Channel 往返 | 预注册内部 Channel 执行 C2S ping 与 S2C pong |
| 运行时分片 Channel | 超过 32KB 的二进制 body 走真实 C2S 分片并在服务端重组 |
| 运行时 Fetch 往返 | 预注册内部 Fetch endpoint 执行 C2S 请求与响应 |
| 运行时 Fetch 错误 | `context.fail(...)` 经真实网络返回 500 错误响应 |
| 运行时 Fetch 超时 | 短超时 endpoint 不回复，由后续网络帧触发 pending timeout |
| 运行时 Store 快照 | 预注册内部 Fetch 触发服务端 Store set，再等待客户端 Store snapshot |

仍需人工场景：

- Fetch 取消语义。
- Store DOM bridge。
- dedicated server 上确认 `EarlyMixins` 不返回客户端 mixin。
