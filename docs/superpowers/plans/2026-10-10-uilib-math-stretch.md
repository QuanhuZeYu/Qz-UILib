# 数学伸缩结构实施计划

> **面向 agent 执行者：** 使用 subagent-driven-development 按任务实施；父代理独占 Gradle 与提交。

**目标：** 自有数学引擎支持真实部件拼接的大型根式及 left/middle/right、binom，包含已有 Item。

**架构：** FontFace 脱离 native 的 MATH facts → FontTextService.stretch 不可变 TextLayout → MathContent 自有 AST 与 MathLayoutEngine 数学盒 → 既有 SceneMath/Text/SceneImage/PaintPlan。

**技术栈：** Java8、现有 FreeType/HarfBuzz、正式 STIX Two Math、唯一 scene。

**开发模式：** 字体/GPU采用 runtime-driven-development，解析与数学盒采用 test-driven-development。

**Spec：** [文本1.4.2](<../specs/2026-10-07-uilib-text-content-spec.md>)、[字体2.9](<../specs/2026-10-07-uilib-font-renderer-spec.md>)及已有核心合同。

## Global Constraints

保留已授权隔离 worktree refactor/uilib-core，基线d3364cc04b；不改核心架构/版本策略，不 push/tag/release，不启动游戏。
优先复用UILib自有scene/信号/PaintCommand/通用控件；不能直连原版绘制/第二字体树/bitmap拉伸。
中文注释/报告；无关联issue，是用户直接授权的下一阶段。父独占Gradle，子代理不派生代理、不提交、不设置sandbox_permissions、禁PowerShell。
普通font候选和大算子策略不变，Item继承既有中性标识与独立节点生命周期。

## Review Focus

字体竖向parts按底到顶，原生26.6与逻辑像素一致；connector overlap合法，所有extender等次数增加维护花括号对称，原advance沿glyph本体而非ink硬贴齐；缓存及换代不持native地址。
围合组与花括号作用域不能串层，middle一次定高不递归影响目标；binom沿stack净空无fraction rule。MathFont已有STACK常量22..27，DISPLAY_STYLE命名；不新增或猜测编号。

### 任务 1：真实字体部件拼接

**开发模式：** runtime-driven-development。
**文件：** FontFace、MathData、NativeFontFace、FontTextService（可新增集中部件定位纯算法），MathFontScenario、MathGlScenario必要GPU探针由父集成。
**接口：** MathFont.stretch(int,float)仍返回现有TextLayout。FontFace新增竖向assembly快照事实（parts/glyphId/startConnector/endConnector/fullAdvance/extender、minOverlap、italicCorrection），无MATH后端默认返回无能力。
- [x] 核对正式HB绑定signature与真实STIX数据，超完整variant后构建底到顶部件，按OpenType连接规则增extender/求overlap。零extender候选无效连接时进入有extender候选，不把可构建判断为永久失败。进展和规模有明确资源/几何边界，不无限循环。
- [x] 部件同字体同em不缩放，x沿原advance，y按原字形原点和fullAdvance定位，不把ink裁切当构造锚点。TextLayout承载真实墨迹/尺寸/来源，italic沿assembly事实；不修改GPU后端。
- [x] 正式native场景测圆括号、花括号、根号多个em及多高度、连接约束、单variant优先、reload及旧fontwrapper可用；每次由父跑raw日志，子实现冻结后规格/质量审查。

### 任务 2：自有定界符及二项式

**开发模式：** test-driven-development。
**文件：** MathContent、MathLayoutEngine、MathLayoutScenario。
**接口：** 原有parse/layout入口不变，新增内部围合组、middle标记及binom类型；不扩大AST长期公共API。
- [x] 解析同分组配对left/right、多middle、嵌套、点不可见及明确token矩阵；错误UTF16来源，不能半解析。围合组可带脚本。
- [x] 以内部非本层定界墨迹ascent/descent和MATHaxis求高度，对照KaTeX0.16.21 leftRightDelim（delimiterFactor901，delimiterExtend 为 5pt，在基准 ptPerEm=10 时换算为根主题字号的 .5em，数学 script 样式不会再缩小该减量）目标，围绕axis居中；每侧可不同glyph variant高度。外侧inner，middle relation，两侧Open/Close间距；null delimiter TeX空间为根主题字号的 .12em，沿固定KaTeX baseSizingClasses重置基准字号，不再随script缩小。
- [x] binom按现有STACK_TOP_SHIFT_UP / STACK_BOTTOM_SHIFT_DOWN / STACK_GAP_MIN及DISPLAY_STYLE常量求净空，居中并圆括号；outerOrd；使用现有fraction数学子样式。
- [x] 真实layout必要反例：多层/错配分组、missingtoken及invalidtoken、middleoutside、不可见、非对称高Item、大型嵌套分式、四style、binom无rule、source范围与独立绑定、脚本净空；父跑raw并任务审查。

### 任务 3：正式呈现与交付

**开发模式：** runtime-driven-development + verification-before-completion。
**文件：** CoreMathPage、MathSceneScenario、MathGlScenario、数学接入指南。父集成。
- [x] /qzcore math加高根式/定界符/middle/binom与Item的可见演示；动态源/主题/em/字体代际及同scene画像/clip保持，诊断和恢复可操作。
- [x] 真实GL显示部件连续性与rule/Item同计划，截图读图，多个em与目标height连接处像素观测。KaTeX固定参照只用于语义/尺寸规则，不复制浏览器字体像素。
- [x] 更新指南能力支持表及人工步骤，AOSP格式，完整build manualTestJar mathGlScenario、包隔离、最终独立审查。
- [ ] 成功后任务文件自动本地提交，提交版正式/人工包重打核版本与hash，若游戏未运行则备份部署普通/GTNH、配置hash不变；用户游戏实机另记，不启动。
