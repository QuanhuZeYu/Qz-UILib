# 全项目生产代码第三轮审查

## 审查范围

- 目标：本轮不再围绕 `/qzuilib test` 或 `src/test` 找问题，转向 `src/main/java` 生产代码审查。
- 重点覆盖：HTML-like 文档核心、控件、布局/绘制/动画、HUD/宿主页、网络/远程桥接、配置与字体运行时。
- 方法：先按模块入口、复杂度和调用路径缩小范围，再回到源码逐条核实；CodeGraph 仅作为定位辅助，结论以源码为准。
- 本轮未修改生产代码，以下均为待修复 findings。

## 结论摘要

- 发现 4 个可证实问题：2 个 P1、2 个 P2。
- P1 集中在输入事件默认取消语义和 HUD top-layer 命中预过滤；P2 集中在动态样式表缓存失效和 top-layer 生命周期状态泄漏。
- 网络/远程桥接、配置同步与字体/远程图片运行时补查了异步回调、主线程派发、Executor 关闭和断连清理路径，本轮未形成可证实 finding。

## Findings

### P1：`textInput.preventDefault()` 不能阻止内置 input/textarea 改值

- 位置：`src/main/java/club/heiqi/uilib/ui/dom/DocumentEventControl.java:55`
- 位置：`src/main/java/club/heiqi/uilib/ui/document/DocumentKeyboardEventDispatcher.java:62`
- 位置：`src/main/java/club/heiqi/uilib/ui/control/DocumentTextInputControl.java:424`
- 位置：`src/main/java/club/heiqi/uilib/ui/control/DocumentTextAreaControl.java:420`
- 问题现象：`DocumentEventControl.preventDefault()` 明确写着可阻止 `input` 默认文本输入，但 `DocumentKeyboardEventDispatcher.dispatchTextInput()` 只把 `DocumentEventControl` 贯穿 capture/target/bubble 并最终返回
  `isPropagationStopped()`，完全没有读取 `isDefaultPrevented()`。内置 `DocumentTextInputControl` 和 `DocumentTextAreaControl` 又把实际文本变更注册成 target `textInput` handler，因此祖先 capture handler 或 target capture handler
  调用 `event.preventDefault()` 后，只要没有同时停止传播，target handler 仍会追加文本或替换选区。
- 影响：页面作者无法用 DOM-like 取消语义拦截文本输入默认行为，例如数字过滤、只读条件拦截、组合输入策略或自定义格式化都可能在取消后仍污染控件值。这也会让 `preventDefault()` 在 click/wheel 链路有效、在 textInput 链路无效，形成事件语义不一致。
- 触发或验证方式：创建一个包含 `DocumentTextInputControl` 的文档，在其祖先元素注册 `setCaptureTextInputHandler(event -> { event.preventDefault(); return false; })`，聚焦输入框后向 widget 派发 `UiTextInputEvent("x", ...)`。预期 value 不变；
  当前会进入 `DocumentTextInputControl.onTextInput()` 并把 `x` 追加到 value。textarea 同理，会进入 `replaceSelection(...)`。
- 建议修复方向：将内置文本控件的“改值”从普通 target handler 拆成分发完成后的默认动作，并在 `eventControl.isDefaultPrevented()` 为 false 时才执行；或至少让内置 input/textarea target handler 在变更前检查 `event.isDefaultPrevented()`。同时补 capture 阶段
  `preventDefault()` 不改值的回归测试。

### P1：HUD 交互预过滤漏掉 top-layer 后代，select 弹层 option 无法可靠点击

