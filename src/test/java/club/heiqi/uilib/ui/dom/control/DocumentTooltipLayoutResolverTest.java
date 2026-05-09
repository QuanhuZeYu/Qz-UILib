package club.heiqi.uilib.ui.dom.control;

import org.junit.Assert;
import org.junit.Test;

/**
 * `DocumentTooltipLayoutResolver` 的定位测试。
 */
public class DocumentTooltipLayoutResolverTest {

    /**
     * 验证空间足够时优先放在鼠标右下。
     */
    @Test
    public void shouldPreferBottomRightWhenSpaceIsEnough() {
        DocumentTooltipLayoutResolver.TooltipPlacement placement = DocumentTooltipLayoutResolver.resolve(
                1280, 720, 200, 180, 240, 120, new DocumentTooltipLayoutResolver.TooltipHeightEstimator() {
                    @Override
                    public int estimate(int tooltipWidth) {
                        return 120;
                    }
                });

        Assert.assertEquals(223, placement.getLeft());
        Assert.assertEquals(203, placement.getTop());
        Assert.assertEquals(240, placement.getWidth());
    }

    /**
     * 验证右下空间不足时会回退到其他方向。
     */
    @Test
    public void shouldFallbackWhenBottomRightWouldOverflow() {
        DocumentTooltipLayoutResolver.TooltipPlacement placement = DocumentTooltipLayoutResolver.resolve(
                400, 300, 360, 260, 180, 120, new DocumentTooltipLayoutResolver.TooltipHeightEstimator() {
                    @Override
                    public int estimate(int tooltipWidth) {
                        return 100;
                    }
                });

        Assert.assertTrue(placement.getLeft() < 360);
        Assert.assertTrue(placement.getTop() < 260);
    }

    /**
     * 验证单方向空间不足时会自动收窄 tooltip 宽度。
     */
    @Test
    public void shouldShrinkWidthToFitAvailableSpace() {
        DocumentTooltipLayoutResolver.TooltipPlacement placement = DocumentTooltipLayoutResolver.resolve(
                140, 240, 70, 100, 220, 120, new DocumentTooltipLayoutResolver.TooltipHeightEstimator() {
                    @Override
                    public int estimate(int tooltipWidth) {
                        return tooltipWidth > 100 ? 80 : 140;
                    }
                });

        Assert.assertEquals(34, placement.getWidth());
        Assert.assertTrue(placement.getLeft() >= 4);
    }
}
