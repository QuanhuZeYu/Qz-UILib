# 规划-Markdown 表格（立项定稿）

**状态：** 立项**冻结**（2026-09-07）。来源 = 用户发起「可否推进表格解析渲染」→ GPT 网页两轮审查 + 本地一手核查逐轮收口，终轮双方一致「不需要再改方案，等 C9 收口后落笔」；C9·6（`cdde849f`）落地后成文本档。**T1/T2 已完成**（2026-09-08：语义契约、像素布局、页面/headless 首消费者、独立对拍、真实 chat3 字面降级反锁与台账同批验收；T3a/T3b 仍待各自裁定）。
**上游裁定：** 《规划-通用Markdown渲染器.md》§五 D4「图片/表格/任务列表/tooltip·书本划到本期范围外（可另立项）」——本文即该"另立项"；§202 C3（块模型进公共面）的预定触发器（"证明行接缝表达不了表格/多栏"）已由 T1 的 B1 最小契约承接，**不整套复活 A 案**。
**目标仓：** Qz-UILib（branch `4.0`，MC 1.7.10，本地提交不 push）。
**基线（立项时点，一手核实）：** `4118 / 0 / 0 / 6，369 类`（C9 收口后）；常驻门禁 R = commonmark-java 0.21.0（+GFM strikethrough）对 `toLayoutLines` 逐行逐 token 对拍，RECORD 恒 0；判据按 C9·6 为**二件套**——语义工件 `diff=A826A2B9E7B0EB57`、`matrix=804A42FB09D74FF5` 逐字不变（profiles 降为记录不校验）。

## 一、立项前一手核查事实清单（本档全部设计判断的地基）

1. **L1 对表格零认知**：`MarkdownBlockParser` 无任何 `|` 行分支（grep 核实），表格现状 = 普通段落字面（`MarkdownBlock` 类头明文「刻意不支持……按普通文本字面保留」）。在明确的 unsupported 分支上开表，不是修补被污染的实现。
2. **接缝冻结现状**：`MarkdownLayoutLine` 全成员恒 **20**（`MarkdownPublicSurfaceGuardTest` 逐类分解钉死），公共 10 参构造器**签名自 M7 冻结**；其类头声明「刻意不是块模型（无子树/无 children）」——二维块塞进行语义域即违约。
3. **像素归属既有事实**：`MarkdownPainter.wrapLayoutLines/toLayoutPaintCommands` 均持 `TextLayoutService`；`MarkdownLayoutLine` 全字段构造 javadoc 明言「块统一内容宽是度量事实，只有持度量服务的 L2 能算，L1 恒取定义值 0」。表格列宽同理落 L2，L1 零度量铁律不破。
4. **样式登记先例**：`MarkdownLayoutLine` 类头（:29-32）说明 `MarkdownStyleTable`「公共方法面不因此膨胀」+ F1（code 衬底/字号）、M7（块级几何）**包内登记**先例在案——表格旋钮零公共面增量有既定机制，审查侧「样式面空白」实为"故意未公共化"。
5. **嵌套类型台账细则**：守卫细则「public 嵌套类型一律不计成员数」（`Kind` 即先例）——单外壳 + 嵌套的骨架形状台账成本最低（§二 B1）。
6. **oracle 依赖现状**：`dependencies.gradle` 已有 core + strikethrough（注释明文「testImplementation 不进生产 jar」，2026-09-06 用户裁定引入）；`GRADLE_USER_HOME=D:\Apps\.Env\Gradle\.gradle` 缓存**无** `commonmark-ext-gfm-tables` 工件且 build 一贯 `--offline` → 需一次联网（§二已写死为前置）。
7. **R 侧映射器可扩展**：门禁 R 侧 `CommonMarkReferenceSemantics` 按 commonmark Kind 映射（`BLOCK_QUOTE`/`CODE_FENCED` 等已在册），表格 AST 对拍面是它的同类旁路而非改造。
8. **关联布局状态**：C9·6 第 7 条的 markdown 页 `quoteGroup` 缺陷已于 **C9·7 闭环**：`d2aac5a2` 主源修（内列 SHRINK）+ `7e3acf72` 夹具回锁；细账见《规划-通用Markdown渲染器.md》§二之八 C9「#7 闭环」（`b84cdb98`）。用户复审核验 CI run `34136263189` 全绿，`:test` 真实执行；**#7 不再作为 T2 未结前置**。同块登记的 home 页长描述行 **ROW clamp 摆动另列 T2 前观察项**（C10 候选，待裁）：它与表格宿主列宽共用 ROW/COLUMN 约束传宽机制；T2 前须核查其是否影响表格可用宽和验收地板，不能把 #7 闭环当作该同族观察也已解决。

## 二、裁定终表（两轮收口冻结，重开须新裁定）

