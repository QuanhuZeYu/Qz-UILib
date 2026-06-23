# Forge 配置模板

本文说明如何使用 `ForgeConfigTemplateScreen` 生成一个替代 Forge 默认 `GuiConfig` 的游戏内配置页。

## 适用场景

- 你的模组已经使用 `net.minecraftforge.common.config.Configuration` 注册配置项。
- 你希望把默认 Forge 配置页替换为 Qz UILib 的 HTML-like 页面。
- 你希望复用一套统一的配置模板，而不是手工为每个字段逐个拼 UI。

## 能力边界

- 模板会直接读取 `Configuration` 中已存在的 `ConfigCategory` 与 `Property`。
- 当前模板支持布尔、整数、小数、字符串与列表属性。
- 布尔属性默认渲染为开关。
- 非列表字符串属性如果声明了 `validValues`，且当前值仍在候选集中，默认渲染为分段选择控件；遗留值会自动回退为文本输入，避免静默改写。
- 其他属性默认渲染为文本输入框。
- 列表输入使用英文逗号分隔；保存时会写回到对应 Forge `Property`。
- 列表属性也可以通过自定义 `PropertyEditorFactory` 派生为专用列表控件，而不是局限于文本输入。
- 页面内置 `Ctrl+S` 保存、`ESC` 返回、恢复当前值、恢复默认值四个基础动作。
- 保存动作若在写盘或宿主 `saveAndReload()` 阶段失败，页面会回滚本次已写回的属性值，并保留失败提示。
- 页面在找不到显式声明的分类时，会在状态区提示缺失分类名；空状态区也会优先显示缺失分类信息。
- 分类名默认按大小写敏感精确匹配；只有通过 `CategorySpec.addAlias(...)` 或 `ConfigSyncCategorySpec.addAlias(...)` 显式声明的历史名称才会参与兼容查找。
- 模板现在支持可选的“服务端权威配置同步”模式：页面本地编辑先进入客户端草稿，再通过 UILIB 自建网络同步到服务端配置会话，只有显式点击保存时才由服务端执行最终提交。
- 如果客户端尚未完成 Qz 网络能力握手，或服务端未注册对应配置目标，模板会自动回退到原有纯本地 `Configuration` 模式，不影响旧接入。

## 最小接入

```java
public class ExampleConfigGui extends ForgeConfigTemplateScreen {

    public ExampleConfigGui(GuiScreen parentScreen) {
        super(parentScreen, createSpec());
    }

    private static Spec createSpec() {
        return new Spec("example_mod", "Example Mod 配置", ExampleConfig.configuration)
                .setSubtitle("HTML-like Config")
                .setDescription("使用 Qz UILib 模板替代默认 Forge 配置页。")
                .setConfigPath(ExampleConfig.getConfigPath())
                .setSaveHandler(new SaveHandler() {
                    @Override
                    public void onSave(Configuration configuration) {
                        ExampleConfig.saveAndReload();
                    }
                })
                .addCategory(new CategorySpec("general").setTitle("General"))
                .addCategory(new CategorySpec("render").setTitle("Render"));
    }
}
```

然后在 `IModGuiFactory` 中返回你的配置页类：

```java
@Override
public Class<? extends GuiScreen> mainConfigGuiClass() {
    return ExampleConfigGui.class;
}
```

## 分类来源

- 显式调用 `addCategory(...)` 时，只渲染这些分类。
- 如果不显式追加分类，模板会遍历 `configuration.getCategoryNames()` 自动生成分类卡片。
- `CategorySpec.setTitle(...)` 用于覆盖页面显示标题。
- `CategorySpec.setDescription(...)` 用于补充分类说明；页面会与 Forge `ConfigCategory.comment` 合并展示。
- `CategorySpec.addAlias(...)` 仅用于兼容已经存在的历史分类名，例如旧配置文件中曾写成小写分类；不要把它当作默认忽略大小写匹配。

## 扩展点

模板现在提供三类对外扩展点：

