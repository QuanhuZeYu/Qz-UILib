# Qz-UILib 文档

[项目首页](../README.zh-CN.md) / 文档

项目唯一规格权威：[UILib 核心职责与交互规格](<superpowers/specs/2026-10-07-uilib-core-contract-design.md>)。下列使用、架构及排障资料仅描述接入或实现，不独立定义要求。

## 从这里开始

| 你要做什么 | 阅读入口 |
| --- | --- |
| 首次接入，了解能力与 API 边界 | [使用文档](使用文档/README.md) |
| 构建配置页、自定义界面或 HUD | [按任务接入](使用文档/README.md#按任务接入) |
| 理解或修改框架实现 | [开发者文档](开发者文档/README.md) → [架构图集](开发者文档/架构图/README.md) |
| 查设计契约或待实施方案 | [规格文档](开发者文档/规格文档/README.md)（唯一 spec 与调查证据入口） |
| 编译、测试或排查故障 | [稳定命令与排障](#稳定命令与排障) · [排障与反馈](反馈层/README.md) |
| 准备发布 | [发布流程](开发者文档/发布流程.md) |
| 追溯旧方案、审查与验收 | [历史报告](历史报告/README.md) |

## 文档分区

| 分区 | 读者与内容 | 阅读边界 |
| --- | --- | --- |
| [使用文档](使用文档/README.md) | 接入开发者：入门、控件、宿主集成、配置与网络、诊断工具 | 接入现状；目标与兼容政策由 spec 和明确授权决定 |
| [开发者文档](开发者文档/README.md) | 框架维护者：架构图、专题参考、规格与发布流程 | 按唯一 spec 维护，实现资料不证明已满足规格 |
| [排障与反馈](反馈层/README.md) | 故障排查者：跨模块复盘与存量踩坑 | 历史定位证据，不构成现行规范 |
| [历史报告](历史报告/README.md) | 需要追溯背景的维护者：规划、施工、审查、验收与任务笔记 | 只证明当时做过什么、验证到什么程度 |

## 文档职责分工

行为和验收以唯一 spec 为准；代码、配置与验证结果提供现状事实。协作流程见 [AGENTS.md](<../AGENTS.md>)。历史材料、旧编号、源码注释及测试不能成为第二套规范。

导航使用相对链接；局部注释解释实现原因，涉及目标或核心边界的认可变化回写同一 spec。

## 稳定命令与排障

以下是通用构建命令的唯一维护处。环境前置见 [环境搭建](../README.zh-CN.md#环境搭建)；headless 等专项工具命令在各自指南维护。发布前置条件见 [发布流程](开发者文档/发布流程.md)。

在仓库根目录执行，Windows 使用自带的 Gradle wrapper：

| 任务 | 命令 |
| --- | --- |
| 编译 | `gradlew.bat --no-configuration-cache compileJava` |
| 单元测试 | `gradlew.bat --no-configuration-cache test` |
| 完整构建（发布前必须全绿） | `gradlew.bat --no-configuration-cache build` |
| 客户端实机验证 | `gradlew.bat --no-configuration-cache runClient21` |

Unix 将 `gradlew.bat` 替换为 `./gradlew`。协作 Agent 的命令入口与实机运行授权遵循 [协作规范](../AGENTS.md)。

- 继承统一基类编写自动化测试：见 [测试应用](开发者文档/测试应用.md)。
- 不启动游戏验证 UI：见 [headless 出图指南](使用文档/headless出图指南.md)。
- 离线构建卡在 GTNH convention 的 manifest 读取：见 [离线 manifest 缓存排查](反馈层/errors/ERROR-elytra-offline-manifest-cache.md)。
- 其他构建、输入、布局与渲染问题：见 [排障与反馈](反馈层/README.md)。
