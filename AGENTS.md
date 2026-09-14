# Qz-UILib 协作规范

回答、审查结论和交付说明优先使用中文；代码标识符、命令、路径、协议字段及行业术语保留原文。

## 项目边界

- Qz-UILib 提供通用 UI、scene、输入、渲染与宿主适配能力，不依赖 Qz-Miner 或其他下游业务仓；开始改动前先读本文件，业务现状以实时 Git 与代码（含其注释）为准，跨模块结论见 `docs/反馈层/errors/`。

## 设计取向与主权

- **对齐目标 = 现代化主流引擎**：UILib 对齐现代化主流引擎的行为语义，不对齐 MC 的「小众」独立格式；markdown 解析/渲染以主流规范（CommonMark 等主流引擎）为唯一对齐基准，MC 特有格式（§ 颜色码、聊天旧行级规则等）不得成为解析核心的语义依据，也不得在 markdown 的输入通道上被解释。
- **§ 在 markdown 内是普通字符、原样显示、零处理**：解析层不为它开任何特例——行首 § 因此吃掉块标记，行内 code、围栏代码与 latex 原子内同样字面。
- **玩家名称走原版解析、发送内容走 markdown，两者不混合**；markdown 输入一律取 unformatted 源，`getFormattedText()` 不作输入。三条输入通道（玩家消息内容 / 其余原版消息 / 显式 markdown 递交）的现行口径与实现要点写在最近的代码处：`internal/chat3/viewmodel/StructuredChatReader`、`MessageGrouper` 类注释与 `api/chat/ChatAccess#printMarkdown` 注释；接入方视角见 `docs/使用文档/03-宿主集成/Minecraft界面入口.md`「聊天 markdown 递交」。
- **中心思想**：数据层以尽可能小的范围计算变化，渲染层以尽可能小的代价把变化刷上屏；两者通过平台无关、不可变的绘制计划协作。数据流两条链在 state 处汇合——平台类型止于适配边界，GL 调用止于 replay/backend。
- **增量性能**：变化从最低必要层起算（layout / paint / geometry / composite 按影响选择）；缓存的价值是跳过未变化工作，必须有明确失效来源，不得以陈旧画面换命中率——**新增缓存必须答得出「让哪一层跳过什么重算」，答不上来就不加**；动画优先使用不触发布局的属性，性能不足先测重算起点与实际热点，不为假设中的负载增加框架。
- **投放与输入主权：本文件不重述**。现行母本是 `docs/开发者文档/规格文档/UI投影宿主语义.md`（content 不识宿主、同一 content 多次投放逐 occurrence 隔离、单一 composition owner 采集、claim 只读且只有胜者 dispatch、handler 只发布 semantic intent）；该文档明示其 Input Scope 条款「不随实现删除」，改输入仲裁以它为唯一真相。
- **约束要在引用处自证，失效引用就地修正**：约束的含义必须能从它出现的地方直接读到（或经该处给出的定义指针查到）；碰到已删母本、历史编号或作废指针，当场按现状改写为自述语义并说明仍有效的约束——历史编号、已删母本与历史记录不充当规范，判断一条红线是否有效看它在所在处能否自证，而不是看它挂在哪个编号下；同形的现行编号不得误伤（`R1-R13` 控件契约红线、`L1/L2` 分层名、`GL\d+` GL 常量）。

## 高影响安全边界

- scene core 保持平台无关：平台输入只经适配边界进入，core 不直接依赖 LWJGL、Minecraft 或 Forge 类型；数据/paint 层不直接调用 GL。
- layout、paint、replay、裁剪和输入共享同一 logical px 坐标事实；Minecraft GUI Scale 不得混入内部闭环，缩放只在 host 边界成对转换。
- UI 变化以 state/signal 驱动；输入 handler 不直接改节点属性或树结构。焦点、capture 等命令只通过路由器受控入口改变权威交互状态。
- paint/replay 之间传递自包含、不可变的绘制计划；replay/backend 必须恢复其触碰的 GL 状态，不能污染 Minecraft 或其他 mod 的后续渲染。
- **绘制禁令（用户硬性要求）：UILib 严禁使用原版包装类（Tessellator 等）。** 绘制一律走直接 GL（GL11/GL14 立即模式与状态调用）或 UILib 自有渲染管线；原版包装类在 Angelica/lwjgl3ify 下行为不可控（真机实证：全局 Tessellator 的 TRIANGLE_FAN 不可见、聊天卡片背景整块丢失）。
- 公共 API、配置持久数据、网络协议、版本兼容承诺或上述边界需要改变时，先说明明确后果并取得用户确认。

## 优先复用 UILib 能力（最高优先级）

- 实现任何功能前，**必须优先评估并复用 UILib 既有能力**：scene 树与布局引擎（SceneNode/布局/SHRINK/AlignSelf）、Signal/Computed/rt.forEach 状态驱动、PaintCommand 管线与 SEGMENTS 文本渲染、通用控件（SceneTextInput/SceneScrollbar/SceneSlider/SceneAutocomplete 等）、字体度量（TextLayoutService）、动画（Animator/DisplayStateMachine）等。
- **「与原版对齐/还原原版」指使用体感对齐，不是实现方式对齐**：原版（GuiNewChat/GuiChat/GuiTextField 等）行为只作为行为规格参照，禁止复制、移植或直连调用其内部逻辑：调研（含 Forge 补丁、反编译源码）只为提取「体感规格」，实现必须落在 UILib 自有抽象内，不得顺手引入原版调用路径。
- 仅当 UILib 确实缺失某能力且无法低成本扩展时，才允许在业务层做最小补充（附缺失理由与取舍说明），不得绕过 UILib 直连原版实现；验收时自查，交付中出现对原版 GUI/渲染类的直接依赖或调用即视为违规，须重构回 UILib 能力。

## 工作方式与验证

- 保留并避开用户或协作者的既有改动，只做目标所需的最小修改。
- 文件读取、搜索和编辑优先使用专用工具；其他终端命令先写成 Python 脚本文件，统一放在工作站根 `temp\` 目录，在本仓工作目录下以 `python D:\Code\MC\Qz工作站\temp\<脚本> <args...>` 执行；禁止命令行内联长命令、复杂管道、多层引号或内嵌脚本，禁止命令字符串拼接和 `shell=True`。
- 代码改动通过完整 build 后才作为可交付增量自动提交（本地 commit）；失败时继续定位、修复并重跑。纯文档改动可跳过 build；未执行的测试、制品或运行态不得写成通过。
- 交付前检查相关 diff、status 与近期提交风格，只暂存任务文件并自动创建本地 commit。无冲突、保留双方完整历史与内容且不删除来源分支的本地纯增量 merge 可自动执行；push、tag 与 release 仍须用户明确要求。
- 修改历史、删除分支、丢弃提交或改动、以及会让现有内容从最终工作目录消失的 Git 操作必须先说明影响并取得用户确认；纯增量 merge 出现冲突或无法证明完整保留时也必须停止询问。
- `runClient*`、`runServer*` 与发布仍交 CI 或用户；修改本文件、核心架构边界、发布策略或公共 API、依赖/版本、生产操作、密钥与认证前取得用户确认，均由用户决定。

## 踩坑

- 重要踩坑**优先写在缺陷所在的源码或测试注释里**（与代码同处、随改随读）；仅当结论跨模块、需要读代码之外的上下文才看得懂时，才落 `docs/反馈层/errors/`。存量记录不批量搬，下次碰到该处代码时顺手搬。
