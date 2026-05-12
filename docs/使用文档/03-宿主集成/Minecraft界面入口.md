# Minecraft 界面入口

本文说明在 Minecraft 1.7.10 宿主中打开 Qz UILib 文档页面的建议方式。

本文是宿主集成说明；内置诊断页和示例页仅作为开发调试入口，不构成对外稳定业务 API。

如果需要排查原版 `GuiScreen` / `GuiContainer` 的键鼠分发细节、HUD 抢占时序或注入层级，请同时参考 `Minecraft原版输入链路.md`。

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

## 诊断与示例入口

内置诊断页和示例页仍通过 `UiDiagnosticsScreens` 创建，但它们属于开发调试入口，不在本文件展开维护具体页面清单。

当前命令入口、跳转菜单、保留页面范围和触发时序统一见 `docs/使用文档/04-诊断入口/指令触发方案.md`。

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
- `UiHudLayerType.INTERACTIVE`：游戏内与容器界面可见；菜单页隐藏。只有当前存在已打开的非菜单界面且鼠标已自由时，才会接通命中和焦点输入；纯游戏内锁鼠状态下仅可见，不可交互。

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
- 交互层当前不再限定必须是容器界面；但只有当前存在已打开的非菜单界面且鼠标未锁定时，才会接通输入。纯游戏内锁定鼠标状态下，交互 HUD 仍只会显示，不会主动接管命中或键盘输入；当前主要适用容器类与部分自定义屏幕。
- 交互 HUD 当前采用“先鼠标、后键盘”的接管契约：必须先通过鼠标命中建立 HUD 焦点，之后才会继续接管键盘；不支持纯键盘首次进入 HUD。
- 交互 HUD 默认会阻断命中区域继续落到底层原生界面；若某个面板或其祖先希望显式放行空白区域，需要声明 `data-hit-test-passthrough="true"`。
- 当当前原生界面已有聚焦的 Minecraft 文本输入框时，交互层不会继续接管键盘；一旦 UILib 获得焦点，会阻断宿主原生键盘链路，避免双方同时响应同一输入。
- 交互层的键盘抢占发生在原生 `handleKeyboardInput()` 之前，避免背包、容器或其他页面先消费 Tab / 文本输入。
