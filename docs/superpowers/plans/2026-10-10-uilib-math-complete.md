# 数学完整能力实施计划

> **面向 agent 执行者：** 使用 subagent-driven-development 按有界任务实施；父独占Gradle、集成图像检查和提交。独立只读调查可并行，生产共享文件任务依赖顺序执行。

**目标：** 自有数学引擎完整接近固定 KaTeX 0.16.21 数学能力，不再由用户逐阶段筛查，最后统一实机验收。

**架构：** MathContent解析一次生成来源明确的不可变数学结构，自有数学盒生成MathLayout；横竖字形构建复用真实字体服务，SceneMath投影同一TextLayout/SceneImage/PaintCommand，不引入第三方数学运行引擎或第二树。

**技术栈：** Java8、HarfBuzz/FreeType、STIX Two Math、既有Scene集成/GPU平台。

**开发模式：** 语法及稳定数学盒采用test-driven-development；native字体和GPU呈现采用runtime-driven-development。用户授权平台真实输出图像自主验收，中间不需MC人工，最后一次用户实机。

**Spec：** 文本规格第0/1.4.3，字体规格2.9/2.10及现核心contract。

## Global Constraints

保留现有隔离refactor/uilib-core worktree，基线10166a6e8c；不push/tag/release、不启动游戏、不改版本策略。
优先复用UILib自有scene/信号/PaintCommand/通用控件/字体资源，禁止原版直连、绘制回调、bitmap轮廓变形或第二字体/场景树。
中文文档、注释、报告、提交；禁PowerShell，命令唯一环境脚本execWithEnvironment；不设sandbox_permissions。
父独占Gradle与最终格式/提交，子代理不派生代理，不自行构建/提交，冻结所属文件后交父验证。
原始source与UTF16来源保留；错误整式原文+定位诊断，不半解析，不新建补偿/重试分支。
嵌入id与节点唯一，可共享来源；数量/tooltip/clip生命周期继续归既有SceneImage。
实际KaTeX固定能力全部进入支持矩阵，功能存在和视觉质量分别验证；暂未完成不能写完成。

## Review Focus

宏来源与局部定义作用域→错误定位/重复Item风险→独立参照语料及原始source诊断，任务3/4。
多轴glyph→错误方向或连接→真实native facts和GPU接缝，任务1/6/7。
数学颜色/框/斜线/phantom→投影与测量分歧→唯一MathLayout/PaintCommand及隐藏Item资格，任务6/7。

### Task 1：真实多轴数学字形

**开发模式：** runtime-driven-development。
**文件：** MathAxis（新），MathFont，FontFace，MathData，NativeFontFace，MathAssembly，FontTextService，MathFontScenario。
**接口：** MathAxis.HORIZONTAL/VERTICAL；FontFace新增mathVariants(int,MathAxis)/mathAssembly(int,MathAxis)，现单参竖向入口和旧度量字段可继续；MathFont新增stretch(int,float,MathAxis)并保留stretch(int,float)，新增topAccentAttachment(TextLayout)无单字形数据时返回NaN。MathData与Result增加中性stretchAdvance，旧verticalAdvance保持竖向兼容。
- [ ] HB_DIRECTION_LTR/TTB同时传variants/assembly/minOverlap，按真实数据构造横向/竖向parts；原始advance/connector轴语义准确。
- [ ] 复用MathAssembly纯一维重复/overlap算法，横向按左到右offset、统一baseline定位，不拉轮廓；top-accent附件点从真实字体快照读取。
- [ ] 正式STIX多em/目标width测宽hat/tilde/overbrace/箭头的实际能力与原始facts、reload/generation/italic；保原竖向完整variant优先与大算子策略。父跑mathFontScenario/native原始日志后独立任务审查。

### Task 2：固定参照支持矩阵与符号数据

**开发模式：** test-driven-development。
**文件：** 新MathSymbols/MathCommandCatalog与资源表、固定语料；不改布局/字体。
**接口：** 每项保持command、mode、unicode、原子类别、font类别与别名；统一查询和可枚举目录，不在解析/布局各复制清单。
- [ ] 基于固定tag真实symbols/functions/environments/macros及插件defaults构建命令/符号/环境/宏清单，标注默认trust=false结果；计数代码计算。
- [ ] 生成静态symbol facts保许可来源，unicode glyph和类别准确；独立参照接受/拒绝语料留JSON，运行依赖不包含KaTeX。
- [ ] 每族覆盖实际解析与布局，符号类别不能靠unicode字符串猜代替；支持状态由行为证据填写，无未覆盖命令消失。

