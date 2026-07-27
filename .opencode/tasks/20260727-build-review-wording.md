# 任务：收口独立 review 残余措辞

## 元数据
- ID：`20260727-build-review-wording`
- 状态：`DONE`
- Owner：OpenCode 内置 `build`
- 创建日期：`2026-07-27`
- 基线分支：`fix/font-depth-state`
- 基线 HEAD：`a92df921c21d4800e9fba83fb3d60ff3d72e6d37`

## 目标
- 消除默认 build 迁移复审发现的两处现行“独立 review 守卫”残余措辞。

## 非目标
- 不改历史审查 provenance、业务源码、硬约束、门禁、CI、版本或发布策略。

## 已确认设计
- 当前守卫责任由权威导航、人工核对与内置 `build` 提交前自审承担。
- 关键发布或高风险变更仍可由用户另开会话或安排外部 review，但不是仓内固定角色。

## 写集
- `docs/反馈层/错误预防.md`
- `docs/反馈层/决策/README.md`
- `.opencode/tasks/INDEX.md`
- `.opencode/tasks/20260727-build-review-wording.md`

## 验收
- A1：两处现行规则不再声称由独立 review 守卫。
- A2：语义与 `PERSISTENT-WORKFLOW.md` 的内置 build 自审边界一致。
- A3：只修改写集，`git diff --check` 通过。

## 风险
- 无。

## 进度与证据
- 迁移提交 `a92df921` 的独立复审发现该非阻断 P2；历史规格中的角色 provenance 不在本任务范围。
- 已回读两处现行规则，并将固定独立 review 守卫改为权威导航人工核对与内置 `build` 提交前自审。
- 已对照 `PERSISTENT-WORKFLOW.md`，保留关键发布或高风险变更由用户另开会话或安排外部 review 的边界。
- `python scripts/run-agent-command.py -- git diff --check`：通过。
- `python scripts/run-agent-command.py -- git status --short --branch`：提交前仅含任务写集；分支仍落后 upstream 1。
- 未运行 Gradle、编译、构建、测试、运行态或 verify；本任务仅要求静态验证。

## 验证
- 回读两处文档。
- `python scripts/run-agent-command.py -- git diff --check`
- `python scripts/run-agent-command.py -- git status --short --branch`

## 唯一下一步
- 无；任务已完成。

## 结果
- 状态：`DONE`。
- 两处现行治理措辞已与内置 `build` 自审边界对齐；未改历史 review provenance、业务源码、硬约束、门禁、CI、版本或发布策略。
- 提交标题：`[Docs]: 收口独立 review 残余措辞`；提交标识以包含本任务文件的提交为准。
