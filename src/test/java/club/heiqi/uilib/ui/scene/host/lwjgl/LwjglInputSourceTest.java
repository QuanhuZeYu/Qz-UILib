package club.heiqi.uilib.ui.scene.host.lwjgl;


import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.input.SceneKeyEvent;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.input.SceneTextEvent;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * LwjglInputSource 差分状态机单元测试（纯沙箱，mock reader 驱动）。
 *
 * <p>覆盖：首帧基线、MOVE 差分、按钮边沿、滚轮差分、修饰键、封板分类。</p>
 */
public class LwjglInputSourceTest {

    private MockPlatformStateReader reader;
    private LwjglInputSource source;

    @Before
    public void setUp() {
        reader = new MockPlatformStateReader();
        source = new LwjglInputSource(reader);
    }

    // ==================== 辅助方法 ====================

    /** drain 一帧并返回指针事件列表 */
    private List<ScenePointerEvent> drainPointerEvents() {
        SceneInputFrame frame = source.drainFrame();
        return frame.getPointerEvents();
    }

    /** drain 一帧并返回帧对象 */
    private SceneInputFrame drainFrame() {
        return source.drainFrame();
    }

    // ==================== 组 G：首帧基线 ====================

    @Test
    public void g1_firstFrameBaselineReturnsEmptyEvenWithNonDefaultState() {
        // 首帧鼠标在非原点且有按键按下
        reader.mouseX = 100;
        reader.mouseY = 200;
        reader.buttonLeft = true;
        reader.advanceTime();

        SceneInputFrame frame = drainFrame();
        Assert.assertTrue("首帧应返回空事件帧（仅建基线不产事件）", frame.isEmpty());
    }

    @Test
    public void g2_secondFrameNoChangeReturnsEmpty() {
        // 首帧建基线
        drainFrame();
        reader.advanceTime();

        // 第二帧无任何变化
        SceneInputFrame frame = drainFrame();
        Assert.assertTrue("无变化帧应为空", frame.isEmpty());
    }

    // ==================== 组 H：MOVE ====================

    @Test
    public void h1_moveProducesEventWithCorrectDelta() {
        drainFrame(); // 首帧基线
        reader.advanceTime();

        // 移动鼠标
        reader.mouseX = 50;
        reader.mouseY = 30;

        List<ScenePointerEvent> events = drainPointerEvents();
        Assert.assertEquals("应产 1 个 MOVE 事件", 1, events.size());

        ScenePointerEvent e = events.get(0);
        Assert.assertEquals("action= MOVE", ScenePointerAction.MOVE, e.getAction());
        Assert.assertEquals("logicalX=50", 50, e.getLogicalX());
        Assert.assertEquals("logicalY=30", 30, e.getLogicalY());
        Assert.assertEquals("deltaX=50", 50, e.getDeltaX());
        Assert.assertEquals("deltaY=30", 30, e.getDeltaY());
    }

    @Test
    public void h2_noMoveWhenPositionUnchanged() {
        drainFrame(); // 基线
        reader.advanceTime();

        // 第二帧位置不变
        List<ScenePointerEvent> events = drainPointerEvents();
        Assert.assertTrue("位置不变不应产 MOVE", events.isEmpty());
    }

    @Test
    public void h3_consecutiveMovesEachProduceEvent() {
        drainFrame(); // 基线

        // 帧2：移动到 (50, 0)
        reader.mouseX = 50;
        reader.advanceTime();
        List<ScenePointerEvent> e2 = drainPointerEvents();
        Assert.assertEquals("帧2 应有 1 个 MOVE", 1, e2.size());
        Assert.assertEquals("帧2 deltaX=50", 50, e2.get(0).getDeltaX());

        // 帧3：移动到 (100, 60)
        reader.mouseX = 100;
        reader.mouseY = 60;
        reader.advanceTime();
        List<ScenePointerEvent> e3 = drainPointerEvents();
        Assert.assertEquals("帧3 应有 1 个 MOVE", 1, e3.size());
        Assert.assertEquals("帧3 deltaX=50", 50, e3.get(0).getDeltaX());
        Assert.assertEquals("帧3 deltaY=60", 60, e3.get(0).getDeltaY());
    }

