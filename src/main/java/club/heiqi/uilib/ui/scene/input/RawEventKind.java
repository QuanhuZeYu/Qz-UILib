package club.heiqi.uilib.ui.scene.input;

/**
 * 原始事件类型枚举。
 *
 * <p>标识 {@link RawInputEvent} 的联合体区分字段，平台适配层通过此枚举标记
 * 推送的事件是键盘事件、指针事件还是文本输入事件。</p>
 */
public enum RawEventKind {
    /** 键盘事件 */
    KEY,
    /** 指针（鼠标/触控）事件 */
    POINTER,
    /** 文本输入事件 */
    TEXT
}
