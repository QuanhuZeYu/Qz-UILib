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

`ConfigManager.save` 在写盘前固定顺序：

1. 内置 `DraftBuffer.validateAll()`（schema 字段约束）
2. 构造只读 `DraftView` 快照（`SnapshotDraftView`），调用 `DraftValidator.validate(view)`
3. 两组 `ValidationResult` 合并；**任一有错 → `SaveOutcome.INVALID`**，Authority、磁盘、draft current、事件总线均不变化
4. 全部通过后才 `applyAll` → 写盘 → `commitDraftToCurrent` → `BATCH_SAVE`

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
- validator 返回 `null`、抛 `RuntimeException`、或视图构造失败时 Manager **fail-closed** 为 INVALID，错误 path 为 `_config`
- 字段错误与全局错误合并时不同 path 均保留；同 path 优先内置消息
- **UI**：`ConfigScreen` 在 INVALID 时把合并结果写入 `DraftSignalAdapter.setSubmitValidation`；字段红字走 `errorSignal`，`_config` 计入 `errorCount` 与保存反馈摘要；用户再编辑任一字段会清空提交错误并重算

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
        .endSection()
        .build();
```

`FieldType` 当前可用：`STRING` / `NUMBER` / `BOOLEAN` / `CHOICE` / `SIMPLE_LIST`。  
复杂类型（`LONG_TEXT` / `TABLE` / `OBJECT` / `KEY_VALUE_MAP` 等）在枚举注释中预留，**尚未接默认 renderer**。

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
3. `Minecraft.displayGuiScreen(...)` 或 `UiScreenManager.enqueue` 延后开屏

他 mod 可自写 `McScreenBridge` 子类，不必用 `ModernConfigScreen`。

## 本 mod 配置文件

- 路径：`config/qzuilib-modern.yaml`（相对 `mcDataDir`）
- 格式：YAML（`ConfigManager.bootstrap` 固定 `ConfigFormat.YAML`）
- 旧 Forge `.cfg` **已废弃且不互通**；老配置不会自动迁移，缺省回落 schema 默认值

## 他 mod 最小接入

1. **声明 Schema**：`ConfigSchema.builder("your-mod-id")...build()`
2. **Bootstrap**：`ConfigManager.bootstrap(new File(mcDataDir, "config/your-mod.yaml"), schema)`；需要跨字段业务校验时用三参 `bootstrap(file, schema, DraftValidator)`（见上文「提交前校验」）
3. **构建屏**：`ConfigUI.buildScreen(manager, input, reg -> { /* 可选 registerPath */ }, policy -> { /* 可选 skip/custom */ })`
4. **桥接**：自写 `extends McScreenBridge` 的 GuiScreen，构造器收 `parent + ConfigScreen`；Forge 侧用 `IModGuiFactory` 反射合法单参 `(GuiScreen)` 中转类即可

可选：在 `manager.eventBus().subscribe(...)` 监听 `BATCH_SAVE`，把 Authority 值回灌到运行时字段（本 mod 参考 `ConfigSaveListener` + `ConfigValueBridge`）。

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
