# ERROR-20260815-scene-picker-panel-flex-grow-overflow.md

**日期**：2026-08-15
**组件**：`ScenePickerPanel`（Phase B1 新增）memberRow / topBar
**状态**：已修复（嵌套结构与固定宽槽方案落地，测试锚定）

## 现象

`ScenePickerPanelTest` L3 集成测试中，listMembers 成员行的编辑/删除按钮点击全部失效、
受控维度切换点击失效。打印面板树发现按钮绝对坐标 X 超出画布（如 `remove center = 904`，
画布宽 800）：成员行内 `info.setFlexGrow(1)` 吞掉全部剩余宽，badge/edit/remove 三个固定
兄弟被推出行容器（行宽 320，内容合计 448）。

## 根因

ROW 容器 flexGrow 分配有「固定兄弟宽度可先验」前提。`info` 的固定兄弟是
`SceneButton` root（容器节点、无 preferredWidth，文本在内部 label 节点上）与动态宽 badge，
引擎无法先验其宽 → 触发布局警告「ROW 容器 grow 分配放弃」并给出错误的分配结果。
旧组件 `SceneSearchPicker.currentMemberRow` 早已规避：actions 收进**固定 preferredWidth
的嵌套行**，grow 只发生在「固定兄弟是文本节点」的行内。

## 修复

1. memberRow 改为嵌套结构（对齐旧组件范式）：
   `row[icon | info(flexGrow)[firstLine[primary(flexGrow)+badge] secondLine[secondary]] | actions(preferredWidth=180)[edit remove]]`。
   外层行的 grow 分配只面对 icon 与固定宽 actions；内层 firstLine 的 grow 固定兄弟是文本节点可先验。
2. topBar 搜索框由 `flexGrow(1)` 改为固定 `preferredWidth(360)`：维度分段
   （SceneSegmented root 是无 preferredWidth 的容器）作固定兄弟时同样无法先验，
   flexGrow 会把分段与统计挤出顶栏。
3. 成员列表行容器 `rows` 由 `flexGrow(1)` 改为 `setFillParentHeight(true)`：
   membersPanel 自身高度无法先验时 grow 分配整体放弃，rows 高度归零（成员行不可见）。

## 教训

- ROW 内 flexGrow 子旁边不能放「容器型、无 preferredWidth、无文本」的固定兄弟；
  必须给固定兄弟显式 preferredWidth，或把 grow 隔离到固定兄弟全是文本/固定宽节点的子行。
- 出现 `[QzUiLib/Layout] ... grow 分配放弃` 警告时即使表面布局看似正常，也应验证
  实际盒（`cachedLayout`）——溢出是静默的，点击命中与渲染都可能漂移。
- `rt.show` 的 anchor 在调用瞬间 append 到父容器，晚于它的兄弟会排在 anchor 之后；
  组件内 `show` 必须最后调用，否则后续 `appendChild` 的兄弟被 anchor 隔开（测试按
  children 下标定位时尤其致命）。
