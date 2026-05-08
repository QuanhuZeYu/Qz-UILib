package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import net.minecraftforge.common.config.Property;

/**
 * Forge 配置模板的属性草稿解析与校验工具。
 */
final class ForgeConfigTemplatePropertyDrafts {

    private ForgeConfigTemplatePropertyDrafts() {}

    /**
     * 读取当前属性值对应的展示文本。
     *
     * @param property Forge 属性
     * @return 展示文本
     */
    static String readCurrentDisplayValue(Property property) {
        if (property == null) {
            return "";
        }
        return property.isList() ? joinList(property.getStringList()) : property.getString();
    }

    /**
     * 读取默认值对应的展示文本。
     *
     * @param property Forge 属性
     * @return 默认展示文本
     */
    static String readDefaultDisplayValue(Property property) {
        if (property == null) {
            return "";
        }
        return property.isList() ? joinList(property.getDefaults()) : property.getDefault();
    }

    /**
     * 生成输入框占位文本。
     *
     * @param property Forge 属性
     * @return 占位文本
     */
    static String resolvePlaceholder(Property property) {
        String defaultText = readDefaultDisplayValue(property);
        return defaultText.isEmpty() ? "输入配置值" : defaultText;
    }

    /**
     * 判断属性是否适合用预定义选项编辑器承载。
     *
     * @param property Forge 属性
     * @return 是否存在非空有效值列表
     */
    static boolean hasDiscreteValidValues(Property property) {
        if (property == null || property.isList()) {
            return false;
        }
        String[] validValues = property.getValidValues();
        return validValues != null && validValues.length > 0;
    }

    /**
     * 返回属性的有效值列表快照。
     *
     * @param property Forge 属性
     * @return 有效值列表；不存在时返回空数组
     */
    static String[] getValidValuesSnapshot(Property property) {
        if (property == null || property.getValidValues() == null) {
            return new String[0];
        }
        String[] validValues = property.getValidValues();
        return Arrays.copyOf(validValues, validValues.length);
    }

    /**
     * 返回当前值在有效值列表中的索引。
     *
     * @param property Forge 属性
     * @return 有效索引；找不到时返回 0
     */
    static int resolveSelectedValidValueIndex(Property property) {
        String[] validValues = getValidValuesSnapshot(property);
        if (validValues.length == 0) {
            return 0;
        }
        String currentValue = property == null ? "" : property.getString();
        for (int index = 0; index < validValues.length; index++) {
            if (Objects.equals(validValues[index], currentValue)) {
                return index;
            }
        }
        return 0;
    }

    /**
     * 估算输入框最大长度。
     *
     * @param property Forge 属性
     * @return 最大长度
     */
    static int resolveMaxLength(Property property) {
        if (property == null) {
            return 128;
        }
        if (property.isList()) {
            return Math.max(64, property.getMaxListLength() > 0 ? property.getMaxListLength() * 16 : 256);
        }
        return 160;
    }

    /**
     * 校验草稿文本是否可写回属性。
     *
     * @param property Forge 属性
     * @param draft 草稿文本
     * @return 错误文本；合法时返回 null
     */
    static String validateDraft(Property property, String draft) {
        if (property == null) {
            return "属性不存在。";
        }
        if (property.isList()) {
            return validateListDraft(property, draft);
        }
        if (property.getType() == Property.Type.INTEGER) {
            return validateIntegerDraft(property, draft);
        }
        if (property.getType() == Property.Type.DOUBLE) {
            return validateDoubleDraft(property, draft);
        }
        return validateStringDraft(property, draft == null ? "" : draft);
    }

    /**
     * 把草稿文本写回到属性。
     *
     * @param property Forge 属性
     * @param draft 草稿文本
     */
    static void applyDraft(Property property, String draft) {
        if (property == null) {
            return;
        }
        String resolvedDraft = draft == null ? "" : draft;
        if (property.isList()) {
            applyListDraft(property, resolvedDraft);
            return;
        }
        if (property.getType() == Property.Type.INTEGER) {
            property.set(parseInteger(resolvedDraft.trim()));
            return;
        }
        if (property.getType() == Property.Type.DOUBLE) {
            property.set(parseDouble(resolvedDraft.trim()));
            return;
        }
        property.set(resolvedDraft);
    }

    private static String validateIntegerDraft(Property property, String draft) {
        String trimmed = draft == null ? "" : draft.trim();
        if (trimmed.isEmpty()) {
            return "需要输入一个整数。";
        }
        try {
            int value = Integer.parseInt(trimmed);
            int minValue = parseInteger(property.getMinValue(), Integer.MIN_VALUE);
            int maxValue = parseInteger(property.getMaxValue(), Integer.MAX_VALUE);
            if (value < minValue || value > maxValue) {
                return "需要位于 " + minValue + " 到 " + maxValue + " 之间。";
            }
            return null;
        } catch (NumberFormatException exception) {
            return "需要输入一个整数。";
        }
    }

