# ERROR-20260518-gradle-parallel-build-race

## 错误现象

同一工作区内并行启动多个 Gradle `test --tests ...` 验证命令后，任务之间竞争 `build/classes/java/main` 输出目录，出现以下现象：

- `compileJava` 报错 `Unable to delete directory '...build\classes\java\main'`。
- 后续 `compileTestJava` 大量报 `找不到符号`，看起来像源码类消失。
- 并行执行 `compileJava` 与 `test --tests ...` 时，可能出现其中一个 PowerShell/Gradle 子进程被杀，另一个进程的 `compileTestJava` 随后大量报主源码类不可见。
- 单独重跑相同测试时又可以通过，说明不是业务源码本身编译失败。

## 触发场景

- 使用并行工具同时执行多个 `./gradlew.bat test --tests ...` 命令。
- 使用并行工具同时执行 `./gradlew.bat compileJava` 与 `./gradlew.bat test --tests ...`。
- 2026-06-06 脏子树优化开发中，再次并行执行 `DocumentPaintEngineTest` 与 `compileJava`，触发 `ChildProcess.kill (...)`；顺序复跑 `compileJava` 后通过，确认仍属验证方式错误。
- 多个 Gradle 进程共享同一个工作区、同一个 `build` 目录和同一个 Gradle 用户缓存。
- 某个进程正在写入或清理 `build/classes/java/main`，另一个进程同时开始编译或测试。

## 根本原因

Gradle 项目输出目录不是多进程并发写安全资源。并行启动多个独立 Gradle 进程会互相清理、写入或锁住同一批 class 文件，导致目录删除失败和后续类路径不完整。这个错误由验证方式引入，不代表被测源码一定存在编译错误。

## 修复方案

- 停止 Gradle 守护进程释放文件句柄：`./gradlew.bat --stop`。
- 改为串行运行 Gradle 验证命令。
- 若已经出现类路径不完整错误，先让一次串行 `compileJava` 或目标测试重新建立输出，再继续判断真实失败。

## 预防措施

- 同一工作区内不要并行执行多个 Gradle 命令，尤其是 `test`、`compileJava`、`compileTestJava`；即使一个是“只编译”、另一个是“只跑目标测试”也必须串行。
- 需要并行验证时，必须使用彼此隔离的工作区或独立 build 目录。
- 看到大量无关 `找不到符号` 且伴随 `Unable to delete directory build/classes` 时，先按构建目录竞争处理，不要直接修改源码。
- 对验证结果做结论前，必须串行重跑关键命令确认是否为真实代码失败。
