# Parallel Perf 真机性能测试页设计方案

> 产出类型：设计方案文档（供 @fixer 平移实现，供主 Agent 拍板）
> 适用阶段：deepwork 阶段 2 第三批 —— 真机校准 fork 阈值 + 决定 `PARALLEL_ENABLED` 默认值
> 侦察基线：已读 `SceneFboPerfHostWidget` / `AbstractSceneHostWidget` / `SceneParallelExecutor` / `SceneTestHubHostWidget` / `SceneTestHubScreen` / `SceneFboPerfDemoScreen` / `SceneSlider` / `ScenePaintEngine` / `SceneLayoutEngine`，结论均标注来源行号。

---

## 0. 侦察结论与对背景的修正（@fixer 与主 Agent 必读）

实测代码后，有 4 处与任务背景描述不一致，**以代码为准**：

### 0.1 阈值是 4 个，不是 2 个（关键）
背景说"forkThreshold + wholeTreeThreshold"两个阈值。实际 `SceneParallelExecutor` 已落地 **4 个独立 volatile 阈值 + 对应 getter/setter**（来源 `SceneParallelExecutor.java:116-202`）：

| 阈值 | 默认 | setter | 作用 |
|---|---|---|---|
| layout 整树门槛 | 256 | `setLayoutWholeTreeThreshold(int)` | 整树节点数 ≥ 此值才走 layout 并行路径 |
| layout 子树 fork 门槛 | 64 | `setLayoutForkThreshold(int)` | 单子树节点数 ≥ 此值才 fork 该子树 |
| paint 整树门槛 | 256 | `setPaintWholeTreeThreshold(int)` | 整树节点数 ≥ 此值才走 paint 并行路径 |
| paint 子树 fork 门槛 | 64 | `setPaintForkThreshold(int)` | 单子树节点数 ≥ 此值才 fork 该子树 |

**对设计的影响**：本页阈值 slider 设计为「layout/paint 联动同步设同值」（简化操作），即一个 forkThreshold slider 同时调 layout+paint 的 fork 门槛，一个 wholeTreeThreshold slider 同时调两者整树门槛。理由见 §4.3。**待主 Agent 拍板**：是否需要 layout/paint 分开独立调（4 个 slider）。推荐联动（2 个 slider），因为校准目标是找统一拐点。

### 0.2 阈值已完成运行时接线，slider 调值立即生效（已验证）
`ScenePaintEngine.java:172/333` 与 `SceneLayoutEngine.java:308/439` 已改为运行时读 `SceneParallelExecutor.getXxxThreshold()`，不再是类加载期 final 常量。故 slider onChange 调 setter 后**下一帧 layout/paint 即生效，无需 rebuild 树**（但需 `resetFrameStats()` 清窗口，见 §8）。

### 0.3 并行触发判定逻辑（已验证，决定树结构设计）
- 整树门槛判定：`root.__getCachedSubtreeNodeCount() >= wholeTreeThreshold`（`ScenePaintEngine.java:172`）
- 子树 fork 判定：遍历 children 时 `child.__getCachedSubtreeNodeCount() >= forkThreshold` 才把该 child 子树 fork（`ScenePaintEngine.java:333`）
- **必须两个条件都满足才有真正的并行收益**：整树达 256 进并行路径，但若每个 child 子树 < forkThreshold（64），则没有任何子树被 fork → 退化为单线程跑完，并行路径反而多了判定开销。

**对树结构的致命影响**：FboPerf 的树是「内容区 → N 行(ROW) → 每行 10 个叶子节点」。每行子树只有约 11 节点（1 行 + 10 叶），**永远 < 64**，所以即使整树 2000 节点达到整树门槛，也**没有任何子树会被 fork**，并行测不出收益。本页必须重新设计树结构，让子树节点数可控地越过 forkThreshold。详见 §5。

