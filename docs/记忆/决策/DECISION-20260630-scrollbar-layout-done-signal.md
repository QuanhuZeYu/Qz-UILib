# DECISION 2026-06-30：Scrollbar layoutDoneSignal 方案 A（int epoch + host 桥接）

## 问题

SceneScrollbar 的 LAYOUT/COMPOSITE/PAINT bind 需读 viewport 的 LayoutBox 派生 thumb 几何，
但 bind 在 effect flush 阶段执行，此时 LayoutBox 可能仍是上一帧的旧值（layout 在 flush 之前跑）。
导致两个缺陷：

- **B3 resize**：viewport 高度变化后，thumb 几何滞后一帧才更新
- **C4 section 切换**：内容高度变化后，thumb 几何滞后一帧

## 候选方案

### A：int epoch + host 桥接（零滞后）

- `SceneLayoutEngine` 持 `int layoutEpoch`，每次 `layout()` 末尾自增
- `AbstractSceneHostWidget` 持 `Signal<Integer> layoutDoneSignal` + `int lastSeenLayoutEpoch`
- host 在第一次 layout 后比对 epoch，不等则 `layoutDoneSignal.set(epoch)`
- scrollbar bind 订阅 `contentChangedSignal`（即 host 的 `layoutDoneSignal`），同帧 flush 内重跑读最新 LayoutBox

### B：host 每帧 bump

- host 每帧无条件 `layoutDoneSignal.set(frameCount++)`
- scrollbar bind 每帧重跑

### C：LayoutBox detector

- scrollbar bind 内部检测 LayoutBox 引用/版本变化，自行决定是否重算
- 不依赖外部 signal

## 三方研究结论

- **A**：与 `measurer.epoch` 同构（节点级 epoch 比对已验证模式），守 I6（layout 层只持 int epoch，signal 在 host 桥接，paint 层不持 signal），零滞后（同帧 flush 内读到最新 LayoutBox），Computed 记忆化天然过滤 signal 值不变的重算
- **B**：滞后一帧（host 在帧末 bump，scrollbar 下一帧才重跑）+ 100+ 处 host 调用点需逐一确认 bump 时机，漏 bump 即失效，维护成本高
- **C**：破坏 LayoutBox 节点纯数据契约（需在 LayoutBox 上加版本号/引用变更检测），违反 I6（paint 层感知 layout 内部状态），实现复杂

## 决策

**采用方案 A**：int epoch + host 桥接 + 零滞后路径。

## 理由

1. **守 I6**：layout 层只持 int epoch 计数器，signal 在 host 桥接层创建和维护，paint/effect 层只读 signal，不感知 layout 内部状态
2. **零滞后**：host 在 layout 后立即桥接 `set(epoch)`，scrollbar bind 在同帧 flush 内重跑，读到最新 LayoutBox
3. **Computed 记忆化天然过滤**：signal 值不变时 Computed 短路不重算，干净帧无额外开销（注：当前 epoch 每帧自增，host 每帧 set，Computed 每帧重算；后续可选优化：几何变化才自增 epoch，进一步减少干净帧重算——见交接记录可选优化 1）
4. **与现有模式同构**：`measurer.epoch` 已是节点级 epoch 比对的成熟模式，`layoutEpoch` 是引擎级同构扩展，认知一致
5. **简化测试**：测试用 `contentChangedSignal.set(...) + runtime.flush()` 模拟 host 桥接，无需真实 host

## 受影响文件

- `src/main/java/club/heiqi/uilib/ui/scene/layout/SceneLayoutEngine.java`：新增 `layoutEpoch` 字段 + `layoutEpoch()` 读方法 + `layout()` 末尾自增
- `src/main/java/club/heiqi/uilib/internal/devtools/pages/AbstractSceneHostWidget.java`：新增 `layoutDoneSignal` + `lastSeenLayoutEpoch` + 桥接逻辑 + `layoutDoneSignal()` 读方法
- `src/main/java/club/heiqi/uilib/ui/scene/control/SceneScrollbar.java`：`Props` 新增 `contentChangedSignal` 字段，LAYOUT/COMPOSITE/PAINT bind 订阅它

## 后续可选优化

- **优化 1**：`layoutEpoch` 改为几何变化时才自增（需 LayoutResult 携带几何变化标志），减少干净帧 Computed 重算。当前评估收益边际、风险较高（漏判导致 scrollbar 不更新），登记后续可选不做。
