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

## 扩展点

模板现在提供三类对外扩展点：

- `Spec.addPropertyEditorFactory(...)`：注册自定义属性编辑器工厂。
- `Spec.setTheme(...)`：覆盖模板颜色主题。
- `Spec.setTextSet(...)`：覆盖按钮、状态、错误提示等文案。

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
- `PropertyBinding` 已开放给外部继承，可自定义 `isDirty()`、`restoreCurrentValue()`、`validateDraft()`、`applyDraft()`。
- 如果某类列表属性需要可视化重排、拖拽或其他专属交互，建议派生一个专用列表控件，再通过 `PropertyBinding` 接入，不要直接修改通用文本列表编辑语义。

### 主题与文案

- `Theme.defaultTheme()` 提供默认配色基线。
- `TextSet.defaultTextSet()` 提供默认中文文案。
- 可以在此基础上 new 一个新的 `Theme` / `TextSet` 传给 `Spec`，避免 fork 整个页面类。

## 当前项目落地

- `club.heiqi.uilib.config.ModGuiFactory` 继续作为 Forge `guiFactory` 入口。
- `club.heiqi.uilib.config.ModConfigGui` 已切换为 `ForgeConfigTemplateScreen` 的具体实现，不再继承默认 `GuiConfig`。
- 当前模板实例覆盖三个分类：`general`、`fontSystem`、`fontSizeSetting`。
- 当前模板已经把 `validValues` 视为一等语义；但当当前值已不在候选集时，会自动回退为文本输入保留遗留值。
- 当前 `fontSystem.fontSort` 使用专用二级排序页；页面面向 300+ 字体列表提供分页、搜索、全局序号跳转、目标序号移动、当前页内拖拽微调与“保存并应用”按钮。启动时仍会根据已发现字体自动补全有效顺序，并在配置数据中保留未发现的历史字体记录。
- 首次启动且尚无 `fontSystem.fontSort` 配置时，字体系统会按当前平台常见多语种字体提示优先整理已发现字体，再追加其他字体，减少自然排序先选中 CAD 等窄用途字体的概率。

## 验证建议

- 先运行 `runClient21`，从模组列表进入配置页，确认页面可以打开。
- 修改布尔、数值、列表配置后保存，确认 `Config.saveAndReload()` 与字体运行时重载逻辑仍生效。
- 使用 `ESC` 返回父界面，确认替代页的退出路径正常。

## 测试注意事项

- 不要在纯 JVM 单测中直接实例化继承 `GuiScreen` / `BaseScreen` 的页面类。
- 这类页面会触发 Minecraft 客户端静态初始化链，不适合在当前纯 JVM 环境直接 new。
- 需要可持续自动验证的逻辑，优先下沉到不依赖 `GuiScreen` 的纯状态层或文档构建层。
