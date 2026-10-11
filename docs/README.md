# Qz-UILib 文档

[项目首页](<../README.zh-CN.md>) / 文档

从[规格入口](<开发者文档/规格文档/README.md>)开始。核心定义跨模块合同，模块定义所属能力与验收；实现与测试按规格推进，接入指南不另立要求。

| 任务 | 入口 |
| --- | --- |
| 确认目标与验收 | [核心与模块规格](<开发者文档/规格文档/README.md>) |
| 接入正式页面与控件 | [接入指南](<使用文档/README.md>) |
| 修改框架和编写场景 | [开发者文档](<开发者文档/README.md>) |
| 执行人工验收 | [双实例清单](<开发者文档/标准控件双实例验证清单.md>) |
| 多行编辑与本轮字体调整验收 | [多行双实例清单](<开发者文档/多行编辑接入与双实例验证.md>) |
| 版本与发布操作 | [发布流程](<开发者文档/发布流程.md>) |

旧教程、架构图、反向规格与施工记录已从工作树移除，需要追溯时使用Git历史。规格中的目标不等于全部能力已经实现；实际入口与验证边界从源码和运行证据核对。

## 构建维护入口

[根构建脚本](<../build.gradle.kts>)组织职责模块：

| 职责 | 脚本 |
| --- | --- |
| 字体依赖、运行时资源与LWJGL2源集 | [font-runtime](<../gradle/font-runtime.gradle.kts>) |
| 独立核心与字体适配生产源集、产物及宿主依赖 | [core](<../gradle/core.gradle.kts>) |
| 自动验收、隔离编译与宿主类路径 | [verification](<../gradle/verification.gradle.kts>) |
| 人工包、正式包排除与产物隔离 | [packaging](<../gradle/packaging.gradle.kts>) |
| headless native与类路径导出 | [headless](<../gradle/headless.gradle.kts>) |

同一核心scene隔离编译单元的场景在verification的coreSceneDeclarations登记；编译和验收依赖由该声明派生。测试接入见[测试应用](<开发者文档/测试应用.md>)。

## 稳定命令与排障

在仓库根目录使用自带Gradle wrapper，Windows为gradlew.bat，Unix为./gradlew。环境见[项目首页](<../README.zh-CN.md#环境搭建>)；协作与实机运行授权见[AGENTS](<../AGENTS.md>)。

| 任务 | 命令 |
| --- | --- |
| 完整构建 | `gradlew.bat --no-configuration-cache build` |
| 核心与MC成品隔离验收 | `gradlew.bat --no-configuration-cache coreArtifactScenario mcArtifactIsolationScenario` |
| 无平台核心场景 | `gradlew.bat --no-configuration-cache coreSceneCheck` |
| 真实GPU字体与页面场景 | `gradlew.bat --no-configuration-cache clientFontServicesScenario` |
| LWJGL2宿主场景 | `gradlew.bat --no-configuration-cache lwjgl2HostScenario` |
| SDL原生文本观察与回调资源 | `gradlew.bat --no-configuration-cache sdlTextInputScenario` |
| 多行正式编辑 | `gradlew.bat --no-configuration-cache multilineEditingScenario` |
| 人工验收包 | `gradlew.bat --no-configuration-cache manualTestJar` |

构建在 `build/libs` 分别生成 `*-core.jar`、`*-font-adapter.jar` 与 MC 宿主发行包；MC 安装须同时提供三者，人工包同样依赖核心和字体适配。核心产物可独立用于配置、状态、布局与绘制合同；CPU 字体 native 能力由字体适配提供，MC GPU 和原生输入接入留在宿主。CI/发布策略尚未调整，不应把旧的单个宿主下载当作完整安装组合。

MC 首次启动创建原配置同目录、同基本名的 `.properties` 文件；已有旧 Forge 配置仅在新文件不存在时一次迁移。修改新文件后使用 F3+T 资源重载。`mc.replaceVanillaFont` 是 boolean；`font.candidates.count` 和从 `font.candidates.0` 开始的索引项表示完整有序来源，值使用 `builtin:<内置来源id>` 或 `file:<字体文件路径>`；生成文件包含当前默认来源，`count=0` 表示明确空表。配置能力与错误边界见[配置规格](<superpowers/specs/2026-10-11-uilib-config-spec.md>)，不在指南重复定义。

设备场景需实际GL环境，CPU通过不替代GPU或Minecraft实机验收。构建异常先核对当前任务日志与脚本，不用旧架构或历史测试顺序推导行为。
