package club.heiqi.uilib.ui.scene.input;

/**
 * 平台无关键码枚举。
 *
 * <p>定义一套与平台（LWJGL/GLFW）和 Minecraft 完全解耦的标准化键盘按键码。
 * 枚举值覆盖常用控制/编辑/方向/修饰/字母/数字/符号/功能键/小键盘按键。</p>
 *
 * <ul>
 *   <li>{@link #UNKNOWN} — 未识别按键的兜底值</li>
 *   <li>控制/编辑键 — ESCAPE, ENTER, TAB, BACKSPACE, SPACE, CAPS_LOCK, INSERT, DELETE,
 *       HOME, END, PAGE_UP, PAGE_DOWN, PRINT_SCREEN, SCROLL_LOCK, PAUSE, NUM_LOCK, MENU</li>
 *   <li>方向键 — ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT</li>
 *   <li>修饰键（左右分立）— SHIFT_LEFT, SHIFT_RIGHT, CONTROL_LEFT, CONTROL_RIGHT,
 *       ALT_LEFT, ALT_RIGHT, META_LEFT, META_RIGHT</li>
 *   <li>字母键 — KEY_A 到 KEY_Z</li>
 *   <li>主键盘数字 — DIGIT_0 到 DIGIT_9</li>
 *   <li>主键盘符号 — MINUS, EQUALS, BRACKET_LEFT, BRACKET_RIGHT, BACKSLASH,
 *       SEMICOLON, APOSTROPHE, GRAVE, COMMA, PERIOD, SLASH</li>
 *   <li>功能键 — F1 到 F12</li>
 *   <li>小键盘 — NUMPAD_0 到 NUMPAD_9, NUMPAD_ADD, NUMPAD_SUBTRACT, NUMPAD_MULTIPLY,
 *       NUMPAD_DIVIDE, NUMPAD_DECIMAL, NUMPAD_ENTER, NUMPAD_EQUALS</li>
 * </ul>
 */
public enum SceneKey {
    /** 未识别按键兜底 */
    UNKNOWN,

    // === 控制/编辑键 ===
    ESCAPE,
    ENTER,
    TAB,
    BACKSPACE,
    SPACE,
    CAPS_LOCK,
    INSERT,
    DELETE,
    HOME,
    END,
    PAGE_UP,
    PAGE_DOWN,
    PRINT_SCREEN,
    SCROLL_LOCK,
    PAUSE,
    NUM_LOCK,
    MENU,

    // === 方向键 ===
    ARROW_UP,
    ARROW_DOWN,
    ARROW_LEFT,
    ARROW_RIGHT,

    // === 修饰键（左右分立） ===
    SHIFT_LEFT,
    SHIFT_RIGHT,
    CONTROL_LEFT,
    CONTROL_RIGHT,
    ALT_LEFT,
    ALT_RIGHT,
    META_LEFT,
    META_RIGHT,

    // === 字母键 ===
    KEY_A,
    KEY_B,
    KEY_C,
    KEY_D,
    KEY_E,
    KEY_F,
    KEY_G,
    KEY_H,
    KEY_I,
    KEY_J,
    KEY_K,
    KEY_L,
    KEY_M,
    KEY_N,
    KEY_O,
    KEY_P,
    KEY_Q,
    KEY_R,
    KEY_S,
    KEY_T,
    KEY_U,
    KEY_V,
    KEY_W,
    KEY_X,
    KEY_Y,
    KEY_Z,

    // === 主键盘数字 ===
    DIGIT_0,
    DIGIT_1,
    DIGIT_2,
    DIGIT_3,
    DIGIT_4,
    DIGIT_5,
    DIGIT_6,
    DIGIT_7,
    DIGIT_8,
    DIGIT_9,

    // === 主键盘符号 ===
    MINUS,
    EQUALS,
    BRACKET_LEFT,
    BRACKET_RIGHT,
    BACKSLASH,
    SEMICOLON,
    APOSTROPHE,
    GRAVE,
    COMMA,
    PERIOD,
    SLASH,

    // === 功能键 ===
    F1,
    F2,
    F3,
    F4,
    F5,
    F6,
    F7,
    F8,
    F9,
    F10,
    F11,
    F12,

    // === 小键盘 ===
    NUMPAD_0,
    NUMPAD_1,
    NUMPAD_2,
    NUMPAD_3,
    NUMPAD_4,
    NUMPAD_5,
    NUMPAD_6,
    NUMPAD_7,
    NUMPAD_8,
    NUMPAD_9,
    NUMPAD_ADD,
    NUMPAD_SUBTRACT,
    NUMPAD_MULTIPLY,
    NUMPAD_DIVIDE,
    NUMPAD_DECIMAL,
    NUMPAD_ENTER,
    NUMPAD_EQUALS
}
