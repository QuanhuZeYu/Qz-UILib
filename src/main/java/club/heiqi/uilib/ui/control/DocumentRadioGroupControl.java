package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.event.UiKeyCodes;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementFocusEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusHandler;
import club.heiqi.uilib.ui.dom.DocumentElementHoverEvent;
import club.heiqi.uilib.ui.dom.DocumentElementHoverHandler;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiCursor;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 基于 HTML-like 元素实现的圆点单选组控件。
 */
public final class DocumentRadioGroupControl {

    private final ElementNode element;
    private final String[] options;
    private final ElementNode[] optionElements;
    private final ElementNode[] circleElements;
    private final ElementNode[] dotElements;
    private final ElementNode[] labelElements;
    private DocumentRadioChangeHandler changeHandler;
    private UiRadioOrientation orientation = UiRadioOrientation.VERTICAL;
    private UiStyleLength itemSpacing = UiStyleLength.px(6);
    private boolean enabled = true;
    private int selectedIndex;
    private int focusedIndex;
    private int focusVisibleIndex = -1;
    private int hoveredIndex = -1;
    private int circleNormalColor = 0xFF1F2937;
    private int circleHoverColor = 0xFF374151;
    private int circleSelectedColor = 0xFF2563EB;
    private int circleDisabledColor = 0xFF334155;
    private int dotColor = 0xFFFFFFFF;
    private int labelColor = 0xFFE5E7EB;
    private int labelDisabledColor = 0xFF64748B;
    private int focusBorderColor = 0xFFBFDBFE;

    /**
     * 创建单选组控件。
     *
     * @param document 所属 HTML-like 文档
     * @param options 选项文本；为空时会创建一个空选项
     */
    public DocumentRadioGroupControl(UiDocument document, String... options) {
        this.options = normalizeOptions(options);
        this.element = document.div();
        this.optionElements = new ElementNode[this.options.length];
        this.circleElements = new ElementNode[this.options.length];
        this.dotElements = new ElementNode[this.options.length];
        this.labelElements = new ElementNode[this.options.length];
        configureElement();
        createOptionElements(document);
        installHandlers();
        updateVisualState();
    }

    /**
     * 返回单选组根元素。
     *
     * @return 单选组根元素
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
     * 设置当前选中索引，默认不触发变更事件。
     *
     * @param selectedIndex 目标索引
     * @return 当前单选组控件
     */
    public DocumentRadioGroupControl setSelectedIndex(int selectedIndex) {
        return setSelectedIndex(selectedIndex, false);
    }

    /**
     * 设置当前选中索引。
     *
     * @param selectedIndex 目标索引
     * @param notify 是否触发变更事件
     * @return 当前单选组控件
     */
    public DocumentRadioGroupControl setSelectedIndex(int selectedIndex, boolean notify) {
        selectIndex(selectedIndex, notify, false);
        return this;
    }

    /**
     * 返回当前选中文本。
     *
     * @return 当前选中文本
     */
    public String getSelectedOption() {
        return options[selectedIndex];
    }

