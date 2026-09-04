# 规划：通用 Markdown 解析渲染器（B 案）

**状态：** D1-D4 已裁（§五）；**M1 `6d9de24c` + M2 `08a8034e` + 裁 B 收窄 `736bafc1` + M3
`531da89e`/`339530e0`/`eddd2c29` + M4/M4-fix `075328ef`/`00d1a45a` + M5 接线 & M6 复生锁 `3e89d91e`
全部完成**（2026-09-04）：chat3 气泡消息已改吃通用 markdown 渲染器，旧行级垫片已删，
死代码窗口闭合（§六 1）；余下的是真机观感验收（§五之二分工）与后续独立裁定。
方向（用户 2026-09-04）：**先建独立通用渲染器 → 删 chat3 现有简易实现 → 接线**——三步全部落地；
基线 **3975 / 0 / 0 / 2，359 类**。
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
  **3975 / 0 / 0 / 2，359 类**（M5+M6 后）。

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
