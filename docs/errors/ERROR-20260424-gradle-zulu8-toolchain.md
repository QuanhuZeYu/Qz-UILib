# ERROR-20260424-gradle-zulu8-toolchain

## 错误现象
- 执行 `./gradlew.bat compileJava` 或 `./gradlew.bat build` 时，在真正进入项目源码编译前失败。
- 典型报错为：Gradle 无法找到满足 `languageVersion=8, vendor=Azul Zulu` 的 Java 工具链。
- 在当前环境中还出现过两类具体表现：
  - Foojay 自动下载/解析 `Zulu 8` 返回 `400 Bad Request`。
  - 多个 Gradle 进程并发运行时，`decompileSrgJar` 因 `build/tmp/decompileSrgJar/mc.jar` 被占用而报 Windows 文件锁错误。

## 触发场景
- 新机器首次搭建本项目环境，只安装了系统默认 JDK 21。
- 直接执行 `./gradlew.bat compileJava` 或 `./gradlew.bat build`，依赖 Gradle/Foojay 自动补齐 `Zulu 8`。
- 同时并发执行 `compileJava` 与 `build` 等 Gradle 命令。

## 根本原因
- 本项目 GTNH 构建链要求使用 `Azul Zulu JDK 8` 作为特定任务的工具链，而不是任意 Java 8 或当前运行 Gradle 的 JDK。
- 当前机器默认仅有 Temurin 21；Gradle 自动下载/注册 `Zulu 8` 在此环境下不稳定，未能生成可识别的本地工具链。
- Windows 下多个 Gradle 构建共享相同工作目录时，`decompileSrgJar` 使用的临时文件存在互斥访问问题。

## 修复方案
- 手动下载并解压 `Zulu 8` 到纯 ASCII 路径：`C:\temp\zulu8\zulu8.92.0.21-ca-jdk8.0.482-win_x64`。
- 构建时显式指定 `GRADLE_USER_HOME` 与 `org.gradle.java.installations.paths`：

```powershell
$env:GRADLE_USER_HOME="C:\temp\gradle-home"
./gradlew.bat "-Dorg.gradle.java.installations.paths=C:\temp\zulu8\zulu8.92.0.21-ca-jdk8.0.482-win_x64,C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot,C:\Users\泉户 黑崎\.jdks\jdk-25.0.2+10" --no-configuration-cache build
```

- 若前一次构建异常中断或曾并发执行 Gradle，先执行 `./gradlew.bat --stop`，再串行重跑单个构建命令。

## 预防措施
- 后续在本机上构建该项目时，默认使用已验证的显式工具链命令，不再依赖 Foojay 自动下载 `Zulu 8`。
- 不要并发执行多个 Gradle 构建命令，尤其不要同时运行 `compileJava` 和 `build`。
- 若需要长期固定本地环境，可考虑后续由用户确认后再把等价配置沉淀到用户级 Gradle 配置，而不是仓库配置。
