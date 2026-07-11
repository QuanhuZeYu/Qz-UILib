# Config 模块

> 现状文档（对齐新架构）。历史迁移叙事见 `docs/反馈层/决策/config-migration-modern.md`，勿把旧 `ConfigNode` 工厂式用法当作配置页主路径。

## 1. 定位

| 包 | 角色 |
|---|---|
| `club.heiqi.config` | 独立配置模块（schema / runtime / 底层节点读写），可脱离 UI 使用 |
| `club.heiqi.config.ui` | 配置页 UI：`ConfigUI` / `ConfigScreen` / `DraftSignalAdapter` / `FieldRenderer*` |
| `club.heiqi.uilib.config.modern` | 本 mod 接入层：schema 声明、bootstrap、path renderer、GuiScreen 桥、保存回灌 |

配置页跑在 **scene 新栈**（`ConfigScreen` + `FormPageShell` / `FormFieldShell`），不经已删除的 Forge 配置模板页。

## 2. 三态四层

**三态**（物理隔离）：

- **Authority**：内存权威快照；游戏读配置的唯一来源
- **DraftBuffer**：打开配置页时从 Authority 深拷贝；编辑只改草稿
- **Persistence**：YAML 文件，只存已提交权威值；写盘失败时内存提交尚未发生

**四层 / 协作者**：

- **schema**：`ConfigSchema` / `SectionSpec` / `FieldSpec` / `FieldType` / `ValueKind` / `ValueSpec` / `Values` / 约束与 widget 声明
- **runtime**：`ConfigManager` / `Authority` / `DraftBuffer` / `DraftView` / `Persistence` / `ConfigEventBus` / `LegacyAdapter` / `DraftValidator`
- **ui**：`ConfigUI` / `ConfigScreen` / `DraftSignalAdapter` / `FieldRestorePolicy` / `field.*` renderer
- **接入**：本 mod `uilib.config.modern`（及他 mod 自写桥）

保存事务（`ConfigManager`）固定锁序为 Authority/manager → draft，但仅在 capture 与 commit 两个短阶段持锁：capture 一次取得 revision、**事务 base**（open 时 Authority 深拷贝，独立于 current）和 NUMBER 规范化 proposed 全表；内置/custom validator、Authority/Draft Map 与持久化文本预制完全锁外；复锁后验证 revision 与 Authority==base，再经 **写前检测**（`ConfigFileSnapshot` 精确字节 + 同 classloader **参与式 writer** 串行 compare+atomic replace）写盘；冲突映射为结构化 `SaveOutcome.ConflictType`（仍 `INVALID`）且保留实际并发修改，无冲突才写盘并引用交换提交（推进 expected/base/current/draft）。stale draft 不得覆盖先提交值。成功写盘与引用交换后在事务锁内建立 manager 级通知状态并封锁 Authority mutation，释放锁后恰发布一次 `BATCH_SAVE`；同一 manager 通知期间任意线程 save/flushRaw/reload 与 Legacy raw mutation 均 `SAVE_DURING_NOTIFICATION` / `ConfigConflictException`，`openDraft` 仍可完成。SIMPLE_LIST 保存候选只接受每个非 null 元素均为 String 的 List。BOOLEAN 严格要求 `Boolean`；STRING/NUMBER/LIST 亦复核类型一致，非法 disk reload/save fail；Authority 解析不得把非法 NUMBER 静默 0.0 后通过 reload。INVALID / IO_FAILED / 写前检测冲突不提交本次 Authority/current。Persistence 的 ATOMIC_MOVE fallback 只是非严格原子的整文件 replace。**仅保证同 classloader 参与式 writer 串行+写前检测**；外部 writer 的 compare→replace 窗口不承诺；**不是** OS 级跨进程 CAS（beta 口径）。`writeAll` 为低级无 expected 比较写（进写域 monitor 串行，**不能**称 CAS）；生产 ConfigManager 不旁路。写域身份仅 canonical path 语法别名，硬链接/inode **不保证**。same-byte ABA 允许。Authority/Legacy/openDraft/flushRaw/reloadDraftFromDisk 共享事务锁域，容器与 `ConfigNode` 读出口防御复制。schema 随 bootstrap 冻结，无 manager 内 schema reload。

