# ERROR-20260615 flex 匿名文本项布局期自污染 layoutVersion 致配置页持续重排

## 错误现象

`ModernConfigTemplateScreen` 配置页稳态每帧约 340ms（2.5~2.7 FPS）且从不下降；同样基于 `HtmlLikeDocumentWidget` 的 `/qzuilib test` 单样例页稳态仅 5~6ms。客户端 `UI 运行统计` 探针显示卡顿 100% 落在 `HtmlLikeDocumentWidget` self time。

## 触发场景

- 任意 `display:flex` 容器**直接包含裸文本子节点**（`appendText` 直接挂在 flex 容器上，而非包在子元素里）
- `ModernConfigTemplateScreen` 每个字段卡片的 label/description 都是 flex column 容器上的裸文本，约 20 个 flex 容器，症状最重
- 与具体页面无关，属布局引擎通用缺陷，任何含裸文本的 flex 容器都会触发，字段越多越明显

## 根本原因

布局是只读过程，却产生了写副作用，破坏了「布局不应改变文档失效版本」的不变量，形成死循环：

1. `FlexLayoutHelper.flushAnonymousTextItem` 在布局过程中为裸文本创建临时匿名元素 `qz-anonymous-flex-item`，调用 `anonymousElement.setAttribute("data-hit-test-hidden", "true")`
2. `ElementNode.setAttribute` 在值变化时调 `markSubtreeMutated()` → `UiDocument.recordLayoutMutation()` → `layoutVersion++`
3. 该匿名元素纯属布局期临时对象，**从不挂入文档树**（无任何 `append`），其 `layoutVersion++` 是错误副作用
4. 下一帧 `HtmlLikeDocumentWidget.resolveLayoutBox` 发现 `layoutVersion` 变化 → 静态布局缓存失效 → 整文档重排 → 重排又创建匿名元素 setAttribute → 永远无法稳定

每个含裸文本的 flex 容器每帧各 bump 一次，配置页 ~20 个容器 → 单帧 `layoutVersion` 被 bump 20 次，每次触发完整静态布局重排；布局每帧失效又导致 paint command 缓存全冷、每帧重建并全量重算 computed style 后回放。

## 修复方案

- `ElementNode` 新增框架内部静默写属性入口 `__putGeneratedAttribute(name, value)`：直接 `attributes.put(...)`，不触发 `markSubtreeMutated`（与既有 `__appendGeneratedChild`、`UiStyleDeclaration.__copyFromSilently` 同属"运行时生成对象静默写入"约定）
- `FlexLayoutHelper.flushAnonymousTextItem` 改用 `__putGeneratedAttribute`，匿名 flex 文本项不再 bump `layoutVersion`
- 修复后实测：稳态帧时间 340ms → 18~30ms（FPS 2.7 → 34~46，约 12 倍），布局阶段 `relayout` 完全消除、稳态 `resolve(cached)` 命中

## 预防措施

- **布局/样式/命中等只读流程绝不能改文档失效版本**。布局期创建的任何临时对象（匿名盒、伪元素载体、测量用元素）若不挂入文档树，对其设置属性/样式必须走静默内部入口，不能用公开 `setAttribute`/`style().setXxx()` 这类带失效副作用的 API
- 全库布局层（`ui.layout`）当前向 DOM 写入的点只有 `FlexLayoutHelper.flushAnonymousTextItem` 一处；新增任何"布局期创建并配置元素"的逻辑时，必须确认走静默路径
- 回归测试：`FlexLayoutHelperBoundaryTest`、`DocumentLayoutEngineTest`、`HtmlLikeDocumentWidgetLayoutCacheTest`、`HtmlLikeDocumentWidgetInlineLayoutCacheTest` 已覆盖匿名文本项布局正确性

---

## 附：UI 卡顿诊断可复用路线

后续遇到 UI 页面卡顿/掉帧，按此路线逐层收敛，避免盲猜：

### 第 0 步：拿到探针读数

客户端 `Config.useDebug=true` 时，`UiPerformanceMonitor` 每秒输出一行 `UI 运行统计[页面名]`。关键字段：

