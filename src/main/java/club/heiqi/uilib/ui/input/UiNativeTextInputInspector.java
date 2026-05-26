package club.heiqi.uilib.ui.input;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 检查当前原生界面是否正由 Minecraft 文本输入框持有键盘焦点。
 */
public final class UiNativeTextInputInspector {

    private static final int MAX_SCAN_DEPTH = 2;
    private static final Logger LOG = LogManager.getLogger("QzUiLib/UiNativeTextInputInspector");
    private static final AtomicBoolean GUI_CHAT_INPUT_FIELD_REFLECTION_LOGGED = new AtomicBoolean(false);
    /**
     * 反射深扫遇到的每个 (声明类 + 字段) 失败仅记录一次。
     *
     * <p>用 {@link ConcurrentHashMap#putIfAbsent} 做访问去重，避免同一个字段在每次屏幕扫描里
     * 重复刷屏，但又能保证每个真正出过问题的字段都至少被诊断到一次。</p>
     */
    private static final ConcurrentHashMap<String, Boolean> SCAN_FIELD_REFLECTION_LOGGED =
            new ConcurrentHashMap<String, Boolean>();
    private static final NativeTextInputAdapter[] ADAPTERS = new NativeTextInputAdapter[] {
            new GuiChatTextInputAdapter() };

    private UiNativeTextInputInspector() {}

    /**
     * 判断当前屏幕是否存在已聚焦的原生文本输入框。
     *
     * @param screen 当前屏幕
     * @return 是否存在已聚焦的原生文本输入框
     */
    public static boolean hasFocusedTextInput(GuiScreen screen) {
        if (screen == null || screen instanceof UiManagedInputScreen) {
            return false;
        }
        NativeTextInputAdapter adapter = findAdapter(screen);
        if (adapter != null) {
            return adapter.hasFocusedTextInput(screen);
        }
        return hasFocusedTextInputReflectively(screen, 0, new IdentityHashMap<Object, Boolean>());
    }

    /**
     * 清除当前屏幕内所有原生文本输入框的焦点。
     *
     * @param screen 当前屏幕
     * @return 是否实际清除了至少一个文本输入框焦点
     */
    public static boolean blurFocusedTextInputs(GuiScreen screen) {
        if (screen == null || screen instanceof UiManagedInputScreen) {
            return false;
        }
        NativeTextInputAdapter adapter = findAdapter(screen);
        if (adapter != null) {
            return adapter.blurFocusedTextInputs(screen);
        }
        return blurFocusedTextInputsReflectively(screen, 0, new IdentityHashMap<Object, Boolean>());
    }

    /**
     * 优先恢复当前屏幕上最适合接回输入权的原生文本输入框焦点。
     *
     * <p>当前仅对存在显式适配器的原生界面生效；未知页面仍保持只释放 HUD、不主动回填原生焦点的策略。</p>
     *
     * @param screen 当前屏幕
     * @return 是否实际恢复了文本输入框焦点
     */
    public static boolean focusPreferredTextInput(GuiScreen screen) {
        if (screen == null || screen instanceof UiManagedInputScreen) {
            return false;
        }
        NativeTextInputAdapter adapter = findAdapter(screen);
        return adapter != null && adapter.focusPreferredTextInput(screen);
    }

    /**
     * 判断当前宿主是否属于支持原生文本框回焦的已知页面。
     *
     * <p>该入口仅供宿主输入协调链内部使用，不作为稳定 API 承诺。</p>
     *
     * @param screen 当前屏幕实例
     * @param screenClassName 当前屏幕类名
     * @return 是否支持原生文本框回焦
     */
    public static boolean supportsPreferredTextInputRefocus(Object screen, String screenClassName) {
        return screen instanceof GuiChat || "net.minecraft.client.gui.GuiChat".equals(screenClassName);
    }