### 0.4 Hub 常量命名
背景写 `DESTINATION_PARALLEL`，现有代码模式是 `DESTINATION_PERF = "perf"`（`SceneTestHubHostWidget.java:47`）。按现有模式新增 `DESTINATION_PARALLEL = "parallel"`，命名一致即可。见 §10。

### 0.5 控件可用 API（已验证）
- `SceneButton.Props(Signal<String> label, Signal<Boolean> enabled, Runnable onClick)` 构造器（`SceneFboPerfHostWidget.java:265/329`）
- `SceneSlider.Props.builder(value).min().max().step().onChange((v,committing)->{}).build()`（`SceneSlider.java:132-226`）；**无 label 方法**，slider 自身不带文字，需在前面放裸 `SceneNode.setText` 标签节点。
- `frameProbe` 五个 getter 已确认存在并由基类自动 tick（`AbstractSceneHostWidget.java:52/108`，FboPerf 已用 `getAverageFps/getAverageFrameTimeMs/getMaxFrameTimeMs/getSlowFrameCount/getSampledFrameCount`）。
- `root.__getCachedSubtreeNodeCount()`（`SceneNode.java:1467`）读当前树节点数。
- `measurer.measureWidth(...)`：**侦察未直接读到该签名**，FboPerf 也未用预热。标注为推测，见 §6 须 @fixer 核实。

---

## 1. 页面功能规格

### 1.1 定位
真机性能实测页，用于回答三个问题：
1. 小树（<forkThreshold）开并行是否负优化？
2. 大树并行 vs 串行的 fps 加速比？
3. forkThreshold 扫描 {32,64,128,256} 的帧率拐点在哪？

### 1.2 用户操作主流程
```
进页
 → 自动预热（measurer widthCache + ForkJoinPool 建池）   [§6]
 → 等"预热完成"提示
 → 设节点数 slider 到目标规模                              [实验引导 §7]
 → 切并行开关 ON/OFF                                       [§4.1]
 → 观察监测条 fps/帧耗时（节流 200ms 刷新）               [§3]
 → 点"重置采样"清窗口，稳定 2~3 秒后读数
 → 调 forkThreshold slider 扫描                            [§4.3]
 → 记录每组 (节点数, 并行开关, 阈值) → fps 数据
```

### 1.3 三组对照实验（页面内文本引导用户怎么跑）见 §7

---

## 2. 页面布局设计（五段式，照搬 FboPerf 骨架）

完全复用 `SceneFboPerfHostWidget` 的五段式根布局（`SceneFboPerfHostWidget.java:176-255`），色板不变：

```
root (COLUMN, fillParentHeight, padding20, gap12, bg=0xFF08111F)
 ├─ titleBar  (COLUMN, h=44, gap4, hitTestable=false)          标题 + 副标题
 │     ├─ "Scene 布局/绘制并行性能实测"           (0xFFEAF1FF)
 │     └─ "阶段2第三批 · fork阈值校准 + PARALLEL_ENABLED 决策"  (0xFF8AA0C8)
 ├─ monitorBar (ROW, h=可适当加高至 56, padding8, gap16/24, bg=0xFF111C31, radius8)  [§3]
 │     ├─ fpsText      节点（字段保存，节流 setText）
 │     ├─ statsText    节点（字段保存）
 │     └─ parallelText 节点（字段保存，展示并行状态/阈值/节点数）  ← 新增第三段
 ├─ content (COLUMN, fillParentHeight, scrollable, clipChildren, padding10, gap8, bg=0xFF0D1728, radius10)  [§5]
 │     └─ 测试树（深窄子树结构，rebuild 重建）
 └─ actionBar (可能需要两行，见 §4.5)  [§4]
       并行开关 / 预热 / 重置  +  节点数 slider / forkThreshold slider / wholeThreshold slider
```

