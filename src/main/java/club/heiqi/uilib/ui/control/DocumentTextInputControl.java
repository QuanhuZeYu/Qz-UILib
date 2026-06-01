package club.heiqi.uilib.ui.control;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.animation.SystemDocumentAnimationClock;
import club.heiqi.uilib.ui.dom.DocumentElementFocusEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusHandler;
import club.heiqi.uilib.ui.dom.DocumentElementHoverEvent;
import club.heiqi.uilib.ui.dom.DocumentElementHoverHandler;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.DocumentElementTextInputEvent;
import club.heiqi.uilib.ui.dom.DocumentElementTextInputHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.paint.DocumentCustomRenderer;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiCursor;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiWhiteSpace;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.render.UiRenderContext;

/**
 * 基于 HTML-like 元素实现的文本输入框控件适配器。
 */
public final class DocumentTextInputControl {

    private static final long BLINK_PERIOD_NANOS = 530_000_000L;
    private static final char DEFAULT_PASSWORD_MASK = '\u2022';

    private final ElementNode element;
    private final ElementNode textElement;
    private final TextNode textNode;
    private final StringBuilder textBuilder = new StringBuilder();
    private DocumentTextInputChangeHandler changeHandler;
    private DocumentElementKeyHandler keyHandler;
    private DocumentInputType inputType = DocumentInputType.TEXT;
    private char passwordMaskCharacter = DEFAULT_PASSWORD_MASK;
    private String placeholder = "";
    private int maxLength = 128;
    private boolean enabled = true;
    private boolean focused;
    private boolean hovered;
    private boolean readOnly;
    private boolean required;
    private long caretBlinkResetNanos;
    private int normalBackgroundColor = 0xFF222233;
    private int normalBorderColor = 0xFF555577;
    private int hoverBorderColor = 0xFF7777AA;
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
        this.element = document.input();
        this.element.setAttribute("type", "text");
        this.textElement = document.span();
        this.textNode = textElement.appendText("");
        this.element.append(textElement);
        configureElement();
        installHandlers();
        installCursorRenderer();
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
     * 设置输入类型。
     *
     * <p>切换类型会按新类型的字符规则重新过滤当前内容，被新类型拒绝的字符会被剔除；
     * 真实值始终通过 {@link #getText()} 返回，不受 PASSWORD 掩码影响。</p>
     *
     * @param inputType 输入类型；为 null 时回退为 {@link DocumentInputType#TEXT}
     * @return 当前文本输入控件
     */
    public DocumentTextInputControl setType(DocumentInputType inputType) {
        DocumentInputType resolvedType = inputType == null ? DocumentInputType.TEXT : inputType;
        if (this.inputType == resolvedType) {
            return this;
        }
        this.inputType = resolvedType;
        element.setAttribute("type", resolvedType.getAttributeValue());
        refilterCurrentText();
        syncText();
        return this;
    }

    /**
     * 返回当前输入类型。
     *
     * @return 输入类型
     */
    public DocumentInputType getType() {
        return inputType;
    }

    /**
     * 设置密码掩码字符。
     *
     * <p>仅在 {@link DocumentInputType#PASSWORD} 下影响显示，真实值不受影响。</p>
     *
     * @param maskCharacter 掩码字符
     * @return 当前文本输入控件
     */
    public DocumentTextInputControl setPasswordMaskCharacter(char maskCharacter) {
        if (this.passwordMaskCharacter == maskCharacter) {
            return this;
        }
        this.passwordMaskCharacter = maskCharacter;
        if (inputType == DocumentInputType.PASSWORD) {
            syncText();
        }
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
        if (!enabled) {
            focused = false;
            element.setAttribute("disabled", "true");
            element.setAttribute("aria-disabled", "true");
        } else {
            element.removeAttribute("disabled");
            element.removeAttribute("aria-disabled");
        }
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
     * 设置文本输入控件是否只读。
     *
     * @param readOnly 是否只读
     * @return 当前文本输入控件
     */
    public DocumentTextInputControl setReadOnly(boolean readOnly) {
        if (this.readOnly == readOnly) {
            return this;
        }
        this.readOnly = readOnly;
        if (readOnly) {
            element.setAttribute("readonly", "true");
            element.setAttribute("aria-readonly", "true");
        } else {
            element.removeAttribute("readonly");
            element.removeAttribute("aria-readonly");
        }
        return this;
    }

    /**
     * 判断文本输入控件是否只读。
     *
     * @return 是否只读
     */
    public boolean isReadOnly() {
        return readOnly;
    }

    /**
     * 设置文本输入控件是否必填。
     *
     * @param required 是否必填
     * @return 当前文本输入控件
     */
    public DocumentTextInputControl setRequired(boolean required) {
        if (this.required == required) {
            return this;
        }
        this.required = required;
        if (required) {
            element.setAttribute("required", "true");
            element.setAttribute("aria-required", "true");
        } else {
            element.removeAttribute("required");
            element.removeAttribute("aria-required");
        }
        return this;
    }

    /**
     * 判断文本输入控件是否必填。
     *
     * @return 是否必填
     */
    public boolean isRequired() {
        return required;
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
     * 设置扩展键盘处理器。
     *
     * <p>该处理器会在文本输入框自身处理完退格后继续参与处理，
     * 可用于回车提交等输入框扩展语义。</p>
     *
     * @param keyHandler 键盘处理器；为 null 时清除
     * @return 当前文本输入控件
     */
    public DocumentTextInputControl setKeyHandler(DocumentElementKeyHandler keyHandler) {
        this.keyHandler = keyHandler;
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
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.START)
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(normalBackgroundColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(4))
                .setTextColor(textColor)
                .setCursor(UiCursor.TEXT)
                .setWhiteSpace(UiWhiteSpace.NOWRAP)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        textElement.style()
                .setWhiteSpace(UiWhiteSpace.NOWRAP)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
    }

    private void installCursorRenderer() {
        element.setCustomRenderer(new DocumentCustomRenderer() {
            @Override
            public void render(UiRenderContext context, int contentLeft, int contentTop, int contentRight,
                    int contentBottom) {
                if (!focused || !enabled || context == null || contentRight <= contentLeft || contentBottom <= contentTop) {
                    return;
                }
                long elapsed = SystemDocumentAnimationClock.getInstance().getCurrentTimeNanos() - caretBlinkResetNanos;
                if (elapsed < 0) {
                    elapsed = 0;
                }
                if ((elapsed / BLINK_PERIOD_NANOS) % 2 != 0) {
                    return;
                }
                int textWidth = Math.min(Math.max(0, context.measureTextWidth(buildDisplayText())),
                        Math.max(0, contentRight - contentLeft - 1));
                int cursorLeft = resolveCursorLeft(contentLeft, contentRight, textWidth);
                int lineHeight = Math.max(1, Math.min(context.getTextLineHeight(), contentBottom - contentTop));
                int cursorTop = contentTop + Math.max(0, (contentBottom - contentTop - lineHeight) / 2);
                int cursorBottom = Math.min(contentBottom, cursorTop + lineHeight);
                context.fillRect(cursorLeft, cursorTop, Math.min(contentRight, cursorLeft + 1), cursorBottom, textColor);
            }
        });
    }

    private void installHandlers() {
        element.setFocusHandler(new DocumentElementFocusHandler() {
            @Override
            public void onFocusChanged(DocumentElementFocusEvent event) {
                focused = event.isFocused() && enabled;
                if (focused) {
                    resetCaretBlink();
                }
                updateVisualState();
            }
        }).setTextInputHandler(new DocumentElementTextInputHandler() {
            @Override
            public boolean onTextInput(DocumentElementTextInputEvent event) {
                if (!focused || !enabled || readOnly) {
                    return false;
                }
                String inputText = event.getText();
                if (inputText == null || inputText.isEmpty()) {
                    return true;
                }
                boolean changed = appendAcceptedCodePoints(textBuilder, inputText, maxLength);
                if (changed) {
                    resetCaretBlink();
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
                    if (!readOnly && textBuilder.length() > 0) {
                        int deleteStart = textBuilder.offsetByCodePoints(textBuilder.length(), -1);
                        textBuilder.delete(deleteStart, textBuilder.length());
                        resetCaretBlink();
                        syncText();
                        fireChange();
                    }
                    return true;
                }
                if (keyHandler != null) {
                    return keyHandler.onKey(event);
                }
                return false;
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

    private void updateVisualState() {
        int backgroundColor;
        int borderColor;
        if (!enabled) {
            backgroundColor = disabledBackgroundColor;
            borderColor = disabledBorderColor;
        } else if (focused) {
            backgroundColor = normalBackgroundColor;
            borderColor = focusBorderColor;
        } else if (hovered) {
            backgroundColor = normalBackgroundColor;
            borderColor = hoverBorderColor;
        } else {
            backgroundColor = normalBackgroundColor;
            borderColor = normalBorderColor;
        }
        element.style()
                .setBackgroundColor(backgroundColor)
                .setBorderColor(borderColor)
                .setCursor(enabled ? UiCursor.TEXT : UiCursor.NOT_ALLOWED);
        syncText();
    }

    private void syncText() {
        boolean showingPlaceholder = textBuilder.length() == 0 && placeholder != null && !placeholder.isEmpty();
        if (showingPlaceholder) {
            textNode.setText(placeholder);
            element.setAttribute("placeholder", placeholder);
            element.style().setTextColor(enabled ? placeholderColor : disabledTextColor);
        } else {
            String displayText = buildDisplayText();
            textNode.setText(displayText);
            element.setAttribute("value", displayText);
            if (placeholder == null || placeholder.isEmpty()) {
                element.removeAttribute("placeholder");
            } else {
                element.setAttribute("placeholder", placeholder);
            }
            element.style().setTextColor(enabled ? textColor : disabledTextColor);
        }
        if (showingPlaceholder) {
            element.setAttribute("value", "");
        }
    }

    /**
     * 构建用于显示与几何测量的文本。
     *
     * <p>PASSWORD 类型按码点数量替换为等量掩码字符，避免明文进入显示树、`value` 属性与光标测量；
     * 其余类型直接返回真实文本。</p>
     *
     * @return 显示文本
     */
    private String buildDisplayText() {
        if (inputType != DocumentInputType.PASSWORD) {
            return textBuilder.toString();
        }
        int codePointCount = textBuilder.codePointCount(0, textBuilder.length());
        StringBuilder masked = new StringBuilder(codePointCount);
        for (int index = 0; index < codePointCount; index++) {
            masked.append(passwordMaskCharacter);
        }
        return masked.toString();
    }

    /**
     * 按当前输入类型重新过滤已有内容，剔除新类型不接受的字符。
     */
    private void refilterCurrentText() {
        String current = textBuilder.toString();
        textBuilder.setLength(0);
        appendAcceptedCodePoints(textBuilder, current, maxLength);
    }

    private int resolveCursorLeft(int contentLeft, int contentRight, int textWidth) {
        int availableWidth = Math.max(0, contentRight - contentLeft);
        UiJustifyContent justifyContent = element.style().getJustifyContent();
        if (justifyContent == UiJustifyContent.CENTER) {
            return contentLeft + Math.max(0, (availableWidth - textWidth) / 2) + textWidth;
        }
        if (justifyContent == UiJustifyContent.END) {
            return Math.max(contentLeft, contentRight - textWidth);
        }
        return contentLeft + textWidth;
    }

    private void fireChange() {
        if (changeHandler != null) {
            changeHandler.onTextChanged(new DocumentTextInputChangeEvent(this, element, textBuilder.toString()));
        }
    }

    private void resetCaretBlink() {
        caretBlinkResetNanos = SystemDocumentAnimationClock.getInstance().getCurrentTimeNanos();
    }

    private boolean appendAcceptedCodePoints(StringBuilder target, String input, int maxCodePointCount) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (int index = 0; index < input.length();) {
            int codepoint = input.codePointAt(index);
            if (inputType.acceptsCodepoint(codepoint)
                    && target.codePointCount(0, target.length()) < maxCodePointCount) {
                target.appendCodePoint(codepoint);
                changed = true;
            }
            index += Character.charCount(codepoint);
        }
        return changed;
    }

    private static String normalizePlaceholder(String placeholder) {
        return placeholder == null ? "" : placeholder;
    }
}