    private static String validateDoubleDraft(Property property, String draft) {
        String trimmed = draft == null ? "" : draft.trim();
        if (trimmed.isEmpty()) {
            return "需要输入一个小数。";
        }
        try {
            double value = Double.parseDouble(trimmed);
            double minValue = parseDouble(property.getMinValue(), -Double.MAX_VALUE);
            double maxValue = parseDouble(property.getMaxValue(), Double.MAX_VALUE);
            if (value < minValue || value > maxValue) {
                return "需要位于 " + minValue + " 到 " + maxValue + " 之间。";
            }
            return null;
        } catch (NumberFormatException exception) {
            return "需要输入一个合法的小数。";
        }
    }

    private static String validateStringDraft(Property property, String draft) {
        String[] validValues = property.getValidValues();
        if (validValues != null && validValues.length > 0 && !contains(validValues, draft)) {
            return "只能填写以下值之一：" + Arrays.toString(validValues) + "。";
        }
        Pattern validationPattern = property.getValidationPattern();
        if (validationPattern != null && !validationPattern.matcher(draft).matches()) {
            return "不符合输入格式要求。";
        }
        return null;
    }

    private static String validateListDraft(Property property, String draft) {
        String[] values = splitList(draft);
        int maxListLength = property.getMaxListLength();
        if (property.isListLengthFixed()) {
            int expectedLength = maxListLength > 0 ? maxListLength : property.getDefaults().length;
            if (values.length != expectedLength) {
                return "需要提供 " + expectedLength + " 个列表项。";
            }
        } else if (maxListLength > 0 && values.length > maxListLength) {
            return "最多只能填写 " + maxListLength + " 个列表项。";
        }

        if (property.getType() == Property.Type.INTEGER) {
            int minValue = parseInteger(property.getMinValue(), Integer.MIN_VALUE);
            int maxValue = parseInteger(property.getMaxValue(), Integer.MAX_VALUE);
            for (String value : values) {
                try {
                    int parsed = Integer.parseInt(value);
                    if (parsed < minValue || parsed > maxValue) {
                        return "列表中的整数需要位于 " + minValue + " 到 " + maxValue + " 之间。";
                    }
                } catch (NumberFormatException exception) {
                    return "列表中的每一项都必须是整数。";
                }
            }
            return null;
        }

        if (property.getType() == Property.Type.DOUBLE) {
            double minValue = parseDouble(property.getMinValue(), -Double.MAX_VALUE);
            double maxValue = parseDouble(property.getMaxValue(), Double.MAX_VALUE);
            for (String value : values) {
                try {
                    double parsed = Double.parseDouble(value);
                    if (parsed < minValue || parsed > maxValue) {
                        return "列表中的小数需要位于 " + minValue + " 到 " + maxValue + " 之间。";
                    }
                } catch (NumberFormatException exception) {
                    return "列表中的每一项都必须是小数。";
                }
            }
            return null;
        }

        if (property.getType() == Property.Type.BOOLEAN) {
            for (String value : values) {
                if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                    return "布尔列表只能填写 true 或 false。";
                }
            }
            return null;
        }

        String[] validValues = property.getValidValues();
        if (validValues != null && validValues.length > 0) {
            for (String value : values) {
                if (!contains(validValues, value)) {
                    return "列表中的值只能填写以下选项之一：" + Arrays.toString(validValues) + "。";
                }
            }
        }

        Pattern validationPattern = property.getValidationPattern();
        if (validationPattern != null) {
            for (String value : values) {
                if (!validationPattern.matcher(value).matches()) {
                    return "列表中的值不符合输入格式要求。";
                }
            }
        }
        return null;
    }

    private static void applyListDraft(Property property, String draft) {
        String[] values = splitList(draft);
        if (property.getType() == Property.Type.INTEGER) {
            int[] parsedValues = new int[values.length];
            for (int index = 0; index < values.length; index++) {
                parsedValues[index] = parseInteger(values[index]);
            }
            property.set(parsedValues);
            return;
        }
        if (property.getType() == Property.Type.DOUBLE) {
            double[] parsedValues = new double[values.length];
            for (int index = 0; index < values.length; index++) {
                parsedValues[index] = parseDouble(values[index]);
            }
            property.set(parsedValues);
            return;
        }
        if (property.getType() == Property.Type.BOOLEAN) {
            boolean[] parsedValues = new boolean[values.length];
            for (int index = 0; index < values.length; index++) {
                parsedValues[index] = Boolean.parseBoolean(values[index]);
            }
            property.set(parsedValues);
            return;
        }
        property.set(values);
    }

    private static String[] splitList(String draft) {
        if (draft == null || draft.trim().isEmpty()) {
            return new String[0];
        }
        String[] rawValues = draft.split("[,，;；\\n\\r]+");
        List<String> values = new ArrayList<String>(rawValues.length);
        for (String rawValue : rawValues) {
            if (rawValue == null) {
                continue;
            }
            String trimmed = rawValue.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values.toArray(new String[values.size()]);
    }

    private static String joinList(String[] values) {
        if (values == null || values.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value.trim());
        }
        return builder.toString();
    }

    private static boolean contains(String[] values, String target) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (Objects.equals(value, target)) {
                return true;
            }
        }
        return false;
    }

    private static int parseInteger(String value) {
        return Integer.parseInt(value.trim());
    }

    private static int parseInteger(String value, int fallback) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static double parseDouble(String value) {
        return Double.parseDouble(value.trim());
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value == null ? "" : value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
