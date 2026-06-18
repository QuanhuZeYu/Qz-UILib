package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.scene.input.mock.MockPlatformInputSource;
import org.junit.Assert;
import org.junit.Test;

/**
 * SceneKey 映射与逃生舱透传单元测试。
 *
 * <p>验证键盘事件的 SceneKey 正确映射，以及 UNKNOWN 按键时
 * nativeKeyCode/nativeScanCode 逃生舱的透传不丢失。</p>
 */
public class SceneKeyMappingTest {

    private static final long NOW = 1000000L;

    // ===== 测试 11：按键映射正确 =====

    /**
     * 验证：enqueueKey(ENTER, PRESSED, ...) drain 后 getKey()==ENTER 且 action 正确。
     */
    @Test
    public void shouldMapEnterKeyCorrectly() {
        MockPlatformInputSource source = new MockPlatformInputSource(800, 600);

        source.enqueueKey(SceneKey.ENTER, SceneKeyAction.PRESSED,
                false, false, false, false, NOW);
        SceneInputFrame frame = source.drainFrame();

        Assert.assertEquals("应有一条键盘事件", 1, frame.getKeyEvents().size());
        SceneKeyEvent event = frame.getKeyEvents().get(0);
        Assert.assertEquals("key 应为 ENTER", SceneKey.ENTER, event.getKey());
        Assert.assertEquals("action 应为 PRESSED", SceneKeyAction.PRESSED, event.getAction());
    }

    // ===== 测试 12：UNKNOWN 逃生舱透传 =====

    /**
     * 验证：enqueueKeyWithNative(UNKNOWN, ..., nativeKeyCode=999, nativeScanCode=1234)
     * drain 后 getKey()==UNKNOWN 且 getNativeKeyCode()==999 / getNativeScanCode()==1234。
     */
    @Test
    public void shouldPassThroughNativeCodesForUnknownKey() {
        MockPlatformInputSource source = new MockPlatformInputSource(800, 600);

        source.enqueueKeyWithNative(SceneKey.UNKNOWN, SceneKeyAction.PRESSED,
                999, 1234, NOW);
        SceneInputFrame frame = source.drainFrame();

        Assert.assertEquals("应有一条键盘事件", 1, frame.getKeyEvents().size());
        SceneKeyEvent event = frame.getKeyEvents().get(0);
        Assert.assertEquals("key 应为 UNKNOWN", SceneKey.UNKNOWN, event.getKey());
        Assert.assertEquals("nativeKeyCode 应为 999", 999, event.getNativeKeyCode());
        Assert.assertEquals("nativeScanCode 应为 1234", 1234, event.getNativeScanCode());
        Assert.assertEquals("action 应为 PRESSED", SceneKeyAction.PRESSED, event.getAction());
    }

    // ===== 测试 13：修饰键正确传递到事件级 =====

    /**
     * 验证：enqueue 带 ctrl+shift 的键事件，drain 后事件级 isControlDown()/
     * isShiftDown() 正确。
     */
    @Test
    public void shouldPassModifiersToEventLevel() {
        MockPlatformInputSource source = new MockPlatformInputSource(800, 600);

        source.enqueueKey(SceneKey.KEY_S, SceneKeyAction.PRESSED,
                true, true, false, false, NOW);
        SceneInputFrame frame = source.drainFrame();

        Assert.assertEquals("应有一条键盘事件", 1, frame.getKeyEvents().size());
        SceneKeyEvent event = frame.getKeyEvents().get(0);
        Assert.assertEquals("key 应为 KEY_S", SceneKey.KEY_S, event.getKey());
        Assert.assertTrue("isControlDown 应为 true", event.isControlDown());
        Assert.assertTrue("isShiftDown 应为 true", event.isShiftDown());
        Assert.assertFalse("isAltDown 应为 false", event.isAltDown());
        Assert.assertFalse("isMetaDown 应为 false", event.isMetaDown());
    }
}
