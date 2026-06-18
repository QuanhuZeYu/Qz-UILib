package club.heiqi.uilib.ui.scene.input;

/**
 * 指针动作枚举。
 *
 * <p>描述指针（鼠标/触控）的四种基本动作：移动、按钮按下、按钮释放、滚轮滚动。</p>
 */
public enum ScenePointerAction {
    /** 指针移动 */
    MOVE,
    /** 按钮按下 */
    BUTTON_DOWN,
    /** 按钮释放 */
    BUTTON_UP,
    /** 滚轮滚动 */
    SCROLL,
    /** 指针取消（窗口失焦等系统事件触发） */
    CANCEL
}
