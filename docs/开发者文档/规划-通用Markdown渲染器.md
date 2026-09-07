# 规划：通用 Markdown 解析渲染器（B 案）

**状态：** D1-D4 已裁（§五）；**M1 `6d9de24c` + M2 `08a8034e` + 裁 B 收窄 `736bafc1` + M3
`531da89e`/`339530e0`/`eddd2c29` + M4/M4-fix `075328ef`/`00d1a45a` + M5 接线 & M6 复生锁 `3e89d91e`
+ M7 方案乙块几何 `8c86a644`（2026-09-05，裁定 B 块几何部分经用户裁定重开，见 §二之三注记
与 §二之七）全部完成**：chat3 气泡消息已改吃通用 markdown 渲染器，旧行级垫片已删，
死代码窗口闭合（§六 1），引用嵌套/真分隔线/围栏底色三项块级几何已入接缝入真机；
余下的是真机观感验收（§五之二分工）与后续独立裁定。
方向（用户 2026-09-04）：**先建独立通用渲染器 → 删 chat3 现有简易实现 → 接线**——三步全部落地；
基线 **3992 / 0 / 0 / 2，362 类**（M7 方案乙后）。
**目标仓：** Qz-UILib（branch `4.0`，MC 1.7.10，本地提交不 push）。
**范围声明：** 只写本仓能做且已核实的事；未核实的运行态在下面明确标出，不当已完成。

## 一、先纠正一件事：这不是从零起

扫仓与扫 git 历史核实到的事实（全部一手）：

1. **通用 markdown 能力的位置早已裁定过**：`规划-聊天框Markdown接管.md` §L0 写着「L1 放
   `club.heiqi.uilib.font.layout.markdown`（与 `RichTextTagParser` 同级：通用文本能力，聊天是
   第一个消费者，未来 tooltip/书本可复用）」。B 案要建的正是这一层，**位置不该另起**。
2. **该层落地过一次并带测试矩阵**：`9c4dcae5`（2026-08-23）
   `feat(markdown): 行内 markdown 解析器落地(聊天框接管阶段一)，含误伤防护测试矩阵`，
   树内两文件：`MarkdownInlineParser.java`、`MarkdownSpan.java`。
3. **它已被删**：`d8d10250`（同日，chat3 接线层 `ChatFacade` 取代寄生接管）连带删掉
   `internal/chat/`（`MarkdownGuiNewChat` 566 行、`ChatLineLayoutCache` 147 行、
   `ChatCardRenderer/Collector`）与 `font/layout/markdown/`；当前 `git grep -iE
   "markdownspan|inlineparser|markdownparser"` = **0 命中**，`font/layout/` 只剩
   `TextSegment/TextStyle/RichTextTagParser/TextLayoutService/MinecraftColorTable/
   TextContentModeStrategy`。
4. **chat3 现在的「markdown」是行级规则垫片**，不是解析器：
   `internal/chat3/viewmodel/ChatMarkdownLineRule` 自述「chat3 无 markdown 解析器，本类以
   「行级规则分派」模式落地两件事……为未来完整 markdown 解析留口（`Kind` 枚举按行级规则扩展）」；
   另有 `ChatCodeSpanSplitter`（code span 切分 + `TextSegment.isLatex()` 分流）、
   `ChatUrlLinkifier`（裸 URL 识别 + 命中区外扩）、`ChatLineLayouter`（按宽度切行 + 度量纪元缓存）。
5. **L0 设施完好可用**：`TextSegment(String|forLatex)` + `TextStyle`（含 `setLink`/`setRandomStyle`
   /`applyFormat` 的 § 码语义）、`TextLayoutService`（度量同源）、`LatexParser`/`LatexCache`
   （`inkEpoch` 就绪代已解决「字形未就绪时布局缓存永久回退」）、软件渲染对拍链
   `LatexSoftwareRenderKit`（共享装配注释明确「每实例约 123MiB，必须共享」）。

所以 B 的正解是：**复活 + 升级为块级**，而不是新写一套。历史里的实现与误伤防护测试矩阵是
现成的行为规格来源，语义裁定文字也还在 `规划-聊天框Markdown接管.md` §L1（flanking、CJK、
`$` 后拒数字、未闭合字面宽容、code span 内不解析、样式叠加不改颜色）。

## 二、分层（B 案，自顶向下）

```
L3 消费层   chat3 接线（ChatMessageList / ChatCardComposer / ChatCodeSpanSplitter 改造）
L2 绘制层   ui/markdown  MarkdownDocument → PaintCommand 流（不直连 GL，见 §四硬约束）
L1 解析层   font/layout/markdown  块级 + 行内 → List<TextSegment>（纯 JVM，零 MC 类型）
L0 既有     TextSegment / TextStyle / TextLayoutService / RichTextTagParser / LatexParser / LatexCache
```

依赖方向严格单向，L1 不依赖任何 Minecraft 类型（沿用原裁定的理由：headless 可测）。
**L1 与 L2 分层的意义**：解析是纯函数（好测、好锁、可被 tooltip/书本复用），绘制才碰 GL。
**接缝修正（2026-09-04，原句自相矛盾，见 §二之三）**：L1→L2 的唯一接缝是**公共面**
`List<TextSegment>`；块级盒模型 `MarkdownBlock` 是**包内**中间表示，M1/M2 期间不外泄。
原句把「+ 块级盒模型」也写成接缝，等于要求块模型同时是包内实现又是对外承诺 —— 正是这句话
把 M2 逼到「三个几何字段无处生效」的死结。裁 B 后：几何旋钮随块模型一起留在包内，
接缝只剩段流；若 M3 证明 L2 必须要块边界（缩进/块间距/标题分界），届时按 §二之三 的 A 案
另裁「块模型进公共面」，那是一次独立的公共 API 变更。

### L1 语法面（块级 = 本次新增；行内 = 复活既有裁定）

- 块级（经 C1a/C3b2/C4 三批演进后的现形）：ATX 标题 `#..######`、围栏代码 ``` / ~~~、
  **缩进代码块**（块起点 ≥4 前导空格，CommonMark 0.30 §4.4；C1a 2026-09-06 补上，本文初版
  「语法面缺缩进代码块 + 段落续行缩进折叠」两条对主流的偏离随批拆除）、引用块 `>`（可嵌套）、
  无序/有序列表（含缩进续行与内容列嵌套，续排/起始序号按主流 = C3b2 修 1）、**setext 标题**
  （段落紧邻 `===`→H1 / `---`→H2，本文初版「setext 缺失」偏离由 C3b2 补实现，惰性续行不得
  充当下划线的收紧由 C4 N2 落地）、分隔线 `---`/`***`、段落与空行、硬换行（行尾两空格 / 反斜杠）。
- **§（U+00A7）= 普通字符，L1 零认知零分支（C4 归位 2026-09-06；C7 定案 2026-09-07）**：
  L1 代码里不存在任何 § 的识别、剥离、转换或上色机制，§ 与任何其它文本字符同格——不参与块标记
  检测（所以行首 `§a- x` 是段落字面而非列表项）、不进前导空白、不在行内 code/围栏/latex 里被
  特殊对待、出段也不切段不上色。这是<b>无条件解析规则</b>：不依赖「输入保证不含 §」的事实，
  事实面（原版 `ChatAllowedCharacters` 排除 U+00A7）只解释「为什么集成层可以不留 § 机制」。
  MC 特有格式若要做成语义，只能作为调用方的<b>显式扩展</b>存在，永远不进解析核心。
  历史上在 chat3 集成层存在过三代 § 处理（C4 初版无条件剥 → C4-fix 乙′「命中才剥 + 输出后置桥」
  → C6b 甲「进 markdown 前 `toSpanStream` 转样式锚点 span 流」），<b>已于 C7 全部拆除</b>：
  玩家消息内容改取原版结构参数（`StructuredChatReader`）或 plain 正则 rest，源头无 §。
  正向行为锁 `MarkdownSectionCodeIsPlainTextLockTest`，反向常驻守卫
  `MarkdownL1ZeroSectionKnowledgeGuardTest`（反例现指仍合法含 § 的原版链 `ChatLineLayouter`），
  L1 直连字面判据仍在 `MarkdownChat3RuleInheritanceTest`——细账见 §二之八 C7。
- 行内：`**`/`__`、`*`/`_`、`***`、`~~`、`` ` ``、`$`/`$$`、`[text](url)`、反斜杠转义 ——
  **语义照抄 §L1 既有裁定，不重开**。
- 刻意不支持（写进文档，别默默失败）：表格、任务列表、HTML 内联、脚注、图片 `![alt](url)`。
  图片需要网络与缓存，`ui/image/DocumentRemoteImageCache` 是既有面，但它是「文档远程图片」用途，
  接不接进 markdown 属独立裁定（本文列为 D4）。

## 二之二 进度状态（跨轮恢复看这一节）

| 步 | 内容 | 状态 |
| --- | --- | --- |
| M1 | 复活 L1 行内解析器 + 测试矩阵 | **完成 `6d9de24c`**（2026-09-04） |
| M2 | L1 扩块级 + `MarkdownDocument` 数据模型 | **完成 `08a8034e`**，留下一处待裁矛盾见 §二之三 |
| M3 | L2 `ui/markdown` 绘制层 + `MarkdownPage` + headless 出图 | **完成 `531da89e`/`339530e0`/`eddd2c29`**，见 §二之四 |
| M4 | chat3 现路 vs B 路行为对拍（产 `-side` 成对图） | **完成，M4-fix 后门禁转绿**（2026-09-04）：PARITY FAIL 6 → **0**、TIE 0、有意差异 1 条（P03@150）→ **M5 可开工**，见 §二之五 与其后「M4-fix 收尾」 |
| M4+ | 判读分辨率 @Nx 真放大 | **完成**：N=4 上限由 `awtCharSize=64` 定死，@1x 逐位不变 |
| M5 | 接线并删除 `ChatMarkdownLineRule` 等旧解析 | **完成 `3e89d91e`**（与 M6 同笔，硬规矩满足）：接线本体 `internal/chat3/view/ChatMarkdownPipeline`，见 §二之六 |
| M6 | 复生锁 G3（与 M5 同一提交） | **完成 `3e89d91e`**：`Chat3MarkdownResurrectionGuardTest` 4 条断言全配正对照+反空跑地板，见 §二之六 |
| M7 | 方案乙：块身份行进接缝 + 三项块级几何（2026-09-05 用户裁定重开裁定 B 块几何部分） | **完成 `8c86a644`**：唯一新公共类型 `MarkdownLayoutLine`；引用嵌套竖条+缩进 / 真横线 / 围栏底色经 BACKGROUND/位置表达，可见文本零改动；门禁零接触全绿；见 §二之七 |
| C 系列 | 向 CommonMark 0.30 归位 + § 划界的拆除批（2026-09-06 宪法裁定后开拆）：C1a 缩进代码 + 内容列唯一判据、C3b1 门禁重基线（R=commonmark-java）、C3b2 有序续排 + setext、C3b3 标题直拍撤豁免、**C4 § 归位 chat3 + setext 惰性收紧 + 门禁 RECORD 清零**、**C4-fix 乙′（命中块标记才剥）+ 一致性锁 + N2 惰性 setext 语料补齐**、**C6a L1 span 流地基 + C6b 甲（§→span 输入转换，预清洗与输出桥整套拆除）**、**C7 § 划界（集成层 § 机制全拆 + 玩家消息结构读取 + L1「§ 是普通字符」定为无条件规则）** | **C4 + C4-fix + C6a + C6b + C7 已完成**，逐批记录见 §二之八 |
| C8 | **通道③落地 = ChatAccess.printMarkdown 双入口**（a1 定案：print 恒纯不过装饰链、
|    | 装饰须显式 decorate 后递入；uilib.markdown 键旁路注入零穿参；markdown 判定短路在
|    | 一切取文本之前；渲染形 = MARKDOWN_LEFT 左对齐无气泡无 sender 不居中） |
|    | **完成 67e44c00**（2026-09-07），细账见 §二之八 C8 |
| C9 | **测试平台鲁棒化**（2026-09-07 用户裁定「甲」：Linux CI 8 红=Windows 字体度量下的绝对像素/计数魔数进了断言；修法=同 JVM 独立测量关系形 + 输入侧按实测 advance 自适应构造，禁 assumeTrue、禁 OS 分支、禁为绿放宽；门禁三件套收窄为二件套） | **测试批完成**，#7 判为<b>真实布局缺陷挂账</b>（主源未动，见 §二之八 C9 第 7 条），细账见 §二之八 C9 |

M1 的验收事实（父代理逐条独立复核过，非采信子代理自述）：三个文件与 `9c4dcae5` **blob hash
逐一相同**（`84dd897c`/`49cfd000`/`6f639546`，463+48+274 行），**零适配**——两周内 layout 层
对 markdown 的使用面无破坏性漂移；`MarkdownInlineParserTest` 26 个用例、90 处 `Assert.` 全绿；
提交仅含 `font/layout/markdown/**`；`src/main` 内除自身外零引用（**故意零消费者**，死代码窗口
按 §三 到 M5 才闭合）。基线 3855 → **3881 / 0 / 0 / 2，352 类**。

子代理留的两条尾巴（真事，不装完）：① `MarkdownInlineParser`/`MarkdownSpan` 的 javadoc 里
仍指向《规划-聊天框Markdown接管.md》与「阶段二 `internal/chat` 桥」——那个包已被 `d8d10250` 删
且不会再回来，M2 动这两个文件时顺手改指本规划；② 本规划的 §一 事实 3 说 blob 一致前我只比对
了一个文件，现已三文件全比对。

## 二之三 M2 复核结论与一处必须裁的形状矛盾（2026-09-04）

M2 交付经独立复核（`javap` 读编译产物为准，不信自述）：提交 `08a8034e` 仅含
`font/layout/markdown/**` 8 文件 +1935/-3；`MarkdownBlock`/`MarkdownBlockParser` 实为包内
`final class`；行内两文件**非注释增删 0 行**、`MarkdownInlineParserTest` diff 为空（行内语义
确未被改）；全包 import 仅 `java`/`club`（G2 纯 JVM 成立）；**3947 / 0 / 0 / 2，354 类**
（基线 3881/352 → +66/+2）。自报的两个真缺陷修复（四连反引号栅栏误判、硬换行反斜杠未剥除）
与三条踩坑语料测试名均实际存在。

### 矛盾：三个 public 几何旋钮在当前接缝下永远读不到

子代理报「越界待裁①：块模型只能 package-private，L2 要引用号底色/缩进/硬换行位图必须先裁公共
面」，另报「`listIndentPx/quoteIndentPx/blockSpacingPx` 仅 L2 消费、L1 不读取」。两句是同一矛盾
的两侧。实测该包内（除声明与 getter/setter 自身）的读取者：

```
getListIndentPx    消费者: *** 无人读取 ***
getQuoteIndentPx   消费者: *** 无人读取 ***
getBlockSpacingPx  消费者: *** 无人读取 ***
对照: getBulletMarker x2 / getThematicBreakText x1 / getHeadingFontSizeDeltaPx x1 /
      getDefaultFontSizePx x1 / isHeadingBold x1 / isQuoteItalic x1 / isHeadingUnderline x1
```

而 `MarkdownDocument.toSegments()` 的接缝是 `List<TextSegment>`；`TextSegment` 字段只有
`text/style/latexSource`，`TextStyle` 全部字段里没有任何 indent/spacing/块边界通道 ——
**块级边界在扁平化时被抹掉**。所以那三个字段不是「以后 L2 会读」，而是当前形状下无法生效。
真正的选择是：块模型进公共面（L2 自己走块树），或给 `TextStyle`/`TextSegment` 加几何字段
（污染全部文本层，最差）。

### 裁定建议

> **2026-09-05 经用户裁定重开块几何部分（方案乙）**——本裁定 B 的「三对 `*IndentPx`
> 访问器移出公共面 + 几何无处表达」结论**部分作废**：作废范围 = 块级几何必须能被 L2 消费
> 这一半（引用嵌套缩进/竖条、真分隔线、围栏块底色在实机截图证实缺失，且软光栅修复
> `2a5ab61a` 后 headless 图才可信——事故档 `ERROR-20260905-software-rasterizer-half-quad-rotated-sampling.md`
> 第八节「立此规矩」为本次全部断言的纪律来源）。**未作废范围**：块模型不外开
> （`MarkdownBlock`/`MarkdownBlockParser` 恒 package-private）、「给 TextStyle 加几何位」
> 仍是最差选项、`MarkdownStyleTable` 公共方法数恒 18。落点 = 新增**唯一**公共类型
> `MarkdownLayoutLine`（行粒度块身份 + 行盒几何 + 块归属，见 §二之七），
> 刻意**不是**回到被删的三对 `*IndentPx` 访问器。

**已裁 B 并执行（`736bafc1`，2026-09-04）**。父代理独立复核：全仓 `git grep` 三对符号
`src/main` + `src/test` 命中均 **0**；`javap -public` 原始清单逐行数得 **16 个 public 成员**
（构造器、`defaults()`、`copy()` + 6 对访问器 + `getHeadingFontSizeDeltaPx(int)`/`set(int,int)`），
无任何 `Indent`/`Spacing` 残留；tally **3947 / 0 / 0 / 2，354 类**（与未删任何用例自洽：三对访问器
本就零测试引用，故删除不减少用例数）；提交单文件 `+7/-37`。**两处口径纠正**：① 子代理报
"23 → 17"，绝对值各多 1（把 `public final class` 声明行计入），净 −6 正确；② 父代理第一版计数
命令（`Select-String '^\s+public '`）在 pwsh 下返回 0，是**仪器空跑**而非成员为 0，改逐行数原始
输出才拿到真值 —— 又一次印证「扫到 ∅ 要先怀疑扫帚」。**

- 原 B 案表述**：把三对 `*IndentPx`/`blockSpacingPx` 访问器与字段**移出公共面**（零读取者、
  零消费者，纯收窄），块模型保持包内；等 M3 真要块级几何那一刻再裁「块模型进公共面」。
  理由：**加方法是兼容变更，删方法是破坏变更**，顺序反了就永久定死。
- **A**：现在就把 `blocks()`/`MarkdownBlock`/`Kind` 放进公共面，省一次裁定，代价是在 L2 还没
  写之前就把一棵递归块树承诺进公共兼容面。
- 本文档 §二 原句「两层的接缝只有 `List<TextSegment>` + 块级盒模型」**自身就是矛盾源**，
  A/B 任一都要先改掉这句，不留两条真相。
## 二之四 M3 复核（含父代理亲自读图结论，2026-09-04）

事实复核（父代理独立跑，不信自述）：三笔提交共 10 文件、禁区零命中，`git diff 9effb4a4..HEAD -- internal/chat3` **为空**；
L2 包内顶层类只有 `MarkdownPainter`（public）与 `MarkdownLineLayout`（包内）—— **公共门面恰 1 个**，
符合 D3；两新包 `GL11.` 计数 **0**，同法正对照 `ui/render` = **559**（子代理报 386，差在计数口径：它按行锚定、
我按出现次数含注释；两者都远超地板 100，锁有效，但**同一把尺没统一**，M6 写守卫时口径要对齐）。
tally **3969 / 0 / 0 / 2，357 类**；出图 9 张 PNG + `profiles.txt` 实存。

### 父代理读图结论（这是本规划要求我承担的验收职责，不是子代理自述）

1. **重影是出图场地的既有特性，非 M3 回归**：我拿同一套软件光栅器的 **LaTeX 老图**
   （`build/reports/latex-render/12-formula.png`，64×44，与 markdown 无任何关系）做对照，
   括号与字形同样双层横移。**因此 headless 出图能判结构、不能判可读性与细间距** ——
   可读性验收只能靠真机 `MarkdownPage`，这条是场地限制不是缺陷。
2. **结构面逐项肉眼确认成立**：标题 H1>H2>H3>H4 尺寸阶梯；围栏内 `**粗**`/`$x$` 保持字面未被行内解析；
   引用块四层缩进阶梯 + 第五行脱出引用；列表 `•` 与有序 `1.`/`2.` 均在，续行缩进对齐到项文本下；
   硬换行确实断成两行；行内公式出现分式与根号原子；链接段有下划线；分隔线成线。
3. **一处真实布局缺陷（肉眼可见）**：**块间距塌缩** —— 同一文档内标题/代码块/列表与相邻段落之间
   没有额外空隙，行距处处均等，块级边界在视觉上不存在。这正是裁定 B 时预见的后果：接缝是扁平
   `List<TextSegment>`，空行只剩一个 `\n`。
4. 次要不值当修的：左 150px 的 label 列文字被截断（`01 标题...0`），不影响判读。

### 块间距塌缩引出的一次待裁（不自动升级为公共面变更）

修法有三，代价差一个量级，**默认走 C1**：
- **C1（建议）**：L2 内部把块边界编码成**段流里的显式换行/占位段**（例如块与块之间插一个带标记的
  `\n` 段，由 `MarkdownPainter` 认它加行距），零公共面变更、零 L1 变更。
- **C2**：给 `TextStyle` 加行几何位 —— 污染全部文本层，规划 §二之三 已判为最差选项。
- **C3**：块模型进公共面（A 案复活）—— 只有在 C1 证明表达不了更复杂块级排版（表格、多栏）时才值。

## 二之五 M4 对拍结果：门禁拦住 6 条真实回退（2026-09-04）

M4 交付**未提交**（红 build 不提交 + 宁可红着回来两条同时成立），三文件以 `refs/wip/m4-parity-gate`
（`81dc45c5`，3 files +1341/−29）保住，主树工作区与 `4.0 @ b2909a03` 未受影响。父代理独立复核：
`git diff HEAD -- src/main internal/chat3` **为空**；`FontConfig.awtCharSize=64.0` 坐实 N=4 上限
（13×4=52≤64，N>4 自动改口"纹理插值"）；M3 测试 `Assert` 36→36、`@Test` 4→4（断言只被 `scale==1`
门控，**零删除**）；`@1x` 产物 sha 与 M3 时逐位相同；`diff.txt` 102 行、35 处 FAIL、六语料号俱在。

判据跑前定死三档：PARITY（chat3 有行为 → 逐段等价）/ NEW（chat3 无行为 → 只记录）/ REGRESSION。
结果 **PARITY 18：PASS 12 / FAIL 6**，NEW 11 条，TIE 0。六条 FAIL 全是**真回退**，不是口径噪声：

| # | 场景 | chat3 现行为 | B 路 | 归属 |
| --- | --- | --- | --- | --- |
| F1 | 命令 `` `curl http://x.y/z -s` `` | 反引号→codeSpan 衬底 + 12px，code 内 URL **不**链接化 | 反引号被吃但无 code 位，URL 被 linkify | L1「第一版 code 仅字面」旧裁定未承接 chat3 现行为 |
| F2 | 缩进列表（2/4 空格） | 每级 2 空格进文本流（`• 乙`/`    • 丙`） | 全部 `• ` 无缩进 | 裁 B 把缩进留在包内块模型，扁平接缝丢了 |
| F3 | `> 引用的文字` | 剥 `> ` + 降为次级色 FF9AA0A8 | 剥 `> ` 但恒白 | 样式表缺引用色旋钮 |
| F4 | `§a- 玩家列表行` | 行首 § 码被无视后识别为列表 | 整行字面 | **块层 §-盲**（`MarkdownDocument.parse` 只吃 String） |
| F5 | `§c红 §fplain` 切换处 | 空格归**后**段（`红色警告`/` plain`） | 空格归**前**段 | §桥输入形态差（整串 vs 逐显示行） |
| F6 | `甲\n\n乙` | 3 显示行（含空行） | 2 行 | 块间距塌缩（§二之四 3 已预见，本门禁钉成 FAIL） |

