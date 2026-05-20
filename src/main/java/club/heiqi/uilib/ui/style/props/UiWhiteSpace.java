package club.heiqi.uilib.ui.style.props;

/**
 * HTML-like 空白字符处理与换行行为。
 */
public enum UiWhiteSpace {
    /** 正常换行（默认）：超出宽度时自动换行。 */
    NORMAL,
    /** 禁止换行：文本在一行内显示，超出部分溢出或被裁剪。 */
    NOWRAP,
    /** 保留空白字符与显式换行，不按容器宽度自动换行。 */
    PRE,
    /** 保留空白字符与显式换行，同时允许按容器宽度自动换行。 */
    PRE_WRAP,
    /** 折叠连续空白字符，保留显式换行，并允许按容器宽度自动换行。 */
    PRE_LINE
}
