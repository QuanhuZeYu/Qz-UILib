package club.heiqi.uilib.internal.devtools.pages;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;

/**
 * LWJGL 当前态读取器 —— 适配层生产实现，唯一碰 LWJGL 反射 + 坐标换算。
 *
 * <p>通过反射访问 LWJGL 的当前态 API，均为非破坏性读取（不调 next() / getEvent*() 等消耗队列的方法）。
 * 类解析<b>优先 {@code org.lwjglx.input.Mouse/Keyboard}（GTNH 对 LWJGL 的升级扩展实现，能力更强），
 * 不可用时降级 {@code org.lwjgl.input.Mouse/Keyboard}</b>，与旧层 {@code LwjglInputRuntime} 的解析优先级保持一致。</p>
 *
 * <h3>坐标系前置约束（★真机对接必须满足，否则 hit-test 系统性偏移）</h3>
 * <p>本 reader 产出 <b>Minecraft 物理像素坐标</b>（displayWidth/displayHeight 量纲，含 Y 轴翻转）。
 * 仅当 SceneHostWidget 挂载在<b>物理像素坐标系宿主</b>（UiScreenHostSession：
 * glOrtho displayWidth + applyLayoutBounds displayWidth）下，鼠标坐标才与 Widget 几何量纲一致、
 * hit-test 不偏移。若将来 SceneHostWidget 改挂 <b>scaled 坐标系宿主</b>
 * （如 ScaledResolution 逻辑坐标的 GuiScreen/HUD 层），必须在此引入
 * {@code rawPhysical / scaleFactor} 换算并让 logicalWidth/Height 返回 scaledWidth/Height，
 * 否则 hover 高亮会随 GUI Scale 倍数系统性偏移。</p>
 *
 * <h3>坐标系换算（参照旧层 LwjglxPollingInputBackend）</h3>
 * <ul>
 *   <li>{@code mouseX} = {@code clamp(Mouse.getX(), 0, mc.displayWidth)}</li>
 *   <li>{@code mouseY} = {@code clamp(mc.displayHeight - Mouse.getY() - 1, 0, mc.displayHeight)} — Y 翻转</li>
 *   <li>视口尺寸取 {@code Minecraft.displayWidth / displayHeight}，与旧层对齐。</li>
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

    static {
        Class<?> mc = null;
        Class<?> kc = null;
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
        MOUSE_CLASS = mc;
        KEYBOARD_CLASS = kc;
        MOUSE_IS_CREATED = findStaticMethod(MOUSE_CLASS, "isCreated");
        MOUSE_GET_X = findStaticMethod(MOUSE_CLASS, "getX");
        MOUSE_GET_Y = findStaticMethod(MOUSE_CLASS, "getY");
        MOUSE_IS_BUTTON_DOWN = findStaticMethod(MOUSE_CLASS, "isButtonDown", int.class);
        MOUSE_GET_DWHEEL = findStaticMethod(MOUSE_CLASS, "getDWheel");
        MOUSE_TOTAL_SCROLL_AMOUNT = findStaticField(MOUSE_CLASS, "totalScrollAmount");
        KEYBOARD_IS_CREATED = findStaticMethod(KEYBOARD_CLASS, "isCreated");
        KEYBOARD_IS_KEY_DOWN = findStaticMethod(KEYBOARD_CLASS, "isKeyDown", int.class);
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
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.displayWidth <= 0) return 0;
        int rawX = invokeInt(MOUSE_GET_X, 0);
        // L3 注意：clamp 上界 displayWidth（宽像素数）为排他上界，
        // 有效坐标 [0, displayWidth-1]。clamp 到此值意味着允许 displayWidth
        // 作为上界，与旧层 LwjglxPollingInputBackend 一致。
        return clamp(rawX, 0, mc.displayWidth);
    }

    @Override
    public int mouseY() {
        if (MOUSE_CLASS == null) return 0;
        if (!invokeBoolean(MOUSE_IS_CREATED, false)) return 0;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.displayHeight <= 0) return 0;
        int rawY = invokeInt(MOUSE_GET_Y, 0);
        // Y 翻转：LWJGL 原点左下 → UI 原点左上
        return clamp(mc.displayHeight - rawY - 1, 0, mc.displayHeight);
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
        if (MOUSE_TOTAL_SCROLL_AMOUNT != null) {
            return readStaticDoubleField(MOUSE_TOTAL_SCROLL_AMOUNT, 0.0);
        }
        // hasTotalScrollAmount == false 时才用 getDWheel
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
    public int logicalWidth() {
        Minecraft mc = Minecraft.getMinecraft();
        return (mc != null && mc.displayWidth > 0) ? mc.displayWidth : 854;
    }

    @Override
    public int logicalHeight() {
        Minecraft mc = Minecraft.getMinecraft();
        return (mc != null && mc.displayHeight > 0) ? mc.displayHeight : 480;
    }

    @Override
    public long nowNanos() {
        return System.nanoTime();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