色板常量（照抄 FboPerf `SceneFboPerfHostWidget.java:36-50`）：
- `ROOT_BG=0xFF08111F` / `TITLE_COLOR=0xFFEAF1FF` / `MUTED_COLOR=0xFF8AA0C8`
- `MONITOR_BG=0xFF111C31` / `CONTENT_BG=0xFF0D1728`
- 测试节点色：复用 `NODE_BG=0xFF2F6FB0`；新增「触发 fork 子树」高亮色建议 `0xFF39B7C9`（青，区分宽扁/深窄结构，非必须）。

监测条因为要展示更多信息，建议高度从 36 提到 **56**，或拆成两行文本（一个 COLUMN 里两个 ROW）。**待主 Agent 拍板**：监测条加高 vs 拆两行。推荐拆两行（信息量大，单行 56 仍可能挤）。

---

## 3. 监测条设计（关键）

### 3.1 展示数据项
| 数据 | 来源 | 节点 |
|---|---|---|
| 平均 fps | `frameProbe.getAverageFps()` | fpsText |
| 平均帧耗时 ms | `frameProbe.getAverageFrameTimeMs()` | statsText |
| 最大帧耗时 ms | `frameProbe.getMaxFrameTimeMs()` | statsText |
| 慢帧数/采样数 | `getSlowFrameCount()` / `getSampledFrameCount()` | statsText |
| 并行开关状态 | `SceneParallelExecutor.isParallelEnabled()` | parallelText |
| 当前节点数 | `root.__getCachedSubtreeNodeCount()` | parallelText |
| 当前 fork 阈值 | `SceneParallelExecutor.getPaintForkThreshold()` | parallelText |
| 当前整树阈值 | `SceneParallelExecutor.getPaintWholeTreeThreshold()` | parallelText |
| 是否真正触发并行 | 派生：`enabled && nodeCount>=wholeThreshold` | parallelText |

### 3.2 文本格式（String.format 模板）
照搬 FboPerf 的 `String.format` 风格（`SceneFboPerfHostWidget.java:161-166`）：

```java
// 第一行：fps
String fps = String.format("fps=%.1f", Double.valueOf(avgFps));

// 第二行：帧统计
String stats = String.format("frame=%.2fms  max=%.2fms  slow=%d/%d",
        Double.valueOf(avgMs),
        Double.valueOf(maxMs),
        Integer.valueOf(slowCount),
        Integer.valueOf(sampledCount));

// 第三行：并行状态（新增）
boolean enabled   = SceneParallelExecutor.isParallelEnabled();
int nodeCount     = root.__getCachedSubtreeNodeCount();
int forkTh        = SceneParallelExecutor.getPaintForkThreshold();
int wholeTh       = SceneParallelExecutor.getPaintWholeTreeThreshold();
boolean wholeHit  = nodeCount >= wholeTh;
String parallel = String.format("并行=%s  节点=%d  整树阈=%d(%s)  fork阈=%d",
        enabled ? "ON" : "OFF",
        Integer.valueOf(nodeCount),
        Integer.valueOf(wholeTh),
        (enabled && wholeHit) ? "已触发" : "未触发",
        Integer.valueOf(forkTh));
```

文本颜色：`fpsText`/`statsText` 用 `TITLE_COLOR`；`parallelText` 用 `TITLE_COLOR`，"已触发"建议整段保持白色（裸 SceneNode 不支持富文本分色）。

### 3.3 节流更新策略（照抄 FboPerf，不用 bind/signal）
完全复用 FboPerf 的节流（`SceneFboPerfHostWidget.java:74-75, 143-169`）：
- 常量 `DISPLAY_INTERVAL_NANOS = 200_000_000L`（200ms）
- 覆写 `render`：先 `super.render(...)` 让基类 tick 帧率，再 `System.nanoTime()` 节流判断，到点才读 probe + `setText`。
- **铁律**：绝不用 `bind`/`signal` 绑监测文本。理由（FboPerf 注释 `:135`）：bind 的 invalidation 刷新会污染测量帧。手动 setText 节流是唯一正确方式。

