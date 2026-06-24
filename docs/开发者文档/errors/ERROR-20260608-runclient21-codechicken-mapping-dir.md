# runClient21 CodeChickenLib 映射目录失效

## 错误现象

- 执行 `runClient21` 时弹出 MCP 映射目录选择窗口。
- 未手动选择目录时，客户端启动日志出现 `Failed to select mappings directory, set it manually in the config`。
- 崩溃链路来自 `cofh.repack.codechicken.lib.asm.ObfMapping$MCPRemapper.getConfFiles` 与 `cofh.asm.LoadingPlugin`。

## 触发场景

- 本地按项目约定使用 `GRADLE_USER_HOME=D:\.MyApps\.ENV\gradle-home` 启动客户端。
- `run/client/config/CodeChickenLib.cfg` 仍指向旧用户目录下的 Gradle 缓存，例如 `C:\Users\QuanHu\.gradle\...\unpacked\conf`。
- 或者 `run/` 目录被清理后，CodeChickenLib 首次在反混淆开发环境中尝试自动选择映射目录。

## 根本原因

CodeChickenLib/CoFHCore 在开发环境需要 `packaged.srg`、`fields.csv`、`methods.csv` 三个 MCP 映射文件。项目实际 Forge 映射文件由 RetroFuturaGradle 解包到当前 `GRADLE_USER_HOME` 下的 Forge 缓存目录，但 `run/client/config/CodeChickenLib.cfg`
属于被忽略的运行目录文件，不能稳定跟随项目约定的 Gradle 用户目录迁移。

## 修复方案

- 新增 Gradle 启动前配置任务 `configureCodeChickenMappings`。
- 任务根据 `minecraftVersion`、`forgeVersion` 和当前 `gradle.gradleUserHomeDir` 计算 Forge `unpacked/conf` 目录。
- 任务在所有 `runClient*` 任务执行前写入 `run/client/config/CodeChickenLib.cfg`，并校验 `packaged.srg`、`fields.csv`、`methods.csv` 存在。

## 预防措施

- 继续通过项目稳定命令显式设置 `GRADLE_USER_HOME`，避免中文用户目录和旧缓存路径干扰。
- 不要把 `run/client/config/CodeChickenLib.cfg` 提交入仓库；它应由 Gradle 根据当前机器环境生成。
- 若再次出现 MCP 映射目录选择窗口，先检查 `configureCodeChickenMappings` 是否执行、`GRADLE_USER_HOME` 是否正确，以及 Forge `unpacked/conf` 是否完整。
