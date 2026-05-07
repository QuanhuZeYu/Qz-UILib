# Minecraft 界面入口

本文说明在 Minecraft 1.7.10 宿主中打开 Qz UILib 文档页面的建议方式。

本文是宿主集成说明；内置诊断页和示例页仅作为开发调试入口，不构成对外稳定业务 API。

## 业务文档入口

业务 UI 优先通过 `UiDocumentScreens.createDocumentScreen(...)` 创建：

```java
Minecraft.getMinecraft().displayGuiScreen(UiDocumentScreens.createDocumentScreen(document -> {
    ElementNode root = document.getRootElement();
    root.style()
            .setWidth(UiStyleLength.percent(1.0F))
            .setHeight(UiStyleLength.percent(1.0F))
            .setPadding(UiStyleLength.px(16));

    ElementNode title = document.element("h1");
    title.appendText("我的 UI");
    root.append(title);
}));
```

该入口会创建 `UiDocument`、`HtmlLikeDocumentWidget` 和宿主 `GuiScreen`，调用方只负责组装文档树、样式和事件。

## 开屏时序约束

- 普通宿主事件或按钮回调里，可以直接调用 `Minecraft.displayGuiScreen(...)`。
- 如果当前上下文会在本次回调结束后关闭现有 `GuiScreen`，则不要直接开屏。
- 典型场景包括客户端聊天命令、聊天按钮或其他会立即结束当前界面的入口。
- 这类场景应通过 `UiScreenManager.getInstance().enqueue(...)` 延后到当前帧输入分发结束后再执行开屏。

```java
UiScreenManager.getInstance().enqueue(new Runnable() {
    @Override
    public void run() {
        Minecraft.getMinecraft().displayGuiScreen(UiDocumentScreens.createDocumentScreen(document -> {
            // 组装文档树。
        }));
    }
});
```

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
- `createDocumentScreen(...)`：业务文档 screen。

## 首版建议

- 外部开发者应优先通过 `createDocumentScreen(...)` 打开业务 UI。
- 诊断页只作为开发期工具使用，不应作为玩家默认入口。
- 默认不向原版背包注入按钮。
- 默认不注册全局右 Shift 打开诊断菜单。
- 当前测试入口统一为 `/qzuilib test`，并通过 `UiScreenManager` 延后开屏后再由菜单跳转到各内置页面。

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
- 保留 `UiScreenManager.getInstance().tick()` 这条延后任务冲刷路径。
- 只移除全局诊断热键，不移除正常输入分发。
