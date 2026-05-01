package club.heiqi.uilib.ui.animation;

/**
 * HTML-like 当前支持的可动画属性。
 */
public enum DocumentAnimationProperty {
    /**
     * 背景色，属于 paint-only 属性。
     */
    BACKGROUND_COLOR(DocumentAnimationImpact.PAINT),

    /**
     * 边框色，属于 paint-only 属性。
     */
    BORDER_COLOR(DocumentAnimationImpact.PAINT),

    /**
     * 边框圆角，属于 paint-only 属性。
     */
    BORDER_RADIUS(DocumentAnimationImpact.PAINT),

    /**
     * 文本色，属于 paint-only 属性。
     */
    TEXT_COLOR(DocumentAnimationImpact.PAINT),

    /**
     * 元素透明度，会影响 paint context 与最终绘制透明度。
     */
    OPACITY(DocumentAnimationImpact.EFFECT),

    /**
     * 背景滤镜 blur 半径，属于 effect-affecting 长度类属性。
     */
    BACKDROP_BLUR_RADIUS(DocumentAnimationImpact.EFFECT),

    /**
     * 元素 content box 宽度，属于 layout-affecting 属性。
     */
    WIDTH(DocumentAnimationImpact.LAYOUT),

    /**
     * 元素 content box 高度，属于 layout-affecting 属性。
     */
    HEIGHT(DocumentAnimationImpact.LAYOUT),

    /**
     * 元素左外边距，属于 layout-affecting 属性。
     */
    MARGIN_LEFT(DocumentAnimationImpact.LAYOUT),

    /**
     * 元素右外边距，属于 layout-affecting 属性。
     */
    MARGIN_RIGHT(DocumentAnimationImpact.LAYOUT);

    private final DocumentAnimationImpact impact;

    private DocumentAnimationProperty(DocumentAnimationImpact impact) {
        this.impact = impact;
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
}
