package club.heiqi.uilib.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.ui.control.DocumentKeyValueEditorControl;

/**
 * 现代配置动态 map 与预设模板绑定测试。
 */
public class ModernConfigDynamicMapPresetPropertyBindingTest {

    @Test
    public void appliesDynamicMapDraftWithTypedValues() throws Exception {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("labels", row("alpha", "old", "enabled", Boolean.TRUE));
        ModernConfigTemplateScreen.FieldSpec fieldSpec = new ModernConfigTemplateScreen.FieldSpec("labels")
                .setTemplateHint("dynamic-map");
        ModernKeyValueMapPropertyBinding binding = new ModernKeyValueMapPropertyBinding(config, "labels",
                config.get("labels"), fieldSpec, infer(config, "labels", fieldSpec), null);

        binding.replaceDraftRows(Arrays.asList(
                new DocumentKeyValueEditorControl.Row("alpha", "2", DocumentKeyValueEditorControl.ValueType.NUMBER),
                new DocumentKeyValueEditorControl.Row("enabled", "false",
                        DocumentKeyValueEditorControl.ValueType.BOOLEAN),
                new DocumentKeyValueEditorControl.Row("empty", "", DocumentKeyValueEditorControl.ValueType.NULL)));

        assertTrue(binding.isDirty());
        assertNull(binding.validateDraft());
        binding.applyDraft();
        assertEquals(2, config.get("labels.alpha").asInt());
        assertFalse(config.get("labels.enabled").asBoolean());
        assertTrue(config.get("labels.empty").isNull());
    }

    @Test
    public void reportsDuplicateDynamicMapKeys() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("labels", row("alpha", "old"));
        ModernConfigTemplateScreen.FieldSpec fieldSpec = new ModernConfigTemplateScreen.FieldSpec("labels")
                .setTemplateHint("key-value-map");
        ModernKeyValueMapPropertyBinding binding = new ModernKeyValueMapPropertyBinding(config, "labels",
                config.get("labels"), fieldSpec, infer(config, "labels", fieldSpec), null);

        binding.replaceDraftRows(Arrays.asList(
                new DocumentKeyValueEditorControl.Row("alpha", "one", DocumentKeyValueEditorControl.ValueType.STRING),
                new DocumentKeyValueEditorControl.Row("alpha", "two", DocumentKeyValueEditorControl.ValueType.STRING)));

        assertTrue(binding.validateDraft().contains("重复"));
    }

    @Test
    public void appliesPresetDefinitionAndKeepsPresetStorage() throws Exception {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("profile", row("mode", "fast", "threads", Integer.valueOf(2), "_presets",
                        row("fast", row("mode", "fast", "threads", Integer.valueOf(2)),
                                "safe", row("mode", "safe", "threads", Integer.valueOf(1)))));
        ModernPresetSelectorPropertyBinding binding = new ModernPresetSelectorPropertyBinding(config, "profile",
                config.get("profile"), null, infer(config, "profile", null), null);

        assertFalse(binding.isDirty());
        assertFalse(binding.isPresetModified());

        assertTrue(binding.selectPreset("safe"));
        assertTrue(binding.isDirty());
        assertNull(binding.validateDraft());
        binding.applyDraft();

        assertEquals("safe", config.get("profile.mode").asString());
        assertEquals(1, config.get("profile.threads").asInt());
        assertTrue(config.get("profile._presets").getType() == club.heiqi.config.ConfigNode.NodeType.MAP);
    }

    private static ModernConfigTypeInference.Result infer(MutableConfig config, String path,
            ModernConfigTemplateScreen.FieldSpec fieldSpec) {
        return ModernConfigTypeInference.infer(path, config.get(path), fieldSpec);
    }

    private static Map<String, Object> row(Object... entries) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        for (int index = 0; index + 1 < entries.length; index += 2) {
            row.put(String.valueOf(entries[index]), entries[index + 1]);
        }
        return row;
    }
}
