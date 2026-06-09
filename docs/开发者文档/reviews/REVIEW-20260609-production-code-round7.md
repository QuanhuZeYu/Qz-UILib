# 全项目生产代码第七轮审查

## 审查范围

- 目标：接续第六轮修复后，评估剩余 animation runtime cache、layout / paint / hit-test、HUD / top-layer / scroll 与 remote UI session 边界。
- 方法：只读审查 `src/main/java` 生产代码与既有测试，不修改源码；先排除第三至第六轮已修 findings，再聚焦尚未覆盖的交互边界。
- 已排除重复项：`textInput.preventDefault()`、HUD top-layer 后代预过滤、mounted stylesheet 失效、select detach/top-layer 状态、`UiStyleDeclaration.copyFrom(...)`、select transform popup anchor、HUD `display:none` 隐藏、popup hover/cursor 刷新、远程 session 旧 dismiss / 旧 stream / TTL / 手动关闭 expired、keyframe forwards fill direction、transform 后滚轮与滚动条 inverse transform、`display:none` transitioncancel。

## 结论摘要

- 发现 2 个可证实问题：1 个 P1、1 个 P2。
- P1：运行态 transform 已参与 paint / hit-test，但 layout、fixed containing block、clip chain 与 scroll metrics 仍只看静态 computed transform，导致 transform 动画中 fixed 后代位置、裁剪和命中可能分叉。
- P2：默认滚动和滚动条命中路径未复用 hit-test suppression / `pointer-events:none` 口径，passthrough 或 pointer-events none 的 scrollable overlay 仍可能吃掉 wheel 或滚动条拖拽。
- remote UI session 本轮未形成新 finding：固定 10 分钟 TTL、不做隐式续期已是当前文档与决策口径；未来长驻 UI 需要单独 keepalive/renew 协议。
- 后续复核（2026-06-09）：2 个 findings 已完成修复。运行态 transform 现在统一参与 fixed containing block、clip、hit-test、paint 与 scroll metrics 口径；默认滚动和滚动条拖拽已复用 hit-test suppression / `pointer-events:none` 命中语义。

## Findings

### P1：运行态 transform 不参与 fixed containing block / clip / scroll metrics 判定

- 位置：`src/main/java/club/heiqi/uilib/ui/animation/DocumentAnimationProperty.java:47`
- 位置：`src/main/java/club/heiqi/uilib/ui/layout/DocumentLayoutEngine.java:303`
- 位置：`src/main/java/club/heiqi/uilib/ui/layout/DocumentEffectChain.java:187`
- 位置：`src/main/java/club/heiqi/uilib/ui/layout/DocumentVisualTraversal.java:157`
- 位置：`src/main/java/club/heiqi/uilib/ui/layout/DocumentScrollMetricsCalculator.java:57`
- 位置：`src/main/java/club/heiqi/uilib/ui/paint/DocumentPaintEngine.java:165`
- 问题现象：transform 子属性在 `DocumentAnimationProperty` 中被归为 paint-only。绘制、视觉 bounds 与 hit-test 会通过 `animationTimeline.resolveFloat(...)` 读取 runtime transform，但 `DocumentLayoutEngine`、`DocumentEffectChain`、`DocumentVisualTraversal` 和 `DocumentScrollMetricsCalculator` 仍只按当前 computed transform 判断是否建立 fixed containing block、是否保留祖先 clip chain、以及 fixed 后代是否参与滚动范围。
- 影响：当容器从非 identity transform transition 到 identity 时，中间帧 runtime transform 仍非 identity，但 computed transform 已是 identity；当 base identity 通过 keyframe / imperative animation 动到非 identity 时，也只有 runtime transform 非 identity。两类路径都会让 fixed 后代按 viewport fixed 布局或清空祖先 clip，而 paint / hit-test 又套用 runtime transform，导致 fixed 后代绘制位置、命中位置、overflow clip 和 scroll range 不一致。
- 可复现路径：创建 `overflow:hidden` 容器，内含 `position:fixed` 子元素；容器初始 `transform:translateX(40px)` 并声明 `transition: translate-x 1000ms`；首帧后把 transform 改为 identity；在 500ms 渲染或命中。预期 fixed 子元素仍相对运行态 transform 祖先并受容器 clip 约束；当前风险是它按 viewport fixed 参与 layout / visual traversal。
- 建议修复方向：不要只把 runtime transform 作为 paint-only 几何。至少在视觉遍历、命中、滚动范围和含 fixed 后代的 layout runtime 路径中统一使用“运行态 transform 是否建立 fixed containing block”的判定；同时评估含 fixed 后代的 transform 动画是否需要 runtime layout cache 失效或专门的 runtime visual context。
- 建议测试：补 `DocumentVisualTraversalTest`、`DocumentHitTestEngineTest`、`DocumentScrollStateTest` 或 `HtmlLikeDocumentWidgetAnimationRuntimeTest`，覆盖 runtime transform ancestor + fixed descendant + overflow hidden 的中间帧位置、clip、命中和 scroll range。
- 后续复核（2026-06-09）：已修复。新增 `DocumentRuntimeTransforms` 统一解析运行态 transform，`DocumentLayoutEngine` 支持按运行态 transform 判定 fixed containing block，`DocumentVisualTraversal` / `DocumentPaintEngine` / `DocumentHitTestEngine` / `DocumentScrollMetricsCalculator` / `DocumentScrollState` 使用同一运行态 visual scene 与 scroll metrics 口径；`HtmlLikeDocumentWidget` 在存在 transform runtime value 时走 runtime layout，避免 fixed 后代布局与 paint-only transform 分叉。

