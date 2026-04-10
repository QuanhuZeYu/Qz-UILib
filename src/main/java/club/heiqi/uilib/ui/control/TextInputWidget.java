package club.heiqi.uilib.ui.control;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 最小文本输入框控件。
 */
public class TextInputWidget extends Widget {

    private final StringBuilder textBuilder = new StringBuilder();

    private String placeholder = "点击后输入文本";
    private int maxLength = 128;
    private boolean focused;
    private boolean hovered;

    @Override
    protected void drawSelf(UiRenderContext context) {
        int absoluteX = getAbsoluteX();
        int absoluteY = getAbsoluteY();
        int fillColor = focused ? 0xD9222630 : 0xCC1B1E24;
        int borderColor = focused ? 0xFF8FB3FF : (hovered ? 0xFF6E88B8 : 0xFF4B5362);

        context.fillRect(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), fillColor);
        context.drawBorder(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), borderColor);

        String displayText = textBuilder.length() > 0 ? textBuilder.toString() : placeholder;
        int textColor = textBuilder.length() > 0 ? 0xFFFFFFFF : 0xFF7F8794;
        int textY = absoluteY + Math.max(4, (getHeight() - context.getTextLineHeight()) / 2);
        context.drawText(trimToVisibleText(displayText, Math.max(1, getWidth() - 20), textBuilder.length() > 0), absoluteX + 10, textY, textColor, false);
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
        }
    }

    @Override
    public void onTextInput(UiTextInputEvent event) {
        if (!focused || event.getText() == null || event.getText().isEmpty()) {
            return;
        }

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
            i += Character.charCount(codepoint);
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
        textBuilder.setLength(0);
        if (text != null && !text.isEmpty()) {
            textBuilder.append(text);
        }
        return this;
    }

    public TextInputWidget setPlaceholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
        return this;
    }

    public TextInputWidget setMaxLength(int maxLength) {
        this.maxLength = Math.max(1, maxLength);
        return this;
    }

    private boolean isAcceptedCodepoint(int codepoint) {
        return !Character.isISOControl(codepoint) && codepoint != '\n' && codepoint != '\r' && codepoint != '\t';
    }

    @Override
    public int getPreferredWidth() {
        String sample = placeholder == null || placeholder.isEmpty() ? "输入文本" : placeholder;
        return Math.max(320, DefaultFontRendererAdapter.getInstance().getStringWidth(sample) * 2 + 28);
    }

    @Override
    public int getPreferredHeight() {
        return 42;
    }

    private String trimToVisibleText(String source, int uiWidth, boolean keepTail) {
        DefaultFontRendererAdapter adapter = DefaultFontRendererAdapter.getInstance();
        int rawWidth = Math.max(1, Math.round(uiWidth / 2.0F));
        if (adapter.getStringWidth(source) <= rawWidth) {
            return source;
        }
        if (!keepTail) {
            return adapter.trimStringToWidth(source, rawWidth);
        }

        int start = 0;
        while (start < source.length()) {
            String suffix = source.substring(start);
            if (adapter.getStringWidth(suffix) <= rawWidth) {
                return suffix;
            }
            start += Character.charCount(source.codePointAt(start));
        }
        return "";
    }
}
