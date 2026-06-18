package club.heiqi.uilib.ui.scene.input;

/**
 * 新栈光标样式枚举 —— I4c cursor 投影能力。
 *
 * <h3>设计意图</h3>
 * <p>值照抄旧栈 {@code club.heiqi.uilib.ui.style.props.UiCursor}，
 * 但独立新建、零 import 旧栈。旧栈属将退役的 ui.document 体系（Phase5），
 * 焊死旧栈会破坏 strangler 独立性（用户拍板 D10-A）。</p>
 *
 * <h3>语义</h3>
 * <ul>
 *   <li>{@link #DEFAULT} —— 默认光标（箭头），祖先链无声明时的回退值</li>
 *   <li>{@link #POINTER} —— 指针光标（手形，表示可点击）</li>
 *   <li>{@link #TEXT} —— 文本光标（I 形，表示可选择文本）</li>
 *   <li>{@link #MOVE} —— 移动光标（十字箭头，表示可拖动）</li>
 *   <li>{@link #GRAB} —— 抓取光标（张开手形，表示可抓取）</li>
 *   <li>{@link #GRABBING} —— 抓取中光标（握拳手形，表示正在拖动）</li>
 *   <li>{@link #NOT_ALLOWED} —— 不允许光标（禁止符号）</li>
 *   <li>{@link #WAIT} —— 等待光标（沙漏/旋转）</li>
 *   <li>{@link #CROSSHAIR} —— 十字光标（精确选择）</li>
 *   <li>{@link #NONE} —— 无光标（隐藏）</li>
 *   <li>{@link #EW_RESIZE} —— 水平调整大小光标</li>
 *   <li>{@link #NS_RESIZE} —— 垂直调整大小光标</li>
 *   <li>{@link #HELP} —— 帮助光标（问号）</li>
 * </ul>
 */
public enum SceneCursor {

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
