package club.heiqi.uilib.ui.style.props;

/**
 * CSS object-fit 枚举。
 *
 * <p>控制替换元素（如 img）的内容如何适配其容器尺寸。</p>
 */
public enum UiObjectFit {

    /** 内容拉伸填满容器（可能变形）。 */
    FILL,

    /** 内容等比缩放以完全包含在容器内（可能留白）。 */
    CONTAIN,

    /** 内容等比缩放以完全覆盖容器（可能裁剪）。 */
    COVER,

    /** 内容保持原始尺寸，不缩放。 */
    NONE,

    /** 取 none 和 contain 中较小的尺寸。 */
    SCALE_DOWN
}
