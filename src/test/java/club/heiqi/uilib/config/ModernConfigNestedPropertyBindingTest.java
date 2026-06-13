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
        ModernNestedCategoryBinding binding = new ModernNestedCategoryBinding(config, config.asImmutable(),
                fieldsByPath, null);

        ElementNode section = binding.createSection(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
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
        ModernNestedCategoryBinding binding = new ModernNestedCategoryBinding(config, config.asImmutable(),
                ModernConfigPropertyBindings.indexFields(null), null);

        ElementNode section = binding.createSection(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());

        assertNotNull(findElementByAttribute(section, "data-modern-config-expand-path", "a.b.c.d.e"));
        binding.navigateTo("a.b.c.d.e");
        assertNotNull(findElementByAttribute(section, "data-modern-config-path", "a.b.c.d.e.f.value"));
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
