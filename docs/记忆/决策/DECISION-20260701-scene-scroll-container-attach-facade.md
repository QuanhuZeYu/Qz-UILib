# DECISION 2026-07-01：SceneScrollContainer.attach 一行门面（对齐 Compose Box+align 模式）

## 背景

`SceneScrollHostWidget`（滚动 demo 页）手建 viewport + `preferredHeight=240` 钉死、无可视滚动条，
作为滚动引擎的底层验收 demo 是合格的，但作为「滚动容器」的易用性样板存在两个问题：

- **缺可视滚动条**：用户只能靠滚轮/拖动条感知位置，无 thumb 几何反馈，体验低于主流框架
- **样板冗长**：调用方需手写「建 container → 建 viewport → 建 content → SceneScrolls.attach → 可选建 scrollbar」5 步，
  每个新建滚动容器的地方都要重抄一遍，复发风险高

已有的 `SceneScrollContainer.create` 是中层工厂（返回 `Result` 含 container/viewport/content/scrollSignal），
解决了样板收敛，但仍要求调用方自己处理 `Props`（8 参）+ 挂载到 parent + 设 flexGrow。

## 调研结论

### 主流框架 scrollbar 结构：viewport 兄弟（非子）

所有主流框架（Compose、Flutter、iOS UIKit、Web CSS）的 scrollbar 都是**视口的兄弟节点**，叠加在视口右侧，
**不是视口的子节点**：

| 框架 | scrollbar 定位 |
|------|----------------|
| Compose | `Box { LazyColumn(...) ; Scrollbar(align = Alignment.TopEnd) }` —— Box 兄弟 |
| Flutter | `Scrollbar(child: ListView())` —— Stack 兄弟 |
| iOS UIKit | `UIScrollView` + 兄弟 `UIScrollView.indicator`（系统绘制层） |
| Web CSS | `overflow: scroll` 的伪元素 / `::-webkit-scrollbar`（不占布局流） |

本项目的 `SceneScrollbar` 也是「viewport 兄弟」结构（container(ROW) 内 viewport 在左、scrollbar column 在右），
与主流一致。**无需引入「overlayChild / scrollbar 作为 viewport 子节点」的引擎改动**——那是过度工程。

### 引擎改动的过度工程性

「让 scrollbar 作为 viewport 子节点叠加」需要：

- 布局引擎支持「子节点不参与父布局流但参与绘制」的 overlay 语义
- paint 引擎的 CLIP 窗口需排除 overlay 子节点
- hit-test 需特殊处理 overlay 子节点的命中优先级

这三项都是引擎核心改动，收益仅为「省一个兄弟 container 节点」，成本远超收益。
**结论：不动引擎，scrollbar 维持 viewport 兄弟结构**。

## P 方案：attach 一行门面

新增 `SceneScrollContainer.attach` 一行门面，对齐 Compose `Box + align` 模式——
「一行建带可视滚动条的滚动容器并挂到 parent」。

## API 三层分工

```
attach（一行门面，调用方零 Props 知识）
   ↑ 调
create（中层，返回 Result 含 container/viewport/content/scrollSignal）
   ↑ 调
SceneScrollbar（自管 8 参控件，Props 含 viewport/scrollSignal/colors/widths）
```

| 层 | 职责 | 调用方门槛 |
|----|------|------------|
| `attach` | 一行建带 bar 容器 + 挂 parent + 设 flexGrow + 装填 content | 零（只传 runtime/parent/layoutDoneSignal/contentBuilder） |
| `create` | 中层工厂，返回 Result，调用方自管挂载与布局 | 中（需懂 Props 8 参 + 手挂 + 手 flexGrow） |
| `SceneScrollbar` | 自管控件，8 参 Props 全暴露 | 高（需懂 viewport/scrollSignal/colors/widths 全参） |

### attach 三个重载

1. **`attach(rt, parent, contentChangedSignal, contentBuilder)`**：默认带 bar，零样式（padding/gap/bg/radius 全 0）
2. **`attach(rt, parent, contentChangedSignal, padding, gap, bg, radius, contentBuilder)`**：默认带 bar + 暴露 viewport 四个外观参
3. **`attachNoBar(rt, parent, contentBuilder)`**：裸滚动，无可视 scrollbar（等价裸 `SceneScrolls.attach` 但走工厂结构）

