# LaTeX 排版能力建设规划

## 目标与执行边界

在确认缺陷修复之后，改善小字号公式的可读性、数学字形一致性及高结构的伸缩质量。继续使用 LatexParser → MathLayoutService/MathMetrics → MathBox → 字体批次/PaintCommand 的既有链路，不新增独立公式渲染器，不依赖原版 GUI 或 Tessellator。

用户已指示开始 LaTeX 排版能力建设，首个实施增量为 B1 内部离散数学样式与深层脚本封顶。B0 根号接缝修复已随 69686e04 交付，B1 与 B2a 实际结果见文末；其余批次仍按依赖推进。本规划不替代对公共 API、默认字体、依赖及既有入口行为变化的具体裁定。

## 现状与约束

- 已有基础：MathBox 保存基线相对坐标、advance、左右 ink 越量、字形与规则线；MathMetrics 注入真实字体度量；TextLayoutService 与绘制共用有效字号量化；LatexCache 根据源码、字号、字体类别、运行时版本、布局版本和 inkEpoch 失效。
- B1 已建立根字号相对的离散 MathStyle 与 cramped 转换，scriptscript 后封顶。B2a 将其用于显式样式命令；display 与 text 的分式间距有明确差异，算子默认上下限位置仍按既有规则。
- TextSegment 仅有 text/style/latexSource，没有独立数学模式；MarkdownInlineParser 明确将 `$...$` 与 `$$...$$` 都交给同一行内公式入口，`<latex>` 亦无 display 状态。不能只在扫描时识别双美元而让后续复制、缓存丢失模式。
- 行内大运算符默认 limits 上下堆叠是现有产品决策；命名算子也有独立模式。新数学样式不得悄悄改变这些默认值。
- 根号和定界符目前整字等比缩放，越高越宽、笔画越粗；GlyphElem 只有文本、坐标、scale、italic，没有字体 face/glyph-id 或 MATH 变体信息。替换字体本身不会自动获得伸缩拼接能力。
- 软件渲染复用生产几何和字形，双线性采样可用于连接检查；它尚未完整模拟实机 shader 的多抽头 AA、smoothstep 和宿主裁剪。

## 分批建设

| 批次 | 具体交付 | 依赖与完成标准 |
| --- | --- | --- |
| B0：根号接缝与视觉验收基础 | 修复斜笔与横线的细缝，保存同公式同字号的修复前后图、原尺寸像素放大图及高 renderScale 重绘图；补真实字形连接回归。 | 当前优先修复。连接处需达到可见亮度连通，不能只检查 quad 相交或非零 alpha；保留线厚、clearance、右端覆盖与完整包围盒。 |
| B1：离散数学样式 | 在布局内部建立 display/text/script/scriptscript 与 cramped 状态，字号从公式根字号推导；scriptscript 后封顶，所有递归分支采用明确转换规则。 | B0 完成后首个实现增量。优先内部实现，现有公开入口仍使用 text、旧 limits 默认不变。脚本深度增加不再无限缩小，盒与实际字形使用同一有效字号。 |
| B2a：样式型命令 | 支持 dfrac/tfrac 和 displaystyle/textstyle/scriptstyle/scriptscriptstyle，明确组内作用域与显式覆盖。维护“支持／近似／字面降级”命令清单。 | 依赖 B1。dfrac/tfrac 必须真正切换分式样式，不能作为 frac 别名。未知命令保持现有字面降级；AST 扩展如果触及公开枚举或构造器，先给出兼容影响。 |
| B2b：数学字体样式命令 | 逐批实现 mathrm/mathit，再评估 mathbf、operatorname 等常用命令，保留数学原子类别和函数间距。 | 依赖 B1 与字体可用性评估；需要字体身份承载时随 B3 交付。不得用 text 替代 mathrm 导致数学字距、分组语义丢失。 |
| B3：数学字形与度量 | 评估现有本地字体、许可与符号覆盖，选择主数学字形及缺字回退方案，统一 ASCII/希腊变量、数字和算子的风格。 | 先小样和资源评估，实际接入前裁定默认字体或新增资源/依赖。复用 FontCatalog、GlyphGenerator、字体页及既有缓存；数学斜体不得再重复斜切。 |
| B4：伸缩结构与宽重音 | 先为一个定界符家族验证变体选择与部件拼接，再扩展根号、其他括号和 widehat/widetilde。 | 依赖 B3 的可用字形/部件证据。高度增长时笔画不失控变粗；接头既连通又不过黑，尺寸单调、数学轴稳定、父盒包含全部部件。保持在既有绘制计划内；不足的承载能力先给最小接口方案。 |
| B5：真实 display 入口与宿主集成 | 独立决定双美元是否成为 display 或块公式，以及行高、居中、超宽处理、复制与命中行为。 | 依赖 B1，但不阻塞脚本封顶和后续内部排版修正。该批改变既有入口行为，需明确裁定再实施。复用 Markdown 块/行布局、scene 与既有滚动视口。 |

