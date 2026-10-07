# 核心重构执行记录

- 代码 worktree：D:/Code/MC/Qz-UILib/.worktrees/uilib-core，分支 refactor/uilib-core，起点 12af6b23。
- 用户已授权核心设计实施、破坏式更新、可达 5.0.0；无平台自动化优先，尽量不用单元测试；MC 启动仍按宿主边界衔接。
- 重要基线：0d2720d1 为人工清空生产源码的归零提交。规格 worktree 中旧源码副本不在 Git。不得从旧 4.0 整批恢复；归零重建，旧类仅作能力证据。
- 裁决：计划原列路径视为职责落点，不是必须迁移的现存文件；从归零基线建立新的 reactive/scene/host 核心，实际接口在唯一 spec 内维护。
- 已实现 ReactiveDomain、Signal、Computed、Effect、Owner 等纯 Java 状态域；真实集成 main 覆盖直接读取、多级派生、分支退订、释放、独立执行器和非递归 Effect。javac --release 8 编译与直接 java 场景通过。
- Gradle 首次失败：归零后残留 usesMixins=true 和不存在的 EarlyMixins。已关闭当前不存在的注入能力，真实 MC 接入需要时再配置。
- build/coreLoopScenario 已实际通过；完整 build 首轮有 Test 无 JUnit 发现及 wildcard import 检查失败，已修正，待重跑。测试入口直启，不新增 JUnit 单元测试；check 强制执行 coreLoopScenario，类路径仅 main/test 输出，不含平台依赖。
- 完整 build 已实际成功（34s），coreLoopScenario、checkstyle、产物检查及类路径隔离均执行通过；首次状态增量提交后继续生命周期。当前没有对应 issue，该功能来自本次核心重建请求，不伪造编号。后续：节点声明/挂载期作用域、布局/命中/绘制闭环，最后独立分支审查。