    // ==================== 组 I：按钮边沿 ====================

    @Test
    public void i1_buttonDownProducesCorrectEvent() {
        drainFrame(); // 基线
        reader.advanceTime();

        reader.buttonLeft = true; // false→true

        List<ScenePointerEvent> events = drainPointerEvents();
        Assert.assertEquals("应产 1 个 BUTTON_DOWN", 1, events.size());

        ScenePointerEvent e = events.get(0);
        Assert.assertEquals("action=BUTTON_DOWN", ScenePointerAction.BUTTON_DOWN, e.getAction());
        Assert.assertEquals("button=LEFT", SceneMouseButton.LEFT, e.getButton());
    }

    @Test
    public void i2_buttonUpProducesCorrectEvent() {
        drainFrame(); // 基线
        reader.advanceTime();

        reader.buttonRight = true; // DOWN
        drainFrame();
        reader.advanceTime();

        reader.buttonRight = false; // UP (true→false)
        List<ScenePointerEvent> events = drainPointerEvents();
        Assert.assertEquals("应产 1 个 BUTTON_UP", 1, events.size());

        ScenePointerEvent e = events.get(0);
        Assert.assertEquals("action=BUTTON_UP", ScenePointerAction.BUTTON_UP, e.getAction());
        Assert.assertEquals("button=RIGHT", SceneMouseButton.RIGHT, e.getButton());
    }

    @Test
    public void i3_heldButtonProducesNoEvent() {
        drainFrame(); // 基线
        reader.advanceTime();

        reader.buttonLeft = true; // DOWN
        drainFrame();
        reader.advanceTime();

        // 第三帧仍按住，无变化
        List<ScenePointerEvent> events = drainPointerEvents();
        Assert.assertTrue("按住不产事件", events.isEmpty());
    }

    @Test
    public void i4_moveAndDownInSameFrameOrderMoveFirst() {
        drainFrame(); // 基线 (0,0)
        reader.advanceTime();

        // 同帧：移动 + 按下
        reader.mouseX = 30;
        reader.mouseY = 40;
        reader.buttonLeft = true;

        List<ScenePointerEvent> events = drainPointerEvents();
        Assert.assertEquals("应产 2 个事件", 2, events.size());

        // MOVE 必须先于 BUTTON_DOWN
        Assert.assertEquals("第一个应为 MOVE", ScenePointerAction.MOVE, events.get(0).getAction());
        Assert.assertEquals("第二个应为 BUTTON_DOWN", ScenePointerAction.BUTTON_DOWN, events.get(1).getAction());
    }

    /**
     * L1 显式契约：DOWN+UP 在同一帧内完成（两次 drainFrame 间隔内按钮按下又释放），
     * 当前态已恢复 false，差分只看到 false→false（无净变化），不产任何事件。
     * 这是方案 C 一帧 poll 一次的固有局限，极短手动点击可能触发。
     */
    @Test
    public void shouldMissClickWhenDownUpInSameFrame() {
        drainFrame(); // 基线 (left=false)
        reader.advanceTime();

        // 在 drainFrame 间隔内：按下→释放，下一帧读到的当前态仍然是 false
        // （模拟：两次 drainFrame 之间发生的完整 click）
        reader.buttonLeft = false; // 本就是 false——差分无变化
        List<ScenePointerEvent> events = drainPointerEvents();

        Assert.assertTrue("同帧 false→false 不应产任何事件（L1 已知局限）", events.isEmpty());
    }

    // ==================== 组 J：滚轮差分 ====================

    @Test
    public void j1_scrollDeltaProducesEvent() {
        drainFrame(); // 基线 (scrollAccum=0)
        reader.advanceTime();

        reader.scrollAccum = 1.0; // +1 个 notch（保底步长算法：1.0 * 120 = 120）

        List<ScenePointerEvent> events = drainPointerEvents();
        Assert.assertEquals("应产 1 个 SCROLL", 1, events.size());

        ScenePointerEvent e = events.get(0);
        Assert.assertEquals("action=SCROLL", ScenePointerAction.SCROLL, e.getAction());
        Assert.assertEquals("wheelDelta=120（1.0 notch × 120，保留幅度）", 120, e.getWheelDelta());
    }

