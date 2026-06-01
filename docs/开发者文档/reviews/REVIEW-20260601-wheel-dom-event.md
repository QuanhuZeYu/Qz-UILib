# wheel DOM 事件语义修复代码审查（Phase 2 第三批）

## 审查信息

- 审查日期：2026-06-01
- 审查提交：`f846cce` [Fix]: 补齐 wheel DOM 事件语义
- 合并提交：`9a283f3` [Fix]: 合并 wheel DOM 事件语义修复
- 对应审查问题：[REVIEW-20260601-browser-semantics-phase2-audit](REVIEW-20260601-browser-semantics-phase2-audit.md) 3.4 [中] wheel 事件无独立 DOM 事件分发
- 审查范围：wheel 事件类型、capture/target/bubble 分发链路、默认滚动取消语义、滚动后 hover 刷新口径、handler 注册入口与命中可交互判定
- 验证方式：源码逐项对照 W3C UI Events / DOM 标准 + 测试断言核对；本轮离线 `test` 在 Gradle 配置阶段命中已知外部波动未能复跑（见下文“验证状态”）

---

## 一、总体结论

**通过。** 本批修复对应 Phase 2 审查报告 3.4（中严重度），方向完全正确，达到工程化质量，不是临时补丁：

- 没有把 wheel 当作 `scrollState` 的一次性消费，而是补齐了完整的 capture → target → bubble DOM 事件链路，与既有 `mousedown`/`mouseup` 分发器结构对齐。
- 严格遵守了项目已确立的「返回值只停止传播、取消默认行为统一依赖 `preventDefault()`」决策（`DECISION-20260531`），没有在 wheel 上引入新的隐式取消语义。
- 新增了 3 条针对性回归测试，分别覆盖分发顺序、返回值语义和 `preventDefault` 默认滚动取消，断言直接验证浏览器规范行为（阶段字符串、scrollTop 数值）。
- 同步更新了稳定 API 清单、能力边界、当前态记忆与审查索引，文档与实现一致，无夸大。

---

## 二、逐项审查

### 2.1 wheel 事件类型与方向语义

**文件**：`DocumentElementWheelEvent.java`

- 继承 `AbstractDocumentElementEvent`，复用统一的 `target`/`currentTarget`/`DocumentEventControl`，未重抄传播控制样板代码。
- 同时暴露 `getWheelDelta()`（原始 LWJGL/MC 方向，向上为正）与 `getDeltaY()`（浏览器式，向下为正，实现为 `-wheelDelta`）。

**浏览器标准对照**：

- `WheelEvent.deltaY` 向下滚动为正值（CSSOM View / UI Events）。LWJGL 滚轮向上为正、向下为负，取反后 `deltaY` 符合浏览器方向。
- 测试 `shouldDispatchWheelEventBeforeDefaultScrollInDomOrder` 用 `wheelDelta=-120` 断言 `getDeltaY()=120`（向下滚动，内容下移、scrollTop 增大到 36），方向自洽。

**结论**：✅ 符合浏览器方向语义。`getWheelDelta()` 作为宿主原始量保留，是合理的逃生口，Javadoc 已说明方向约定。

### 2.2 capture → target → bubble 分发顺序

**文件**：`DocumentMouseEventDispatcher.dispatchWheel`（`DocumentMouseEventDispatcher.java:179-243`）

- 阶段一 CAPTURING：从根向目标父（`path.size()-1` 到 `1`）逐级触发 `captureWheelHandler`。
- 阶段二 AT_TARGET：目标自身先触发 `captureWheelHandler`，再触发 `wheelHandler`，中间用 `isImmediatePropagationStopped()` 守卫。
- 阶段三 BUBBLING：从目标父向根（`1` 到 `path.size()-1`）逐级触发 `wheelHandler`。

**浏览器标准对照**：

- W3C UI Events 三阶段传播：capture 自上而下、目标处先 capture 后 bubble 监听、再 bubble 自下而上。
- 测试断言序列 `[root-capture:CAPTURING, child-capture:AT_TARGET, child:AT_TARGET, root-bubble:BUBBLING]` 与标准一致。目标元素 capture handler 在 AT_TARGET 阶段报告，符合浏览器对 target 节点上 capture/bubble 监听器统一在 AT_TARGET 触发的行为。

**结论**：✅ 与 `mousedown`/`mouseup` 同构，分发顺序正确，无方向性错误。

### 2.3 返回值与 preventDefault 默认滚动取消

**文件**：`DocumentElementWheelHandler.java`、`DocumentMouseEventDispatcher.WheelDispatchResult`、`HtmlLikeDocumentWidget.onMouseScroll`（`HtmlLikeDocumentWidget.java:631-655`）

- handler 返回 `true` → `eventControl.stopPropagation()`，仅停止传播。
- `preventDefault()` → `eventControl.defaultPrevented`，分发器结果回传 `isDefaultPrevented()`。
- `onMouseScroll`：先分发 wheel；若 `defaultPrevented` 则直接消费返回，**不执行** `scrollState.handleWheel`；否则执行默认滚动，`consumed = scrolled || propagationStopped`。

**浏览器标准对照**：

- `wheel` 事件 `cancelable`，`preventDefault()` 阻止默认滚动；仅 `stopPropagation()` 不影响默认动作（UI Events / CSSOM View）。
- 测试 `shouldKeepDefaultWheelScrollWhenHandlerOnlyStopsPropagation`：child 返回 true，root 不再收到，但 scrollTop 仍变为 36（默认滚动照常）→ 正确。
- 测试 `shouldPreventDefaultWheelScrollFromWheelEvent`：child `preventDefault()` 后 scrollTop 保持 0，事件仍被消费（返回 true）→ 正确。

