# 自有数学与中性图像嵌入实施计划

> **面向 agent 执行者：** 必需子技能：使用 subagent-driven-development，按任务实施本计划。父代理独占 Gradle 构建，按独立文件职责分派，不重复实现。

**目标：** 在正式数学控件中显示含宿主 Item 的积分、分式和上下标，动态重排并复用 UILib 现有字体、图片及唯一场景管线。

**架构：** MathContent 源文及 AST → 自有 MathLayoutEngine 数学盒 → 不可变 MathLayout → SceneMath 文字/rule 与既有 SceneImage 子节点几何。MathFont 是当前样式下 MATH 度量端口，FontTextService 管具名数学字体/同一 FT/HB face 资格，NativeFontFace 提供数学数据，TEXT 和 IMAGE 不新增 GPU 绘制体系。

**技术栈：** Java 8；既有 LWJGL 3.4.2 FreeType/HarfBuzz/OpenType；OFL STIX Two Math；现有反应式 scene、PaintPlan、测试应用。

**开发模式：** 解析与数学盒采用 test-driven-development；字体、GL 和宿主呈现采用 runtime-driven-development。离线 native/GL 不冒充 MC 实机。

**Spec：** [文本规格 §1.4.1](<../specs/2026-10-07-uilib-text-content-spec.md>)；[核心规格](<../specs/2026-10-07-uilib-core-contract-design.md>)、[字体规格](<../specs/2026-10-07-uilib-font-renderer-spec.md>)和[图像规格](<../specs/2026-10-07-uilib-image-spec.md>)各自仍为所属权威。

## Global Constraints

不引入第二 UI/input/font 树，不以 Minecraft ItemStack 或绘制回调构造核心数学 AST。数学子树只为纯内容/几何数据。

当前已有隔离 worktree refactor/uilib-core，基线 a8067069；保留已授权分支。Java 保持 4 空格及完整 build，中文文档/注释。

首期显式 em 嵌入盒：ordinary 原子，宽/高/顶部基线合法有限，稳定直属节点，每标识一次；不宣称任意 SceneNode 自然尺寸/minmax。数学 script scale 作用于盒，图像字体独立绑定。

源码或字体失败完整 fallback 加 UTF-16 来源诊断，其他公式不受影响。无完整 KaTeX 一致性承诺；后续范围留在规格矩阵。

不 push/tag/release，不启动游戏。本次用户直接功能要求，无关联 issue，不编造编号。

## Review Focus

MATH 百分比不按位置量除64；竖向 variant advance 不当横向 advance；伸缩超过真实能力不强拉；LRU 不能使 MathFont 引用失效 native face。字体与其 OFL/版权同包。

测量不得依赖最后一次 cache 覆盖 final MathLayout；presentText 固定同代布局，arrange/paint 只读；未引用 slot 不得出现幽灵输入，普通图像 slot 不可聚焦。padding 与浮点到节点整数盒只转一次。

### 任务 1：正式数学字体接缝

**开发模式：** runtime-driven-development。
**文件：** MathFont（父已定义端口）；FontFace/NativeFontFace；FontTextService；STIX Two Math 字体及许可；字体 native 场景。
**接口：** SceneTextMeasurer.mathFont(TextStyle) → MathFont 或 null。MathFont.constant(Constant)（百分比原值/其余逻辑像素）；text(String) → TextLayout；stretch(codepoint,minHeight) → 覆盖目标高度的真实完整 variant，超限诊断（本期不拼接 assembly）；displayOperator(codepoint,preferredHeight) → 最小满足建议高度的完整 variant，无满足者取最大完整 variant；italicCorrection(TextLayout) → 逻辑像素。BatchTextMeasurer 代理已由父接入。

- [x] 实际取得数学字体 MATH 常量及积分/根式 variant，布局和光栅 key 沿同 FontSource/字号/代际；stretch 不持有跨调用易淘汰的 face。
- [x] 核对伸缩超过能力、缺字、reload 和 native face 关闭等实际后端边界。保留输出原始日志；不静默用默认字体冒充数学字体。
- [x] 任务独立规格/质量审查后进入数学算法任务。

### 任务 2：自有解析与数学盒

**开发模式：** test-driven-development。
**文件：** text/math/MathContent（含内部 Parser）、MathLayout、MathLayoutEngine、MathStyle、MathEmbedMetrics；数学生产集成场景。
**接口：** MathContent.parse(String) 保不可变源码/AST/诊断；MathLayoutEngine.layout(content,TextStyle,MathStyle,SceneTextMeasurer,Map<String,MathEmbedMetrics>) → MathLayout。MathLayout 带 width/height/baseline、文字 pieces、规则线、embed slots、来源诊断与 fallback 信息；原子间距不在业务手算。

- [x] 跑通积分上下限 + embed，被积项可位于 frac/scripts/root；数学字体真度量、MATH axis/script/fraction/radical 常量约束净空。
- [x] 必要反例：双脚本不撞，嵌套分式净空，根式覆盖完整内容，声明样式作用域，UTF-16 诊断，重复/缺 slot、unknowncommand/错配、字体超限显示完整源码。
- [x] 固定小范围常用函数/符号矩阵，不声称整个 TeX；自有解析不运行宏包/宿主动作，源码改变重新解析但样式变不重解析。

### 任务 3：正式数学与图像控件

**开发模式：** test-driven-development + runtime-driven-development。
**文件：** control/SceneMath；internal/devtools 数学页面；manualTest 的既有命令分派；数学场景。
**接口：** SceneMath 在构造期将 embed map 稳定子节点加入唯一树，measure 纯计算，presentText 固定 final MathLayout，arrange → ChildPlacements，paint → TEXT/rule；Image child 由主 pipeline 画。不自行出 IMAGE 或手画 tooltip。

- [x] 正式 /qzcore math 展示积分 Item、frac Item 与 script Item；字号/主题/非法源/恢复/长内容等入口和可见诊断。
- [x] 场景验证 source/theme/em/resource 换代、slot current geometry/clip、Image pending/failed/ready 与 hover overlay、close release；布局不写声明/业务信号，paint 不重排。
- [x] 真实 GL 出图验证数学 glyph、rule、图像同计划像素；记录设备和有效观测区域，保留可打开证据。MC Item 游戏反馈待用户。

### 任务 4：整体验收与交付

**开发模式：** verification-before-completion。
**文件：** gradle/verification.gradle.kts（必要任务接入）；开发者数学使用/支持矩阵/人工步骤；外部证据目录。

- [x] 最终独立整分支审查，修复已证实规格/质量问题并验证。
- [x] 完整 build manualTestJar 与必要 math native/GL 场景成功，格式和本地链接核对。
- [x] 按授权精确本地提交，保留包和指南；本计划以本次提交作为交付载体。

提交后的版本戳重包及双实例备份部署沿此前持续授权，在确认游戏未运行且配置不改的条件下执行，结果记入构建目录之外的交付清单；新数学游戏实机状态保持待用户。
