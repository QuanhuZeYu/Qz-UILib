# Minecraft 界面入口

本文说明在 Minecraft 1.7.10 宿主中打开 Qz UILib 文档页面的建议方式。

## 当前内部入口

当前内部页面通过 `UiDocumentScreens` 创建 `GuiScreen`：

```java
Minecraft.getMinecraft().displayGuiScreen(UiDocumentScreens.createHtmlLikeSmoke());
```

已有页面包括：

- `createUiTest()`：诊断菜单。
- `createUiTestLayout()`：布局诊断页。
- `createHtmlLikeSmoke()`：HTML-like Smoke 页。
- `createHtmlLikeGlass()`：Glass Lab 页。
- `createInventoryOverview(...)`：背包概览示例页。

## 首版建议

- 外部开发者应通过明确的 screen 工厂或后续 API 门面打开业务 UI。
- 诊断页只作为开发期工具使用，不应作为玩家默认入口。
- 默认不向原版背包注入按钮。
- 默认不注册全局右 Shift 打开诊断菜单。

## 环境对象

`UiDocumentScreens.DocumentScreenEnvironment` 收敛文本测量与运行时适配器：

```java
UiDocumentScreens.DocumentScreenEnvironment environment =
        UiDocumentScreens.DocumentScreenEnvironment.minecraftDefaults();
```

使用建议：

- 正常游戏内使用 `minecraftDefaults()`。
- 测试中注入确定性 `TextMeasureService`。
- Minecraft 物品、鼠标携带物品等能力通过 `UiRuntimeAdapters` 注入。

## 输入路由

`UiInputTickListener` 负责每帧刷新 `UiInputService` 与 `UiScreenManager`，正式 UI 仍需要这条输入路径。

保留建议：

- 保留 `UiInputService.getInstance().initialize()`。
- 保留 `UiInputTickListener` 注册。
- 只移除全局诊断热键，不移除正常输入分发。