### P2：默认滚动和滚动条拖拽未尊重 hit-test passthrough / pointer-events:none

- 位置：`src/main/java/club/heiqi/uilib/ui/document/HtmlLikeDocumentWidget.java:653`
- 位置：`src/main/java/club/heiqi/uilib/ui/document/HtmlLikeDocumentWidget.java:680`
- 位置：`src/main/java/club/heiqi/uilib/ui/layout/DocumentHitTestEngine.java:266`
- 位置：`src/main/java/club/heiqi/uilib/ui/layout/DocumentScrollState.java:348`
- 位置：`src/main/java/club/heiqi/uilib/ui/layout/DocumentScrollState.java:594`
- 位置：`src/main/java/club/heiqi/uilib/ui/layout/DocumentScrollState.java:744`
- 问题现象：`HtmlLikeDocumentWidget.onMouseScroll()` 先用 `findElementAt(...)` 分发 wheel DOM-like 事件，该路径会过滤 `data-hit-test-hidden`、`data-hit-test-passthrough` 和 `pointer-events:none`；但随后默认滚动进入 `DocumentScrollState.handleWheel(...)`，滚动状态自己按视觉树寻找最深可滚盒，没有等价过滤。`onMouseDown(...)` 还会先调用 `beginScrollbarDrag(...)`，滚动条命中同样不检查这些 hit-test 抑制语义。
- 影响：底层有可滚动内容，上层覆盖 scrollable overlay 并设置 `data-hit-test-passthrough="true"` 或 `pointer-events:none` 时，click / hover / wheel event target 会透传到底层，但默认 wheel 或滚动条拖拽仍可能命中上层 overlay。HUD / top-layer 中会表现为“点击穿透但滚轮或滚动条不穿透”。
- 可复现路径：在同一文档或 HUD mountRoot 内放置底层 scroller；其上叠一个高 z-index、`overflow:auto` 且有内容溢出的 overlay，并设置 `data-hit-test-passthrough="true"` 或 `pointer-events:none`；鼠标位于 overlay 视觉区域滚轮或点滚动条。预期滚动底层或不消费；当前风险是滚动 / 拖拽 overlay。
- 建议修复方向：`DocumentScrollState` 的 wheel / scrollbar hit 路径应复用 `DocumentHitTestEngine` 的 suppression / pointer-events 口径，或抽公共可见命中策略，确保默认滚动目标与作者可交互命中目标一致。
- 建议测试：补 `DocumentScrollStateTest` 与 `HtmlLikeDocumentWidgetScrollTest`，覆盖 passthrough、hidden、pointer-events none overlay 不应成为默认滚动目标，也不应被 `beginScrollbarDrag()` 捕获。
- 后续复核（2026-06-09）：已修复。`DocumentScrollState` 的默认 wheel 目标选择和 scrollbar hit-drag 会跳过 `data-hit-test-hidden`、`data-hit-test-passthrough` 与 `pointer-events:none` 节点，widget 级 wheel / scrollbar 行为与 `DocumentHitTestEngine` 的作者命中语义保持一致。

## 补查未形成 finding 的范围

- remote UI：`RemoteHtmlSessionGateway`、`RemoteDocumentPages`、`RemoteDocumentClientBridge`、`RemoteHudOverlays`、`RemoteHudOverlayClientBridge` 与使用文档/TTL 决策一致。固定 10 分钟 TTL 同时覆盖 HTML 拉取与后续提交；页面关闭只清客户端当前 session 归属；HUD 关闭/过期继续保持非空 session 精确匹配。
- 现有测试已覆盖静态 transform fixed containing block、静态 transform 滚动命中、wheel preventDefault、远程页面/HUD 旧 session 与 TTL 过期路径；未覆盖本轮两个 runtime / hit-test suppression 组合边界。

## 验证

- 原审查阶段为只读审查，未修改生产代码，未运行 Gradle 测试。
- 后续修复验证（2026-06-09）已通过：`./gradlew.bat --no-configuration-cache test --tests "club.heiqi.uilib.ui.layout.DocumentVisualTraversalTest"`。
- 后续修复验证（2026-06-09）已通过：`./gradlew.bat --no-configuration-cache test --tests "club.heiqi.uilib.ui.layout.DocumentHitTestEngineTest"`。
- 后续修复验证（2026-06-09）已通过：`./gradlew.bat --no-configuration-cache test --tests "club.heiqi.uilib.ui.layout.DocumentScrollStateTest"`。
- 后续修复验证（2026-06-09）已通过：`./gradlew.bat --no-configuration-cache test --tests "club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetScrollTest"`。
- 后续修复验证（2026-06-09）已通过：`./gradlew.bat --no-configuration-cache test --tests "club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetAnimationRuntimeTest"`。
- 后续修复验证（2026-06-09）已通过：`./gradlew.bat --no-configuration-cache compileJava`。
- 后续修复验证（2026-06-09）已通过：`git diff --check`。
