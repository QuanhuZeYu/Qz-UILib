# Scene Overlay P0 施工清单

> **状态**：本计划已落地完成，overlay P0 地基（`ui.scene.overlay` 包 + `portal`/`portalAnchored` + dismiss + 保护节点机制）已合回 4.0。本文档转为施工记录留存，正文施工细节保留作为历史参考，时态词按"已落地"理解。

本文固化 scene 通用 `top-layer/overlay` 地基 P0 的文件级施工计划。它不是 `SceneSelect` 私有方案；`SceneSelect` 只是第一个消费者和验收用例。

## 目标

- 新增平台无关的 scene overlay 地基：portal 注册、stacking、独立绘制 root、浮层优先命中、anchor 定位、dismiss 和 Owner 清理。
- 让浮层可跨主树 `clipChildren` / `scrollable` 绘制并优先命中。
- 保持 `NORTH_STAR.md` I1/I2/I6/I7/I10/I11：handler 只写 signal，渲染层不读 signal/组件，scene 核心无平台 import，hit-test 只读。
- 以 `SceneSelect` 验证 P0，但不把任何地基能力特化到 `SceneSelect`。

## 关键源码事实

- `SceneHostWidget.render(...)` 当前是单 root pipeline：`layout(root)` → `route(root)` → `flush` → `layout(root)` → `paint(root)` → `replay(plan)`。
- `SceneInputRouter.route(...)` 当前只认单 root；hover/focus/pressed/captured 的权威状态机都在同一个 router 内，overlay 必须接入同一 router。
- `SceneHitTester.hitTest(...)` 无状态，可对 overlay root 独立调用；当前命中受祖先 bounds 限制。
- `ScenePaintEngine.paint(root)` 可对任意 root 产出 `PaintPlan`；`ScenePaintReplayer.replay(plan, ctx, offsetX, offsetY)` 可多次顺序调用叠加绘制。
- `SceneLayoutEngine` 的 `lastRootConstraints` 是实例字段，overlay root 不应复用主树同一个 layout engine 实例，否则约束变化判断会串味。
- `Owner.onCleanup(...)` 已是 runtime handler/effect 清理模式，overlay 注册必须复用该生命周期。

## P0 新增文件

### `src/main/java/club/heiqi/uilib/ui/scene/overlay/SceneOverlayHost.java`

- **目的**：维护 active overlay roots 的有序栈，尾部为栈顶。
- **内容**：注册 overlay entry、摘除 entry、按 bottom-first 供绘制、按 top-first 供命中。
- **约束**：只保存 scene 数据对象，不引用平台、渲染上下文或旧 DOM。
- **风险**：强引用 root 和回调，必须由 `OverlayHandle.dispose()` 与 Owner cleanup 保证摘除。

### `src/main/java/club/heiqi/uilib/ui/scene/overlay/OverlayHandle.java`

- **目的**：表示一次 overlay 注册，提供幂等 `dispose()`。
- **内容**：持有 host 与 entry 引用；dispose 后从 host 移除。
- **约束**：不直接 dispose overlay 子树 Owner，避免双重清理；Owner 由 portal 作用域管理。

### `src/main/java/club/heiqi/uilib/ui/scene/overlay/SceneAnchorResolver.java`

- **目的**：纯函数计算浮层位置与尺寸约束。
- **P0 行为**：向下展开、左边对齐 anchor、宽度对齐 trigger、限高到 host 剩余空间。
- **暂缓**：flip、右对齐、四象限 collision、指针避让。

### `src/main/java/club/heiqi/uilib/ui/scene/overlay/OverlayDismissPolicy.java`

- **目的**：声明 ESC、外部点击、选中关闭等策略。
- **约束**：policy 只描述规则，不执行节点摘除；实际关闭必须通过 `expanded.set(false)` 这类 signal 写入。

### `src/main/java/club/heiqi/uilib/ui/scene/overlay/package-info.java`

- **目的**：记录 overlay 包契约。
- **必须写明**：overlay 是数据层额外 root 列表；显隐经 signal→portal 派生；dismiss 只触发 signal 写入；包内禁止平台 import。

## P0 修改文件

