package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.scene.input.mock.MockPlatformInputSource;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * InputFrameBuilder 封板正确性单元测试。
 *
 * <p>覆盖封板状态机的核心行为：事件投影、列表冻结、粘滞态保留、
 * 一次性清空、帧时间戳规则等。</p>
 */
public class InputFrameBuilderTest {

    private static final long NOW = 1000000L;

    // ===== 测试 1：未 push 直接 drain 返回 EMPTY =====

    /**
     * 验证：构造后未推入任何事件时 drainFrame 返回 SceneInputFrame.EMPTY。
     */
    @Test
    public void shouldReturnEmptyOnInitialDrain() {
        MockPlatformInputSource source = new MockPlatformInputSource(800, 600);
        SceneInputFrame frame = source.drainFrame();
        Assert.assertTrue("初始 drain 应返回空帧", frame.isEmpty());
        Assert.assertSame("初始 drain 应返回 EMPTY 单例", SceneInputFrame.EMPTY, frame);
    }

    // ===== 测试 2：分别 push 不同种类事件，drain 后列表隔离 =====

    /**
     * 验证：分别 push KEY / POINTER / TEXT 各一条，drain 后三列表各 size==1
     * 且事件不串列。
     */
    @Test
    public void shouldSegregateEventTypesInDrain() {
        MockPlatformInputSource source = new MockPlatformInputSource(800, 600);

        source.enqueueKey(SceneKey.ENTER, SceneKeyAction.PRESSED,
                false, false, false, false, NOW);
        source.enqueuePointer(ScenePointerAction.MOVE, 100, 200,
                SceneMouseButton.NONE, 0, 0, 0, false, false, false, false, NOW + 1);
        source.enqueueText("hello", NOW + 2);

        SceneInputFrame frame = source.drainFrame();

        Assert.assertEquals("键盘事件列表大小", 1, frame.getKeyEvents().size());
        Assert.assertEquals("指针事件列表大小", 1, frame.getPointerEvents().size());
        Assert.assertEquals("文本事件列表大小", 1, frame.getTextEvents().size());

        Assert.assertEquals("键盘事件 key 应为 ENTER", SceneKey.ENTER,
                frame.getKeyEvents().get(0).getKey());
        Assert.assertEquals("指针事件 action 应为 MOVE", ScenePointerAction.MOVE,
                frame.getPointerEvents().get(0).getAction());
        Assert.assertEquals("文本事件 text 应为 hello", "hello",
                frame.getTextEvents().get(0).getText());
    }

    // ===== 测试 3：同类多条按 push 顺序出现 =====

    /**
     * 验证：推入多条同类型事件，drain 后列表中的顺序与 push 顺序一致。
     */
    @Test
    public void shouldPreservePushOrderForSameKind() {
        MockPlatformInputSource source = new MockPlatformInputSource(800, 600);

        source.enqueueKey(SceneKey.KEY_A, SceneKeyAction.PRESSED,
                false, false, false, false, NOW);
        source.enqueueKey(SceneKey.KEY_B, SceneKeyAction.PRESSED,
                false, false, false, false, NOW + 1);
        source.enqueueKey(SceneKey.KEY_C, SceneKeyAction.RELEASED,
                false, false, false, false, NOW + 2);

        SceneInputFrame frame = source.drainFrame();
        List<SceneKeyEvent> keys = frame.getKeyEvents();

        Assert.assertEquals("应有三条键盘事件", 3, keys.size());
        Assert.assertEquals("第 0 条应为 KEY_A", SceneKey.KEY_A, keys.get(0).getKey());
        Assert.assertEquals("第 1 条应为 KEY_B", SceneKey.KEY_B, keys.get(1).getKey());
        Assert.assertEquals("第 2 条应为 KEY_C", SceneKey.KEY_C, keys.get(2).getKey());
    }

    // ===== 测试 4：drain 后再 drain 返回空帧（事件不残留） =====

    /**
     * 验证：drain 后再次 drain 事件列表为空（一次性增量，不跨帧残留）。
     */
    @Test
    public void shouldClearEventsOnDrain() {
        MockPlatformInputSource source = new MockPlatformInputSource(800, 600);

        source.enqueueKey(SceneKey.ENTER, SceneKeyAction.PRESSED,
                false, false, false, false, NOW);
        SceneInputFrame frame1 = source.drainFrame();
        Assert.assertEquals("第一次 drain 应有 1 条事件", 1, frame1.getKeyEvents().size());

        // 不推任何新事件，再次 drain
        SceneInputFrame frame2 = source.drainFrame();
        Assert.assertTrue("第二次 drain 事件列表应为空", frame2.getKeyEvents().isEmpty());
    }

    // ===== 测试 5：指针位置粘滞 =====