**reload 三阶段**：capture（manager 锁内记 Authority 深快照 + expected 基线 + disk snapshot）→ 锁外完整内置+custom 校验 → commit（manager 锁 + 参与式写域 monitor 复核 Authority/expected/disk 仍等基线后原子更新并 `RELOAD`）。冲突抛 `ConfigReloadException`（VALIDATION/IO/CONFLICT），零推进零事件。

**冲突与 UI 恢复（4.5.3-beta-1）**：


| ConflictType | requiresReload | UI 行为 |
|---|---|---|
| `STALE_DRAFT_BASE` | true | 保留编辑供查看；保存禁用；普通编辑不清冲突；「丢弃编辑并重新加载」→ `reloadDraftFromDisk` + `replaceDraft` |
| `AUTHORITY_MODIFIED_DURING_SAVE` | true | 同上 |
| `CONFIG_FILE_CHANGED_SINCE_LOAD` | true | 写前检测：外部编辑/删除/目录/不同内容重建；同上 |
| `DRAFT_MODIFIED_DURING_SAVE` | false | 保留草稿；可重试反馈；不要求 reload |
| `SAVE_DURING_NOTIFICATION` | false | 同上（BATCH_SAVE **与** RELOAD 通知期） |
| `DRAFT_OWNER_MISMATCH` | false | 程序员错误：foreign/unbound draft；Authority/YAML 零副作用 |
| 普通校验失败 | false | 字段红字 / errorCount / 摘要（非冲突） |

UI 必须读 `conflictType`/`requiresReload`/`ConfigReloadException.reason()`，禁止英文诊断串匹配。冲突不注入字段 error/errorCount。不得自动 reload/重试/静默覆盖 Authority。`reloadDraftFromDisk`：三阶段 capture→完整校验→写域 commit 后才更新 Authority/expected；成功发布 `RELOAD`（**不**伪装 `BATCH_SAVE`）；失败零推进。`ConfigSaveListener` 对 BATCH_SAVE 与 RELOAD **不**在 event 回调直接 Bridge/font：经 `MainThreadDispatcher` CLIENT 队列（**lock+ArrayDeque batch swap**，禁止 size 快照 / `queue.size()` 预算）latest-wins 主线程回灌；他 mod 消费者必须处理 RELOAD。故障注入缝在 `Persistence` 包级（非 `AtomicFileWrites` 生产 API）。

**section raw overlay**：schema section 内未知字段/子树以 MAP overlay 保留；序列化 raw 为底 + schema typed 覆盖。**schema 优先仅限 MAP overlay**——disk 上 schema section 为 scalar/list 时 bootstrap/reload **fail-closed**（`ConfigException` VALIDATION），禁止静默用默认覆盖整段。`STRUCTURED_LIST` 以递归 `ValueSpec` 严格校验 `List<Object>`，对象未知 member 保留并继续写回。


**草稿所有权**：每 `ConfigManager` 实例持不可伪造 owner token；`openDraft` 创建绑定该 token 的 `DraftBuffer`；`save` 在任何 base/validator/persistence 前拒绝非本 manager draft。未绑定 owner 的外部 Draft（`DraftBuffer.from(Authority)`）不得写任意 manager。`replaceDraft` 要求与 adapter 原 draft 同一 owner（`hasSameOwner`，不开放 token 对象）且 schema 路径/类型兼容（`SchemaReplaceCompatibility`）。

**DraftSignalAdapter 主线程契约**：构造捕获 ownerThread；所有 mutator 在读写前 assertOwnerThread；违规 `IllegalStateException`（含线程名）且状态零变化。不加内部锁，不假装 scheduler 线程安全。