### `src/main/java/club/heiqi/uilib/ui/scene/component/SceneRuntime.java`

- 增加 `SceneOverlayHost` 字段，与 `SceneInputRouter` 同生命周期。
- 新增 portal 薄委托，语义接近 `show(...)`，但挂载目标是 overlay host 而非主树 parent。
- portal effect 只订阅 visible signal；内容构建与卸载包在 untrack 内，避免 overlay 内部 signal 回流成 visible 依赖。
- 在当前 Owner 下登记 cleanup；组件卸载或 runtime dispose 时自动移除 overlay entry 与内部 handler/effect。
- 暴露测试探针或包级 accessor 给 host/router 使用；不要把 active overlay list 暴露给业务作者。

### `src/main/java/club/heiqi/uilib/ui/scene/input/SceneInputRouter.java`

- 增加 overlay 优先 hit-test：每个 pointer event 先按 top-first 命中 overlay roots，未命中再走主树。
- overlay 命中后仍使用同一套 target+bubble、hover/focus/pressed/captured 状态机。
- 外部点击关闭：POINTER_DOWN 未命中指定 overlay 时触发 entry 的 dismiss request；dismiss request 只能写 signal，不得摘节点。
- ESC 关闭：KEY_DOWN ESC 优先关闭栈顶可 ESC dismiss 的 overlay，并阻止继续传播。
- 保持 route 零标脏：不得在 router 中调用 `SceneNode` setter、mark、append/remove。

### `src/main/java/club/heiqi/uilib/internal/devtools/pages/SceneHostWidget.java`

- 在 pipeline 中接入 overlay roots：主树 layout 后计算 anchor，route 前确保 overlay root 有可命中布局。
- 主树 replay 后按 bottom-first 顺序 paint/replay overlay roots，使后开浮层覆盖旧浮层与主树。
- overlay root 使用独立 layout engine 或 per-root layout engine，避免 `SceneLayoutEngine.lastRootConstraints` 与主树串味。
- replay overlay plan 时使用 anchor 绝对坐标作为 `offsetX/offsetY`，复用现有 replayer offset 叠加能力。

### `src/main/java/club/heiqi/uilib/ui/scene/control/package-info.java`

- 新增 R11：浮层显隐必须经 signal→portal 派生，禁止 handler 命令式提升或摘除节点。
- 明确带浮层控件的 handler 只允许写 signal、调用 props 回调或使用 EventContext 受控命令。

### `src/test/java/club/heiqi/uilib/ui/scene/input/ScenePackageIsolationTest.java`

- 把 `ui.scene.overlay` 纳入平台 import 与渲染实现引用扫描。
- 断言 overlay 包不得引用 `org.lwjgl`、`net.minecraft`、`net.minecraftforge`、具体 `UiRenderContext`、`ui.text.*`。

## SceneSelect 首个消费者

新增 `src/main/java/club/heiqi/uilib/ui/scene/control/SceneSelect.java`，必须排在 overlay P0 地基之后。

- Props 使用外部 `selectedIndex` 作为唯一真值，`onSelect` 上抛期望值。
- 内部只允许 `expanded`、`highlightedIndex` 等 UI 态 signal。
- trigger 留在主树；listbox 经 portal 提升为 overlay root。
- listbox 限高，内部复用 `scrollable` 与 `scrollOffsetY`。
- 点击 trigger 展开/收起；点击选项 `onSelect` 后关闭；外部点击与 ESC 关闭。
- P0 键盘只做方向键、Enter/Space、ESC；Home/End、typeahead、PageUp/PageDown 暂缓。

## 施工顺序记录

以下为 P0 实际施工顺序记录，已全部完成：