B1 与 B3 的字体小样调研可以并行；B2a 在样式状态稳定后实施。B2b 的字体承载需求先核实，不能为了命令覆盖绕开字形体系。B4 按可用部件逐族推进，不一次建设通用字体解析框架。

## B1 的最小可交付增量

1. 用内部不可变样式状态替代散落的递归倍率；保留现有公开 layout、forLatex 入口签名。现有入口初始化为 text，display 状态先用于内部测试与下一批显式命令。
2. 明确转换表：上标继承 cramped，下标进入 cramped；根体 cramped、根指数固定 scriptscript；分子按当前样式转换，分母使用对应 cramped；组、矩阵、定界符和重音按各自转换规则传递状态；矩阵/cases 单元若要求 text、重音若要求 cramped，应显式转换，不能靠 helper 默认值意外重置。转移规则对照参考实现后定为测试规格，不能机械照搬当前 helper 的默认参数。
3. 字号相对根字号使用 text=1、script=0.7、scriptscript=0.5；多层脚本停留在 scriptscript。量化继续复用 LatexFontSize，审查 Builder.addBox 的 scale 传播，避免“度量缩一次、展平再缩一次”。
4. 复查 script 样式下原子间距、sup/sub drop 和分式 clearance。只修明确的样式语义差异，每个改变附独立几何断言与小字号样张。
5. 更新布局版本，验证缓存命中与字体异步就绪后重排。仍不启用新的美元语法或改变默认 limits；这些有独立兼容决策。

## 验收矩阵

| 维度 | 必须覆盖 |
| --- | --- |
| 结构 | 普通根号、嵌套根号、分子/分母根号、高矩阵根号、根指数、深层上下标、嵌套分式、脚本运算符、重音、left/middle/right、文本混排。 |
| 尺寸与缩放 | 正文字号 12/14/16/24px；低字号边界和非整数中间字号；renderScale 1、1.25、4、8；含非整数起点。原尺寸 nearest 放大与高分辨率重绘分别标注。 |
| 字体 | 两套明确记录名称/文件版本的真实字体，覆盖常用符号；缺字时显式记录 fallback。固定字体夹具若需新增资源，先确认许可和仓库资源策略。 |
| 几何与像素 | 独立计算 bbox 包含、基线、clearance、字距和推进；局部连接以可见亮度连通检查，保留连接区域像素与参数，防止近透明像素掩盖断缝。不可只测试内部计算公式的复述。 |
| 缓存 | 同源码不同有效样式不得错命中；runtime reload 与 inkEpoch 变化后重布局；无变化场景继续使用现有缓存，不为规划新增第二套缓存。 |
| 集成 | 普通文本、富文本、Markdown 页面、表格单元与显式聊天；测量、绘制、行框和超宽处理使用同一逻辑像素事实。 |
| 验证等级 | 每批正式回归及完整 build；软件样张验证实际执行后记录。实机由用户或授权 CI 回验，不把软件通过写成 GPU 或宿主裁剪通过。 |

## 参考对照方法

既有 LatexReferenceComparisonTest 依赖开发者固定 jar 路径，缺失时跳过，而且参考字号与自身字号不一致；它目前是开发辅助工具。后续将参考版本、来源及调用路径显式配置，统一公式、数学样式、逻辑字号、输出缩放与基线。

先对 AST 语义、字体无关的几何不变量建立强回归；字体不同的参考图主要比较结构、间隙和比例，不要求逐像素相同。只有固定同字体同采样环境才适合像素差异阈值。缺参考工具时明确标记未执行，不能用跳过数量证明对拍成功，也不为运行时引入参考引擎。

## 需具体裁定的行为与接口

