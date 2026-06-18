package club.heiqi.uilib.ui.scene.input;

/**
 * 平台无关鼠标按钮标识。
 *
 * <p>定义一套与平台（LWJGL/GLFW）和 Minecraft 完全解耦的标准化鼠标按钮。
 * 平台适配层负责将原生 button code（如 GLFW 的 0=左/1=右/2=中）翻译为本枚举值，
 * 禁止裸 int 透传进入核心。</p>
 *
 * <ul>
 *   <li>{@link #LEFT} — 鼠标左键</li>
 *   <li>{@link #RIGHT} — 鼠标右键</li>
 *   <li>{@link #MIDDLE} — 鼠标中键</li>
 *   <li>{@link #BUTTON_4} — 附加按钮 4</li>
 *   <li>{@link #BUTTON_5} — 附加按钮 5</li>
 *   <li>{@link #NONE} — 无按钮（MOVE/SCROLL 等无按钮事件使用）</li>
 * </ul>
 */
public enum SceneMouseButton {
    /** 鼠标左键 */
    LEFT,
    /** 鼠标右键 */
    RIGHT,
    /** 鼠标中键 */
    MIDDLE,
    /** 附加按钮 4 */
    BUTTON_4,
    /** 附加按钮 5 */
    BUTTON_5,
    /** 无按钮（MOVE/SCROLL 等无按钮事件使用） */
    NONE
}
