package club.heiqi.uilib.ui.input;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.event.UiKeyCodes;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;

/**
 * `LwjglxPollingInputBackend` 的宿主字符输入兜底契约测试。
 */
public class LwjglxPollingInputBackendTest {

    /**
     * 验证可打印字符会由宿主 `keyTyped` 路径合成为文本输入事件。
     */
    @Test
    public void shouldCreateTextEventForPrintableHostTypedCharacter() {
        UiInputService inputService = createInputService();
        LwjglxPollingInputBackend backend = new LwjglxPollingInputBackend(inputService, false);

        backend.handleHostTypedCharacter('7', UiKeyCodes.KEY_7);

        UiInputFrame frame = inputService.collectFrame();
        Assert.assertEquals(1, frame.getTextEvents().size());
        UiTextInputEvent textEvent = frame.getTextEvents().get(0);
        Assert.assertEquals("7", textEvent.getText());
        Assert.assertTrue(textEvent.getTimeNanos() > 0L);
        Assert.assertTrue(frame.getKeyEvents().isEmpty());
    }

    /**
     * 验证 ESC、退格和回车等控制字符不会被错误合成为文本输入事件。
     */
    @Test
    public void shouldIgnoreControlHostTypedCharacters() {
        UiInputService inputService = createInputService();
        LwjglxPollingInputBackend backend = new LwjglxPollingInputBackend(inputService, false);

        backend.handleHostTypedCharacter((char) 0, UiKeyCodes.KEY_ESCAPE);
        backend.handleHostTypedCharacter('\b', UiKeyCodes.KEY_BACK);
        backend.handleHostTypedCharacter('\r', UiKeyCodes.KEY_RETURN);
        backend.handleHostTypedCharacter((char) 127, UiKeyCodes.KEY_DELETE);

        UiInputFrame frame = inputService.collectFrame();
        Assert.assertTrue(frame.getTextEvents().isEmpty());
        Assert.assertTrue(frame.getKeyEvents().isEmpty());
    }

    /**
     * 验证即时输入去重窗口会阻止同一字符再次进入 collected 文本队列。
     */
    @Test
    public void shouldSkipSuppressedHostTypedText() {
        UiInputService inputService = createInputService();
        LwjglxPollingInputBackend backend = new LwjglxPollingInputBackend(inputService, false);

        inputService.suppressNextCollectedKeyboardEvent(UiKeyCodes.KEY_A, UiKeyEvent.Action.PRESSED, "A");
        backend.handleHostTypedCharacter('A', UiKeyCodes.KEY_A);

        UiInputFrame frame = inputService.collectFrame();
        Assert.assertTrue(frame.getTextEvents().isEmpty());
        Assert.assertTrue(frame.getKeyEvents().isEmpty());
    }

    private static UiInputService createInputService() {
        return new UiInputService(new NoOpInputBackend());
    }

    /**
     * 不参与事件生产的输入后端替身。
     */
    private static final class NoOpInputBackend implements UiInputBackend {

        @Override
        public void initialize() {}

        @Override
        public void tick() {}

        @Override
        public void beginTextInput() {}

        @Override
        public void endTextInput() {}

        @Override
        public void handleHostTypedCharacter(char typedChar, int keyCode) {}

        @Override
        public void setHostKeyboardRepeatEnabled(boolean enabled) {}

        @Override
        public UiInputFrame createImmediateKeyboardFrame() {
            return null;
        }

        @Override
        public UiInputFrame createImmediateMouseFrame() {
            return null;
        }
    }
}
