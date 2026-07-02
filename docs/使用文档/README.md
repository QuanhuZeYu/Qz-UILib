# 使用文档

本目录面向准备接入 Qz UILib 的 Mod 开发者，按入门、控件、宿主集成和诊断入口分级组织。

> 内部开发文档（错误预防、发布流程、规格）见 [开发者文档](../开发者文档/README.md)。

## 面向对象

- Minecraft 1.7.10 / GTNH / LWJGL3ify 环境下希望接入 HTML-like UI 的 Mod 开发者。
- 希望用 Java API 构建 DOM-like 文档树、样式、控件和页面的开发者。

## 阅读顺序

1. [项目定位与能力边界](01-入门/项目定位与能力边界.md)
2. [最小文档页面](01-入门/最小文档页面.md)
3. [完整业务页面示例](01-入门/完整业务页面示例.md)
4. [基础控件](02-控件/基础控件.md)
5. [表格与背包槽位](02-控件/表格与背包槽位.md)
6. [Forge 配置模板](02-控件/Forge配置模板.md)
7. [远程页面](02-控件/远程页面.md)
8. [远程 HUD 浮窗](02-控件/远程HUD浮窗.md)
9. [网络层入门](02-控件/网络层入门.md)
10. [Minecraft 界面入口](03-宿主集成/Minecraft界面入口.md)
11. [指令触发方案](04-诊断入口/指令触发方案.md)
12. [v4.x LTS 稳定 API 清单](v4.x-LTS-稳定API清单.md)

> 注：旧「现代配置模板」与「现代配置模板示例」使用文档已随旧栈 ModernConfig 拆除删除，新架构配置页文档待新架构施工完成后补写。

## 核心要点

- 使用 Java API 构建 UI，不是编写 HTML/CSS 文件。
- 通过 `UiDocumentScreens.createDocumentScreen(...)` 创建业务界面。
- 服务端生成的安全子集 HTML 可通过 `RemoteDocumentPages.open(...)` 下发给客户端显示。
- 同样的安全子集 HTML 可通过 `RemoteHudOverlays.open(...)` 做成 HUD 弹窗、提示和弹幕。
- 页面内容通过 `UiDocument`、`ElementNode`、样式和控件组织。
- 双端通信通过 `NetService` 注册 Channel / Fetch / Stream / Store。
- 诊断页和示例页只作为开发期工具，不作为玩家默认入口。

## 相关文档

- [文档全局导航](../README.md)
- [开发者文档](../开发者文档/README.md)（内部错误预防、发布流程、规格）
