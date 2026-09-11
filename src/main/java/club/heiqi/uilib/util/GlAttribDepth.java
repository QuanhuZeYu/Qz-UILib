package club.heiqi.uilib.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

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
 * <p>深度入口按"新→旧"解析：优先 {@code GLStateManager.getAttribDepth()}（2.1.x 起即为 public static，
 * 且 2.2.10 把 attribDepth 由 GLStateManager 静态字段迁至 GLContextState 后仍由该访问器暴露），
 * 其次回退旧版 {@code private static int attribDepth} 字段反射。Angelica 不可用或两个入口都缺失时
 * 所有方法静默降级为 no-op（返回 -1）。</p>
 */
public final class GlAttribDepth {

    private static final Logger LOG = LogManager.getLogger("QzUILib/GlAttribDepth");

    /** 新版入口：{@code public static int GLStateManager.getAttribDepth()}。 */
    private static Method depthMethod;
    /** 旧版入口：{@code private static int GLStateManager.attribDepth}。 */
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
            return readAttribDepth();
        } catch (Throwable throwable) {
            // 原为静默 return -1；改为首次 WARN 留痕（对齐 5d-D5 assertClientThread 先例），
            // 语义不变：Angelica 缺席/反射失败时降级 no-op。
            if (!readWarned) {
                readWarned = true;
                LOG.warn("Angelica attrib 栈深度不可读，attrib 过量弹出保护降级为 no-op：{}",
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

    /** 读取 Angelica 侧 attrib 栈深度；优先 public 访问器，回退旧版私有字段。 */
    private static int readAttribDepth() throws Exception {
        resolveDepthAccessor();
        if (initFailed) {
            return -1;
        }
        Method method = depthMethod;
        if (method != null) {
            Object value = method.invoke(null);
            return value instanceof Number ? ((Number) value).intValue() : -1;
        }
        return depthField.getInt(null);
    }

    /**
     * 解析并缓存深度读取入口（每个进程一次）。
     *
     * <p>Angelica 2.2.10 把 {@code attribDepth} 从 GLStateManager 静态字段迁移到 GLContextState，
     * 旧字段反射必然失败；而 {@code getAttribDepth()} 在 2.1.32 / 2.1.43 / 2.1.50 / 2.2.10 中均为
     * {@code public static int}，2.1.x 返回 GLStateManager.attribDepth、2.2.10 返回
     * GLContextState.attribDepth，语义同为 attrib 栈深度。</p>
     */
    private static synchronized void resolveDepthAccessor() throws ClassNotFoundException {
        if (depthMethod != null || depthField != null || initFailed) {
            return;
        }
        Class<?> glsm = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager");
        try {
            Method accessor = glsm.getMethod("getAttribDepth");
            if (accessor.getReturnType() == int.class && Modifier.isStatic(accessor.getModifiers())) {
                depthMethod = accessor;
                return;
            }
        } catch (NoSuchMethodException ignored) {
            // 无访问器的旧版继续走字段反射。
        }
        for (Field field : glsm.getDeclaredFields()) {
            if ("attribDepth".equals(field.getName()) && field.getType() == int.class) {
                field.setAccessible(true);
                depthField = field;
                return;
            }
        }
        initFailed = true;
        LOG.warn("Angelica 未提供可读的 attrib 栈深度入口（getAttribDepth()/attribDepth 均缺失），"
                + "attrib 过量弹出保护降级为 no-op");
    }
}
