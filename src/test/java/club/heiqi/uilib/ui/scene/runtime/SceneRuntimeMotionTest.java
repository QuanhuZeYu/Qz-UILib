package club.heiqi.uilib.ui.scene.runtime;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** Config-scoped Motion 的手动时钟、失效级别与 occurrence 隔离测试。 */
public class SceneRuntimeMotionTest {

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    @Test
    public void floatMotionUsesManualFrameTimeWithoutWritingHistory() {
        SceneRuntime runtime = new SceneRuntime();
        try {
            runtime.__enableMotion();
            SceneNode node = new SceneNode();
            Signal<Float> target = Signal.create(Float.valueOf(1.0f));
            runtime.__bindAnimatedFloat(target::get, node::setOpacity, 160);
            runtime.flush();
            runtime.__sampleMotion(1_000_000L);

            target.set(Float.valueOf(0.0f));
            runtime.flush();
            int historyAfterIntent = ReactiveScheduler.get().transactionLog().size();
            node.clearDirtyFlags();

            runtime.__sampleMotion(501_000_000L);
            Assert.assertEquals("长帧后的首次 sample 仍从起点开始", 1.0f, node.getOpacity(), 0.0001f);

            runtime.__sampleMotion(581_000_000L);
            Assert.assertEquals("半程 smoothstep 仍为 0.5", 0.5f, node.getOpacity(), 0.0001f);
            Assert.assertTrue("opacity sample 只标 composite", node.__isCompositeDirty());
            Assert.assertFalse(node.__isSelfLayoutDirty());
            Assert.assertFalse(node.__isSelfPaintDirty());
            Assert.assertEquals("逐帧 sample 不写事务历史", historyAfterIntent,
                    ReactiveScheduler.get().transactionLog().size());

            runtime.__sampleMotion(661_000_000L);
            Assert.assertEquals("standard 160ms 到达端点", 0.0f, node.getOpacity(), 0.0001f);
            Assert.assertEquals(0, runtime.__activeMotionCountForTest());
        } finally {
            runtime.dispose();
        }
    }

    @Test
    public void colorMotionInterpolatesArgbAtFastDuration() {
        SceneRuntime runtime = new SceneRuntime();
        try {
            runtime.__enableMotion();
            SceneNode node = new SceneNode();
            Signal<Integer> target = Signal.create(Integer.valueOf(0xFF000000));
            runtime.__bindAnimatedColor(target::get, node::setBackgroundColor, 90);
            runtime.flush();

            target.set(Integer.valueOf(0xFFFFFFFF));
            runtime.flush();
            runtime.__sampleMotion(5_000_000L);
            runtime.__sampleMotion(50_000_000L);
            Assert.assertEquals("fast 半程 ARGB 插值", 0xFF808080, node.getBackgroundColor());

            runtime.__sampleMotion(95_000_000L);
            Assert.assertEquals(0xFFFFFFFF, node.getBackgroundColor());
        } finally {
            runtime.dispose();
        }
    }

    @Test
    public void runtimesSampleSameSignalIndependently() {
        SceneRuntime first = new SceneRuntime();
        SceneRuntime second = new SceneRuntime();
        try {
            first.__enableMotion();
            second.__enableMotion();
            Signal<Float> target = Signal.create(Float.valueOf(1.0f));
            SceneNode firstNode = new SceneNode();
            SceneNode secondNode = new SceneNode();
            first.__bindAnimatedFloat(target::get, firstNode::setOpacity, 160);
            second.__bindAnimatedFloat(target::get, secondNode::setOpacity, 160);
            first.flush();

            target.set(Float.valueOf(0.0f));
            first.flush();
            first.__sampleMotion(10_000_000L);
            first.__sampleMotion(90_000_000L);

            Assert.assertEquals("first occurrence 独立推进", 0.5f, firstNode.getOpacity(), 0.0001f);
            Assert.assertEquals("second occurrence 未采样不推进", 1.0f, secondNode.getOpacity(), 0.0001f);
            Assert.assertEquals(1, first.__activeMotionCountForTest());
            Assert.assertEquals(1, second.__activeMotionCountForTest());

            second.__sampleMotion(20_000_000L);
            second.__sampleMotion(180_000_000L);
            Assert.assertEquals(0.0f, secondNode.getOpacity(), 0.0001f);
            Assert.assertEquals("first 不受 second 时钟影响", 0.5f, firstNode.getOpacity(), 0.0001f);
        } finally {
            first.dispose();
            second.dispose();
        }
    }
}
