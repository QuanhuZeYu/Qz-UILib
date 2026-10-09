# Qz UILib

面向Minecraft 1.7.10 / GTNH的声明式UI框架，按无平台核心、LWJGL宿主、Minecraft外层推进。

[文档首页](<docs/README.md>) · [规格入口](<docs/开发者文档/规格文档/README.md>) · [接入指南](<docs/使用文档/README.md>)

## 当前实现

正式页面使用ScenePage与ScenePageSession，Minecraft入口为MinecraftSceneScreen。响应式状态、统一布局/裁剪/命中/绘制、正式按钮与标签、运行时浅深主题、焦点导航和最小滚动显露已接入。文字使用生产字体服务，宿主处理framebuffer像素与原生输入事实。

- [声明式页面接入](<docs/开发者文档/声明式页面接入.md>)
- [标准控件与动态主题](<docs/开发者文档/标准控件与动态主题.md>)
- [测试应用](<docs/开发者文档/测试应用.md>)

核心与模块规格是目标及验收依据，不代表所有模块已完成。编辑/IME、其它控件及外围专题继续按对应规格建设；旧版本教程不构成当前API或兼容承诺。

## 获取与依赖

制品与版本从当前构建、Git和Release核对。产物隔离由[打包脚本](<gradle/packaging.gradle.kts>)维护；版本及发布操作见[发布流程](<docs/开发者文档/发布流程.md>)。本轮重构没有发布新的稳定版本。

## 环境搭建

使用仓库自带Gradle wrapper，编译工具链由[构建脚本](<build.gradle.kts>)、[字体运行时脚本](<gradle/font-runtime.gradle.kts>)与[wrapper配置](<gradle/wrapper/gradle-wrapper.properties>)确定。首次构建需要对应JDK与依赖仓库可达；离线构建需要已有依赖缓存。

生产目标为Java8字节码，字体内部运行时与真实宿主验收的Java/native需求按构建脚本核对。Minecraft与LWJGL设备测试需要相应实际环境。

## 构建

命令集中在[文档首页](<docs/README.md#稳定命令与排障>)；实机验证清单见[双实例验证](<docs/开发者文档/标准控件双实例验证清单.md>)。协作边界见[AGENTS](<AGENTS.md>)。

## 许可证

见[LICENSE](<LICENSE>)。
