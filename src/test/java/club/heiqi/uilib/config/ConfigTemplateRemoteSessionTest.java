package club.heiqi.uilib.config;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.config.FontConfig;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * `ConfigTemplateRemoteSession` 的纯 JVM 测试。
 */
public class ConfigTemplateRemoteSessionTest {

    /**
     * 验证字段变更会写入服务端草稿，并在显式保存后回写权威配置。
     */
    @Test
    public void shouldApplyDraftAndSaveToAuthoritativeConfiguration() {
        Configuration configuration = sampleConfiguration();
        final int[] saveCalls = new int[1];
        ConfigSyncTarget target = ConfigSyncTarget.builder("test-config", configuration)
                .title("Test Config")
                .categories(java.util.Collections.singletonList(
                        new ConfigSyncCategorySpec("general", "General", "测试分类")))
                .saveAction(new ConfigSyncTarget.SaveAction() {
                    @Override
                    public void save(Configuration configuration) {
                        saveCalls[0]++;
                    }
                })
                .build();

        ConfigTemplateRemoteSession session = new ConfigTemplateRemoteSession(target, "playerA");
        ConfigSyncModels.ConfigFieldChange change = new ConfigSyncModels.ConfigFieldChange();
        change.fieldKey = ConfigSyncModels.buildFieldKey("general", "mode");
        change.draftValue = "debug";

        ConfigSyncModels.ConfigFieldValidationResult result = session.applyChange(change);

        Assert.assertTrue(result.accepted);
        Assert.assertEquals("debug", session.snapshotState().draft.values.get(change.fieldKey));

        ConfigSyncModels.ConfigSaveResult saveResult = session.save();

        Assert.assertTrue(saveResult.success);
        Assert.assertEquals("debug", configuration.getCategory("general").get("mode").getString());
        Assert.assertEquals(1, saveCalls[0]);
    }

    /**
     * 验证服务端保存前会拦截字段级校验失败。
     */
    @Test
    public void shouldRejectInvalidDraftBeforeSaving() {
        Configuration configuration = sampleConfiguration();
        ConfigSyncTarget target = ConfigSyncTarget.builder("test-config", configuration)
                .categories(java.util.Collections.singletonList(
                        new ConfigSyncCategorySpec("general", "General", "测试分类")))
                .build();

        ConfigTemplateRemoteSession session = new ConfigTemplateRemoteSession(target, "playerA");
        ConfigSyncModels.ConfigDraftSnapshot draft = new ConfigSyncModels.ConfigDraftSnapshot();
        draft.values.put(ConfigSyncModels.buildFieldKey("general", "mode"), "legacy");
        session.applyDraftSnapshot(draft);

        ConfigSyncModels.ConfigSaveResult saveResult = session.save();

        Assert.assertFalse(saveResult.success);
        Assert.assertTrue(saveResult.message.contains("保存前校验失败"));
        Assert.assertEquals("normal", configuration.getCategory("general").get("mode").getString());
    }

    /**
     * 验证保存时会沿用会话定义中解析到的实际分类名。
     */
    @Test
    public void shouldSaveAliasResolvedCategoryToAuthoritativeConfiguration() {
        Configuration configuration = new Configuration();
        configuration.get("fontsystem", "fontSort", new String[] { "Alpha" }, "字体排序");
        ConfigSyncTarget target = ConfigSyncTarget.builder("test-config", configuration)
                .categories(java.util.Collections.singletonList(new ConfigSyncCategorySpec(FontConfig.CATEGORY,
                        "Font System", "字体配置").addAlias("fontsystem")))
                .saveAction(noopSaveAction())
                .build();
        ConfigTemplateRemoteSession session = new ConfigTemplateRemoteSession(target, "playerA");
        ConfigSyncModels.ConfigDraftSnapshot draft = new ConfigSyncModels.ConfigDraftSnapshot();
        draft.values.put(ConfigSyncModels.buildFieldKey(FontConfig.CATEGORY, "fontSort"), "Beta, Gamma");
        session.applyDraftSnapshot(draft);

        ConfigSyncModels.ConfigSaveResult saveResult = session.save();

        Assert.assertTrue(saveResult.message, saveResult.success);
        Assert.assertArrayEquals(new String[] { "Beta", "Gamma" },
                configuration.getCategory("fontsystem").get("fontSort").getStringList());
        Assert.assertFalse(configuration.hasCategory(FontConfig.CATEGORY)
                && configuration.getCategory(FontConfig.CATEGORY).containsKey("fontSort"));
    }

