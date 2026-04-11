package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 最小视口容器，负责提供 padding 与基础背景/边框绘制。
 */
public class ViewportWidget extends Widget {

    private int fillColor = 0x88141A22;
    private int borderColor = 0xFF5C6B84;
    private int paddingLeft = 8;
    private int paddingTop = 8;
    private int paddingRight = 8;
    private int paddingBottom = 8;

    @Override
    protected void drawSelf(UiRenderContext context) {
        int absoluteX = getAbsoluteX();
        int absoluteY = getAbsoluteY();
        context.fillRect(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), fillColor);
        context.drawBorder(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), borderColor);
    }

    public ViewportWidget setPadding(int padding) {
        return setPadding(padding, padding, padding, padding);
    }

    public ViewportWidget setPadding(int left, int top, int right, int bottom) {
        paddingLeft = Math.max(0, left);
        paddingTop = Math.max(0, top);
        paddingRight = Math.max(0, right);
        paddingBottom = Math.max(0, bottom);
        return this;
    }

    public ViewportWidget setFillColor(int fillColor) {
        this.fillColor = fillColor;
        return this;
    }

    public ViewportWidget setBorderColor(int borderColor) {
        this.borderColor = borderColor;
        return this;
    }

    protected int getPaddingLeft() {
        return paddingLeft;
    }

    protected int getPaddingTop() {
        return paddingTop;
    }

    protected int getPaddingRight() {
        return paddingRight;
    }

    protected int getPaddingBottom() {
        return paddingBottom;
    }
}
