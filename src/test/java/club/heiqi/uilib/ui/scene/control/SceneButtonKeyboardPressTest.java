package club.heiqi.uilib.ui.scene.control;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/** 键盘反馈消费同一个 pressed signal，动作仍按每次 KEY_DOWN 立即触发。 */
public class SceneButtonKeyboardPressTest {
    private SceneInteractionHarness harness;
    private SceneRuntime runtime;
    private SceneNode root;
    private SceneNode button;
    private Signal<Boolean> enabled;
    private ReadableSignal<Boolean> pressed;
    private MountHandle mount;
    private AtomicInteger clicks;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create();
        runtime = harness.getRuntime();
        root = new SceneNode();
        enabled = Signal.create(true);
        clicks = new AtomicInteger();
        mount = runtime.mount(root, SceneButton.create(runtime,
                new SceneButton.Props(Signal.create("OK"), enabled, clicks::incrementAndGet)));
        button = mount.getRoot();
        pressed = runtime.interactionState(button).pressed();
        runtime.flush();
        harness.mountRoot(root, 200, 100);
        runtime.requestFocus(button);
        runtime.flush();
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    @Test
    public void enterAndSpacePaintPressedAndKeepDownRepeatActivation() {
        for (SceneKey key : new SceneKey[] {SceneKey.ENTER, SceneKey.SPACE}) {
            int before = clicks.get();
            route(key(key, SceneKeyAction.PRESSED));
            Assert.assertEquals("DOWN 同步触发动作，无需等松键或 flush", before + 1, clicks.get());
            runtime.flush();
            Assert.assertTrue(pressed.get());
            Assert.assertEquals(SceneThemes.DEFAULT.surface(SceneTheme.Role.BUTTON_STANDARD)
                    .getPressed().getTint(), button.getBackgroundColor());
            route(key(key, SceneKeyAction.REPEATED));
            runtime.flush();
            Assert.assertEquals("重复 KEY_DOWN 保持既有动作次数", before + 2, clicks.get());
            Assert.assertTrue(pressed.get());
            route(key(key, SceneKeyAction.RELEASED));
            runtime.flush();
            Assert.assertFalse(pressed.get());
            Assert.assertEquals(SceneThemes.DEFAULT.surface(SceneTheme.Role.BUTTON_STANDARD)
                    .getIdle().getTint(), button.getBackgroundColor());
            Assert.assertEquals("松键不再触发动作", before + 2, clicks.get());
        }
    }

    @Test
    public void releasingOneActivationKeyKeepsOtherKeyPressed() {
        route(key(SceneKey.ENTER, SceneKeyAction.PRESSED), key(SceneKey.SPACE, SceneKeyAction.PRESSED));
        runtime.flush();
        route(key(SceneKey.ENTER, SceneKeyAction.RELEASED));
        runtime.flush();
        Assert.assertTrue(pressed.get());
        route(key(SceneKey.SPACE, SceneKeyAction.RELEASED));
        runtime.flush();
        Assert.assertFalse(pressed.get());
    }

    @Test
    public void pointerAndKeyboardReleaseIndependently() {
        harness.press(button);
        route(key(SceneKey.ENTER, SceneKeyAction.PRESSED));
        route(key(SceneKey.ENTER, SceneKeyAction.RELEASED));
        runtime.flush();
        Assert.assertTrue("松键保留鼠标按压", pressed.get());
        harness.release(button);
        Assert.assertFalse(pressed.get());

        route(key(SceneKey.SPACE, SceneKeyAction.PRESSED));
        harness.press(button);
        harness.release(button);
        Assert.assertTrue("松鼠标保留键盘按压", pressed.get());
        route(key(SceneKey.SPACE, SceneKeyAction.RELEASED));
        runtime.flush();
        Assert.assertFalse(pressed.get());
    }

    @Test
    public void focusLossClearsKeyboardButKeepsPointerPress() {
        harness.press(button);
        route(key(SceneKey.ENTER, SceneKeyAction.PRESSED));
        runtime.requestFocus(root);
        runtime.flush();
        Assert.assertTrue(pressed.get());
        harness.release(button);
        Assert.assertFalse(pressed.get());
        runtime.requestFocus(button);
        runtime.flush();
        Assert.assertFalse("重新聚焦不恢复旧按键", pressed.get());
    }

    @Test
    public void keyHandlerFocusTransferDoesNotResurrectPressAfterDispatch() {
        runtime.on(button, SceneEventType.KEY_DOWN, (ev, ctx) -> runtime.requestFocus(root));
        route(key(SceneKey.ENTER, SceneKeyAction.PRESSED));
        runtime.flush();
        Assert.assertFalse(pressed.get());
        Assert.assertSame(root, runtime.getFocusedNode());
        Assert.assertEquals(1, clicks.get());
    }

