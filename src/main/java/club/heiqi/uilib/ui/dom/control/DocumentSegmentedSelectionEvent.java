package club.heiqi.uilib.ui.dom.control;

import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * HTML-like 分段选择控件的选择变更事件。
 */
public final class DocumentSegmentedSelectionEvent {

    private final DocumentSegmentedSelectorControl source;
    private final ElementNode element;
    private final int selectedIndex;
    private final String selectedOption;
    private final boolean keyboardTriggered;
    private final int keyCode;
    private final int button;
    private final long timeNanos;

    /**
     * 创建分段选择变更事件。
     *
     * @param source 控件实例
     * @param element 控件根元素
     * @param selectedIndex 当前选中索引
     * @param selectedOption 当前选中文本
     * @param keyboardTriggered 是否由键盘触发
     * @param keyCode 键盘键码；非键盘触发时为 -1
     * @param button 鼠标按钮；非鼠标触发时为 -1
     * @param timeNanos 事件时间戳
     */
    public DocumentSegmentedSelectionEvent(DocumentSegmentedSelectorControl source, ElementNode element,
            int selectedIndex, String selectedOption, boolean keyboardTriggered, int keyCode, int button,
            long timeNanos) {
        this.source = source;
        this.element = element;
        this.selectedIndex = selectedIndex;
        this.selectedOption = selectedOption == null ? "" : selectedOption;
        this.keyboardTriggered = keyboardTriggered;
        this.keyCode = keyCode;
        this.button = button;
        this.timeNanos = timeNanos;
    }

    public DocumentSegmentedSelectorControl getSource() {
        return source;
    }

    public ElementNode getElement() {
        return element;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public String getSelectedOption() {
        return selectedOption;
    }

    public boolean isKeyboardTriggered() {
        return keyboardTriggered;
    }

    public int getKeyCode() {
        return keyCode;
    }

    public int getButton() {
        return button;
    }

    public long getTimeNanos() {
        return timeNanos;
    }
}
