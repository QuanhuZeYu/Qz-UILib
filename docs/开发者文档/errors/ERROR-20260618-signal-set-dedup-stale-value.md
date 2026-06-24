# ERROR-20260618 — Signal.set 去重拿已 flush 旧值比较，吞掉「同帧 set 回帧初值」的写入

## 错误现象

中文输入法连续输入「好好好」时，输入框只显示一个「好」；输入「什么问题」等多字词也残缺。
单字符 emoji（单 codepoint、单 TEXT 事件）不受影响，故首轮 codepoint 修复时未暴露。

## 触发场景

任何「同一帧内把同一个 `Signal` 先 set 到中间值、再 set 回该帧初始值」的链路，例如：

- 文本框 handler 在同帧收到多个 TEXT 事件，用 `signal.set(model + text)` 累积（每次 set 值不同，但若中途又回到旧值则触发）
- 同帧 `[TEXT "好", BACKSPACE]`：先 `set("好好")` 再 `set("好")`，第二次 set 回到帧初值「好」
- toggle 同帧抖动 `false → true → false`
- 计数器同帧 `+1` 再 `-1`
- 拖拽回弹、撤销到原状等「终值 == 帧初值但中途经过别值」的所有场景

## 根本原因

旧 `Signal.set` 在写入时做相等去重：

```java
public void set(T newValue) {
    if (Objects.equals(newValue, value)) return; // value = 已 flush 的旧值（帧初值）
    ReactiveScheduler.get().queueWrite(this, newValue);
}
```

`ReactiveScheduler` 的写入队列是 FIFO，**flush 前 `value` 恒为帧初值**（reactive 帧末批处理的正确设计，I9）。
于是同帧第二次 `set(帧初值)` 时，`Objects.equals(帧初值, value=帧初值)` 为真，**该 set 被当成「无变化」直接丢弃、根本没入队**。
队列里只剩前一次的中间值，flush 后 signal 错误地落在中间值上。

本质：去重比较的对象错了——拿「已 flush 的帧初值」比较，而不是「该 signal 在本帧队列里的最后 pending 值」。这是 reactive 核心的 latent bug，文本输入只是第一个撞上的真实场景。

## 修复方案

把去重从 `Signal.set` 移到 `ReactiveScheduler.flush` 阶段，按「帧初值 vs 帧末合并终值」裁定净变化（I9「帧末批处理合并写入」的字面正确实现，不引入新 API、不改宪章条文）：

1. `Signal.set`：去掉 `Objects.equals` 守卫，只调 `queueWrite`。
2. `ReactiveScheduler`：写入队列从 FIFO `Deque` 换为 `LinkedHashMap<Signal<?>, Object> pendingWrites`，`queueWrite` 用 `put` 同 signal 覆盖末值、保留首次插入顺序。
3. `flush` 阶段1：快照并清空 pendingWrites，对每个 signal 对比 `peek()`（帧初值）与合并终值，仅 `!Objects.equals(before, after)` 才 `applyAndNotify` + 记事务日志条目。
4. `reset` 同步改 `pendingWrites.clear()`。

修复后：`set(B); set(A)`（A=帧初值）→ pendingWrites 只留 A → flush 时 `before(A)==after(A)` → 跳过 apply、不 markDirty、不重跑 effect、不入日志，行为最优。

## 预防措施

- 文本/可编辑状态不要用 `signal.get()` 当作权威当前值来「读-改-写」：在 reactive 帧末批处理模型下，flush 前 `get()` 恒返回旧值。应持有即时可变的应用/文档模型（demo 用私有 String 字段），handler 操作模型后 `signal.set(模型快照)`，signal 只作「模型 → 渲染」单向派生。见决策
  `DECISION-20260618-scene-text-input-model.md`。
- 任何 reactive 去重逻辑必须基于「帧初值 vs 帧末终值」，绝不能在 set 时拿尚未 flush 的 `value` 做比较。
- 回归测试必须覆盖「同帧 set 回帧初值 → 净无变化、effect 不重跑」这条（`ReactiveSchedulerMergeWriteTest`），以及 scene 层「同帧 hover A→B→A 中间节点不残留」（`SceneRouterInteractionTest`）。

## 关联

- 修复测试：`ReactiveSchedulerMergeWriteTest`（7 用例）、`TransactionLogTest` 拆分改写、`SceneHostWidgetTextModelTest`、`SceneRouterInteractionTest` 同帧多 MOVE 断言更新
- 审查留档：`docs/开发者文档/reviews/REVIEW-20260618-scene-input-focus-codepoint.md`
- 决策：`docs/记忆/决策/DECISION-20260618-scene-text-input-model.md`
