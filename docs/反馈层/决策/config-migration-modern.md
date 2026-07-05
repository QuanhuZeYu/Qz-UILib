# 决策：旧栈配置模板页全量迁移到新栈

## 背景

项目有两套 UI 栈：旧栈（HTML-like document 栈，`uilib.config` 包）与新栈（scene 栈，`config.ui` 包）。旧栈 `ForgeConfigTemplateScreen`（1482 行）+ 约 20 个协作类是历史遗留配置页入口，经 Forge `guiFactory` 暴露给主界面 Mods 按钮。

迁移工程长期挂在 `交接.md` §2 `<item id="旧栈Forge配置迁移">`，标"独立大工程、低优先级、待排期"。本会话用户正式拍板启动。

### 一个被原表述掩盖的关键事实

用户初始表述是"配置**页**迁移 + 废弃 `.cfg`"，但 oracle 深评揭示这实际是**三件被捆在一起、量级完全不同**的事：

1. **UI 入口迁移**（轻）：`guiFactory` 从 `ForgeConfigTemplateScreen` 换成新栈 `ConfigScreen`。
2. **运行时数据源迁移**（重，被低估）：`Config.java:47-54` / `FontConfig.load()` 运行时字段全部从 Forge `Configuration` 读，新栈 `ConfigManager` 只写独立 YAML，两者不互通。光换 UI 不切数据源会导致"改了不生效"。
3. **网络同步一支**（可切分）：实际是两个半环——客户端半环（随 UI 一起死）+ 服务端半环（`RemoteConfigDocumentPages` 远程 HTML 编辑，与客户端 UI 无关）。

三件必须分开评估、分批做，否则会因表述掩盖而低估成本。

## 候选方案

### 迁移范围
- **A 全量迁移**：UI + 数据源 + 网络同步全切到新栈
- B 只换入口：`guiFactory` 指新栈，旧栈类暂留
- C 只删旧栈：UI 类直接删，`guiFactory` 入口受损

### 网络同步
- 半删半留：客户端半环随 UI 删，服务端半环（`RemoteConfigDocumentPages`）保留并解耦 `normalizeInlineText`
- **整支移除**：含服务端远程 HTML 编辑能力一起删，后续重建
- 整支保留：只做最小解耦，本轮不碰

### .cfg 老配置
- A 自动迁移：首次启动读 `.cfg` 填充 YAML
- **B 直接废弃**：老用户配置回落默认值
- C 保留 `.cfg` 只读兜底（违背"完全废弃"目标）

### guiFactory 接入路径
- **改 ModConfigGui 内部**：保留 `ModConfigGui` 中转层（已是单参 `GuiScreen` 合法契约），`createTargetScreen` 内部从 `new ForgeConfigTemplateScreen` 改为 `ModernConfigEntry.createScreen(parent)`；`ModGuiFactory` 一行不改
- 改 ModernConfigScreen：让它满足单参 `(GuiScreen)` 反射契约（现状是双参）

### fontSort/characterFontRules
- **接入**：扩展 `FieldType.SIMPLE_LIST` + 新增 renderer，做阶段 A
- 暂丢失 + 登记偏离：跳过阶段 A，后续会话补

## 最终选择

| 维度 | 决策 |
|---|---|
| 迁移范围 | **全量迁移**（A） |
| 网络同步 | **整支移除**（含服务端 `RemoteConfigDocumentPages`） |
| .cfg 兼容 | **直接废弃**（B，不自动迁移，老用户配置回落默认） |
| guiFactory 接入 | **改 `ModConfigGui` 内部**，`ModGuiFactory` 不动 |
| fontSort/characterFontRules | **接入**（做阶段 A，扩展 `FieldType.SIMPLE_LIST`） |

## 选择原因

- **全量迁移**：旧栈是历史遗留，长期并存增加维护成本，用户选择一刀切
- **网络同步整支移除**：用户选择激进路线，远程 HTML 编辑能力后续按需重建（`RemoteConfigDocumentPages` 一支约 6 类随客户端 UI 一起删，登记为功能缺口）
- **直接废弃 .cfg**：本项目尚无外部用户（私有 UI 库），无历史配置包袱，迁移逻辑不值得写
- **改 ModConfigGui 内部**：`ModGuiFactory.mainConfigGuiClass()` 通过反射调单参 `(GuiScreen)` 构造器，`ModConfigGui` 已是合法中转；`ModernConfigScreen` 是双参构造器不满足契约，改它需动桥接层，得不偿失
- **接入 fontSort/characterFontRules**：这两项是字体系统核心配置（字库排序、字符规则），不接入则迁移实际丢能力，不算"全量"

