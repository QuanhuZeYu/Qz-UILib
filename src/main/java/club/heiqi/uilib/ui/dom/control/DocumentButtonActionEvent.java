package club.heiqi.uilib.ui.dom.control;

import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * HTML-like 按钮动作事件。
 */
public final class DocumentButtonActionEvent {

    private final DocumentButtonControl source;
    private final ElementNode element;
    private final boolean keyboardTriggered;
    private final int keyCode;
    private final int button;
    private final long timeNanos;

    /**
     * 创建按钮动作事件。
     *
     * @param source 按钮控件
     * @param element 按钮元素
     * @param keyboardTriggered 是否由键盘触发
     * @param keyCode 键盘触发时的键码；非键盘触发为 0
     * @param button 鼠标触发时的按钮编号；非鼠标触发为 -1
     * @param timeNanos 事件时间戳
     */
    public DocumentButtonActionEvent(DocumentButtonControl source, ElementNode element, boolean keyboardTriggered,
            int keyCode, int button, long timeNanos) {
        this.source = source;
        this.element = element;
        this.keyboardTriggered = keyboardTriggered;
        this.keyCode = keyCode;
        this.button = button;
        this.timeNanos = timeNanos;
    }

    /**
     * 返回按钮控件。
     *
     * @return 按钮控件
     */
    public DocumentButtonControl getSource() {
        return source;
    }

    /**
     * 返回按钮元素。
     *
     * @return 按钮元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 判断动作是否由键盘触发。
     *
     * @return 是否由键盘触发
     */
    public boolean isKeyboardTriggered() {
        return keyboardTriggered;
    }

    /**
     * 返回键盘触发时的键码。
     *
     * @return 键码；非键盘触发为 0
     */
    public int getKeyCode() {
        return keyCode;
    }

    /**
     * 返回鼠标触发时的按钮编号。
     *
     * @return 按钮编号；非鼠标触发为 -1
     */
    public int getButton() {
        return button;
    }

    /**
     * 返回事件时间戳。
     *
     * @return 时间戳
     */
    public long getTimeNanos() {
        return timeNanos;
    }
}