    @Test
    public void j2_noScrollWhenAccumUnchanged() {
        drainFrame(); // 基线
        reader.advanceTime();

        // scrollAccum 不变
        List<ScenePointerEvent> events = drainPointerEvents();
        Assert.assertTrue("无滚轮变化不应产 SCROLL", events.isEmpty());
    }

    /**
     * J3：方向测试——scrollAccum 负差分应产负 wheelDelta。
     *
     * <p>符号约定（与 SceneScrolls handler `next = current - wheelDelta` 对齐）：
     * scrollDiff < 0（向下滚，看下方内容，scrollOffsetY 应增）→ wheelDelta < 0。</p>
     */
    @Test
    public void j3_negativeScrollDeltaProducesNegativeWheelDelta() {
        drainFrame(); // 基线 (scrollAccum=0)
        reader.advanceTime();

        reader.scrollAccum = -1.0; // -1 notch（向下滚）

        List<ScenePointerEvent> events = drainPointerEvents();
        Assert.assertEquals("应产 1 个 SCROLL", 1, events.size());

        ScenePointerEvent e = events.get(0);
        Assert.assertEquals("action=SCROLL", ScenePointerAction.SCROLL, e.getAction());
        Assert.assertTrue("向下滚 wheelDelta 应为负，实际=" + e.getWheelDelta(),
                e.getWheelDelta() < 0);
        Assert.assertEquals("1.0 notch × 120 = -120", -120, e.getWheelDelta());
    }

    /**
     * J4：速度无关性测试——不同 scrollAccum 幅度都应产非零 wheelDelta。
     *
     * <p>Oracle 建议补全项：验证保底步长算法在极小差分（触控板）和正常差分（传统滚轮）
     * 下都能产非零事件，不被取整丢失。极小差分 0.5 × 120 = 60（非零），
     * 大差分 5.0 × 120 = 600（非零），两者都应触发 SCROLL。</p>
     */
    @Test
    public void j4_variousScrollMagnitudesAllProduceNonZeroWheelDelta() {
        // 子用例 1：小差分 0.5（触控板轻滑）
        drainFrame(); // 基线
        reader.advanceTime();
        reader.scrollAccum = 0.5;
        List<ScenePointerEvent> e1 = drainPointerEvents();
        Assert.assertEquals("小差分 0.5 应产 1 个 SCROLL", 1, e1.size());
        Assert.assertTrue("小差分 wheelDelta 应非零，实际=" + e1.get(0).getWheelDelta(),
                e1.get(0).getWheelDelta() != 0);
        Assert.assertEquals("0.5 × 120 = 60", 60, e1.get(0).getWheelDelta());

        // 子用例 2：大差分 5.0（快速滚轮）
        reader.advanceTime();
        reader.scrollAccum = 5.0; // 相对上一帧 +4.5
        List<ScenePointerEvent> e2 = drainPointerEvents();
        Assert.assertEquals("大差分应产 1 个 SCROLL", 1, e2.size());
        Assert.assertTrue("大差分 wheelDelta 应非零，实际=" + e2.get(0).getWheelDelta(),
                e2.get(0).getWheelDelta() != 0);
        // 5.0 - 0.5 = 4.5，4.5 × 120 = 540
        Assert.assertEquals("4.5 × 120 = 540", 540, e2.get(0).getWheelDelta());
    }

    /**
     * J5：极小差分保底步长测试——scrollDiff 极小（× 120 取整为 0）时保底 1px。
     *
     * <p>Oracle 更优方案核心：触控板单帧差分可能 0.001 级别，× 120 = 0.12 取整为 0，
     * 传统算法会丢失事件。保底 max(1, ...) 确保极小差分也产 1px 滚动。</p>
     */
    @Test
    public void j5_tinyScrollDiffGuaranteesMinStepOne() {
        drainFrame(); // 基线
        reader.advanceTime();
        // 0.001 × 120 = 0.12 → round = 0 → max(1, 0) = 1（保底）
        reader.scrollAccum = 0.001;

        List<ScenePointerEvent> events = drainPointerEvents();
        Assert.assertEquals("极小差分也应产 1 个 SCROLL（保底步长）", 1, events.size());
        Assert.assertEquals("保底 wheelDelta=1", 1, events.get(0).getWheelDelta());
    }