三者统一行为：container 自动 `setFlexGrow(1)`，使其在 COLUMN 父中撑满剩余高（viewport 高度确定的必要条件）。

## 关键约束：contentChangedSignal 必须外传 host.layoutDoneSignal

**物理原因**（见 [DECISION-20260630-scrollbar-layout-done-signal](DECISION-20260630-scrollbar-layout-done-signal.md)）：

- `SceneScrollbar` 的 LAYOUT/COMPOSITE/PAINT bind 读 viewport 的 `LayoutBox` 派生 thumb 几何
- 但 bind 在 effect flush 阶段执行，此时 LayoutBox 可能仍是上一帧旧值
- scrollbar bind 订阅 `contentChangedSignal`，host 在 layout 后 `layoutDoneSignal.set(epoch)`，
  同帧 flush 内 bind 重跑读最新 LayoutBox——零滞后

**若 contentChangedSignal 传错（如传 null 或无关 signal）**：
- scrollbar bind 不在 layout 后重跑
- thumb 几何停在首帧兜底值（thumb 高=0、translateY=0，视觉上 scrollbar 不显示/不跟随滚动）
- 这正是 attach 门面把 `contentChangedSignal` 列为必参、且 demo 显式传 `layoutDoneSignal()` 的原因

attach 门面通过 `defaultScrollbarSpec` 私有方法复用 `SceneScrollbar` 的 4 个 `DEFAULT_*` 常量
（颜色/宽度/最小 thumb 高），调用方无需了解这些细节，**只需记得传 layoutDoneSignal**。

## 不动的文件（边界声明）

本次改动严格收敛在「新增门面 + 新增 demo」，**不改动任何既有控件/引擎逻辑**：

- `SceneScrollbar.java`：**不动**（4 个 `DEFAULT_*` 常量本就是 `public static final`，attach 直接复用）
- `SceneScrolls.java`：**不动**（attach 仍走 `SceneScrolls.attach` 附加滚动能力）
- `SceneScrollContainer.create`：**不动**（attach 是 create 之上的薄门面）
- `ConfigScreen` / 其他既有 demo：**不动**

## 受影响文件

- `src/main/java/club/heiqi/uilib/ui/scene/control/SceneScrollContainer.java`：
  新增 `attach` × 2 重载 + `attachNoBar` + `defaultScrollbarSpec` 私有方法 + `java.util.function.Consumer` import
- `src/main/java/club/heiqi/uilib/internal/devtools/pages/SceneScrollContainerHostWidget.java`：新建 demo widget
- `src/main/java/club/heiqi/uilib/internal/devtools/pages/SceneScrollContainerDemoScreen.java`：新建 demo screen 壳
- `src/main/java/club/heiqi/uilib/internal/devtools/pages/SceneTestHubHostWidget.java`：
  新增 `DESTINATION_SCROLL_CONTAINER` 常量 + 按钮 + `isScrollContainerDestination` 判断
- `src/main/java/club/heiqi/uilib/internal/devtools/pages/SceneTestHubScreen.java`：
  `createTargetScreen` 新增 scrollContainer 分支
- `src/test/java/club/heiqi/uilib/ui/scene/control/SceneScrollContainerTest.java`：
  新增 `attachShouldCreateContainerWithViewportAndScrollbar` + `attachNoBarShouldCreateContainerWithoutScrollbar`

## 后续可选优化

- **优化 1**：若后续出现「自定义 thumb 颜色/宽度」的 demo 需求，可再加一个 `attach(rt, parent, contentChangedSignal, ScrollbarSpec, contentBuilder)` 重载暴露完整 ScrollbarSpec。当前无此需求，避免过度设计。
- **优化 2**：`attachNoBar` 当前不接收样式参（padding/gap/bg/radius 全 0）。若裸滚动场景需要样式，调用方可退回 `create` 中层工厂。保持 attachNoBar 极简。
