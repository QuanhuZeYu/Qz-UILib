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
 *
 * <p>另提供仅用于诊断日志的观测面：宿主当前 pass 键（0/1 为主 pass、2 为光影 shadow pass）与可读标签。
 * pass 字段是宿主私有实现细节，读不到只让观测退化为「未知」，既不影响 {@code hasPendingDeferredGeometry()}
 * 的判定，也不影响捕获与回放行为。</p>
 */
public final class AngelicaTesrBatchProbe implements TesrTextReplayCoordinator.HostProbe {

    private static final String RENDERER_CLASS = "com.gtnewhorizons.angelica.rendering.tesr.TesrBatchRenderer";
    /** Angelica coremod 入口类：只用于区分「宿主缺席」与「宿主版本不提供该渲染器」。 */
    private static final String ANGELICA_TWEAKER_CLASS = "com.gtnewhorizons.angelica.loading.AngelicaTweaker";
    private static final String INSTANCE_FIELD = "INSTANCE";
    private static final String PENDING_GEOMETRY_METHOD = "hasPendingGeometry";
    /** 类加载时序可能早于宿主初始化，解析失败允许的有限重试次数。 */
    private static final int MAX_RESOLVE_ATTEMPTS = 3;
    /** 宿主当前 pass 字段（观测用私有成员）。 */
    private static final String ACTIVE_PASS_FIELD = "activePass";
    /** 宿主 pass 键：主 pass。 */
    private static final int PASS_MAIN_0 = 0;
    /** 宿主 pass 键：第二主 pass。 */
    private static final int PASS_MAIN_1 = 1;
    /** 宿主 pass 键：光影 shadow pass。 */
    private static final int PASS_SHADOW = 2;

    private Object renderer;
    private Method pendingGeometryMethod;
    private int resolveAttempts;
    private boolean unavailable;
    private boolean abiWarned;
    private Field activePassField;
    private boolean activePassResolved;
    private boolean activePassUnavailable;

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

    @Override
    public int activePassKey() {
        if (renderer == null && !resolve()) {
            return TesrTextReplayCoordinator.HostProbe.PASS_UNKNOWN;
        }
        return readActivePass(renderer);
    }

    @Override
    public int activePassKeyOf(Object hostInstance) {
        return hostInstance == null ? TesrTextReplayCoordinator.HostProbe.PASS_UNKNOWN : readActivePass(hostInstance);
    }

    @Override
    public String activePassLabel(int passKey) {
        return describePass(passKey);
    }

    /**
     * 宿主 pass 键的可读标签（观测用）。
     *
     * @param passKey pass 键
     * @return 标签；非已知键为 {@code "unknown"}
     */
    static String describePass(int passKey) {
        switch (passKey) {
            case PASS_MAIN_0:
                return "main0";
            case PASS_MAIN_1:
                return "main1";
            case PASS_SHADOW:
                return "shadow";
            default:
                return "unknown";
        }
    }

    private int readActivePass(Object hostInstance) {
        if (unavailable || activePassUnavailable) {
            return TesrTextReplayCoordinator.HostProbe.PASS_UNKNOWN;
        }
        if (!activePassResolved) {
            resolveActivePassField(hostInstance);
        }
        Field field = activePassField;
        if (field == null) {
            return TesrTextReplayCoordinator.HostProbe.PASS_UNKNOWN;
        }
        try {
            return field.getInt(hostInstance);
        } catch (Throwable throwable) {
            activePassUnavailable = true;
            warnAbi("读取宿主 TESR pass 身份失败（未复核的 Angelica 版本），世界文字协调观测停用", throwable);
            return TesrTextReplayCoordinator.HostProbe.PASS_UNKNOWN;
        }
    }

    private void resolveActivePassField(Object hostInstance) {
        activePassResolved = true;
        try {
            Field field = hostInstance.getClass().getDeclaredField(ACTIVE_PASS_FIELD);
            field.setAccessible(true);
            activePassField = field;
        } catch (Throwable throwable) {
            activePassUnavailable = true;
            warnAbi("宿主 TESR pass 字段不可用（未复核的 Angelica 版本），世界文字协调观测停用", throwable);
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
            // 无 Angelica：正常组合，静默 fail-open。有 Angelica 却没有该类，说明宿主版本不提供
            // TESR 批处理（GTNH 2.8.x 的 Angelica 1.0.0-betaXX）：世界文字层序保护不可用属功能降级，
            // 不是正常缺席，留一次告警便于现场定位。
            unavailable = resolveAttempts >= MAX_RESOLVE_ATTEMPTS;
            if (unavailable && angelicaPresent()) {
                warnAbi("宿主 Angelica 未提供 TESR 批处理渲染器，世界文字退回即时绘制（层序保护不可用）",
                        absent);
            }
            return false;
        } catch (Throwable throwable) {
            unavailable = resolveAttempts >= MAX_RESOLVE_ATTEMPTS;
            warnAbi("宿主 TESR 批处理 ABI 不可用（未复核的 Angelica 版本），世界文字保持即时绘制", throwable);
            return false;
        }
    }

    /** Angelica coremod 是否在本进程内（与 EarlyMixins 的宿主判据同源，仍只用类名字符串）。 */
    private static boolean angelicaPresent() {
        try {
            Class.forName(ANGELICA_TWEAKER_CLASS, false, AngelicaTesrBatchProbe.class.getClassLoader());
            return true;
        } catch (Throwable absent) {
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