另记两条场地事实：门禁测试落在 `font.render.software`（`LatexSoftwareRenderKit.Shared` 为包内可见，
与 M3 同先例）—— 属可接受的权宜，M6 应把共享装配升成正式 testkit 位置；N10 暴露 chat3 自身
怪癖（code 配对不识别转义），如实记录未修。

### M4-fix 收尾：六条修复落地，门禁 FAIL 6 → 0（2026-09-04）

工具实测：`# 汇总: PARITY 条目=18 NEW 条目=11 FAIL 差异=0 TIE=0 有意差异=1 PNG=141`；
`diff.txt` 的 PASS 行 24 → **35**（另 1 条 `DIVERGENT` + 1 条 `INVARIANT`）；tally 3969/0/0/2（357 类）
→ **3976 / 0 / 0 / 2，358 类**；全量 `build --offline` = BUILD SUCCESSFUL。逐条落点与钉死测试
（钉死用例全在 `MarkdownSoftwareRenderTest`，相对门禁 ref 是 +212/−0 纯新增；门禁本体相对 ref 是
+139/−2，被改的 2 行只是主循环 `if (parity)` 改三分支与汇总行加计数，`compareParityEntry`、
段宽 0.0px、TIE 2.0px、行宽 1px、命中区 1px 判据一字未动）：

| # | 落点 | 钉死 |
| --- | --- | --- |
| F1 | `MarkdownInlineParser` 的 CODE_TICK 分支 + `codeStyle()`：吃反引号的当场写 `codeSpan` 位、`codeBackgroundColor`、`fontSizePx`（chat3 口径 12px）并清 `link`；值取自 `MarkdownStyleTable` 的**包内**登记项（L1 不 import chat3，G4 唯一登记面）。旧裁定「第一版 code 仅字面输出」自 2026-09-04 起被 chat3 出货行为取代 | `fixF1InlineCodeSpanCarriesChat3CodeStyle` + 门禁 P03@269 |
| F2〔**C1a 已拆**：「每级 2 前导空格进段文本」与深缩进独立列表机制按 CommonMark 内容列退役，见 §二之八〕| `MarkdownDocument.walk/emit` 带 `markerLevel`，`emitListItem` 按级拼 2 个前导空格进 bullet 段文本（`ChatMessageList.java:952-956` 同口径）；块模型与缩进 px 仍未外开 | `fixF2NestedListIndentIsLeadingSpacesInMarkerSegment` + 门禁 P08@150/@269 |
| F3 | `MarkdownStyleTable.get/setQuoteTextColor`（默认 0xFF9AA0A8 = chat3 次级色），`quoteStyle()` 在 QUOTE 块应用；0 = 不降色 | `fixF3QuoteTextColorKnobMatchesChat3Secondary` + 门禁 P12 |
| F4〔**C4 已拆归位**：markerView「块层 §-容忍」整体废止，§ 输入清洗迁至 chat3 集成层，见 §二之八〕| ~~`MarkdownBlockParser.markerView()`~~ 历史定稿口径 = 行首 § 序列仅在确实命中块标记时随标记一并消费；未命中块标记时一字不动；这不是解析 § 颜色，颜色语义仍由下游决定。理由：A 路 `ChatMarkdownLineRule.classify` 第一步就 `stripLeadingFormatCodes`，实测 A 侧文本 `«• 玩家列表行»` 不含 `§a`；若「命中也原样保留」则漂移 2 码点 / 14px，P13 恒红 | `fixF4BlockLayerToleratesLeadingSectionCodes` + 门禁 P13 |
| F5 | 落在 **L2** `MarkdownLineLayout.unifySwitchPointSpaces`：只在「两侧仅颜色不同、FontType/fontSizePx/italic 逐项相同、两侧非 code/link/latex」时把上一段尾随空格并进后一段（度量中性，逐字符推进宽与总行宽一字不变）。不落在 L1 的 `parse(spans)`：那里有既有测试 `shouldParseSpanStream` 钉死「尾随空格归前段」 | `fixF5SwitchPointSpaceBelongsToNextSegment`（段文本+段宽双等）+ 门禁 P14 |
| F6 | 走 C1：`MarkdownBlock.blanksBefore`（包内）由 `parseBlocks` 在消费空行处 `stamp()`，`walk` 在该类块边界产**一个空文本占位段**；L2 `splitLogicalLines` 认它强制产一个空显示行、`wrapVisualLine` 不再吞中间空行。零公共面变更（未给 `TextStyle`/`TextSegment` 加几何字段） | `fixF6BlockGapBecomesExactlyOneVisualBlankLine` + 门禁 P16 |

> C 系列拆除批注记（2026-09-06，§二之八）：上表六条落点中 **F2 已由 C1a 拆除**、
> **F4 已由 C4 拆除归位**（markerView 机制连同其钉死用例的旧期望一并重定），F1/F3/F5/F6
> 仍为现行。**A 路「chat3 现路行为规格」基准已由 C3b1 废止重基线**（R=commonmark-java 0.21
> + GFM strikethrough），本节凡以 A 路实测为判据的条目均为历史记录，不再约束现行门禁。

