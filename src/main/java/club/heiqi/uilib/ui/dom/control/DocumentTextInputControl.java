package club.heiqi.uilib.ui.dom.control;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.dom.DocumentElementFocusEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusHandler;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.DocumentElementTextInputEvent;
import club.heiqi.uilib.ui.dom.DocumentElementTextInputHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * 基于 HTML-like 元素实现的文本输入框控件适配器。
 */
public final class DocumentTextInputControl {

    private final ElementNode element;
    private final TextNode textNode;
    private final StringBuilder textBuilder = new StringBuilder();
    private DocumentTextInputChangeHandler changeHandler;
    private String placeholder = "";
    private int maxLength = 128;
    private boolean enabled = true;
    private boolean focused;
    private int normalBackgroundColor = 0xFF222233;
    private int normalBorderColor = 0xFF555577;
    private int focusBorderColor = 0xFF5A9EF7;
    private int disabledBackgroundColor = 0xFF333344;
    private int disabledBorderColor = 0xFF444455;
    private int textColor = 0xFFEEEEFF;
    private int placeholderColor = 0xFF777799;
    private int disabledTextColor = 0xFF666677;

    /**
     * 创建文本输入控件。
     *
     * @param document 所属 HTML-like 文档
     */
    public DocumentTextInputControl(UiDocument document) {
        this.element = document.div();
        this.textNode = element.appendText("");
        configureElement();
        installHandlers();
        updateVisualState();
    }

    /**
     * 返回文本输入控件根元素。
     *
     * @return 文本输入控件根元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 返回当前文本内容。
     *
     * @return 当前文本内容
     */
    public String getText() {
        return textBuilder.toString();
    }

    /**
     * 设置文本内容。
     *
     * @param text 文本内容；为 null 时清空
     * @return 当前文本输入控件
     */
    public DocumentTextInputControl setText(String text) {
        textBuilder.setLength(0);
        if (text != null) {
            appendAcceptedCodePoints(textBuilder, text, maxLength);
        }
        syncText();
        return this;
    }

    /**
     * 设置占位文本。
     *
     * @param placeholder 占位文本；为 null 时清空
     * @return 当前文本输入控件
     */
    public DocumentTextInputControl setPlaceholder(String placeholder) {
        this.placeholder = normalizePlaceholder(placeholder);
        if (textBuilder.length() == 0) {
            syncText();
        }
        return this;
    }

    /**
     * 设置最大输入长度。
     *
     * @param maxLength 最大输入长度
     * @return 当前文本输入控件
     */
    public DocumentTextInputControl setMaxLength(int maxLength) {
        this.maxLength = Math.max(1, maxLength);
        return this;
    }

