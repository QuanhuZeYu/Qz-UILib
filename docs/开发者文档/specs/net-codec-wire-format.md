# 网络层内容信封与 codec 格式

本文记录当前网络层线协议，配合 `network-layer-plan.md` 阅读。

## 物理 channel

所有普通逻辑帧映射到 vanilla channel name：

```text
qz:0
```

真实逻辑 id 放在 Qz envelope 的 `key` 字段中，例如 `mymod:chat`、`mymod:getUser`。

## Envelope v2

`NetEnvelope` 当前格式：

| 字段 | 类型 | 说明 |
|---|---|---|
| magic | int | 固定 `QZNL` |
| version | u8 | 当前为 `2` |
| kind | u8 | `CHANNEL` / `FETCH_REQUEST` / `FETCH_RESPONSE` / `FETCH_ERROR` / `STORE_SNAPSHOT` / `STORE_DELTA` / `META` / `CHUNK` |
| targetSide | u8 | `CLIENT=1` / `SERVER=2` |
| key | bytes | UTF-8 + varint 长度，业务 route / channel / store id |
| contentType | bytes | UTF-8 + varint 长度，MIME-like 内容类型 |
| requestId | i64 | Fetch 请求 id，其它帧为 `0` |
| statusCode | varint | Fetch response 状态码，其它帧为 `0` |
| headers | map | varint 数量，随后每项为 key/value UTF-8 字符串 |
| payload | bytes | varint 长度 + body 字节 |

收到帧后先校验 `targetSide`，不匹配则丢弃并记录 warn。

`typeId` 不再属于 envelope。协议身份由 `key` 与 `contentType` 表达，业务自己的 JSON 或二进制格式由业务 handler 解析。

## Header 规则

Header 是轻量元数据通道，不能绕过 body 与大内容传输边界：

- header 名大小写不敏感，线协议写入前归一成小写 token。
- token 字符集为 ``a-z 0-9 !#$%&'*+-.^_`|~``。
- 单帧最多 32 个 header。
- 单个 header 名最多 64 字节，单个值最多 1024 字节。
- 单帧 header 名和值合计最多 8192 字节。
- header 值不允许 CR/LF。

## 内容类型

内置内容类型：

- `application/json`
- `application/octet-stream`
- `text/plain; charset=utf-8`

业务可使用 `NetContentType.of(...)` 声明自定义 MIME-like 类型，例如：

```text
application/vnd.mymod.state+json
application/x-mymod-binary
```

## 可选 POJO codec

`NetCodec` 保留为业务内部的紧凑二进制编码辅助，不参与 envelope 路由、握手或能力协商主路径。

- 字段布局由 `FieldLayout` 固化，排序为父类优先、同层协议字段名升序。
- `static` / Java `transient` / synthetic / `@NetTransient` 字段不参与编码。
- `@NetField(name = "...", since = 1)` 可固定协议字段名。
- 支持基础类型、包装类型、`String`、`byte[]`、`Enum`、`List`、`Set`、`Map`、嵌套 POJO。
- 集合与 Map 字段必须声明具体泛型。
- POJO 解码需要无参构造器。

推荐用法是业务先 `NetCodec.of(MyPojo.class).encode(value)` 得到 `byte[]`，再通过 `NetBody.of(NetContentType.of("application/x-mymod-pojo"), bytes)` 发送。

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
