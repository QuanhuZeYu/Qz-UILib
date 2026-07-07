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
- 2026-07-05：阶段 C C4 完成（commit `946be55b`）。guiFactory 入口正式切到新栈：`ModConfigGui.createTargetScreen` 由 `new ForgeConfigTemplateScreen(parent, spec)` 改为 `ModernConfigEntry.createScreen(parentScreen)`；删 `createForgeSpec`（含 setSaveHandler→Config.saveAndReload 桥接 + FontSort/FontCharacterRule PropertyEditorFactory + enableQzNetworkSync）+ `createBaseSpec`；保留单参 `(GuiScreen)` 构造器（ModGuiFactory 反射契约），`ModGuiFactory` 一行不改。独立复审全过 + P1 Javadoc 收尾；编译绿 + 14 用例回归全绿；守 I1/I3/I7。
  - **C5/C6 死代码暂留按拍板**：用户拍板 C4 单 commit、C5/C6 暂留到阶段 D/E 统一收敛。`Config.java` 的 init/saveAndReload/load/registerEvents/onConfigChangeEvent/configuration 仍保留为"暂留死代码"，因：`ConfigTemplateSyncManager:577,580,596` 仍引用 `Config.configuration` 与 `Config.saveAndReload`（阶段 E 才删）、`CommonProxy.preInit:34` 仍调 `Config.init` 且 C3 ModernConfigBootstrap 在其后跑（删 Config.init 需先重排 CommonProxy 时序铁律）。`ConfigChangedEvent` 全工程 grep 仅 Config.java 自监听、无任何推送点（mainConfigGuiClass=ModConfigGui 非 Forge GuiConfig），onConfigChangeEvent 现已是死代码但保留无害。死代码窗口 = 一次真机周期，commit message 登记。
  - **关键事实闭合（oracle 工具故障后主 agent 补侦察）**：ModGuiFactory:19-21 mainConfigGuiClass=ModConfigGui.class（反射入口不动）✅ / ForgeConfigTemplateScreen:48 extends BaseScreen 非 GuiConfig（保存靠 SaveHandler 内调）✅ / ConfigSaveListener:82 随 manager 生命周期非静态常驻但保存动作与屏同源 → 保存丢失窗口不存在 ✅ / I7 守卫已转嫁 ConfigSaveListener:92 ✅ / fontSort 阶段 A 已接入新栈 FieldType.SIMPLE_LIST ✅。
  - 下一步：真机验证阶段 C（启动回灌 + 改配置保存触发 reload + 重启验证回灌 + 异常容错）后启动阶段 D（删旧栈 24 文件）+ E（删 ConfigTemplateSyncManager 整支 + 统收 Config.java 死代码）。
- 2026-07-05：阶段 C 反向持久化改向 A2 完成（commit `7e3b1735`）。FontConfig 新增 `public static detachLegacyConfiguration()`（实现：`activeConfiguration = null`），`ConfigValueBridge.applyFromAuthority` 末尾（`refreshDerivedRuleSet` 之后）调之，使后续 FontService.reload → FontRegistry.reload → applyFontOrderSnapshot → persistFontSortToConfiguration 的 null 守卫自动 no-op，切断"resolved 顺序反向写回 .cfg 污染老用户配置 + 与 YAML 双写不一致"链路。reviewer 全过 + P2 文档行号瑕疵顺手修；编译绿 + 17 用例回归全绿（ConfigValueBridgeTest 6+1 新 + ConfigSaveListenerTest 5 + ModernConfigBootstrapTest 3 + FontConfigCategoryTest 2 老用例不破坏）；守 I1/I3/I7 + 不 publish BATCH_SAVE（防 applyFontOrderSnapshot(resolved) → publish → listener 回灌覆盖用户值 → 保存回环，handoff 反向改向约束已满足）。
  - **A2 vs A1 取舍**：A1（FontConfig.load 末尾置 null）会让 FontConfigCategoryTest 老用例 `shouldPersistFontSortSnapshotToExistingLowercaseCategory` 失败（因 activeConfiguration=null 后 applyFontOrderSnapshot 不反向写，断言 `["Alpha","Bravo"]` 不成立）需改测试；A2（detach 方法 + Bridge 调）入口更显式、单一职责、不破坏老测试，故选 A2。
  - **FontConfig.detachLegacyConfiguration 调用方单一性**：grep 确认生产路径仅 `ConfigValueBridge.applyFromAuthority` 一处调用（C2 ConfigSaveListener 与 C3 ModernConfigBootstrap 都经 Bridge 自动 detach）；旧栈路径（FontConfigCategoryTest 字面测试）不配 Bridge 仍测老行为，两类互补不污染。
  - **方法保留不删**：detachLegacyConfiguration 方法体保留到阶段 D/E 统一收敛（与 persistFontSortToConfiguration / applyFontOrderSnapshot / 字段 activeConfiguration 一起删）。
  - **阶段 C 全部完成**：C1 Bridge 值回灌 / C2 ConfigSaveListener 保存回调 / C3 ModernConfigBootstrap 启动加载首次回灌 / C4 ModConfigGui 中转到 ModernConfigEntry / 反向持久化改向 A2 detachLegacyConfiguration，五项各自过独立复审。下一步：真机验证 + 启动阶段 D（删旧栈 24 文件）+ E（删 ConfigTemplateSyncManager 整支 + 统收 Config.java 死代码）。
