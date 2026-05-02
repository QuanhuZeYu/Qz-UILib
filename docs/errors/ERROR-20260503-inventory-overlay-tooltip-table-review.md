# 2026-05-03 背包 overlay、tooltip 与 table 语义审核问题

## 错误现象
- 同一背包页内 hotbar 与 backpack 两个网格都携带鼠标物品快照时，鼠标携带物品 overlay 可能被重复绘制。
- 鼠标悬停在同一槽位时，槽位内容变化后 tooltip 文本可能保留旧内容。
- 无表头背包 slot table 仍保留空 `thead`，HTML-like 语义不够干净，也让测试依赖脆弱的子节点下标。

## 触发场景
- `inventory_overview` 同时刷新 hotbar/backpack 两个 `DocumentInventorySlotGridControl` 的 carried snapshot。
- 鼠标停在槽位上执行拾取、放置或其他导致槽位内容变化的操作。
- 背包 slot grid 通过 `DocumentTableControl` 创建仅有表体的表格。

## 根本原因
- 鼠标携带物品 overlay 被建模在每个 grid 控件内部，但页面上多个 grid 共享同一 carried snapshot，缺少单一负责者。
- tooltip 文本只在 hover enter 时解析，`refreshSlotStates()` 没有同步更新当前 hover 槽位的 tooltip 文本。
- `DocumentTableControl` 构造时无条件挂载 `thead`，即使没有调用 `setHeader(...)` 也会留下空表头节点。

## 修复方案
- `DocumentInventorySlotGridControl` 增加 `setCarriedItemOverlayEnabled(...)`，背包页只让 hotbar grid 负责登记鼠标携带物品 overlay，backpack 仅保留 carried snapshot 用于 tooltip 抑制。
- `refreshSlotStates()` 在存在 hover 槽位时重新解析可见 tooltip 文本，避免数据变化后显示旧内容。
- `DocumentTableControl` 改为默认只挂载 `tbody`，调用 `setHeader(...)` 时按需挂载 `thead`；清空表头时移除空 `thead`。

## 预防措施
- 页面级 overlay 若来自全局状态，必须明确单一登记入口，避免每个局部控件重复绘制。
- hover 派生内容不能只在 hover enter 时采样；如果底层数据会变化，刷新数据状态时应同步刷新 hover 派生内容。
- HTML-like 结构测试应优先按标签或控件公开 accessor 获取语义节点，不应依赖 `getChildren().get(1)` 这类脆弱下标。
