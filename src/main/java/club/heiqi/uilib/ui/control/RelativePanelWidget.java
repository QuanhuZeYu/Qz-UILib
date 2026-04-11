package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 仅支持相对布局的容器，并在绘制前将子元素限制在容器范围内。
 */
public class RelativePanelWidget extends Widget {

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

    public RelativePanelWidget setPadding(int padding) {
        return setPadding(padding, padding, padding, padding);
    }

    public RelativePanelWidget setPadding(int left, int top, int right, int bottom) {
        paddingLeft = Math.max(0, left);
        paddingTop = Math.max(0, top);
        paddingRight = Math.max(0, right);
        paddingBottom = Math.max(0, bottom);
        return this;
    }

    public RelativePanelWidget setFillColor(int fillColor) {
        this.fillColor = fillColor;
        return this;
    }

    public RelativePanelWidget setBorderColor(int borderColor) {
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
