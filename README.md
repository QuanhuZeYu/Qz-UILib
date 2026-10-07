# Qz UILib

> 规格与验收以 [唯一 spec](<docs/superpowers/specs/2026-10-07-uilib-core-contract-design.md>) 为准。本页及所链接的实现、接入和历史资料不另立要求，旧“现行规范”标签及历史编号无规范效力。

[English](README.md) | [简体中文](README.zh-CN.md)

A scene-stack UI framework for Minecraft 1.7.10 / GTNH / LWJGL3ify.

[Documentation](docs/README.md) · [Integration](docs/使用文档/README.md) · [Maintenance](docs/开发者文档/README.md) · [Troubleshooting](docs/反馈层/README.md)

## Overview

Qz UILib provides a declarative **scene** UI stack for Minecraft modding: signals → dirty marks → layout → Display List → OpenGL. Build screens with Java APIs (`SceneRuntime` + `Scene*` controls + host bridges). Configuration pages use Schema + `ConfigUI` + scene form shells.

Highlights:

- Scene stack (`ui.scene`: node / layout / paint / runtime / input / control / form / host / overlay / text / theme / image)
- Reactive signals and keyed list reuse
- Built-in scene controls (button, toggle, text input, select, slider, list, data table, …)
- Modern config pages (`ConfigUI` / `ConfigScreen` / field renderers)
- Passive client HUD (virtual window) with a built-in layout-edit mode
- Host bridge with one logical-box / GUI-scale conversion point (`HostViewportScale`)
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

- [Minecraft 界面入口](docs/使用文档/03-宿主集成/Minecraft界面入口.md) — host integration; index: [使用文档](docs/使用文档/README.md)

> The HTML-like / `UiDocument` / CSS stack was **removed entirely** in the 4.x breaking major and has no replacement entry point — build screens with the scene APIs above.

## Getting the library

Artifacts are distributed as **GitHub Release assets**; there is no remote Maven / JitPack coordinate to depend on:

- Each release ships 4 jars: `qz_uilib-<tag>.jar` (runtime), `qz_uilib-<tag>-dev.jar` (compile-time dev jar), `-sources.jar`, `-dev-preshadow.jar`.
- Downstream mods consume them as local file dependencies or through a private Maven — see the `libs/` layout used by the downstream Qz-Miner mod as a working example.
- Declare the runtime requirement in your own `@Mod` as `required-after:qz_uilib@[<lower>,<upper>)`, with a range that stays inside this library's `acceptableRemoteVersions`.

## Requirements

**Runtime target**

- Minecraft 1.7.10 with Forge 10.13.4.1614
- Java 8 bytecode (Jabel lowers modern Java syntax); the GTNH lwjgl3ify form runs the client on Java 21 (`runClient21`)
- GTNHLib sits on the development classpath only and is not a runtime dependency of the published jar
- LWJGL3ify is optional: when present, its enhanced text-input bridge is picked up by reflection
- Mixins are enabled (`usesMixins = true`), so UniMixins must be present at runtime

**Build toolchain**

- JDK 25 for compilation (CI pins temurin 25; `.java-version` is an editor/jenv hint and is not read by the build scripts)
- Gradle 9.3.1 via the wrapper (no separate Gradle install required)

## Environment Setup

Notes for first-time setup, especially on Windows with a non-ASCII username:

1. **JDK 25** — compilation needs a Java 25 toolchain (CI pins temurin 25); if the build cannot find one, install JDK 25 on your machine.
2. **Gradle** — always invoke the bundled `gradlew.bat` (Windows) / `./gradlew` (Unix). The wrapper is pinned to 9.3.1, so there is no need to install Gradle separately.
3. **`GRADLE_USER_HOME` on Windows** — if your Windows username or its home path contains non-ASCII characters, spaces, or other special characters, set `GRADLE_USER_HOME` to a clean ASCII path before running the build.
4. **GTNH Maven reachability** — the first build resolves a large dependency graph from `nexus.gtnewhorizons.com` (the GTNH modpack). On networks where that host is slow or blocked, expect long sync times or timeouts. See the [offline manifest cache guide](docs/反馈层/errors/ERROR-elytra-offline-manifest-cache.md) for a fallback.

## Documentation

| Document | Description |
|----------|-------------|
| [Usage Docs (Chinese)](docs/使用文档/README.md) | Onboarding guide, controls and host integration for integrators |
| [ModernConfig](docs/使用文档/02-控件/配置页（ModernConfig）.md) | Config page integration guide |
| [Public API stability list (Chinese)](docs/使用文档/公共API稳定清单.md) | What integrators may rely on, and what is explicitly unstable |
| [Developer Docs (Chinese)](docs/开发者文档/README.md) | Architecture, specifications, and validation entry points for framework maintainers |
| [Troubleshooting (Chinese)](docs/反馈层/README.md) | Guides and historical incident records grouped by symptom |

Full documentation index: [docs/README.md](docs/README.md).

> Note: detailed documentation is currently authored in Simplified Chinese.

## Build

Compile, test and in-game run commands are maintained in one place: see [build commands and troubleshooting](docs/README.md#稳定命令与排障), which also holds the troubleshooting entry points. Environment prerequisites (JDK 25, Gradle wrapper, `GRADLE_USER_HOME`, GTNH Maven reachability) are listed in Environment Setup above.

## License

See [LICENSE](LICENSE).
