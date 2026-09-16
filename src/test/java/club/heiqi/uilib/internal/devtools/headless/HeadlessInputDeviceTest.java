package club.heiqi.uilib.internal.devtools.headless;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;

/**
 * headless 输入设备模型与输入脚本的帧划分契约。
 *
 * <p>这些断言替代一次实机验证：输入是「一次动作 → 哪一帧、带什么修饰键」的时序契约，
 * 时序错了 UI 会以极难排查的方式表现（点不中、修饰键丢失、动作被吞）。</p>
 */
public class HeadlessInputDeviceTest {

    /** click 必须跨帧：同一帧内按下并抬起对部分控件不可靠。 */
    @Test
    public void clickSpansTwoFrames() {
        HeadlessInputSource source = new HeadlessInputSource(200, 100);
        HeadlessInputScript.apply(source.device(), "move 30 40; frame; click");

        SceneInputFrame moved = source.drainFrame();
        Assert.assertEquals(1, moved.getPointerEvents().size());
        Assert.assertEquals(ScenePointerAction.MOVE, moved.getPointerEvents().get(0).getAction());
        Assert.assertEquals(30, moved.getPointerX());
        Assert.assertEquals(40, moved.getPointerY());

        SceneInputFrame pressed = source.drainFrame();
        Assert.assertEquals(ScenePointerAction.BUTTON_DOWN, pressed.getPointerEvents().get(0).getAction());

        SceneInputFrame released = source.drainFrame();
        Assert.assertEquals(ScenePointerAction.BUTTON_UP, released.getPointerEvents().get(0).getAction());
        Assert.assertEquals(SceneMouseButton.LEFT, released.getPointerEvents().get(0).getButton());
    }

    /** 按住修饰键后，后续事件必须携带该修饰（否则 Ctrl/Shift 组合静默失效）。 */
    @Test
    public void modifiersFollowHeldKeys() {
        HeadlessInputSource source = new HeadlessInputSource(200, 100);
        HeadlessInputScript.apply(source.device(), "keydown SHIFT_LEFT; frame; move 10 10");

        SceneInputFrame pressed = source.drainFrame();
        Assert.assertEquals(SceneKeyAction.PRESSED, pressed.getKeyEvents().get(0).getAction());

        SceneInputFrame moved = source.drainFrame();
        Assert.assertTrue("按住 Shift 之后的事件必须带 Shift 修饰", moved.isShiftDown());
    }

    /** wait 的语义是「先把动作发完，再空转」，不是「一上来就吞帧」。 */
    @Test
    public void waitRunsAfterQueuedActions() {
        HeadlessInputSource source = new HeadlessInputSource(200, 100);
        HeadlessInputScript.apply(source.device(), "move 5 5; wait 2");

        Assert.assertEquals(1, source.drainFrame().getPointerEvents().size());
        Assert.assertTrue(source.drainFrame().isEmpty());
        Assert.assertTrue(source.drainFrame().isEmpty());
    }

    /** 整串文本（外部接管 / IME 语义）在一帧内以单条 TEXT 事件交付。 */
    @Test
    public void composeDeliversWholeTextInOneFrame() {
        HeadlessInputSource source = new HeadlessInputSource(200, 100);
        HeadlessInputScript.apply(source.device(), "compose 中文输入");

        SceneInputFrame frame = source.drainFrame();
        Assert.assertEquals(1, frame.getTextEvents().size());
        Assert.assertEquals("中文输入", frame.getTextEvents().get(0).getText());
    }

    /** 脚本语法错误必须显式失败，而不是静默跳过整条语句。 */
    @Test
    public void scriptRejectsUnknownStatement() {
        HeadlessInputSource source = new HeadlessInputSource(200, 100);
        try {
            HeadlessInputScript.apply(source.device(), "teleport 1 2");
            Assert.fail("未知语句必须显式失败");
        } catch (HeadlessFailure failure) {
            Assert.assertTrue(failure.getMessage().contains("teleport"));
        }
    }
}
