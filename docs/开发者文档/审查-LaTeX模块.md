# LaTeX 模块审查

审查基线：分支 4.0，提交 bb56ccbd。用户提出“回头审查 latex 模块，效果并不完美”。本轮仅审查并生成隔离探针与渲染证据，未修改生产代码、正式测试、公共 API 或依赖。

> 修复进展：后续修复已完成 L01–L13 的代码与正式回归，完整 build 和真实字形软件复验通过。下文缺陷证据保留审查基线事实；修复结果见文末。

## 结论

确认 13 项缺陷：1 项 P1、12 项 P2。优先处理高根号的负间隙和包围盒漏包，以及正常输入的脚本绑定、环境边界、空文本异常、度量与绘制不一致。现有专项测试通过不能证明这些组合正确。

视觉不足还来自明确的能力限制：没有完整的 text/script/scriptscript 数学样式，深层脚本持续缩小；伸缩定界符整字等比放大；常用样式命令仍有缺失。这些需要制定排版规格，不能靠随意微调常量解决。

P1 表示应优先修复的几何完整性缺陷；P2 表示正常输入下应修复的局部语义或几何错误。未把任何一项推断成已实测的游戏崩溃。

## 确认缺陷

源码路径相对仓库根；行号对应审查基线。以下 Parser 指 src/main/java/club/heiqi/uilib/font/latex/LatexParser.java，Layout 指同目录 layout/MathLayoutService.java。

### L01 / P1：高内容根号出现负间隙，父盒漏包

- 位置：Layout:649、657、704、715。
- 触发：`\sqrt{\begin{matrix}x\\x\\x\\x\end{matrix}}`。
- 原因：最后一档根号尺寸仍小于目标时，继续将负余量的一半加到 clearance；随后用根号线的位置覆盖 builder.height，丢弃已合并的内容上界。
- 真实字形、24px 探针：矩阵 h=54.300003、d=42.300003；根号盒 h=35.88、d=42.12；线中心 y=-35.4、厚度 0.96。Python Decimal 验算：横线下沿距内容顶为 -19.380003px，父盒漏报内容顶部 18.420003px。横线已进入内容。
- 正式测试同款 mock 度量的 10px 隔离探针也重现，排除仅由某一字体造成的假阳性。
- 建议：最高档不足时按目标继续扩展，避免负余量；最终盒必须包含内容、根号、规则线和根指数。补高矩阵/高分式嵌套及盒包含性回归。
- 证据：build/reports/latex-audit/12-24.png、bounds.txt。样张采用既有 testkit 原点，顶部截断来自坐标超出软件画布；不宣称已完成真实 scene/GPU 裁剪验证。

### L02 / P2：伸缩括号内命令的上下标失去绑定

- 位置：Parser:428。
- 触发：`\left(\alpha_i+\sum_{i=1}^{n}x_i\right)`。
- AST 和真实字形均确认：alpha、sum 成为裸原子，下划线作为正文显示，n 绑定到错误的组；去掉 left/right 后绑定正确。
- 原因：括号中的普通命令走 parseCommand，绕过 parseFactor 脚本绑定。
- 建议：保留 right/middle/end 边界处理，普通内容统一走因子解析；补命令、脚本、嵌套定界符组合测试。
- 证据：build/reports/latex-audit/13-24.png。

### L03 / P2：裸命令参数吞掉外层脚本

- 位置：Parser:370。
- 触发：`\frac23^2、\sqrt x^2`。
- 证据：前者解析为 Frac(2, SupSub(3,2))；\frac{2}{3}^2 则是 SupSub(Frac(2,3),2)。Python Fraction 验算错绑值为 2/9，应为 4/9。
- 原因：裸参数调用 parseFactor，超过“一个 token 参数”的边界。
- 建议：裸参数只取原子，脚本由外层 factor 消费；不能要求调用方强制补花括号来掩盖解析错误。

### L04 / P2：合法空文本抛异常

- 位置：Parser:644；同目录 node/LatexAtom.java:80。
- 触发：`\text{}`。
- AST 和真实软件渲染入口均抛 IllegalArgumentException: text 不能为空。LatexCache、TextLayoutService 正常测量与 MarkdownLineLayout 入口没有局部捕获/降级；未据此宣称客户端崩溃。
- 建议：空文本使用既有空组表达，保留 Atom 非空约束；验证解析、测量与绘制入口。

### L05 / P2：嵌套矩阵结束符留给父层，后续行列逃出环境

- 位置：Parser:504、516、520。
- 触发：`\begin{pmatrix}\begin{matrix}a&b\end{matrix}&c\\d&e\end{pmatrix}`。
- 证据：外矩阵只留下一个含内矩阵的单元，后面的 &、c、行分隔、d、&、e 成为顶层原子。
- 建议：退出前消费本层匹配的 end；测试嵌套环境、最后一行和环境后的普通内容。

