# fontsort 拖拽视觉预览态 signal 化决策

## 决策

允许拖拽视觉预览态 signal 化，用于被拖行浮起跟随等纯视觉反馈。

## 原因

用户已批准 fontsort 拖拽全套手感升级。浮起跟随需要从 POINTER_MOVE handler 写入偏移，
再经 signal -> bind -> transform 链驱动视觉移动；该链路符合 UI = f(state)，且 transform 属
COMPOSITE 级，不触发布局或绘制重算。

## 约束

- 拖拽业务真值（落点 index 等）仍当场按事件坐标计算，不做 signal 化读路径。
- 拖拽视觉预览态只能使用 owner-scoped 单一 signal，随组件卸载退订。
- 视觉预览态只允许驱动 COMPOSITE 级 transform，不触 layout/paint。
- 拖拽结束（UP/CANCEL）必须 reset 归零，避免预览态泄漏。

## 影响

- `SceneDragReorder` 可引入 `dragOffsetSig` 驱动被拖行 transform。
- `docs/设定值层/硬约束总目录.md` §5 从“拖拽瞬态 signal 只写不读”细化为业务真值与视觉预览态分层。
