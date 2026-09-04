# 规划：通用 Markdown 解析渲染器（B 案）

**状态：** 待你裁 4 项后开工（见 §五）。方向已定（用户 2026-09-04）：**先建独立通用渲染器 → 删 chat3 现有简易实现 → 接线**。
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
两层的接缝只有 `List<TextSegment>` + 块级盒模型 —— 这也是「唯一真相」的落点。

### L1 语法面（块级 = 本次新增；行内 = 复活既有裁定）

- 块级：ATX 标题 `#..######`、围栏代码 ``` / ~~~、引用块 `>`（可嵌套）、无序/有序列表
  （含缩进续行）、分隔线 `---`/`***`、段落与空行、硬换行（行尾两空格 / 反斜杠）。
- 行内：`**`/`__`、`*`/`_`、`***`、`~~`、`` ` ``、`$`/`$$`、`[text](url)`、反斜杠转义 ——
  **语义照抄 §L1 既有裁定，不重开**。
- 刻意不支持（写进文档，别默默失败）：表格、任务列表、HTML 内联、脚注、图片 `![alt](url)`。
  图片需要网络与缓存，`ui/image/DocumentRemoteImageCache` 是既有面，但它是「文档远程图片」用途，
  接不接进 markdown 属独立裁定（本文列为 D4）。

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
- **G4 度量同源**：块级排版不得自带一套字号/行高常量。chat3 现有一批定值
  （`INPUT_AREA_INSET_PX` 已收口为单一来源；`getCodeFontSizePx()` 等仍在 `ChatMarkdownSettings`）。
  B 的默认样式表放哪属裁定 D3。
- 每次 M 步收尾跑 `./gradlew.bat build --offline --console=plain`，绿了才提交；当前基线
  **3855 / 0 / 0 / 2，351 类**。

## 五、要你裁的四项（D1 不开工就没法写第一行）

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