### Task 3：数学词法与宏作用域

**开发模式：** test-driven-development。
**文件：** 新MathLexer/MathMacroExpander与MathContent入口，宏资源表、MathMacroScenario。
**接口：** 展开tokens具有原始sourceStart/sourceEnd；宏expansion只发生parse，original source保留。MathContent.parse入口保留，增加显式不可变MathMacros上下文输入和解析结果的nextMacros供调用方串联同文档公式；parse不修改调用方状态，不共享隐藏全局。普通单公式parse使用新上下文。固定插件globalGroup=true的文档内续定义通过显式nextMacros表达；文档重置交上层共同文档入口，不在数学创建文档树。MathContent现字符Parser可消费展开序列，但其Node与Diagnostic最后必须统一映射原source范围，宏定义和token词法不能在layout重做。
- [ ] 控制词/控制符、注释、分组与参数，固定内置宏、参数替换、def/gdef/edef/xdef/let/newcommand/renewcommand/providecommand及局部scope；支持与参照实际一致。
- [ ] 有界展开及循环diagnostic指回源文；不得外部执行或文件读取；定义生命周期限此解析，不共享隐式global业务状态。
- [ ] 独立参照语料覆盖定义/覆盖/嵌套/转义/分组/无效参数/递归和展开后的独立/重复Item；父运行后任务审查。

### Task 4：完整结构解析

**开发模式：** test-driven-development。
**文件：** MathContent（可拆MathParser职责），内部Node，MathParseScenario。
**接口：** 用明确Kind及不可变payload表示二维行列/样式/重音/尺寸/颜色/框等语义，来源来自任务3；不让value字符串隐含不相关参数协议。
- [ ] 环境matrix/pmatrix/bmatrix/Bmatrix/vmatrix/Vmatrix/smallmatrix/cases/dcases/rcases/array/aligned/align/alignedat/alignat/gather/gathered/equation/split等固定实际环境，嵌套与&/行分隔作用域正确、l/c/r和竖分隔/行线等声明准确。
- [ ] 分式genfrac/above/over/atop/choose、fixed delimiters、字体与文字作用域、算子limits/primes、accent/overunder/arrows、size/color/box/phantom等函数及其参数语义按固定清单实现。
- [ ] 结构错配、token缺失、非法尺寸、matrix列约束、不可trusted Web command按参照真实结果；原文不会半解析，错式后邻公式独立。

### Task 5：二维数学盒与对齐

**开发模式：** test-driven-development。
**文件：** MathLayoutEngine及集中MathArrayLayout职责，MathArrayScenario。
**接口：** 各cell沿现有Context生成Box，列宽/行ascent/descent源于真实内容与style；同一MathLayout来源/Item。
- [ ] 按环境style与列规则布置cell，短行补空cell、空行/单元、显式行距与lines，行baseline、列l/c/r、aligned关系间距和整体axis定位。
- [ ] 环境外括号调用现有真实delimiter，外侧scripts沿既有数学盒净空；Item尺寸/数量动态、style子级、nested fractions可达。
- [ ] 真实STIX/独立Item反例验证左右列对齐、行净空、嵌套、脚本、非法恢复，父运行并任务审查。

### Task 6：完整数学盒与样式绘制

