package club.heiqi.uilib.util;

import java.lang.reflect.Field;

import org.lwjgl.opengl.GL11;

/**
 * Angelica GLStateManager attribDepth 只读访问与过量弹出工具。
 *
 * <p>Angelica 模拟固定管线时部分第三方渲染路径（如 FFP 着色器变体编译期间）存在
 * glPushAttrib 未配对弹出的缺陷，累积到上限后抛 "Attrib stack overflow"。
 * UILib 在自身状态边界（图标 scope / 字体守卫 / 屏幕帧）读取真实深度，
 * 把边界内第三方多压入的深度按量弹出，避免泄漏跨帧累积。</p>
 *
 * <p>Angelica 不可用时所有方法静默降级为 no-op（返回 -1）。</p>
 */
public final class GlAttribDepth {

    private static Field depthField;
    private static boolean initFailed;

    private GlAttribDepth() {
    }

    /** 返回当前 attribDepth；不可用时返回 -1。 */
    public static int current() {
        if (initFailed) {
            return -1;
        }
        try {
            return depthField().getInt(null);
        } catch (Throwable throwable) {
            return -1;
        }
    }

    /** 把深度弹出到不高于 target；不可用或已达标时不动作。 */
    public static void popExcess(int target) {
        if (target < 0) {
            return;
        }
        for (int attempt = 0; attempt < 32; attempt++) {
            int depth = current();
            if (depth < 0 || depth <= target) {
                return;
            }
            try {
                GL11.glPopAttrib();
            } catch (Throwable throwable) {
                return;
            }
        }
    }

    private static Field depthField() throws Exception {
        if (depthField == null) {
            Class<?> glsm = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager");
            for (Field field : glsm.getDeclaredFields()) {
                if ("attribDepth".equals(field.getName()) && field.getType() == int.class) {
                    field.setAccessible(true);
                    depthField = field;
                    break;
                }
            }
            if (depthField == null) {
                initFailed = true;
                throw new IllegalStateException("attribDepth field not found");
            }
        }
        return depthField;
    }
}
