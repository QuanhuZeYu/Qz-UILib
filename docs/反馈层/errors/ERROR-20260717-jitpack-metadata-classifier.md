# JitPack metadata 与 classifier 语义错位

## 错误现象

Qz-UILib 4.6.0 的本地 publication 与 GitHub Release JAR 正确，但 JitPack canonical 坐标下的 Gradle Module Metadata 无法正确关联分类制品；显式消费 `com.github.QuanhuZeYu:Qz-UILib:4.6.0:dev` 返回 404。

## 触发场景

- 构建先按项目自身 GAV 生成 POM、main/dev/sources 与 `.module`，随后由 JitPack 映射为 `com.github.<owner>:<repository>:<tag>` canonical 坐标。
- 验证只停留在普通 `publishToMavenLocal` 或 GitHub Release 资产，没有从 JitPack Remote 读取 POM/classifier/metadata，也没有用全新 consumer 显式解析 `:dev`。

## 根本原因

普通本地 publication 正确不代表 JitPack 改写后仍正确。JitPack canonicalization 改变了对外 GAV 与制品路径边界，而生成的 `.module` classifier URL/variant 语义没有随该边界形成一致合同，导致 canonical metadata 与实际 dev 制品分离。

该问题不属于 HostImage 源码、RetroFuturaGradle 或 GTNHGradle 普通本地 publication；误差来自缺少 JitPack 端到端传感器，把本地 GMM 成功错误外推成远端 classifier 成功。

## 修复方案

- JitPack 构建开始时将 runner 提供的 group/artifact/version 映射为 canonical GAV，使 POM 与 main/dev/sources 从发布源头使用最终坐标。
- 仅在 `JITPACK=true` 时禁用 Gradle Module Metadata，以 canonical POM + Maven classifier 作为 JitPack 合同；普通 publication 继续保留并校验正确 GMM。
- 增加 `scripts/check-publication.ps1` 的 SelfTest、Local `RequiredCorrect`/`Forbidden` 与 Remote 模式，并在 branch/tag workflow 串联；tag 发布还必须通过 clean 显式 `:dev` consumer 和 hash 核对。
- 4.6.0 tag 不移动，修复版本为 4.6.1。

## 预防措施

- publication 验收必须同时覆盖 POM GAV、main/dev/sources 文件、classifier URL、sha1/sha256、module 策略、peeled commit 和 clean consumer；任一局部成功都不能替代端到端结果。
- 普通 publication 的 `.module` 必须正确；只有 JitPack 条件组合的 `.module=404` 才是预期，禁止扩大为全局策略。
- tag workflow 必须在 GitHub Release 前完成真实 JitPack Remote 与 clean consumer；DNS/5xx 只能记为待重试，不能记为成功。
- 同类 publication 误判已不再只停留在通则：已上溯为 publication 脚本及 branch/tag 机械门禁。它不涉及 UI 宪章 I1-I13，无需修改 `NORTH_STAR.md`。
