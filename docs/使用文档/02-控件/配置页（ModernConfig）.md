# 配置页（ModernConfig）

本文说明如何用新架构配置页接入游戏内配置界面。基于当前源码：`club.heiqi.config.ui` + 本 mod 接入层 `club.heiqi.uilib.config.modern`。

> **已删除，勿再引用**：旧 `ForgeConfigTemplateScreen` 整支、远程配置同步 API（`ConfigTemplateSyncManager` / `RemoteConfigDocumentPages` / `ConfigSync*`）均已移除。

## 架构一览

```
Schema (ConfigSchema)  →  ConfigManager.bootstrap(file, schema[, DraftValidator])
                              ↓
                    ConfigUI.buildScreen(manager, input, registryCustomizer, restorePolicyCustomizer)
                              ↓
                         ConfigScreen（scene 新栈）
                              ↓
              McScreenBridge / ModernConfigScreen 包成 MC GuiScreen
```

三态物理隔离：

| 态 | 职责 |
|---|---|
| **Authority** | 内存权威快照；游戏运行时读取唯一来源 |
| **DraftBuffer** | 打开配置页时从 Authority 深拷贝的草稿；编辑只改草稿 |
| **Persistence** | YAML 文件，只持久化权威值 |

`DraftSignalAdapter` 把 DraftBuffer 每字段镜像为 uilib `Signal`，供控件双向绑定；真值仍在 DraftBuffer。

### 提交前校验（DraftValidator）

`ConfigManager.save` 使用三阶段乐观事务 + 参与式写前检测：

1. 按 manager → draft 锁序捕获一次 candidate：revision、**事务 base**（open 时 Authority）、规范化 proposed 全表；Authority 已不等于 base 时立即 `STALE_DRAFT_BASE`
2. 完全锁外执行内置校验、只读 `DraftView` custom 校验，并预制 Authority/Draft Map 与完整持久化文本
3. 按相同锁序复锁，复核 revision 与 Authority==base，并复核 **capture 冻结的 expected 基线**仍等于 manager 当前 expected；再与该冻结基线精确字节比较（同 classloader 参与式 writer 串行）；冲突映射 `ConflictType` 且保留实际并发修改，无冲突才写盘并引用交换提交（推进 expected/base/current/draft）。reload 推进 expected 后，旧 save 的 prepared **不得**拿新 expected 写盘

成功释放锁后恰发布一次 `BATCH_SAVE`。同源旧 draft 在其他 draft 已保存后属于 stale，保存返回 `STALE_DRAFT_BASE`（`requiresReload=true`），不覆盖先提交值。外部改盘/删除/目录替换返回 `CONFIG_FILE_CHANGED_SINCE_LOAD`（`requiresReload=true`）。

> **beta 口径**：写前检测是同 JVM classloader 内**参与式 writer** 串行 + 精确字节比较 + atomic replace，**不是** OS 级跨进程 CAS；外部 writer 的 compare→replace 窗口不承诺；硬链接/inode 写域不保证（仅 canonical path 语法别名）；same-byte ABA 允许。

#### 冲突类型与恢复（4.5.3-beta-1）

| 类型 | 是否须 reload | 用户侧 |
|---|---|---|
| `STALE_DRAFT_BASE` / `AUTHORITY_MODIFIED_DURING_SAVE` / `CONFIG_FILE_CHANGED_SINCE_LOAD` | 是 | 编辑保留供查看；保存禁用；点「丢弃编辑并重新加载」（`reloadDraftFromDisk`） |
| `DRAFT_MODIFIED_DURING_SAVE` / `SAVE_DURING_NOTIFICATION` | 否 | 保留草稿，可重试 |
| `DRAFT_OWNER_MISMATCH` | 否 | 程序员错误，修正调用方 |
| 普通字段校验 | 否 | 字段红字 + 摘要 |

