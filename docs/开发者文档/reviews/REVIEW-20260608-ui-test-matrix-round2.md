# `/qzuilib test` 矩阵第二轮审查

## 审查范围

- 目标：第二轮继续查 bug 与审查优化点，不推进新功能、不修源码。
- 背景：第一轮两个问题（批量运行污染分组页索引、人工结果无回写入口）本轮不优先展开。
- 重点文件：`UiTestAnimationAssertionRunner`、`UiTestAnimationVisualFactory`、`UiTestControlsAssertionRunner`、`UiTestControlsVisualFactory`、`DocumentClickEventDispatcher`、`DocumentTabControl` 及相关测试。

## 结论摘要

- 发现 4 个问题：1 个真实浏览器语义 bug，3 个矩阵自动断言/覆盖强度问题。
- 最高风险是原生 `disabled` 表单元素仍可通过鼠标路径派发 `click`，以及 Animation 分组自动断言能在未验证真实生命周期/渲染样例的情况下通过。

## Findings

### P1：原生 `disabled` 表单元素仍可通过鼠标派发 `click`

- 位置：`src/main/java/club/heiqi/uilib/ui/dom/ElementSemantics.java:23`
- 位置：`src/main/java/club/heiqi/uilib/ui/document/HtmlLikeDocumentWidget.java:674`
- 位置：`src/main/java/club/heiqi/uilib/ui/document/HtmlLikeDocumentWidget.java:713`
- 位置：`src/main/java/club/heiqi/uilib/ui/document/DocumentClickEventDispatcher.java:55`
- 位置：`src/main/java/club/heiqi/uilib/ui/document/DocumentClickEventDispatcher.java:80`
- 现象：`ElementSemantics.isDisabled()` 已把带 `disabled` 属性的原生 `button/input/textarea/select` 识别为禁用，焦点和文本输入路径也会避开禁用元素；但鼠标路径在 `onMouseDown()` 仍把禁用元素保存为 `pressedElement`，`onMouseUp()` 仍解析 common ancestor 并调用
  `DocumentClickEventDispatcher.dispatchClick()`。点击分发器没有检查 `target.isDisabled()`，因此裸 `document.button().setAttribute("disabled", "true")` 如果挂了 click handler，鼠标按下/抬起后仍会触发该 handler。
- 影响：这与浏览器原生禁用表单控件语义不一致。当前 `DocumentButtonControl` 因自身 `activate()` 内部检查 `enabled`，掩盖了裸原生元素路径；`VIS-CTRL-001` 只覆盖控件包装层，无法发现 raw button/raw input 这类作者直接使用 DOM-like 元素时的副作用。
- 验证方式：新增一个 `HtmlLikeDocumentWidget` 级测试，创建 raw `document.button()`，设置 `disabled` 与 click handler 计数，将 widget 布局后对按钮中心调用 `onMouseDown()` / `onMouseUp()`；期望计数为 `0`，当前代码路径会进入
  `DocumentClickEventDispatcher` 并调用 target click handler。
- 建议：在鼠标 click 目标解析或 `dispatchClick()` 入口过滤禁用原生表单元素，并补 raw disabled button 的鼠标回归测试；同时评估 `dblclick/contextmenu/mousedown/mouseup` 是否需要按项目浏览器语义边界一并收口。

### P1：Animation 自动断言可在未验证真实生命周期和渲染样例时通过

- 位置：`src/main/java/club/heiqi/uilib/internal/devtools/pages/UiTestMatrixRegistry.java:425`
- 位置：`src/main/java/club/heiqi/uilib/internal/devtools/pages/UiTestAnimationVisualFactory.java:83`
- 位置：`src/main/java/club/heiqi/uilib/internal/devtools/pages/UiTestAnimationAssertionRunner.java:115`
- 位置：`src/main/java/club/heiqi/uilib/internal/devtools/pages/UiTestAnimationAssertionRunner.java:128`
- 位置：`src/main/java/club/heiqi/uilib/internal/devtools/pages/UiTestAnimationAssertionRunner.java:167`
- 位置：`src/main/java/club/heiqi/uilib/internal/devtools/pages/UiTestAnimationAssertionRunner.java:203`
- 现象：矩阵文案宣称 `VIS-ANIM-001` 会切换 `background-color` 并检查 `transitionstart/transitionend`，但视觉样例创建蓝色 box 后在首次布局前直接把背景设成绿色，断言只检查 transition 声明、900ms duration 和最终绿色 computed style。`VIS-ANIM-002` 与
  `VIS-ANIM-004` 的断言 runner 会重新注册 `qzAnimPulseAssert` / `qzAnimFillAssert` 并覆盖当前元素的 `animationName`，验证的是断言自建 keyframes，而不是视觉样例中的 `qzAnimPulse` / `qzAnimFill`。`VIS-ANIM-003` 也重新注册 `qzAnimTimingAssert`，
  并在 0.5 进度采样；该点上 `linear` 与 `steps(4,end)` 都是 `0.5`，不能证明离散 steps 节奏或真实 translate 差异。
