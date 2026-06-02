# Minecraft 界面入口

本文说明在 Minecraft 1.7.10 宿主中打开 Qz UILib 文档页面的建议方式。

本文是宿主集成说明；内置诊断页和示例页仅作为开发调试入口，不构成对外稳定业务 API。

如果需要排查原版 `GuiScreen` / `GuiContainer` 的键鼠分发细节、HUD 抢占时序或注入层级，请参考 `../../开发者文档/Minecraft原版输入链路.md`。

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

从第一版开放边界开始，`UiDocumentScreens` 只承担这条业务开屏门面职责；诊断页托管机制、页面标识与内部 definition 不再属于对外可感知 API。

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

内置诊断页和示例页只保留给内部开发工具链使用，不在对外使用文档中暴露具体页面工厂；其底层托管页面定义和页面标识属于库内实现细节，不建议外部代码依赖。

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
- 当前测试入口统一为 `/qzuilib test`，并通过 `UiScreenManager` 延后开屏；旧内置子页已清空，当前打开 test P0 语义首页。

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

- `UiHudLayerType.PASSIVE`
    - 可见性：只在纯游戏内 HUD 阶段可见，任何 `GuiScreen`（含背包、箱子、菜单）打开后立即隐藏。
    - 输入：永不接收输入；整棵子树默认不可命中。
- `UiHudLayerType.INTERACTIVE`
    - 可见性：纯游戏内与非黑名单 `GuiScreen` 都可见。当前默认黑名单包括游戏主页（含原版 `GuiMainMenu`、新版 `TitleScreen`、第三方主页 `galaxyspace.core.gui.GSGuiMainMenu`）、选图页、服务器列表、游戏内菜单和 Forge 配置页（落入 `MENU` 分类时既不显示也不接通输入）。
    - 输入：仅在容器态（`GuiContainer` 子类宿主界面）且鼠标已释放时接通命中与键盘焦点输入；纯游戏内只渲染，不可点。

```java
UiHudDocumentRegistration registration = UiHudDocumentHost.getInstance().register(
        UiHudLayerType.PASSIVE,
        context -> {
            UiDocument document = context.getDocument();
            ElementNode root = context.getMountRoot();
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

- HUD 文档仍复用 `UiDocument` 与 `HtmlLikeDocumentWidget`，不是单独的一套渲染语法；但宿主内部现已改为“单共享 document/widget + 每个 HUD 一棵挂载子树”。
- `register(...)` 传入的 builder 会收到 `UiHudMountContext`：`getDocument()` 用于创建节点，`getMountRoot()` 才是当前 HUD 专属根；不要再假设 `document.getRootElement()` 归当前 HUD 独占。
- 每个 HUD 的挂载根默认补齐 `width:100%`、`height:100%` 与 `overflow:visible`。
- 被动层会默认标记为整棵子树不可命中。
- 交互层在实现上仍会随纯游戏界面渲染，但只有容器态才会接通输入。当前 HUD 隐藏范围采用显式黑名单：游戏主页（含原版 `GuiMainMenu`、新版 `TitleScreen`、第三方主页 `galaxyspace.core.gui.GSGuiMainMenu`）、选图页、服务器列表、游戏内菜单和 Forge 配置页会落入 `MENU` 分类，因此既不会显示，也不会接通输入；未命中黑名单的第三方与大多数原版 `GuiScreen` 默认按容器态处理。
- 交互 HUD 当前采用“先鼠标、后键盘”的接管契约：必须先通过鼠标命中建立 HUD 焦点，之后才会继续接管键盘；不支持纯键盘首次进入 HUD。
- 交互 HUD 的键盘抢占以“HUD 内是否仍有有效焦点控件”为唯一依据；鼠标悬停、HUD 可见但未聚焦都不会单独触发键盘抢占。
- 交互 HUD 只会拦截实际浮窗面板及其非穿透子树；命中挂载根自身空白区域时会默认放行到底层原生界面。若某个面板或其祖先也希望显式放行空白区域，需要声明 `data-hit-test-passthrough="true"`；该语义在共享根下会把整棵子树视为透明，并继续命中视觉下方 HUD。
- 多个交互 HUD 重叠时，只会把输入路由给最上层命中的那一层，避免多层同时响应同一次点击。
- 当当前原生界面已有聚焦的 Minecraft 文本输入框时，交互层不会继续接管键盘；一旦 UILib 获得焦点，会阻断宿主原生键盘链路，避免双方同时响应同一输入。
- `GuiChat` 打开时 HUD 仍可见，但不会沿用上一个屏幕里的旧 HUD 焦点继续抢占键盘；聊天框会先保留原生输入权，只有在当前聊天界面里再次鼠标命中 HUD 并形成有效焦点后，HUD 才会重新接管。若 HUD 已抢占过聊天框输入，随后主键点击浮窗外部会显式恢复聊天框原生输入焦点。
- 交互层的键盘抢占发生在原生 `handleKeyboardInput()` 之前，避免背包、容器或其他页面先消费 Tab / 文本输入。
- `Config.GENERAL.uiDebug=true` 时，会在屏幕右上角显示当前 `GuiScreen` 类名，并自动裁剪到屏幕内，适合排查某个页面为什么会被 HUD 黑名单隐藏或继续显示。

当前实现可以按四层理解：

1. 可交互判定：只有容器态且鼠标未被游戏重新抓取时，交互 HUD 才接通输入。
2. 鼠标命中仲裁：每次鼠标事件先从最上层 HUD 做命中测试；命中非穿透区域时由 HUD 消费，否则放行宿主。
3. 焦点归属：只有命中的 HUD 文档实际获得有效焦点后，才会建立 HUD 键盘捕获状态。
4. 原生输入阻断：一旦 HUD 已聚焦，后续即时键盘事件会在宿主 `handleKeyboardInput()` 之前先路由到 HUD，并阻断原生页面继续处理同一事件；原生 `GuiScreen` 上的 HUD 按键事件只走 immediate 路径，不再消费 collected 键盘帧，文本输入仍复用 collected 文本事件，避免退格等无文本按键重复落到 HUD。
