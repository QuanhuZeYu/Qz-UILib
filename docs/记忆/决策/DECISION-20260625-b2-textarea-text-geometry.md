# DECISION-20260625 B2 TextArea 文本几何 O(N²) 消除方案裁决

## 状态

**已完成**（commit `ae263575` + `7b7b1722`，合回 4.0）。SceneTextAreaTest 31 用例全绿，
reviewer 审核通过。真机帧率收益待用户验收。

## 背景

SceneTextAreaPrimitive 的行定位方法（lineStartIndex/lineEndIndex/caretRow/isCaretInRow/
rowPrefixText/rowSuffixText/moveCaretVertical）每次调用都内部 `splitLines(value)`（全量
split O(L)）+ 线性累加，N 行 × N 次调用 = O(N²)。点击路径的 `buildPrefixWidths` 逐码点
substring 整前缀再测量，标准 O(N²) 且无缓存。

## 方案选型

oracle 裁决选 **方案 B(R1)：scene 内自建轻量缓存**。

### 否决方案 A：整包复用旧栈 TextLayoutEngine

- scene 核心严禁认识 `ui.text.*`（I6/I10 接缝），复用要 scene 包 import `ui.text.layout`
- TextArea 当前不做软换行，VisualLineLayout 的软换行/视觉行能力全部用不上（YAGNI）
- 需引入 LogicalTextLine/VisualLineLine 模型，scene 栈无此层

### 否决方案 C：混合复用单行 PrefixWidthCache

- 单行版 `PrefixWidthCache` 只缓存"整次产出结果"，**miss 那一次仍是 O(N²)**
- 它消除的是"重复构建"，不是"单次构建复杂度"
- 行定位问题完全未解决

## 关键陷阱：像素一致性（最重要的教训）

**scene `measureWidth` 含 `ceil`+`round` 双取整，"逐码点 UI 宽相加"不等于"整前缀测量"。**

度量链路：
```
rawWidth = ceil(Σ getSegmentWidth)          // TextLayoutService 整串一次 ceil
return Math.round(rawWidth * fontSizePx / lineHeight)   // UI 层再缩放 round
```

若用"逐码点宽相加"建前缀和，`Σ round(ceil(advᵢ)·s)` 与 `round(ceil(Σadvᵢ)·s)` 因每码点
两次取整累积漂移，点击定位/caret x 必漂移。

**更危险的是测试假绿**：`FixedTextMeasurer.measureWidth` = `text.length() * charWidth`，
严格线性、零取整，所以"逐码点相加"在测试里恰好等于整前缀测量，**测试全绿但真机漂移**。

### 结论

`buildPrefixWidths` 函数体**一行都不许改**——保持逐前缀 `measureTextWidth(整前缀)`，
只加跨帧缓存跳过重复构建。像素一致由"测量方式未改"在数学上保证。

真 O(N) 的 raw 前缀累加端口（R2）本期不做，留待真机出现"单行超长点击卡顿"瓶颈时启动，
且必须 oracle 重新评估（触碰 SceneTextMeasurer 核心契约 + I6/I10 接缝）。

## 实施设计

### Step1：LineStructureCache（行结构前缀和）

- 实例级（create() 闭包内 final 持有，非静态——多 TextArea 实例会跨实例串味）
- 字段：cachedValue / int[] lineStartCp / int[] lineLenCp / String[] lines
- 失效键：`value.equals(cachedValue)`（纯字符切分，不涉测量，不含 epoch）
- 7 个行定位方法改查表，O(N²)→O(N)
- 边界语义：split("\n",-1) 保尾空串；caretRow 用 `caret <= end` 归当前行；二分 + 末尾哨兵

### Step2：ClickPrefixWidthCache（点击前缀宽数组）

- 实例级，与 LineStructureCache 同级
- 失效键三元组：行文本 + fontSize + **textMeasureEpoch()**（涉测量，必须含 epoch）
- 只缓存最近点击行单行一份（点击是离散事件）
- buildPrefixWidths 函数体禁改，调用点加缓存层

## 受控不变量遵守

缓存 `cachedValue` 只是"上次构建的输入快照引用"，用途仅是 equals 比对判失效，不是"控件
持有 value 真值副本"（类比 layout 缓存持有上次约束做比对）。与单行版 `PrefixWidthCache.display`
同构，是既定合规范式。缓存对象不得暴露 setter、不得被 onChange 之外的路径写。

## 风险清单

| # | 风险 | 等级 | 处置 |
|---|---|---|---|
| R-1 | 逐码点相加替代整前缀测量 | 高 | 禁止，buildPrefixWidths 函数体禁改 |
| R-2 | 静态字段存缓存致跨实例串味 | 高 | 实例级，create() 闭包持有 |
| R-3 | 二分 caretRow 边界错 | 中 | 复刻 caret ≤ end，空行/尾空行专项测试 |
| R-4 | 尾空行/连续 \n 丢失 | 中 | 与 split("\n",-1) 对齐，专项测试 |
| R-6 | 缓存②漏 epoch | 中 | 失效键含 textMeasureEpoch()（缓存①不需要）|
| R-7 | 缓存①命中路径未零 rebuild | 中 | equals 命中绝不 rebuild |

## 参考

- oracle 裁决全文：会话记录（task ses_100fb2475ffe...）
- reviewer 审核报告：Step1 通过 / Step2 有条件通过→补测试后放行
- 关联：DECISION-20260615-shared-text-layout-engine（旧栈 TextLayoutEngine 设计）