---

## 4. 操作条设计

### 4.1 并行开关按钮（ON/OFF 切换）
- 文案随状态切换 "并行: OFF" ↔ "并行: ON"
- onClick：翻转 `SceneParallelExecutor.setParallelEnabled(!isParallelEnabled())`，更新按钮 label signal，调 `resetSampling()` 清窗口。
- **不需要 rebuild 树**（开关不改树结构，只改判定路径）。

### 4.2 预热按钮
- 文案 "预热"。onClick：执行 §6 预热逻辑，置预热状态文本。
- 进页已自动预热一次，此按钮供手动重跑（如调大节点数后想再热一次 widthCache）。

### 4.3 节点数 slider（0-2000 step 50）
照抄 FboPerf `mountNodeCountSlider`（`SceneFboPerfHostWidget.java:282-297`）：
- min=0 max=2000 step=50
- onChange：`value.set(v)`；**仅 committing 时** `nodeCountSignal.set(round(v))` + `rebuild()`（拖拽预览不重建，避免拖拽期狂建树卡死）。

### 4.4 forkThreshold slider（16-512 step 16）
- min=16 max=512 step=16（覆盖实验3的 {32,64,128,256}，step16 能精确停在这些点）
- onChange：committing 时同时设 layout+paint 两个 fork 阈值（联动）：
  ```java
  int th = (int) Math.round(v);
  SceneParallelExecutor.setLayoutForkThreshold(th);
  SceneParallelExecutor.setPaintForkThreshold(th);
  resetSampling();   // 阈值改了清窗口，不用 rebuild（§0.2 已接线）
  ```

### 4.5 wholeTreeThreshold slider（64-1024 step 64）
- min=64 max=1024 step=64
- onChange：committing 时同时设 layout+paint 整树阈值：
  ```java
  int th = (int) Math.round(v);
  SceneParallelExecutor.setLayoutWholeTreeThreshold(th);
  SceneParallelExecutor.setPaintWholeTreeThreshold(th);
  resetSampling();
  ```

### 4.6 reset 按钮
照抄 FboPerf `resetSampling`（`SceneFboPerfHostWidget.java:338-341`）：`resetFrameStats()` + `lastDisplayNanos=0L`。

### 4.7 操作条布局
控件总数 7 个（3 按钮 + 3 slider + 1 reset = 实际 4 按钮 3 slider）。单行 ROW（actionBar h=52）会挤。建议 **actionBar 分两行**：
- 行1（按钮排）：并行开关 / 预热 / 重置
- 行2（slider 排）：节点数 / forkThreshold / wholeThreshold（每个 slider 前放一个裸 SceneNode 文字标签，因 SceneSlider 无内置 label）

**待主 Agent 拍板**：actionBar 单行挤排 vs 两行。推荐两行，配合 slider 标签。

---

## 5. 内容区树结构设计（核心，决定能否测出并行收益）

### 5.1 问题（见 §0.3）
FboPerf 的「N 行 × 每行 10 叶」结构，单行子树 ~11 节点永远 < forkThreshold(64)，**子树永不 fork**，测不出并行。

### 5.2 方案：深窄子树（让子树节点数可控越过 forkThreshold）
内容区下挂 **K 个「分支容器」**，每个分支容器是一棵子树，节点数 ≈ `总节点数 / K`。让每个分支子树节点数 > forkThreshold，这些分支才会各自被 fork 到不同 worker。

推荐结构：
```
content (COLUMN, scrollable, clipChildren)
 ├─ branch[0] (COLUMN, 子树含 M 个叶)   ← 子树节点数 = M+1
 ├─ branch[1] (COLUMN, 子树含 M 个叶)
 ├─ ...
 └─ branch[K-1]
其中 K = 分支数（建议固定 8，对齐 cores-1 量级），M = ceil(nodeCount / K)
```
- 每个 branch 内部：M 个叶按 NODES_PER_ROW(10) 再分行排（行节点 hitTestable=false），保持视觉与 FboPerf 一致。
- branch 子树节点数 ≈ M = nodeCount/8。当 nodeCount=1000 时 M≈125 > 64 → 每个 branch 都会 fork，8 路并行。当 nodeCount=50 时 M≈7 < 64 → 不 fork（正好测实验1小树负优化）。

