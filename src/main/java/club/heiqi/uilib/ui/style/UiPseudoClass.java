package club.heiqi.uilib.ui.style;

/**
 * CSS 伪类枚举。
 *
 * <p>描述元素的交互状态，用于选择器匹配时的状态条件判定。</p>
 */
public enum UiPseudoClass {

    /** 鼠标悬停状态。 */
    HOVER,

    /** 元素获得焦点状态。 */
    FOCUS,

    /** 元素获得焦点且应显示焦点指示器（键盘导航触发）。 */
    FOCUS_VISIBLE,

    /** 元素被按下状态（mousedown 到 mouseup 之间）。 */
    ACTIVE,

    /** 元素处于禁用状态。 */
    DISABLED
}
