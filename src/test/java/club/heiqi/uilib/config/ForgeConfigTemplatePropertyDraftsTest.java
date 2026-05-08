package club.heiqi.uilib.config;

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
}
