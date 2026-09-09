package club.heiqi.uilib.ui.scene.input;

import org.junit.Assert;
import org.junit.Test;

/** 宿主缩放不得丢失排队按钮、双击计数或文本输入。 */
public class SceneInputFrameScaleTest {
    @Test
    public void queuedEventsAndStickyPointerShareScaleWithoutChangingSemantics() {
        InputFrameBuilder builder = new InputFrameBuilder(0, 0);
        builder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, 120, 80,
                SceneMouseButton.LEFT, 0, 0, 0, true, false, false, false, 10L));
        builder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, 120, 80,
                SceneMouseButton.LEFT, 0, 0, 0, true, false, false, false, 20L));
        builder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, 120, 80,
                SceneMouseButton.LEFT, 0, 0, 0, true, false, false, false, 30L));
        builder.push(RawInputEvent.ofText("输入😀", 40L));
        SceneInputFrame raw = builder.drainFrame();
        SceneInputFrame scaled = raw.scalePointerCoordinates(0.5F);
        Assert.assertEquals(60, scaled.getPointerX());
        Assert.assertEquals(40, scaled.getPointerY());
        Assert.assertEquals(60, scaled.getPointerEvents().get(2).getLogicalX());
        Assert.assertEquals(2, scaled.getPointerEvents().get(2).getClickCount());
        Assert.assertEquals(30L, scaled.getPointerEvents().get(2).getTimeNanos());
        Assert.assertSame(raw.getTextEvents(), scaled.getTextEvents());
        Assert.assertSame(raw.getKeyEvents(), scaled.getKeyEvents());
        Assert.assertTrue(scaled.isControlDown());
        Assert.assertEquals(40L, scaled.getFrameTimeNanos());
        Assert.assertEquals(120, raw.getPointerX());
        Assert.assertSame(raw, raw.scalePointerCoordinates(1F));
        SceneInputFrame sticky = builder.drainFrame().scalePointerCoordinates(2F);
        Assert.assertTrue(sticky.isEmpty());
        Assert.assertEquals(240, sticky.getPointerX());
        Assert.assertEquals(160, sticky.getPointerY());
    }

    @Test
    public void rejectsInvalidScale() {
        for (float factor : new float[] {0F, -1F, Float.NaN, Float.POSITIVE_INFINITY}) {
            try {
                SceneInputFrame.EMPTY.scalePointerCoordinates(factor);
                Assert.fail("invalid scale accepted: " + factor);
            } catch (IllegalArgumentException expected) {
                // Invalid host coordinate mapping must fail before dispatch.
            }
        }
    }
}