- 位置：`src/main/java/club/heiqi/uilib/ui/hud/UiHudDocumentHost.java:503`
- 位置：`src/main/java/club/heiqi/uilib/ui/document/HtmlLikeDocumentWidget.java:414`
- 位置：`src/main/java/club/heiqi/uilib/ui/layout/DocumentLayoutEngine.java:472`
- 位置：`src/main/java/club/heiqi/uilib/ui/control/DocumentSelectControl.java:463`
- 位置：`src/main/java/club/heiqi/uilib/ui/remote/RemoteHtmlDocumentParser.java:352`
- 问题现象：HUD 在把鼠标帧交给共享 `HtmlLikeDocumentWidget` 前，会先用 `resolveMouseTargetEntry()` 遍历每个交互 HUD 注册项，并调用 `sharedWidget.findElementAtWithin(entry.mountRoot, mouseX, mouseY)` 判断是否应捕获。`findElementAtWithin()`
  虽然先构造了 `topLayerBoxes` 并用它定位 `subtreeRoot`，但真正命中时只对 `subtreeLocation.getBoxContext().getBox()` 做普通子树 hit-test，没有把同属该子树的 top-layer descendants 纳入命中。与此同时，`DocumentLayoutEngine` 会跳过已注册
top-layer 的普通子节点，
  `DocumentSelectControl` 展开时又把 popup 注册为 top-layer。
- 影响：交互 HUD 中的 `DocumentSelectControl` 可以展开弹层，但点击 option 时，HUD 预过滤会在 option 所在坐标只命中全屏 `mountRoot` 或空白，`shouldCaptureHit()` 又明确拒绝 `hitElement == entry.mountRoot`，导致鼠标帧不进入共享 widget 的真实事件路由。远程 HTML 解析支持
  `<select>` 并创建 `DocumentSelectControl`，因此远程 HUD/交互 HUD 中的下拉控件存在真实不可点风险。
- 触发或验证方式：通过 `UiHudDocumentHost.register(UiHudLayerType.INTERACTIVE, ...)` 在 HUD 中挂载一个 `DocumentSelectControl`，或让远程 HUD HTML 包含 `<select><option>...</option></select>`；在容器界面中点击触发展开，再点击 popup option。预期
  option 的 click handler 选择新值；当前预过滤路径可能返回 `null`，导致 option click 不被派发。
- 建议修复方向：`findElementAtWithin()` 应先对包含普通根与 top-layer roots 的完整 visual scene 做 hit-test，再用 DOM 父链判断命中元素是否属于 `subtreeRoot`；或新增专门的 `hitTestWithinSubtreeIncludingTopLayer(...)`，确保逻辑上属于 HUD mountRoot 的
  top-layer descendants 参与 HUD 预过滤。

### P2：挂载后的 `UiStyleSheet` 再变更不会触发布局/绘制缓存失效

- 位置：`src/main/java/club/heiqi/uilib/ui/style/cascade/UiStyleSheet.java:54`
- 位置：`src/main/java/club/heiqi/uilib/ui/style/cascade/UiStyleSheet.java:143`
- 位置：`src/main/java/club/heiqi/uilib/ui/dom/UiDocument.java:642`
- 位置：`src/main/java/club/heiqi/uilib/ui/dom/UiDocument.java:739`
- 位置：`src/main/java/club/heiqi/uilib/ui/style/cascade/UiStyleRule.java:48`
- 位置：`src/main/java/club/heiqi/uilib/ui/document/HtmlLikeDocumentWidget.java:883`
- 问题现象：`UiDocument.addStyleSheet()` 只在首次挂载时调用 `recordGlobalLayoutMutation()`；但 `UiStyleSheet.addRule()`、`UiStyleSheet.clear()` 以及 `UiStyleRule.getDeclaration()` 返回的 mutable `UiStyleDeclaration`
  后续变更都没有回调文档。`HtmlLikeDocumentWidget.resolveLayoutBox()` 又只按 `document.getLayoutVersion()`、文本测量 epoch 和 widget 尺寸判断能否复用缓存，因此 mounted stylesheet 变更不会让已缓存布局/样式失效。
- 影响：业务如果用公开 API 做运行时主题切换、远程页面增量样式注入或复用 stylesheet 后再补规则，页面会继续显示旧 computed style，直到发生其他 DOM/style/layout 变更才偶然刷新。布局属性变更会留下错误布局盒；paint-only 属性变更也可能被旧 paint/cache 路径掩盖。
- 触发或验证方式：创建 `UiStyleSheet sheet = UiStyleSheet.create()`，挂载到 document 并渲染一次；随后调用 `sheet.addRule(".warn", new UiStyleDeclaration().setWidth(UiStyleLength.px(200)))` 或修改 `sheet.getRules().get(0)
  .getDeclaration().setTextColor(...)`，不做任何其他 DOM 变更，再请求布局或绘制。预期立即应用新规则；当前 `cachedLayoutVersion == document.getLayoutVersion()` 时会复用旧 `cachedLayoutBox`。
