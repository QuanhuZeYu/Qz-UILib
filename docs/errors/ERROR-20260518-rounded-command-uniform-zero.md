# ERROR-20260518-rounded-command-uniform-zero

## 错误现象

分角圆角能力已进入 paint command 与 renderer 链路后，仍存在多处退化风险：

- 左上角为 0、其他角有圆角时，clip 快照可能被当成“无圆角”跳过。
- `BOX_SHADOW` / `BOX_SHADOW_INSET` 命令如果只携带旧单值半径，会让分角圆角在 renderer 侧退化为统一 0。
- renderer 重新从样式读取 box-shadow 原始颜色时，可能绕过 paint engine 已经应用的 opacity。
- 测试记录器把四角圆角压成单值，宽松断言只看调用数量或允许半径为 0，无法捕获同类回归。

## 触发场景

- 元素设置 `border-radius` 分角，且 `top-left` 为 0、`top-right` 或其他角大于 0。
- 元素同时使用 `overflow:hidden`、`backdrop-filter`、`box-shadow`、`outline`、边框或背景绘制。
- 元素或祖先设置 `opacity` 后仍绘制 box-shadow。
- 测试只断言 draw call 数量，或把 `ResolvedCornerRadii` 读取为 `getUniformRadius()`。

## 根本原因

- `ResolvedCornerRadii.getUniformRadius()` 只在四角一致时有语义；把它当作“是否存在圆角”的判断会遗漏左上角为 0、其他角非 0 的分角形态。
- 命令对象默认会为旧构造器填充 `uniform(borderRadius)`，如果新链路没有显式传入真实 `ResolvedCornerRadii`，renderer 的样式回退分支会因为命令已有 `cornerRadii` 而不可达。
- renderer 不应重新用样式原始颜色覆盖 paint engine 解析后的命令颜色，否则 opacity、动画颜色等上游计算会丢失。
- 测试替身压缩关键信息会制造假绿：生产代码丢失四角值时，测试仍可能因为单值半径或调用数量通过。

## 修复方案

- 判断圆角存在时检查四个角任意一个是否大于 0，不再使用 `getUniformRadius()` 作为存在性判断。
- paint engine 在生成背景、clip、backdrop、border、outline、box-shadow 命令时携带真实 `ResolvedCornerRadii`。
- renderer 优先使用 `command.getColor()` 中的已解析颜色，并让 inset box-shadow 基于四角半径逐层内缩。
- 测试记录器保留完整四角值，断言必须精确验证颜色、位置、四角圆角和关键语义。

## 预防措施

- 分角值类型进入命令链路后，测试替身不得降级为旧单值字段。
- 新增或修改圆角绘制能力时，至少覆盖“左上角为 0、其他角大于 0”的用例。
- 涉及 opacity、动画或变量解析后的命令颜色，renderer 不应回读样式原始值覆盖命令值。
- 对视觉回归测试避免只断言调用数量；必须断言能区分统一半径、分角半径和半径全 0 的关键字段。
