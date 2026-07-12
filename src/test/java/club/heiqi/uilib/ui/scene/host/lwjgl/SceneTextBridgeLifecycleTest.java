package club.heiqi.uilib.ui.scene.host.lwjgl;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/** scene host 文本桥注册、降级和关闭 finally 语义的纯 JVM 测试。 */
public class SceneTextBridgeLifecycleTest {

    @Test
    public void successfulInitIsIdempotentAndCloseResetsMode() {
        FakeRegistration registration = new FakeRegistration(true, false, false);
        RecordingMode mode = new RecordingMode();
        SceneTextBridgeLifecycle lifecycle = new SceneTextBridgeLifecycle();

        Assert.assertTrue(lifecycle.init(registration, mode));
        Assert.assertTrue(lifecycle.init(registration, mode));
        Assert.assertEquals("重复 init 不得重复注册", 1, registration.registerCount);
        Assert.assertTrue(lifecycle.isActive());
        lifecycle.close(registration, mode);
        Assert.assertEquals(1, registration.unregisterCount);
        Assert.assertFalse(lifecycle.isActive());
        Assert.assertEquals(Boolean.FALSE, mode.values.get(mode.values.size() - 1));
    }

    @Test
    public void failedInitStaysInFallbackMode() {
        FakeRegistration registration = new FakeRegistration(false, false, false);
        RecordingMode mode = new RecordingMode();
        SceneTextBridgeLifecycle lifecycle = new SceneTextBridgeLifecycle();

        Assert.assertFalse(lifecycle.init(registration, mode));
        Assert.assertFalse(lifecycle.isActive());
        Assert.assertEquals(Boolean.FALSE, mode.values.get(mode.values.size() - 1));
        Assert.assertEquals(0, registration.unregisterCount);
    }

    @Test
    public void registrationExceptionUnregistersPartialRegistrationAndFallsBack() {
        FakeRegistration registration = new FakeRegistration(true, true, false);
        RecordingMode mode = new RecordingMode();
        SceneTextBridgeLifecycle lifecycle = new SceneTextBridgeLifecycle();

        Assert.assertFalse(lifecycle.init(registration, mode));
        Assert.assertFalse(lifecycle.isActive());
        Assert.assertEquals(1, registration.unregisterCount);
        Assert.assertEquals(Boolean.FALSE, mode.values.get(mode.values.size() - 1));
    }

    @Test
    public void closeResetsModeWhenUnregisterThrows() {
        FakeRegistration registration = new FakeRegistration(true, false, true);
        RecordingMode mode = new RecordingMode();
        SceneTextBridgeLifecycle lifecycle = new SceneTextBridgeLifecycle();
        lifecycle.init(registration, mode);

        try {
            lifecycle.close(registration, mode);
            Assert.fail("unregister 异常应向上传递");
        } catch (RuntimeException expected) {
            Assert.assertEquals("close 异常后仍必须复位 external mode", Boolean.FALSE,
                    mode.values.get(mode.values.size() - 1));
            Assert.assertFalse(lifecycle.isActive());
        }
    }

    private static final class RecordingMode implements SceneTextBridgeLifecycle.Mode {
        private final List<Boolean> values = new ArrayList<Boolean>();

        @Override
        public void setExternalTextMode(boolean external) {
            values.add(Boolean.valueOf(external));
        }
    }

    private static final class FakeRegistration implements SceneTextBridgeLifecycle.Registration {
        private final boolean result;
        private final boolean throwOnRegister;
        private final boolean throwOnUnregister;
        private int registerCount;
        private int unregisterCount;

        private FakeRegistration(boolean result, boolean throwOnRegister, boolean throwOnUnregister) {
            this.result = result;
            this.throwOnRegister = throwOnRegister;
            this.throwOnUnregister = throwOnUnregister;
        }

        @Override
        public boolean register() {
            registerCount++;
            if (throwOnRegister) throw new RuntimeException("register failed after listener add");
            return result;
        }

        @Override
        public void unregister() {
            unregisterCount++;
            if (throwOnUnregister) throw new RuntimeException("unregister failed");
        }
    }
}