    /**
     * 验证：push MOVE 到 (30,40) 后 drain 帧 pointerX/Y==30/40；
     * 不 push 再 drain 仍为 30/40（粘滞不归零）。
     */
    @Test
    public void shouldStickPointerPosition() {
        MockPlatformInputSource source = new MockPlatformInputSource(800, 600);

        source.enqueuePointer(ScenePointerAction.MOVE, 30, 40,
                SceneMouseButton.NONE, 0, 0, 0, false, false, false, false, NOW);
        SceneInputFrame frame1 = source.drainFrame();
        Assert.assertEquals("第一帧 pointerX", 30, frame1.getPointerX());
        Assert.assertEquals("第一帧 pointerY", 40, frame1.getPointerY());

        // 不推新事件，再次 drain
        SceneInputFrame frame2 = source.drainFrame();
        Assert.assertEquals("第二帧 pointerX 仍为 30（粘滞）", 30, frame2.getPointerX());
        Assert.assertEquals("第二帧 pointerY 仍为 40（粘滞）", 40, frame2.getPointerY());
    }

    // ===== 测试 6：修饰键粘滞 =====

    /**
     * 验证：push shiftDown 键事件后 drain，下帧无事件时帧级 isShiftDown() 仍为 true。
     */
    @Test
    public void shouldStickModifierState() {
        MockPlatformInputSource source = new MockPlatformInputSource(800, 600);

        // push 一个 shiftDown=true 的键事件
        source.enqueueKey(SceneKey.KEY_A, SceneKeyAction.PRESSED,
                false, true, false, false, NOW);
        SceneInputFrame frame1 = source.drainFrame();
        Assert.assertTrue("第一帧 shiftDown 应为 true", frame1.isShiftDown());

        // 不推新事件，再次 drain
        SceneInputFrame frame2 = source.drainFrame();
        Assert.assertTrue("第二帧 shiftDown 仍为 true（粘滞）", frame2.isShiftDown());
    }

    // ===== 测试 7-extra：TEXT 事件不污染帧级修饰键（回归锚点） =====

    /**
     * 验证：同帧先 push KEY(shift=true) 再 push TEXT，drain 后帧级 isShiftDown() 仍为 true。
     *
     * <p>GLFW 真机“按住 Shift 打字”场景下，key callback 携带 mods=true，
     * 而 char callback 不携带 mods。TEXT 事件不得覆盖已建立的修饰键粘滞态。</p>
     */
    @Test
    public void shouldNotPolluteModifiersWithTextEvent() {
        MockPlatformInputSource source = new MockPlatformInputSource(800, 600);

        // 模拟"按住 Shift 按 A"：先 KEY(shift=true)，再 TEXT("A")
        source.enqueueKey(SceneKey.KEY_A, SceneKeyAction.PRESSED,
                false, true, false, false, NOW);
        source.enqueueText("A", NOW + 1);

        SceneInputFrame frame = source.drainFrame();
        // 帧级 shiftDown 必须仍为 true（不被 TEXT 的 false 覆盖）
        Assert.assertTrue("帧级 shiftDown 应为 true（不被 TEXT 事件污染）", frame.isShiftDown());
        Assert.assertEquals("应有两条事件", 2,
                frame.getKeyEvents().size() + frame.getTextEvents().size());
    }

    /**
     * 验证：同一输入帧内多条 TEXT 会按 push 顺序合并为一条，并沿用最后一条 TEXT 的时间戳。
     */
    @Test
    public void shouldMergeMultipleTextEventsInSameFrame() {
        MockPlatformInputSource source = new MockPlatformInputSource(800, 600);

        source.enqueueText("修", NOW);
        source.enqueueText("好", NOW + 1);
        source.enqueueText("了", NOW + 2);

        SceneInputFrame frame = source.drainFrame();
        List<SceneTextEvent> textEvents = frame.getTextEvents();

        Assert.assertEquals("同帧多条 TEXT 应合并为 1 条", 1, textEvents.size());
        Assert.assertEquals("合并文本应保持原顺序", "修好了", textEvents.get(0).getText());
        Assert.assertEquals("合并事件时间戳应取最后一条 TEXT", NOW + 2,
                textEvents.get(0).getTimeNanos());
    }

    // ===== 测试 7：帧时间戳 =====

    /**
     * 验证：frameTimeNanos 取本帧最后一个 push 事件的 timeNanos；
     * 无事件时取 0。
     */
    @Test
    public void shouldUseLastEventTimeForFrameTimestamp() {
        MockPlatformInputSource source = new MockPlatformInputSource(800, 600);

        source.enqueueKey(SceneKey.KEY_A, SceneKeyAction.PRESSED,
                false, false, false, false, 100L);
        source.enqueueKey(SceneKey.KEY_B, SceneKeyAction.PRESSED,
                false, false, false, false, 200L);
        SceneInputFrame frame1 = source.drainFrame();
        Assert.assertEquals("帧时间戳应为最后事件时间 200", 200L, frame1.getFrameTimeNanos());

        // 再次 drain（无事件）
        SceneInputFrame frame2 = source.drainFrame();
        Assert.assertEquals("无事件帧时间戳应为 0", 0L, frame2.getFrameTimeNanos());
    }
}
