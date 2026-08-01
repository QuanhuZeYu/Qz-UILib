package club.heiqi.uilib.ui.scene.input;

/**
 * 场景事件类型枚举。
 *
 * <p>定义路由层处理的标准化事件类型。由 {@link SceneInputRouter} 根据
 * {@link ScenePointerAction} 映射生成，CLICK 为合成事件。</p>
 */
public enum SceneEventType {
    /** 指针按下（对应 BUTTON_DOWN） */
    POINTER_DOWN,
    /** 指针释放（对应 BUTTON_UP） */
    POINTER_UP,
    /** 指针移动（对应 MOVE） */
    POINTER_MOVE,
    /** 滚轮滚动（对应 SCROLL） */
    SCROLL,
    /** 点击合成事件（DOWN+UP 在同节点完成） */
    CLICK,
    /** 指针取消事件（窗口失焦等，对应 ScenePointerAction.CANCEL） */
    POINTER_CANCEL,
    /** 键盘按键按下（含 REPEATED，repeat 标志区分） */
    KEY_DOWN,
    /** 键盘按键释放 */
    KEY_UP,
    /** 文本输入事件 */
    TEXT_INPUT,
    /** 焦点 authority 已切入目标节点；同步派发，不等待 focused signal flush */
    FOCUS_GAINED,
    /** 焦点 authority 已离开目标节点；同步派发，不等待 focused signal flush */
    FOCUS_LOST
}
