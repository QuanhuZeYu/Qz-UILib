package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.scene.input.SceneKey;

/**
 * LWJGL 原生键码 → {@link SceneKey} 映射表（适配层，I10 平台侧端点）。
 *
 * <p>将 LWJGL/LWJGLX {@code Keyboard.KEY_xxx} 原生整型键码映射为平台无关的
 * {@link SceneKey} 枚举。未识别的键码统一返回 {@link SceneKey#UNKNOWN}，
 * 原始键码由事件逃生舱字段透传，不参与核心层分支。</p>
 *
 * <h3>映射约定</h3>
 * <p>按 §7.4 约定落实全套映射：控制/编辑键、方向键、左右分立修饰键、
 * 字母键、主键盘数字与符号、功能键 F1-F12、小键盘 NUMPAD_ 前缀。
 * 国际/多媒体键（KANA/KANJI/CONVERT/YEN/POWER/SLEEP/多媒体）落 {@link SceneKey#UNKNOWN}。</p>
 *
 * <h3>键码来源</h3>
 * <p>使用 LWJGL 2 / lwjglx 公认的 Keyboard 常量值（DIK 码系），
 * 与 {@link LwjglStateReader} 的 lwjglx 优先反射桥保持一致。</p>
 */
public final class LwjglKeyMapper {

    private LwjglKeyMapper() {
        // 工具类，禁止实例化
    }

