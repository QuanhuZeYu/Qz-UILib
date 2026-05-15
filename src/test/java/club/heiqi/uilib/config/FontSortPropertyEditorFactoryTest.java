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
     * 验证工厂在 Forge 实际分类为小写时仍能匹配字体排序属性。
     */
    @Test
    public void shouldMatchLowercaseFontSystemCategory() {
        Configuration configuration = new Configuration();
        Property fontSort = configuration.get("fontsystem", "fontSort", new String[] { "Alpha" }, "字体排序");
        FontSortPropertyEditorFactory factory = new FontSortPropertyEditorFactory();

        Assert.assertTrue(factory.matchesForTesting(new ForgeConfigTemplateScreen.CategorySpec(FontConfig.CATEGORY),
                fontSort));
    }
}
