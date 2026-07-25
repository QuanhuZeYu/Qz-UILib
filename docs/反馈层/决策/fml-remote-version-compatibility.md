# 决策：FML 远端版本兼容

## 背景

FML 1.7.10 默认按两端各自的 network checker 判定远端 mod 版本。若不声明范围，checker 只接受与本端完全相同的版本；这会让协议未变的同 minor patch 版本无法联机。另一方面，无界兼容会把未来可能不兼容的 minor 与线协议变化一并放行。

## 最终选择

### 有界同 minor 范围

- `MyMod.@Mod` 固定声明 `acceptableRemoteVersions = "[4.6.2,4.7.0)"`，当前源码版本身份为未发布的 `4.6.3` 候选；已发布旧版本仍为 `4.6.2`。
- 该字符串按 FML 使用的 Maven 版本范围语义解释：接受 `4.6.2`、后续 `4.6.x`，以及排序低于正式版的 `4.7.0-alpha`、`4.7.0-beta`、`4.7.0-rc1`、`4.7.0-SNAPSHOT`；拒绝 `4.6.1`、`4.6.2-alpha`、正式 `4.7.0`、`5.0.0` 与 `*`。
- 接受 `4.7.0` 的上述 qualifier 是已确认的边界，不把开区间上界误写成“拒绝所有带 4.7.0 前缀的版本”。正式 `4.7.0` 仍由上界 `4.7.0)` 拒绝。
- 该范围只承诺 FML 连接握手的远端版本准入，不承诺 Java API 的源码兼容、二进制兼容，也不替代接入方对公共 API 与依赖版本的核对。

### 双向检查与旧端点限制

- FML 在客户端和服务端分别用本端 checker 检查对端版本。跨 patch 互认必须双方端点都携带覆盖对端版本的范围声明，任一端拒绝都会阻断连接。
- 因此本声明只为后续携带同一合同的版本播种兼容能力，不能让新端点单方面兼容旧端点。已发布、未携带该范围的旧 `4.6.2` 端点仍按精确版本检查，会拒绝正式 `4.6.3`；无论旧端点位于客户端还是服务端，另一端升级都不能绕过它自己的 checker。
- FML 默认 checker 对“远端缺少 `qz_uilib`”保留方向性语义，其中 `Side` 表示远端侧：客户端有 Qz-UILib、远端服务端缺少时（`side == Side.SERVER`）接受；服务端有 Qz-UILib、远端客户端缺少时（`side == Side.CLIENT`）拒绝。版本范围只在远端版本表含该 mod 时判断，不把缺 mod 泛化成双向可选。

### 线协议保持冻结

- 本次只改变 FML 握手准入，主 `NetEnvelope` 仍为 v2，Realtime `NetRealtimeFrame` 仍为 v1。
- 没有新增运行时协议协商、能力探测或降级路径；落入版本范围不代表端点会动态适配不同线协议。
- 若 patch 内无法保持现有 v2/v1 流量兼容，必须升 minor 并收紧 FML 范围，不能用同 minor 范围掩盖协议不兼容。

## 拒绝的方案

- **`*`**：会关闭版本边界并接受未来 minor/major，无法在不兼容线协议进入前 fail closed。
- **自定义 `@NetworkCheckHandler`**：会覆盖静态范围，引入额外分支、方向判断与长期测试面；当前有界 Maven 范围已能表达所需合同，不增加可编程 checker。
- **把范围当作协议协商或 Java API 承诺**：FML checker 只回答是否接受连接，不能证明实际消息流、JAR annotation 或外部 Java consumer 兼容。

## 影响与证据

- 源码提交 `2c025422d5b0ca21066b4128cef8bc5d78f74519` 在生产 `MyMod.@Mod` 增加该范围，并新增直接读取生产 annotation、按 FML Maven 语义核对边界且禁止自定义 handler 的契约测试源码。
- 独立静态复审未发现 P0/P1/P2，任务 A1-A4 静态满足。
- agent 未在本机执行 Gradle、编译、JUnit、构建、运行态或 verify；CI、发布 JAR annotation 与 dedicated server 双向握手/真实 v2/v1 流量均无本次实证，状态为 `INCOMPLETE`。
