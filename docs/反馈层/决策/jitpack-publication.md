# 决策：JitPack publication

## 背景

普通 `publishToMavenLocal` 生成的 publication 在项目 GAV 下是正确的：POM、main/dev/sources 与 Gradle Module Metadata 能一致表达 classifier 和 hash。JitPack 对外使用 `com.github.QuanhuZeYu:Qz-UILib:<tag>` canonical GAV；4.6.0 经 JitPack canonicalization 后，`.module` 的 classifier 语义与 canonical 制品路径不再一致，显式 `:dev` 消费返回 404。

该缺陷位于 JitPack publication 改写边界，不在 HostImage 源码、RetroFuturaGradle 或 GTNHGradle 的普通本地 publication。4.6.0 tag 与 GitHub Release 资产保持不动，修复进入 4.6.1。

## 候选方案

### A. 保留现状，只依赖本地 publication

本地 GMM 虽正确，却不能证明 JitPack canonicalization 后的 URL、classifier 与 hash。该方案无法发现 4.6.0 的 `dev` 404，拒绝。

### B. 全局禁用 Gradle Module Metadata

可绕开 JitPack 对 `.module` 的错误消费，但会让普通 Maven publication 丢失正确的 variant 语义，使非 JitPack 消费方退化。影响面超过实际故障边界，拒绝。

### C. JitPack 预先映射 canonical GAV，并仅在 JitPack 模式禁用 GMM

`jitpack.yml` 将 JitPack 提供的 `GROUP`、`ARTIFACT`、`VERSION` inline 映射为构建使用的 canonical group/artifact/version；仅当 runner 声明 `JITPACK=true` 时禁用 `GenerateModuleMetadata`。普通 publication 不设置该模式，继续生成并校验正确 GMM。采用此方案。

## 已验证结果

- 4.6.1 annotated tag object 为 `4ff2fad08b1b0a3b88cee9f0ed6c85c6a8fd25e2`，peeled commit 为 `c2d3ea91173fbedaeb912f3f07e8dcfce55bfc43`；[tag workflow 29554928156](https://github.com/QuanhuZeYu/Qz-UILib/actions/runs/29554928156) 的 Release gate、真实 JitPack Remote、clean `:dev` consumer、reusable Release 与 notes 全部成功。
- JitPack canonical main/dev/sources 的 SHA-256 分别为 `d69005594a223e556fcc63be8f22398928e5807a97dc7c0406508e2525503ef9`、`76319a864734d5770d1bf8579b7ab03f16be52453c92b662b9aae9010b898493`、`b4ae2e30791b8b62ae7c94e8107bfff91e1d7654a04b7643f36cc5df7fdb290d`；Build API、canonical POM、三个制品及 sha1、build.log 均通过远端核对。
- `.module=404` 只表示 JitPack canonical 组合方案按设计禁用 GMM；普通 Maven publication 仍须生成并校验正确 GMM，其既有合同不变。
- [GitHub Release 4.6.1](https://github.com/QuanhuZeYu/Qz-UILib/releases/tag/4.6.1) 已完成，非 draft/prerelease，正文与 `.changelogs/4.6.1.md` 一致且 4 个 JAR 资产齐全。GTNH Maven 步骤 skipped、尚未发布且不是该闭环的前置条件。

## 最终选择

- 普通 Maven publication 的合同是正确 POM + main/dev/sources + 正确 GMM。`apiElements` 与 `runtimeElements` 指向 dev，`reobfElements` 指向 main，`sourcesElements` 指向 sources；metadata URL 和 sha256 必须与真实文件一致。
- JitPack canonical publication 的合同是正确 canonical POM + main/dev/sources classifier；由于该模式主动禁用 GMM，`.module` 返回 404 是预期成功状态。显式 `:dev` 依赖必须由 Maven classifier 语义解析到 dev JAR，而不是依赖 Gradle variant metadata。
- POM 只声明 canonical GAV 与常规 Maven 依赖语义；classifier 由文件名 `-dev`、`-sources` 和消费方显式选择表达。main/dev/sources 必须内容互异、hash 可追溯，不能以同一 JAR 重命名冒充分类制品。
- branch/PR gate 负责 SelfTest、普通 publication 与 canonical 模拟，守卫代码合并；独立 `jitpack-advisory.yml` 负责真实 JitPack Build API、远端制品/sha1/hash、module=404、build.log 与 clean `:dev` consumer。
- Remote 按整套矩阵轮询到整体收敛：暂态 HTTP、building、日志/制品未完成记 pending；权限、非零 exit、错误身份/GAV/hash、classifier 碰撞或 module 污染等确定性错误立即失败，不能被 pending 遮蔽。
- JitPack advisory 只确认 JitPack 渠道，不是 GitHub Release 的 `needs` 或前置；GitHub Release、JitPack 与未来 Maven 的零依赖拓扑见 `release-channel-topology.md`。
- GTNH Maven 不是 JitPack publication 闭环或 GitHub Release 的前置；其凭据、workflow 与实际状态必须独立记录。

## 失败策略

- 所有版本使用不可移动的 annotated tag。DNS、超时、JitPack 5xx 或未收敛等非确定性基础设施失败，可对同一 tag 与 peeled commit 重试，不改变坐标或预期矩阵。
- 若同一版本已出现确定性的错误 GAV、classifier、POM、hash、module 状态或 commit 绑定，禁止移动 tag 覆盖；JitPack 渠道保持失败并在后续版本修复，但不得阻断或改写 GitHub Release 状态。
- branch 模拟、本地成功或 workflow 设计均不能写成真实 JitPack 已成功；只有 Remote 与 clean consumer 的运行结果可以确认远端 publication。

## 影响范围

- 构建边界：`build.gradle.kts`、`jitpack.yml` 与 publication 环境映射。
- 传感边界：`scripts/check-publication.ps1`、branch/PR gate、独立 JitPack advisory 和 clean consumer。
- 发布边界：JitPack 与 GitHub Release/Maven 零依赖，各自 fail-closed、分别重试和记录状态。
- 不改变源码、公共 API、UI 行为或普通 Maven publication 的 GMM 语义。

## 演进

- 2026-07-22：保留 4.6.1 已验证事实与 canonical GAV/GMM/classifier 技术合同，将真实 Remote/consumer 从 GitHub Release 前置改为独立 advisory；触发原因为渠道耦合把 JitPack 记录不一致错误扩大成仓库 Release 阻断。
