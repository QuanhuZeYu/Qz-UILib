package club.heiqi.uilib.ui.input;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 通过反射访问当前客户端可用的 LWJGL 输入运行时。
 */
final class LwjglInputRuntime {

    private static final Logger LOG = LogManager.getLogger("QzUiLib/LwjglInputRuntime");
    private static final String LWJGLX_KEYBOARD_CLASS_NAME = "org.lwjglx.input.Keyboard";
    private static final String LWJGL2_KEYBOARD_CLASS_NAME = "org.lwjgl.input.Keyboard";
    private static final String LWJGLX_MOUSE_CLASS_NAME = "org.lwjglx.input.Mouse";
    private static final String LWJGL2_MOUSE_CLASS_NAME = "org.lwjgl.input.Mouse";
    private static final AtomicBoolean KEYBOARD_RESOLUTION_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean MOUSE_RESOLUTION_LOGGED = new AtomicBoolean(false);
    private static final ConcurrentHashMap<String, Boolean> METHOD_LOG_REGISTRY = new ConcurrentHashMap<String, Boolean>();
    private static final ConcurrentHashMap<String, Boolean> FIELD_LOG_REGISTRY = new ConcurrentHashMap<String, Boolean>();
    private static final KeyboardRuntime KEYBOARD = KeyboardRuntime.create();
    private static final MouseRuntime MOUSE = MouseRuntime.create();

    private LwjglInputRuntime() {}

    /**
     * 返回单调递增的输入事件时间戳。
     *
     * @return 纳秒时间戳
     */
    static long getNanoTime() {
        return System.nanoTime();
    }

    /**
     * 返回键盘运行时桥。
     *
     * @return 键盘运行时桥
     */
    static KeyboardRuntime keyboard() {
        return KEYBOARD;
    }

    /**
     * 返回鼠标运行时桥。
     *
     * @return 鼠标运行时桥
     */
    static MouseRuntime mouse() {
        return MOUSE;
    }

    /**
     * 检查键盘运行时是否在初始化时成功解析。
     *
     * <p>该方法只检查反射解析是否成功，不调用原生方法。可用于诊断或条件逻辑。</p>
     *
     * @return true 如果至少有一个键盘类（org.lwjglx 或 org.lwjgl）成功加载
     */
    static boolean isKeyboardRuntimeAvailable() {
        return KEYBOARD.isCreatedMethod != null;
    }

    /**
     * 检查鼠标运行时是否在初始化时成功解析。
     *
     * <p>该方法只检查反射解析是否成功，不调用原生方法。可用于诊断或条件逻辑。</p>
     *
     * @return true 如果至少有一个鼠标类（org.lwjglx 或 org.lwjgl）成功加载
     */
    static boolean isMouseRuntimeAvailable() {
        return MOUSE.isCreatedMethod != null;
    }

