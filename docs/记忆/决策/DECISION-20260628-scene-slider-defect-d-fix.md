# DECISION-20260628：SceneSlider 缺陷 D 根治（修法甲 + 全面重构）

## 状态

已落地（2026-06-28，commit c37b1b3c 合回 4.0）。reviewer 审核通过
（6 通过 + 2 有条件通过，有条件项已在 1d830ec0 修复）。

## 决策缘起

SceneSlider 在用户使用中暴露「松手提交偶发丢失」缺陷（缺陷 D）。根因诊断
（详见 `DECISION-20260627-display-list-contract-line.md` 决策缘起段）定位到
**Signal 与渲染帧严重绑定**——`draggingValue` 走 `queueWrite` 帧末 flush，
但 UP handler 同帧读回依赖"写后同帧可见"，契约错配。

经 Oracle 两轮评估后，用户曾拍板"slider 不修，等并发重构后整体重写"
（见 DECISION-20260627 阶段 0 取消）。后用户推翻该拍板，决定先用修法甲
独立根治缺陷 D，并发框架方向单独保留。

## 候选方案

- **修法甲**：UP/MOVE handler 用事件自身坐标 `valueFromPointerX` 当场算提交值，
  不读回刚 set 的瞬态 `draggingValue` signal。3-5 行可解契约错配。
- **控件级事件总线**：破 R1/R7 + reactive 劣化重造，解决错问题（Oracle 已否决）。
- **独立总线线程**：契约错配与线程化正交，换得更难调试的跨线程 race（Oracle 已否决）。
- **不修，等并发重构整体重写**：原拍板，已推翻。

## 最终选择

采用**修法甲**，并扩展为全面重构：

1. **`draggingValue` 降级为纯渲染只写不读**：dragging 会话期间 `draggingValue`
   signal 仅用于 thumb 实时位置渲染，handler 永不读它作为提交值来源。
2. **UP/MOVE 用事件坐标当场算提交值**：`valueFromPointerX(pointerX)` 在 handler
   内当场计算，提交值（`onChange`）与渲染值（`draggingValue`）同帧由同一坐标源
   推导，消除"写后同帧读"契约错配。
3. **capture 托管拖拽会话**：POINTER_DOWN 时 `requestPointerCapture`，UP 时释放，
   保证拖拽期间指针事件不丢失到兄弟节点。
4. **三处 NaN/Infinity 防御**：`valueFromPointerX` 输入边界 + 除零保护 + 落点 clamp。

## 选择原因

1. **根因是单线程时序问题，不是架构问题**：契约错配源于"同帧写后读"，修法甲
   从源头消除"读瞬态 signal"的依赖，无需动 reactive 内核或引入线程化。
2. **修法甲是可复用到其他拖拽控件的范式约束**：未来范围滑块、可拖拽分隔条等
   拖拽类控件都应遵循"瞬态拖拽 signal 只写不读、业务值用事件坐标当场算"的纪律。
3. **不阻塞并发框架方向**：缺陷 D 独立根治后，Display List 契约线 + 子树并行
   方向不再以"slider 待修"为缘起，可独立推进（见 DECISION-20260627）。
4. **YAGNI**：控件级事件总线 / 独立总线线程都是解决错问题，YAGNI。

## 影响范围

- `SceneSliderPrimitive.java`（327 行，新建）：primitive 层，承载修法甲核心逻辑
- `SceneSlider.java`：wrapper 层，`thumb` 定位因布局引擎无绝对定位而回退的取舍
  （见 `SceneSlider.java:49`，属长期边界）
- 26 测试全绿，含缺陷 D 回归锚点

## 后续注意事项

- **拖拽类控件范式约束**：未来新增拖拽类控件（范围滑块、可拖拽分隔条等）必须
  遵循"瞬态拖拽 signal 只写不读、业务值用事件坐标当场算"纪律，不得读回刚 set
  的瞬态 signal 作为提交值来源。
- **`draggingValue` 只写不读是硬约束**：任何"为了方便"读 `draggingValue` 做
  提交值的改动都是缺陷 D 复辟的起点。
- **thumb 绝对定位边界**：当前布局引擎无绝对定位，thumb 用 ROW 布局自然定位 +
  空白占位节点实现，若未来布局引擎补绝对定位能力，可重新评估 thumb 定位方案。

## 决策来源

- 触发：SceneSlider 松手提交偶发丢失（缺陷 D）→ Oracle 两轮评估 →
  用户推翻"不修"拍板 → 修法甲独立根治 + 全面重构
- commit：c37b1b3c（合并）/ 1d830ec0（reviewer 建议级修复）/ 058ade04（测试修复）
- reviewer：6 通过 + 2 有条件通过，有条件项已在 1d830ec0 修复
  （完整 review 见 commit message，未单独落盘 review 文件）
- 关联决策：`DECISION-20260627-display-list-contract-line.md`（并发框架方向，
  原阶段 0 取消已由本决策推翻）
