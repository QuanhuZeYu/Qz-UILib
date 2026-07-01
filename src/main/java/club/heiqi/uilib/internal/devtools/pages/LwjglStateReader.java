package club.heiqi.uilib.internal.devtools.pages;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * LWJGL 当前态读取器 —— 适配层生产实现，唯一碰 LWJGL 反射 + 坐标换算。
 *
 * <p>通过反射访问 LWJGL 的当前态 API，均为非破坏性读取（不调 next() / getEvent*() 等消耗队列的方法）。
 * 类解析<b>优先 {@code org.lwjglx.input.Mouse/Keyboard}（GTNH 对 LWJGL 的升级扩展实现，能力更强），
 * 不可用时降级 {@code org.lwjgl.input.Mouse/Keyboard}</b>，与旧层 {@code LwjglInputRuntime} 的解析优先级保持一致。</p>
 *
 * <h3>坐标系前置约束（★真机对接必须满足，否则 hit-test 系统性偏移）</h3>
 * <p>本 reader 产出 <b>窗口物理像素坐标</b>（Display.getWidth/getHeight 量纲，含 Y 轴翻转）。
 * 仅当 SceneHostWidget 挂载在<b>物理像素坐标系宿主</b>（UiScreenHostSession：
 * glOrtho displayWidth + applyLayoutBounds displayWidth）下，鼠标坐标才与 Widget 几何量纲一致、
 * hit-test 不偏移。若将来 SceneHostWidget 改挂 <b>scaled 坐标系宿主</b>
 * （如 ScaledResolution 逻辑坐标的 GuiScreen/HUD 层），必须在此引入
 * {@code rawPhysical / scaleFactor} 换算并让 logicalWidth/Height 返回 scaledWidth/Height，
 * 否则 hover 高亮会随 GUI Scale 倍数系统性偏移。</p>
 *
 * <h3>坐标系换算（参照旧层 LwjglxPollingInputBackend）</h3>
 * <ul>
 *   <li>{@code mouseX} = {@code clamp(Mouse.getX(), 0, Display.getWidth())}</li>
 *   <li>{@code mouseY} = {@code clamp(Display.getHeight() - Mouse.getY() - 1, 0, Display.getHeight())} — Y 翻转</li>
 *   <li>视口尺寸经 {@code org.lwjgl(x).opengl.Display.getWidth/getHeight} 反射获取，与鼠标坐标同源于 LWJGL、不依赖 Minecraft。</li>
 *   <li><b>不额外做 GUI scale 换算</b>：旧层同样直接用物理尺寸。</li>
 * </ul>
 */
public class LwjglStateReader implements PlatformStateReader {

    // ==================== 反射缓存 ====================

    private static final Class<?> MOUSE_CLASS;
    private static final Class<?> KEYBOARD_CLASS;
    private static final Method MOUSE_IS_CREATED;
    private static final Method MOUSE_GET_X;
    private static final Method MOUSE_GET_Y;
    private static final Method MOUSE_IS_BUTTON_DOWN;
    private static final Method MOUSE_GET_DWHEEL;
    private static final Field MOUSE_TOTAL_SCROLL_AMOUNT;
    private static final Method KEYBOARD_IS_CREATED;
    private static final Method KEYBOARD_IS_KEY_DOWN;
    private static final Class<?> DISPLAY_CLASS;
    private static final Method DISPLAY_GET_WIDTH;
    private static final Method DISPLAY_GET_HEIGHT;
    private static final Method DISPLAY_IS_ACTIVE;

