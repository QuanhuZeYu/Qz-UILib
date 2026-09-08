# LaTeX 命令支持清单

本清单区分命令语义与字形质量。解析入口为 LatexParser，符号映射以 LatexSymbols 为准；这是数学子集，不执行宏定义、包加载或外部 TeX。B2a 样式命令已按批准方案实现，完整构建与软件回归通过，实际覆盖见《规划-LaTeX排版能力建设》的本批交付记录。

## 支持的结构与样式

| 输入 | 语义 |
| --- | --- |
| `^`、`_`、`{...}` | 上下标与分组；深脚本在 scriptscript 级封顶。 |
| `\frac`、`\binom` | 按所在数学样式派生分子分母／上下项。 |
| `\dfrac`、`\tfrac`（B2a） | 分式内部显式使用 display／text；不替换外围脚本环境。 |
| `\displaystyle`、`\textstyle`、`\scriptstyle`、`\scriptscriptstyle`（B2a） | 当前数学列表剩余部分的样式声明；组、脚本、参数和单元格内声明局部生效。 |
| `\sqrt{...}`、`\sqrt[index]{...}` | 根体与可选根指数；根指数默认 scriptscript。 |
| `\left`、`\middle`、`\right` | 配对及中间定界符；middle 分段不结束内部样式声明。 |
| `\begin{matrix/pmatrix/bmatrix/vmatrix/cases}` 与对应 `\end` | 矩阵与分段结构，`&` 分列、`\\` 分行；单元格默认 text。 |
| `\begin{array}{lcr}` 与 `\end{array}` | 既有数组环境，支持 l/c/r 列对齐；竖线等格式修饰仍忽略。 |
| `\text{...}` | 直体文本，保留空白；内部样式命令按文本处理。 |
| `\limits`、`\nolimits` | 按既有算子类别处理显式上下限修饰。默认位置不随 B2a 样式切换改动。 |
| `\,`、`\:`、`\;`、`\!`、`\quad`、`\qquad`、反斜杠空格 | 显式数学间距，随该处有效字号度量；支持负间距。 |

样式声明示例：`{\scriptstyle a+b}c` 只缩小组内内容；`x^{\displaystyle\frac{a}{b}}` 允许脚本局部放大；`\scriptstyle\dfrac{a}{b}^{c}` 的分式采用 display，而 c 仍由外层 script 派生。这些例子不改变 `$...$`、`$$...$$` 或 `<latex>` 的默认入口模式。

符号命令覆盖希腊字母、关系、集合、箭头与大运算符等，具体名称由 `src/main/java/club/heiqi/uilib/font/latex/LatexSymbols.java` 的静态表维护。已识别的普通函数包括 `log/ln/sin/cos/tan/mod/exp/arg`；上下限类函数包括 `lim/max/min/det/gcd/sup/inf/Pr`。

## 已解析但字形或排版仍为近似

| 命令／结构 | 当前限制 |
| --- | --- |
| `\sqrt`、高定界符、矩阵外围括号 | 普通字体整字缩放，尚无数学字体变体选择与部件拼接；高结构笔画可能偏粗。 |
| `\hat`、`\bar`、`\vec`、`\dot`、`\ddot`、`\tilde` | 使用现有间隔字符定位重音，尚无宽重音变体或拼接。 |
| `\overline`、`\underline` | 复用规则线覆盖内容宽度，度量来自当前字体及布局常量。 |
| 数学变量与大运算符 | 数学变量斜体仍含渲染斜切近似；算子使用现有字体与上下限产品规则，未接入 OpenType MATH。 |
| 矩阵内部间距 | 保留当前比例布局；明确 cell 的 text 样式不等于完整实现 TeX array 排版。 |

## 字面降级与容错

未知命令保留反斜杠和名称，后续参数按现有数学语法解析；这不表示支持该命令的语义。例如 `\mathrm`、`\mathit`、`\mathbf`、`\operatorname`、`\widehat`、`\widetilde` 尚未在本批实现。数学字体命令属于 B2b，字形资源和伸缩能力属于 B3/B4。

分式等结构缺参按空组容错；上下标和重音沿既有缺参规则处理；多余闭花括号和无配对的 right/end 等仍按解析器既有规则处理。需要原样显示完整源码时使用代码通道；text 会处理自身转义与嵌套分组，不能把未知命令降级当成稳定的转义机制：未来新增受支持命令时，其显示会变为对应排版语义。

## 验证边界

正式回归验证作用域、字距、脚本绑定、几何及生产绘制采集。软件图复用真实字形，但不等同于游戏 GPU shader、宿主裁剪或外部 TeX 引擎对拍；实际执行与未覆盖范围以本批报告为准。
