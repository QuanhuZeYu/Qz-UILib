# 决策：发布渠道拓扑

## 背景

旧通用发布链把多个渠道、reusable workflow、创建上传和正式化组合在同一控制图，导致 4.6.2 的第三方状态、权限和 draft 身份问题互相放大。

## 当前选择

- 仓库只保留已完成的固定 `4.6.2` recovery 与常规 build CI，不提供通用 tag 自动发布；`4.6.3` 的一次性人工发布已完成，但没有因此新增长期 workflow 能力。
- recovery 的 `verify` job 只读：绑定 tag `4.6.2`、tag object `6155c157b823c928accc25b037f7a95e7e83d669` 和 commit `e86a731cf10c5fa9e0f3dd87fe52126646bf8ed1`，重建四资产与 notes，生成 manifest 并上传 bundle artifact；不读取 Release 或 draft API。
- 条件 `publish` job 是全仓 workflow 唯一写权限，在 PATCH 前按固定 ID `357902877` 完整核对 draft、notes 和四资产，只能把同一 ID 的 `draft` 改为 `false`；随后按 ID/tag 双端点复验，不得 Create、上传、删除、移动 tag 或发现动态 ID。
- `4.6.3` 一次性人工路径已完成：`--no-ff` merge commit 为 `M=16d8c45beaa3c224cc509818fe569607ee94ff65`，绑定 `M` 的最终 Build and test run `30157047707` 为 attempt 1、`completed/success`；annotated tag object 为 `T=3b0ad894fb3168e82a6c9075a85eedb86614ee0e`，peeled commit 为 `M`；GitHub Release ID `359754162` 已于 `2026-07-25T12:34:30Z` 正式发布四个独立 JAR。
- 该人工路径未修改或复用固定 recovery，也未恢复通用 tag、JitPack、Maven 或本地 agent Gradle 能力；如需这些长期能力，必须另开任务重建。
- `4.6.3` 的 GitHub Release 渠道完成不代表 JitPack、Maven 或 clean consumer 已可用，也不代表 Qz-Miner 已升级依赖。
- 各渠道状态独立记录，历史结果不冒充当前能力。
- 固定 recovery 已由 verify run `29989023055` 与 publish run `29989322298` 于 2026-07-23 成功完成；它仍是不可重跑的一次性恢复链，不是通用发布入口。

## 选择原因

固定 recovery 只需要固定身份与一个最小可逆前核验/不可逆写点；`4.6.3` 则以候选、最终 merge commit、annotated tag object 和单一 Release ID 串起已完成的一次性人工闭环。两条路径都拒绝动态身份发现和通用渠道扩张，使权限与部分成功边界可直接审计。

## 影响范围

该决策只记录固定 recovery 与已完成的 `4.6.3` 一次性人工发布拓扑，不改 Java、Gradle 配置、workflow、资源、scene 或现有 tag。两条一次性路径的完成状态都不外溢到未来版本或其他发布渠道；tag 后文档回写也不改变 `4.6.3` 指向正式 tag commit `M` 的事实。
