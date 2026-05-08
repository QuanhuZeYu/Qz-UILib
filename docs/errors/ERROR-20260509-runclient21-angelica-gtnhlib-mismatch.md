# ERROR-20260509 runClient21 Angelica 与 GTNHLib 版本错配

## 错误现象

- 在 `dependencies.gradle` 中加入 `runtimeOnly(project.elytraModpackVersion.gtnhdev("Angelica"))` 后，`runClient21` 启动阶段直接崩溃。
- 关键报错为 `ClassMetadataNotFoundException: com.gtnewhorizon.gtnhlib.client.renderer.quad.QuadProvider`。
- 日志显示失败的 mixin 为 `mixins.angelica.early.json:angelica.models.MixinBlock`。

## 触发场景

- 继续使用项目默认的现代 Java 运行链路：`./gradlew.bat --no-configuration-cache runClient21`。
- 运行时使用 `elytraModpackVersion.gtnhdev("Angelica")` 解析到 `Angelica 1.0.0-beta66b`。
- 工程同时启用了 `gtnhgradle` 的 Modern Java 模块。

## 根本原因

- `runClient21` 会由 `gtnhgradle` 的 Modern Java 模块强制给 `implementation`、`runtimeOnly` 等配置注入 `GTNHLib 0.9.20` 约束，用于现代 Java 运行链路。
- `Angelica 1.0.0-beta66b` 的早期 mixin 仍引用旧版 `GTNHLib` API `com.gtnewhorizon.gtnhlib.client.renderer.quad.QuadProvider`。
- 当前解析到的 `GTNHLib 0.9.20` 中该类已不存在，导致 Angelica 在改写 `net.minecraft.block.Block` 时直接崩溃。

## 修复方案

- 不在 `runClient21` 环境里强行把 `GTNHLib` 锁回 `0.7.10`。
- 在保持默认现代 Java 运行链路的前提下，改用与 `GTNHLib 0.9.20` 同代的 Angelica 版本。
- 当前项目已验证 `runtimeOnly("com.github.GTNewHorizons:Angelica:2.1.15:dev")` 可在 `runClient21` 下正常启动。

## 预防措施

- `runClient21` 出现 Angelica 启动崩溃时，先检查运行时实际解析的 `GTNHLib` 版本，而不是只看整合包原始版本。
- 若测试目标是“当前现代 Java 开发链路下的兼容性”，优先升级 Angelica 到与 `GTNHLib 0.9.x` 兼容的版本。
- 若测试目标是“严格复刻旧整合包环境”，不要继续使用 `runClient21`，而应切换到旧版运行链路单独验证。
