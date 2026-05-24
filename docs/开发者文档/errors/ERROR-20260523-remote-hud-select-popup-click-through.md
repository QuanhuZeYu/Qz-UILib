# 远程 HUD select 下拉选项点击穿透

## 错误现象

- 远程 HUD `DIALOG` 中展开 `<select>` 后，点击下拉选项可能没有选中目标项，而是触发视觉下方的按钮或原生界面输入。
- 运行时截图中浮窗默认关闭按钮也可能被按钮默认布局拉伸成一条横向蓝条，遮住服务端下发的 HTML 内容。

## 触发场景

- `<select>` 位于 HUD 浮窗的滚动或裁剪内容容器内，展开面板视觉上覆盖到容器外或覆盖到后续按钮区域。
- 展开 `<select>` 后继续拖动 DIALOG 浮窗、滚动内容容器，或 HUD 输入路由重新同步真实视口。
- DIALOG 宿主把默认关闭按钮作为普通 `DocumentButtonControl` 追加到 flex shell，未显式约束宽高。

## 根本原因

- 下拉候选面板仍按普通 DOM 子树参与布局和命中测试，容易受父级 `overflow:auto/hidden` 或 stacking boundary 影响；HUD 即时输入在命中不到候选项时会把鼠标事件放给下层界面。
- 默认关闭按钮继承普通按钮的 flex/block 布局语义，在绝对定位但宽度未约束时会按包含块拉伸。
- 早期修复把候选面板 `append` 到 document root，再用 `position:fixed + z-index` 模拟顶层。这只覆盖简单重叠场景，不等价于浏览器 top-layer：它会改变 `option` 与 `select` 的逻辑 DOM 归属，并且在 fixed HUD shell、滚动内容和 stacking context 叠加时仍可能让视觉位置、绘制层级和命中链路脱节。
- top-layer 初版只在打开瞬间同步 popup 的 `left/top/width`，锚点随后因浮窗拖拽、滚动或视口变化移动时，候选面板坐标会停留在旧位置。
- HUD 主键按下路由在命中目标后先全局清理交互状态，导致已经展开的 select 因失焦关闭，option 还没收到 mousedown 就被移出 top-layer。

## 修复方案

- `DocumentSelectControl` 展开时把候选面板提升为文档 fixed 顶层节点，并用当前 select 布局边界同步 left/top/width；关闭后恢复到 select 内部。
- 顶层候选面板声明鼠标按下时保留当前焦点，避免点击 option 的 mousedown 先触发 select blur 导致面板关闭。
- 远程 HUD 默认关闭按钮显式设置固定宽高、居中 flex 和更高 z-index，只承担关闭操作，不再参与作者内容布局。
- 运行时远程 HUD smoke HTML 改由服务端内容自身提供 `data-qz-hud-drag-handle="true"` 拖拽把手和面板外观。
- 后续修正改为文档运行时内部 top-layer：`popupElement` 保持在 `select` 内部，展开时只注册为 UA 顶层；普通布局跳过 top-layer 元素，绘制追加在普通文档之后，命中测试先查 top-layer 再查普通树，滚动状态同时覆盖普通树和顶层盒。
- 文档运行时在每次 top-layer 布局/绘制/命中前，按 select 当前布局边界重新放置候选面板，使 popup 跟随 fixed shell、滚动内容和 viewport 变化。
- HUD 主键按下清理改为保留本次命中的 HUD 文档交互状态，只清掉其它 HUD，避免同一文档内 top-layer 点击在事件派发前被 blur 关闭。
- 远程 HUD smoke 增加普通、覆盖按钮、裁剪容器三类下拉对比项，用运行时表单提交验证 top-layer 交互路径。

## 预防措施

- 表单控件的弹出层不能简单当作普通子节点处理；遇到 select、菜单、tooltip 等覆盖型 UI 时优先按浏览器 top-layer 语义建模。
- HUD 输入穿透类问题需要同时覆盖普通文档命中和 HUD/裁剪容器场景，单测不能只验证无裁剪的简单重叠。
- 宿主自动生成的控件必须有明确尺寸契约，避免默认控件样式污染远程 HTML 作者的视觉边界。
- 不要用 `root.append(...) + z-index` 代替 top-layer。浏览器语义要求 DOM 归属、布局参与、绘制层级、命中顺序分开建模。
- top-layer 的视觉位置不能只在打开瞬间计算；锚点布局、滚动偏移或宿主视口变化后，绘制与命中前都要重新同步。
- 宿主级输入仲裁不能在派发同一文档命中事件前清空该文档焦点，否则会破坏 select、菜单等依赖焦点维持的顶层弹层。
