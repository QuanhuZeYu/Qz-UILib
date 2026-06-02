# 浏览器语义一致性审查（Phase 2）

## 审查信息

- 审查日期：2026-06-01
- 审查主题：Phase 1 合并后，系统性检查项目中仍存在的与浏览器标准语义不一致的实现
- 审查范围：事件系统、命中测试、布局引擎、样式级联与继承、绘制顺序、DOM API 与控件行为
- 核实方法：对每个子系统深入阅读源码，逐项对比 CSS/HTML/DOM 规范标准行为

---

## 审查结论

Phase 1 已修复了事件三阶段传播、visibility 后代恢复、block margin collapse 基础规则、共享视觉遍历层等核心语义问题。本次审查在此基础上发现 **28 处** 与浏览器标准不一致的实现，其中 **高严重度 9 处、中严重度 13 处、低严重度 6 处**。

高严重度问题集中在：
1. 布局引擎的 min/max 约束应用顺序、负 margin collapse、flex item min-width 默认值
2. DOM 操作的同父节点索引偏移 bug
3. 命中测试中 fixed 定位不创建 stacking context、overflow+border-radius 裁剪缺失
4. disabled 布尔属性语义错误

---

## 一、布局引擎

### 1.1 [高] min-width > max-width 时约束应用顺序错误

**文件**: `DocumentLayoutEngine.java:569-575`

**当前实现**:
```java
int result = Math.max(contentWidth, minW);  // 先应用 min
result = Math.min(result, maxW);            // 再应用 max（可能覆盖 min）
```

**浏览器标准**: CSS 2.1 §10.4 规定当 `min-width > max-width` 时，`min-width` 胜出。正确实现应先限制 max 再确保 min：
```java
int result = Math.min(contentWidth, maxW);
result = Math.max(result, minW);
```

**影响**: 当 min-width 大于 max-width 时，max-width 会错误地覆盖 min-width。同样问题存在于 `applyHeightConstraints`（第 649-661 行）。

---

### 1.2 [高] 负 margin collapse 规则不完整

**文件**: `DocumentLayoutEngine.java:397`

**当前实现**:
```java
marginCollapseAdjustment = Math.min(previousMarginBottom, childMarginTop);
```

**浏览器标准**: margin collapse 需区分三种情况：
- 两个正 margin：取较大值
- 两个负 margin：取绝对值最大的负值
- 一正一负：正值 + 负值（相加）

当前 `Math.min` 对正值场景恰好正确（`adjustedFlowTop - min(a,b)` 等价于保留 `max(a,b)` 间距），但对一正一负场景产生错误结果。

---

### 1.3 [高] Flex item min-width 默认值应为 auto 而非 0

**文件**: `FlexLayoutHelper.java:874`

**当前实现**: row 方向的 flex item `minContentMainSize` 默认为 0（Java int 默认值），只有 column 方向在第 233 行设置了 `resolveColumnFlexItemMinMainSize`。

**浏览器标准**: CSS Flexbox §4.5 规定 flex item 的 `min-width`/`min-height` 初始值为 `auto`，等价于 `min(content size, specified size)`，防止 item 被缩小到内容溢出。

**影响**: row 方向的 flex-shrink 可以将 item 缩小到 0 宽度，导致内容溢出。

---

### 1.4 [高] flex-basis 的 box-sizing 转换条件错误

**文件**: `FlexLayoutHelper.java:414-416` → `DocumentLayoutEngine.java:581-583`

**当前实现**: `resolveBoxSizingContentWidth` 的条件为：
```java
if (computedStyle.getBoxSizing() != UiBoxSizing.BORDER_BOX || isAuto(computedStyle.getWidth())) {
    return resolvedWidth;
}
```

当 flex-basis 非 auto 但 width 为 auto 时，`isAuto(width)` 为 true，导致 border-box 的 flex-basis 不会扣除 border/padding。

**浏览器标准**: flex-basis 的 box-sizing 转换应独立于 width 是否为 auto。

---

### 1.5 [中] 空块自身 margin collapse 缺失

**文件**: `DocumentLayoutEngine.java:295-421`

**当前实现**: 没有对空块（无 border、无 padding、无内容、无确定高度）的 top/bottom margin 自身折叠做处理。

**浏览器标准**: 满足条件的空块，其 margin-top 和 margin-bottom 应折叠为一个 margin。

---

### 1.6 [中] 父子 margin collapse 递归性缺失

