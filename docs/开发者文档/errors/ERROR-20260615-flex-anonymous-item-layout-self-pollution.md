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

### 第四层瓶颈：稳态 FPS 天花板真凶 = 只读 getDocumentBounds 每次推进动画时间线全树遍历（方向B，已修复保底层）

**承上**：第三层已坐实稳态 FPS 天花板由每帧 `replay` 决定（方向B），且 `replay-custom(x20) ≈14ms` 是绝对大头（`text/geom/stack` 合计仅 2~4ms，GL draw call 仅 316~360 非瓶颈）。custom 按控件类拆分：`DocumentTextAreaControl` 选区+光标两个 custom renderer、`DocumentCodeEditorControl` 行号 renderer 各自每帧调 `getDocumentBounds()` 取 viewport/content/layer 边界。

**两段错位的归因（重要，纠正交接记录原假设）**：
- 原假设：`getDocumentBounds()` → `requestElementBounds` → `DocumentVisualTraversal.resolveVisualScene()` 每次从根重建整棵 `VisualScene` 再走树定位单元素，零缓存，是大头。据此做了**保底层**：`DocumentVisualTraversal.indexBoxLocations(scene)` 单趟 DFS 展开为 `IdentityHashMap<ElementNode, BoxLocation>`，`HtmlLikeDocumentWidget` 按场景签名（rootBox 实例 + scrollState 版本 + 是否运行态动画 + 运行态当前时间）缓存定位索引，使同帧/稳态跨帧定位摊销为 O(1)。
- **实测打脸**：带保底层版本稳态仍 ~20ms/fps46，**零收益**。加外科探针 `BoundsProbe` 把 `requestElementBounds` 拆三段（`interactive` = `resolveInteractiveLayoutBox` / `index` = 场景重建 / `lookup` = O(1) 查表）后铁证：稳态命中率 100%、`index=0.02ms/秒`（保底层缓存完美工作、场景重建已近免费），而 **`interactive` 段占 total 的 99.9%**（~580ms/秒、~0.88ms/call、每帧~13 次=~11~12ms，精确对上那 14ms custom 段）。

**根因本质**：`getDocumentBounds()` 是**只读查询**，但旧实现经 `resolveInteractiveLayoutBox()` 每次都无条件 `animationTimeline.updateFromLayout(全树递归遍历每元素 COLOR/FLOAT 属性 + 新建 HashSet)` + `flushCompletedAnimationEvents()`。而绘制管线 `resolvePaintCommands()` 每帧已推进过一次时间线，且 custom renderer 的 bounds 查询发生在绘制命令**回放期**（paint 之后），本帧时间线早已推进——这十余次重复推进纯属浪费。**只读 getter 触发了副作用。**

**保底层修复（已实施，本轮）**：新增只读路径 `HtmlLikeDocumentWidget.resolveLayoutBoxForBoundsQuery()`，稳态（`!hasLayoutRuntimeValue()`）下只复用版本键控的静态布局盒 + 幂等 `updateScrollStateFromCachedLayoutIfNeeded()`，**不推进时间线、不派发动画完成事件**；存在布局/transform 运行态动画时回退完整 `resolveInteractiveLayoutBox()` 保证实时正确。`resolveCachedBoxLocation` 改走此路径。**实测**：稳态 `frame ~20ms→~13ms`、`fps 46→75`（接近 vsync 上限）、单次 bounds 查询 0.88ms→0.37ms，`HtmlLikeDocumentWidgetAnimationRuntimeTest`(31) + 文本控件测试全过、零回归。

**残留与治本层（方向B 治本，待施工）**：修复后 `interactive` 段仍占 total ~99.8%（~0.37ms/call、每帧 ~4.7ms、占 13ms 帧约 35%），残因是只读路径里 `hasLayoutRuntimeValue()` 每次 bounds 查询仍多次遍历 `animationTimeline.states`（1 次 LAYOUT + 5 次 transform 属性 = 6 次 `states.values()` 全遍历）。**治本层**：命令构建期（`DocumentPaintEngine.appendCustomCommand`，已持有 box）固化 renderer 所需的 viewport(element)/text(contentElement)/layer bounds 传入 CUSTOM 命令，TextArea/CodeEditor renderer 改读固化参数、回放期完全不调 `getDocumentBounds()`，可把这 4.7ms 彻底清零、帧时再压到 ~8ms。注意：`DocumentLayoutBox` 无 `getParent()`，父/兄弟 box 需从遍历调用栈传入，要改 `appendCustomCommand` 签名。

### 第四层诊断补充的复用经验

- **只读 getter 谨防副作用**：`getDocumentBounds()` 名义只读，却经 `resolveInteractiveLayoutBox` 推进了动画时间线。给热路径定位时，务必追到副作用边界——本轮真凶不在「定位算法」（场景遍历）而在「定位前无条件触发的时间线推进」。
- **探针要拆到「方法段」粒度而非「控件类」粒度**：第三层拆到 custom 控件类（TextArea/CodeEditor）只能说明「custom 段贵」，无法区分贵在「场景遍历」还是「时间线推进」。本轮把 `requestElementBounds` 内部拆成 interactive/index/lookup 三段独立计时 + 缓存命中计数，才一击锁定。先怀疑的（场景重建）实测仅 0.02ms，真凶（时间线推进）占 99.9%——与第三层「静态判断的真凶实测只占 8ms」同款打脸，再次印证「先拆段实测再投入」。
- **零收益修复也要留探针证伪**：保底层上线后稳态零变化，正是「加段计时探针」证明了缓存确实命中（`index=0.02ms`）、问题在别处，避免了「以为没生效就乱改缓存键」的歧路。