**有意差异（唯一 1 条，PARITY → 有意差异，三处留档）**：**P03@150**。第二个根因与 F1 无关——A 路是
「先按容器宽切显示行、再在显示行内配对反引号」，于是同一条消息换个窗口宽度就换一种样式语义：
@150 跨行的反引号对留字面 `` ` `` 且 code 内 URL 被链化（实测 `A=[«命令·`curl·» w=63.84 |
«http://x.y/z» c=FF7AB8F5 link=… w=60.98]`），@269 却剥反引号 + 12px + 无 link。用户裁定：该顺序
副作用是 **chat3 缺陷，B 不复刻**（B 的「解析 → linkify → 换行」使 code span 语义与容器宽无关，
与 CommonMark 及「内容不变则样式不变」一致，严格更优）。门禁**没有摘掉这条比对**，而是改判一条
**更强的正向不变量**（`assertWidthIndependentCodeStyle`）：① 同语料在 @150/@269 下 B 的 code 段指纹
（文本 + codeSpan + 衬底色 + fontSizePx + link==null + 段宽）逐项相等；② 两宽度的换行确实不同
（否则不变量空转）；③ A 侧确实随宽度换语义（否则登记理由不成立）。留档三处 = `diff.txt` 的
`DIVERGENT` 行、`profiles.txt` 的 `divergent` 行、本节。**其余 17 条 PARITY 判据一字未动**。

> **2026-09-05 用户裁定「甲」——登记通道重构授权注记**：上述登记机制存在结构性缺陷——
> `INTENTIONAL_DIVERGENCES` 是 `String[]`，处置却是「凡登记条目一律改跑
> `assertWidthIndependentCodeStyle`」：通道只有一条语义，登记一条与 code 无关的差异会得到
> 一条 code 不变量去判，必然通过且通过得毫无意义（**假绿机器**）。按 §二之五 铁律
> 「改判据引擎须另裁」，本次由用户 2026-09-05 明确授权另裁：每条登记改为携带
> `{key, reason, 替代不变量标识}`，分派表对未知/缺失标识**硬失败**（绝不默认放行、绝不
> 退化为跳过）；P03@150 的替代不变量语义**逐位保留**（通道重构后 diff.txt 与基线逐行
> 零差异实证）。落点、P20 长引用语料的「先实测后登记」全程与对账表见 §二之七 末段，
> 代码批 `06a9e45d`。

**公共面变化清单（javap -public 逐行数，不含 class 声明行）**：`MarkdownStyleTable` **16 → 18**，
增量恰为 F3 的引用色一对；`MarkdownInlineParser` 仍 2、`MarkdownDocument` 仍 6、`MarkdownPainter` 仍 5
（F1 的 code 字号/衬底与 F5/F6 的新入口全部包内）；`TextStyle`/`TextSegment` 零改动；
`MarkdownBlock`/`MarkdownBlockParser`/`blocks()` 仍包内；`internal/chat3/**` 一行未改。

**两条用户裁定（2026-09-04，M4-fix 收尾轮）**

- **裁定①（F2 撞既有非门禁测试）**：授权并要求改 `MarkdownDocumentTest:217-225` 那一条期望——它钉的是
  2026-08 裁 B 的「缩进不进文本流」，而用户已裁 PARITY 优先、chat3 现行是把每级 2 空格写进段文本。
  期望由 `«• 乙»` 改为 `«  • 乙»`：新期望**更具体**（多两个必须存在的前导空格），属规格变更下的
  **收紧**，不是为换绿放宽断言；注释三要素（取代关系 / 日期 / 依据 = 门禁 P08 与
  `ChatMessageList.java:952-956` 同口径）已写进该处与其类 javadoc。裁 B 的另一半「不外开块模型 /
  不开缩进 px」不变。
- **裁定②（P03@150 第二根因）**：判定 chat3 该行为是**缺陷**，B **不复刻**；但**不许把这条从门禁里
  摘掉**——改为正向断言 B 的宽度无关性（比原 PARITY 判据更强），并在 diff.txt / profiles.txt / 本节
  三处留档。判据原文：A 路「先按宽度切显示行、再在显示行内配对反引号」使 code span 识别依赖排版
  结果，同一条消息换个窗口宽度就换一种样式语义，与 CommonMark 及「内容不变则样式不变」都相反；
  B 的顺序（解析 → linkify → wrap）严格更优且宽度无关。

**修完后仍与 chat3 有意的差异（3 条遗留，M5 接线时按此对齐，不得当成 bug 顺手改）**

1. **列表项续行**（lazy continuation）不带 chat3 保留的源前导空格：A 路 `«  续行»`，B 路 `«续行»`
   （`ChatLineLayouter` 把行首空白并入行文本，L1 按 CommonMark 以 contentCol 剥缩进）。语料 N04
   属 NEW 档，只记录不判等。
2. **§ 码在 markdown 路径恒字面（C7 定案后本条不再是「差异」）**。历史三代口径（C4 无条件剥 →
   C4-fix 乙′预清洗+后置桥 → C6b 甲 `toSpanStream` → `parseSpans` 转样式锚点）曾让 chat3 消息路
   与文档形参路对 § 有两种结果；**C7（2026-09-07 划界）把集成层的 § 机制整套拆除**，玩家消息内容
   改取原版 `chat.type.text` 的结构参数 `getFormatArgs()[1]`（`StructuredChatReader`）或 plain
   正则 rest——两条通道都从 unformatted 源取值，而 § 残渣本来的来源是客户端
   `ChatComponentStyle.getFormattedText()` 逐组件注样式码（母本 :105-119，:115 无条件
   `append(RESET)`），不是服务端。于是 chat3 与 L1 直连消费者对 § 得到同一结果：<b>普通字符、
   原样显示、零样式</b>；「markdown 不引入颜色」旧裁定继续成立，且不再需要任何转换。
   甲↔乙′ 迁移等价锁与其退役镜像、比较尺、`FormatPrefixStripper`、`toSpanStream` 均随对象消失
   删除；正向锁改由 `MarkdownSectionCodeIsPlainTextLockTest`（L1）与
   `ChatMarkdownPipelineTest#sectionCodesAreLiteralTextWithZeroStyleEffect`（chat3）承担。
   `MarkdownChat3RuleInheritanceTest` 的 L1 直连字面判据原样保留。细账见 §二之八 C7 条。
   〔**C6a 区分注记（2026-09-06，防拿旧句当挡箭牌）**：本句禁的只是**输出侧反接**——把解析
   产物（`toSegments` 的段）再喂回 `parse(spans)` 做第二次解析。C6a 新增的都是**输入侧单次
   解析**通道，不违反其字面与理由：① `parse(spans)` 自 C6a 起为拼接文本上的跨 span 连续扫描
   （定界符可跨样式锚点合法配对——旧句立论所依赖的「逐 span 漏配」已消除，未闭合仍恒字面）；
   ② `MarkdownDocument.parse(List<MarkdownSpan>)` 为块级文档 span 流入口，是裁定「方案甲：§
   在进 markdown 前转样式锚点」的 L1 地基（块检测由 L1 在纯文本上跑、与 String 入口共用判据），
   C6b 将把 chat3 消息路切到该入口并拆除输入侧预清洗。**两禁不变**：不许援引旧句否决输入侧
   通道，也不许借新入口造「先产段再喂回」的双解析。〕
3. **围栏代码块**未打 `codeSpan` 位 / 12px / 衬底：F1 只承接行内反引号（chat3 无围栏行为，N02 属
   NEW 档）。围栏内容仍全字面、不解析行内标记（M2 既有裁定不变）。

**@1x 产物 sha 归因（含 1px 位移的区分性检查）**：先把生产码复位 + 门禁原样做 HEAD 对照复跑，
304 项产物与基线 `changed=0`（场地确定）；带修复后像素真有差的只落在被修复命中的条目
（`05-hard-break` 行数 6→7、`08-thematic-break` quads 67→76 / ink 1790→2444 = F1 衬底，
`03`/`04` = F3/F2，门禁侧 P03/P08/P12/P13/P16/N03 = 对应条目）。余下 N08/P17/P18 各差 1px：
用同一探针在修复前后各跑一次，比对这三条 A 路与 B 路的**行宽 / 行高 / 段数 / 逐段宽（%.17g）/
段级字号**数值序列 —— **61 行全部逐位相同**；而差异像素在 A 路图与 B 路图上的**坐标完全相同**
（N08 同为 x=14,y=23；P18 同为 x=32,y=28；P17 像素零差仅重编码差异）。数值同而像素移
⇒ **共享 atlas 装配位移成立，不是语义变化**（F4 改变了送进 `assembleGlyphs` 的段流）。

## 二之六 M5 接线 + M6 复生锁收尾（`3e89d91e`，2026-09-04，同批硬规矩达成）

工具实测：全量 `build --offline` = BUILD SUCCESSFUL；tally 3976/0/0/2（358 类）→
**3975 / 0 / 0 / 2，359 类**（−2 旧契约测试类共 21 用例：行级规则 8 + code 切分 13；
+3 新类共 20 用例：复生锁 4 + 管道 6 + 承接 10）；门禁
`# 汇总: PARITY 条目=19 NEW 条目=11 FAIL 差异=0 TIE=0 有意差异=1 PNG=147`（新增 P19
「深缩进独立列表行」两宽度 PASS；P03@150 有意差异与正向不变量原样）。

**接线落点**（链路 = 门禁 B 路定义顺序，一字不差）：

| 环节 | 落点 |
| --- | --- |
| 消息级管道 | 新 `internal/chat3/view/ChatMarkdownPipeline`：`MarkdownDocument.parse` →
`toSegments(chatStyleTable, 基础样式)` → § 桥 → `ChatUrlLinkifier.linkify`（换行前整条流）→
`MarkdownPainter.wrapLines`；两级 LRU（扁平段流 = 原文@色#配色代；视觉行 = +定行宽#字号#
度量纪元 `FontService.getRuntimeVersion()`），沿用 `ChatLineLayouter` 布局缓存+纪元失效口径，
每帧零解析；缓存按实例隔离 |
| 引用色登记 | `chatStyleTable()` 每次以 `setQuoteTextColor(getTextSecondaryArgb())` 现值构建
（F3 旋钮与 chat3 次级色恒同源，G4；配色变更经缓存 key 即时失效） |
| § 桥 | `bridgeSectionCodes`：逐段 `TextStyle.applyFormat`（L0 唯一 § 语义实现）切 run，
markdown 样式位随 run 拷贝保留、latex/codeSpan 段恒透传（code 内容字面旧裁定不破）、
纯格式码段整段消失（与 `parseSegments` 空 run 丢弃一致）——chat3 现有 § 颜色语义保住，
「markdown 不引入颜色」不变 |
| 结构判据 | 引用竖条 = 首可视段色 = 引用旋钮现值（`isQuoteRow`）；块级公式 4px 间距 =
整行单 latex 原子（`isBlockMathRow`）。旧行级文本前缀判据（"> "/行首 `$$`）随 L1 剥标记
改为段流结构判定；**行为差**：长引用被换行折断的续行现在也带竖条（旧仅首行带），属
引用块语义的正常化，无既有测试钉旧缺失 |
| URL 跨行 | 换行前整条流链接化 → 每行 link 值恒为完整 URL，气泡路 `UrlChain` 回填机制
不再被触达（系统消息逐行 PRESERVE+续链原样保留）；`ChatUrlLinkifier` 存留件身份不变 |
| HUD 截断 | `clampHudLines`：L2 视觉行 >8 时保 8 行、末行按度量回退后补 `...`
（无度量注入时直补）；行节点 maxLines/ellipsis 防御照旧 |
| headless 同源度量 | `ChatMessageList.SegmentFlowWrapper` 注入缝：生产 = L2 `wrapLines` +
`FontService` 度量（与切分/钳宽/命中一把尺）；测试注入 4px 同源替身，保持「composer 切行
宽 == 渲染换行宽 == 命中度量」既有前提（`longSelfMessageClamps...` 首轮即因两把尺而红，
按铁律修接线而非改断言） |
| 组头/系统消息 | 仍走 `SegmentParser`（`ChatSceneController.uiLibSegmentParser` 角色收窄，
javadoc 已记）；逃生舱 `ChatMarkdownSettings.isEnabled()` 与 `ChatMarkdownInstaller` 零触碰 |

**删除与承接**：`ChatMarkdownLineRule.java`(169 行) 与 `ChatCodeSpanSplitter.java`(127 行，
整类即反引号配对解析；code 样式职责已由 F1 承接进 `MarkdownStyleTable` 包内登记) 整删；
两旧契约测试删除，其契约在 `MarkdownChat3RuleInheritanceTest` 经 L1 公共接缝逐案复验。
为让承接完整，L1 包内两处（零公共面变更）：

1. **F2 补全**〔C1a 已拆：`baseLevel`/`readDeepList` 退役，≥4 前导空格行进缩进代码块字面〕：
   顶层列表块携带 `baseLevel = 1 + 首行前导空格/2`（`MarkdownBlock.baseLevel` +
   `readDeepList` 剥基准缩进），独立成块的 `"  - 乙"`/`"    - deep"` 与 chat3 旧行级规则
   「层级 = 前导空格/2」同缩进；嵌套子列表（depth≥1，contentCol 已剥）保持相对嵌套，
   P08 语料输出逐位不变（门禁实测 PASS）。
2. **markerView 修正**〔C4 已拆：markerView 整体删除，本条与其钉死用例的旧期望同批重定，
   chat3 观感改由输入侧清洗承接〕：`§f` 后带空格再命中块标记时，旧实现把该组空格二次计入
   视图 → 缩进翻倍落回字面（丢 F4 承接）。改为按消费点续切；`fixF4` 既有钉死用例输出不变。

**门禁本体**〔C3b1 已拆：A 路复刻与三档判据整体废止，门禁重基线为 R=commonmark-java 0.21
对拍 B=toLayoutLines 的逐 token 语义矩阵；下文记录的是废止前的历史形态〕：
A 路两旧类语义按 1:1 快照移入私有方法（`classifyReplica`/`codeSpanSplitReplica`，
含行首/行尾 § 剥离、`$` 计数、空配/未闭合字面、跨段不配对、清 link 全谱）；语料、三档判据、
比对引擎、容差数字一字未动。

**M6 复生锁**（`Chat3MarkdownResurrectionGuardTest`，4 条断言，全部反向断言配正对照+地板）：

| 断言 | 反向内容 | 正对照（M5 实测→写死地板） |
| --- | --- | --- |
| ① 文件存在性 | `ChatMarkdownLineRule.java`/`ChatCodeSpanSplitter.java` 恒不存在 | 探测器对 `ChatMessageList.java`/`ChatUrlLinkifier.java` 必须判「存在」 |
| ② 定界解析模式 | chat3 主源代码行 0 命中（实测 0）：反引号字面/`\u0060` 文本/`(char) 0x60`/`CODE_TICK`/`'*'`/`"**"`/`"~~"`/`"$$"`/旧类名标识（注释剥除后扫） | L1 兄弟包同扫描器实测 **21** → 地板 8；门禁 A 路复刻文件实测 **17** → 地板 4；chat3 文件数实测 37 → 地板 30 |
| ③ 生产锚 | 唯一入口必须实调 `MarkdownDocument.parse(`+`MarkdownPainter.wrapLines(`+`.toSegments(`+`ChatUrlLinkifier.linkify(` | 断言本身即正向锚（命中数 ≥1 写进消息） |
| ④ 入口唯一 | chat3 内 markdown 层类型引用收敛在 `ChatMarkdownPipeline.java` 单文件 | 同扫描器对 devtools `MarkdownPage` 必须报出 L1+L2 双引用（反空跑） |

**GL11 计数口径统一申明（§二之四 遗留「两把尺」收口）**：markdown 层守卫与
`UiHudRenderListenerGlFenceTest` 一律**按出现次数计（含注释，逐行 indexOf 累加）**；
`ui/render` 现值 559（历史 386 = 按行去重口径，已在 `MarkdownLayerGuardTest` 注明只认
出现次数口径，地板 100 不变）。

**判读图（接线后重跑，@1x 断言照跑、@4x 照产）**：真实聊天语料人工读图 ≥3 张：
`P10-side@4x.png`（跨行 URL 续链：两路断点/链接色一致）、`P11-side@4x.png`（junction
长文 7 行逐行对齐）、`P14-side@4x.png`（§ 色混排：红/白切换一致）、`P19-side@4x.png`
（深缩进承接：两路同「    • deep」）。重影为 §二之四 1 已确证的场地特性，非回归。

**测试期望变更全清单（本批仅 2 处，均已在提交信息登记）**：
`ChatMessageListTest.orderedListLineKeepsNumberAndIsUnchanged`（段数 1→2，M2 有序标记承接 +
门禁 P09 coalesce 等价口径，可见文本零差）；`normalLinesAreUnaffectedByMarkdownRules`
（行内 `$x$` 1 段字面 → 「foo 」+latex+「 bar」，M2 行内语法面复活裁定 + 门禁 N08 NEW 能力
落地；原意图「不独占行→无块级间距/无 bullet」改正向钉死并加强）。其余 chat3 既有测试
零断言改动；首轮唯一因接线而红的钳宽用例按铁律以注入缝修复（非改断言）。

**真机待验（本批未跑，如实挂账）**：消息列表实际观感（标题/围栏/删除线等 NEW 能力首现于
聊天框）、宽引用块续行竖条、§ 色码与 markdown 样式位叠加的真机手感、HUD 长消息 8 行
截断观感、`MarkdownSettings` 配色热切换。

## 二之七 M7 方案乙落点：块身份行进接缝，三项块级几何落地（2026-09-05，代码批 `8c86a644`）

> 触发：用户实机截图证实三项缺失——①引用嵌套无水平缩进/竖条不分层（chat3 仅有单层
> 2px 竖条，一/二/三层肉眼不可分）；②分隔线是 36~38 个字面 `-`；③围栏代码块无底色。
> 前提核实：src 全树 `IndentPx` 命中 0，引用缩进无任何代码表达；
> `MarkdownDocument` 旧 case QUOTE 只调 `quoteStyle()`（斜体位+颜色），零几何。

**接缝形状（唯一新增公共类型）**：`font.layout.markdown.MarkdownLayoutLine`——
行粒度不可变 DTO：`kind(TEXT/CODE/THEMATIC_BREAK)` + `quoteLevel` + `blockId`(块归属)
+ 行盒几何 `leftInsetPx/indentStepPx/barWidthPx/ruleThicknessPx` + 装饰色
`accentArgb/backgroundArgb` + `segments` + `withSegments`。为什么这比三对 `*IndentPx`
访问器窄：样式表公共面**零膨胀**（18→18，4 个新旋钮 `quoteIndentPx=8/quoteBarWidthPx=2/
ruleThicknessPx=1/blockAccentArgb=0x40FFFFFF` 全包内登记，沿 F1 code 口径先例，
`MarkdownStyleTable.java:70-73`）；块模型不外开（无子树/无 children/无源偏移）；
消费者拿到的是扁平行序列而非需要遍历的树。

**三项几何实现落点**（「几何一律经位置与图元表达，绝不改可见文本」）：

| 项 | L1 | L2（PaintCommand 路） | L3（chat3 SceneNode 路） |
| --- | --- | --- | --- |
| 引用嵌套几何 | `toLayoutLines`（`MarkdownDocument.java:141`）沿 QUOTE 递归加层级，行盒 `leftInsetPx=level×8`；**不写前导空格**（列表 F2 机制原样保留、不侵犯） | `MarkdownLineLayout.blockCommands:200` 每层每行 1 条竖条 BACKGROUND（x=`l×step`，y 相邻成连续柱）；`layoutLines:118` 折行宽=容器−`leftInsetPx` | `ChatMessageList.java:1117` `quoteLevel` 层嵌套 `row[竖条+内层]`（level=1 与旧结构逐位相同）；`ChatMarkdownPipeline.java:64` `RenderedLine` 自有视图（markdown 类型不出管道文件，复生锁④不破） |
| 真分隔线 | `ruleLine`（`MarkdownDocument.java:469`）恒成行 kind=THEMATIC_BREAK；文本有无由**既有** `setThematicBreakText` 旋钮（未新加） | `blockCommands` 横线 = 1px 高 BACKGROUND 铺至内容右缘（`MarkdownLineLayout.java:200` 段） | `chatStyleTable()` 设 `setThematicBreakText("")`（`ChatMarkdownPipeline.java:251`）→ 行身份 RULE 用背景条节点画线（`ChatMessageList.java:1056`） |
| 围栏底色 | 每源行 kind=CODE 同 `blockId`（空源行也带 CODE 身份，`emitCodeLayout:366`） | 连续同 blockId 合并**单条** BACKGROUND 覆盖全部显示行（`MarkdownLineLayout.java:182`） | CODE 行 `setBackgroundColor` + 块内统一宽（`ChatMessageList.java:1065`） |

「不改可见文本」证据：`MarkdownLayoutLinesTest.visibleTextIdenticalAcrossSeamsOnGateCorpus`
在门禁 12 条语料镜像上断言两接缝**行序列化逐字等值**（含行界符计数）；`toSegments` 本体
一字未动（段生成原语 `listMarker/inlineSegments` 抽助手两路共用防漂移）。

**门禁处置（§二之五 铁律）**：`MarkdownChat3ParityTest` 文件**零接触**——判据/容差
（段宽 0.0D、TIE 2.0D、行宽 1、命中区 1）/登记表/比对引擎/语料未改一字。实测汇总
`PARITY=19 NEW=11 FAIL=0 TIE=0 有意差异=1(P03@150) PNG=147` 与基线逐字一致。
为何无新表项：门禁比较面 = 段流文本/样式位/段宽/断点/命中区，**不含** BACKGROUND 命令
与行盒 x 偏移；引用断点差异需「长引用行」才触发，现有 PARITY 语料唯一引用条目 P12 为
短行（@150 扣 8px 仍单行）→ 零新差异。四项「B 有 chat3 无」的有意差异在此节与代码
注释（`ChatMessageListTest.thematicBreakBubbleLineIsSolidRuleNotDashes` 头部）留档；
若未来给门禁加长引用语料，其断点差异将撞 `isIntentionalDivergence →
assertWidthIndependentCodeStyle` 的 P03 专用不变量——那是判据改动，须另裁，不得顺手。

**复生锁锚点演进**：`Chat3MarkdownResurrectionGuardTest` 断言③生产锚字符串随接线更名
（`.toSegments(`→`.toLayoutLines(`、`wrapLines(`→`wrapLayoutLines(`），「>=1 命中」正向
语义与全部地板一字未放松；断言①②④未动。门禁本体未触碰（上段）。

**公共面清单（javap -public）**：`MarkdownStyleTable` 18→18；`MarkdownDocument` 6→7
（+`toLayoutLines`）；`MarkdownPainter` 5→7（+`wrapLayoutLines`/`toLayoutPaintCommands`）；
新公共类型 1 个 = `MarkdownLayoutLine`；`ui/markdown` 顶层 public 类型恒 1；
`ChatMessageList`/`ChatSceneController` public 面 diff 为空；L1/L2 `GL11.` 出现次数恒 0
（同扫描器正对照 `ui/render`=559）。
**C6a 续账（2026-09-06，`javap -public` 实测于 `build/classes/java/main`）**：
`MarkdownDocument` 7→**8**（+静态 `parse(List<MarkdownSpan>)`＝能力①块级文档 span 流入口；
连带源码兼容注记——`parse(null)` 裸 null 由单义变二义，调用方须 `parse((String) null)` 强转，
本仓唯一受影响点 `MarkdownDocumentTest:63` 已同批加 cast）；`MarkdownInlineParser` 公共方法面
**2→2 不变**（`parse(spans)` 实现换为能力②跨 span 连续扫描，签名不动；块级叠加链
`parse(spans, table, StyleTransform)` 为包内重载不进账）；`MarkdownLayoutLine` 全成员 **20
冻结不变**（样式锚点只在包内随块模型走，出接缝仍只 `TextSegment`）；新类型 `StyleTransform`/
`StyleValues` 均 package-private（顶层 public 类型账不变）。

**C6b 续账（2026-09-07，方案甲收尾批，代码批 `28e71094`/`7d92e4f5`/`c9ae3b54`）**：
`MarkdownDocument` 公共静态方法数 **8→8**——`parse(List<MarkdownSpan>)` **改名**
`parseSpans(List<MarkdownSpan>)`：C6a 引入的重载曾使 `parse(null)` 二义编译失败（与该形参「可为
null」的 javadoc 承诺冲突），本批收回该零收益源码破坏；方法从未随版本发布，属周期内自纠、不算
破坏公共面承诺（`MarkdownDocumentTest:63` 的 `(String)` 强转同批退还）。出段样式定序改判（块级链
叠位、span 显式色覆盖块级色、行内位最后）为包内行为，接缝与公共面零接触；`MarkdownInlineParser`
公共面恒 2；`MarkdownLayoutLine` 全成员 20 冻结不动；chat3 `ChatMarkdownPipeline` 仍
package-private、public 成员账恒 0（新增 toSpanStream/logicalForTest 均包内）。

**C7 续账（2026-09-07，§ 划界批，代码批 `2144cfc4`/`2c03e683`）**：**L1/L2 公共签名零变化**——
`MarkdownDocument` public 方法恒 8（`parse(String)` 与 `parseSpans(List)` 都在册；本批只把 chat3
的调用点从后者换回前者，`parseSpans` 自此<b>零生产消费者</b>、能力由 `MarkdownSpanStreamC6aLockTest`
继续钉，且不得被任何 § 相关代码使用）；`MarkdownStyleTable` 恒 19、`MarkdownLayoutLine` 全成员恒 20、
`MarkdownInlineParser` 恒 2、`ChatMessageList` 恒 9、`ChatSceneController` 恒 26；
`ChatMarkdownPipeline` public 恒 0，<b>声明成员（含非 public）20→19</b>（删 `toSpanStream` 与
`flushSpan`；javap -p 逐条数得，反射反空跑地板的实测数同步）。chat3 侧新增
`internal.chat3.viewmodel.StructuredChatReader`（public final）按全成员尺 = 本体 **1**
（`read/1`）+ public 嵌套 `PlayerChat` **3**（`getSender/0`/`getContent/0`/`toString/0`；
嵌套类型不计入外层账，见锁细则 2；私有构造与包内键集合不入账），属集成层
内部类型、不在 markdown 五锚定表内；`MessageGroupModel` 只加包内 `isStructured()`，公共签名零变化。
删除的公共类型 1 个 = `FormatPrefixStripper`（划界后全仓零引用，连本体与直测锁一并拆）。

**测试与出图**：360→362 套件、3979→3992（+13 全新增零删除：L1 行接缝 6、L2 几何 3、
出图入图探针 1、chat3 结构 3）；`MarkdownSoftwareRenderTest` 出图路切命令流渲染，
几何入图配「像素列探针 + mark 批 quad 计数对照」双判据（事故档第八节：能区分是哪个
位置，非墨量非文件数）。读图亲验（修复后光栅器）：03 嵌套引用一/二/三层竖条 1/2/3 根
+ 缩进递增肉眼可分；08 分隔线为整幅实线**非**破折号；02 围栏整段灰底且内容全字面；
00 整页字形完整无错切（先证仪器可读再谈判读，未把任何错切误读为位移）。
新图 sha256：`00-full-page.png` 7961ed63fe8f1845f76410d97b7cbdb60ed01eaa63ee9dd081ba66b5c07ce3de；
`03-nested-quote.png` 8a358db679454176cbd9df90ec88f4a534446769ceb884cf59415146df8a8d01；
`08-thematic-break.png` 4e5fb5b57bd9a652c2d5c429dbb05b9177c196786eb30aa9d6cc27e0b6d9366c；
`02-code-fence.png` d3d3be5d4d3a1051dd9c79b61090b0ac1750171f42d7774c10fbb2fb848b2f12。

**真机待验（本批未跑，如实挂账）**：chat3 气泡内嵌套引用竖条观感、真横线厚度（1px 在
高 GUI Scale 下是否够眼）、围栏底色与气泡底色叠加观感、playground MarkdownPage 手感。

### 二之七·续 门禁登记通道重构（方案甲，2026-09-05 用户授权另裁，代码批 `06a9e45d`）

§二之七 首版写「若未来加长引用语料进门禁，其登记会撞 P03 专用不变量——属判据改动，须另裁」；
用户当日裁定走甲，本节记录重构与实测全程。**本轮主源零改动，仅动门禁测试一个文件。**

1. **通道**：登记条目 = `Divergence{key, reason, invariant}`；`resolveDivergenceInvariant`
   显式查表分派（`MarkdownChat3ParityTest.java` 内），**未知/缺失标识抛
   `IllegalArgumentException`——不默认放行、不退化跳过**；主循环命中登记 →
   `applyIntentionalDivergence` 分派到对应不变量。硬失败本身由新增 @Test
   `divergenceChannelMustHardFailOnUnknownInvariant` 钉死：正对照 = 两个已知标识可解析；
   未知标识与 null 各须抛且异常消息点破语义；反 ∅ 地板 = 登记表逐项真实走分派
   （条目数 ≥1、理由非空）。
2. **P03@150 逐位保留**：迁移到 `CODE_STYLE_WIDTH_INDEPENDENT` 标识下，判据正文参数化但
   字符串逐字不变；通道重构后、P20 入库前 diff.txt 与基线**逐行零差异**（83 行全保留）。
3. **P20 二层引用长文（先测量，不预设结论）**：`>> + 84 CJK`，@150/@269 两档都折行、
   quoteLevel=2。**未登记态实测 = REGRESSION**（@269 5 FAIL：行数 A=6/B=5、行#0
   A=<`>>`>残行 vs B=<正文>、行宽 A=8/B=263 等；@150 同型 5 FAIL；汇总 FAIL=10 TIE=9）。
   差异构成如实记录：① A 路嵌套引用只剥一层 `>` 留残行（chat3 旧缺陷，N03 属 NEW 档佐证）；
   ② 门禁 B 路走旧接缝不扣宽，而 M7 新接缝按层扣宽——扣宽效应由不变量处理器在新接缝上
   直接实测，不依赖 B 路切换。
4. **引用专属不变量 `QUOTE_BREAK_MONOTONIC_TEXT_PRESERVED`**（登记 P20@269、P20@150 两条）：
   ① 断点单调：同一文本合成层级 0..3，首行断点 idx 单调不增且 idx(3)<idx(0) 严格不等
   （防「扣宽没生效」的同义反复）——实测 @269 = 20/19/19/18、@150 = 11/10/10/9；
   ② 文本守恒：B 折行拼接 == 单行原文（不丢不增）且 == A 拼接剥净行首引用标记后的文本
   （剥净必要：A 只剥一层是旧缺陷，不复刻；只钉「几何可变、文本不可变」）；
   ③ 每次调用实际比较计数 ≥6 地板（反分派空跑）。凭什么不弱于逐段等价：逐段等价根本不比
   层级间断点关系（本不变量额外钉单调方向），文本维度取更严口径（整条流逐字等，仅行界
   放开——行界正是被许可的唯一差异维度）。
5. **门禁全量对账（基线 → 现在）**：PARITY 19→**20**（新增 P20，不降）/ NEW 11→11 /
   FAIL 0→0 / TIE 0→0 / 有意差异 1→**3**（P03@150 + P20@两档）/ PNG 147→**153**
   （P20×2 宽×3 图，语料新增的合法后果）。diff.txt 逐行对账：基线 83 行仅汇总行更新，
   其余逐字保留；新增 P20 四行（2×DIVERGENT + 2×INVARIANT）。
6. **不碰清单全守**：容差 0.0D/2.0D/1/1 一字未改；三档语义未改；既有语料不删不改；
   `compareParityEntry` 函数本体一字未动（比对字段未减）。
7. **测试计数**：3992→**3993**（+1 = 通道自检 @Test；零删除）；全量
   `gradlew build --offline --console=plain` = BUILD SUCCESSFUL（0 失败 0 错误 2 跳过）。
   反向核查：ui/markdown GL11.=0（正对照 ui/render=559）；MarkdownStyleTable 公共方法
   恒 18（本轮未触主源）；ChatMessageList/ChatSceneController public diff 为空
   （本轮 git diff 仅门禁测试 1 文件）。
8. **本节的自我限制（残余风险，必读）**：硬失败只挡得住**沉默**放行，挡不住**设计出来**的放行——
   「登记一条差异 + 配一条自洽不变量」自此成为一条**可复制的变绿路径**。故立**常设规矩**：
   **新增 `INTENTIONAL_DIVERGENCES` 条目、新增不变量标识，一律与「改容差/改判据」同级，
   须经用户裁定**，实现方不得自行登记。另如实标注 P20 不变量的**取证边界**：它在 A 侧剥净
   行首引用标记之后才比对文本（A 只剥一层系旧缺陷，不复刻），因此 **P20 不再断言 A/B 几何
   一致**，只断言「B 自身断点单调 + B 文本与 A 语义文本等值」——这是**合法的收窄，
   不是等价的强度**。且实测序列 20/19/19/18 说明 8px/层扣宽在 269px 容器上仅移动一两个字，
   **该语料钉住的是一件小事**；若日后调大 `quoteIndentPx`，必须重测该序列。

9. **M8 装配侧第二套真相：围栏底色「三表面三口径」，块内统一宽上收 L2（2026-09-05，用户裁定，代码批 `4ba7d871`）**

   > **【M9 就地更正｜本条开头的归因是假的，事实本身是真的】**
   > 原文写「用户实机发现 devtools 页围栏底色**右缘参差**」——**这个归因错了，是父代理读图造成的假前提**，
   > 我（实现侧）未复核就沿用。用户实机报的是「底色**三截**」，那是**纵向**不连续（每行底色只有 14px 高、
   > 行间留 8px 缝），与横向参差不是同一件事，见第 10 条。
   > 但本条盘点出的「三表面三口径」经 M9 实测**确实成立**：在 `10dbe20b`（M8 前）同一探针下，页面三条
   > 围栏底色矩形实测 **w=92 / 302 / 11**（横向真参差），M8 后统一为 **302 / 302 / 302** ——
   > 所以 M8 的横向修复**有效且必要**，只是它**没有解决用户报的那个问题**。
   > 顺带更正本轮一度出现的另一个相反假前提：「列布局 shrink-wrap 到最宽子 → 页面 `preferredWidth`
   > 不决定像素、M8 没改任何像素」——实测证伪：卡片列 `gap=8`、子节点按各自 `preferredWidth` 排布
   > （实测盒宽 92/302/11 与首选宽逐条相等），首选宽**就是**决定底色矩形的那组量。
   > **教训（已写进事故档 §八）**：读图必须同时给横向与纵向实测游程，只量一个维度等于没量。

   逐处核实到行后确认：**同一件几何事实被抄了两份、漏了第三份**（下述三口径盘点与 M8 修复内容不变）。

   - **(a) L2 出图路** `src/main/java/club/heiqi/uilib/ui/markdown/MarkdownLineLayout.java:182`（本批**前**行号）
     —— 连续同 `blockId` 的 CODE 行合并成一个矩形（合并本身是对的），但矩形宽取 **容器右缘** `contentRight`；
   - **(b) 聊天面板** `src/main/java/club/heiqi/uilib/internal/chat3/view/ChatMessageList.java:1088-1092`
     —— 自建**私有** `Map<Integer,Integer> codeBlockWidthPx`，按 `blockId` 查块内最宽行，`lineWidth = max(行宽, 块宽)`
     （视觉干净，但是第二份实现）；
   - **(c) devtools 页** `src/main/java/club/heiqi/uilib/internal/devtools/playground/pages/MarkdownPage.java:140-143`
     —— 每行宽 = **该行自身文字宽 + `CODE_BG_PAD_PX*2`**，完全没有块口径 → 横向右缘参差
     （M9 实测证实：M8 前该页三条底色矩形 w=92/302/11，M8 后统一 302/302/302）。
     **注：这不是用户实机报的「三截」——那是纵向问题，见第 10 条。**

   即「块内统一宽」没有单一产地：(a)(b) 各写一遍且**口径还不同**（容器宽 vs 块内最宽行宽），(c) 根本没写。

   - **裁定与落点**：块内统一宽是**度量事实**（要持 `TextLayoutService` 才量得出来），产地只能是 L2。
     接缝新增 `MarkdownLayoutLine#getBlockContentWidthPx()`，由 `MarkdownLineLayout#unifyCodeBlockContentWidth`
     在 `wrapLayoutLines` 折行后按 `blockId` 聚合写入。**取值语义（不留未定义值）**：CODE 行 = 同块全部 CODE
     视觉行「自身文字宽（`ceil(段流推进宽)`）」的最大值，下限 1（整块皆空行也画得出一条可辨识底色）；
     非 CODE 行恒 `0 = 不适用`；L1 逻辑行恒 `0 = 未算`（L1 纯解析层零度量）。三侧改读同一个数：
     (a) 合并矩形宽 = 该值（旧「铺满容器右缘」作废——那是与两路消费者都不一致的**第四套数**）；
     (b) 私有 `codeBlockWidthPx` 机制**整块删除**，`RenderedLine` 逐字透传 L2 值；(c) 改读该值。
     内衬 `CODE_BG_SIDE_PAD_PX` / `CODE_BG_PAD_PX` 留在各自视图（节点盒装饰口径，不是块宽口径），
     `max(本行宽, 块宽) + 2*pad` 与旧数值逐位等价 → 聊天面板**零观感变化**。
   - **兼容性处理（选拷贝法，构造器一个不加）**：公共 10 参全字段构造器**签名一字不动**（该类型已对外、
     有存量消费者：L1 装配、门禁 `MarkdownChat3ParityTest:541` 合成行、L2 复制路），另加
     `withBlockContentWidthPx(int)` + getter，实现收在**私有** 11 参构造器里。理由：① 块宽是**换行之后
     才存在的派生量**，本质「从已有行派生一行」，与既有 `withSegments()` 同构；② 再加一个全参重载会把
     「哪个是权威构造入口」变成两代并存，下次增字段继续膨胀；③ L1 永远产不出这个数，却让 L1 看见一个
     「度量字段形参」本身就是误导。`withSegments()` 同步透传该字段（防「换段流丢块宽」）。
     公共成员 15 → **17**（+1 getter +1 拷贝法）。
   - **上收后的单一真相链路**：`MarkdownDocument.toLayoutLines`（身份/缩进/颜色，块宽 0=未算）→ §桥 +
     换行前链接化（`withSegments` 保身份）→ **`MarkdownPainter.wrapLayoutLines` = 块宽唯一产地**（按
     `blockId` 聚合）→ 三条消费路只读 `getBlockContentWidthPx()`：L2 `blockCommands` 出合并 BACKGROUND
     矩形宽 / `ChatMarkdownPipeline.RenderedLine` 逐字透传 → `ChatMessageList` 钉行节点宽 / `MarkdownPage`
     钉行节点宽。全仓不再有任何一处「按 blockId 求块内最宽」。
   - **核心锁（三面对一份真相的机器形式）**：新增 `MarkdownBlockContentWidthLockTest`（5 用例）。判据不是
     「矩形读的就是 getter」这种自证，而是**三方同数**：合并 BACKGROUND 矩形宽 == 该行
     `getBlockContentWidthPx()` == **独立 oracle**（只用公共入口 `MarkdownPainter.lineWidthPx` 逐行量「本行
     自身宽」再取块内最大）。再加两条**反同义反复地板**：≥3 个块的成员行宽互不相同（否则被废弃的 (c)
     逐行口径也能过等式）、≥4 个块宽严格小于容器宽（否则被废弃的 (a) 铺满口径也能过）。实测参与比较：
     CODE 视觉行 14 / 合并矩形 6 / ragged 块 5 / 块宽<容器 6（地板分别写死 10/6/3/4）。**正对照**：无围栏
     文档零 CODE 矩形 + 全行 getter 取定义值 0，且同一扫描器在围栏语料上必须恰命中 1 条矩形（证明「零
     命中」不是扫帚坏）。**突变检验**（各实跑一遍）：矩形改回铺满容器 → 核心锁红 1 条；聚合退化为不写值
     → 红 4 条（核心锁 + 块内等值锁 + 定义值语义锁 + 页面级锁）；页面改回逐行自字宽 → 页面级锁红。
     恒真断言已排除。
   - **页面级是真页面断言**（未用 L2 断言冒充）：`PlaygroundPageRegistryTest#markdownPageCodeBlockLineNodes
     ShareWidthPerBlock` —— headless 构造 markdown 页后遍历**已装配的 scene 节点**，断言同一围栏块内全部
     CODE 行节点 `getPreferredWidth()` 彼此相等（识别口径：非空段流 + 左右内衬 + 非零背景色；**正对照**：
     同页两个围栏块的统一宽必须互不相等，钉住「按块取值」而非全局常量/铺满容器；地板：块数 ≥2、
     节点数 ≥4）。**刻意并入该既有类而不是新开测试类**，原因见本节末条。
   - **守卫是否扩到装配侧 —— 本仓判断：扩，但只扩「聚合产地唯一」这一条窄锚。** M6 复生锁
     `Chat3MarkdownResurrectionGuardTest` 守的是**解析侧**（锚点 = 反引号 / `*` / `~~` / `$$` 等**内容字符** +
     旧类名 + 文件存在性），它能机器判定是因为「重新解析 markdown」有具体字符特征。装配侧的第二套真相
     **没有这种字符特征**：`Math.max(lineWidth, blockW)`、`+2*pad`、`min(lineWidth, maxBubble-reserve)`
     在消费者里都是**合法且必要**的本地几何。若把守卫写成「消费者不得对块几何做任何计算」，就会把内衬、
     气泡钳宽、引用扣宽一起判成违规 —— 那是**防写代码，不是防第二套真相**，故**不采**。
     **采的窄形态**：给聚合起唯一专名 `unifyCodeBlockContentWidth`，守卫只断言 ① 该 token 在主源里只出现
     在 L2（定义+调用 ≥2 = 正对照）；② 三个消费者文件里该 token 与被删的私有机制名 `codeBlockWidth`
     各 0 命中；③ 消费者代码行必须**真的读 getter**（合计 ≥3 命中，反 ∅）。扫描沿用 M6 同一「剥注释后
     代码行」口径（否则注释里提一句被禁 token 就假红）。落点
     `MarkdownBlockContentWidthLockTest#blockWidthAggregationMayLiveOnlyInL2`。**如实标注局限**：改名即可
     绕过 —— 静态窄锚防的是「把那份实现整份复制过去」这一最常见形态，防不住「重写一个等价聚合」；
     真正兜住**数值**漂移的是上面的三方同数核心锁与页面级锁。两者互补，都刻意不扩宽。若日后要把守卫
     扩成「禁止消费者新增任何块级本地几何计算」，属门禁语义改变，须另裁。
   - **门禁与计数对账**：门禁 `MarkdownChat3ParityTest` 判据/容差/登记表/比对引擎一字未动；与基线（同机
     `10dbe20b` 重跑产物）逐项等值：PARITY=20 / NEW=11 / FAIL=0 / TIE=0 / 有意差异=3 / PNG=153，且
     `diff.txt` 87 行**逐行完全一致**。全量 `build --offline` = BUILD SUCCESSFUL；测试计数 3993 → **3999**
     （+6 = 新锁 5 条 + 页面级锁 1 条，**删除 0 条**），套件 362 → 363（+1 新测试类），
     0 failures / 0 errors / 2 skipped 与基线同。反向核查四项：① `ui/markdown` 的 `GL11.` 按出现次数 =
     **0**（正对照 `ui/render` = **559** > 0，同口径）；② `MarkdownStyleTable` `javap -public` 方法数恒 **18**
     （+1 构造器 = 19 成员，与基线逐行等值）；③ `ChatMessageList` / `ChatSceneController` 及 5 个嵌套
     public 类型与基线**逐成员差异为空**（本批只改私有成员与包内 `RenderedLine`）；④ `MarkdownLayoutLine`
     公共成员 15 → **17**。
   - **本批暴露的一条既有跨测试耦合（非我引入，但必须留档）**：`PlaygroundButtonRowLayoutTest` 的「home 页
     按钮行不越界」断言用的是**真实字体测量**（`SceneHostAssembly.defaultMeasurer()` →
     `DefaultTextMeasureService` → `FontService.getInstance()`），而字体注册**异步**：注册完成后 home 页文本
     变宽，行子节点越界 22~36px。基线树（`10dbe20b`）实测——加一个「仅构造 markdown 页、零断言」的前置
     测试类且类名排序在 `Playground*` 之前 → **3/6 红**；同一个类改名排到其后 → **0/4 红**。本批最初新开
     `MarkdownPageCodeBlockWidthTest` 正好踩中前者（同命令一红一绿），据此把页面级锁**并入基线就已构造
     全部页面的 `PlaygroundPageRegistryTest`**（排序在 row 测试之后）→ 回到基线暴露面。**遗留待裁**：这条
     耦合本身仍在（任何先跑并构造 markdown 页的新测试类都会暴露它），修法是给该测试注入确定度量端口
     或等注册完成再断言 —— 均属验证设施改动、与本批无关，未动，等放行。

10. **M9 真缺陷：围栏底色「三截条」是纵向不连续，不是横向参差（2026-09-05，用户实机判定）**
    用户换到含 M8 的 0.352 jar 后实机判定：**底色是三截条**，并要求把页面级锁从「锁记账值」改成「锁像素」。
    M8 那条横向修复方向没错但**没解决用户报的问题**（第 9 条已就地更正假前提）。

    - **实测根因（判据是数值不是猜）**：headless 构造 markdown 页 → layout → paint → replayer 收到后端
      调用，逐条量出 `MarkdownPainter.lineHeightPx(CODE 行, 14) = **14**`；卡片列 `PlaygroundKit.card()`
      的 `gap = SceneChromeTokens.GAP_MD = **8**`；三条 CODE 行盒 `y=36/58/80, h=14` → **节距 22 = 14+8**；
      底色矩形 `fillRect t=288..302 / 310..324 / 332..346` → **每条 14px 高、夹 8px 无底色缝**。
      即候选 **(i) 成立**：行距多出的 8px 属于**卡片列 gap（在所有节点盒之外）**，底色只画节点盒。
      候选 **(ii) 证伪**：底色矩形高等于节点盒高（14）、宽等于盒宽（含左右内衬 3）——画的是**整盒**
      而非 em-box；上下内衬为 0 只是更难观，不是根因。
    - **三表面各自状态（逐个实测，不是一句「只有页面有」）**：
      | 表面 | 纵向状态 | 证据 | 处置 |
      |---|---|---|---|
      | (a) L2 出图路 | 本就无缝 | 合并矩形 `top=tops[i]..tops[j]+heights[j]`；实测矩形高 == 块内全部行高之和 | 加锁 `mergedCodeRectHeightCoversAllRowsSeamlessly` |
      | (b) 聊天面板 | 本就无缝 | 内容列 `column()` 默认 `gap=0`；实测 CODE 行盒 `y=5/23/41/59, h=18` → 节距==行高 | 加锁 `fencedCodeBubbleLineBoxesAreVerticallySeamless` |
      | (c) devtools 页 | **有缺陷** | 卡片列 `gap=8` 被当成块内行距，每行各挂一个底色节点 | 本条修复 |
      为什么只有页面有：页面把「块内行距」外包给了**卡片的块间距**（`gap` 的语义是块与块之间，被误用成
      块内行与行之间）；L2 用 y 游标、聊天面板用 `gap=0` 的内容列，两者行距恒等于行高。
    - **修法（复用既有能力、零新图元、可见文本一字未改）**：`MarkdownPage` 把「连续同 `blockId` 的 CODE
      行」合并成**一个块级容器节点** `SceneNode.column(0)`，底色与左右内衬打在容器上，行节点只带段流
      与行高（不再各自打底色）。容器盒天然覆盖全部子行与其间隙 → 实测底色从三条 14px 带变成**一条
      42px 带**（`fillRect t=288..330`）。容器上下内衬保持 0，块外缘观感与旧版逐字节相同，只消掉块内
      那条 8px 缝。口径与 L2「连续同 blockId 合并单矩形」一致——是同一件事的**纵向**版本。
    - **页面级锁改成锁像素**：删除上一批那条 `getPreferredWidth()` 记账值断言（**不留**不决定像素的
      绿断言），新增 `PlaygroundPageRegistryTest#markdownPageCodeBackdropIsOneContinuousPixelBlock`：
      headless 造页 → `ScenePaintCapture`（layout→paint→replayer→后端调用）→ 把后端收到的**实心面**调用
      （`fillRect`/`drawSurface`）按调用顺序覆盖式栅格进 ARGB 缓冲 → **只在像素上断言**，判据**形状无关**：
      ① **无缝**——任意列上两段底色之间的空档若 `>0 且 < 一个行高`，即块内裂缝 → 红；
      ② **跨行连续**——至少一个底色带高度 `>= 2×行高`；③ **横向统一**——带内每一行水平跨度的左右缘必须
      相同；④ **正对照**——同页无围栏的「标题」卡在该色上零像素；⑤ **反 ∅ 地板**——底色像素总量 `>=3000`、
      横向带数 `>=2`、CODE 行比较数 `>=4`、多行带数 `>=1`。行高与底色都由探针从 L1/L2 接缝现取（不硬编码）。
      如实申明：场景侧**没有**现成的 scene→`SoftwareRenderFrame` 桥（那条软件光栅路只吃字形批），故像素
      由「后端收到的实心面指令流」重建——它正是真机 GL 收到的同一批几何；文本调用刻意忽略（取样落在左内衬
      列，不受字形墨影响）。
    - **突变实测（证明锁不是恒真）**：把页面改回旧形状（容器 `gap=8` + 底色下放到每行节点）→ 像素锁红，
      且红在**像素理由**上：`x=15 第 0 段末 y=301 与下一段起 y=310 之间空档 8px < 行高 14px`，命中 **91 处**
      （91 = 该块底色覆盖的列数，逐列都看到同一条缝）。恢复修复 → 绿。另两把锁（L2 行高之和、聊天面板
      行盒相接）在各自方向上同样可红，且都带正对照地板。
    - **验收对账**：门禁 `MarkdownChat3ParityTest` 一字未动，与基线 `fe1045af`（同机重跑）`diff.txt`
      **87 行逐行完全一致**，汇总 PARITY=20 / NEW=11 / FAIL=0 / TIE=0 / 有意差异=3 / PNG=153 全等，
      PARITY 未降。全量 `build --offline` = BUILD SUCCESSFUL，测试计数 3999 → **4001**（+2 = L2 纵向
      无缝锁、聊天面板行盒相接锁；页面那条是**一删一增**（记账值锁 → 像素锁）净变化 0；**删除测试 0 条**），
      套件 363 不变，0 failures / 0 errors / 2 skipped。反向核查：`ui/markdown` 的 `GL11.` 出现次数 **0**
      （正对照 `ui/render` = **559**）、`MarkdownStyleTable` 公共方法恒 **18**、`ChatMessageList` /
      `ChatSceneController` / `MarkdownPainter` / `MarkdownPage` 与基线**逐成员差异为空**、
      `MarkdownLayoutLine` 公共成员维持 **17**（本批未加公共面）。像素锁稳定性：单独 3 轮 + 与
      `PlaygroundButtonRowLayoutTest` 配对 3 轮 = **6/6 绿**（继续写在 `PlaygroundPageRegistryTest` 内，
      不新开测试类，见第 9 条末的跨测试耦合留档）。
    - **本条给「三面对一份真相」补的边界**：M8 只统一了**横向**一个数，用户看到的缺陷在**纵向**——
      「单一真相」必须按**几何量的完整维度**收（宽 + 高 + 位置），只锁一个轴会留下同样致命的第二套口径。
      第 9 条的窄锚因此**不扩**：纵向连续性由「块级容器 = 单矩形」这一实现形状加三把像素/几何锁共同保证，
      而不是再立一条静态 token 锚。

11. **M10 两条实机裁定落地：引用竖条连续化（页面层专属）+ 列表续行对齐正文列（2026-09-05，代码批 `1c27a847`）**

    用户 2026-09-05 依实机 887×1320 像素实测裁三条：①引用竖条合并成连续条；②列表续行对齐正文列；
    ③顺手改掉假文案。③已随本批完成（`SAMPLES` 「分隔线」卡说明改「--- 产真横线（1px 铺内容宽）」、
    「列表与续行」卡说明补「并对齐正文列」）。①②是本条。

    - **(a) 两句被推翻的旧注释就地改写成新事实（不得只删不写）**：`MarkdownDocument.listMarker`
      javadoc 的「M7 行接缝同样不给列表开几何通道——引用几何才走行盒」与 `MarkdownLayoutLine.Kind`
      javadoc 的「标题/列表/普通段对 L2 无块级几何差异，恒 TEXT」均被裁定②作废，新版文字写在原处。
      **通道裁定（定死，不再发明）**：复用 `leftInsetPx` 作接缝上唯一「行左偏移」真相
      （引用份额 `quoteLevel × indentStepPx` + 列表续行的正文列），**不加新几何字段、不开样式表旋钮**
      ——正文列是度量事实，零度量的 L1 算不出像素；`MarkdownStyleTable` 公共方法恒 **18** 不变。
      公共面净增 = `Kind.LIST` 枚举常量 + `MarkdownLayoutLine#withLeftInsetPx(int)` 拷贝法
      （与 `withSegments`/`withBlockContentWidthPx` 同形，私有 11 参构造器 + 公共 10 参冻结不动）；
      `MarkdownLayoutLine` 外层类公共成员 **17 → 18**（本仓无守卫测试钉该数，javap -public 实测在此记档）。
    - **L1 地基（测试钉死）**：`emitListItemLayout` 把 `listMarker` 计算提到 `startBlock` 之前，
      标记非空 ⇒ 首行 `Kind.LIST`、圆点配空串 ⇒ 退 `TEXT` 零偏移；`appendOne` 让内嵌 \n 的续行
      继承 curKind/curBlockId ⇒ **同 blockId 内只有第一行带标记段且恰居 `segments.get(0)`**——
      锁在 `MarkdownLayoutLinesTest#listIdentityMarksOnlyFirstLineOfEachBlock`。
      可见文本跨两接缝逐字等值由既有对拍钉（几何走 px 不走文本，裁定 B 底线），语料新增 L01 条。
    - **L2 唯一产地**：`layoutLines` 前置扫按 blockId 记「首条 LIST 行下标 + 正文列
      = ceil(该块标记段推进宽)」——嵌套项标记段自带 F2 前导空格 ⇒ 正文列天然逐级变宽
      （实测 13/25/38px 与有序 19/20px，对上实机正文首墨 45−32=13 系）。标记逻辑行的**第一个**
      视觉行保持原 inset，其后每个视觉行（含同行软折、含同块后续逻辑行）`inset + 正文列` 且可用宽
      同扣——`wrapVisualLine` 扩为「首行/续行」双宽重载，**两宽相等时与旧单宽逐位一致**，
      段流路 `wrap()` 恒走该形态 ⇒ 门禁比对路零扰动。竖条按 `l×step`、CODE 底色按
      `leftInset+blockContentWidth`、真横线按 `leftInset` 与 LIST 偏移正交 ⇒ **`blockCommands`
      零改动**（正交性由锁③机器钉：竖条 x 恒 0 槽 + SEGMENTS.left 与视觉行 inset 逐行相等）。
      `withLeftInsetPx` 只在算出的偏移不同才重建，非 LIST 行路径一字不改。
    - **(b) M10a 取证结论：只有页面断，修复只落在页面层**。L2 `blockCommands` 的 y 是游标
      （`tops[k]=cursor; cursor+=heights[k]`）⇒ 相邻同层竖条首尾相接；一次性探针实测同一样本
      三槽 bars=7/3/2、**接缝=0**（跑完即删，结论与判据转正进页面锁）。聊天面板内容列 `gap=0`、
      行盒高==节距（M9 锁 `fencedCodeBubbleLineBoxesAreVerticallySeamless` 在案）。页面缺陷真身：
      旧 `quoteWrap` 给**每条行**各挂一层 `row[bar(fillParentHeight), content]`，而
      `PlaygroundKit.card()` 列 gap=8 被当成行距 ⇒ 条只有 14px、行间 8px 空档。修法=两趟装配：
      第一趟产 `(node, quoteLevel, accentArgb)` 单元（围栏合并/真横线/普通行口径不动），第二趟对
      「连续 quoteLevel>=1」极大段递归 `quoteGroup` 成套容器 `row[贯穿竖条 + 内层 column]`，
      内层列 gap **恒读 `card.getGap()`**（SceneNode.getGap() public，禁硬编码——改 gap 条随行距走）；
      旧 `quoteWrap` 整方法删除不留死代码；零新图元。**边界**：`blankLine()` 产 quoteLevel=0
      ⇒ 相邻两引用块天然断组，绝不跨组连条（本页样本恰因 readQuote 把「空行后仍是 > 行」并成
      同一引用块而 7 行一组——连续条/断组两态都由同一判据「极大连续段」统一）。
    - **文字位置一字不动的反自证钉法（本批踩过的坑，记死）**：第一版像素锁拿「实测 drawSegments 行 y」
      同时当行距真值与游程期望——突变（内列 gap→0）下两者一起挪，锁**跟着假绿**（突变检验当场抓出）。
      改后判据双向独立：行距式 `ys[i+1]-ys[i] == 行高 + card.getGap()` 与条高式
      `run高 == n×行高 + (n-1)×card.gap`（行高/层级/条宽/步距全从接缝现取），任一 mutation 只挪一边即红。
    - **(c) 已知边界（本裁定未覆盖，如实记「未完成」）**：裁定②只覆盖**同 blockId 的懒延续/软折续行**。
      松散项标记行之后的第二段落、以及嵌套列表等子块在 L1 各自 `startBlock` 另起 blockId ⇒
      **拿不到父项的正文列**（嵌套子项自身是带更宽标记的标记行，其自身续行已覆盖；「父项第二段落
      缩进不齐正文列」为已知残留）。要覆盖需给接缝开「块父子归属」——超出本轮裁定范围，未做。
      （**2026-09-05 M10d 续补第 13 条已闭合本残留**；并纠正本条预想：实测本仓解析器把
      「- a / 空行 / b / 空行 / c」并进同一 blockId 段落，b/c 并非「另起 blockId」——真正
      另起块的是项内标题/引用/围栏/嵌套子项，见第 13 条「实况发现」。）
    - **(d) 门禁与计数对账（E 序实跑）**：门禁判据/容差/登记表/不变量标识**一字未动、零新增**，
      汇总 PARITY=20 / NEW=11 / FAIL=0 / TIE=0 / 有意差异=3 / PNG=153 与基线逐项等，且
      `diff.txt` 与**基线提交 b4ebf431 worktree 同机重跑产物逐行对账 87 行 0 差异**。
      全量 `build --offline` = BUILD SUCCESSFUL：suites 363→**364**（新 `MarkdownListContinuationLockTest`），
      tests 4001→**4010**（+9 全新增、删除 0：L1 身份 2 + L2 锁 4 + 页面像素 2 + 聊天 L3 锁 1），
      0 failures / 0 errors / 2 skipped。层界反向核查：`ui/markdown` `GL11.` 出现次数 **0**
      （同扫描器正对照 `ui/render`=**559**>地板100）、`MarkdownStyleTable` 公共方法恒 **18**、
      `ChatMessageList`/`ChatSceneController` 本批文件零接触（公共面 diff 恒空）。
      聊天实测锁：`markdownListContinuationGetsNoDoubleOffsetAndFitsBubble`——续行与标记行盒左缘重合
      （视图不得二次施加正文列）+ 全部行盒不出气泡左右缘。
      **突变实跑全红**：①内列 gap→0 ⇒ 页面引用锁红（行距 22 vs 14）；②页面 padding 写入摘除 ⇒
      列表像素锁红（x 位移期望 13 实得 0）；③L2 正文列清零 ⇒ L2 四条锁连同正对照全红。
      已知跨类耦合（`PlaygroundButtonRowLayoutTest` 字体异步注册）本批全量 build 内未触发红。
12. **M10c 聊天面补票：列表正文列真正落到气泡（2026-09-05 用户裁定「补」，代码批 `6300a0f2`）**

    M10b 只让正文列进了接缝与页面/出图路，聊天面板当时「不消费、只透传」（第 11 条挂账）。
    用户当日裁定补上。本轮改动全在 L3 内部，**三个类公共面逐位不变**（javap -public 前后
    对数：`ChatMessageList` 9→9、`ChatSceneController` 26→26、`ChatMarkdownPipeline` 0→0；
    `RenderedLine` 为包内 static final 嵌套类，其新增成员不算公共面）。

    - **前提一（取证在后，动手在前）**：`RenderedLine.leftInsetPx()` 在改动前全仓只有
      `ChatMarkdownPipeline` 自己的省略号路读（:414-415），`ChatMessageList` 里
      `leftInset` 出现 **0 次**；而 `RenderedLine` javadoc 写着「消费端行盒/钳宽 reserve
      同源用」——写下时即假。本轮**把这句变成真**（javadoc 就地改写成新事实并标注其历史，
      不留假话）。
    - **前提二（决定实现形状）**：chat3 的引用水平缩进走私有常数
      `QUOTE_BAR_WIDTH_PX(2)+QUOTE_GAP_PX(6)`（:89/:93 定义，:1070/:1085 reserve、:1109/:1116
      嵌套结构消费），**不读样式表 quoteIndentPx**——8==8 是巧合等值。故**绝对禁止**把
      `leftInsetPx` 整值当 padding 施加：那会把引用份额算两遍（结构一遍+padding 一遍）。
      唯一正确反解式与页面同式：`listExtra = max(0, leftInsetPx − quoteLevel × indentStepPx)`。
    - **任务面 1：接缝搬运 indentStepPx**。`RenderedLine` 增字段 + 包内访问器，
      **逐字透传** `MarkdownLayoutLine#getIndentStepPx()`（M8 blockContentWidthPx 同范式）；
      生产路（`render()`）、换行替身注入路（`wrapOverride`）与 HUD 截断重建
      （`clampHudLines`）三处同源填充，不留两口径——替身路的逻辑行 inset 本就不带
      M10b 悬挂列（既有「缺度量替身」语义，未动），但步长必须到位，否则消费端反解失真。
      `renderedForTest` 合成行几何字段取定义值 0（无块身份），不是第三口径。
    - **任务面 2：ChatMessageList 施加（纯内部装配）**。① 正文列施加为
      `setPadding(0, codePad, 0, codePad + listExtra)` 的**显式合成**——CODE 行恒非 LIST
      身份 ⇒ 两项必有一项为 0，但写成加法，不靠「后句覆盖前句」的巧合；② 钳宽 reserve
      同扣 `listExtra`（钳宽与偏移是一式两面）；③ rule 行 roomy 用同式（其 listExtra
      恒 0，同式防漂移）；④ 系统/纯文本路（无 RenderedLine）恒 0，零新分支语义。
      HUD 复核：`ChatCardComposer`（viewmodel 包）grep `RenderedLine` **0 命中**，
      HUD 与容器两形态共用 `ChatMessageList` 同一装配块 ⇒ 无第二条 HUD 装配路，未顺手改。
    - **锁的翻转（第 11 条聊天锁的旧断言「续行与标记行盒左缘重合」钉的是补票前的世界，
      本轮就地改写成新事实）**：
      ① `markdownListContinuationGetsNoDoubleOffsetAndFitsBubble`（重写）——续行**内容左缘**
      （行盒 x + 左内衬）与标记行之差 == 测试内**独立量出**的标记推进宽（自造「圆点+空格」段
      逐码点 `resolveAdvance` 求和取 ceil，与被测写入路径、接缝读值零共享实现），且
      `!= 2×列`（钉引用份额没被算两遍）、== 接缝反解值（`leftInsetPx − ql×indentStepPx`，
      管道透传贯通证据）、标记行左内衬恒 0；正对照拆出独立用例
      `plainParagraphContinuationKeepsColumnOrigin`（两行短段落左缘差恒 0，防正文列泄漏，
      与字体注册态无关）。② 新增 `markdownListInsideQuoteAppliesColumnOnceOnTopOfStructuralIndent`
      ——引用内列表续行内容左缘 == 标记行内容左缘 + **一份**列（结构引用偏移两侧同额抵消），
      并显式断言 `!= 标记行 + 整值 leftInsetPx`；前置自检钉「该行接缝 inset 确含引用份额」
      防样本退化成顶层列表的空转正对照。③ 钳宽断言踩坑记档：布局引擎 SHRINK 聚合只数子节点
      `preferredWidth`、**不计子内衬**，用 bubbleBox 反推内宽会让断言恒真——账面值断言
      `preferred + 左右内衬 ≤ 生产公式内宽`（`round((chatWidthFor(400)−2padX)×ratio) − 2padX`，
      含「inner < 换行宽」的反 ∅ 工况自检，保证钳宽分支真被踩到）。地板：rows≥3、
      续行族≥2（实测 rows=8、族=7，HUD 截断内）；引用锁族≥2（实测 6）。
    - **突变实跑（两向全红）**：① reserve 摘掉 `+ listExtra` ⇒
      「行账面值（preferred+左右内衬）不得超气泡内宽」红（line=111 inner=99）——第一版该突变
      曾**存活**，根因是测试钉的 4px/码点替身度量与生产换行宽不同源、钳宽分支从未触发；换
      `uiLibSegmentMeasurer` + 生产公式内宽后抓红（教训：**钳宽类断言必须自带工况自检**）。
      ② 反解式摘掉 `− ql×indentStepPx`（整值施加）⇒ 引用组合锁红
      「引用内列表标记行不得吃正文列 expected 0 but was 8」——引用份额算两遍当场现形。
    - **仍存边界（如实，不销账）**：(a) 第 11 条 (c) 不变——松散项第二段与标记行之后的
      嵌套子块另起 blockId，拿不到父项正文列（本裁定只覆盖同块懒延续/软折）；
      （**M10d 第 13 条闭合**；「第二段另起 blockId」的形态描述系预想，与解析器实况不符——
      第二段是并入同块的段内行且旧机制已覆盖它，真缺口为另起块与嵌套标记行祖先列，见 13 条。）
      (b) chat3 私有常数 2+6=8 与样式表 `quoteIndentPx=8` 仍是**巧合等值**：本轮靠透传
      `indentStepPx` 让反解不再依赖巧合，但 chat3 **结构嵌套本身仍用私有常数**——
      归入既有待办「chat3 clamp 字面量收 named 常数」，不得当已完成。
    - **回归对账**：门禁判据/容差/登记表一字未动，汇总 PARITY=20 / NEW=11 / FAIL=0 / TIE=0 /
      有意差异=3 / PNG=153 与基线等，`diff.txt` 87 行与第 11 条留档基线**逐行 0 差异**
      （padding/钳宽是几何，不触段流文本与断点，如预期）。全量 `build --offline` =
      BUILD SUCCESSFUL：suites 364→364（无新类）、tests 4010→**4012**（+2 净新增：引用组合锁、
      防泄漏独立对照；翻转 1 条；删除 0），0 失败 0 错误 2 跳过。层界：`ui/markdown`
      `GL11.`=0（正对照 `ui/render`=559）、样式表公共方法恒 18、`MarkdownLayoutLine`
      公共成员维持 18（**本批零新接缝成员**）。M10 的页面像素锁、L2 悬挂列锁、L1 身份锁
      全部原样绿（本批未触其判据）。
13. **M10d「做全」：正文列覆盖列表项名下全部块 + 列算法从文本代理改为显式链求和（2026-09-05 追加裁定，代码批 6f3efd5a）**

    - **裁定面**：一个列表项名下的每个块（首段、段内行、项内标题/引用/围栏/嵌套子项，含
      嵌套项标记行自身）都落在同一正文列；列 = Σ 沿**显式标记链**逐级 ceil(标记段推进宽)，
      与首行同一把尺（resolveAdvance），废除「标记文本里每级 2 空格」的宽度代理。接缝公共面
      +1 读端（用户批准 18→**19**）：`MarkdownLayoutLine#getListMarkerChain()`；写端走
      package-private 全参构造器（L1 同包装配），公共 10 参构造器原样委派不变。
    - **链元素选 TextSegment 而非派单建议的 String[]（冲突照报）**：quoteStyle 的斜体开关使
      同款标记文本在引用内外推进宽不同，纯文本丢款式后「引用内列表」与「顶层列表」共用一个
      数必错其一；TextSegment 自带 L1 产出时款式、是接缝既有类型、零新依赖。
    - **度量实测（headless 软件栅格器逐码点，本环境唯一可信尺）**：@16 档 adv(「• 」)=13.789→14、
      adv(「  」)=14.222→15、adv(「1. 」)=20.81→21、adv(「12. 」)=29.886→30；@14 档 adv(「• 」)=12.065→13、
      adv(「  」)=12.444→13。**adv(「  」)≠adv(「• 」) 坐实「文本代理≠度量」**——但派单「13/25/38 上轮
      实测」在 headless 档不逐字成立：@14 代理档=13/25/37（三级 37≠38，38 系真机 GL 字体读数）、
      @16 代理档=14/29/43；真值（逐级累计）@14=13/26/39（派单此数在 headless @14 成立）、
      @16=14/28/42。漂移方向随档位翻转（@16 代理偏宽、@14 代理偏窄），是整串 ceil 与逐级 ceil 的
      累计舍入差，非普适「每深一层少 1px」。锁一律写死 headless 实测数；公式选**逐级取整**
      （父列是已落定整 px 几何，本级标记从父列起笔）。
    - **L1 落地**：行路 walkLayout/emitLayout/emitListItemLayout/emitCodeLayout/ruleLine 的
      markerLevel 参数退役，改传 `List<TextSegment> chain`；`bareListMarker` 成两路标记裸体单源
      （段流路照旧在外部叠 F2 前导、行路只吃裸体+链）；LineFlattener.startBlock 三参落链，
      块内续行继承、blankLine 不带链；空串圆点级**不进链**（零宽不进链，防注水出第二口径）。
    - **L2 落地**：触发器从 kind==LIST 换成链非空；标记行首视觉行 = 引用份额 + 祖先份额，
      其余一切视觉行（含项内另起块的 TEXT/CODE/引用与零段结构行）= 引用份额 + 全额列；
      无链路径一字不改、flat `wrap()` 零接触；`copyWithSegments` 改走 `withSegments` 类内拷贝法
      ——公共 10 参构造器丢链丢块宽是本轮踩坑点（MUT3 实锤，注释记死）。
    - **踩坑转正（「坑写源码注释」新规矩落点）**：块首 LIST 行软折后 seg0 可残成「•」（行尾
      空白丢弃，K3 既有行为），凡按视觉行 seg0 反扣本级宽必假红（实测 7≠14），oracle 与写入口
      一律取链尾元素（= 逻辑标记段原文）；项内围栏底色容器平移走同式反解（顶层 extra=0 逐字节
      不变）；chat3「CODE 与 LIST 必有一项为 0」「横线行 listExtra 恒 0」两条旧注释按现状改写。
    - **实况发现（推翻派单与第 11/12 条共同前提，块树探针取证）**：`- a/空行/b/空行/c` 在本仓
      解析器**并入同一 blockId 段落**（b/c 为段内行、空行被吞、不产 F6 占位行）——「b/c 是
      新 blockId」不成立；旧机制其实已覆盖段内行，真缺口 = 项内**另起块**（标题/引用/围栏/
      嵌套子项）与嵌套标记行的祖先列。空行被吞是解析层残留（视觉松散丢失），不在 B1 几何
      范围、未扩面，如实挂账。
    - **消费端零逻辑改动**：chat3 反解式 `max(0, leftInsetPx − ql×indentStepPx)` 天然覆盖新偏移
      （嵌套标记行/项内块自动吃 padding）；页面 leafNode 同式 + CODE 容器平移；blockCommands
      零改动（锁③机器钉竖条 x=0 槽与 SEGMENTS.left==inset 逐行等值；「标记行首视觉行=纯引用
      inset」对 1 级项仍成立，引用组合锁原样绿）。
    - **锁面**：`MarkdownLayoutLinesTest` 等值锁改「剥净前导后逐字等 + 差异必为成双前导空格 +
      只许落 LIST 标记行 + 行路标记无前导」四重判据（P08 折叠行数 ≥2 正对照）；身份锁收紧为
      裸标记 + 三钉链（链尾==seg0、同块续行链等值、深度 1/2 计数）；`MarkdownListContinuationLockTest`
      ⑤硬值锁（均匀档 14/28/42 + 混级档 14/35/28，四道「不得等于」反代理钉 29/43/36/2×14——
      **混级档是杀「level×固定步长」的必要条件**，均匀档 42 恰=3×14 单档放不住）；⑥「做全」锁
      （同块段行/另起标题/另起引用/嵌套有序子项四类，断言行实测 12 ≥ 地板 8）；页面像素锁改
      链 oracle + 嵌套标记行 @14 硬值 13/26 三钉（一级 ≥3 仍 0 正对照、二三级各恰 1）；chat3
      `unorderedListIndentMapsLeadingSpacesPerLevel`（C 拍板期钉前导代理的用例）翻转为
      `unorderedListDeepStartItemCarriesNoTextIndent`：深缩进起步单顶层项 = 裸标记 + 偏移 0
      （无父项⇒无链是正确语义），配嵌套 inset>0 正对照——**chat3 断言变更仅此一条**，非恒真化。
    - **突变实跑**：MUT1 L2 链求和→链长×链首宽 → ⑤⑥红（页面锁与①在均匀链下绿——正是
      「均匀档放不住步长代理」的实证注脚）；MUT2 L1 行路复活 F2 前导 → 6 红（L1 等值/身份、
      ①⑤⑥、chat3 翻转锁、页面像素锁）；MUT3 视觉拷贝丢链 → ①⑥+页面锁红、门禁仍绿。
    - **页面实机可见性**：SAMPLES 新增「列表项名下全部块（M10d）」卡（松散项二段/三段、项内
      子标题、项内引用、项内有序子项），hint 只写真实行为（段内行与另起块全部同列；**不承诺
      视觉空行**——空行被解析器吞，上轮假文案教训）；「列表与续行」卡 hint 补嵌套事实；页
      description 8→9 卡；像素锁取样卡（LIST_SAMPLE）未动、参与行判据不变。
    - **门禁与公共面对账（E 序实跑）**：门禁判据/容差/登记表零接触；`diff.txt` 与 M10c 基线
      **逐字节全等**（87 行 / 6642B / sha1 534fcadb…，跑前快照、跑后比对，非读注释）；汇总
      PARITY=20 / NEW=11 / FAIL=0 / TIE=0 / 有意差异=3 / PNG=153 逐项等。**门禁 B 侧走段接缝
      toSegments（F2 保留）**，行接缝文本改动结构上进不了对拍——本轮实测坐实。公共面：
      `MarkdownLayoutLine` 18→**19**（javap 全文在交付回报），`MarkdownStyleTable` 公共法恒 18
      （文件零 git 改动），chat3 三件套 9/26/0 不变，`ui/markdown` GL11. = 0，M9 三锁与 M10
      引用连续条锁语义零改动原样绿。全量 `build --offline` = BUILD SUCCESSFUL：suites 364、
      tests 4012→**4014**（+2 = 新锁⑤⑥，删 0）、0 failures / 0 errors / 2 skipped。注：首跑
      `PlaygroundButtonRowLayoutTest`（home 页子右缘 +36px）红一次——§二之七·续第 9/11 条在案的
      跨类字体注册耦合签名，复跑全量与单跑均绿；本批未触该类，但页卡片数 +1 影响其注册时序
      一事如实挂账（若再红按该耦合处理，不赖新账）。
    - **AGENTS.md 同步项的实况（冲突照报）**：派单要求同步 AGENTS.md「越界/零消费」句——当前
      AGENTS.md 无该句；带时间限定的历史取证句（第 12 条前提一「改动前全仓只有…」）本就是
      过去时记录，现状「leftInsetPx 已被页面+聊天两表面消费」已由第 12 条与条13 共同覆盖，
      不新增、不改写 AGENTS.md（改它属高影响边界，须用户确认）。


## 二之八 C 系列拆除批记录：向 CommonMark 0.30 归位（2026-09-06 宪法裁定，AGENTS.md 主权条款）

> 本节是「已拆裁定」的销账登记面：凡与本文初版/M 系列记录冲突处，以本节 + 各处〔批注〕为准；
> 历史段落保留为过程证据，不再约束现行实现。宪法基准 = 现代化主流引擎（CommonMark 0.30），
> MC 特有格式（§ 颜色码、聊天旧行级规则）只能作为 chat3 集成层的输入清洗或显式扩展存在。

| 批 | 拆除对象 | 落点 | 锁 |
| --- | --- | --- | --- |
| C1a `cf69e087` | ①「深缩进独立列表 F2/baseLevel/readDeepList」；②「缩进代码块缺失」；
|   | ③「段落续行缩进不折叠」；④「F2 段流前导空格编码」 | `MarkdownBlockParser` 内容列
|   | （CommonMark 0.30 §5.2）为嵌套/续行唯一判据 + `readIndentedCode`（§4.4）；旧「字面
|   | 保留」段落行首空白折叠裁定作废 | `MarkdownBlockParserTest` C1a 族、门禁 X01..X03、
|   | `indentLevelsFollowCommonMarkIndentationModel` |
| C3a/C3b1 `dc24d758`+`8d048e3f` | 门禁 A 路「chat3 旧行为规格快照」基准整体废止 | 重基线为
|   | R=commonmark-java 0.21.0（+GFM strikethrough）对拍 B=toLayoutLines 的逐行逐 token
|   | 语义矩阵（产物 `build/reports/markdown-compare/`） | 矩阵本体 + 通道自检（不恒真） |
| C3b2 `14671fb9`+`158d5534` | 「有序列表每项源序号原文」「setext 标题缺失」两条主流语义差 |
|   | 修 1 = 首项源序号 start + 续排；修 2 = `setextUnderlineLevel`（≤3 前导、单字符跑、
|   | `=`→H1/`-`→H2，判定先于 interruptsParagraph）；门禁判据定稿：归一后仍存差异 FAIL 即红 |
|   | setext 族用例 + X10/X11 + 核准表进代码 |
| C3b3 `3e55db68`+`0208da64` | 标题样式豁免域 `HEADING_STYLE_ONLY`（先例 `SETEXT_NO_SUPPORT`）|
|   | 行接缝 Kind 补 HEADING + getHeadingLevel()；N01/X10/X11 直拍 kind+level |
|   | `headingStyleOnlyExemptionMustBeGone()` 反向锁 |
| C4 | **`§` 颜色码处理从 L1 归位 chat3（F4 整体废止）+ setext 惰性续行偏离收紧（N2）+
|   | 门禁最后一个 RECORD 豁免撤销** | 见下段逐条 | 见下段逐条 |
| **C4-fix（本批）** | **chat3 § 清洗「无条件剥」改判乙′「命中块标记才剥」+ 一致性锁 +
|   | 门禁补 N2 惰性 setext 语料（42→48）+ 上述两条行为变化全部改回 C4 前观感** | 见「C4-fix 细账」 | 见「C4-fix 细账」 |
| **C6a/C6b** | 三代 § 机制的第二代（乙′）拆除：chat3 气泡路切「§ → 样式锚点 span 流」甲口径 |
|   | `ChatMarkdownPipeline.toSpanStream` + `MarkdownDocument.parseSpans`（L1 地基 = C6a） |
|   | 迁移等价锁 20 例 + 转换器直测族（**两者均已被 C7 删除**，见下行） |
| **C7（本批）** | **markdown 路径与 § 彻底划界**：三代 § 机制（甲 的 `toSpanStream`）连同
|   | 一代清洗器 `FormatPrefixStripper`、甲↔乙′ 迁移等价锁与退役镜像、比较尺全部拆除；
|   | 玩家消息改走**结构读取**（`StructuredChatReader` 读原版 `chat.type.text`），两条玩家
|   | 通道一律取 unformatted 源；L1 定「§ 是普通字符」为无条件规则** | 见「C7 细账」 | 见「C7 细账」 |

### C4 细账（§ 归位 + N2 + RECORD 清零）

1. **L1 去 §**：`MarkdownBlockParser` 删 `SECTION` 常量、`markerView()`、`isBlockStart()`、
   `isFormatCode()`（markerView 的 10 个调用点一律退回原始行）。markerView 的立论（复刻旧
   行级规则 classify 的「剥行首码后才认块标记」）随该类在 `3e89d91e` 被删、且复生锁以
   `\bChatMarkdownLineRule\b` 钉死不得复活而失效——**本批未新建任何同名类/文件**，清洗落在
   `ChatMarkdownPipeline` 现有结构内。连带注释（markerView 不变式、缩进代码隔离句、惰性续行
   「检测视图」句）全部按新前提改写，不留失效引用。类头新增「L1 对 § 零认知」段并登记行为
   后果：**直连消费者拿 `§a- x` 得到字面段落文本而非列表项——这正是归位目的**。
2. **chat3 输入侧清洗**（**本批后生效口径 = C4-fix 乙′，原文「无条件剥」已改判——理由见
   下方 C4-fix 细账**）：`ChatMarkdownPipeline.stripLeadingSectionCodes(String)`——逐行做
   「至多 3 空格 + 连续 § 码对」交替检测视图（`markerView`，判据与循环结构自 F4 的 L1 版
   逐句搬运：围栏/ATX/分隔线/引用/列表命中才采用剥后视图，未命中整行保留），码集
   0-9a-f k-o r、大小写同义、非法码/孤立 § 不吞；`§a- 玩家列表行` 在 chat3 仍渲染为列表项，
   `§c红色警告` 行首色保留。输出侧桥（`bridgeSectionCodes`/`splitRunsOnFormatCodes`）解释
   行中/段中残留码**与未命中行的行首码**，行为原样保留。**缓存口径**：清洗是原文的纯函数
   （幂等：命中视图行首必是触发字符、二次调用恒同引用；未命中行二次判定结果不变），两级
   缓存 key 一律用清洗后文本——同原文恒同清洗结果；不同原文清洗后同串则渲染逐段等值
   （共享条目 = 去重），清洗后不同串则 key 必不同；「§a- x 与 - x 互相串味」在该设计下
   不存在（串味需要同 key 不同语义，而 key 唯一决定 parse 输入）。锁：
   `stripLeadingSectionCodesConsumesOnlyOnBlockMarkerHitAndIdempotent` +
   `cacheKeyUsesCleanedTextWithoutFlavorMixing` +
   `coarseBlockMarkerAgreesWithL1BlockIdentity`（乙′ 粗检⇔L1 真判据一致性锁）。
3. **行为变化（C4 当时登记的两条，C4-fix 已全部改判回 C4 前观感——原文保留作历史）**：
   - ~~chat3 里 `§f + 4 空格 + "- item"` 剥 `§f` 后判缩进代码块~~ → **C4-fix 乙′**：该形态
     ind&gt;3 不算命中 ⇒ **不剥** ⇒ L1 见 §f 行首 ⇒ 段落字面（= C4 前形态）。这是 chat3
     集成层的既有语义差——纯 markdown 消费者走 L1 直连时「    - item」才是缩进代码块
     （CommonMark 0.30 §4.4 / C1a），不是 bug；锁
     `leadingCodePlusFourSpaceMarkerStaysParagraphLiteral`。
   - ~~chat3 里行首码的颜色语义随剥消失（`§c红色警告` → 无色）~~ → **C4-fix 乙′**：未命中
     块标记的行**行首色保住**（`§c红色警告` 经桥上色），命中行（列表/标题等）行首码仍
     消费——与 C4 前逐位一致；锁 `leadingColorSemanticsSurviveOnNonHitLines` /
     `midLineSectionCodesAreStillBridgedToColor`。
4. **N2 setext 惰性收紧**：`MarkdownBlockParser` 引入包内 `SrcLine(text, lazy)`——
   `readQuote`/`readList` 在惰性续行吸收点打 `lazy=true`（内容列续行/剥标记行随文本继承
   原标记），`readParagraph` 对惰性行跳过 `setextUnderlineLevel` 判定（CommonMark：setext
   下划线不得是 lazy continuation）。类头已裁简化表中该条偏离**删除**（不再存在）。锁：
   `setextUnderlineMustNotBeLazyContinuation`（引用/列表两侧 + 自带标记/顶层正对照），
   C3b2 setext 族与 X11 语料原样绿。
5. **门禁撤最后一个 RECORD 豁免**：`§` 桥差异域名从**词表/核准表/分类通道（compareEntry
   整条跳过 + classifyKind 归口 + startsWithSection）/语料声明列**整体移除；P13/P14 转直拍，
   B 路不经 chat3 桥、R 路 commonmark 不认 § ⇒ 两侧同为字面段落，**实测双双 NO_DIFF**。
   地板断言 `recordTotal >= 1` → **`recordTotal == 0`**；反向锁 `bridgeSectionExemptionMustBeGone()`
   照 C3b3 先例四路扫描 + 全文零容忍（域名在本类源码出现即红）。本批之后：门禁零 RECORD、
   零新增豁免域；豁免照登通道仅剩 N11 的 `EM_FLANK_SIMPLIFIED`（RECORD_ONLY 唯一成员，
   行内 emphasis 定界主流化那批 = C5 的事，本批未动）。
6. **测试改判清单（按新语义重写、零删除凑数）**：`MarkdownChat3RuleInheritanceTest`
   § 方法改钉 L1 字面（~~原「命中块标记才消费」期望作废~~——该「作废」半句随 C4-fix 失效：
   chat3 可观测语义已恢复，本类只保留「L1 直连字面」的归位判据）；`MarkdownSoftwareRenderTest`
   fixF4 锁按新语义重写为 `fixF4RetiredL1BlockLayerIsSectionCodeBlind`；`ChatMarkdownPipelineTest`
   +6 新锁（清洗纯函数/列表保住/§f+4空格成代码/行中码仍上色/行首色随剥/缓存不串味）——
   其中「§f+4空格成代码」「行首色随剥」两条与清洗本体锁一并被 C4-fix 反转换名（见下）；
   `MarkdownBlockParserTest` +1（N2）；门禁 +1 反向锁、自检 12 改反向钉。**未新增语料**，
   未触碰 C3b3 在 BPathSemantics 登记的「标题内含行内强调」地雷（真修属 C5）。

### C4-fix 细账（乙′ 改判 + 一致性锁 + N2 惰性 setext 语料补齐）

1. **改判理由（父代理实测旧代码原文坐实）**：C4 把清洗按「无条件剥行首格式码」搬进 chat3
   是口径误读——旧 ChatMarkdownLineRule.classify 的 stripLeadingFormatCodes 只作用于它自己的
   **检测局部变量**、从不改显示文本；旧 ChatMessageList 的 NONE 分支走 parseCached(renderLine)，
   renderLine 含行首 § 码 ⇒ 不命中块标记的行**颜色保留**；只有命中列表/公式的行才用
   markdown.getContent()（无码）⇒ 那类行行首色才丢。旧 L1 markerView「命中块标记才消费、
   未命中原样保留」精确复刻该观感 ⇒ 用户裁定方案乙′：把该判据搬到 chat3 集成层（L1 仍零
   认知 §，宪法满足），chat3 观感与 C4 前逐位一致。
2. **实现**：`ChatMarkdownPipeline.markerView`（自 `bd58b343^` 的 L1 版逐句照搬「≤3 空格 +
   连续码对」交替循环 + isBlockStart 判据，空格位置保留进视图故 `§f §a- x`/`§f  - x` 正常
   命中）+ 包私有粗检族 `coarseBlockStart/coarseHeadingLevel/coarseFenceStart/coarseThematicBreak/
   coarseListStart/coarseLeadingSpaces`（L1 私有面跨包不可见、公共面冻结，故自带同构复刻，
   不反射、不扩面；反引号/星号以常量书写绕开复生锁 G3 的定界字面禁令）。输出侧桥与
   `splitRunsOnFormatCodes` 零改动。
3. **一致性锁（两份判据代码防漂移）**：`ChatMarkdownPipelineTest#
   coarseBlockMarkerAgreesWithL1BlockIdentity`——48 样本行覆盖 ATX 1..6 与 7 井号/井号无空格、
   setext 下划线独立行（`===`/`--`/`= =` 两边同判否——蓝本 isBlockStart 不认独立下划线）、
   围栏 ```/`~~~`/两连/info 含反引号、引用 `>`/`>>`、无序 `- * +` 与裸 `-`、有序 `N.`/`N)`/
   无空格/`-not`/10 位序号、分隔线 `***`/`---`/`___`/`- - -`、缩进 ≤3/=4/=5、空格交替
   （`§f §a- x`、` §f- x`、`  §f# t`、`   §f> q`）、纯文本、`§r`/`§f§r`、`§z` 非法码、行尾
   孤立 `§`、制表符、`§f+4 空格+标记` 等形态，断言「粗检命中 ⇔ 剥后交 L1 实际产出块级身份
   （行接缝 kind≠TEXT 或 quoteLevel>0；零行 = 空块结构算真）」+ stripLeadingSectionCodes 与
   markerView 同读数 + 命中/未命中计数地板反空跑（≥15/≥10）。漂移即红。