1. 扩展 `ScenePackageIsolationTest`，先把 overlay 包纳入红线扫描。
2. 新增 overlay 纯数据类与纯函数：`OverlayDismissPolicy`、`SceneAnchorResolver`、`OverlayHandle`、`SceneOverlayHost`。
3. 为 overlay 纯数据类补测试：注册/摘除/栈序、anchor 向下展开与限高。
4. 接入 `SceneRuntime.portal(...)`，补 visible 切换、Owner cleanup、runtime dispose 清理测试。
5. 接入 `SceneInputRouter` overlay 优先命中，先测命中覆盖，再测外部点击和 ESC dismiss。
6. 接入 host pipeline 多 root layout/paint/replay，补跨 clip 可见、overlay 覆盖主树、主树稳定兄弟不重排测试。
7. 新增 `SceneSelect`，补受控零状态、鼠标、键盘、滚轮和关闭行为测试。
8. 新增或扩展 demo 页面，只验证 top-layer 与 `SceneSelect` P0，不接真实 modern config 字段引擎。

## P0 测试清单

- `SceneAnchorResolverTest`：向下展开、宽度对齐、限高；明确不做 flip。
- `SceneOverlayHostTest`：注册、摘除、幂等 dispose、栈序 top-first/bottom-first。
- `SceneOverlayPortalTest`：visible true/false 挂卸 overlay；Owner dispose 和 runtime dispose 清理。
- `SceneOverlayHitTestTest`：overlay 覆盖主树节点时优先命中 overlay；overlay 外仍命中主树。
- `SceneOverlayDismissTest`：外部点击、ESC、选中请求均只触发 dismiss signal。
- `SceneOverlayPipelineTest`：overlay 在 scrollable/clip 容器触发的 anchor 上仍跨 clip 可见；主树稳定兄弟 `relayoutCount==0`，overlay 滚动 `regeneratedFragmentCount==0`。
- `SceneSelectTest`：受控零状态、点击选项、键盘选择、ESC/外部关闭、listbox 限高滚动。
- `ScenePackageIsolationTest`：overlay 包隔离红线。

## P0 不做

- placement flip、四象限 collision、指针避让。
- focus trap、focus return、Dialog 模态遮罩。
- typeahead、PageUp/PageDown、复杂键盘导航。
- 选项虚拟化；超大列表后续做独立 `SceneVirtualList`。
- Tooltip/ContextMenu/ColorPicker/DatePicker/Dialog 具体控件。
- 修改 `UiSurface` 对外接口；P0 应在 host 内部接入。

## 不变量守线

- **I1/I11**：handler 只写 `expanded`、`highlightedIndex`、`scrollOffsetY` 或调用 `onSelect`；router dismiss 只调 dismiss request，实际关闭由 signal 驱动。
- **I2/I9**：所有 overlay 显隐、选中和滚动写入仍经 `Signal.set`，由帧末 flush 合并。
- **I6**：top-layer 是数据层维护的额外 root 列表；渲染层只顺序消费 `PaintPlan`，不认识 signal、组件或 SceneNode。
- **I7/I8**：overlay hit-test 只读；overlay root 独立 layout/paint，主树稳定兄弟不得被污染；滚动继续走几何级 offset。
- **I10**：overlay 包纳入包隔离测试，无平台 import。

## 文档同步

以下为 P0 落地时需同步的文档清单，按当前状态标注：

- `docs/记忆/决策/DECISION-20260623-scene-overlay-foundation.md`：指向本文作为 P0 文件级施工清单。**已完成**。
- `docs/记忆/当前态/当前计划.md`：下一步保持"先按本文施工 overlay P0"。**已完成**（P0 已落地，当前计划已推进至后续阶段）。
- `docs/记忆/长期事实/架构边界.md`：P0 落地后再把"尚未落地"改为"已具备 P0 top-layer"。**已同步**（主 Agent 已在本次 docs 文档清理中修订架构边界.md）。
- `src/main/java/club/heiqi/uilib/ui/scene/control/package-info.java`：实现时补 R11。**已完成**。
- `NORTH_STAR.md`：是否把 top-layer 写入正文或偏离登记涉及宪章边界，必须先经用户确认。**待用户确认**（宪章边界变更未在本 P0 内推进）。

## 一句话总纲

在 scene 核心新增平台无关的 overlay root 栈，由 `SceneRuntime.portal` 以 signal 驱动挂卸并经 Owner 自动清理；Router 先命中 overlay 再命中主树，host 在主树之后 layout/paint/replay overlay roots，`SceneSelect` 只作为首个消费者验收跨 clip、优先命中、dismiss 和
I7 不污染主树。
