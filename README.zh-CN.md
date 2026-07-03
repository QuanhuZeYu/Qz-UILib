# Qz UILib

[English](README.md) | [简体中文](README.zh-CN.md)

面向 Minecraft 1.7.10 / GTNH / LWJGL3ify 环境的 HTML-like UI 框架。

## 简介

Qz UILib 提供类似 Web 的文档式 UI 开发体验：通过 Java API 构建 DOM 树、声明 CSS-like 样式、注册 DOM 事件，由框架负责布局计算、绘制命令生成和 OpenGL 渲染。

核心特性：

- DOM-like 文档树（`UiDocument` / `ElementNode` / `TextNode`）
- CSS-like 样式系统（选择器、层叠、伪类、伪元素）
- Flex / Block / Table / Inline 布局引擎
- Transition / Keyframe 动画系统
- 完整 DOM 事件模型（capture → target → bubble）
- 内建控件（按钮、输入框、文本域、选择器、表格、物品栏槽位等）
- 自定义字体渲染管线
- HUD 文档层支持
- 背景模糊、圆角裁剪、box-shadow 等视觉效果

## 快速开始

```java
GuiScreen screen = UiDocumentScreens.createDocumentScreen(document -> {
    ElementNode root = document.getRootElement();
    root.style()
            .setPadding(UiStyleLength.px(16))
            .setBackgroundColor(0xE0101420)
            .setTextColor(0xFFE5E7EB);

    ElementNode title = document.element("h1");
    title.appendText("Hello Qz UILib");
    root.append(title);
});

Minecraft.getMinecraft().displayGuiScreen(screen);
```

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
