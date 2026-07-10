# 决策：ConfigManager 提交前 DraftValidator 钩子

## 背景

接入方（如 Qz-Miner）需要在配置页保存时做跨字段 / 业务规则校验，且必须在写盘与 Authority 变更之前 fail-closed。原 `ConfigManager.save` 仅有 `DraftBuffer.validateAll()` 的 schema 字段约束，无扩展点。

## 拍板结论

- **新增** `club.heiqi.config.runtime.DraftValidator`：`ValidationResult validate(DraftBuffer draft)`
- **bootstrap 三参** `bootstrap(File, ConfigSchema, DraftValidator)`；二参 100% 兼容并委托 `DraftValidator.noop()`
- Manager **final 持有** validator；禁止 bootstrap 传 null（用 noop 表达无校验）
- save 顺序：内置 validateAll → custom validator → `ValidationResult.merge` → 有错立即 INVALID
- validator **返回 null 或抛 RuntimeException** → fail-closed INVALID，path 固定 `_config`（`DraftValidator.GLOBAL_ERROR_PATH`），不让异常落到 BATCH_SAVE 之后
- 同 path 合并时 **内置消息优先**；不同 path 字段错误均保留
- **不改 ConfigScreen**：仍只调 `manager.save`，INVALID 自然进现有保存反馈
- 版本：向后兼容 **patch 4.5.2**

## 非目标

- 不引入测试专用生产 API 泄漏
- 不改 IO_FAILED 回滚语义
- 本轮不强制把业务校验 UI 文案做到全局 path 展示（字段错误走既有信号即可）

## 演进

- 2026-07-10：首版落地（`add/config-draft-validator`），供 Qz-Miner 等接入方使用。