    /**
     * J6：getDWheel fallback 测试——scrollAccum 恒为 0 但 dWheelDelta 返回非零，
     * SCROLL 事件仍应被 push。
     *
     * <p>Bug1 真根因场景：真机 totalScrollAmount 字段不更新，scrollAccum() 差分恒为 0，
     * 路径 1 失效。此时 fallback 读 getDWheel()（破坏性读取），若返回非零则路径 2 生效。
     * mock scrollAccum 恒为 0、dWheelDelta 注入非零值，断言 SCROLL 仍被 push。</p>
     */
    @Test
    public void j6_dWheelFallbackWhenScrollAccumStuckAtZero() {
        drainFrame(); // 基线 (scrollAccum=0, dWheelDelta=0)
        reader.advanceTime();

        // 真机场景模拟：scrollAccum 恒为 0（字段不更新），但 getDWheel 返回非零
        reader.scrollAccum = 0.0; // 路径 1 差分 = 0
        reader.dWheelDelta = 120; // 路径 2 fallback 增量

        List<ScenePointerEvent> events = drainPointerEvents();
        Assert.assertEquals("fallback 路径应产 1 个 SCROLL", 1, events.size());

        ScenePointerEvent e = events.get(0);
        Assert.assertEquals("action=SCROLL", ScenePointerAction.SCROLL, e.getAction());
        Assert.assertEquals("wheelDelta 应来自 dWheelDelta", 120, e.getWheelDelta());
    }

    /**
     * J7：getDWheel fallback 方向测试——dWheelDelta 为负时 wheelDelta 也为负。
     */
    @Test
    public void j7_dWheelFallbackNegativeDirection() {
        drainFrame(); // 基线
        reader.advanceTime();

        reader.scrollAccum = 0.0;
        reader.dWheelDelta = -120; // 向下滚

        List<ScenePointerEvent> events = drainPointerEvents();
        Assert.assertEquals("应产 1 个 SCROLL", 1, events.size());
        Assert.assertTrue("向下滚 wheelDelta 应为负，实际=" + events.get(0).getWheelDelta(),
                events.get(0).getWheelDelta() < 0);
        Assert.assertEquals("-120", -120, events.get(0).getWheelDelta());
    }

    /**
     * J8：路径 1 优先于路径 2——scrollAccum 差分非零时，不读 getDWheel（避免破坏性清零）。
     *
     * <p>守约：getDWheel() 是破坏性读取，路径 1 有效时不应调用路径 2，
     * 避免每帧清零影响旧层 UiInputService 消费同一事件队列。
     * 验证：scrollAccum 差分非零 + dWheelDelta 也设非零，wheelDelta 应来自路径 1（按 × 120 算），
     * 而非直接用 dWheelDelta 值。</p>
     */
    @Test
    public void j8_path1TakesPrecedenceOverPath2() {
        drainFrame(); // 基线
        reader.advanceTime();

        reader.scrollAccum = 1.0; // 路径 1 差分 = 1.0 → 120
        reader.dWheelDelta = 999; // 路径 2 不应被采用（否则会拿到 999）

        List<ScenePointerEvent> events = drainPointerEvents();
        Assert.assertEquals("应产 1 个 SCROLL", 1, events.size());
        // 路径 1 生效：1.0 × 120 = 120，而非路径 2 的 999
        Assert.assertEquals("路径 1 优先，wheelDelta=120 而非 999", 120, events.get(0).getWheelDelta());
    }

    // ==================== 组 L：修饰键 ====================

    @Test
    public void l1_controlDownReflectedInEvent() {
        drainFrame(); // 基线
        reader.advanceTime();

        reader.control = true;
        reader.mouseX = 10;

        List<ScenePointerEvent> events = drainPointerEvents();
        Assert.assertTrue("应至少产 1 个事件", events.size() >= 1);

        ScenePointerEvent e = events.get(0);
        Assert.assertTrue("isControlDown=true", e.isControlDown());
        Assert.assertFalse("isShiftDown=false", e.isShiftDown());
    }