- UI **必须**读 `SaveOutcome.conflictType()` / `requiresReload()` / `ConfigReloadException.reason()`，**禁止**匹配英文错误串
- **不得**自动 reload、自动重试、静默覆盖 Authority；reload 会**丢弃**当前编辑并从磁盘重载
- `reloadDraftFromDisk` **三阶段**：capture（Authority 深快照 + expected + disk）→ 锁外完整校验（内置+custom）→ 写域 monitor 内复核后 commit；通过后更新 Authority/expected 并发布 **`RELOAD`**（不伪装 BATCH_SAVE）；校验/IO/冲突失败零推进并保留 UI 编辑（结构化 VALIDATION/IO/CONFLICT）
- BOOLEAN 严格 `Boolean`；STRING/NUMBER/LIST 类型一致；非法 disk 值 reload/save fail；不得静默 NUMBER→0.0
- `ConfigSaveListener`：event 回调不直接 Bridge/font；`MainThreadDispatcher` CLIENT + latest-wins 主线程回灌
- `DraftSignalAdapter` 契约为 **UI 主线程** mutator；跨线程抛 `IllegalStateException`
- 诊断日志含 `conflictType`，不含字段敏感值
- `ConfigScreen` 构造要求 `manager.owns(adapter.draft())`
- 故障注入测试缝在 `Persistence` 包级；`AtomicFileWrites` 仅真实写 API



接入示例：

```java
// 二参：向后兼容，等价 DraftValidator.noop()
// ConfigManager mgr = ConfigManager.bootstrap(file, schema);

// 三参：挂载提交前钩子（validator 不可 null；入参为只读 DraftView，仅 schema 字段）
ConfigManager mgr = ConfigManager.bootstrap(file, schema, view -> {
    Object host = view.getDraft("server.host");
    if ("blocked".equals(host)) {
        return ValidationResult.error("server.host", "host not allowed");
    }
    return ValidationResult.ok(); // 无错必须 ok()，禁止返回 null
});
```

契约要点：

- **禁止用 null 表示无校验**；无逻辑时传 `DraftValidator.noop()`
- 入参是 **深度只读 `DraftView`**（仅 schema 字段：`getDraft` / 深度冻结 `draftSnapshot` / `fieldPaths`；**无** `schema()`，避免 defaultValue 容器泄漏）：List/Map/数组递归 unmodifiable
- `DraftView` 是 validator 唯一稳定输入；validator 不得捕获并写来源 manager/draft/Authority/Legacy，也不得调用同一 manager 的 save/flushRaw。框架不声称物理上无法旁路修改，检测到修改时按并发冲突统一 INVALID 并保留实际修改
- validator 返回 `null`、抛 `RuntimeException`、或视图构造失败时 Manager **fail-closed** 为 INVALID，错误 path 为 `_config`
- 字段错误与全局错误合并时不同 path 均保留；同 path 优先内置消息
- NUMBER 合法数字字符串保存时统一为 `Double`，validator、Authority、draft/current 与磁盘共享该规范化 candidate；非法/NaN/Infinity 拒绝保存
- SIMPLE_LIST 保存值必须是 `List<String>`（**严格拒绝** null 元素，每个元素须为非 null String）；标量、`List<Integer>` 或混合元素列表均 INVALID，bridge 不会事后字符串化
- **UI**：`ConfigScreen` 在 INVALID 与成功保存后先从 DraftBuffer 全字段回读 Signal；字段红字走 `errorSignal`，`_config` 计入 `errorCount` 与保存反馈摘要；**冲突**走 `conflictType`/`requiresReload`，不注入字段 error；用户再编辑任一字段会清空普通提交错误（requiresReload 冲突须显式 reload）；Signal 中 List 为只读值
- 发现态列表 prefill（fontSort 等）：Authority 空时只展示，不进 candidate/YAML；首次**真实控件**编辑/删除/拖拽才写 draft
- 持久化优先使用同目录 temp + ATOMIC_MOVE；平台不支持时退回非严格原子的整文件 replace；写前参与式精确字节检测
- `BATCH_SAVE` **与** `RELOAD` 通知期间，同一 manager 的 save/flushRaw/reload 与 Legacy mutation 均稳定 `SAVE_DURING_NOTIFICATION` 且不再嵌套发布事件；`openDraft` 只读仍可完成
- schema 随 bootstrap 冻结（constraints/default/widget）；无 manager 内 schema reload
- 自写 event 消费者：**必须**处理 `RELOAD`（与 BATCH_SAVE 同样从 Authority 回灌或按业务分流）；不得依赖「reload 伪装 BATCH_SAVE」。Qz-Miner 适配留 UILib beta 发布后接入


## 入口 API

通用门面（`club.heiqi.config.ui.ConfigUI`）：

```java
ConfigScreen screen = ConfigUI.buildScreen(
        manager,
        input,                          // PlatformInputSource，可为 null（headless）
        registryCustomizer,             // Consumer<FieldRendererRegistry>，不可 null
        restorePolicyCustomizer);       // Consumer<FieldRestorePolicy>，不可 null
```

