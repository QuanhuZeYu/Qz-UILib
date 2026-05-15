package club.heiqi.uilib.font.util;

import java.awt.Font;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link FontOrderPlanner} 的纯 JVM 测试。
 */
public class FontOrderPlannerTest {

    /**
     * 验证配置命中的字体优先输出，缺失项只记录不写回有效顺序。
     */
    @Test
    public void shouldPrioritizeConfiguredFontsAndTrackMissingNames() {
        FontOrderPlanner planner = new FontOrderPlanner();

        FontOrderSnapshot snapshot = planner.plan(Arrays.asList(
                font("Noto Sans"),
                font("JetBrains Mono"),
                font("Emoji Color")),
                new String[] { "Missing Font", "JetBrains Mono", "Noto Sans" });

        Assert.assertArrayEquals(new String[] { "JetBrains Mono", "Noto Sans", "Emoji Color" },
                snapshot.getResolvedFontNames());
        Assert.assertArrayEquals(new String[] { "Missing Font" }, snapshot.getMissingConfiguredFontNames());
        Assert.assertEquals(Arrays.asList("JetBrains Mono", "Noto Sans", "Emoji Color"),
                extractNames(snapshot.getOrderedFonts()));
    }

    /**
     * 验证未在配置中的字体会按自然顺序追加。
     */
    @Test
    public void shouldAppendUnconfiguredFontsByNaturalOrder() {
        FontOrderPlanner planner = new FontOrderPlanner();

        FontOrderSnapshot snapshot = planner.plan(Arrays.asList(
                font("Font 10"),
                font("Font 2"),
                font("Font 1")),
                new String[0]);

        Assert.assertArrayEquals(new String[] { "Font 1", "Font 2", "Font 10" },
                snapshot.getResolvedFontNames());
    }

    /**
     * 验证首启默认提示可以提升常见多语种字体，且不会污染缺失字体列表。
     */
    @Test
    public void shouldPrioritizeDefaultHintsWithoutTrackingMissingNames() {
        FontOrderPlanner planner = new FontOrderPlanner();

        FontOrderSnapshot snapshot = planner.plan(Arrays.asList(
                font("CADFont"),
                font("Microsoft YaHei"),
                font("Font 1")),
                new String[] { "Missing Preferred", "Microsoft YaHei" }, false);

        Assert.assertArrayEquals(new String[] { "Microsoft YaHei", "CADFont", "Font 1" },
                snapshot.getResolvedFontNames());
        Assert.assertArrayEquals(new String[0], snapshot.getMissingConfiguredFontNames());
    }

    /**
     * 验证同名字体族会保留全部字体实例，但名称只写入一次。
     */
    @Test
    public void shouldKeepAllFontInstancesWithinSameDisplayNameGroup() {
        FontOrderPlanner planner = new FontOrderPlanner();
        Font plain = new Font("Dialog", Font.PLAIN, 14);
        Font bold = new Font("Dialog", Font.BOLD, 14);

        FontOrderSnapshot snapshot = planner.plan(Arrays.asList(plain, bold), new String[] { "Dialog" });

        Assert.assertArrayEquals(new String[] { "Dialog" }, snapshot.getResolvedFontNames());
        Assert.assertEquals(2, snapshot.getOrderedFonts().size());
    }

    private static Font font(String name) {
        return new Font(name, Font.PLAIN, 14);
    }

    private static List<String> extractNames(List<Font> fonts) {
        java.util.ArrayList<String> names = new java.util.ArrayList<String>();
        for (Font font : fonts) {
            names.add(font.getName());
        }
        return names;
    }
}
