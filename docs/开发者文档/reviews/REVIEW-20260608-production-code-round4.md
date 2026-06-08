# 全项目生产代码第四轮审查

## 审查范围

- 目标：接续第三轮生产代码审查，只审查 `src/main/java`，不修改生产代码。
- 重点覆盖：第三轮新增 top-layer detach 兜底、HUD 输入与可见性生命周期、HTML-like 事件/命中/布局缓存、动态样式与动画运行态边界。
- 方法：先用 CodeGraph 和 grep 缩小调用链，再回到源码与现有测试逐条核实；结论以当前源码为准。
- 本轮未重复报告第三轮已修复的 4 个 findings；仅在发现修复不完整或新边界时单独列出。

## 结论摘要

- 发现 5 个可证实问题：4 个 P2、1 个 P3。
- P2 集中在 select top-layer 生命周期修复不完整、公开样式复制绕过缓存失效、transform 下 select popup 锚点错误、HUD 内部可见性隐藏可被后代样式绕过。
- P3 为 popup 关闭后 hover/cursor 运行态未立即刷新，属于可见交互状态残留。
- 本轮未修改生产代码，以下均为待修 findings。

## Findings

### P2：移除展开 select 子树后只清理 top-layer 注册，重挂会恢复旧打开状态

- 位置：`src/main/java/club/heiqi/uilib/ui/dom/DocumentNode.java:187`
- 位置：`src/main/java/club/heiqi/uilib/ui/dom/DocumentNode.java:278`
- 位置：`src/main/java/club/heiqi/uilib/ui/dom/DocumentNode.java:291`
- 位置：`src/main/java/club/heiqi/uilib/ui/dom/UiDocument.java:122`
- 位置：`src/main/java/club/heiqi/uilib/ui/control/DocumentSelectControl.java:356`
- 位置：`src/main/java/club/heiqi/uilib/ui/control/DocumentSelectControl.java:428`
- 位置：`src/main/java/club/heiqi/uilib/ui/control/DocumentSelectControl.java:472`
- 位置：`src/main/java/club/heiqi/uilib/ui/layout/DocumentLayoutEngine.java:472`
- 问题现象：第三轮修复已让 `removeChild` / `replaceChild` / `clearChildren` 调用 `UiDocument.__detachTopLayerDescendants(...)`，能把展开 select 的 popup 从 `topLayerElements` 移除。但该路径只移除文档 top-layer 注册，不会调用 `DocumentSelectControl.setOpen(false)` 或 `restorePopupInlinePlacement()`。因此控件内部 `open` 仍为 true，`aria-expanded` 仍为 `true`，popup inline style 仍保持 `display:flex`、`position:fixed`、旧 `left/top/width`。
- 影响：移除包含已展开 select 的子树后再挂回同一子树，popup 不再是 top-layer，但普通布局会看到它仍是 `display:flex` 且 `position:fixed` 的 select 子节点，于是可能在旧坐标重新显示。第三轮的 top-layer 引用泄漏已修，但控件打开状态和 popup 样式状态仍泄漏，可能造成重挂载后弹层意外可见、ARIA 状态错误和后续点击语义混乱。
- 可复现路径：创建 `DocumentSelectControl` 并挂到文档；点击触发展开；对其祖先执行 `root.removeChild(shell)`；再执行 `root.append(shell)` 并渲染。预期重挂后 select 关闭且 popup `display:none`；当前仅 `document.__isTopLayerElement(popup)` 变为 false，`aria-expanded` 和 popup fixed/display 状态仍保持打开态。
- 建议修复方向：DOM detach 清理 top-layer descendants 时不能只操作注册表。对于内置 select popup，应进入控件级关闭路径，统一重置 `open`、`aria-expanded`、popup `display/position/z-index/left/top/width` 和相关 hover/focus 视觉状态；或为 top-layer owner 增加内部 detach callback，由控件处理自身生命周期。

### P2：`UiStyleDeclaration.copyFrom(...)` 公开样式复制绕过文档失效版本