    @Test
    public void l2_shiftDownReflectedInEvent() {
        drainFrame(); // 基线
        reader.advanceTime();

        reader.shift = true;
        reader.mouseX = 20;

        List<ScenePointerEvent> events = drainPointerEvents();
        Assert.assertTrue("应至少产 1 个事件", events.size() >= 1);

        ScenePointerEvent e = events.get(0);
        Assert.assertTrue("isShiftDown=true", e.isShiftDown());
        Assert.assertFalse("isControlDown=false", e.isControlDown());
    }

    // ==================== 组 M：封板 ====================

    @Test
    public void m1_multiEventFrameCorrectlyClassified() {
        drainFrame(); // 基线 (0,0)
        reader.advanceTime();

        // 同帧：移动 + 左键按下 + 滚轮
        reader.mouseX = 100;
        reader.mouseY = 50;
        reader.buttonLeft = true;
        reader.scrollAccum = 0.5;

        SceneInputFrame frame = drainFrame();
        List<ScenePointerEvent> pointerEvents = frame.getPointerEvents();

        Assert.assertTrue("指针事件列表不应为空", pointerEvents.size() >= 2);
        Assert.assertTrue("键盘事件列表应为空", frame.getKeyEvents().isEmpty());
        Assert.assertTrue("文本事件列表应为空", frame.getTextEvents().isEmpty());
    }

    @Test
    public void m2_noChangeFrameIsEmpty() {
        drainFrame(); // 基线
        reader.advanceTime();

        // 无变化
        SceneInputFrame frame = drainFrame();
        Assert.assertTrue("isEmpty=true", frame.isEmpty());
    }

    // ==================== 附加：按钮枚举映射 ====================

    @Test
    public void shouldMapAllButtonCodes() {
        Assert.assertEquals("0→LEFT", SceneMouseButton.LEFT, LwjglInputSource.mapButtonCode(0));
        Assert.assertEquals("1→RIGHT", SceneMouseButton.RIGHT, LwjglInputSource.mapButtonCode(1));
        Assert.assertEquals("2→MIDDLE", SceneMouseButton.MIDDLE, LwjglInputSource.mapButtonCode(2));
        Assert.assertEquals("3→BUTTON_4", SceneMouseButton.BUTTON_4, LwjglInputSource.mapButtonCode(3));
        Assert.assertEquals("4→BUTTON_5", SceneMouseButton.BUTTON_5, LwjglInputSource.mapButtonCode(4));
        Assert.assertEquals("99→NONE", SceneMouseButton.NONE, LwjglInputSource.mapButtonCode(99));
    }

    // ==================== 附加：首帧后正常产事件 ====================

    @Test
    public void shouldProduceEventsAfterBaseline() {
        drainFrame(); // 基线
        reader.advanceTime();

        reader.mouseX = 42;
        List<ScenePointerEvent> events = drainPointerEvents();
        Assert.assertEquals("首帧后的正常帧应产事件", 1, events.size());
    }

    // ==================== I4b 组 K：pushKeyTyped ====================

    /** drain 一帧并返回键盘事件列表 */
    private List<SceneKeyEvent> drainKeyEvents() {
        SceneInputFrame frame = source.drainFrame();
        return frame.getKeyEvents();
    }

    /** drain 一帧并返回文本事件列表 */
    private List<SceneTextEvent> drainTextEvents() {
        SceneInputFrame frame = source.drainFrame();
        return frame.getTextEvents();
    }

    @Test
    public void k1_pushKeyTypedProducesKeyEvent() {
        // 首帧建立基线
        drainFrame();
        reader.advanceTime();

        // push 可打印字符 'a' (KEY_A=30)
        source.pushKeyTyped('a', 30, reader.nowNanos());
        reader.advanceTime();

        SceneInputFrame frame = drainFrame();
        List<SceneKeyEvent> keyEvents = frame.getKeyEvents();
        List<SceneTextEvent> textEvents = frame.getTextEvents();

        Assert.assertEquals("应产 1 个 KEY 事件", 1, keyEvents.size());
        SceneKeyEvent keyEvent = keyEvents.get(0);
        Assert.assertEquals("key=A", SceneKey.KEY_A, keyEvent.getKey());
        Assert.assertEquals("action=PRESSED", SceneKeyAction.PRESSED, keyEvent.getAction());
        Assert.assertEquals("nativeKeyCode=30", 30, keyEvent.getNativeKeyCode());

        Assert.assertEquals("应产 1 个 TEXT_INPUT 事件", 1, textEvents.size());
        SceneTextEvent textEvent = textEvents.get(0);
        Assert.assertEquals("text='a'", "a", textEvent.getText());
    }