### 5.3 叶节点构造（复用 FboPerf buildTestNode 简化版）
本页**不需要** transform/clip 三模式（那是 FBO 页的事）。叶子只需有背景色矩形即可让 layout/paint 有真实工作量：
```java
private SceneNode buildLeaf() {
    SceneNode node = new SceneNode();
    node.setPreferredWidth(NODE_W);    // 40
    node.setPreferredHeight(NODE_H);   // 40
    node.setBackgroundColor(NODE_BG);  // 0xFF2F6FB0
    node.setHitTestable(false);
    return node;
}
```
> 说明（占位）：本轮叶子是纯色矩形桩，目的是给 layout/paint 制造可量化的节点数负载。下一轮若要测「真实控件混排」的并行收益，把 buildLeaf 换成真实控件树即可，rebuild 逻辑不变。

### 5.4 是否需要宽扁 vs 深窄两种结构？
- **深窄（分支结构，§5.2）**：测子树级 fork（主目标，必须有）。
- **宽扁（FboPerf 原结构）**：content 直挂大量行，每行子树小，整树达门槛但子树不 fork → 用于验证「整树达标但子树不够 fork」时并行无收益甚至负优化。

**待主 Agent 拍板**：是否加「结构切换」按钮（深窄/宽扁）。推荐**先只做深窄结构**（满足三组核心实验），宽扁作为下一轮可选增强。理由：宽扁结构的并行无收益结论可由实验1（小树）间接覆盖，先收敛范围。

### 5.5 rebuild 逻辑（照抄 FboPerf 骨架）
照搬 `SceneFboPerfHostWidget.java:349-378`：
1. `resetSampling()`（清窗口）
2. 复制 `content.__getChildren()` 到新 List，逐个 `content.removeChild(child)`（避免并发修改）
3. 按 nodeCount/K 重建 branch 子树
4. `runtime.flush()`

---

## 6. 预热逻辑设计

> ⚠️ 本节 `measurer.measureWidth` 签名为**推测**，@fixer 实现前须核实 `SceneTextMeasurer` 接口的实际方法名/参数。FboPerf 未做预热，无现成范例可抄。

### 6.1 为什么要预热
1. **measurer widthCache**：首次测量文本会填缓存，冷启动首帧偏慢，污染 fps 窗口。
2. **ForkJoinPool 懒初始化**：`SceneParallelExecutor.getPool()` 双重检查锁懒建池（`SceneParallelExecutor.java:65-85`），首次开并行的那一帧会触发建池 + 线程启动，制造一个超大尖峰帧，污染测量。

### 6.2 预热内容
```java
private void warmUp() {
    // 1. 预热 ForkJoinPool：强制建池，避免首次并行帧建池尖峰
    SceneParallelExecutor.getPool();

    // 2. 预热文本度量缓存（签名待核实）
    //    若 measurer 有 measureWidth(String, float)：循环度量本页所有静态文本
    //    for (String s : 本页所有标签文本) measurer.measureWidth(s, 字号);
    // ⚠ @fixer：核实 SceneTextMeasurer 实际 API 后填充

    warmedUp = true;  // 置状态
}
```
进页构造末尾调一次 `warmUp()`（在 `rebuild()` 之后、首帧之前）。

### 6.3 预热状态展示
- 标题副文本或 monitorBar 增加一句状态："预热中..." → "预热完成"。
- 因为 `getPool()` 同步建池很快（ms 级），构造期调用基本瞬时完成，可直接构造末尾置 "预热完成"。无需异步进度条。
- **待主 Agent 拍板**：是否需要异步预热 + 进度提示。推荐同步预热（够快），状态文本只是给用户确认"已热"。