    static {
        Class<?> mc = null;
        Class<?> kc = null;
        Class<?> dc = null;
        // 优先 org.lwjglx（GTNH 对 LWJGL 的升级扩展实现，能力更强），降级 org.lwjgl 原始实现。
        // 与既有 LwjglInputRuntime.resolveRuntimeClass 的优先级保持一致。
        try {
            mc = Class.forName("org.lwjglx.input.Mouse");
        } catch (Exception e) {
            try {
                mc = Class.forName("org.lwjgl.input.Mouse");
            } catch (Exception e2) {
                // 均不可用
            }
        }
        try {
            kc = Class.forName("org.lwjglx.input.Keyboard");
        } catch (Exception e) {
            try {
                kc = Class.forName("org.lwjgl.input.Keyboard");
            } catch (Exception e2) {
                // 均不可用
            }
        }
        try {
            dc = Class.forName("org.lwjglx.opengl.Display");
        } catch (Exception e) {
            try {
                dc = Class.forName("org.lwjgl.opengl.Display");
            } catch (Exception e2) {
                // 均不可用
            }
        }
        MOUSE_CLASS = mc;
        KEYBOARD_CLASS = kc;
        DISPLAY_CLASS = dc;
        MOUSE_IS_CREATED = findStaticMethod(MOUSE_CLASS, "isCreated");
        MOUSE_GET_X = findStaticMethod(MOUSE_CLASS, "getX");
        MOUSE_GET_Y = findStaticMethod(MOUSE_CLASS, "getY");
        MOUSE_IS_BUTTON_DOWN = findStaticMethod(MOUSE_CLASS, "isButtonDown", int.class);
        MOUSE_GET_DWHEEL = findStaticMethod(MOUSE_CLASS, "getDWheel");
        MOUSE_TOTAL_SCROLL_AMOUNT = findStaticField(MOUSE_CLASS, "totalScrollAmount");
        KEYBOARD_IS_CREATED = findStaticMethod(KEYBOARD_CLASS, "isCreated");
        KEYBOARD_IS_KEY_DOWN = findStaticMethod(KEYBOARD_CLASS, "isKeyDown", int.class);
        DISPLAY_GET_WIDTH = findStaticMethod(DISPLAY_CLASS, "getWidth");
        DISPLAY_GET_HEIGHT = findStaticMethod(DISPLAY_CLASS, "getHeight");
        DISPLAY_IS_ACTIVE = findStaticMethod(DISPLAY_CLASS, "isActive");
    }

    // ==================== 反射工具 ====================

