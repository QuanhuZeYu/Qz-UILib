# LaTeX 剩余规划实施契约

## 状态与目标

用户要求继续完成全部排版规划。本文把 B2b 后续、B3/B4 字体与伸缩、B5 display 的具体可见行为和公共兼容后果集中供审查；本文是拟实施契约，不是完成报告。已交付 B0、B1、B2a、B2b 首批 mathrm/mathit 保持原交付记录。

所有实现复用 LatexParser → MathLayoutService/MathMetrics → MathBox/GlyphElem → 字体任务/页面 → collector/PaintCommand，以及 Markdown/scene/滚动宿主。不开辟独立公式渲染器，不使用原版 GUI 或 Tessellator，不引入生产参考引擎。

## B2b：字体与完整命名算子

### 用户可见规则

- 支持 `\mathbf`、`\mathnormal`、`\operatorname`、`\operatorname*`。现有 `\mathrm`/`\mathit` 作用域与字号层保持。
- `\mathbf` 选择局部粗正体数学 alphabet，复用真实 BOLD 度量和字体页；它不是对所有符号通用加粗的 `\boldsymbol`。`\mathnormal` 显式复位局部数学字体选择，同时屏蔽宿主斜体再次叠加。嵌套最内层选择覆盖外层，参数外恢复。
- 在 B3 接入前，普通字形仍沿用既有字体规则；B3 完成后数学 alphabet 使用固定数学字体内的真实字形。普通正文的默认字体不改变。
- `\operatorname` 对外分类为 OP，主体保存完整数学参数树，支持空参数、显式间距和嵌套结构；不拍平成非空字符串，也不改为普通 text。名称主体直接文本原子按名称排版，不产生普通 BIN 数学间距；直接减号/星号使用名称字形。额外显式组、嵌套字体、分式和根式内部保留数学原子类别与字距，不递归归一整个子树。该边界已用 KaTeX 0.16.22 operatorname.js 及实际 AST/HTML 树探针核验，来源为固定版本 npm 镜像包，证据在工作站 temp/latex-operator-katex-0.16.22/。
- 普通命名算子默认侧挂，参数后仅吞一个紧随的 `\limits`（允许通常数学空白），对应 amsopn nolimits@ 的单次 lookahead；它不是永久禁止 limits 的 AST 标志。之后的修饰交由常规因子解析，最后一次生效。星号形式仅有效 DISPLAY 样式默认上下堆叠，显式 `\limits`/`\nolimits` 优先。自然字号和基线不因堆叠改变，不套用会放大大符号的旧 layoutLimits。已实际读取 amsopn.dtx v2.04；KaTeX 0.16.22 星号 DISPLAY+ nolimits 的偏差不作为目标。名称主体分组按前述 KaTeX 子树边界定义，不声称与任意 TeX mathcode 宏作用域完全等价。
- 既有 sum/int/lim/max/min 等默认上下限位置保持；旧 LIMITS_OPERATOR 注释与实际侧挂行为不一致，修正文档描述，不能借新命令改旧产品行为。

### 公共增量

保留旧构造器、旧方法和旧枚举成员顺序。

```java
// LatexNode.Kind 末尾追加 OPERATOR。
public final class LatexOperator extends LatexNode {
    public LatexOperator(LatexNode body, boolean limitsInDisplayStyle);
    public LatexOperator(LatexNode body, boolean limitsInDisplayStyle,
                         MathStyleOverride mathStyleOverride);
    public LatexNode getBody();
    public boolean isLimitsInDisplayStyle();
    public int getLimitsFlag();
    public void setLimitsFlag(int limitsFlag);
}
// MathFontStyle 末尾追加 BOLD、MATH_NORMAL。
// GlyphElem 追加兼容重载及不可变字体选择：
public GlyphElem(String text, float x, float y, float sizeScale,
                 boolean italic, boolean inheritTextItalic, MathFontStyle mathFontStyle);
public MathFontStyle getMathFontStyle();
// MathMetrics 追加 default 能力，旧实现可继续链接：
default MathMetrics forFontStyle(MathFontStyle style);
```

LatexOperator body 非 null，可为空组；limitsFlag 复用 LatexAtom 现有三态常量与解析期 setter 约定。旧 GlyphElem 构造器默认 INHERIT。MathMetrics 默认返回原 metrics，第三方实现要覆盖该方法才有对应字重的准确度量。

