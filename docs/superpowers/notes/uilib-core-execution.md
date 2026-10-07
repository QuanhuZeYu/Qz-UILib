# 核心重构执行记录

- worktree：D:/Code/MC/Qz-UILib/.worktrees/uilib-core，分支 refactor/uilib-core，起点12af6b23，状态增量49f1b91e。主仓4.0协作者未提交文档保持不动。
- 人工测试偏好：每次交付少量容易操作的检查，短步骤和可观察预期；本轮只测打开/添加、正文滚动、模态隔离，其他项后续分批。用户已授权部署到指定Prism实例。用户于2026-10-08再次明确：以后不得由agent直接启动实例（包括启动器命令与脚本）；实机验证给出短操作步骤、观察点，由用户启动并按需提供日志或截图。人工包用独立manualTestJar附加入口（真实实例无需JVM参数），发布包继续排除devtools。
- 用户授权：核心实施、破坏式更新、可5.0.0；无平台自动化优先，尽量不写单元测试；MC runClient/runServer 依AGENTS第15行仍需明确授权。
- 基线0d2720d1为人工清空源码，旧规格worktree中的源码副本不在Git；从归零基线建设，不整树恢复。计划旧路径仅作职责证据。
- 已实现独立ReactiveDomain/Signal/Computed/Effect/Owner与呈现Observation；节点UUID、attach/detach/move/destroy、逻辑repeat、脏绑定；自然/固定/有界填充、滚动/裁剪；统一几何命中、点击/滚轮/模态；不可变TextLayout/TextStyle/PaintPlan；Measure/Arrange/Paint自定义控件端口及声明测量依赖。
- 核心直启集成入口coreLoopScenario classpath只main/test输出，场景确认MC类不可加载。javac --release8另可独立执行；不新增JUnit单元测试。完整build最新52s通过，含场景/checkstyle/产物排除/类路径隔离。
- 归零构建故障：残留usesMixins/EarlyMixins已对齐；JUnit无用例发现改为直启场景check门禁；spotlessApply不是本仓任务，已停止使用，不影响后续build通过。
- 独立只读审查3a76d7a0-4207-4406-a116-1a70ecc4374e发现repeat重挂载丢UUID、实际宽度自然高度不符、混合min/max留余、余数突破max、paint-only全测量。全部修复并重跑原探针；repeat同对象/同UUID；列25x20/父20；行50x20/父20；水位80+20=100；余数1,1,0；paint-only额外度量0。真实集成锁定反例。审查另跑seed20261007共2000组有界分配无失败。
- 同轮补充独立复核已通过：control三阶段写Signal均被拒绝且来源保持0，最新集成场景通过；直接GL矩形静态符合合同，撤回Tessellator差距。最新完整build为31s通过。开发命令/qzcore已编译准备，面板加列表、标题、字号、模态和滚动；开发工具整包不入产物。原版字体是开发比较边界，不代表正式字体系统。Gui.drawRect已替换自有shader/VAO/VBO矩形，不借道Tessellator。真实MC/GL恢复/字体效果未验收。
- 当前交付范围是首个核心软件切片及宿主验证准备，不是全库5.0发布：标准编辑/IME/focus/capture/portal与全部控件、百分比策略、字体/DSL/Markdown/数学/图像、配置/网络/聊天/HUD后续继续，不能因切片缺少细则而静默宣称已替代旧产品能力。数学深入保持暂停。
- 本次功能无对应issue，提交不伪造编号；无push/tag/release或自动下游升级。
- 最新字体小步交付：b8c59c75，仅3个宿主字体文件新增systemFontFiles配置；两个测试实例（GTNH2.9.0b3、1.7.10）均已备份并部署正式130+b8c59c751e，首选C:/Windows/Fonts/msyh.ttc。GTNH此前实际菜单已确认；1.7.10仅配置/包hash及实际8u504字体CPUprobe已确认，等待用户启动观察，未由agent启动。证据build/system-font-review/。
- 用户要求逐步实现供代码审阅：下一核心生产增量仅指针手势所有权与取消（按下不激活、合法释放激活、第二按钮吞尾、owner失活取消），复用现有scene/信号/几何/handler，不并入focus/导航/IME；目前只读调查完成，尚未实施。
