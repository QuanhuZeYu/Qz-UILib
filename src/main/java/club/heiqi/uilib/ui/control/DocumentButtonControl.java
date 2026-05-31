package club.heiqi.uilib.ui.control;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.dom.DocumentElementActiveEvent;
import club.heiqi.uilib.ui.dom.DocumentElementActiveHandler;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementFocusEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusHandler;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiCursor;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 基于 HTML-like 元素实现的按钮控件适配器。
 */
public final class DocumentButtonControl {

    private final ElementNode element;
    private final ElementNode labelElement;
    private final TextNode labelText;
    private DocumentButtonActionHandler actionHandler;
    private boolean enabled = true;
    private boolean focusVisible;
    private boolean active;
    private boolean spacePressed;
    private int normalBackgroundColor = 0xFF3182CE;
    private int activeBackgroundColor = 0xFF2B6CB0;
    private int disabledBackgroundColor = 0xFF4A5568;
    private int focusBorderColor = 0xFFBEE3F8;
    private int textColor = 0xFFFFFFFF;
    private int disabledTextColor = 0xFFA0AEC0;

    /**
     * 创建按钮控件。
     *
     * @param document 所属 HTML-like 文档
     * @param label 按钮文本
     */
    public DocumentButtonControl(UiDocument document, String label) {
        this.element = document.button();
        this.element.setAttribute("type", "button");
        this.labelElement = document.span();
        this.labelText = labelElement.appendText(normalizeLabel(label));
        this.element.append(labelElement);
        configureElement();
        installHandlers();
        updateVisualState();
    }

    /**
     * 返回按钮根元素。
     *
     * @return 按钮根元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 返回按钮文本。
     *
     * @return 按钮文本
     */
    public String getLabel() {
        return labelText.getText();
    }

    /**
     * 设置按钮文本。
     *
     * @param label 按钮文本
     * @return 当前按钮控件
     */
    public DocumentButtonControl setLabel(String label) {
        labelText.setText(normalizeLabel(label));
        return this;
    }

