# 决策：ModernConfigTemplateScreen 不拆分 Spec/FieldSpec 为独立文件

## 背景

Batch 6「测试、文档与收口」阶段需要评估 `ModernConfigTemplateScreen.java`（846 行）是否拆出 `Spec` / `FieldSpec` 为独立文件：

- `ModernConfigTemplateScreen.java`：846 行，其中 `Spec`（约 170 行）+ `FieldSpec`（约 195 行）+ `SaveHandler`（约 12 行）合计约 377 行是 public static 嵌套类。
- `ModernConfigSearchFilter.java`：514 行。
- `ModernNestedCategoryBinding.java`：516 行。

AGENTS.md §5 规定「单文件接近或超过 1000 行时必须评估职责拆分；优先按真实职责、变更频率和复用边界拆，不按行数机械拆」。

## 候选方案

1. **拆出 FieldSpec 为独立 public 类** `ModernConfigFieldSpec`，保留 Spec 作为 Screen 内部类。
2. **拆出 Spec + FieldSpec + SaveHandler 为独立规格文件** `ModernConfigTemplateSpec.java`。
3. **全部保留为 Screen 嵌套类**，不拆分。

## 最终选择

采用方案 3：全部保留为 `ModernConfigTemplateScreen` 的 public static 嵌套类，不拆分。

`ModernConfigSearchFilter`（514 行）与 `ModernNestedCategoryBinding`（516 行）同样保留，职责单一不需拆分。

## 选择原因

- **未达硬门槛**：846 行未到 1000 行的强制评估门槛；按「真实职责、变更频率、复用边界」衡量，Spec/FieldSpec 与 Screen 同生命周期，不是必须拆分的信号。
- **Batch 6 范围边界**：任务明确「不碰 5-A/5-B/5-C/5-D 已定稿的内部实现」。`Spec`/`FieldSpec` 被 `ModernConfigTypeInference`、`ModernConfigPropertyBindings`、`ModernConfigSearchIndex`、全部 binding（primitive/choice/multiline/simpleList/table/object/keyValueMap/presetSelector/rawEditor/enhancedPicker）与 `ModernNestedCategoryBinding` 等 20+ 处引用，拆分会大面积改动已定稿代码，违反批次边界。
- **API 惯例稳定**：`ModernConfigTemplateScreen.Spec` / `ModernConfigTemplateScreen.FieldSpec` 是全代码库（含 22 套件 197 项测试）的既有引用惯例，拆分相当于一次全局重命名，收益不抵风险。
- **Spec 与 Screen 强耦合**：`Spec` 引用 `ForgeConfigTemplateScreen.Theme`/`TextSet`/`SaveHandler`，主要消费者是 `ModernConfigTemplateScreen` 构造函数与 `ModernConfigDocumentBuilder`；独立成文件不改变耦合关系，只是物理拆分。
- **职责单一性已满足**：`ModernConfigSearchFilter`（搜索过滤：查询+类型分段+只看已修改+结果列表+跳转）与 `ModernNestedCategoryBinding`（嵌套分类：树形导航+面包屑+对象内联+叶子 binding 管理）各自职责单一，514/516 行主要是控件创建与事件处理的必要实现，无冗余职责可拆。

## 影响范围

- 不改动任何 main 代码，仅记录决策。
- 后续如出现以下信号，可重新评估拆分：
  - `ModernConfigTemplateScreen.java` 接近或超过 1000 行（例如新增更多屏幕级逻辑）。
  - `FieldSpec` 被包外其他模块直接引用，复用边界确实独立于 Screen。
  - 构建系统支持真正 optional dependency / source set 分离，可把现代配置页整体移到独立包。
- 在那之前，保持 `Spec`/`FieldSpec`/`SaveHandler` 作为 `ModernConfigTemplateScreen` 的 public static 嵌套类不变。
