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
- 依赖已在本地缓存时，可加 `--offline` 跳过配置阶段的 GitHub manifest 拉取，离线复跑目标测试做验证（已验证：`--offline --no-configuration-cache test --tests ...` 可在 manifest 不可达时通过）。
- 若持续失败，再进一步检查网络、代理、GitHub 可达性与插件上游状态。

## 预防措施

- 记录一次成功的 `compileJava` 或目标测试结果后，再做末次收口验证时，若只出现该错误，应在交接与验证结论中明确标注“外部 manifest 波动”。
- 不要因为该错误回退本地源码或随意修改业务实现。
