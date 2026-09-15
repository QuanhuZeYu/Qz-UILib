package club.heiqi.uilib.internal.font.tesr.angelica;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.internal.font.tesr.TesrTextReplayCoordinator;

/**
 * Angelica TESR 批处理窗口探针（可选宿主围栏）。
 *
 * <p>只经反射读取宿主公开面 {@code TesrBatchRenderer.INSTANCE.hasPendingGeometry()}；
 * 上游 ABI 类名只出现在本包，通用路径不引用 Angelica 类。Angelica 缺席、类名/成员缺失或运行期
 * 调用失败时一律返回 false 并停用本探针，世界文字退回即时绘制（fail-open），无 Angelica 也能正常运行。</p>
 *
 * <p>未复核的 Angelica 版本（类在、成员不在）留一次 WARN，便于现场定位 ABI 漂移；类完全缺席属正常
 * 组合，不告警。</p>
 */
public final class AngelicaTesrBatchProbe implements TesrTextReplayCoordinator.HostProbe {

    private static final String RENDERER_CLASS = "com.gtnewhorizons.angelica.rendering.tesr.TesrBatchRenderer";
    private static final String INSTANCE_FIELD = "INSTANCE";
    private static final String PENDING_GEOMETRY_METHOD = "hasPendingGeometry";
    /** 类加载时序可能早于宿主初始化，解析失败允许的有限重试次数。 */
    private static final int MAX_RESOLVE_ATTEMPTS = 3;

    private Object renderer;
    private Method pendingGeometryMethod;
    private int resolveAttempts;
    private boolean unavailable;
    private boolean abiWarned;

    @Override
    public boolean hasPendingDeferredGeometry() {
        if (unavailable) {
            return false;
        }
        if (renderer == null && !resolve()) {
            return false;
        }
        try {
            Object value = pendingGeometryMethod.invoke(renderer);
            return value instanceof Boolean && ((Boolean) value).booleanValue();
        } catch (Throwable throwable) {
            unavailable = true;
            warnAbi("调用宿主 TESR 批处理探针失败", throwable);
            return false;
        }
    }

    private boolean resolve() {
        if (resolveAttempts >= MAX_RESOLVE_ATTEMPTS) {
            unavailable = true;
            return false;
        }
        resolveAttempts++;
        try {
            Class<?> rendererClass = Class.forName(RENDERER_CLASS);
            Field instanceField = rendererClass.getField(INSTANCE_FIELD);
            Method method = rendererClass.getMethod(PENDING_GEOMETRY_METHOD);
            Object instance = instanceField.get(null);
            if (instance == null) {
                return false;
            }
            pendingGeometryMethod = method;
            renderer = instance;
            return true;
        } catch (ClassNotFoundException absent) {
            // 无 Angelica：正常组合，静默 fail-open。
            unavailable = resolveAttempts >= MAX_RESOLVE_ATTEMPTS;
            return false;
        } catch (Throwable throwable) {
            unavailable = resolveAttempts >= MAX_RESOLVE_ATTEMPTS;
            warnAbi("宿主 TESR 批处理 ABI 不可用（未复核的 Angelica 版本），世界文字保持即时绘制", throwable);
            return false;
        }
    }

    private void warnAbi(String message, Throwable throwable) {
        if (abiWarned) {
            return;
        }
        abiWarned = true;
        MyMod.LOG.warn(message + "：{}", throwable.toString());
    }
}
