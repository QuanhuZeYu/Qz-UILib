# ERROR-20260426 Gradle Java 8 Worker 用户路径兼容问题

## 错误现象

- IDEA 运行 `Run Client (Java 21)` 时失败，失败任务为 `:compileMcLauncherJava`。
- 命令行复现 `./gradlew.bat --no-configuration-cache compileMcLauncherJava --stacktrace --info` 时，Gradle Worker 使用 Zulu 8 启动后立即退出。
- 关键错误：`Error occurred during initialization of VM`。
- 关键错误：`java.lang.InternalError: Could not create SecurityManager: worker.org.gradle.process.internal.worker.child.BootstrapSecurityManager`。
- 关键错误：`Process 'Gradle Worker Daemon' finished with non-zero exit value 1`。

## 触发场景

- Windows 用户目录为 `C:\Users\泉户 黑崎`，路径包含中文与空格。
- `GRADLE_USER_HOME` 未显式设置时，Gradle Worker 缓存位于 `C:\Users\泉户 黑崎\.gradle`。
- `compileMcLauncherJava` 使用 Java 8 工具链启动 Gradle Worker，并从该 Gradle home 加载 `workerMain\gradle-worker.jar`。

## 根本原因

- Java 8 Worker 进程对包含中文和空格的 Gradle 用户目录兼容性不稳定，导致启动阶段无法创建 Gradle 的 `BootstrapSecurityManager`。
- 仅集中 Java 安装路径不足以解决该问题；Gradle Worker 自身的缓存目录也需要放在纯 ASCII 路径下。

## 修复方案

- 将用户级 `GRADLE_USER_HOME` 设置为纯 ASCII 路径：`D:\.MyApps\.ENV\gradle-home`。
- 在 `D:\.MyApps\.ENV\gradle-home\gradle.properties` 写入 `org.gradle.java.installations.paths`，显式包含 Zulu 8、JDK 21、JDK 25 与 IntelliJ JBR 21。
- 将旧可用缓存从 `C:\temp\gradle-home` 合并到 `D:\.MyApps\.ENV\gradle-home`，避免重新下载大量 Forge/RFG 依赖。

## 预防措施

- 本项目在 Windows 上不要把 `GRADLE_USER_HOME` 放在包含中文、空格或特殊字符的路径中。
- 遇到 Java 8 Gradle Worker 崩溃时，优先检查 Worker 命令行中的 `GRADLE_USER_HOME`、`workerMain\gradle-worker.jar` 路径和工具链 Java 版本。
- 修改 Java/Gradle 环境后，应验证 `./gradlew.bat --no-configuration-cache javaToolchains` 和 `./gradlew.bat --no-configuration-cache compileMcLauncherJava --stacktrace`。
