# 浏览器语义修复代码审查（Phase 2 后续批次）

## 审查信息

- 审查日期：2026-06-01
- 审查提交：`7371007` [Fix]: 继续修复浏览器语义
- 合并提交：`73a46e1` [Fix]: 合并浏览器语义后续修复
- 审查范围：DOM API、焦点事件、鼠标状态通知、样式元数据、绘制层级
- 验证方式：源码逐项对照 W3C/WHATWG 规范 + 离线回归测试全绿（`BUILD SUCCESSFUL`）

---

## 一、总体结论

本批修复对应 Phase 2 审查报告中 P2 级别的 7 项问题，全部按浏览器标准方向修复，无方向性错误。代码质量整体达到工程化标准，不是补丁式修改：每项修复均有独立的回归测试覆盖，且测试断言直接验证浏览器规范行为（事件顺序字符串、命令类型枚举、异常消息内容）。

---

## 二、逐项审查

### 2.1 removeChild 返回值与异常语义

**对应审查问题**：6.6 [低] removeChild 返回 boolean 而非被移除节点

**修复内容**（`DocumentNode.java:164`）：
- 返回类型从 `boolean` 改为 `DocumentNode`，返回被移除的节点
- `null` 参数改为 `Objects.requireNonNull` 抛 NPE
- 非直接子节点从静默返回 `false` 改为抛 `IllegalArgumentException`

**浏览器标准对照**：
- `Node.removeChild()` 返回被移除的子节点（WHATWG DOM §4.2.6）
- 非子节点抛 `NotFoundError` DOMException（对应 Java 的 `IllegalArgumentException`，语义等价）
- `null` 参数抛 `TypeError`（对应 `NullPointerException`，语义等价）

**结论**：✅ 完全符合浏览器语义。

**调用方安全性**：已核查所有内部调用方（`UiHudDocumentHost.java:148,726,775`、`DocumentTableControl.java:287`、`DocumentTabControl.java:180,182`），均在调用前已确认父子关系（`getParent() != null` 或 `getParent() == element` 守卫），不会触发新增异常。

---

### 2.2 querySelector / querySelectorAll 排除内部根节点

**对应审查问题**：6.5 [中] querySelector("*") 可能返回内部根节点

**修复内容**（`DocumentQuerySupport.java:38-68`）：
- `querySelector` 和 `querySelectorAll` 不再从 `root` 自身开始匹配，改为从 `root.getChildren()` 开始遍历

**浏览器标准对照**：
- `document.querySelector()` 在文档的后代元素中搜索，不包含 `document` 节点本身（WHATWG DOM §4.5.6）
- 等价于：调用方是 `document`，搜索范围是其子树，但 `document` 本身不参与匹配

**结论**：✅ 符合浏览器语义。

**细节确认**：`getElementById` 和 `getElementsByTagName/ClassName` 未做同样修改，但这些方法的 `root` 参数在实际调用路径中传入的是 `document.getRootElement()`（即内部根节点），其 id 不会被业务代码设置，实际不构成问题。`querySelector*` 是最容易被业务代码触发的路径，优先修复正确。

---

### 2.3 focusout 独立冒泡事件与焦点切换顺序

**对应审查问题**：3.1 [P2] 焦点事件顺序、3.2 [P2] focusout 缺失

**修复内容**（`DocumentFocusManager.java`、新增 `DocumentElementFocusOutEvent`、`DocumentElementFocusOutHandler`）：

新增 `focusout` 独立冒泡事件类型，并将焦点切换分发顺序调整为：
```
focusout(旧元素) → focusin(新元素) → blur(旧元素) → focus(新元素)
```

**浏览器标准对照**（W3C UI Events §4.3.7）：
> 焦点从元素 A 移到元素 B 时，事件顺序为：
> 1. `focusout` 在 A 上触发（冒泡）
> 2. `focusin` 在 B 上触发（冒泡）
> 3. `blur` 在 A 上触发（不冒泡）
> 4. `focus` 在 B 上触发（不冒泡）

测试 `shouldDispatchFocusTransitionInBrowserOrder` 直接断言了这个顺序字符串 `[focusout:first, focusin:second, blur:first, focus:second]`，与规范完全一致。

**结论**：✅ 完全符合浏览器语义。

**一个值得注意的实现细节**：`dispatchFocusIn` 和 `dispatchFocusOut` 内部均使用 `DocumentEventControl` 支持 `stopPropagation`，这与浏览器中 `focusin`/`focusout` 可冒泡但不可取消（`cancelable: false`）的语义略有差异——当前实现允许 handler 返回 `true` 来中断冒泡。这是一个已知的设计取舍（框架内部事件消费模型），不属于本批修复引入的新问题，且与 `focusin` 的既有行为保持一致。

---

### 2.4 hover / active 状态通知不中断祖先同步

**对应审查问题**：3.3 [P2] hover 消费语义、3.6 [P2] active 消费语义

**修复内容**（`DocumentMouseEventDispatcher.java`）：
- `dispatchActive` 返回类型从 `boolean` 改为 `void`，移除 handler 返回值中断冒泡的逻辑
- `dispatchHoverChangedWithAncestorAwareness` 同样改为 `void`，移除中断逻辑

**浏览器标准对照**：
- `:hover` 和 `:active` 是 CSS 伪类状态，由浏览器内部维护，不受 JS 事件 handler 的 `stopPropagation` 控制
- 祖先元素的 `:hover`/`:active` 状态是独立计算的，子元素的事件消费不影响祖先状态同步
- `mouseover`/`mouseout` 事件可冒泡且可被 `stopPropagation` 中断，但这是事件分发层，与状态同步层分离

