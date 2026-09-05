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

- 块级：ATX 标题 `#..######`、围栏代码 ``` / ~~~、引用块 `>`（可嵌套）、无序/有序列表
  （含缩进续行）、分隔线 `---`/`***`、段落与空行、硬换行（行尾两空格 / 反斜杠）。
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
| F2 | `MarkdownDocument.walk/emit` 带 `markerLevel`，`emitListItem` 按级拼 2 个前导空格进 bullet 段文本（`ChatMessageList.java:952-956` 同口径）；块模型与缩进 px 仍未外开 | `fixF2NestedListIndentIsLeadingSpacesInMarkerSegment` + 门禁 P08@150/@269 |
| F3 | `MarkdownStyleTable.get/setQuoteTextColor`（默认 0xFF9AA0A8 = chat3 次级色），`quoteStyle()` 在 QUOTE 块应用；0 = 不降色 | `fixF3QuoteTextColorKnobMatchesChat3Secondary` + 门禁 P12 |
| F4 | `MarkdownBlockParser.markerView()`：**定稿口径 = 行首 § 序列仅在确实命中块标记时随标记一并消费；未命中块标记时一字不动；这不是解析 § 颜色，颜色语义仍由下游决定**。理由：A 路 `ChatMarkdownLineRule.classify` 第一步就 `stripLeadingFormatCodes`，实测 A 侧文本 `«• 玩家列表行»` 不含 `§a`；若「命中也原样保留」则漂移 2 码点 / 14px，P13 恒红 | `fixF4BlockLayerToleratesLeadingSectionCodes` + 门禁 P13 |
| F5 | 落在 **L2** `MarkdownLineLayout.unifySwitchPointSpaces`：只在「两侧仅颜色不同、FontType/fontSizePx/italic 逐项相同、两侧非 code/link/latex」时把上一段尾随空格并进后一段（度量中性，逐字符推进宽与总行宽一字不变）。不落在 L1 的 `parse(spans)`：那里有既有测试 `shouldParseSpanStream` 钉死「尾随空格归前段」 | `fixF5SwitchPointSpaceBelongsToNextSegment`（段文本+段宽双等）+ 门禁 P14 |
| F6 | 走 C1：`MarkdownBlock.blanksBefore`（包内）由 `parseBlocks` 在消费空行处 `stamp()`，`walk` 在该类块边界产**一个空文本占位段**；L2 `splitLogicalLines` 认它强制产一个空显示行、`wrapVisualLine` 不再吞中间空行。零公共面变更（未给 `TextStyle`/`TextSegment` 加几何字段） | `fixF6BlockGapBecomesExactlyOneVisualBlankLine` + 门禁 P16 |

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
2. **行中间的 § 码**在文档形参路径（`MarkdownDocument.parse(String)`）仍是字面文本，chat3 会由
   `parseSegments` 解析成颜色。这是「markdown 不引入颜色」旧裁定。**M5 实际落定（措辞定稿）**：
   消息路在 `toSegments` 之后走 chat3 侧 § 桥（`ChatMarkdownPipeline.bridgeSectionCodes`，逐段
   `TextStyle.applyFormat` 切分，latex/codeSpan 段恒透传），§ 码在消息渲染中被 L0 同源语义消费，
   不会以字面颜色形态遇到；直接使用文档形参路径的消费方（playground/门禁 B 路 bridge=0 语料）
   行中 § 仍字面，旧裁定不变。桥不用 `MarkdownInlineParser.parse(spans)` 反接：那会把已消费的
   未闭合定界符二次配对（双解析漂移），且块级结构（F2/F3/F4/F6）只在文档路径存在。
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

1. **F2 补全**：顶层列表块携带 `baseLevel = 1 + 首行前导空格/2`（`MarkdownBlock.baseLevel` +
   `readDeepList` 剥基准缩进），独立成块的 `"  - 乙"`/`"    - deep"` 与 chat3 旧行级规则
   「层级 = 前导空格/2」同缩进；嵌套子列表（depth≥1，contentCol 已剥）保持相对嵌套，
   P08 语料输出逐位不变（门禁实测 PASS）。
2. **markerView 修正**：`§f` 后带空格再命中块标记时，旧实现把该组空格二次计入视图 →
   缩进翻倍落回字面（丢 F4 承接）。改为按消费点续切；`fixF4` 既有钉死用例输出不变。

**门禁本体**：A 路两旧类语义按 1:1 快照移入私有方法（`classifyReplica`/`codeSpanSplitReplica`，
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
  **3992 / 0 / 0 / 2，362 类**（M7 方案乙后；M5+M6 时 3975/359，软光栅修复批 +4=3979/360）。

## 五、裁定结果（2026-09-04，D1-D4 全部照建议通过）

> D1 L1 回 `font/layout/markdown` + L2 新建 `ui/markdown`；D2 从 `9c4dcae5` 复活再审；
> D3 先做最小公共面；D4 图片/表格/任务列表/tooltip·书本全部划到本期范围外。
> 追加重写：M3 的验收面必须含**游戏内可视测试页**与**headless 出图**两件事，见下 §五之二。
> 原「要你裁的四项」正文保留在下文作裁定依据，不再待决。

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