## 影响范围

### 三件被捆在一起的事（必须配对）
1. **UI 入口**（阶段 C 部分）：`ModConfigGui.createTargetScreen` 改实现
2. **运行时数据源**（阶段 C 核心，**P0 最高危**）：
   - `Config.java:47-54` 字段读取源从 Forge `Configuration` 改为 `ConfigManager` 读 YAML
   - `FontConfig.load()` 同步改造
   - 保存回调挂到新栈 `ConfigScreen` 保存事务后触发 `FontService.reload`
3. **网络同步整支移除**（阶段 D+E 合并）：
   - 删 `ConfigTemplateSyncManager` / `ConfigTemplateRemoteSyncController` / `ConfigTemplateRemoteSession`
   - 删 `RemoteConfigDocumentPages`（服务端远程 HTML 编辑能力）
   - 删 `ConfigSync*`（Target/Models/Json/CategorySpec）
   - `CommonProxy.java:38` 注册点 + `ClientProxy.java:83` 断连清理一并删

### 旧栈 UI 协作类（阶段 D 删除）
`ForgeConfigTemplateScreen` + `ConfigTemplateDocumentBuilder` + `ConfigTemplatePropertyBindings` + `ForgeConfigTemplatePropertyDrafts` + `ForgeConfigTemplateMessages` + `FontSort*EditorFactory` + `FontCharacterRule*EditorFactory` + `NumericControl*` + `ChoicePropertyBinding` + `QzUiLibConfigSchema` 等 24 文件，约 8+ 测试类同步删。

### 新栈新增（阶段 A）
- `FieldType.SIMPLE_LIST` 枚举
- 列表 renderer（守 R1-R12，条件渲染走 `rt.show`/I5 keyed diff，禁命令式挂卸）
- `QzUiLibModernSchema` 解除 fontSort/characterFontRules 跳过

### guiFactory 入口（阶段 C）
- `ModConfigGui.createTargetScreen:39-42` 改实现
- 阶段 B 先抽出 `ModernConfigEntry.createScreen(parent)` 同步入口（统一异步/同步路径）

## 分阶段路线（oracle 出，用户已确认范围）

| 阶段 | 内容 | 风险 |
|---|---|---|
| **A** | 扩展 `FieldType.SIMPLE_LIST` + 列表 renderer（守 R1-R12） | 中（新写 scene 控件） |
| **B** | 抽出同步 `ModernConfigEntry.createScreen(parent)` | 低（纯加法） |
| **C** | **数据源切到 YAML + guiFactory 入口切换**（必须配对） | **P0 最高危**（碰 `Config`/`FontConfig` 运行时 + 字体 reload 热路径） |
| **D** | 删 `ForgeConfigTemplateScreen` 一支（24 文件 + 8 测试） | P1（`RemoteConfigDocumentPages.java:295` 静态依赖 `normalizeInlineText` 随整支删一起消除） |
| **E** | 网络同步整支移除（含服务端半环） | 中（功能缺口登记） |

**用户选择不预拆会话**（走一步看一步），由主 agent 按上下文压力主动建议停会话做交接。

## 演进

- 2026-07-05：初始决策（本会话）。5 项拍板如上表。用户选择比 oracle 推荐更激进的"网络同步整支移除 + 直接废弃 .cfg"路线——后果是**老用户配置真的会丢、远程 HTML 编辑能力后续重建**，已显式确认接受。
- 2026-07-05：阶段 A 完成（FieldType.SIMPLE_LIST + SimpleListFieldRenderer + QzUiLibModernSchema 接入 fontSort/characterFontRules）。分支 `refactor/config-fullmigrate-modern` 5 commit（ed1a9145 schema 地基 / 44ef7163 renderer+注册 / 1f6b1c4c schema 接入 / 6fbdbcdb 测试 / 4f8b4901 FormFieldShell 高度按字段自带）。两项设计取舍固化：
  - **fontSort 拖拽降级**：SceneSimpleList 无拖拽排序能力，fontSort 从旧栈拖拽（FontSortOrderControl）降级为"删了重加调顺序"；characterFontRules 无序增删不受影响。缺口登记 `docs/诊断层/scene技术债.md` SceneSimpleList 拖拽排序缺失。
  - **reset 多 1 帧延迟（接受）**：ListItem 携带 draft 里不存在的控件私有 id（I5 keyed 复用锚），从无 id 的 List<String> 派生稳定 id 需状态存储，Computed 提供不了（违 I3 纯函数），D2 本地可写 Signal 桥是合法载体。比 STRING/CHOICE 的 Computed 直读多 1 flush（~16ms），主流异步 effect-set 语义（React/Vue/Svelte），接受。详见 SimpleListFieldRenderer 类头 D2 段。
