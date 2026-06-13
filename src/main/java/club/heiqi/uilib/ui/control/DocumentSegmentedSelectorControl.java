package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.event.UiKeyCodes;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 基于 HTML-like 元素实现的分段选择控件。
 */
public final class DocumentSegmentedSelectorControl {

    private final ElementNode element;
    private final String[] options;
    private final DocumentButtonControl[] buttons;
    private DocumentSegmentedSelectionHandler selectionHandler;
    private boolean enabled = true;
    private int selectedIndex;
    private int selectedBackgroundColor = 0xFF2563EB;
    private int selectedActiveBackgroundColor = 0xFF1D4ED8;
    private int normalBackgroundColor = 0xFF334155;
    private int normalActiveBackgroundColor = 0xFF1E293B;
    private int disabledBackgroundColor = 0xFF1E293B;
    private int selectedTextColor = 0xFFFFFFFF;
    private int normalTextColor = 0xFFCBD5E1;
    private int disabledTextColor = 0xFF64748B;
    private int focusBorderColor = 0xFFBFDBFE;

    /**
     * 创建分段选择控件。
     *
     * @param document 所属 HTML-like 文档
     * @param options 选项文本；为空时会创建一个空选项
     */
    public DocumentSegmentedSelectorControl(UiDocument document, String... options) {
        this.options = normalizeOptions(options);
        this.element = document.div();
        this.buttons = new DocumentButtonControl[this.options.length];
        configureElement();
        createButtons(document);
        installHandlers();
        updateVisualState();
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
     * 返回选项数量。
     *
     * @return 选项数量
     */
    public int getOptionCount() {
        return options.length;
    }

    /**
     * 返回当前选中索引。
     *
     * @return 选中索引
     */
    public int getSelectedIndex() {
        return selectedIndex;
    }

    /**
     * 返回当前选中文本。
     *
     * @return 选中文本
     */
    public String getSelectedOption() {
        return options[selectedIndex];
    }

    /**
     * 设置当前选中索引，程序化设置不会触发选择事件。
     *
     * @param selectedIndex 目标索引
     * @return 当前控件
     */
    public DocumentSegmentedSelectorControl setSelectedIndex(int selectedIndex) {
        return setSelectedIndex(selectedIndex, false);
    }

    /**
     * 设置当前选中索引。
     *
     * @param selectedIndex 目标索引
     * @param notify 是否触发选择事件
     * @return 当前控件
     */
    public DocumentSegmentedSelectorControl setSelectedIndex(int selectedIndex, boolean notify) {
        selectIndex(selectedIndex, notify, false, -1, -1, 0L);
        return this;
    }

    /**
     * 设置控件是否启用。
     *
     * @param enabled 是否启用
     * @return 当前控件
     */
    public DocumentSegmentedSelectorControl setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return this;
        }
        this.enabled = enabled;
        if (enabled) {
            element.removeAttribute("disabled");
        } else {
            element.setAttribute("disabled", "true");
        }
        for (DocumentButtonControl button : buttons) {
            button.setEnabled(enabled);
        }
        updateVisualState();
        return this;
    }

    /**
     * 返回控件是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置选择变更处理器。
     *
     * @param selectionHandler 选择变更处理器；为 null 时清除
     * @return 当前控件
     */
    public DocumentSegmentedSelectorControl setSelectionHandler(DocumentSegmentedSelectionHandler selectionHandler) {
        this.selectionHandler = selectionHandler;
        return this;
    }

    /**
     * 设置选项视觉颜色。
     *
     * @param selectedBackgroundColor 选中态背景色
     * @param selectedActiveBackgroundColor 选中按下态背景色
     * @param normalBackgroundColor 普通态背景色
     * @param normalActiveBackgroundColor 普通按下态背景色
     * @param disabledBackgroundColor 禁用态背景色
     * @return 当前控件
     */
    public DocumentSegmentedSelectorControl setBackgroundColors(int selectedBackgroundColor,
            int selectedActiveBackgroundColor, int normalBackgroundColor, int normalActiveBackgroundColor,
            int disabledBackgroundColor) {
        this.selectedBackgroundColor = selectedBackgroundColor;
        this.selectedActiveBackgroundColor = selectedActiveBackgroundColor;
        this.normalBackgroundColor = normalBackgroundColor;
        this.normalActiveBackgroundColor = normalActiveBackgroundColor;
        this.disabledBackgroundColor = disabledBackgroundColor;
        updateVisualState();
        return this;
    }

    /**
     * 设置选项文本颜色。
     *
     * @param selectedTextColor 选中态文本色
     * @param normalTextColor 普通态文本色
     * @param disabledTextColor 禁用态文本色
     * @return 当前控件
     */
    public DocumentSegmentedSelectorControl setTextColors(int selectedTextColor, int normalTextColor,
            int disabledTextColor) {
        this.selectedTextColor = selectedTextColor;
        this.normalTextColor = normalTextColor;
        this.disabledTextColor = disabledTextColor;
        updateVisualState();
        return this;
    }

    /**
     * 设置键盘焦点描边颜色。
     *
     * @param focusBorderColor 键盘焦点描边颜色
     * @return 当前控件
     */
    public DocumentSegmentedSelectorControl setFocusBorderColor(int focusBorderColor) {
        this.focusBorderColor = focusBorderColor;
        updateVisualState();
        return this;
    }

    private void configureElement() {
        element.setAttribute("role", "radiogroup");
        element.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.STRETCH)
                .setColumnGap(UiStyleLength.px(4));
    }

    private void createButtons(UiDocument document) {
        for (int index = 0; index < options.length; index++) {
            final int optionIndex = index;
            DocumentButtonControl button = new DocumentButtonControl(document, options[index]);
            button.setFocusBorderColor(focusBorderColor)
                    .setActionHandler(new DocumentButtonActionHandler() {
                        @Override
                        public void onAction(DocumentButtonActionEvent event) {
                            selectIndex(optionIndex, true, event.isKeyboardTriggered(), event.getKeyCode(),
                                    event.getButton(), event.getTimeNanos());
                        }
                    });
            button.getElement().style()
                    .setFlexGrow(1.0F)
                    .setPadding(UiStyleLength.px(6));
            button.getElement().setAttribute("role", "radio");
            buttons[index] = button;
            element.append(button.getElement());
        }
    }

    private void installHandlers() {
        element.setKeyHandler(new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                if (!enabled) {
                    return false;
                }
                if (event.getAction() != UiKeyEvent.Action.PRESSED) {
                    return false;
                }
                if (event.getKeyCode() == UiKeyCodes.KEY_LEFT) {
                    int nextIndex = Math.max(0, selectedIndex - 1);
                    selectIndex(nextIndex, true, true, event.getKeyCode(), -1, event.getTimeNanos());
                    event.requestFocus(buttons[selectedIndex].getElement(), true);
                    return true;
                }
                if (event.getKeyCode() == UiKeyCodes.KEY_RIGHT) {
                    int nextIndex = Math.min(options.length - 1, selectedIndex + 1);
                    selectIndex(nextIndex, true, true, event.getKeyCode(), -1, event.getTimeNanos());
                    event.requestFocus(buttons[selectedIndex].getElement(), true);
                    return true;
                }
                return false;
            }
        });
    }

    private void selectIndex(int selectedIndex, boolean notify, boolean keyboardTriggered, int keyCode, int button,
            long timeNanos) {
        int nextIndex = Math.max(0, Math.min(selectedIndex, options.length - 1));
        if (this.selectedIndex == nextIndex) {
            return;
        }
        this.selectedIndex = nextIndex;
        updateVisualState();
        if (notify && selectionHandler != null) {
            selectionHandler.onSelectionChanged(new DocumentSegmentedSelectionEvent(this, element, nextIndex,
                    options[nextIndex], keyboardTriggered, keyCode, button, timeNanos));
        }
    }

    private void updateVisualState() {
        for (int index = 0; index < buttons.length; index++) {
            boolean selected = index == selectedIndex;
            buttons[index].setFocusBorderColor(focusBorderColor)
                    .setBackgroundColors(selected ? selectedBackgroundColor : normalBackgroundColor,
                            selected ? selectedActiveBackgroundColor : normalActiveBackgroundColor,
                            disabledBackgroundColor)
                    .setTextColors(selected ? selectedTextColor : normalTextColor, disabledTextColor);
            buttons[index].getElement().setAttribute("aria-checked", String.valueOf(selected));
        }
    }

    private static String[] normalizeOptions(String[] options) {
        if (options == null || options.length == 0) {
            return new String[] { "" };
        }
        String[] normalized = options.clone();
        for (int index = 0; index < normalized.length; index++) {
            if (normalized[index] == null) {
                normalized[index] = "";
            }
        }
        return normalized;
    }
}
