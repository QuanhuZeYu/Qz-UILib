# 决策：抽取共享固定行高虚拟候选列表

## 背景

`DocumentSelectControl` 已针对超长选项列表实现控件级虚拟化，只保留少量可复用 `option` 节点。输入框自动完成场景使用 `DocumentAutocompleteInputControl`，仍在每次候选刷新时清空并重建全部候选节点，字体名输入框等大量候选场景仍会卡顿。

## 候选方案

- 只降低 `DocumentAutocompleteInputControl` 的默认候选上限，减少一次性节点数量。
- 直接复制 `DocumentSelectControl` 的虚拟化代码到 `DocumentAutocompleteInputControl`。
- 抽取内部固定行高虚拟候选列表 helper，由 select 和 autocomplete 共用。

## 最终选择

抽取包内内部 helper，例如 `DocumentVirtualizedOptionList`，只负责固定行高虚拟列表的 spacer、复用节点、滚动窗口计算和按索引滚入视图。select 与 autocomplete 通过回调绑定候选文本、ARIA、视觉状态和点击行为。

## 选择原因

- 两个控件共享的只是固定行高候选列表性能逻辑，业务语义不同，不应抽成公开控件或通用布局引擎能力。
- helper 可以复用 `DocumentSelectControl` 已验证的虚拟化算法，避免 autocomplete 再复制一套难维护实现。
- 不改变公开 API，不影响业务作者使用方式。

## 影响范围

- `DocumentSelectControl`：内部虚拟化实现迁移到 helper，外部行为和测试应保持不变。
- `DocumentAutocompleteInputControl`：完整候选数据仍保留，但 popup DOM 只渲染可视窗口和少量 overscan 节点。
- 使用文档需更新 autocomplete 大候选说明，不再描述为保留全部 option 节点。

## 后续注意事项

- helper 仅支持固定行高候选，不扩展到可变高度选项或通用 overflow 容器虚拟化。
- `highlightedIndex`、`selectedIndex` 等状态继续由各控件维护，helper 不持有业务选择语义。
- 测试必须覆盖远端滚动后点击真实候选、query 变化后窗口重置、键盘高亮滚入可视区和 select 原有一万条选项回归。