    private static Class<?> resolveRuntimeClass(String runtimeName, AtomicBoolean logFlag, String... classNames) {
        for (String className : classNames) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException exception) {
                // 继续尝试下一个可选运行时。
            } catch (SecurityException exception) {
                logResolutionFailureOnce(runtimeName, className, logFlag, exception);
            } catch (LinkageError error) {
                logResolutionFailureOnce(runtimeName, className, logFlag, error);
            }
        }
        logResolutionFailureOnce(runtimeName, classNames.length == 0 ? "" : classNames[classNames.length - 1],
                logFlag, null);
        return null;
    }

    private static Method findMethod(Class<?> ownerClass, String methodName, Class<?>... parameterTypes) {
        if (ownerClass == null) {
            return null;
        }
        try {
            return ownerClass.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException exception) {
            return null;
        } catch (SecurityException exception) {
            logMethodInvocationFailureOnce(ownerClass.getName() + "." + methodName, exception);
            return null;
        }
    }

    private static Field findField(Class<?> ownerClass, String fieldName) {
        if (ownerClass == null) {
            return null;
        }
        try {
            return ownerClass.getField(fieldName);
        } catch (NoSuchFieldException exception) {
            return null;
        } catch (SecurityException exception) {
            logFieldInvocationFailureOnce(ownerClass.getName() + "." + fieldName, exception);
            return null;
        }
    }

    private static boolean invokeBoolean(Method method, boolean fallback, Object... args) {
        Object value = invoke(method, args);
        return value instanceof Boolean ? ((Boolean) value).booleanValue() : fallback;
    }

    private static int invokeInt(Method method, int fallback, Object... args) {
        Object value = invoke(method, args);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static void invokeVoid(Method method, Object... args) {
        invoke(method, args);
    }

    private static Object invoke(Method method, Object... args) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(null, args);
        } catch (IllegalAccessException exception) {
            logMethodInvocationFailureOnce(method.getDeclaringClass().getName() + "." + method.getName(), exception);
            return null;
        } catch (InvocationTargetException exception) {
            logMethodInvocationFailureOnce(method.getDeclaringClass().getName() + "." + method.getName(),
                    exception.getCause() == null ? exception : exception.getCause());
            return null;
        } catch (IllegalArgumentException exception) {
            logMethodInvocationFailureOnce(method.getDeclaringClass().getName() + "." + method.getName(), exception);
            return null;
        } catch (RuntimeException exception) {
            logMethodInvocationFailureOnce(method.getDeclaringClass().getName() + "." + method.getName(), exception);
            return null;
        } catch (LinkageError error) {
            logMethodInvocationFailureOnce(method.getDeclaringClass().getName() + "." + method.getName(), error);
            return null;
        }
    }

    private static void logResolutionFailureOnce(String runtimeName, String className, AtomicBoolean logFlag,
            Throwable throwable) {
        if (logFlag.compareAndSet(false, true)) {
            LOG.debug("UILib 未找到可用的 {} 输入运行时，已降级为无该输入源：className={}", runtimeName, className,
                    throwable);
        }
    }

    private static void logMethodInvocationFailureOnce(String methodName, Throwable throwable) {
        if (METHOD_LOG_REGISTRY.putIfAbsent(methodName, Boolean.TRUE) == null) {
            LOG.debug("UILib 原生输入方法反射调用失败，已按无事件处理：methodName={}", methodName, throwable);
        }
    }

    private static void logFieldInvocationFailureOnce(String fieldName, Throwable throwable) {
        if (FIELD_LOG_REGISTRY.putIfAbsent(fieldName, Boolean.TRUE) == null) {
            LOG.debug("UILib 原生输入字段反射读取失败，已按无扩展字段处理：fieldName={}", fieldName, throwable);
        }
    }

    /**
     * 键盘运行时反射桥。
     */
    static final class KeyboardRuntime {

        private final Method isCreatedMethod;
        private final Method pollMethod;
        private final Method nextMethod;
        private final Method isKeyDownMethod;
        private final Method getEventKeyStateMethod;
        private final Method getEventKeyMethod;
        private final Method isRepeatEventMethod;

        private KeyboardRuntime(Class<?> keyboardClass) {
            this.isCreatedMethod = findMethod(keyboardClass, "isCreated");
            this.pollMethod = findMethod(keyboardClass, "poll");
            this.nextMethod = findMethod(keyboardClass, "next");
            this.isKeyDownMethod = findMethod(keyboardClass, "isKeyDown", Integer.TYPE);
            this.getEventKeyStateMethod = findMethod(keyboardClass, "getEventKeyState");
            this.getEventKeyMethod = findMethod(keyboardClass, "getEventKey");
            this.isRepeatEventMethod = findMethod(keyboardClass, "isRepeatEvent");
        }

        private static KeyboardRuntime create() {
            return new KeyboardRuntime(resolveRuntimeClass("键盘", KEYBOARD_RESOLUTION_LOGGED,
                    LWJGLX_KEYBOARD_CLASS_NAME, LWJGL2_KEYBOARD_CLASS_NAME));
        }

        boolean isCreated() {
            return invokeBoolean(isCreatedMethod, false);
        }

        void poll() {
            invokeVoid(pollMethod);
        }

        boolean next() {
            return invokeBoolean(nextMethod, false);
        }

        boolean isKeyDown(int keyCode) {
            return invokeBoolean(isKeyDownMethod, false, Integer.valueOf(keyCode));
        }

        boolean getEventKeyState() {
            return invokeBoolean(getEventKeyStateMethod, false);
        }

        int getEventKey() {
            return invokeInt(getEventKeyMethod, 0);
        }

        boolean isRepeatEvent() {
            return invokeBoolean(isRepeatEventMethod, false);
        }
    }

    /**
     * 鼠标运行时反射桥。
     */
    static final class MouseRuntime {

        private final Method isCreatedMethod;
        private final Method pollMethod;
        private final Method nextMethod;
        private final Method isGrabbedMethod;
        private final Method getXMethod;
        private final Method getYMethod;
        private final Method getDWheelMethod;
        private final Method isButtonDownMethod;
        private final Method getEventXMethod;
        private final Method getEventYMethod;
        private final Method getEventButtonMethod;
        private final Method getEventButtonStateMethod;
        private final Method getEventDWheelMethod;
        private final Field totalScrollAmountField;

        private MouseRuntime(Class<?> mouseClass) {
            this.isCreatedMethod = findMethod(mouseClass, "isCreated");
            this.pollMethod = findMethod(mouseClass, "poll");
            this.nextMethod = findMethod(mouseClass, "next");
            this.isGrabbedMethod = findMethod(mouseClass, "isGrabbed");
            this.getXMethod = findMethod(mouseClass, "getX");
            this.getYMethod = findMethod(mouseClass, "getY");
            this.getDWheelMethod = findMethod(mouseClass, "getDWheel");
            this.isButtonDownMethod = findMethod(mouseClass, "isButtonDown", Integer.TYPE);
            this.getEventXMethod = findMethod(mouseClass, "getEventX");
            this.getEventYMethod = findMethod(mouseClass, "getEventY");
            this.getEventButtonMethod = findMethod(mouseClass, "getEventButton");
            this.getEventButtonStateMethod = findMethod(mouseClass, "getEventButtonState");
            this.getEventDWheelMethod = findMethod(mouseClass, "getEventDWheel");
            this.totalScrollAmountField = findField(mouseClass, "totalScrollAmount");
        }

        private static MouseRuntime create() {
            return new MouseRuntime(resolveRuntimeClass("鼠标", MOUSE_RESOLUTION_LOGGED,
                    LWJGLX_MOUSE_CLASS_NAME, LWJGL2_MOUSE_CLASS_NAME));
        }

        boolean isCreated() {
            return invokeBoolean(isCreatedMethod, false);
        }

        void poll() {
            invokeVoid(pollMethod);
        }

        boolean next() {
            return invokeBoolean(nextMethod, false);
        }

        boolean isGrabbed() {
            return invokeBoolean(isGrabbedMethod, false);
        }

        int getX() {
            return invokeInt(getXMethod, 0);
        }

        int getY() {
            return invokeInt(getYMethod, 0);
        }

        int getDWheel() {
            return invokeInt(getDWheelMethod, 0);
        }

        boolean isButtonDown(int button) {
            return invokeBoolean(isButtonDownMethod, false, Integer.valueOf(button));
        }

        int getEventX() {
            return invokeInt(getEventXMethod, 0);
        }

        int getEventY() {
            return invokeInt(getEventYMethod, 0);
        }

        int getEventButton() {
            return invokeInt(getEventButtonMethod, -1);
        }

        boolean getEventButtonState() {
            return invokeBoolean(getEventButtonStateMethod, false);
        }

        int getEventDWheel() {
            return invokeInt(getEventDWheelMethod, 0);
        }

        boolean hasTotalScrollAmount() {
            return totalScrollAmountField != null;
        }

        double readTotalScrollAmount() {
            if (totalScrollAmountField == null) {
                return 0.0D;
            }
            try {
                Object value = totalScrollAmountField.get(null);
                return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
            } catch (IllegalAccessException exception) {
                logFieldInvocationFailureOnce(totalScrollAmountField.getDeclaringClass().getName() + "."
                        + totalScrollAmountField.getName(), exception);
                return 0.0D;
            } catch (IllegalArgumentException exception) {
                logFieldInvocationFailureOnce(totalScrollAmountField.getDeclaringClass().getName() + "."
                        + totalScrollAmountField.getName(), exception);
                return 0.0D;
            } catch (RuntimeException exception) {
                logFieldInvocationFailureOnce(totalScrollAmountField.getDeclaringClass().getName() + "."
                        + totalScrollAmountField.getName(), exception);
                return 0.0D;
            } catch (LinkageError error) {
                logFieldInvocationFailureOnce(totalScrollAmountField.getDeclaringClass().getName() + "."
                        + totalScrollAmountField.getName(), error);
                return 0.0D;
            }
        }
    }
}
