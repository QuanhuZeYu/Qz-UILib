# 决策：ModernConfig 配置页面系统性性能优化

## 背景

测试页 MODCFG demo 打开现代配置页面时存在明显卡顿，根因是一打开就全量构建 DOM、Binding、搜索索引和布局树。当前 demo 仅 12 个模板入口，但每个入口可能展开为多层嵌套配置项（TABLE/OBJECT/PRESET_SELECTOR/KEY_VALUE_MAP），实际 DOM 节点数远超表面数量。

## 候选方案

1. 只做交互层防抖和增量索引刷新，不改初始构建流程。
2. 只做分批渐进式 DOM 构建和延迟加载，不改高频交互链路。
3. 同时做交互优化、渐进加载和架构级虚拟化/复用，系统性解决初始卡顿和后续交互延迟。

## 最终选择

采用方案 3：完整架构优化（P0 + P1 + P2），分三阶段实施。

## 实施阶段

### P0 高频交互优化（2-3 天）

1. **防抖草稿变更回调**：`ModernConfigTemplateScreen.onDraftChangedInternal()` 引入 150ms 防抖，状态文本立即更新，搜索索引延迟刷新。
2. **搜索索引增量更新**：`ModernConfigSearchIndex.refreshDirtyMarkers()` 原地更新 dirty 状态，避免每次创建全新 entries 列表。
3. **列表视图差量更新**：`ModernSimpleListPropertyBinding.refreshListView()` 和 `ModernTablePropertyBinding` 改为复用已有卡片、只更新变化的值文本节点，不再每次 `clearChildren()` 全量重建。

### P1 初始加载优化（3-4 天）

4. **分批渐进式 DOM 构建**：`ModernConfigDocumentBuilder.appendBasicFieldCards()` 改为每批 10 个卡片，通过 `UiScreenManager.enqueue()` 分帧调度，首批优先渲染保证快速可见。
5. **延迟初始化搜索索引**：`ModernConfigTemplateScreen` 构造时不立即构建 `searchIndex`，改为搜索框首次获得焦点时懒加载。
6. **嵌套分类延迟加载**：`ModernNestedCategoryBinding` 未展开的分类显示占位符，首次选中时才 `rebuildModel()` 构建子 binding。

### P2 架构级优化（5-6 天）

7. **虚拟化字段列表**：新建 `ModernConfigVirtualizedFieldList`，参考 `DocumentVirtualizedOptionList` 的固定行高虚拟化算法，只渲染可见区域 + overscan 的配置卡片。超过 30 个配置项时自动启用。
8. **Binding 实例池复用**：`ModernNestedCategoryBinding.rebuildModel()` 尝试复用已有 binding 实例，新增 `canReuse()` / `reset()` / `dispose()` 生命周期协议。

## 选择原因

- 用户反馈"一打开进去就卡"，初始全量构建是主要瓶颈，必须从构建流程层面解决。
- 交互卡顿（文本输入、列表操作）是次要但持续影响体验的问题，需要同步处理。
- 虚拟化方案已在 `DocumentSelectControl`（DECISION-20260610）和 `DocumentAutocompleteInputControl`（DECISION-20260612）中验证可行，可复用算法。
- 不改动 `ModernConfigTemplateScreen.Spec` / `FieldSpec` 公开 API，内部优化对调用方透明。

## 影响范围

- 修改文件：`ModernConfigTemplateScreen.java`、`ModernConfigDocumentBuilder.java`、`ModernConfigSearchIndex.java`、`ModernConfigPropertyBindings.java`、`ModernSimpleListPropertyBinding.java`、`ModernTablePropertyBinding.java`、`ModernNestedCategoryBinding.java`、`ModernConfigSearchFilter.java`。
- 新增文件：`ModernConfigVirtualizedFieldList.java`（虚拟化列表组件）、`ModernConfigBindingLifecycle.java`（生命周期接口）。
- 新增测试：`ModernConfigPerformanceTest.java`（性能基准测试）。
- 公开 API 不变，`Spec` / `FieldSpec` / `SaveHandler` 保持嵌套类。

## 后续注意事项

- 虚拟化列表初期使用固定卡片高度 120px 假设，后续按需改为动态测量。
- 分批构建首批 10 个卡片，后续批次通过 `UiScreenManager.enqueue()` 分帧调度，不引入新线程。
- Binding 复用的 `reset()` 协议必须严格定义，子类需覆盖以重置特定状态。
- 每阶段完成后需通过 `git diff --check`、`compileJava`、现有 25 套件 197 项测试回归。
- 开发分支建议 `perf/modern-config-optimization`，从 `4.0` 创建，完成后合并回 `4.0`。
- 性能基准测试场景：小型（12 项 demo）、中型（50 项）、大型（200+ 项）。
