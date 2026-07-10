# 决策：ConfigManager 提交前 DraftValidator 钩子

## 背景

接入方（如 Qz-Miner）需要在配置页保存时做跨字段 / 业务规则校验，且必须在写盘与 Authority 变更之前 fail-closed。原 `ConfigManager.save` 仅有 `DraftBuffer.validateAll()` 的 schema 字段约束，无扩展点。

## 拍板结论

- **新增** `club.heiqi.config.runtime.DraftView`（只读）+ `DraftValidator.validate(DraftView)`
- Manager 在内置校验后构造 `SnapshotDraftView` 不可变快照再交 custom；**禁止**把可变 `DraftBuffer` 交给 validator
- **bootstrap 三参** `bootstrap(File, ConfigSchema, DraftValidator)`；二参 100% 兼容并委托 `DraftValidator.noop()`
- Manager **final 持有** validator；禁止 bootstrap 传 null（用 noop 表达无校验）
- save 顺序：内置 validateAll → 构造 DraftView → custom → `ValidationResult.merge` → 有错立即 INVALID
- 视图构造失败 / validator 返回 null / 抛 RuntimeException → fail-closed INVALID，path 固定 `_config`
- 同 path 合并时 **内置消息优先**；不同 path 字段错误均保留
- **UI 接入**：`DraftSignalAdapter` 持 `submitValidationSignal`；`ConfigScreen.saveChanges` 在 INVALID 时写入合并结果；字段红字 / errorCount（含 `_config`）/ 真实摘要反馈；字段编辑或成功保存清空提交错误
- **版本策略（patch 例外）**：4.5.2 按 CHANGELOG「修订号 = 行为修复或文档调整」发布。虽新增公共 API，但是**向后兼容、仅扩展保存事务缺口**的单调增量；未破坏既有二参 bootstrap / save 语义，故不升 minor。本决定是 4.5.x 系列一次明确登记的 patch 例外，不改动宪章信条。

## 非目标

- 不引入测试专用生产 API 泄漏
- 不改 IO_FAILED 回滚语义
- 不要求全局 `_config` 有独立字段卡片（计入 errorCount + 保存反馈即可）

## 演进

- 2026-07-10：首版落地（`add/config-draft-validator`）。
- 2026-07-10：reviewer 阻断修复——`DraftView` 只读入参；提交错误接入 adapter/UI；patch 例外登记。
- 2026-07-10：深度只读 `SnapshotDraftView.deepFreeze`（List/Map/数组）；编辑字段同步清 `saveFeedback=NONE`。
- 2026-07-10：终审收口——单 candidate 串行事务 + revision 守卫；`ValueCopy` 白名单 Number；DraftView 去 schema/仅 schema 字段；YAML/JSON 原子写；ValidationResult 保序与 path 规范化；UI 同步源清理。
- 2026-07-10：简化事务——删写盘后二次补偿；revision 变保留 draft 新编辑；Authority 深快照旁路检测；get 防御副本；current/draft 双种子；Signal 回读 buffer。
