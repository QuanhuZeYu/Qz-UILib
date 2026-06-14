package club.heiqi.uilib.ui.input;

import org.junit.Assert;
import org.junit.Test;

/**
 * `UiInputService` facade 生命周期契约测试。
 */
public class UiInputServiceTest {

    /**
     * 验证未初始化时不会向底层后端切换宿主键盘重复事件。
     */
    @Test
    public void shouldSkipHostKeyboardRepeatBeforeInitialize() {
        RecordingInputBackend backend = new RecordingInputBackend();
        UiInputService inputService = new UiInputService(backend);

        inputService.setHostKeyboardRepeatEnabled(true);

        Assert.assertEquals(0, backend.keyboardRepeatChangeCount);
    }

    /**
     * 验证初始化后会向底层后端切换宿主键盘重复事件。
     */
    @Test
    public void shouldDelegateHostKeyboardRepeatAfterInitialize() {
        RecordingInputBackend backend = new RecordingInputBackend();
        UiInputService inputService = new UiInputService(backend);

        inputService.initialize();
        inputService.setHostKeyboardRepeatEnabled(true);
        inputService.setHostKeyboardRepeatEnabled(false);

        Assert.assertEquals(1, backend.initializeCount);
        Assert.assertEquals(2, backend.keyboardRepeatChangeCount);
        Assert.assertTrue(backend.firstKeyboardRepeatEnabled);
        Assert.assertFalse(backend.currentKeyboardRepeatEnabled);
    }

    /**
     * 记录服务转发调用的输入后端替身。
     */
    private static final class RecordingInputBackend implements UiInputBackend {

        private int initializeCount;
        private int keyboardRepeatChangeCount;
        private boolean firstKeyboardRepeatEnabled;
        private boolean currentKeyboardRepeatEnabled;

        @Override
        public void initialize() {
            initializeCount++;
        }

        @Override
        public void tick() {}

        @Override
        public void beginTextInput() {}

        @Override
        public void endTextInput() {}

        @Override
        public void handleHostTypedCharacter(char typedChar, int keyCode) {}

        @Override
        public void setHostKeyboardRepeatEnabled(boolean enabled) {
            if (keyboardRepeatChangeCount == 0) {
                firstKeyboardRepeatEnabled = enabled;
            }
            currentKeyboardRepeatEnabled = enabled;
            keyboardRepeatChangeCount++;
        }

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
