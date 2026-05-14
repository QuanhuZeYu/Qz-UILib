package club.heiqi.uilib.config;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * `ForgeConfigTemplatePropertyDrafts` 的纯 JVM 校验测试。
 */
public class ForgeConfigTemplatePropertyDraftsTest {

    /**
     * 验证整数与小数范围校验。
     */
    @Test
    public void shouldValidateNumericDraftsAgainstBounds() {
        Configuration configuration = new Configuration();
        Property intProperty = configuration.get("general", "threadCount", 4, "线程数", 1, 16);
        Property doubleProperty = configuration.get("render", "fontScale", 0.9D, "字体缩放", 0.0D, 1.0D);

        Assert.assertNull(ForgeConfigTemplatePropertyDrafts.validateDraft(intProperty, "8"));
        Assert.assertEquals("需要位于 1 到 16 之间。",
                ForgeConfigTemplatePropertyDrafts.validateDraft(intProperty, "32"));

        Assert.assertNull(ForgeConfigTemplatePropertyDrafts.validateDraft(doubleProperty, "0.75"));
        Assert.assertEquals("需要位于 0.0 到 1.0 之间。",
                ForgeConfigTemplatePropertyDrafts.validateDraft(doubleProperty, "9.5"));
    }

    /**
     * 验证列表草稿会按类型校验并可写回属性。
     */
    @Test
    public void shouldValidateAndApplyListDrafts() {
        Configuration configuration = new Configuration();
        Property sortProperty = configuration.get("fontsystem", "fontSort", new String[] { "default" }, "字体排序");
        Property intListProperty = configuration.get("general", "weights", new int[] { 1, 2 }, "权重", 0, 10);

        Assert.assertNull(ForgeConfigTemplatePropertyDrafts.validateDraft(sortProperty, "default, fallback, emoji"));
        ForgeConfigTemplatePropertyDrafts.applyDraft(sortProperty, "default, fallback, emoji");
        Assert.assertArrayEquals(new String[] { "default", "fallback", "emoji" }, sortProperty.getStringList());

        Assert.assertNull(ForgeConfigTemplatePropertyDrafts.validateDraft(intListProperty, "1, 5, 10"));
        ForgeConfigTemplatePropertyDrafts.applyDraft(intListProperty, "1, 5, 10");
        Assert.assertArrayEquals(new int[] { 1, 5, 10 }, intListProperty.getIntList());

        Assert.assertEquals("列表中的整数需要位于 0 到 10 之间。",
                ForgeConfigTemplatePropertyDrafts.validateDraft(intListProperty, "1, 12"));
    }

    /**
     * 验证默认展示文本、当前展示文本与占位文本生成。
     */
    @Test
    public void shouldBuildDisplayTextsFromPropertyValues() {
        Configuration configuration = new Configuration();
        Property stringProperty = configuration.get("general", "mode", "normal", "运行模式");
        Property listProperty = configuration.get("fontsystem", "fontSort", new String[] { "default", "fallback" },
                "字体排序");
        listProperty.set(new String[] { "runtime", "emoji" });

        Assert.assertEquals("normal", ForgeConfigTemplatePropertyDrafts.readDefaultDisplayValue(stringProperty));
        Assert.assertEquals("normal", ForgeConfigTemplatePropertyDrafts.readCurrentDisplayValue(stringProperty));
        Assert.assertEquals("normal", ForgeConfigTemplatePropertyDrafts.resolvePlaceholder(stringProperty));

        Assert.assertEquals("default, fallback",
                ForgeConfigTemplatePropertyDrafts.readDefaultDisplayValue(listProperty));
        Assert.assertEquals("runtime, emoji",
                ForgeConfigTemplatePropertyDrafts.readCurrentDisplayValue(listProperty));
        Assert.assertEquals("default, fallback",
                ForgeConfigTemplatePropertyDrafts.resolvePlaceholder(listProperty));
    }

    /**
     * 验证长列表展示会取前五项摘要与 200 字符截断中信息量更长的一种。
     */
    @Test
    public void shouldSummarizeLongListDisplayValues() {
        Configuration configuration = new Configuration();
        Property listProperty = configuration.get("fontsystem", "fontSort",
                new String[] { "Default", "Fallback", "Emoji", "Cjk", "Symbols", "Legacy" }, "字体排序");
        listProperty.set(new String[] { "Runtime", "Emoji", "Cjk", "Symbols", "Legacy", "Serif", "Sans" });

        Assert.assertEquals("Runtime, Emoji, Cjk, Symbols, Legacy, Serif, Sans",
                ForgeConfigTemplatePropertyDrafts.readCurrentDisplayValue(listProperty));
        Assert.assertEquals("Default, Fallback, Emoji, Cjk, Symbols, Legacy",
                ForgeConfigTemplatePropertyDrafts.readDefaultDisplayValue(listProperty));
        Assert.assertEquals("Runtime, Emoji, Cjk, Symbols, Legacy, Serif, Sans",
                ForgeConfigTemplatePropertyDrafts.readFullListDisplayValue(listProperty));
    }

