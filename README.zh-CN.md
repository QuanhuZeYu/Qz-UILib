# Qz UILib

[English](README.md) | [简体中文](README.zh-CN.md)

面向 Minecraft 1.7.10 / GTNH / LWJGL3ify 环境的 **scene 新栈** UI 框架。

## 简介

Qz UILib 提供声明式 scene UI 栈：signal → 脏标 → layout → Display List → OpenGL。用 Java API（`SceneRuntime` + `Scene*` 控件 + 宿主桥）建屏。配置页走 Schema + `ConfigUI` + scene 表单壳。

核心特性：

- scene 栈（`ui.scene`：node / layout / paint / runtime / input / control / form / host / overlay / text / theme / image）
- 响应式 signal 与 keyed 列表复用
- 内建 scene 控件（按钮、开关、输入、选择、滑条、列表、表格等）
- 现代化配置页（`ConfigUI` / `ConfigScreen` / FieldRenderer）
- 被动客户端 HUD（虚拟窗口）与内置布局编辑模式
- 宿主桥：逻辑盒 ↔ GUI Scale 的单一换算处（`HostViewportScale`）
- 自定义字体渲染管线
- 网络与主线程派发辅助

## 快速开始

**打开本库自己的配置页（实验性）：**

```java
// 本库自身接入样板；完整 bootstrap 见 ModernConfigEntry
GuiScreen screen = ModernConfigEntry.createScreen(parent);
Minecraft.getMinecraft().displayGuiScreen(screen);
```

**给自己的 Mod 接入配置页：** 用 `ConfigUI.buildScreen(...)` 构建 `ConfigScreen`，自行桥接为 MC `GuiScreen`。接入指南：

- [配置页（ModernConfig）](docs/使用文档/02-控件/配置页（ModernConfig）.md) — 配置页接入指南

**自定义 scene 屏：** 使用 `AbstractSceneHostWidget` / `McScreenBridge` + `SceneRuntime` + `Scene*` 控件。权威文档：

- [Minecraft 界面入口](docs/使用文档/03-宿主集成/Minecraft界面入口.md) — 宿主集成；索引：[使用文档](docs/使用文档/README.md)

> HTML-like / `UiDocument` / CSS 那一套已在 4.x breaking major 中**整体删除**，没有留下替代入口——界面一律用上面的 scene API 构建。

## 获取与依赖

制品以 **GitHub Release 资产**形式分发，不提供可依赖的远程 Maven / JitPack 坐标：

- 每次发布包含 4 个 jar：`qz_uilib-<tag>.jar`（运行时）、`qz_uilib-<tag>-dev.jar`（编译期 dev jar）、`-sources.jar`、`-dev-preshadow.jar`。
- 下游以本地文件依赖或自建私有 Maven 接入；可参照下游 Qz-Miner 的 `libs/` 目录写法。
- 在自己的 `@Mod` 中声明 `required-after:qz_uilib@[<下界>,<上界>)`，区间需落在本库 `acceptableRemoteVersions` 的语义内。

## 环境要求

**运行时目标**

- Minecraft 1.7.10 + Forge 10.13.4.1614
- 产物为 Java 8 字节码（Jabel 将现代 Java 语法降级）；GTNH 的 lwjgl3ify 形态下客户端以 Java 21 运行（`runClient21`）
- GTNHLib 只在开发期 classpath 上，不是发布产物的运行时依赖
- LWJGL3ify 可选：存在时由反射接入其增强文本输入桥
- 启用了 Mixin（`usesMixins = true`），运行时需要 UniMixins

**编译工具链**

- 编译使用 JDK 25（CI 固定 temurin 25；`.java-version` 供编辑器/jenv 参考，构建脚本不读取）
- Gradle 9.3.1，由 wrapper 提供（无需单独安装 Gradle）

## 环境搭建

首次搭建注意事项，尤其针对 Windows 用户名含非 ASCII 字符的情况：

1. **JDK 25** —— 编译需要 JDK 25（CI 固定 temurin 25）；若构建找不到可用的 JDK 25，请在本机安装。
2. **Gradle** —— 一律使用仓库自带的 `gradlew.bat`（Windows）/ `./gradlew`（Unix）。wrapper 已锁定 9.3.1，无需单独安装 Gradle。
3. **Windows 下的 `GRADLE_USER_HOME`** —— 若 Windows 用户名或其家目录路径含非 ASCII 字符、空格或其他特殊字符，请在首次构建前将其配置为纯 ASCII 路径。
4. **GTNH Maven 可达性** —— 首次构建会从 `nexus.gtnewhorizons.com`（GTNH 整合包）拉取大量依赖。在该主机不可达或访问缓慢的网络环境下，可能出现长时间同步或超时；离线回退方案见 `docs/反馈层/errors/ERROR-elytra-offline-manifest-cache.md`。

## 文档

| 文档 | 说明 |
|------|------|
| [使用文档](docs/使用文档/README.md) | 面向接入开发者的入门指南、控件、宿主集成 |
| [配置页（ModernConfig）](docs/使用文档/02-控件/配置页（ModernConfig）.md) | 配置页接入指南 |
| [公共 API 稳定清单](docs/使用文档/公共API稳定清单.md) | 接入方可以依赖什么、什么明确不稳定 |
| [开发者文档](docs/开发者文档/README.md) | 面向框架维护者的内部架构、规格与排障记录 |

完整文档导航见 [docs/README.md](docs/README.md)。

## 构建

编译、测试与实机运行命令集中在 [docs/README.md](docs/README.md) 的「稳定命令与排障」一节，文档导航与排障入口同页；环境前置（JDK 25、Gradle wrapper、`GRADLE_USER_HOME`、GTNH Maven 可达性）见上文「环境搭建」。

## 许可证

见 [LICENSE](LICENSE)。