- **双美元语义**：若改为 display/独立块，需先给出现有行中双美元、跨行输入、列表/表格中的变化示例，并明确行高、折行、超宽和交互后果。旧 forLatex 与 `<latex>` 的默认模式必须写清。
- **默认 limits**：分别列 sum/prod、int、lim/max/min 的默认位置；显式 limits/nolimits 优先。是否跟随 display/text 切换须独立裁定，不能随 B1 顺带改掉旧卡片行为。
- **公开类型扩展**：新增 TextSegment 模式、AST 枚举、MathMetrics/GlyphElem 能力或 FontType 值时，列出旧构造器、第三方实现和穷举分派的兼容方案。模式还需贯穿段复制与映射、MarkdownDocument、MarkdownTableModel、TextContentModeStrategy，以及 LatexCache 键。
- **字体与依赖**：实际替换默认数学字体、增加分发资源或第三方 MATH 解析依赖前，提交覆盖对比、许可、回退、包体和生成成本评估。

这些裁定是对应实施批次的边界，不阻塞已授权的 B1 内部建设。后续涉及公共 AST、默认字体资源或 display 宿主行为时，应先提交具体兼容方案。

## 完成定义

生产代码实施批次交付源码与正式回归、同条件样张、完整构建结果和剩余限制，并创建本地增量提交；只读字体调研和兼容裁定交付证据即可，无需制造代码或资源变更。B1 内部样式与 B2a 显式命令已交付，下一批为 B2b 数学字体样式命令；字体与伸缩资源采用独立可复核的小样决策。上述验收矩阵是分批扩展的目标，各批实际完成范围分别记录。

## B0 本轮实际交付

根因：横线恰从根号 ink 包围盒右缘开始，而包围盒不代表斜笔尖存在足够强度的可见像素；纹理 alpha 边缘与独立规则线之间形成细缝。修复在 MathLayoutService 中将横线左端向斜笔内搭接一个规则线宽，右端、顶部、厚度和内容字形坐标保持；布局版本更新为 18。MathMetrics 只更新对应说明，未改接口签名。

- LatexSoftwareRenderKit 新增仅用于测试的 renderScale 入口，将倍率真正传到生产 adapter，保持布局字号；同时保存原尺寸 nearest 放大图。
- LatexRadicalSeamRegressionTest 覆盖普通/嵌套/根指数/上标/分母根号，字号 12/14/16/18/24、renderScale 4/8，以约半覆盖和四分之三覆盖的亮度阈值做局部八邻域连通检查。只恢复旧接头表达式的隔离负向控制会失败，证明回归能击中旧缺陷。
- Python 对同条件样张检查：修复前 100 项连接判据均断开，修复后 100 项均连通。16px、8 倍图中普通与嵌套根号的旧接头，在半覆盖/较强覆盖阈值下分别至少有 2/3 个像素间隙；搭接为 0.64 逻辑像素，在该倍率下为 5.12 像素。
- 全量 `gradlew.bat build --offline --console=plain` 成功。Python 汇总 385 个测试类、4284 项测试，4278 通过、6 跳过、0 失败、0 错误。
- 完整构建产物重新生成的 80 张 PNG 与已验证修复样张逐字节一致。主代理目视检查原始前后图及对照图。

制品：`build/reports/latex-radical-seam/comparison.png`（左旧右新，同为16px、8倍实际重绘）；`before/`、`after-built/` 保存原图；`pixel-connectivity.json`、`numeric-summary.json`、`joint-profile.csv`、`build-verification.json` 保存像素判据、数值和构建证据。制品在 build 下，不纳入 Git。

本轮使用 testkit 当前真实字体环境，未覆盖两套真实字体、非整数绘制起点或完整 shader AA 参数矩阵；未运行游戏客户端/GPU。这些保留为后续验收扩展和实机回验范围，不将软件复现写成实机已确认修复。以上是 B0 交付时的覆盖范围；B1 的后续进展单独记录如下。

## B1 内部数学样式

生产实现继续沿用 MathLayoutService/MathMetrics/MathBox 链路，内部 MathStyle 保存公式根字号、display/text/script/scriptscript 级别及 cramped 状态。各级字号直接从根字号按 1/0.7/0.5 推导并通过 LatexFontSize 量化，后续脚本停留在 scriptscript。对子盒只换算字形倍率，不重复缩放已经度量的坐标和规则线。布局版本更新为 19；现有公开入口仍使用 text，limits 默认位置与美元入口语义保持。