- `frame` / `render` / `present`：整帧、渲染、贴屏耗时。`render≈frame` 说明瓶颈在渲染而非输入/贴屏
- `slowWidgetSelf` / `slowWidgetTotal`：最慢 widget 的 self / total 耗时，先定位是哪个 widget
- `phases`：阶段细分（**默认为 `<none>`，因为生产代码没有常驻 `recordPhase` 调用**，需要临时埋点）
- 对照一个"正常页面"的同字段读数做基线（本次用 `/qzuilib test` 单样例页 5~6ms 作基线，暴露出配置页 340ms 异常）

日志位置：`run/client/logs/fml-client-latest.log`（`latest.log` 通常只有宿主侧概要，探针在 FML 主日志）。

### 第 1 步：用临时 `recordPhase` 区分"重建 vs 回放"

`HtmlLikeDocumentWidget.drawSelf` 是文档页渲染主入口，分两段：`resolvePaintCommands()`（布局+命令重建）与 `DocumentPaintRenderer.render(...)`（命令回放）。临时在 drawSelf 里给两段分别 `UiPerformanceMonitor.getInstance().recordPhase(标签, 耗时)`，标签里编码缓存是否命中（对比 `paintCacheGeneration` / `staticLayoutGeneration` / `runtimeLayoutGeneration` 前后值）、命令数量。一帧即可区分瓶颈在重建还是回放。

### 第 2 步：若每帧重建布局，定位失效的缓存键

`resolveLayoutBox` 命中条件是 `layoutVersion` / `textMeasureEpoch` / `width` / `height` 四者不变。在未命中分支临时 `recordPhase` 打出是哪个键变了：
- `layoutVer` 变 → 有人在改 DOM/样式（最常见，见本错误）
- `epoch` 变 → 字体测量纪元每帧涨（查 `FontService.textMeasureEpoch` 递增点）
- `w`/`h` 变 → 视口/容器尺寸每帧抖动

### 第 3 步：若是 layoutVersion 每帧变，抓 mutation 调用栈

在 `DocumentNode.markMutated` / `markSubtreeMutated` 临时加**限流（每秒一次）堆栈采样**，打印 `Thread.currentThread().getStackTrace()`。调用栈会直接指向是哪个控件、哪个方法在渲染期间触发布局突变。本次一次就锁定 `FlexLayoutHelper.flushAnonymousTextItem → setAttribute`。

### 第 4 步：回退所有临时探针，只保留修复

诊断探针全部带 `// [临时诊断探针] ... 验证后回退` 注释，定位后用 `git diff` 逐一回退，确保提交只含真实修复。

### 已知第二层瓶颈（已修复）

本次修复后稳态仍有 `replay(cmd~724) 约 17~27ms`，即 paint 命令回放本身偏慢。当时嫌疑：`DocumentPaintRenderer.render` 每帧对每个非文本命令调 `UiStyleResolver` 全量重算 computed style。

**第二层修复方案（构建期固化样式）**：`DocumentPaintCommand` 新增 `elementStyle` 字段 + `withElementStyle()`/`getElementStyle()`；`DocumentPaintEngine` 生成 BORDER/OUTLINE/BOX_SHADOW/TEXT/inline-border 命令时固化 `box.getComputedStyle()`/`ownerStyle`；`DocumentPaintRenderer.resolveCommandStyle()` 优先读固化、缺失回退 `resolveStyle`。

**实测结论（重要）**：固化后探针实测 `styleRecompute=0`、`bakedHit≈命令数`、零回归，**但 replay 耗时几乎没降**。这证明 **样式重算不是 replay 的主成本**——旧的逐帧 `styleMemo`（按元素实例 IdentityHashMap 备忘）已把同帧重复 compute 摊销掉了，固化只是把「同帧首次 compute」也省了，量级很小。replay 的真实成本在命令逐条回放（draw/clip/paint-context 栈操作、文本提交）本身，属独立优化方向（方向B），第二层固化样式作为正确且低风险的清理保留。

### 已知第三层瓶颈：hover/paint 变更触发全文档命令重建（方向A，部分处理）

