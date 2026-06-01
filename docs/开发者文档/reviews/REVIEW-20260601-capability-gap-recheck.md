# 浏览器能力缺口复核（取代 2026-05-18 结论）

## 审查信息

- 审查日期：2026-06-01
- 审查主题：以当前源码为准，重新核实项目相对浏览器常用能力的真实剩余缺口
- 触发原因：`REVIEW-20260518-browser-capability-gap-audit.md` 正文结论已严重滞后（例如其判定 `transform`、`position:sticky`、flex `order`、`::before/::after` 等"完全未实现"，但当前源码已全链路落地）。该文档虽有"后续状态"补注，但正文表格与"30 项完全没有实现"的总结数字未同步，会持续误导后续判断。
- 核实方法：对每项能力分别核对样式声明（`UiStyleDeclaration`）、级联（`UiStyleResolver`/`ComputedStyle`）、布局（`DocumentLayoutEngine` 及各 Helper）、绘制（`DocumentPaintEngine`/`DocumentPaintRenderer`）、事件（`Document*EventDispatcher`/`HtmlLikeDocumentWidget`）、控件（`Document*Control`）的实际消费链路，而非依赖任何历史文档结论。

## 总体结论

当前 HTML-like 框架的浏览器能力覆盖度远高于 `REVIEW-20260518` 正文所述。该次审查列为"待实现/部分实现"的 20+ 项已完整落地（见第三节）。当前真实剩余缺口约 **23 项主项**，其中 **12 项完全未实现、约 11 项部分实现**，另有 **1 项本次新发现的运行时语义一致性缺口**。

更关键的是：剩余缺口中有相当一部分是项目**已明确声明的有意能力边界**（按需扩面、不默认追求 CSS 全量对齐），不应与"待补缺陷"混为一谈。见第四节分级。

## 一、完全未实现（12 项）

布局：
- CSS Grid（`display:grid`）：`UiDisplay` 无 GRID 值，`DocumentLayoutEngine` 无 grid 分支
- float 浮动：全项目无 float 布局逻辑与环绕排列
- transform 的 skew / matrix：`UiTransform` 仅承载 translate/scale/rotate/origin，无 skew、matrix 字段

样式视觉：
- 渐变 linear-gradient / radial-gradient：背景仅纯色 + 单图
- 多背景（逗号分隔）：`backgroundImage` 为单值，`backgroundColor` 为单 int
- 多重 box-shadow / 多重 text-shadow：均为单值字段
- background-repeat / position / size 模型：背景图固定拉伸填充 border box，无平铺/定位/尺寸
- background-image 的 `url()` CSS 字符串解析：`RemoteCssParser` 无 `background-image` case，只能经 Java API 设置

选择器与规则：
- 兄弟组合器（`A + B` 相邻 / `A ~ B` 通用）：`Combinator` 仅 DESCENDANT/CHILD
- 属性选择器（`[attr]` / `[attr=value]`）：`UiSelector` 解析遇到 `[` 即抛异常
- `@media` 媒体查询：全项目无实现

文本：
- font-family 作者层 CSS 属性：底层 `FontType` 有字体能力，但 `UiStyleDeclaration`/`ComputedStyle` 无字段，未开放为作者层样式属性

## 二、部分实现（约 11 主项）