### L06 / P2：非 BMP 数学字符被截成非法 UTF-16

- 位置：Parser:195、196。
- 触发：`𝑥^2`。
- 证据：索引跨过完整码点 U+1D465，输出只保留高代理项 U+D835，低代理项丢失。
- 建议：按 codepoint 或完整源 substring 构造原子；字体覆盖能力是另一问题，解析不能先损坏文本。

### L07 / P2：数学空白切断脚本绑定

- 位置：Parser:155、156。
- 触发：`x _i ^2 对照 x_i^2`。
- 证据：有空白时得到五个裸原子，无空白时为一个 SupSub，违背数学空白忽略的现有约定。
- 建议：每轮检查脚本/修饰符前跳过数学空白并处理 EOF；补空白插入的语义等价测试。

### L08 / P2：limits 修饰串改前面的无关运算符

- 位置：Parser:269、295、299。
- 触发：`\sum_{i=1}^n+\lim\nolimits_{x\to0}x`。
- 证据：sum 的 limitsFlag 被改为 NOLIMITS，lim 保持 DEFAULT。
- 原因：全局 lastBigOperator 用于当前 factor 的修饰，而 lim 等命名算子不更新它。
- 建议：修饰符局部绑定当前有效操作数，不能回溯修改无关节点。

### L09 / P2：text 内嵌套分组提前结束文本域

