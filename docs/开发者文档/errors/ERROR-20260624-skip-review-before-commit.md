# 错误：fixer 工作完成后跳过独立 review 直接提交

## 错误现象
flat 回收任务中，fixer 完成代码改动后，主 Agent 直接跑 build + test 通过就提交了 commit `55e4ed6`，没有派 @reviewer 做独立代码审核。用户指出后才补派 reviewer 审核。

## 触发场景
- flat 回收是机械性改动（删字段+删分支+删测试用例），改动量小、逻辑简单
- 主 Agent 心存侥幸："改动简单 + build/test 全过 = 无需 review"
- 流程变成 fixer → build → test → commit，跳过了 fixer → build → test → **review** → commit 的标准链路

## 根本原因
**把"build/test 通过"等同于"review 通过"**，用编译器+测试覆盖了独立审核的职责。
- build/test 只能验证"代码能跑、测试通过"，不能替代 review 验证"改动是否精确、是否有遗漏、是否守不变量、是否守规范"
- AGENTS.md 第 7 节明确要求"实现完成后必须经一次独立子代理审核"，无例外条款
- 改动简单不是跳过 review 的理由；越是机械改动越容易有机械遗漏（悬挂 import、未更新 Javadoc、残留注释等），review 正是抓这些

## 修复方案
本次已补派 @reviewer 审核已提交的 commit `55e4ed6`，审核通过无阻断项无建议项，7 节全过。补审结果已记录在会话中。

## 预防措施
1. **标准链路不可跳**：fixer → build → test → review → commit，每一步都不能省。review 是提交前的硬性门禁，不是可选步骤。
2. **改动简单不是借口**：机械改动反而更需要 review 抓机械遗漏（悬挂 import、未更新 Javadoc、残留常量/注释、测试用例误删等）。
3. **build/test 通过 ≠ review 通过**：编译器和测试覆盖的是"能跑"，review 覆盖的是"改对了"。两者职责不同，不可互替。
4. **提交前自检**：在执行 `git commit` 前，自问"这一轮 fixer 的产出是否已经过独立 reviewer 审核？"若否，先派 review 再提交。
5. **本条已纳入 errors 索引**：`docs/开发者文档/errors/README.md` 的"其他"分组，供后续 Agent 查阅。
