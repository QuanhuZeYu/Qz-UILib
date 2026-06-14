# `/qzuilib test` 现代配置模板 demo 接入规划

本文规划在 `/qzuilib test` 视觉矩阵中新增「现代配置模板页 12 入口 demo」的接入方案。现代配置模板页（`ModernConfigTemplateScreen`）Batch 0-6 施工已完结，覆盖 STRING/NUMBER/BOOLEAN/CHOICE/LONG_TEXT/SIMPLE_LIST/TABLE/OBJECT/KEY_VALUE_MAP/PRESET_SELECTOR/RAW_EDITOR/ENHANCED_PICKER 共 12 个模板入口，但目前没有游戏内展示入口。

> 相关背景：
> - 视觉矩阵总体规划：`qzuilib-test-page-visual-matrix-plan.md`
> - 现代配置模板使用文档：`docs/使用文档/02-控件/现代配置模板.md`
> - 12 入口示例配置：`docs/使用文档/02-控件/现代配置模板示例.md`
> - 现代配置模板页施工规划：`modern-config-template-screen-plan.md`

## 1. 选定接入方案：方案 A（新增独立组 MODCFG）

在 `UiTestMatrixRegistry.createDefault` 新增第 11 组 `MODCFG`，组页面以「打开完整现代配置模板 demo 页」按钮为入口，跳转到独立的 `ModernConfigTemplateScreen`。

### 选择理由

1. **职责对等**：现代配置模板页是 UILib 的完整功能模块（12 入口、搜索、草稿/保存/恢复、嵌套导航），不是一个单点视觉样例。它与现有 10 组同属「UILib 能力」，应在矩阵中占独立位置，让首页总览、热力图、分组导航都体现这项能力，而不是藏在某个组的子样例里。
2. **客观约束决定展示形态**：`ModernConfigTemplateScreen extends BaseScreen`，是完整屏幕，无法嵌入 UiTest 文档页（文档页只能内嵌 widget 样例）。用独立组承载「跳转独立屏幕」的新展示模式，能在组页面里显式说明「此组以独立屏幕展示完整能力」，反而比塞进文档页内嵌样例更诚实。
3. **方案 B（复用 NET 组）语义勉强**：NET 组「RemoteNet 远程、配置与网络语义」偏网络链路，把完整的配置模板 demo 放进去会冲淡 NET 的网络语义焦点，且 NET 已有 6 个计划样例。
4. **方案 C（首页独立 section）割裂矩阵**：在首页加独立 section 虽然改动小，但会让现代配置模板游离于矩阵计数/热力图之外，破坏「矩阵统一反映 UILib 能力覆盖」的设计意图。

### MODCFG 组规格

| 字段 | 取值 |
|---|---|
| code | `MODCFG` |
| title | `ModernConfig 现代配置模板完整 demo` |
| coverage | STRING/NUMBER/BOOLEAN/CHOICE/LONG_TEXT/SIMPLE_LIST/TABLE/OBJECT/KEY_VALUE_MAP/PRESET_SELECTOR/RAW_EDITOR/ENHANCED_PICKER 12 入口、搜索、草稿/保存/恢复 |
| 计划样例数 | 1（`VIS-MODCFG-001`） |
| 自动语义数 | 0 |
| 人工确认数 | 1（屏幕跳转与 12 入口可见性需游戏内确认） |

`VIS-MODCFG-001` 样例：展示「打开完整现代配置模板 demo 页」按钮 + 12 入口预览卡片 + config 模块可用性状态；点击按钮跳转到 `ModernConfigTemplateScreen`。

## 2. 屏幕跳转边界研究结论

### 现状

- `UiDocumentScreens.DocumentScreenEnvironment` 只持有 `textMeasureService`、`runtimeAdapters`、`defaultTextContentMode`、`backdropBlurPolicy`，**不提供当前 GuiScreen 引用**。
- `UiTestDocumentPageController` 持有 `DocumentPageAuthoringSurface`、`DocumentPageRuntimeView`，**不持有 GuiScreen 引用**；其 `NavigationHandler` / `GroupInteractionHandler` 内部接口只支持文档页内切换（`openHome` / `openGroup` / `previousCase` / `runCurrentCaseAssertion`），无打开外部屏幕能力。
- `UiTestSampleVisualFactory.appendCaseDemo` 按 case id 分派，签名 `(UiDocument, ElementNode, UiTestCaseSpec)`，不传入屏幕跳转回调。