**I3 presentation 规则**：SIMPLE_LIST / FontSort 在 Authority 为空时 prefill 仅为 renderer/bridge **局部只读初始投影**；render 构建期禁止 `Signal.set`、adapter seed、validation/feedback 清理。FontSort 在 screen-open、coordinator initial apply 前冻结 discovered snapshot，merge/filter/reload 均只更新单屏 presentation；用户首次合法索引移动、拖拽 UP 或显式恢复默认才经真实控件语义调用 `onFieldEdit` 写入完整 merged 列表并 dirty=true。玩家不能添加字体、删除字体或改名；筛选为大小写不敏感 contains 投影，索引始终作用全量顺序。`seedPresentation`/`seedFieldBaseline` 若保留：不得在 render 调用且不得清 validation；`setDraftAndCurrent` deprecated 且不改事务 base。

UI 在 INVALID/成功后全字段回读 DraftBuffer，提交校验 Signal 是错误展示与 `canSave` 的唯一 UI 真值，字段容器 Signal 深度只读。`canSave` = dirty && !hasError && !requiresReload。


## 3. U1 / U2 / U3 现状

| 项 | 状态 |
|---|---|
| U1 `FormFieldShell` / `FormTheme` 下沉 uilib 通用 form 包 | **已落地**（`ui.scene.form`） |
| U2 `FormPageShell` 组合式页骨架，`ConfigScreen` 复用 | **已落地** |
| U3 `FieldShellBinder` + `FieldRenderSupport` 样板收敛 | **已落地** |

字段外壳经 `FieldShellBinder.build` 收口；主题经 `ConfigTheme.asFormTheme()` 桥到 `FormTheme.defaultDark()`。

## 4. 包-职责表

| 路径 | 职责 |
|---|---|
| `config.schema` | 不可变 Schema DSL 与字段元数据 |
| `config.runtime` | bootstrap、草稿、保存事务、`DraftValidator` 提交前钩子、事件总线（**零 uilib 依赖**） |
| `config.ui` | 配置页门面与屏幕骨架 |
| `config.ui.field` | `FieldRenderer` 接口、默认 registry、各类型 renderer、path 专用 renderer |
| `config.ui.theme` | `ConfigTheme`（桥接 FormTheme） |
| `uilib.ui.scene.form` | 通用表单外壳（无 config 业务 path） |
| `uilib.config.modern` | 本 mod YAML 路径、schema、Bridge、SaveListener、`ModernConfigScreen` |
| `uilib.config.ModConfigGui` / `ModGuiFactory` | Forge 反射中转入口 |

底层仍保留 `Config` / `ConfigNode` / JSON·YAML Loader 等通用节点 API（非配置页主路径）。

## 5. 关键 API 入口

| API | 说明 |
|---|---|
| `ConfigUI.buildScreen(manager, input, registryCustomizer, restorePolicyCustomizer)` | 构建 `ConfigScreen`；customizer 不可 null |
| `FieldRendererRegistry.defaultRegistry()` / `register` / `registerPath` | type 默认与 path 覆盖（path 优先） |
| `DraftSignalAdapter` | DraftBuffer ↔ Signal 镜像；`onFieldEdit` / dirty / error / canSave |
| `FieldRestorePolicy.skip` / `custom` | 恢复默认逐字段策略 |
| `ConfigManager.bootstrap(file, schema)` | 启动加载（YAML）；委托 no-op validator，向后兼容 |
| `ConfigManager.bootstrap(file, schema, DraftValidator)` | 同上 + 提交前自定义校验（validator 不可 null） |
| `ConfigManager.reloadDraftFromDisk()` | 候选完整校验后原子刷新 Authority+expected，返回同 owner 新 Draft；成功发 `RELOAD`（不伪装 BATCH_SAVE） |
| `ConfigManager.owns(DraftBuffer)` | 公开身份查询，不泄露 owner token |
| `ConfigFileSnapshot` | canonical 字节快照；写前检测 expected；仅 path 语法别名 |
| `ConfigConflictException` | flushRaw/reload/legacy 冲突或通知期（extends ConfigException） |
| `DraftView` / `SnapshotDraftView` | 提交前只读快照（仅 schema 字段，无 schema()）；`ValueCopy` 深度冻结 |
| `DraftValidator` / `DraftValidator.noop()` | 锁外提交前钩子；只读 DraftView 是唯一稳定输入，无逻辑用 noop，禁止 null |
| `ValidationResult.merge` / `summary` | 合并错误；UI 反馈摘要 |
| `DraftSignalAdapter.setSubmitValidation` | 提交错误接入 errorSignal / errorCount |
| `SchemaReplaceCompatibility` | 同 owner 下 schema 路径/类型纯判定 |
| `ModernConfigEntry.createScreen(parent)` | 本 mod 同步开屏样板 |