4. **既有锁改判**：`leadingColorSemanticsAreStrippedAwayByDesign` →
   `leadingColorSemanticsSurviveOnNonHitLines`（反转「行首色保住」）；
   `leadingCodePlusFourSpaceMarkerBecomesIndentedCodeBlock` →
   `leadingCodePlusFourSpaceMarkerStaysParagraphLiteral`（乙′ 不剥 ⇒ 段落字面；注释写明这是
   chat3 集成层既有语义差、非 bug）；`stripLeadingSectionCodesIsPureLineHeadScopedAndIdempotent`
   → `stripLeadingSectionCodesConsumesOnlyOnBlockMarkerHitAndIdempotent`（` §a- x` 从「不命中」
   改为「命中且空格保留 ⇒ ` - x`」、`§b- x`/`§r清空` 混合多行逐行独立、幂等含命中+未命中
   两侧）；新增 `preC4SectionFamilySemanticsRestoredThroughIntegrationLayer`（§f- item /
   §f  - item / §f§l- item / §f §a- item 全回「• item」，§c 纯文本行保色）。
   `MarkdownChat3RuleInheritanceTest` 的 L1 直连字面判据**保留**（归位成果），仅类头与 § 方法
   注释撤销「旧期望作废」的过度声明并指向恢复锁；`fixF4RetiredL1BlockLayerIsSectionCodeBlind`
   一字未动（L1 确实零认知 §）。
