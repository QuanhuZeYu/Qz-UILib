package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 简单面板容器。
 */
public class PanelWidget extends Widget {

    private int fillColor = 0x88141A22;
    private int borderColor = 0xFF5C6B84;

    @Override
    protected void drawSelf(UiRenderContext context) {
        int absoluteX = getAbsoluteX();
        int absoluteY = getAbsoluteY();
        context.fillRect(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), fillColor);
        context.drawBorder(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), borderColor);
    }

    public PanelWidget setFillColor(int fillColor) {
        this.fillColor = fillColor;
        return this;
    }

    public PanelWidget setBorderColor(int borderColor) {
        this.borderColor = borderColor;
        return this;
    }
}
