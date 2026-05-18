package club.heiqi.uilib.ui.style;

/**
 * CSS cursor 枚举。
 *
 * <p>控制鼠标悬停在元素上时的光标样式。</p>
 */
public enum UiCursor {

    /** 默认光标（箭头）。 */
    DEFAULT,

    /** 指针光标（手形，表示可点击）。 */
    POINTER,

    /** 文本光标（I 形，表示可选择文本）。 */
    TEXT,

    /** 移动光标（十字箭头，表示可拖动）。 */
    MOVE,

    /** 抓取光标（张开手形，表示可抓取）。 */
    GRAB,

    /** 抓取中光标（握拳手形，表示正在拖动）。 */
    GRABBING,

    /** 不允许光标（禁止符号）。 */
    NOT_ALLOWED,

    /** 等待光标（沙漏/旋转）。 */
    WAIT,

    /** 十字光标（精确选择）。 */
    CROSSHAIR,

    /** 无光标（隐藏）。 */
    NONE,

    /** 水平调整大小光标。 */
    EW_RESIZE,

    /** 垂直调整大小光标。 */
    NS_RESIZE,

    /** 帮助光标（问号）。 */
    HELP
}
