package club.heiqi.uilib.ui.animation;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * 命名 keyframes 定义。
 *
 * <p>当前 MVP 只表达首帧到末帧的线性属性范围，供作者侧 `animation-name` 复用。</p>
 */
public final class DocumentKeyframes {

    private final String name;
    private final Map<DocumentAnimationProperty, ColorRange> colorRanges;
    private final Map<DocumentAnimationProperty, FloatRange> floatRanges;

    private DocumentKeyframes(String name, Map<DocumentAnimationProperty, ColorRange> colorRanges,
            Map<DocumentAnimationProperty, FloatRange> floatRanges) {
        this.name = normalizeName(name);
        this.colorRanges = Collections.unmodifiableMap(new EnumMap<DocumentAnimationProperty, ColorRange>(colorRanges));
        this.floatRanges = Collections.unmodifiableMap(new EnumMap<DocumentAnimationProperty, FloatRange>(floatRanges));
    }

    /**
     * 创建命名 keyframes builder。
     *
     * @param name keyframes 名称
     * @return builder
     */
    public static Builder named(String name) {
        return new Builder(name);
    }

    /**
     * 返回 keyframes 名称。
     *
     * @return 名称
     */
    public String getName() {
        return name;
    }

    /**
     * 返回颜色属性范围。
     *
     * @return 颜色属性范围
     */
    public Map<DocumentAnimationProperty, ColorRange> getColorRanges() {
        return colorRanges;
    }

    /**
     * 返回数值属性范围。
     *
     * @return 数值属性范围
     */
    public Map<DocumentAnimationProperty, FloatRange> getFloatRanges() {
        return floatRanges;
    }

    /**
     * 返回是否不包含任何属性范围。
     *
     * @return 是否为空
     */
    public boolean isEmpty() {
        return colorRanges.isEmpty() && floatRanges.isEmpty();
    }

    private static String normalizeName(String value) {
        String normalized = Objects.requireNonNull(value, "name").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("name cannot be empty");
        }
        return normalized;
    }

    /**
     * keyframes builder。
     */
    public static final class Builder {

        private final String name;
        private final EnumMap<DocumentAnimationProperty, ColorRange> colorRanges =
                new EnumMap<DocumentAnimationProperty, ColorRange>(DocumentAnimationProperty.class);
        private final EnumMap<DocumentAnimationProperty, FloatRange> floatRanges =
                new EnumMap<DocumentAnimationProperty, FloatRange>(DocumentAnimationProperty.class);

        private Builder(String name) {
            this.name = normalizeName(name);
        }

        /**
         * 添加颜色属性首末帧。
         *
         * @param property 动画属性
         * @param fromColor 首帧颜色
         * @param toColor 末帧颜色
         * @return 当前 builder
         */
        public Builder setColor(DocumentAnimationProperty property, int fromColor, int toColor) {
            DocumentAnimationProperty resolvedProperty = requirePaintOrEffectProperty(property);
            colorRanges.put(resolvedProperty, new ColorRange(fromColor, toColor));
            floatRanges.remove(resolvedProperty);
            return this;
        }

        /**
         * 添加数值属性首末帧。
         *
         * @param property 动画属性
         * @param fromValue 首帧值
         * @param toValue 末帧值
         * @return 当前 builder
         */
        public Builder setFloat(DocumentAnimationProperty property, float fromValue, float toValue) {
            DocumentAnimationProperty resolvedProperty = requirePaintOrEffectProperty(property);
            floatRanges.put(resolvedProperty, new FloatRange(fromValue, toValue));
            colorRanges.remove(resolvedProperty);
            return this;
        }

        /**
         * 创建不可变 keyframes 定义。
         *
         * @return keyframes 定义
         */
        public DocumentKeyframes build() {
            return new DocumentKeyframes(name, colorRanges, floatRanges);
        }

        private static DocumentAnimationProperty requirePaintOrEffectProperty(DocumentAnimationProperty property) {
            DocumentAnimationProperty resolvedProperty = Objects.requireNonNull(property, "property");
            if (resolvedProperty.getImpact() == DocumentAnimationImpact.LAYOUT) {
                throw new IllegalArgumentException("layout keyframes are not supported yet: " + resolvedProperty);
            }
            return resolvedProperty;
        }
    }

    /**
     * 颜色属性首末帧。
     */
    public static final class ColorRange {

        private final int fromColor;
        private final int toColor;

        private ColorRange(int fromColor, int toColor) {
            this.fromColor = fromColor;
            this.toColor = toColor;
        }

        public int getFromColor() {
            return fromColor;
        }

        public int getToColor() {
            return toColor;
        }
    }

    /**
     * 数值属性首末帧。
     */
    public static final class FloatRange {

        private final float fromValue;
        private final float toValue;

        private FloatRange(float fromValue, float toValue) {
            this.fromValue = fromValue;
            this.toValue = toValue;
        }

        public float getFromValue() {
            return fromValue;
        }

        public float getToValue() {
            return toValue;
        }
    }
}