- 2 参 / 3 参重载委托到 4 参，未定制时传空 lambda。
- `manager` 不可 null。
- 本方法只构建 `ConfigScreen`，不切换 MC `GuiScreen`；宿主桥接由调用方完成。

本 mod 同步入口（`ModernConfigEntry`）：

```java
GuiScreen screen = ModernConfigEntry.createScreen(parent);
// 或命令入口：ModernConfigEntry.open();  // 经 UiScreenManager 延后切屏
```

Forge Mods 按钮：`ModGuiFactory` → 反射 `ModConfigGui(GuiScreen)` → `ModernConfigEntry.createScreen`。

## Schema

用 `ConfigSchema.builder(modId)` DSL 声明 section 与字段：

```java
ConfigSchema schema = ConfigSchema.builder("my-mod")
        .title("My Mod 配置")
        .section("general")
            .title("General")
            .bool("enabled").defaultValue(Boolean.TRUE)
                .label("启用").helper("总开关").build()
            .number("scale").defaultValue(Double.valueOf(1.0)).range(0.5, 2.0)
                .label("缩放").build()
            .choice("mode").options("a", "b", "c").defaultValue("a")
                .label("模式").build()
            .string("name").defaultValue("")
                .label("名称").build()
            .simpleList("tags").defaultValue(new ArrayList<String>())
                .label("标签").build()
            .structuredList("rules", Values.object(
                    Values.member("id", Values.string()),
                    Values.member("members", Values.list(Values.string()))))
                .label("规则").build()
        .endSection()
        .build();
```

`FieldType` 当前可用：`STRING` / `NUMBER` / `BOOLEAN` / `CHOICE` / `SIMPLE_LIST` / `STRUCTURED_LIST`。
`STRUCTURED_LIST` 的值由递归 `ValueSpec` 描述，默认表达
`List<Object{id:String,members:List<String>}>`；未知 object member 在读取、草稿和写盘时保留。
Authority/YAML 使用严格节点类型，Draft 校验错误路径可精确到
`general.rules[0].members[1]`。默认 renderer 提供增删、上移/下移、标量编辑、`List<String>` 编辑、
`List<CHOICE>` 受控多选和字段恢复默认。choice 按 schema 顺序显示；未知字符串标记“（已失效）”且只能删除。
对象列表可在 object spec 上用 `withIdentityMember("id")` 声明唯一身份，以支持 reset/reload
后的行复用；renderer 还保留当前列表实例内仍存活 key 的有限 identity lineage，当前唯一 identity
优先、历史唯一 identity 次之。重复、空、历史多 key 或已占用 identity fail-closed，不把业务 id
直接当作 scene key，也不猜测 refocus。
其它复杂类型（`LONG_TEXT` / `TABLE` / `KEY_VALUE_MAP` 等）仍未接默认 renderer。

字段 path 格式：`section.field`（点号分隔，不含 schema 名），例如 `fontSystem.fontSort`。

## 默认 FieldRenderer

`FieldRendererRegistry.defaultRegistry()` 预注册：

| FieldType | 默认控件 |
|---|---|
| BOOLEAN | `SceneToggle` |
| STRING | `SceneTextInput` |
| NUMBER | 声明 `SliderSpec` → `SceneSlider`；否则 `SceneTextInput` |
| CHOICE | 选项 ≤4 → `SceneSegmented`；>4 → `SceneSelect` |
| SIMPLE_LIST | `SceneSimpleList`（默认可增删，拖拽需 path 覆盖） |
| STRUCTURED_LIST | keyed 对象列表（增删、上移/下移、标量、`List<String>` 与 `List<CHOICE>` member 编辑） |

外壳统一经 `FieldShellBinder` + `FormFieldShell`（标题 / helper / dirty / error）。

## path 覆盖与恢复默认

path 优先于 type。在 `registryCustomizer` 里：

```java
registry.registerPath("fontSystem.fontSort", new FontSortFieldRenderer(...));
registry.registerPath("fontSystem.characterFontRules", new CharacterRuleFieldRenderer());
```

本 mod 示例见 `ModernConfigEntry.configureFieldRenderers`。

恢复默认策略（`FieldRestorePolicy`）：

```java
policy.skip("fontSystem.characterFontRules");           // 恢复时跳过
policy.custom("fontSystem.fontSort", adapter -> { ... }); // 自定义写回
// 未声明 path → adapter.resetFieldToDefault(path)
```

优先级：`skip` > `custom` > 默认 `resetFieldToDefault`。示例见 `ModernConfigEntry.configureRestorePolicy`。

## 宿主桥接

