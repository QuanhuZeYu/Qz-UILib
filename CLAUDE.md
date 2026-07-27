<!--
本文件内容平移自 AGENTS.md，二者应保持一致。
协作规范以 AGENTS.md 为权威来源；如需修改规范，请先修改 AGENTS.md（须经用户确认），再同步回本文件。
-->

# 协作规范

回答内容优先使用中文；完整规则以 `AGENTS.md` 为权威。

## 架构与实现
- 架构改动先读 `NORTH_STAR.md`，scene 改动再读 `docs/设定值层/硬约束总目录.md`，逐条守 I1-I13、R1-R13、布局同步契约与 paint/node 铁律。
- 代码标识符遵循 Java 常规命名；类与重要方法使用简洁中文注释/Javadoc；接近千行的文件先评估职责拆分。
- 新建或评审 scene 测试先读 `docs/传感层/测试体系约定.md`。

## Git 与文档
- 在非主分支实施，每次改动提交；标题格式 `[English]: 中文标题`，正文使用中文 Markdown。
- 合并使用 `git merge --no-ff`。修改 `AGENTS.md` 必须经用户确认。
- 开始前读 `docs/README.md`；业务状态写 `docs/反馈层/交接.md`，错误与决策写回对应权威文档，不留流水账。

## 默认 build 与持久任务
- 使用 OpenCode 内置默认 `build`，不维护仓内自定义 agent。简单问答不建任务；跨轮设计在 `.opencode/tasks/` 创建 `DRAFT`，范围和验收确认后转 `READY`，开始写盘时转 `ACTIVE`。
- 任务索引为 `.opencode/tasks/INDEX.md`，格式与状态机见 `TASK-BRIEF.md` 和 `PERSISTENT-WORKFLOW.md`。聊天只是易失缓存，影响后续动作的事实必须在阶段边界落盘。
- 同一 `ACTIVE` 任务只有一个所有者；内置 `build` 只改写集，静态核对、对照验收与 diff 自审、提交并写回结果。写集不足或关键取舍未决时零写返回 `INCOMPLETE`。
- 产品方向、公共 API、不可逆 Git/发布/生产操作、密钥和 agent 配置事项由用户决定。

## 工具、环境与验证
- 本机环境归用户、CI 环境归 runner；agent 只逐项只读核验，禁止赋值、持久修复、全量枚举或用 Gradle/JDK home 参数绕过。
- agent 不在本机执行 Gradle、编译、构建、测试、运行态或 verify 命令。必需实证交 CI 或用户；缺少结果时如实返回 `INCOMPLETE`。
- 文件读写搜索优先使用专用工具；终端命令固定经 Python runner 的参数列表执行，不用 Python 绕过文件工具。
- 禁止直接 PowerShell/CMD/Bash、复杂 `python -c`、命令字符串拼接或 `shell=True`；外部程序须显式 cwd、超时与返回码。
- 文档纪律、scene 结构与源码语义由权威导航、测试和对照验收的 diff 自审守卫；现行 CI 只额外机械检查仓库与 workflow 的零 PowerShell 约束。
