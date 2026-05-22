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

- Minecraft 1.7.10 + Forge 10.13.4.1614
- GTNH 生态（GTNHLib、lwjgl3ify）
- Java 8 运行时（源码使用 Jabel 支持现代语法）

## 文档

| 文档 | 说明 |
|------|------|
| [使用文档](docs/使用文档/README.md) | 面向接入开发者的入门指南、控件、宿主集成 |
| [开发者文档](docs/开发者文档/README.md) | 面向框架维护者的内部架构、审查、错误记录 |

完整文档导航见 [docs/README.md](docs/README.md)。

## 构建

```powershell
# 设置 Gradle Home（Windows 中文路径环境必须）
$env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"

# 编译
./gradlew.bat compileJava

# 测试
./gradlew.bat test

# 启动客户端
./gradlew.bat runClient21
```

## 许可证

见 [LICENSE](LICENSE)。
