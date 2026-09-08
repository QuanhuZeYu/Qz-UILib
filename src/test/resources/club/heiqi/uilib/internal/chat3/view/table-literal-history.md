# chat3 表格历史反锁

基准提交：`6c7637e512d7d2ce5a641b4d819580f730c23357`。T3a/T3b 尚未获接入裁定。

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

2026-09-08 当前协作树复核：独立 javac 编译上述四包最新主源后，两个历史快照测试（含 TABLE 身份正锁）与真实 L2 派生形测试全部通过；四份快照与隔离历史捕获逐字节一致。