    /**
     * 设置按钮是否启用。
     *
     * @param enabled 是否启用
     * @return 当前按钮控件
     */
    public DocumentButtonControl setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return this;
        }
        this.enabled = enabled;
        if (!enabled) {
            focusVisible = false;
            active = false;
            spacePressed = false;
            element.setAttribute("disabled", "true");
            element.setAttribute("aria-disabled", "true");
        } else {
            element.removeAttribute("disabled");
            element.removeAttribute("aria-disabled");
        }
        element.setFocusable(enabled);
        updateVisualState();
        return this;
    }

    /**
     * 判断按钮是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置按钮动作处理器。
     *
     * @param actionHandler 动作处理器；为 null 时清除
     * @return 当前按钮控件
     */
    public DocumentButtonControl setActionHandler(DocumentButtonActionHandler actionHandler) {
        this.actionHandler = actionHandler;
        return this;
    }

    /**
     * 设置按钮背景色。
     *
     * @param normalBackgroundColor 普通态背景色
     * @param activeBackgroundColor 按下态背景色
     * @param disabledBackgroundColor 禁用态背景色
     * @return 当前按钮控件
     */
    public DocumentButtonControl setBackgroundColors(int normalBackgroundColor, int activeBackgroundColor,
            int disabledBackgroundColor) {
        this.normalBackgroundColor = normalBackgroundColor;
        this.activeBackgroundColor = activeBackgroundColor;
        this.disabledBackgroundColor = disabledBackgroundColor;
        updateVisualState();
        return this;
    }

    /**
     * 设置键盘焦点描边颜色。
     *
     * @param focusBorderColor 键盘焦点描边颜色
     * @return 当前按钮控件
     */
    public DocumentButtonControl setFocusBorderColor(int focusBorderColor) {
        this.focusBorderColor = focusBorderColor;
        updateVisualState();
        return this;
    }

    /**
     * 设置按钮文本颜色。
     *
     * @param textColor 普通文本颜色
     * @param disabledTextColor 禁用文本颜色
     * @return 当前按钮控件
     */
    public DocumentButtonControl setTextColors(int textColor, int disabledTextColor) {
        this.textColor = textColor;
        this.disabledTextColor = disabledTextColor;
        updateVisualState();
        return this;
    }

    private void configureElement() {
        element.setFocusable(true);
        element.style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(normalBackgroundColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(999))
                .setTextColor(textColor)
                .setCursor(UiCursor.POINTER)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        labelElement.style()
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
    }

    private void installHandlers() {
        element.setActiveHandler(new DocumentElementActiveHandler() {
            @Override
            public boolean onActiveChanged(DocumentElementActiveEvent event) {
                if (event.getButton() != 0) {
                    return false;
                }
                active = event.isActive() && enabled;
                updateVisualState();
                return true;
            }
        }).setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                if (event.getButton() != 0) {
                    return false;
                }
                activate(false, 0, event.getButton(), event.getTimeNanos());
                return true;
            }
        }).setFocusHandler(new DocumentElementFocusHandler() {
            @Override
            public void onFocusChanged(DocumentElementFocusEvent event) {
                focusVisible = event.isFocused() && event.isFocusVisible() && enabled;
                if (!event.isFocused()) {
                    active = false;
                    spacePressed = false;
                }
                updateVisualState();
            }
        }).setKeyHandler(new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                if (!isActivationKey(event.getKeyCode())) {
                    return false;
                }
                event.preventDefault();
                if (isEnterKey(event.getKeyCode()) && event.getAction() == UiKeyEvent.Action.PRESSED) {
                    active = enabled;
                    updateVisualState();
                    activate(true, event.getKeyCode(), -1, event.getTimeNanos());
                    return true;
                }
                if (event.getKeyCode() == Keyboard.KEY_SPACE && event.getAction() == UiKeyEvent.Action.PRESSED) {
                    spacePressed = enabled;
                    active = enabled;
                    updateVisualState();
                    return true;
                }
                if (event.getKeyCode() == Keyboard.KEY_SPACE && event.getAction() == UiKeyEvent.Action.RELEASED) {
                    boolean shouldActivate = spacePressed && enabled;
                    spacePressed = false;
                    active = false;
                    updateVisualState();
                    if (shouldActivate) {
                        activate(true, event.getKeyCode(), -1, event.getTimeNanos());
                    }
                    return true;
                }
                if (event.getAction() == UiKeyEvent.Action.RELEASED) {
                    active = false;
                    updateVisualState();
                    return true;
                }
                return true;
            }
        });
    }

    private void activate(boolean keyboardTriggered, int keyCode, int button, long timeNanos) {
        if (!enabled || actionHandler == null) {
            return;
        }
        actionHandler.onAction(new DocumentButtonActionEvent(this, element, keyboardTriggered, keyCode, button,
                timeNanos));
    }

    private void updateVisualState() {
        int backgroundColor;
        int resolvedTextColor;
        if (!enabled) {
            backgroundColor = disabledBackgroundColor;
            resolvedTextColor = disabledTextColor;
        } else if (active) {
            backgroundColor = activeBackgroundColor;
            resolvedTextColor = textColor;
        } else {
            backgroundColor = normalBackgroundColor;
            resolvedTextColor = textColor;
        }
        element.style()
                .setBackgroundColor(backgroundColor)
                .setBorderColor(focusVisible ? focusBorderColor : 0)
                .setTextColor(resolvedTextColor)
                .setCursor(enabled ? UiCursor.POINTER : UiCursor.NOT_ALLOWED);
    }

    private static boolean isActivationKey(int keyCode) {
        return isEnterKey(keyCode) || keyCode == Keyboard.KEY_SPACE;
    }

    private static boolean isEnterKey(int keyCode) {
        return keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER;
    }

    private static String normalizeLabel(String label) {
        return label == null ? "" : label;
    }
}