- 2026-07-05：用户真机验证后提两项 UI/字段语义调整（commit `f4ecd951`），17 用例回归全绿：
  - **数值字段默认走 input 模式**：QzUiLibModernSchema 删 7 处 `.slider()`（lerpMode / aaMode / smoothRangeMin / smoothRangeMax / aaStrength / awtCharSize / charSize），保留 `.range()` 约束。NumberFieldRenderer 的 `instanceof SliderSpec` 分发自动走 renderTextInput（WidgetSpec.java:10 已声明 widget=null 默认 input）。控件形态与离散型/大范围字段语义更匹配（如 lerpMode 仅 0-3 枚举、aaStrength 可达 120）。
  - **fontSortConfigured 兜底语义校正**：C1 Bridge 早期把 `FontConfig.fontSortConfigured = true` 强置（schema 总声明该 path → "声明即配置"），导致 FontRegistry.reload:40 永远走 `FontConfig.fontSort` 分支、DefaultFontOrderHints 系统字体优先级提示完全失效。本次改为 `= FontConfig.fontSort != null && FontConfig.fontSort.length > 0`：空 yaml → false → 走 DefaultFontOrderHints（中文字体如 Microsoft YaHei / PingFang SC 排前）；非空 → true → 走用户配置。FontOrderPlanner:70-83 剩余字体末尾追加逻辑保证用户配置不被插队（"系统字体兜底不插队"约束已满足）。
  - **设计认知**：schema DSL 总声明某 path（保证 normalizeDefault 注入）≠ 用户已配置该字段，二者不能划等号；`fontSortConfigured` 字段语义应反映"用户是否实际填了值"而非"schema 是否声明了 path"。同类字段（如 characterFontRules）若后续有类似二分支判断需求，应优先看实际值而非 schema 声明。
  - **配置模板抽象已完备（无需新增）**：用户曾提"新栈需要配置模板提供给他人使用"，侦察确认 `club.heiqi.config` 整套已是完整模板（ConfigSchema DSL + ConfigManager + ConfigUI.buildScreen + FieldRendererRegistry + HostBridge），ModernConfigEntry.createScreen 已走该模板（不是自己直接写 ConfigScreen）。其他 mod 接入只需 4 步：声明 schema → ConfigManager.bootstrap → ConfigUI.buildScreen → ModernConfigScreen 桥接。本次接受现状，不新增模板代码。