**文件**: `DocumentLayoutEngine.java:851-882`（`resolveCollapsibleFirstChildTopMargin`）

**当前实现**: 只查找第一个可见 in-flow 子元素的 margin-top，不递归。

**浏览器标准**: 父子 margin collapse 应递归——如果第一个子元素的第一个子元素也满足折叠条件，其 margin-top 也应参与折叠。

---

### 1.7 [中] Flex-wrap 多行时缺少 align-content 支持

**文件**: `FlexLayoutHelper.java:83-212`

**当前实现**: 多行 flex 布局时各行按自然高度紧密排列，没有 `align-content` 属性处理。

**浏览器标准**: `align-content` 控制多行 flex 容器中各行在交叉轴上的分布方式（stretch/center/space-between 等）。

---

### 1.8 [中] Flex item auto margin 应禁用 align-self/stretch

**文件**: `FlexLayoutHelper.java:141-149, 184-191`

**当前实现**: 先做 stretch 拉伸，然后再处理 auto margin，可能导致 item 被不必要地拉伸后再偏移。

**浏览器标准**: 当 flex item 在交叉轴方向有 auto margin 时，align-self 应被忽略。

---

### 1.9 [中] Absolute 定位元素 auto margin 居中缺失

**文件**: `PositionedLayoutHelper.java:89-106`

**当前实现**: 当 absolute 元素同时设置 left/right/width 且 margin-left/margin-right 为 auto 时，没有平分剩余空间实现居中。

**浏览器标准**: auto margin 应平分剩余空间。

---

### 1.10 [中] Table auto 列宽缺少内容测量

**文件**: `TableLayoutHelper.java:214-232`

**当前实现**: auto 列宽简单均分剩余空间。

**浏览器标准**: auto 列宽应基于单元格内容的 min-content/max-content 宽度按比例分配。

---

### 1.11 [中] Inline-block baseline 对齐缺失

**文件**: `InlineLayoutHelper.java`（inline-block 放置逻辑）

**当前实现**: inline-block 简单放在行顶部，没有参与行内 baseline 对齐。

**浏览器标准**: inline-block 的 baseline 应是其最后一行内容的 baseline。

---

## 二、命中测试与视觉层

### 2.1 [高] position:fixed 无 z-index 时不创建 stacking context

**文件**: `DocumentEffectChain.java:68-69`

**当前实现**:
```java
boolean positionedStackingContext = style.getPosition() == UiPosition.STICKY
        || style.getPosition() != UiPosition.STATIC && style.getZIndex() != null;
```

`position:fixed` 且 `z-index:auto`（null）时不创建 stacking context。

**浏览器标准**: `position:fixed` 总是创建 stacking context（CSS Positioned Layout Module Level 3, §3.1）。

**修复建议**: 条件中增加 `|| style.getPosition() == UiPosition.FIXED`。

---

### 2.2 [高] overflow:hidden + border-radius 时子元素 clip 不考虑圆角

**文件**: `DocumentEffectChain.java:205-211`

**当前实现**: clip 边界始终是矩形，不考虑 border-radius。

**浏览器标准**: 当 overflow 非 visible 且有 border-radius 时，子内容的 clip 区域应是圆角矩形。

**影响**: 圆角容器角落区域的子元素在视觉上被裁剪不可见，但命中测试仍能命中。

---

### 2.3 [中] position:fixed 无条件清空祖先 clip chain

**文件**: `DocumentVisualTraversal.java:133-134`

**当前实现**: fixed 元素清空所有祖先 clip。

**浏览器标准**: 如果 fixed 元素的祖先有 `transform`/`filter`/`perspective`，fixed 的 containing block 变为该祖先，仍受其 overflow clip 约束。

---

### 2.4 [低] position:sticky 无条件创建 stacking context

**文件**: `DocumentEffectChain.java:68`

**当前实现**: sticky 无条件创建 stacking context。

**浏览器标准**: sticky 本身不创建 stacking context（除非有其他触发条件）。

**后续复核（2026-06-02）**: 本条为规范口径误报。现代 CSS Positioned Layout 明确 `position: sticky` 总是创建 stacking context，当前实现保留 sticky 无条件创建 stacking context。不要按本条再发起修复。

---

## 三、事件系统

### 3.1 [中] 焦点事件顺序不正确

**文件**: `DocumentFocusManager.java:175-184`

**当前实现**: blur(A) → focusout(A) → focus(B) → focusin(B)

