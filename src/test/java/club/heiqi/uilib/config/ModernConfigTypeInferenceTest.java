package club.heiqi.uilib.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

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

    private static ModernConfigTypeInference.Result infer(MutableConfig config, String path,
            ModernConfigTemplateScreen.FieldSpec fieldSpec) {
        return ModernConfigTypeInference.infer(path, config.get(path), fieldSpec);
    }
}
