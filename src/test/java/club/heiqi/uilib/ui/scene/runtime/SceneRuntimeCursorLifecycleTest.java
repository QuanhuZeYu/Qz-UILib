package club.heiqi.uilib.ui.scene.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.input.CursorBackend;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * SceneRuntime 系统光标关闭生命周期测试。
 *
 * <p>仅使用 runtime、reactive 与平台无关 input 边界，验证普通响应式投影和关闭强制复位互不混用。</p>
 */
public class SceneRuntimeCursorLifecycleTest {

    private SceneInteractionHarness harness;
    private SceneRuntime runtime;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create();
        runtime = harness.getRuntime();
    }

    @After
    public void tearDown() {
        harness.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 非默认光标关闭后须走 forceApply(DEFAULT)，且不得改写 cursorSignal 或伪装成普通 apply。 */
    @Test
    public void shouldForceDefaultWithoutChangingCursorSignalWhenDisposed() {
        RecordingCursorBackend backend = new RecordingCursorBackend();
        runtime.bindCursor(backend);
        routePointerCursor();

        Assert.assertEquals("关闭前 cursorSignal 应为 POINTER", SceneCursor.POINTER, runtime.cursorSignal().get());
        Assert.assertEquals("响应式投影应通过普通 apply 下发 POINTER", SceneCursor.POINTER,
                backend.latestAppliedCursor());
        Assert.assertTrue("关闭前不应调用强制复位", backend.forceAppliedCursors.isEmpty());
        int ordinaryApplyCount = backend.appliedCursors.size();

        runtime.dispose();

        Assert.assertEquals("关闭复位不得修改 cursorSignal", SceneCursor.POINTER, runtime.cursorSignal().get());
        Assert.assertEquals("关闭复位不得伪装成普通 apply", ordinaryApplyCount, backend.appliedCursors.size());
        Assert.assertEquals("关闭时应强制恢复 DEFAULT", Collections.singletonList(SceneCursor.DEFAULT),
                backend.forceAppliedCursors);
    }

    /** 每个已绑定后端都只在 runtime 生命周期结束时强制复位一次。 */
    @Test
    public void shouldForceEveryBoundBackendOnlyOnceAcrossRepeatedDispose() {
        RecordingCursorBackend first = new RecordingCursorBackend();
        RecordingCursorBackend second = new RecordingCursorBackend();
        runtime.bindCursor(first);
        runtime.bindCursor(second);

        runtime.dispose();
        runtime.dispose();

        Assert.assertEquals(Collections.singletonList(SceneCursor.DEFAULT), first.forceAppliedCursors);
        Assert.assertEquals(Collections.singletonList(SceneCursor.DEFAULT), second.forceAppliedCursors);
    }

    /** 未绑定光标后端的 runtime 关闭及重复关闭均应保持安全。 */
    @Test
    public void shouldDisposeSafelyWithoutBoundCursorBackend() {
        runtime.dispose();
        runtime.dispose();
    }

    /** Owner 子树 cleanup 抛错时，dispose finally 仍须尝试光标复位且后续 dispose 不得重试。 */
    @Test
    public void shouldForceDefaultWhenOwnerCleanupFails() {
        RecordingCursorBackend backend = new RecordingCursorBackend();
        runtime.bindCursor(backend);
        SceneNode failingParent = new SceneNode() {
            @Override
            public void removeChild(SceneNode child) {
                throw new IllegalStateException("owner-cleanup-failed");
            }
        };
        runtime.mount(failingParent, SceneNode::new);

        try {
            runtime.dispose();
            Assert.fail("Owner cleanup 失败应继续向调用方传播");
        } catch (IllegalStateException exception) {
            Assert.assertEquals("owner-cleanup-failed", exception.getMessage());
        }

        Assert.assertEquals("Owner cleanup 失败后仍应强制恢复 DEFAULT",
                Collections.singletonList(SceneCursor.DEFAULT), backend.forceAppliedCursors);
        runtime.dispose();
        Assert.assertEquals("重复 dispose 不得再次强制复位", 1, backend.forceAppliedCursors.size());
    }

    /** 路由一次命中 POINTER 节点的 MOVE，并推进响应式 flush。 */
    private void routePointerCursor() {
        SceneNode root = new SceneNode();
        SceneNode target = new SceneNode();
        target.setPreferredWidth(40);
        target.setPreferredHeight(30);
        target.setCursor(SceneCursor.POINTER);
        root.appendChild(target);
        harness.mountRoot(root, 100, 100);
        harness.moveTo(target);
    }

    /** 分离记录普通投影与强制复位的 fake backend。 */
    private static final class RecordingCursorBackend implements CursorBackend {

        private final List<SceneCursor> appliedCursors = new ArrayList<>();
        private final List<SceneCursor> forceAppliedCursors = new ArrayList<>();

        @Override
        public void apply(SceneCursor cursor) {
            appliedCursors.add(cursor);
        }

        @Override
        public void forceApply(SceneCursor cursor) {
            forceAppliedCursors.add(cursor);
        }

        /** @return 最近一次普通 apply 的光标。 */
        private SceneCursor latestAppliedCursor() {
            return appliedCursors.get(appliedCursors.size() - 1);
        }
    }
}