---

## 7. 三组对照实验引导（页面内文本）

在 content 顶部或 titleBar 副文本区放一段固定引导文字（裸 SceneNode setText，可多行多个节点）。建议放一个可滚动的引导块在 content 最上方（branch 之前），文案：

```
实验1 小树负优化：节点数=50 → 切并行 ON/OFF 各稳定3秒 → 比 fps（预期 ON 持平或略慢）
实验2 大树收益  ：节点数=1000 → 切并行 ON/OFF 各稳定3秒 → 加速比 = fps(ON)/fps(OFF)
实验3 阈值扫描  ：节点数=1000 并行 ON → forkThreshold 扫 32→64→128→256 → 找 fps 拐点
每次改参后点[重置采样]，等慢帧数稳定后再读数
```

文字颜色 `MUTED_COLOR=0xFF8AA0C8`，字号同 FboPerf 副文本。

**待主 Agent 拍板**：引导文字放 content 顶部（占内容区空间）还是折叠进副标题。推荐放 content 顶部单独引导块，醒目，真机操作时看得到。

---

## 8. 交互细节（改参后是否 rebuild / reset 一览）

| 操作 | rebuild 树？ | resetFrameStats？ | 理由 |
|---|---|---|---|
| 切并行开关 | 否 | 是 | 不改树结构，只改判定路径；清窗口避免新旧混合 |
| 调 forkThreshold | 否 | 是 | §0.2 已运行时接线，改阈值下一帧生效；清窗口 |
| 调 wholeThreshold | 否 | 是 | 同上 |
| 调节点数 | 是（committing） | 是（rebuild 内已调） | 改树规模必须重建 |
| 拖拽任意 slider 预览期 | 否 | 否 | committing=false 只更新预览值，不动树不清窗口 |
| 点重置 | 否 | 是 | 主动清窗口重新测 |
| 点预热 | 否 | 否 | 只填缓存/建池，不动测量 |

- **拖拽预览不 rebuild**：slider onChange 的 `committing` 参数区分（`SceneSlider.java:102` 语义）。只有 committing=true（松手/键盘）才 rebuild + reset，拖拽中只 `value.set(v)` 更新滑块视觉。这是 FboPerf 已验证的范式（`SceneFboPerfHostWidget.java:288-294`）。
- **按钮点击反馈**：SceneButton 自带 hover/pressed 态（控件内置），无需额外处理。并行开关的 label 文字翻转即是状态反馈。

---

## 9. API 代码片段（@fixer 直接参考，遵循 FboPerf 模式）

> 来源标注：以下片段结构照抄 `SceneFboPerfHostWidget` 已验证用法，阈值 setter 来自 `SceneParallelExecutor.java` 已验证签名。

### 9.1 并行开关按钮（label 随状态翻转）
```java
/** 并行开关按钮 label（随状态翻转）。 */
private final Signal<String> parallelLabel =
        Signal.create(SceneParallelExecutor.isParallelEnabled() ? "并行: ON" : "并行: OFF");

private void mountParallelToggle(SceneNode parent) {
    SceneButton.Props props = new SceneButton.Props(
            parallelLabel,
            Signal.create(Boolean.TRUE),
            () -> {
                boolean next = !SceneParallelExecutor.isParallelEnabled();
                SceneParallelExecutor.setParallelEnabled(next);
                parallelLabel.set(next ? "并行: ON" : "并行: OFF");
                resetSampling();   // 切开关清窗口，不 rebuild
            });
    SceneNode button = runtime.mount(parent, SceneButton.create(runtime, props)).getRoot();
    button.setPreferredWidth(110);
    button.setPreferredHeight(36);
}
```

