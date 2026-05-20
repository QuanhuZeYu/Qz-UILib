package club.heiqi.uilib.ui.style.props;

/**
 * HTML-like 文本溢出处理方式。
 *
 * <p>仅在 {@link UiWhiteSpace#NOWRAP} 且容器有明确宽度时生效。</p>
 */
public enum UiTextOverflow {
    /** 直接裁剪（默认）。 */
    CLIP,
    /** 超出部分显示省略号 {@code …}。 */
    ELLIPSIS
}
