package club.heiqi.uilib.ui.animation;

/**
 * HTML-like 当前支持的可动画属性。
 */
public enum DocumentAnimationProperty {
    /**
     * 背景色，属于 paint-only 属性。
     */
    BACKGROUND_COLOR,

    /**
     * 边框色，属于 paint-only 属性。
     */
    BORDER_COLOR,

    /**
     * 边框圆角，属于 paint-only 属性。
     */
    BORDER_RADIUS,

    /**
     * 文本色，属于 paint-only 属性。
     */
    TEXT_COLOR,

    /**
     * 元素透明度，当前作为标准 paint command 的累积透明度处理。
     */
    OPACITY
}
