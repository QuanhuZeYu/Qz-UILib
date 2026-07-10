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

**配置页（本 mod 推荐入口）：**

```java
// 完整 bootstrap 见 ModernConfigEntry / 使用文档
GuiScreen screen = ModernConfigEntry.createScreen(parent);
Minecraft.getMinecraft().displayGuiScreen(screen);
```

**自定义 scene 屏：** 使用 `AbstractSceneHostWidget` / `McScreenBridge` + `SceneRuntime` + `Scene*` 控件。权威文档：

- [配置页（ModernConfig）](docs/使用文档/02-控件/配置页（ModernConfig）.md) — **唯一**配置接入文档
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
3. **Windows 下的 `GRADLE_USER_HOME`** —— 若 Windows 用户名或其家目录路径含非 ASCII 字符、空格或其他特殊字符，*必须*将 `GRADLE_USER_HOME` 指向纯 ASCII 路径，否则 Java 8 Gradle Worker 会崩溃。请在 shell 环境中自行设置该变量（指向你选择的 ASCII 路径即可）：
   ```powershell
   # 示例：在 PowerShell 中临时设置（路径请按你的环境调整）
   $env:GRADLE_USER_HOME="<你的纯 ASCII 路径>/gradle-home"
   ```
4. **GTNH Maven 可达性** —— 首次构建会从 `nexus.gtnewhorizons.com`（GTNH 整合包）拉取大量依赖。在该主机不可达或访问缓慢的网络环境下，可能出现长时间同步或超时；离线回退方案见 `docs/控制律层/稳定命令.md`。

## 文档

| 文档 | 说明 |
|------|------|
| [使用文档](docs/使用文档/README.md) | 面向接入开发者的入门指南、控件、宿主集成 |
| [配置页（ModernConfig）](docs/使用文档/02-控件/配置页（ModernConfig）.md) | **唯一**配置页接入文档 |
| [开发者文档](docs/开发者文档/README.md) | 面向框架维护者的内部架构、审查、错误记录 |

完整文档导航见 [docs/README.md](docs/README.md)。

## 构建

```powershell
# 若用户路径含非 ASCII 字符，需先将 GRADLE_USER_HOME 指向纯 ASCII 路径（路径请按你的环境调整）
# $env:GRADLE_USER_HOME="<你的纯 ASCII 路径>/gradle-home"

# 编译
./gradlew.bat --no-configuration-cache compileJava

# 测试
./gradlew.bat --no-configuration-cache test

# 启动客户端（游戏内验证，lwjgl3ify 运行时）
./gradlew.bat --no-configuration-cache runClient21
```

权威、维护最新的命令清单与排障（离线模式、日志位置、MCP 映射目录弹窗等）见 `docs/控制律层/稳定命令.md`。

## 许可证

见 [LICENSE](LICENSE)。