1. `ConfigUI.buildScreen` → `ConfigScreen`（实现 `UiSurface`）
2. `new ModernConfigScreen(parent, configScreen)` — 继承 `McScreenBridge`，接入 MC `GuiScreen` 生命周期
3. `McScreenBridge` 是 `SceneLwjgl3ifyTextBridge` 的唯一 host owner：成功注册后启用完整 String
   external text mode；不可用时安全降级到 char 输入，关闭时 finally 注销并复位
4. `Minecraft.displayGuiScreen(...)` 或 `UiScreenManager.enqueue` 延后开屏

他 mod 可自写 `McScreenBridge` 子类，不必用 `ModernConfigScreen`；不应在子类中再次手工注册文本桥。

## 本 mod 配置文件

- 路径：`config/qzuilib-modern.yaml`（相对 `mcDataDir`）
- 格式：YAML（`ConfigManager.bootstrap` 固定 `ConfigFormat.YAML`）
- 旧 Forge `.cfg` **已废弃且不互通**；老配置不会自动迁移，缺省回落 schema 默认值

## 他 mod 最小接入

1. **声明 Schema**：`ConfigSchema.builder("your-mod-id")...build()`
2. **Bootstrap**：`ConfigManager.bootstrap(new File(mcDataDir, "config/your-mod.yaml"), schema)`；需要跨字段业务校验时用三参 `bootstrap(file, schema, DraftValidator)`（见上文「提交前校验」）
3. **构建屏**：`ConfigUI.buildScreen(manager, input, reg -> { /* 可选 registerPath */ }, policy -> { /* 可选 skip/custom */ })`
4. **桥接**：自写 `extends McScreenBridge` 的 GuiScreen，构造器收 `parent + ConfigScreen`；Forge 侧用 `IModGuiFactory` 反射合法单参 `(GuiScreen)` 中转类即可

可选：在 `manager.eventBus().subscribe(...)` 监听 `BATCH_SAVE` **与** `RELOAD`，把 Authority 值回灌到运行时字段（本 mod 参考 `ConfigSaveListener` + `ConfigValueBridge`）。忽略 `RELOAD` 会导致磁盘重载后运行态陈旧。

## 相关源码

| 路径 | 职责 |
|---|---|
| `club.heiqi.config.ui.ConfigUI` | 门面 |
| `club.heiqi.config.ui.ConfigScreen` | 配置页 scene 骨架 |
| `club.heiqi.config.ui.field.FieldRendererRegistry` | type / path 渲染器 |
| `club.heiqi.config.ui.FieldRestorePolicy` | 恢复默认策略 |
| `club.heiqi.config.runtime.ConfigManager` | bootstrap / 草稿 / 保存事务 |
| `club.heiqi.config.runtime.DraftView` | 提交前只读草稿视图 |
| `club.heiqi.config.runtime.DraftValidator` | 提交前自定义校验钩子（可选；入参 DraftView） |
| `club.heiqi.config.runtime.ValidationResult` | 校验结果；`merge` / `summary` |
| `club.heiqi.uilib.config.modern.ModernConfigEntry` | 本 mod 接入样板 |
| `club.heiqi.uilib.config.ModConfigGui` | Forge guiFactory 中转 |
| `club.heiqi.uilib.ui.screen.McScreenBridge` | MC GuiScreen 宿主基类 |

诊断层说明见 `docs/诊断层/Config模块.md`。迁移决策档案见 `docs/反馈层/决策/config-migration-modern.md`。
## 配置回灌与 disk 严格类型（beta）

- ConfigSaveListener 经 ModernConfigApplyCoordinator 全局协调：每次打开配置页注册不可变 Registration（generation+manager 原子发布）；仅当前 Registration 事件可 submit；register/submit 同一线性化域，stale 不得覆盖新世代；静态队列 Runnable 不闭包旧 listener；协调器持最新 manager 作为 UILib 全局配置当前 Authority
- **no-spin / next-drain**：`MainThreadDispatcher` 使用 **lock+ArrayDeque 批次交换**（禁止 `queue.size()` 快照预算）；drain 在 lock 内 swap 旧 batch，期间 enqueue 只进新队列、绝不本次消费，下一 tick 再跑。per-side drain owner CAS：第二 drainer 返回 0。一次 CLIENT dispatcher drain 中 coordinator task 最多执行一次；owner true 时 submit 只更新 pending；失败/剩余 pending 由下一 CLIENT END 的 `retryPendingOnce` 再排（owner false 才 enqueue，禁止与已有 queued 重复、禁止同 drain 自旋）。AssertionError / ErrorSink 抛 Assertion 时旧 batch 尾重排后 rethrow
- apply 前取走 pending；失败仅无更新时 reoffer，新事件优先；last snapshot 仅成功后推进；测试 hook 无论 Runtime/Assertion/Error 均无条件释放 enqueueOwner
- disk / legacy raw 路径按 FieldType 严格检查 NodeType（NUMBER 拒绝 quoted 字符串等）；schema 字段 `setRawJson` 错型抛 ConfigException 且 Authority/typed/expected/disk 零变化；UI NUMBER 字符串解析仅限 DraftBuffer 提交边界
- **section raw overlay**：schema section 内未知 MAP 子树保留；**schema 优先仅限 MAP overlay**——section 为 scalar/list 时 bootstrap/reload fail-closed，禁止静默默认覆盖
## 搜索选择器 beta 接入