- CSS transform：translate/scale/rotate 已全链路（布局基准、绘制矩阵、命中反算、stacking context、动画插值），但缺 skew/matrix，且 transform 只影响绘制与命中、不参与布局尺寸
- calc()：仅 `calc(N% ± Mpx)` 固定式，不支持纯像素相加、多项、乘除、嵌套或任意表达式
- 逗号分组选择器：仅 `RemoteCssParser` 样式表解析层按 `,` 拆分等效支持，`UiSelector` 对象层不支持，编程式无法构造分组
- 结构性伪类：仅 `:first-child/:last-child/:nth-child`（含 odd/even/an+b），缺 `:nth-of-type/:nth-last-child/:only-child/:not()`
- flex `align-items: baseline`：退化为 START（`FlexLayoutHelper` 有 fallback 告警），无真实基线对齐
- textarea：支持 `\n` 多行编辑/光标/选区/maxLength，但无 `pre-wrap` 式按容器宽度软换行（逻辑行固定 NOWRAP）
- input type：固定 `type=text`，无 number/password/email、密码掩码、类型校验
- 表单校验：仅 `required` + `maxLength`，缺 `pattern/min/max/checkValidity/ValidityState` 与校验态样式
- 拖拽：drag/dragstart/dragover/dragend 已实现，缺独立 `dragenter/dragleave/drop`（dragover 兼做进入/释放语义）
- DOM 文本读写 API：内部有 `collectTextContent`，但未公开标准 `textContent`/`innerHTML` getter/setter
- vertical-align：仅 baseline/top/middle/bottom 4 值，缺 sub/super/text-top/text-bottom/数值偏移
- text-overflow ellipsis：仅 `white-space:nowrap` 单行下生效，无多行省略
- contextmenu：仅鼠标右键触发，无键盘 Menu 键/长按等触发路径
- 命令式 animate() 句柄：返回 `DocumentAnimation` 仅含 cancel/isRunning，缺 pause/reverse/playbackRate/seek/finish
- keyframe per-stop timing function：整条 track 共用单一 timingFunction，每段 stop 无独立缓动

## 三、本次新发现的运行时语义一致性缺口（1 项）

- `DocumentScrollMetricsCalculator`（`measureContentBounds`）未跟随 2026-06-01 fixed containing block 语义更新：可滚范围度量仍无条件把所有 fixed 后代按视口处理并 `continue` 跳过，未接入 `DocumentVisualTraversal` 的 fixedContainingBlock 状态。当 fixed 后代位于 transform 祖先内并超出该祖先 content box 时，理论上应参与该祖先可滚范围计算，但当前被忽略。低优先、非回归（既有行为本就跳过全部 fixed），属"语义尚未完全统一"而非"改坏"。

## 四、缺口分级（填补前必读）

按项目既定的"按需扩面、不默认追求 CSS 全量对齐"哲学，上述缺口应先分两类，避免把有意边界当待补缺陷。

### A 类：已声明的有意能力边界（默认不补，按真实业务需求再评估）

依据 `docs/记忆/当前态/当前上下文.md` 第 43 行与动画 showcase 边界声明：
- CSS Grid
- float 浮动
- gradient 渐变
- transform skew/matrix（矩阵堆栈）
- flex `align-items: baseline`（等价 START）
- 完整 Web Animations API 时间轴控制（pause/reverse/playbackRate/seek）
- keyframe per-stop timing function
- CSS `var(...)` 声明级解析

### B 类：性价比待评估的真缺口（可作为集中填补候选）

按"实现代价 vs 作者层收益"粗排：
- 高性价比候选：input type（password/number，已完成）、textContent/innerHTML 读写（标准 API 缺失，作者高频用）、textarea 软换行（补完多行表单）、background-image 的 CSS `url()` 解析（远程页面易用性）
- 中等：属性选择器、兄弟组合器、结构伪类细分（nth-of-type/not）、表单校验扩展、拖拽 dragenter/dragleave/drop、vertical-align 扩展、多背景/多重阴影
- 低：@media、background-repeat/position/size、text-overflow 多行、contextmenu 触发路径、`DocumentScrollMetricsCalculator` fixed 一致性

> **font-family 已移出 B 类（2026-06-01）**：原以为底层 `FontType` 已具备字体能力、开放代价小，核实后发现字体引擎（`FontMatcher`/`FontCatalog`/字形表）无字体族维度，只能按全局 `fontSort` 选字体。低成本路径会产出"只记录不生效"的假能力，真正生效需独立的字体运行时改造大工程。详见 `docs/记忆/决策/DECISION-20260601-font-family-deferred.md`。

## 五、后续动作建议

1. 当前主线仍是 Phase 2 浏览器语义**修复**（修正已实现能力的语义偏差），与"填补新能力**缺口**"是两条不同的线，建议不要在同一批次混做。
2. 若要开"缺口填补"线，先在 A/B 分级上与项目维护者确认边界，再从 B 类高性价比项中挑 1~2 个影响面可控的，沿用现有"每轮先补回归测试 → 最小实现 → `git diff --check`/相关测试/`compileJava`"节奏推进，避免一次铺开多个大件导致半成品。
3. `REVIEW-20260518` 正文结论以本文件为准取代；其历史价值保留，但数字与"完全未实现"清单不再作为现状依据。