    private static Method findStaticMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        if (clazz == null) return null;
        try {
            return clazz.getMethod(name, paramTypes);
        } catch (Exception e) {
            return null;
        }
    }

    private static Field findStaticField(Class<?> clazz, String name) {
        if (clazz == null) return null;
        try {
            return clazz.getField(name);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean invokeBoolean(Method m, boolean fallback, Object... args) {
        if (m == null) return fallback;
        try {
            Object result = m.invoke(null, args);
            return result instanceof Boolean ? (Boolean) result : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int invokeInt(Method m, int fallback, Object... args) {
        if (m == null) return fallback;
        try {
            Object result = m.invoke(null, args);
            return result instanceof Number ? ((Number) result).intValue() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double readStaticDoubleField(Field f, double fallback) {
        if (f == null) return fallback;
        try {
            Object value = f.get(null);
            return value instanceof Number ? ((Number) value).doubleValue() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    // ==================== PlatformStateReader 实现 ====================

    @Override
    public int mouseX() {
        if (MOUSE_CLASS == null) return 0;
        if (!invokeBoolean(MOUSE_IS_CREATED, false)) return 0;
        int displayWidth = displayWidth();
        if (displayWidth <= 0) return 0;
        int rawX = invokeInt(MOUSE_GET_X, 0);
        // L3 注意：clamp 上界 displayWidth（宽像素数）为排他上界，
        // 有效坐标 [0, displayWidth-1]。clamp 到此值意味着允许 displayWidth
        // 作为上界，与旧层 LwjglxPollingInputBackend 一致。
        return clamp(rawX, 0, displayWidth);
    }

    @Override
    public int mouseY() {
        if (MOUSE_CLASS == null) return 0;
        if (!invokeBoolean(MOUSE_IS_CREATED, false)) return 0;
        int displayHeight = displayHeight();
        if (displayHeight <= 0) return 0;
        int rawY = invokeInt(MOUSE_GET_Y, 0);
        // Y 翻转：LWJGL 原点左下 → UI 原点左上
        return clamp(displayHeight - rawY - 1, 0, displayHeight);
    }

    @Override
    public boolean buttonDown(int button) {
        if (MOUSE_CLASS == null) return false;
        if (!invokeBoolean(MOUSE_IS_CREATED, false)) return false;
        return invokeBoolean(MOUSE_IS_BUTTON_DOWN, false, button);
    }

    @Override
    public double scrollAccum() {
        if (MOUSE_CLASS == null) return 0.0;
        if (!invokeBoolean(MOUSE_IS_CREATED, false)) return 0.0;
        // 优先用累计字段（非破坏性），不可用时回退到 getDWheel。
        // L2 降级风险：getDWheel() 是破坏性读取（读一次清零事件队列），若旧层
        // UiInputService 同时也在调 Mouse.next()/getEventDWheel() 消费同一队列，
        // 则两者互相抢事件。优先 totalScrollAmount 避免此冲突；仅在无该字段的
        // 老旧 LWJGL build 上才降级到 getDWheel。
        boolean hasField = MOUSE_TOTAL_SCROLL_AMOUNT != null;
        return hasField
            ? readStaticDoubleField(MOUSE_TOTAL_SCROLL_AMOUNT, 0.0)
            : invokeInt(MOUSE_GET_DWHEEL, 0);
    }

    /**
     * 滚轮单帧增量（破坏性读取）—— Bug1 双路径修复的 fallback 路径。
     *
     * <p>反射 {@code Mouse.getDWheel()}：返回自上次调用以来的滚轮增量，<b>读后清零</b>。
     * 仅在 {@link #scrollAccum()} 差分路径无效（真机 totalScrollAmount 恒为 0）时
     * 由 {@link LwjglInputSource#drainFrame()} 调用，避免每帧清零影响其他层
     * （旧层 UiInputService 可能也在消费同一事件队列）。</p>
     *
     * <p>符号约定与 {@code Mouse.getDWheel()} 一致：正=向上滚，负=向下滚，
     * 与 {@link #scrollAccum()} 累计值增长方向一致。</p>
     *
     * @return 自上次调用以来的滚轮增量，Mouse 不可用时返回 0
     */
    @Override
    public int dWheelDelta() {
        if (MOUSE_CLASS == null) return 0;
        if (!invokeBoolean(MOUSE_IS_CREATED, false)) return 0;
        // getDWheel() 破坏性读取：读后内部计数清零
        return invokeInt(MOUSE_GET_DWHEEL, 0);
    }

    @Override
    public boolean control() {
        if (KEYBOARD_CLASS == null) return false;
        if (!invokeBoolean(KEYBOARD_IS_CREATED, false)) return false;
        // LWJGL key codes: 29=LCONTROL, 157=RCONTROL
        return invokeBoolean(KEYBOARD_IS_KEY_DOWN, false, 29)
                || invokeBoolean(KEYBOARD_IS_KEY_DOWN, false, 157);
    }

    @Override
    public boolean shift() {
        if (KEYBOARD_CLASS == null) return false;
        if (!invokeBoolean(KEYBOARD_IS_CREATED, false)) return false;
        // LWJGL key codes: 42=LSHIFT, 54=RSHIFT
        return invokeBoolean(KEYBOARD_IS_KEY_DOWN, false, 42)
                || invokeBoolean(KEYBOARD_IS_KEY_DOWN, false, 54);
    }

    @Override
    public boolean alt() {
        if (KEYBOARD_CLASS == null) return false;
        if (!invokeBoolean(KEYBOARD_IS_CREATED, false)) return false;
        // LWJGL key codes: 56=LMENU(Left Alt), 184=RMENU(Right Alt)
        return invokeBoolean(KEYBOARD_IS_KEY_DOWN, false, 56)
                || invokeBoolean(KEYBOARD_IS_KEY_DOWN, false, 184);
    }

    @Override
    public boolean meta() {
        // L4：LWJGL 2 / Windows 无 Meta 键语义，始终返回 false。
        // GLFW/LWJGL 3 迁移后可读 GLFW_MOD_SUPER。
        return false;
    }

    @Override
    public boolean windowFocused() {
        // 反射 Display.isActive()，不可用时降级返回 true（保守：不误伤合成 cancel）
        return invokeBoolean(DISPLAY_IS_ACTIVE, true);
    }

    @Override
    public int logicalWidth() {
        int w = displayWidth();
        return w > 0 ? w : 854;
    }

    @Override
    public int logicalHeight() {
        int h = displayHeight();
        return h > 0 ? h : 480;
    }

    @Override
    public long nowNanos() {
        return System.nanoTime();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 读取当前窗口物理像素宽度。
     *
     * <p>经 {@code org.lwjgl(x).opengl.Display.getWidth()} 反射获取，与鼠标坐标同源于
     * LWJGL，不依赖 Minecraft。Display 不可用时返回 0，由调用方落兜底值。</p>
     *
     * @return 窗口物理像素宽度，不可用时 0
     */
    private static int displayWidth() {
        return invokeInt(DISPLAY_GET_WIDTH, 0);
    }

    /**
     * 读取当前窗口物理像素高度。
     *
     * @return 窗口物理像素高度，不可用时 0
     */
    private static int displayHeight() {
        return invokeInt(DISPLAY_GET_HEIGHT, 0);
    }
}
