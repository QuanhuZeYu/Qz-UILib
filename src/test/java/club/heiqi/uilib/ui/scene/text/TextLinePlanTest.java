package club.heiqi.uilib.ui.scene.text;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;

/**
 * TextLinePlan 一次性行计划契约测试（审查报告 §8 B2-4）。
 *
 * <p>验证：拆行 → clamp（maxLines + 省略号）→ 逐行行高解析 → 逐行链接区域的
 * 内容与行块总高正确性；行高解析器与度量解耦（回调注入）。</p>
 */
public class TextLinePlanTest {

    /** 覆写拆行与链接区域的确定性替身（度量委托 FixedTextMeasurer 固定行高 16）。 */
    private static final class PlanMeasurer implements SceneTextMeasurer {

        private final FixedTextMeasurer delegate = new FixedTextMeasurer(8, 16);

        @Override
        public int measureWidth(String text, int fontSizePx) {
            return delegate.measureWidth(text, fontSizePx);
        }

        @Override
        public int lineHeight(int fontSizePx) {
            return delegate.lineHeight(fontSizePx);
        }

        @Override
        public int epoch() {
            return delegate.epoch();
        }

        @Override
        public List<String> splitLines(String text, int fontSizePx, int wrapWidth, int textMode) {
            return Arrays.asList("AAAA", "BBBB", "CCCC");
        }

        @Override
        public List<TextLinkRegion> linkRegions(String line, int fontSizePx, int textMode) {
            if ("BBBB".equals(line)) {
                return Arrays.asList(new TextLinkRegion(8, 16, "https://a.b"));
            }
            return java.util.Collections.emptyList();
        }
    }

    @Test
    public void buildShouldProduceLinesHeightsLinksAndTotal() {
        PlanMeasurer measurer = new PlanMeasurer();
        // 行高解析：自动行高 ×2（模拟 node.resolveLineHeight 的行距倍数语义）
        TextLinePlan plan = TextLinePlan.build(measurer, "AAAABBBBCCCC", 16, 0,
                SceneTextMode.UILIB_RAW, 0, false, height -> height * 2);

        Assert.assertEquals(Arrays.asList("AAAA", "BBBB", "CCCC"), plan.getLines());
        Assert.assertArrayEquals(new int[] { 32, 32, 32 }, plan.getLineHeights());
        Assert.assertEquals(96, plan.getTotalHeight());
        // 链接区域只在第二行
        Assert.assertEquals(0, plan.getLinkRegionsPerLine().get(0).size());
        Assert.assertEquals(1, plan.getLinkRegionsPerLine().get(1).size());
        Assert.assertEquals("https://a.b", plan.getLinkRegionsPerLine().get(1).get(0).getUrl());
        Assert.assertEquals(0, plan.getLinkRegionsPerLine().get(2).size());
    }

    @Test
    public void buildShouldClampWithEllipsis() {
        PlanMeasurer measurer = new PlanMeasurer();
        // maxLines=2 + ellipsis + wrap 有效：保留两行，末行富文本感知裁剪后追加省略号
        TextLinePlan plan = TextLinePlan.build(measurer, "AAAABBBBCCCC", 16, 40,
                SceneTextMode.UILIB_RAW, 2, true, height -> height);

        Assert.assertEquals(2, plan.getLines().size());
        Assert.assertEquals("AAAA", plan.getLines().get(0));
        Assert.assertTrue("末行应带省略号", plan.getLines().get(1).endsWith("..."));
        Assert.assertEquals(32, plan.getTotalHeight());
    }

    @Test
    public void lineHeightResolverShouldDriveResolvedHeights() {
        PlanMeasurer measurer = new PlanMeasurer();
        // resolver 恒返 7（低于自动行高 16）：验证解析器权威性（不重算，直接采用）
        TextLinePlan plan = TextLinePlan.build(measurer, "AAAABBBBCCCC", 16, 0,
                SceneTextMode.UILIB_RAW, 0, false, height -> 7);

        Assert.assertArrayEquals(new int[] { 7, 7, 7 }, plan.getLineHeights());
        Assert.assertEquals(21, plan.getTotalHeight());
    }
}
