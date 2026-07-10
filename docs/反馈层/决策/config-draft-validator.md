# 决策：ConfigManager 提交前 DraftValidator 钩子

## 背景

接入方（如 Qz-Miner）需要在配置页保存时做跨字段 / 业务规则校验，且必须在写盘与 Authority 变更之前 fail-closed。原 `ConfigManager.save` 仅有 `DraftBuffer.validateAll()` 的 schema 字段约束，无扩展点。

## 拍板结论

- **新增** `club.heiqi.config.runtime.DraftView`（只读）+ `DraftValidator.validate(DraftView)`
- Manager 从单次规范化 candidate 构造 `SnapshotDraftView` 不可变快照再交 custom；**禁止**把可变 `DraftBuffer` 交给 validator
- **bootstrap 三参** `bootstrap(File, ConfigSchema, DraftValidator)`；二参保留原签名并委托 `DraftValidator.noop()`
- Manager **final 持有** validator；禁止 bootstrap 传 null（用 noop 表达无校验）
- save 采用三阶段乐观事务：双锁短暂 capture（revision + base/current 全表 + 规范化 proposed 全表）→ 完全锁外内置/custom 校验与预制 → 同锁序复锁 verify/write/引用交换 commit
- capture 时 Authority 已不等于 draft base，或提交复核时 draft revision / Authority base 冲突，均返回 INVALID；实际并发修改保留，不恢复旧 candidate
- `DraftView` 是 validator 唯一稳定输入；validator 契约禁止写来源 manager/draft/Authority/Legacy 或调用 save/flushRaw。运行时不承诺物理阻止旁路，而以同一乐观冲突规则 fail-closed
- NUMBER 合法数字字符串在 candidate 中统一为 `Double`；内置校验、DraftView、Authority、draft/current 与磁盘消费同一规范化值；非法与非有限数 fail-closed
- SIMPLE_LIST 保存候选必须是 `List`，且每个非 null 元素必须是 `String`；非法标量、整数列表或混合列表均 fail-closed，不在接入 bridge 事后字符串化
- 视图构造失败 / validator 返回 null / 抛 RuntimeException → fail-closed INVALID，path 固定 `_config`
- 同 path 合并时 **内置消息优先**；不同 path 字段错误均保留
- **UI 接入**：`DraftSignalAdapter` 持 `submitValidationSignal`；`ConfigScreen.saveChanges` 在 INVALID 时写入合并结果；字段红字 / errorCount（含 `_config`）/ 真实摘要反馈；字段编辑或成功保存清空提交错误
- INVALID 接入与成功保存均从 DraftBuffer 全字段回读 Signal；Signal 中的 List 继续深度只读
- Persistence 保留 `writeAll`，内部拆成锁外构树/序列化与锁内 temp+replace；优先 ATOMIC_MOVE，不支持时 fallback 为非严格原子的整文件 replace
- 成功写盘与引用交换后、释放事务锁前建立 manager 级通知状态，再锁外恰发布一次 `BATCH_SAVE`；同一 manager 通知期间任意线程 save 均返回 INVALID 且不再发事件，`openDraft` 只读不受影响。监听器 RuntimeException/AssertionError 隔离，致命 Error 传播
- **版本策略（patch 例外）**：4.5.2 按 CHANGELOG「修订号 = 行为修复或文档调整」发布。虽新增公共 API，但是**向后兼容、仅扩展保存事务缺口**的单调增量；未破坏既有二参 bootstrap / save 语义，故不升 minor。本决定是 4.5.x 系列一次明确登记的 patch 例外，不改动宪章信条。

## 非目标

- 不引入测试专用生产 API 泄漏
- 不改变 IO_FAILED 的外部结果类型；实现改为写盘前不修改 Authority/current，故失败无需恢复旧快照
- 不要求全局 `_config` 有独立字段卡片（计入 errorCount + 保存反馈即可）

## 演进

- 2026-07-10：首版落地（`add/config-draft-validator`）。
- 2026-07-10：reviewer 阻断修复——`DraftView` 只读入参；提交错误接入 adapter/UI；patch 例外登记。
- 2026-07-10：深度只读 `SnapshotDraftView.deepFreeze`（List/Map/数组）；编辑字段同步清 `saveFeedback=NONE`。
- 2026-07-10：终审收口——单 candidate + revision 守卫；`ValueCopy` 白名单 Number；DraftView 去 schema/仅 schema 字段；YAML/JSON 同目录 temp replace；ValidationResult 保序与 path 规范化；UI 同步源清理。
- 2026-07-10：简化事务——删写盘后二次补偿；revision 变保留 draft 新编辑；Authority 深快照旁路检测；get 防御副本；current/draft 双种子；Signal 回读 buffer。
- 2026-07-10：终审 P1 阶段性实现——Authority/manager→draft 固定锁序并曾以整段持锁串行保存；Authority/Legacy/openDraft/flushRaw 共用锁域，事件锁外发布；Authority 与 UI Signal 容器读值断别名。该整段持锁方案随后由三阶段乐观事务取代。
- 2026-07-10：完整终审阻断修复——保存改为三阶段乐观事务；validator 全程锁外；冲突保留并发修改；NUMBER 单 candidate 规范化；prepared state/content 引用交换提交；通知重入、异常隔离与 UI 全字段回读闭环。
- 2026-07-10：4.5.2 最小纠偏——通知状态改为 manager 级跨线程可见并在 final verify 锁内复核；SIMPLE_LIST 增加 `List<String>` 保存门禁；事务辅助方法收窄为包级。
- 2026-07-10：**4.5.3-beta-1 纠偏**（`fix/config-stale-draft-recovery`，预发布非稳定 4.5.3；稳定公共能力目标 4.6.0）：
  - ConfigManager 每实例 owner token；`openDraft` 绑定；`save` 在任何 base/validator/persistence 前拒绝 foreign/unbound draft → `DRAFT_OWNER_MISMATCH`（requiresReload=false）；`DraftBuffer.hasSameOwner` 不泄露 token。
  - `replaceDraft` 要求同 owner identity + schema 路径/类型兼容；失败前完成校验且 adapter/Signals 不变。
  - **I3**：SimpleList/FontSort render 构建期禁止 Signal.set / seedPresentation / validation 清理；prefill 为局部只读初值；首次真实交互 `onFieldEdit`；`seedPresentation` 若保留不在 render 调用且不清算 validation。
  - 三阶段冲突测试按窗口精确断言 ConflictType；真实 harness 点击 reload 按钮。
  - 为何不是稳定 4.5.3：用户拍板连续 beta 迭代，稳定公共能力归 4.6.0。
