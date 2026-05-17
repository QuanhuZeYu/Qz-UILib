package club.heiqi.uilib.ui.style;

/**
 * HTML-like 元素可见性。
 *
 * <p>与 {@code display:none} 不同，{@code visibility:hidden} 的元素仍占据布局空间，
 * 只是不可见且不响应命中测试。</p>
 */
public enum UiVisibility {
    /** 可见（默认）。 */
    VISIBLE,
    /** 不可见，但仍占据布局空间，不响应命中测试。 */
    HIDDEN
}
