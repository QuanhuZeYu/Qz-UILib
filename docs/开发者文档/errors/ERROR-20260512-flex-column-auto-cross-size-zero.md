# flex column 非 stretch 子项 auto 宽度被错误压成 0

## 错误现象

- 业务页面在 `display:flex` + `flex-direction:column` + `align-items:start/center/end` 下，若子项未显式声明宽度，会被挤成极窄列。
- 典型表现为文本被压成竖排、横向按钮组被压成细长竖条。

## 触发场景

- 列方向 flex 容器取消默认 `stretch`，改为 `START`、`CENTER` 或 `END`。
- 子项 `width:auto`，且内容需要依赖固有宽度或子元素宽度来决定自身尺寸。

## 根本原因

- `DocumentLayoutEngine.resolveColumnCrossContentWidth(...)` 在 `width:auto` 且 `align-items != STRETCH` 时直接返回 `0`。
- 该值随后被当作 `forcedContentWidth` 传入 `layoutElement(...)`，导致元素内容宽度不再走 auto 测量，而是被强制固定为 `0`。
- 现有测试只覆盖了 `flex column + stretch` 路径，没有覆盖 `start/center/end + auto-width` 的交叉轴测量。

## 修复方案

- 将列方向 flex 子项的 auto 交叉轴宽度改为走统一的固有宽度测量逻辑。
- 测量结果受可用内容宽度裁剪，避免超出父容器内容盒。
- 补充 `flex column` 在 `START`、`CENTER`、`END` 下的 auto 宽度与对齐回归测试。

## 预防措施

- 所有 layout-affecting 修改都要补对应方向、对齐模式和 auto/显式尺寸组合测试，不能只测默认 `stretch`。
- 对 `forcedContentWidth` / `forcedContentHeight` 这类“跳过 auto 解析”的入口要特别谨慎，避免用占位值误替代真实测量结果。
- 业务侧如果明确需要列容器子项横向占满，仍应显式使用 `width:100%` 或保留 `align-items:stretch`，不要依赖 shrink-to-fit 推断为满宽。
