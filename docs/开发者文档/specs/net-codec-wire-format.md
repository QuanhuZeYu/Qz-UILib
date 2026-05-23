# 网络层 codec 与信封格式

本文记录第一版网络层线协议，配合 `network-layer-plan.md` 阅读。

## 物理 channel

所有普通逻辑帧映射到 vanilla channel name：

```text
qz:0
```

真实逻辑 id 放在 Qz envelope 的 `key` 字段中，例如 `mymod:chat`、`mymod:getUser`。

## Envelope

`NetEnvelope` 当前格式：

| 字段 | 类型 | 说明 |
|---|---|---|
| magic | int | 固定 `QZNL` |
| version | u8 | 当前为 `1` |
| kind | u8 | `CHANNEL` / `FETCH_REQUEST` / `FETCH_RESPONSE` / `FETCH_ERROR` / `STORE_SNAPSHOT` / `STORE_DELTA` / `META` / `CHUNK` |
| targetSide | u8 | `CLIENT=1` / `SERVER=2` |
| key | bytes | UTF-8 + varint 长度 |
| typeId | varint | schema 注册表内类型 id |
| requestId | i64 | Fetch 请求 id，其它帧为 `0` |
| payload | bytes | varint 长度 + 负载 |

收到帧后先校验 `targetSide`，不匹配则丢弃并记录 warn。

## POJO codec

`NetCodec` 使用反射 codec：

- 字段布局由 `FieldLayout` 固化，排序为父类优先、同层协议字段名升序。
- `static` / Java `transient` / synthetic / `@NetTransient` 字段不参与编码。
- `@NetField(name = "...", since = 1)` 可固定协议字段名。
- 支持基础类型、包装类型、`String`、`byte[]`、`Enum`、`List`、`Set`、`Map`、嵌套 POJO。
- 集合与 Map 字段必须声明具体泛型。
- POJO 解码需要无参构造器。

每个字段都会写 1 byte null marker。整数和长度使用 varint；带符号整数使用 ZigZag。

## 分片

当 envelope 编码后超过当前方向物理帧上限，但没有超过 16 MiB 普通逻辑消息上限时，`NetService` 自动切成 `CHUNK` 帧。

`CHUNK` payload：

| 字段 | 类型 | 说明 |
|---|---|---|
| streamId | i64 | 本地递增分片流 id |
| sequence | i32 | 当前分片序号 |
| total | i32 | 总分片数 |
| originalLength | i32 | 原始 envelope 长度 |
| chunkLength | i32 | 当前分片长度 |
| chunkBytes | bytes | 分片内容 |

`NetChunkAssembler` 默认 30 秒重组超时，完成后再按普通 envelope 解码。
