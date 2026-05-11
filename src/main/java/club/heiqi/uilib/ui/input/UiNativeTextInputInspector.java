package club.heiqi.uilib.ui.input;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import club.heiqi.uilib.ui.screen.BaseScreen;

/**
 * 检查当前原生界面是否正由 Minecraft 文本输入框持有键盘焦点。
 */
public final class UiNativeTextInputInspector {

    private static final int MAX_SCAN_DEPTH = 2;

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
        return hasFocusedTextInput((Object) screen, 0, new IdentityHashMap<Object, Boolean>());
    }

    private static boolean hasFocusedTextInput(Object value, int depth, IdentityHashMap<Object, Boolean> visited) {
        if (value == null || depth > MAX_SCAN_DEPTH || visited.containsKey(value)) {
            return false;
        }
        visited.put(value, Boolean.TRUE);
        if (value instanceof GuiTextField) {
            return ((GuiTextField) value).isFocused();
        }
        if (value instanceof Iterable<?>) {
            for (Object entry : (Iterable<?>) value) {
                if (hasFocusedTextInput(entry, depth + 1, visited)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Map<?, ?>) {
            for (Object entry : ((Map<?, ?>) value).values()) {
                if (hasFocusedTextInput(entry, depth + 1, visited)) {
                    return true;
                }
            }
            return false;
        }
        Class<?> valueClass = value.getClass();
        if (valueClass.isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                if (hasFocusedTextInput(Array.get(value, index), depth + 1, visited)) {
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
                    if (hasFocusedTextInput(field.get(value), depth + 1, visited)) {
                        return true;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // 反射失败时忽略该字段，继续检查其他候选输入框。
                }
            }
        }
        return false;
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
}
