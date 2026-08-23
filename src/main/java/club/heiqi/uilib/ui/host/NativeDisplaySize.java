package club.heiqi.uilib.ui.host;

import java.lang.reflect.Method;

/**
 * 原生窗口物理分辨率读取(唯一权威来源)。
 *
 * <p>经 {@code org.lwjglx.opengl.Display.getWidth/getHeight} 反射获取,优先 GTNH 的
 * {@code org.lwjglx} 扩展实现,不可用时降级 {@code org.lwjgl} 原始实现。与
 * {@link club.heiqi.uilib.ui.scene.host.lwjgl.LwjglStateReader} 同源(物理像素,含 Y 翻转),
 * <b>不依赖 Minecraft 的 scaled {@code displayWidth/displayHeight}</b>——MC 的 scaled 分辨率
 * 受 GUI Scale 影响,渲染/布局一律用本类的原生窗口尺寸,避免元素随 GUI Scale 失真。</p>
 */
public final class NativeDisplaySize {

    private static final Method GET_WIDTH = resolve("getWidth");
    private static final Method GET_HEIGHT = resolve("getHeight");

    private NativeDisplaySize() {
    }

    /** @return 原生窗口物理像素宽;Display 不可用时返回 0 */
    public static int width() {
        return invoke(GET_WIDTH);
    }

    /** @return 原生窗口物理像素高;Display 不可用时返回 0 */
    public static int height() {
        return invoke(GET_HEIGHT);
    }

    private static Method resolve(String name) {
        Class<?> displayClass = null;
        try {
            displayClass = Class.forName("org.lwjglx.opengl.Display");
        } catch (Exception exception) {
            try {
                displayClass = Class.forName("org.lwjgl.opengl.Display");
            } catch (Exception fallbackException) {
                return null;
            }
        }
        try {
            return displayClass.getMethod(name);
        } catch (Exception exception) {
            return null;
        }
    }

    private static int invoke(Method method) {
        if (method == null) {
            return 0;
        }
        try {
            Object value = method.invoke(null);
            return value instanceof Number ? ((Number) value).intValue() : 0;
        } catch (Exception exception) {
            return 0;
        }
    }
}
