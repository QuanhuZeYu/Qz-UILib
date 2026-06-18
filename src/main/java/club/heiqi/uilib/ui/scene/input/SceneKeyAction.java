package club.heiqi.uilib.ui.scene.input;

/**
 * 键盘动作枚举。
 *
 * <p>描述按键的三种基本动作状态：按下、重复、释放。</p>
 */
public enum SceneKeyAction {
    /** 按键按下 */
    PRESSED,
    /** 按键重复（长按产生的重复事件） */
    REPEATED,
    /** 按键释放 */
    RELEASED
}
