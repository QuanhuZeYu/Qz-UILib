package club.heiqi.uilib.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.MutableConfig;

/**
 * 现代配置类型推断测试。
 */
public class ModernConfigTypeInferenceTest {

    @Test
    public void infersPrimitiveLeafTypes() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("name", "Qz")
                .set("count", 12)
                .set("enabled", true)
                .set("empty", null);

        assertEquals(ModernConfigTypeInference.TemplateType.STRING,
                infer(config, "name", null).getTemplateType());
        assertEquals(ModernConfigTypeInference.TemplateType.NUMBER,
                infer(config, "count", null).getTemplateType());
        assertTrue(infer(config, "count", null).isIntegerNumber());
        assertEquals(ModernConfigTypeInference.TemplateType.BOOLEAN,
                infer(config, "enabled", null).getTemplateType());
        assertEquals(ModernConfigTypeInference.TemplateType.NULL,
                infer(config, "empty", null).getTemplateType());
    }

    @Test
    public void infersLongTextFromContentAndHint() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("content", "line1\nline2")
                .set("notes", null);

        assertEquals(ModernConfigTypeInference.TemplateType.LONG_TEXT,
                infer(config, "content", null).getTemplateType());
        assertEquals(ModernConfigTypeInference.TemplateType.LONG_TEXT,
                infer(config, "notes", new ModernConfigTemplateScreen.FieldSpec("notes")
                        .setTemplateHint("textarea")).getTemplateType());
    }

    @Test
    public void infersChoicesFromFieldSpec() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("mode", "fast");
        ModernConfigTemplateScreen.FieldSpec fieldSpec = new ModernConfigTemplateScreen.FieldSpec("mode")
                .setValidValues("fast", "balanced", "safe");

        ModernConfigTypeInference.Result result = infer(config, "mode", fieldSpec);

        assertEquals(ModernConfigTypeInference.TemplateType.CHOICE, result.getTemplateType());
        assertEquals(3, result.getChoiceOptions().size());
        assertTrue(result.shouldUseSegmentedChoice());
    }

    @Test
    public void usesSelectForManyChoices() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("mode", "a");
        ModernConfigTemplateScreen.FieldSpec fieldSpec = new ModernConfigTemplateScreen.FieldSpec("mode")
                .setValidValues("a", "b", "c", "d", "e");

        ModernConfigTypeInference.Result result = infer(config, "mode", fieldSpec);

        assertEquals(ModernConfigTypeInference.TemplateType.CHOICE, result.getTemplateType());
        assertFalse(result.shouldUseSegmentedChoice());
    }

    @Test
    public void infersNullTypeFromDefaultValue() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("count", null)
                .set("enabled", null)
                .set("name", null);

        assertEquals(ModernConfigTypeInference.TemplateType.NUMBER,
                infer(config, "count", new ModernConfigTemplateScreen.FieldSpec("count")
                        .setDefaultValue(3)).getTemplateType());
        assertEquals(ModernConfigTypeInference.TemplateType.BOOLEAN,
                infer(config, "enabled", new ModernConfigTemplateScreen.FieldSpec("enabled")
                        .setDefaultValue(Boolean.TRUE)).getTemplateType());
        assertEquals(ModernConfigTypeInference.TemplateType.STRING,
                infer(config, "name", new ModernConfigTemplateScreen.FieldSpec("name")
                        .setDefaultValue("Qz")).getTemplateType());
    }

    @Test
    public void infersSimpleAndTableLists() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("names", Arrays.asList("alpha", "beta"))
                .set("servers", Arrays.asList(row("host", "a.local", "port", 25565),
                        row("host", "b.local", "port", 25566)))
                .set("mixed", Arrays.asList(row("host", "a.local"), "plain"));

        assertEquals(ModernConfigTypeInference.TemplateType.SIMPLE_LIST,
                infer(config, "names", null).getTemplateType());
        assertEquals(ModernConfigTypeInference.TemplateType.TABLE,
                infer(config, "servers", null).getTemplateType());
        assertEquals(Arrays.asList("host", "port"), infer(config, "servers", null).getTableColumns());
        assertEquals(ModernConfigTypeInference.TemplateType.READ_ONLY,
                infer(config, "mixed", null).getTemplateType());
    }

    @Test
    public void infersExplicitDynamicMapAndPresetSelector() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("labels", row("alpha", "A", "beta", "B"))
                .set("profile", row("mode", "fast", "_presets",
                        row("fast", row("mode", "fast"), "safe", row("mode", "safe"))));

        ModernConfigTemplateScreen.FieldSpec dynamicMapSpec = new ModernConfigTemplateScreen.FieldSpec("labels")
                .setTemplateHint("dynamic-map");

        assertEquals(ModernConfigTypeInference.TemplateType.KEY_VALUE_MAP,
                infer(config, "labels", dynamicMapSpec).getTemplateType());
        assertEquals(ModernConfigTypeInference.TemplateType.OBJECT,
                infer(config, "labels", null).getTemplateType());
        assertEquals(ModernConfigTypeInference.TemplateType.PRESET_SELECTOR,
                infer(config, "profile", null).getTemplateType());
    }

    @Test
    public void infersRawEditorFromHint() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("payload", "{}")
                .set("data", "x: 1");

        ModernConfigTemplateScreen.FieldSpec rawSpec =
                new ModernConfigTemplateScreen.FieldSpec("payload").setTemplateHint("raw");
        ModernConfigTemplateScreen.FieldSpec jsonSpec =
                new ModernConfigTemplateScreen.FieldSpec("payload").setTemplateHint("json");
        ModernConfigTemplateScreen.FieldSpec yamlSpec =
                new ModernConfigTemplateScreen.FieldSpec("data").setTemplateHint("yaml-editor");

        ModernConfigTypeInference.Result rawResult = infer(config, "payload", rawSpec);
        assertEquals(ModernConfigTypeInference.TemplateType.RAW_EDITOR, rawResult.getTemplateType());
        assertEquals(ConfigFormat.JSON, rawResult.getRawFormat());

        ModernConfigTypeInference.Result jsonResult = infer(config, "payload", jsonSpec);
        assertEquals(ModernConfigTypeInference.TemplateType.RAW_EDITOR, jsonResult.getTemplateType());
        assertEquals(ConfigFormat.JSON, jsonResult.getRawFormat());

        ModernConfigTypeInference.Result yamlResult = infer(config, "data", yamlSpec);
        assertEquals(ModernConfigTypeInference.TemplateType.RAW_EDITOR, yamlResult.getTemplateType());
        assertEquals(ConfigFormat.YAML, yamlResult.getRawFormat());
    }

    @Test
    public void infersRawEditorUsesFallbackForGenericHint() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON).set("payload", "{}");
        ModernConfigTemplateScreen.FieldSpec rawSpec =
                new ModernConfigTemplateScreen.FieldSpec("payload").setTemplateHint("raw");

        ModernConfigTypeInference.Result defaultResult =
                ModernConfigTypeInference.infer("payload", config.get("payload"), rawSpec, null);
        assertEquals(ConfigFormat.JSON, defaultResult.getRawFormat());

        ModernConfigTypeInference.Result yamlFallback =
                ModernConfigTypeInference.infer("payload", config.get("payload"), rawSpec, ConfigFormat.YAML);
        assertEquals(ConfigFormat.YAML, yamlFallback.getRawFormat());

        ModernConfigTypeInference.Result explicitJsonOverridesFallback =
                ModernConfigTypeInference.infer("payload", config.get("payload"),
                        new ModernConfigTemplateScreen.FieldSpec("payload").setTemplateHint("json"),
                        ConfigFormat.YAML);
        assertEquals(ConfigFormat.JSON, explicitJsonOverridesFallback.getRawFormat());
    }

    @Test
    public void infersEnhancedPickerFromHint() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("primary", "#FF8800")
                .set("texture", "minecraft:block/stone")
                .set("click", "minecraft:block.stone.click");

        ModernConfigTemplateScreen.FieldSpec colorSpec =
                new ModernConfigTemplateScreen.FieldSpec("primary").setTemplateHint("color");
        ModernConfigTemplateScreen.FieldSpec resourceSpec =
                new ModernConfigTemplateScreen.FieldSpec("texture").setTemplateHint("resource");
        ModernConfigTemplateScreen.FieldSpec soundSpec =
                new ModernConfigTemplateScreen.FieldSpec("click").setTemplateHint("sound");

        ModernConfigTypeInference.Result colorResult = infer(config, "primary", colorSpec);
        assertEquals(ModernConfigTypeInference.TemplateType.ENHANCED_PICKER, colorResult.getTemplateType());
        assertEquals(ModernConfigTypeInference.PickerKind.COLOR, colorResult.getPickerKind());

        ModernConfigTypeInference.Result resourceResult = infer(config, "texture", resourceSpec);
        assertEquals(ModernConfigTypeInference.TemplateType.ENHANCED_PICKER, resourceResult.getTemplateType());
        assertEquals(ModernConfigTypeInference.PickerKind.RESOURCE, resourceResult.getPickerKind());

        ModernConfigTypeInference.Result soundResult = infer(config, "click", soundSpec);
        assertEquals(ModernConfigTypeInference.TemplateType.ENHANCED_PICKER, soundResult.getTemplateType());
        assertEquals(ModernConfigTypeInference.PickerKind.SOUND, soundResult.getPickerKind());
    }

    @Test
    public void enhancedPickerSupportsVariantHints() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("a", "#000")
                .set("b", "asset:x")
                .set("c", "audio:y");

        assertEquals(ModernConfigTypeInference.PickerKind.COLOR,
                infer(config, "a", new ModernConfigTemplateScreen.FieldSpec("a").setTemplateHint("colour"))
                        .getPickerKind());
        assertEquals(ModernConfigTypeInference.PickerKind.COLOR,
                infer(config, "a", new ModernConfigTemplateScreen.FieldSpec("a").setTemplateHint("hex"))
                        .getPickerKind());
        assertEquals(ModernConfigTypeInference.PickerKind.RESOURCE,
                infer(config, "b", new ModernConfigTemplateScreen.FieldSpec("b").setTemplateHint("asset"))
                        .getPickerKind());
        assertEquals(ModernConfigTypeInference.PickerKind.SOUND,
                infer(config, "c", new ModernConfigTemplateScreen.FieldSpec("c").setTemplateHint("audio"))
                        .getPickerKind());
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

    private static Map<String, Object> row(String key, Object value) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put(key, value);
        return row;
    }
}