| 项 | 裁定 |
|---|---|
| Table 是否独立语义块 | **是**：L1 包内 `MarkdownBlock.Kind.TABLE` + 包内表格结构（Table→Row→Cell；cell 持 span 流内容，经既有 `MarkdownInlineParser`，**禁**造表格版行内解析器、禁存已渲染 String） |
| 跨层接缝形态 | **B1 = 最小跨层数据契约**：单公共外壳 `MarkdownTableModel`（Row/Cell/Alignment 全嵌套，无像素字段、无度量服务引用、不出具完整块子树）+ `MarkdownDocument` +1 导出法。这是"数据契约"，**不是** L1 AST 公共化 |
| `MarkdownLayoutLine` + 表格引用成员 | **否**（语义域不匹配优先于台账成本；行不装二维块） |
| 整块模型公共化（B2 / C3 全复活） | **否** |
| L1 直接算像素（B3） | **否**（违铁律 3） |
| 首消费者 | 仅 `MarkdownPage` + headless 出图；**chat3 拆 T3a（`printMarkdown`）/T3b（玩家气泡）分别另裁**（折行宽同源、气泡盒约束不同；各自明确 HUD/容器待遇）；T2 必过 chat3 接缝门，未裁定接入的路径维持字面降级 |
| T2 前 unsupported 降级形态 | **正锁**（L1 已识别 TABLE）+ **反锁**（`toSegments`/`toLayoutLines` 对表格输出与今日字面形状逐字节一致）+ javadoc 一句「TABLE 于 T2 前只解析、不布局」三件套；**不造** capability 协商框架（禁把暂态迁移变成永久公共概念） |
| 字面降级性质 | **产品化暂态行为**：T2 起按已裁定的消费者显式迁移（锁改动入提交信息），不是从测试黑洞捞伪实现；首消费者接入不等于所有共享接缝消费者同时解除降级 |
| 两个 oracle 面 | **彻底分离**：T1 = 新增 Table AST 对拍面（R=TablesExtension ↔ B=`MarkdownTableModel`），**现行行接缝对拍零接触**；其 CORPUS 无表格样本，二件套哈希逐字不变按 C9 口径只证明既有语义未受扰动，表格字面降级的逐字节一致另由反锁证明（**零接触观测件 ≠ 表格语义件，不可互替**）；T2 = TABLE 身份进布局接缝后，才把表格形状并入行接缝对拍 |
| 文档序交错 | 归 **T2**（T1 接缝不表达表格，"paragraph-table-paragraph" 在旧缝看仍是字面行，T1 无可验证性）；T2 三案实测（① 哨兵行+旁挂 model ② union item（破坏性，最后选）③ 平行表格 units+块锚），模板 = 仓内**两缝并存**先例（`toSegments`/`toLayoutLines`）；三案皆硬伤才升格「文档布局接缝抽象化」独立裁定 |
| 度量次序 | 宽度 pass 先于换行：measure 全 cell → intrinsic 列宽 → 按可用宽分配 → 逐 cell wrap → 行高 → 定位；禁"先按整行折再回头修表" |
| 样式面 | 表格旋钮全走包内登记（F1/M7 先例），`MarkdownStyleTable` 公共面**零增量** |
| 语法优先级 | 表头升级 vs setext H2（`---` 已被 C3b2/C4 实现并收紧惰性口径）、blockquote 内惰性行含 `|`、列数不齐、`\|` 转义——**一律以 TablesExtension 实测为唯一真相，不心算、不读规格凭感觉** |
| 缓存 | 表格布局进消费层 L3 缓存（列宽 = 全 cell 度量，比行布局贵一量级），随度量纪元整体失效；零每帧解析铁律；缓存责任在消费层（渲染器规划 §六风险 3 口径） |
| oracle 依赖 | `org.commonmark:commonmark-ext-gfm-tables:0.21.0`，testImplementation（同 parent），**一次联网拉取已获用户批准（2026-09-07）**；「运行时依赖 commonmark」的事实架构层面永不成立，不再讨论 |
| 首版语法面 | GFM 基础锁死（§五），一字符都不扩 |

## 三、批次定义

### T1 —— 语义与契约（不产一像素、不动任何消费者、行接缝零接触）