- 位置：Parser:614、638。
- 触发：`\text{a {b} c}+x`。
- 证据：TEXT 只有 a {b，c 逃入数学域，文本分组和空白语义丢失。
- 建议：跟踪文本组深度，分组括号不输出，转义括号保留。

### L10 / P2：所有复合节点都归 Inner，产生多余字距

- 位置：Layout:296。
- 触发：`xx、x{x}、xx^2`。
- 正式测试同款 mock 度量、10px 下，后一个 x 的起点分别为 5、6.666667、6.666667px。仅加分组或上标就改变基底左侧字距。
- 原因：非 Atom 一律 Inner；脚本丢失基底类别，也影响带脚本运算符的上下文字距。
- 建议：脚本继承 base 类别；普通 group/root/accent 明确为 Ord，fraction/fenced 等按自身类别处理。补结构等价输入，不能只测裸原子字距表。

### L11 / P2：nolimits 大算子平移后未更新包围盒

- 位置：Layout:542。
- 触发：`\sum\nolimits，或将其嵌入分式/根式`。
- 正式测试同款 mock 度量、10px 下，轴居中后实际 ink 高深为 7.5/2.5px，返回盒仍为 8/2px。
- 建议：按平移后的 ink 算高深；轴居中本身正确，需守盒包含性，不能只检查 glyph Y。

### L12 / P2：数学度量与实际字形使用不同整数字号

- 位置：src/main/java/club/heiqi/uilib/font/layout/TextLayoutService.java:1094、1209、1257；src/main/java/club/heiqi/uilib/font/api/DefaultFontRendererAdapter.java:1117、1143。
- 触发：`正文 14px 的 x^2；正文 10px 的二级脚本`。
- 证据：数学度量截断字号，绘制四舍五入。Python float32 复算：前者按 9px 度量、10px 绘制；后者按 4px 度量、5px 绘制。
- 已有 measureSizePx 补偿只统一推进宽度，未统一实际字形大小。这里不报告已修过的逐字推进漂移，也不宣称所有公式都会碰撞。
- 建议：共用有效字号量化策略，或沿既有链路统一浮点字号；补非整数字号脚本的真实 ink 包含性测试。

### L13 / P2：无字形公式的推进在绘制中丢失

- 位置：src/main/java/club/heiqi/uilib/font/api/DefaultFontRendererAdapter.java:1156、1158；配合 1025。
- 触发：`A<latex>\quad</latex>B 对照 AB，字号 24px`。
- 证据：quad 的 MathBox 宽度 24px、无 glyph；两个输入的渲染终点均为 37（包含 testkit 原点），PNG 逐字节相同，B 没有右移。包含实体字形的 \quad x 正向对照能产生推进。
- 原因：盒宽差补偿仅附在本段末 glyph；无 glyph 时没有条目承载推进。prepare 更新 segmentStartX 不直接推进 replay 的 currentX。
- 建议：在既有绘制计划/展平流程内明确承载无字形推进，不能靠假可见字符或 L3 偏移绕过布局事实；补纯间距、纯规则线及前后普通文本测试。
- 证据：build/reports/latex-audit/bounds.txt、bounds-0.png、bounds-1.png。

## 视觉能力限制与既有决策

| 项目 | 当前事实 | 后续建议 |
| --- | --- | --- |
| 数学样式 | MathConstants:18 标记 scriptscript 预留；Layout:309 等递归乘 0.7，没有完整离散样式和深层封顶。小字号深层脚本持续缩小。 | 明确 display/text/script/scriptscript 和 cramped 的传递，用真实小字号样张验收。 |
| 命令覆盖 | 样张中的 \dfrac、\tfrac、\mathrm、\widehat 按未知命令字面保留。 | 列常用命令与降级策略，再扩展语义；未知命令降级不能冒充支持。 |
| 定界符字形 | Layout:772 使用整字等比放大，高度增加时宽度和笔画一起增加。 | 先评估既有字形管线承载变体/拼接的能力，再决定字体资源方案。 |
| 数学字形风格 | 变量主要采用普通字体斜切近似；ASCII 变量与希腊符号的识别范围不同，样张存在风格差异。 | 单独制定数学字形目标；换字体不能替代解析和几何修复。 |
| 大算子 limits | Layout:319 明确记载行内默认上下堆叠是既有产品决策。 | 若改为随数学样式切换，说明行为变化；本报告不把默认堆叠列为 bug。 |

## 测试为何未拦住

本轮独立 JVM 重跑七个既有测试类：LatexParserTest、MathLayoutServiceTest、LatexCacheTest、TextLayoutServiceLatexTest、LatexSoftwareRenderTest、LatexRuleBaselineMissingCellTest、LatexReferenceComparisonTest。共 129 条运行记录，127 项通过、0 失败，2 项参考工具因缺固定路径 jar 而 assumption skip。Python 从本轮输出汇总计数。

- 解析测试覆盖基础定界符、脚本和单层矩阵，缺少本文的组合与语义等价输入。
- MathLayoutServiceTest:513 附近的根号测试重算同一阶梯公式，没有最高档不足的反例；261 附近的 nolimits 期望仍用移动前高深。
- “有墨水”“有 quad”“输出确定”守住基础链路，不能证明公式含义和结构正确。
- LatexReferenceComparisonTest:59 依赖开发者机器固定 jar 路径；生成对照图不是视觉误差门禁。现有 ref 22px 与 ours 16px 放大 2 倍的并排图不能直接当同字号像素误差依据。
- 本次未下载参考引擎，未完成新的外部引擎对拍；旧 latex-compare 图不作为当前实现正确性的证据。

## 验证与制品

当前 LaTeX 源码隔离编译，复用上一轮完整 build 的其他生产类和既有测试类。生产树在审查期间未改变。解析与几何另有独立探针，分别新编译当前解析器和布局源码。

真实字形样张复用 LatexSoftwareRenderKit：TextLayoutService → GlyphGenerator → 软件页装配 → DefaultFontRendererAdapter → FontSoftwareRasterizer。测量、字形生成和 quad 收集与生产同源，最后一步为软件光栅化；未运行 runClient、GPU 或真实宿主输入。

主要制品（build 目录未纳入 Git）：

- build/reports/latex-audit/current-formulas.png：12/16/24px 完整拼图，显示放大 2 倍；同目录保留单公式原尺寸 PNG。
- build/reports/latex-audit/visual-metrics.txt：源码、字号、MathBox 和字体现场。
- build/reports/latex-audit/bounds.txt：真实根号、纯间距、空文本证据。
- build/reports/latex-audit/tests.txt：本轮各类测试计数。
- build/reports/latex-audit/verification.json：Python Decimal/Fraction/float32 验算、测试汇总与 SHA256。

关键制品 SHA256：

- current-formulas.png：bbded9357f9b34f0c141411c189887f6274f086bb944d242da15d72cf6175676
- 12-24.png：7e6d6b9082a6d0e195dd0daedd2911063e8e6eb479ad904d8fdf05d99e1b6b36
- 13-24.png：8beb9fc7a4b79f93990231600bc86f8648c5da60e233545f1d6ae47d89130e3e

工作站 D:/Code/MC/Qz工作站/temp 保留 LatexAuditVisual.java、LatexAuditBounds.java、latex_audit_visual_run.py、latex_audit_verify.py、LatexParserAuditProbe.java、latex_parser_audit_probe.py、GeometryAudit.java、run_geometry_audit.py、latex_size_check.py 及原始输出。这些是审查探针，不是已合入的正式回归测试。

本轮仅新增报告，未重跑全量 build；上面的专项验证已实际执行。修复生产代码时仍须正式回归和完整 build。

## 建议修复顺序

1. **语义与几何完整性**：先修 L01 根号漏包；L02–L09 脚本绑定、环境边界、空文本和 Unicode。各项补能击中旧行为的回归，不放宽现有断言。
2. **测量与绘制一致性**：修 L10–L13 原子类别、移动后盒边界、有效字号和无字形推进；按 12/14/16/24px、至少两套真实字体核验 ink、基线和嵌套包围盒。
3. **排版质量提升**：确定数学样式、常用命令、字体/定界符策略，再建立同样式同尺寸参考对照。涉及公共 API、依赖或既有兼容承诺时按仓库规范说明后果，本报告不提前裁定。

实现继续复用 UILib 的 MathMetrics、MathBox、字体和 PaintCommand/scene 管线，不接入原版 GUI 或绘制包装类。审查阶段尚未确认根号横线右覆盖量、limits 的 ink 中心相对误差，未将其列入上述缺陷；后续修复的补充证据见下节。

## 修复结果

用户授权“开始修复”后，完成 L01–L13。生产修改集中在 LatexParser、MathLayoutService、TextLayoutService、DefaultFontRendererAdapter，新增 font/internal/LatexFontSize 统一内部字号量化；不增加对外 API、依赖或字体资源。布局缓存版本由 16 更新到 17。

| 范围 | 实现与回归 |
| --- | --- |
| L02–L09 解析语义 | 普通因子统一绑定脚本，裸参数只取原子，limits 仅修改当前 OP；完整保留 Unicode 码点、忽略数学空白；空文本返回既有空组，文本按组深度解析；矩阵消费本层匹配结束符。LatexParserRegressionTest 直接检查 AST 归属与容错边界。 |
| L01、L10、L11 数学布局 | 根号超过最高档后继续按现有 ink 度量扩展，并保留子盒/规则线/根指数并集；脚本继承基底类别，普通组/根式/重音使用 Ord；nolimits 高深跟随轴移动。MathLayoutGeometryRegressionTest 使用独立 AST 与两套 mock ink 度量，检查逐元素包含性和结构等价字距。旧 nolimits 回归同步为移动后期望。 |
| L12 字号一致性 | 测量与实际字形共用 max(1, truncate) 策略，仅对整数附近 4 ULP 内的缩放噪声归一化。公式内逐码点推进直接复用 MathMetrics，避免显式 size/sup 样式再次缩放。 |
| L13 无字形推进 | PreparedText 在真实字形边界独立保存无字形段宽度，覆盖前/中/尾/整行、连续正负间距及 renderScale；没有占位字形。纯规则线公式使用本段字体 ascent，保留规则位置和终点。LatexSizeAndAdvanceRegressionTest 通过生产 collector 和真实字形批次验证，亦覆盖空文本入口。 |

修复时补充了根号横线右端的独立反例：根号 ink 右缘小于 advance，而内容放在 advance 之后，旧公式将横线终点错误地锚到 ink 右缘。OFFSET_INK 回归明确构造该度量，并断言线覆盖内容右侧；终点改为同时考虑 ink 右缘与实际内容起点。这是本轮新增的几何证据，不追认审查阶段已完成真实字体反例。

### 实际验证

- 完整 `gradlew.bat build --offline --console=plain` 成功，包含生产/测试编译、checkstyle、测试与制品任务。Python 读取本轮 JUnit XML：384 个测试类，4283 项测试，4277 通过、6 跳过、0 失败、0 错误。新增三个回归类共 25 项测试；另修正既有 nolimits 测试的参照边界。
- 用本轮完整构建的生产/测试类重新生成 12/14/16/24px 样张，复用原软件渲染链路；没有复用审查期隔离生产类。
- 24px 四行矩阵根号：内容 h=54.300003、d=42.300003；根号盒 h=56.460003、d=42.300003；线中心 y=-55.980003、厚度 0.96。Python Decimal 验算线下沿距内容顶部 1.200000px，父盒包含内容。
- `A<latex>\quad</latex>B` 与 `AB` 的绘制终点分别为 61、37（含 testkit 起点），Python 验算差 24px，图像不再相同。纯 quad 终点 28、空 text 终点 4，符合起点 4 加各自宽度。
- 主代理检查完整差异并目视查看高根号、括号命令脚本及完整样张。样张沿用 testkit 的字体环境；未完成两套真实字体的逐项对拍，也未运行游戏客户端、GPU 或新的外部参考引擎。

修复后制品位于 `build/reports/latex-fix/`：`current-formulas.png`、`visual-metrics.txt`、`bounds.txt`、`verification.json`（Python 汇总及原始 PNG SHA256）。审查制品 `build/reports/latex-audit/` 保留用于前后对照，两者均不纳入 Git。

本轮解决确认缺陷；离散数学样式、常用命令扩展、专用数学字形及定界符拼接仍属于下一步排版能力工作。小字号深层公式可读性和极高根号的宽度/笔画膨胀仍需后续改善。
