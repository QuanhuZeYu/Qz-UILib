# AI 记忆文档

本文件只保留跨任务长期稳定的高层导航和边界信息。遇到具体任务时，Agent 必须主动读取对应文档和源码确认现状。

## 文档体系导航

| 需要什么 | 去哪里找 |
|----------|----------|
| 文档全局入口 | `docs/README.md` |
| 对外接入方式、能力边界 | `docs/使用文档/README.md` |
| 内部架构方向、审查、错误 | `docs/开发者文档/README.md` |
| 技术方向与取舍原则 | `docs/开发者文档/项目建议.md` |
| 开放化方案与边界 | `docs/开发者文档/开放化调整.md` |
| 审查报告索引 | `docs/开发者文档/reviews/README.md` |
| 错误记录索引 | `docs/开发者文档/errors/README.md` |
| 示例页规格 | `docs/开发者文档/specs/README.md` |

## 项目定位

- Minecraft 1.7.10 / GTNH / LWJGL3ify 环境下的 HTML-like UI 框架 Mod。
- 根包：`club.heiqi.uilib`，Mod ID：`qz_uilib`。
- 作者层暴露浏览器语义（DOM / CSS / 事件模型），不向页面作者暴露 Minecraft GUI 生命周期或底层渲染实现细节。

## 对外入口边界

- 业务文档入口：`UiDocumentScreens.createDocumentScreen(...)`。
- 双端网络入口：`NetService.getInstance()`，在 `preInit` 注册 Channel / Fetch / Stream / Store，`postInit` 后注册表冻结；网络消息以 `route/key + contentType + headers + body` 为核心，不以 Java 类型作为协议身份；普通逻辑消息默认 16 MiB，超过阈值走 Stream/chunk；Fetch endpoint 可配滑动窗口限流，Store 可注册业务 delta applier；默认 vanilla 传输，可用 `netTransport` 或 `-Dqzuilib.net.transport=forge` 切 Forge 回退。
- 诊断/示例页只保留内部开发工具入口（`/qzuilib test`），不对外暴露页面工厂。
- 不新增扩大直接 `Widget` 作者入口的 API。

## 主动读取原则

- 涉及对外接入 → 先读 `docs/使用文档/README.md`
- 涉及错误复现 → 先读 `docs/开发者文档/errors/README.md`
- 涉及审查结论 → 先读 `docs/开发者文档/reviews/README.md`
- 涉及命令入口、HUD、输入路由 → 先读 `docs/使用文档/03-宿主集成/` 下对应文档
- 需要具体类/入口/目录位置 → 用 Glob/Grep/Read 现查，不在本文件维护索引

## 运行与验证

- Windows 环境，PowerShell。
- `GRADLE_USER_HOME` 必须设为 `D:\.MyApps\.ENV\gradle-home`。
- 常用命令：
  - 编译：`$env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"; ./gradlew.bat --no-configuration-cache compileJava`
  - 测试：`$env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"; ./gradlew.bat --no-configuration-cache test`
  - 客户端：`$env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"; ./gradlew.bat --no-configuration-cache runClient21`
- 纯 JVM 测试不要直接实例化继承 `GuiScreen` / `BaseScreen` 的页面类。
- 网络层运行时自检入口为 `/qzuilib test`，支持逐项或全部执行；真实 Channel / Fetch / Stream / Store 往返、C2S 分片、Fetch 错误/超时/取消/限流、玩家 Store、Store delta 检查已在当前 GTNH / ModularUI2 服务端环境完成一次完整联机验收（18/18 通过）；Forge 回退仍需单独切换 `netTransport=forge` 验证。
- 默认 `runServer` 目前会被 LWJGL3ify relauncher 中止；dedicated server 完整 smoke 需换用不带该 relauncher 的服务端配置，相关记录见 `docs/开发者文档/errors/ERROR-20260523-runserver-lwjgl3ify-relauncher.md`。

## 本文件的维护规则

- 只记录长期稳定边界和导航指针，不记录阶段流水账、类清单、修复日志。
- 有明确归属的信息写回原文档，不在此重复。
- 更新时优先做删减和归并，避免按日期追加。
- 临时调试记录和一次性试验结论不沉淀到本文件。
