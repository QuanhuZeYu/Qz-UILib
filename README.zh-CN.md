# Qz UILib

[English](README.md) | [简体中文](README.zh-CN.md)

面向 Minecraft 1.7.10 / GTNH / LWJGL3ify 环境的 **scene 新栈** UI 框架。

## 简介

Qz UILib 提供声明式 scene UI 栈：signal → 脏标 → layout → Display List → OpenGL。用 Java API（`SceneRuntime` + `Scene*` 控件 + 宿主桥）建屏。配置页走 Schema + `ConfigUI` + scene 表单壳。

核心特性：

- scene 栈（`ui.scene`：node / layout / paint / runtime / input / control / form / host）
- 响应式 signal 与 keyed 列表复用
- 内建 scene 控件（按钮、开关、输入、选择、滑条、列表、表格等）
- 现代化配置页（`ConfigUI` / `ConfigScreen` / FieldRenderer）
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

- [使用文档](docs/使用文档/README.md)

> 请勿再把已移除的 HTML-like / `UiDocument` / CSS 当作主路径。

## 环境要求

**运行时目标**

- Minecraft 1.7.10 + Forge 10.13.4.1614
- GTNH 生态（GTNHLib、lwjgl3ify）
- 运行时为 Java 8 字节码（Jabel 将现代 Java 语法降级到 Java 8 字节码）

**编译工具链**

- 编译使用 JDK 25（`.java-version` 指定；Gradle 通过 toolchain 自动下载）
- Gradle 9.3.1，由 wrapper 提供（无需单独安装 Gradle）

## 环境搭建

首次搭建注意事项，尤其针对 Windows 用户名含非 ASCII 字符的情况：

1. **JDK 25** —— `.java-version` 固定使用 Java 25 编译。Gradle 的 toolchain 支持会尝试自动下载；若网络受限下载失败，请手动安装 JDK 25，Gradle 会自动识别。
2. **Gradle** —— 一律使用仓库自带的 `gradlew.bat`（Windows）/ `./gradlew`（Unix）。wrapper 已锁定 9.3.1，无需单独安装 Gradle。
3. **Windows 下的 `GRADLE_USER_HOME`** —— 若 Windows 用户名或其家目录路径含非 ASCII 字符、空格或其他特殊字符，请在启动 agent 之前、于 agent 外部将其预先配置为纯 ASCII 路径。agent 只验证既有值，不修改环境。
4. **GTNH Maven 可达性** —— 首次构建会从 `nexus.gtnewhorizons.com`（GTNH 整合包）拉取大量依赖。在该主机不可达或访问缓慢的网络环境下，可能出现长时间同步或超时；离线回退方案见 `docs/反馈层/errors/ERROR-elytra-offline-manifest-cache.md`。

## 文档

| 文档 | 说明 |
|------|------|
| [使用文档](docs/使用文档/README.md) | 面向接入开发者的入门指南、控件、宿主集成 |
| [配置页（ModernConfig）](docs/使用文档/02-控件/配置页（ModernConfig）.md) | 配置页接入指南 |
| [开发者文档](docs/开发者文档/README.md) | 面向框架维护者的内部架构、规格与排障记录 |

完整文档导航见 [docs/README.md](docs/README.md)。

## 构建

编译、测试与实机运行命令集中在 [docs/README.md](docs/README.md) 的「稳定命令与排障」一节，文档导航与排障入口同页；环境前置（JDK 25、Gradle wrapper、`GRADLE_USER_HOME`、GTNH Maven 可达性）见上文「环境搭建」。

## 许可证

见 [LICENSE](LICENSE)。