5. **门禁补语料（C4 挂下的覆盖缺口）**：X12..X17 六条全部走 R 路 commonmark-java 0.21.0
   直接对拍、零新增豁免域（条目 42→48，归一判等 6、豁免照登 2 不变，PNG 84→96）：
   X12 `.甲/惰性 ===`=段内字面、X13 自带标记 === 升格 H1（N2 本体两条）；X14
   `. a/> - b/惰性 ===`、X15 `. a/> - b/> ===`、X16 内有序列表惰性、X17 `>> a/> ===`
   （深层嵌套惰性组合 4 条）。**逐条实测全 NO_DIFF ⇒ 保守侧「宁可少升格」的 SrcLine 惰性
   继承策略正确，锁死即可，实现零改动**。
6. **立项发现（如实登记，不属本批修复范围）**：初稿 X16 曾取「> a / > - b / >   ===」（内容列
   自带下划线 = 正常升格正对照），实测红于「项首块 = 标题 ⇒ 标记段独占空行」的既有行折形态
   差（B=两行 LIST+HEADING vs R=一行 LIST_ITEM+H1 注记，且标题基粗体泄漏语义面）——该差对任意
   `- # t` ATX 同形、与惰性/§ 两域均无关（C3b3 装配 + BPathSemantics 剥标记通道交互），修它需动
   行折装配或提取器结构映射，与本批「禁改归一通道/禁放宽判据」及 C5 地雷登记域相邻，按范围
   纪律改册第 5 条的惰性形态并在门禁注释留证。「项首块=标题的接缝折行」正式修法（含几何
   「首视觉行不吃本级宽」触发器从 kind==LIST 的扩读）属 C5 立项面。
