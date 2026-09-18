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
 * <p>深度入口按"新→旧"解析，三种形态互斥，统一收敛为 {@link DepthAccessor}：</p>
 * <ol>
 *   <li>{@code public static int GLStateManager.getAttribDepth()}（2.1.x 起即为 public static，
 *       且 2.2.10 把 attribDepth 由 GLStateManager 静态字段迁至 GLContextState 后仍由该访问器暴露）；</li>
 *   <li>{@code private static int GLStateManager.attribDepth} 字段；</li>
 *   <li>{@code private static final IntStack GLStateManager.attribs} 字段（GTNH 2.8.x 的
 *       Angelica 1.0.0-betaXX 形态）——该容器本身就是 attrib 栈，深度即 {@code size()}。</li>
 * </ol>
 *
 * <p>Angelica 不可用或三档都缺失时所有方法静默降级为 no-op（返回 -1）。字段反射沿用本类既有做法
 * （{@code setAccessible} 只用于读取上游私有状态，不做写入）。</p>
 */
public final class GlAttribDepth {

    private static final Logger LOG = LogManager.getLogger("QzUILib/GlAttribDepth");

    /** 宿主 GLStateManager 类名；只在此处出现，通用路径不链接上游类型。 */
    private static final String GLSM_CLASS_NAME = "com.gtnewhorizons.angelica.glsm.GLStateManager";

    private static DepthAccessor accessor;
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
            ensureAccessor();
            DepthAccessor resolved = accessor;
            return resolved == null ? -1 : resolved.read();
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

    /** 解析并缓存深度读取入口（每个进程一次）。 */
    private static synchronized void ensureAccessor() throws ClassNotFoundException {
        if (accessor != null || initFailed) {
            return;
        }
        DepthAccessor resolved = resolveFrom(Class.forName(GLSM_CLASS_NAME));
        if (resolved == null) {
            initFailed = true;
            LOG.warn("Angelica 未提供可读的 attrib 栈深度入口（getAttribDepth()/attribDepth/attribs 均缺失），"
                    + "attrib 过量弹出保护降级为 no-op");
            return;
        }
        accessor = resolved;
    }

    /**
     * 从宿主类解析深度读取入口（纯解析，不缓存；包内可见以便纯 JVM 测试直接喂替身类）。
     *
     * <p>{@code getAttribDepth()} 在 2.1.32 / 2.1.43 / 2.1.50 / 2.2.10 中均为 {@code public static int}，
     * 2.1.x 返回 GLStateManager.attribDepth、2.2.10 返回 GLContextState.attribDepth，语义同为 attrib 栈深度；
     * 1.0.0-betaXX 两者都没有，只有私有的 {@code IntStack attribs} 容器。</p>
     *
     * @param glsm 宿主 GLStateManager 类
     * @return 深度读取入口；三档都缺失时返回 {@code null}
     */
    static DepthAccessor resolveFrom(Class<?> glsm) {
        if (glsm == null) {
            return null;
        }
        try {
            Method candidate = glsm.getMethod("getAttribDepth");
            if (candidate.getReturnType() == int.class && Modifier.isStatic(candidate.getModifiers())) {
                return DepthAccessor.forMethod(candidate);
            }
        } catch (NoSuchMethodException ignored) {
            // 无访问器的版本继续走字段反射。
        } catch (SecurityException ignored) {
            // 同上：安全策略拒绝探测时退回字段反射。
        }
        for (Field field : glsm.getDeclaredFields()) {
            if ("attribDepth".equals(field.getName()) && field.getType() == int.class) {
                field.setAccessible(true);
                return DepthAccessor.forIntField(field);
            }
        }
        for (Field field : glsm.getDeclaredFields()) {
            if (!"attribs".equals(field.getName())) {
                continue;
            }
            Method size = sizeMethodOf(field.getType());
            if (size == null) {
                continue;
            }
            field.setAccessible(true);
            return DepthAccessor.forStackField(field, size);
        }
        return null;
    }

    /** 取容器类型的 {@code int size()}；fastutil 的 IntStack 由 IntCollection 继承而来。 */
    private static Method sizeMethodOf(Class<?> stackType) {
        if (stackType == null) {
            return null;
        }
        try {
            Method size = stackType.getMethod("size");
            return size.getReturnType() == int.class ? size : null;
        } catch (NoSuchMethodException exception) {
            return null;
        } catch (SecurityException exception) {
            return null;
        }
    }

    /** 深度读取入口；三种形态互斥，构造期已定死其一。 */
    static final class DepthAccessor {

        private final Method accessorMethod;
        private final Field depthField;
        private final Field stackField;
        private final Method stackSizeMethod;

        private DepthAccessor(Method accessorMethod, Field depthField, Field stackField, Method stackSizeMethod) {
            this.accessorMethod = accessorMethod;
            this.depthField = depthField;
            this.stackField = stackField;
            this.stackSizeMethod = stackSizeMethod;
        }

        static DepthAccessor forMethod(Method accessorMethod) {
            return new DepthAccessor(accessorMethod, null, null, null);
        }

        static DepthAccessor forIntField(Field depthField) {
            return new DepthAccessor(null, depthField, null, null);
        }

        static DepthAccessor forStackField(Field stackField, Method stackSizeMethod) {
            return new DepthAccessor(null, null, stackField, stackSizeMethod);
        }

        /**
         * 读取当前 attrib 栈深度。
         *
         * @return 深度；入口形态不可读时返回 -1
         * @throws Exception 反射调用失败（由调用方统一降级为 -1 并首次留痕）
         */
        int read() throws Exception {
            if (accessorMethod != null) {
                Object value = accessorMethod.invoke(null);
                return value instanceof Number ? ((Number) value).intValue() : -1;
            }
            if (depthField != null) {
                return depthField.getInt(null);
            }
            Object stack = stackField.get(null);
            if (stack == null) {
                return -1;
            }
            Object size = stackSizeMethod.invoke(stack);
            return size instanceof Number ? ((Number) size).intValue() : -1;
        }
    }
}
