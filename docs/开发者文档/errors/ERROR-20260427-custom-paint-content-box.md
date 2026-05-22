# ERROR-20260427-custom-paint-content-box

## 错误现象

- `DocumentCustomRenderer` 的参数命名为 `contentLeft/contentTop/contentRight/contentBottom`，但 `DocumentPaintEngine` 实际传入的是 padding box。
- 带 padding/border 的 HTML-like 背包格子控件会把槽位绘制到 padding 区域，偏离 HTML 内容盒语义。
- 元素自身存在滚动偏移时，CUSTOM 内容命令未随该元素的 `DocumentScrollState` 移动。
- 迁移版背包页测试声称验证返回按钮，但实际直接调用 `model.returnToVanillaInventory()`，没有覆盖 HTML-like 按钮事件链路。

## 触发场景

- 在 HTML-like 元素上设置 `ElementNode.setCustomRenderer(...)`，同时设置 padding、border 或 `overflow: auto`。
- 使用 `DocumentInventorySlotGridControl` 这类依赖 CUSTOM paint 的控件绘制复杂内容。
- 编写页面控制器测试时只校验文本和模型结果，没有通过 `HtmlLikeDocumentWidget` 分发键盘/鼠标事件。

## 根本原因

- `appendCustomCommand(...)` 使用了 `getPaddingBoxLeft/Top/Right/Bottom`，与 `DocumentCustomRenderer` 的内容盒契约不一致。
- CUSTOM 命令生成发生在计算子内容滚动偏移之前，导致它不像文本和子树一样作为内容参与滚动。
- `ElementNode.setCustomRenderer(...)` 没有提升文档 mutation version，运行期新增或替换 custom renderer 时可能复用旧 paint command 缓存。
- 测试通过直接调用 model 绕过 UI 事件，造成“测试通过但按钮链路未被验证”的假阳性。

## 修复方案

- CUSTOM 命令改用 `DocumentLayoutBox` 的内容盒坐标。
- CUSTOM 命令与文本、子树共用子内容滚动偏移，保证位于元素自身滚动内容层。
- `ElementNode.setCustomRenderer(...)` 在 renderer 变化时调用 `markMutated()`，强制刷新布局/绘制缓存。
- 为 paint engine、paint renderer、库存格子控件和背包诊断页补充定向测试，测试通过真实渲染坐标与键盘激活按钮覆盖问题链路。

## 预防措施

- 新增 CUSTOM paint 能力时必须明确它属于元素内容层，而不是背景/边框层。
- 带 padding、border、overflow 的自定义控件必须有坐标契约测试。
- 页面交互测试不能直接调用 model 方法替代 UI 事件；需要通过 `HtmlLikeDocumentWidget` 的鼠标、键盘或文本输入分发路径触发。
