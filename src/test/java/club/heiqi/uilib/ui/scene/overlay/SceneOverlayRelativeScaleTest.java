package club.heiqi.uilib.ui.scene.overlay;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * overlay 相对倍率 API（{@link SceneOverlayHost.Entry} / {@link OverlayHandle}）默认值与参数校验测试。
 *
 * <p>契约：默认 1.0F（跟随宿主 = 既有行为）；仅有限正数可写；仅全屏浮层（anchorProvider == null）可写；
 * 句柄失效后静默 no-op。</p>
 */
public class SceneOverlayRelativeScaleTest {

    /** 默认 1.0F：未声明 relativeScale 的既有 overlay 消费者零行为变化。 */
    @Test
    public void relativeScaleShouldDefaultToIdentity() {
        SceneOverlayHost host = new SceneOverlayHost();
        OverlayHandle handle = host.register(new SceneNode());

        Assert.assertEquals(1.0F, handle.getEntry().getRelativeScale(), 0.0F);
    }

    /** handle 转发到 entry，值可读写。 */
    @Test
    public void handleShouldForwardRelativeScaleToEntry() {
        SceneOverlayHost host = new SceneOverlayHost();
        OverlayHandle handle = host.register(new SceneNode());

        handle.setRelativeScale(1.5F);
        Assert.assertEquals(1.5F, handle.getEntry().getRelativeScale(), 0.0F);

        handle.setRelativeScale(1.0F);
        Assert.assertEquals(1.0F, handle.getEntry().getRelativeScale(), 0.0F);
    }

    /** 非有限或非正数一律 IllegalArgumentException，且不污染既有值。 */
    @Test
    public void invalidRelativeScaleShouldBeRejected() {
        SceneOverlayHost host = new SceneOverlayHost();
        OverlayHandle handle = host.register(new SceneNode());

        assertRejected(handle, Float.NaN);
        assertRejected(handle, Float.POSITIVE_INFINITY);
        assertRejected(handle, Float.NEGATIVE_INFINITY);
        assertRejected(handle, 0.0F);
        assertRejected(handle, -1.0F);

        Assert.assertEquals("非法写入不得污染已有值", 1.0F, handle.getEntry().getRelativeScale(), 0.0F);
    }

    /** 锚定浮层（anchorProvider != null）拒绝相对倍率：其几何由锚点解析决定。 */
    @Test
    public void anchoredOverlayShouldRejectRelativeScale() {
        SceneOverlayHost host = new SceneOverlayHost();
        SceneNode anchor = new SceneNode();
        OverlayHandle handle = host.register(new SceneNode(), OverlayDismissPolicy.DEFAULT, null,
                AnchorProvider.forNode(anchor));

        try {
            handle.setRelativeScale(2.0F);
            Assert.fail("锚定浮层必须拒绝 relativeScale");
        } catch (IllegalStateException expected) {
            // 期望路径
        }
        Assert.assertEquals(1.0F, handle.getEntry().getRelativeScale(), 0.0F);
    }

    /** dispose 后静默 no-op：不改 entry，也不校验参数（清理路径不应收到迟到写入的异常）。 */
    @Test
    public void disposedHandleShouldIgnoreRelativeScale() {
        SceneOverlayHost host = new SceneOverlayHost();
        OverlayHandle handle = host.register(new SceneNode());
        handle.setRelativeScale(2.0F);
        handle.dispose();

        handle.setRelativeScale(0.5F);
        handle.setRelativeScale(Float.NaN);

        Assert.assertEquals("dispose 后写入必须被忽略", 2.0F, handle.getEntry().getRelativeScale(), 0.0F);
    }

    /** 未失效的句柄仍必须校验参数（no-op 只在句柄失效后生效）。 */
    @Test
    public void liveHandleShouldStillValidateArguments() {
        SceneOverlayHost host = new SceneOverlayHost();
        OverlayHandle handle = host.register(new SceneNode());

        try {
            handle.setRelativeScale(0.0F);
            Assert.fail("存活句柄必须拒绝非法 relativeScale");
        } catch (IllegalArgumentException expected) {
            // 期望路径
        }
    }

    private static void assertRejected(OverlayHandle handle, float value) {
        try {
            handle.setRelativeScale(value);
            Assert.fail("必须拒绝非法 relativeScale: " + value);
        } catch (IllegalArgumentException expected) {
            // 期望路径
        }
    }
}
