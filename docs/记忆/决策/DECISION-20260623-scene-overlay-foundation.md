# 决策：Scene 浮层控件地基按通用 top-layer 建设

## 背景

现代配置页需要 `CHOICE/Select`，但 `Select` 暴露的不是单控件缺口，而是 scene 新栈缺少通用浮层控件地基。后续常用控件如 Dropdown、Autocomplete、Tooltip、ContextMenu、ColorPicker、DatePicker、Dialog、Popover 都会需要同一类能力：脱离原父链绘制、覆盖其它内容、跨裁剪显示、优先命中、锚点定位、外部点击关闭和生命周期清理。

当前 scene 主树的命中、裁剪和绘制顺序都绑定在父子树拓扑内：子树命中依赖祖先 bounds，绘制 clip 沿树向下作用，z-order 由 DFS 与兄弟尾序决定。inline listbox 能绕过 `Select` 的部分视觉问题，但无法解决这类控件共同需要的拓扑能力。

## 候选方案

1. **为 SceneSelect 特化浮层**：只让 Select 有独立绘制和命中逻辑。
2. **用 inline listbox 暂时绕过**：列表参与布局流，展开时推开后续内容。
3. **建设通用 top-layer/overlay 地基**：新增通用浮层提升、独立绘制、独立命中、锚点定位、dismiss 和生命周期能力，`SceneSelect` 作为首个消费者。

## 最终选择

采用方案 3：建设通用 top-layer/overlay 地基，`SceneSelect` 只是首个落点和验收用例。

inline listbox 不作为正式路线，只允许作为 top-layer 施工前的临时探针或降级 fallback。禁止把浮层能力做成 `SceneSelect` 私有机制，因为同一能力会被 Dropdown、Autocomplete、Tooltip、ContextMenu、ColorPicker、DatePicker、Dialog 和 Popover 复用。

## 选择原因

- **问题是拓扑缺口**：浮层要脱离原父链、跨 clip、盖住其它内容并优先命中，这不是 `Select` 的局部 UI 细节。
- **旧栈经验已验证**：旧 DOM top-layer 本质上是把浮层作为独立 layout/paint root 处理，并在主树之后绘制，再配合锚点重定位。
- **inline 不可推广**：Tooltip、ContextMenu、Autocomplete、Dialog、Popover 等都不能用参与布局流的 inline 展开表达。
- **不变量可守住**：top-layer 可建成“数据层维护额外 root 列表 + paint 多 root 消费 + hit-test 多 root 优先入口”，渲染层不需要读取 signal/组件状态。

## 通用地基能力

P0 必须覆盖：

- **top-layer/portal 注册**：数据层维护 active overlay roots，组件卸载时自动摘除。
- **overlay stacking**：后打开浮层位于更高层，命中也按栈顶优先。
- **独立绘制 root**：主树绘制后再绘制 top-layer root，避免被主树祖先 clip 裁剪。
- **浮层优先 hit-test**：指针事件先命中 top-layer roots，未命中再走主树。
- **anchor 定位**：从 trigger 的 layout box 计算绝对锚点，P0 先支持向下展开、宽度对齐 trigger、限高滚动。
- **dismiss policy**：支持选中关闭、ESC 关闭、外部点击关闭。
- **Owner 生命周期清理**：组件卸载、条件隐藏或宿主关闭时自动移除 overlay 与相关 handler。

P1 再补：

- placement flip：下方空间不足时向上展开。
- scroll/resize reposition：anchor 所在滚动容器或宿主尺寸变化时重新定位。
- 键盘导航完善：Home/End、highlightedIndex 与 selectedIndex 分离、栈顶浮层键盘优先。
- 多浮层 stacking 策略：后开在上、关闭时弹栈、嵌套浮层顺序明确。

P2 暂缓：