- 建议修复方向：让 `UiStyleSheet` 支持文档级 mutation listener，并在挂载/移除时绑定或解绑；或者明确将 `UiStyleSheet` 挂载后视为 immutable，移除公开可变路径并提供 `UiDocument` 级样式表 mutation API，所有 mutation 统一调用全局 layout/paint 失效。

### P2：移除已展开 select 子树后 top-layer 注册残留，造成状态泄漏和重挂载异常

- 位置：`src/main/java/club/heiqi/uilib/ui/dom/UiDocument.java:71`
- 位置：`src/main/java/club/heiqi/uilib/ui/dom/UiDocument.java:89`
- 位置：`src/main/java/club/heiqi/uilib/ui/dom/UiDocument.java:104`
- 位置：`src/main/java/club/heiqi/uilib/ui/dom/DocumentNode.java:187`
- 位置：`src/main/java/club/heiqi/uilib/ui/dom/DocumentNode.java:285`
- 位置：`src/main/java/club/heiqi/uilib/ui/document/HtmlLikeDocumentWidget.java:980`
- 位置：`src/main/java/club/heiqi/uilib/ui/control/DocumentSelectControl.java:463`
- 问题现象：`DocumentSelectControl` 展开时会通过 `__showTopLayerElement(popupElement)` 把 popup 注册到 `UiDocument.topLayerElements`。但通用 DOM 结构移除路径 `removeChild()` / `clearChildren()` 只断开父子关系并记录结构变更，不会隐藏被移除子树中的
  top-layer descendants。`HtmlLikeDocumentWidget.resolveTopLayerLayoutBoxes()` 遇到 detached top-layer element 只是 `continue` 跳过，没有从文档注册表中清理；`__isTopLayerElement()` 也仍会返回 true。
- 影响：移除一个仍展开的 select 或其祖先后，文档继续持有 detached popup 引用，形成状态/内存泄漏。如果后续把同一 select 子树重新挂回文档，旧 popup 会立刻恢复 top-layer 身份，可能绕过正常 open/close 生命周期并出现在错误层级。HUD unregister 已有专门 `detachTopLayerDescendants()` 清理，
  说明该生命周期风险在宿主层被局部补丁规避，但普通 HTML-like document 仍未收口。
- 触发或验证方式：创建 select，点击展开，使 `document.__isTopLayerElement(popup)` 为 true；随后对 select 的父节点执行 `removeChild(select.getElement())` 或 `clearChildren()`。预期 top-layer 注册同步移除；当前
  `document.__getTopLayerElements()
  ` 仍包含该 popup。再把 select 重挂回文档，popup 仍会作为 top-layer 参与后续布局/命中。
- 建议修复方向：在 `DocumentNode` 结构 detach 路径中通知 `UiDocument` 剪枝被移除子树的 top-layer descendants；或在 `UiDocument.__getTopLayerElements()` / `HtmlLikeDocumentWidget.resolveTopLayerLayoutBoxes()` 发现 detached entry
  时主动移除并记录布局失效。同时补 select 展开后移除/重挂载的生命周期回归测试。

## 补查未形成 finding 的范围

- 网络/远程桥接：`RemoteDocumentClientBridge`、`RemoteHudOverlayClientBridge`、`ConfigTemplateRemoteSyncController` 的异步完成回调均重新派发到 `UiScreenManager` 或 `NetService.runOnMainThread`；本轮未发现可证实 UI 线程越界。
- 网络生命周期：`NetService.onClientDisconnected()` / `shutdown()` 会失败 pending fetch/stream、清理 chunk assembler 与远端取消表；本轮未发现可证实断连泄漏。
- 字体与远程图片运行时：`GlyphGenerationDispatcher.reset()` 会暂停、代际隔离并关停 worker；`DocumentRemoteImageCache.shutdown()` 支持误关停后按需重建 executor；本轮未发现可证实线程池不可恢复问题。
