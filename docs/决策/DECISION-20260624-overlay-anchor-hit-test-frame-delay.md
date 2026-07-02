# 决策：overlay 锚定 hit-test 滞后一帧属 retained-mode 固有延迟，接受不修

## 背景

reviewer 在 SceneSelect 三批缺陷修复后登记了 9 项非阻断建议，其中 B1 描述为：
「`AbstractSceneHostWidget.java:95-100` 的 replay 用 `entry.getAnchorX/Y()` 来自上一帧
layout 结果，当 anchor trigger 滚动/移动时存在 1 帧视觉错位窗口」。

经 explorer 逐帧推演 + oracle 独立核验，**原始前提有误**，实际窗口在 hit-test 而非 replay。

## 逐帧时序真相

`AbstractSceneHostWidget.render`（行 87-101）单帧执行序：

| 步骤 | 行号 | 操作 | anchor 状态 |
|------|------|------|-------------|
| 1 | 88 | `layout(root)` ① | 主树布局，刷新 trigger cachedLayout |
| 2 | 89 | `layoutOverlays` ① | 写 `entry.setAnchorX/Y`（行 132-133，用①几何 + 当前 scrollOffsetY） |
| 3 | 91 | `runtime.route(...)` | **hit-test 读 anchor**（`SceneInputRouter.java:388`），读的是①写的值 |
| 4 | 93 | `runtime.flush()` | scroll signal 落地：`setScrollOffsetY` → `markGeometryDirty`（`SceneNode.java:1182-1185`） |
| 5 | 94 | `layout(root)` ② | scroll 不重排（`SceneLayoutEngine.computeHeight:776-793` 钉死视口高），trigger LayoutBox 不变 |
| 6 | 95 | `layoutOverlays` ② | **重写 anchor**（用新 scrollOffsetY） |
| 7 | 97 | paint 主树 | 用新 scrollOffsetY 平移绘制 |
| 8 | 101 | replay overlay | **读 anchor**，读的是②写的新值 |

### 关键结论

- **replay 无视觉错位**：行 101 读的是同帧 flush 后行 95 ② 重写的 anchor，paint 与 anchor 同帧自洽
- **真实窗口在 hit-test**：行 91 route 在行 95 ② 之前，读的是上一帧 ② 写入的 anchor
  （`SceneOverlayHost.Entry:109-110` 字段跨帧持久），比 replay 滞后一帧
- 表现：overlay 命中区域跟手滞后约 1 帧，**视觉位置永远正确**

## 触发条件

比原始描述更窄：

- **listbox 内滚动不触发**：listbox 是独立 overlay root，自身 scrollable
  （`SceneSelect.java:222`），SCROLL handler `ctx.stopPropagation()`（`:233`），
  改的是 listbox 自身 scrollOffsetY，不改 trigger 在主树的 anchor 几何
- **仅 page 级滚动触发**：trigger 挂在 page viewport（scrollable）子树内时，
  滚 page viewport 会改变 trigger 的 absoluteBox → anchor 变化
- 需同帧凑齐三件事：(a) 下拉展开中 (b) page viewport 在滚 (c) 指针落在旧新 anchor 差集带
- 自然交互无法稳定复现

## 候选方案

1. **接受现状 + 文档登记**：0 代码，不破任何不变量
2. **route 前补 anchor 刷新**：拆 `layoutOverlays` 的 anchor 计算段为独立方法，
   插在 route 之前。但此时用 flush 前的 scrollOffsetY，仍滞后一帧——治标不治本
3. **重构 route 时序**：flush 后、route 前再 layout，等于打乱
   「layout→route→flush→layout」既定时序契约，破坏 CLICK 合成依赖的 DOWN/UP 同序

## 最终选择

采用方案 1：接受现状 + 文档登记。

## 选择原因

- **视觉零错位**：B1 只影响 hit-test 命中判定，不影响 paint。用户看到的浮层位置任何帧都正确
- **retained-mode 固有延迟**：「事件用上一帧布局」是 React/DOM/所有 retained UI 的共性，
  非本项目缺陷
- **触发面极窄**：listbox 内滚动不触发；仅 page 级滚动 + 同帧精确命中差集带
- **修复收益≈0，风险>0**：方案 2 治标不治本（flush 前 anchor 仍滞后）；
  方案 3 破坏输入时序契约（`SceneInputRouter` 单帧单遍历 + CLICK 合成）
- **I7 反被强化**：滚动只 `markGeometryDirty` 不重排，layout② 整树复用 return，
  trigger LayoutBox 不变。B1 正是「I7 让滚动不重排」的副产物——因为不重排，
  anchor 必须靠 layoutOverlays 重读几何，而 route 在其之前，遂滞后一帧

## NORTH_STAR 不变量核对

- **I7**：✓ 未破。滚动只 `markGeometryDirty`，layout② 整树复用
- **I8**：✓ 未破。anchor 滞后不涉及 fragment 重生成
- **I11**：✓ 未破。SCROLL handler 只 `scrollSignal.set`（逃生舱①只读几何测量），
  anchor 读取经 `AnchorProvider.get()`→`absoluteBox` 全程只读不标脏

三条不变量全部完好，**无需偏离登记**。

## 备案（仅当未来出现可感知 bug 报告时重启）

方案 2（route 前补 anchor 刷新）作为备案保留，但需注意：
- 工作量约半天（拆 `layoutOverlays:125-133` 的 anchor 计算段为独立方法）
- anchor 此时用 flush 前的 scrollOffsetY，仍滞后一帧——治标不治本
- 要真消除需在 flush 后、route 前再 layout，等于打乱既定时序契约
- 破坏 `SceneInputRouter` 单帧单遍历 + CLICK 合成时序假设

## 裁决依据

- explorer 逐帧推演（`AbstractSceneHostWidget.java:87-101` 时序）
- oracle 独立核验（确认 anchor 跨帧持久、scroll 不重排、SCROLL 只入队不即时生效）
- 拓扑实证（listbox 内滚动 `ctx.stopPropagation()` 不触发 B1）
- 2026-06-24 用户拍板派 oracle 裁决
