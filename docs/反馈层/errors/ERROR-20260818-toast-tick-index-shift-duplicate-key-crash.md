# ERROR-20260818-toast-tick-index-shift-duplicate-key-crash.md

**日期**：2026-08-18
**组件**：`ui.scene.control`（SceneToast.Host.tick）
**状态**：已修复（tick 状态机改为构建式列表更新，回归测试 OverlayKeyIntegrityTest 锚定）

## 现象

真机：打开 `/qzuilib test` 测试场地操作浮层页后，渲染屏崩溃：
`IllegalStateException: forEach 检测到重复 key [28]：key 必须唯一，否则无法对齐新旧项`
（crash-2026-08-18_14.02.57-client.txt，Screen: TestPlaygroundScreen）。
headless 复现（OverlayKeyIntegrityTest 修复前版本）：show/tick 高频交错 8 轮内必崩，
崩溃列表 keys=[2,...,18,19,19,21,22,23,24]——id=19 出现两次、id=20 被覆盖消失。

## 根因：tick 单次遍历内「结构修改 + 陈旧索引回写」的列表竞态

`Host.tick` 对 toast 列表做 read-modify-write，同一遍历内混用两种破坏长度的操作：

1. 退场完成的条目：`next.remove(entry)` —— **列表缩短 1**；
2. 进入退场的条目：`next.set(i, entry.enteringLeave(now))` —— **仍用原列表索引 i 回写**。

当同帧既有移除（靠前的条目）又有退场标记（靠后的条目）时：remove 之后所有后续条目的
索引整体前移 1，`set(i)` 落在错位的槽位上——leaving 副本覆盖了相邻条目，而原条目还在
列表中 → **同一 id 出现「原条目 + leaving 副本」两份** → forEach 的 key 唯一性被打破，
reconciler 按设计抛异常（keyed 列表无法对齐新旧项）。

非多线程竞态（渲染线程单线程）；本质是「遍历中结构性修改后继续使用陈旧索引」的
逻辑竞态。旧版 tick 只 `remove` 不 `set`，没有错位窗口；本轮浮层改进加入退场状态机
（enteringLeave + set(i)）后引入。

## 修复

`Host.tick` 改为**构建式**更新：next 从空开始，跳过退场完成条目（跳过即移除）、
替换进入退场的条目（enteringLeave）、原样追加其余条目——**绝不按原列表索引回写**，
索引错位从类型上被消除（remove/set 两操作都消失）。懒创建语义保留：无任何变化时
next 保持 null，不产生列表拷贝与信号写入。

```java
// 修复后：跳过即删、逐条构建，无 remove/set(i)
if (leavingAt > 0 && nowNanos - leavingAt >= LEAVE_DURATION_NANOS) {
    if (next == null) next = new ArrayList<>(current.subList(0, i));
    nodeByEntryId.remove(Long.valueOf(entry.id()));
    continue;
}
if (leavingAt == 0 && nowNanos - entry.createdAtNanos() >= entry.durationNanos()) {
    entry = entry.enteringLeave(nowNanos);
    if (next == null) next = new ArrayList<>(current.subList(0, i));
    next.add(entry);
} else if (next != null) {
    next.add(entry);
}
```

## 教训

- **列表 read-modify-write 禁止「遍历中 remove + 按原索引 set」混用**：结构性修改后所有
  索引失效，`set(i)` 必然错位覆盖。要改就全量构建式（跳过/替换/追加），要么只删不写。
- **keyed 列表的重复 key 是数据层缺陷的哨兵**：reconciler 抛异常是正确行为，修法是
  找到产生重复的写路径，而不是放宽检测。
- **headless 高频交错复现比真机更快定位**：show/tick 交错 8 轮内必崩的循环 30 行即可
  复现真机 40 秒操作；崩溃时把 reconciler 的完整 key 列表打进异常消息，一次即可锁定
  「19 双份、20 被覆盖」的错位模式。
- **Signal pending-write 语义下 read-modify-write 的基值可能过期**：show/tick 都读
  `entries.get()` 再 set，跨 flush 的读写顺序依赖调度器 drain/sweep 交替；构建式更新
  至少保证单次遍历内部自洽，不依赖外部时序。