**开发模式：** test-driven-development + runtime-driven-development（字形/绘制部分）。
**文件：** MathLayoutEngine（可拆单一职责helper），MathLayout，SceneMath必要投影，MathDecorationScenario。
**接口：** MathLayout是唯一几何来源；增加颜色/绘制原语时与既有PaintCommand对齐，不能绕过标准pipeline。TextPiece与rule各持实际颜色/可见性事实，phantom隐藏墨迹及Item但保尺寸。
- [ ] 字体字母映射/真实成员、文字分组style、显式size/dimension、atom强制类别、operator/large glyph、一般fractions/delimiters/scripts与间距沿参照语义。
- [ ] 窄重音使用真实附件点和glyph，字体上下括号使用横向assembly；宽帽/倒帽/波浪按数学路径的明确形式求几何，箭头布局族明确杆/头/label结构，完全复用通用LINE/quad，不走字体失败后fallback。路径不是glyph，不改source轮廓或位图；净空/数学轴/墨迹入同MathLayout并独立图像验收。overline/underline按规则线语义绘制。
- [ ] color/textcolor/colorbox/fcolorbox/boxed、cancel/enclose、phantom/hphantom/vphantom/smash/raisebox/rule/kern等沿同一MathLayout与PaintCommand，动态主题字号正确，source与generation保留。
- [ ] 图像比较各布局族、边界/净空与重载后同代事实；原点平移signed delimiter不变量及原字形尺度保持，父实跑任务审查。

### Task 7：完整集成平台图像验收

**开发模式：** runtime-driven-development。
**文件：** 复用/扩展MathGlScenario/MathSceneScenario，完整语料runner及CoreMathPage统一演示，数学指南/支持矩阵。
- [ ] 所有登记能力有对应运行证据；逐布局族多em/theme生成GPUPNG并读取图像，symbol批量sheet与宏/环境/Item组合，不只检查parse。
- [ ] 原始native/font/geometry/GPU与固定KaTeX语义对照；连续性、行列、附件点、规则线、颜色与隐藏Item资格符合目标；问题修正后同输入回归，不删反例。
- [ ] 更新能力状态，未完成项继续实现；正式及人工包隔离，统一 /qzcore math 最后实机入口，既有quantity/tooltip/clip/reload保留。

任务6依赖Task9通用图元与局部子树绘制接口，Task9与字体/静态目录不共享文件，可独立推进。

### Task 8：整体验证与集中交付

**开发模式：** verification-before-completion。
- [ ] 任务评审+完整范围审计+最终整分支独立审查，关闭真实Critical/Important；AOSP格式，完整build/manualTestJar/mathGlScenario以及新增实际任务。
- [ ] 用户直接任务无issue，沿既有本地提交授权分可验收增量提交；最终提交版重打正式/人工包、Forge身份与hash验证。
- [ ] 安装前重查游戏，未运行才备份更新普通/GTNH并逐配置hash不变；若运行完成包与具体可审查安装准备，避免热覆盖。
- [ ] 独立_evidence保原始日志/图像/参照/审查/包/部署与完整支持矩阵，一次交用户实机验收；不把平台GPU结果冒称MC实机。

### Task 9：通用斜线图元与局部子树装饰绘制

**开发模式：** runtime-driven-development + test-driven-development（顺序合同）。
**文件：** PaintCommand、OrderedQuadBatch、GlQuadRenderer、GlPaintPlanRenderer、通用软件batch消费者、SceneControl、SceneFramePipeline；新PaintPrimitiveScenario与必要现有batch回归。不得改MathLayout/SceneMath/字体/解析/Gradle注册。
**接口：** PaintCommand.line(float x0,float y0,float x1,float y1,float thickness,LayoutBox clip,int color)封闭不可变端点/线宽事实，不引用live节点；生产GL/软件消费同四角quad，不用锯齿矩形阶梯。SceneControl.paintAfterChildren(SceneNode,SceneGeometryView,PaintPlanBuilder)默认no-op；pipeline在此node有效几何子树结束后、下一兄弟内容之前调用，不走全页tooltipoverlay。直接端点0长线明确定义空绘制。
- [ ] 四角quad保旧轴对齐构造，顶点位置可表达非轴对齐，UV/索引/clip/batching沿既有抽象；resource/state仍有序，所有真实消费者更新，不在某host硬编码数学。
- [ ] 局部afterChildren严格唯一树当前presentation顺序，reverse close descendants，captured fallback递归/overlay分支不重复；phantom或未定位孩子不产生额外输入/绘制。
- [ ] 真实集成场景观测line方向/线宽/半透明/clip像素，以及parent→child→afterChild→sibling→tooltip顺序；父独占运行和GPU图像，不声明MC。报告及任务审查后冻结，供Task6消费。