- `Spec.addPropertyEditorFactory(...)`：注册自定义属性编辑器工厂。
- `Spec.setTheme(...)`：覆盖模板颜色主题。
- `Spec.setTextSet(...)`：覆盖按钮、状态、错误提示等文案。
- `Spec.enableQzNetworkSync(...)`：为模板页一键接入 Qz 网络服务端权威配置会话。
- `Spec.setRemoteSyncController(...)` + `Spec.setRemoteSyncScreenId(...)`：底层扩展入口，保留给需要替换同步控制器的高级场景。

## 服务端权威同步

当你希望配置页以服务端共享配置为权威源，而不是直接在客户端本地改 `Configuration` 时，可以把模板页绑定到
`ConfigTemplateSyncManager` 注册的配置目标：

```java
return new Spec("example_mod", "Example Mod 配置", ExampleConfig.configuration)
        .setSubtitle("HTML-like Config")
        .setDescription("服务端权威配置页面")
        .setConfigPath(ExampleConfig.getConfigPath())
        .setSaveHandler(new SaveHandler() {
            @Override
            public void onSave(Configuration configuration) {
                ExampleConfig.saveAndReload();
            }
        })
        .enableQzNetworkSync("example-mod-config")
        .addCategory(new CategorySpec("general").setTitle("General"));
```

服务端在 `preInit` 前后注册同一个目标。若模板 `Spec` 不依赖客户端专属类，推荐直接复用它生成同步目标：

```java
ForgeConfigTemplateScreen.Spec spec = createSpec();
ConfigTemplateSyncManager.getInstance().registerTarget(spec.createQzNetworkSyncTarget());
```

也可以手动注册 `ConfigSyncTarget`，用于服务端与客户端模板规格需要分开维护的场景：

```java
ConfigTemplateSyncManager.getInstance().registerTarget(
        ConfigSyncTarget.builder("example-mod-config", ExampleConfig.configuration)
                .modId("example_mod")
                .title("Example Mod 配置")
                .subtitle("Server Authoritative Config")
                .description("通过 UILIB 自建网络同步草稿并显式保存。")
                .configPath(ExampleConfig.getConfigPath())
                .categories(Arrays.asList(
                        new ConfigSyncCategorySpec("general", "General", "基础配置"),
                        new ConfigSyncCategorySpec("fontSystem", "Font System", "字体配置")
                                .addAlias("fontsystem")))
                .draftValidator("fontSystem", "customList", new ConfigSyncTarget.DraftValidator() {
                    @Override
                    public String validateDraft(Property property, String draftValue) {
                        return validateCustomListDraft(draftValue);
                    }
                })
                .saveAction(new ConfigSyncTarget.SaveAction() {
                    @Override
                    public void save(Configuration configuration) {
                        ExampleConfig.saveAndReload();
                    }
                })
                .build());
```

同步语义如下：

- 页面打开时，客户端通过 `NetService` 下的 Fetch 打开一个配置会话，并拿到服务端当前快照。
- 字段编辑优先修改客户端控件草稿；模板页会把变化通过 Qz `Channel` 异步同步给服务端草稿。
- 点击保存后，客户端通过 Fetch 触发显式保存；服务端完成校验、持久化和业务重载后，再把最终快照回推回来。
- 如果某个字段在本地页面使用了专用 `PropertyBinding.validateDraft()`，远程目标也应通过 `ConfigSyncTarget.Builder.draftValidator(...)` 注册同等字段级校验，避免服务端只执行通用类型校验。
- 会话状态和最终保存结果通过 `Store` 回推；当服务端未启用该能力时，模板自动回退到旧的本地保存模式。
- 本地模板页关闭时会通过 Qz Fetch 通知服务端释放配置会话，玩家离线时服务端也会清理该玩家的会话与 per-player Store 状态。

这套模式当前优先面向“共享服务端配置”。如果你需要客户端私有配置优先级，请在业务层自行叠加，不要直接改写服务端权威会话语义。

### 自定义属性编辑器

当默认的开关 / 分段选择 / 文本输入不满足需求时，可以注册 `PropertyEditorFactory`：

```java
spec.addPropertyEditorFactory(new ForgeConfigTemplateScreen.PropertyEditorFactory() {
    @Override
    public ForgeConfigTemplateScreen.PropertyBinding create(UiDocument document,
            ForgeConfigTemplateScreen.CategorySpec categorySpec,
            Property property,
            ForgeConfigTemplateScreen owner) {
        if (!"specialMode".equals(property.getName())) {
            return null;
        }
        return new CustomPropertyBinding(document, categorySpec, property, owner);
    }
});
```

