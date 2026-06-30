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

## 第二次回归：坐标系 rootAbsY 错位（hit-test transform 修复后仍失效）

### 错误现象

`c62ae524` 修复 hit-test transform 错位后，单测全绿（单测 rootAbs=0,0），但真机仍拖不动。thumb 滚动到非顶部位置后点击 thumb 视觉位置，column DOWN handler 走 page 分支而非拖动分支（scroll 跳跃一页），拖动完全失效。

### 触发场景

- 真机 GUI 窗口居中显示，根节点屏幕绝对坐标 `rootAbsX/Y ≠ 0`（margin）
- thumb 滚动到非顶部位置（translateY > 0）
- 用户点击 thumb 视觉位置

### 根本原因

column DOWN handler 判定「点击是否落在 thumb 视觉区」时，坐标系错位：

- `ev.getPointerY()` 返回**画布逻辑坐标**（= host 局部 + rootAbsY，含屏幕绝对偏移）
- `SceneGeometry.absoluteBox(node, 0, 0)` 返回 **host 局部坐标**（不含 rootAbsY）
- 两者比对差一个 `rootAbsY`

后果（以 rootAbsY=30、scroll=100、thumb 视觉区 [233.5, 299.5) 为例）：
- 用户点击 thumb 视觉区 host 局部 Y=280 → 画布逻辑 Y=310
- 修复前：`ev.getPointerY()=310` 与 `thumbVisualBottom=299.5` 比对 → `310 >= 299.5` → 误判为 track 下方 → page down
- 实际应判为 thumb 视觉区 → 启动拖动

单测未覆盖：`routePointer` 默认 `rootAbs=0,0`，画布逻辑 == host 局部，错位不暴露。这是「单测坐标系与真机不一致」导致的假绿。

### 修复方案（commit `a7959d41`）

1. **SceneEvent 加 hostPointerY**：`SceneEvent` 新增 `hostPointerY` 字段（= `pointerY - rootAbsY`，host 局部），与 `absoluteBox` 同系
2. **delta 范式**：拖动闭包用 `ev.getHostPointerY()` 记 `dragStart[1]`，MOVE 时 `pointerDelta = ev.getHostPointerY() - dragStart[1]`，全链路 host 局部系，rootAbsY 自然消去
3. **column DOWN handler 改用 getHostPointerY**：判定 thumb 视觉区用 `ev.getHostPointerY()` 与 `absoluteBox` 比对，同系不再错位
4. **删除视觉中心校准**：原 `dragStart` 校准 thumb 视觉中心的逻辑删除，改为 delta 范式（dragStart[1]=点击点 host 局部 Y），首帧 MOVE delta=0 不跳跃
5. **单测补 rootAbsY≠0 路径**：新增 `dragFromThumbVisualPositionWithRootAbsYShouldStartDragNotPage`、`trackClickWithRootAbsYShouldStillPage`、`dragFromThumbLayoutPositionWithRootAbsYShouldDrag`、`dragWithRootAbsYShouldClearOnCancel` 等，显式传 rootAbsX/Y≠0

### 预防措施（补充）

- **单测必须覆盖 rootAbs≠0 路径**：凡涉及指针坐标与节点几何比对的 handler，单测必须显式传非零 rootAbsX/Y，不能只测 rootAbs=0,0 默认路径
- **坐标系一致性是硬约束**：handler 内所有坐标比对必须同系。`ev.getPointerY()` 是画布逻辑（含 rootAbs），`absoluteBox` 是 host 局部（不含 rootAbs），两者混用必错位
- **优先用 hostPointerY**：与节点几何比对的场景，优先用 `ev.getHostPointerY()`（host 局部），与 `absoluteBox` 同系
- **delta 范式优于绝对位置校准**：拖动用 delta（终点 - 起点）驱动，起点记点击点本身，避免「校准到视觉中心」这类隐含坐标系假设

## 关联

- 上游修复：`0160e4df`（滚动条全面修复，引入 COMPOSITE transform 平移 thumb）
- 第一次修复：`c62ae524`（hit-test 几何错位 → column 转发拖动）
- 第二次修复：`a7959d41`（坐标系 rootAbsY 错位 → SceneEvent 加 hostPointerY + delta 范式 + 删除视觉中心校准）
- Oracle C 复审：两次均通过，无阻断