7. **文档口径**：本文 §二 L1 语法面 § 条、§二之三 F4 更新括注、§二之八 C4 细账第 2/3/6 条
   已同步乙′；AGENTS.md 主权条款拆除项注记同步（宪法句一字未动）；未新增独立文档。
8. **乙′ 相对 C4 前的已知残留不一致（逐位核对如实点名，两处均与 C4 现状同形、非本批引入，
   属「集成层预清洗（物理行一次性）」与「旧 L1 消费期视图（每递归层消费点再过一次
   markerView）」的结构差，chat3 侧复刻后者 = 再造递归解析器，犯 G3/漂移大忌，故不修只登记）**：
   - 嵌套行内层 §：`> §a- x`、`- §a- x`——C4 前内层 parseBlocks 会对「§a- x」再过视图命中
     升格（引用内/项内列表、行首码消费）；乙′ 预清洗见行首是 `>`/`-` 即不命中 ⇒ 整行保留、
     内层 § 由桥上色但**不升格**。（`§a$$x$$` 类「剥后恰成独占行公式」反向：C4 无条件剥会
     意外升格为块级公式，乙′ 不剥 ⇒ 与 C4 前反而一致。）
   - 围栏内行：``` … `§a- x` … ```——C4 前 readFence 恒吃原始行（旧类头不变式，视图从不
     进围栏），代码面显示 `§a- x` 字面；乙′ 预清洗按物理行「行首 §+标记」命中 ⇒ 围栏内显示
     `- x`（与 C4 现状同形）。玩家消息含「§色码 + 列表标记」形态的整段围栏属罕见角落，
     彻底对齐需在集成层做围栏感知（= 第二套块扫描，违宪风险），留待与 C5 同批裁定。

### C6a/C6b 细账（方案甲落地：§ → 样式锚点 span 流；预清洗与输出桥整套拆除）

1. **批次结构**：C6a（第一步，`9b49b79f`/`09d91abe`/`f468430b`/`3259d6a8`）打 L1 地基：块级文档
   span 流入口 + 行内跨 span 连续扫描 + L1 § 零知识常驻守卫。C6b（第二步收尾，本批，分三笔）：
   把 chat3 气泡消息路从「§ 输入预清洗 + 输出后置桥」切到「§ 在进 markdown 前转样式锚点 span 流」，
   并整套拆除预清洗与桥。代码批 `28e71094`（L1 改名+定序）/`7d92e4f5`（chat3 切换+迁移锁）/
   `c9ae3b54`（拆除）。
2. **输入转换器**（`ChatMarkdownPipeline.toSpanStream`）：扫描与 L0 `TextLayoutService.parseSegments`/
   旧桥 `splitRunsOnFormatCodes` 同源——§ 与其后一字符成码对、逐码 `TextStyle.applyFormat` 消费
   （大小写同义；未知码走 default=重置，与 L0/原版同形）；码对不进 span 文本。**行界不可吞**：紧邻
   CR/LF 的孤立 § 与消息尾 § 按字面保留（旧桥按逐行段流作业、从来看不到跨行码对；换行是 L1 块检测的
   输入材料，吞行界=伪造第二套切行）。码效应沿整条消息累计、不随 markdown 段重启——样式锚点的
   定义性属性（差异清单第 5 条）。起始样式 = `resetAll(baseColor)`（非显式底色，见第 3 条）。转换器住
   ChatMarkdownPipeline.java 本体内（G3 断言②唯一 markdown 层引用文件），不引入任何 markdown 定界字面。
3. **样式施加顺序裁定（本批关键改判）**：旧桥语义 =「markdown 样式位先叠加、§ 码后生效 ⇒ 服务端色
   优先于块级色」；C6a 链序（span 为底、块链压顶）会在引用内把 § 色洗成引用降色——沿用即观感回退。
   C6b 定序改判为：**块级链在 span 基础样式拷贝上叠样式位 → span 携带显式色（`isColorExplicit`）
   时其颜色覆盖块级色 → 行内位最后**（行内 `MarkdownInlineParser.resolve` 与 `BlockStyle.applied`
   同一把尺；标题粗体/字号、引用斜体等块级位仍存活，「样式位叠加、色覆盖」）。显式/非显式二分同时
   保住「引用降色对宿主未着色文本照常生效」（C6a 锁 1 的「无语义样式」输入因此改记非显式底色 =
   宿主未指定色的诚实编码；新增定序锁 `explicitSpanColorMustWinOverBlockChainColor` 钉两侧）。
   转换器以 `isColorExplicit` 为 § 着色尺（色码→true；§r→false）⇒ 旧桥的「§r 引用内重置回段起色」
   「§f 显式白压引用色」两落点逐位保持。String 路（blockTransform 恒 null）不经覆盖分支、行为逐位
   不变——门禁 48 条 + `MarkdownInlineParserTest` 26 例常绿即机器证明。
4. **两缺陷结构性消灭**：缺陷 a（围栏内行被误剥——预清洗看不见围栏上下文，「§c# 标」类内容被改写
   且色丢）：甲转换与块上下文无关、围栏内容恒字面且样式锚点保留（迁移锁
   `fenceLineLookingLikeBlockMarkerIsDocumentedDelta` + C6a 锁 5）。缺陷 b（容器行首 § 不剥——判据
   只看物理行首，`> §a- x` 内层不升格）：甲转后文本恒纯，L1 在容器内层照常升格（迁移锁
   `listLineInsideContainerIsDocumentedDelta` + C6a 锁 6）。C4-fix 细账第 8 条登记的两处乙′ 残留
   不一致（嵌套行内层 §、围栏内行）随本批一并消灭。
5. **迁移等价对账**（`ChatMarkdownSectionSpanMigrationLockTest`，任务书点名交付）：等价 9 条
   （行中色 §c甲§f乙 / 引用内 > §c甲 / 围栏内普通 § / §r 引用外与引用内 / 连续码 §c§l / 行尾孤立 § /
   **a§cb** 强调内色 / §a- §citem 复合）逐段文本+全视觉样式字段+行身份直断等值；有意差异 8 条
   （行首色+列表保色、围栏标记形、容器升格、**§f+4 空格 → 缩进代码块**（任务书第三部分第 4 条点名
   改判：色进锚点后文本以 4 空格开头，L1 按 CommonMark §4.4 判缩进代码——主流正确结果，乙′ 的
   「段落字面」妥协随机制退役）、§z 结构差、色跨强调持续染色、色跨软换行持续、残留 § 反噬强调结构）
   双期望写死 + 逐条成因，无容差；**不可能等价 2 条**（latex 段内 §、行内 code span 段内 §）：甲按
   定义转换先于 markdown——转换器要豁免这两域必须持有一份 markdown 上下文 = 第二套块扫描（违宪 +
   G3 雷区）；且乙′ 对该两域的豁免与它对围栏内 § 的上色行为本不自洽，甲把 § 语义统一为
   「与原版 parseSegments（系统消息路）同解读」，双侧期望写死留证。比较尺不含 `colorExplicit`
   （第 7 条）。
6. **桥退役**：删桥前置实证 = 对全部新增语料 + 门禁 P13/P14 原文断言「甲 段流上桥 = 恒 no-op」
   （`convertedStreamLeavesNoConsumableSectionPairsForTheBridge`，C6b·3 起对镜像跑、常驻防漏）；
   无 § 的门禁语料按构造 trivially no-op（转换器零改写 + 桥对无 § 段流零拷贝直通）。P13/P14 语料
   保持现状（门禁两侧 § 均字面 ⇒ NO_DIFF 不变），缺陷场景覆盖在 L3 锁层、不进门禁语料；本批门禁
   判据/容差/登记表零接触、零新增豁免域（豁免照登通道仍仅剩 N11 的 EM_FLANK_SIMPLIFIED）。
7. **`colorExplicit` 落点迁移**（如实登记）：乙′ 路该位除 §r 后外恒 true（caller 底色经 setColor）；
   甲 路宿主未着色段为 false（resetAll 起点）、§ 着色段 true。该位在 chat3 渲染链零消费点（全仓消费
   点 = RichTextTagParser / TextContentModeStrategy 的 vanilla 合并尺 / StyleValues 分组粒度三处，
   均不触气泡像素），观感不变；甲 路引用文本该位仍随块级 setColor 为 true、与乙′ 同。
8. **缓存口径重定**：两级 key 吃消息**原文**（displayText 未转换形态）+ baseColor + 配色代指纹
   （次级色/链接色）+ 定行宽/字号/度量纪元（+ wrap 替身分标记）；转换只在未命中时做。论证：转换 =
   (原文, baseColor) 纯函数 ⇒ 同 key 同语义输入，结构上无「同 key 不同语义」；「转换后等值的异原文」
   （§r- x vs - x）分占条目 = 去重效率回退、无串味（锁内双断言）。乙′「吃清洗后文本」口径随机制退役。
   锁 = `cacheKeyUsesRawTextAndNeverSharesAcrossSemantics`。
9. **parse→parseSpans 改名**：见公共面账 §二之七·续 C6b 续账（周期内自纠、非破坏承诺；
   `MarkdownDocumentTest:63` 的 (String) 强转同批退还）。
10. **守卫面**：`Chat3MarkdownResurrectionGuardTest` 断言②生产锚字符串随入口更名演进
    （`MarkdownDocument.parse(` → `MarkdownDocument.parseSpans(`；「>=1 命中」正向语义与全部地板
    一字未放松——先例 = M7 的 toSegments→toLayoutLines 演进，演进纪律在锁自身 javadoc 有档）。
    `MarkdownL1ZeroSectionKnowledgeGuardTest` 零接触：L1 代码面 § 恒 0 主断言照绿；对照文件代码命中
    地板（现实测 3 = 地板 3，由转换器的 § 字面两处与 applyFormat 一处继续满足）照常成立。
11. **拆除清单（C6b·3 numstat 原值）**：`ChatMarkdownPipeline.java` +9/−348（stripLeadingSectionCodes、
    markerView、coarseHitsBlockMarker、coarseBlockStart、coarseLeadingSpaces、coarseHeadingLevel、
    coarseFenceStart、coarseThematicBreak、coarseListStart、isChatFormatCode、bridgeSectionCodes、
    splitRunsOnFormatCodes 共 12 方法 + FENCE_TICK_HEX/MARK_STAR_HEX/MAX_ORDINAL_DIGITS 三常量 +
    分节注释；isChatFormatCode 无需保留改名——转换器的「§+任意字符」消费与 L0 applyFormat 天然同集，
    码集判定随粗检族失去唯一消费者，留之即孤儿）；`ChatMarkdownPipelineTest.java` +5/−233（删 5 条
    退役直测锁：bridgeSplitsSectionCodesLikeParseSegments、
    bridgePreservesMarkdownBitsAndCodePathResetsLikeMc、bridgeSkipsLatexCodeAndPlainSegmentsZeroCopy、
    stripLeadingSectionCodesConsumesOnlyOnBlockMarkerHitAndIdempotent、
    coarseBlockMarkerAgreesWithL1BlockIdentity——判据已由转换族锁与迁移锁承接）；迁移锁 +415/−3
    （乙′ 只读镜像 RetiredBPrime 逐句照搬自 `7d92e4f5` 随锁常驻——迁移等值结论在机制删除后仍可机器
    复验；镜像不随生产演化，分叉代价如实登记）。测试计数 4074 → **4095**（净 +21：新增迁移/转换/
    定序/缓存锁，删除 5 条退役直测锁与 1 条随桥迁移的实证锁位，全部增减逐条可对应，无故减为零）。
12. **文档口径**：本文 §二 L1 语法面 § 条、§二之五 遗留差异第 2 条、AGENTS.md 主权条款拆除项已同步
    甲口径（宪法句一字未动）；门禁类头新增分层声明一句话（B 路管 markdown 语义对齐、§ 转换属集成层
    由 L3 锁管）；未新增独立文档。
    〔**C7 注记（2026-09-07）**：本条所同步的「甲口径」已整体作废——甲 转换器与集成层全部 § 机制
    随 C7 拆除，上述三处口径现按下方「C7 细账」重写；门禁类头分层声明同步改口为「集成层无 § 机制，
    § 行为由 L1 正向锁钉」。〕

### C7 细账（划界定案：markdown 路径不解释 §；玩家消息走结构读取）

**定案四条（用户裁决，不可自行改设计）**：① 系统消息层除用 markdown 方法发送的消息外，其余
全走原版解析链；② 玩家消息里 § 一定不会出现（原版 `ChatAllowedCharacters.isAllowedCharacter`
第 11 行 `character != 167`，167 == 0xA7 == §；服务端逐字符校验，含 § 的输入整条拒收）；
③ 玩家名称走原版解析、发送内容走 UILib markdown，两者不混合；④ markdown 解析器内 § 原样
显示、不做任何处理。

1. **实测证据链（本代理逐条复读母本源码 + `javap`，行号是本仓 `build/rfg/minecraft-src`
   与 `build/rfg/recompiled_minecraft-1.7.10.jar` 的实测值）**：
   - **构造点 = `net/minecraft/network/NetHandlerPlayServer.java:768`**（不是 PlayerManager；
     PlayerManager 只在 :771 的 `sendChatMsgImpl` 里负责广播）：
     `new ChatComponentTranslation("chat.type.text", [player.func_145748_c_() /*getDisplayName*/,
     ForgeHooks.newChatWithLinks(s)])`，其后 :769 再过 `ForgeHooks.onServerChatEvent`（mod 可改写
     或丢弃）⇒ **key 与两槽形状是 Forge 保证的，不是裸 vanilla 保证的**；
   - **内容槽在 Forge 下通常是 `ChatComponentText("")` 带 URL siblings**（`ForgeHooks.java:391-433`
     `newChatWithLinks`：命中 URL 正则的片段拆成子组件并挂 `ClickEvent.OPEN_URL`），**不是**任务书
     写的「原始消息 String」；无 URL 时才是单段 ChatComponentText，经
     `IChatComponent.Serializer:191-193` 序列化 + `:133-141` 反序列化按「无样式且无 siblings」
     降级成 String ⇒ 同一槽位**两形都可能出现**，读取器必须两形都吃（本批实测锁
     `StructuredChatReaderTest#readsForgeLinkWrappedContentComponent` 用带链接的 Forge 实形复验：
     取到的 unformatted 内容 = `看 http://a.co 吧`，事件不进内容、由
     `ChatUrlLinkifier` 在段流上重做链接化）；
   - § 进不来玩家输入的实证在 `NetHandlerPlayServer.java:753-760`：逐字符
     `ChatAllowedCharacters.isAllowedCharacter`，不过就 :757
     `kickPlayerFromServer("Illegal characters in chat")`；该判定本体在
     `ChatAllowedCharacters.java:11` `return character != 167 && ...`（167 == 0xA7 == §）；
   - 客户端 `NetHandlerPlayClient.java:790-797` `handleChat` 把 `event.message` 原样交给
     `printChatMessage` ⇒ 结构到得了 chat3 的接收入口（`ChatFacade.printChatMessage` →
     `ChatCore.appendMessage` → `ChatHistory.append`，全程持引用不转字符串）；
   - `ChatComponentTranslation` 公开 `getKey()`/`getFormatArgs()`（`javap` 确认），且
     `getUnformattedText()`/`getFormattedText()` 在 `ChatComponentStyle` 里是 **final**
     （`javap` 确认）——这决定了测试侧的「不碰翻译」计数锁只能覆写 `iterator()`/`getUnformattedTextForChat()`。
   **§ 残渣的真来源（本批新证，比原任务书「服务端为消息体定起始样式」的说法更准）**：不是
   服务端塞的，是 `ChatComponentStyle.getFormattedText()`（母本 :105-119）逐组件
   `append(getChatStyle().getFormattingCode())` + `append(EnumChatFormatting.RESET)`（:115
   无条件追加）注进去的。实测同一条 `chat.type.text`：`getFormattedText()` =
   `<§rSteve§r> §r<b>hi</b>§r`，`getUnformattedText()` = `<Steve> <b>hi</b>`。
2. **新增 `internal/chat3/viewmodel/StructuredChatReader`（127 行，纯函数、headless 可测）**：
   `root instanceof ChatComponentTranslation` + `getKey()` 命中可扩展键集合
   `PLAYER_CHAT_FORMAT_KEYS`（现只登记 `chat.type.text`，将来加 `chat.type.action` 一类
   同源结构只改常量）+ `getFormatArgs()` 形如 `[sender, content]` ⇒ 返回不可变
   `PlayerChat(sender, content)`，否则 null（不抛、不猜）。sender 支持 String 与
   ChatComponentText 两形，内容只从 args[1] 取。**硬约束**：结构化路径禁调翻译组件的
   `getUnformattedText()`/`getFormattedText()`（二者 final，第一步都是 `iterator()`，
   `ChatComponentTranslation.iterator()` 先 `ensureInitialized()` → `StatCollector` 语言表）。
   **额外收益**：结构读取绕开原版语言表翻译查找（`chat.type.text` 需 StatCollector 参与），
   既少一次依赖，也消除格式缺参时 `ChatComponentTranslationFormatException` 的抛出风险。
3. **装配收口：markdown 输入永不取自 `getFormattedText()`**。两条玩家通道都从源头无 §——
   结构命中取 args[1]，正则兜底取 `getPlainText()` 上 `SenderExtractor` 的 rest；
   `ChatCardComposer.displayText` 三分支（结构 / 兜底玩家行 / 系统行），系统行仍取 formatted
   全文交 `ChatMessageList` 原版解析链（定案 ①）。**代价（登记 + 设备验证项）**：上游原版链
   给内容定的颜色不再透传进气泡，气泡颜色一律由基础色 / 样式表 / markdown 自有语法决定——
   与定案 ③「不混合」同向。
