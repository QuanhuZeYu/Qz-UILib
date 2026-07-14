package club.heiqi.uilib.ui.screen;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.render.UiRenderFrameAbortException;

/** 无 Minecraft/GL 实例化的帧中止边界测试。 */
public class SceneFrameAbortBoundaryTest {

    /** 专用中止信号只结束当前帧。 */
    @Test
    public void consumesOnlyDedicatedFrameAbort() {
        Assert.assertTrue(SceneFrameAbortBoundary.run(
                () -> { throw new UiRenderFrameAbortException("abort"); }));
        Assert.assertFalse(SceneFrameAbortBoundary.run(() -> { }));
    }

    /** 普通运行时异常原样传播。 */
    @Test
    public void propagatesOrdinaryRuntimeException() {
        IllegalStateException failure = new IllegalStateException("ordinary");
        try {
            SceneFrameAbortBoundary.run(() -> { throw failure; });
            Assert.fail("普通异常不得被边界吞掉");
        } catch (IllegalStateException expected) {
            Assert.assertSame(failure, expected);
        }
    }

    /** LinkageError 原样传播。 */
    @Test
    public void propagatesLinkageError() {
        LinkageError failure = new LinkageError("linkage");
        try {
            SceneFrameAbortBoundary.run(() -> { throw failure; });
            Assert.fail("LinkageError 不得被边界吞掉");
        } catch (LinkageError expected) {
            Assert.assertSame(failure, expected);
        }
    }
}
