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

**Runtime target**

- Minecraft 1.7.10 with Forge 10.13.4.1614
- GTNH ecosystem (GTNHLib, lwjgl3ify)
- Java 8 bytecode at runtime (Jabel lowers modern Java syntax to Java 8 bytecode)

**Build toolchain**

- JDK 25 for compilation (pinned in `.java-version`; Gradle auto-downloads it via toolchains)
- Gradle 9.3.1 via the wrapper (no separate Gradle install required)

## Environment Setup

Notes for first-time setup, especially on Windows with a non-ASCII username:

1. **JDK 25** — `.java-version` pins Java 25 for compilation. Gradle's toolchain support will try to download it automatically; if your network blocks that, install JDK 25 manually and Gradle will detect it.
2. **Gradle** — always invoke the bundled `gradlew.bat` (Windows) / `./gradlew` (Unix). The wrapper is pinned to 9.3.1, so there is no need to install Gradle separately.
3. **`GRADLE_USER_HOME` on Windows** — if your Windows username or its home path contains non-ASCII characters, spaces, or other special characters, you *must* redirect `GRADLE_USER_HOME` to a clean ASCII path, otherwise the Java 8 Gradle worker will crash:
   ```powershell
   $env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"
   ```
4. **GTNH Maven reachability** — the first build resolves a large dependency graph from `nexus.gtnewhorizons.com` (the GTNH modpack). On networks where that host is slow or blocked, expect long sync times or timeouts. An offline fallback is documented in `docs/控制律层/稳定命令.md`.

## Documentation

| Document | Description |
|----------|-------------|
| [Usage Docs (Chinese)](docs/使用文档/README.md) | Onboarding guide, controls and host integration for integrators |
| [Developer Docs (Chinese)](docs/开发者文档/README.md) | Internal architecture, reviews, and error records for framework maintainers |

Full documentation index: [docs/README.md](docs/README.md).

> Note: detailed documentation is currently authored in Simplified Chinese.

## Build

```powershell
# Required on Windows when the user path contains non-ASCII characters
$env:GRADLE_USER_HOME="D:\.MyApps\.ENV\gradle-home"

# Compile
./gradlew.bat --no-configuration-cache compileJava

# Run tests
./gradlew.bat --no-configuration-cache test

# Launch the client (in-game validation, lwjgl3ify runtime)
./gradlew.bat --no-configuration-cache runClient21
```

For the authoritative, up-to-date command list and troubleshooting (offline mode, log locations, MCP mapping dialog, etc.), see `docs/控制律层/稳定命令.md`.

## License

See [LICENSE](LICENSE).
