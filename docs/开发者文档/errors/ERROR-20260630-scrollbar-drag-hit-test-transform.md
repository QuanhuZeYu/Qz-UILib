# ERROR-20260630 滚动条拖拽失效（hit-test 不叠加 transform → thumb 命中区错位）

## 错误现象

滚动条无法拖拽。thumb 滚动到非顶部位置后，用户点击 thumb 视觉位置并拖动，thumb 完全无响应，DOWN 事件不触发拖动逻辑。

## 触发场景

- 内容溢出，thumb 滚动到非顶部位置（translateY > 0）
- 用户点击 thumb 视觉位置（实际位于 column 中段）并尝试拖动
- thumb 的 DOWN handler 永不触发，拖动无法启动

## 根本原因

thumb 用 COMPOSITE 级 `Transform.translate` 平移（守信条五「零重排」——滚动只走合成层 transform，不回流 layout）。但 `SceneHitTester` 命中测试只读 `LayoutBox`，不叠加 transform（`SceneHitTester.java:77-78`）。

后果：
- thumb 的 `LayoutBox` 位置永远在 column 顶部 Y=0
- thumb 的视觉位置在 `translateY`（由 COMPOSITE transform 平移）
- 用户点击 thumb 视觉位置时，hit tester 用布局位置判定，命中的是 column 而不是 thumb
- thumb 的 DOWN handler 永不触发 → 拖动闭包永不启动

这是「信条五零重排」与「hit-test 只读 LayoutBox 契约」之间的隐性冲突：transform 平移让视觉位置与布局位置分离，而 hit-test 只认布局位置。

## 修复方案

不破坏「零重排」信条，也不修改 hit tester 契约（hit tester 叠加 transform 会影响所有 COMPOSITE 级 transform 节点，风险面过大）。改由父节点 column 转发交互：

1. **column DOWN handler 检测 thumb 视觉区**：column 收到 DOWN 时，计算 thumb 当前视觉位置（手动叠加 transform），判断 `clickY` 是否落在 thumb 视觉区内
2. **手动启动拖动**：若命中 thumb 视觉区，column 复用 thumb 的 `dragStart` / `dragging` 闭包启动拖动
3. **dragStart 校准视觉中心**：拖动起点用 thumb 当前 `translateY`（视觉位置）作为基准，而非布局位置 Y=0，避免拖动起始跳变
4. **requestPointerCapture**：column DOWN 内 `ctx.requestPointerCapture()` 捕获指针
5. **column 注册 MOVE / UP / CANCEL**：column 注册完整指针生命周期 handler，转发给拖动闭包，确保 POINTER_UP / POINTER_CANCEL 均清除 `dragging` 状态 + 释放 capture

## 预防措施

使用 COMPOSITE 级 transform 平移的可命中节点，必须考虑 hit-test 只读 `LayoutBox` 的契约：

- 若节点需要点击 / 拖拽交互，且 transform 平移让视觉位置偏离布局位置，则：
  - 方案 A：改用 LAYOUT 级 `setY` 平移（破坏零重排信条，需评估性能影响）
  - 方案 B：由父节点转发交互（本案例采用，守零重排）
  - 方案 C：扩展 hit tester 叠加 transform（影响面大，需全量评估所有 COMPOSITE transform 节点）
- 新增 COMPOSITE 级 transform 平移的可命中节点时，必须在 PR 自检清单中回答「hit-test 如何命中此节点的视觉位置」
- 拖动闭包设计应可被父节点复用（dragStart / dragging 解耦于具体 handler 宿主），便于转发方案落地

## 相关文件

- `SceneHitTester.java:77-78`（hit-test 只读 LayoutBox 契约）
- `SceneScrollbar`（thumb COMPOSITE transform 平移 + column 转发拖动）
- 信条五「零重排」（`NORTH_STAR.md`）

## 关联

- 上游修复：`0160e4df`（滚动条全面修复，引入 COMPOSITE transform 平移 thumb）
- 本次修复：`c62ae524`（hit-test 几何错位 → column 转发拖动）
- Oracle C 复审：通过，无阻断
