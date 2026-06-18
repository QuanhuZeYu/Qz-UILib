package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.scene.input.mock.MockPlatformInputSource;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * SceneInputFrame 不可变性单元测试。
 *
 * <p>验证 drainFrame 返回的帧快照中所有列表均为不可变，且不同帧之间互不干扰。</p>
 */
public class SceneInputFrameImmutabilityTest {

    private static final long NOW = 1000000L;

    // ===== 测试 8：getter 列表不可变 =====

    /**
     * 验证：getKeyEvents() 返回的列表不可修改。
     */
    @Test(expected = UnsupportedOperationException.class)
    public void shouldThrowOnKeyEventsModification() {
        MockPlatformInputSource source = new MockPlatformInputSource(800, 600);
        source.enqueueKey(SceneKey.ENTER, SceneKeyAction.PRESSED,
                false, false, false, false, NOW);
        SceneInputFrame frame = source.drainFrame();

        List<SceneKeyEvent> keys = frame.getKeyEvents();
        Assert.assertEquals("应有一条事件", 1, keys.size());
        // 尝试修改列表应抛出 UnsupportedOperationException
        keys.add(new SceneKeyEvent(SceneKey.KEY_A, SceneKeyAction.PRESSED,
                false, false, false, false,
                RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, NOW));
    }

    /**
     * 验证：getPointerEvents() 返回的列表不可修改。
     */
    @Test(expected = UnsupportedOperationException.class)
    public void shouldThrowOnPointerEventsModification() {
        MockPlatformInputSource source = new MockPlatformInputSource(800, 600);
        source.enqueuePointer(ScenePointerAction.MOVE, 10, 20,
                SceneMouseButton.NONE, 0, 0, 0, false, false, false, false, NOW);
        SceneInputFrame frame = source.drainFrame();

        List<ScenePointerEvent> pointers = frame.getPointerEvents();
        Assert.assertEquals("应有一条事件", 1, pointers.size());
        pointers.add(new ScenePointerEvent(ScenePointerAction.MOVE, 0, 0,
                SceneMouseButton.NONE, 0, 0, 0, false, false, false, false, NOW));
    }

    /**
     * 验证：getTextEvents() 返回的列表不可修改。
     */
    @Test(expected = UnsupportedOperationException.class)
    public void shouldThrowOnTextEventsModification() {
        MockPlatformInputSource source = new MockPlatformInputSource(800, 600);
        source.enqueueText("test", NOW);
        SceneInputFrame frame = source.drainFrame();

        List<SceneTextEvent> texts = frame.getTextEvents();
        Assert.assertEquals("应有一条事件", 1, texts.size());
        texts.add(new SceneTextEvent("new", NOW));
    }

    // ===== 测试 9：帧间解耦（旧帧不受新 push 影响） =====

    /**
     * 验证：drain 出帧 F1 后再 push 新事件 drain 得到 F2，
     * F1 的事件列表 size 不受影响（完全解耦）。
     */
    @Test
    public void shouldDecoupleFrames() {
        MockPlatformInputSource source = new MockPlatformInputSource(800, 600);

        source.enqueueKey(SceneKey.KEY_A, SceneKeyAction.PRESSED,
                false, false, false, false, NOW);
        SceneInputFrame frame1 = source.drainFrame();
        Assert.assertEquals("F1 键盘事件数", 1, frame1.getKeyEvents().size());

        // push 新事件并 drain F2
        source.enqueueKey(SceneKey.KEY_B, SceneKeyAction.PRESSED,
                false, false, false, false, NOW + 1);
        source.enqueueKey(SceneKey.KEY_C, SceneKeyAction.PRESSED,
                false, false, false, false, NOW + 2);
        SceneInputFrame frame2 = source.drainFrame();
        Assert.assertEquals("F2 键盘事件数", 2, frame2.getKeyEvents().size());

        // 断言 F1 列表 size 未变（解耦）
        Assert.assertEquals("F1 列表 size 应不受 F2 影响", 1, frame1.getKeyEvents().size());
        Assert.assertEquals("F1 首事件仍为 KEY_A", SceneKey.KEY_A,
                frame1.getKeyEvents().get(0).getKey());
    }
}
