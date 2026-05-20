package club.heiqi.uilib.ui.style.props;

/**
 * CSS word-break 枚举。
 *
 * <p>控制单词内部的换行行为。</p>
 */
public enum UiWordBreak {

    /** 默认换行规则：只在允许的断点处换行。 */
    NORMAL,

    /** 允许在任意字符间断行（CJK 文本的默认行为）。 */
    BREAK_ALL,

    /** 保持单词完整，不在单词内部断行（即使溢出）。 */
    KEEP_ALL
}
