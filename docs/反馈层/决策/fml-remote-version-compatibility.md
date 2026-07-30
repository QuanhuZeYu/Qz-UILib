# 决策：FML 远端版本兼容

## 背景

FML 1.7.10 默认按两端各自的 network checker 判定远端 mod 版本。若不声明范围，checker 只接受与本端完全相同的版本；这会让协议未变的同 minor patch 版本无法联机。另一方面，无界兼容会把未来可能不兼容的 minor 与线协议变化一并放行。

## 最终选择

### 有界同 minor 范围

- `MyMod.@Mod` 固定声明 `acceptableRemoteVersions = "[5.0.0,5.1.0)"`，Breaking 功能分支的目标发布身份为 `5.0.0`；源码版本仍由 Git tag / GTNH Gradle 推导，创建目标 tag 前不宣称已有 `5.0.0` 制品。
- 该字符串按 FML 使用的 Maven 版本范围语义解释：接受正式 `5.0.0`、后续 `5.0.x`，以及排序低于正式版的 `5.1.0-alpha`、`5.1.0-beta`、`5.1.0-rc1`、`5.1.0-SNAPSHOT`；拒绝 `4.6.3`、`5.0.0-alpha`、`5.0.0-rc1`、`5.0.0-SNAPSHOT`、正式 `5.1.0`、`6.0.0` 与 `*`。
- 接受 `5.1.0` 的上述 qualifier 是已确认的边界，不把开区间上界误写成“拒绝所有带 5.1.0 前缀的版本”。正式 `5.1.0` 仍由上界 `5.1.0)` 拒绝；低于闭区间下界的 `5.0.0` qualifier 同样明确拒绝。
- 该范围只承诺 FML 连接握手的远端版本准入，不承诺 Java API 的源码兼容、二进制兼容，也不替代接入方对公共 API 与依赖版本的核对。

### 双向检查与旧端点限制

- FML 在客户端和服务端分别用本端 checker 检查对端版本。跨 patch 互认必须双方端点都携带覆盖对端版本的范围声明，任一端拒绝都会阻断连接。
- 因此 `5.0.0` 是新范围合同的首个目标端点，后续 `5.0.x` 只有继续携带覆盖对端的声明才可双向互认。已发布 `4.x` 端点不接受 `5.0.0`，本范围也明确拒绝 `4.x`；无论旧端点位于客户端还是服务端，另一端升级都不能绕过它自己的 checker。
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

- 历史提交 `2c025422d5b0ca21066b4128cef8bc5d78f74519` 建立了生产 annotation、FML Maven 范围边界与禁止自定义 handler 的契约测试形状；当前 Breaking 功能分支将生产范围和同一契约测试同步到 `5.0.x`。
- 本次只完成源码、测试源码与文档静态同步；未取得独立复审回执。
- agent 未在本机执行 Gradle、编译、JUnit、构建、运行态或 verify；`5.0.0` tag/JitPack 制品、CI、发布 JAR annotation 与 dedicated server 双向握手/真实 v2/v1 流量均无本次实证，状态为 `INCOMPLETE`。
