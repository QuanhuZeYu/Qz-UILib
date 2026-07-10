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
- **Persistence**：YAML 文件，只存权威值；写盘失败可回滚 Authority

**四层 / 协作者**：

- **schema**：`ConfigSchema` / `SectionSpec` / `FieldSpec` / `FieldType` / 约束与 widget 声明
- **runtime**：`ConfigManager` / `Authority` / `DraftBuffer` / `Persistence` / `ConfigEventBus` / `LegacyAdapter` / `DraftValidator`
- **ui**：`ConfigUI` / `ConfigScreen` / `DraftSignalAdapter` / `FieldRestorePolicy` / `field.*` renderer
- **接入**：本 mod `uilib.config.modern`（及他 mod 自写桥）

保存事务（`ConfigManager`）：内置 `validateAll` → 可选 `DraftValidator` → 合并 `ValidationResult`（有错则 INVALID、无副作用）→ 备份 snapshot → `Authority.applyAll` → 写盘 → 成功则 commit + `BATCH_SAVE` 广播 / 失败则回滚 Authority。validator 返回 null 或抛 RuntimeException 时 fail-closed 为 INVALID（全局 path `_config`）。

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
| `DraftValidator` / `DraftValidator.noop()` | 提交前钩子接口；无逻辑用 noop，禁止 null |
| `ValidationResult.merge(a, b)` | 合并内置与自定义字段错误 |
| `ModernConfigEntry.createScreen(parent)` | 本 mod 同步开屏样板 |

默认 type→控件：BOOLEAN→Toggle，STRING→TextInput，NUMBER→Slider\|TextInput，CHOICE→Segmented\|Select，SIMPLE_LIST→SceneSimpleList。  
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
- 复杂 `FieldType`（枚举注释中的 LONG_TEXT / TABLE / OBJECT 等）**未接**默认 renderer
- 业务 path 专用 renderer 原则：**应在接入层**（`CharacterRuleFieldRenderer` 已迁 `uilib.config.modern`；`FontSortFieldRenderer` 仍在 `config.ui.field` 但经 Supplier 注入、无 font 硬依赖）
- 使用文档中部分入门示例仍可能描述已移除的 document 栈 API，以源码为准逐步收敛

## 8. 维护规则

- **config 核心层（schema / runtime / 节点读写）零 uilib 硬依赖**
- **field 通用默认 renderer 勿塞 mod 专属 import**；业务 path 覆盖放接入层 customizer
- `FormFieldShell` / `FormPageShell` / `FormTheme` 保持只吃通用类型（String / Signal / Supplier / FormTheme），不 import config schema
- 新增 FieldType 或默认 renderer 须补测试；破坏性 API 变更评估 LTS 清单与使用文档
