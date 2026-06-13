# Modern Config 模板配置页施工规划

## 目标

基于未来可独立发布的 `club.heiqi.config` 模块，新增现代配置模板页能力。UILib 侧通过运行时检测决定是否启用现代配置页；检测不到 config 模块时继续使用现有 `ForgeConfigTemplateScreen` 作为回退。

本规划只记录施工方案，不做 Forge 配置迁移工具。复杂配置模型在 Forge 回退时如何兼容由接入方自行承担；建议需要兼容回退的接入方把复杂结构序列化为 JSON 字符串，存入 Forge cfg 的字符串属性。

## 固定边界

- 全部模板最终都要实现，但按从易到难分批施工。
- UI 风格保持当前 `ForgeConfigTemplateScreen` 的主题、间距、卡片、状态栏和工具栏口径。
- 不做 Forge 到 config 模块的配置迁移，不在页面内提供迁移按钮。
- config 模块按可选能力处理，后续独立成新 Mod 时 UILib 不能硬依赖其运行时存在。
- 性能暂无硬指标，但结构设计应预留懒加载、分页、虚拟列表和搜索索引扩展点。
- 每个批次结束必须提交 git，并在交接记录中给出下一批次提示词。

## 入口策略

### 运行时检测

`ModConfigGui` 只负责选择入口：

1. 通过 `Class.forName("club.heiqi.config.Config")`、`Class.forName("club.heiqi.config.MutableConfig")` 检测 config 模块是否存在。
2. 存在时通过桥接类创建现代配置页。
3. 不存在时走现有 Forge 配置页。

检测的是模块能力是否存在，不是检测某个配置文件是否存在。

### 桥接边界

现代配置页相关类允许直接引用 `club.heiqi.config` 类型，但入口类不能在静态初始化阶段硬引用这些类型，避免 config 模块缺失时类加载失败。推荐形状：

```java
final class ModernConfigBridge {
    static GuiScreen createScreen(GuiScreen parentScreen, ModernConfigTemplateScreen.Spec spec) {
        return new ModernConfigTemplateScreen(parentScreen, spec);
    }
}
```

如果后续构建系统支持真正 optional dependency 分离，可再把 bridge 移到独立 source set 或 integration 包；首批先用最小桥接验证运行时检测边界。

## 总体类结构

```text
club.heiqi.uilib.config/
├── ModConfigGui.java
├── ModernConfigBridge.java
├── ModernConfigTemplateScreen.java
├── ModernConfigDocumentBuilder.java
├── ModernConfigPropertyBindings.java
├── ModernConfigTypeInference.java
├── ModernConfigChangeTracker.java
├── ModernConfigSearchIndex.java
├── bindings/
│   ├── PrimitivePropertyBinding.java
│   ├── ChoicePropertyBinding.java
│   ├── MultilineTextPropertyBinding.java
│   ├── SimpleListPropertyBinding.java
│   ├── TablePropertyBinding.java
│   ├── NestedCategoryBinding.java
│   ├── ObjectPropertyBinding.java
│   ├── KeyValueMapPropertyBinding.java
│   ├── PresetSelectorPropertyBinding.java
│   ├── RawEditorPropertyBinding.java
│   ├── EnhancedPickerPropertyBinding.java
│   └── SearchFilterPropertyBinding.java
└── controls/
    ├── DocumentDataTableControl.java
    ├── DocumentTreeViewControl.java
    ├── DocumentBreadcrumbControl.java
    ├── DocumentKeyValueEditorControl.java
    ├── DocumentCodeEditorControl.java
    └── DocumentColorPickerControl.java
```

实际施工时优先控制文件规模；单文件接近 1000 行时按真实职责拆出 state、renderer、validator 或 row model。

## Spec 形状

现代配置页的 `Spec` 应复用现有主题语义，避免重复设计视觉系统。

建议字段：

- `modId`：模组 ID。
- `title`、`subtitle`、`description`：页面头部文案。
- `MutableConfig config`：当前可变配置对象。
- `configPath`：展示用路径。
- `Theme theme`：复用 `ForgeConfigTemplateScreen.Theme` 或复制同等字段。
- `TextSet textSet`：复用当前保存、恢复、返回、状态文案口径。
- `List<FieldSpec>`：可选字段规格，用于补足 config 模块没有 schema 时的 UI 语义。
- `List<TemplateFactory>`：自定义模板工厂扩展点。
- `SaveHandler`：保存回调，默认 `config.save()`。

`FieldSpec` 建议只做轻量声明，避免把 schema 系统一次做大：

- `path`：配置路径。
- `label`、`description`：显示文案。
- `templateHint`：指定模板，例如 `table`、`raw`、`color`。
- `validValues`：离散值。
- `minValue`、`maxValue`、`step`：数字范围。
- `placeholder`、`maxLength`：文本约束。
- `collapsedByDefault`：嵌套组初始折叠。

