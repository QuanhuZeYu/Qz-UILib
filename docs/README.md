# Qz-UILib 文档导航

本文件是 Qz-UILib 文档的顶层索引与**真源优先级声明**。各分区独立维护，不互相复制内容。

## 分区

| 分区 | 面向 | 入口 |
|------|------|------|
| 使用文档 | 接入本库的 Mod 开发者 | [使用文档/README.md](使用文档/README.md) |
| 开发者文档 | 框架维护者与协作 Agent | [开发者文档/README.md](开发者文档/README.md) |
| 架构图集 | 需要先建立整体心智的维护者 | [开发者文档/架构图/README.md](开发者文档/架构图/README.md) |
| 规格文档 | 局部页面与专项能力的现行规范 | [开发者文档/规格文档/README.md](开发者文档/规格文档/README.md) |
| 踩坑记录 | 排查同类故障 | [反馈层/errors/](反馈层/errors/) |

## 真源优先级

文档之间冲突时按此顺序裁决，上位真源压过下位：

1. **实时源码及其注释** —— 唯一实现真源。行为、契约边界、失败语义与踩坑根因以最近的代码处注释为准。
2. **[AGENTS.md](../AGENTS.md)** —— 工程边界、协作规范与高影响安全边界。
3. **[开发者文档/规格文档/](开发者文档/规格文档/)** —— 经裁定的现行语义母本（scene 基础 API、宿主投影语义、物品渲染接缝、网络层方案等）。
4. **[使用文档/](使用文档/)** —— 对外接入方式与 [稳定 API 清单](使用文档/v4.x-LTS-稳定API清单.md)。
5. **[开发者文档/架构图/](开发者文档/架构图/)** —— 结构说明；图与源码冲突时以源码为准。
6. **[反馈层/errors/](反馈层/errors/)** —— 历史踩坑记录与处置结论，本身不构成新的现行规范。
7. **专项施工记录**（规划、任务单、冻结契约、逐实例台账、验收记录、施工手册、架构审查）—— 只证明「当时做过什么、验证到什么程度」，**不得作为当前施工依据**。

独立文档只承载最上层设计思想与长期有效边界；实现细节、单个类的行为说明、一次性施工结论写在最近的代码注释处。

## 稳定命令与排障

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