外部 AST switch/visitor 必须处理新增 OPERATOR，重建节点及字形须保留新增元信息。内部 parser 的样式复制、透明组、limits 定位、脚本基底和遍历都同步更新。测量与绘制必须选择同一字体；不能仅变位图或通过段尾补差掩盖度量错误。

## B3/B4：固定数学字体、度量与伸缩

### 实物资源与可见影响

推荐原样捆绑 STIXTwoMath-Regular.otf，内嵌版本 **2.12 b168**，配套 OFL.txt。实际取得自 [清华 CTAN 镜像](https://mirrors.tuna.tsinghua.edu.cn/CTAN/fonts/stix2-otf.zip)。数学 face 为 838508 bytes，SHA-256 `95bc2729e41faf93b0bcae9e96c4dc4da45855067fd0581e621e30734fe8d90b`；OFL 为 4882 bytes，Python 验算两者未压缩合计 843390 bytes，不含元数据且不等于最终 JAR 增量。OFL 1.1 允许随软件分发，保留版权与许可，不修改/子集化原字体、不捆绑无关 Text face/PDF。Cambria Math 6.99 只作本机对照，不进入分发包。

数学字母、数字、符号优先使用该固定数学字体。普通正文和 `\text` 保留现有字体排序。真实数学 italic/bold 字母通过明确 Unicode 映射及例外表选择物理 glyph，不能再次几何斜切或伪粗体。缺字回退到既有 FontMatcher，并记录实际 face；结构部件必须来自同一 face，不跨字体拼接。此举会改变既有公式宽度、行高、断行和观感，是默认数学字体变更。

MATH 数据以与字体 SHA 绑定的离线生成数据承载，读取所需常量、字形度量、变体和拼接配方，不新增 Java 解析依赖。保留 B1 根字号 script=0.7、scriptscript=0.5 的已交付比例；不静默切换为 STIX 表中的 scriptscript=0.55。数学轴、线厚、根号间隙、真实 italic correction、top accent attachment 等使用对应字体数据，并逐项回归。

### 承载与公共兼容

在上述 B2b 字形字体选择之外，追加平台无关的明确字形身份。拟采用以下最小公开数据与能力入口（内部实现不向 AST/MathBox 暴露 AWT Font、路径或 GL）：

```java
public final class MathGlyphRef {
    public enum Kind { FONT_GLYPH, PROCEDURAL_ACCENT }
    public static MathGlyphRef forFontGlyph(String faceKey, int glyphId);
    public static MathGlyphRef forProceduralAccent(ProceduralAccentSpec spec);
    public Kind getKind();
    public String getFaceKey();
    public int getGlyphId();
    public ProceduralAccentSpec getProceduralAccent();
    // 值语义 equals/hashCode；错误kind的专属getter抛IllegalStateException。
}
public final class ProceduralAccentSpec {
    public enum Kind { HAT, TILDE }
    public static ProceduralAccentSpec of(Kind kind, int profileRevision,
        int widthUnits, int heightUnits, int strokeUnits);
    public Kind getKind();
    public int getProfileRevision();
    public int getWidthUnits();
    public int getHeightUnits();
    public int getStrokeUnits();
    // 每em固定65536单位，尺寸和stroke必须正值，profileRevision为正。
}
// GlyphElem 新增末尾带 MathGlyphRef 的兼容重载，旧构造器默认 null。
public GlyphElem(String text, float x, float y, float sizeScale,
    boolean italic, boolean inheritTextItalic, MathFontStyle mathFontStyle,
    MathGlyphRef mathGlyphRef);
public MathGlyphRef getMathGlyphRef();
// MathMetrics 新增可选能力，默认 null 继续既有路径。
default MathFontSupport mathFontSupport();
```

MathFontSupport 采用以下能力接口；字体引用、程序形状、度量与配方均为不可变值，旧 MathMetrics 返回 null 时沿既有路径。

```java
public interface MathFontSupport {
    MathGlyphRef resolve(int codepoint, MathFontStyle style, FontType weight);
    MathGlyphMetrics measure(MathGlyphRef glyph, int effectiveSizePx);
    MathFontParameters constants(int effectiveSizePx);
    MathGlyphConstruction construction(MathGlyphRef glyph,
        MathStretchAxis axis, int effectiveSizePx);
}
public enum MathStretchAxis { HORIZONTAL, VERTICAL }
```

公开不可变值类型范围固定：MathGlyphMetrics 保存 advance、inkLeft/Top/Right/Bottom、italicCorrection、hasTopAccentAttachment/topAccentAttachment；MathFontParameters 保存本批使用的数学轴、规则线厚、根号普通/display间隙、根号额外上伸/指数抬升及重音基础高度；MathGlyphConstruction 保存有序 Variant(glyphRef,stretchAdvance) 和可选 Assembly(parts,minConnectorOverlap,italicCorrection)，Part 保存 glyphRef/startConnector/endConnector/fullAdvance/extender。各值类使用与这些字段同序的构造器和同名 get/is 只读方法，防御复制列表，拒绝 null/非有限/非法负尺寸及不满足connector范围的数据；所有几何量均为有效字号下 logical px，只换算一次。可选 attachment 用显式 has 标志，缺 construction/无法解析的字符返回 null，不以 .notdef 伪成功。来源引用不依赖平台对象，不把 glyph-id 编码成 Unicode/PUA。MathGlyphRef 是内容身份，不含 generation，不能声称凭该值判断引用年龄。faceKey 必须完整绑定资源 SHA、face index 与影响字形选择的 profile；同内容身份可以跨代复用。provider 只接受当前已注册且身份一致的资源，不将旧 faceKey 偷换成另一个 face。任务 token、页槽引用和绘制计划另携 generation/requestId，旧代结果与计划必须按既有屏障拒绝。程序形状身份由版本化 profile 和规范化尺寸决定，同样与运行代际分开。

固定点程序形状参数只决定形状，不包含 AWT 或 atlas；运行时 key 加 generation、raster size 和 tile 身份。超过 atlas 单槽宽度的形状，在生成侧按同一全局路径分片绘制，保留采样 padding、核心区不重复叠加 alpha，不先分配超大整图。各片继续受同页面预算和驱逐管理，逻辑 MathBox 保留整个形状的 advance/ink。该分片和生命周期必须实际验收后才能将任意宽重音范围标为完成。

数学 face 与元数据在现有 candidate/generation 中原子发布，faceKey 包含资源身份，generation 验证防旧盒指向新版字体。GlyphGenerator 增明确 glyph-id 分支，通过同一物理字体 createGlyphVector(int[])、drawGlyphVector 生成；复用现有 probe、裁边、页面和上传。GlyphPageManager 在原码点数组之外维护稀疏数学字形记录，继续同一 claim/token/dispatcher/mailbox/page budget/驱逐/reload/inkEpoch 生命周期，不建第二套 atlas。缓存/绘制计划不能绕过旧 generation 屏障。

外部 GlyphElem 复制者需保留新引用，外部 MathMetrics 可维持旧路径但不会自动获得数学字体质量。公开能力中的新增类型/签名、runtime token 身份扩展属于本批公共兼容范围，不伪装为纯内部改动。

GlyphRequestToken、GlyphGenerationTask、GlyphGenerationResult 也是公开类型，现有 token 构造器强制合法 Unicode，worker 构造器无条件读 codepoint，必须同步增量适配：

```java
// GlyphRequestToken
public enum Kind { CODEPOINT, MATH_GLYPH }
public static GlyphRequestToken forMathGlyph(int generation, long requestId,
    MathGlyphRef glyphRef, int rasterSize, int tileIndex);
public Kind getKind();
public MathGlyphRef getMathGlyphRef();
public int getRasterSize();
public int getTileIndex();
// GlyphGenerationTask 与 GlyphGenerationResult 同步提供
public GlyphRequestToken.Kind getKind();
public MathGlyphRef getMathGlyphRef();
```

旧构造器保持 CODEPOINT 分支和旧 getter 语义；新数学分支 getCodepoint/getFontType 抛 IllegalStateException，不能返回假码点、PUA或伪字重。旧 getCodepoint 消费者必须先分派 kind 后才处理新任务/结果。数学字形已解析物理样式，其余信息来自 glyphRef；rasterSize为正，tileIndex非负，普通未分片数学字形为tileIndex零。相同形状、rasterSize及固定分页profile确定tile裁片，worker使用token中的尺寸且拒绝冲突，不另设可漂移的第二份尺寸。旧 token 的数学专属 getter 同样拒绝错误kind。现有 GlyphGenerationTask(token, size, priority) 保留签名，内部按 kind 取信息；结果冻结像素和数学元数据时不得再强制构造码点 GlyphInfo，数学元数据走明确分支，原getGlyphInfo仅适用于CODEPOINT。所有正常码点任务/结果保持兼容。

### 伸缩结构

STIX 实物数据确认：圆括号各有 13 个竖向变体及端部/重复段配方，根号有 4 个竖向变体及拼接配方。先交付圆括号族，再扩展其余已有可用配方的括号和根号。选择满足目标的最小原生变体，超过上限使用同字号部件与合法连接重叠，保持数学轴和笔画，不按总高整体放大。父盒以真实部件 ink 并集包围，advance 独立；根号继续 RuleElem 横线并以实际末端连接。限制异常尺寸/重复数，覆盖异步就绪、页面预算和驱逐。

`\widehat`/`\widetilde` 查询 U+0302/U+0303，不能查询现有 ASCII ^/~。两者各有 6 个横向变体，最大 advance 为 2385 设计单位，**没有 assembly**。原生变体范围按数学字体直接绘制；超过范围使用明确固定 stroke 的程序形状字形，由现有 GlyphGenerator 生成、现有页面管理，不能用横向拉伸近似顶替规划完成。程序形状需要明确的可判别身份（形状种类、目标宽、高、stroke、profile 版本及 generation），不得伪造字体 glyph-id。形状小样、接口与实际生产路径验收完成前，本项保持未完成。

为区别横线与宽重音，LatexAccent 新增 `AccentMode { FIXED, RULE, WIDE }`、带 mode 的构造重载和 getAccentMode，旧 stretchable=true 仍仅表示 RULE。宽重音通过字形来源中的程序形状分支承载目标尺寸，不额外增加 scaleX，也不复用 sizeScale 改变整个结构高度。最终字形来源重载保留所有旧签名，固定 stroke 只在字形生成层使用 AWT 路径，不新增通用 PaintCommand path API。

### 必须补齐的证据

资源 SHA/许可、AWT 实际 glyph-id 绘制、数学映射例外、fallback face、轴/线厚/间隙单位换算、圆括号每个变体阈值和 assembly 重复、根号接缝、宽重音上限内/外、两个 face 同 gid 不串页、过期 token/plan、异步重排、预算/驱逐，都需要正式回归和实际图。当前已完成资源/数学表取证及独立 AWT glyph-id 小样：STIX 实际加载与请求 glyph-id 绘制成功，四字号样张与固定 stroke 宽重音可审查。主代理已查看两张总图；该环境是 Java 25，未经过 UILib 字体页或游戏 shader，不能写为生产绘制通过。小样在工作站 temp/b3b4-font-evidence/awt-probe/，包括 comparison.png、procedural-accent-comparison.png、字体和耗时记录。

## B5：数学样式与块公式宿主

### 输入行为与迁移

| 输入 | 拟实施行为 |
| --- | --- |
| `$x$` | 既有单美元货币/空白规则，根 TEXT，行内原子。 |
| `a $$x$$ b` | 根 DISPLAY，保持行内，不强制前后换行或整行居中。 |
| 独占内容行 `$$x$$` | 新布局出口生成 MATH_DISPLAY 块。 |
| 独占行 `$$` 开/闭栏，中间多行 TeX | 新布局出口生成单个数学块，保留正文换行和空白。 |
| `$$2+2$$`、`$$ x $$` | 双美元允许数字起始及两端空白；空或全空白体不识别。 |
| 未闭合、转义、连续三个或更多美元 | 字面容错，不从长美元串偷取双美元，不吞后文。 |
| 表格单元中的 `$$x$$` | DISPLAY 行内原子，服从单元列对齐，不产生块。 |

行中双美元只在同物理行配对；多行数学仅由独占行围栏承载。不支持开栏同行有正文而跨行关闭的混合形式。块开/闭行位于当前列表/引用内容列后零至三个空格，闭合后只能空白；代码通道优先。围栏不跨列表项、引用边界，不靠 lazy 续行开闭；数学内部空行独立于表格开关保留。表格继续先按既有未转义 pipe 分列，数学不改 GFM 分列规则。

采用既有表格的显式出口迁移方式：MarkdownDocument.toLayoutContent 提供新数学块和 DISPLAY；历史 toSegments/toLayoutLines 保留旧段落投影、完整旧美元词法和旧双美元 TEXT 表现。不能仅把 DISPLAY 结果改回 TEXT：旧入口对数字开头/首尾空白的拒绝与原跨行关闭规则也由内部 LEGACY policy 保留，policy 必须贯穿旧 walk/inline 路径；literalBlocks 本身只能隔离块树。新旧出口以 `$$2+2$$`、`$$ x $$`、行中双美元跨行等输入分别回归。直接 MarkdownInlineParser.parse 的双美元变 DISPLAY 属于明确视觉变化。旧 forLatex、旧 layout/cache 入口及无属性 `<latex>` 保持根 TEXT。页面和聊天都消费新 Content 出口，不能仅有表格时才启用内容宿主。

### 公共增量

```java
// TextSegment
public static TextSegment forLatex(String latexSource, TextStyle style,
                                  MathStyleOverride mathStyle);
public MathStyleOverride getLatexMathStyle();
public TextSegment withStyle(TextStyle style);
// MathLayoutService
public MathBox layout(List<LatexNode> nodes, float baseSizePx,
                      MathMetrics metrics, MathStyleOverride mathStyle);
// LatexCache
public MathBox getOrLayout(String latexSource, int baseSizePx, int runtimeVersion,
    FontType fontType, MathLayoutService layoutService, MathMetrics metrics,
    int inkEpoch, MathStyleOverride mathStyle);
// TextLayoutService
public MathBox getLatexBox(TextSegment segment, int baseFontSizePx);
// MarkdownLayoutLine.Kind 末尾追加 MATH_DISPLAY。
```

新增根样式 INHERIT 归一 TEXT，null 拒绝；普通段 getter 返回 INHERIT。withStyle 仅替换样式引用而保留公式源码/数学样式；需要隔离时调用方传 style.copy()。getLatexBox 要求公式段，内部持 generation 锁并复用完整缓存键；根数学样式纳入已有 LatexCache，无第二套布局缓存。所有 copy/map/限高/换行/测量/回放同步使用完整元信息。

富文本新增 `<latex math-style="display">...</latex>`，另接受 text/script/scriptscript，缺省或未知值回 TEXT。序列化往返保留根样式，不向公式源码注入 `\displaystyle`。

### 布局、交互与复制

块公式保持指定字号，按完整 MathBox 上伸/下伸及已有公式留白确定行高，在扣除列表/引用缩进的正文列内居中；超宽时左对齐、不拆公式、不缩字。滚动和裁剪范围包含左右 ink 外伸。行中 DISPLAY 用完整上下伸参与既有混排行框。列表 marker 留在列表列，只绘制一次，不跟公式居中。

链接区域与公式同一几何、同一滚动/裁剪投影，覆盖完整公式行框；非链接公式不创建假链接。复用 scene 和现有双轴滚动内容宿主，测量/绘制/命中共享 logical px。聊天块公式默认不套旧行内限高；行内 DISPLAY 仍可接受调用方的显式限高，但不能丢数学样式。

本批复制契约明确区分两种源码：全文 document.getSource 保留原始 Markdown（包括 CR/LF/CRLF、容器前缀与定界符）；单公式 getLatexSource 返回提取后的 TeX，允许统一换行为 LF，但保留正文内部空行、尾空格，剥除每行容器前缀和约定开栏缩进。独占开栏后的第一处换行与闭栏前的最后一处换行不计入 TeX；正文行之间换行保留，因此多余空白行仍可表示。单行公式只剥定界符，保留体首尾空白。块扫描器私有 SrcLine 保存原始源区间/换行种类，或从统一原source切片提取，不能在丢弃offset/换行后靠拼接行文本宣称恢复原文。CRLF、空行、尾空格、列表和引用前缀分别回归。样式映射、缩小和换行不得再次改写已提取的源码。当前没有富段公式内部 caret，本批不把 TeX 字符长度伪装成内部字符位置，不新增右键复制菜单或全文选区公共 API。

## 验收与完成定义

每个生产增量完成正式回归、完整离线 build、实际软件样张和本地提交；旧 public/protected 签名快照检查保留。数学计算与测试计数由 Python 核验。检验正常/阴影、双字体及固定资源版本、缺字身份、不同字号/实际倍率、非整数起点、缓存 reload/inkEpoch、中文混排、块/表格/列表/聊天和链接区域。参考引擎仅为开发期外部工具，统一数学样式、逻辑字号、实际倍率与基线，不对异字体强求像素相等。

游戏客户端/GPU 验收遵守仓库 runClient/runServer 由用户或 CI 执行的边界；软件和 scene 几何通过不写成 GPU shader 或真机观感通过。缺未授权运行态时保留明确验收项，不把规划整体标为完成。
