# Agent 执行提示词：lwjgl3ify 解耦质量优化修复

## 任务概述

你需要执行 `refactor/decouple-lwjgl3ify` 分支的代码质量优化修复工作。本任务基于严格的代码审查报告，分两批次渐进式完成。

## 前置条件确认

在开始前，请确认：

1. **当前分支**：`refactor/decouple-lwjgl3ify`
2. **工作区状态**：执行 `git status` 确认工作区干净或只有 `docs/记忆/当前态/交接记录.md` 未提交修改
3. **编译环境**：Windows PowerShell，Gradle 环境变量已设置
4. **执行计划**：已读取 `docs/开发者文档/reviews/REVIEW-20260613-lwjgl3ify-decouple-plan.md`

## 你的任务：执行批次 1（核心修复）

### 任务清单

按以下顺序依次执行：

#### ✅ 任务 1.1：测试基础设施确认（15 分钟）
- 目标：确认测试编译问题为既有问题
- 详见执行计划文档 "任务 1.1" 章节
- **关键**：如果确认是既有问题，创建立项文档后继续后续任务

#### ✅ 任务 1.2：M1 修复 - 日志噪音控制（30 分钟）
- 文件：`src/main/java/club/heiqi/uilib/ui/input/LwjglInputRuntime.java`
- 修改内容：将全局 `AtomicBoolean` 改为 `ConcurrentHashMap` 独立记录
- 详见执行计划 "任务 1.2" 章节
- **验证**：`./gradlew compileJava` 必须通过

#### ✅ 任务 1.3：M2 修复 - 光标异常处理优化（45 分钟）
- 文件：`src/main/java/club/heiqi/uilib/ui/host/SystemDocumentCursorHost.java`
- 修改内容：区分初始化失败与运行时失败
- 详见执行计划 "任务 1.3" 章节
- **注意**：这是最复杂的修改，务必仔细对照执行计划

#### ✅ 任务 1.4：M3 文档补充（10 分钟）
- 文件：`docs/记忆/决策/DECISION-20260612-lwjgl3ify-input-backend.md`
- 修改内容：在第 37 行后插入 REPEATED 语义说明
- 详见执行计划 "任务 1.4" 章节

#### ✅ 任务 1.5：M4 优化 - 时间戳读取（20 分钟）
- 文件：`src/main/java/club/heiqi/uilib/ui/input/Lwjgl3ifyInputBackend.java`
- 修改内容：新增 `readEventTimestampNanos()` 和 `readLongField()` 方法
- 详见执行计划 "任务 1.5" 章节

#### ✅ 任务 1.6：提交与验证（20 分钟）
- 运行验证清单（见执行计划）
- 使用执行计划提供的提交信息模板
- 更新交接记录

### 执行策略

**严格按顺序执行**：
1. 每个任务开始前，使用 `todowrite` 标记为 `in_progress`
2. 每个任务完成后，使用 `todowrite` 标记为 `completed`
3. 修改代码前，先用 `read` 工具读取目标文件确认当前内容
4. 修改代码后，立即运行 `./gradlew compileJava` 验证编译
5. 如果遇到问题，停止并报告，不要继续下一任务
6. 所有任务完成后，运行完整验证清单

### 关键注意事项

**代码规范遵守**：
- 所有修改必须符合 `AGENTS.md` 编码规范
- 中文注释，英文命名
- 4 个空格缩进
- 左大括号不换行

**验证要求**：
- 每次修改后必须运行 `./gradlew compileJava`
- 最终提交前必须运行 `git diff --check`
- 不能引入新的编译警告

**提交规范**：
- 使用执行计划中提供的提交信息模板
- 提交标题格式：`[Refactor]: 标题`
- 提交正文使用 Markdown，包含修改内容、验证结果、关联信息

## 执行命令速查

```powershell
# 环境变量设置（每次运行 Gradle 前）
$env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"

# 编译验证
./gradlew.bat --no-configuration-cache compileJava

# Git 检查
git diff --check
git status --short

# 清理构建（如需要）
./gradlew.bat clean
```

## 成功标准

批次 1 完成时，应满足：

- [ ] 所有 6 个任务已完成
- [ ] `git diff --check` 无输出
- [ ] `./gradlew compileJava` 成功
- [ ] 代码修改已提交（1 个提交）
- [ ] 交接记录已更新
- [ ] TodoWrite 显示所有任务 `completed`

## 如果遇到问题

**测试编译失败（任务 1.1）**：
- 如果确认是既有问题，创建立项文档后继续
- 如果是本轮引入，停止并报告

**主源码编译失败**：
- 停止执行，报告错误信息
- 不要继续下一个任务

**代码逻辑不确定**：
- 参考执行计划中的详细说明
- 如果仍不确定，停止并请求澄清

## 开始执行

请按以下步骤开始：

1. 使用 `read` 工具读取执行计划：`docs/开发者文档/reviews/REVIEW-20260613-lwjgl3ify-decouple-plan.md`
2. 使用 `bash` 确认当前分支：`git status --short --branch`
3. 使用 `todowrite` 创建任务清单（6 个任务）
4. 开始执行任务 1.1

**提示**：执行计划中包含每个任务的详细修改内容、代码示例和验收标准。严格按照执行计划操作，不要自行发挥或省略步骤。

---

## 简化版启动提示词（供复制）

如果你希望用更简洁的方式启动另一个 Agent，可以使用以下提示词：

```
请执行 `refactor/decouple-lwjgl3ify` 分支的代码质量优化修复（批次 1）。

执行步骤：
1. 读取执行计划：docs/开发者文档/reviews/REVIEW-20260613-lwjgl3ify-decouple-plan.md
2. 读取 Agent 提示：docs/开发者文档/reviews/AGENT-PROMPT-lwjgl3ify-decouple-fix.md
3. 确认当前分支：refactor/decouple-lwjgl3ify
4. 按执行计划依次完成任务 1.1 到 1.6（共 6 个任务）
5. 每个任务完成后使用 todowrite 标记进度
6. 所有任务完成后提交代码并更新交接记录

关键要求：
- 严格按执行计划操作，不要跳过或修改步骤
- 每次修改后立即运行 ./gradlew compileJava 验证
- 遇到问题立即停止并报告
- 最终必须满足批次 1 的所有验收标准

开始前请确认你已理解任务要求。
```