    @Test
    public void k2_nonPrintableCharOnlyProducesKeyEvent() {
        drainFrame();
        reader.advanceTime();

        // push KEY_UP (200)，无对应可打印字符（typedChar='\0'）
        source.pushKeyTyped('\0', 200, reader.nowNanos());
        reader.advanceTime();

        SceneInputFrame frame = drainFrame();
        List<SceneKeyEvent> keyEvents = frame.getKeyEvents();
        List<SceneTextEvent> textEvents = frame.getTextEvents();

        Assert.assertEquals("应产 1 个 KEY 事件", 1, keyEvents.size());
        Assert.assertEquals("key=ARROW_UP", SceneKey.ARROW_UP, keyEvents.get(0).getKey());

        Assert.assertTrue("不应产 TEXT_INPUT 事件（字符不可打印）", textEvents.isEmpty());
    }

    @Test
    public void k3_enterKeyWithNoCharProducesOnlyKeyEvent() {
        drainFrame();
        reader.advanceTime();

        // ENTER (28)，typedChar 通常为 '\r' (0x0D)，不可打印
        source.pushKeyTyped('\r', 28, reader.nowNanos());
        reader.advanceTime();

        SceneInputFrame frame = drainFrame();
        List<SceneKeyEvent> keyEvents = frame.getKeyEvents();
        List<SceneTextEvent> textEvents = frame.getTextEvents();

        Assert.assertEquals("应产 KEY 事件", 1, keyEvents.size());
        Assert.assertEquals("key=ENTER", SceneKey.ENTER, keyEvents.get(0).getKey());
        Assert.assertTrue("\\r 不可打印，不应产 TEXT", textEvents.isEmpty());
    }

    @Test
    public void k4_modsFromReaderReflectedInKeyEvent() {
        drainFrame();
        reader.advanceTime();

        // 设置修饰键态
        reader.control = true;
        reader.shift = true;

        source.pushKeyTyped('X', 45, reader.nowNanos()); // KEY_X
        reader.advanceTime();

        List<SceneKeyEvent> keyEvents = drainKeyEvents();
        Assert.assertEquals(1, keyEvents.size());

        SceneKeyEvent keyEvent = keyEvents.get(0);
        Assert.assertTrue("isControlDown=true", keyEvent.isControlDown());
        Assert.assertTrue("isShiftDown=true", keyEvent.isShiftDown());
        Assert.assertFalse("isAltDown=false", keyEvent.isAltDown());
        Assert.assertFalse("isMetaDown=false", keyEvent.isMetaDown());
    }

    @Test
    public void k5_unknownKeyCodeReturnsUnknown() {
        drainFrame();
        reader.advanceTime();

        // 使用未映射的键码 999
        source.pushKeyTyped('\0', 999, reader.nowNanos());
        reader.advanceTime();

        List<SceneKeyEvent> keyEvents = drainKeyEvents();
        Assert.assertEquals(1, keyEvents.size());
        Assert.assertEquals("key=UNKNOWN", SceneKey.UNKNOWN, keyEvents.get(0).getKey());
        Assert.assertEquals("nativeKeyCode 透传", 999, keyEvents.get(0).getNativeKeyCode());
    }