**浏览器标准**: focusout(A) → focusin(B) → blur(A) → focus(B)

---

### 3.2 [中] focusin handler 在失焦时也被调用

**文件**: `DocumentFocusManager.java:385-396`

**当前实现**: `dispatchFocusChanged` 无论 `focused=true/false` 都通过 `focusInHandler` 分发，用布尔值区分。

**浏览器标准**: `focusin` 只在获得焦点时触发；失焦时应触发独立的 `focusout` 事件。

---

### 3.3 [中] hover 事件（mouseenter/mouseleave 语义）允许被单个 handler "消费"

**文件**: `DocumentMouseEventDispatcher.java:199-200`

**当前实现**: handler 返回 true 时停止向上通知后续祖先。

**浏览器标准**: `mouseenter`/`mouseleave` 不冒泡，每个元素独立接收事件，不存在"消费"概念。

---

### 3.4 [中] wheel 事件无独立 DOM 事件分发

**文件**: `HtmlLikeDocumentWidget.java:629-645`

**当前实现**: 滚轮输入直接被 `scrollState.handleWheel` 消费，页面作者无法通过 DOM 事件拦截或 preventDefault 阻止滚动。

**浏览器标准**: `wheel` 事件遵循 capture → target → bubble 传播，`preventDefault()` 可阻止默认滚动。

---

### 3.5 [低] textInput 事件缺少 capture 阶段

**文件**: `DocumentKeyboardEventDispatcher.java:62-80`

**当前实现**: 只有简单的向上冒泡，没有 capture 阶段。

---

### 3.6 [低] active 状态通知允许被单个 handler "消费"

**文件**: `DocumentMouseEventDispatcher.java:41-42`

**当前实现**: handler 返回 true 阻止更上层祖先收到 active 状态变化。

**浏览器标准**: `:active` 状态同时应用于目标及所有祖先，无法被某一层"消费"。

---

## 四、样式级联与继承

### 4.1 [中] text-shadow 错误标记为继承属性

**文件**: `UiStyleProperty.java:73`

**当前实现**: `TEXT_SHADOW(true, UiStyleChangeImpact.PAINT)` — 标记为继承。

**浏览器标准**: `text-shadow` 不是继承属性，初始值为 `none`。

**后续复核（2026-06-01）**: 本条为规范口径误报。CSS Text Decoration 中 `text-shadow` 是继承属性，当前实现保留 `TEXT_SHADOW(true, ...)`。不要按本条再发起修复。

---

### 4.2 [中] border-collapse 错误标记为非继承属性

**文件**: `UiStyleProperty.java:68`

**当前实现**: `BORDER_COLLAPSE(false, UiStyleChangeImpact.LAYOUT)` — 标记为非继承。

**浏览器标准**: `border-collapse` 是继承属性。

---

### 4.3 [中] font-style 的 changeImpact 应为 LAYOUT 而非 PAINT

**文件**: `UiStyleProperty.java:77`

**当前实现**: `FONT_STYLE(true, UiStyleChangeImpact.PAINT)`

**浏览器标准**: font-style 变化会改变字形宽度，应触发重新布局。

---

## 五、绘制顺序

### 5.1 [低] inset box-shadow 应在背景之上、边框之下绘制

**文件**: `DocumentPaintEngine.java:167-168`

**当前实现**: inset shadow 在边框之后绘制。

**浏览器标准**: inset shadow 在背景之上、边框之下（边框覆盖 inset shadow）。

---

## 六、DOM API 与控件

### 6.1 [高] insertBefore 同父节点移动时索引偏移 bug

**文件**: `DocumentNode.java:204-209`

**当前实现**: 先获取 referenceChild 索引，再从旧父节点移除 child，最后用旧索引插入。

**问题**: 如果 child 的旧父节点就是当前节点且在 referenceChild 之前，移除后 referenceChild 的实际索引减 1，导致插入位置偏后一位。

**示例**: `[A, B, C]` 执行 `insertBefore(A, C)` → 期望 `[B, A, C]`，实际 `[B, C, A]`。

---

### 6.2 [高] replaceChild 同父节点替换时索引偏移 bug

**文件**: `DocumentNode.java:238-244`

**当前实现**: 与 insertBefore 相同的问题。如果 newChild 已是当前节点子节点且在 oldChild 之前，移除后 oldChild 索引变化导致 `set(index, ...)` 操作到错误位置。

---

