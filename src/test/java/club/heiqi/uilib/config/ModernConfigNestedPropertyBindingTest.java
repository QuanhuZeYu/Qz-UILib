package club.heiqi.uilib.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.ui.component.UiComponentRuntime;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;

/**
 * 现代配置嵌套结构模板绑定测试。
 */
public class ModernConfigNestedPropertyBindingTest {

    @Test
    public void createsNavigationControlsAndAppliesNestedDefaultDraft() throws Exception {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("server.host", "old.local")
                .set("server.port", Integer.valueOf(25565));
        Map<String, ModernConfigTemplateScreen.FieldSpec> fieldsByPath = ModernConfigPropertyBindings.indexFields(
                Arrays.asList(new ModernConfigTemplateScreen.FieldSpec("server.host")
                        .setDefaultValue("new.local")));
        UiDocument doc = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(doc);
        ModernNestedCategoryBinding binding = new ModernNestedCategoryBinding(config, config.asImmutable(),
                fieldsByPath, null, runtime);

        ElementNode section = binding.createSection(doc, ForgeConfigTemplateScreen.Theme.defaultTheme());
        binding.restoreDefaultValue();

        assertNotNull(findElementByAttribute(section, "data-document-control", "tree-view"));
        assertNotNull(findElementByAttribute(section, "data-document-control", "breadcrumb"));
        assertTrue(binding.isDirty());
        assertNull(binding.validateDraft());
        binding.applyDraft();
        assertEquals("new.local", config.get("server.host").asString());
    }

    @Test
    public void limitsDeepInlineObjectsAndAllowsNavigation() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("a.b.c.d.e.f.value", "deep");
        UiDocument doc = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(doc);
        ModernNestedCategoryBinding binding = new ModernNestedCategoryBinding(config, config.asImmutable(),
                ModernConfigPropertyBindings.indexFields(null), null, runtime);

        ElementNode section = binding.createSection(doc, ForgeConfigTemplateScreen.Theme.defaultTheme());

        assertNotNull("根区块应显示 a 的分类占位", findElementByAttribute(section, "data-modern-config-path", "a"));
        binding.navigateTo("a.b.c.d.e.f");
        assertNotNull("进入深路径后应显示叶子 value", findElementByAttribute(section, "data-modern-config-path", "a.b.c.d.e.f.value"));
    }

    @Test
    public void infersMapAsObjectTemplate() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("server", row("host", "localhost"));

        assertEquals(ModernConfigTypeInference.TemplateType.OBJECT,
                ModernConfigTypeInference.infer("server", config.get("server"), null).getTemplateType());
    }

    private static ElementNode findElementByAttribute(ElementNode element, String attributeName, String attributeValue) {
        if (attributeValue.equals(element.getAttribute(attributeName))) {
            return element;
        }
        for (DocumentNode child : element.getChildren()) {
            if (child instanceof ElementNode) {
                ElementNode found = findElementByAttribute((ElementNode) child, attributeName, attributeValue);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Map<String, Object> row(String key, Object value) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put(key, value);
        return row;
    }
}
