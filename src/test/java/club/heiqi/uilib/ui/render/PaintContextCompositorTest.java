package club.heiqi.uilib.ui.render;

import org.junit.Assert;
import org.junit.Test;

/** FBO 借用计数异常安全测试。 */
public class PaintContextCompositorTest {
    @Test
    public void ensureSizeFailureDoesNotIncrementBorrowCount() {
        PaintContextCompositor compositor = new PaintContextCompositor(FailingTarget::new);
        try {
            compositor.borrowIsolatedLayer(32, 32);
            Assert.fail("expected");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("ensure", expected.getMessage());
        }
        Assert.assertEquals(0, compositor.__getBorrowedLayerCount());
    }

    private static final class FailingTarget extends UiRenderTarget {
        @Override public void ensureSize(int width, int height) { throw new IllegalStateException("ensure"); }
    }
}
