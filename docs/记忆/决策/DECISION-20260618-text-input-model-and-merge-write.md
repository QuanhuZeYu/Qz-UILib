# 决策：文本输入即时可变模型 + 同帧写入合并语义

## 背景

Scene 输入层 I4 真机验收发现中文 IME 连打「好好好」只进一个「好」、「什么问题」残缺。
根因诊断分两层：

1. **表层（demo handler 写法）**：文本框 handler 用
   `inputTextSignal.set(inputTextSignal.get() + text)` 累积文本。SDL `onTextEvent`
   在同一帧 push 多个 TEXT 事件时，route 在 flush 之前连续调用 N 次 handler，而
   reactive `Signal` 在 flush 前 `get()` 恒返回旧值（I9 帧末批处理），导致同帧 N 次
   累积互相覆盖，只剩最后一个字。
2. **深层（reactive 核心 latent bug）**：`Signal.set` 的相等去重拿「已 flush 的旧值」
   比较，使「同帧 set 到中间值、再 set 回帧初值」的第二次 set 被误判无变化丢弃。
   详见 `docs/开发者文档/errors/README.md` 事件系统类（reactive 去重通则）。

## 候选方案

- **A（采纳，表层）**：文本控件持有即时可变文本模型（私有字段），handler 操作模型而非
  读 signal，signal 仅作「模型 → 渲染」单向派生。
- **B（否决）**：给 `Signal` 加 `set(Function<T,T> updater)` 链式累积 API。动 reactive
  地基、引入新心智模型、破坏「flush 前 get 恒旧值」极简不变量，且即便加了文本控件仍
  该有自己的模型——B 是绕过模型缺失的补丁。
- **C（否决，核心去重）**：在 source/bridge/route 层合并同帧 TEXT 事件。合并有损
  （丢时间戳/事件独立性），只解决「同帧多 TEXT」一种表现，且污染核心 route 语义。

## 最终选择

1. **文本输入即时可变模型范式（方案 A）**：可编辑控件（输入框/文本域/富文本）持有
   即时可变文本载体作为「权威当前文本」，handler 只操作模型 → `signal.set(模型快照)`
   做渲染派生，**handler 绝不 `signal.get()` 当文本读**。codepoint-aware 操作
   （`offsetByCodePoints` 删除、cursor 用 codepoint index）。
2. **同帧写入合并语义（reactive 核心）**：去重从 `Signal.set` 移到 `flush` 阶段——
   `queueWrite` 按 signal 合并末值（`LinkedHashMap`），flush 阶段1 对比「帧初值」
   （`peek()`）与「合并终值」，仅净变化才 apply + markDirty + 记事务日志。

## 选择原因

- 方案 A 守 I1/I9/I11：模型字段是控件私有的应用/文档模型、不是渲染源；渲染层看到文本
  变化的唯一通道仍是 `signal.set`。等价于「读外部数据源算出新值再 set」，与 React
  `useState` 背后的可变存储同理。
- 核心合并语义是 I9「帧末批处理合并写入」的**字面正确实现**，不改任何宪章条文、不引入
  新 API，只是把去重移到它本该在的位置（对比帧初值 vs 帧末终值）。
- 终值 == 帧初值时 `before==after` → 不 apply、effect 不重跑，比「不去重全 apply」
  更优（后者会 apply 中间值并 markDirty，在脏标记体系里触发冗余 relayout/repaint）。

## 影响范围

- reactive 核心：`Signal.set`（去掉 set 层去重）、`ReactiveScheduler`
  （writeQueue → pendingWrites LinkedHashMap、flush 阶段1 重写、commitTransaction 简化）。
  影响所有 signal 写入路径，但语义只在「同帧 set 回旧值/抖动」边界 case 上变化——
  从「错误残留中间值」变为「正确吸收为无净变化」。
- TransactionLog：每 signal 单条 before/after，删掉旧「首次/后续」特判；undo/redo
  （`applyAndRerun` 不经队列）零改动。
- demo 适配层 `SceneHostWidget`：两个文本框各持私有 String 字段。

## 后续注意事项

- 真实文本控件应把私有 String 字段沉淀为独立模型类（`TextInputModel`/`TextDocument`）：
  即时可变载体 + codepoint-aware 操作 + `snapshot()` 供 signal 派生 + cursor 用
  codepoint index。这是所有可编辑控件的统一基线。
- 任何「读 signal → 改 → 写回同一 signal」的同帧高频场景都要警惕：signal 不是即时
  可变存储，flush 前 get 恒旧值。需要即时累积/读改写的状态必须用独立可变模型承载。
- reactive 是宪章地基，本次合并语义改动经用户显式拍板批准后落地。
