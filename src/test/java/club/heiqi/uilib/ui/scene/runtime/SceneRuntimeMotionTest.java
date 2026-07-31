package club.heiqi.uilib.ui.scene.runtime;

import java.util.Arrays;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.input.SceneHitTester;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
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
    public void colorMotionPreservesVisibleRgbAtTransparentEndpoint() {
        SceneRuntime runtime = new SceneRuntime();
        try {
            runtime.__enableMotion();
            SceneNode node = new SceneNode();
            Signal<Integer> target = Signal.create(Integer.valueOf(0x00000000));
            runtime.__bindAnimatedColor(target::get, node::setBackgroundColor, 90);
            runtime.flush();

            target.set(Integer.valueOf(0xFFD0BCFF));
            runtime.flush();
            runtime.__sampleMotion(1_000_000L);
            runtime.__sampleMotion(46_000_000L);
            Assert.assertEquals("淡入半程只插值 alpha，不应把 RGB 一并压暗",
                    0x80D0BCFF, node.getBackgroundColor());
            runtime.__sampleMotion(91_000_000L);
            Assert.assertEquals(0xFFD0BCFF, node.getBackgroundColor());

            target.set(Integer.valueOf(0x00000000));
            runtime.flush();
            runtime.__sampleMotion(92_000_000L);
            runtime.__sampleMotion(137_000_000L);
            Assert.assertEquals("淡出半程应保留可见端点 RGB",
                    0x80D0BCFF, node.getBackgroundColor());
            runtime.__sampleMotion(182_000_000L);
            Assert.assertEquals(0x00000000, node.getBackgroundColor());
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

    @Test
    public void staggeredRevealWaitsForFreshLayoutAndCapsDelayWithoutOpacityFlash() {
        SceneRuntime runtime = new SceneRuntime();
        try {
            runtime.__enableMotion();
            SceneNode parent = SceneNode.column();
            SceneNode first = new SceneNode().setPreferredHeight(20);
            SceneNode firstControl = new SceneNode().setPreferredWidth(100).setPreferredHeight(20);
            first.appendChild(firstControl);
            first.setCachedLayout(new LayoutBox(0, 0, 100, 20));
            SceneNode second = new SceneNode().setPreferredHeight(20);
            second.__setHitTestSubtreeEnabled(false);
            SceneNode third = new SceneNode().setPreferredHeight(20);
            runtime.mount(parent, () -> {
                SceneNode group = SceneNode.column();
                group.appendChild(first);
                group.appendChild(second);
                group.appendChild(third);
                runtime.__staggeredReveal(Arrays.asList(first, second, third), -20.0f, 200, 60, 80);
                return group;
            });
            runtime.flush();

            Assert.assertEquals("layout-ready 前保持完整可见初态", 1.0f, first.getOpacity(), 0.0001f);
            Assert.assertEquals(-20, first.__getPresentationOffsetY());
            Assert.assertEquals(-20, second.__getPresentationOffsetY());
            Assert.assertEquals(-20, third.__getPresentationOffsetY());
            Assert.assertTrue("stagger target 必须保持 identity transform",
                    first.getTransform() == null || first.getTransform().isIdentity());
            Assert.assertEquals("即使残留旧 LayoutBox，也必须等待安装后的新 publication", 0,
                    runtime.__activeMotionCountForTest());
            Assert.assertFalse("视觉位置与布局命中盒分离期间必须关闭整棵字段子树输入",
                    first.__isHitTestSubtreeEnabled());
            Assert.assertFalse(second.__isHitTestSubtreeEnabled());
            Assert.assertFalse(third.__isHitTestSubtreeEnabled());

            SceneLayoutEngine layout = new SceneLayoutEngine(new FixedTextMeasurer());
            layout.layout(parent, new Constraints(200, 100));
            Assert.assertFalse("位移期间不得命中真实字段控件",
                    new SceneHitTester().hitTest(parent, 1, 1, 0, 0).contains(firstControl));
            runtime.__bridgeLayoutEpoch(layout.layoutEpoch());
            runtime.flush();
            Assert.assertEquals(3, runtime.__activeMotionCountForTest());

            runtime.__sampleMotion(1_000_000L);
            runtime.__sampleMotion(21_000_000L);
            Assert.assertTrue("首项已开始向终点移动", first.__getPresentationOffsetY() > -20);
            Assert.assertTrue("位移推进不得改写普通 transform",
                    first.getTransform() == null || first.getTransform().isIdentity());
            Assert.assertEquals("第二项仍处于 60ms delay", -20,
                    second.__getPresentationOffsetY());
            Assert.assertEquals("第三项仍处于 80ms capped delay", -20,
                    third.__getPresentationOffsetY());
            Assert.assertEquals("级联不得使用 opacity 明灭", 1.0f, second.getOpacity(), 0.0001f);

            runtime.__sampleMotion(121_000_000L);
            Assert.assertTrue("第三项应按 80ms cap 启动，而非等待原始 120ms",
                    third.__getPresentationOffsetY() > -20);

            runtime.__sampleMotion(301_000_000L);
            Assert.assertEquals(0, first.__getPresentationOffsetY());
            Assert.assertEquals(0, second.__getPresentationOffsetY());
            Assert.assertEquals(0, third.__getPresentationOffsetY());
            Assert.assertEquals(0, runtime.__activeMotionCountForTest());
            Assert.assertTrue("归位完成后恢复字段子树输入", first.__isHitTestSubtreeEnabled());
            Assert.assertFalse("原本关闭的 hit-test 门禁必须保持关闭", second.__isHitTestSubtreeEnabled());
            Assert.assertTrue(third.__isHitTestSubtreeEnabled());
            Assert.assertTrue(new SceneHitTester().hitTest(parent, 1, 1, 0, 0).contains(firstControl));
        } finally {
            runtime.dispose();
        }
    }

    @Test
    public void staggeredRevealDisposalBeforeLayoutPublicationRestoresInitialState() {
        SceneRuntime runtime = new SceneRuntime();
        try {
            runtime.__enableMotion();
            SceneNode parent = SceneNode.column();
            SceneNode target = new SceneNode().setPreferredHeight(20);
            MountHandle handle = runtime.mount(parent, () -> {
                SceneNode group = SceneNode.column();
                group.appendChild(target);
                runtime.__staggeredReveal(Arrays.asList(target), -20.0f, 240, 0, 0);
                return group;
            });
            runtime.flush();
            Assert.assertEquals(0, runtime.__activeMotionCountForTest());
            Assert.assertFalse(target.__isHitTestSubtreeEnabled());

            handle.dispose();
            runtime.__bridgeLayoutEpoch(1);
            runtime.flush();

            Assert.assertEquals("layout-ready 前卸载不得迟到启动", 0, runtime.__activeMotionCountForTest());
            Assert.assertEquals(0, target.__getPresentationOffsetY());
            Assert.assertTrue(target.__isHitTestSubtreeEnabled());
        } finally {
            runtime.dispose();
        }
    }

    @Test
    public void runtimeDisposeRestoresActiveStaggeredReveal() {
        SceneRuntime runtime = new SceneRuntime();
        boolean disposed = false;
        SceneNode target = new SceneNode().setPreferredHeight(20);
        try {
            runtime.__enableMotion();
            SceneNode parent = SceneNode.column();
            runtime.mount(parent, () -> {
                SceneNode group = SceneNode.column();
                group.appendChild(target);
                runtime.__staggeredReveal(Arrays.asList(target), -20.0f, 240, 0, 0);
                return group;
            });
            runtime.flush();
            SceneLayoutEngine layout = new SceneLayoutEngine(new FixedTextMeasurer());
            layout.layout(parent, new Constraints(200, 100));
            runtime.__bridgeLayoutEpoch(layout.layoutEpoch());
            runtime.flush();
            Assert.assertEquals(1, runtime.__activeMotionCountForTest());

            runtime.dispose();
            disposed = true;

            Assert.assertEquals(0, runtime.__activeMotionCountForTest());
            Assert.assertEquals(0, target.__getPresentationOffsetY());
            Assert.assertTrue(target.__isHitTestSubtreeEnabled());
        } finally {
            if (!disposed) {
                runtime.dispose();
            }
        }
    }

    @Test
    public void staggeredRevealTracksFollowMountOwnerLifetime() {
        SceneRuntime runtime = new SceneRuntime();
        try {
            runtime.__enableMotion();
            SceneNode parent = SceneNode.column();
            SceneNode target = new SceneNode().setPreferredHeight(20);
            target.__setHitTestSubtreeEnabled(false);
            MountHandle handle = runtime.mount(parent, () -> {
                SceneNode group = SceneNode.column();
                group.appendChild(target);
                runtime.__staggeredReveal(Arrays.asList(target), -20.0f, 240, 0, 0);
                return group;
            });
            runtime.flush();
            Assert.assertFalse(target.__isHitTestSubtreeEnabled());
            SceneLayoutEngine layout = new SceneLayoutEngine(new FixedTextMeasurer());
            layout.layout(parent, new Constraints(200, 100));
            runtime.__bridgeLayoutEpoch(layout.layoutEpoch());
            runtime.flush();
            Assert.assertEquals(1, runtime.__activeMotionCountForTest());

            handle.dispose();

            Assert.assertEquals("卸载必须取消迟到轨道", 0, runtime.__activeMotionCountForTest());
            Assert.assertEquals("卸载清理归零 presentation offset，节点复用不带残留", 0,
                    target.__getPresentationOffsetY());
            Assert.assertFalse("卸载必须恢复 presentation shell 原有 hit-test 门禁",
                    target.__isHitTestSubtreeEnabled());
        } finally {
            runtime.dispose();
        }
    }
}
