# GitHub Release 与 JitPack 渠道耦合

## 现象

`4.6.2` annotated tag 已固定，但旧 tag workflow attempts 1–5 因 JitPack build log 与 Build API/制品记录不一致而失败，GitHub Release 至今不存在。JitPack 单渠道异常被错误扩大为仓库自有 Release 阻断。

另有一次诊断偏差：agent 对尚未构建的 commit 坐标 `com.github.QuanhuZeYu:Qz-UILib:e86a731cf10c5fa9e0f3dd87fe52126646bf8ed1` 发起普通 GET，以为只是读取状态，实际触发了 JitPack 按需构建。该请求晚于原始 attempts 1–5 故障，不改变 `4.6.2` tag object 或 peeled commit，也不能用来解释原始失败。

渠道解耦首版还存在确定性控制面错绑：reusable 三个 job 把目标 tag checkout 到工作区根，再从根调用新 `check-github-release.ps1`。`4.6.2@e86a731c` 不含该 checker（文件后续才加入），因此 recovery 的首个 Identity 步骤必然 file-not-found，后续 job 因 `needs` 跳过。recovery 亦未拒绝从非默认 ref 手工 dispatch，可能让未审计控制 commit 进入调用链。

## 触发场景

- tag workflow 以真实 JitPack Remote/consumer 成功作为 GitHub Release reusable job 的 `needs`。
- 第三方渠道处于 pending、缓存记录不一致、网络失败或确定性污染时，仓库自身 tag/notes/资产虽可验证，Release 仍无法运行。
- 诊断者把 JitPack 制品 URL GET 当成无副作用查询，对未构建的 tag/commit 坐标试探。
- reusable checkout 目标 tag 时覆盖了控制面来源；Static/SelfTest 只守权限和渠道拓扑，未守 checkout 来源、顺序、路径、working directory 与 checker/业务归属。
- recovery 固定了目标 tag 身份，却未同时固定 dispatch 控制 ref 的默认分支边界。

## 根因

- 拓扑把“同版本的多个分发渠道”误建模成“必须串行提交的单一事务”，没有按渠道划分故障域、权限与状态真值。
- GitHub Release 曾依赖外部 reusable 与 JitPack gate，仓库自有 tag 身份、notes 和四资产合同不完整。
- 对 JitPack 的按需构建语义缺少操作边界：制品 GET 可能启动构建，不是纯只读诊断。
- 将“执行合同的程序”和“被合同验证的数据”错误视为同一 checkout：旧 tag 业务树天然可能没有当前 checker，而从默认分支取全部业务文件又会破坏 immutable tag 身份。

## 修复

- `4270f1fc` 实施 `release-channel-decoupling/v1`：tag caller 与 `4.6.2` recovery 共用仓库自有 Release 合同，JitPack 改为独立 advisory，未来 Maven 固定为独立 workflow。
- `531a011b`、`938c0704`、`b8a25842` 补齐 Release SelfTest/Static、Remote 整体收敛、确定性错误优先、最小写权限及 YAML 权限绕过传感。
- Release publish 仅在 absent 时创建 draft，远端下载复验后正式化；已有 draft、冲突 Release或重复记录均 fail-closed，不删除、不覆盖。
- reusable 三个 job 改为先 checkout `job.workflow_repository@job.workflow_sha` 到 `control`，再 checkout immutable tag 到 `target`；checker/manifest 程序来自 control，RepositoryRoot、Gradle、scene/doc、notes、四资产、artifact 与 Release 路径全部绑定 target。
- recovery confirmation 在确认串之前拒绝非默认分支/tag dispatch；Static/SelfTest 增加双 checkout、路径归属与默认分支 guard 的逐类 mutation 负例。

## 预防

- GitHub Release、JitPack 与 Maven 的 workflow、`needs`、凭据、并发、重试和状态必须独立；branch/PR publication gate 只守卫代码合并。
- 修改 workflow 后必须运行 `check-github-release.ps1 -SelfTest/-Static` 与 `check-publication.ps1 -SelfTest`，确认零依赖、四资产、身份、最小权限及 advisory 收敛矩阵。
- 修改 Release checkout 或路径时，必须同时核对 control/target 来源矩阵：control 只提供当前合同程序，target 独占 Git/Gradle/源码/notes/资产数据；禁止根 checkout、错序、相对 `RepositoryRoot` 或从 target 调 checker。
- agent 不得通过 JitPack tag/commit 制品 URL、Build API 或其他 Remote 请求试探未构建坐标；Remote/advisory/dispatch 只由用户明确授权的 runner 执行。诊断只读仓库内记录与既有 run，不用可能触发按需构建的 GET。
- tag 永不可移动；draft 残留、冲突正式 Release或远端状态不一致时停止自动化，保留现场并交用户拍板。
