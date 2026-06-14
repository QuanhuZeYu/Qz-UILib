package club.heiqi.uilib.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;

/**
 * 现代配置模板 DOM 文档构建器测试。
 *
 * <p>纯 JVM 测试：直接调用 {@link ModernConfigDocumentBuilder#build(UiDocument)} 验证输出结构，
 * 不实例化 {@code ModernConfigTemplateScreen}（它依赖 Minecraft 静态初始化）。</p>
 */
public class ModernConfigDocumentBuilderTest {

    /**
     * 空配置 + 无搜索过滤组件时，应渲染空状态区块，且 visibleSectionCount 为 0。
     */
    @Test
    public void buildEmptyConfigProducesEmptyState() {
        ModernConfigTemplateScreen.Spec spec = newSpec("demo", "演示配置",
                Config.createMutable(ConfigFormat.JSON));
        UiDocument document = UiDocument.create();
        BuilderFixture fixture = BuilderFixture.create(spec, Collections
                .<ModernConfigPropertyBindings.ConfigPropertyBinding>emptyList(), null, document);

        ModernConfigDocumentBuilder.Result result = fixture.build(document);

        assertNotNull(result.getStatusText());
        assertEquals(0, result.getVisibleSectionCount());
        // 空状态文案来自 textSet.emptyTemplateText，仅空配置时渲染
        assertTrue(containsText(document.getRootElement(),
                ForgeConfigTemplateScreen.TextSet.defaultTextSet().emptyTemplateText));
    }