1. `MarkdownBlockParser` 识别 TABLE 块（GFM 表头+分隔行；优先级冲突用例先跑 R 侧实测再定实现）；
2. 包内表格结构 + `Kind.TABLE`（cell=span 流，样式锚点同 C6a 机制随行）；
3. 公共接缝：`MarkdownTableModel`（单外壳+嵌套，零像素）+ `MarkdownDocument` +1 导出法（含块锚信息）；
4. **新增 Table AST 独立对拍面**：语料覆盖列数/行数/三对齐/`\|`/列数不齐补空/cell 内 `** ~~ ` $ []` 行内复用/setext-vs-delimiter/惰性续行/引用内表格；
5. **现行行接缝门禁零接触**：既有语料、参考扩展、映射/归一判据与报告格式保持不变，验收 = diff/matrix 两哈希逐字不变；`MarkdownChat3ParityTest` 类头与 `CORPUS`（:199-318）限定此证据为既有无表格语料回归，B 路是 L1 直连 String，不经过真实 chat3 消费路径，不能代替下一条表格降级反锁；
6. 字面降级三件套（正/反锁 + javadoc）；
7. 守卫与台账同批：G2 新文件入零 MC 依赖扫描；G3 复生锁加反锁项「`ui/markdown`/`chat3` 内零 `|` 行检测」（带正对照地板）；五锚台账 +`MarkdownTableModel` 行（全成员+方法/构造/字段分解）与 `MarkdownDocument` 方法数续账；合计地板复核；`MarkdownStyleTable` 恒 19 不动；
8. `dependencies.gradle` +1 行，一次联网拉取（前置，可为 T1 首票）。

**T1 验收：** build 绿；行接缝二件套哈希逐字不变（既有语义回归）；Table AST 对拍全绿（表格解析语义）；表格样本反锁证明 `toSegments`/`toLayoutLines` 的消费者可见输出与立项时点逐字节一致（表格降级语义）；三者不可互替；javap 台账逐字入账。

### T2 —— 像素与首消费者（出图 / `MarkdownPage`）

- L2 表格布局 pass（intrinsic→分配→wrap→行高）→ `PaintCommand` 流（G1：新代码零 `GL11.`）；
- 文档序交错三案实测并裁；行接缝 oracle 于本批并入表格形状（TABLE 身份入缝的唯一时机）；新增表格语料/报告内容时显式登记对应二件套基线迁移，T1 的旧哈希不变判据不跨用到扩面后的报告；
- **chat3 接缝门（T2 硬验收，不得后移到 T3）**：TABLE 布局产物进入共享出口时，须对 `printMarkdown` 与玩家气泡分别登记「显式维持字面降级 / 经用户裁定显式接入」。T3a/T3b 未裁定接入前，两路均维持既有消费者可见字面输出，表格反锁必须覆盖真实 chat3 消费路径；共享 `toLayoutLines`/L2 管道不能代替消费者裁定，具体隔离落点随交错三案收口，不新增公共 capability 框架；
- 字面降级按消费者显式迁移：只撤换已裁定接入面的旧锁，锁改动逐条入提交信息；仍降级的 chat3 路径保留反锁，到各自 T3a/T3b 裁定时决定续留或迁移；
- 验收面沿《规划-通用Markdown渲染器.md》§五之二：headless 出图（人眼可判整页合成图 + 可机判地板）+ playground `MarkdownPage` 增表格段；
- 出图/页面判据**继承 C9 方法论**（派生形地板、双度量自证、代理可区分前置）；C9·7 的 #7 闭环见事实 8。T2 前核查 home 页 ROW clamp 观察项对表格宿主传宽的影响，记录约束来源与可复现性；若影响列宽/折行判据，先明确处置再定验收地板（C10 仍为候选，未裁定开工）；
- 消费层 L3 缓存 + 度量纪元失效落地并有锁。

**T3 拆分依据（T3a/T3b 共用的宽度与 HUD 事实）：** `ChatSceneController:744-745` 生成同一个 `maxLine = chatWidthFor(viewport) − 2×bubblePaddingX`，`ChatCardComposer:321/:334` 把同值送给玩家消息与 `printMarkdown`。后者豁免的是 `ChatMessageList:919-925/:1104-1111` 的气泡盒 maxWidth、padding 及行盒 reserve 钳制；玩家气泡另受 `0.85×` 盒宽上限。默认参数下，传入逻辑视口宽 1920 时两路折行宽均为 460px；在 `[360, 800)` 档，两路均只有 140px（Python 按代码公式验算；低于 360 另走半视口宽分支）。折行宽、气泡盒上限和最终正文可用宽属于不同约束层，不能用「整行宽所以顺带可做」论证 `printMarkdown` 表格可用。

`ChatMessageList:936-943` 对两路 markdown 的 HUD 输出都调用 `clampHudLines`，当前上限 8 个视觉行；`printMarkdown` 无气泡也无此豁免。表格可能在中途被截断，故 **HUD 与展开容器的表格待遇须分别裁定和验收**，不能把容器出图通过视为 HUD 通过。

### T3a —— chat3 显式 markdown 行（`printMarkdown`，另裁）

- 在同源窄聊天列下评估表格布局或维持字面降级，由用户裁定；无气泡盒约束是独立评估的理由，不是充分宽度保证。
- 裁定须明确超出 HUD 行预算时的行为，并以跨预算表格样本验收；展开容器另验完整展示或裁定的降级形态。

### T3b —— chat3 玩家气泡（另裁）

- 在气泡盒上限、padding、accent/quote/list reserve 共同约束下，表格压缩降单列/横滚/维持字面的产品决策由用户裁；同样覆盖窄列与跨 HUD 行预算场景。
- 两路独立收口：T3a 接入不能自动改变 T3b，反向亦然；每路复查缓存命中与零每帧解析，依裁定保留降级反锁或迁移到接入验收锁。

## 四、硬约束重申（表格立项期间一条不破）

G1（L2 零直连 GL）/ G2（L1 零 MC·AWT）/ G3 扩展项（表格语法只活在 L1）/ G4（度量同源，样式表为唯一数值登记处）；§=普通字符宪法（cell 内同样）；门禁 RECORD 零豁免；死代码窗口——新接缝必须在同一大批次内获得真实消费者（Table AST 对拍面与出图测试计为消费者，口径同 M1-M6，窗口不跨批次收口）。

## 五、首版语法面（GFM tables，锁死）

✓：表头行+分隔行、`:---`/`:---:`/`---:`、行列数不齐补空、cell 内行内 markdown（复用 L1）、`\|` 转义、自动列宽、cell 按列宽软折。
✗：colspan/rowspan、HTML `<table>`、cell 内块级嵌套、表格嵌套、排序/交互（后者是产品能力不是 parser 问题）。

## 六、风险

1. **优先级面误判**：表头升级 vs setext/惰性续行是 CommonMark+GFM 最易踩错的接缝，唯一对策 = R 实测先行（语料取自 GFM 规范表格节与 TablesExtension 行为）。
2. **平台度量差**：列宽分配/软折是 C9 刚治完的误红重灾区，T2 判据必须构造形/派生形，禁绝对像素地板。
3. **共享接缝导致被动接入**：`ChatMarkdownPipeline.logicalCached:274-275` 直吃 `toLayoutLines`，`:229` 共用 L2 wrap；现有分流仅 CODE/THEMATIC_BREAK（:241-242/:302-303），没有 TABLE 消费决策。T2 一旦改变共享布局产物，玩家气泡与 `printMarkdown` 都会被动收到，HUD 还会按视觉行截断；仅写「chat3 留到 T3」不构成隔离。**T2 必过 chat3 接缝门**：每路显式维持降级或经裁定接入，真实消费路径的表格反锁/接入锁同批验收；未接入路径的反锁继续保留。
4. 运行态：本立项全部依据 headless 与代码一手核实，**未跑真机**；观感验收归 T2/T3a/T3b 既定分工（真机用户判读）。

## 七、进度表

| 步 | 内容 | 状态 |
|---|---|---|
| 立项 | 两轮收口 + 本地核查 + 本轮复审补正（闭环指针/消费者分批与接缝门/二件套性质/源码指针） | **完成** |
| T1 | 语义+契约+独立 oracle 面对拍+降级三件套+台账续账 | **完成**：完整 build 绿，4194 / 0 / 0 / 6、372 类；二件套未变，57 项 Table oracle 与历史反锁通过，javap 入账见 §八 |
| T2 | 像素 pass+交错三案+首消费者+行接缝并表格+chat3 接缝门+缓存；前置观察 home ROW clamp | **完成**：完整 build 绿，4228 / 0 / 0 / 6、377 类；平行表格 units＋块锚、页面与双度量出图、真实 chat3 降级反锁均通过；见 §九 |
| T3a | chat3 `printMarkdown` 表格与 HUD/容器待遇裁定 | 未开始 |
| T3b | chat3 玩家气泡表格与 HUD/容器待遇裁定 | 未开始 |

## 八、T1 实施记录

- **契约落点**：`MarkdownDocument.toTableModels(TextStyle)` 是本批唯一新增公共导出法；`MarkdownTableModel` 只有 `getBlockPath/getAlignments/getHeader/getRows` 四个读端，`Row.getCells` 与 `Cell.getSegments` 独立嵌套。块锚是语义块树的零基索引路径，不等同于旧行接缝的 `blockId`，不承诺跨文档编辑稳定；不导出完整 AST 或像素数据。
- **降级实现**：文档创建时识别表格；含表格时由同一块解析器的包内模式额外生成历史降级树，两个旧出口共用该树，无表格时共享原树。该暂态成本只发生在文档创建时，不在输出或绘制时重新解析块；T2 按消费者迁移时再收口，不公开能力开关。
- **参考前置**：`commonmark-ext-gfm-tables:0.21.0` 已通过独立 Gradle resolve 任务联网拉入缓存，仅 `testImplementation`。`CommonMarkTableOracle` 和 `MarkdownTableOracleParityTest` 独立核对表结构、对齐、单元格行内语义和块锚；既有 `MarkdownChat3ParityTest` 的扩展、语料、归一与报告保持原样。
- **历史反锁出处**：`table-literal-head.snapshot` 来自固定提交 `de4b53a557a4e2269f2480774d688276c434d349` 源码隔离编译，另与开工前隔离的旧字节码执行结果逐字一致。12 个样本覆盖两出口全部公开 getter（样式、链接、code/latex、块身份、链与几何），不是用新实现的禁表模式自比。fixture SHA-256：`748874a0dbfdf2b691cc174dcb7139aa324b243bb508a695c2f7767f5d62a244`。
- **验收结果（2026-09-08 本地实跑）**：`build --offline --console=plain` 成功，`:compileJava/:compileTestJava/:test` 实际执行；Python 汇总 `4194 / 0 / 0 / 6，372 类`。其中 Table oracle 57 项、历史降级锁 3 项、Model 契约锁 12 项均零失败零跳过；独立源码复核发现的测试 API、引用默认样式、公式语料和 span 裁剪问题均已修复。
- **既有语义回归**：本批完整测试重新生成 `diff=A826A2B9E7B0EB57`、`matrix=804A42FB09D74FF5`，与立项基线逐字不变；该结果仅证明既有无表格语料回归，表格语义与降级由上述独立面证明。未跑真机，T2 像素/首消费者、home ROW clamp 观察及 T3a/T3b 裁定均未开始。
- **公共面实测**：本批 `javap -public` 与反射守卫一致：Model `4=4/0/0`、Document `9=9/0/0`（原 8 方法 +1）；Row `1=1/0/0`、Cell `1=1/0/0`、Alignment `6=2/0/4` 独立计账。原五锚仍为 `20/19/9/26/0`；含本批外壳、Document 和独立嵌套账合计 95，反空跑地板 80。LayoutLine 公共 10 参构造器与 StyleTable 19 成员不变。

以下为本批编译产物的 `javap -public` 原文（新契约、Document 与两个冻结面）：

```text
Compiled from "MarkdownTableModel.java"
public final class club.heiqi.uilib.font.layout.markdown.MarkdownTableModel {
  public java.util.List<java.lang.Integer> getBlockPath();
  public java.util.List<club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Alignment> getAlignments();
  public club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Row getHeader();
  public java.util.List<club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Row> getRows();
}
Compiled from "MarkdownTableModel.java"
public final class club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Row {
  public java.util.List<club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Cell> getCells();
}
Compiled from "MarkdownTableModel.java"
public final class club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Cell {
  public java.util.List<club.heiqi.uilib.font.layout.TextSegment> getSegments();
}
Compiled from "MarkdownTableModel.java"
public final class club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Alignment extends java.lang.Enum<club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Alignment> {
  public static final club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Alignment NONE;
  public static final club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Alignment LEFT;
  public static final club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Alignment CENTER;
  public static final club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Alignment RIGHT;
  public static club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Alignment[] values();
  public static club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Alignment valueOf(java.lang.String);
}
Compiled from "MarkdownDocument.java"
public final class club.heiqi.uilib.font.layout.markdown.MarkdownDocument {
  public static club.heiqi.uilib.font.layout.markdown.MarkdownDocument parse(java.lang.String);
  public static club.heiqi.uilib.font.layout.markdown.MarkdownDocument parseSpans(java.util.List<club.heiqi.uilib.font.layout.markdown.MarkdownSpan>);
  public java.util.List<club.heiqi.uilib.font.layout.markdown.MarkdownTableModel> toTableModels(club.heiqi.uilib.font.layout.TextStyle);
  public java.lang.String getSource();
  public int getBlockCount();
  public boolean isEmpty();
  public java.util.List<club.heiqi.uilib.font.layout.TextSegment> toSegments(club.heiqi.uilib.font.layout.TextStyle);
  public java.util.List<club.heiqi.uilib.font.layout.TextSegment> toSegments(club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable, club.heiqi.uilib.font.layout.TextStyle);
  public java.util.List<club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine> toLayoutLines(club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable, club.heiqi.uilib.font.layout.TextStyle);
}
Compiled from "MarkdownLayoutLine.java"
public final class club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine {
  public static final int NO_BLOCK;
  public club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine(club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine$Kind, int, int, java.util.List<club.heiqi.uilib.font.layout.TextSegment>, int, int, int, int, int, int);
  public static club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine blank();
  public club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine withSegments(java.util.List<club.heiqi.uilib.font.layout.TextSegment>);
  public club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine withBlockContentWidthPx(int);
  public club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine withLeftInsetPx(int);
  public club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine$Kind getKind();
  public int getHeadingLevel();
  public int getQuoteLevel();
  public int getBlockId();
  public java.util.List<club.heiqi.uilib.font.layout.TextSegment> getSegments();
  public int getLeftInsetPx();
  public int getIndentStepPx();
  public int getBarWidthPx();
  public int getRuleThicknessPx();
  public int getAccentArgb();
  public int getBackgroundArgb();
  public java.util.List<club.heiqi.uilib.font.layout.TextSegment> getListMarkerChain();
  public int getBlockContentWidthPx();
  public java.lang.String toString();
}
Compiled from "MarkdownStyleTable.java"
public final class club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable {
  public club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable();
  public static club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable defaults();
  public club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable copy();
  public boolean isHeadingBold();
  public void setHeadingBold(boolean);
  public boolean isHeadingUnderline();
  public void setHeadingUnderline(boolean);
  public boolean isQuoteItalic();
  public void setQuoteItalic(boolean);
  public int getHeadingFontSizeDeltaPx(int);
  public void setHeadingFontSizeDeltaPx(int, int);
  public int getDefaultFontSizePx();
  public void setDefaultFontSizePx(int);
  public java.lang.String getBulletMarker();
  public void setBulletMarker(java.lang.String);
  public java.lang.String getThematicBreakText();
  public void setThematicBreakText(java.lang.String);
  public int getQuoteTextColor();
  public void setQuoteTextColor(int);
}
```

## 九、T2 实施记录

- **交错接缝**：采用裁定表中的③“平行表格 units＋块锚”。`MarkdownDocument.toLayoutContent(MarkdownStyleTable, TextStyle)` 返回非表格逻辑行及有序 `TableUnit`；`beforeLineIndex` 表示插入到哪一条逻辑行之前，同锚按 units 顺序。表格仍携带 T1 语义块路径，`MarkdownTableModel` 不增加像素或布局服务。①哨兵方案会给既有行消费者引入占位行语义，②替换旧返回类型的 union 方案破坏兼容；三案证据与边界样例随本批测试验收。
- **度量与样式**：L2 完整度量全部单元格，生成 intrinsic 列宽和不可拆原子的最小宽，再分配、逐 cell 换行、确定行高、定位 `PaintCommand`。最小宽先保底，剩余预算向未达到自然宽的列均分，短列封顶后把余量留给长列；宽视图不再因长说明比例过大而拆开短表头。极窄容器小于原子与边框最小宽时，保留原子并报告实际溢出宽，不缩字、不拆公式。包内样式登记经 `TableUnit` 快照传递；`MarkdownLayoutLine` 的公共构造和成员、`MarkdownStyleTable` 公共面保持冻结。
- **公式与列表几何**：单元格真实行盒复用数学布局器与注入度量，包含纯公式居中和混排上伸补偿；公式链接覆盖完整单元格视觉行框，普通文字链接保留文本框。列表直接以表格开头时，L1 仍导出独立标记事件，L2 匹配直属标记后与首表头同行，不增加一整行高度。既有 LaTeX 缓存需要未公开的度量运行时版本；本批不增加公共 API 或伪造缓存键，因此布局阶段存在额外的同源 MathBox 测量，L3 命中后无每帧重复。
- **首消费者与缓存**：`MarkdownPainter.layoutContent` 生成带总宽高的计划，供页面和 headless 共用；内部 `MarkdownPageContent` 每次挂载解析一次，缓存键包含消费实例（固定内容/样式）、实际可用宽、基础字号、度量服务及纪元。`layoutDone` 读取最终内容盒，帧信号只比较纪元；同计划跳过度量、布局、命令及节点重建。页面通过既有 scene 叶承接 BACKGROUND/SEGMENTS，并以实际计划钉总高；普通链接及公式链接沿既有非交互预览语义保留样式，LINK_REGION 由 headless 验证而不写 scene 所有的命中缓存。
- **宿主传宽与 home 观察**：`MarkdownTableHostWidthTest` 从真实宿主切页并往返变宽，画布 `360/720/1080/720` 对应页面 `292/652/824/652`、表格可用宽 `268/628/800/628`。约束来源是宿主宽上限、滚动条与 viewport/card padding，已用 Python 按源码常量验算；内容宽不由表格 intrinsic 反推。初始 home ROW 盒越界可复现，隔离观察数 `13/1/0/1`，且首次完整 build 被既有 `PlaygroundButtonRowLayoutTest` 挡住（行宽 628、子右缘 721）。本批在 `HomePage` 两种说明行中给说明叶设置既有 `flexGrow=1`：标题自然宽计入 fixedW，说明只领取扣除标题与 gap 后的剩余预算；无 scene core 改动，未开启 C10 通用算法改造。原失败测试未削弱，定向复验通过；同组画布往返观察为 `0/0/0/0`，表格传宽仍为原值。该修复只修节点盒预算，不承诺改变普通 TEXT 默认 `maxTextWidth=0` 的字形换行/截断策略。旧页在切换时 dispose，其 ROW 不在表格祖先链；表格宽在往返与重复布局中保持稳定。
- **chat3 接缝门**：`printMarkdown` 与玩家气泡均显式维持字面降级，继续消费旧 `toLayoutLines`；T1 两个旧出口的历史快照锁保留。本批另以固定 T1 提交的真实消费者结果和实时 L2 路径反锁验证 HUD/展开容器，T3a/T3b 接入尚未裁定。
- **报告迁移**：原无表格回归报告保留为 `legacy-diff.txt` / `legacy-matrix.txt`，继续核对 T1 二件套；当前 `diff.txt` / `matrix.txt` 追加 TABLE 结构与文档交错章节，其新基线以本批完整测试实测登记。
- **列表表格漏项修复**：扩面 oracle 的 `- before`／空行／缩进表格样例暴露历史列表收拢丢失空行的问题。本批表格语义模式保留列表内部空行；无管线候选直接历史解析，有候选但未成表也回退历史语义。全文含表格时，新出口的其他列表项也保留真实段落边界，旧 `toLayoutLines/toSegments` 仍来自历史树。该兼容边界以混合文档锁和独立 AST 对拍验证，不声称仅改表格所在子树。
- **词回退预算**：既有 token 换行器在词边界回退后可能无条件拼接“后缀＋不可拆公式”，超过已分配列宽。包内 `wrapCell` 复用同一 token 引擎，回退后重新检查列预算；旧 `wrap/layoutLines` 保持原行为，未裁定的聊天表格消费者不被动迁移。
- **完整验收**：最终 `build --offline --console=plain` 成功，生产编译与 `:test` 实际执行；Python 汇总 `4228 / 0 / 0 / 6，377 类`。独立 AST 对拍 61 项、模型 14 项、T1 历史降级锁 3 项、表格布局 16 项及预算反例 1 项全部通过；真实 chat3 新增 3 项反锁包含四份固定历史快照和无换行替身的 L2 路径。首次失败的 home ROW 盒预算问题已按上文修复，原测试完整通过，未用重跑碰运气或豁免掩盖。
- **扩面基线迁移**：完整主测试生成现行 `diff=6A3339AB3C81787E`、`matrix=83647E59842DD50C`，新增 T01–T18 表格结构/文档交错章节，零新增豁免；`legacy-diff=A826A2B9E7B0EB57`、`legacy-matrix=804A42FB09D74FF5` 保持 T1 字节基线。表格形状、块锚、表头/正文边界、对齐及单元格 token 进入严格判等，缺表/重排/篡改形状等负对照均能报错。
- **出图与双度量**：`build/reports/markdown-table-render/00-full-page.png` 为宽窄整页合成图，另有 `00-full-page@4x.png` 真字号放大副本。机判使用从 collector 排除背景/装饰的 glyph-only 帧，要求真实 glyph quads 非零且墨水数不小于 quads，另验几何、链接和高公式。最终源码重新编译后，以 `QZ_C9_TEST_FONT=Serif` 对页面缓存/scene/headless 再验 4 项全绿；两套 `metricProbeWidth=65.02374087439642/65.79620255364311` 从实际 profiles 读取后经 Python Decimal 验证不同。第二套出图在 `markdown-table-render-Serif/`。已目检最终默认合成图，短列完整、公式不穿框；未跑真机、未独立跑 CI。
- **公共面实测**：Document `10=10/0/0`、LayoutContent `2=2/0/0`、TableUnit `8=8/0/0`、Painter `8=8/0/0`、ContentLayout `3=3/0/0`；嵌套均无 public 构造。Model 与 Row/Cell/Alignment 延续 T1 台账，Line 20/10 参、StyleTable 19 保持冻结。所有已锚定类含 Painter 及独立嵌套合计 117，地板 100，Python 验算与反射守卫一致。以下是最终编译产物的 `javap -public` 原文：

```text
Compiled from "MarkdownDocument.java"
public final class club.heiqi.uilib.font.layout.markdown.MarkdownDocument {
  public static club.heiqi.uilib.font.layout.markdown.MarkdownDocument parse(java.lang.String);
  public static club.heiqi.uilib.font.layout.markdown.MarkdownDocument parseSpans(java.util.List<club.heiqi.uilib.font.layout.markdown.MarkdownSpan>);
  public java.util.List<club.heiqi.uilib.font.layout.markdown.MarkdownTableModel> toTableModels(club.heiqi.uilib.font.layout.TextStyle);
  public java.lang.String getSource();
  public int getBlockCount();
  public boolean isEmpty();
  public java.util.List<club.heiqi.uilib.font.layout.TextSegment> toSegments(club.heiqi.uilib.font.layout.TextStyle);
  public java.util.List<club.heiqi.uilib.font.layout.TextSegment> toSegments(club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable, club.heiqi.uilib.font.layout.TextStyle);
  public java.util.List<club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine> toLayoutLines(club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable, club.heiqi.uilib.font.layout.TextStyle);
  public club.heiqi.uilib.font.layout.markdown.MarkdownDocument$LayoutContent toLayoutContent(club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable, club.heiqi.uilib.font.layout.TextStyle);
}
Compiled from "MarkdownDocument.java"
public final class club.heiqi.uilib.font.layout.markdown.MarkdownDocument$LayoutContent {
  public java.util.List<club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine> getLines();
  public java.util.List<club.heiqi.uilib.font.layout.markdown.MarkdownDocument$TableUnit> getTables();
}
Compiled from "MarkdownDocument.java"
public final class club.heiqi.uilib.font.layout.markdown.MarkdownDocument$TableUnit {
  public int getBeforeLineIndex();
  public club.heiqi.uilib.font.layout.markdown.MarkdownTableModel getModel();
  public club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine getContext();
  public int getPaddingXPx();
  public int getPaddingYPx();
  public int getBorderPx();
  public int getBorderArgb();
  public int getHeaderArgb();
}
Compiled from "MarkdownTableModel.java"
public final class club.heiqi.uilib.font.layout.markdown.MarkdownTableModel {
  public java.util.List<java.lang.Integer> getBlockPath();
  public java.util.List<club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Alignment> getAlignments();
  public club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Row getHeader();
  public java.util.List<club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Row> getRows();
}
Compiled from "MarkdownTableModel.java"
public final class club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Row {
  public java.util.List<club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Cell> getCells();
}
Compiled from "MarkdownTableModel.java"
public final class club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Cell {
  public java.util.List<club.heiqi.uilib.font.layout.TextSegment> getSegments();
}
Compiled from "MarkdownTableModel.java"
public final class club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Alignment extends java.lang.Enum<club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Alignment> {
  public static final club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Alignment NONE;
  public static final club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Alignment LEFT;
  public static final club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Alignment CENTER;
  public static final club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Alignment RIGHT;
  public static club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Alignment[] values();
  public static club.heiqi.uilib.font.layout.markdown.MarkdownTableModel$Alignment valueOf(java.lang.String);
}
Compiled from "MarkdownLayoutLine.java"
public final class club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine {
  public static final int NO_BLOCK;
  public club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine(club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine$Kind, int, int, java.util.List<club.heiqi.uilib.font.layout.TextSegment>, int, int, int, int, int, int);
  public static club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine blank();
  public club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine withSegments(java.util.List<club.heiqi.uilib.font.layout.TextSegment>);
  public club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine withBlockContentWidthPx(int);
  public club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine withLeftInsetPx(int);
  public club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine$Kind getKind();
  public int getHeadingLevel();
  public int getQuoteLevel();
  public int getBlockId();
  public java.util.List<club.heiqi.uilib.font.layout.TextSegment> getSegments();
  public int getLeftInsetPx();
  public int getIndentStepPx();
  public int getBarWidthPx();
  public int getRuleThicknessPx();
  public int getAccentArgb();
  public int getBackgroundArgb();
  public java.util.List<club.heiqi.uilib.font.layout.TextSegment> getListMarkerChain();
  public int getBlockContentWidthPx();
  public java.lang.String toString();
}
Compiled from "MarkdownStyleTable.java"
public final class club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable {
  public club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable();
  public static club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable defaults();
  public club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable copy();
  public boolean isHeadingBold();
  public void setHeadingBold(boolean);
  public boolean isHeadingUnderline();
  public void setHeadingUnderline(boolean);
  public boolean isQuoteItalic();
  public void setQuoteItalic(boolean);
  public int getHeadingFontSizeDeltaPx(int);
  public void setHeadingFontSizeDeltaPx(int, int);
  public int getDefaultFontSizePx();
  public void setDefaultFontSizePx(int);
  public java.lang.String getBulletMarker();
  public void setBulletMarker(java.lang.String);
  public java.lang.String getThematicBreakText();
  public void setThematicBreakText(java.lang.String);
  public int getQuoteTextColor();
  public void setQuoteTextColor(int);
}
Compiled from "MarkdownPainter.java"
public final class club.heiqi.uilib.ui.markdown.MarkdownPainter {
  public static club.heiqi.uilib.ui.markdown.MarkdownPainter$ContentLayout layoutContent(club.heiqi.uilib.font.layout.markdown.MarkdownDocument$LayoutContent, club.heiqi.uilib.font.layout.TextLayoutService, int, int);
  public static java.util.List<java.util.List<club.heiqi.uilib.font.layout.TextSegment>> wrapLines(java.util.List<club.heiqi.uilib.font.layout.TextSegment>, club.heiqi.uilib.font.layout.TextLayoutService, int, int);
  public static java.util.List<club.heiqi.uilib.ui.scene.paint.PaintCommand> toPaintCommands(java.util.List<club.heiqi.uilib.font.layout.TextSegment>, club.heiqi.uilib.font.layout.TextLayoutService, int, int);
  public static int lineWidthPx(java.util.List<club.heiqi.uilib.font.layout.TextSegment>, club.heiqi.uilib.font.layout.TextLayoutService, int);
  public static int lineHeightPx(java.util.List<club.heiqi.uilib.font.layout.TextSegment>, club.heiqi.uilib.font.layout.TextLayoutService, int);
  public static int measureHeight(java.util.List<club.heiqi.uilib.font.layout.TextSegment>, club.heiqi.uilib.font.layout.TextLayoutService, int, int);
  public static java.util.List<club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine> wrapLayoutLines(java.util.List<club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine>, club.heiqi.uilib.font.layout.TextLayoutService, int, int);
  public static java.util.List<club.heiqi.uilib.ui.scene.paint.PaintCommand> toLayoutPaintCommands(java.util.List<club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine>, club.heiqi.uilib.font.layout.TextLayoutService, int, int);
}
Compiled from "MarkdownPainter.java"
public final class club.heiqi.uilib.ui.markdown.MarkdownPainter$ContentLayout {
  public java.util.List<club.heiqi.uilib.ui.scene.paint.PaintCommand> getCommands();
  public int getHeightPx();
  public int getWidthPx();
}
```
