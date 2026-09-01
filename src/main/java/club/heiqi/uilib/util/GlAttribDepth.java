package club.heiqi.uilib.util;

import java.lang.reflect.Field;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
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

    private static final Logger LOG = LogManager.getLogger("QzUILib/GlAttribDepth");

    private static Field depthField;
    private static boolean initFailed;
    /** 降级只 WARN 一次：本工具处于每帧调用路径，重复告警会刷屏。 */
    private static boolean readWarned;
    private static boolean popWarned;

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
            // 原为静默 return -1；改为首次 WARN 留痕（对齐 5d-D5 assertClientThread 先例），
            // 语义不变：Angelica 缺席/反射失败时降级 no-op。
            if (!readWarned) {
                readWarned = true;
                LOG.warn("Angelica GLStateManager.attribDepth 不可读，attrib 过量弹出保护降级为 no-op：{}",
                        throwable.toString());
            }
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
                // 原为静默 return；首次 WARN 留痕。不重抛：本方法运行在绘制边界，
                // 抛异常会把第三方泄漏升级为崩溃。
                if (!popWarned) {
                    popWarned = true;
                    LOG.warn("glPopAttrib 清理第三方 attrib 泄漏失败，停止本轮过量弹出（depth={}）：{}",
                            Integer.valueOf(depth), throwable.toString());
                }
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
