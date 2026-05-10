# Minecraft 界面入口

本文说明在 Minecraft 1.7.10 宿主中打开 Qz UILib 文档页面的建议方式。

本文是宿主集成说明；内置诊断页和示例页仅作为开发调试入口，不构成对外稳定业务 API。

## 业务文档入口

业务 UI 优先通过 `UiDocumentScreens.createDocumentScreen(...)` 创建：

```java
Minecraft.getMinecraft().displayGuiScreen(UiDocumentScreens.createDocumentScreen(document -> {
    ElementNode root = document.getRootElement();
    root.style()
            .setPadding(UiStyleLength.px(16));

    ElementNode title = document.element("h1");
    title.appendText("我的 UI");
    root.append(title);
}));
```

该入口会创建 `UiDocument`、`HtmlLikeDocumentWidget` 和宿主 `GuiScreen`，调用方只负责组装文档树、样式和事件。

默认还会给根元素补齐 `width:100%`、`height:100%` 和 `overflow-y:auto`；只有需要覆盖默认全视口根滚动时，才显式改这些样式。

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

当前内部诊断页和示例页通过 `UiDiagnosticsScreens` 创建 `GuiScreen`：

```java
Minecraft.getMinecraft().displayGuiScreen(UiDiagnosticsScreens.createHtmlLikeSmoke());
```

已有页面包括：

- `UiDiagnosticsScreens.createUiTest()`：诊断菜单。
- `UiDiagnosticsScreens.createUiTestLayout()`：布局诊断页。
- `UiDiagnosticsScreens.createHtmlLikeSmoke()`：HTML-like Smoke 页。
- `UiDiagnosticsScreens.createHtmlLikeGlass()`：Glass Lab 页。
- `UiDiagnosticsScreens.createInventoryOverview(...)`：背包概览示例页。
- `createDocumentScreen(...)`：业务文档 screen。

## 替换 Forge 配置页

如果宿主模组仍在使用 `IModGuiFactory -> GuiConfig` 这条旧配置入口，可以改为继承
`ForgeConfigTemplateScreen`：

```java
public class ExampleConfigGui extends ForgeConfigTemplateScreen {

    public ExampleConfigGui(GuiScreen parentScreen) {
        super(parentScreen, createSpec());
    }
}
```

接入特点：

- 继续保留 Forge `IModGuiFactory` 注册方式。
- 页面内容改为 HTML-like 文档承载，不再依赖默认 `GuiConfig` 列表页。
- 模板会自动读取 `Configuration` 中的分类与属性。
- 保存动作可通过 `Spec.setSaveHandler(...)` 挂接到宿主自己的 `saveAndReload()` 逻辑。

完整模板说明见 `docs/使用文档/02-控件/Forge配置模板.md`。

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
- Minecraft 物品、宿主图片、鼠标携带物品等能力通过 `UiRuntimeAdapters` 注入。

例如，业务页如果想把 Minecraft 物品当作 `img` 一样挂到文档里，通常不需要自己碰运行时适配器细节；只要页面通过 `createDocumentScreen(...)` 使用默认环境，即可直接使用：

```java
Minecraft.getMinecraft().displayGuiScreen(UiDocumentScreens.createDocumentScreen(document -> {
    ElementNode root = document.getRootElement();
    DocumentHostImageControl icon = new DocumentHostImageControl(document,
            HostImageSource.itemStack(new ItemStack(Items.apple)));
    icon.setSize(20);
    root.append(icon.getElement());
}));
```

## 输入路由

`UiInputTickListener` 负责每帧刷新 `UiInputService` 与 `UiScreenManager`，正式 UI 仍需要这条输入路径。

保留建议：

- 保留 `UiInputService.getInstance().initialize()`。
- 保留 `UiInputTickListener` 注册。
- 保留 `UiScreenManager.getInstance().tick()` 这条延后任务冲刷路径。
- 只移除全局诊断热键，不移除正常输入分发。

## HUD 文档层

如果宿主想在游戏内 HUD 区域承载 HTML-like 内容，可以使用 `UiHudDocumentHost` 注册 HUD 文档层。

当前内置两类层：

- `UiHudLayerType.PASSIVE`：不可交互，只在纯游戏内 HUD 可见；打开背包、箱子、菜单后会隐藏。
- `UiHudLayerType.INTERACTIVE`：游戏内与容器界面可见；菜单页隐藏。当前只在鼠标已自由的容器界面里接通命中和焦点输入。

```java
UiHudDocumentRegistration registration = UiHudDocumentHost.getInstance().register(
        UiHudLayerType.PASSIVE,
        document -> {
            ElementNode root = document.getRootElement();
            ElementNode badge = document.div();
            badge.style()
                    .setPosition(UiPosition.FIXED)
                    .setRight(UiStyleLength.px(12))
                    .setTop(UiStyleLength.px(12));
            badge.appendText("战斗中");
            root.append(badge);
        });
```

当前稳定边界：

- HUD 文档仍复用 `UiDocument` 与 `HtmlLikeDocumentWidget`，不是单独的一套渲染语法。
- HUD 根元素默认补齐 `width:100%`、`height:100%` 与 `overflow:visible`。
- 被动层会默认标记为整棵子树不可命中。
- 交互层首版优先服务“容器界面上方的小面板/浮层”场景，暂不主动接管原版游戏内锁定鼠标状态。
