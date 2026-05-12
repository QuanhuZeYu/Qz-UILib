# 使用文档总览

本目录面向准备接入 Qz UILib 的开发者，按入门、控件、宿主集成和诊断入口分级组织文档。

本目录是对外开发使用文档；阶段方案、协作记忆、专项规格和长期规划分别见 `docs/开放化调整.md`、`docs/AI记忆文档.md`、`docs/specs/README.md` 与 `项目建议.md`。

## 阅读顺序

1. `01-入门/项目定位与能力边界.md`
2. `01-入门/最小文档页面.md`
3. `01-入门/完整业务页面示例.md`
4. `02-控件/基础控件.md`
5. `02-控件/表格与背包槽位.md`
6. `02-控件/Forge配置模板.md`
7. `03-宿主集成/Minecraft界面入口.md`
8. `03-宿主集成/Minecraft原版输入链路.md`
9. `04-诊断入口/指令触发方案.md`

## 相关专题

- `../specs/README.md`：示例页专属需求、视觉规格和专项方案索引，不属于通用 UI 接入必读项。
- `03-宿主集成/Minecraft原版输入链路.md`：整理原版 `Minecraft` / `GuiScreen` / `GuiContainer` 输入分发主链，适合排查宿主输入抢占、HUD 键鼠隔离和特殊界面串扰问题。

## 面向对象

- Minecraft 1.7.10 / GTNH / LWJGL3ify 环境下希望接入 HTML-like UI 的 Mod 开发者。
- 希望用 Java API 构建 DOM-like 文档树、样式、控件和页面的开发者。
- 需要在游戏内验证 UI 布局、输入、动画、表格或背包示例页的框架维护者。

## 首版重点

- 使用 Java API 构建 UI，而不是编写 HTML/CSS 文件。
- 优先通过 `UiDocumentScreens.createDocumentScreen(...)` 在宿主入口创建业务界面。
- 页面内容仍通过 `UiDocument`、`ElementNode`、样式和控件组织。
- 诊断页和示例页只作为开发期工具，不作为玩家默认入口。
- 所有测试入口应改为显式指令触发，不再默认注入原版界面或占用全局热键。
