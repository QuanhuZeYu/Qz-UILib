# 子代理调度纪律

## 1. 角色

主 agent 负责澄清目标、建立任务单、派发、抽检与交付；explorer/librarian/oracle/designer 提供只读专业意见；fixer 按写集实施并提交；reviewer 独立复审；git agent 处理集中 Git 操作。写盘 agent 串行，只读工作可并行。

## 2. 活动任务单

非平凡任务统一使用被 Git 忽略的 `.opencode/task.md`，格式见 [`TASK-BRIEF.md`](TASK-BRIEF.md)。任务单必须包含目标、非目标、写集、已验证事实、动作、验收、验证、结果，可选风险；验收和风险分别使用 `A?`、`R?`。

主 agent 只传任务单绝对路径和一句执行指令。fixer 只改写集；写集不足时零写返回 `INCOMPLETE` 并说明缺少路径。

## 3. 实施与复审

1. 主 agent 核实现状并把可执行结论写入任务单。
2. fixer 实施获授权范围，执行无网络静态验证，按 Git 规范提交并填写结果。
3. reviewer 读取同一任务单与 diff，核对范围、验证真实性、I1-I13、R1-R13 与 Scene 规则，按 P0/P1/P2 中文输出。
4. 主 agent 抽检关键证据。P0/P1 进入更窄纠偏，P2 仅记录。

agent 不在本机执行 Gradle、编译、构建、测试、运行态或 verify。任务要求这些实证时交 CI 或用户；尚无结果必须返回 `INCOMPLETE`，静态检查不得冒充通过。

## 4. 纠偏

任何 Task 返回后旧 `task_id` 立即失效。纠偏、重试或继续工作时，主 agent 核对 Git 与证据，覆盖任务单为更窄剩余范围，并创建全新 task。发现产品取舍、公共 API、宪章偏离、不可逆操作、发布、merge、push、密钥或权限问题时停止并请用户拍板。

## 5. 工具与边界

- 本机环境归用户、CI 环境归 runner；只读核验，禁止赋值、持久修复、全量枚举或 Gradle/JDK home 参数绕过。
- PowerShell 只使用 `pwsh` 7+；文件读写搜索优先专用工具，shell 仅用于 git/包管理等原生命令。
- 单次工具调用不超过 300 秒；长检索拆分，长构建/测试交 CI 或用户。
- 提交标题 `[English]: 中文标题`，正文使用中文 Markdown。
- docs 改动按权威导航人工核对并独立 review。

跨会话工作记忆见 [`SESSION-HANDOFF.md`](SESSION-HANDOFF.md)。
