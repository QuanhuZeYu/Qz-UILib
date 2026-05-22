# ERROR-20260518-browser-semantics-audit

## 错误现象

通过全面代码审查发现 HTML-like UI 渲染框架存在 28 处不符合浏览器语义的实现，涵盖 CSS 盒模型、Flex 布局、定位、overflow、display、文本、事件等多个维度。

## 触发场景

- 业务代码使用 `text-align`、`line-height`、`white-space`、`text-overflow` 等文本样式属性时无效
- 使用 `visibility:hidden` 时元素消失（等同于 `display:none`）
- 圆角按钮在角落区域仍可点击
- `min-width`/`max-width`/`min-height`/`max-height` 约束无效
- `align-self` 无法覆盖父容器 `align-items`
- `flex-basis` 无效，主轴初始尺寸直接用 `width`/`height`
- `flex-wrap:wrap` 无效，超出容器直接溢出
- `justify-content: space-around`/`space-evenly` 无效
- `align-items: baseline` 无效
- flex item 的 `margin:auto` 不吸收剩余空间
- `overflow:scroll` 无效（等同于 `overflow:visible`）
- 独立的 `mousedown`/`mouseup` DOM 事件不存在
- hover 从子元素移到父元素时，父元素会收到多余的 leave 事件
- `focusin`/`focusout` 冒泡事件不存在
- `position:relative` 的 `top/bottom` 百分比在父高 auto 时退化为 0（参数名误导）
- `UiPosition.ABSOLUTE` 注释写"content box"但实现是 padding box

## 根本原因

框架在初版实现时只覆盖了最基础的布局能力，大量 CSS 标准属性未实现或实现不完整：
- `ComputedStyle`/`UiStyleDeclaration` 缺少 12 个新属性字段
- 布局引擎未接入 min/max 约束、flex-basis、align-self、flex-wrap 等
- 命中测试引擎未考虑 border-radius 和 visibility
- 绘制引擎未处理 visibility:hidden
- 事件系统缺少 mousedown/mouseup/focusin/focusout 独立事件

## 修复方案

### 第0批：基础设施
- 新建枚举：`UiTextAlign`、`UiWhiteSpace`、`UiTextOverflow`、`UiVisibility`、`UiAlignSelf`、`UiFlexWrap`
- 扩展枚举：`UiJustifyContent`（+SPACE_AROUND/SPACE_EVENLY）、`UiAlignItems`（+BASELINE）、`UiOverflow`（+SCROLL）
- `ComputedStyle` 添加 12 个新字段（lineHeight/textAlign/whiteSpace/textOverflow/visibility/minWidth/maxWidth/minHeight/maxHeight/flexBasis/alignSelf/flexWrap）
- `UiStyleDeclaration` 添加对应 getter/setter/clear/update
- `UiStyleResolver` 添加解析逻辑（textAlign/whiteSpace/visibility 支持继承）

### 第1批：文本属性
- `line-height`：`resolveTextLineHeight` 读取 ownerStyle，支持 px/百分比
- `text-align`：`appendTextRun` 计算行内偏移量
- `white-space:nowrap`：`appendTextRun` 跳过换行，只取单行
- `text-overflow:ellipsis`：配合 nowrap，超出时截断并加 `…`

### 第2批：尺寸约束
- `min/max-width`：`resolveContentWidth` 末尾加 `applyWidthConstraints`
- `min/max-height`：`resolveContentHeight` 末尾加 `applyHeightConstraints`

### 第3批：Flex 增强
- `flex-basis`：`resolveContentMainSize` 优先读 flexBasis，非 auto 时用作主轴初始尺寸
- `align-self`：新增 `resolveItemCrossOffset`/`isItemCrossStretch`，flex 路径使用
- `flex-wrap:wrap`：`layoutRowFlexChildren` 重写为多行逻辑
- `justify-content space-around/space-evenly`：`resolveDynamicGap`/`resolveLeadingOffsetForSpacing` 新增分支
- `align-items baseline`：`resolveCrossOffset` 新增 BASELINE 分支（暂按 START 处理）
- flex shrink 权重修正：使用 flexBasis 而非 contentMainSize
- `margin:auto` 在 flex item 中吸收剩余空间

### 第4批：命中测试与绘制
- `border-radius` hit test：`DocumentHitTestEngine` 新增 `containsInRoundedRect`/`resolveBorderRadius`
- `visibility:hidden` hit test：`isHitTestHidden` 检查 `UiVisibility.HIDDEN`
- `visibility:hidden` 绘制：`appendBoxCommands` 开头跳过 hidden 元素
- `overflow:scroll`：`DocumentScrollState`/`DocumentPaintEngine` 将 AUTO 判断改为 `isScrollableOverflow()`

### 第5批：定位修复
- `resolveRelativeOffsetY` 参数名 `borderBoxHeight` → `containingHeight`（功能已正确，消除误导）
- `UiPosition.ABSOLUTE` 注释更新为"相对最近 positioned ancestor 的 padding box"

### 第6批：事件系统
- 新建 `DocumentElementMouseDownEvent`/`Handler`、`DocumentElementMouseUpEvent`/`Handler`
- `ElementNode` 添加 mouseDownHandler/mouseUpHandler 字段
- `HtmlLikeDocumentWidget` 在 onMouseDown/onMouseUp 中分发独立事件
- hover 父子切换修复：`updateHoveredElement` 改用 `dispatchHoverChangedWithAncestorAwareness`，跳过公共祖先节点
- 新建 `DocumentElementFocusInEvent`/`Handler`，`dispatchFocusChanged` 添加冒泡分发

## 预防措施

- 新增 CSS 属性时，必须同时修改 ComputedStyle、UiStyleDeclaration、UiStyleResolver 三个文件
- 布局引擎修改后必须运行 `compileJava` 验证
- 定期对照浏览器标准审查实现，不要完全依赖项目内文档
- 枚举扩展时检查所有 switch/if-else 分支是否覆盖新值
- 诊断页或展示页只能展示已经接入布局、绘制、命中或事件链路并有最小验证的能力；仅接入级联解析、值类型或手动同步示例时，必须明确写成能力边界，不得用示例页面暗示浏览器语义已完整支持