**结论**：✅ 符合浏览器语义。状态通知（`:hover`/`:active` 同步）与事件分发（`mouseover`/`mouseout` 冒泡）现在正确分离。

---

### 2.5 border-collapse 继承标记

**对应审查问题**：4.2 [P2] border-collapse 继承标记错误

**修复内容**（`UiStyleProperty.java:68`）：
```java
// 修复前
BORDER_COLLAPSE(false, UiStyleChangeImpact.LAYOUT),
// 修复后
BORDER_COLLAPSE(true, UiStyleChangeImpact.LAYOUT),
```

**浏览器标准对照**：
- CSS 2.1 §17.6：`border-collapse` 是继承属性，子表格元素从父元素继承该值

**结论**：✅ 符合浏览器语义。

---

### 2.6 font-style 变更影响级别

**对应审查问题**：4.3 [P2] font-style changeImpact 错误

**修复内容**（`UiStyleProperty.java:77`、`UiStyleDeclaration.java:2172`）：
```java
// 修复前
FONT_STYLE(true, UiStyleChangeImpact.PAINT),
// 修复后
FONT_STYLE(true, UiStyleChangeImpact.LAYOUT),
```
`UiStyleDeclaration.updateFontStyle` 中的 `updateProperty` 调用也同步修正为 `LAYOUT`。

**浏览器标准对照**：
- `font-style: italic` 会影响字形选择，斜体字形通常有不同的字符宽度，因此必须触发布局重算
- 仅标记为 `PAINT` 会导致切换 italic 后文本宽度不重新测量，出现布局错位

**结论**：✅ 符合浏览器语义。两处修改（枚举元数据 + 声明更新方法）保持一致，无遗漏。

---

### 2.7 inset box-shadow 绘制层级

**对应审查问题**：绘制顺序中 inset shadow 应在背景之上、边框之下

**修复内容**（`DocumentPaintEngine.java:161-168`）：
```java
// 修复前顺序：outset shadow → background → border → inset shadow（错误）
// 修复后顺序：outset shadow → background → inset shadow → border（正确）
appendBoxShadowCommand(..., false);   // outset shadow（在背景之下）
appendBackgroundCommand(...);
appendBoxShadowCommand(..., true);    // inset shadow（在背景之上、边框之下）
appendBorderCommand(...);
```

**浏览器标准对照**（CSS Backgrounds and Borders Level 3 §9）：
> 盒模型绘制顺序（从下到上）：
> 1. outer box-shadow
> 2. background-color
> 3. background-image
> 4. inner box-shadow（inset）
> 5. border

**结论**：✅ 完全符合 CSS 绘制层级规范。

---

## 三、测试覆盖评估

| 修复项 | 测试类 | 测试方法 | 断言质量 |
|--------|--------|----------|----------|
| removeChild 语义 | `UiDocumentTest` | `shouldReturnRemovedNodeAndRejectNonChildFromRemoveChild` | 验证返回值、父节点清空、异常消息 |
| querySelector 排除根节点 | `DocumentQueryTest` | `querySelectorDoesNotReturnInternalDocumentRoot`、`querySelectorAllDoesNotIncludeInternalDocumentRoot` | 验证 `*` 选择器与 `#id` 选择器两种路径 |
| 焦点切换顺序 | `HtmlLikeDocumentWidgetTest` | `shouldDispatchFocusTransitionInBrowserOrder` | 直接断言事件顺序字符串 |
| active 不中断祖先 | `HtmlLikeDocumentWidgetTest` | `shouldNotifyActiveStateAncestorsEvenWhenTargetConsumes` | 子元素消费后祖先仍收到通知 |
| hover 不中断祖先 | `HtmlLikeDocumentWidgetTest` | `shouldNotifyHoverAncestorsEvenWhenTargetConsumes` | 子元素消费后祖先仍收到通知 |
| inset shadow 层级 | `DocumentPaintEngineTest` | `shouldPaintInsetBoxShadowAboveBackgroundAndBelowBorder` | 断言命令类型枚举顺序 |
| font-style 触发布局 | `UiDocumentTest` | `shouldIncrementLayoutVersionOnLayoutImpactingStyleChange`（扩展） | 验证 layoutVersion 递增 |
| border-collapse 继承 | `UiStyleResolverTest` | `shouldApplyBrowserInheritanceMetadataForTableAndTextStyles` | 验证子元素 computed style 继承值 |

所有测试均在离线回归中通过（`BUILD SUCCESSFUL in 30s`）。

---

## 四、遗留问题与边界说明

1. **focusin/focusout 可被 stopPropagation 中断**：浏览器中这两个事件 `cancelable: false`，但冒泡可被 `stopPropagation` 中断。当前实现允许 handler 返回 `true` 中断冒泡，与浏览器行为一致；但 `cancelable` 语义未区分，属已知设计取舍，不影响主流用法。

2. **text-shadow 继承标记**：Phase 2 审查报告 4.1 将 `text-shadow` 标记为"非继承"，但 CSS 标准中 `text-shadow` 实际是继承属性（CSS Text Decoration Level 3 §4）。本批修复未修改 `text-shadow` 继承标记，保留了正确的继承语义，审查报告中该条目为误报，已在 README 索引中注明。

3. **wheel 事件 DOM 分发**（3.4 P2）：本批未覆盖，仍为待修复项。

---

## 五、审查结论

**通过**。7 项修复全部方向正确，代码质量达到工程化标准，测试覆盖完整，回归测试全绿。无需返工。