### 9.2 forkThreshold slider（联动 layout+paint）
```java
private void mountForkThresholdSlider(SceneNode parent) {
    Signal<Double> value = Signal.create(
            Double.valueOf(SceneParallelExecutor.getPaintForkThreshold()));
    SceneSlider.Props props = SceneSlider.Props.builder(value)
            .min(16)
            .max(512)
            .step(16)
            .onChange((v, committing) -> {
                value.set(Double.valueOf(v));
                if (committing) {
                    int th = (int) Math.round(v);
                    SceneParallelExecutor.setLayoutForkThreshold(th);
                    SceneParallelExecutor.setPaintForkThreshold(th);
                    resetSampling();   // 阈值已运行时接线，无需 rebuild
                }
            })
            .build();
    runtime.mount(parent, SceneSlider.create(runtime, props));
}
```

### 9.3 wholeTreeThreshold slider（联动 layout+paint）
```java
private void mountWholeThresholdSlider(SceneNode parent) {
    Signal<Double> value = Signal.create(
            Double.valueOf(SceneParallelExecutor.getPaintWholeTreeThreshold()));
    SceneSlider.Props props = SceneSlider.Props.builder(value)
            .min(64)
            .max(1024)
            .step(64)
            .onChange((v, committing) -> {
                value.set(Double.valueOf(v));
                if (committing) {
                    int th = (int) Math.round(v);
                    SceneParallelExecutor.setLayoutWholeTreeThreshold(th);
                    SceneParallelExecutor.setPaintWholeTreeThreshold(th);
                    resetSampling();
                }
            })
            .build();
    runtime.mount(parent, SceneSlider.create(runtime, props));
}
```

### 9.4 节点数 slider（committing 才 rebuild，照抄 FboPerf）
```java
private void mountNodeCountSlider(SceneNode parent) {
    Signal<Double> value = Signal.create(Double.valueOf(nodeCountSignal.get()));
    SceneSlider.Props props = SceneSlider.Props.builder(value)
            .min(0)
            .max(2000)
            .step(50)
            .onChange((v, committing) -> {
                value.set(Double.valueOf(v));
                if (committing) {
                    nodeCountSignal.set(Integer.valueOf((int) Math.round(v)));
                    rebuild();   // rebuild 内已含 resetSampling
                }
            })
            .build();
    runtime.mount(parent, SceneSlider.create(runtime, props));
}
```

### 9.5 监测条 render 节流（照抄 FboPerf + 加第三行）
```java
@Override
public void render(int w, int h, UiRenderBackend ctx, int absX, int absY) {
    super.render(w, h, ctx, absX, absY);   // 基类 tick 帧率

    long now = System.nanoTime();
    if (now - lastDisplayNanos < DISPLAY_INTERVAL_NANOS) {
        return;
    }
    lastDisplayNanos = now;

    fpsText.setText(String.format("fps=%.1f",
            Double.valueOf(frameProbe.getAverageFps())));
    statsText.setText(String.format("frame=%.2fms  max=%.2fms  slow=%d/%d",
            Double.valueOf(frameProbe.getAverageFrameTimeMs()),
            Double.valueOf(frameProbe.getMaxFrameTimeMs()),
            Integer.valueOf(frameProbe.getSlowFrameCount()),
            Integer.valueOf(frameProbe.getSampledFrameCount())));

    boolean enabled  = SceneParallelExecutor.isParallelEnabled();
    int nodeCount    = root.__getCachedSubtreeNodeCount();
    int wholeTh      = SceneParallelExecutor.getPaintWholeTreeThreshold();
    int forkTh       = SceneParallelExecutor.getPaintForkThreshold();
    boolean wholeHit = nodeCount >= wholeTh;
    parallelText.setText(String.format("并行=%s  节点=%d  整树阈=%d(%s)  fork阈=%d",
            enabled ? "ON" : "OFF",
            Integer.valueOf(nodeCount),
            Integer.valueOf(wholeTh),
            (enabled && wholeHit) ? "已触发" : "未触发",
            Integer.valueOf(forkTh)));
}
```

