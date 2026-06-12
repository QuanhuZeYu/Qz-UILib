package club.heiqi.uilib.ui.input;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjglx.Sys;

import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;

/**
 * 通过反射桥接 lwjgl3ify `InputEvents` 的输入后端。
 */
final class Lwjgl3ifyInputBackend implements UiInputBackend {

    private static final Logger LOG = LogManager.getLogger("QzUiLib/Lwjgl3ifyInputBackend");
    private static final AtomicBoolean UNAVAILABLE_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean INPUT_FIELD_REFLECTION_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean INPUT_EVENTS_METHOD_LOGGED = new AtomicBoolean(false);
    private static final String INPUT_EVENTS_CLASS_NAME = "me.eigenraven.lwjgl3ify.api.InputEvents";
    private static final String KEYBOARD_LISTENER_CLASS_NAME =
            "me.eigenraven.lwjgl3ify.api.InputEvents$KeyboardListener";
    private static final short SDL_KMOD_LSHIFT = 0x0001;
    private static final short SDL_KMOD_RSHIFT = 0x0002;
    private static final short SDL_KMOD_LCTRL = 0x0040;
    private static final short SDL_KMOD_RCTRL = 0x0080;
    private static final short SDL_KMOD_LALT = 0x0100;
    private static final short SDL_KMOD_RALT = 0x0200;
    private static final short SDL_KMOD_LGUI = 0x0400;
    private static final short SDL_KMOD_RGUI = 0x0800;

    private final UiInputService inputService;
    private final LwjglxPollingInputBackend pollingBackend;
    private final Class<?> inputEventsClass;
    private final Method addKeyboardListenerMethod;
    private final Object keyboardListener;

    private Lwjgl3ifyInputBackend(UiInputService inputService, Class<?> inputEventsClass,
            Method addKeyboardListenerMethod, Object keyboardListener) {
        this.inputService = inputService;
        this.pollingBackend = new LwjglxPollingInputBackend(inputService, false);
        this.inputEventsClass = inputEventsClass;
        this.addKeyboardListenerMethod = addKeyboardListenerMethod;
        this.keyboardListener = keyboardListener;
    }

    /**
     * 创建可用的 lwjgl3ify 输入后端。
     *
     * @param inputService 输入服务
     * @return 后端实例；当前环境缺少 `InputEvents` 时返回 null
     */
    static UiInputBackend create(UiInputService inputService) {
        try {
            Class<?> inputEventsClass = Class.forName(INPUT_EVENTS_CLASS_NAME);
            Class<?> keyboardListenerClass = Class.forName(KEYBOARD_LISTENER_CLASS_NAME);
            Method addKeyboardListenerMethod = inputEventsClass.getMethod("addKeyboardListener", keyboardListenerClass);
            Object keyboardListener = Proxy.newProxyInstance(keyboardListenerClass.getClassLoader(),
                    new Class<?>[] { keyboardListenerClass }, new KeyboardListenerInvocationHandler(inputService));
            return new Lwjgl3ifyInputBackend(inputService, inputEventsClass, addKeyboardListenerMethod,
                    keyboardListener);
        } catch (ClassNotFoundException exception) {
            logUnavailableOnce(exception);
            return null;
        } catch (NoSuchMethodException exception) {
            logUnavailableOnce(exception);
            return null;
        } catch (SecurityException exception) {
            logUnavailableOnce(exception);
            return null;
        } catch (IllegalArgumentException exception) {
            logUnavailableOnce(exception);
            return null;
        } catch (LinkageError error) {
            logUnavailableOnce(error);
            return null;
        }
    }

    @Override
    public void initialize() {
        pollingBackend.initialize();
        try {
            addKeyboardListenerMethod.invoke(null, keyboardListener);
        } catch (IllegalAccessException exception) {
            logInputEventsMethodFailureOnce("addKeyboardListener", exception);
        } catch (InvocationTargetException exception) {
            logInputEventsMethodFailureOnce("addKeyboardListener", exception);
        } catch (IllegalArgumentException exception) {
            logInputEventsMethodFailureOnce("addKeyboardListener", exception);
        }
    }

    @Override
    public void tick() {
        pollingBackend.tick();
    }

