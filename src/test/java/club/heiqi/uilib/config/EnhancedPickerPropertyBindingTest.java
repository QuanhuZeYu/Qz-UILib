package club.heiqi.uilib.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.ui.control.DocumentColorPickerControl;
import club.heiqi.uilib.ui.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.dom.UiDocument;

/**
 * 增强选择器字段绑定测试。
 *
 * <p>覆盖：COLOR 的 ARGB↔HEX 往返、COLOR 编辑写回 draft、RESOURCE/SOUND 文本兜底、
 * 非法颜色 confirm 时 isValid=false 保留旧 draft 值、初始非法颜色退回默认色。</p>
 */
public class EnhancedPickerPropertyBindingTest {

    @Test
    public void argbToHexRoundTrip() {
        // alpha 0xFF：返回紧凑 6 位
        assertEquals("#FF8800", EnhancedPickerPropertyBinding.formatArgbAsHex(0xFFFF8800));
        assertEquals("#000000", EnhancedPickerPropertyBinding.formatArgbAsHex(0xFF000000));
        // alpha != 0xFF：返回完整 8 位
        assertEquals("#80FF8800", EnhancedPickerPropertyBinding.formatArgbAsHex(0x80FF8800));
        assertEquals("#00FFFFFF", EnhancedPickerPropertyBinding.formatArgbAsHex(0x00FFFFFF));
    }

    @Test
    public void hexToArgbRoundTrip() {
        assertEquals(Integer.valueOf(0xFFFF8800), EnhancedPickerPropertyBinding.parseHexQuiet("#FF8800"));
        assertEquals(Integer.valueOf(0xFFFF8800), EnhancedPickerPropertyBinding.parseHexQuiet("FF8800"));
        assertEquals(Integer.valueOf(0xFFFF8800), EnhancedPickerPropertyBinding.parseHexQuiet("#ff8800"));
        assertEquals(Integer.valueOf(0x80FF8800), EnhancedPickerPropertyBinding.parseHexQuiet("#80FF8800"));
        // 非法
        assertNull(EnhancedPickerPropertyBinding.parseHexQuiet("nope"));
        assertNull(EnhancedPickerPropertyBinding.parseHexQuiet(""));
        assertNull(EnhancedPickerPropertyBinding.parseHexQuiet(null));
        assertNull(EnhancedPickerPropertyBinding.parseHexQuiet("#FFF"));
        assertNull(EnhancedPickerPropertyBinding.parseHexQuiet("#1234567"));
    }

