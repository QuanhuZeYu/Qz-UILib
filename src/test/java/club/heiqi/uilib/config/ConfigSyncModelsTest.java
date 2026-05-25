package club.heiqi.uilib.config;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.config.FontConfig;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * `ConfigSyncModels` 的纯 JVM 测试。
 */
public class ConfigSyncModelsTest {

    /**
     * 验证配置定义、副本与草稿回写链路。
     */
    @Test
    public void shouldCaptureDefinitionCopyAndApplyDraft() {
        Configuration configuration = sampleConfiguration();
        ConfigSyncModels.ConfigDefinitionSnapshot definition = ConfigSyncModels.captureDefinition(configuration,
                Collections.singletonList(new ConfigSyncCategorySpec("general", "General", "测试分类")));

        Assert.assertEquals(1, definition.categories.size());
        Assert.assertEquals(3, definition.categories.get(0).properties.size());

        Configuration copy = ConfigSyncModels.copyConfiguration(configuration, definition);
        ConfigSyncModels.ConfigDraftSnapshot draft = ConfigSyncModels.captureDraft(copy, definition);
        draft.values.put(ConfigSyncModels.buildFieldKey("general", "mode"), "debug");
        draft.values.put(ConfigSyncModels.buildFieldKey("general", "fontScale"), "1.5");
        draft.values.put(ConfigSyncModels.buildFieldKey("general", "fontOrder"), "default, emoji");

        ConfigSyncModels.applyDraft(copy, definition, draft);

        Assert.assertEquals("debug", copy.getCategory("general").get("mode").getString());
        Assert.assertEquals(1.5D, copy.getCategory("general").get("fontScale").getDouble(), 0.0001D);
        Assert.assertArrayEquals(new String[] { "default", "emoji" },
                copy.getCategory("general").get("fontOrder").getStringList());
    }

    /**
     * 验证字段校验会拒绝不存在字段和非法值。
     */
    @Test
    public void shouldValidateChangeAgainstDefinition() {
        Configuration configuration = sampleConfiguration();
        ConfigSyncModels.ConfigDefinitionSnapshot definition = ConfigSyncModels.captureDefinition(configuration,
                Collections.singletonList(new ConfigSyncCategorySpec("general", "General", "测试分类")));

        ConfigSyncModels.ConfigFieldChange missing = new ConfigSyncModels.ConfigFieldChange();
        missing.fieldKey = "missing:key";
        missing.draftValue = "value";
        Assert.assertFalse(ConfigSyncModels.validateChange(configuration, definition, missing).accepted);

        ConfigSyncModels.ConfigFieldChange invalid = new ConfigSyncModels.ConfigFieldChange();
        invalid.fieldKey = ConfigSyncModels.buildFieldKey("general", "mode");
        invalid.draftValue = "legacy";
        ConfigSyncModels.ConfigFieldValidationResult invalidResult =
                ConfigSyncModels.validateChange(configuration, definition, invalid);
        Assert.assertFalse(invalidResult.accepted);
        Assert.assertTrue(invalidResult.message.contains("以下值之一"));

        ConfigSyncModels.ConfigFieldChange valid = new ConfigSyncModels.ConfigFieldChange();
        valid.fieldKey = ConfigSyncModels.buildFieldKey("general", "mode");
        valid.draftValue = "safe";
        Assert.assertTrue(ConfigSyncModels.validateChange(configuration, definition, valid).accepted);
    }

    /**
     * 验证分类名默认按大小写敏感精确匹配。
     */
    @Test
    public void shouldNotMatchLowercaseCategoryWithoutExplicitAlias() {
        Configuration configuration = new Configuration();
        configuration.get("fontsystem", "fontSort", new String[] { "Alpha" }, "字体排序");

        ConfigSyncModels.ConfigDefinitionSnapshot definition = ConfigSyncModels.captureDefinition(configuration,
                Collections.singletonList(new ConfigSyncCategorySpec(FontConfig.CATEGORY, "Font System",
                        "字体配置")));

        Assert.assertTrue(definition.categories.isEmpty());
    }

    /**
     * 验证显式 alias 可以读写历史分类名。
     */
    @Test
    public void shouldResolveLowercaseCategoryWithExplicitAlias() {
        Configuration configuration = new Configuration();
        configuration.get("fontsystem", "fontSort", new String[] { "Alpha" }, "字体排序");

        ConfigSyncModels.ConfigDefinitionSnapshot definition = ConfigSyncModels.captureDefinition(configuration,
                Collections.singletonList(new ConfigSyncCategorySpec(FontConfig.CATEGORY, "Font System",
                        "字体配置").addAlias("fontsystem")));

        Assert.assertEquals(1, definition.categories.size());
        Assert.assertEquals(FontConfig.CATEGORY, definition.categories.get(0).categoryName);
        Assert.assertEquals("fontsystem", definition.categories.get(0).actualCategoryName);

        ConfigSyncModels.ConfigDraftSnapshot draft = ConfigSyncModels.captureDraft(configuration, definition);
        draft.values.put(ConfigSyncModels.buildFieldKey(FontConfig.CATEGORY, "fontSort"), "Beta, Gamma");
        ConfigSyncModels.applyDraft(configuration, definition, draft);

        Assert.assertArrayEquals(new String[] { "Beta", "Gamma" },
                configuration.getCategory("fontsystem").get("fontSort").getStringList());
        Assert.assertFalse(configuration.hasCategory(FontConfig.CATEGORY)
                && configuration.getCategory(FontConfig.CATEGORY).containsKey("fontSort"));
    }

    /**
     * 验证会话状态 copy 不共享可变 Map。
     */
    @Test
    public void shouldCopySessionStateDefensively() {
        ConfigSyncModels.ConfigSessionState state = new ConfigSyncModels.ConfigSessionState();
        state.sessionId = "s1";
        state.screenId = "screen";
        state.draft.values.put("general:mode", "safe");
        state.fieldErrors.put("general:mode", "bad");

        ConfigSyncModels.ConfigSessionState copy = state.copy();
        copy.draft.values.put("general:fontscale", "1.2");
        copy.fieldErrors.put("general:fontscale", "oops");

        Assert.assertFalse(state.draft.values.containsKey("general:fontscale"));
        Assert.assertFalse(state.fieldErrors.containsKey("general:fontscale"));
    }

    private static Configuration sampleConfiguration() {
        Configuration configuration = new Configuration();
        Property mode = configuration.get("general", "mode", "normal", "运行模式");
        mode.setValidValues(new String[] { "normal", "safe", "debug" });
        configuration.get("general", "fontScale", 1.0D, "字体缩放", 0.5D, 2.0D);
        configuration.get("general", "fontOrder", new String[] { "default", "fallback" }, "字体顺序");
        configuration.getCategory("general").setPropertyOrder(Arrays.asList("mode", "fontScale", "fontOrder"));
        return configuration;
    }
}
