# LaTeX 命令支持清单

本清单区分命令语义与字形质量。解析入口为 LatexParser，符号映射以 LatexSymbols 为准；这是数学子集，不执行宏定义、包加载或外部 TeX。B2a 样式命令与 B2b mathrm/mathit/mathbf/mathnormal/operatorname 已按批准方案实现，完整构建与软件回归通过，实际覆盖见《规划-LaTeX排版能力建设》的本批交付记录。

## 支持的结构与样式

| 输入 | 语义 |
| --- | --- |
| `^`、`_`、`{...}` | 上下标与分组；深脚本在 scriptscript 级封顶。 |
| `\frac`、`\binom` | 按所在数学样式派生分子分母／上下项。 |
| `\dfrac`、`\tfrac`（B2a） | 分式内部显式使用 display／text；不替换外围脚本环境。 |
| `\displaystyle`、`\textstyle`、`\scriptstyle`、`\scriptscriptstyle`（B2a） | 当前数学列表剩余部分的样式声明；组、脚本、参数和单元格内声明局部生效。 |
| `\mathrm{...}`、`\mathit{...}`（B2b） | 局部数学原子直体／斜体；保留原子类别、参数作用域及内外字号覆盖。生产默认使用固定 STIX 数学字形，真实斜体不再二次斜切；无数学能力的旧注入度量保留近似路径。 |
| `\mathbf{...}`、`\mathnormal{...}`（B2b） | 数学 alphabet 局部粗正体／显式恢复当前默认数学字体规则；度量和实际字体页同步选择，最内层字体选择覆盖外层。 |
| `\operatorname{...}`、`\operatorname*{...}`（B2b） | 完整数学主体、空 OP 与局部结构保留。名称直接字符紧排，额外组/嵌套结构保留数学字距；星号形式在 DISPLAY 默认上下堆叠，名称保持自然尺寸。 |
| `\sqrt{...}`、`\sqrt[index]{...}` | 根体与可选根指数；根指数默认 scriptscript。 |
| `\left`、`\middle`、`\right` | 配对及中间定界符；middle 分段不结束内部样式声明。 |
| `\begin{matrix/pmatrix/bmatrix/vmatrix/cases}` 与对应 `\end` | 矩阵与分段结构，`&` 分列、`\\` 分行；单元格默认 text。 |
| `\begin{array}{lcr}` 与 `\end{array}` | 既有数组环境，支持 l/c/r 列对齐；竖线等格式修饰仍忽略。 |
| `\text{...}` | 直体文本，保留空白；内部样式命令按文本处理。 |
| `\limits`、`\nolimits` | 按既有算子类别处理显式上下限修饰。默认位置不随 B2a 样式切换改动。 |
| `\,`、`\:`、`\;`、`\!`、`\quad`、`\qquad`、反斜杠空格 | 显式数学间距，随该处有效字号度量；支持负间距。 |

样式声明示例：`{\scriptstyle a+b}c` 只缩小组内内容；`x^{\displaystyle\frac{a}{b}}` 允许脚本局部放大；`\scriptstyle\dfrac{a}{b}^{c}` 的分式采用 display，而 c 仍由外层 script 派生。旧公式 API、`$...$`、无属性 `<latex>` 默认 TEXT。新 Markdown Content 出口的行中双美元为 DISPLAY 原子，独占双美元为居中块；历史段/行出口保留完整旧词法。富文本可用 `<latex math-style="display">` 显式指定，往返保留源码。

符号命令覆盖希腊字母、关系、集合、箭头与大运算符等，具体名称由 `src/main/java/club/heiqi/uilib/font/latex/LatexSymbols.java` 的静态表维护。已识别的普通函数包括 `log/ln/sin/cos/tan/mod/exp/arg`；历史命名函数包括 `lim/max/min/det/gcd/sup/inf/Pr`，当前实现保持旧侧挂规则。`\ast` 映射数学星号 U+2217/BIN。

新命名算子非星号默认侧挂，并只吞一个紧随的 `\limits`；后续修饰仍由因子解析处理。星号形式默认仅 DISPLAY 堆叠，显式 `\limits`/`\nolimits` 优先。`\operatorname{a-b*c}` 直接标点采用名称字形，额外显式组或分式内部仍保留数学结构。

## 已解析但字形或排版仍为近似

| 命令／结构 | 当前限制 |
| --- | --- |
| `\sqrt`、高定界符、矩阵外围括号 | 生产使用固定数学字体变体和同字号部件拼接；连接区单一归属避免重复 alpha 加深。缺配方及旧注入度量保留降级边界。 |
| `\hat`、`\bar`、`\vec`、`\dot`、`\ddot`、`\tilde` | 使用数学组合字形和 top accent attachment；旧注入度量保留原定位近似。 |
| `\widehat`、`\widetilde` | 原生横向变体覆盖较短内容；超范围用固定 stroke 程序轮廓，按统一全局路径分页采样。程序形状不宣称复制字体原生曲线。 |
| `\overline`、`\underline` | 复用规则线覆盖内容宽度，度量来自当前字体及布局常量。 |
| `\mathrm`、`\mathit` | 固定字体按数学 alphabet 选择真实字形；Unicode 无独立 italic 数字，真实字体路径数字保持正体。 |
| `\mathbf`、`\mathnormal` | 真实粗体/粗斜体采用固定数学 alphabet；mathnormal 恢复默认数学选择，拉丁和小写希腊变量默认斜体，大写希腊与数字正体。缺字仍由既有字体匹配器回退。 |
| 数学变量与大运算符 | 使用 MATH 轴、线厚、根号间隙、italic correction、attachment 和伸缩数据；未实现完整 MathKernInfo/所有 OpenType 数学特性，旧上下限产品规则保持。 |
| 矩阵内部间距 | 保留当前比例布局；明确 cell 的 text 样式不等于完整实现 TeX array 排版。 |

字体例子：`a\mathrm{+}b` 保留二元间距，`a\mathrm{{+}}b` 的额外显式组仍按普通组处理；`\mathrm{x_i}^j` 的 x/i 直体，外部 j 恢复默认。显式数学原子字体选择优先于外层富文本斜体，正常／阴影一致；text 和未知命令维持原规则。生产数学根号、重音和定界符同样携带物理字形身份，不接受额外宿主斜切；旧无数学能力度量保留历史路径。

## 字面降级与容错

未知命令保留反斜杠和名称，后续参数按现有数学语法解析；这不表示支持该命令的语义。例如 `\boldsymbol` 尚未实现；本模块仍是明确范围的数学子集。

分式等结构缺参按空组容错；上下标和重音沿既有缺参规则处理；多余闭花括号和无配对的 right/end 等仍按解析器既有规则处理。需要原样显示完整源码时使用代码通道；text 会处理自身转义与嵌套分组，不能把未知命令降级当成稳定的转义机制：未来新增受支持命令时，其显示会变为对应排版语义。

## 验证边界

正式回归验证作用域、字距、脚本绑定、几何及生产绘制采集。软件图复用真实字形，但不等同于游戏 GPU shader、宿主裁剪或外部 TeX 引擎对拍；实际执行与未覆盖范围以本批报告为准。参考引擎对拍（JLaTeXMath）在未配置参考 jar 的环境恒跳过且只出图无断言，属开发期人工工具，不构成门禁。常驻锁另有两条：`LatexPublicSurfaceSnapshotTest`（LaTeX 公共面全成员与跨包接缝双向快照）与 `LatexAssemblySeamPixelLockTest`（伸缩部件接缝的稳定竖直列逐行亮度恒定）。
