# 任务：迁移默认 build 与持久任务模态

## 元数据

- ID：`20260727-build-persistent-mode`
- 状态：`DONE`
- 所有者：OpenCode 内置 `build`
- 创建日期：`2026-07-27`
- 更新日期：`2026-07-27`
- 分支：`fix/font-depth-state`
- 起始 HEAD：`79aa0bd640a6fc7546dfd3ff719b5652c138b67c`
- 依赖：无

## 目标

- 保留已完成的 Python 命令策略迁移，改用 OpenCode 内置默认 `build` 直接工作。
- 用 `.opencode/tasks/<id>.md` 与 `INDEX.md` 承载可跨会话恢复的持久任务。
- 明确上下文只是易失缓存，影响后续动作的事实必须在阶段边界落盘。

## 非目标

- 不修改业务源码、公共 API、版本、依赖、CI、发布策略或 I1-I13/R1-R13。
- 不执行 Gradle、编译、构建、测试、verify、merge、push、tag 或 release。

## 已确认设计

- `.opencode/opencode.json` 显式指定内置 `build`，删除会覆盖内置身份的自定义 agent 定义。
- 持久任务采用 `DRAFT/READY/ACTIVE/BLOCKED/DONE/CANCELLED` 状态机；简单问答不建任务。
- L0 会话上下文不具权威性；L1 任务工作记忆、L2 项目语义记忆和 L3 情景档案均为仓内持久文件。
- 默认 `build` 负责规划、实施、静态核对、自审和提交，不再依赖 Task 调度或独立 handoff。

## 写集

- `.gitignore`
- `.opencode/opencode.json`
- `.opencode/agents/*.md`（删除）
- `.opencode/session-handoff.md`（删除）
- `.opencode/task.md`（迁移后删除）
- `.opencode/tasks/INDEX.md`
- `.opencode/tasks/20260727-build-persistent-mode.md`
- `AGENTS.md`
- `CLAUDE.md`
- `NORTH_STAR.md`
- `docs/控制律层/编排模式/PERSISTENT-WORKFLOW.md`
- `docs/控制律层/编排模式/SUBAGENT-ORCHESTRATION.md`（删除）
- `docs/控制律层/编排模式/SESSION-HANDOFF.md`（删除）
- `docs/控制律层/编排模式/TASK-BRIEF.md`
- `docs/控制律层/稳定命令.md`
- `docs/控制律层/项目约定.md`
- `docs/传感层/门禁脚本说明.md`
- `docs/反馈层/交接.md`
- `docs/反馈层/错误预防.md`
- `scripts/run-agent-command.py`

## 验收

- A1：默认 agent 为内置 `build`，仓库不再定义自定义 agent。
- A2：现行治理不依赖 subagent、Task 调度、临时任务单或独立 handoff。
- A3：持久工作流定义 L0-L3、CHAT/DESIGN/EXECUTE、状态机、所有权和写回时机。
- A4：任务文件可独立恢复目标、决定、范围、状态、证据、阻断和唯一下一步。
- A5：本机禁 Gradle/编译/测试/verify、环境所有权、Git/发布边界和业务硬约束不变。
- A6：Python runner 与命令策略改动纳入同一提交，无业务源码或发布配置改动。

## 风险

- R1：移除独立 reviewer 后上下文隔离减弱；常规任务由 `build` 对照验收与 diff 自审，关键发布可由用户另开会话或安排外部 review。
- R2：历史审查和规格中的角色字样属于 provenance，不批量改写；现行治理路径不得依赖这些角色。
- R3：分支开始时落后 upstream 1 个提交；本任务不 pull/rebase/merge。

## 进度与证据

- 已读取仓库规则、宪章、硬约束目录、交接、稳定命令与任务要求。
- 已核对起始分支、HEAD、工作区；任务开始前的 Python 命令策略改动由用户确认纳入本迁移。
- 已完成配置、治理文档、持久任务和 Python runner 的静态回读与 diff 自审。
- `python scripts/run-agent-command.py -- git diff --check`：通过。
- `python scripts/run-agent-command.py -- git status --short --branch`：提交前仅含任务写集；分支仍落后 upstream 1。
- 未运行 Gradle、编译、构建、测试、运行态或 verify；本任务不要求这些实证。

## 唯一下一步

- 无；任务已完成。OpenCode 需重启后才会加载新的默认 agent 配置。

## 结果

- 状态：`DONE`。
- 已迁移到内置默认 `build` 与受版本控制的持久任务模式，删除旧自定义 agent、临时任务与 handoff 入口。
- 已保留并纳入 Python runner 及相关命令策略改动；未修改业务源码、公共 API、版本、依赖、CI 或发布配置。
- 提交标题：`[Refactor]: 迁移默认 build 与持久任务模式`；提交标识以包含本任务文件的提交为准。
