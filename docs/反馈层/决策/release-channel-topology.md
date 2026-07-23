# 决策：发布渠道拓扑

## 背景

旧通用发布链把多个渠道、reusable workflow、创建上传和正式化组合在同一控制图，导致 4.6.2 的第三方状态、权限和 draft 身份问题互相放大。

## 当前选择

- 仓库只保留已完成的固定 `4.6.2` recovery 与常规 build CI，不提供通用 tag 自动发布。
- recovery 的 `verify` job 只读：绑定 tag `4.6.2`、tag object `6155c157b823c928accc25b037f7a95e7e83d669` 和 commit `e86a731cf10c5fa9e0f3dd87fe52126646bf8ed1`，重建四资产与 notes，生成 manifest 并上传 bundle artifact；不读取 Release 或 draft API。
- 条件 `publish` job 是全仓 workflow 唯一写权限，在 PATCH 前按固定 ID `357902877` 完整核对 draft、notes 和四资产，只能把同一 ID 的 `draft` 改为 `false`；随后按 ID/tag 双端点复验，不得 Create、上传、删除、移动 tag 或发现动态 ID。
- JitPack、Maven、通用 tag 和本地 agent Gradle 能力均不存在现行 workflow；如需恢复，必须独立任务重建，不能扩展固定 recovery。
- 各渠道状态独立记录，历史结果不冒充当前能力。
- 固定 recovery 已由 verify run `29989023055` 与 publish run `29989322298` 于 2026-07-23 成功完成；它仍是不可重跑的一次性恢复链，不是通用发布入口。

## 选择原因

一次性恢复只需要固定身份与一个最小可逆前核验/不可逆写点。删除通用状态空间、外部渠道依赖和脚本控制面后，权限与失败边界可直接由两个 job 审计。

## 影响范围

该决策只改变 CI/Release 自动化、agent 本地构建边界和活动文档，不改 Java、Gradle 配置、资源、scene 或 tag。固定 recovery 已完成；未来任何远端 merge、push、dispatch 或 Release 操作仍须由用户逐项授权。
