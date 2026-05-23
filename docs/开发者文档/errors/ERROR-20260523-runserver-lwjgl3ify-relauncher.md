# ERROR-20260523-runserver-lwjgl3ify-relauncher

## 错误现象

在网络层 dedicated server smoke 验收中运行：

```powershell
$env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"; "stop" | ./gradlew.bat --no-configuration-cache runServer
```

服务端启动到 LaunchWrapper / Mixin 预初始化阶段后失败，日志出现：

- `Detected Side : SERVER`
- `lwjgl3ify relauncher does not support server launches`
- `Task :runServer FAILED`

## 触发场景

当前 GTNH 开发运行目录同时包含 `lwjgl3ify` relauncher；使用 Gradle `runServer` 做 dedicated server smoke 时，LWJGL3ify 的 relauncher 会在 server launch 中止流程。

## 根本原因

这不是 Qz 网络层或 early mixin 本身的崩溃。日志已经进入 Mixin `SERVER` 环境检测，但 `me.eigenraven.lwjgl3ify.relauncher.Lwjgl3ifyRelauncherTweaker` 不支持当前 server launch 方式，导致 Forge/FML 还未完成 mod 初始化就退出。

## 修复方案

- 对 `EarlyMixins` 增加纯 JVM 侧过滤断言，固定 `Side.SERVER` 下不会返回客户端 mixin。
- dedicated server 真机 smoke 需要使用不带 LWJGL3ify relauncher 的服务端运行配置，或按 LWJGL3ify README 调整服务端安装方式后再复跑。

## 预防措施

- 看到 `runServer` 在 LWJGL3ify relauncher 处失败时，不要先按网络层崩溃排查。
- 判断 dedicated 侧 mixin 过滤时，先看日志是否已经显示 `Detected Side : SERVER`，再区分是 mixin 配置问题还是运行环境启动器问题。
- 在本仓库默认 `runServer` 环境修复前，保留 JVM 过滤测试作为最低门禁。
