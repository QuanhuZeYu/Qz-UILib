# ERROR-20260425-idea-runclient21-jbr-toolchain

## 错误现象
- 在 IDEA 中运行 `runClient21` 或在命令行执行 `./gradlew.bat runClient21` 时，项目可能在配置阶段失败或长时间停在工具链解析阶段。
- 典型报错包括 `Failed to load the manifest from Github`、`java.nio.channels.UnresolvedAddressException`，或日志显示正在从 Foojay 下载 `jbrsdk_jcef-21-JetBrains-21.0.10`。

## 触发场景
- 网络刚重置或无法稳定访问 GitHub/Foojay 时，Gradle 配置 `com.gtnewhorizons.gtnhconvention` 插件或解析运行时工具链。
- 本地 Gradle/IDEA 工具链路径只包含 Zulu 8、Temurin 21、JDK 25，但没有包含 IDEA 自带的 JetBrains JBR 21 + JCEF。
- 在 IDEA 中直接点击 `runClient21`，未通过用户级 Gradle 配置或运行配置传入 `org.gradle.java.installations.paths`。

## 根本原因
- `runClient21` 不是普通 Java 21 启动任务，它会优先寻找 JetBrains JBR 21 + JCEF 运行时。
- 当前机器的 IDEA 已安装可用 JBR：`D:\.MyApps\JetBrain\IntelliJ\jbr`，但 Gradle 默认自动检测没有把该路径作为可选工具链提供给 `runClient21`。
- 当本地路径缺失时，Gradle 会尝试通过 Foojay 自动下载 JBR；网络不稳定时下载会卡住或失败。
- `Failed to load the manifest from Github` 是配置阶段访问 Elytra/GTNH manifest 失败，不是源码编译错误。

## 修复方案
- 先确保网络可访问 GitHub manifest 和 GTNH Nexus；网络恢复后 `javaToolchains` 应能通过。
- 当前本机修复方式是通过用户级 `GRADLE_USER_HOME=D:\.MyApps\.ENV\gradle-home` 持久化 Gradle 工具链路径。
- 在 `D:\.MyApps\.ENV\gradle-home\gradle.properties` 中写入：

```properties
org.gradle.java.installations.paths=D:/.MyApps/.ENV/zulu8.92.0.21-ca-jdk8.0.482-win_x64,D:/.MyApps/.ENV/jdk-21.0.10+7,D:/.MyApps/.ENV/jdk-25.0.2+10,D:/.MyApps/.ENV/jbr-21-intellij
```

- 若不使用用户级 Gradle home，也可以在 IDEA 中把等价的 `org.gradle.java.installations.paths` 配到该 Gradle 运行配置的 VM options，避免依赖 Foojay 自动下载。

## 预防措施
- 本项目在本机运行 `runClient21` 时默认把 `D:\.MyApps\.ENV\jbr-21-intellij` 加入 Gradle 工具链路径。
- 不把该绝对路径写入仓库级 `gradle.properties`，避免污染其他开发者环境。
- 若 `runClient21` 再次卡住，优先检查是否正在下载 `jbrsdk_jcef-21` 或是否出现 GitHub manifest 访问失败，而不是先排查源码编译。