**结论**：✅ 严格符合 `DECISION-20260531` 与浏览器 cancelable 语义。本框架不区分 passive listener，所有 wheel handler 均可 `preventDefault`，属合理简化（无 passive 概念）。

### 2.4 wheel 事件 target 选择

**文件**：`HtmlLikeDocumentWidget.onMouseScroll` → `findElementAt` → `DocumentHitTestEngine.hitTest`

- wheel target 由 `findElementAt` 命中测试给出，命中判定为 `insideBorderBox && isSelfHitTestVisible && isPointerEventsEnabled`（`DocumentHitTestEngine.java:163`），**不依赖元素是否注册 handler**。

**浏览器标准对照**：

- 浏览器 wheel 事件 target 是指针命中的最深元素，与是否有监听器无关；`pointer-events:none` 元素跳过。命中语义一致。
- 默认滚动目标仍由 `scrollState.handleWheel` 独立选择最深可滚动盒（scroll chaining）。这与浏览器「wheel 派发到命中元素，默认动作滚动最近可滚祖先」的目标解耦关系一致，不会因 target 自身不可滚而丢失滚动。

**结论**：✅ target 选择与默认动作目标解耦，符合浏览器语义。

### 2.5 滚动后 hover 刷新口径修正

**文件**：`HtmlLikeDocumentWidget.onMouseScroll`（`HtmlLikeDocumentWidget.java:645-653`）

- 旧逻辑：`if (consumed) updateHoveredElement(...)`。
- 新逻辑：记录 `previousScrollVersion`，仅当 `scrollState.getVersion()` 变化（内容真正滚动）时刷新 hover。

**分析**：

- 引入 wheel 分发后，`consumed` 现在还可能因 `propagationStopped` 或 `defaultPrevented` 为 true，但内容并未滚动。若仍按 `consumed` 刷新 hover，会在内容没有移动时做无意义命中；按 `version` 变化判断更精确。
- `DocumentScrollState.version` 仅在 `updateOffsets` 实际改变偏移（`DocumentScrollState.java:535`）或丢弃带偏移条目（`:173`）时自增，能准确代表「内容相对鼠标移动」。这正是 `ERROR-20260509-html-hover-stale-after-scroll` 要解决的根因。

**结论**：✅ 比旧的 `consumed` 口径更贴合「滚动后重新命中 hover」的真实触发条件，是一处实质改进而非回归。

### 2.6 handler 注册入口与命中可交互判定

**文件**：`ElementInteractionHandlers.java`、`ElementInteractionNode.java`、`HtmlLikeDocumentWidget.isInteractiveHit`（`:562-563`）

- 在 handler 容器中新增 `wheelHandler` / `captureWheelHandler` 字段，注册/读取方法与既有 handler 一致，保持链式 `self()` 返回。
- `isInteractiveHit` 增加 wheel/captureWheel 判定：注册了 wheel handler 的元素纳入可交互命中目标，与点击、拖拽、键盘等 handler 同级处理。

**结论**：✅ 命名、注释、转发结构与既有 handler 完全一致，无样板膨胀（容器集中存放）。

---

## 三、代码质量评价

- **封装**：`WheelDispatchResult` 用不可变值对象同时回传 `propagationStopped` 与 `defaultPrevented`，避免用布尔元组或可变 out 参；分发器为包级 `static`，不污染公开 API。
- **复用**：分发逻辑与 `dispatchMouseDown`/`dispatchMouseUp` 完全同构，`buildAncestorPath` 复用，未重复发明事件循环。
- **命名/注释**：英文驼峰、中文 Javadoc 齐备，符合项目规范；handler 接口 Javadoc 明确写出「返回 true 停止传播、preventDefault 取消默认滚动」的双语义边界。
- **测试覆盖**：3 条新增测试覆盖顺序、返回值、preventDefault 三条独立路径，断言直接验证规范行为，符合 `ERROR-20260521` 沉淀的「带默认行为的事件必须补 handler 返回 false 但 preventDefault 生效」要求。

---

## 四、遗留与边界（非缺陷）

1. **deltaX / 横向 wheel**：`DocumentElementWheelEvent` 只暴露 `deltaY`，未提供 `deltaX`。`UiMouseEvent` 已有 `deltaX` 字段但语义是鼠标位移而非横向滚轮量；当前默认滚动在纵向不可滚时回退横向（`DocumentScrollState.java:262-266`），但 wheel 事件本身不携带横向滚轮分量。属可接受的能力边界，后续若需横向滚轮需扩展输入源。
2. **deltaMode / deltaZ**：未实现 `WheelEvent.deltaMode`（像素/行/页）与 `deltaZ`。框架按固定 `scrollStep` 折算步数，属合理简化。
3. **passive listener**：不区分被动监听器，所有 wheel handler 均可 `preventDefault`。无 passive 概念，符合现状。
4. **更大影响面项仍未收口**：fixed clip chain、父子 margin collapse 递归等仍属后续批次，与本批无关。

---

## 五、验证状态

- 空白检查 `git diff --check`：通过。
- 离线 `test --tests HtmlLikeDocumentWidgetTest`：本轮连续重试 3 次均在 Gradle 配置阶段命中 `Failed to load the manifest from Github`（`ERROR-20260601-gradle-gtnhconvention-github-manifest-flaky`，外部波动），未能在本会话内复跑。按 errors 记录不判定为代码回归。
- 提交记录与当前态记忆显示该测试集此前已通过离线 `HtmlLikeDocumentWidgetTest` 与离线 `compileJava`。
- 建议：网络恢复后补跑一次 `test --tests "club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTest"` 做末次收口确认。
