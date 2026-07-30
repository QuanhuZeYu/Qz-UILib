# 决策：发布渠道拓扑

## 背景

旧通用发布链把多个渠道、reusable workflow、创建上传和正式化组合在同一控制图，导致 4.6.2 的第三方状态、权限和 draft 身份问题互相放大。

## 当前选择

- 仓库只保留常规 build CI，不提供通用 tag 自动发布。
- 固定 `4.6.2` recovery 已完成且实现已删除；其历史身份与执行结果保留在本文，不再构成可 dispatch 的仓库能力。
- JitPack、Maven、通用 tag 和本地 agent Gradle 能力均不存在现行 workflow；如需恢复，必须独立任务重建，不能扩展固定 recovery。
- 各渠道状态独立记录，历史结果不冒充当前能力。
- 固定 recovery 已由 verify run `29989023055` 与 publish run `29989322298` 于 2026-07-23 成功完成；删除 workflow 不改变该历史结果。

## 选择原因

一次性恢复只需要固定身份与一个最小可逆前核验/不可逆写点。任务完成后删除 workflow，避免已消费的写入口继续留在活动控制面。

## 影响范围

该决策只记录历史 recovery 并移除其 workflow，不改 Java、Gradle 配置、资源、scene、既有 tag 或 Release。未来任何远端 merge、push、dispatch 或 Release 操作仍须由用户逐项授权。
