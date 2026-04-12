package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 最小视口容器，负责提供 padding 与可选背景/边框绘制。
 */
public class ViewportWidget extends Widget {

    private int fillColor;
    private int borderColor;
    private int paddingLeft;
    private int paddingTop;
    private int paddingRight;
    private int paddingBottom;

    @Override
    protected void drawSelf(UiRenderContext context) {
        int absoluteX = getAbsoluteX();
        int absoluteY = getAbsoluteY();
        if (fillColor != 0) {
            context.fillRect(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), fillColor);
        }
        if (borderColor != 0) {
            context.drawBorder(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), borderColor);
        }
    }

    public ViewportWidget setPadding(int padding) {
        return setPadding(padding, padding, padding, padding);
    }

    public ViewportWidget setPadding(int left, int top, int right, int bottom) {
        paddingLeft = Math.max(0, left);
        paddingTop = Math.max(0, top);
        paddingRight = Math.max(0, right);
        paddingBottom = Math.max(0, bottom);
        requestLayout();
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
