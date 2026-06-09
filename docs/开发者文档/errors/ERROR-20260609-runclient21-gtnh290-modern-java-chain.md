# runClient21 GTNH 2.9 beta 现代 Java 链路错配

## 错误现象

- 将开发依赖基线切到 GTNH `2.9.0-beta-1` 后，`runClient21` 的 Gradle 任务返回 `BUILD SUCCESSFUL`，但客户端主线程启动失败。
- 第一阶段失败为 `NoClassDefFoundError: com/gtnewhorizons/retrofuturabootstrap/api/BytePatternMatcher`。
- 升级 GTNH Gradle 插件后，该缺类消失，但客户端继续在第三方 mixin 阶段失败：`ServerUtilities` 的 `MixinWorldServer_SleepPercentage` 与 `Et-Futurum-Requiem` 的 `playerssleepingpercentage.MixinWorldServer` 对 `WorldServer` 睡眠逻辑发生 `@Redirect conflict`，随后 `InvalidInjectionException`。

## 触发场景

- 分支 `chore/gtnh-290-beta` 将 `dependencies.gradle` 的 `elytraModpackVersion` 基线改为 `2.9.0-beta-1`。
- 使用稳定命令运行 `$env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"; ./gradlew.bat --no-configuration-cache runClient21`。
- 项目继续使用 GTNH Gradle modern Java run task，而不是完整整合包启动器。

## 根本原因

- `gtnhgradle 2.0.20` 的 `ModernJavaModule` 硬编码 `java17PatchDependencies` 为 `com.github.GTNewHorizons:lwjgl3ify:3.0.10:forgePatches`。
- `2.9.0-beta-1` manifest 让普通 `java17Dependencies` / `runtimeClasspath` 中的 `lwjgl3ify` 解析到 `3.0.23`，于是运行时同时存在新版 `lwjgl3ify` dev jar 与旧版 `3.0.10-forgePatches` / `rfb@1.0.14`。
- `lwjgl3ify-3.0.10-forgePatches.jar` 不包含新版链路需要的 `BytePatternMatcher`；`lwjgl3ify-3.0.20-forgePatches.jar` 已包含该类。
- 升级插件后剩余失败不再是 Qz 或 RFB 缺类，而是 `ServerUtilities 2.3.0` 与 `Et-Futurum-Requiem 2.6.40-GTNH` 的早期 mixin 同目标冲突。

## 修复方案

- 将 `settings.gradle.kts` 的 `com.gtnewhorizons.gtnhsettingsconvention` 从 `2.0.20` 升到 `2.0.25`，让 modern Java run 链路使用插件维护的 `lwjgl3ify 3.0.20:forgePatches`。
- 不在项目脚本里手写覆盖 RetroFuturaBootstrap 或 `forgePatches` jar，避免绕过 GTNH Gradle 对 modern Java run task 的配套约束。
- `ExampleMod` / Blowdryer 未发现新版本要求，`gtnh.settings.blowdryerTag` 保持 `0.2.2`。

## 预防措施

- 升级 GTNH manifest 后必须同时检查 `runtimeClasspath`、`java17Dependencies` 和 `java17PatchDependencies`，不要只看普通运行时依赖。
- 遇到 `RFB` 缺类时先排查 `lwjgl3ify-*-forgePatches.jar` 与 `lwjgl3ify-*-dev.jar` 是否同代。
- `runClient21` 进入第三方 mod mixin 冲突后，优先用运行目录配置或上游版本配套解决，不要把第三方 mixin 冲突归因到 Qz 生产代码。