    /**
     * 设置控件是否启用。
     *
     * @param enabled 是否启用
     * @return 当前单选组控件
     */
    public DocumentRadioGroupControl setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return this;
        }
        this.enabled = enabled;
        if (!enabled) {
            focusVisibleIndex = -1;
            hoveredIndex = -1;
            element.setAttribute("aria-disabled", "true");
        } else {
            element.removeAttribute("aria-disabled");
        }
        for (ElementNode optionElement : optionElements) {
            optionElement.setFocusable(enabled);
        }
        updateVisualState();
        return this;
    }

    /**
     * 判断控件是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置排列方向。
     *
     * @param orientation 排列方向；为 null 时使用垂直方向
     * @return 当前单选组控件
     */
    public DocumentRadioGroupControl setOrientation(UiRadioOrientation orientation) {
        this.orientation = orientation == null ? UiRadioOrientation.VERTICAL : orientation;
        updateLayoutStyle();
        return this;
    }

    /**
     * 设置选项间距。
     *
     * @param itemSpacing 选项间距；为 null 时使用 0
     * @return 当前单选组控件
     */
    public DocumentRadioGroupControl setItemSpacing(UiStyleLength itemSpacing) {
        this.itemSpacing = itemSpacing == null ? UiStyleLength.px(0) : itemSpacing;
        updateLayoutStyle();
        return this;
    }

    /**
     * 设置选择变更处理器。
     *
     * @param changeHandler 选择变更处理器；为 null 时清除
     * @return 当前单选组控件
     */
    public DocumentRadioGroupControl setChangeHandler(DocumentRadioChangeHandler changeHandler) {
        this.changeHandler = changeHandler;
        return this;
    }

    /**
     * 设置圆点外圈颜色。
     *
     * @param normal 普通态颜色
     * @param hover 悬停态颜色
     * @param selected 选中态颜色
     * @param disabled 禁用态颜色
     * @return 当前单选组控件
     */
    public DocumentRadioGroupControl setCircleColors(int normal, int hover, int selected, int disabled) {
        this.circleNormalColor = normal;
        this.circleHoverColor = hover;
        this.circleSelectedColor = selected;
        this.circleDisabledColor = disabled;
        updateVisualState();
        return this;
    }

    /**
     * 设置内点颜色。
     *
     * @param dotColor 内点颜色
     * @return 当前单选组控件
     */
    public DocumentRadioGroupControl setDotColor(int dotColor) {
        this.dotColor = dotColor;
        updateVisualState();
        return this;
    }

    /**
     * 设置标签文本颜色。
     *
     * @param normal 普通态文本颜色
     * @param disabled 禁用态文本颜色
     * @return 当前单选组控件
     */
    public DocumentRadioGroupControl setLabelColors(int normal, int disabled) {
        this.labelColor = normal;
        this.labelDisabledColor = disabled;
        updateVisualState();
        return this;
    }

    /**
     * 设置键盘焦点描边颜色。
     *
     * @param focusBorderColor 键盘焦点描边颜色
     * @return 当前单选组控件
     */
    public DocumentRadioGroupControl setFocusBorderColor(int focusBorderColor) {
        this.focusBorderColor = focusBorderColor;
        updateVisualState();
        return this;
    }

    private void configureElement() {
        element.setAttribute("role", "radiogroup");
        element.style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.START);
        updateLayoutStyle();
    }

    private void updateLayoutStyle() {
        element.style()
                .setFlexDirection(orientation == UiRadioOrientation.HORIZONTAL ? UiFlexDirection.ROW
                        : UiFlexDirection.COLUMN)
                .setRowGap(itemSpacing)
                .setColumnGap(itemSpacing);
    }

    private void createOptionElements(UiDocument document) {
        for (int index = 0; index < options.length; index++) {
            final int optionIndex = index;
            ElementNode optionElement = document.div();
            ElementNode circleElement = document.div();
            ElementNode dotElement = document.div();
            ElementNode labelElement = document.span();
            labelElement.appendText(options[index]);
            circleElement.append(dotElement);
            optionElement.append(circleElement);
            optionElement.append(labelElement);
            optionElement.setAttribute("role", "radio")
                    .setAttribute("tabindex", "0");
            optionElement.setFocusable(enabled);
            optionElement.style()
                    .setDisplay(UiDisplay.FLEX)
                    .setFlexDirection(UiFlexDirection.ROW)
                    .setAlignItems(UiAlignItems.CENTER)
                    .setColumnGap(UiStyleLength.px(8))
                    .setPadding(UiStyleLength.px(4))
                    .setBorderWidth(UiStyleLength.px(1))
                    .setBorderStyle(UiBorderStyle.SOLID)
                    .setBorderRadius(UiStyleLength.px(6))
                    .setCursor(UiCursor.POINTER);
            circleElement.style()
                    .setDisplay(UiDisplay.FLEX)
                    .setAlignItems(UiAlignItems.CENTER)
                    .setJustifyContent(UiJustifyContent.CENTER)
                    .setWidth(UiStyleLength.px(16))
                    .setHeight(UiStyleLength.px(16))
                    .setBorderWidth(UiStyleLength.px(1))
                    .setBorderStyle(UiBorderStyle.SOLID)
                    .setBorderRadius(UiStyleLength.px(999));
            dotElement.style()
                    .setDisplay(UiDisplay.NONE)
                    .setWidth(UiStyleLength.px(8))
                    .setHeight(UiStyleLength.px(8))
                    .setBorderRadius(UiStyleLength.px(999));
            optionElement.setClickHandler(new DocumentElementClickHandler() {
                @Override
                public boolean onClick(DocumentElementClickEvent event) {
                    if (!enabled || event.getButton() != 0) {
                        return false;
                    }
                    focusedIndex = optionIndex;
                    selectIndex(optionIndex, true, false);
                    return true;
                }
            }).setHoverHandler(new DocumentElementHoverHandler() {
                @Override
                public boolean onHoverChanged(DocumentElementHoverEvent event) {
                    hoveredIndex = event.isHovered() && enabled ? optionIndex : -1;
                    updateVisualState();
                    return false;
                }
            }).setFocusHandler(new DocumentElementFocusHandler() {
                @Override
                public void onFocusChanged(DocumentElementFocusEvent event) {
                    if (event.isFocused()) {
                        focusedIndex = optionIndex;
                        focusVisibleIndex = event.isFocusVisible() && enabled ? optionIndex : -1;
                    } else if (focusVisibleIndex == optionIndex) {
                        focusVisibleIndex = -1;
                    }
                    updateVisualState();
                }
            });
            optionElements[index] = optionElement;
            circleElements[index] = circleElement;
            dotElements[index] = dotElement;
            labelElements[index] = labelElement;
            element.append(optionElement);
        }
    }

    private void installHandlers() {
        element.setKeyHandler(new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                if (!enabled || event.getAction() != UiKeyEvent.Action.PRESSED) {
                    return false;
                }
                int keyCode = event.getKeyCode();
                if (isPreviousKey(keyCode)) {
                    moveFocusedSelection(-1, event);
                    return true;
                }
                if (isNextKey(keyCode)) {
                    moveFocusedSelection(1, event);
                    return true;
                }
                if (isActivationKey(keyCode)) {
                    selectIndex(focusedIndex, true, true);
                    return true;
                }
                return false;
            }
        });
    }

    private void moveFocusedSelection(int delta, DocumentElementKeyEvent event) {
        int nextIndex = Math.max(0, Math.min(focusedIndex + delta, options.length - 1));
        focusedIndex = nextIndex;
        focusVisibleIndex = nextIndex;
        event.requestFocus(optionElements[nextIndex], true);
        selectIndex(nextIndex, true, true);
    }

    private void selectIndex(int selectedIndex, boolean notify, boolean keyboardTriggered) {
        int nextIndex = Math.max(0, Math.min(selectedIndex, options.length - 1));
        if (this.selectedIndex == nextIndex) {
            updateVisualState();
            return;
        }
        this.selectedIndex = nextIndex;
        focusedIndex = nextIndex;
        updateVisualState();
        if (notify && changeHandler != null) {
            changeHandler.onRadioChanged(new DocumentRadioChangeEvent(this, element, nextIndex, options[nextIndex],
                    keyboardTriggered));
        }
    }

    private void updateVisualState() {
        for (int index = 0; index < optionElements.length; index++) {
            boolean selected = index == selectedIndex;
            int circleColor;
            if (!enabled) {
                circleColor = circleDisabledColor;
            } else if (selected) {
                circleColor = circleSelectedColor;
            } else if (index == hoveredIndex) {
                circleColor = circleHoverColor;
            } else {
                circleColor = circleNormalColor;
            }
            optionElements[index].setAttribute("aria-checked", String.valueOf(selected));
            optionElements[index].style()
                    .setBorderColor(index == focusVisibleIndex ? focusBorderColor : 0)
                    .setCursor(enabled ? UiCursor.POINTER : UiCursor.NOT_ALLOWED);
            circleElements[index].style()
                    .setBackgroundColor(circleColor)
                    .setBorderColor(circleColor);
            dotElements[index].style()
                    .setDisplay(selected ? UiDisplay.FLEX : UiDisplay.NONE)
                    .setBackgroundColor(dotColor);
            labelElements[index].style().setTextColor(enabled ? labelColor : labelDisabledColor);
        }
    }

    private boolean isPreviousKey(int keyCode) {
        if (orientation == UiRadioOrientation.HORIZONTAL) {
            return keyCode == UiKeyCodes.KEY_LEFT;
        }
        return keyCode == UiKeyCodes.KEY_UP;
    }

    private boolean isNextKey(int keyCode) {
        if (orientation == UiRadioOrientation.HORIZONTAL) {
            return keyCode == UiKeyCodes.KEY_RIGHT;
        }
        return keyCode == UiKeyCodes.KEY_DOWN;
    }

    private static boolean isActivationKey(int keyCode) {
        return keyCode == UiKeyCodes.KEY_SPACE || keyCode == UiKeyCodes.KEY_RETURN || keyCode == UiKeyCodes.KEY_NUMPADENTER;
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
