# Minecraft 界面入口

本文说明在 Minecraft 1.7.10 宿主中接入 Qz UILib scene 页面和配置页的建议方式。

本文是宿主集成说明；内置诊断页和示例页仅作为开发调试入口，不构成对外稳定业务 API。

如果需要排查原版 `GuiScreen` 的生命周期、输入桥接或 HUD 时序，请参考开发者文档；`GuiContainer`、vanilla Slot
和 inventory bridge 不属于当前 UILib 合同。

## 业务页面入口

当前业务页面走 `UiSurface` + `McScreenBridge` scene 宿主；配置页由 `ConfigUI.buildScreen(...)` 构建
`ConfigScreen`，再由 `ModernConfigScreen` 或自定义 `McScreenBridge` 包装成 Minecraft `GuiScreen`。
不要再引用已删除的 `UiDocumentScreens`、`UiDocument`、`HtmlLikeDocumentWidget` 或旧 document 控件。

普通页面的 scene 树、输入和宿主图片能力分别由 `SceneRuntime`、`PlatformInputSource` 和 `UiRuntimeAdapters`
收口。ItemStack 图标只使用：

```java
SceneImageSource icon = HostImageSource.itemIcon(stack);
```

该 source 创建时复制完整 snapshot，不提供数量、耐久、tooltip、carried 或槽位交互语义。

## 开屏时序约束

- 普通宿主事件或按钮回调里，可以直接调用 `Minecraft.displayGuiScreen(...)`。
- 如果当前上下文会在本次回调结束后关闭现有 `GuiScreen`，则不要直接开屏。
- 典型场景包括客户端聊天命令、聊天按钮或其他会立即结束当前界面的入口。
- 这类场景应通过 `UiScreenManager.getInstance().enqueue(...)` 延后到当前帧输入分发结束后再执行开屏。

```java
UiScreenManager.getInstance().enqueue(() -> {
    // 业务页面：实现 UiSurface（或继承 AbstractSceneHostWidget 提供 scene 根节点），
    // 再用 McScreenBridge 子类包成 GuiScreen 开屏。
    UiSurface surface = new MyHostWidget();
    Minecraft.getMinecraft().displayGuiScreen(new MyScreenBridge(surface));
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

- 外部开发者应优先通过 `ConfigUI.buildScreen(...)` 或自有 `UiSurface` + `McScreenBridge` 打开业务 UI。
- 诊断页只作为开发期工具使用，不应作为玩家默认入口。
- 默认不向原版背包注入按钮。
- 默认不注册全局右 Shift 打开诊断菜单。
- 调试入口仅保留 `/qzuilib modernconfig`（配置页）；scene 演示测试台（原 `/qzuilib test`）已移除。

## 运行时适配器

`McScreenBridge` 默认创建屏幕独占的 `UiRuntimeAdapters.minecraftDefaults()`；测试或非 Minecraft host 可显式注入
`UiRuntimeAdapters.empty()` 和确定性 `HostImageRenderer`/`ItemIconRenderer`。普通图片走轻量路径，Item icon 由
`MinecraftItemIconRenderer` 当帧直绘（纯 2D / 3D block / 多 pass 一律完整委托原版，`RenderSemantics` 两语义 + `ItemRenderTierRegistry` 分级），无缓存、无占位；空适配器路径跳过绘制。

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
HUD 使用的 session scene 会在世界卸载时释放，并在重连后按仍有效的 registration 重建。

## 聊天 markdown 递交（ChatAccess.printMarkdown）

聊天接管（`ChatMarkdownSettings.isEnabled()`）生效后，mod 可以用 `ChatAccess` 的两个 public
入口把消息**按 markdown 渲染**递交进聊天框（AGENTS 三条输入通道的第③条）。语义定案：
**print 家族恒纯——内容即所见，永不过装饰器链**；要装饰必须由调用方主动调既有
`decorate(IChatComponent)`（只变换不注入）再把结果递进来。两者语义完全分开，没有
「过装饰链的 markdown 打印」这种糖。

```java
// 用法一：纯 markdown 一行递交（** # > ` 等定界按 markdown 渲染；左对齐、无气泡、无 sender）
ChatAccess.getInstance().printMarkdown("### 维护公告\n今晚 **22:00** 起停服，见 http://example.net");

// 用法二：显式装饰 = 自己先构造组件、手动过一次装饰器链，再把结果递进 markdown 通道
IChatComponent raw = new ChatComponentTranslation(ChatAccess.MARKDOWN_CHAT_KEY,
        new Object[] {"- 列表项 A\n- 列表项 B"});   // 组件形递交的 markdown 内容装在这个槽
IChatComponent decorated = ChatAccess.getInstance().decorate(raw);
if (decorated != null) { // decorate 返回 null = 装饰链丢弃语义
    ChatAccess.getInstance().printMarkdown(decorated);
}
```

- **线程语义**：与原版 `printChatMessage` 同主线程约定（客户端主线程调用；投递即入史，
  视图刷新由接管层脏标记在主线程冲刷）。
- **未接管时**：两入口降级为原版显示——String 形按原文纯文本显示（不露任何内部键字面）；
  组件形直接把原组件交原版（自定义组件被原版渲染其 key 属「未接管时给自定义组件」的
  固有形状）。
- 与装饰器链的关系：`registerDecorator` 的链照旧只作用于**原版消息到达路径**
  （网络/第三方 mod 经 `printChatMessage` 注入的消息），print 家族对其零自动调用。
- **永不发送**：printMarkdown 任何路径不触发送链（不写已发送历史、不发聊天包），
  它只递交显示。