### 结论：无需扩展 DocumentScreenEnvironment 或 controller 接口

屏幕跳转所需的两个能力都可从 Minecraft 静态入口直接获取：

1. **parentScreen**：`Minecraft.getMinecraft().currentScreen` 即当前 `/qzuilib test` 页对应的 `BaseScreen`（`GuiScreen` 派生），正好作为 `ModernConfigTemplateScreen` 的 `parentScreen`。
2. **屏幕切换**：`UiScreenManager.getInstance().enqueue(...)` + `minecraft.displayGuiScreen(...)`，与 `QzUiLibClientCommand.openDiagnosticsMenu`（`QzUiLibClientCommand.java:69`）和 `ModernConfigTemplateScreen.requestClose`（`ModernConfigTemplateScreen.java:243`）完全一致的既定模式。

### 返回路径已由 Batch 0-6 实现

`ModernConfigTemplateScreen.requestClose()`（ESC / 返回按钮触发）已实现：`enqueue` + `displayGuiScreen(parentScreen)`。只要 demo 构造时把当前 test 页作为 `parentScreen` 传入，ESC / 返回就会回到 `/qzuilib test` 的 MODCFG 组页面。**无需改动 Batch 0-6 定稿代码**。

### 跳转触发点

按钮的 `DocumentButtonActionHandler` 是匿名类，可直接调用静态方法 `UiTestModernConfigDemoLauncher.openDemo()`（见第 6 节）。`Launcher` 仅负责 Class.forName 检测与安全入口；检测通过后再加载 `UiTestModernConfigDemoBridge`，由 `Bridge` 从 `Minecraft.getMinecraft()` 获取 `currentScreen` 并切换屏幕。因此 `UiTestSampleVisualFactory` / `UiTestGroupVisualBuilder` 完全不需要 import `Minecraft`，保持文档构建层纯净。

## 3. 可选依赖降级策略

### 检测方式（与 ModConfigGui 完全一致）

复用 `ModConfigGui` 的检测模式（`ModConfigGui.java:80-93`）：

```
Class.forName("club.heiqi.config.Config", false, classLoader)
Class.forName("club.heiqi.config.MutableConfig", false, classLoader)
```

- 检测的是**模块能力是否存在**，不检测配置文件。
- `initialize=false`（第二参数），避免触发目标类初始化。
- 同时捕获 `ClassNotFoundException` 与 `LinkageError`。

### 当前运行时语义

`club.heiqi.config` 包当前位于本项目 `src/main/java/club/heiqi/config/`，与 `club.heiqi.uilib` 同 jar、同 main sourceSet，运行时一定存在，检测恒为 `true`。但代码按「未来拆分为独立 Mod 后可能缺失」写，保留降级路径，与 `ModConfigGui` 边界一致。

### 降级表现

config 模块不可见时（未来拆分场景）：

- demo 按钮在文档页内禁用，不崩溃。
- 页面常驻提示：「未检测到 `club.heiqi.config` 模块，按钮已禁用，无法展示现代配置模板 demo。」并继续展示 12 入口说明卡片。
- 不尝试构造 `MutableConfig` / `ModernConfigTemplateScreen.Spec`（避免 `NoClassDefFoundError`）。

### 类加载隔离

`UiTestModernConfigDemoLauncher` 不直接引用 `ModernConfigTemplateScreen`、`MutableConfig`、`Config`、`FieldSpec` 等 config 类型。直接引用 config 与现代配置屏幕类型的实现收敛在 `UiTestModernConfigDemoBridge` 中，且只在 `Launcher.isModernConfigModuleAvailable()` 检测通过后调用。

- 检测代码（`isModernConfigModuleAvailable`）只用 `Class.forName` 字符串，不引用任何 config 类型。
- demo 数据构造与屏幕创建全部收敛在 `UiTestModernConfigDemoBridge` 内部。
- 按钮 handler 根据 `Launcher.isModernConfigModuleAvailable()` 设置按钮启用状态；启用时调用 `Launcher.openDemo()`，由 Launcher 再进入 Bridge。

## 4. demo 数据设计

demo 构造一个覆盖 12 入口的 `MutableConfig`（`Config.createMutable(ConfigFormat.JSON)`，无文件源）+ `ModernConfigTemplateScreen.Spec`（12 个 `FieldSpec`）。配置值与 FieldSpec 声明直接复用 `docs/使用文档/02-控件/现代配置模板示例.md` 的素材。