## 模板分工

为避免重复，模板选择按以下优先级执行：

1. `SearchFilterPropertyBinding`：页面级搜索过滤，不绑定单个配置路径。
2. `RawEditorPropertyBinding`：页面级或子树级源码编辑，不与普通字段同时显示同一路径。
3. `PresetSelectorPropertyBinding`：只处理声明了预设的字段组。
4. `EnhancedPickerPropertyBinding`：只处理明确 hint 或可稳定推断为颜色、资源、声音的字符串字段。
5. `TablePropertyBinding`：只处理 list 且元素主要为同构 map 的路径。
6. `SimpleListPropertyBinding`：处理非表格 list。
7. `KeyValueMapPropertyBinding`：处理声明为动态 map 的路径。
8. `ObjectPropertyBinding`：处理普通 map 的内联编辑。
9. `ChoicePropertyBinding`：处理有离散值的 primitive 字段。
10. `MultilineTextPropertyBinding`：处理长文本或含换行字符串。
11. `PrimitivePropertyBinding`：兜底处理 string、number、boolean、null。
12. `NestedCategoryBinding`：作为整体布局/导航模板，负责组织子树，不与单字段绑定竞争。

## 批次计划

### Batch 0：基础架构与检测

目标：建立可进入现代配置页的最小骨架。

任务：

- 在 `ModConfigGui` 增加 config 模块运行时检测，不检测配置文件。
- 新增 `ModernConfigBridge`，隔离入口和现代页实现。
- 新增 `ModernConfigTemplateScreen` 骨架，包含生命周期、按钮、状态刷新、脏状态统计和保存入口。
- 新增 `ModernConfigDocumentBuilder`，复用当前 hero、status、toolbar、card 视觉结构。
- 新增 `ModernConfigPropertyBindings` 工厂骨架，暂只能渲染空状态或只读路径列表。

验收：

- config 模块存在时能进入现代配置页骨架。
- config 模块缺失时仍走 Forge 回退，且不因类加载失败崩溃。
- 空配置、只读展示、保存按钮状态不崩溃。
- 通过 `git diff --check` 和 `compileJava`。

提交后交接提示词应要求下一位从 Batch 1 开始。

### Batch 1：基础类型模板

目标：让现代配置页能编辑基本字段。

任务：

- 实现 `ModernConfigTypeInference`，统一推断 string、number、boolean、null、long text、choice。
- 实现 `PrimitivePropertyBinding`：string 用 `DocumentTextInputControl`，number 用 slider/input，boolean 用 `DocumentToggleSwitchControl`。
- 实现 `ChoicePropertyBinding`：少量选项用 `DocumentSegmentedSelectorControl`，较多选项用 `DocumentSelectControl`。
- 实现 `MultilineTextPropertyBinding`：长字符串或含换行字符串用 `DocumentTextAreaControl`。
- 补基础字段保存、恢复当前值、恢复默认值、校验错误展示。

验收：

- JSON/YAML 中 string、number、boolean 能显示、编辑、保存。
- 离散值和长文本按预期选择专用控件。
- 脏状态统计准确。
- 通过 `git diff --check`、相关单测和 `compileJava`。

提交后交接提示词应要求下一位从 Batch 2 开始。

### Batch 2：列表模板

目标：支持基础列表和同构对象列表。

任务：

- 实现 `SimpleListPropertyBinding`，支持添加、删除、上移、下移；拖拽排序可先复用 DOM drag 语义，复杂拖拽问题留到后续优化。
- 实现批量导入：多行文本按行拆分，空行按选项决定保留或过滤。
- 新增 `DocumentDataTableControl`，先支持固定列、行内编辑、添加行、删除选中行、按列排序。
- 实现 `TablePropertyBinding`，只处理元素主要为 map 且列集合稳定的 list。

验收：

- primitive list 能增删改排并保存。
- list of map 能表格化显示、编辑、排序、保存。
- 空列表和混合列表有合理降级提示。
- 通过 `git diff --check`、列表/表格相关测试和 `compileJava`。

提交后交接提示词应要求下一位从 Batch 3 开始。

### Batch 3：嵌套结构模板

目标：支持树形导航和普通对象内联编辑。

任务：

- 新增 `DocumentTreeViewControl`，支持折叠、展开、当前节点高亮。
- 新增 `DocumentBreadcrumbControl`，显示当前路径并支持点击回跳。
- 实现 `NestedCategoryBinding`，作为页面级或子树级导航模板。
- 实现 `ObjectPropertyBinding`，普通 map 以卡片内联方式渲染，递归深度默认限制为 5。
- 超深层级通过“展开编辑”跳转树形导航，避免无限内联。

