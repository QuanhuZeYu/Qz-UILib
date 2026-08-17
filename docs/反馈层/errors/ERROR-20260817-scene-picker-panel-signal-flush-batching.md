# ERROR-20260817-scene-picker-panel-signal-flush-batching.md

**日期**：2026-08-17（首次出现于第八轮，本文补档）
**组件**：`ScenePickerPanel`（隐式武装/重武装决策）与 scene 响应式调度器
**状态**：已规避（局部布尔决策 + 回归测试锚定）

## 现象

LIST_MEMBERS 面板内「点击候选即隐式新增 → 成功后重新武装」的决策链上，若在
同一事件派发内先 `Signal.set` 再读回该信号，读到的仍是旧值：隐式武装判定用
`addingMember.get()` 判断是否已武装时，前一次 `beginAdd` 写入的 TRUE 尚未生效，
导致重复武装或走错分支。

## 根因

`Signal.set` 是帧末批处理语义（`ReactiveScheduler.flush` 统一落地），不是同步写。
同一 dispatch 内的「写后读」必然读到旧值，任何基于「刚写入」的后续分支都会失真。

## 修复

- `commitSelection` 用局部布尔（`armedNow`/`implicitArm`）承载同帧决策，不读回
  刚写入的 `addingMember`；重武装决策同样由局部布尔推导（第八轮 `cc6f76dc`）。
- 删除一步直达（第十二轮 `b6a699f8`）的删除路径不读回任何同帧信号，未引入回归。

## 教训

- 响应式框架的写入落地时机是契约的一部分：事件回调内「写信号后读信号」一律视为
  读旧值，状态机决策必须用局部变量或显式参数传递。
- 测试断言信号值时，click/键盘辅助必须先 `flush` 再断言，否则读到批处理前旧值。