4. **被删机制清单与作废原因**：

   | 删除物 | 原职责 | 作废原因 |
   | --- | --- | --- |
   | `ChatMarkdownPipeline.toSpanStream` + `flushSpan`（C6b 甲，−97 行主源） | 进 markdown 前把 § 码对转样式锚点 span 流 | 输入侧已无 §，转换器恒空转；且它是「markdown 结果依赖 §」的最后载体 |
   | `MarkdownDocument.parseSpans(List)` 的 chat3 消费路径 | 甲 的落地入口 | **入口本体保留**（将来富文本 component 通道），只删 § 相关用法；现零生产消费者，能力仍由 `MarkdownSpanStreamC6aLockTest` 钉 |
   | `FormatPrefixStripper`（本体 40 行 + 直测锁 3 例） | 从 formatted 文本按「有效字符数」跳过 § 对切前缀 | § 残渣的搬运工而非清洗器；随第 3 条收口退出装配路径后全仓零引用 ⇒ 本体与锁一并删（原版链与组头均不经它，已核引用） |
   | `ChatMarkdownSectionSpanMigrationLockTest`（20 @Test / 797 行）+ 只读镜像 `RetiredBPrime` + 比较尺 `StyleFieldsKey`（39 行） | 「§ 与 markdown 共存输入」的甲↔乙′ 逐段迁移等价对账 | 划界后该输入不再存在，等价锁失去对账对象；镜像与尺仅被本锁使用（`StyleFieldsKey` 零他引，已核） |
   | `ChatMarkdownPipelineTest` 5 条转换器直测 + 甲 口径观感锁（该类 14→7） | 钉 § 上色、锚点存活、显式色定序 | 行为整体作废；替换为「§ 字面零样式」「行中字面」「缓存单轨」「空输入形状」4 条新锁 |
5. **L1 定案「§ 是普通字符」= 无条件解析规则**（不得写成、也不得依赖「输入保证不含 §」）：
   事实面（原版排除 167）只解释「为什么可以删掉那堆 § 机制」，不参与决定解析器行为。新增
   `MarkdownSectionCodeIsPlainTextLockTest`（8 例，纯 L1 测试侧，不依赖 chat3 与 MC 类，
   每例真的把 § 喂进 `parse` 再断言）：正文含 § 逐字符保留且样式字段与无 § 等价输入逐字段
   全等；行内 code、围栏代码、latex 原子内 § 字面；**行首 § 吃掉块标记**（`§a- x` /
   `§a# t` / `§a> q` 全判段落，配「去掉行首 § 照常命中」正例对照防误锁成空断言）；§ 出现在
   缩进之后走 CommonMark 正常后果（4 空格 + § ⇒ 缩进代码且 § 留在 CODE 文本，≤3 空格 + §
   ⇒ 段落且行首空白按既有口径折叠）；孤立 § 宽容字面。禁止清单同步入册：不得新增任何 §
   识别/剥离/转换机制，不得恢复输出侧 § 桥，不得引入 markerView/κ 一类检测视图。
6. **守卫面**：`MarkdownL1ZeroSectionKnowledgeGuardTest` 保留，两处同步——（a）反例文件从
   `ChatMarkdownPipeline` 改指仍合法含 § 的原版链 `ChatLineLayouter`（划界删转换器后前者
   代码面 § 命中归 0，继续拿它当反例等于把反例换成第二个空跑；「反例必须真能触发红」性质
   不变）；（b）代码命中地板 3→1（3 是上一批自设过紧的基线值，1 已足以区分「恒假」与
   「真命中」），实测 5/全行 20 由脚本复读，理由写进类头。`Chat3MarkdownResurrectionGuardTest`
   的 L1 入口锚随生产演进 `MarkdownDocument.parseSpans(` → `MarkdownDocument.parse(`
   （「>=1 命中」正向语义与全部地板一字未放松，先例见该锁 javadoc）。
7. **公共面账（javap -public 实测于 `build/classes/java/main`）**：L1 零接触——
   `MarkdownDocument` public 方法恒 **8**、`MarkdownLayoutLine` 全成员恒 **20**、
   `MarkdownStyleTable` 恒 **19**、`ChatMessageList` 恒 **9**、`ChatSceneController` 恒 **26**；
   `ChatMarkdownPipeline` public 成员恒 **0**（类仍非 public），其**声明**成员（含非 public）
   **20→19**（删 toSpanStream/flushSpan），反射反空跑地板的实测数已同步。新增类型
   `StructuredChatReader`（`internal.chat3.viewmodel`，非 markdown 接缝、不在门禁五锚定表内）
   的 public 成员按全成员尺 = **1 + 3**：本体 `read/1`；public 嵌套 `PlayerChat`
   `getSender/0`、`getContent/0`、`toString/0`（类声明行不计、私有构造与包内键集合不入账，
   嵌套类型按锁细则 2 单独数）；`MessageGroupModel` 侧只加包内 `isStructured()`，
   公共签名零变化。
8. **缓存单轨**：两级 key = 最终喂进 `MarkdownDocument.parse` 的那个字符串 @基础色#配色代，
   装配处（`displayText` 三分支）与缓存处共用同一个值，不留「一处用原文、一处用结构内容」
   的两把尺（锁 `ChatMarkdownPipelineTest#cacheKeyIsExactlyTheStringFedToMarkdown`）。
9. **已知边界（设计行为，不是缺陷；三处加锁）**：非 vanilla 聊天格式（服务端自定义 key 或
   改写过的 `chat.type.text`）走正则兜底时内容可能带 §，此时 markdown 原样显示字面 §、
   不产生任何样式——L1 侧 `MarkdownSectionCodeIsPlainTextLockTest`、chat3 侧
   `ChatMarkdownPipelineTest#sectionCodesAreLiteralTextWithZeroStyleEffect`、视图模型侧
   `MessageGrouperTest#fallbackContentKeepsSectionCodeVerbatim`。一句话：**markdown 路径
   对 § 不做任何分支**。
10. **顺带修的一处旧失真**：`ChatMarkdownPipeline.layout` javadoc 的「空文本 → 单空行
    （至少一行）」在两代实现里都不成立（空流与空文档同样产空表），按现状更正并由
    `emptyAndNullInputsProduceNoLines` 钉住（配非空正例防空跑）。非 C7 引入，就地处理。
11. **实测计数**：`build --offline` = BUILD SUCCESSFUL；`cleanTest test` =
    **4089 tests / 368 suites / 0 failures / 0 errors / 2 skipped**（skipped 恒为
    `LatexReferenceComparisonTest` 两条，脚本数 XML 得）。C6b 基线 4095/368 → 净 **−6**
    （脚本逐文件数 @Test 注解：文件净口径删 30 例 / 增 24 例；按用例名口径 34 例消失
    / 28 例新增）。消失的用例名 = 迁移等价锁 20 例 + `ChatMarkdownPipelineTest` § 族 11 例
    （其中 4 例是同义换名，净 −7）+ `FormatPrefixStripperTest` 3 例；新增 = reader 契约 9 例 +
    L1「§ 是普通字符」8 例 + grouper 结构 4 例 + composer 三分支 3 例 + pipeline C7 锁 4 例。
    门禁 48 条目产物**零变化**：`条目=48 FAIL条目=0 FAIL差异行=0 归一判等行=6 豁免照登行=2
    RECORD条目=0 F6剔行=2 PNG=96`，**无新增豁免域**（B 路本就 L1 直连 String、两侧 § 恒字面，
    划界只删 chat3 侧转换器，不触门禁判据/容差/登记表）。
12. **踩坑档**：本批任务书里「对 `ChatComponentTranslation.getUnformattedText()` 调一次必抛
    `NoClassDefFoundError: commons-io`」的实测结论出自仓库外的单文件 javac 探针（classpath
    不完备），在 gradle 测试 classpath 上**不成立**（commons-io 在场、StatCollector 可用、
    取文本不抛），按它写的断言实测必红。已按现实改锁（`iterator()` 计数恒 0 + 形参不合时
    原版抛而 reader 退 null + formatted/raw 对照），并把「跨环境结论必须标明 classpath 前提」
    记入 `踩坑记录.md`。
13. **设备验证清单（本批未跑真机，如实挂账）**：① 真机玩家气泡是否还有 § 残渣；② 给内容着色
    的服务端（含改写 `chat.type.text` 者）气泡丢色的观感；③ 昵称/前缀是彩色组件时组头是否
    仍按原版着色（名称侧路径未动）；④ HUD 8 行截断与末行省略号在新正文下的形状。

### C8 细账（通道③落地：`ChatAccess.printMarkdown` 双入口，2026-09-07 用户裁决 a1）

**定案（用户裁决，逐字口径）**：「给甲额外添加入口，两者语义完全分开，要么纯 markdown，
要么显式调用装饰方法」的落地形——`printMarkdown` = 显式递交、内容即所见，**永不过装饰器链**
（print 家族整体与装饰链解耦，不是默认过链，也不是默认跳过+变体）；装饰 = 调用方主动调既有
public `ChatAccess.decorate(IChatComponent)`（只变换不注入），把结果递进
`printMarkdown(IChatComponent)`；**不新增 `printDecoratedMarkdown` 一类糖**。装饰器链对原版
消息的既有行为（`ChatCore.appendMessage:37` 处 decorate）一字不动。本条 = AGENTS 三条输入
通道的第③条落地，属系统消息层里唯一被划出走 markdown 的例外（宪法 1 原文『系统消息层除
使用markdown方法发送的消息，其余全走原版解析链条』——宪法句一字未动）。

1. **机制（复用 C7 结构通道，零穿参）**：`printMarkdown(String md)` 包成
   `ChatComponentTranslation(键, [md])`（args[0]=String；C7 探针与 `StructuredChatReaderTest`
   已证 args 读取 headless 安全）；`printMarkdown(IChatComponent)` 同样旁路注入（供显式装饰后
   的组件）。注入出口 = 安装器回写的 sink（`ChatAccess.setMarkdownSink`，完全照
   `setTakeoverActive` 先例与包内注释同口径：接管装成两条路径写入 `core::appendMarkdown`、
   读回失败与逃生舱回退两条路径清 null），**不经 `ChatFacade.printChatMessage`（那条会
   decorate）**。sink 缺席（未接管/未安装）降级原版显示：String 形 = `ChatComponentText(md
   原文)`（零键字面外泄）；组件形 = 直接递原组件——若它正是 markdown 键翻译组件，原版会渲染
   key 字面 "uilib.markdown"，这属『未接管时给自定义组件』的固有形状（组件通道对原版本就
   没有 markdown 语义），注释写明、不特判。降级出口抽成包内可测缝 `__setVanillaPrintForTest`
   （headless 无 Minecraft 实例，测试注入捕获器断言产物类型与文本；真机默认实现 = 原样直连
   `mc.ingameGUI.getChatGUI().printChatMessage`，行为不变）。
2. **键常量单一定义点** = `ChatAccess.MARKDOWN_CHAT_KEY`（api.chat 层 public 常量，值
   "uilib.markdown"）；`internal.chat3` 的 `StructuredChatReader` 引用它——internal→api 是
   既有依赖方向（`ChatCore` 已调 `ChatAccess.decorate`），零新依赖边；测试侧消费同一常量，
   锁 `markdownKeyConstantIsSingleSourceOfTruth` 钉字面值——两处各自定义字面量的形态堵死。
3. **`StructuredChatReader` +2 public static（记账）**：`rendersAsMarkdown(IChatComponent)`
   （root 是翻译组件且 `getKey()` 命中 markdown 键 ⇒ true；纯结构判形，不触翻译查找，不递归
   siblings）；`markdownContentOf(IChatComponent)`（取 `getFormatArgs()[0]`，String 与
   `ChatComponentText` 两形都吃，照 `plainTextOf` 现法；形不合一律 null 不猜）。
   `PLAYER_CHAT_FORMAT_KEYS` 未动；`read()` 对 markdown 键组件恒 null（两判据互斥，锁
   `readNeverHitsMarkdownKeyComponent`——markdown 内容含 `<Steve> ` 形状也不得被玩家通道认领）。
4. **渲染路由（关键正确性点）**：markdown 判定在 `MessageGrouper.group` 里排在**任何取文本
   调用之前**（先于 C7 结构读取与正则兜底）——兜底要先 `record.getPlainText()`，对 markdown
   键组件那是语言表查找、实机只返回 key 字面，错。硬证 = markdown 短路锁
   `markdownRecordShortCircuitsBeforeAnyTextExtraction`：计数组件包 markdown 键走分组器 ⇒
   渲染入口调用数恒 0（配「亲手渲染一次必非 0」正对照反空跑）。命中且内容非 null ⇒
   `MessageGroupModel.markdown` 形（不进 `SenderExtractor`、独立成组并切断前后合并）；
   args 形不合的 markdown 键组件退回既有通道（不猜，与 reader「不合形即 null」同律）。
5. **呈现形状与装配**：`Alignment` +`MARKDOWN_LEFT`（枚举常量 3→4；按公共面守卫细则 2 不进
   成员账。不复用 `SYSTEM_CENTER`——「居中」与该形「左对齐」定案直接冲突；也不复用
   `OTHER_LEFT`——它带气泡与 sender 语义。新增值是唯一不撒谎的形状）；`GroupLine`
   +`isMarkdown()`（包内姊妹判据，命名对齐 C7 的 `isStructured()`，公共签名零变化）。
   `ChatCardComposer.displayText` 四条装配分支（markdown 最先短路 ⇒ 本体 = args[0] 原文；
   `getPlainText()/getFormattedText()` 永不调用，unformatted 源纪律不破）；`MARKDOWN_LEFT`
   切行共用 `systemLayouter`（与渲染字族 font-system 同源）；组头分支与 SYSTEM_CENTER 同族
   「无组头」。`ChatMessageList` 的路由 `system ? null : markdown.layout(...)` 判据只认
   `SYSTEM_CENTER`，markdown 行**天然进 layout**（这正是它存在的意义），同时吃系统字族/底色、
   无气泡（背景/padding/maxWidth/圆角全不建）、左对齐（`AlignSelf.START`）、不吃气泡钳宽，
   bake/hover 归无气泡族；`ChatSceneController.estimateHudGroupHeight` 同族（行数×行高无壳）。
   HUD 8 行截断与气泡行同一 `clampHudLines` 路（平价锁）。`ChatMarkdownPipeline` 零逻辑改动：
   cacheKey 单轨（C7 第 8 条）自然覆盖——markdown 形传入的字符串恰是 args[0]（同源锁
   `markdownLineSequenceMatchesDirectPipelineCallSameStringSameWidth`：同串同宽在替身换行下
   逐行等值；§ 字面锁喂 `"§ab"` 断言字面）。
6. **永不触碰发送链（自证）**：printMarkdown 全路径代码零 `ChatBridge` / `sendChatMessage` /
   `addToSentMessages` / `getSentMessages` 引用（代码路径锁
   `printMarkdownCodePathNeverTouchesSendChain` + 「send() 方法体仍含 ChatBridge」正对照防
   扫描器瞎）；`ChatCore.appendMarkdown` 方法体零 `decorate`（结构锁与 `appendMessage`
   对照锁同文件）；已发送历史与原版发送链结构性不可达。
7. **锁 25 例（全部真跑，逐类对应见第 9 条）**：reader ×5（判形矩阵 / args[0] 两形原文含
   `**`、换行、中文、URL 形状、§ 零特判 / 形不合全 null / read 互斥 / 渲染路径零计数）；
   grouper ×3（短路先于取文本硬证 / 正则不污染 + 真玩家消息对照正例防误锁 / 形不合退通道）；
   composer ×2（displayText = args[0] 同源 + § 零 formatted 尾注）；ChatAccessTest ×6（键常量
   锚 / print 恒纯计数 0 + decorate 对照 ≥1 防空断言 / 显式装饰恰 1 次 / 降级产物类型与文本
   断言（锁 7 接法 = 可测缝注入捕获器）/ null 两形忽略 / 永不发送路径锁）；ChatCoreTest ×3
   （sink 端到端旁路零装饰 + reader 从入史组件取回原文 + appendMessage 对照 / appendMarkdown
   null 守卫与消息 id 替换语义 / Facade 两路分尺）；InstallerTest ×2（sink 与 takeover 标志
   成对回写的接线锁 + 旁路体零 decorate 结构锁）；MessageListTest ×4（渲染路由形状三连：
   左对齐/无组头/无气泡无圆角，且 `**` 出粗体段、§a 字面存活、零 key 字面上屏 / 同源行序 /
   HUD 截断平价 / 普通系统行仍居中走 § 路的对照防误锁）。既有 4089 例零回归（装饰器链语义
   一字未动；`MarkdownPublicSurfaceGuardTest` 12 例、`Chat3MarkdownResurrectionGuardTest`
   4 例、门禁族全绿）。
8. **公共面续账（`javap -public` 实测于 `build/classes/java/main`，全成员尺）**：`ChatAccess`
   **7→11**（+字段 `MARKDOWN_CHAT_KEY`、+方法 `setMarkdownSink/1`、`printMarkdown/1` ×2；
   其余 7 方法逐位与 C7 终态一致，私有构造不入账）——api.chat 属公共兼容承诺包，全部纯增量、
   零签名破坏；
   `StructuredChatReader` 本体 **1→3**（+`rendersAsMarkdown/1`、+`markdownContentOf/1`），
   嵌套 `PlayerChat` 恒 3；`MessageGroupModel` 公共面零变化（`markdown(...)` 工厂与
   `isMarkdown()` 皆包内；`Alignment` 枚举常量 +1 按守卫细则 2 不进成员账）。
   `MarkdownPublicSurfaceGuardTest` 五锚定值核实**不含 ChatAccess/Reader**（锚 =
   `MarkdownLayoutLine` 20 / `MarkdownStyleTable` 19 / `ChatMessageList` 9 /
   `ChatSceneController` 26 / `ChatMarkdownPipeline` 0，类头口径明示「markdown 接缝与 chat3
   消费面」）——五值实测零变化、无需同步锚点；「守卫不覆盖 api.chat」这一核实结论登记于此，
   防后人误以为 printMarkdown 该进那张表。
9. **实测计数与门禁**：`build --offline` = BUILD SUCCESSFUL；`cleanTest test` =
   **4114 tests / 368 suites / 0 failures / 0 errors / 2 skipped**（skipped 恒为
   `LatexReferenceComparisonTest` 两条；脚本数 XML 非心算）。C7 基线 4089 → 净 **+25**，
   逐类可对应：reader 9→14、grouper 13→16、composer 30→32、ChatAccessTest 7→13、ChatCoreTest
   4→7、InstallerTest 2→4、MessageListTest 72→76。L1 门禁三工件哈希**逐字未变**（diff.txt
   `A826A2B9E7B0EB57` / matrix.txt `804A42FB09D74FF5` / profiles.txt `83C4AD6B6317B8A6`，
   SHA256 前 16）——本批零 L1 接触。
10. **文档口径**：AGENTS.md 主权条款第③通道改「已落地」（两入口语义写清：print 恒纯、
    装饰须显式 `decorate` 后递入；宪法句一字未动）；使用文档《Minecraft 界面入口》并入
    「聊天 markdown 递交」条目（纯递交 + 显式装饰两例、线程语义 = 与原版 `printChatMessage`
    同主线程约定、未接管降级行为）；未新建独立文档、未触版本文件（gradle.properties /
    build.gradle.kts / CHANGELOG.md 零接触）。
11. **设备验证清单（本批未跑真机，如实挂账；①-④ 沿 C7 未销项不重列，本批新增 ⑤⑥）**：
    ⑤ `printMarkdown` 真机观感——markdown 系统行左对齐、无气泡、无 sender、不居中，行内
    列表/引用/代码块/粗斜体排版与气泡行同源；⑥ 未接管（总开关关/接管未装）时 String 形降级
    不露 `uilib.markdown` 字面、显示原文（组件形给自定义组件时原版渲 key 字面属固有形状，
    观感确认即可，非缺陷）。

### C9 细账（测试平台鲁棒化，2026-09-07 用户裁定「甲」；测试批，主源零接触）

**背景与定性**：Windows 本地全绿（4114/368/0/0/2）下，Linux CI（GTNH build-and-test，xvfb）
红 8 项（run 944175267，报告存档工作站 `temp\ci_reports\944175267-reports\tests\test\`）。
共性=断言里写了 Windows 字体度量下的绝对像素/计数魔数，语义零平台差——铁证：CI 产出的
门禁 diff.txt / matrix.txt 与本地**逐字同哈希**，只有 profiles.txt 平台相关。根因面：
测试与生产共用 AWT 逻辑字体 `Dialog`（仓库不携带 TTF），Windows 解析到 Microsoft Sans
Serif 系（CJK 全宽 ≈16px@16），Linux 解析到 DejaVu Sans（拉丁宽 ~10%、**无 CJK 覆盖** →
CJK 窄化 ≈10px 且无墨）。裁定「甲=平台鲁棒化」：判据改「同 JVM 独立测量后比较」的相对形
或「由构造推导」的派生形；禁 assumeTrue 跳过、禁 OS 分支放宽、禁为绿调容差。先例在案：
`docs/反馈层/errors/ERROR-20260716-font-test-platform-advance.md`（同一课，当时只修一例）。

**8 条逐例**（Linux 为何红 → 改后为何两平台都真）：

1. `FontSoftwareRasterizerSamplingTest#realPipelineGlyphQuadsSampleAtlasWindowAtCorrectPositions`
   （:214「字形#4 右上半区像素地板 实测=11<12」）——红因：「I」字形 quad 宽随字体 ink 盒
   （Win 2.5px、Linux 2.25px），T2 计数 11 是几何事实不是缺陷，地板 12 是 Windows 一次
   读数。修法：地板全部关系化——①覆盖 tri1+tri2 ≥8（16px 大写 cap-height ≥8 行 × ≥1 列）；
   ②上下半区计数比 ∈[¼,¾]（quad 对角线二等分面积，像素中心离散化偏移 ≤¼，几何不变量）；
   ③T2 真墨 ≥1 且 K≥N/13（缺陷 180° 旋转使失配 ≈2K，击穿 0.85 率阈需 2K>0.15N——地板即
   判别力下界，字体稀疏到带外如实红「本环境样本失去判别力」）；④全页墨 ≥ Σatlas真墨/2
   （正确渲染墨 ⊇ atlas 真墨）。0.85 一致率与正对照判据一字未动。两平台真：计数比
   Win 13/24、Linux 11/26、Verdana 33/60、SimSun 30/60 全落带内；旧页墨 150 在 SimSun
   （实测 122）下反证为哑弹。
2. `MarkdownListContinuationLockTest#continuationInsetEqualsIndependentlyMeasuredMarkerWidth`
   （:232「续行数地板 ≥6 实测 3」）——红因：语料长行 30/40 字按 Windows CJK@16≈16px 写死，
   Linux CJK≈10px 不折行，续行只剩逻辑行。修法：语料改 `wrapCountChars`（实测单字 advance
   推导「容量+2」字数 ⇒ 任何字体必折且第二行 ≥2 字），地板 6 保留并注记构造推导值 ≥9；
   名字里 IndependentlyMeasured 的本意（oracle 独立 + 输入侧不绑度量）至此名实相符。