## 第三次回归：坐标系错位是系统性问题（不止 scrollbar）

### 错误现象

第二次修复 scrollbar 后，复查同栈其它控件发现**同款坐标系错位**普遍存在：

- `SceneSlider`：拖动提交值用 `ev.getPointerX()`（画布逻辑，含 rootAbs）与 `absoluteBox(track, 0, 0)`（host 局部）混比，rootAbs≠0 时提交值偏移
- `SceneTextInput`：点击定位用 `ev.getPointerX()` 与 `absoluteBox(content, 0, 0)` 混比，rootAbs≠0 时点击列偏移
- `SceneTextAreaPrimitive`：点击行号 + 行内 X 同款混比，rootAbs≠0 时点击行列偏移

### 触发场景

- 真机 GUI 窗口居中显示，`rootAbsX/Y ≠ 0`
- 任意涉及「指针坐标与节点几何比对」的 handler，且单测只覆盖 `rootAbs=0,0`

### 根本原因（系统性）

不止控件层写错，**SceneEvent 注释误导是根因之一**：

- `SceneEvent.pointerX/Y` 注释标"不叠加 rootAbs"，但实际是**屏幕绝对坐标、含 rootAbs**
- 多个控件作者按注释写 `pointerX - absoluteBox(node, 0, 0)`，以为同系，实际差一个 rootAbs
- 单测默认 `rootAbs=0,0`，画布逻辑 == host 局部，错位不暴露（假绿）

### 修复方案（commit `0da919b6`，第 1 轮）

对齐 Flutter 三件套（raw / host / local + 框架自动注入 local），分 3 轮落地。第 1 轮：

1. **SceneEvent 主树新增 `hostPointerX/Y`**：= `pointerX/Y - rootAbsX/Y`，host 局部，与 `absoluteBox` 同系
2. **I12 不变量**：坐标系三层（raw/host/local）+ handler 默认 local + raw 与 `absoluteBox(0,0)` 禁止混比
3. **3 控件同系修正**：
   - `SceneTextInput`：点击定位改 `hostPointerX`
   - `SceneSlider`：拖动提交值改 `hostPointerX`
   - `SceneTextAreaPrimitive`：点击行号 + 行内 X 改 `hostPointerX/Y`
4. **SceneEvent 注释修正**：`pointerX/Y` 明确标"屏幕绝对、含 rootAbs"，删除"不叠加 rootAbs"误导
5. **NORTH_STAR I12**：写入宪章不变量

第 2 轮（待做）：overlay localPointer + 结构对齐（hitTestable 调整，框架自动注入 local）。
第 3 轮（待做）：旧 API `pointerX/Y` 改名 / 废弃。

详细决策见 `docs/记忆/决策/DECISION-20260630-coordinate-system-flutter-alignment.md`。

### 预防措施（系统性补充）

- **坐标系错位是系统性问题，不是 scrollbar 个例**：凡涉及指针坐标与节点几何比对的 handler，必须确认同系。`pointerX/Y`（raw 屏幕绝对）与 `absoluteBox(0,0)`（host 局部）混比必错位
- **I12 契约硬约束**：handler 内坐标比对必须同系；raw 与 `absoluteBox(0,0)` 禁止混比；优先用 `hostPointerX/Y`（第 1 轮）/ `localPointerX/Y`（第 2 轮后）
- **handler 默认 localPointer**：对齐 Flutter/Compose/Android/Web/SwiftUI 共识，框架自动注入 local，业务 handler 不手动减 rootAbs
- **单测必须覆盖 rootAbs≠0**：凡涉及指针坐标与节点几何比对的 handler，单测必须显式传非零 `rootAbsX/Y`，不能只测 `rootAbs=0,0` 默认路径（否则假绿）
- **注释必须与实际语义一致**：`SceneEvent.pointerX/Y` 注释误导是本次系统性错位根因之一，注释修正与代码修正同等重要