**现象**：稳态无输入 `replay(cmd~734) ≈16~22ms`、FPS≈45；hover 到 select/checkbox/radio 等控件时 `updateVisualState()` 改 background（合法悬停高亮，`UiStyleDeclaration.updateProperty` 已 `Objects.equals` 去重同值不 bump），单元素局部 paint 变更触发 `paintVersion++` → `resolvePaintLayoutBox` 全树 `refreshComputedStyles()` + `resolvePaintCommands` 全量 `buildPaintCommands` 重建整列命令。

**静态定位的两段 rebuild 成本**：
1. `DocumentLayoutBox.refreshComputedStyles()` 递归全树，每个非匿名节点裸调 `UiStyleResolver.compute(element)`，而 `compute` 内部 `computeParentStyle` 递归到根、无跨节点缓存，复杂度 O(N×D)。
2. `DocumentPaintEngine.buildPaintCommands` 全量重建 ~734 条命令。

**探针实测（22 秒 hover，`recordPhase` 每秒报当秒最慢一帧）后的反直觉归因**：
- `refreshStyles` 段实测仅 **8.42ms 且整轮只触发 1 次**——远非预想的「第一真凶」；`buildCmd` 段 16~46ms 才是 rebuild 大头，但 rebuild 本身偶发（22 秒里约 4 帧最慢帧发生重建，其余 hover 帧多为 `resolve(cached)`，另有 2 帧是 window resize）。
- **决定性发现**：连续 12 秒 `events=0`（完全无输入）的稳态帧，依旧 `replay 16~20ms + resolve(cached)<2ms`，FPS 卡 41~52。**说明 FPS 天花板由每帧 replay（方向B）决定，而非命令重建（方向A）**。增量重建即便做到完美，也只能消除 hover 切换/resize 的偶发尖刺帧，无法提升稳态 FPS。

**第三层修复（方向A，低风险高收益部分）**：把 `DocumentLayoutBox.refreshComputedStyles()` 从裸 `compute(element)` 改为自顶向下传 DOM 父样式 + 按元素实例备忘的 `computeStyleWithCache`，复杂度 O(N×D)→O(N)，与布局期 `DocumentLayoutEngine.LayoutContext.computeStyle` 同语义（DOM 父级级联、无激活伪类），匿名 flex item 分支保持沿用 `computedStyle`。该改动是纯性能优化、输出逐元素等价，由 `HtmlLikeDocumentWidgetAnimationRuntimeTest.shouldRefreshPaintOnlyStyleWithoutRecomputingLayout` 直接覆盖。

**未做的高风险增量重建（方向A治本，经数据驱动决策暂缓）**：曾评估两条增量重建切入点——(2a) paint-only 变更只 refresh 脏子树（需改 `markPaintMutated` 失效链路带脏元素来源）；(2b) 命令列表按 stacking/子树分段缓存、脏区间 splice（须严谨处理 `CLIP_START/END`、`PAINT_CONTEXT_START/END`、`TRANSFORM_START/END` 成对栈边界，分段错位会导致 clip/opacity 栈不平衡）。因实测表明稳态瓶颈是 replay（方向B）而非重建（方向A），增量重建只能消偶发尖刺，投入产出比低，故本轮不实施，留待与方向B（replay 提速）一并评估。

### 第三层诊断补充的复用经验

- **探针采样口径**：`UiPerformanceMonitor.recordPhase` 每秒只累加并报「当秒最慢一帧」的 phase，所以读数反映的是「哪些秒的最慢帧触发了重建」，不是逐帧频率。判断「稳态 vs 偶发」要把 `events=0` 帧单独拎出来看。
- **先拆段再投入**：rebuild 是两段之和时，务必先用 `recordPhase` 给每段独立计时，拿实测占比再决定投入比例，别凭静态分析就断定哪段是真凶（本轮静态判断的「第一真凶 refreshStyles」实测只占 8ms 且偶发）。
- **区分 FPS 天花板成本 vs 偶发尖刺成本**：稳态（无输入）帧的耗时决定 FPS 上限；只在输入/状态切换帧出现的成本只是尖刺。优化前先确认目标成本属于哪一类。
