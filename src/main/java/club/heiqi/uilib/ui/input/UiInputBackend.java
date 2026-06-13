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
