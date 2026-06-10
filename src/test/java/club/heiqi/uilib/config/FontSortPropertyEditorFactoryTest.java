package club.heiqi.uilib.config;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.config.FontConfig;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * `FontSortPropertyEditorFactory` 的属性匹配测试。
 */
public class FontSortPropertyEditorFactoryTest {

    /**
     * 验证工厂仅匹配字体排序列表属性。
     */
    @Test
    public void shouldMatchOnlyFontSortListProperty() {
        Configuration configuration = new Configuration();
        Property fontSort = configuration.get(FontConfig.CATEGORY, "fontSort", new String[] { "Alpha" }, "字体排序");
        Property otherList = configuration.get(FontConfig.CATEGORY, "otherFonts", new String[] { "Alpha" }, "其他字体");
        FontSortPropertyEditorFactory factory = new FontSortPropertyEditorFactory();

        Assert.assertTrue(factory.matchesForTesting(new ForgeConfigTemplateScreen.CategorySpec(FontConfig.CATEGORY),
                fontSort));
        Assert.assertFalse(factory.matchesForTesting(new ForgeConfigTemplateScreen.CategorySpec(FontConfig.CATEGORY),
                otherList));
        Assert.assertFalse(factory.matchesForTesting(new ForgeConfigTemplateScreen.CategorySpec("general"), fontSort));
    }

    /**
     * 验证工厂只在显式 alias 声明后匹配历史小写分类。
     */
    @Test
    public void shouldMatchLowercaseFontSystemCategoryOnlyWithExplicitAlias() {
        Configuration configuration = new Configuration();
        Property fontSort = configuration.get("fontsystem", "fontSort", new String[] { "Alpha" }, "字体排序");
        FontSortPropertyEditorFactory factory = new FontSortPropertyEditorFactory();

        Assert.assertFalse(factory.matchesForTesting(new ForgeConfigTemplateScreen.CategorySpec("fontsystem"),
                fontSort));
        Assert.assertTrue(factory.matchesForTesting(new ForgeConfigTemplateScreen.CategorySpec(FontConfig.CATEGORY)
                .addAlias("fontsystem"), fontSort));
    }

    /**
     * 验证字符字体规则工厂仅匹配字符字体覆盖规则列表属性。
     */
    @Test
    public void shouldMatchOnlyCharacterFontRuleListProperty() {
        Configuration configuration = new Configuration();
        Property characterFontRules = configuration.get(FontConfig.CATEGORY, "characterFontRules",
                new String[] { "字=Alpha" }, "字符字体覆盖规则");
        Property fontSort = configuration.get(FontConfig.CATEGORY, "fontSort", new String[] { "Alpha" }, "字体排序");
        FontCharacterRulePropertyEditorFactory factory = new FontCharacterRulePropertyEditorFactory();

        Assert.assertTrue(factory.matchesForTesting(new ForgeConfigTemplateScreen.CategorySpec(FontConfig.CATEGORY),
                characterFontRules));
        Assert.assertFalse(factory.matchesForTesting(new ForgeConfigTemplateScreen.CategorySpec(FontConfig.CATEGORY),
                fontSort));
        Assert.assertFalse(factory.matchesForTesting(new ForgeConfigTemplateScreen.CategorySpec("general"),
                characterFontRules));
    }
}
