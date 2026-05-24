# 表单控件渲染缺少浏览器语义导致文字与光标异常

## 错误现象

- 远程页面运行时 smoke 中，`textarea` 多行文本的光标与实际文本行位置不一致。
- `select` 下拉面板展开后，option 背景可见但选项文字没有绘制。

## 触发场景

- 服务端下发 HTML-like 表单页面，客户端用 `DocumentTextAreaControl` 和 `DocumentSelectControl` 渲染表单控件。
- `DocumentSelectControl` 的 option 元素使用 `display:flex`，并保留直接文本子节点。
- `DocumentTextAreaControl` 根元素带 border / padding / overflow，内部 selection/caret layer 使用自定义渲染器绘制光标与选区。

## 根本原因

- flex 布局只收集可见 `ElementNode` 子项，忽略了直接 `TextNode`。按浏览器语义，flex 容器中的非空直接文本应生成匿名 flex item 参与布局和绘制；缺少这层语义后，option 直接文本不会进入布局盒树。
- textarea 光标和选区坐标使用 overlay layer 的 content 参数推导，未统一到实际文本内容盒。根 textarea 的 padding / border / scroll 语义存在时，文本绘制位置与 caret 计算原点可能分离。

## 修复方案

- 在 flex 布局层补齐匿名 flex item：收集非空直接文本，按父级文本样式计算匿名 item 样式，参与 flex 排布、固有宽度测量、盒树顺序和绘制。
- `DocumentSelectControl` 保持 option 直接文本模型，不插入业务可见的 span。
- textarea 光标、选区和文本测量统一使用 `contentElement.getDocumentBounds()` 的真实 content origin，并使用根 textarea bounds 作为 viewport 尺寸来源，保留根元素 border / padding / overflow / scroll 语义。
- 补充 JVM 渲染回归测试：flex 直接文本绘制、select 展开后 option 文本绘制、textarea 多行 caret 与对应文本行末对齐。

## 预防措施

- 表单控件渲染问题优先回到 HTML-like/CSS 语义层排查，避免在控件层插入额外可见 DOM 结构掩盖布局缺口。
- flex 相关修改必须覆盖直接文本、真实元素、absolute/fixed 脱流子项和最终盒树顺序。
- 自定义渲染器若绘制 caret/selection，应明确使用哪个 content box 作为测量原点，并用运行时 bounds 回归测试验证。
