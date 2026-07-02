# 决策：文本控件共享测量基础设施重构（TextLayoutEngine）

## 背景

ModernConfig 大视图稳态 render ~320ms/帧、fps ~2.7。已实测锁定根因（不再复验）：

- `DocumentPaintRenderer` 命令分类计时显示 CUSTOM 占 replay ~98%。
- 细分：`DocumentTextAreaControl` 的 selection/caret 两层 custom renderer ~47ms/条 × 4 条；
  `DocumentCodeEditorControl` 的 gutter/selection/caret 三层 ~38ms/条 × 3 条；
  `DocumentTextInputControl` 光标 ~0.01ms/条（仅 focused，可忽略）。

根因链：

- `DocumentTextAreaControl.VisualLineMetrics.create()` 对每个码点调用
  `context.measureTextWidth(text.substring(0, currentOffset), UILIB_RAW)`，每行 O(N²)。
- `VisualLineMetrics.resolveVisualLineEnd()` 软换行探测也是逐码点递增前缀测量。
- selection 层与 caret 层各自 `updateRenderedLineMetrics → measureVisualLines`，**全文每帧测两遍**。
- 无选区、无焦点也照测（caret 层即使光标不可见仍重算整页 metrics）。
- `DocumentCodeEditorControl.renderSelection/renderCaret/renderGutter` 同构：每帧用
  `measureTextWidth(line.text.substring(0, localStart/localEnd/localOffset))` 逐前缀测量，但**未做任何缓存**。
- `VisualLineMetrics` 是 `DocumentTextAreaControl` 私有内部类，CodeEditor 没有复用，各写一套。

## 现有底层能力盘点（可承载共享缓存）

- `UiRenderContext.measureTextWidth(text, mode)` = `Math.round(getStringWidth(text, mode) * 2.0F)`，
  其中 `2.0F` 是 `UI_TEXT_SCALE`。
- `getStringWidth` 底层落到 `TextLayoutService.getStringWidth`：逐码点累加 raw advance，最后 `(int) Math.ceil(sum)`。
- `TextLayoutService` 已有**逐码点宽度缓存**（`GlyphRuntimeTables.widthArray`，按 codepoint 直索引），
  但**没有前缀宽度缓存**：对 `substring(0, k)` 的反复调用每次都从头累加 → O(N²) 的真正来源。
- `TextMeasureService` / `DefaultTextMeasureService` 是布局期文本测量抽象，但走的是另一条路（widget 级换行/裁剪），
  与控件内 caret/selection 测量（走 `UiRenderContext`）不是同一层，不要混淆。
- `FontService.getTextMeasureEpoch()`：字体注册 / 匹配缓存 / 文本布局缓存任一变化即 `textMeasureEpoch++`，
  是天然的缓存失效信号，可作为缓存键的一部分。

## 数值一致性约束（决定可行性）

要把逐前缀 `measureTextWidth(substring(0, k))` 改为一次 O(N) 遍历得到 `prefixWidth[k]`，
且**结果与逐次调用完全相同**（否则现存 caret 像素断言会破），必须复刻链路：

```
prefixWidth[k] = round( ceil( rawSum(0..k) ) * UI_TEXT_SCALE )
```

其中 `rawSum(0..k)` 是从行首累加到第 k 个码点边界的 raw advance 之和。
现状 `getStringWidth(substring)` 也是对该前缀做同样的累加 + ceil，因此逐边界复刻
`ceil → ×scale → round` 即可数值等价。测量服务必须暴露"按码点边界返回前缀宽度向量"的能力，
而不是只返回整串宽度，才能保证等价。

## 测试与影响面约束（来自只读调研）

- 像素级回归保护**只**在 `DocumentTextAreaControlTest`，且只断言 caret（宽 2 / 高 18 / 色 `0xFFFFFFFF`），
  期望 left = `betaText.x + renderContext.measureTextWidth("Beta", UILIB_RAW)`。
  → 只要新前缀宽度对每个边界等于 `measureTextWidth(prefix)`，全部断言自动通过。
- `ControlTestRenderContext.measureTextWidth = text.length() * 12`（线性），增量累加天然一致。
- 软换行测试只断言视觉行文本片段存在性（"abcd"/"efgh"/"i"），不验坐标。
- `DocumentCodeEditorControlTest` **不渲染**，CodeEditor 的 selection/caret/软换行无像素回归保护 → 需补测。
- `VisualLineMetrics` 无任何反射依赖 → 可自由替换/删除。
- `TextLayoutServiceTextContentModeTest` 锁定 `UILIB_RAW` 把 `§` 当字面量、`MINECRAFT_FORMATTED` 解析颜色码，
  共享层默认按 `UILIB_RAW`（控件现状），不得回归改这条契约。

## 候选方案

### 方案 A：只给两个控件各自加前缀缓存
- 优点：改动局部、风险低。
- 缺点：仍是两套实现，重复未消除，违背"建立可复用基础设施"目标。

