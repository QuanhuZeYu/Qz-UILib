# ERROR-20260818-playground-row-button-fill-and-dual-mount.md

**日期**：2026-08-18
**组件**：`internal.devtools.playground`（PlaygroundKit + pages/）—— 测试场地按钮行
**状态**：已修复（SHRINK 收口 + 去重复挂载，headless 回归测试锚定）

## 现象

真机反馈：打开 `/qzuilib test` 后，演示页里的按钮（按钮行/操作按钮）不在容器内部，
看起来全部跑到容器最右侧，只露出一点点圆角边缘。按钮被挤到卡片右缘外，大部分被视口裁切，
只剩圆角边缘可见。

## 根因（两条叠加）

### 根因 1：按钮根节点未按引擎 WidthSizing 语义设 SHRINK

`SceneButton` 根节点是容器节点（ROW，内有 label 子），默认 `SceneNode.WidthSizing.FILL`
（`SizingCalculator.computeWidth` 容器分支：宽 = 可用宽）。把它放进 ROW 行后，
`ConstraintResolver.buildChildConstraints` 的 ROW 回退分支给非 grow 的容器子下传
`innerWidth - marginH`（行内宽），按钮 FILL 后宽 = 整行内宽。

- 三个按钮每个宽 = 行内宽（headless 实测 628px）→ FlexLayouter 主轴 cursor 依次推进：
  按钮1 x=0、按钮2 x=636、按钮3 x=1272 → 后两个整体溢出到行右缘外
  （实测按钮右缘 x+w=1264 > 行宽 628）。行内 START 对齐 + 溢出 → 用户看到的
  「按钮在容器最右侧、只露圆角边缘」正是被挤出并裁切的后缘按钮。

**引擎语义**：ROW 行内「按内容宽排布」的控件必须显式 `setWidthSizing(WidthSizing.SHRINK)`
（或 `setPreferredWidth` 钉死）。既有控件全部遵守（SceneDialog.buildButton :196、
VariantChooser back/confirm :267/:280、SceneKeyValueMap :587、SceneObjectField :827、
SceneSimpleList :709、SceneBreadcrumb :130、SceneRadioGroup :145 等），PlaygroundKit 漏了。

### 根因 2：对已 mount 挂载的按钮再次 appendChild（双份挂载）

`PlaygroundKit.button/primaryButton` 内部已 `rt.mount(parent, ...)`（mount 自动把根节点
append 进 parent）。页面调用处又写 `row.appendChild(PlaygroundKit.button(rt, row, ...))`，把
**同一个按钮节点第二次 append 进同一行**——`SceneNode.appendChild` 无重复子去重
（SceneNode.java:210 直接 `children.add`）→ children 双份（headless 实测行 kids=6 而非 3）。

FlexLayouter 按 children 顺序对同一实例排两次、第二次覆盖第一次 LayoutBox（实测 3 按钮
x=153/462/767，起点不为 0）：位置进一步右移，且每条不变量（不越界、不重叠、无重复）
被打破。FILL 未修时双份把溢出放大到 1264px 的越界值。

## 修复

1. `PlaygroundKit.button/primaryButton`：挂载后对根节点 `setWidthSizing(SHRINK)`
   （统一收口，新增 applyButtonSizing 私有辅助 + Javadoc 说明引擎规则）。
2. 五个演示页移除重复 append：`row.appendChild(PlaygroundKit.button(...))` →
   `PlaygroundKit.button(...)`（mount 已挂载，勿再 append）。
3. 新增回归测试 `PlaygroundButtonRowLayoutTest`（headless：TestPlaygroundHost(null)
   + SceneLayoutEngine + LayoutBox 断言）锚定四条不变量：行子不越界 / 行子无重复引用 /
   行子不重叠 / 带 chrome 控件子必须 SHRINK 且宽 < 行宽。

## 教训

- **ROW 行内的按钮/徽标等控件 = 默认 FILL = 拉满整行**：是引擎既定语义而非 bug；
  想要「一行几个按内容宽排布」的按钮，必须在控件根或装配层统一 SHRINK，没有例外。
- **mount 是「挂载并返回根」，不是「构建出未挂载的根」**：对 mount 返回值再 appendChild
  是双重挂载，SceneNode 不防重——静默双份，布局位置与点击命中都会漂移。
- 布局回归必须断言实际 LayoutBox（`cachedLayout`），不能只看「看起来差不多」；
  溢出是静默的，渲染与命中(点击坐标)都可能漂到容器外。