- 影响：Animation 运行态、事件派发、declared keyframes 注册名、forwards fill 最终布局宽度或 steps 视觉节奏发生回归时，`/qzuilib test` 仍可能显示自动通过。当前
  `UiTestDocumentPageControllerTest.shouldRenderAnimationSamplesAndRunAnimationAssertions()` 也主要断言诊断文本片段，无法阻止这类假阳性。
- 验证方式：临时破坏 transition lifecycle 事件派发或把视觉样例的 `qzAnimPulse` 注册改错，当前断言仍可能通过，因为 runner 不依赖样例真实生命周期或样例注册名。更直接的回归测试应使用手动 clock：先完成初始布局，再变更样式，推进到中间帧和结束帧，断言 active transition/keyframe 计数、start/end 事件日志、
  中间颜色/位移和最终 fill 后宽度。
- 建议：让自动断言验证视觉样例本身，而不是注册 assertion-only keyframes；`VIS-ANIM-001` 应真实触发属性变化并检查 lifecycle，`VIS-ANIM-002/004` 应读取 `qzAnimPulse` / `qzAnimFill` 并比较运行态样式，`VIS-ANIM-003` 应在非边界进度采样或直接比较 linear/steps 的运行态
  transform。

### P2：`VIS-CTRL-007` 宣称 roving focus，但所有 tab 都在 Tab 顺序中

- 位置：`src/main/java/club/heiqi/uilib/internal/devtools/pages/UiTestMatrixRegistry.java:392`
- 位置：`src/main/java/club/heiqi/uilib/ui/control/DocumentTabControl.java:107`
- 位置：`src/main/java/club/heiqi/uilib/ui/control/DocumentTabControl.java:518`
- 位置：`src/main/java/club/heiqi/uilib/internal/devtools/pages/UiTestControlsAssertionRunner.java:269`
- 现象：矩阵规格写明 tablist 使用 `roving focus`，但 `DocumentTabControl.addTab()` 给每个 tab 固定设置 `tabindex="0"` 并让每个 tab 都 `setFocusable(enabled)`；`updateVisualState()` 只更新 `aria-selected` 和颜色，不会把非活动 tab 改为
  `tabindex="-1"`。`VIS-CTRL-007` 自动断言只点击第二个 tab 并验证 disabled input/button 拒绝焦点，没有检查 tab roving tabindex 或 Tab 遍历行为。
- 影响：键盘 Tab 遍历会停在每一个 tab 上，而不是只进入当前 roving item；这会让 `/qzuilib test` 页面报告的控件语义比真实实现更乐观，也影响业务页面中 `DocumentTabControl` 的键盘可用性和辅助语义一致性。
- 验证方式：创建 3 个 tab 的 `DocumentTabControl`，设置 active index 后收集所有 `role="tab"` 元素；预期只有当前 roving tab 为 `tabindex="0"`，其他为 `-1`，当前实现全部为 `0`。再通过 `HtmlLikeDocumentWidget.onFocusTraversal(false)` 验证 Tab
  遍历当前会逐个停留在所有 tab。
- 建议：明确项目是否承诺 roving tabindex。若承诺，应在 `DocumentTabControl` 状态刷新时同步 active/focused tab 的 tabindex，并补 `DocumentTabControlTest` 与 `VIS-CTRL-007` 自动断言；若暂不承诺，应修改矩阵文案，避免把未实现能力标为已验证。

### P3：`VIS-CTRL-005` 选择 option 时绕过真实 top-layer 命中测试

- 位置：`src/main/java/club/heiqi/uilib/internal/devtools/pages/UiTestControlsAssertionRunner.java:212`
- 位置：`src/main/java/club/heiqi/uilib/internal/devtools/pages/UiTestControlsAssertionRunner.java:221`
- 位置：`src/main/java/club/heiqi/uilib/internal/devtools/pages/UiTestControlsAssertionRunner.java:307`
- 位置：`src/test/java/club/heiqi/uilib/internal/devtools/pages/UiTestDocumentPageControllerTest.java:773`
- 位置：`src/test/java/club/heiqi/uilib/internal/devtools/pages/UiTestDocumentPageControllerTest.java:779`
- 现象：`VIS-CTRL-005` 自动断言会用真实 widget 鼠标事件打开 select，并检查 popup 被注册为 top-layer；但选择“红石”时调用 `clickElementDirect(targetOption, ...)`，直接执行 option 的 click handler。页面测试里的 `clickOptionByLabel()` 也是直接调用 handler，
  而不是通过 widget hit-test / mouse down/up 走真实弹层命中路径。
- 影响：popup 位置、top-layer 盒参与命中、clip chain 或滚动偏移有问题时，只要 option 节点存在且 handler 正常，矩阵仍能通过“选项选择、value、change 日志和 table 同步”。这会削弱 Select 样例对近期 top-layer、clip、命中语义的回归价值。
- 验证方式：打开 select 后解析目标 option 的布局盒中心，通过 `HtmlLikeDocumentWidget.onMouseDown()` / `onMouseUp()` 进行真实点击，并断言 `findElementAt` 或最终事件目标命中 option；不要直接调用 handler。
- 建议：将 `UiTestControlsAssertionRunner` 和页面测试 helper 的 option 选择路径改为 widget 鼠标事件。弹层位置截图仍可保留人工确认，但自动断言至少应覆盖 top-layer option 可命中。
