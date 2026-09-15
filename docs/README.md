# Qz-UILib 文档导航

本文件是 Qz-UILib 文档的顶层索引与**文档职责分工**声明。各分区独立维护，不互相复制内容。

## 分区

| 分区 | 面向 | 入口 |
|------|------|------|
| 使用文档 | 接入本库的 Mod 开发者 | [使用文档/README.md](使用文档/README.md) |
| 开发者文档 | 框架维护者与协作 Agent | [开发者文档/README.md](开发者文档/README.md) |
| 架构图集 | 需要先建立整体心智的维护者 | [开发者文档/架构图/README.md](开发者文档/架构图/README.md) |
| 规格文档 | 局部页面与专项能力的现行规范与在办材料 | [开发者文档/规格文档/README.md](开发者文档/规格文档/README.md) |
| 踩坑记录 | 排查同类故障 | [反馈层/errors/](反馈层/errors/)（跨模块单项复盘）· [反馈层/踩坑记录.md](反馈层/踩坑记录.md)（存量合集，只作检索参考，不构成现行规范） |
| 历史报告 | 已交付、已否决或已收口的历史材料与专项记录；只证明当时做过什么、验证到什么程度，不构成现行要求 | [历史报告/README.md](历史报告/README.md) |

## 文档职责分工

文档之间不建立「上位压过下位」的效力排序，各类资料分工如下：实际行为与构建状态以当前源码、配置与验证结果为准；工程边界与协作方式以 [AGENTS.md](../AGENTS.md) 为准；设计要求与契约以标记为现行规范的文档为准——实现与规范不一致时应指出偏差，不能仅因代码已如此实现而视为规范已变更；历史记录（规划、任务单、台账、验收、审查、踩坑）只证明当时做过什么、验证到什么程度，不构成现行要求。

文档保留判据（与 [AGENTS.md](../AGENTS.md) 同口径）：设计意图、契约边界与失败语义写在最近的代码注释处；施工过程与一次性结论不进长期文档，需要跨轮恢复的写短任务笔记；独立文档只保留跨模块设计原则，以及有独立读者的使用、接入、兼容与发布说明。

## 稳定命令与排障

以下命令清单是本库命令的唯一维护处，根 README 只作指引、不重复列出；发布前置条件见发布流程，本文不重复其判定。

```powershell
./gradlew.bat --no-configuration-cache compileJava    # 编译
./gradlew.bat --no-configuration-cache test           # 单元测试
./gradlew.bat --no-configuration-cache build          # 完整构建（发布前必须全绿）
./gradlew.bat --no-configuration-cache runClient21    # 客户端实机验证
```

- 环境前置（JDK 25、Gradle wrapper、`GRADLE_USER_HOME`、GTNH Maven 可达性）见根 [README.md](../README.md)。
- 发布动作、tag 命名与 Release 资产规范见 [开发者文档/发布流程.md](开发者文档/发布流程.md)（发布流程唯一权威）。
- 离线构建在 GTNH convention 配置阶段需要本机 manifest 缓存，失败形态与恢复入口见 [反馈层/errors/ERROR-elytra-offline-manifest-cache.md](反馈层/errors/ERROR-elytra-offline-manifest-cache.md)。
- 其他构建与运行故障按 [反馈层/errors/](反馈层/errors/) 中的同名症状记录排查。
