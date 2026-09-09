package club.heiqi.uilib.ui.scene.host.lwjgl;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneTextInput;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/** 使用真实宿主输入源封板再 route，避免只有 synthetic KEY_UP 的测试掩盖游戏缺失松键。 */
public class LwjglButtonKeyboardFeedbackTest {
    private MockPlatformStateReader reader;
    private LwjglInputSource source;
    private SceneRuntime runtime;
    private SceneNode root;
    private SceneNode button;
    private ReadableSignal<Boolean> pressed;
    private AtomicInteger clicks;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        reader = new MockPlatformStateReader();
        source = new LwjglInputSource(reader);
        runtime = new SceneRuntime(new FixedTextMeasurer(8, 16));
        root = new SceneNode();
        clicks = new AtomicInteger();
        button = runtime.mount(root, SceneButton.create(runtime,
                new SceneButton.Props(Signal.create("OK"), Signal.create(true), clicks::incrementAndGet))).getRoot();
        pressed = runtime.interactionState(button).pressed();
        runtime.flush();
        runtime.requestFocus(button);
        runtime.flush();
        source.drainFrame();
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    @Test
    public void enterHoldRepeatAndReleaseReachButtonWithoutExtraActions() {
        reader.keysDown.add(28);
        source.pushKeyTyped((char) 13, 28, reader.nowNanos());
        SceneInputFrame down = drainAndRoute();
        Assert.assertEquals(1, down.getKeyEvents().size());
        Assert.assertTrue(pressed.get());
        Assert.assertEquals(SceneThemes.DEFAULT.surface(SceneTheme.Role.BUTTON_STANDARD)
                .getPressed().getTint(), button.getBackgroundColor());
        Assert.assertEquals(1, clicks.get());
        Assert.assertTrue("持续按住不合成新 DOWN 或 UP", drainAndRoute().getKeyEvents().isEmpty());
        source.pushKeyTyped((char) 13, 28, reader.nowNanos());
        drainAndRoute();
        Assert.assertEquals("重复回调仍按现有规则激活", 2, clicks.get());
        reader.keysDown.remove(28);
        SceneInputFrame up = drainAndRoute();
        Assert.assertEquals(1, up.getKeyEvents().size());
        Assert.assertEquals(SceneKeyAction.RELEASED, up.getKeyEvents().get(0).getAction());
        Assert.assertFalse(pressed.get());
        Assert.assertEquals(SceneThemes.DEFAULT.surface(SceneTheme.Role.BUTTON_STANDARD)
                .getIdle().getTint(), button.getBackgroundColor());
        Assert.assertEquals(2, clicks.get());
        Assert.assertTrue("释放只发一次", drainAndRoute().getKeyEvents().isEmpty());
    }

    @Test
    public void shortTapBeforeFirstDrainKeepsSingleClickAndEndsReleased() {
        source = new LwjglInputSource(reader); // 尚未建立指针基线也必须补齐松键
        source.pushKeyTyped((char) 13, 28, reader.nowNanos());
        SceneInputFrame frame = drainAndRoute(); // 物理态已经松开
        Assert.assertEquals(2, frame.getKeyEvents().size());
        Assert.assertEquals(SceneKeyAction.PRESSED, frame.getKeyEvents().get(0).getAction());
        Assert.assertEquals(SceneKeyAction.RELEASED, frame.getKeyEvents().get(1).getAction());
        Assert.assertEquals(1, clicks.get());
        Assert.assertFalse(pressed.get());
    }

    @Test
    public void externalTextSpaceActivatesButtonAndStillUsesSingleTextChannel() {
        source.setExternalTextMode(true);
        reader.keysDown.add(57);
        source.pushKeyTyped(' ', 57, reader.nowNanos());
        source.pushText(" ", reader.nowNanos());
        SceneInputFrame frame = drainAndRoute();
        Assert.assertEquals(1, frame.getKeyEvents().size());
        Assert.assertEquals(SceneKey.SPACE, frame.getKeyEvents().get(0).getKey());
        Assert.assertEquals(1, frame.getTextEvents().size());
        Assert.assertEquals(" ", frame.getTextEvents().get(0).getText());
        Assert.assertEquals(1, clicks.get());
        Assert.assertTrue(pressed.get());
        reader.keysDown.remove(57);
        drainAndRoute();
        Assert.assertFalse(pressed.get());
    }

    @Test
    public void externalSpaceDoesNotDoubleInsertOrPressTextInput() {
        source.setExternalTextMode(true);
        Signal<String> text = Signal.create("");
        AtomicInteger changes = new AtomicInteger();
        SceneNode input = runtime.mount(root, SceneTextInput.create(runtime,
                SceneTextInput.Props.builder(text).onChange(next -> {
                    changes.incrementAndGet();
                    text.set(next);
                }).build())).getRoot();
        ReadableSignal<Boolean> inputPressed = runtime.interactionState(input).pressed();
        runtime.flush();
        runtime.requestFocus(input);
        reader.keysDown.add(57);
        source.pushKeyTyped(' ', 57, reader.nowNanos());
        source.pushText(" ", reader.nowNanos());
        drainAndRoute();
        Assert.assertEquals("只由 TEXT 通道插入一次空格", " ", text.get());
        Assert.assertEquals(1, changes.get());
        Assert.assertFalse(inputPressed.get());
        Assert.assertFalse(pressed.get());
        Assert.assertEquals(0, clicks.get());
        reader.keysDown.remove(57);
        drainAndRoute();
        Assert.assertEquals(1, changes.get());
        Assert.assertEquals(" ", text.get());
    }

    @Test
    public void focusLossClearsHostReleaseTrackingAndRouterPress() {
        reader.keysDown.add(28);
        source.pushKeyTyped((char) 13, 28, reader.nowNanos());
        drainAndRoute();
        Assert.assertTrue(pressed.get());
        reader.windowFocused = false; // 失焦时即便平台键态尚未清空，也必须清理
        drainAndRoute();
        Assert.assertFalse(pressed.get());
        reader.windowFocused = true;
        Assert.assertTrue("失焦已终结旧按键，不得恢复旧 feedback", drainAndRoute().getKeyEvents().isEmpty());
        Assert.assertFalse(pressed.get());
        Assert.assertEquals(1, clicks.get());
    }

    @Test
    public void physicalHoldWithoutCallbackDoesNotCreateActivationOrRelease() {
        reader.keysDown.add(28);
        Assert.assertTrue(drainAndRoute().getKeyEvents().isEmpty());
        reader.keysDown.remove(28);
        Assert.assertTrue(drainAndRoute().getKeyEvents().isEmpty());
        Assert.assertEquals(0, clicks.get());
        Assert.assertFalse(pressed.get());
    }

    private SceneInputFrame drainAndRoute() {
        reader.advanceTime();
        SceneInputFrame frame = source.drainFrame();
        runtime.route(root, frame, 0, 0);
        runtime.flush();
        return frame;
    }
}
