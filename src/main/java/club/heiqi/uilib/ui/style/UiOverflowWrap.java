package club.heiqi.uilib.ui.style;

/**
 * CSS overflow-wrap 枚举。
 *
 * <p>控制长单词或 URL 在溢出容器时是否允许断行。</p>
 */
public enum UiOverflowWrap {

    /** 只在正常断点处换行（默认）。 */
    NORMAL,

    /** 允许在任意位置断行以防止溢出（等同于旧版 word-wrap: break-word）。 */
    BREAK_WORD,

    /** 在溢出时在任意位置断行（类似 break-word 但更激进）。 */
    ANYWHERE
}