3. 同类 `#listColumnIsActuallyConsumedByWrapping`（:316「b(45) < a(45)」）——红因：45 个 CJK
   在 Linux 480 容器两案都不折 → b=a。修法：容器宽改 `narrowWrapWidth = round(3cw)+inset−1`
   （cw=实测「癸」宽、inset=实测「• 」列宽），b=2、a≥3 **由构造必然**；补构造反证地板 a≥3、
   b≥1（防「两边空转 b<a 恒成立」）。实测：Win a=3/b=2，Linux a=4/b=2，Verdana a=5/b=2。
4. 同类 `#looseItemFollowUpBlocksAllGetTheirItemsContentColumn`（:609「expected:<14> but
   was:<17>」）——红因：期望列宽是 Windows 字面量 COL_BULLET=14，Linux「• 」@16 实测 17。
   修法：期望值全部 `measuredColumn` 当场量（与 L2 写入路径零共享的 oracle 早已存在，缺的
   只是别把 Windows 读数当规格）；混级「21」→实测「1. 」列宽；语料长度自适应；断言行数
   地板 ≥8 保留（构造推导 ≥10）。引用步长 8 为样式表登记常量（非字体度量），字面保留。
5. 同类 `#threeLevelColumnsAreChainSumsNotFixedStepProxies`（:435←:524「每块必须软折出
   第二条视觉行」）——红因同 2。修法：真值 14/28/42 与反证代理 29/43/36 全测量形（真值=
   逐级 ceil 之和、代理=整串 ceil 当场量），并加「代理与真值必须可区分」前置——某平台重合
   即「样本失去判别力」如实红（Win 29≠28、Verdana 31≠34、SimSun 30≠32 三组实测可区分），
   既不硬写会误红的字面量、也不静默放宽；折行由构造保证。
6. `MarkdownSoftwareRenderTest#writesPerCaseAndCompositePngsWithFloors`（:332「case=heading
   实测=130<500」）——红因：CI 的 DejaVu 无 CJK，纯中文标题页只剩 ASCII label 有墨（CI 存档
   `markdown-render/01-heading.png` 目检铁证：只有「01 1..6」）。读码确认**无逐像素金样
   比对**（PNG 只回读数墨），故无需任何金样 OS 条件化。修法：页/合成图墨水地板改
   「ink ≥ 同 JVM 实收 quad 数」（bbox 定尺画布上每枚 quad ≥1 非背景像素；「页非空」语义
   两平台同一真断言）；顺带拆掉同类 PNG 字节地板 >1000（Verdana 模拟 <1000B 实证误红、
   Linux 1129B 余量仅 129B——字节数随字体覆盖与压缩器漂移，非语义；职责移交「可解码 +
   墨水关系形」）。
7. `PlaygroundButtonRowLayoutTest#buttonRowsStayInsideRowContainersOnEveryPage`
   （:175「行 @x=12,w=628 子右缘 636>628」）——**判为真实布局缺陷，本批不闭环**（主源未动、
   断言未放宽）。证据链：①测试纯量引擎输出，零硬编码；②工作站 temp 临时取证（跑完即删）
   强制切到 markdown 页后 **Windows 同样溢出 628/636，数字与 CI 逐字相同**——溢出与字体
   无关：`MarkdownPage.quoteGroup` 的内层列默认 FILL，在 SHRINK 行里拿到整行宽 628、
   x=8 ⇒ 右缘 636，行自身被 clamp 到可用宽 628；③Windows 为何从未见红：navBar 可用宽内
   第 9 段（x=685..833）中心 759 > 画布 720，`clickNode` 静默 miss、页面切换未发生，
   测试停在 latex 页（无 ROW 容器 ⇒ pairs 空 continue）——**该测试在 Windows 上从未覆盖
   markdown 页**；Linux CJK 窄 ⇒ 导航放得下 ⇒ 切页成功 ⇒ 真缺陷第一次被看见。建议（供主控
   裁定，主源批）：页面侧 `quoteGroup` 内层列 `setWidthSizing(SHRINK)`（一行，语义=行按
   内容宽）；或引擎侧「SHRINK 行内 FILL 子的可用宽扣主轴已占量」（影响面大，需盘点既有
   消费者）；测试侧应改为切页后断言 `__getDisplayedPageId()==目标`（或经 signal+flush
   确定性切页）——测试侧收口必须与主源修同批，否则 Windows 先红。工作站 parked 清单该条
   处置由主控更新。
8. `PlaygroundPageRegistryTest#markdownPageListContinuationAlignsToContentColumn`
   （:663「expected:<13> but was:<15>」）——红因：祖先列硬值 (chainLen−1)×13 的 13 是
   「• 」@14 Windows 读数，Linux=15。修法：期望改「该行标记链扣除本级后逐元素独立量出之和」
   （与同例 :643 对齐行 oracle 同源不同例，专钉「标记行吃祖先列」写入路径）；代理反证 12
   改当场 floor(adv(「  」))（Win 读数仍 12）+「代理与真值可区分」前置。同批派生化：
   MIN_BACKDROP_PIXELS=3000 / MIN_BAR_PIXELS=200（Windows 面积一次读数）→「每底色带高 ≥
   本 JVM 实测行高」「竖条像素 ≥ barWidth×行高×层数」（三因子现取）。双度量自证见下。

**同类扫描（grep 证据）**：全测试树扫「地板/ink/MIN_*」——已修=上列 1/6/8 涉及的 6 处绝对
地板 + PNG 字节地板；**保留并给理由**：合成 quad 用例地板（8×6 画布自造纹理，字体无关）、
`MarkdownBlockGeometryTest`（命令计数形；65 字 ASCII token@120 容器 ≥3 行由构造）、
`MarkdownBlockContentWidthLockTest`（全计数形 + CI 已证两平台真）、`PlaygroundPageRegistryTest`
MIN_RUNS/MIN_BAR_COLUMNS/参与行≥8/标记行≥5（语料结构常量）、`MarkdownChat3ParityTest`
矩阵正文/出图数地板与反射扫描数 `scanned≥15`（计数形）、@Nx 判读副本 `png.length()>100`
（防截断守卫：最小画布 854×480 的纯色 PNG 本体即 >100B，任何字体下不可能误红，非度量
读数）、`ChatMessageListTest` 软折地板（100 字语料「两种度量模式
同真」构造形，设计注释在案 :1928-1929）、`LatexSoftwareRenderTest`（ink 判据全 >0 或 ±
相对形，公式字形 DejaVu 有覆盖）、`MarkdownRenderScaleKit.MIN_CANVAS_*`（输入侧补白常量）、
guard 族 MIN_HITS（源码扫描计数）、`GtnhWelcomeLineBreakRealMetricsTest`（真度量但判据
结构形、语料纯 ASCII）。

**门禁判据更正（三件套→二件套）**：原三件套判据由 C8 批登记（本 § C8 细账第 9 条，
2026-09-07，主控按用户裁定 a1 记录）。C9 起「哈希逐字不变」判据收窄为 **diff.txt
(A826A2B9E7B0EB57) + matrix.txt (804A42FB09D74FF5)** 二件套；profiles.txt 降为环境观测
记录、退出哈希判据——实证两面：Linux 值全面不同（fontScene/ink/Bh 行）；本机过滤跑与
全量跑之间 fam 扫描计数 245↔251 漂移（C8-era 代码复跑同漂移，非代码引入）。已同步登记
`MarkdownChat3ParityTest` 类头「C9 门禁判据收窄」段。

**双字体模拟（自证方法与结果）**：kit 面=`LatexSoftwareRenderKit` 环境变量钩子
`QZ_C9_TEST_FONT`（默认不设=行为逐字不变）；生产度量面=`C9DualFontProbeTest`
（@Ignore+理由常驻：fontSort 扰动只在 FontService 首初始化前生效，禁与常规套件同 JVM
共跑；含扰动生效自检 adv(•)@14 5.844→7.791）。实测矩阵（Windows 主机，Dialog/Verdana/
SimSun 三组度量）：`MarkdownListContinuationLockTest` 6/6×3、`FontSoftwareRasterizerSamplingTest`
4/4×3、`MarkdownSoftwareRenderTest`+`MarkdownChat3ParityTest` 15/15×3、playground 三锁
4/4（Verdana）。Verdana 的「• 」@16 列宽=17 恰与 Linux CI 实测同值——模拟的正是炸出来的
那类扰动面。**最终 Linux 真证 = 推送后 CI 复跑（主控执行，非本批未完成项）**。

**实测计数与门禁**：`build --offline` = BUILD SUCCESSFUL；`cleanTest test` =
**4118 tests / 369 suites / 0 failures / 0 errors / 6 skipped**（脚本数 XML 非心算；C8 基线
4114/368/0/0/2 净 **+4 用例 / +1 类 / +4 skipped**，全部来自 C9·5 探针类 @Ignore 常驻，
零既有用例删改）。diff.txt / matrix.txt 哈希逐字未变；profiles.txt 全量复跑逐字重现
C8 登记值 83C4AD6B6317B8A6（本批零接触实证）。

**提交分解**：C9·1 采样断言关系形 + kit 钩子；C9·2 列表正文列锁测量形+自适应语料；
C9·3 出图/门禁墨水关系形 + PNG 字节地板拆除；C9·4 页面锁测量形 + 面积地板派生化；
C9·5 双字体探针（@Ignore）；C9·6 文档（本细账 + Parity 类头门禁段 + 踩坑记录 C9 条）。
版本文件（gradle.properties / build.gradle.kts / CHANGELOG.md）零接触；主源零接触。

**#7 闭环（C9·7，2026-09-07——本系列首个动主源的测试缺陷闭环批）**：

- 缺陷机理（复核确认）：`MarkdownPage.quoteGroup` 内层列默认 FILL；SHRINK 行在约束下传阶段无己宽
  先验（`SizingCalculator.computeWidth(c,false)` SHRINK 分支保守回退外层可用宽，:85-88/:134-138），
  FILL 子列照单全收整行宽；定位侧再叠条 2px+gap 6px=8px 主轴偏移 ⇒ 子右缘恒超行宽 8px（720 画布
  628+8=636>628）。纯结构性、与字体无关。取证（工作站 temp 临时探针，跑完即删）：修复前 markdown 页
  **pairs=8、OVERFLOW=4**（与 Linux CI 及主控 Windows 探针逐字同数）；修复后 **pairs=8、overflow=0**，
  9 页全扫零溢出。
- 修复形=内列 `setWidthSizing(SHRINK)`（一行+javadoc 宽度语义段）：引用组宽=条+gap+内容列实测宽
  （`computeShrinkContainerWidth` COLUMN 分支=最宽子行+padding，被可用宽 clamp），不再伪装拉满。
  M10a「与平铺在卡列时逐字节相同」承诺仍成立的维度：组高=Σ行高+gap×(n−1)、行 y、文字 x、竖条像素
  全同；变窄的只有隐形容器盒右缘（引用组无底色=零像素表现）。组内各行共享内容列宽（取最宽子行），
  无参差——「量最宽子行写显式 preferredWidth」设想即 SHRINK 的引擎原生形，页面不自算第二套宽度真相，
  故弃显式形。观感影响=设备清单：真机 MarkdownPage 引用演示区（预期零像素差；引用组右缘无可见物，
  既有锁覆盖：像素三锁证文字/竖条不动；盒宽本身无锁覆盖——无像素表现故无需锁）。
- 夹具收口两改（与主源修同批提交，缺一判不合格条款）：①切页确定性化——`TestPlaygroundHost
  .__getActivePageSignal()`（包级探针外露 R8 受控源=SceneSegmented.onSelect 写回的同一 signal，非新
  通道）`set+flush`，切页后硬断言 `__getDisplayedPageId()==目标`（`PlaygroundButtonRowLayoutTest
  .switchToPage`、`PlaygroundPageRegistryTest.everyPageBuildsNonNullTreeAndMountsCleanly` 宿主落点段、
  `PlaygroundTextInputPageTest.setUp` 钉落点）；②markdown 页非空覆盖锁——pairs≥1 且含引用组行（ROW 恰
  2 子、内列 COLUMN 且 SHRINK）回锁，内列回改 FILL 则覆盖锁与右缘不变量双杀。更正下发口径：
  PlaygroundPageRegistryTest 原状无点击式 switchToPage（其 markdown 像素锁走 lookup+build 直建，与点击
  无关），2a/2b 在该类落点=新增宿主段而非改既有点击。
- 新增观察（非 #7、挂主控裁定）：home 页「演示页」长描述行文本叶在 ROW 主轴拿整行宽 clamp（可用宽
  不扣主轴已占量），Windows 实测随类序/JVM 初始化在「溢出（671/721/754 读数漂移）」与「不溢出」间
  摆动——与 #7 同族（引擎侧 ROW 约束下传），面大，本批未动。
- 提交分解：C9·7-1 主源（d2aac5a2）；C9·7-2 夹具（同批）；C9·7-3 文档（本块 + 踩坑记录 C9 第 5 点
  挂账指针改闭环）。实测：`build --offline` 绿 + `cleanTest test --offline` 强制复跑绿，4118/369/0/0/6
  与基线逐字同（只加断言不新增用例）；diff.txt/matrix.txt 二件套哈希逐字未变，profiles 复现登记值。

---

## 三、迁移与「不得并存」门禁

B 案最大的风险就是长出第二套真相。用**顺序 + 门禁**防，而不是靠自觉：

```
M1 复活 L1 行内解析器到 font/layout/markdown + 历史测试矩阵复原（当前无消费者：死代码窗口，
   必须在同一提交序列内接下一步；测试矩阵是它「不是死代码」的证据）
M2 L1 扩块级 + 建 MarkdownDocument 数据模型（仍零消费者）
M3 L2 绘制层 + 独立演示面（devtools playground 新增一页，不接业务；先立可对拍的验收面）
M4 行为对拍门禁：同一批语料，chat3 现路 vs B 路，段流/宽度/命中区逐项比对（脚本，非常驻测试）
M5 接线：chat3 改吃 B 的产物，并同提交删除 ChatMarkdownLineRule + ChatCodeSpanSplitter 的解析部分
M6 复生锁：守卫断言 chat3 内不再有 markdown 解析实现（见 §四 G3），删除后不可回潮
```

**硬规矩：M5 与 M6 同一提交。** 删旧与接线同时发生，工作树里任何一刻都不存在两条真相。
M4 不过就不进 M5 —— 这是唯一的「先立后破」次序，不因进度压力让步。

> C3b1 重基线注记（2026-09-06）：上述 M4「chat3 现路 vs B 路」的**行为规格快照基准**已由
> C3a/C3b1 废止——A 路复刻整体删除，常驻门禁现为 R=commonmark-java 0.21.0（+GFM
> strikethrough）对拍 B=本仓行接缝（C3b2 判据定稿、C3b3/C4 撤豁免清零 RECORD，见 §二之八）。
> 「先立后破」次序与 M6 复生锁本身不变——复生锁钉的是「旧解析不得复活」，与门禁基准无关。

## 四、硬约束与要写的守卫（本仓规范）

- **G1 不绕过自有抽象**：L2 产出 `PaintCommand` 流、度量走 `TextLayoutService`，
  **新代码里 `GL11.` 出现次数必须为 0**（既有 `UiHudRenderListenerGlFenceTest` 同族做法：
  反向锁 + 正对照 + 用量地板，防止 ∅ 空跑蒙绿）。
- **G2 零 MC 依赖锁**：`font/layout/markdown/**` 不得 import `net.minecraft`/`cpw.mods`/AWT。
  照 `LayerContractGuardTest` 的写法，须带反空跑地板（扫到 ≥1 个真实文件才判定）。
- **G3 复生锁（M6）**：`internal/chat3/**` 不得再出现 markdown 定界符解析
  （模式如 `indexOf('*')`/`"~~"`/`"**"`），且 `ChatMarkdownLineRule` 必须不存在。
  这条只在 M5 之后加，加了就要当场验它现在为真（不是将来时）。
  **已落地 `3e89d91e`** = `Chat3MarkdownResurrectionGuardTest`（4 断言 + 正对照地板，
  见 §二之六表）；同笔把 G1 的 GL11 计数口径统一申明为「按出现次数（含注释）」。
- **G4 度量同源**：块级排版不得自带一套字号/行高常量。chat3 现有一批定值
  （`INPUT_AREA_INSET_PX` 已收口为单一来源；`getCodeFontSizePx()` 等仍在 `ChatMarkdownSettings`）。
  B 的默认样式表放哪属裁定 D3。
- 每次 M 步收尾跑 `./gradlew.bat build --offline --console=plain`，绿了才提交；当前基线
  **4059 / 0 / 0 / 2，365 类**（C4-fix 乙′批后：C4 基线 4057 上净 +2——一致性锁 +
  C4 前语义恢复锁两条，清洗本体锁与两条行为锁为换名反转非增减；门禁语料 42→48、
  PNG 84→96、RECORD 恒 0；历史：M5+M6 3975/359 → 软光栅修复 +4=3979/360
  → M7 方案乙 3992/362 → C 系列各批见 §二之八与提交信息）。

## 五、裁定结果（2026-09-04，D1-D4 全部照建议通过）

> D1 L1 回 `font/layout/markdown` + L2 新建 `ui/markdown`；D2 从 `9c4dcae5` 复活再审；
> D3 先做最小公共面；D4 图片/表格/任务列表/tooltip·书本全部划到本期范围外。
> 追加重写：M3 的验收面必须含**游戏内可视测试页**与**headless 出图**两件事，见下 §五之二。
> 原「要你裁的四项」正文保留在下文作裁定依据，不再待决。
>
> D4 范围外分支销账进度：表格已于 2026-09-07 正式另立项（裁定终表与 T1/T2/T3 批次见《规划-Markdown表格.md》，T1 待开工）；图片与任务列表仍范围外。

### 五之二 两条必做验收面（用户 2026-09-04 追加，写进 M3 完成定义）

现成范式已核过，**照抄不另造**：

| 能力 | 既有实现（一手核实） | markdown 侧对应做法 |
| --- | --- | --- |
| headless 出图 | `FontSoftwareRasterizer.toImage(argb,w,h)` + `writePng(...)`（`ImageIO`） | 同一 rasterizer，产 PNG 条带 |
| 出图目录与命名 | `LatexSoftwareRenderTest` 写 `build/reports/latex-render/%02d-formula.png`，另有 `profiles.txt` 记环境 | `build/reports/markdown-render/%02d-<case>.png` + `profiles.txt` |
| 成对对比图 | `LatexReferenceComparisonTest` 写 `-ref.png`/`-ours.png`/`-side.png` 三件套，用 `Assume.assumeTrue("参考 jar 不存在，跳过")` 门控 | 接线前的 chat3 现路 vs B 路**也用 side 成对图**，即 M4 对拍门禁的可视化产物 |
| 共享装配 | `LatexSoftwareRenderKit` 注释明确「`GlyphRuntimeTables` 每实例约 123MiB，必须共享」，`@AfterClass` 释放 | markdown 出图测试**必须复用同一共享装配**，不得各自 new FontService（否则测试 JVM 堆被 123MiB×N 挤爆） |
| 游戏内可视页 | `internal/devtools/playground/pages/LatexPage.java`、`RichTextPage.java`；`TestPlaygroundHostTest` 有「注册表含 latex 页」的正向锚 | 新增 `MarkdownPage`，并给 playground 注册表测试补一条「含 markdown 页」正向锚（防注册漏了而测试仍绿） |

**分工明确（用户定的验收姿势）**：headless 出的 PNG **我自己用视觉检验**（读图比对
定界/换行/公式位/代码块底色），游戏内可视页由你看观感与手感。因此出图测试不能只断言
「文件存在/像素非空」，必须产出一张**人眼可判**的整页合成图（多样本纵向拼接 + 每条留 label），
否则我看不出对齐问题。同时保留可机器判的断言（非空像素数、宽度不超容器、基线单调）作为地板。

旧标题：

### （原）五、要你裁的四项（D1 不开工就没法写第一行）

- **D1 包归属**：L1 回到 `font/layout/markdown`（沿用 2026-08 裁定，与 `RichTextTagParser` 同级），
  L2 新建 `ui/markdown`？**建议就这样**。备选：两层都进 `ui/markdown`（好处是门面集中，坏处是
  把纯解析层挪进 UI 层、破坏「L1 零 MC 依赖」的可测性）。
- **D2 复活 vs 新写**：建议 `git show 9c4dcae5` 取回行内解析器与其测试矩阵做基线（省一轮
  误伤规则重设计），代价是要先审它一遍。备选：只把语义裁定当规格、代码新写（干净但重犯
  flanking/CJK 那些坑的概率高）。
- **D3 公共 API 面**：新公共类是公共兼容承诺（AGENTS.md 要确认）。要多少表面？
  最小面 = `MarkdownDocument.parse(String)` + `render(...)` 走既有绘制签名 + 一个样式表类型；
  宽面 = 再加块级查询/自定义节点扩展。**建议先最小面**，留口靠既有 `Kind` 式枚举而不是开放继承。
- **D4 范围外**：图片 `![...]`、表格、任务列表本期不做（可另立项）；
  tooltip/书本接入本期不做，只保证 L1/L2 不挡路。

## 六、风险（按会不会真出事排）

1. **死代码窗口**（M1-M4 期间 B 无业务消费者）：靠 M5/M6 同提交收口；期间若被打断，
   仓里会留一份「已测但没接」的解析器 —— 我认为可接受，但要在提交信息里写明窗口结束条件。
   **已闭合（`3e89d91e`）**：窗口结束条件（chat3 气泡消息段流改吃 L1/L2 且旧垫片同笔删除）
   已达成并写进该笔提交信息。
2. **对拍不等价**：chat3 现有实现带着若干真机踩坑修正（行junction 丢失见
   `ERROR-20260825-chat3-line-junction-loss-stale-jar.md`；`ChatLineLayouter` 注释里那条
   「结尾 + 下一行以 URL 字符开头在两种断行下文本完全同形」的反查陷阱）。B 必须承接这些
   结论，不能只对齐语法。**M4 的语料要专门含这几类。**
3. **每帧成本**：markdown 解析必须在消息到达时做一次（既有裁定「零每帧解析」，靠布局缓存 +
   度量纪元失效）。B 若提供「每次渲染现解析」的便利入口，就是把性能债埋进公共 API。
   建议 L1 只提供纯函数，缓存责任留在消费层，且这条写进 javadoc。
4. **配置字段继续膨胀**：`ChatMarkdownSettings` 还挂着 64 字段 final 化（E，裁定「先不改 final，
   挂着」）。B 若新增自己的可调项，必须走单一登记面，别再造一个 64 字段的大杂烩。
   与 R4（单字段登记表）有交集，排期上 R4 可能反而要先走。
5. 运行态（真机）：本次规划**未跑过任何真机渲染**，M3/M4 的对拍与验收都在 headless 与
   devtools 演示面内，视觉效果仍要你在真机看。