### 方案 B：抽取共享 TextLayoutEngine + VisualLineLayout，两控件共用（选定）
- 优点：消除重复；O(N) 增量；按内容+宽度+epoch 缓存稳态零测量；测量绘制解耦，两层共享一次结果。
- 缺点：需要新建抽象并迁移两个控件，改动面较大；需补 CodeEditor 渲染测试。

### 方案 C：在 UiRenderContext 内做前缀缓存
- 优点：所有调用点透明受益。
- 缺点：缓存归属错位（context 每帧重建/无状态语义），且无法表达"逻辑行→视觉行"的换行结构，职责不清。

## 最终选择

**方案 B**。在 `club.heiqi.uilib.ui.text.layout`（新包）下建立：

1. `TextMeasureFunction`（函数式接口）：`int widthOf(String text)`，把"如何测一段文本"抽象出来，
   由控件用 `context::measureTextWidth`（绑定 `UILIB_RAW`）适配，便于单测注入线性替身。
   同时提供 `int[] prefixWidths(String line)`：一次遍历返回 `[0, w(cp1), w(cp1cp2), ...]`，
   长度 = 码点数 + 1，元素 i = `measureTextWidth(前 i 个码点)`。默认实现可逐码点累加并复用每段增量。

2. `VisualLineLayout`（值对象，替代 `VisualLineMetrics`）：持有
   `logicalLineIndex / visualTop / visualStartIndex / visualEndIndex / text / charOffsets[] / boundaryXs[]`，
   并提供 `resolveBoundaryX / resolveClosestCaretIndex / containsCaretIndex`（迁移原有语义，保持不变）。

3. `TextLayoutEngine`（带缓存的协调者）：输入 = 逻辑行列表 + 可用宽度 + epoch + 测量函数；
   输出 = `List<VisualLineLayout>`。内部按 `(内容指纹 + 可用宽度 + epoch)` 缓存：
   - 三者全不变 → 直接返回上次结果（稳态零测量）。
   - 变化 → 重新布局，软换行与前缀宽度都用 O(N) 增量（共用 `prefixWidths` 的单次遍历）。
   - caret 层与 selection 层向同一个 engine 取结果，引擎在同一帧内对相同输入只算一次。

4. 软换行 O(N)：`resolveVisualLineEnd` 改为在前缀宽度向量上二分 / 单调推进，
   不再每个候选 end 都重新 `measureTextWidth(substring)`。

5. 控件接入：
   - `DocumentTextAreaControl` 删除私有 `VisualLineMetrics`，改持有 `TextLayoutEngine` + `List<VisualLineLayout>`；
     `updateRenderedLineMetrics` 改为"喂输入给 engine、engine 自行决定是否重算"。
   - `DocumentCodeEditorControl` 用同一 engine 取每行前缀宽度，
     `renderSelection/renderCaret` 不再 `measureTextWidth(substring)`，改查 `boundaryXs`。
     CodeEditor 当前不软换行（白空间不换行），engine 以"每逻辑行一条视觉行"模式工作即可。
   - `DocumentTextInputControl` 单行无选区高亮，仍可只测整段；但整段宽度也经由共享 `TextMeasureFunction`，
     统一入口、消除散落的 `measureTextWidth` 直调语义差异。

## 缓存失效与正确性

- 缓存键三元组：内容指纹（逻辑行文本的拼接 hash 或版本计数）、可用宽度（视口内容宽，影响软换行）、字体 epoch。
- 编辑（插入/删除/换行）→ 内容指纹变 → 重算。
- 视口宽度变化（窗口缩放、布局变化）→ 宽度变 → 重算软换行。
- 字体重载 → epoch 变 → 全部重算。
- caret 闪烁、滚动、选区移动**不**改这三者 → 复用，稳态零测量（仅画矩形）。

## 验收口径

- 稳态（无编辑、无字体重载）custom renderer 单帧 < 2ms，整屏 render 个位数 ms。
- 交互（输入、选区、缩放）时增量重算结果正确：caret 像素、选区矩形、软换行、点击命中与旧实现一致。
- TextArea / CodeEditor / TextInput 共用同一套 `TextLayoutEngine` / `TextMeasureFunction`。

## 流程约束（AGENTS.md）

- 从 `4.0` 建 `refactor/` 分支开发；提交标题 `[Refactor]: 中文标题`；
  完成且测试通过后 `git merge --no-ff` 合回 `4.0`；提交身份 `QuanhuZeYu`。
- 单测覆盖：软换行 / CJK / 空行 / 选区 / 光标 / 点击命中；CodeEditor 补像素级渲染测试。
- 本机 `compileJava` + 定向 `test` 通过；帧率实测交用户在 `runClient21` 内确认。
- 完成后回退临时诊断埋点：commit `5b8e5b1f`（命令分类计时）、`5108a87c`（CUSTOM 细分诊断）。
