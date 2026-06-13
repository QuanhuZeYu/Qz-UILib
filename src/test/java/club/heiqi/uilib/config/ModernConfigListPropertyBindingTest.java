package club.heiqi.uilib.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.ui.dom.UiDocument;

/**
 * 现代配置列表模板绑定测试。
 */
public class ModernConfigListPropertyBindingTest {

    @Test
    public void splitsBatchImportLinesWithEmptyPolicy() {
        assertEquals(Arrays.asList("a", "b"), ModernSimpleListPropertyBinding.parseImportLines("a\n\nb", false));
        assertEquals(Arrays.asList("a", "", "b", ""),
                ModernSimpleListPropertyBinding.parseImportLines("a\n\nb\n", true));
    }

    @Test
    public void appliesPrimitiveListDefaultDraft() throws Exception {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("ports", Arrays.asList(Integer.valueOf(1), Integer.valueOf(2)));
        ModernConfigTemplateScreen.FieldSpec fieldSpec = new ModernConfigTemplateScreen.FieldSpec("ports")
                .setDefaultValue(Arrays.asList(Integer.valueOf(3), Integer.valueOf(4)));
        ModernSimpleListPropertyBinding binding = new ModernSimpleListPropertyBinding(config, "ports",
                config.get("ports"), fieldSpec, infer(config, "ports", fieldSpec), null);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        binding.restoreDefaultValue();

        assertTrue(binding.isDirty());
        assertNull(binding.validateDraft());
        binding.applyDraft();
        assertEquals(3, config.get("ports").get(0).asInt());
        assertEquals(4, config.get("ports").get(1).asInt());
    }

    @Test
    public void appliesTableDefaultDraft() throws Exception {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("servers", Arrays.asList(row("host", "old.local", "port", Integer.valueOf(25565))));
        ModernConfigTemplateScreen.FieldSpec fieldSpec = new ModernConfigTemplateScreen.FieldSpec("servers")
                .setDefaultValue(Arrays.asList(row("host", "new.local", "port", Integer.valueOf(25566))));
        ModernTablePropertyBinding binding = new ModernTablePropertyBinding(config, "servers", config.get("servers"),
                fieldSpec, infer(config, "servers", fieldSpec), null);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        binding.restoreDefaultValue();

        assertTrue(binding.isDirty());
        assertNull(binding.validateDraft());
        binding.applyDraft();
        assertEquals("new.local", config.get("servers").get(0).get("host").asString());
        assertEquals(25566, config.get("servers").get(0).get("port").asInt());
    }

    private static ModernConfigTypeInference.Result infer(MutableConfig config, String path,
            ModernConfigTemplateScreen.FieldSpec fieldSpec) {
        return ModernConfigTypeInference.infer(path, config.get(path), fieldSpec);
    }

    private static Map<String, Object> row(String firstKey, Object firstValue, String secondKey, Object secondValue) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put(firstKey, firstValue);
        row.put(secondKey, secondValue);
        return row;
    }
}
