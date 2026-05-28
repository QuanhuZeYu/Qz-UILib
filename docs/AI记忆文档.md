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
- 内置弹出型控件按浏览器 top-layer 语义处理：DOM 归属不变，布局/绘制/命中由文档运行时提升到视口顶层；top-layer 仍是内部能力，不作为业务作者 API。
- 2026-05-25 代码结构 P0-P3 整改已收口：`__` public 内部 API 暂保持君子协定并明确不保证未来稳定，input 包改为消费宿主注册抽象，远程图片缓存拆分 `clear()` / JVM 退出 `shutdown()`，`DocumentCustomRenderer` / `CUSTOM` 仅作为宿主级逃生口。

## 对外入口边界

- 业务文档入口：`UiDocumentScreens.createDocumentScreen(...)`；服务端生成的安全子集 HTML 远程页面入口为 `RemoteDocumentPages.open(...)`，HUD 浮窗入口为 `RemoteHudOverlays.open(...)`，两者都不执行 JavaScript、不嵌入真实浏览器，并共享内部远程 HTML session / Stream 网关。
- HUD 宿主内部现已收敛为“单共享 `UiDocument` / `HtmlLikeDocumentWidget` + 每个 HUD 一棵挂载子树”：`UiHudDocumentHost.register(...)` 的 builder 接收 `UiHudMountContext`，应只通过 `getMountRoot()` 操作当前 HUD 根，不再假设 `document.getRootElement()` 由当前 HUD 独占。
- HUD 输入抢占的长期契约保持为：交互 HUD 仅在容器态接通输入，必须先鼠标命中建立 HUD 焦点，键盘抢占只由 HUD 内有效焦点驱动；`GuiChat` 打开时 HUD 可继续显示，但不会沿用旧 HUD 焦点继续抢占，必须在当前聊天界面里再次点击 HUD 才会重新接管；若 HUD 已从聊天框手里抢走输入，主键点击浮窗外部会显式恢复聊天框原生焦点；`UiHostInputCoordinator` 只作为宿主原生输入链路上的协调桥，不承载 HUD 业务规则。
- 配置页现支持基于 UILIB 自建网络的服务端权威同步：本地模板页可通过 `ForgeConfigTemplateScreen.Spec.setRemoteSyncController(...)` / `setRemoteSyncScreenId(...)` 绑定配置会话，服务端远程配置页入口为 `RemoteConfigDocumentPages.open(...)`；两条路线共享同一个 `ConfigTemplateSyncManager` 配置目标 / 草稿 / 显式保存模型；配置分类名默认大小写敏感，只有通过 `CategorySpec.addAlias(...)` / `ConfigSyncCategorySpec.addAlias(...)` 显式声明的历史名称才参与兼容查找。
- 双端网络入口：`NetService.getInstance()`，在 `preInit` 注册 Channel / Fetch / Stream / Store，`postInit` 后注册表冻结；网络消息以 `route/key + contentType + headers + body` 为核心，不以 Java 类型作为协议身份；普通逻辑消息默认 16 MiB，超过阈值走 Stream/chunk；Fetch endpoint 可配滑动窗口限流，Store 可注册业务 delta applier；默认 vanilla 传输，其能力握手在 FML connection-established 事件之后触发，不在 Play NetHandler 构造期发送；可用 `netTransport` 或 `-Dqzuilib.net.transport=forge` 切 Forge 回退。
- 诊断/示例页只保留内部开发工具入口（`/qzuilib test`），页面实现归入 `internal.devtools.pages`；可被真实客户端适配复用的库存概览模型归入 `ui.inventory`；不对外暴露页面工厂。
- `__` 双下划线 public 方法是内部协作君子协定：为特殊宿主、兼容层和诊断路径保留可见性，不属于稳定 API，未来可替换为 internal accessor 或 package-private bridge。
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
- 网络层运行时自检入口为 `/qzuilib test`，端点注册、用例执行、远程页面/HUD smoke 构造在内部拆分协作；真实 Channel / Fetch / Stream / Store 往返、C2S 分片、Fetch 错误/超时/取消/限流、玩家 Store、Store delta 检查已在当前 GTNH / ModularUI2 服务端环境完成一次基础联机验收（18/18 通过）；远程页面与远程 HUD 自检都属于交互 smoke，会分别触发 `RemoteDocumentPages.open(...)` 和 `RemoteHudOverlays.open(...)` 并需要提交按钮完成表单回传验证；Forge 回退仍需单独切换 `netTransport=forge` 验证。
- `/qzuilib test` 现已新增“配置同步” smoke：客户端会通过同一套 `NetService` 端点完成配置会话打开、草稿同步和显式保存链路自检。
- 默认 `runServer` 目前会被 LWJGL3ify relauncher 中止；dedicated server 完整 smoke 需换用不带该 relauncher 的服务端配置，相关记录见 `docs/开发者文档/errors/ERROR-20260523-runserver-lwjgl3ify-relauncher.md`。

## 本文件的维护规则

- 只记录长期稳定边界和导航指针，不记录阶段流水账、类清单、修复日志。
- 有明确归属的信息写回原文档，不在此重复。
- 更新时优先做删减和归并，避免按日期追加。
- 临时调试记录和一次性试验结论不沉淀到本文件。