    /**
     * 验证部分草稿保存不会丢失会话已有完整草稿。
     */
    @Test
    public void shouldPreserveExistingDraftWhenApplyingPartialSnapshot() {
        Configuration configuration = sampleConfiguration();
        ConfigSyncTarget target = ConfigSyncTarget.builder("test-config", configuration)
                .categories(java.util.Collections.singletonList(
                        new ConfigSyncCategorySpec("general", "General", "测试分类")))
                .saveAction(noopSaveAction())
                .build();
        ConfigTemplateRemoteSession session = new ConfigTemplateRemoteSession(target, "playerA");
        ConfigSyncModels.ConfigDraftSnapshot draft = new ConfigSyncModels.ConfigDraftSnapshot();
        draft.values.put(ConfigSyncModels.buildFieldKey("general", "mode"), "debug");

        session.applyDraftSnapshot(draft);
        ConfigSyncModels.ConfigSaveResult saveResult = session.save();

        Assert.assertTrue(saveResult.message, saveResult.success);
        Assert.assertEquals("debug", configuration.getCategory("general").get("mode").getString());
        Assert.assertEquals(1.0D, configuration.getCategory("general").get("fontScale").getDouble(), 0.0001D);
    }

    /**
     * 验证保存阶段权威属性缺失时返回配置同步字段错误。
     */
    @Test
    public void shouldReportMissingAuthoritativePropertyDuringSave() {
        Configuration configuration = sampleConfiguration();
        ConfigSyncTarget target = ConfigSyncTarget.builder("test-config", configuration)
                .categories(java.util.Collections.singletonList(
                        new ConfigSyncCategorySpec("general", "General", "测试分类")))
                .build();
        ConfigTemplateRemoteSession session = new ConfigTemplateRemoteSession(target, "playerA");
        configuration.getCategory("general").remove("mode");

        ConfigSyncModels.ConfigSaveResult saveResult = session.save();

        Assert.assertFalse(saveResult.success);
        Assert.assertTrue(saveResult.message.contains("配置同步字段不存在"));
        Assert.assertFalse(saveResult.message.contains("属性不存在"));
    }

    /**
     * 验证从权威配置刷新后会覆盖旧草稿。
     */
    @Test
    public void shouldRefreshDraftFromAuthoritativeConfiguration() {
        Configuration configuration = sampleConfiguration();
        ConfigSyncTarget target = ConfigSyncTarget.builder("test-config", configuration)
                .categories(java.util.Collections.singletonList(
                        new ConfigSyncCategorySpec("general", "General", "测试分类")))
                .build();
        ConfigTemplateRemoteSession session = new ConfigTemplateRemoteSession(target, "playerA");

        configuration.getCategory("general").get("mode").set("safe");
        session.refreshFromAuthoritative();

        Assert.assertEquals("safe",
                session.snapshotState().draft.values.get(ConfigSyncModels.buildFieldKey("general", "mode")));
    }

    private static Configuration sampleConfiguration() {
        Configuration configuration = new Configuration();
        Property mode = configuration.get("general", "mode", "normal", "运行模式");
        mode.setValidValues(new String[] { "normal", "safe", "debug" });
        configuration.get("general", "fontScale", 1.0D, "字体缩放", 0.5D, 2.0D);
        return configuration;
    }

    private static ConfigSyncTarget.SaveAction noopSaveAction() {
        return new ConfigSyncTarget.SaveAction() {
            @Override
            public void save(Configuration configuration) {
                // 纯 JVM 用例只验证会话写回，不触发 Forge 配置文件持久化。
            }
        };
    }
}
