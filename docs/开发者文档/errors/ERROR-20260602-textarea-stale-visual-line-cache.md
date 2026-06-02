# ERROR-20260602 textarea 删除换行后复用过期视觉行缓存崩溃

## 错误现象

游戏运行时在 `/qzuilib test` 的 textarea 样例中输入或删除文本后崩溃，日志显示：

```text
java.lang.IndexOutOfBoundsException: Index 1 out of bounds for length 1
    at club.heiqi.uilib.ui.control.DocumentTextAreaControl.isSoftWrappedVisualLine(DocumentTextAreaControl.java:795)
    at club.heiqi.uilib.ui.control.DocumentTextAreaControl.requestCaretReveal(DocumentTextAreaControl.java:765)
    at club.heiqi.uilib.ui.control.DocumentTextAreaControl.replaceSelection(DocumentTextAreaControl.java:609)
    at club.heiqi.uilib.ui.control.DocumentTextAreaControl.deleteBackward(DocumentTextAreaControl.java:627)
```

## 触发场景

- textarea 已经渲染并生成两条或多条视觉行缓存
- 光标位于后续逻辑行，按退格删除前一个换行符
- 文本从多逻辑行合并成更少逻辑行后，`requestCaretReveal()` 立即使用上一帧 `visualLineMetrics`

## 根本原因

`syncContent()` 会重建 `logicalLines`，但 `visualLineMetrics` 仍保留上一帧的视觉行索引。删除换行后旧视觉行可能仍引用 `logicalLineIndex=1`，而新的 `logicalLines` 只剩 1 行，`isSoftWrappedVisualLine()` 直接 `logicalLines.get(1)` 导致越界。

不能简单在内容变化后清空全部视觉行缓存：既有逻辑依赖上一帧视觉行临时判断软换行，从而避免长行输入时把横向滚动推离 0。

## 修复方案

- 在 `isSoftWrappedVisualLine()` 中对旧视觉行的 `logicalLineIndex` 做边界保护
- 当旧视觉行索引已不属于当前 `logicalLines` 时，返回非软换行，避免越界
- 增加回归测试：渲染 `A\nB` 后将光标移到第二行行首，按退格删除换行，断言文本合并为 `AB` 且不崩溃

## 预防措施

- 任何缓存了逻辑行索引、布局盒索引或视觉行索引的运行时结构，在内容变更后使用前必须校验索引仍然属于当前数据集
- 修复缓存过期问题时要先检查既有行为是否依赖旧缓存作为过渡状态，不要无差别清空缓存
- textarea 的换行删除、软换行、光标 reveal 和滚动行为需要一起补回归测试
