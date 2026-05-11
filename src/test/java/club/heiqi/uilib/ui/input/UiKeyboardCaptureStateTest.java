package club.heiqi.uilib.ui.input;

import org.junit.Assert;
import org.junit.Test;

/**
 * `UiKeyboardCaptureState` 的状态契约测试。
 */
public class UiKeyboardCaptureStateTest {

    /**
     * 验证屏幕与 HUD 任一方接管键盘时，都会阻断原生键盘链路。
     */
    @Test
    public void shouldTreatScreenAndHudCaptureAsUnifiedKeyboardCapture() {
        UiKeyboardCaptureState state = UiKeyboardCaptureState.getInstance();
        state.clear();
        try {
            Assert.assertFalse(state.isUiLibKeyboardCaptured());
            Assert.assertFalse(state.shouldCancelNativeKeyboardInput());

            state.setScreenKeyboardCaptured(true);
            Assert.assertTrue(state.isUiLibKeyboardCaptured());
            Assert.assertTrue(state.shouldCancelNativeKeyboardInput());

            state.setScreenKeyboardCaptured(false);
            state.setHudKeyboardCaptured(true);
            Assert.assertTrue(state.isUiLibKeyboardCaptured());
            Assert.assertTrue(state.shouldCancelNativeKeyboardInput());
        } finally {
            state.clear();
        }
    }
}
