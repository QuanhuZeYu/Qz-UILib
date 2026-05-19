package club.heiqi.uilib.ui.dom.control;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * 下拉选择控件当前值变更事件。
 */
public final class DocumentSelectChangeEvent {

    private final DocumentSelectControl source;
    private final ElementNode element;
    private final int selectedIndex;
    private final String selectedOption;
    private final boolean keyboardTriggered;
    private final int keyCode;
    private final int button;
    private final long timeNanos;

    DocumentSelectChangeEvent(DocumentSelectControl source, ElementNode element, int selectedIndex,
            String selectedOption, boolean keyboardTriggered, int keyCode, int button, long timeNanos) {
        this.source = Objects.requireNonNull(source, "source");
        this.element = Objects.requireNonNull(element, "element");
        this.selectedIndex = selectedIndex;
        this.selectedOption = Objects.requireNonNull(selectedOption, "selectedOption");
        this.keyboardTriggered = keyboardTriggered;
        this.keyCode = keyCode;
        this.button = button;
        this.timeNanos = timeNanos;
    }

    /**
     * 返回触发事件的下拉选择控件。
     *
     * @return 下拉选择控件
     */
    public DocumentSelectControl getSource() {
        return source;
    }

    /**
     * 返回控件根元素。
     *
     * @return 控件根元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 返回当前选中索引。
     *
     * @return 当前选中索引
     */
    public int getSelectedIndex() {
        return selectedIndex;
    }

    /**
     * 返回当前选中文本。
     *
     * @return 当前选中文本
     */
    public String getSelectedOption() {
        return selectedOption;
    }

    /**
     * 判断本次变化是否由键盘触发。
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
