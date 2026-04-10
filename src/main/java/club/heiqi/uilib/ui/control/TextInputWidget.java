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
        int fillColor = focused ? 0xE6131A24 : 0xD910151D;
        int borderColor = focused ? 0xFF89B4FF : (hovered ? 0xFF607697 : 0xFF35465D);

        context.fillRect(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), fillColor);
        context.fillRect(absoluteX + 1, absoluteY + 1, absoluteX + getWidth() - 1, absoluteY + 3, focused ? 0x337EB1FF : 0x1A607697);
        context.drawBorder(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), borderColor);

        String displayText = textBuilder.length() > 0 ? textBuilder.toString() : placeholder;
        String visibleText = trimToVisibleText(displayText, Math.max(1, getWidth() - 24), textBuilder.length() > 0);
        int textColor = textBuilder.length() > 0 ? 0xFFF3F7FF : 0xFF7E8A9D;
        int textY = absoluteY + Math.max(3, (getHeight() - context.getTextLineHeight()) / 2);
        int textX = absoluteX + 12;
        context.drawText(visibleText, textX, textY, textColor, false);

        if (focused && textBuilder.length() > 0) {
            int caretX = Math.min(absoluteX + getWidth() - 8, textX + context.measureTextWidth(visibleText) + 1);
            context.fillRect(caretX, absoluteY + 8, caretX + 2, absoluteY + getHeight() - 8, 0xFFD6E5FF);
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
        return Math.max(280, DefaultFontRendererAdapter.getInstance().getStringWidth(sample) * 2 + 36);
    }

    @Override
    public int getPreferredHeight() {
        return 38;
    }

    @Override
    public int getMinContentWidth() {
        return 140;
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
