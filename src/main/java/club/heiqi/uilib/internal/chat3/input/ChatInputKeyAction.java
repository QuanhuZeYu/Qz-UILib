package club.heiqi.uilib.internal.chat3.input;

/**
 * 聊天输入屏键盘节流键映射(L4 纯函数,headless 可测):LWJGL 键码 → 节流动作。

 * <p>原版 GuiChat.keyTyped 语义:Tab(15) 补全、Return(28) 发送、Up(200)/Down(208) 历史、
 * PageUp(201)/PageDown(209) 翻页;其余按键(含方向键/Home/End)交原版文本输入管线,
 * 且任何非 Tab 键都清补全循环态(GuiChat.java:91)。</p>
 */
public final class ChatInputKeyAction {

    /** 键盘节流动作。 */
    public enum Action {
        /** 发送(Enter)。 */
        SUBMIT,
        /** 历史上一条(Up)。 */
        HISTORY_UP,
        /** 历史下一条(Down)。 */
        HISTORY_DOWN,
        /** Tab 补全(Shift+Tab 反向)。 */
        TAB,
        /** 聊天区向上翻页(PageUp)。 */
        PAGE_UP,
        /** 聊天区向下翻页(PageDown)。 */
        PAGE_DOWN,
        /** 其余按键(方向键/Home/End/字符等)交原版输入管线。 */
        PASS_THROUGH
    }

    /** LWJGL 键码(原版 GuiChat 同表)。 */
    public static final int KEY_RETURN = 28;
    public static final int KEY_UP = 200;
    public static final int KEY_DOWN = 208;
    public static final int KEY_TAB = 15;
    public static final int KEY_PRIOR = 201; // Page Up
    public static final int KEY_NEXT = 209; // Page Down
    /** 方向键(原版交给 GuiTextField 移动光标;属清循环态的非 Tab 键)。 */
    public static final int KEY_LEFT = 203;
    public static final int KEY_RIGHT = 205;

    private ChatInputKeyAction() {
    }

    /** LWJGL 键码 → 节流动作。 */
    public static Action of(int keyCode) {
        switch (keyCode) {
            case KEY_RETURN:
                return Action.SUBMIT;
            case KEY_UP:
                return Action.HISTORY_UP;
            case KEY_DOWN:
                return Action.HISTORY_DOWN;
            case KEY_TAB:
                return Action.TAB;
            case KEY_PRIOR:
                return Action.PAGE_UP;
            case KEY_NEXT:
                return Action.PAGE_DOWN;
            default:
                return Action.PASS_THROUGH;
        }
    }

    /** 该键是否清补全循环态(原版 GuiChat:91:任何非 Tab 键清循环)。 */
    public static boolean clearsCompletionCycle(int keyCode) {
        return of(keyCode) != Action.TAB;
    }
}
