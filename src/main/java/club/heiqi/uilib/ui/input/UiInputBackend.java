package club.heiqi.uilib.ui.input;

/**
 * UI 原始输入后端。
 */
interface UiInputBackend {

    /**
     * 初始化底层输入来源。
     */
    void initialize();

    /**
     * 刷新本帧输入状态。
     */
    void tick();

    /**
     * 开启底层文本输入模式。
     */
    void beginTextInput();

    /**
     * 结束底层文本输入模式。
     */
    void endTextInput();

    /**
     * 处理宿主 `GuiScreen.keyTyped(...)` 已翻译出的字符。
     *
     * @param typedChar 已翻译字符
     * @param keyCode 原始键码；当前实现暂未使用，保留用于后续区分小键盘、宿主键语义或 IME 等扩展场景
     */
    void handleHostTypedCharacter(char typedChar, int keyCode);

    /**
     * 设置宿主键盘重复事件开关。
     *
     * @param enabled true 表示开启重复事件
     */
    void setHostKeyboardRepeatEnabled(boolean enabled);

    /**
     * 基于当前原生键盘事件构造即时输入快照。
     *
     * @return 即时输入快照；当前事件无效时返回 null
     */
    UiInputFrame createImmediateKeyboardFrame();

    /**
     * 基于当前原生鼠标事件构造即时输入快照。
     *
     * @return 即时输入快照；当前事件无效时返回 null
     */
    UiInputFrame createImmediateMouseFrame();
}