| 结构 | 明确转换 |
| --- | --- |
| 上标／下标 | display/text → script，script/scriptscript → scriptscript；上标继承 cramped，下标强制 cramped。 |
| 分子／分母、组合数 | display → text → script → scriptscript，二级后封顶；分子继承 cramped，分母强制 cramped。 |
| 根体／根指数 | 根体同级 cramped；根指数固定非 cramped scriptscript。 |
| 组、复合基底、left/middle/right | 保留父样式。 |
| 重音／overline／underline | 普通重音和 overline 内容 cramped；underline 继承；规则线结构的外层脚本使用实际盒作参照。 |
| 矩阵及 cases 单元 | 显式切到非 cramped text；外围数学轴仍取所在样式。 |
| 自动间距 | script/scriptscript 只保留 Ord→Op、Op→Ord、Op→Op、Close→Op、Inner→Op 的细间距；显式 kern 保留。 |

审查同时发现旧水平拼接用包围盒最大宽度充当推进量，导致负 kern 被夹到零；本批将推进与视觉边界分开，根级包装也保留完整盒语义。正式回归用独立的负推进与实际后续字形左移预期验证，避免仅对照布局自身返回值而漏检。输入字号的无效值转换沿用现有有效字号策略。display 状态在内部回归中验证，本批尚不提供新的对外 display 入口。矩阵内部间距仍为现有比例近似，普通字体整字伸缩与数学斜体近似留待 B3/B4。

参考复核入口为 KaTeX v0.16.22 的 Style.js、spacings.js 与 functions 下各结构实现。本轮网络全文抓取受工具 DNS 判定阻挡，未逐行下载核验，也未运行外部引擎对拍；回归以明确转换表和几何不变量验证。

### B1 验证结果

- 新增 MathStyleGeometryRegressionTest，覆盖状态转换、非整数与低字号、深层脚本盒包含、cramped 传播、矩阵和根指数、脚本间距与负 kern、display 分式及异常字号。生产 collector 回归扩展深层上下标在各 renderScale 的实际字号，并独立验证负细间距使后续字形左移。
- 布局包针对性测试 56 项通过；完整 `gradlew.bat build --offline --console=plain` 成功。Python 读取实际 JUnit XML 汇总：386 个测试类、4295 项测试，4289 通过、6 跳过、0 失败、0 错误。包含既有缓存命中、runtimeVersion/inkEpoch 失效与根号接缝回归。
- 旧版编译类先独立快照，新版样张由完整构建产物生成；同一探针、Arial 与 Times New Roman、字号 12/14/16/24、renderScale 1/1.25/4/8。前后各 256 组实际重绘、320 张 PNG（含原尺寸 nearest8）；Python 核验样张矩阵一致、非零墨迹、有限 quad 坐标与文件存在。256 组中 224 组图像发生变化，这是观察值，不作为质量通过阈值。
- 主代理实际查看深层脚本、嵌套分式与根指数样张；软件图中深层脚本保持可辨识字号。几何回归与最终静态复审通过。未运行游戏客户端、GPU shader 或外部参考引擎对拍。

画布边界检查额外发现：24px 文本混排的固定起点样张有顶部触边与负 Y quad。B1 最终同帧扩画布诊断严格确认新版 Arial 存在可见像素裁切；旧版触边仅说明风险，不能据此断言已丢失可见像素。独立公式的深脚本对照图有边缘余量；混排样张不能作为“完整显示无裁切”通过证据。后续需单独完善 testkit 的包围画布与宿主行框验收，不将该现象直接推定为游戏裁剪结果。

制品位于 `build/reports/latex-style-b1/`：`comparison.png` 是精选原像素对照总图，`comparison.html` 是逐样本前后对照，`before/` 与 `after/` 含原图和字体记录，`comparison.csv`、`glyph-comparison.csv` 与 `build-verification.json` 保存数值证据。仍未覆盖非整数绘制起点、中文混排、逐字形 fallback 归属或固定字体文件版本；本轮使用本机已安装字体，不新增分发资源。

## B2a 实施前的承载方案

现有 AST 节点为公开不可变类型，Kind 分派后按具体类型强转。样式声明需要作用于当前数学列表后续节点，不能把这段列表包成普通 GROUP，否则会改变原子类别、二元符号降级和间距；也不能借文本或 SPACE 的特殊值传递样式。