- 2026-07-05：阶段 D + E 全部完成（commit `e2ca2ba2` + `8a84d560` + `3d0f758c`，3 commit）。原计划 4 commit（日志增强 / 阶段 D 删旧栈 / 阶段 E.1 删网络同步引用方 / 阶段 E.2 统收死代码），实际合并为 3 commit——因删 `ForgeConfigTemplateScreen` 会让 commit 3 范围的网络同步文件编译失败，分两 commit 不可编译，合并 commit 2+3 为单一 commit 更符合"每个 commit 可编译"原则。34 用例全绿。
  - **commit 1 日志增强**（`e2ca2ba2`）：INFO 关键事件 + DEBUG 细节。ModernConfigBootstrap.bootstrapAndApply / ConfigSaveListener.onConfigChanged / ConfigValueBridge.applyFromAuthority / CommonProxy.preInit 4 阶段时序 [1/4]-[4/4]。占位符 `{}` + 基本类型装箱 `Integer/Boolean.valueOf`，沿用 Log4j2 既有约定。真机排错依赖，可定位启动加载 / 保存回调 / reload 触发链路问题。
  - **commit 2+3 合并 删旧栈整支 + 修引用方**（`8a84d560`，39 文件改动 / -9591 行）：删 22 主代码 + 10 测试 + 修 6 引用方。22 主代码分两组：旧栈 UI 11（ForgeConfigTemplateScreen + 协作类 + 字体编辑器工厂 + NumericControl + ConfigTemplatePropertyBindings 等）+ 网络同步 11（ConfigTemplateSyncManager + ConfigTemplateRemoteSyncController/Session + RemoteConfigDocumentPages + ConfigSync* + ConfigCategoryResolver + QzUiLibConfigSchema + FontCharacterRuleDrafts）。修引用方 6（CommonProxy/ClientProxy/VanillaMixinTransport/NetSelfCheckRunner/NetRuntimeSelfChecks + 清单外额外发现 NetEnvelopeDispatcher setClientRemoteAvailable 改空 if 块）。保留 ModConfigGui/ModGuiFactory + modern/ 子包 + CommonProxy.preInit 时序 + ClientProxy/VanillaMixinTransport 其他断连清理 + NetSelfCheckRunner 其他 13 自检方法。reviewer 全过 + P1 ModConfigGui Javadoc 顺手修（过时"§C5/C6 暂留死代码窗口"段落改为反映 ConfigTemplateSyncManager 已删）。
  - **commit 4 阶段 E.2 统收死代码**（`3d0f758c`，9 文件改动）：Config.java 124→27 行（删 9 死方法/字段 GENERAL/CONFIG_LISTENER/configFile/configuration/init/load/saveAndReload/applyLoadedFontConfig/registerEvents/onConfigChangeEvent/getConfigPath，保留 4 字段 useDebug/uiDebug/fontRuntimeDebug/netTransport 13+ 处运行时读取 + 私有构造）；FontConfig.java 359→200 行（删反向持久化整支包含 A2 detach 方法 + activeConfiguration/activeFontCategory 字段 + load(Configuration)/persistFontSortToConfiguration/resolveFontCategory/readLegacyBrightnessGain，applyFontOrderSnapshot 内调 persist 行也删）；CommonProxy.preInit 时序 [1/4]→[1/3]（Config.init 删除）；ConfigValueBridge 删 detach 调用 + 改类头"承担"段为"不涉及反向持久化"；删 FontConfigCategoryTest 整支（测 FontConfig.load 字面行为目标已不存在）+ ConfigValueBridgeTest.detach 用例 7→6 + FontMatcherRuntimeVersionTest:166 改 static 字段直接赋值。reviewer 全过 + P1 4 文件悬空溯源顺手修（P1-1 ConfigValueBridge 行内注释 / P1-2 ModernConfigBootstrap 时序铁律前驱重写 / P1-3 ConfigSaveListener Javadoc / P1-4 FontMatcherRuntimeVersionTest 已用"原"字标注保守保留）。
  - **悬空溯源上溯触发**（按 §4.3）：Javadoc 中"删除 API 时未同步清理 Javadoc 中对该 API 的溯源行号引用"已成为第 2 次累积（C4/C5/C6 时第 1 次、阶段 E.2 时第 2 次），达上溯触发点。已登记到交接.md §2 `<item id="悬空溯源上溯评估">`，待后续评估是否在 NORTH_STAR 或 AGENTS.md 补一条不变量"删除 API 时同步清理 Javadoc 中对该 API 的溯源行号引用"。
  - **阶段 A-E 全部完成汇总**：阶段 A FieldType.SIMPLE_LIST + 接入 fontSort / 阶段 B ModernConfigEntry 抽同步入口 / 阶段 C 数据源 + guiFactory 配对（C1 Bridge + C2 Listener + C3 Bootstrap + C4 ModConfigGui 切换 + 反向持久化改向 A2 detach）/ 阶段 D 删旧栈 UI 一支 + 网络同步整支 / 阶段 E 统收 Config.java + FontConfig.java 死代码（含 A2 detach 方法）。新栈单栈运行，旧栈整支（22 主代码 + 10 测试）已删 + Config.java 死代码收敛 + FontConfig.java 反向持久化整支删除。
  - **真机综合验证全部通过**：用户确认启动加载首次回灌 + 改配置保存触发 reload + 重启验证回灌 + 异常容错 + 字体兜底不插队 5 项均无异常。日志 debug 已就位（INFO 关键 + DEBUG 细节），后续深化若有真机问题可据日志定位。**整个迁移工程闭合**。
  - **下一新方向**：深化 uilib 配置内容（fontSort + characterFontRules）—— 开放性需求，待用户拍板深化方向后启动。可能维度：拖拽排序支持 / 字体名自动补全 / 字段拆分 / 验证规则增强 / 默认预设 / UI 编辑器复杂化等。新方向不动迁移工程已闭合的状态。

## 不变量对齐

- 新栈 `ConfigScreen` 已守 I1/I3/I7/I8/I11（类头 `ConfigScreen.java:62-68` 逐条声明）
- `ModernConfigScreen extends McScreenBridge` 是 I11 允许的宿主层第三方桥接
- 阶段 A 新写 renderer 必须过 R1-R12（`control/package-info.java:1-148`）
- 阶段 C 字体 reload 热路径不得引入命令式改 UI（守 I1，走 `saveFeedbackSignal`）
- 网络同步移除与 I 系列无关（`net.*` 网络层 + 服务端会话，不在 scene 数据/渲染链路）
