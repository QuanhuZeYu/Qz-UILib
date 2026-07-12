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

若宿主仍走 `IModGuiFactory`，请接入新架构配置页，**不要**再使用已删除的 `ForgeConfigTemplateScreen`。

推荐路径：

1. 声明 `ConfigSchema` → `ConfigManager.bootstrap(yamlFile, schema)`
2. 需要搜索选择器时调用 `ConfigUI.buildScreen(manager, input, registryCustomizer, restorePolicyCustomizer, editorRegistryCustomizer)` 注册 `ValueEditorProvider`；无此需求继续使用原 2/3/4 参重载
3. 用 `McScreenBridge` 子类（本 mod 为 `ModernConfigScreen`）包成 `GuiScreen`
4. Forge 侧保留单参 `(GuiScreen)` 中转类（本 mod：`ModConfigGui` → `ModernConfigEntry.createScreen`）

本 mod 样板：`ModernConfigEntry` / `ModConfigGui` / `ModGuiFactory`。  
完整步骤见 [配置页（ModernConfig）](../02-控件/配置页（ModernConfig）.md)。

SearchPicker 配置入口当前为 beta API，不属于 LTS 稳定承诺；宿主 provider 不得依赖 Minecraft 类型穿透 config core。

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

`UiInputService` 对外仍是稳定 facade：运行时优先通过内部 `Lwjgl3ifyInputBackend` 反射订阅 `InputEvents`，缺少该 API 时回退 `LwjglxPollingInputBackend`。回退后端只覆盖基础按键、鼠标和滚轮；现代文本输入与 IME 事件仍需要 LWJGL3ify `InputEvents`。

保留建议：

- 保留 `UiInputService.getInstance().initialize()`。
- 保留 `UiInputTickListener` 注册。
- 保留 `UiScreenManager.getInstance().tick()` 这条延后任务冲刷路径。
- 只移除全局诊断热键，不移除正常输入分发。

## 通用被动 HUD

游戏内 HUD 使用 `ClientHudService` 注册不可变 snapshot，或使用 `TextHud` / `CompactHud` 预制入口。
该入口只承载被动展示，不接收输入，也不暴露 Minecraft、Forge、scene 节点或绝对坐标。

```java
HudRegistration registration = TextHud.register("example:status", HudAnchor.TOP_RIGHT,
        () -> HudSnapshot.of(HudLine.text("status", "战斗中", HudTone.WARNING)));
```

registration 归调用 mod 所有，跨断线与世界切换保持有效，直到调用方在客户端主线程调用 `close()`。
provider 在 render 主线程读取，应无副作用并返回不可变 `HudSnapshot`。
- 当当前原生界面已有聚焦的 Minecraft 文本输入框时，交互层不会继续接管键盘；一旦 UILib 获得焦点，会阻断宿主原生键盘链路，避免双方同时响应同一输入。
- `GuiChat` 打开时 HUD 仍可见，但不会沿用上一个屏幕里的旧 HUD 焦点继续抢占键盘；聊天框会先保留原生输入权，只有在当前聊天界面里再次鼠标命中 HUD 并形成有效焦点后，HUD 才会重新接管。若 HUD 已抢占过聊天框输入，随后主键点击浮窗外部会显式恢复聊天框原生输入焦点。
- 交互层的键盘抢占发生在原生 `handleKeyboardInput()` 之前，避免背包、容器或其他页面先消费 Tab / 文本输入。
- `Config.GENERAL.uiDebug=true` 时，会在屏幕右上角显示当前 `GuiScreen` 类名，并自动裁剪到屏幕内，适合排查某个页面为什么会被 HUD 黑名单隐藏或继续显示。

当前实现可以按四层理解：

1. 可交互判定：只有容器态且鼠标未被游戏重新抓取时，交互 HUD 才接通输入。
2. 鼠标命中仲裁：每次鼠标事件先从最上层 HUD 做命中测试；命中非穿透区域时由 HUD 消费，否则放行宿主。
3. 焦点归属：只有命中的 HUD 文档实际获得有效焦点后，才会建立 HUD 键盘捕获状态。
4. 原生输入阻断：一旦 HUD 已聚焦，后续即时键盘事件会在宿主 `handleKeyboardInput()` 之前先路由到 HUD，并阻断原生页面继续处理同一事件；原生 `GuiScreen` 上的 HUD 按键事件只走 immediate 路径，不再消费 collected 键盘帧，文本输入仍复用 collected 文本事件，避免退格等无文本按键重复落到 HUD。
