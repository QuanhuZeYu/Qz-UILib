package club.heiqi.uilib.ui.event;

/**
 * UI 层使用的 LWJGL2/MC 键码常量。
 *
 * <p>这些数值保持与当前 `UiKeyEvent.keyCode` 语义一致，用于避免业务代码直接依赖底层输入类。</p>
 * <p>完整覆盖 LWJGL2 org.lwjgl.input.Keyboard 的所有 KEY_* 常量（0-255）。</p>
 */
public final class UiKeyCodes {

    // ===== 特殊控制键 =====
    public static final int KEY_NONE = 0;
    public static final int KEY_ESCAPE = 1;

    // ===== 数字键区 (主键盘) =====
    public static final int KEY_1 = 2;
    public static final int KEY_2 = 3;
    public static final int KEY_3 = 4;
    public static final int KEY_4 = 5;
    public static final int KEY_5 = 6;
    public static final int KEY_6 = 7;
    public static final int KEY_7 = 8;
    public static final int KEY_8 = 9;
    public static final int KEY_9 = 10;
    public static final int KEY_0 = 11;

    // ===== 符号键区 (主键盘顶部) =====
    public static final int KEY_MINUS = 12;
    public static final int KEY_EQUALS = 13;

    // ===== 编辑键区 =====
    public static final int KEY_BACK = 14;
    public static final int KEY_TAB = 15;

    // ===== 字母键区 (QWERTY 布局) =====
    public static final int KEY_Q = 16;
    public static final int KEY_W = 17;
    public static final int KEY_E = 18;
    public static final int KEY_R = 19;
    public static final int KEY_T = 20;
    public static final int KEY_Y = 21;
    public static final int KEY_U = 22;
    public static final int KEY_I = 23;
    public static final int KEY_O = 24;
    public static final int KEY_P = 25;
    public static final int KEY_LBRACKET = 26;
    public static final int KEY_RBRACKET = 27;
    public static final int KEY_RETURN = 28;
    public static final int KEY_LCONTROL = 29;
    public static final int KEY_A = 30;
    public static final int KEY_S = 31;
    public static final int KEY_D = 32;
    public static final int KEY_F = 33;
    public static final int KEY_G = 34;
    public static final int KEY_H = 35;
    public static final int KEY_J = 36;
    public static final int KEY_K = 37;
    public static final int KEY_L = 38;
    public static final int KEY_SEMICOLON = 39;
    public static final int KEY_APOSTROPHE = 40;
    public static final int KEY_GRAVE = 41;
    public static final int KEY_LSHIFT = 42;
    public static final int KEY_BACKSLASH = 43;
    public static final int KEY_Z = 44;
    public static final int KEY_X = 45;
    public static final int KEY_C = 46;
    public static final int KEY_V = 47;
    public static final int KEY_B = 48;
    public static final int KEY_N = 49;
    public static final int KEY_M = 50;
    public static final int KEY_COMMA = 51;
    public static final int KEY_PERIOD = 52;
    public static final int KEY_SLASH = 53;
    public static final int KEY_RSHIFT = 54;

    // ===== 小键盘运算符 =====
    public static final int KEY_MULTIPLY = 55;

    // ===== 修饰键 =====
    public static final int KEY_LMENU = 56; // Left Alt
    public static final int KEY_SPACE = 57;
    public static final int KEY_CAPITAL = 58; // Caps Lock

    // ===== 功能键区 (F1-F10) =====
    public static final int KEY_F1 = 59;
    public static final int KEY_F2 = 60;
    public static final int KEY_F3 = 61;
    public static final int KEY_F4 = 62;
    public static final int KEY_F5 = 63;
    public static final int KEY_F6 = 64;
    public static final int KEY_F7 = 65;
    public static final int KEY_F8 = 66;
    public static final int KEY_F9 = 67;
    public static final int KEY_F10 = 68;

    // ===== 锁定键 =====
    public static final int KEY_NUMLOCK = 69;
    public static final int KEY_SCROLL = 70; // Scroll Lock

    // ===== 小键盘数字区 =====
    public static final int KEY_NUMPAD7 = 71;
    public static final int KEY_NUMPAD8 = 72;
    public static final int KEY_NUMPAD9 = 73;
    public static final int KEY_SUBTRACT = 74;
    public static final int KEY_NUMPAD4 = 75;
    public static final int KEY_NUMPAD5 = 76;
    public static final int KEY_NUMPAD6 = 77;
    public static final int KEY_ADD = 78;
    public static final int KEY_NUMPAD1 = 79;
    public static final int KEY_NUMPAD2 = 80;
    public static final int KEY_NUMPAD3 = 81;
    public static final int KEY_NUMPAD0 = 82;
    public static final int KEY_DECIMAL = 83;

    // ===== 功能键区 (F11-F15) =====
    public static final int KEY_F11 = 87;
    public static final int KEY_F12 = 88;

    // ===== 扩展功能键 (F13-F15) =====
    public static final int KEY_F13 = 100;
    public static final int KEY_F14 = 101;
    public static final int KEY_F15 = 102;

    // ===== 国际键与多媒体键 =====
    public static final int KEY_KANA = 112;
    public static final int KEY_F16 = 107;
    public static final int KEY_F17 = 108;
    public static final int KEY_F18 = 109;
    public static final int KEY_KANJI = 148;
    public static final int KEY_F19 = 113;
    public static final int KEY_CONVERT = 121;
    public static final int KEY_NOCONVERT = 123;
    public static final int KEY_YEN = 125;
    public static final int KEY_NUMPADEQUALS = 141;
    public static final int KEY_CIRCUMFLEX = 144;
    public static final int KEY_AT = 145;
    public static final int KEY_COLON = 146;
    public static final int KEY_UNDERLINE = 147;
    public static final int KEY_STOP = 149;
    public static final int KEY_AX = 150;
    public static final int KEY_UNLABELED = 151;

    // ===== 小键盘 Enter 与控制键 =====
    public static final int KEY_NUMPADENTER = 156;
    public static final int KEY_RCONTROL = 157;

    // ===== 小键盘运算符扩展 =====
    public static final int KEY_NUMPADCOMMA = 179;
    public static final int KEY_DIVIDE = 181;

    // ===== 系统与多媒体控制键 =====
    public static final int KEY_SYSRQ = 183;
    public static final int KEY_RMENU = 184; // Right Alt
    public static final int KEY_PAUSE = 197;

    // ===== 导航键区 =====
    public static final int KEY_HOME = 199;
    public static final int KEY_UP = 200;
    public static final int KEY_PRIOR = 201; // Page Up
    public static final int KEY_LEFT = 203;
    public static final int KEY_RIGHT = 205;
    public static final int KEY_END = 207;
    public static final int KEY_DOWN = 208;
    public static final int KEY_NEXT = 209; // Page Down
    public static final int KEY_INSERT = 210;
    public static final int KEY_DELETE = 211;

    // ===== Windows 与应用控制键 =====
    public static final int KEY_LMETA = 219; // Left Windows / Command
    public static final int KEY_RMETA = 220; // Right Windows / Command
    public static final int KEY_APPS = 221; // Context Menu

    // ===== 电源管理键 =====
    public static final int KEY_POWER = 222;
    public static final int KEY_SLEEP = 223;

    private UiKeyCodes() {}
}
