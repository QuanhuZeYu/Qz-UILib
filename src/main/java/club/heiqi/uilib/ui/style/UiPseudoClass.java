package club.heiqi.uilib.ui.style;

/**
 * CSS 伪类枚举。
 *
 * <p>描述选择器中的伪类条件。交互状态伪类依赖运行时状态集合；结构伪类直接基于
 * 当前 DOM 树关系计算。</p>
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
    DISABLED,

    /** 元素是父元素的第一个元素子节点。 */
    FIRST_CHILD,

    /** 元素是父元素的最后一个元素子节点。 */
    LAST_CHILD,

    /** 元素满足 nth-child 结构位置条件。 */
    NTH_CHILD
}
