package club.heiqi.uilib.ui.control;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 最小按钮控件。
 */
public class ButtonWidget extends Widget {

    private String text;
    private Runnable clickHandler;
    private boolean hovered;
    private boolean pressed;
    private boolean focused;

    /**
     * 使用文本创建按钮。
     *
     * @param text 按钮文本
     */
    public ButtonWidget(String text) {
        this.text = text;
    }

    @Override
    protected void drawSelf(UiRenderContext context) {
        int absoluteX = getAbsoluteX();
        int absoluteY = getAbsoluteY();
        int fillColor = 0xDD243041;
        int borderColor = 0xFF44556E;
        int accentColor = 0x335D86C5;
        if (pressed) {
            fillColor = 0xDD1D2938;
            borderColor = 0xFF3B4B62;
            accentColor = 0x22486EA7;
        } else if (focused) {
            fillColor = 0xDD2A4161;
            borderColor = 0xFF9CC3FF;
            accentColor = 0x447EB1FF;
        } else if (hovered) {
            fillColor = 0xDD2C3B51;
            borderColor = 0xFF5B7293;
            accentColor = 0x336891D0;
        }

        context.fillRect(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), fillColor);
        context.fillRect(absoluteX + 1, absoluteY + 1, absoluteX + getWidth() - 1, absoluteY + 3, accentColor);
        context.drawBorder(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), borderColor);
        int textY = absoluteY + Math.max(3, (getHeight() - context.getTextLineHeight()) / 2);
        context.drawCenteredText(text, absoluteX + (getWidth() / 2), textY, 0xFFF7FAFF, false);
    }

    @Override
    public void onMouseEnter() {
        hovered = true;
    }

    @Override
    public void onMouseLeave() {
        hovered = false;
        pressed = false;
    }

    @Override
    public void onMouseDown(UiMouseEvent event) {
        if (event.getButton() == 0) {
            pressed = true;
        }
    }

    @Override
    public void onMouseUp(UiMouseEvent event) {
        boolean shouldClick = pressed && event.getButton() == 0 && contains(event.getMouseX(), event.getMouseY());
        pressed = false;
        if (shouldClick) {
            triggerClick();
        }
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public void onFocusChanged(boolean focused) {
        this.focused = focused;
    }

    @Override
    public void onKeyEvent(UiKeyEvent event) {
        if (event.getAction() != UiKeyEvent.Action.PRESSED) {
            return;
        }
        if (event.getKeyCode() == Keyboard.KEY_RETURN || event.getKeyCode() == Keyboard.KEY_NUMPADENTER
                || event.getKeyCode() == Keyboard.KEY_SPACE) {
            triggerClick();
        }
    }

    public ButtonWidget setText(String text) {
        this.text = text;
        return this;
    }

    public ButtonWidget setClickHandler(Runnable clickHandler) {
        this.clickHandler = clickHandler;
        return this;
    }

    @Override
    public int getPreferredWidth() {
        return Math.max(148, DefaultFontRendererAdapter.getInstance().getStringWidth(text) * 2 + 36);
    }

    @Override
    public int getPreferredHeight() {
        return 38;
    }

    @Override
    public int getMinContentWidth() {
        return Math.max(92, DefaultFontRendererAdapter.getInstance().getStringWidth(text) * 2 + 24);
    }

    protected void triggerClick() {
        if (clickHandler != null) {
            clickHandler.run();
        }
    }
}