    @Test
    public void colorPickerWritesBackHexDraft() throws Exception {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("theme.primary", "#FF8800");
        ModernConfigTemplateScreen.FieldSpec fieldSpec = new ModernConfigTemplateScreen.FieldSpec("theme.primary")
                .setTemplateHint("color");
        EnhancedPickerPropertyBinding binding = newBinding(config, "theme.primary", fieldSpec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        DocumentColorPickerControl colorControl = binding.getColorControl();
        assertNotNull(colorControl);
        assertNull(binding.getTextControl());
        assertFalse(binding.isDirty());
        // 初次渲染：初始值 #FF8800 应被解析为 ARGB 0xFFFF8800
        assertEquals(0xFFFF8800, colorControl.getColor());

        // 用户改成新颜色
        binding.simulateColorChange(0xFF112233);
        assertTrue(binding.isDirty());
        assertEquals("#112233", binding.getDraftValue());
        assertNull(binding.validateDraft());

        binding.applyDraft();
        assertEquals("#112233", config.get("theme.primary").asString());
    }

    @Test
    public void colorWithAlphaUsesAarrggbbForm() throws Exception {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("overlay.tint", "#80FF8800");
        ModernConfigTemplateScreen.FieldSpec fieldSpec = new ModernConfigTemplateScreen.FieldSpec("overlay.tint")
                .setTemplateHint("color");
        EnhancedPickerPropertyBinding binding = newBinding(config, "overlay.tint", fieldSpec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        // alpha 非 0xFF 时 draftValue 保留 8 位形式
        assertEquals("#80FF8800", binding.getDraftValue());
        assertFalse(binding.isDirty());

        binding.simulateColorChange(0xFF4060FF);
        assertEquals("#4060FF", binding.getDraftValue());
        binding.applyDraft();
        assertEquals("#4060FF", config.get("overlay.tint").asString());
    }

    @Test
    public void unlawfulColorKeepsOldDraftOnInvalidConfirm() throws Exception {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("theme.primary", "#FF8800");
        ModernConfigTemplateScreen.FieldSpec fieldSpec = new ModernConfigTemplateScreen.FieldSpec("theme.primary")
                .setTemplateHint("color");
        EnhancedPickerPropertyBinding binding = newBinding(config, "theme.primary", fieldSpec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        DocumentColorPickerControl colorControl = binding.getColorControl();
        // 先改成合法值
        binding.simulateColorChange(0xFF112233);
        assertEquals("#112233", binding.getDraftValue());

        // 模拟用户输入非法 HEX：setHex 失败时不触发 changeHandler，但 commitNow 会触发 confirm
        colorControl.setHex("not_a_color");
        assertFalse(colorControl.getError().isEmpty());
        colorControl.commitNow();

        // draftValue 保留上一次合法值，不被非法输入污染
        assertEquals("#112233", binding.getDraftValue());
        // applyDraft 仍写最后一次合法值
        binding.applyDraft();
        assertEquals("#112233", config.get("theme.primary").asString());
    }

    @Test
    public void resourceHintFallsBackToTextInput() throws Exception {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("texture.block", "minecraft:block/stone");
        ModernConfigTemplateScreen.FieldSpec fieldSpec = new ModernConfigTemplateScreen.FieldSpec("texture.block")
                .setTemplateHint("resource");
        EnhancedPickerPropertyBinding binding = newBinding(config, "texture.block", fieldSpec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        DocumentTextInputControl textControl = binding.getTextControl();
        assertNotNull(textControl);
        assertNull(binding.getColorControl());
        assertFalse(binding.isDirty());
        assertEquals("minecraft:block/stone", textControl.getText());

        // 用户编辑
        binding.simulateTextChange("minecraft:block/cobblestone");
        assertTrue(binding.isDirty());
        assertEquals("minecraft:block/cobblestone", binding.getDraftValue());
        assertNull(binding.validateDraft());

        binding.applyDraft();
        assertEquals("minecraft:block/cobblestone", config.get("texture.block").asString());
    }

    @Test
    public void soundHintFallsBackToTextInput() throws Exception {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("audio.click", "minecraft:block.stone.click");
        ModernConfigTemplateScreen.FieldSpec fieldSpec = new ModernConfigTemplateScreen.FieldSpec("audio.click")
                .setTemplateHint("sound");
        EnhancedPickerPropertyBinding binding = newBinding(config, "audio.click", fieldSpec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        DocumentTextInputControl textControl = binding.getTextControl();
        assertNotNull(textControl);
        // 占位文案应体现 sound hint
        assertNotNull(textControl.getElement().getAttribute("placeholder"));

        binding.simulateTextChange("minecraft:entity.pig.ambient");
        binding.applyDraft();
        assertEquals("minecraft:entity.pig.ambient", config.get("audio.click").asString());
    }

    @Test
    public void restoreCurrentValueResetsColorDraft() throws Exception {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("theme.primary", "#FF8800");
        ModernConfigTemplateScreen.FieldSpec fieldSpec = new ModernConfigTemplateScreen.FieldSpec("theme.primary")
                .setTemplateHint("color");
        EnhancedPickerPropertyBinding binding = newBinding(config, "theme.primary", fieldSpec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        DocumentColorPickerControl colorControl = binding.getColorControl();
        binding.simulateColorChange(0xFF112233);
        assertTrue(binding.isDirty());

        binding.restoreCurrentValue();
        assertFalse(binding.isDirty());
        assertEquals("#FF8800", binding.getDraftValue());
        assertEquals(0xFFFF8800, colorControl.getColor());
    }

    @Test
    public void restoreCurrentValueResetsTextDraft() throws Exception {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("audio.click", "minecraft:block.stone.click");
        ModernConfigTemplateScreen.FieldSpec fieldSpec = new ModernConfigTemplateScreen.FieldSpec("audio.click")
                .setTemplateHint("sound");
        EnhancedPickerPropertyBinding binding = newBinding(config, "audio.click", fieldSpec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        DocumentTextInputControl textControl = binding.getTextControl();
        binding.simulateTextChange("minecraft:entity.pig.ambient");
        assertTrue(binding.isDirty());

        binding.restoreCurrentValue();
        assertFalse(binding.isDirty());
        assertEquals("minecraft:block.stone.click", binding.getDraftValue());
        assertEquals("minecraft:block.stone.click", textControl.getText());
    }

    @Test
    public void initialIllegalColorFallsBackToDefault() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("theme.primary", "garbage");
        ModernConfigTemplateScreen.FieldSpec fieldSpec = new ModernConfigTemplateScreen.FieldSpec("theme.primary")
                .setTemplateHint("color");
        EnhancedPickerPropertyBinding binding = newBinding(config, "theme.primary", fieldSpec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        DocumentColorPickerControl colorControl = binding.getColorControl();
        // 初始值非法：binding 退回默认色，且不判为脏
        assertEquals(DocumentColorPickerControl.DEFAULT_COLOR, colorControl.getColor());
        assertFalse(binding.isDirty());
        assertEquals("#000000", binding.getDraftValue());
    }

    private static EnhancedPickerPropertyBinding newBinding(MutableConfig config, String path,
            ModernConfigTemplateScreen.FieldSpec fieldSpec) {
        ModernConfigTypeInference.Result inference = ModernConfigTypeInference.infer(path, config.get(path),
                fieldSpec);
        return new EnhancedPickerPropertyBinding(config, path, config.get(path), fieldSpec, inference, null);
    }
}
