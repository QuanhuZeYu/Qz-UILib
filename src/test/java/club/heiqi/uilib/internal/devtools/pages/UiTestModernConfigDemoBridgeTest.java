package club.heiqi.uilib.internal.devtools.pages;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.config.ConfigNode;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.config.ModernConfigTemplateScreen;

/**
 * `MODCFG` 现代配置模板 demo 数据与字段规格测试。
 */
public class UiTestModernConfigDemoBridgeTest {

    /**
     * 验证当前开发环境可检测到 config 模块。
     */
    @Test
    public void shouldDetectModernConfigModuleInCurrentClasspath() {
        Assert.assertTrue(UiTestModernConfigDemoLauncher.isModernConfigModuleAvailable());
    }

    /**
     * 验证 demo 内存配置覆盖 12 个模板入口所需的基础数据形态。
     */
    @Test
    public void shouldBuildDemoConfigForAllTemplateEntrances() {
        MutableConfig config = UiTestModernConfigDemoBridge.createDemoConfig();

        Assert.assertFalse(config.isDirty());
        Assert.assertEquals("Steve", config.get("player.name").asString(""));
        Assert.assertEquals(20L, config.get("server.maxPlayers").asLong(0L));
        Assert.assertTrue(config.get("feature.enableHud").asBoolean(false));
        Assert.assertEquals("fast", config.get("render.mode").asString(""));
        Assert.assertTrue(config.get("motd").asString("").contains("LONG_TEXT"));
        Assert.assertEquals(ConfigNode.NodeType.LIST, config.get("ports").getType());
        Assert.assertEquals(3, config.get("ports").asList().size());
        Assert.assertEquals(ConfigNode.NodeType.LIST, config.get("servers").getType());
        Assert.assertEquals("a.local", config.get("servers").get(0).get("host").asString(""));
        Assert.assertEquals("admin", config.get("database.credentials.username").asString(""));
        Assert.assertEquals("A", config.get("labels.alpha").asString(""));
        Assert.assertEquals("fast", config.get("profile._presets.fast.mode").asString(""));
        Assert.assertTrue(config.get("payload").asString("").contains("localhost"));
        Assert.assertEquals("#FF8800", config.get("theme.primary").asString(""));
        Assert.assertEquals("minecraft:block/stone", config.get("texture.block").asString(""));
        Assert.assertEquals("minecraft:block.stone.click", config.get("audio.click").asString(""));
    }

    /**
     * 验证 demo FieldSpec 覆盖 12 入口及关键 hint / validValues / range 声明。
     */
    @Test
    public void shouldBuildFieldSpecsForAllTemplateEntrances() {
        MutableConfig config = UiTestModernConfigDemoBridge.createDemoConfig();
        ModernConfigTemplateScreen.Spec spec = UiTestModernConfigDemoBridge.buildDemoSpec(config);
        Map<String, ModernConfigTemplateScreen.FieldSpec> fields = indexFields(spec);

        Assert.assertEquals("qzuilib-test-demo", spec.getModId());
        Assert.assertEquals("现代配置模板 demo", spec.getTitle());
        Assert.assertEquals(14, spec.getFields().size());
        Assert.assertTrue(fields.containsKey("player.name"));
        Assert.assertEquals(Integer.valueOf(32), fields.get("player.name").getMaxLength());
        Assert.assertEquals(Integer.valueOf(1), fields.get("server.maxPlayers").getMinValue());
        Assert.assertEquals(Integer.valueOf(100), fields.get("server.maxPlayers").getMaxValue());
        Assert.assertEquals("balanced", fields.get("render.mode").getDefaultValue());
        Assert.assertEquals(3, fields.get("render.mode").getValidValues().size());
        Assert.assertEquals("textarea", fields.get("motd").getTemplateHint());
        Assert.assertTrue(fields.get("ports").hasDefaultValue());
        Assert.assertTrue(fields.containsKey("servers"));
        Assert.assertTrue(fields.containsKey("database.credentials"));
        Assert.assertEquals("dynamic-map", fields.get("labels").getTemplateHint());
        Assert.assertTrue(fields.containsKey("profile"));
        Assert.assertEquals("json", fields.get("payload").getTemplateHint());
        Assert.assertEquals("color", fields.get("theme.primary").getTemplateHint());
        Assert.assertEquals("resource", fields.get("texture.block").getTemplateHint());
        Assert.assertEquals("sound", fields.get("audio.click").getTemplateHint());
    }

    /**
     * 按 path 索引字段规格。
     *
     * @param spec demo 规格
     * @return path 到字段规格的映射
     */
    private static Map<String, ModernConfigTemplateScreen.FieldSpec> indexFields(
            ModernConfigTemplateScreen.Spec spec) {
        Map<String, ModernConfigTemplateScreen.FieldSpec> fields =
                new LinkedHashMap<String, ModernConfigTemplateScreen.FieldSpec>();
        for (ModernConfigTemplateScreen.FieldSpec field : spec.getFields()) {
            fields.put(field.getPath(), field);
        }
        return fields;
    }
}
