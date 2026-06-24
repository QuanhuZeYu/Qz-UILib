# ERROR-20260624-parallel-fixer-gradle-build-race

## 错误现象
两个 fixer 子代理并行实现 SceneSimpleList 和 SceneKeyValueMap 后，各自独立跑
`jetbrainsBuildProject` / `./gradlew.bat compileTestJava`，导致：
- `compileJava` 报 UP-TO-DATE 但 `build/classes` 中 main `.class` 实际缺失
- `compileTestJava` 找不到 `club.heiqi.config.Config` 等确实存在的 main 类
- 测试无法编译运行，误判为新控件代码问题

## 触发场景
主 Agent 并行派发两个 fixer 子代理实现无依赖的控件，两个 fixer 各自在
任务末尾跑 `jetbrainsBuildProject` 验证编译。两个 build 进程竞争同一
`build/` 目录，导致 class 文件互相覆盖/缺失。

## 根本原因
与 `ERROR-20260518-gradle-parallel-build-race` 同类：并行 Gradle build 进程
竞争共享 `build/` 目录。`jetbrainsBuildProject` 和 `gradlew` 走不同编译器
通路（IDE 内置 vs Gradle），产物目录可能交叉污染。

主 Agent 调度失误：并行 fixer 只能并行**写代码**，**build 必须串行**或
统一由主 Agent 在所有 fixer 完成后单次跑。

## 修复方案
- `clean compileTestJava` 后恢复，非新控件代码问题
- 两个控件测试全绿（SceneSimpleListTest 9 项，SceneKeyValueMapTest 11 项）

## 预防措施
1. **并行 fixer 派发时，明确要求不要自己跑 build**——只写代码，编译验证
   由主 Agent 在所有 fixer 完成后统一单次跑
2. 如果 fixer 必须验证编译，用 `jetbrainsBuildProject` 的 `filesToRebuild`
   参数只编译自己改的文件，不跑全量 gradle
3. 主 Agent 收到所有 fixer 结果后，串行跑一次 `jetbrainsBuildProject`
   全量编译 + 一次 `gradlew test` 跑所有相关测试
4. 参考 `ERROR-20260518-gradle-parallel-build-race` 的共性教训：
   并行 Gradle 进程必须避免共享 build 目录
