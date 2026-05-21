package club.heiqi.uilib.ui.animation;

/**
 * HTML-like 当前支持的可动画属性。
 */
public enum DocumentAnimationProperty {
    /**
     * 背景色，属于 paint-only 属性。
     */
    BACKGROUND_COLOR(DocumentAnimationImpact.PAINT, ValueType.COLOR),

    /**
     * 边框色，属于 paint-only 属性。
     */
    BORDER_COLOR(DocumentAnimationImpact.PAINT, ValueType.COLOR),

    /**
     * 边框圆角，属于 paint-only 属性。
     */
    BORDER_RADIUS(DocumentAnimationImpact.PAINT, ValueType.FLOAT),

    /**
     * box-shadow 颜色，属于 paint-only 属性。
     */
    BOX_SHADOW_COLOR(DocumentAnimationImpact.PAINT, ValueType.COLOR),

    /**
     * box-shadow 的 X 方向偏移，属于 paint-only 属性。
     */
    BOX_SHADOW_OFFSET_X(DocumentAnimationImpact.PAINT, ValueType.FLOAT),

    /**
     * box-shadow 的 Y 方向偏移，属于 paint-only 属性。
     */
    BOX_SHADOW_OFFSET_Y(DocumentAnimationImpact.PAINT, ValueType.FLOAT),

    /**
     * box-shadow 的 blur 半径，属于 paint-only 属性。
     */
    BOX_SHADOW_BLUR_RADIUS(DocumentAnimationImpact.PAINT, ValueType.FLOAT),

    /**
     * box-shadow 的 spread 半径，属于 paint-only 属性。
     */
    BOX_SHADOW_SPREAD_RADIUS(DocumentAnimationImpact.PAINT, ValueType.FLOAT),

    /**
     * transform 的 X 方向平移分量，属于 paint-only 属性。
     */
    TRANSLATE_X(DocumentAnimationImpact.PAINT, ValueType.FLOAT),

    /**
     * transform 的 Y 方向平移分量，属于 paint-only 属性。
     */
    TRANSLATE_Y(DocumentAnimationImpact.PAINT, ValueType.FLOAT),

    /**
     * transform 的 X 方向缩放分量，属于 paint-only 属性。
     */
    SCALE_X(DocumentAnimationImpact.PAINT, ValueType.FLOAT),

    /**
     * transform 的 Y 方向缩放分量，属于 paint-only 属性。
     */
    SCALE_Y(DocumentAnimationImpact.PAINT, ValueType.FLOAT),

    /**
     * transform 的旋转角度分量，属于 paint-only 属性。
     */
    ROTATE(DocumentAnimationImpact.PAINT, ValueType.FLOAT),

    /**
     * 文本色，属于 paint-only 属性。
     */
    TEXT_COLOR(DocumentAnimationImpact.PAINT, ValueType.COLOR),

    /**
     * 元素透明度，会影响 paint context 与最终绘制透明度。
     */
    OPACITY(DocumentAnimationImpact.EFFECT, ValueType.FLOAT),

    /**
     * 背景滤镜 blur 半径，属于 effect-affecting 长度类属性。
     */
    BACKDROP_BLUR_RADIUS(DocumentAnimationImpact.EFFECT, ValueType.FLOAT),

    /**
     * 元素 content box 宽度，属于 layout-affecting 属性。
     */
    WIDTH(DocumentAnimationImpact.LAYOUT, ValueType.FLOAT),

    /**
     * 元素 content box 高度，属于 layout-affecting 属性。
     */
    HEIGHT(DocumentAnimationImpact.LAYOUT, ValueType.FLOAT),

    /**
     * 元素 `top` 定位值，属于 layout-affecting 属性。
     */
    TOP(DocumentAnimationImpact.LAYOUT, ValueType.FLOAT),

    /**
     * 元素 `right` 定位值，属于 layout-affecting 属性。
     */
    RIGHT(DocumentAnimationImpact.LAYOUT, ValueType.FLOAT),

    /**
     * 元素 `bottom` 定位值，属于 layout-affecting 属性。
     */
    BOTTOM(DocumentAnimationImpact.LAYOUT, ValueType.FLOAT),

    /**
     * 元素 `left` 定位值，属于 layout-affecting 属性。
     */
    LEFT(DocumentAnimationImpact.LAYOUT, ValueType.FLOAT),

    /**
     * 元素左外边距，属于 layout-affecting 属性。
     */
    MARGIN_LEFT(DocumentAnimationImpact.LAYOUT, ValueType.FLOAT),

    /**
     * 元素右外边距，属于 layout-affecting 属性。
     */
    MARGIN_RIGHT(DocumentAnimationImpact.LAYOUT, ValueType.FLOAT),

    /**
     * 元素左内边距，属于 layout-affecting 属性。
     */
    PADDING_LEFT(DocumentAnimationImpact.LAYOUT, ValueType.FLOAT),

    /**
     * 元素右内边距，属于 layout-affecting 属性。
     */
    PADDING_RIGHT(DocumentAnimationImpact.LAYOUT, ValueType.FLOAT);

    private final DocumentAnimationImpact impact;
    private final ValueType valueType;

    private DocumentAnimationProperty(DocumentAnimationImpact impact, ValueType valueType) {
        this.impact = impact;
        this.valueType = valueType;
    }

    /**
     * 返回该动画属性对渲染流水线的影响范围。
     *
     * @return 动画影响范围
     */
    public DocumentAnimationImpact getImpact() {
        return impact;
    }

    /**
     * 返回该动画属性的插值值类型。
     *
     * @return 插值值类型
     */
    public ValueType getValueType() {
        return valueType;
    }

    /**
     * 返回该属性是否只影响 paint command。
     *
     * @return 是否为 paint-only 属性
     */
    public boolean isPaintOnly() {
        return impact == DocumentAnimationImpact.PAINT;
    }

    /**
     * 返回该属性是否会影响效果合成或 runtime pass。
     *
     * @return 是否为 effect-affecting 属性
     */
    public boolean isEffectAffecting() {
        return impact == DocumentAnimationImpact.EFFECT;
    }

    /**
     * 返回该属性是否会影响布局。
     *
     * @return 是否为 layout-affecting 属性
     */
    public boolean isLayoutAffecting() {
        return impact == DocumentAnimationImpact.LAYOUT;
    }

    /**
     * 返回该属性是否使用颜色插值轨道。
     *
     * @return 是否为颜色值属性
     */
    public boolean isColorValue() {
        return valueType == ValueType.COLOR;
    }

    /**
     * 返回该属性是否使用数值插值轨道。
     *
     * @return 是否为数值属性
     */
    public boolean isFloatValue() {
        return valueType == ValueType.FLOAT;
    }

    /**
     * 动画属性的插值值类型。
     */
    public enum ValueType {
        /**
         * ARGB 颜色值。
         */
        COLOR,

        /**
         * 单精度数值。
         */
        FLOAT
    }
}
