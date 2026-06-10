# 决策：select 大列表采用控件级虚拟化

## 背景

`DocumentSelectControl` 原实现会在构造时为每个候选项创建一个真实 `option` DOM 节点，并在展开、聚焦、悬停、选择变化时遍历全部节点刷新视觉状态。当候选项达到一万条时，DOM 构建、状态刷新、flex 布局、滚动范围计算、绘制命令生成和命中测试都会被完整列表放大。

## 候选方案

- 限制 `DocumentSelectControl` 最多展示固定数量候选，其余要求调用方改用 `DocumentAutocompleteInputControl`。
- 在通用布局、绘制或命中层对 overflow 子节点做可见性裁剪。
- 在 `DocumentSelectControl` 内实现固定高度选项的虚拟窗口，只渲染可视行和少量 overscan。

## 最终选择

`DocumentSelectControl` 采用控件级虚拟化：完整选项数据仍保存在控件内部，popup DOM 只保留上下 spacer 和少量可复用 option 视图节点。滚动位置决定当前渲染窗口，鼠标、键盘和程序化选择继续使用真实选项索引。

## 选择原因

- 保持 `getOptionCount()`、`getSelectedIndex()`、`getSelectedOption()` 和 change event 的外部语义不变。
- 直接消除构造、状态刷新、布局、绘制和命中链路中的大部分 `O(total options)` DOM 成本。
- 不把 select 的固定行高和单选语义泄漏到通用布局引擎，避免给所有 overflow 容器引入复杂虚拟化协议。
- 仍保留 `DocumentAutocompleteInputControl` 作为需要搜索、过滤和相关度排序时的推荐控件。

## 影响范围

- `DocumentSelectControl` 的 popup 内部 DOM 不再等同于完整选项列表，测试和调试应以控件 API 或当前可视窗口为准。
- 默认滚轮和滚动条拖拽发生偏移变化后会分发元素 `scroll` 事件，供虚拟窗口同步使用。
- 远程 HTML `<select>` 仍可按完整 option 列表解析，提交值映射继续依赖真实 selected index。

## 后续注意事项

- 若未来支持可变高度 option、分组或自定义 option 内容，需要重新设计虚拟窗口的高度测量和索引映射。
- 若需要在超长列表中快速定位，仍应提供可输入过滤体验，而不是只依赖传统 select 滚动。
