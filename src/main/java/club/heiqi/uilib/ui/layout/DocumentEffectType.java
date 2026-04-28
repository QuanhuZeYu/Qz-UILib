package club.heiqi.uilib.ui.layout;

/**
 * HTML-like 元素效果链中的效果类型。
 */
public enum DocumentEffectType {

    /**
     * 子树需要以独立绘制上下文处理。
     */
    PAINT_CONTEXT,

    /**
     * 元素需要在自身背景前采样背后内容。
     */
    BACKDROP_FILTER,

    /**
     * 元素对子内容建立 overflow 结构裁剪。
     */
    OVERFLOW_CLIP
}
