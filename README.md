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

**Open this mod's own config page (experimental):**

```java
// uilib's own integration sample; see ModernConfigEntry for the full bootstrap
GuiScreen screen = ModernConfigEntry.createScreen(parent);
Minecraft.getMinecraft().displayGuiScreen(screen);
```

**Wire a config page into your own mod:** build a `ConfigScreen` with `ConfigUI.buildScreen(...)` and bridge it to MC `GuiScreen` yourself — integration guide:

- [配置页（ModernConfig）](docs/使用文档/02-控件/配置页（ModernConfig）.md) — config integration guide

**Scene host (custom screens):** use `AbstractSceneHostWidget` / `McScreenBridge` with `SceneRuntime` and `Scene*` controls. Authoritative guide:

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
4. **GTNH Maven reachability** — the first build resolves a large dependency graph from `nexus.gtnewhorizons.com` (the GTNH modpack). On networks where that host is slow or blocked, expect long sync times or timeouts. An offline fallback is documented in `docs/反馈层/errors/ERROR-elytra-offline-manifest-cache.md`.

## Documentation

| Document | Description |
|----------|-------------|
| [Usage Docs (Chinese)](docs/使用文档/README.md) | Onboarding guide, controls and host integration for integrators |
| [ModernConfig](docs/使用文档/02-控件/配置页（ModernConfig）.md) | Config page integration guide |
| [Developer Docs (Chinese)](docs/开发者文档/README.md) | Internal architecture, specs, and troubleshooting records for framework maintainers |

Full documentation index: [docs/README.md](docs/README.md).

> Note: detailed documentation is currently authored in Simplified Chinese.

## Build

Compile, test and in-game run commands are maintained in one place: see the 「稳定命令与排障」 section of [docs/README.md](docs/README.md), which also holds the troubleshooting entry points. Environment prerequisites (JDK 25, Gradle wrapper, `GRADLE_USER_HOME`, GTNH Maven reachability) are listed in Environment Setup above.

## License

See [LICENSE](LICENSE).