- focus trap 与 focus return，等 Dialog/Popover 真实进入时再补。
- Tooltip 四象限 collision 与指针避让。
- typeahead、PageUp/PageDown。
- 超大选项虚拟化；真出现上千选项时再做独立 `SceneVirtualList`，不塞进 top-layer 地基。
- aria-like 语义状态；当前 MC GUI 无对应可访问性消费端，不移植旧 DOM 的 aria 属性。

## 内部 API 边界

建议新增 `club.heiqi.uilib.ui.scene.overlay` 内部包，承载浮层地基：

- `SceneOverlayHost`：维护 active overlay roots，提供注册/摘除能力。
- `OverlayHandle`：表示一次浮层提升，`dispose()` 后移除并触发生命周期清理。
- `SceneAnchorResolver`：纯函数，根据 anchor box、overlay size、host size 和 placement 计算浮层位置。
- `OverlayDismissPolicy`：内部策略枚举，描述 ESC、外部点击、失焦等关闭规则。

`SceneRuntime` 只暴露薄委托，避免业务作者直接操作 top-layer 列表。控件作者也不应拿到 `activeOverlays()` 或手动调整 z-order；浮层显隐必须由 signal 派生。

## Select 首刀

`SceneSelect` 是 top-layer 地基的第一个消费者和验收控件。

- **数据模型**：外部 `selectedIndex` 仍是唯一真值；`onSelect` 上抛期望值；内部只允许 `expanded`、`highlightedIndex` 这类 UI 态 signal。
- **结构**：trigger 保持在主树；listbox 经 top-layer 提升为浮层 root；列表限高并复用 scrollable；选项用 keyed children。
- **鼠标**：点击 trigger 切换展开；点击选项上抛 `onSelect` 并关闭；点击外部关闭。
- **键盘**：焦点保持在 trigger；方向键移动 highlighted；Enter/Space 选择或展开；ESC 关闭。
- **滚轮**：指针在 listbox 内时只滚动 listbox viewport，使用既有 `scrollOffsetY` 几何级路径。

## NORTH_STAR 自检

- **I1/I11**：handler 只写 `expanded`、`highlightedIndex`、`scrollOffsetY` 或调用 `onSelect`；不得直接改 SceneNode 属性槽或树结构。
- **I2/I9**：所有 signal 写入仍经过中央事务，同帧合并。
- **I6/I10**：top-layer 是 scene 数据层维护的额外 root 列表；渲染层只消费绘制计划和命令，不读取 signal、组件或平台输入；scene 核心包不引入 MC/LWJGL/Forge import。
- **I7/I8**：浮层优先 hit-test 是只读遍历；overlay 显隐和滚动只影响对应 root 与几何级滚动，不污染稳定兄弟子树。
- **I3**：浮层注册与摘除绑定 Owner 生命周期，组件函数仍只负责声明树、bind、on 和 show/portal 派生。

## 验证锚点

- Select 位于 scrollable/clip 容器内时，浮层可跨 clip 可见。
- 浮层覆盖主树节点时，命中优先落到浮层选项。
- 外部点击、ESC、选中后均关闭浮层。
- Anchor 随宿主 resize 或父滚动更新位置。
- Owner dispose 后 overlay root 与 handler 清理，无残留命中。
- highlighted 变化和 listbox 滚动不触发主树稳定兄弟重布局。
- `ScenePackageIsolationTest` 覆盖新 overlay 包无平台 import。

## 后续注意事项

- 文件级 P0 施工清单已落在 `docs/开发者文档/specs/scene-overlay-p0-plan.md`；实现时按该文档拆分，不得退回 `SceneSelect` 私有浮层。
- `ui.scene.control` 契约后续应新增 R11：浮层显隐必须经 signal→show/portal 派生，禁止 handler 命令式提升或摘除节点。
- `NORTH_STAR.md` 是否需要新增 top-layer 作为“额外 paint root + 独立 hit-test 入口”的说明，涉及宪章边界扩展，需经用户确认后再改。
