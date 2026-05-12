package club.heiqi.uilib.ui.input;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import club.heiqi.uilib.ui.screen.BaseScreen;

/**
 * 检查当前原生界面是否正由 Minecraft 文本输入框持有键盘焦点。
 */
public final class UiNativeTextInputInspector {

    private static final int MAX_SCAN_DEPTH = 2;
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
        if (screen == null || screen instanceof BaseScreen) {
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
        if (screen == null || screen instanceof BaseScreen) {
            return false;
        }
        NativeTextInputAdapter adapter = findAdapter(screen);
        if (adapter != null) {
            return adapter.blurFocusedTextInputs(screen);
        }
        return blurFocusedTextInputsReflectively(screen, 0, new IdentityHashMap<Object, Boolean>());
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
                } catch (ReflectiveOperationException ignored) {
                    // 反射失败时忽略该字段，继续检查其他候选输入框。
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
                } catch (ReflectiveOperationException ignored) {
                    // 反射失败时忽略该字段，继续清理其他候选输入框。
                }
            }
        }
        return changed;
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

        private GuiTextField resolveTextField(GuiScreen screen) {
            if (inputField == null || screen == null) {
                return null;
            }
            try {
                Object value = inputField.get(screen);
                return value instanceof GuiTextField ? (GuiTextField) value : null;
            } catch (ReflectiveOperationException ignored) {
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
