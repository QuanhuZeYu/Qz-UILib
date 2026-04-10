package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 用于观察响应式布局结果的探针控件。
 */
public class ResponsiveProbeWidget extends Widget {

    private String title;
    private int fillColor = 0xAA1D2430;
    private int borderColor = 0xFF7AA2FF;

    /**
     * 创建响应式探针控件。
     *
     * @param title 标题
     */
    public ResponsiveProbeWidget(String title) {
        this.title = title;
    }

    @Override
    protected void drawSelf(UiRenderContext context) {
        int absoluteX = getAbsoluteX();
        int absoluteY = getAbsoluteY();
        int lineHeight = Math.max(18, context.getTextLineHeight() - 2);
        context.fillRect(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), fillColor);
        context.drawBorder(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), borderColor);
        context.drawText(title, absoluteX + 10, absoluteY + 10, 0xFFFFFFFF, true);
        context.drawText("size: " + getWidth() + " x " + getHeight(), absoluteX + 10, absoluteY + 10 + lineHeight, 0xFFD7E3FF, false);
        context.drawText("pos: " + getX() + ", " + getY(), absoluteX + 10, absoluteY + 10 + lineHeight * 2, 0xFFBFD1F2, false);
    }

    @Override
    public int getPreferredWidth() {
        return Math.max(240, DefaultFontRendererAdapter.getInstance().getStringWidth(title) * 2 + 32);
    }

    @Override
    public int getPreferredHeight() {
        return 118;
    }

    public ResponsiveProbeWidget setTitle(String title) {
        this.title = title;
        return this;
    }

    public ResponsiveProbeWidget setFillColor(int fillColor) {
        this.fillColor = fillColor;
        return this;
    }

    public ResponsiveProbeWidget setBorderColor(int borderColor) {
        this.borderColor = borderColor;
        return this;
    }
}
