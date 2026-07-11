package club.heiqi.uilib.ui.scene.image;

import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.render.UiRenderBackend;

/** scene 图片契约的平台隔离与旧 backend 兼容测试。 */
public class SceneImageIsolationTest {
    /** 核心类型可在 headless JVM 加载，公开签名不泄漏 MC/ItemStack/GL。 */
    @Test
    public void coreApi_isHeadlessAndPlatformNeutral() throws Exception {
        Class<?>[] coreTypes = {SceneImageSource.class, SceneImageRect.class,
                Class.forName("club.heiqi.uilib.ui.scene.paint.PaintCommand")};
        for (Class<?> type : coreTypes) {
            for (Method method : type.getDeclaredMethods()) {
                assertNeutral(method.getReturnType());
                for (Class<?> parameter : method.getParameterTypes()) assertNeutral(parameter);
            }
        }
        LegacyBackend backend = new LegacyBackend();
        backend.drawImage(new SceneImageSource() { }, 0, 0, 1, 1);
        Assert.assertEquals(0, backend.fillCalls);
    }

    private static void assertNeutral(Class<?> type) {
        String name = type.getName();
        Assert.assertFalse(name, name.startsWith("net.minecraft"));
        Assert.assertFalse(name, name.startsWith("org.lwjgl"));
    }

    private static final class LegacyBackend implements UiRenderBackend {
        private int fillCalls;
        @Override public void fillRect(int l, int t, int r, int b, int c) { fillCalls++; }
        @Override public void drawSurface(int l, int t, int r, int b, int f, int c, int radius) { }
        @Override public void drawBorder(int l, int t, int r, int b, int c) { }
        @Override public void pushClip(int l, int t, int r, int b, int radius) { }
        @Override public void popClip() { }
        @Override public void drawText(String text, int x, int y, int c, boolean shadow) { }
        @Override public void drawText(String text, int x, int y, int c, boolean shadow, int size) { }
        @Override public void pushGroupOpacity(int l, int t, int r, int b, float opacity) { }
        @Override public void popGroupOpacity() { }
        @Override public void pushTransform(float tx, float ty, float d, float sx, float sy, float ox, float oy,
                int l, int t, int r, int b) { }
        @Override public void popTransform() { }
        @Override public void pushTransformLayer(float tx, float ty, float d, float sx, float sy, float ox, float oy,
                int l, int t, int r, int b) { }
        @Override public void popTransformLayer() { }
    }
}
