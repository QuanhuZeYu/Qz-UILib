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

    /**
     * 应用视口内边距。
     *
     * <p>该方法属于底层视口实现入口；页面作者应优先使用屏幕或文档壳暴露的语义方法。</p>
     *
     * @param padding 四边统一留白
     */
    public final void applyViewportPadding(int padding) {
        applyViewportPadding(padding, padding, padding, padding);
    }

    /**
     * 应用视口内边距。
     *
     * <p>该方法属于底层视口实现入口；页面作者应优先使用屏幕或文档壳暴露的语义方法。</p>
     *
     * @param left 左侧留白
     * @param top 上侧留白
     * @param right 右侧留白
     * @param bottom 下侧留白
     */
    public final void applyViewportPadding(int left, int top, int right, int bottom) {
        paddingLeft = Math.max(0, left);
        paddingTop = Math.max(0, top);
        paddingRight = Math.max(0, right);
        paddingBottom = Math.max(0, bottom);
        requestLayout();
    }

    /**
     * 应用视口背景色。
     *
     * <p>该方法属于底层视口实现入口；页面作者应优先使用屏幕或文档壳暴露的语义方法。</p>
     *
     * @param fillColor 背景色
     */
    public final void applyViewportFillColor(int fillColor) {
        this.fillColor = fillColor;
    }

    /**
     * 应用视口边框色。
     *
     * <p>该方法属于底层视口实现入口；页面作者应优先使用屏幕或文档壳暴露的语义方法。</p>
     *
     * @param borderColor 边框色
     */
    public final void applyViewportBorderColor(int borderColor) {
        this.borderColor = borderColor;
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
