# Qz UILib

[English](README.md) | [简体中文](README.zh-CN.md)

An HTML-like UI framework for Minecraft 1.7.10 / GTNH / LWJGL3ify.

## Overview

Qz UILib brings a web-like, document-based UI development experience to Minecraft modding. Build DOM trees, declare CSS-like styles and register DOM events through a Java API; the framework handles layout, paint command generation, and OpenGL rendering.

Highlights:

- DOM-like document tree (`UiDocument` / `ElementNode` / `TextNode`)
- CSS-like style system (selectors, cascade, pseudo-classes, pseudo-elements)
- Flex / Block / Table / Inline layout engine
- Transition / Keyframe animation system
- Full DOM event model (capture → target → bubble)
- Built-in controls (buttons, text inputs, text areas, selectors, tables, inventory slots, and more)
- Custom font rendering pipeline
- HUD document layer support
- Backdrop blur, rounded clipping, box-shadow and other visual effects

## Quick Start

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

## Requirements

- Minecraft 1.7.10 with Forge 10.13.4.1614
- GTNH ecosystem (GTNHLib, lwjgl3ify)
- Java 8 runtime (source uses Jabel for modern syntax)

## Documentation

| Document | Description |
|----------|-------------|
| [Usage Docs (Chinese)](docs/使用文档/README.md) | Onboarding guide, controls and host integration for integrators |
| [Developer Docs (Chinese)](docs/开发者文档/README.md) | Internal architecture, reviews, and error records for framework maintainers |

Full documentation index: [docs/README.md](docs/README.md).

> Note: detailed documentation is currently authored in Simplified Chinese.

## Build

```powershell
# Set Gradle Home (required on Windows when the user path contains non-ASCII characters)
$env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"

# Compile
./gradlew.bat compileJava

# Run tests
./gradlew.bat test

# Launch the client
./gradlew.bat runClient21
```

## License

See [LICENSE](LICENSE).
