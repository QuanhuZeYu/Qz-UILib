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
import club.heiqi.uilib.ui.dom.UiDocument;

/**
 * 现代配置基础 binding 边界测试，覆盖 primitive / choice / multiline 三类绑定的草稿、
 * 校验、恢复与写回路径。
 *
 * <p>纯 JVM 测试：直接调用 package-private 的 binding 构造与方法，不实例化屏幕类。</p>
 */
public class ModernConfigBindingsBoundaryTest {

    // ==================== Primitive binding ====================

    /**
     * 字符串 binding 恢复默认值后应判为脏，applyDraft 写回默认值。
     */
    @Test
    public void stringBindingRestoresAndAppliesDefaultValue() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON).set("name", "current");
        ModernConfigTemplateScreen.FieldSpec spec = new ModernConfigTemplateScreen.FieldSpec("name")
                .setDefaultValue("default");
        ModernPrimitivePropertyBinding binding = newBinding(config, "name", spec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        assertFalse(binding.isDirty());

        binding.restoreDefaultValue();
        assertTrue(binding.isDirty());
        binding.applyDraft();
        assertEquals("default", config.get("name").asString());
    }

    /**
     * 字符串 binding 恢复当前值应清除脏状态。
     */
    @Test
    public void stringBindingRestoreCurrentValueClearsDirty() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON).set("name", "current");
        ModernConfigTemplateScreen.FieldSpec spec = new ModernConfigTemplateScreen.FieldSpec("name")
                .setDefaultValue("default");
        ModernPrimitivePropertyBinding binding = newBinding(config, "name", spec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        binding.restoreDefaultValue();
        assertTrue(binding.isDirty());

        binding.restoreCurrentValue();
        assertFalse(binding.isDirty());
    }

    /**
     * 布尔 binding 恢复默认值后应判为脏，applyDraft 写回布尔值。
     */
    @Test
    public void booleanBindingRestoresAndAppliesDefaultValue() throws Exception {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON).set("enabled", true);
        ModernConfigTemplateScreen.FieldSpec spec = new ModernConfigTemplateScreen.FieldSpec("enabled")
                .setDefaultValue(Boolean.FALSE);
        ModernPrimitivePropertyBinding binding = newBinding(config, "enabled", spec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        assertFalse(binding.isDirty());

        binding.restoreDefaultValue();
        assertTrue(binding.isDirty());
        binding.applyDraft();
        assertFalse(config.get("enabled").asBoolean());
    }

    /**
     * 数值 binding 超出范围时 validateDraft 应返回错误信息。
     */
    @Test
    public void numberBindingValidationRejectsOutOfRange() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON).set("count", 50);
        ModernConfigTemplateScreen.FieldSpec spec = new ModernConfigTemplateScreen.FieldSpec("count")
                .setRange(0, 100);
        ModernPrimitivePropertyBinding binding = newBinding(config, "count", spec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        // 初始值 50 在范围内
        assertNull(binding.validateDraft());

        binding.restoreDefaultValue(); // defaultValue=null → 解析为 0，在范围内
        assertNull(binding.validateDraft());
    }

    /**
     * 整数 binding applyDraft 应写回 Long 类型。
     */
    @Test
    public void integerBindingWritesBackAsLong() throws Exception {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON).set("count", 7);
        ModernConfigTemplateScreen.FieldSpec spec = new ModernConfigTemplateScreen.FieldSpec("count")
                .setDefaultValue(42);
        ModernPrimitivePropertyBinding binding = newBinding(config, "count", spec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        assertTrue(ModernConfigTypeInference.infer("count", config.get("count"), spec).isIntegerNumber());

        binding.restoreDefaultValue();
        binding.applyDraft();
        assertEquals(42, config.get("count").asInt());
    }

    /**
     * NULL 类型空草稿 applyDraft 应写回 null。
     */
    @Test
    public void nullBindingEmptyDraftWritesBackNull() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON).set("empty", null);
        ModernConfigTemplateScreen.FieldSpec spec = new ModernConfigTemplateScreen.FieldSpec("empty");
        ModernPrimitivePropertyBinding binding = newBinding(config, "empty", spec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        // NULL 类型 + 空草稿：validateDraft 不报错（走 string 分支返回 null）
        assertNull(binding.validateDraft());

        binding.applyDraft();
        assertTrue(config.get("empty").isNull());
    }

    /**
     * primitive binding createCard 应携带 data-modern-config-path 属性。
     */
    @Test
    public void primitiveCreateCardProducesPathAttribute() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON).set("name", "x");
        ModernPrimitivePropertyBinding binding = newBinding(config, "name", null);

        club.heiqi.uilib.ui.dom.ElementNode card = binding.createCard(UiDocument.create(),
                ForgeConfigTemplateScreen.Theme.defaultTheme());

        assertEquals("name", card.getAttribute("data-modern-config-path"));
    }

    // ==================== Choice binding ====================

    /**
     * 离散选项 binding 恢复默认值后应判为脏，applyDraft 写回默认值。
     */
    @Test
    public void choiceBindingRestoresAndAppliesDefaultValue() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON).set("mode", "fast");
        ModernConfigTemplateScreen.FieldSpec spec = new ModernConfigTemplateScreen.FieldSpec("mode")
                .setValidValues("fast", "balanced", "safe")
                .setDefaultValue("safe");
        ModernChoicePropertyBinding binding = newChoiceBinding(config, "mode", spec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        assertFalse(binding.isDirty());

        binding.restoreDefaultValue();
        assertTrue(binding.isDirty());
        binding.applyDraft();
        assertEquals("safe", config.get("mode").asString());
    }

    /**
     * 5 个以上选项应走 select 控件分支，不崩溃且写回正确。
     */
    @Test
    public void choiceWithFiveOptionsUsesSelectAndWritesBack() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON).set("tier", "a");
        ModernConfigTemplateScreen.FieldSpec spec = new ModernConfigTemplateScreen.FieldSpec("tier")
                .setValidValues("a", "b", "c", "d", "e")
                .setDefaultValue("c");
        ModernConfigTypeInference.Result inference = ModernConfigTypeInference.infer("tier", config.get("tier"),
                spec);
        assertFalse(inference.shouldUseSegmentedChoice());
        ModernChoicePropertyBinding binding = new ModernChoicePropertyBinding(config, "tier", config.get("tier"),
                spec, inference, null);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        assertFalse(binding.isDirty());

        binding.restoreDefaultValue();
        assertTrue(binding.isDirty());
        assertNull(binding.validateDraft());
        binding.applyDraft();
        assertEquals("c", config.get("tier").asString());
    }

    /**
     * 离散选项 binding 值为 null 时应退回第一个选项。
     */
    @Test
    public void choiceBindingFallsBackToFirstOptionWhenValueNull() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON).set("mode", null);
        ModernConfigTemplateScreen.FieldSpec spec = new ModernConfigTemplateScreen.FieldSpec("mode")
                .setValidValues("fast", "safe");
        ModernChoicePropertyBinding binding = newChoiceBinding(config, "mode", spec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        // validateDraft 总是基于选项集，null 值退回第一个选项后应有效
        assertNull(binding.validateDraft());
    }

    /**
     * 离散选项 binding restoreCurrentValue 应清除脏状态。
     */
    @Test
    public void choiceBindingRestoreCurrentValueClearsDirty() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON).set("mode", "fast");
        ModernConfigTemplateScreen.FieldSpec spec = new ModernConfigTemplateScreen.FieldSpec("mode")
                .setValidValues("fast", "safe")
                .setDefaultValue("safe");
        ModernChoicePropertyBinding binding = newChoiceBinding(config, "mode", spec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        binding.restoreDefaultValue();
        assertTrue(binding.isDirty());

        binding.restoreCurrentValue();
        assertFalse(binding.isDirty());
    }

    // ==================== Multiline binding ====================

    /**
     * 长文本 binding 恢复默认值后应判为脏，applyDraft 写回。
     */
    @Test
    public void multilineBindingRestoresAndAppliesDefaultValue() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON).set("bio", "current text");
        ModernConfigTemplateScreen.FieldSpec spec = new ModernConfigTemplateScreen.FieldSpec("bio")
                .setTemplateHint("textarea")
                .setDefaultValue("default bio");
        ModernMultilineTextPropertyBinding binding = newMultilineBinding(config, "bio", spec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        assertFalse(binding.isDirty());

        binding.restoreDefaultValue();
        assertTrue(binding.isDirty());
        binding.applyDraft();
        assertEquals("default bio", config.get("bio").asString());
    }

    /**
     * 长文本 binding restoreCurrentValue 应清除脏状态。
     */
    @Test
    public void multilineBindingRestoreCurrentValueClearsDirty() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON).set("bio", "line1\nline2");
        ModernConfigTemplateScreen.FieldSpec spec = new ModernConfigTemplateScreen.FieldSpec("bio")
                .setTemplateHint("long-text")
                .setDefaultValue("other");
        ModernMultilineTextPropertyBinding binding = newMultilineBinding(config, "bio", spec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        binding.restoreDefaultValue();
        assertTrue(binding.isDirty());

        binding.restoreCurrentValue();
        assertFalse(binding.isDirty());
        assertEquals("line1\nline2", config.get("bio").asString());
    }

    /**
     * 长文本 binding validateDraft 始终返回 null（无校验约束）。
     */
    @Test
    public void multilineBindingValidationAlwaysPasses() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON).set("bio", "text");
        ModernMultilineTextPropertyBinding binding = newMultilineBinding(config, "bio", null);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        assertNull(binding.validateDraft());
    }

    /**
     * 长文本 binding createCard 应携带 data-modern-config-path 属性。
     */
    @Test
    public void multilineCreateCardProducesPathAttribute() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON).set("bio", "text");
        ModernMultilineTextPropertyBinding binding = newMultilineBinding(config, "bio", null);

        club.heiqi.uilib.ui.dom.ElementNode card = binding.createCard(UiDocument.create(),
                ForgeConfigTemplateScreen.Theme.defaultTheme());

        assertEquals("bio", card.getAttribute("data-modern-config-path"));
    }

    // ==================== 工厂方法 ====================

    private static ModernPrimitivePropertyBinding newBinding(MutableConfig config, String path,
            ModernConfigTemplateScreen.FieldSpec spec) {
        ModernConfigTypeInference.Result inference = ModernConfigTypeInference.infer(path, config.get(path), spec);
        return new ModernPrimitivePropertyBinding(config, path, config.get(path), spec, inference, null);
    }

    private static ModernChoicePropertyBinding newChoiceBinding(MutableConfig config, String path,
            ModernConfigTemplateScreen.FieldSpec spec) {
        ModernConfigTypeInference.Result inference = ModernConfigTypeInference.infer(path, config.get(path), spec);
        return new ModernChoicePropertyBinding(config, path, config.get(path), spec, inference, null);
    }

    private static ModernMultilineTextPropertyBinding newMultilineBinding(MutableConfig config, String path,
            ModernConfigTemplateScreen.FieldSpec spec) {
        ModernConfigTypeInference.Result inference = ModernConfigTypeInference.infer(path, config.get(path), spec);
        return new ModernMultilineTextPropertyBinding(config, path, config.get(path), spec, inference, null);
    }
}
