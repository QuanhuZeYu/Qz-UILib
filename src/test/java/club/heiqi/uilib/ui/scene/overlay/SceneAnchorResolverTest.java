package club.heiqi.uilib.ui.scene.overlay;

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
        SceneAnchorResolver.AnchorRect anchor = new SceneAnchorResolver.AnchorRect(12, 34, 120, 24);

        SceneAnchorResolver.ResolvedAnchor resolved = SceneAnchorResolver.resolveDown(anchor, 300, 200);

        Assert.assertEquals(12, resolved.getX());
        Assert.assertEquals(58, resolved.getY());
    }

    /**
     * 验证浮层宽度对齐 trigger 宽度。
     */
    @Test
    public void shouldMatchTriggerWidth() {
        SceneAnchorResolver.AnchorRect anchor = new SceneAnchorResolver.AnchorRect(20, 10, 144, 30);

        SceneAnchorResolver.ResolvedAnchor resolved = SceneAnchorResolver.resolveDown(anchor, 400, 300);

        Assert.assertEquals(144, resolved.getWidth());
    }

    /**
     * 验证最大高度被限制到 host 底部剩余空间。
     */
    @Test
    public void shouldLimitMaxHeightToRemainingHostSpace() {
        SceneAnchorResolver.AnchorRect anchor = new SceneAnchorResolver.AnchorRect(8, 150, 100, 32);

        SceneAnchorResolver.ResolvedAnchor resolved = SceneAnchorResolver.resolveDown(anchor, 300, 210);

        Assert.assertEquals(28, resolved.getMaxHeight());
    }

    /**
     * 验证 P0 不做 flip：anchor 已贴近底部时仍保持向下位置，只把可用高度压到 0。
     */
    @Test
    public void shouldNotFlipWhenNoSpaceBelow() {
        SceneAnchorResolver.AnchorRect anchor = new SceneAnchorResolver.AnchorRect(8, 190, 100, 32);

        SceneAnchorResolver.ResolvedAnchor resolved = SceneAnchorResolver.resolveDown(anchor, 300, 210);

        Assert.assertEquals(222, resolved.getY());
        Assert.assertEquals(0, resolved.getMaxHeight());
    }
}
