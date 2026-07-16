package club.heiqi.uilib.ui.scene.overlay;

import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import org.junit.Assert;
import org.junit.Test;

/**
 * {@link SceneAnchorResolver} 的 P0 锚点解析测试。
 */
public class SceneAnchorResolverTest {

    /**
     * 验证 P0 只向下展开：左边对齐 anchor，Y 位于 anchor 底边。
     */
    @Test
    public void shouldPlaceOverlayBelowAnchor() {
        AnchorRect anchor = new AnchorRect(12, 34, 120, 24);

        SceneAnchorResolver.ResolvedAnchor resolved = SceneAnchorResolver.resolveDown(anchor, 300, 200);

        Assert.assertEquals(12, resolved.getX());
        Assert.assertEquals(58, resolved.getY());
    }

    /**
     * 验证浮层宽度对齐 trigger 宽度。
     */
    @Test
    public void shouldMatchTriggerWidth() {
        AnchorRect anchor = new AnchorRect(20, 10, 144, 30);

        SceneAnchorResolver.ResolvedAnchor resolved = SceneAnchorResolver.resolveDown(anchor, 400, 300);

        Assert.assertEquals(144, resolved.getWidth());
    }

    /**
     * 验证最大高度被限制到 host 底部剩余空间。
     */
    @Test
    public void shouldLimitMaxHeightToRemainingHostSpace() {
        AnchorRect anchor = new AnchorRect(8, 150, 100, 32);

        SceneAnchorResolver.ResolvedAnchor resolved = SceneAnchorResolver.resolveDown(anchor, 300, 210);

        Assert.assertEquals(28, resolved.getMaxHeight());
    }

    /**
     * 验证 P0 不做 flip：anchor 已贴近底部时仍保持向下位置，只把可用高度压到 0。
     */
    @Test
    public void shouldNotFlipWhenNoSpaceBelow() {
        AnchorRect anchor = new AnchorRect(8, 190, 100, 32);

        SceneAnchorResolver.ResolvedAnchor resolved = SceneAnchorResolver.resolveDown(anchor, 300, 210);

        Assert.assertEquals(222, resolved.getY());
        Assert.assertEquals(0, resolved.getMaxHeight());
    }

    /**
     * resolveAuto 向下空间足够时应保持向下展开。
     */
    @Test
    public void resolveAutoShouldPreferDownWhenContentFitsBelow() {
        AnchorRect anchor = new AnchorRect(12, 40, 120, 20);

        SceneAnchorResolver.ResolvedAnchor resolved = SceneAnchorResolver.resolveAuto(anchor, 300, 200, 80);

        Assert.assertEquals(12, resolved.getX());
        Assert.assertEquals(60, resolved.getY());
        Assert.assertEquals(120, resolved.getWidth());
        Assert.assertEquals(140, resolved.getMaxHeight());
    }

    /**
     * resolveAuto 向下不够但向上够时应向上紧贴 trigger 上沿。
     */
    @Test
    public void resolveAutoShouldFlipUpWhenOnlyAboveFits() {
        AnchorRect anchor = new AnchorRect(12, 150, 120, 20);

        SceneAnchorResolver.ResolvedAnchor resolved = SceneAnchorResolver.resolveAuto(anchor, 300, 200, 90);

        Assert.assertEquals(60, resolved.getY());
        Assert.assertEquals(150, resolved.getMaxHeight());
    }

    /**
     * resolveAuto 两侧都不够时应选择空间更大的一侧 cap。
     */
    @Test
    public void resolveAutoShouldUseLargerSideWhenBothSidesAreTooSmall() {
        AnchorRect anchor = new AnchorRect(12, 30, 120, 20);

        SceneAnchorResolver.ResolvedAnchor resolved = SceneAnchorResolver.resolveAuto(anchor, 300, 130, 120);

        Assert.assertEquals(50, resolved.getY());
        Assert.assertEquals(80, resolved.getMaxHeight());

        AnchorRect lowAnchor = new AnchorRect(12, 90, 120, 20);
        SceneAnchorResolver.ResolvedAnchor upResolved = SceneAnchorResolver.resolveAuto(lowAnchor, 300, 130, 120);
        Assert.assertEquals(0, upResolved.getY());
        Assert.assertEquals(90, upResolved.getMaxHeight());
    }

    /**
     * resolveAuto 两侧空间相等且都不足时默认向下。
     */
    @Test
    public void resolveAutoShouldDefaultDownWhenSpacesAreEqual() {
        AnchorRect anchor = new AnchorRect(12, 50, 120, 20);

        SceneAnchorResolver.ResolvedAnchor resolved = SceneAnchorResolver.resolveAuto(anchor, 300, 120, 100);

        Assert.assertEquals(70, resolved.getY());
        Assert.assertEquals(50, resolved.getMaxHeight());
    }

    /** 显式策略在宽屏使用首选宽度，并在右边缘向左 clamp 到安全边距。 */
    @Test
    public void policyShouldUsePreferredWidthAndClampRightEdge() {
        AnchoredPortalLayout policy = new AnchoredPortalLayout(480, 360, 8);
        AnchorRect anchor = new AnchorRect(760, 40, 32, 20);

        SceneAnchorResolver.ResolvedAnchor resolved = SceneAnchorResolver.resolveAuto(
                anchor, 800, 300, 80, policy);

        Assert.assertEquals(480, resolved.getWidth());
        Assert.assertEquals(312, resolved.getX());
    }

    /** 中屏应在最小与首选宽度间收窄，且保留双侧安全边距。 */
    @Test
    public void policyShouldShrinkBetweenMinimumAndPreferredWidth() {
        AnchoredPortalLayout policy = new AnchoredPortalLayout(480, 360, 8);

        SceneAnchorResolver.ResolvedAnchor resolved = SceneAnchorResolver.resolveAuto(
                new AnchorRect(40, 40, 120, 20), 420, 300, 80, policy);

        Assert.assertEquals(404, resolved.getWidth());
        Assert.assertEquals(8, resolved.getX());
    }

    /** 可用宽度不足最小值时应窄屏 clamp，不得越过宿主边界。 */
    @Test
    public void policyShouldClampBelowMinimumOnNarrowHost() {
        AnchoredPortalLayout policy = new AnchoredPortalLayout(480, 360, 8);

        SceneAnchorResolver.ResolvedAnchor resolved = SceneAnchorResolver.resolveAuto(
                new AnchorRect(220, 40, 40, 20), 300, 300, 80, policy);

        Assert.assertEquals(284, resolved.getWidth());
        Assert.assertEquals(8, resolved.getX());
        Assert.assertTrue(resolved.getX() + resolved.getWidth() <= 300 - policy.getSafeInset());
    }

    /** 默认策略继续保持 trigger 等宽与原始左对齐，不引入横向碰撞行为变化。 */
    @Test
    public void defaultPolicyShouldPreserveTriggerWidthAndX() {
        AnchorRect anchor = new AnchorRect(290, 40, 120, 20);

        SceneAnchorResolver.ResolvedAnchor resolved = SceneAnchorResolver.resolveAuto(
                anchor, 300, 300, 80, AnchoredPortalLayout.DEFAULT);

        Assert.assertEquals(290, resolved.getX());
        Assert.assertEquals(120, resolved.getWidth());
    }
}