默认 type→控件：BOOLEAN→Toggle，STRING→TextInput，NUMBER→Slider\|TextInput，CHOICE→Segmented\|Select，SIMPLE_LIST→SceneSimpleList，STRUCTURED_LIST→keyed 对象列表编辑器；SIMPLE_LIST 保存值契约为 `List<String>`（**严格拒绝** null 元素，每个非 null 元素须为 String）。
本 mod path 覆盖示例：`fontSystem.fontSort` → `FontSortFieldRenderer`；`fontSystem.characterFontRules` → `CharacterRuleFieldRenderer`（见 `ModernConfigEntry.configureFieldRenderers`）。

**注意**：`ConfigUI` / 新架构配置页 API **尚未纳入** LTS 稳定清单；接入说明见 `docs/使用文档/02-控件/配置页（ModernConfig）.md`。

## 6. 与 Forge Configuration 的区别

| | Forge Configuration | 本模块配置页路径 |
|---|---|---|
| 格式 | `.cfg` 专有 | YAML（schema 驱动） |
| 结构 | 扁平 category/property | 嵌套 section + 点号 path |
| UI | 默认 GuiConfig / 已删旧模板 | scene `ConfigScreen` + FieldRenderer |
| 数据源 | 与旧 `.cfg` 绑定 | Authority + YAML；本 mod `.cfg` 已废弃不互通 |
| 依赖 | Forge | 核心层独立；UI 层软依赖 uilib scene |

## 7. 已知缺口

- 远程配置同步整支已删（含服务端远程配置页）；重建需求见决策 `config-migration-modern`
- 复杂 `FieldType`（枚举注释中的 LONG_TEXT / TABLE / KEY_VALUE_MAP 等）**未接**默认 renderer；`STRUCTURED_LIST` 已由递归 `ValueSpec` 接入
- 业务 path 专用 renderer 原则：**应在接入层**；`FontSortFieldRenderer` 保持 config UI 通用实现，接入层只传入 frozen discovered snapshot，运行时不重新发现字体。
- 使用文档中部分入门示例仍可能描述已移除的 document 栈 API，以源码为准逐步收敛

## 8. 维护规则

- **config 核心层（schema / runtime / 节点读写）零 uilib 硬依赖**
- **field 通用默认 renderer 勿塞 mod 专属 import**；业务 path 覆盖放接入层 customizer
- `FormFieldShell` / `FormPageShell` / `FormTheme` 保持只吃通用类型（String / Signal / Supplier / FormTheme），不 import config schema
- 新增 FieldType 或默认 renderer 须补测试；破坏性 API 变更评估 LTS 清单与使用文档
- `STRUCTURED_LIST` 的 schema/runtime 入口必须保持零 scene 依赖；scene 默认 renderer 只能经 `DraftSignalAdapter` 写回

### 收口补记（回灌/基线/严格类型）

- save/flush capture 冻结 expected 基线；commit 复核且 cas 用该基线
- disk / legacy raw 严格 NodeType；schema 字段 setRawJson 错型零写入；UI NUMBER 字符串仅 DraftBuffer
- ModernConfigApplyCoordinator：**单一 monitor** 线性化（generation++ 在锁内；同线程 reentrant register fail-fast；无 lease/wait）+ no-spin + tick `retryPendingOnce`；`TEST_BEFORE_OWNER_RELEASE` 嵌套 finally 无条件 `enqueueOwner=false`；MainThreadDispatcher **lock+ArrayDeque batch swap** + per-side drain owner CAS（第二 drainer 返回 0）+ AssertionError/ErrorSink Assertion 尾重排 + 任务隔离
- section raw overlay：**schema 优先仅限 MAP**；scalar/list section bootstrap/reload fail-closed
