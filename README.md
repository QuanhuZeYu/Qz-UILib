# Qz UILib

[English](README.md) | [简体中文](README.zh-CN.md)

A scene-stack UI framework for Minecraft 1.7.10 / GTNH / LWJGL3ify.

## Overview

Qz UILib provides a declarative **scene** UI stack for Minecraft modding: signals → dirty marks → layout → Display List → OpenGL. Build screens with Java APIs (`SceneRuntime` + `Scene*` controls + host bridges). Configuration pages use Schema + `ConfigUI` + scene form shells.

Highlights:

- Scene stack (`ui.scene`: node / layout / paint / runtime / input / control / form / host)
- Reactive signals and keyed list reuse
- Built-in scene controls (button, toggle, text input, select, slider, list, data table, …)
- Modern config pages (`ConfigUI` / `ConfigScreen` / field renderers)
- Custom font rendering pipeline
- Network transport helpers (main-thread dispatcher, channels)

## Quick Start

**Configuration page (recommended entry for this mod):**

```java
// See ModernConfigEntry / docs for full bootstrap
GuiScreen screen = ModernConfigEntry.createScreen(parent);
Minecraft.getMinecraft().displayGuiScreen(screen);
```

**Scene host (custom screens):** use `AbstractSceneHostWidget` / `McScreenBridge` with `SceneRuntime` and `Scene*` controls. Authoritative guide:

- [配置页（ModernConfig）](docs/使用文档/02-控件/配置页（ModernConfig）.md) — **unique** config integration doc
- [使用文档](docs/使用文档/README.md)

> Do **not** use removed HTML-like / `UiDocument` / CSS APIs as the primary path.

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
3. **`GRADLE_USER_HOME` on Windows** — if your Windows username or its home path contains non-ASCII characters, spaces, or other special characters, configure `GRADLE_USER_HOME` to a clean ASCII path outside the agent before starting it. Agents only verify the existing value and never modify the environment.
4. **GTNH Maven reachability** — the first build resolves a large dependency graph from `nexus.gtnewhorizons.com` (the GTNH modpack). On networks where that host is slow or blocked, expect long sync times or timeouts. An offline fallback is documented in `docs/控制律层/稳定命令.md`.

## Documentation

| Document | Description |
|----------|-------------|
| [Usage Docs (Chinese)](docs/使用文档/README.md) | Onboarding guide, controls and host integration for integrators |
| [ModernConfig](docs/使用文档/02-控件/配置页（ModernConfig）.md) | **Unique** config page integration doc |
| [Developer Docs (Chinese)](docs/开发者文档/README.md) | Internal architecture, reviews, and error records for framework maintainers |

Full documentation index: [docs/README.md](docs/README.md).

> Note: detailed documentation is currently authored in Simplified Chinese.

## Build

```powershell
# If needed, configure GRADLE_USER_HOME outside the agent before starting it; agents only verify it.

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