说明：

- 返回 `null` 表示当前工厂不处理该属性，模板会继续尝试后续工厂和默认编辑器。
- `PropertyBinding` 是 `ForgeConfigTemplateScreen` 的非静态内部类，必须由属于当前页面实例的工厂内部 `new` 出来；外部独立继承一个 `PropertyBinding` 子类几乎不可行，真正的扩展点是 `PropertyEditorFactory`。子类化 `PropertyBinding` 仅用于在工厂内自定义 `isDirty()`、
  `restoreCurrentValue()`、`validateDraft()`、`applyDraft()` 等钩子。
- 如果某类列表属性需要可视化重排、拖拽或其他专属交互，建议派生一个专用列表控件，再通过 `PropertyEditorFactory` 在内部实例化 `PropertyBinding` 子类接入，不要直接修改通用文本列表编辑语义。

### 主题与文案

- `Theme.defaultTheme()` 提供默认配色基线。
- `TextSet.defaultTextSet()` 提供默认中文文案。
- 可以在此基础上 new 一个新的 `Theme` / `TextSet` 传给 `Spec`，避免 fork 整个页面类。

## 当前项目落地

- `club.heiqi.uilib.config.ModGuiFactory` 继续作为 Forge `guiFactory` 入口。
- `club.heiqi.uilib.config.ModConfigGui` 已切换为 `ForgeConfigTemplateScreen` 的具体实现，不再继承默认 `GuiConfig`。
- 当前模板实例覆盖三个分类：`general`、`fontSystem`、`fontSizeSetting`。
- Qz-UILib 自身为历史小写 `fontsystem` 分类显式声明 alias；其他分类仍按精确分类名匹配。
- `ModConfigGui` 现已作为 `ConfigTemplateSyncManager` 的首个接入方，默认绑定 `screenId=mod-config` 的服务端权威配置目标。
- 当前模板已经把 `validValues` 视为一等语义；但当当前值已不在候选集时，会自动回退为文本输入保留遗留值。
- 当前 `fontSystem.fontSort` 使用专用二级排序页；页面面向 300+ 字体列表提供分页、搜索、全局序号跳转、目标序号移动、当前页内拖拽微调与“保存并应用”按钮。启动时仍会根据已发现字体自动补全有效顺序，并在配置数据中保留未发现的历史字体记录。
- 当前 `fontSystem.characterFontRules` 使用专用字符字体规则编辑器；规则格式为 `字符或范围=字体名`，支持单字符、`U+XXXX`、`U+XXXX-U+YYYY` 与 `A-Z` 这类范围写法，禁用规则会保存为 `disabled:` 前缀。字体名输入复用 `DocumentAutocompleteInputControl`，
  会按当前字体排序快照弹出可滚动候选下拉；规则格式错误会阻断保存，重叠启用范围只提示并按顺序优先级处理；本地与远程配置同步都会执行字符规则字段级校验。运行时首个启用且命中的规则优先于 `fontSort` 自动匹配，若目标字体不存在或不能显示该字符，则继续回退到原有自动匹配链路。
- 首次启动且尚无 `fontSystem.fontSort` 配置时，字体系统会按当前平台常见多语种字体提示优先整理已发现字体，再追加其他字体，减少自然排序先选中 CAD 等窄用途字体的概率。

## 验证建议

- 先运行 `runClient21`，从模组列表进入配置页，确认页面可以打开。
- 修改布尔、数值、列表配置后保存，确认 `Config.saveAndReload()` 与字体运行时重载逻辑仍生效。
- 使用 `ESC` 返回父界面，确认替代页的退出路径正常。

## 测试注意事项

- 不要在纯 JVM 单测中直接实例化继承 `GuiScreen` / `BaseScreen` 的页面类。
- 这类页面会触发 Minecraft 客户端静态初始化链，不适合在当前纯 JVM 环境直接 new。
- 需要可持续自动验证的逻辑，优先下沉到不依赖 `GuiScreen` 的纯状态层或文档构建层。
