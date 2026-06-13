package club.heiqi.uilib.ui.event;

/**
 * UI 层使用的 LWJGL2/MC 键码常量。
 *
 * <p>这些数值保持与当前 `UiKeyEvent.keyCode` 语义一致，用于避免业务代码直接依赖底层输入类。</p>
 */
public final class UiKeyCodes {

    public static final int KEY_ESCAPE = 1;
    public static final int KEY_1 = 2;
    public static final int KEY_BACK = 14;
    public static final int KEY_TAB = 15;
    public static final int KEY_RETURN = 28;
    public static final int KEY_LCONTROL = 29;
    public static final int KEY_A = 30;
    public static final int KEY_S = 31;
    public static final int KEY_LSHIFT = 42;
    public static final int KEY_RSHIFT = 54;
    public static final int KEY_LMENU = 56;
    public static final int KEY_SPACE = 57;
    public static final int KEY_NUMPADENTER = 156;
    public static final int KEY_RCONTROL = 157;
    public static final int KEY_RMENU = 184;
    public static final int KEY_HOME = 199;
    public static final int KEY_UP = 200;
    public static final int KEY_PRIOR = 201;
    public static final int KEY_LEFT = 203;
    public static final int KEY_RIGHT = 205;
    public static final int KEY_END = 207;
    public static final int KEY_DOWN = 208;
    public static final int KEY_NEXT = 209;
    public static final int KEY_DELETE = 211;

    private UiKeyCodes() {}
}
