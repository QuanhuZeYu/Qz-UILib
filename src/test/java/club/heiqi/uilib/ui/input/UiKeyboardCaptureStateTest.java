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
            Assert.assertEquals(UiKeyboardCaptureState.Owner.NONE, state.getKeyboardCaptureOwner());

            state.setScreenKeyboardCaptured(true);
            Assert.assertTrue(state.isUiLibKeyboardCaptured());
            Assert.assertTrue(state.shouldCancelNativeKeyboardInput());
            Assert.assertEquals(UiKeyboardCaptureState.Owner.SCREEN, state.getKeyboardCaptureOwner());

            state.setHudKeyboardCaptured(true);
            Assert.assertTrue(state.isUiLibKeyboardCaptured());
            Assert.assertTrue(state.shouldCancelNativeKeyboardInput());
            Assert.assertEquals(UiKeyboardCaptureState.Owner.BOTH, state.getKeyboardCaptureOwner());

            state.setScreenKeyboardCaptured(false);
            Assert.assertEquals(UiKeyboardCaptureState.Owner.HUD, state.getKeyboardCaptureOwner());
        } finally {
            state.clear();
        }
    }

    /**
     * 验证文本输入模式请求也要按 Screen/HUD 聚合，避免 HUD 释放焦点时误关 Screen 的文本输入模式。
     */
    @Test
    public void shouldAggregateScreenAndHudTextInputRequests() {
        UiKeyboardCaptureState state = UiKeyboardCaptureState.getInstance();
        state.clear();
        try {
            Assert.assertFalse(state.shouldKeepTextInputActive());

            state.setScreenTextInputRequested(true);
            Assert.assertTrue(state.shouldKeepTextInputActive());

            state.setHudTextInputRequested(true);
            state.setScreenTextInputRequested(false);
            Assert.assertTrue(state.shouldKeepTextInputActive());

            state.setHudTextInputRequested(false);
            Assert.assertFalse(state.shouldKeepTextInputActive());
        } finally {
            state.clear();
        }
    }

    /**
     * 验证 HUD 释放键盘捕获时，不会误清 Screen 仍在持有的文本输入请求。
     */
    @Test
    public void shouldNotClearScreenTextInputRequestWhenHudCaptureReleases() {
        UiKeyboardCaptureState state = UiKeyboardCaptureState.getInstance();
        state.clear();
        try {
            state.setScreenKeyboardCaptured(true);
            state.setScreenTextInputRequested(true);
            state.setHudKeyboardCaptured(true);
            state.setHudTextInputRequested(true);

            state.setHudKeyboardCaptured(false);
            state.setHudTextInputRequested(false);

            Assert.assertTrue(state.isUiLibKeyboardCaptured());
            Assert.assertEquals(UiKeyboardCaptureState.Owner.SCREEN, state.getKeyboardCaptureOwner());
            Assert.assertTrue(state.shouldKeepTextInputActive());
        } finally {
            state.clear();
        }
    }

}