### MutableConfig 配置值（12 入口）

| # | 模板入口 | 配置路径 | 值 |
|---|---|---|---|
| 1 | STRING | `player.name` | `Steve` |
| 2 | NUMBER | `server.maxPlayers` | `20` |
| 3 | BOOLEAN | `feature.enableHud` | `true` |
| 4 | CHOICE | `render.mode` | `fast` |
| 5 | LONG_TEXT | `motd` | `欢迎进入 demo\n本公告演示 LONG_TEXT`（含换行触发长文本） |
| 6 | SIMPLE_LIST | `ports` | `[25565, 25566, 25567]` |
| 7 | TABLE | `servers` | `[{host:a.local,port:25565},{host:b.local,port:25566}]` |
| 8 | OBJECT | `database.credentials` | `{username:admin,password:secret}` |
| 9 | KEY_VALUE_MAP | `labels` | `{alpha:A,beta:B}`（需 `templateHint("dynamic-map")`） |
| 10 | PRESET_SELECTOR | `profile` | `{mode:fast,threads:4,_presets:{fast:{...},safe:{...}}}` |
| 11 | RAW_EDITOR | `payload` | `{"host":"localhost","port":8080}`（JSON 字符串，`templateHint("json")`） |
| 12 | ENHANCED_PICKER | `theme.primary` / `texture.block` / `audio.click` | `#FF8800` / `minecraft:block/stone` / `minecraft:block.stone.click`（color/resource/sound 三种 pickerKind） |

> ENHANCED_PICKER 在 demo 中拆为 3 个子字段（color/resource/sound），共用一个 `FieldSpec` 组，覆盖三种 `pickerKind` 推断。

### FieldSpec 列表

按示例文档声明，关键 hint / range / validValues / defaultValue：

- `player.name`：`setLabel("玩家名称").setMaxLength(32)`
- `server.maxPlayers`：`setLabel("最大玩家数").setRange(1,100).setStep(1).setDefaultValue(20)`
- `feature.enableHud`：`setLabel("启用 HUD").setDefaultValue(Boolean.TRUE)`
- `render.mode`：`setLabel("渲染模式").setValidValues("fast","balanced","safe").setDefaultValue("balanced")`
- `motd`：`setLabel("服务器公告").setTemplateHint("textarea").setMaxLength(4096)`
- `ports`：`setLabel("监听端口列表").setDefaultValue(Arrays.asList(25565))`
- `servers`：`setLabel("服务器列表")`
- `database.credentials`：`setLabel("数据库凭证")`
- `labels`：`setLabel("自定义标签").setTemplateHint("dynamic-map")`
- `profile`：`setLabel("运行档位")`（自动识别 `_presets`）
- `payload`：`setLabel("JSON 负载").setTemplateHint("json")`
- `theme.primary`：`setLabel("主题色").setTemplateHint("color")`
- `texture.block`：`setLabel("方块纹理").setTemplateHint("resource")`
- `audio.click`：`setLabel("点击音效").setTemplateHint("sound")`

### SaveHandler

demo 的 `MutableConfig` 无文件源（`getSource() == null`），默认 `SaveHandler` 调 `config.save()` 会抛 `ConfigException("No source file associated with this config")`，导致 demo 点「保存」报错。因此 demo 使用自定义 `SaveHandler`：

```java
spec.setSaveHandler(new ModernConfigTemplateScreen.SaveHandler() {
    @Override
    public void onSave(MutableConfig config) {
        if (config != null) {
            config.markClean();
        }
    }
});
```

仅清理脏标记，不写文件，demo 状态条显示保存成功。

### Spec 其余字段

- `modId`：`"qzuilib-test-demo"`
- `title`：`"现代配置模板 demo"`
- `subtitle`：`"覆盖 12 个模板入口的完整示例"`
- `description`：`"此页面由 /qzuilib test MODCFG 组打开，演示 ModernConfigTemplateScreen 全部能力。"`
- `configPath`：`"demo（内存配置，不落盘）"`
- `theme` / `textSet`：使用 `Spec` 默认值（`ForgeConfigTemplateScreen.Theme.defaultTheme()` / `TextSet.defaultTextSet()`），与 `ModernConfigBridge` 不传 theme/textSet 时的行为一致。

## 5. 断言策略