建议下一批采用明确的增量 AST 样式承载：保留既有 Kind、parse/layout 入口和全部旧构造器，旧调用默认 INHERIT；新增不可变的样式覆盖信息及只读访问能力，分式区分自身 dfrac/tfrac 覆盖与外层脚本的声明上下文。内部按节点身份的旁表虽然可避开旧签名修改，但有重建节点丢状态、外部遍历不可见及生命周期成本，不作为首选实现。具体签名与迁移说明已收敛到《设计-LaTeX样式命令B2a》，用户在方案交付后指示“继续”，本批按已批准方案实施。

必测作用域包括：`a+\scriptstyle b` 保持同一数学列表；`{\scriptstyle a}b` 不泄漏；脚本内显式 display 可放大；分子局部覆盖不影响分母；矩阵单元/行边界关闭局部声明；`\dfrac ab^2` 保留正确脚本绑定；text 内容与未知命令继续字面处理。命令样式已包含在源码缓存键中，复用现有 LatexCache，不增加另一套缓存。

## B2a 显式数学样式命令

用户批准具体接口方案后，本批实现 `\dfrac`、`\tfrac`、`\displaystyle`、`\textstyle`、`\scriptstyle`、`\scriptscriptstyle`。AST 新增 MathStyleOverride 与兼容构造器重载，LatexFrac 另存 FractionStyle；旧 Kind、旧构造器与 parse/layout 签名保留。显式命令从字面降级变为数学语义，重建 AST 的外部调用者应传递新增字段。具体接口与迁移影响见《设计-LaTeX样式命令B2a》，支持／近似／字面降级范围见《LaTeX命令支持清单》。

声明标注当前列表的完整因子，子组、脚本、分子分母和矩阵单元局部作用域隔离；分式自身 display/text 不覆盖外层脚本环境。布局用右因子的声明样式确定前置自动间距，保留跨声明 BIN 分类；字形倍率在返回边界归一，坐标、规则线和 advance 不重复缩放。布局版本更新为 20，继续使用既有 LatexCache。

审查修正了两个边界问题：middle 前尚未产生因子的声明，借既有 part 组的入口样式保存，middle 两侧各取对应右侧样式计算间距；array 附加样式时完整保留未使用列的对齐说明。后者仅在当前 parser 构造期保存原始列说明，最终 AST 自含数据，不依赖持久旁表。

实际验证：

- 完整 `gradlew.bat build --offline --console=plain` 成功。Python 汇总实际 JUnit XML：389 个测试类、4327 项测试，4321 通过、6 跳过，0 失败、0 错误。新增解析、几何和生产绘制回归均通过。
- javap 检查旧编译产物与新编译产物中选定的 13 个类，旧 public/protected 签名及描述符没有缺失；不将此声明扩大为任意第三方同名子类源码均兼容。
- Arial 与 Times New Roman 分别实际运行新增生产 collector 回归并出图；字号 12/14/16/24，实际 renderScale 1/1.25/4/8，共 384 次重绘，含 nearest8 的 PNG 总数为 480。样张按实际批次边界建立画布，像素 origin 保留 .25/.5；Python 检查全部边界有限、落在画布内、非空且无边缘墨水。
- 精选图包含 12 对样张，原 PNG 像素原样粘贴，Python 验证对应区域像素一致。主代理查看总图及小字号 nearest8：分式样式、局部脚本恢复和组作用域结构符合预期；部分原尺寸小字号仍有笔画偏弱，高分辨率重绘不能替代原尺寸清晰度验收。

制品位于 `build/reports/latex-style-b2a/`：`comparison.png` 是显式源码左右对照，**不是不同提交的修复前后图**；`after/index.html` 提供完整矩阵，`after/manifest.json`、各字体 samples.tsv/geometry.csv、`build-verification.json`、`numeric-verification.json`、`api/verification.json` 保存验证证据。样张不纳入 Git。

未执行游戏客户端/GPU shader、外部参考引擎对拍；没有验证固定字体文件版本、逐字形 fallback 归属或中文混排。样张画布完整性仅适用于本 probe，不代表宿主行框或裁剪已验证。数学字体质量、整字伸缩及宽重音仍属于 B2b/B3/B4；美元入口与默认 limits 保持现有语义，B5 尚未实施。