验收：

- 多层 map 能树形导航。
- 面包屑路径正确。
- 普通对象能内联编辑并保存。
- 超深层级不会撑爆页面或递归崩溃。
- 通过 `git diff --check`、嵌套结构相关测试和 `compileJava`。

提交后交接提示词应要求下一位从 Batch 4 开始。

### Batch 4：动态 map 与预设模板

目标：支持可变键集合和预设批量应用。

任务：

- 新增 `DocumentKeyValueEditorControl`，支持 key/value/type 三列编辑。
- 实现 `KeyValueMapPropertyBinding`，只处理明确声明为动态 map 的路径，避免和普通对象模板冲突。
- 实现 `PresetSelectorPropertyBinding`，约定 `_presets` 或 `presets` 存储预设定义。
- 选择预设时批量写入目标字段，偏离预设时显示已修改标记。
- 预设对比可先做只读摘要，不必首批做完整复杂表格。

验收：

- 动态 map 能新增、删除、修改 key/value/type。
- 重复 key 有明确错误提示。
- 预设能被识别、应用、显示偏离状态。
- 通过 `git diff --check`、动态 map/预设测试和 `compileJava`。

提交后交接提示词应要求下一位从 Batch 5 开始。

### Batch 5：高级编辑与搜索模板

目标：补齐源码编辑、增强选择器和搜索过滤。

任务：

- 新增 `DocumentCodeEditorControl`，基于 `DocumentTextAreaControl` 做行号、基础高亮、Tab 插入和错误行提示。
- 实现 `RawEditorPropertyBinding`，支持 JSON/YAML 格式化、语法验证、表单/代码模式切换。
- 实现 `EnhancedPickerPropertyBinding`，覆盖颜色、资源、声音三类字符串字段；颜色可先实现色块、HEX/RGB 输入和预览，HSV 色轮可后续增强。
- 新增 `ModernConfigSearchIndex`，索引路径、键名、值摘要和类型。
- 实现 `SearchFilterPropertyBinding`，支持搜索、按类型过滤、只看已修改、结果跳转。

验收：

- 源码编辑能正确解析回配置树，语法错误不污染当前草稿。
- 颜色/资源/声音字段能用增强选择器编辑或预览。
- 搜索能定位到匹配字段，过滤逻辑可用。
- 通过 `git diff --check`、高级模板测试和 `compileJava`。

提交后交接提示词应要求下一位从 Batch 6 开始。

### Batch 6：测试、文档与收口

目标：将现代配置模板页整理到可维护状态。

任务：

- 为现代页主流程、类型推断、每类 binding 和新增控件补测试。
- 增加完整 JSON/YAML 示例配置，覆盖 12 个模板入口。
- 新增或更新对外使用文档，说明可选依赖、回退边界、模板选择规则和复杂结构兼容建议。
- 更新长期事实和当前态交接。
- 检查文件规模，必要时拆分超大文件。

验收：

- 通过 `git diff --check`、相关测试和 `compileJava`。
- 文档说明“不迁移、使用者承担回退模型、建议 JSON 字符串 cfg 兼容”三项边界。
- 工作区干净并完成最终提交。

提交后交接提示词应要求下一位做整体 review 或进入后续增强，而不是继续堆模板。

## 测试建议

- 每批至少运行 `git diff --check`。
- 涉及生产 Java 代码的批次至少运行 `compileJava`。
- 有可定位测试时优先运行定向测试，再运行相关包测试。
- 纯 JVM 测试不要直接实例化 `GuiScreen` / `BaseScreen` 页面类，可拆出类型推断、草稿模型、搜索索引、binding 数据转换做测试。
- 涉及真实游戏内 UI 的最终 smoke 可用 `runClient21`，但不作为每批硬门槛。

## 下一位 Agent 接力格式

每批结束时，交接记录需包含：

1. 当前完成批次和提交号。
2. 修改文件范围。
3. 已运行验证命令和结果。
4. 遗留风险或刻意未做项。
5. 下一批施工提示词。

提示词模板：

```text
你接手 Qz-UILib 的 ModernConfigTemplateScreen 分阶段施工。先阅读 AGENTS.md、docs/AI记忆文档.md、docs/开发者文档/specs/modern-config-template-screen-plan.md、docs/记忆/决策/DECISION-20260613-modern-config-template-optional-module.md、docs/记忆/当前态/交接记录.md。当前应从 Batch <N> 开始，不要重做已完成批次。保持 UI 风格与 ForgeConfigTemplateScreen 一致，不做 Forge→Config 迁移。每批结束必须运行 git diff --check、相关测试/compileJava，提交 git，并更新交接记录给出下一批提示词。
```