### 以人工确认为主（HUMAN_OBSERVED）

现代配置模板 demo 是独立屏幕，`UiTestAssertionRunner` 基于文档页 DOM/布局/事件的自动断言无法应用到独立屏幕内部。`VIS-MODCFG-001` 标注 `requiresManualConfirmation = true`，人工原因：「屏幕跳转、12 入口可见性、搜索/草稿/保存/返回链路需 runClient21 游戏内确认」。

### 可自动的部分：模块可用性检测

demo 按钮所在样例舞台会渲染 config 模块可用性状态牌（文本来自 `Launcher.isModernConfigModuleAvailable()`）。`UiTestAssertionRunner` 可对 MODCFG 组追加一条轻量自动检查：样例舞台包含「已检测到 club.heiqi.config 模块」或「未检测到」状态文本。这不验证屏幕内部，只验证检测逻辑与状态牌渲染，可在 JVM 测试里覆盖。

### JVM 测试覆盖（不触发 GuiScreen 静态初始化）

遵循 `ERROR-20260508-jvm-test-guiscreen-static-init.md`：

- **不**在纯 JVM 测试中 `new ModernConfigTemplateScreen`（依赖 Minecraft 静态初始化）。
- 可测的部分：
  - `UiTestModernConfigDemoLauncher.isModernConfigModuleAvailable()` 当前环境返回 `true`（config 模块同 jar）。
  - `Bridge` 的 demo config / Spec 构造逻辑（抽出为包级静态方法 `createDemoConfig()` / `buildDemoSpec(MutableConfig)`，验证 12 个配置入口与 FieldSpec 的 path/label/hint）。
  - `UiTestMatrixRegistry` 包含 MODCFG 组与 `VIS-MODCFG-001` case。
  - `UiTestDocumentPageController` 打开 MODCFG 组页面后，文档树包含「打开完整现代配置模板 demo 页」按钮文本与 12 入口预览。

## 6. 文件改动清单

### 新增（main）

| 文件 | 职责 |
|---|---|
| `src/main/java/club/heiqi/uilib/internal/devtools/pages/UiTestModernConfigDemoLauncher.java` | config 模块检测 + 安全跳转入口；不直接引用 config 类型，暴露 `boolean isModernConfigModuleAvailable()` 与 `void openDemo()`。 |
| `src/main/java/club/heiqi/uilib/internal/devtools/pages/UiTestModernConfigDemoBridge.java` | 检测通过后加载，直接引用 `ModernConfigTemplateScreen` / `MutableConfig` / `Config` / `FieldSpec`，负责 demo 内存配置、Spec/FieldSpec 构造与 `UiScreenManager.enqueue` 切屏。 |

### 修改（main）

| 文件 | 改动 |
|---|---|
| `UiTestMatrixRegistry.java` | `createDefaultGroups` 末尾新增 MODCFG 组；`createDefaultCases` 末尾新增 `VIS-MODCFG-001` case。 |
| `UiTestSampleVisualFactory.java` | `appendCaseDemo` 分派链新增 `VIS-MODCFG-001` 分支，调用新方法 `appendModernConfigDemoStage`：渲染「打开完整 demo 页」按钮（handler 调 `Launcher.openDemo()` 或降级提示）、config 模块状态牌、12 入口预览卡片。 |
| `UiTestControlsAssertionRunner.java` | 将 `VIS-CTRL-005` 的 select top-layer option 点击改为扫描真实 hit-test 命中，避免弹层选项边界偏移导致全量断言误失败。 |

### 修改（test）

| 文件 | 改动 |
|---|---|
| `UiTestDocumentPageControllerTest.java` | 同步全局计数断言（53→54、59→60、42/11→42/12、全量完成 53→54 等）；新增 MODCFG 组断言（分组导航含「打开 MODCFG」、MODCFG 组页含「打开完整现代配置模板 demo 页」按钮文本、12 入口预览文本、config 模块状态牌文本）。 |
| `UiTestRuntimeHostVisualMatrixTest.java` | 同步 registry 已接入样例数 53→54。 |
| `UiTestModernConfigDemoBridgeTest.java` | 新增纯 JVM 测试，覆盖模块检测、demo 内存配置与 14 个 FieldSpec 声明。 |

### 不改动（Batch 0-6 定稿边界）

