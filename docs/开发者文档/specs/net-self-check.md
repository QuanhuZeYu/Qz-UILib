# 网络层自检规格

网络自检页挂在 `/qzuilib test` 的诊断菜单中，页面 id 为：

```text
net_self_check
```

当前自检覆盖不依赖真实联机环境的基础链路：

| 场景 | 覆盖内容 |
|---|---|
| 大小策略 | 32KB 兼容帧、8 MiB 大消息提示、16 MiB 默认逻辑上限、256 MiB 默认物理能力、1 GiB 硬上限 |
| 反射 codec | enum、List、Map、嵌套对象、`@NetTransient` |
| 分片重组 | 100KB envelope 在 32KB 兼容帧下分片并重组 |
| 主线程队列 | client/server 主线程任务入队与 drain |

真实联机往返仍需人工场景：

- `runServer` + `runClient21` 双实例验证 C2S/S2C Channel。
- Fetch 超时、取消和远端异常。
- Store snapshot 与 DOM bridge。
- dedicated server 上确认 `EarlyMixins` 不返回客户端 mixin。
