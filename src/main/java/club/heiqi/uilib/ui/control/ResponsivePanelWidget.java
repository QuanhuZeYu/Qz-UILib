package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.render.UiRenderContext;

/**
 * 带背景和边框的响应式面板容器。
 */
public class ResponsivePanelWidget extends ResponsiveContainerWidget {

    private int fillColor = 0x88141A22;
    private int borderColor = 0xFF5C6B84;

    public ResponsivePanelWidget() {
        setClipChildren(true);
    }

    @Override
    protected void drawSelf(UiRenderContext context) {
        int absoluteX = getAbsoluteX();
        int absoluteY = getAbsoluteY();
        context.fillRect(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), fillColor);
        context.drawBorder(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), borderColor);
    }

    @Override
    public ResponsivePanelWidget setPadding(int padding) {
        super.setPadding(padding);
        return this;
    }

    @Override
    public ResponsivePanelWidget setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
        return this;
    }

    @Override
    public ResponsivePanelWidget setClampChildrenInside(boolean clampChildrenInside) {
        super.setClampChildrenInside(clampChildrenInside);
        return this;
    }

    public ResponsivePanelWidget setFillColor(int fillColor) {
        this.fillColor = fillColor;
        return this;
    }

    public ResponsivePanelWidget setBorderColor(int borderColor) {
        this.borderColor = borderColor;
        return this;
    }
}
