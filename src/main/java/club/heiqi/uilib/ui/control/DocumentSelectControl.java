package club.heiqi.uilib.ui.control;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.dom.DocumentElementBounds;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementFocusEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusHandler;
import club.heiqi.uilib.ui.dom.DocumentElementHoverEvent;
import club.heiqi.uilib.ui.dom.DocumentElementHoverHandler;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBoxSizing;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiCursor;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 基于 HTML-like 元素实现的标准下拉选择控件。
 */
public final class DocumentSelectControl {

    private static final int DEFAULT_TRIGGER_HEIGHT = 32;
    private static final int DEFAULT_OPTION_HEIGHT = 28;
    private static final String PRESERVE_FOCUS_ON_MOUSE_DOWN_ATTRIBUTE = "data-qz-preserve-focus-on-mousedown";

    private final ElementNode element;
    private final ElementNode triggerElement;
    private final ElementNode labelElement;
    private final TextNode labelText;
    private final ElementNode arrowElement;
    private final TextNode arrowText;
    private final ElementNode popupElement;
    private final ElementNode[] optionElements;
    private final String[] options;
    private DocumentSelectChangeHandler changeHandler;
    private boolean enabled = true;
    private boolean open;
    private boolean focusVisible;
    private boolean hovered;
    private int selectedIndex;
    private int highlightedIndex;
    private int triggerBackgroundColor = 0xFF222233;
    private int triggerBorderColor = 0xFF555577;
    private int hoverBorderColor = 0xFF7777AA;
    private int focusBorderColor = 0xFF5A9EF7;
    private int popupBackgroundColor = 0xFF161625;
    private int optionBackgroundColor = 0xFF2A2A3A;
    private int selectedOptionBackgroundColor = 0xFF2563EB;
    private int disabledBackgroundColor = 0xFF333344;
    private int textColor = 0xFFEEEEFF;
    private int mutedTextColor = 0xFFAAAAEE;
    private int disabledTextColor = 0xFF666677;

    /**
     * 创建下拉选择控件。
     *
     * @param document 所属 HTML-like 文档
     * @param options 选项文本；为空时会创建一个空选项
     */
    public DocumentSelectControl(UiDocument document, String... options) {
        this.options = normalizeOptions(options);
        this.element = document.select();
        this.triggerElement = document.div();
        this.labelElement = document.span();
        this.labelText = labelElement.appendText(this.options[0]);
        this.arrowElement = document.span();
        this.arrowText = arrowElement.appendText("v");
        this.popupElement = document.div();
        this.optionElements = new ElementNode[this.options.length];
        triggerElement.append(labelElement);
        triggerElement.append(arrowElement);
        element.append(triggerElement);
        element.append(popupElement);
        configureElement();
        createOptions(document);
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
        return options[selectedIndex];
    }

    /**
     * 设置当前选中索引，程序化设置默认不触发事件。
     *
     * @param selectedIndex 目标索引
     * @return 当前控件
     */
    public DocumentSelectControl setSelectedIndex(int selectedIndex) {
        return setSelectedIndex(selectedIndex, false);
    }

    /**
     * 设置当前选中索引。
     *
     * @param selectedIndex 目标索引
     * @param notify 是否触发事件
     * @return 当前控件
     */
    public DocumentSelectControl setSelectedIndex(int selectedIndex, boolean notify) {
        selectIndex(selectedIndex, notify, false, 0, -1, 0L);
        return this;
    }