    @Override
    public void beginTextInput() {
        invokeInputEventsMethod("beginTextInput");
    }

    @Override
    public void endTextInput() {
        invokeInputEventsMethod("endTextInput");
    }

    @Override
    public UiInputFrame createImmediateKeyboardFrame() {
        return pollingBackend.createImmediateKeyboardFrame();
    }

    @Override
    public UiInputFrame createImmediateMouseFrame() {
        return pollingBackend.createImmediateMouseFrame();
    }

    private void invokeInputEventsMethod(String methodName) {
        try {
            Method method = inputEventsClass.getMethod(methodName);
            method.invoke(null);
        } catch (NoSuchMethodException exception) {
            logInputEventsMethodFailureOnce(methodName, exception);
        } catch (IllegalAccessException exception) {
            logInputEventsMethodFailureOnce(methodName, exception);
        } catch (InvocationTargetException exception) {
            logInputEventsMethodFailureOnce(methodName, exception);
        } catch (SecurityException exception) {
            logInputEventsMethodFailureOnce(methodName, exception);
        } catch (IllegalArgumentException exception) {
            logInputEventsMethodFailureOnce(methodName, exception);
        }
    }

    private static void handleKeyEvent(UiInputService inputService, Object event) {
        int keyCode = readIntField(event, "lwjgl2KeyCode", 0);
        UiKeyEvent.Action action = mapAction(readObjectField(event, "action", null));
        if (inputService.isSuppressedCollectedKeyEvent(keyCode, action)) {
            return;
        }
        int glfwKeyCode = readFirstIntField(event, "glfwKeyCode", "sdlKeyCode", 0);
        int glfwScanCode = readFirstIntField(event, "glfwScanCode", "sdlScanCode", 0);
        short modifierMask = readShortField(event, "sdlKeyModifiers", (short) 0);

        inputService.addKeyEvent(new UiKeyEvent(keyCode, glfwKeyCode, glfwScanCode, action,
                readBooleanField(event, "controlPressed", hasAnyFlag(modifierMask, SDL_KMOD_LCTRL, SDL_KMOD_RCTRL)),
                readBooleanField(event, "shiftPressed", hasAnyFlag(modifierMask, SDL_KMOD_LSHIFT, SDL_KMOD_RSHIFT)),
                readBooleanField(event, "altPressed", hasAnyFlag(modifierMask, SDL_KMOD_LALT, SDL_KMOD_RALT)),
                readBooleanField(event, "superPressed", hasAnyFlag(modifierMask, SDL_KMOD_LGUI, SDL_KMOD_RGUI)),
                Sys.getNanoTime()));
    }

    private static void handleTextEvent(UiInputService inputService, Object event) {
        Object textValue = readObjectField(event, "text", null);
        String text = textValue instanceof String ? (String) textValue : null;
        if (text == null || text.isEmpty() || inputService.isSuppressedCollectedText(text)) {
            return;
        }
        inputService.addTextEvent(new UiTextInputEvent(text, Sys.getNanoTime()));
    }

    private static UiKeyEvent.Action mapAction(Object action) {
        String actionName = action == null ? "" : String.valueOf(action);
        if ("RELEASED".equals(actionName)) {
            return UiKeyEvent.Action.RELEASED;
        }
        if ("REPEATED".equals(actionName)) {
            return UiKeyEvent.Action.REPEATED;
        }
        return UiKeyEvent.Action.PRESSED;
    }

    private static int readFirstIntField(Object instance, String firstFieldName, String secondFieldName, int fallback) {
        Field firstField = findField(instance, firstFieldName);
        if (firstField != null) {
            return readIntField(firstField, instance, fallback);
        }
        Field secondField = findField(instance, secondFieldName);
        if (secondField != null) {
            return readIntField(secondField, instance, fallback);
        }
        logInputFieldReflectionFailureOnce(firstFieldName + "/" + secondFieldName, null);
        return fallback;
    }

    private static int readIntField(Object instance, String fieldName, int fallback) {
        Field field = findField(instance, fieldName);
        return field == null ? fallback : readIntField(field, instance, fallback);
    }

