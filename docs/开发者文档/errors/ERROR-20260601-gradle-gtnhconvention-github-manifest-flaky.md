# Gradle `gtnhconvention` GitHub manifest 偶发失败

## 错误现象

- 执行 `./gradlew.bat --no-configuration-cache compileJava` 或 `test` 时，构建可能在 `Configure project` 阶段直接失败。
- 典型报错：`Failed to load the manifest from Github`。

## 触发场景

- `gtnhconvention` 插件在配置阶段尝试从 GitHub 加载 manifest。
- 网络抖动、GitHub 临时不可达或远端限流时会触发。

## 根本原因

- 失败发生在外部构建脚本依赖加载阶段，不属于当前业务源码编译错误。
- 同一工作区中，前一次 `compileJava` 或 `test` 可能成功，下一次重跑却在配置阶段失败，说明问题具有明显外部波动特征。

## 修复方案

- 先直接重试相同 Gradle 命令。
- 若重试后成功，按“外部波动已恢复”处理，不要误判为本轮代码回归。
- 依赖已在本地缓存时，单独 `--offline` 仍可能在 Blowdryer 设置拉取阶段失败；需要同时用命令行属性 `"-Pgtnh.settings.blowdryerTag="` 临时覆盖为空，跳过远端设置 tag 拉取。
- 已验证可用命令：`$env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"; ./gradlew.bat --offline --no-configuration-cache "-Pgtnh.settings.blowdryerTag=" test --tests "<类名>"`。
- 若持续失败，再进一步检查网络、代理、GitHub 可达性与插件上游状态。

## 预防措施

- 记录一次成功的 `compileJava` 或目标测试结果后，再做末次收口验证时，若只出现该错误，应先尝试 `--offline "-Pgtnh.settings.blowdryerTag="`；仍失败时再在交接与验证结论中明确标注“外部 manifest 波动”。
- 不要因为该错误回退本地源码或随意修改业务实现。