    /**
     * 验证离散有效值属性会暴露分段选择所需状态。
     */
    @Test
    public void shouldResolveDiscreteValidValuesMetadata() {
        Configuration configuration = new Configuration();
        Property modeProperty = configuration.get("general", "mode", "normal", "运行模式");
        modeProperty.setValidValues(new String[] { "normal", "safe", "debug" });

        Assert.assertTrue(ForgeConfigTemplatePropertyDrafts.hasDiscreteValidValues(modeProperty));
        Assert.assertArrayEquals(new String[] { "normal", "safe", "debug" },
                ForgeConfigTemplatePropertyDrafts.getValidValuesSnapshot(modeProperty));
        Assert.assertEquals(0, ForgeConfigTemplatePropertyDrafts.resolveSelectedValidValueIndex(modeProperty));

        modeProperty.set("debug");
        Assert.assertEquals(2, ForgeConfigTemplatePropertyDrafts.resolveSelectedValidValueIndex(modeProperty));
    }

    /**
     * 验证遗留值不会再被静默回退成第一个 validValues 选项。
     */
    @Test
    public void shouldFallbackFromChoiceEditorWhenCurrentValueIsLegacyValue() {
        Configuration configuration = new Configuration();
        Property modeProperty = configuration.get("general", "mode", "legacy", "运行模式");
        modeProperty.setValidValues(new String[] { "normal", "safe", "debug" });

        Assert.assertTrue(ForgeConfigTemplatePropertyDrafts.hasDiscreteValidValues(modeProperty));
        Assert.assertEquals(-1, ForgeConfigTemplatePropertyDrafts.resolveSelectedValidValueIndex(modeProperty));
        Assert.assertFalse(ForgeConfigTemplatePropertyDrafts.shouldUseDiscreteValidValuesEditor(modeProperty));
    }

    /**
     * 验证列表输入最大长度至少覆盖当前与默认展示文本。
     */
    @Test
    public void shouldResolveListMaxLengthFromObservedContent() {
        Configuration configuration = new Configuration();
        String[] defaults = new String[] { "default_resource_pack_identifier_with_long_name" };
        Property listProperty = configuration.get("general", "resourcePacks", defaults, "资源包列表");
        listProperty.setMaxListLength(2);
        listProperty.set(new String[] {
                "runtime_resource_pack_identifier_with_even_longer_name",
                "emoji_fallback_resource_pack_identifier"
        });

        int maxLength = ForgeConfigTemplatePropertyDrafts.resolveMaxLength(listProperty);

        Assert.assertTrue(maxLength >= ForgeConfigTemplatePropertyDrafts.readCurrentDisplayValue(listProperty).length());
        Assert.assertTrue(maxLength >= ForgeConfigTemplatePropertyDrafts.readDefaultDisplayValue(listProperty).length());
    }

    /**
     * 验证保存动作失败时会回滚已写回的属性值。
     */
    @Test
    public void shouldRollbackAppliedDraftsWhenSaveActionFails() {
        Configuration configuration = new Configuration();
        Property modeProperty = configuration.get("general", "mode", "normal", "运行模式");
        Property listProperty = configuration.get("general", "resourcePacks", new String[] { "base" }, "资源包列表");

        try {
            ForgeConfigTemplatePropertyDrafts.runWithRollback(Arrays.asList(modeProperty, listProperty), new Runnable() {
                @Override
                public void run() {
                    modeProperty.set("debug");
                    listProperty.set(new String[] { "runtime", "emoji" });
                }
            }, new Runnable() {
                @Override
                public void run() {
                    throw new IllegalStateException("save failed");
                }
            });
            Assert.fail("expected save failure");
        } catch (IllegalStateException exception) {
            Assert.assertEquals("save failed", exception.getMessage());
        }

        Assert.assertEquals("normal", modeProperty.getString());
        Assert.assertArrayEquals(new String[] { "base" }, listProperty.getStringList());
    }

    /**
     * 验证空状态文案会优先暴露缺失分类。
     */
    @Test
    public void shouldPreferMissingCategoriesInEmptyStateMessage() {
        Assert.assertEquals("当前模板没有找到可展示的 Forge 配置项。请检查分类名是否与 Configuration 中注册的一致。",
                ForgeConfigTemplateMessages.resolveEmptyStateMessage(
                        "当前模板没有找到可展示的 Forge 配置项。请检查分类名是否与 Configuration 中注册的一致。", ""));
        Assert.assertEquals("以下分类未在 Configuration 中找到：[general, render]。",
                ForgeConfigTemplateMessages.resolveEmptyStateMessage(
                        "当前模板没有找到可展示的 Forge 配置项。请检查分类名是否与 Configuration 中注册的一致。",
                        "以下分类未在 Configuration 中找到：[general, render]。"));
    }
}