    private static int readIntField(Field field, Object instance, int fallback) {
        try {
            return field.getInt(instance);
        } catch (IllegalAccessException exception) {
            logInputFieldReflectionFailureOnce(field.getName(), exception);
            return fallback;
        } catch (IllegalArgumentException exception) {
            logInputFieldReflectionFailureOnce(field.getName(), exception);
            return fallback;
        }
    }

    private static short readShortField(Object instance, String fieldName, short fallback) {
        Field field = findField(instance, fieldName);
        if (field == null) {
            return fallback;
        }
        try {
            return field.getShort(instance);
        } catch (IllegalAccessException exception) {
            logInputFieldReflectionFailureOnce(fieldName, exception);
            return fallback;
        } catch (IllegalArgumentException exception) {
            logInputFieldReflectionFailureOnce(fieldName, exception);
            return fallback;
        }
    }

    private static boolean readBooleanField(Object instance, String fieldName, boolean fallback) {
        Field field = findField(instance, fieldName);
        if (field == null) {
            return fallback;
        }
        try {
            return field.getBoolean(instance);
        } catch (IllegalAccessException exception) {
            logInputFieldReflectionFailureOnce(fieldName, exception);
            return fallback;
        } catch (IllegalArgumentException exception) {
            logInputFieldReflectionFailureOnce(fieldName, exception);
            return fallback;
        }
    }

    private static Object readObjectField(Object instance, String fieldName, Object fallback) {
        Field field = findField(instance, fieldName);
        if (field == null) {
            return fallback;
        }
        try {
            return field.get(instance);
        } catch (IllegalAccessException exception) {
            logInputFieldReflectionFailureOnce(fieldName, exception);
            return fallback;
        } catch (IllegalArgumentException exception) {
            logInputFieldReflectionFailureOnce(fieldName, exception);
            return fallback;
        }
    }

    private static Field findField(Object instance, String fieldName) {
        if (instance == null) {
            return null;
        }
        try {
            return instance.getClass().getField(fieldName);
        } catch (NoSuchFieldException exception) {
            return null;
        } catch (SecurityException exception) {
            logInputFieldReflectionFailureOnce(fieldName, exception);
            return null;
        }
    }

    private static boolean hasAnyFlag(short mask, short firstFlag, short secondFlag) {
        return (mask & firstFlag) != 0 || (mask & secondFlag) != 0;
    }

    private static void logUnavailableOnce(Throwable throwable) {
        if (UNAVAILABLE_LOGGED.compareAndSet(false, true)) {
            LOG.debug("UILib 未检测到可用的 lwjgl3ify InputEvents，切换到 LWJGLX 轮询输入后端", throwable);
        }
    }

    private static void logInputFieldReflectionFailureOnce(String fieldName, Throwable throwable) {
        if (INPUT_FIELD_REFLECTION_LOGGED.compareAndSet(false, true)) {
            LOG.debug("UILib 输入字段反射读取失败，已降级为 fallback：fieldName={}", fieldName, throwable);
        }
    }

    private static void logInputEventsMethodFailureOnce(String methodName, Throwable throwable) {
        if (INPUT_EVENTS_METHOD_LOGGED.compareAndSet(false, true)) {
            LOG.debug("UILib 输入事件方法反射调用失败，当前实现未提供该方法：methodName={}", methodName,
                    throwable);
        }
    }

    private static final class KeyboardListenerInvocationHandler implements InvocationHandler {

        private final UiInputService inputService;

        private KeyboardListenerInvocationHandler(UiInputService inputService) {
            this.inputService = inputService;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, args);
            }
            if (args == null || args.length == 0 || args[0] == null) {
                return null;
            }
            if ("onKeyEvent".equals(method.getName())) {
                handleKeyEvent(inputService, args[0]);
                return null;
            }
            if ("onTextEvent".equals(method.getName())) {
                handleTextEvent(inputService, args[0]);
                return null;
            }
            return null;
        }

        private Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
            if ("toString".equals(method.getName())) {
                return "QzUiLib Lwjgl3ifyInputBackend listener";
            }
            if ("hashCode".equals(method.getName())) {
                return Integer.valueOf(System.identityHashCode(proxy));
            }
            if ("equals".equals(method.getName())) {
                return Boolean.valueOf(args != null && args.length == 1 && proxy == args[0]);
            }
            return null;
        }
    }
}