### 9.6 预热（getPool 已验证，measureWidth 待核实）
```java
private void warmUp() {
    SceneParallelExecutor.getPool();   // 已验证：强制建池，避首帧建池尖峰
    // TODO @fixer：核实 SceneTextMeasurer.measureWidth 真实签名后补文本预热
    warmedUp = true;
}
```

---

## 10. 接线指引清单（给 @fixer）

### 10.1 新建宿主 Widget
新建 `SceneParallelPerfHostWidget extends AbstractSceneHostWidget`，照搬 `SceneFboPerfHostWidget` 五段式骨架，按本文档 §2~§9 改造：
- 字段：`root` / `content` / `fpsText` / `statsText` / `parallelText` / `nodeCountSignal` / `parallelLabel` / `lastDisplayNanos` / `warmedUp`
- 删掉 FboPerf 的 mode/depth 相关（不需要 transform/clip 三模式）
- 树结构换成 §5.2 深窄分支结构
- 构造末尾：`rebuild()` → `warmUp()` → `runtime.flush()`

### 10.2 新建 Screen 壳
新建 `SceneParallelPerfDemoScreen extends McScreenBridge`，照抄 `SceneFboPerfDemoScreen.java` 全文，替换：
- 构造器 `new SceneFboPerfHostWidget(...)` → `new SceneParallelPerfHostWidget(...)`
- 类名/注释

### 10.3 Hub 三件套接线
1. `SceneTestHubHostWidget.java`：
   - 新增常量 `private static final String DESTINATION_PARALLEL = "parallel";`（仿 `:47`）
   - 构造里加 `mountButton("Parallel Perf", DESTINATION_PARALLEL);`（仿 `:83`，在 FBO Perf 按钮后）
   - 新增 `static boolean isParallelDestination(String d) { return DESTINATION_PARALLEL.equals(d); }`（仿 `:240-242`）
2. `SceneTestHubScreen.java`：
   - `createTargetScreen` 加分支（仿 `:123-125`）：
     ```java
     if (SceneTestHubHostWidget.isParallelDestination(destination)) {
         return new SceneParallelPerfDemoScreen(returnHubScreen);
     }
     ```

### 10.4 占位 → 下一轮接线点
| 本轮占位 | 下一轮换成 | 位置 |
|---|---|---|
| `buildLeaf()` 纯色矩形桩 | 真实控件混排树 | 宿主 Widget `buildLeaf` 方法 |
| 文本预热（TODO 待核实 API） | `measurer` 实际 measureWidth 调用 | §6.2 `warmUp()` |
| 深窄单结构 | 可选加宽扁结构切换按钮 | §5.4 |

---

## 11. 待主 Agent 拍板清单（汇总）

1. **阈值 slider 数量**：2 个联动（layout/paint 同值）vs 4 个独立。推荐 2 个联动。（§0.1, §4.3）
2. **监测条形态**：加高单行 vs 拆两行。推荐拆两行。（§2, §3）
3. **操作条形态**：单行挤排 vs 两行。推荐两行 + slider 标签。（§4.7）
4. **树结构**：只做深窄 vs 深窄+宽扁切换。推荐先只做深窄。（§5.4）
5. **预热**：同步 vs 异步进度。推荐同步。（§6.3）
6. **实验引导文字位置**：content 顶部 vs 折叠副标题。推荐 content 顶部引导块。（§7）

## 12. 须 @fixer 核实的不确定项
1. `SceneTextMeasurer.measureWidth(...)` 真实方法名/参数（§6 标推测，本侦察未读该接口）。
2. `分支数 K=8` 是否合理：建议 @fixer 实现后用 `getPool().getParallelism()` 读实际并行度，让 K 对齐并行度而非硬编码 8（增强项，非阻塞）。
