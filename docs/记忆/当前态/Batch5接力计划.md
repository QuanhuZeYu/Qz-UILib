# Batch 5 接力计划

本文件记录 Modern Config 模板页 Batch 5「高级编辑与搜索模板」的 4 阶段接力施工总览。Batch 5 整体范围按 `docs/开发者文档/specs/modern-config-template-screen-plan.md` 描述，包含源码编辑、增强选择器与搜索过滤模板。

## 接力总览

- **当前分支**：`docs/modern-config-template-plan`
- **范围边界**：只做 Batch 5，不重做 Batch 0/1/2/3/4；不做 Forge→Config 迁移；不堆 UI 控件、binding、Screen、Builder 的逻辑到单一文件。
- **每阶段独立提交 git、覆盖更新交接记录、生成下一阶段提示词。**

## 阶段划分

### 5-A：基础件与推断骨架（已完成）

- ConfigSerializer（`club.heiqi.config.ConfigSerializer`）：复用 JsonConfigWriter/YamlConfigWriter 的内部转换逻辑，对外暴露 `toString(ConfigNode, ConfigFormat)`。
- ModernConfigSearchIndex（`club.heiqi.uilib.config.ModernConfigSearchIndex`）：一次性遍历 rootSnapshot 构建索引，支持 path/displayName/valueSummary 搜索、类型过滤、只看已修改。
- ModernConfigTypeInference 扩展：新增 `RAW_EDITOR` / `ENHANCED_PICKER` 枚举值，识别 raw/code/json/yaml/color/resource/sound 等 hint；Result 新增 `rawFormat` 与 `pickerKind`。
- ModernConfigPropertyBindings.formatType 补 `RAW_EDITOR` →「源码」、`ENHANCED_PICKER` →「颜色/资源/声音」中文标签；createBinding 暂不分支（5-C 才创建 binding）。
- 验证：`ConfigSerializerTest`、`ModernConfigSearchIndexTest`、`ModernConfigTypeInferenceTest` 扩展 + `compileJava`。

### 5-B：UI 控件层（下一位）

- `DocumentCodeEditorControl`：基于 `DocumentTextAreaControl` 结构复制，叠加行号、Tab 插入、错误行提示、（可选）语法高亮；配套 `DocumentCodeEditorSyntaxSupport` 与事件三元组（change/select/scroll 或同等抽象）。
- `DocumentColorPickerControl`：色块、HEX/RGB 输入、预览；事件三元组（change/select/confirm 或同等抽象）。
- 验证：两控件 Test + `compileJava`。
- 实际不直接依赖 5-A 产出，但需读本文件了解 Batch 5 全局。
- 完成后生成 5-C 接力提示词。

### 5-C：binding 与工厂

- `ModernRawEditorPropertyBinding`：模仿 `ModernMultilineTextPropertyBinding`，使用 `DocumentCodeEditorControl` + `ConfigSerializer`；语法错误不污染当前草稿。
- `ModernEnhancedPickerPropertyBinding`：color 用 `DocumentColorPickerControl`；resource/sound 用 `DocumentAutocompleteInputControl`（如不存在则降级为文本输入并在 5-D 收口时复核）。
- `ModernConfigPropertyBindings.createBinding()` if-else 链补 `RAW_EDITOR` / `ENHANCED_PICKER` 分支。
- 验证：两 binding Test + 全套 `ModernConfig*Test` 防回归。
- 完成后生成 5-D 接力提示词。

### 5-D：屏幕级集成与收口

- `ModernConfigSearchFilter`：屏幕级组件，组合搜索框 + 类型过滤 + 只看已修改 + 结果列表 + 跳转。
- `ModernConfigDocumentBuilder.appendSearchFilter`：把 SearchFilter 注入文档流。
- `ModernConfigTemplateScreen` 持有 `searchIndex` / `searchFilter` 并集成（沿用现有 changeListener 钩子）。
- 验证：全套 `ModernConfig*Test` 防回归、`git diff --check`、`compileJava`、必要 smoke。
- 完成后更新交接记录、给出 Batch 6 提示词（测试、文档与收口）。

## 共同约束

- 命名沿用现有风格：`ModernConfigSearchIndex`、`ConfigSerializer` 等大驼峰；包名遵守 `club.heiqi.config` / `club.heiqi.uilib.config`。
- 代码规范遵循 `AGENTS.md` §5：4 空格缩进、左大括号不换行、运算符两侧空格、中文 Javadoc。
- 单文件接近 1000 行必须评估拆分；优先按 registry/builder/checker/state 拆。
- 不做 Forge→Config 迁移，不在页面加迁移按钮。
- UI 风格沿用当前 `ForgeConfigTemplateScreen` 主题。
- 不破坏 Batch 0/1/2/3/4 既有测试。
