package club.heiqi.uilib.ui.scene.runtime;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.input.InputBinding;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * {@link SceneRuntime#portal} 的 visible 驱动挂卸与生命周期测试。
 */
public class SceneOverlayPortalTest {

    private SceneRuntime runtime;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime();
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 验证 visible 初始 false 时 flush 后不注册 overlay。 */
    @Test
    public void initialFalseShouldNotRegisterOverlay() {
        Signal<Boolean> visible = Signal.create(false);
        runtime.portal(visible, SceneNode::new);

        runtime.flush();

        Assert.assertTrue("初始 false 不应注册 overlay", runtime.getOverlayHost().isEmpty());
    }

    /** 验证 visible true 后注册一个 overlay root。 */
    @Test
    public void trueShouldRegisterOneOverlayRoot() {
        Signal<Boolean> visible = Signal.create(false);
        SceneNode overlayRoot = new SceneNode();
        runtime.portal(visible, () -> overlayRoot);

        visible.set(true);
        runtime.flush();

        Assert.assertEquals("true 后应注册 1 个 overlay", 1, runtime.getOverlayHost().size());
        Assert.assertSame("注册的 root 应来自 builder", overlayRoot,
                runtime.getOverlayHost().bottomFirst().get(0).getRoot());
    }

    /** 验证 visible 连续 true 不重复注册或重建。 */
    @Test
    public void continuousTrueShouldNotRebuildOverlay() {
        Signal<Boolean> visible = Signal.create(false);
        AtomicInteger buildCount = new AtomicInteger();
        runtime.portal(visible, () -> {
            buildCount.incrementAndGet();
            return new SceneNode();
        });

        visible.set(true);
        runtime.flush();
        runtime.flush();

        Assert.assertEquals("连续 true 只构建一次", 1, buildCount.get());
        Assert.assertEquals("连续 true 只保留 1 个 overlay", 1, runtime.getOverlayHost().size());
    }

    /** 验证 visible false 后移除 overlay。 */
    @Test
    public void falseShouldRemoveOverlay() {
        Signal<Boolean> visible = Signal.create(true);
        runtime.portal(visible, SceneNode::new);
        runtime.flush();
        Assert.assertEquals(1, runtime.getOverlayHost().size());

        visible.set(false);
        runtime.flush();

        Assert.assertTrue("false 后应移除 overlay", runtime.getOverlayHost().isEmpty());
    }

    /** 验证 portal handle dispose 后移除 overlay，且后续 visible true 不再重建。 */
    @Test
    public void handleDisposeShouldRemoveOverlayAndStopFutureRebuild() {
        Signal<Boolean> visible = Signal.create(true);
        AtomicInteger buildCount = new AtomicInteger();
        ScenePortalHandle handle = runtime.portal(visible, () -> {
            buildCount.incrementAndGet();
            return new SceneNode();
        });
        runtime.flush();

        handle.dispose();
        visible.set(false);
        runtime.flush();
        visible.set(true);
        runtime.flush();

        Assert.assertTrue("handle 应已释放", handle.isDisposed());
        Assert.assertTrue("handle dispose 后 overlay 应移除", runtime.getOverlayHost().isEmpty());
        Assert.assertEquals("dispose 后不应重新构建", 1, buildCount.get());
    }

    /** 验证 runtime.dispose 会清空 portal 注册的 overlay host。 */
    @Test
    public void runtimeDisposeShouldClearOverlayHost() {
        Signal<Boolean> visible = Signal.create(true);
        runtime.portal(visible, SceneNode::new);
        runtime.flush();
        Assert.assertEquals(1, runtime.getOverlayHost().size());

        runtime.dispose();

        Assert.assertTrue("runtime dispose 后 overlay host 应为空", runtime.getOverlayHost().isEmpty());
    }

    /** 验证 overlay builder 内注册的 bind 与 on 随 portal 卸载清理。 */
    @Test
    public void bindAndOnInsidePortalShouldDisposeWithOverlay() {
        Signal<Boolean> visible = Signal.create(true);
        Signal<Integer> color = Signal.create(0);
        Binding[] bindingHolder = new Binding[1];
        InputBinding[] inputHolder = new InputBinding[1];

        runtime.portal(visible, () -> {
            SceneNode root = new SceneNode();
            bindingHolder[0] = runtime.bind(Invalidation.PAINT, color, root::setBackgroundColor);
            inputHolder[0] = runtime.on(root, SceneEventType.CLICK, (event, context) -> { });
            return root;
        });
        runtime.flush();

        visible.set(false);
        runtime.flush();

        Assert.assertNotNull("应创建 bind 句柄", bindingHolder[0]);
        Assert.assertNotNull("应创建 on 句柄", inputHolder[0]);
        Assert.assertTrue("portal 卸载后 bind 应释放", bindingHolder[0].isDisposed());
        Assert.assertTrue("portal 卸载后 on 应释放", inputHolder[0].isDisposed());
        Assert.assertTrue("portal 卸载后 overlay 应移除", runtime.getOverlayHost().isEmpty());
    }

    /** 验证组件 Owner cleanup 会清理其内部声明的 portal overlay。 */
    @Test
    public void componentOwnerCleanupShouldRemovePortalOverlay() {
        SceneNode parent = new SceneNode();
        Signal<Boolean> visible = Signal.create(true);
        MountHandle mount = runtime.mount(parent, () -> {
            runtime.portal(visible, SceneNode::new);
            return new SceneNode();
        });
        runtime.flush();
        Assert.assertEquals(1, runtime.getOverlayHost().size());

        mount.dispose();

        Assert.assertTrue("组件卸载应清理 portal overlay", runtime.getOverlayHost().isEmpty());
    }
}