- 位置：`src/main/java/club/heiqi/uilib/ui/dom/ElementNode.java:49`
- 位置：`src/main/java/club/heiqi/uilib/ui/dom/ElementNode.java:97`
- 位置：`src/main/java/club/heiqi/uilib/ui/style/cascade/UiStyleDeclaration.java:2374`
- 位置：`src/main/java/club/heiqi/uilib/ui/style/cascade/UiStyleDeclaration.java:2446`
- 位置：`src/main/java/club/heiqi/uilib/ui/style/cascade/UiStyleDeclaration.java:2467`
- 位置：`src/main/java/club/heiqi/uilib/ui/document/HtmlLikeDocumentWidget.java:882`
- 问题现象：普通 `element.style().setWidth(...)` 等 setter 会经 `ElementNode` 的 `UiStyleChangeListener` 递增 layout/paint 版本，但 `UiStyleDeclaration.copyFrom(...)` 是 public 方法，直接替换所有字段和 `declaredValues`，并在 Javadoc 中明确“不触发变更回调”。`ElementNode.cloneNode(...)` 依赖这个静默行为复制 detached clone 的样式，但同一个 public 方法也可被页面作者直接用于已挂载元素。
- 影响：对已挂载元素执行 `element.style().copyFrom(otherDeclaration)` 后，`UiDocument.layoutVersion` / `paintVersion` 不变。`HtmlLikeDocumentWidget.resolveLayoutBox(...)` 会继续按旧版本复用 `cachedLayoutBox`，paint-only 复制也不会刷新 `cachedPaintCommands`，导致运行时主题切换、样式模板复用或批量样式拷贝显示旧布局/旧绘制，直到发生其他 DOM 或样式 setter 变更才偶然刷新。
- 可复现路径：渲染一个宽度 20 的元素并建立布局缓存；构造 `UiStyleDeclaration` 设置宽度 200 或背景色；调用已挂载元素的 `style().copyFrom(...)`；不做其他变更直接请求布局或绘制。预期立即使用新声明；当前版本号未变，布局/绘制缓存可继续返回旧结果。
- 建议修复方向：拆分“公开会失效的 copy”和“内部静默 clone copy”。例如让 public `copyFrom(...)` 比较前后声明并按最大影响触发 `recordChange(...)`，再给 `ElementNode.cloneNode(...)` 使用包内 `copyFromSilently(...)`；如果该方法确实只打算内部使用，应收紧可见性并补公开批量样式替换 API。

### P2：transform 下 select top-layer popup 使用未变换布局坐标锚定

- 位置：`src/main/java/club/heiqi/uilib/ui/control/DocumentSelectControl.java:467`
- 位置：`src/main/java/club/heiqi/uilib/ui/document/HtmlLikeDocumentWidget.java:500`
- 位置：`src/main/java/club/heiqi/uilib/ui/document/HtmlLikeDocumentWidget.java:1009`
- 位置：`src/main/java/club/heiqi/uilib/ui/layout/DocumentHitTestEngine.java:128`
- 位置：`src/main/java/club/heiqi/uilib/ui/layout/DocumentHitTestEngine.java:371`
- 位置：`src/main/java/club/heiqi/uilib/ui/paint/DocumentPaintEngine.java:165`
- 问题现象：select 展开时，`DocumentSelectControl.syncPopupTopLayerPlacement()` 通过 `element.getDocumentBounds()` 设置 popup 的 fixed `left/top/width`；随后 `HtmlLikeDocumentWidget.syncSelectTopLayerPlacement(...)` 也用 `DocumentVisualTraversal.findBoxLocation(...)` 得到的布局盒坐标持续重算 popup 锚点。paint 和 hit-test 对 `transform` / transform 动画是在绘制与命中阶段单独应用的，`getDocumentBounds()` 与 `findBoxLocation(...)` 返回的是未经过 transform 的布局坐标。
- 影响：当 select 位于 `transform: translate(...)`、scale、rotate 或 paint-only transform 动画的祖先内时，触发器会按变换后的视觉位置绘制并命中，但 top-layer popup 会打开在未变换的旧布局位置。用户看到弹层脱离触发器，甚至可能在 HUD/远程页面中点击到错误区域。
- 可复现路径：把 `DocumentSelectControl` 放入带 `style().setTransform(UiTransform.translate(80, 40))` 的容器；按变换后的视觉坐标点击触发器；popup 展开后观察其左上角。预期出现在视觉触发器下方；当前会按未变换布局坐标定位。
- 建议修复方向：select popup 锚点应使用包含 paint transform 和当前 animation runtime 的视觉 bounds，而不是原始 layout bounds。可扩展 `DocumentElementBounds`/widget 内部 API 返回 transformed anchor rect，或在 `HtmlLikeDocumentWidget.syncSelectTopLayerPlacement(...)` 中按当前 visual scene 计算变换后的 anchor 几何后再写入 top-layer fixed 坐标。

### P2：HUD 用 `visibility:hidden` 隐藏注册项，显式 visible 后代可在其他层触发渲染时泄漏