- 2026-07-05：阶段 C P0 核心三块完成（C1+C2+C3，5 commit）。P0 缺口（新栈无值回灌链路 / 保存回调唯一重挂点）经 explorer 侦察 + oracle 深评 + 主 agent 抽检修正 + fixer 实施 + reviewer 独立复审全链路闭合：
  - **C1 ConfigValueBridge 值回灌抽象**（commit `3c9f2a66` + P2 收尾 `8de8bdf3`）：新建 `uilib.config.modern.ConfigValueBridge.applyFromAuthority(Authority)` 全量拉值回灌 Config.*(4)+FontConfig.*(20) 静态字段；int 转换用 Math.round 避免 2.9999→2 截断；List→String[] null 守卫；characterFontRules 后调 `FontConfig.refreshDerivedRuleSet()`（派生逻辑留 FontConfig 内，守宪章派生态不陈旧）；单一职责纯回灌（不判 affectsFontRuntime/不调 reload/不刷 last*）。reviewer 6 维度全过。
  - **C2 ConfigSaveListener 保存回调 listener**（commit `22b82454` + P2 收尾 `a386ab18`）：新建 `ConfigSaveListener implements ConfigChangeListener`，`onConfigChanged` 只认 BATCH_SAVE → Bridge 回灌 → affectsFontRuntime → 条件 `FontService.reload`（reason="modern_config_saved"）→ onConfigReload；等价迁移自 `Config.saveAndReload:64-78`（去 Forge save/load，换 Bridge 回灌源）。`ModernConfigEntry.createScreen:86-87` 内 subscribe（bootstrap 之后立即挂，与 manager 生命周期绑定，不泄漏）。reviewer 7 维度全过。
  - **C3 ModernConfigBootstrap 启动加载首次回灌**（commit `dffccd1d`）：新建 `ModernConfigBootstrap.bootstrapAndApply(File)` 封装启动加载（bootstrap+catch ConfigException log+return 不中断 → Bridge → 补刀 affectsFontRuntime+isInitialized+reload（reason="modern_config_loaded"）→ onConfigReload）；等价 `Config.applyLoadedFontConfig:87-94`。**🔴 CommonProxy.preInit 时序铁律**（:34 Config.init → :38 bootstrapAndApply → :39 FontService.initialize → :40 NetTransportFactory.create）三条约束（Config.init 后 / FontService.initialize 前 / NetTransportFactory.create 前）reviewer 独立自核通过。**方案 A 保守**：保留 Config.init（旧栈屏依赖，阶段 D 删）。
  - **守 I1/I3/I7**：Bridge 写静态字段是数据层（非 SceneNode 属性槽）；reload 仅 affectsFontRuntime 时触发 → invalidateAll 失效注册表非命令式改节点；C1/C2/C3 三块统一构型（ConfigValueBridge.applyFromAuthority + FontService.reload 条件触发 + FontConfig.onConfigReload 三段流程）。
  - **C3 reviewer 3 条 P2**（不阻断）：路径常量未统一抽取 / Bridge 兜底容错范围 / commit 正文已主 agent 核。后续打磨。
  - 下一步：阶段 C 后续子任务（guiFactory 切换 + 反向持久化改向 + ConfigChangedEvent 去留 + 方案 A→B 收敛评估）+ 真机验证（runClient + /qzuilib modernconfig + 改配置保存 + 重启验证回灌）。

## 不变量对齐

- 新栈 `ConfigScreen` 已守 I1/I3/I7/I8/I11（类头 `ConfigScreen.java:62-68` 逐条声明）
- `ModernConfigScreen extends McScreenBridge` 是 I11 允许的宿主层第三方桥接
- 阶段 A 新写 renderer 必须过 R1-R12（`control/package-info.java:1-148`）
- 阶段 C 字体 reload 热路径不得引入命令式改 UI（守 I1，走 `saveFeedbackSignal`）
- 网络同步移除与 I 系列无关（`net.*` 网络层 + 服务端会话，不在 scene 数据/渲染链路）