- `ModernConfigTemplateScreen.java` 及其全部协作者（`ModernConfigDocumentBuilder` / `PropertyBindings` / `TypeInference` / `SearchIndex` / `SearchFilter` / 全部 binding / 控件）。
- `ModConfigGui.java` / `ModernConfigBridge.java`（demo 不走 Forge 回退链，不经过这两个类）。
- `club.heiqi.config` 包。

### 文档同步

| 文件 | 改动 |
|---|---|
| `docs/开发者文档/specs/README.md` | 新增本规划文档条目。 |
| `docs/记忆/当前态/交接记录.md` | 实现完成后覆盖更新。 |

## 7. 分阶段实施计划

### 阶段 1：屏幕跳转骨架与降级（核心边界）

- 新增 `UiTestModernConfigDemoLauncher`：`isModernConfigModuleAvailable`（Class.forName 检测）、`openDemo`（检测通过后加载 Bridge，不通过直接返回）。
- 新增 `UiTestModernConfigDemoBridge`：获取 currentScreen、构造 demo 屏幕并 enqueue displayGuiScreen。
- 自定义 demo `SaveHandler`（markClean，不落盘）。
- 验证：runClient21 进入 `/qzuilib test`，临时入口（可在首页 hero 或临时按钮）点击后能跳转到 `ModernConfigTemplateScreen`，ESC 返回 test 页。
- 提交：`[Plan]: 现代配置模板 demo 屏幕跳转骨架与降级`。

### 阶段 2：MODCFG 组与 demo 数据接入

- `UiTestMatrixRegistry` 新增 MODCFG 组与 `VIS-MODCFG-001` case。
- `UiTestSampleVisualFactory` 新增 `VIS-MODCFG-001` 分支：渲染「打开完整 demo 页」按钮、config 模块状态牌、12 入口预览卡片。
- `Bridge.createDemoConfig` 与 `Bridge.buildDemoSpec` 补全 12 入口的 MutableConfig 配置值与 14 个 FieldSpec（ENHANCED_PICKER 拆 color/resource/sound）。
- 同步 `UiTestDocumentPageControllerTest` 计数断言与新增 MODCFG 断言。
- 验证：compileJava + compileTestJava + `club.heiqi.uilib.config.*` / `club.heiqi.config.*` 25 套件 197 项不回归 + UiTest 测试通过；runClient21 确认 MODCFG 组页面与 12 入口预览。
- 提交：`[Add]: MODCFG 组与现代配置模板 12 入口 demo 数据`。

### 阶段 3：断言收口与文档同步

- `VIS-MODCFG-001` 的人工确认原因文案定稿。
- 视情况给 `UiTestAssertionRunner` 追加 MODCFG 组的模块可用性状态牌自动检查（可选，非硬性）。
- 同步 `specs/README.md`、`交接记录.md`。
- 验证：全套基线不回归 + runClient21 全链路确认（进入 demo、12 入口可见、搜索/草稿/保存/返回正常、模块缺失降级提示——当前同 jar 无法实测降级，但代码路径保留）。
- 提交：`[Docs]: 现代配置模板 demo 收口文档与交接`。

## 8. 关键约束对齐

- **命名**：新增类沿用 `UiTest*` 前缀（`UiTestModernConfigDemoLauncher` / `UiTestModernConfigDemoBridge`）。
- **代码规范**（AGENTS.md §5）：4 空格缩进、左大括号不换行、中文 Javadoc、运算符两侧空格、类与方法必须注释。
- **不改动 Batch 0-6 定稿**：只复用 `ModernConfigTemplateScreen` 的 public API（`Spec` / `FieldSpec` / `SaveHandler` / 构造函数）与 `ModConfigGui` 的检测模式。
- **可选依赖检测**：`Class.forName` 字符串检测，不检测配置文件，与 `ModConfigGui` 完全一致。
- **纯 JVM 测试**：不 `new ModernConfigTemplateScreen`；demo 验证用 runClient21。
- **防回归基线**：`club.heiqi.uilib.config.*` + `club.heiqi.config.*` 25 套件 197 项只增不减；UiTest 测试同步更新后通过。

## 9. 待确认事项

- 实现阶段若发现 `ModernConfigTemplateScreen` 需要小改动（如暴露 demo 友好的 Spec 工厂），将先记录到本节并与用户确认，不擅自改动 Batch 0-6 定稿代码。当前研究结论是：**无需改动**，现有 public API（`Spec` / `FieldSpec` / 构造函数）足以支撑 demo。
