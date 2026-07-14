# Scene 焦点投影与成员编辑时序缺口

## 错误现象

- Portal 中 TextInput 首次获得权威焦点后可以接收文本，但没有 focus border 与 caret；先点击内部按钮再点输入则恢复。
- LIST_MEMBERS 在空搜索词时点击当前成员 Edit 无可见变化，Delete 正常。

## 触发场景

- Portal 同一轮 effect 创建 TextInput 后立即 `requestFocus`，而 focused 派生 Computed 尚未首次求值。
- 当前成员 candidate 解析复用用户 query 结果；消费方明确让空 query 返回空，导致 candidate 缺失。

## 根本原因

- Router 已更新 `focusedNode`，但 `SceneInteractionState.focused()` 仍未声明，`writeFocused(true)` 按懒 signal 契约短路；TEXT 按权威节点路由，视觉则读取新建后默认 false 的 signal。
- 成员 selection 解码本身合法，但 candidate 解析与瞬时 query 耦合；Edit 又只有“candidate 存在且有 variants”视觉分支，unknown/无 variants 只写隐藏 editingId。

## 修复方案

- TextInput 建树期立即取得并复用 focused 只读 signal，确保任何焦点写入前完成声明。
- queryResults 只服务候选展示；当前成员先无损解码，再按唯一 candidate key 精确查询并只接受完全相等 key。
- unknown/无 variants 编辑保留稳定 memberId，进入聚焦的候选替换态；焦点意图成功执行后复位为 NONE，允许后续同值重放。

## 预防措施

- 新增依赖 Router 懒交互 signal 的控件时，测试“首次 effect flush 前 requestFocus/route”边界，并同时断言权威节点、signal 与视觉。
- 当前值解析与用户筛选必须保持两条派生链；测试空 query、模糊首项、重复 key 去重搜索、unknown/malformed 及稳定 id 原位替换。