    @Test
    public void k6_multiplePushesBeforeDrainAllInFrame() {
        drainFrame();
        reader.advanceTime();

        // 先在 reader 推进时间，push 多个字符
        long t1 = reader.nowNanos();
        source.pushKeyTyped('a', 30, t1);
        reader.advanceTime();
        long t2 = reader.nowNanos();
        source.pushKeyTyped('b', 48, t2);
        reader.advanceTime();
        long t3 = reader.nowNanos();
        source.pushKeyTyped('c', 46, t3);
        reader.advanceTime();

        SceneInputFrame frame = drainFrame();
        List<SceneKeyEvent> keyEvents = frame.getKeyEvents();
        List<SceneTextEvent> textEvents = frame.getTextEvents();

        Assert.assertEquals("应产 3 个 KEY 事件", 3, keyEvents.size());
        Assert.assertEquals("KEYS[0]=A", SceneKey.KEY_A, keyEvents.get(0).getKey());
        Assert.assertEquals("KEYS[1]=B", SceneKey.KEY_B, keyEvents.get(1).getKey());
        Assert.assertEquals("KEYS[2]=C", SceneKey.KEY_C, keyEvents.get(2).getKey());

        Assert.assertEquals("同帧 TEXT_INPUT 应合并为 1 个事件", 1, textEvents.size());
        Assert.assertEquals("abc", textEvents.get(0).getText());
    }

    @Test
    public void k7_keyTypedAndPointerInSameFrame() {
        drainFrame(); // 首帧基线
        reader.advanceTime();

        // pushKeyTyped 先入缓冲
        source.pushKeyTyped('x', 45, reader.nowNanos()); // KEY_X
        reader.advanceTime();

        // 再修改 reader 当前态模拟鼠标移动，drainFrame 内 poll 会再 push pointer
        reader.mouseX = 100;
        reader.mouseY = 50;

        SceneInputFrame frame = drainFrame();
        List<SceneKeyEvent> keyEvents = frame.getKeyEvents();
        List<SceneTextEvent> textEvents = frame.getTextEvents();
        List<ScenePointerEvent> pointerEvents = frame.getPointerEvents();

        Assert.assertEquals("1 个 KEY", 1, keyEvents.size());
        Assert.assertEquals("1 个 TEXT", 1, textEvents.size());
        Assert.assertEquals("1 个 MOVE", 1, pointerEvents.size());
    }

    @Test
    public void k8_textEventDoesNotCarryMods() {
        // ofText 恒不带修饰键（I1 既定契约）
        drainFrame();
        reader.advanceTime();

        // 即使 reader 有修饰键态，TEXT 事件的 mods 也不受影响
        reader.control = true;
        reader.shift = true;
        reader.alt = true;

        source.pushKeyTyped('Z', 44, reader.nowNanos());

        SceneInputFrame frame = drainFrame();
        List<SceneTextEvent> textEvents = frame.getTextEvents();
        Assert.assertEquals(1, textEvents.size());

        // SceneTextEvent 本身不暴露 mods（结构上无该字段），此条确保编译正确即可。
        Assert.assertEquals("文本内容正确", "Z", textEvents.get(0).getText());
    }

    @Test
    public void k9_backspaceWithDelCharProducesOnlyKeyEvent() {
        drainFrame();
        reader.advanceTime();

        // BACKSPACE (14)，typedChar 通常为 DEL 0x7F（不可打印）
        source.pushKeyTyped((char) 0x7F, 14, reader.nowNanos());
        reader.advanceTime();

        SceneInputFrame frame = drainFrame();
        List<SceneKeyEvent> keyEvents = frame.getKeyEvents();
        List<SceneTextEvent> textEvents = frame.getTextEvents();

        Assert.assertEquals("应产 1 个 KEY 事件", 1, keyEvents.size());
        Assert.assertEquals("key=BACKSPACE", SceneKey.BACKSPACE, keyEvents.get(0).getKey());
        Assert.assertTrue("DEL (0x7F) 不可打印，不应产 TEXT", textEvents.isEmpty());
    }

    // ==================== I4d 组 N：失焦边沿合成 CANCEL ====================

