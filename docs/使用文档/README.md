# 使用文档

本目录面向准备接入 Qz UILib 的 Mod 开发者，按入门、控件、宿主集成和诊断入口分级组织。

> 内部开发文档（错误预防、发布流程、规格）见 [开发者文档](../开发者文档/README.md)。

## 面向对象

- Minecraft 1.7.10 / GTNH / LWJGL3ify 环境下希望接入 Qz UILib（scene 新栈与宿主能力）的 Mod 开发者。
- 希望用 Java API 构建配置页、scene 控件与宿主界面的开发者。

## 阅读顺序

1. [项目定位与能力边界](01-入门/项目定位与能力边界.md)
2. [配置页（ModernConfig）](02-控件/配置页（ModernConfig）.md)
3. [场景文本输入与浮层](02-控件/场景文本输入与浮层.md)
4. [网络层入门](02-控件/网络层入门.md)
5. [Minecraft 界面入口](03-宿主集成/Minecraft界面入口.md)
6. [指令触发方案](04-诊断入口/指令触发方案.md) ／ [测试场地](04-诊断入口/测试场地.md)
7. [稳定 API 清单](v4.x-LTS-稳定API清单.md)
8. [Config 模块使用指南](../Config模块使用指南.md)

> 旧 document 栈（HTML-like 文档树 / CSS-like 样式表 / 远程文档页）已随 breaking major 整体删除，
> 对应的入门示例与控件教程（最小文档页面、完整业务页面示例、基础控件、表格与背包槽位、远程页面、
> 远程 HUD 浮窗）已移除；其 API 不再可用，历史版本以 git 记录为准。

## 核心要点

- 使用 Java API 构建 UI（scene 树 + 响应式 signal），不是编写 HTML/CSS 文件。
- 配置页走 `ConfigUI.buildScreen(...)`（scene 新栈）；本 mod 样板见 `ModernConfigEntry`。
- 页面与宿主能力以当前源码与 [稳定 API 清单](v4.x-LTS-稳定API清单.md) 为准。
- ItemStack 视觉只使用 `HostImageSource.itemIcon(ItemStack)`（icon-only 合同），完整 item seam 见
  [物品视觉渲染接缝](../开发者文档/规格文档/物品视觉渲染接缝.md)。
- 双端通信通过 `NetService` 注册 Channel / Fetch / Stream / Store。
- 调试入口为 `/qzuilib <test|modernconfig>`（测试场地 + 配置页），均属 internal 调试设施，不构成对外稳定 API。

## 相关文档

- [开发者文档](../开发者文档/README.md)（架构图、规格文档、发布流程）
