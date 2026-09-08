# chat3 表格历史反锁

基准提交：`6c7637e512d7d2ce5a641b4d819580f730c23357`。捕获时 T3a/T3b 均尚未获接入裁定。T3a 本轮获用户裁定接入二维滚动视口；T3b 玩家气泡继续字面。

捕获步骤：通过参数数组执行 `git archive` 导出上述固定提交到工作站 temp 隔离目录；独立 javac 编译该提交的 `font/layout/markdown`、`ui/markdown`、`internal/chat3`、`api/chat` 全部主源，再编译 `ChatMessageListTest` 的捕获版夹具。其余类型和依赖使用既有 build/classes 与缓存 jar。JUnit 分别执行两个消费者的 HUD、container，捕获消息 SceneNode 树，不从当前 L1/L2 输出计算期望。四份快照冻结后删除捕获语句，常驻测试无重录开关。

测试从真实 `ChatAccess.printMarkdown(String)` 经现有 sink 接入 controller.history；玩家从 `chat.type.text` 的组件参数接入。两路均经过真实 composer、pipeline 和 ChatMessageList。HUD 通过 controller.buildContent，容器复用真实 Style.container 挂载入口。只在历史快照用既有 FIXED_WRAP/FIXED_MEASURER 固定字符度量；另一个测试不注换行替身，经过真实 MarkdownPainter.wrapLayoutLines，检查表头、分隔符字面存活、窄列实际软折、HUD 八行截断和完整容器尾文。

快照是消费者产出的节点盒属性与段序列，不是最终 GPU 像素或鼠标打开容器的运行态证据。节点行记录字段依次为 depth、background、左右 padding、maxWidth、preferredWidth、preferredHeight、fontSize、maxLines、ellipsis；段记录包含 text、color、fontType、fontSize、link、underline、strikethrough、italic、latex。

文件名两个 boolean 依次为 explicit printMarkdown、HUD；false 分别表示玩家气泡、容器。UTF-8/LF 字节 SHA256：

| 文件 | SHA256 |
| --- | --- |
| table-literal-true_true.snapshot | 9e533da55e5ba5ffed4769d362d11335b720d49c57706ef74606fc31f46b6fdc |
| table-literal-true_false.snapshot | 8323f443d3a2f7af19e6bcd0aac1dfcaee178f93fea7a8d8655bc3ca62e182ee |
| table-literal-false_true.snapshot | bec9af7d63bce923210e5bec0440f39709bdf8d28c2dccff1f28a692e33e3cfa |
| table-literal-false_false.snapshot | 3bc34e6fcd510faa7bae196718286e660072d6b4f822794febbd3797cdfafc4a |

历史捕获阶段：全链三项测试通过，只做独立 javac/JUnit 定向执行，没有完整 build、commit 或真机运行。这是捕获过程记录；T2 最终集成 build 与交付状态由父协调另行验证登记。

2026-09-08 T2 协作树复核：独立 javac 编译上述四包当时最新主源后，两个历史快照测试（含 TABLE 身份正锁）与真实 L2 派生形测试全部通过；四份快照与隔离历史捕获逐字节一致。

T3a 锁迁移：

- 用户裁定显式 `printMarkdown` 含表文档在 HUD 和展开聊天中都完整保留在尺寸受限的视口内。关闭聊天时只能看裁剪后的片段；打开聊天、释放鼠标后可纵滚长表，超过最小内容宽时可横滚。非表格显式 markdown 不变，玩家气泡仍字面。
- `table-literal-true_true.snapshot`、`table-literal-true_false.snapshot` 不再与当前显式输出比较，只保留不可重录的历史证据；`explicitTableHistoricalEvidenceRemainsFrozen` 继续逐份校验上述 SHA256。
- `table-literal-false_true.snapshot`、`table-literal-false_false.snapshot` 仍是当前玩家输出的逐字节锁，`tablePlayerBubbleKeepsHistoricalLiteralHudAndContainer` 与 `tablePlayerBubbleKeepsLiteralOutputThroughRealL2` 继续验证 TABLE 正识别、字面分隔符、真实窄列软折、HUD 截断及容器完整尾文。
- 显式通道新验收归 `ChatMarkdownTableConsumerTest`：真实 ChatAccess sink → history → composer → ChatMessageList → scene layout/paint/replay；不注入 markdown wrap 替身。通过 replay 的完整段流与独立 clip 栈分别判断“保留内容”及“当前可见内容”，避免用表头解析成功代替二维消费，或用尾文仍在树上代替有效裁剪。
- 新消费者验收涵盖 L1 TABLE 正识别、二维列位置、表格分隔语法消失、跨预算 HUD 真裁剪不删尾、展开内层滚至尾文、宽窄软折、超最小列宽横向 slider 真鼠标拖动、内层移动阻止历史冒泡及边缘交还历史、非表显式旧路、HUD/展开偏移隔离。展开使用真实 `ChatContainer`；仅以测试根回调替代会启动 LWJGL 的 `ChatInputSurface` 平台壳，并校验生产根 SCROLL 接线未漂移。
- 高公式链接测试以独立 L2 `LINK_REGION` 为同度量几何期望，经真实鼠标 CLICK 验证普通前行公式可见顶端、部分滚动后的 clip 内外差异、普通尾行公式在最大滚动位置的完整边界和链接投递。链接 hover 另验两条不同 URL 之间连续 MOVE/flush、B 同点重复 MOVE，以及旧显式非表行与新表格 region 双向交接；只读现有 tooltip URL Signal，不观察内部 owner 标志。
- headless 拼图输出为 `build/reports/chat-markdown-table-consumer/scene-clip-scroll.png`：同一真实 scene replay 的完整命令（示意关闭 clip）、HUD 开启 clip、展开滚至尾部三栏。JDK Java2D 仅作字形替身；布局位置、表格背景、视口和滚动坐标均取真实 scene 命令。它是几何/裁剪证据，不是原版 GUI、GL 真机截图或最终字形像素对拍。

T3a 定向验证：工作站 temp Python 以参数数组调用独立 javac/JUnit，编译上述四包当前主源与消费者测试到隔离输出目录；消费者 11 项及历史迁移 3 项通过，四份 snapshot 未改写。完整 build 由父协调另行执行；以上结果不代表真机运行已经通过。