    private static NativeTextInputAdapter findAdapter(GuiScreen screen) {
        if (screen == null) {
            return null;
        }
        for (NativeTextInputAdapter adapter : ADAPTERS) {
            if (adapter != null && adapter.supports(screen)) {
                return adapter;
            }
        }
        return null;
    }

    private static boolean hasFocusedTextInputReflectively(Object value, int depth,
            IdentityHashMap<Object, Boolean> visited) {
        if (value == null || depth > MAX_SCAN_DEPTH || visited.containsKey(value)) {
            return false;
        }
        visited.put(value, Boolean.TRUE);
        if (value instanceof GuiTextField) {
            return ((GuiTextField) value).isFocused();
        }
        if (value instanceof Iterable<?>) {
            for (Object entry : (Iterable<?>) value) {
                if (hasFocusedTextInputReflectively(entry, depth + 1, visited)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Map<?, ?>) {
            for (Object entry : ((Map<?, ?>) value).values()) {
                if (hasFocusedTextInputReflectively(entry, depth + 1, visited)) {
                    return true;
                }
            }
            return false;
        }
        Class<?> valueClass = value.getClass();
        if (valueClass.isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                if (hasFocusedTextInputReflectively(Array.get(value, index), depth + 1, visited)) {
                    return true;
                }
            }
            return false;
        }
        if (isJdkValueObject(valueClass)) {
            return false;
        }
        for (Class<?> currentClass = valueClass;
                currentClass != null && currentClass != Object.class;
                currentClass = currentClass.getSuperclass()) {
            Field[] fields = currentClass.getDeclaredFields();
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    if (hasFocusedTextInputReflectively(field.get(value), depth + 1, visited)) {
                        return true;
                    }
                } catch (ReflectiveOperationException exception) {
                    logScanFieldReflectionFailureOnce(field, "hasFocusedTextInput", exception);
                }
            }
        }
        return false;
    }

    private static boolean blurFocusedTextInputsReflectively(Object value, int depth,
            IdentityHashMap<Object, Boolean> visited) {
        if (value == null || depth > MAX_SCAN_DEPTH || visited.containsKey(value)) {
            return false;
        }
        visited.put(value, Boolean.TRUE);
        if (value instanceof GuiTextField) {
            GuiTextField textField = (GuiTextField) value;
            boolean focused = textField.isFocused();
            if (focused) {
                textField.setFocused(false);
            }
            return focused;
        }
        boolean changed = false;
        if (value instanceof Iterable<?>) {
            for (Object entry : (Iterable<?>) value) {
                changed |= blurFocusedTextInputsReflectively(entry, depth + 1, visited);
            }
            return changed;
        }
        if (value instanceof Map<?, ?>) {
            for (Object entry : ((Map<?, ?>) value).values()) {
                changed |= blurFocusedTextInputsReflectively(entry, depth + 1, visited);
            }
            return changed;
        }
        Class<?> valueClass = value.getClass();
        if (valueClass.isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                changed |= blurFocusedTextInputsReflectively(Array.get(value, index), depth + 1, visited);
            }
            return changed;
        }
        if (isJdkValueObject(valueClass)) {
            return false;
        }
        for (Class<?> currentClass = valueClass;
                currentClass != null && currentClass != Object.class;
                currentClass = currentClass.getSuperclass()) {
            Field[] fields = currentClass.getDeclaredFields();
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    changed |= blurFocusedTextInputsReflectively(field.get(value), depth + 1, visited);
                } catch (ReflectiveOperationException exception) {
                    logScanFieldReflectionFailureOnce(field, "blurFocusedTextInputs", exception);
                }
            }
        }
        return changed;
    }

    /**
     * 反射深扫遇到字段访问失败时的一次性诊断日志。
     *
     * <p>按 (声明类全名 + 字段名 + 用途) 做去重，保证同一字段在同一用途下只记录一次。
     * 避免同一个屏幕扫描里递归深扫引发数十/上百次重复刷屏。</p>
     *
     * @param field 反射失败的字段
     * @param scanContext 扫描用途（hasFocusedTextInput / blurFocusedTextInputs）
     * @param exception 反射异常
     */
    private static void logScanFieldReflectionFailureOnce(Field field, String scanContext,
            ReflectiveOperationException exception) {
        Class<?> declaringClass = field.getDeclaringClass();
        String dedupKey = declaringClass.getName() + "#" + field.getName() + "@" + scanContext;
        if (SCAN_FIELD_REFLECTION_LOGGED.putIfAbsent(dedupKey, Boolean.TRUE) == null) {
            LOG.debug("UILib 反射深扫读取 {}#{} 失败 ({})，已跳过该字段。", declaringClass.getName(), field.getName(),
                    scanContext, exception);
        }
    }

    private static boolean isJdkValueObject(Class<?> valueClass) {
        if (valueClass == null || valueClass.isPrimitive() || valueClass.isEnum()) {
            return true;
        }
        Package valuePackage = valueClass.getPackage();
        if (valuePackage == null) {
            return false;
        }
        String packageName = valuePackage.getName();
        return packageName.startsWith("java.") || packageName.startsWith("javax.");
    }

    private interface NativeTextInputAdapter {

        boolean supports(GuiScreen screen);

        boolean hasFocusedTextInput(GuiScreen screen);

        boolean blurFocusedTextInputs(GuiScreen screen);

        boolean focusPreferredTextInput(GuiScreen screen);
    }

    private static final class GuiChatTextInputAdapter implements NativeTextInputAdapter {

        private final Field inputField = findGuiTextFieldField(GuiChat.class, "inputField");

        @Override
        public boolean supports(GuiScreen screen) {
            return screen instanceof GuiChat;
        }

        @Override
        public boolean hasFocusedTextInput(GuiScreen screen) {
            GuiTextField textField = resolveTextField(screen);
            return textField != null && textField.isFocused();
        }

        @Override
        public boolean blurFocusedTextInputs(GuiScreen screen) {
            GuiTextField textField = resolveTextField(screen);
            if (textField == null || !textField.isFocused()) {
                return false;
            }
            textField.setFocused(false);
            return true;
        }

        @Override
        public boolean focusPreferredTextInput(GuiScreen screen) {
            GuiTextField textField = resolveTextField(screen);
            if (textField == null) {
                return false;
            }
            if (textField.isFocused()) {
                return true;
            }
            textField.setFocused(true);
            return textField.isFocused();
        }

        private GuiTextField resolveTextField(GuiScreen screen) {
            if (inputField == null || screen == null) {
                return null;
            }
            try {
                Object value = inputField.get(screen);
                return value instanceof GuiTextField ? (GuiTextField) value : null;
            } catch (ReflectiveOperationException exception) {
                if (GUI_CHAT_INPUT_FIELD_REFLECTION_LOGGED.compareAndSet(false, true)) {
                    LOG.debug("UILib 反射读取 GuiChat.inputField 失败，已降级为不识别原生 chat 输入框焦点。", exception);
                }
                return null;
            }
        }
    }

    private static Field findGuiTextFieldField(Class<?> ownerClass, String preferredName) {
        for (Class<?> currentClass = ownerClass;
                currentClass != null && currentClass != Object.class;
                currentClass = currentClass.getSuperclass()) {
            Field namedField = tryFindDeclaredField(currentClass, preferredName);
            if (namedField != null && GuiTextField.class.isAssignableFrom(namedField.getType())) {
                namedField.setAccessible(true);
                return namedField;
            }
            Field[] declaredFields = currentClass.getDeclaredFields();
            for (Field field : declaredFields) {
                if (!GuiTextField.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    private static Field tryFindDeclaredField(Class<?> ownerClass, String fieldName) {
        try {
            return ownerClass.getDeclaredField(fieldName);
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }
}