### 6.3 [高] disabled="false" 被视为非禁用，违反布尔属性语义

**文件**: `ElementSemantics.java:29-30`

**当前实现**:
```java
return value != null && !"false".equals(value.trim().toLowerCase(Locale.ROOT));
```

**浏览器标准**: HTML 布尔属性只要存在就为 true，无论值是什么。`<button disabled="false">` 在浏览器中仍然是禁用的。

---

### 6.4 [中] appendChild/insertBefore 返回值语义不一致

**文件**: `DocumentNode.java:135, 186`

**当前实现**: 返回 `this`（父节点）。

**浏览器标准**: `appendChild` 返回被追加的子节点，`insertBefore` 返回被插入的新节点。

---

### 6.5 [中] querySelector("*") 可能返回内部根节点

**文件**: `DocumentQuerySupport.java:109-113`

**当前实现**: `findFirstMatch` 从 root 节点开始检查，如果 root 自身匹配就返回 root。

**浏览器标准**: `document.querySelector()` 搜索后代元素，不包括 document 节点本身。

---

### 6.6 [低] removeChild 返回 boolean 而非被移除节点

**文件**: `DocumentNode.java:164`

**当前实现**: 返回 `boolean`，非子节点时静默返回 `false`。

**浏览器标准**: 返回被移除的子节点，非子节点时抛出 `NotFoundError`。

---

## 七、优先修复建议

按影响面和修复代价排序：

| 优先级 | 问题 | 修复代价 |
|--------|------|----------|
| P0 | insertBefore/replaceChild 索引偏移 bug（6.1, 6.2） | 低 — 移除后重新获取索引即可 |
| P0 | min/max 约束应用顺序（1.1） | 低 — 交换 Math.max/min 顺序 |
| P0 | position:fixed 不创建 stacking context（2.1） | 低 — 条件增加 FIXED |
| P1 | disabled 布尔属性语义（6.3） | 低 — 移除 `!"false".equals(...)` 判断 |
| P1 | flex item min-width 默认值（1.3） | 中 — 需要在 row 方向测量内容宽度 |
| P1 | flex-basis box-sizing 转换（1.4） | 中 — 需要独立的 box-sizing 判断逻辑 |
| P1 | overflow+border-radius 裁剪（2.2） | 中 — ClipBounds 需扩展为圆角矩形 |
| P1 | 负 margin collapse（1.2） | 中 — 需要区分三种情况 |
| P2 | 焦点事件顺序（3.1, 3.2） | 中 — 需要重构 FocusManager 分发逻辑 |
| P2 | text-shadow/border-collapse 继承标记（4.1, 4.2） | 低 — 修改枚举值 |
| P2 | font-style changeImpact（4.3） | 低 — 修改枚举值 |
| P2 | hover/active 消费语义（3.3, 3.6） | 中 — 需要移除 break 逻辑 |
| P2 | wheel 事件 DOM 分发（3.4） | 中 — 需要新增 wheel 事件类型和分发链路 |
| P3 | 空块/递归 margin collapse（1.5, 1.6） | 高 — 需要较大的布局逻辑重构 |
| P3 | align-content、auto margin 禁用 stretch（1.7, 1.8） | 中 |
| P3 | absolute auto margin 居中（1.9） | 中 |
| P3 | 其他低严重度问题 | 低~中 |

---

## 八、确认正确的实现（无需修改）

以下经源码核实确认与浏览器标准一致：

- 特异性计算（inline > id > class/pseudo > tag）
- `!important` 优先级处理
- `inherit`/`initial`/`unset` 关键字语义
- `opacity < 1` 创建 stacking context 和 group opacity
- `transform` 创建 stacking context
- `visibility:hidden` 占布局空间但不绘制，子元素可恢复
- `pointer-events` 继承行为
- outline 在所有内容之后绘制
- 百分比 padding/margin 垂直方向相对包含块宽度解析
- `position:relative` + `z-index:auto` 不创建 stacking context
- `display:none` 不生成布局盒
- `color`、`font-weight`、`line-height`、`text-align`、`cursor`、`visibility` 等正确标记为继承
- `margin`、`padding`、`border-width`、`background-color`、`display` 等正确标记为非继承
- click target 按 mousedown/mouseup 最近公共祖先解析
- scroll 事件不冒泡
- insertBefore referenceNode 为 null 时等同于 appendChild
- appendChild 已有父节点的节点时先从旧父节点移除