    @Test
    public void disableDuringDownAndReenableDoesNotRestoreHeldKeys() {
        runtime.on(button, SceneEventType.KEY_DOWN, (ev, ctx) -> enabled.set(false));
        route(key(SceneKey.SPACE, SceneKeyAction.PRESSED));
        runtime.flush();
        Assert.assertFalse(pressed.get());
        Assert.assertNull(runtime.getFocusedNode());
        enabled.set(true);
        runtime.flush();
        runtime.requestFocus(button);
        runtime.flush();
        Assert.assertFalse(pressed.get());
        route(key(SceneKey.SPACE, SceneKeyAction.RELEASED));
        runtime.flush();
        Assert.assertFalse(pressed.get());
        Assert.assertEquals(1, clicks.get());
    }

    @Test
    public void downThenReleaseAndEnabledRoundTripBeforeFlushLeavesNoStalePress() {
        route(key(SceneKey.ENTER, SceneKeyAction.PRESSED));
        enabled.set(false);
        route(key(SceneKey.ENTER, SceneKeyAction.RELEASED));
        enabled.set(true);
        runtime.flush();
        Assert.assertFalse(pressed.get());
        Assert.assertEquals(1, clicks.get());
    }

    @Test
    public void unmountClearsPreviouslyPublishedPressAndRegistration() {
        route(key(SceneKey.ENTER, SceneKeyAction.PRESSED));
        runtime.flush();
        Assert.assertTrue(pressed.get());
        mount.dispose();
        runtime.flush();
        Assert.assertFalse(pressed.get());
        Assert.assertNull(runtime.getFocusedNode());
        route(key(SceneKey.ENTER, SceneKeyAction.REPEATED));
        runtime.flush();
        Assert.assertFalse(pressed.get());
        Assert.assertEquals(1, clicks.get());
    }

    @Test
    public void unmountInsideKeyDownDoesNotResurrectPress() {
        runtime.on(button, SceneEventType.KEY_DOWN, (ev, ctx) -> mount.dispose());
        route(key(SceneKey.ENTER, SceneKeyAction.PRESSED));
        runtime.flush();
        Assert.assertFalse(pressed.get());
        Assert.assertEquals(1, clicks.get());
    }

    @Test
    public void cancelClearsKeyboardWithoutPointerAndAlsoSameFrameDown() {
        route(key(SceneKey.SPACE, SceneKeyAction.PRESSED));
        runtime.flush();
        route(cancel());
        runtime.flush();
        Assert.assertFalse(pressed.get());
        route(key(SceneKey.ENTER, SceneKeyAction.PRESSED), cancel());
        runtime.flush();
        Assert.assertFalse("pointer 先派发不应使同帧 KEY_DOWN 在 cancel 后残留", pressed.get());
        Assert.assertEquals("取消不改变既有 KEY_DOWN 动作次数", 2, clicks.get());
    }

    @Test
    public void cancelClearsCombinedPressEvenWhenCancelHandlerThrows() {
        harness.press(button);
        route(key(SceneKey.ENTER, SceneKeyAction.PRESSED));
        runtime.flush();
        runtime.on(button, SceneEventType.POINTER_CANCEL, (ev, ctx) -> {
            throw new IllegalStateException("cancel failure");
        });
        try {
            route(cancel());
            Assert.fail("应透传 handler 异常");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("cancel failure", expected.getMessage());
        }
        runtime.flush();
        Assert.assertFalse(pressed.get());
    }

    @Test
    public void ordinaryFocusedNodeReceivesKeysWithoutButtonPressState() {
        ReadableSignal<Boolean> otherPressed = runtime.interactionState(root).pressed();
        AtomicInteger keyDowns = new AtomicInteger();
        runtime.on(root, SceneEventType.KEY_DOWN, (ev, ctx) -> keyDowns.incrementAndGet());
        runtime.requestFocus(root);
        route(key(SceneKey.ENTER, SceneKeyAction.PRESSED), key(SceneKey.SPACE, SceneKeyAction.PRESSED));
        runtime.flush();
        Assert.assertFalse(otherPressed.get());
        Assert.assertFalse(pressed.get());
        Assert.assertEquals(2, keyDowns.get());
        Assert.assertEquals(0, clicks.get());
    }

    private void route(RawInputEvent... events) {
        InputFrameBuilder builder = new InputFrameBuilder(0, 0);
        for (RawInputEvent event : events) builder.push(event);
        runtime.route(root, builder.drainFrame(), 0, 0);
    }

    private static RawInputEvent key(SceneKey key, SceneKeyAction action) {
        return RawInputEvent.ofKey(key, action, false, false, false, false, 0, 0, 1000L);
    }

    private static RawInputEvent cancel() {
        return RawInputEvent.ofPointer(ScenePointerAction.CANCEL, 0, 0, SceneMouseButton.NONE,
                0, 0, 0, false, false, false, false, 1000L);
    }
}