    /**
     * 非空配置时，应渲染字段卡片区块，visibleSectionCount 为 1。
     * 嵌套分类的叶子绑定延迟加载，根路径下非 OBJECT 子项才直接渲染卡片。
     */
    @Test
    public void buildWithFieldsShowsFieldCards() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON).set("debug", true);
        ModernConfigTemplateScreen.Spec spec = newSpec("demo", "演示配置", config);
        List<ModernConfigPropertyBindings.ConfigPropertyBinding> bindings =
                ModernConfigPropertyBindings.createBindings(config, Collections
                        .<ModernConfigTemplateScreen.FieldSpec>emptyList(), null);
        UiDocument document = UiDocument.create();
        BuilderFixture fixture = BuilderFixture.create(spec, bindings, null, document);

        ModernConfigDocumentBuilder.Result result = fixture.build(document);

        assertEquals(1, result.getVisibleSectionCount());
        assertNotNull(findByAttribute(document.getRootElement(), "data-modern-config-path", "debug"));
    }

    /**
     * 注入搜索过滤组件时，visibleSectionCount 应递增。
     */
    @Test
    public void buildWithSearchFilterIncrementsVisibleSectionCount() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON).set("server.host", "localhost");
        ModernConfigTemplateScreen.Spec spec = newSpec("demo", "演示配置", config);
        List<ModernConfigPropertyBindings.ConfigPropertyBinding> bindings =
                ModernConfigPropertyBindings.createBindings(config, Collections
                        .<ModernConfigTemplateScreen.FieldSpec>emptyList(), null);
        ModernConfigSearchIndex index = new ModernConfigSearchIndex(expandLeafBindings(bindings),
                Collections.<String, ModernConfigTemplateScreen.FieldSpec>emptyMap(), config.asImmutable());
        UiDocument document = UiDocument.create();
        ModernConfigSearchFilter filter = new ModernConfigSearchFilter(document, index, null);
        BuilderFixture fixture = BuilderFixture.create(spec, bindings, filter, document);

        ModernConfigDocumentBuilder.Result result = fixture.build(document);

        // searchFilter + field cards = 2
        assertEquals(2, result.getVisibleSectionCount());
        assertNotNull(findByAttribute(document.getRootElement(), "data-modern-config-search", "true"));
    }

    /**
     * hero 区块应包含 title、subtitle、description 文本。
     */
    @Test
    public void buildProducesHeroWithTitleSubtitleAndDescription() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON).set("a", 1);
        ModernConfigTemplateScreen.Spec spec = new ModernConfigTemplateScreen.Spec("demo", "主标题", config)
                .setSubtitle("副标题文本")
                .setDescription("描述说明文本")
                .setConfigPath("config/demo.json");
        List<ModernConfigPropertyBindings.ConfigPropertyBinding> bindings =
                ModernConfigPropertyBindings.createBindings(config, Collections
                        .<ModernConfigTemplateScreen.FieldSpec>emptyList(), null);
        UiDocument document = UiDocument.create();
        BuilderFixture fixture = BuilderFixture.create(spec, bindings, null, document);

        fixture.build(document);

        ElementNode header = findFirstByTag(document.getRootElement(), "header");
        assertNotNull(header);
        assertTrue(containsText(header, "主标题"));
        assertTrue(containsText(header, "副标题文本"));
        assertTrue(containsText(header, "描述说明文本"));
        assertTrue(containsText(header, "config/demo.json"));
    }

    /**
     * 状态卡片区块应包含一个可变的 status 文本节点。
     */
    @Test
    public void buildProducesStatusCardWithMutableTextNode() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON).set("a", 1);
        ModernConfigTemplateScreen.Spec spec = newSpec("demo", "演示", config);
        List<ModernConfigPropertyBindings.ConfigPropertyBinding> bindings =
                ModernConfigPropertyBindings.createBindings(config, Collections
                        .<ModernConfigTemplateScreen.FieldSpec>emptyList(), null);
        UiDocument document = UiDocument.create();
        BuilderFixture fixture = BuilderFixture.create(spec, bindings, null, document);

        ModernConfigDocumentBuilder.Result result = fixture.build(document);

        TextNode status = result.getStatusText();
        assertNotNull(status);
        status.setText("自定义状态");
        assertEquals("自定义状态", status.getText());
    }

    /**
     * 工具栏应包含 4 个动作按钮。
     */
    @Test
    public void buildProducesToolbarWithFourButtons() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON).set("a", 1);
        ModernConfigTemplateScreen.Spec spec = newSpec("demo", "演示", config);
        List<ModernConfigPropertyBindings.ConfigPropertyBinding> bindings =
                ModernConfigPropertyBindings.createBindings(config, Collections
                        .<ModernConfigTemplateScreen.FieldSpec>emptyList(), null);
        UiDocument document = UiDocument.create();
        BuilderFixture fixture = BuilderFixture.create(spec, bindings, null, document);

        fixture.build(document);

        // 按钮控件元素会被注入工具栏；通过 label 文本验证 4 个按钮都存在
        ForgeConfigTemplateScreen.TextSet textSet = ForgeConfigTemplateScreen.TextSet.defaultTextSet();
        assertTrue(containsText(document.getRootElement(), textSet.saveButtonLabel));
        assertTrue(containsText(document.getRootElement(), textSet.restoreCurrentButtonLabel));
        assertTrue(containsText(document.getRootElement(), textSet.restoreDefaultsButtonLabel));
        assertTrue(containsText(document.getRootElement(), textSet.backButtonLabel));
    }

    /**
     * 多次 build 同一文档时，结构应稳定（不崩溃，statusText 可用）。
     */
    @Test
    public void buildIsRepeatable() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON).set("a", 1);
        ModernConfigTemplateScreen.Spec spec = newSpec("demo", "演示", config);
        List<ModernConfigPropertyBindings.ConfigPropertyBinding> bindings =
                ModernConfigPropertyBindings.createBindings(config, Collections
                        .<ModernConfigTemplateScreen.FieldSpec>emptyList(), null);

        UiDocument firstDoc = UiDocument.create();
        UiDocument secondDoc = UiDocument.create();
        ModernConfigDocumentBuilder.Result first = BuilderFixture.create(spec, bindings, null, firstDoc)
                .build(firstDoc);
        ModernConfigDocumentBuilder.Result second = BuilderFixture.create(spec, bindings, null, secondDoc)
                .build(secondDoc);

        assertEquals(first.getVisibleSectionCount(), second.getVisibleSectionCount());
        assertNotNull(second.getStatusText());
    }

    private static ModernConfigTemplateScreen.Spec newSpec(String modId, String title, MutableConfig config) {
        return new ModernConfigTemplateScreen.Spec(modId, title, config);
    }

    /**
     * 展开 NestedCategoryBinding 的叶子 binding，用于构建搜索索引。
     */
    private static List<ModernConfigPropertyBindings.ConfigPropertyBinding> expandLeafBindings(
            List<ModernConfigPropertyBindings.ConfigPropertyBinding> bindings) {
        List<ModernConfigPropertyBindings.ConfigPropertyBinding> all =
                new ArrayList<ModernConfigPropertyBindings.ConfigPropertyBinding>();
        for (ModernConfigPropertyBindings.ConfigPropertyBinding binding : bindings) {
            if (binding instanceof ModernNestedCategoryBinding) {
                all.addAll(((ModernNestedCategoryBinding) binding).resolveDescendantBindings(""));
            }
            all.add(binding);
        }
        return all;
    }

    private static ElementNode findByAttribute(ElementNode root, String name, String value) {
        if (value.equals(root.getAttribute(name))) {
            return root;
        }
        for (DocumentNode child : root.getChildren()) {
            if (child instanceof ElementNode) {
                ElementNode found = findByAttribute((ElementNode) child, name, value);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static ElementNode findFirstByTag(ElementNode root, String tagName) {
        if (tagName.equals(root.getTagName())) {
            return root;
        }
        for (DocumentNode child : root.getChildren()) {
            if (child instanceof ElementNode) {
                ElementNode found = findFirstByTag((ElementNode) child, tagName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean containsText(ElementNode root, String expected) {
        for (DocumentNode child : root.getChildren()) {
            if (child instanceof TextNode) {
                if (((TextNode) child).getText().contains(expected)) {
                    return true;
                }
            } else if (child instanceof ElementNode) {
                if (containsText((ElementNode) child, expected)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 构建器夹具：用同一 document 创建按钮与 builder，避免跨 document append。
     */
    private static final class BuilderFixture {

        private final ModernConfigDocumentBuilder builder;

        static BuilderFixture create(ModernConfigTemplateScreen.Spec spec,
                List<ModernConfigPropertyBindings.ConfigPropertyBinding> bindings,
                ModernConfigSearchFilter filter, UiDocument document) {
            ForgeConfigTemplateScreen.TextSet textSet = spec.getTextSet();
            DocumentButtonControl save = new DocumentButtonControl(document, textSet.saveButtonLabel);
            DocumentButtonControl restoreCurrent = new DocumentButtonControl(document,
                    textSet.restoreCurrentButtonLabel);
            DocumentButtonControl restoreDefaults = new DocumentButtonControl(document,
                    textSet.restoreDefaultsButtonLabel);
            DocumentButtonControl back = new DocumentButtonControl(document, textSet.backButtonLabel);
            ModernConfigDocumentBuilder builder = new ModernConfigDocumentBuilder(spec, bindings, save,
                    restoreCurrent, restoreDefaults, back, filter);
            return new BuilderFixture(builder);
        }

        private BuilderFixture(ModernConfigDocumentBuilder builder) {
            this.builder = builder;
        }

        ModernConfigDocumentBuilder.Result build(UiDocument document) {
            return builder.build(document);
        }
    }
}
