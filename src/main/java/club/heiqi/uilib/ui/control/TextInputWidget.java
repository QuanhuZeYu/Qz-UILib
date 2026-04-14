package club.heiqi.uilib.ui.control;

import java.util.Objects;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 最小文本输入框控件。
 */
public class TextInputWidget extends Widget {

    private final StringBuilder textBuilder = new StringBuilder();
    private final TextMeasureService textMeasureService;

    private String placeholder = "点击后输入文本";
    private int maxLength = 128;
    private boolean focused;
    private boolean hovered;
    private UiControlTheme.TextInputStyle style;

    public TextInputWidget(UiControlTheme.TextInputStyle style) {
        this(style, DefaultTextMeasureService.getInstance());
    }

    public TextInputWidget(UiControlTheme.TextInputStyle style, TextMeasureService textMeasureService) {
        this.textMeasureService = Objects.requireNonNull(textMeasureService, "textMeasureService");
        this.style = Objects.requireNonNull(style, "style");
    }

    @Override
    protected void drawSelf(UiRenderContext context) {
        int absoluteX = getAbsoluteX();
        int absoluteY = getAbsoluteY();
        UiControlTheme.BoxState state = resolveVisualState();

        context.fillRect(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), state.fillColor);
        if (state.accentColor != 0) {
            context.fillRect(absoluteX + 1, absoluteY + 1, absoluteX + getWidth() - 1, absoluteY + 3, state.accentColor);
        }
        context.drawBorder(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), state.borderColor);

        String displayText = textBuilder.length() > 0 ? textBuilder.toString() : placeholder;
        String visibleText = trimToVisibleText(displayText, Math.max(1, getWidth() - style.textHorizontalPadding * 2),
                textBuilder.length() > 0);
        int textColor = textBuilder.length() > 0 ? style.textColor : style.placeholderColor;
        int textY = absoluteY + Math.max(3, (getHeight() - context.getTextLineHeight()) / 2);
        int textX = absoluteX + style.textHorizontalPadding;
        context.drawText(visibleText, textX, textY, textColor, false);

        if (focused && textBuilder.length() > 0) {
            int caretX = Math.min(absoluteX + getWidth() - style.caretRightInset,
                    textX + context.measureTextWidth(visibleText) + 1);
            context.fillRect(caretX, absoluteY + style.caretVerticalInset, caretX + style.caretWidth,
                    absoluteY + getHeight() - style.caretVerticalInset, style.caretColor);
        }
    }

    @Override
    public void onMouseEnter() {
        hovered = true;
    }

    @Override
    public void onMouseLeave() {
        hovered = false;
    }

    @Override
    public void onMouseDown(UiMouseEvent event) {}

    @Override
    public void onFocusChanged(boolean focused) {
        this.focused = focused;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public void onKeyEvent(UiKeyEvent event) {
        if (!focused || event.getAction() != UiKeyEvent.Action.PRESSED) {
            return;
        }

        if (event.getKeyCode() == Keyboard.KEY_BACK && textBuilder.length() > 0) {
            textBuilder.deleteCharAt(textBuilder.length() - 1);
            requestLayout();
        }
    }

    @Override
    public void onTextInput(UiTextInputEvent event) {
        if (!focused || event.getText() == null || event.getText().isEmpty()) {
            return;
        }

        boolean changed = false;
        for (int i = 0; i < event.getText().length();) {
            int codepoint = event.getText().codePointAt(i);
            if (!isAcceptedCodepoint(codepoint)) {
                i += Character.charCount(codepoint);
                continue;
            }
            if (textBuilder.codePointCount(0, textBuilder.length()) >= maxLength) {
                break;
            }
            textBuilder.appendCodePoint(codepoint);
            changed = true;
            i += Character.charCount(codepoint);
        }
        if (changed) {
            requestLayout();
        }
    }

    /**
     * 获取当前文本。
     *
     * @return 文本内容
     */
    public String getText() {
        return textBuilder.toString();
    }

    public TextInputWidget setText(String text) {
        String currentText = textBuilder.toString();
        String normalizedText = text == null ? "" : text;
        if (currentText.equals(normalizedText)) {
            return this;
        }
        textBuilder.setLength(0);
        if (!normalizedText.isEmpty()) {
            textBuilder.append(normalizedText);
        }
        requestLayout();
        return this;
    }

    public TextInputWidget setPlaceholder(String placeholder) {
        String normalizedPlaceholder = placeholder == null ? "" : placeholder;
        if (!normalizedPlaceholder.equals(this.placeholder)) {
            this.placeholder = normalizedPlaceholder;
            requestLayout();
            return this;
        }
        this.placeholder = normalizedPlaceholder;
        return this;
    }

    public TextInputWidget setMaxLength(int maxLength) {
        this.maxLength = Math.max(1, maxLength);
        return this;
    }

    /**
     * 设置文本输入框样式。
     *
     * @param style 文本输入框样式；为空时恢复默认样式
     * @return 当前输入框
     */
    public TextInputWidget setStyle(UiControlTheme.TextInputStyle style) {
        this.style = Objects.requireNonNull(style, "style");
        requestLayout();
        return this;
    }

    private boolean isAcceptedCodepoint(int codepoint) {
        return !Character.isISOControl(codepoint) && codepoint != '\n' && codepoint != '\r' && codepoint != '\t';
    }

    @Override
    public int getPreferredWidth() {
        String sample = placeholder == null || placeholder.isEmpty() ? "输入文本" : placeholder;
        return Math.max(style.preferredMinWidth, textMeasureService.getStringWidth(sample) * 2 + style.preferredExtraWidth);
    }

    @Override
    public int getPreferredHeight() {
        return style.height;
    }

    @Override
    public int getMinContentWidth() {
        return style.minContentWidthFloor;
    }

    private String trimToVisibleText(String source, int uiWidth, boolean keepTail) {
        int rawWidth = Math.max(1, Math.round(uiWidth / 2.0F));
        if (textMeasureService.getStringWidth(source) <= rawWidth) {
            return source;
        }
        if (!keepTail) {
            return textMeasureService.trimStringToWidth(source, rawWidth);
        }

        int start = 0;
        while (start < source.length()) {
            String suffix = source.substring(start);
            if (textMeasureService.getStringWidth(suffix) <= rawWidth) {
                return suffix;
            }
            start += Character.charCount(source.codePointAt(start));
        }
        return "";
    }

    /**
     * 解析当前视觉状态。
     *
     * @return 当前视觉状态
     */
    private UiControlTheme.BoxState resolveVisualState() {
        if (focused) {
            return style.focusedState;
        }
        if (hovered) {
            return style.hoveredState;
        }
        return style.normalState;
    }
}
