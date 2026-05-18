package club.heiqi.uilib.ui.dom;

/**
 * DOM 事件传播阶段。
 *
 * <p>对应浏览器标准的三阶段事件传播模型：</p>
 * <ul>
 *   <li>{@link #NONE} — 事件尚未分发</li>
 *   <li>{@link #CAPTURING} — 从根元素向目标元素传播（捕获阶段）</li>
 *   <li>{@link #AT_TARGET} — 到达事件目标元素</li>
 *   <li>{@link #BUBBLING} — 从目标元素向根元素传播（冒泡阶段）</li>
 * </ul>
 */
public enum DocumentEventPhase {

    /** 事件尚未分发。 */
    NONE,

    /** 捕获阶段：从根元素向目标元素传播。 */
    CAPTURING,

    /** 目标阶段：事件到达目标元素。 */
    AT_TARGET,

    /** 冒泡阶段：从目标元素向根元素传播。 */
    BUBBLING
}