- 位置：`src/main/java/club/heiqi/uilib/ui/hud/UiHudDocumentHost.java:658`
- 位置：`src/main/java/club/heiqi/uilib/ui/hud/UiHudDocumentHost.java:1214`
- 位置：`src/main/java/club/heiqi/uilib/ui/hud/UiHudDocumentHost.java:1228`
- 位置：`src/main/java/club/heiqi/uilib/ui/style/UiStyleProperty.java:59`
- 位置：`src/main/java/club/heiqi/uilib/ui/paint/DocumentPaintEngine.java:161`
- 问题现象：HUD 注册项按屏幕类型可见性切换时，`HudEntry.setHostVisible(false)` 只给内部 `hostShell` 写 `visibility:hidden`。该属性在当前样式系统中是继承属性，并且绘制引擎明确实现了 CSS 语义：hidden 祖先隐藏自身，但允许显式 `visibility:visible` 后代恢复绘制。`renderVisibleLayers(...)` 只要当前屏幕存在任意可见 entry 就会渲染整个 shared widget，因此 passive entry 在容器界面被隐藏时，如果同时有 interactive entry 使 shared scene 继续渲染，passive 子树里显式 visible 的后代仍可能被绘制出来。
- 影响：被产品语义要求“容器界面隐藏”的 passive HUD 内容可能泄漏到背包/箱子等容器界面。因为 `hostShell` 是宿主内部壳，这不是普通作者 CSS 语义选择，而是宿主隐藏机制被后代样式绕过。
- 可复现路径：同时注册一个 passive HUD 和一个 interactive HUD；passive mountRoot 下添加子元素并显式 `style().setVisibility(UiVisibility.VISIBLE)`；切到容器界面。预期 passive entry 完全不可见，仅 interactive entry 绘制；当前 shared widget 会因 interactive entry 可见而渲染，passive hidden hostShell 的 visible 后代可恢复绘制。
- 建议修复方向：宿主级可见性不应依赖可被 CSS 子代恢复的 `visibility:hidden`。可改为 `display:none`、内部 paint/hit-test subtree suppression 标记，或在 visual traversal/paint 阶段识别 `data-qz-hud-host-shell` 的 host visibility 并整棵子树跳过，确保作者样式不能覆盖宿主生命周期隐藏。

### P3：select popup 关闭后不立即刷新 hover/cursor，隐藏 option 可保留交互状态到下一次鼠标移动

- 位置：`src/main/java/club/heiqi/uilib/ui/control/DocumentSelectControl.java:256`
- 位置：`src/main/java/club/heiqi/uilib/ui/control/DocumentSelectControl.java:314`
- 位置：`src/main/java/club/heiqi/uilib/ui/control/DocumentSelectControl.java:356`
- 位置：`src/main/java/club/heiqi/uilib/ui/document/HtmlLikeDocumentWidget.java:700`
- 位置：`src/main/java/club/heiqi/uilib/ui/document/HtmlLikeDocumentWidget.java:1108`
- 位置：`src/main/java/club/heiqi/uilib/ui/document/DocumentCursorResolver.java:43`
- 问题现象：鼠标位于 popup option 上时，`HtmlLikeDocumentWidget.hoveredElement` 会记录 option。点击 option 后，`onMouseUp(...)` 在 dispatch click 之前已经完成 hover/cursor 同步；option click handler 随后执行 `setOpen(false)` 隐藏 popup，但没有让 widget 按当前鼠标位置重新 hit-test。键盘 `Esc` 关闭 popup 也是同样路径。`DocumentCursorResolver` 只校验 hoveredElement 是否仍 attached，不校验 display/visibility，因此已隐藏但仍 attached 的 option 仍可驱动 pointer cursor 和祖先 `:hover` 状态，直到下一次 mouse move/leave/scroll 刷新。
- 影响：用户选择 option 或按 Esc 关闭弹层后，如果鼠标停在原 popup 区域，select hover 边框、作者侧 hover handler 状态或系统光标可能短时间保持为 popup/option 的交互状态。这不破坏数据，但会造成 HUD/表单控件视觉状态与实际命中不一致。
- 可复现路径：展开 select；移动鼠标到第二个 option；点击 option 或按 Esc 关闭；不移动鼠标直接观察 cursor/hover 视觉。预期关闭后立即按当前坐标重新命中底层元素或空白；当前保留上一次 option hover/cursor 到下一次鼠标事件。
- 建议修复方向：任何 top-layer popup 从 open 变为 closed 后，应触发一次基于最新 pointer 的 hover/cursor 刷新。可在 widget 事件路由结束时检测 layout/top-layer 状态变化后 `updateHoveredElement(findElementAt(latestX, latestY), syntheticEvent)`，或给控件提供内部 runtime hook 通知宿主刷新当前 hover。

## 补查未形成 finding 的范围

- 第三轮新增 `UiStyleSheet` / `UiStyleRule` / `UiStyleDeclaration` listener 链路已覆盖 mounted stylesheet 的 `addRule`、`clear` 和规则 declaration setter 变更；本轮未发现重复注册导致的多次失效或 remove 后继续通知问题。
- 第三轮 `textInput.preventDefault()` 修复已在 input/textarea 内置 target handler 前检查 `isDefaultPrevented()`；本轮未重复报告该问题。
- 第三轮 HUD top-layer 后代预过滤修复已让 `findElementAtWithin(...)` 对完整 visual scene hit-test 后按 DOM 父链过滤；本轮未发现同一修复点回退。