结构化对象 member 可用 `Values.widget(valueSpec, Values.searchPicker(editorId, maxItems))`
声明搜索选择器，并通过 `ConfigUI.buildScreen` 5 参重载最后一个 customizer 注册对应
`ValueEditorProvider`。装配顺序是打开 draft、创建 adapter、定制并冻结每 screen editor registry、
创建默认字段 registry、定制字段 renderer、定制恢复策略、创建 screen。

声明 picker 却缺 provider 会在构建字段时 fail-fast；provider 搜索及 codec 异常只降级当前控件，
不得以 null 擦除 Draft。4.5.3-beta-7 的 codec 无状态地接收当前受控值，领域错误按阶段展示；
raw member 与 picker 可同时存在，结构化列表标题优先使用稳定 identity。beta-10 的选择 API 为
`ALL` 与 `SELECTED`：后者用 checkbox 选择 1..N 个唯一 key，空草稿禁确认；当前未枚举的旧 key
显示为可移除的通用失效项且默认无损保留。取消、Escape 或点击外部均不写 Draft。SearchPicker、
ValueEditorProvider 与 ConfigUI editor registry 都属于预发布 API，不在 LTS 稳定清单。

### `List<String>` 成员绑定

整组值仍默认走兼容的 `SINGLE_VALUE` + `Codec` 路径。需要逐项选择时必须显式声明
`SearchPickerSpec.BindingMode.LIST_MEMBERS`，并让对应 provider 的 codec 实现 `ListMemberCodec`：

```java
Values.widget(
        Values.list(Values.string()),
        Values.searchPicker("my-mod:item", 64,
                SearchPickerSpec.BindingMode.LIST_MEMBERS));
```

- 当前 raw 列表成员会显示在 `CANDIDATES` portal；每个 `SceneSimpleList.ListItem.id` 是列表内稳定身份，candidate key 不承担成员身份，因此多个 raw 项选择同一 candidate 时仍分别显示和编辑，不自动合并。
- 字段关闭态只显示当前成员规则摘要与 `Manage`；raw 列表默认折叠在高级区域，继续作为直接修正/删除入口。
- 管理 portal 以 480px 为目标宽度、360px 为最小宽度，并与视口边缘保持至少 8px；高度不超过可用视口，超出内容在 portal 内滚动。搜索框位于 portal 顶部；“当前成员”按空间动态显示且最多 3 行，“搜索结果”最多 5 行。
- 点击某成员后确认只按稳定 id 替换该目标项；从新增入口确认只在末尾追加一项。Picker 内删除先进入待确认态，用户再次确认后才按稳定 id 删除；取消确认零写。raw 高级区域仍可用于修正或删除。
- 未枚举 candidate 保留原 selection。无法解码的 malformed raw 与 duplicate candidate 只显示通用提示，不回显原始坏值，也不自动合并重复项；原列表顺序与成员身份不变。只有用户明确编辑、两步确认删除或使用 raw 高级入口处理目标项时才改变该项；操作其它项不得改写它。
- active overlay 打开期间，Tab/Shift+Tab 只在当前顶层 portal 内循环；确认、取消、Escape 或点击外部关闭后，焦点恢复到打开前的 `Manage` 或成员触发控件。Escape 与点击外部关闭不写 Draft。
- `LIST_MEMBERS` 不会静默回退到整值 `Codec`；provider codec 未实现 `ListMemberCodec` 时字段构建 fail-fast。编码异常、null、非字符串结果或确认前目标已删除时均零写。

以上自动化交互与布局回归已通过；消费仓仍需实机确认 portal 尺寸、滚动、焦点与领域文案，当前不视为实机验收完成。
