package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
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
        int fillColor = 0xCC2C313A;
        if (pressed) {
            fillColor = 0xDD3A4352;
        } else if (hovered) {
            fillColor = 0xDD394353;
        }

        context.fillRect(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), fillColor);
        context.drawBorder(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), 0xFF8FB3FF);
        int textY = absoluteY + Math.max(4, (getHeight() - context.getTextLineHeight()) / 2);
        context.drawCenteredText(text, absoluteX + (getWidth() / 2), textY, 0xFFFFFFFF, true);
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
        if (shouldClick && clickHandler != null) {
            clickHandler.run();
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
        return Math.max(220, DefaultFontRendererAdapter.getInstance().getStringWidth(text) * 2 + 48);
    }

    @Override
    public int getPreferredHeight() {
        return 42;
    }
}