    /**
     * 设置文本输入控件是否启用。
     *
     * @param enabled 是否启用
     * @return 当前文本输入控件
     */
    public DocumentTextInputControl setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return this;
        }
        this.enabled = enabled;
        element.setFocusable(enabled);
        updateVisualState();
        return this;
    }

    /**
     * 判断文本输入控件是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 判断文本输入控件当前是否聚焦。
     *
     * @return 是否聚焦
     */
    public boolean isFocused() {
        return focused;
    }

    /**
     * 设置文本变更处理器。
     *
     * @param changeHandler 文本变更处理器；为 null 时清除
     * @return 当前文本输入控件
     */
    public DocumentTextInputControl setChangeHandler(DocumentTextInputChangeHandler changeHandler) {
        this.changeHandler = changeHandler;
        return this;
    }

    /**
     * 设置正常态背景色。
     *
     * @param normalBackgroundColor 正常态背景色
     * @return 当前文本输入控件
     */
    public DocumentTextInputControl setNormalBackgroundColor(int normalBackgroundColor) {
        this.normalBackgroundColor = normalBackgroundColor;
        updateVisualState();
        return this;
    }

    /**
     * 设置正常态边框色。
     *
     * @param normalBorderColor 正常态边框色
     * @return 当前文本输入控件
     */
    public DocumentTextInputControl setNormalBorderColor(int normalBorderColor) {
        this.normalBorderColor = normalBorderColor;
        updateVisualState();
        return this;
    }

    /**
     * 设置聚焦态边框色。
     *
     * @param focusBorderColor 聚焦态边框色
     * @return 当前文本输入控件
     */
    public DocumentTextInputControl setFocusBorderColor(int focusBorderColor) {
        this.focusBorderColor = focusBorderColor;
        updateVisualState();
        return this;
    }

    /**
     * 设置文本颜色。
     *
     * @param textColor 文本颜色
     * @param placeholderColor 占位文本颜色
     * @param disabledTextColor 禁用文本颜色
     * @return 当前文本输入控件
     */
    public DocumentTextInputControl setTextColors(int textColor, int placeholderColor, int disabledTextColor) {
        this.textColor = textColor;
        this.placeholderColor = placeholderColor;
        this.disabledTextColor = disabledTextColor;
        updateVisualState();
        return this;
    }

    private void configureElement() {
        element.setFocusable(true);
        element.style()
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(normalBackgroundColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(4))
                .setTextColor(textColor)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
    }

    private void installHandlers() {
        element.setFocusHandler(new DocumentElementFocusHandler() {
            @Override
            public void onFocusChanged(DocumentElementFocusEvent event) {
                focused = event.isFocused() && enabled;
                updateVisualState();
            }
        }).setTextInputHandler(new DocumentElementTextInputHandler() {
            @Override
            public boolean onTextInput(DocumentElementTextInputEvent event) {
                if (!focused || !enabled) {
                    return false;
                }
                String inputText = event.getText();
                if (inputText == null || inputText.isEmpty()) {
                    return true;
                }
                boolean changed = appendAcceptedCodePoints(textBuilder, inputText, maxLength);
                if (changed) {
                    syncText();
                    fireChange();
                }
                return true;
            }
        }).setKeyHandler(new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                if (!focused || !enabled) {
                    return false;
                }
                if (event.getKeyCode() == Keyboard.KEY_BACK
                        && (event.getAction() == UiKeyEvent.Action.PRESSED
                                || event.getAction() == UiKeyEvent.Action.REPEATED)) {
                    if (textBuilder.length() > 0) {
                        int deleteStart = textBuilder.offsetByCodePoints(textBuilder.length(), -1);
                        textBuilder.delete(deleteStart, textBuilder.length());
                        syncText();
                        fireChange();
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private void updateVisualState() {
        int backgroundColor;
        int borderColor;
        if (!enabled) {
            backgroundColor = disabledBackgroundColor;
            borderColor = disabledBorderColor;
        } else if (focused) {
            backgroundColor = normalBackgroundColor;
            borderColor = focusBorderColor;
        } else {
            backgroundColor = normalBackgroundColor;
            borderColor = normalBorderColor;
        }
        element.style()
                .setBackgroundColor(backgroundColor)
                .setBorderColor(borderColor);
        syncText();
    }

    private void syncText() {
        boolean showingPlaceholder = textBuilder.length() == 0 && placeholder != null && !placeholder.isEmpty();
        if (showingPlaceholder) {
            textNode.setText(placeholder);
            element.style().setTextColor(enabled ? placeholderColor : disabledTextColor);
        } else {
            textNode.setText(textBuilder.toString());
            element.style().setTextColor(enabled ? textColor : disabledTextColor);
        }
    }

    private void fireChange() {
        if (changeHandler != null) {
            changeHandler.onTextChanged(new DocumentTextInputChangeEvent(this, element, textBuilder.toString()));
        }
    }

    private static boolean appendAcceptedCodePoints(StringBuilder target, String input, int maxCodePointCount) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (int index = 0; index < input.length();) {
            int codepoint = input.codePointAt(index);
            if (isAcceptedCodepoint(codepoint)
                    && target.codePointCount(0, target.length()) < maxCodePointCount) {
                target.appendCodePoint(codepoint);
                changed = true;
            }
            index += Character.charCount(codepoint);
        }
        return changed;
    }

    private static boolean isAcceptedCodepoint(int codepoint) {
        return !Character.isISOControl(codepoint) && codepoint != '\n' && codepoint != '\r' && codepoint != '\t';
    }

    private static String normalizePlaceholder(String placeholder) {
        return placeholder == null ? "" : placeholder;
    }
}
