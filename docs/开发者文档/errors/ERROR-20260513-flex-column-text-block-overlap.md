# flex column 中文本块未按真实换行高度参与兄弟排布

## 错误现象

- 固定宽度 HUD 浮窗中，`flex-direction:column` 容器下的多个纯文本 block 子项会出现视觉压叠。
- 现象接近“后一个组件块没有为前一个文本块的真实换行高度留位置”。

## 触发场景

- 列方向 flex 容器使用 `align-items:start/center/end` 一类非 `stretch` 对齐。
- 子项为 `width:auto`、`height:auto` 的 block 文本块，并在窄宽度下发生多行换行。

## 根本原因

- `DocumentLayoutEngine.layoutColumnFlexChildren(...)` 先用一次预布局盒测量 auto 高子项，再把 `item.box.getContentHeight()` 写回 `contentMainSize` 作为兄弟项排布依据。
- 旧实现正式布局时继续把这个测量值作为强制高度传回 `layoutElement(...)`，导致 auto 高文本块失去再次按最终宽度自然扩展的机会。
- 一旦预布局高度和最终文本换行高度不一致，就会出现兄弟项之间留白不足，表现为文本块压叠。

## 修复方案

- 为 `DocumentLayoutEngineTest` 增加最小回归用例：固定宽度容器、`flex column`、多个 `width:auto` 的纯文本 block 子项，断言后一个子项 `top >= 前一个子项 bottom`。
- 在列方向 flex 的最终布局阶段，对未被 grow/shrink 改写的 auto 高子项继续保留 `AUTO_SIZE`，让文本块按最终宽度重新计算真实内容高度。
- 仅在显式高度或 grow/shrink 真实改写了主轴尺寸时，才把主轴尺寸作为强制高度传回最终布局。

## 预防措施

- 后续所有 layout-affecting 修改都要同时覆盖：行/列方向、`stretch`/非 `stretch` 对齐、auto/显式宽高，以及“窄宽中文换行”场景。
- 遇到“组件之间没给文本留位置”的实机现象，优先怀疑布局引擎的测量值与最终布局值不一致，而不是先归因到 demo 结构。
