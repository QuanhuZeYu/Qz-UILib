package club.heiqi.uilib.font.config;

import java.awt.Font;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.util.FontOrderPlanner;
import net.minecraftforge.common.config.Configuration;

/**
 * `FontConfig` 字体分类解析测试。
 */
public class FontConfigCategoryTest {

    /**
     * 验证小写字体分类中的排序配置会被运行时读取。
     */
    @Test
    public void shouldLoadFontSortFromLowercaseCategory() {
        Configuration configuration = new Configuration();
        configuration.get("fontsystem", "fontSort", new String[] { "Bravo", "Alpha" }, "字体排序");

        FontConfig.load(configuration);

        Assert.assertArrayEquals(new String[] { "Bravo", "Alpha" }, FontConfig.getFontSortSnapshot());
        Assert.assertTrue(FontConfig.fontSortConfigured);
    }

    /**
     * 验证字体注册写回排序快照时仍写入实际存在的小写分类。
     */
    @Test
    public void shouldPersistFontSortSnapshotToExistingLowercaseCategory() {
        Configuration configuration = new Configuration();
        configuration.get("fontsystem", "fontSort", new String[] { "Bravo", "Alpha" }, "字体排序");
        FontConfig.load(configuration);

        FontConfig.applyFontOrderSnapshot(new FontOrderPlanner().plan(Arrays.asList(
                new Font("Alpha", Font.PLAIN, 14),
                new Font("Bravo", Font.PLAIN, 14)),
                new String[] { "Alpha", "Bravo", "Missing" }));

        Assert.assertArrayEquals(new String[] { "Alpha", "Bravo" },
                configuration.get("fontsystem", "fontSort", new String[0], "字体排序").getStringList());
        Assert.assertFalse(configuration.hasCategory(FontConfig.CATEGORY)
                && configuration.getCategory(FontConfig.CATEGORY).containsKey("fontSort"));
    }
}