    /**
     * N1：windowFocused true→false（失焦边沿）→ drainFrame 产出 CANCEL pointer 事件。
     */
    @Test
    public void n1_focusLostProducesCancelEvent() {
        drainFrame(); // 首帧基线，windowFocused=true
        reader.advanceTime();

        // 失焦
        reader.windowFocused = false;
        reader.mouseX = 100;
        reader.mouseY = 200;

        List<ScenePointerEvent> events = drainPointerEvents();
        // 坐标变化可能产 MOVE + CANCEL，也可能只有 CANCEL（取决于 baseline 坐标）
        // 但至少应有一个 CANCEL 事件
        boolean hasCancel = false;
        for (ScenePointerEvent e : events) {
            if (e.getAction() == ScenePointerAction.CANCEL) {
                hasCancel = true;
                // 坐标用粘滞位置
                Assert.assertEquals("CANCEL logicalX=curX", 100, e.getLogicalX());
                Assert.assertEquals("CANCEL logicalY=curY", 200, e.getLogicalY());
                Assert.assertFalse("CANCEL mods 全 false", e.isControlDown());
                Assert.assertFalse("CANCEL mods 全 false", e.isShiftDown());
                Assert.assertFalse("CANCEL mods 全 false", e.isAltDown());
                Assert.assertFalse("CANCEL mods 全 false", e.isMetaDown());
            }
        }
        Assert.assertTrue("失焦帧应产出 CANCEL 事件", hasCancel);
    }

    /**
     * N2：windowFocused true→true（焦点未变）→ 不产 CANCEL。
     */
    @Test
    public void n2_focusUnchangedTrueDoesNotProduceCancel() {
        drainFrame(); // 基线 windowFocused=true
        reader.advanceTime();
        reader.mouseX = 50;
        reader.windowFocused = true; // 保持焦点

        List<ScenePointerEvent> events = drainPointerEvents();
        for (ScenePointerEvent e : events) {
            Assert.assertNotEquals("焦点未变不应产 CANCEL",
                    ScenePointerAction.CANCEL, e.getAction());
        }
    }

    /**
     * N3：windowFocused false→false（持续失焦）→ 不产 CANCEL。
     */
    @Test
    public void n3_focusUnchangedFalseDoesNotProduceCancel() {
        drainFrame(); // 基线 windowFocused=true
        reader.advanceTime();
        reader.windowFocused = false;
        drainFrame(); // 失焦帧（产 CANCEL）
        reader.advanceTime();

        // 下一帧仍失焦
        reader.mouseX = 60;
        reader.windowFocused = false;
        List<ScenePointerEvent> events = drainPointerEvents();
        for (ScenePointerEvent e : events) {
            Assert.assertNotEquals("持续失焦不应再产 CANCEL",
                    ScenePointerAction.CANCEL, e.getAction());
        }
    }

    /**
     * N4：首帧基线 windowFocused=false 不误产 CANCEL（首帧不产任何事件）。
     */
    @Test
    public void n4_firstFrameBaselineWithFocusFalseDoesNotProduceCancel() {
        // 重建 source，首帧即 windowFocused=false
        MockPlatformStateReader reader2 = new MockPlatformStateReader();
        reader2.windowFocused = false;
        LwjglInputSource source2 = new LwjglInputSource(reader2);

        SceneInputFrame frame = source2.drainFrame();
        Assert.assertTrue("首帧基线应返回空（即使 windowFocused=false）", frame.isEmpty());
        Assert.assertTrue("首帧基线应无 pointer 事件", frame.getPointerEvents().isEmpty());
    }

    /**
     * N5：失焦合成 CANCEL 在多事件帧中最后到达。
     */
    @Test
    public void n5_cancelArrivesLastInMultiEventFrame() {
        drainFrame(); // 基线 (0,0), windowFocused=true
        reader.advanceTime();

        // 同帧：移动 + 失焦
        reader.mouseX = 70;
        reader.mouseY = 80;
        reader.windowFocused = false;
        reader.buttonLeft = true; // 同时按下

        List<ScenePointerEvent> events = drainPointerEvents();
        // 顺序应为：MOVE → BUTTON_DOWN → CANCEL（CANCEL 在差分 push 之后、封板之前）
        Assert.assertTrue("至少 2 个事件（MOVE + BUTTON_DOWN + CANCEL）", events.size() >= 2);

        // 最后一个事件应为 CANCEL
        ScenePointerEvent last = events.get(events.size() - 1);
        Assert.assertEquals("最后一个事件应为 CANCEL", ScenePointerAction.CANCEL, last.getAction());
    }
}