    /**
     * 将 LWJGL 原生键码映射为平台无关 SceneKey。
     *
     * @param nativeKeyCode LWJGL Keyboard.KEY_xxx 原生整型值
     * @return 对应的 SceneKey，未识别时返回 {@link SceneKey#UNKNOWN}
     */
    public static SceneKey map(int nativeKeyCode) {
        switch (nativeKeyCode) {
            // === 控制/编辑键 ===
            case 1:   return SceneKey.ESCAPE;        // KEY_ESCAPE
            case 28:  return SceneKey.ENTER;          // KEY_RETURN
            case 15:  return SceneKey.TAB;            // KEY_TAB
            case 14:  return SceneKey.BACKSPACE;      // KEY_BACK
            case 57:  return SceneKey.SPACE;          // KEY_SPACE
            case 58:  return SceneKey.CAPS_LOCK;      // KEY_CAPITAL
            case 210: return SceneKey.INSERT;         // KEY_INSERT
            case 211: return SceneKey.DELETE;         // KEY_DELETE
            case 199: return SceneKey.HOME;           // KEY_HOME
            case 207: return SceneKey.END;            // KEY_END
            case 201: return SceneKey.PAGE_UP;        // KEY_PRIOR
            case 209: return SceneKey.PAGE_DOWN;      // KEY_NEXT
            case 183: return SceneKey.PRINT_SCREEN;   // KEY_SYSRQ
            case 70:  return SceneKey.SCROLL_LOCK;    // KEY_SCROLL
            case 197: return SceneKey.PAUSE;          // KEY_PAUSE
            case 69:  return SceneKey.NUM_LOCK;       // KEY_NUMLOCK
            case 221: return SceneKey.MENU;           // KEY_APPS

            // === 方向键 ===
            case 200: return SceneKey.ARROW_UP;       // KEY_UP
            case 208: return SceneKey.ARROW_DOWN;     // KEY_DOWN
            case 203: return SceneKey.ARROW_LEFT;     // KEY_LEFT
            case 205: return SceneKey.ARROW_RIGHT;    // KEY_RIGHT

            // === 修饰键（左右分立） ===
            case 42:  return SceneKey.SHIFT_LEFT;     // KEY_LSHIFT
            case 54:  return SceneKey.SHIFT_RIGHT;    // KEY_RSHIFT
            case 29:  return SceneKey.CONTROL_LEFT;   // KEY_LCONTROL
            case 157: return SceneKey.CONTROL_RIGHT;  // KEY_RCONTROL
            case 56:  return SceneKey.ALT_LEFT;       // KEY_LMENU
            case 184: return SceneKey.ALT_RIGHT;      // KEY_RMENU
            case 219: return SceneKey.META_LEFT;      // KEY_LMETA
            case 220: return SceneKey.META_RIGHT;     // KEY_RMETA

            // === 字母键 ===
            case 30: return SceneKey.KEY_A;           // KEY_A
            case 48: return SceneKey.KEY_B;           // KEY_B
            case 46: return SceneKey.KEY_C;           // KEY_C
            case 32: return SceneKey.KEY_D;           // KEY_D
            case 18: return SceneKey.KEY_E;           // KEY_E
            case 33: return SceneKey.KEY_F;           // KEY_F
            case 34: return SceneKey.KEY_G;           // KEY_G
            case 35: return SceneKey.KEY_H;           // KEY_H
            case 23: return SceneKey.KEY_I;           // KEY_I
            case 36: return SceneKey.KEY_J;           // KEY_J
            case 37: return SceneKey.KEY_K;           // KEY_K
            case 38: return SceneKey.KEY_L;           // KEY_L
            case 50: return SceneKey.KEY_M;           // KEY_M
            case 49: return SceneKey.KEY_N;           // KEY_N
            case 24: return SceneKey.KEY_O;           // KEY_O
            case 25: return SceneKey.KEY_P;           // KEY_P
            case 16: return SceneKey.KEY_Q;           // KEY_Q
            case 19: return SceneKey.KEY_R;           // KEY_R
            case 31: return SceneKey.KEY_S;           // KEY_S
            case 20: return SceneKey.KEY_T;           // KEY_T
            case 22: return SceneKey.KEY_U;           // KEY_U
            case 47: return SceneKey.KEY_V;           // KEY_V
            case 17: return SceneKey.KEY_W;           // KEY_W
            case 45: return SceneKey.KEY_X;           // KEY_X
            case 21: return SceneKey.KEY_Y;           // KEY_Y
            case 44: return SceneKey.KEY_Z;           // KEY_Z

            // === 主键盘数字 ===
            case 2:  return SceneKey.DIGIT_1;         // KEY_1
            case 3:  return SceneKey.DIGIT_2;         // KEY_2
            case 4:  return SceneKey.DIGIT_3;         // KEY_3
            case 5:  return SceneKey.DIGIT_4;         // KEY_4
            case 6:  return SceneKey.DIGIT_5;         // KEY_5
            case 7:  return SceneKey.DIGIT_6;         // KEY_6
            case 8:  return SceneKey.DIGIT_7;         // KEY_7
            case 9:  return SceneKey.DIGIT_8;         // KEY_8
            case 10: return SceneKey.DIGIT_9;         // KEY_9
            case 11: return SceneKey.DIGIT_0;         // KEY_0

            // === 主键盘符号 ===
            case 12: return SceneKey.MINUS;           // KEY_MINUS
            case 13: return SceneKey.EQUALS;          // KEY_EQUALS
            case 26: return SceneKey.BRACKET_LEFT;    // KEY_LBRACKET
            case 27: return SceneKey.BRACKET_RIGHT;   // KEY_RBRACKET
            case 43: return SceneKey.BACKSLASH;       // KEY_BACKSLASH
            case 39: return SceneKey.SEMICOLON;       // KEY_SEMICOLON
            case 40: return SceneKey.APOSTROPHE;      // KEY_APOSTROPHE
            case 41: return SceneKey.GRAVE;           // KEY_GRAVE
            case 51: return SceneKey.COMMA;           // KEY_COMMA
            case 52: return SceneKey.PERIOD;          // KEY_PERIOD
            case 53: return SceneKey.SLASH;           // KEY_SLASH

            // === 功能键 ===
            case 59: return SceneKey.F1;              // KEY_F1
            case 60: return SceneKey.F2;              // KEY_F2
            case 61: return SceneKey.F3;              // KEY_F3
            case 62: return SceneKey.F4;              // KEY_F4
            case 63: return SceneKey.F5;              // KEY_F5
            case 64: return SceneKey.F6;              // KEY_F6
            case 65: return SceneKey.F7;              // KEY_F7
            case 66: return SceneKey.F8;              // KEY_F8
            case 67: return SceneKey.F9;              // KEY_F9
            case 68: return SceneKey.F10;             // KEY_F10
            case 87: return SceneKey.F11;             // KEY_F11
            case 88: return SceneKey.F12;             // KEY_F12

            // === 小键盘 ===
            case 82:  return SceneKey.NUMPAD_0;       // KEY_NUMPAD0
            case 79:  return SceneKey.NUMPAD_1;       // KEY_NUMPAD1
            case 80:  return SceneKey.NUMPAD_2;       // KEY_NUMPAD2
            case 81:  return SceneKey.NUMPAD_3;       // KEY_NUMPAD3
            case 75:  return SceneKey.NUMPAD_4;       // KEY_NUMPAD4
            case 76:  return SceneKey.NUMPAD_5;       // KEY_NUMPAD5
            case 77:  return SceneKey.NUMPAD_6;       // KEY_NUMPAD6
            case 71:  return SceneKey.NUMPAD_7;       // KEY_NUMPAD7
            case 72:  return SceneKey.NUMPAD_8;       // KEY_NUMPAD8
            case 73:  return SceneKey.NUMPAD_9;       // KEY_NUMPAD9
            case 78:  return SceneKey.NUMPAD_ADD;     // KEY_ADD
            case 74:  return SceneKey.NUMPAD_SUBTRACT;// KEY_SUBTRACT
            case 55:  return SceneKey.NUMPAD_MULTIPLY;// KEY_MULTIPLY
            case 181: return SceneKey.NUMPAD_DIVIDE;  // KEY_DIVIDE
            case 83:  return SceneKey.NUMPAD_DECIMAL; // KEY_DECIMAL
            case 156: return SceneKey.NUMPAD_ENTER;   // KEY_NUMPADENTER
            case 141: return SceneKey.NUMPAD_EQUALS;  // KEY_NUMPADEQUALS

            // 未识别 → UNKNOWN（国际键/多媒体键/未映射键均落此兜底）
            default: return SceneKey.UNKNOWN;
        }
    }
}
