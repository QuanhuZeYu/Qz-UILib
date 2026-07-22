# 决策：发布渠道拓扑

## 背景

旧 tag 流程把真实 JitPack Remote 与 clean consumer 串在 GitHub Release 之前。`4.6.2` 的 JitPack 日志/记录不一致使 workflow attempts 1–5 失败，尽管 immutable tag、目标 commit 与仓库可构建资产并未因此失效，GitHub Release 仍被拓扑耦合阻断。未来 Maven 若沿用同一 DAG，还会把凭据与第三方可用性继续扩大为仓库 Release 的故障域。

## 候选方案

### A. 保留单一串行发布链

把 JitPack、GitHub Release 与未来 Maven 视为一个事务，状态直观但故障域最大；第三方 pending、缓存污染或凭据缺失都会阻断仓库自有资产，不采用。

### B. GitHub Release 先行，其他渠道仍以它为 `needs`

可解除 GitHub Release 前置阻断，但仍让 JitPack/Maven 共享调度、权限或状态语义，无法形成真正独立的重试与审计边界，不采用。

### C. 三渠道零依赖

branch/PR publication gate 只守卫可合并代码；GitHub Release、JitPack advisory 与未来 Maven 各自持有 workflow、权限、合同和状态，不把渠道间成功作为前置。采用此方案。

## 最终选择

- GitHub Release 由仓库自有 `_github-release-contract.yml` 负责。未来 tag caller 与 `4.6.2` recovery 只传 immutable annotated tag object、peeled commit 与 publish 意图；合同重验身份、test/build、scene/doc、权威 notes，以及 main/dev/sources/dev-preshadow 四资产。
- reusable 的控制面与业务树必须 side-by-side 双 checkout：`control` 来自定义当前 reusable 的 `job.workflow_repository@job.workflow_sha`，`target` 来自当前仓库 immutable tag。checker/manifest 程序只从 control 执行，身份、Gradle、源码、scene/doc、notes 与四资产只从 target 读取；所有 run step 以 target 为 working directory，artifact 与 Release 路径显式指向 target bundle。
- recovery 只允许从仓库默认分支 dispatch，confirmation 在 mode/身份确认前拒绝其他 branch/tag ref，避免未审计控制 commit 调用 reusable。该控制面来源方案限定于本仓库 GitHub.com，不承诺 GHES 兼容。
- publish 路径只在远端 absent 时创建 draft，按 asset ID 下载复验后正式化并最终复验。已有匹配正式 Release只复验；已有 draft、冲突或重复 Release均 fail-closed，不删除、不覆盖、不续传。
- JitPack 使用独立 `jitpack-advisory.yml`，继续 fail-closed 核对 canonical GAV/GMM-classifier 组合、整套 Remote 收敛与 clean `:dev` consumer；其结论只属于 JitPack，不阻断 GitHub Release。
- 未来 Maven 必须新建独立 workflow，使用独立凭据、gate、并发与重试策略；禁止加入 GitHub Release caller/contract 的 `needs`，也禁止复用 Release 写权限。
- 各渠道状态分别记录。GitHub Release 成功不证明 JitPack/Maven 成功，advisory 失败也不把已验证 Release 改写为失败。

## 选择原因

- 仓库对自有 tag、源码、notes 与构建资产拥有验证主权，不应把第三方按需构建或凭据状态纳入 Release 身份合同。
- 零依赖把故障、权限与重试约束缩到各渠道最小边界，同时保留 branch/PR gate 对共同代码配置的前置质量保护。
- 仓库自有合同以 draft 和远端下载复验缩小不可逆写入窗口，并以最小 `contents:write`、固定 action SHA、按 tag 非取消并发与 fail-closed 冲突处理守住供应链边界。

## 影响范围

- 控制律：标准 tag 发布、`4.6.2` recovery、用户授权点、不可移动 tag 与 draft 残留处置。
- 传感：`check-github-release.ps1` 独立守 Release；`check-publication.ps1` 只守 Maven/JitPack publication。
- 工作流：tag caller/recovery 共用仓库内 Release 合同；JitPack advisory 独立；未来 Maven 另建入口。
- 控制面：旧 tag 不必包含当前 checker；Static/SelfTest 机械守卫双 checkout 的来源、顺序、路径与默认分支 dispatch 边界。远端仍须 push 后以 verify-only 验证真实 expression 与旧 tag 重建。
- 不改变业务代码、公共 API、普通 Maven GMM 或 JitPack canonical classifier 技术合同。

## 演进

- 2026-07-22：采用 `release-channel-decoupling/v1`，从“真实 JitPack 通过后才创建 GitHub Release”转为三渠道零依赖；触发原因为 `4.6.2` 第三方记录不一致暴露了串行拓扑的错误故障域。
- 2026-07-22：补充 control/target 双 checkout 与 recovery 默认分支 guard，纠正 checkout 旧 tag 后从目标树调用新 checker 的控制面错绑。
