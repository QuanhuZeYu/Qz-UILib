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

    /**
     * {@code wait N} 在<b>语句位置</b>等待，后续语句落到 N 帧之后。
     *
     * <p>用「wait 之后还有动作」的脚本钉住位置语义 —— 若把 wait 放在末尾，两种实现恰好等价，
     * 测试就失去区分力（旧实现是「等整个队列耗尽后再空转」，独立复核被它静默误导过：
     * {@code …; wait 10; click} 里的 click 不会等，连续点击被退场动画吞掉）。</p>
     */
    @Test
    public void waitHoldsItsStatementPosition() {
        HeadlessInputSource source = new HeadlessInputSource(200, 100);
        HeadlessInputScript.apply(source.device(), "move 5 5; wait 2; move 9 9");

        Assert.assertEquals("wait 之前的动作在第一帧下发",
                1, source.drainFrame().getPointerEvents().size());
        // 关键断言：wait 让后续语句顺延。旧实现（等队列耗尽才空转）会把两个 move 挤进相邻两帧，
        // 即第二帧就能看到 move 9 9 —— 此处为空即证明 wait 生效在语句位置。
        Assert.assertTrue("wait 期间空转，后续语句不得提前下发", source.drainFrame().isEmpty());
        Assert.assertEquals("wait 之后的动作晚 2 帧下发",
                1, source.drainFrame().getPointerEvents().size());
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