    /**
     * 设置控件是否启用。
     *
     * @param enabled 是否启用
     * @return 当前控件
     */
    public DocumentSelectControl setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return this;
        }
        setOpen(false);
        this.enabled = enabled;
        if (!enabled) {
            focusVisible = false;
            hovered = false;
            element.setAttribute("disabled", "true");
            element.setAttribute("aria-disabled", "true");
            element.setFocusable(false);
        } else {
            element.removeAttribute("disabled");
            element.removeAttribute("aria-disabled");
            element.setFocusable(true);
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
     * 设置选择变更处理器。
     *
     * @param changeHandler 选择变更处理器；为 null 时清除
     * @return 当前控件
     */
    public DocumentSelectControl setChangeHandler(DocumentSelectChangeHandler changeHandler) {
        this.changeHandler = changeHandler;
        return this;
    }

    private void configureElement() {
        element.setAttribute("aria-haspopup", "listbox");
        element.setFocusable(true);
        element.style()
                .setDisplay(UiDisplay.BLOCK)
                .setPosition(UiPosition.RELATIVE)
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(DEFAULT_TRIGGER_HEIGHT))
                .setBackgroundColor(triggerBackgroundColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(4))
                .setCursor(UiCursor.POINTER)
                .setOverflowX(UiOverflow.VISIBLE)
                .setOverflowY(UiOverflow.VISIBLE)
                .setTextColor(textColor);
        triggerElement.style()
                .setDisplay(UiDisplay.FLEX)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.SPACE_BETWEEN)
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(8));
        popupElement.setAttribute("role", "listbox");
        popupElement.setAttribute(PRESERVE_FOCUS_ON_MOUSE_DOWN_ATTRIBUTE, "true");
        popupElement.style()
                .setDisplay(UiDisplay.NONE)
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(0))
                .setTop(UiStyleLength.percent(1.0F))
                .setWidth(UiStyleLength.percent(1.0F))
                .setMaxHeight(UiStyleLength.px(DEFAULT_OPTION_HEIGHT * 5))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO)
                .setBackgroundColor(popupBackgroundColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(4))
                .setZIndex(20);
    }

    private void createOptions(UiDocument document) {
        for (int index = 0; index < options.length; index++) {
            final int optionIndex = index;
            ElementNode option = document.option();
            option.appendText(options[index]);
            option.setAttribute("role", "option");
            option.style()
                    .setDisplay(UiDisplay.FLEX)
                    .setBoxSizing(UiBoxSizing.BORDER_BOX)
                    .setFlexDirection(UiFlexDirection.ROW)
                    .setAlignItems(UiAlignItems.CENTER)
                    .setJustifyContent(UiJustifyContent.START)
                    .setWidth(UiStyleLength.percent(1.0F))
                    .setHeight(UiStyleLength.px(DEFAULT_OPTION_HEIGHT))
                    .setCursor(UiCursor.POINTER)
                    .setPadding(UiStyleLength.px(8));
            option.setClickHandler(new DocumentElementClickHandler() {
                @Override
                public boolean onClick(DocumentElementClickEvent event) {
                    if (!enabled || event.getButton() != 0) {
                        return false;
                    }
                    selectIndex(optionIndex, true, false, 0, event.getButton(), event.getTimeNanos());
                    setOpen(false);
                    return true;
                }
            });
            optionElements[index] = option;
            popupElement.append(option);
        }
    }

    private void installHandlers() {
        element.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                if (!enabled || event.getButton() != 0) {
                    return false;
                }
                setOpen(!open);
                return true;
            }
        }).setFocusHandler(new DocumentElementFocusHandler() {
            @Override
            public void onFocusChanged(DocumentElementFocusEvent event) {
                focusVisible = enabled && event.isFocused() && event.isFocusVisible();
                if (!event.isFocused()) {
                    setOpen(false);
                    return;
                }
                updateVisualState();
            }
        }).setKeyHandler(new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                if (!enabled) {
                    return false;
                }
                return handleKeyEvent(event);
            }
        }).setHoverHandler(new DocumentElementHoverHandler() {
            @Override
            public boolean onHoverChanged(DocumentElementHoverEvent event) {
                hovered = event.isHovered() && enabled;
                updateVisualState();
                return false;
            }
        });
    }

    private boolean handleKeyEvent(DocumentElementKeyEvent event) {
        int keyCode = event.getKeyCode();
        UiKeyEvent.Action action = event.getAction();
        boolean repeatable = action == UiKeyEvent.Action.PRESSED || action == UiKeyEvent.Action.REPEATED;
        if (keyCode == Keyboard.KEY_ESCAPE && action == UiKeyEvent.Action.PRESSED && open) {
            setOpen(false);
            return true;
        }
        if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER || keyCode == Keyboard.KEY_SPACE)
                && action == UiKeyEvent.Action.PRESSED) {
            setOpen(!open);
            return true;
        }
        if (!repeatable) {
            return false;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            moveSelection(1, event.getTimeNanos(), keyCode);
            return true;
        }
        if (keyCode == Keyboard.KEY_UP) {
            moveSelection(-1, event.getTimeNanos(), keyCode);
            return true;
        }
        if (keyCode == Keyboard.KEY_HOME) {
            selectIndex(0, true, true, keyCode, -1, event.getTimeNanos());
            return true;
        }
        if (keyCode == Keyboard.KEY_END) {
            selectIndex(options.length - 1, true, true, keyCode, -1, event.getTimeNanos());
            return true;
        }
        return false;
    }

    private void moveSelection(int delta, long timeNanos, int keyCode) {
        int nextIndex = selectedIndex + delta;
        if (nextIndex < 0) {
            nextIndex = 0;
        }
        if (nextIndex >= options.length) {
            nextIndex = options.length - 1;
        }
        selectIndex(nextIndex, true, true, keyCode, -1, timeNanos);
    }

    private void setOpen(boolean open) {
        if (!enabled) {
            open = false;
        }
        if (this.open == open) {
            highlightedIndex = selectedIndex;
            updateVisualState();
            return;
        }
        this.open = open;
        highlightedIndex = selectedIndex;
        if (!this.open) {
            restorePopupInlinePlacement();
        }
        updateVisualState();
        if (this.open) {
            revealSelectedOption();
        }
    }

    private void selectIndex(int nextIndex, boolean notify, boolean keyboardTriggered, int keyCode, int button,
            long timeNanos) {
        int resolvedIndex = Math.max(0, Math.min(nextIndex, options.length - 1));
        boolean changed = selectedIndex != resolvedIndex;
        selectedIndex = resolvedIndex;
        highlightedIndex = resolvedIndex;
        updateVisualState();
        if (open) {
            revealSelectedOption();
        }
        if (changed && notify && changeHandler != null) {
            changeHandler.onSelectionChanged(new DocumentSelectChangeEvent(this, element, selectedIndex,
                    options[selectedIndex], keyboardTriggered, keyCode, button, timeNanos));
        }
    }

    private void revealSelectedOption() {
        if (selectedIndex < 0 || selectedIndex >= optionElements.length) {
            return;
        }
        int currentScrollTop = popupElement.getScrollTop();
        int viewportHeight = DEFAULT_OPTION_HEIGHT * 5;
        int optionTop = selectedIndex * DEFAULT_OPTION_HEIGHT;
        int optionBottom = optionTop + DEFAULT_OPTION_HEIGHT;
        int targetScrollTop = currentScrollTop;
        if (optionTop < currentScrollTop) {
            targetScrollTop = optionTop;
        } else if (optionBottom > currentScrollTop + viewportHeight) {
            targetScrollTop = optionBottom - viewportHeight;
        }
        popupElement.scrollTo(popupElement.getScrollLeft(), Math.max(0, targetScrollTop));
    }

    private void updateVisualState() {
        int backgroundColor = enabled ? triggerBackgroundColor : disabledBackgroundColor;
        int borderColor;
        if (enabled && focusVisible) {
            borderColor = focusBorderColor;
        } else if (enabled && hovered) {
            borderColor = hoverBorderColor;
        } else {
            borderColor = triggerBorderColor;
        }
        int resolvedTextColor = enabled ? textColor : disabledTextColor;
        labelText.setText(options[selectedIndex]);
        element.style()
                .setBackgroundColor(backgroundColor)
                .setBorderColor(borderColor)
                .setCursor(enabled ? UiCursor.POINTER : UiCursor.NOT_ALLOWED)
                .setTextColor(resolvedTextColor);
        labelElement.style().setTextColor(resolvedTextColor);
        arrowElement.style().setTextColor(enabled ? mutedTextColor : disabledTextColor);
        if (open && !syncPopupTopLayerPlacement()) {
            this.open = false;
        }
        arrowText.setText(this.open ? "^" : "v");
        popupElement.style()
                .setDisplay(this.open ? UiDisplay.FLEX : UiDisplay.NONE)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setBorderColor(triggerBorderColor)
                .setBackgroundColor(enabled ? popupBackgroundColor : disabledBackgroundColor);
        element.setAttribute("aria-expanded", String.valueOf(this.open));
        element.setAttribute("value", options[selectedIndex]);
        for (int index = 0; index < optionElements.length; index++) {
            boolean selected = index == selectedIndex;
            optionElements[index].setAttribute("aria-selected", String.valueOf(selected));
            if (selected) {
                optionElements[index].setAttribute("selected", "true");
            } else {
                optionElements[index].removeAttribute("selected");
            }
            optionElements[index].style()
                    .setBackgroundColor(enabled ? (selected ? selectedOptionBackgroundColor : optionBackgroundColor)
                            : disabledBackgroundColor)
                    .setCursor(enabled ? UiCursor.POINTER : UiCursor.NOT_ALLOWED)
                    .setTextColor(enabled ? (selected ? textColor : mutedTextColor) : disabledTextColor);
        }
    }

    /**
     * 将展开面板注册到文档运行时顶层。
     *
     * <p>浏览器原生 select 的下拉面板由 UA 以 top-layer 语义管理，不会离开 select 的逻辑 DOM
     * 归属，也不受普通祖先 overflow 或 stacking context 裁剪。</p>
     *
     * @return 是否成功完成顶层放置
     */
    private boolean syncPopupTopLayerPlacement() {
        if (!open) {
            return false;
        }
        DocumentElementBounds bounds = element.getDocumentBounds();
        if (!bounds.isAvailable() || bounds.getWidth() <= 0) {
            restorePopupInlinePlacement();
            return false;
        }
        popupElement.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(bounds.getLeft()))
                .setTop(UiStyleLength.px(bounds.getTop() + bounds.getHeight()))
                .setWidth(UiStyleLength.px(bounds.getWidth()))
                .clearZIndex();
        element.getOwnerDocument().__showTopLayerElement(popupElement);
        return true;
    }

    private void restorePopupInlinePlacement() {
        element.getOwnerDocument().__hideTopLayerElement(popupElement);
        if (popupElement.getParent() != element) {
            element.append(popupElement);
        }
        popupElement.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(0))
                .setTop(UiStyleLength.percent(1.0F))
                .setWidth(UiStyleLength.percent(1.0F))
                .setZIndex(20);
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
