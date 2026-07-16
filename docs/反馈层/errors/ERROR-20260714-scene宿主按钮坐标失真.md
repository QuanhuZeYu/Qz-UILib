# Scene 宿主按钮坐标失真

## 错误现象

Minecraft GUI Scale 不为 1 时，scene 的 MOVE/hover 与 DOWN/UP/click 落点不一致：可见按钮外仍可能命中，Portal 文本输入框鼠标点击可能无法聚焦或显示 caret。

## 触发场景

`McScreenBridge` 接收 Minecraft scaled 鼠标回调，同时 `LwjglInputSource` 的 MOVE 从平台 reader 读取 framebuffer 物理坐标。GUI Scale 为 2 等非 1 值时，两条路径混用。

## 根本原因

旧实现把 scaled 整数坐标乘 `scaleFactor` 反推物理坐标。缩放时已被整数除法丢弃的余数无法恢复，因此该结果不一定等于平台 reader 的当前物理坐标；DOWN/UP 与 MOVE 由此落在不同坐标事实。

## 修复方案

宿主回调只负责提供可靠的按钮边沿。`LwjglInputSource.pushPointerButton` 在 push 时从与 MOVE 相同的 `PlatformStateReader` 读取当前 X/Y，外部模式继续停产 poll 按钮边沿，避免双路事件。宿主回调坐标仅保留为兼容参数，不参与反推。

## 预防措施

- 坐标换算只允许在平台边界基于权威原始值做一次；禁止从已量化的 scaled 整数逆推原值。
- 旁路回归测试必须让宿主回调坐标与 reader 坐标刻意不一致，并断言 DOWN/UP 与 MOVE 最终采用 reader 坐标。
- GUI Scale、`ScaledResolution` 不得进入 UILib 自有 input 闭环，持续遵守 NORTH_STAR I13。
