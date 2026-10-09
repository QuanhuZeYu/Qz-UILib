# 使用文档

> 规格与验收以 [核心及对应模块规格](<../开发者文档/规格文档/README.md>) 为准。本页及所链接的实现、接入和历史资料不另立要求，旧“现行规范”标签及历史编号无规范效力。

[文档首页](../README.md) / 使用文档

面向在 Minecraft 1.7.10 / GTNH / LWJGL3ify 环境中接入 Qz UILib 的 Mod 开发者。使用 Java API、scene 树与响应式 signal 构建界面。

## 首次接入

1. 阅读 [项目定位与能力边界](01-入门/项目定位与能力边界.md)，确认本库适合承担的职责。
2. 按 [获取与依赖](../../README.zh-CN.md#获取与依赖) 准备制品，并核对 [公共 API 稳定清单](公共API稳定清单.md)。
3. 从下表选择要实现的功能；自定义界面先读 [Minecraft 界面入口](03-宿主集成/Minecraft界面入口.md)。

## 按任务接入

| 任务 | 指南 | 解决的问题 |
| --- | --- | --- |
| 创建配置页 | [配置页（ModernConfig）](02-控件/配置页（ModernConfig）.md) | Schema、`ConfigUI.buildScreen(...)` 与宿主桥接 |
| 读写配置数据 | [Config 模块使用指南](Config模块使用指南.md) | 配置数据层的接入与使用 |
| 打开自定义界面、递交聊天 Markdown | [Minecraft 界面入口](03-宿主集成/Minecraft界面入口.md) | scene 宿主与 Minecraft 入口集成 |
| 添加输入框、弹层与提示 | [场景文本输入与浮层](02-控件/场景文本输入与浮层.md) | 文本编辑与浮层能力 |
| 显示富文本 | [富文本标签与 SceneLabel](02-控件/富文本标签与SceneLabel.md) | 富文本内容与标签控件 |
| 显示数学公式 | [LaTeX 命令支持清单](../开发者文档/LaTeX命令支持清单.md) | 支持、近似与字面降级的范围 |
| 添加被动 HUD | [通用被动 HUD](02-控件/通用被动HUD.md) | 虚拟窗口与布局编辑 |
| 接入双端通信 | [网络层入门](02-控件/网络层入门.md) | 通过 `NetService` 使用 Channel / Fetch / Stream / Store |

## 验证与诊断

| 任务 | 入口 |
| --- | --- |
| 不启动游戏渲染 UI | [headless 出图指南](headless出图指南.md) |
| 查玩家命令和开发诊断入口 | [指令触发方案](04-诊断入口/指令触发方案.md) |
| 查看开发期控件演示 | [测试场地](04-诊断入口/测试场地.md) |
| 编译与运行测试 | [稳定命令与排障](../README.md#稳定命令与排障) |
| 排查布局、输入或渲染问题 | [排障与反馈](../反馈层/README.md) |

## 接入边界

- 页面与宿主能力以当前源码与 [公共 API 稳定清单](公共API稳定清单.md) 为准。本库配置页的接入样板为 `ModernConfigEntry`。
- ItemStack 视觉使用 `HostImageSource.itemIcon(ItemStack)`；合同见 物品视觉渲染接缝（旧资料已退役，原文见 Git 历史）。
- 发布产物的玩家命令为 `/qzuilib modernconfig` 与 `/qzuilib chatmd on|off|status`。scene 测试场地等开发设施位于 `internal.devtools`，不随发布 jar 分发。
- 旧 document 栈（HTML-like 文档树、CSS-like 样式表、远程文档页）已随 breaking major 整体删除，相关教程与 API 不再可用；旧版本资料从 Git 历史查阅。

修改框架本身时转到 [开发者文档](../开发者文档/README.md)。
