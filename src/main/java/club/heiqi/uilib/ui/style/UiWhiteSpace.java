package club.heiqi.uilib.ui.style;

/**
 * HTML-like 空白字符处理与换行行为。
 */
public enum UiWhiteSpace {
    /** 正常换行（默认）：超出宽度时自动换行。 */
    NORMAL,
    /** 禁止换行：文本在一行内显示，超出部分溢出或被裁剪。 */
    NOWRAP
}
