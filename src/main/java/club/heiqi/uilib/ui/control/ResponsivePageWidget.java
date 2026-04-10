package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.layout.UiAnchor;
import club.heiqi.uilib.ui.layout.UiInsets;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 用于界面主页面的响应式滚动容器。
 */
public class ResponsivePageWidget extends VerticalScrollPanelWidget {

    private int suggestedWidth = 640;
    private int suggestedHeight = 420;
    private float maxWidthRatio = 0.80F;
    private float maxHeightRatio = 0.84F;

    @Override
    public ResponsivePageWidget setPadding(int padding) {
        super.setPadding(padding);
        return this;
    }

    @Override
    public ResponsivePageWidget setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
        return this;
    }

    @Override
    public ResponsivePageWidget setFillColor(int fillColor) {
        super.setFillColor(fillColor);
        return this;
    }

    @Override
    public ResponsivePageWidget setBorderColor(int borderColor) {
        super.setBorderColor(borderColor);
        return this;
    }

    /**
     * 设置页面建议尺寸。
     *
     * @param suggestedWidth 建议宽度
     * @param suggestedHeight 建议高度
     * @return 当前页面
     */
    public ResponsivePageWidget setSuggestedSize(int suggestedWidth, int suggestedHeight) {
        this.suggestedWidth = Math.max(1, suggestedWidth);
        this.suggestedHeight = Math.max(1, suggestedHeight);
        return this;
    }

    /**
     * 设置页面相对父容器的最大占比。
     *
     * @param maxWidthRatio 最大宽度占比
     * @param maxHeightRatio 最大高度占比
     * @return 当前页面
     */
    public ResponsivePageWidget setViewportRatio(float maxWidthRatio, float maxHeightRatio) {
        this.maxWidthRatio = clampRatio(maxWidthRatio);
        this.maxHeightRatio = clampRatio(maxHeightRatio);
        return this;
    }

    @Override
    public void render(UiRenderContext context) {
        adaptToParentViewport();
        super.render(context);
    }

    private void adaptToParentViewport() {
        Widget parent = getParent();
        if (parent == null) {
            return;
        }

        int contentLeft = 0;
        int contentTop = 0;
        int contentWidth = parent.getWidth();
        int contentHeight = parent.getHeight();
        if (parent instanceof RelativePanelWidget) {
            RelativePanelWidget panel = (RelativePanelWidget) parent;
            contentLeft = panel.getPaddingLeft();
            contentTop = panel.getPaddingTop();
            contentWidth = Math.max(0, parent.getWidth() - panel.getPaddingLeft() - panel.getPaddingRight());
            contentHeight = Math.max(0, parent.getHeight() - panel.getPaddingTop() - panel.getPaddingBottom());
        }

        UiLayoutSpec layoutSpec = getLayoutSpec();
        UiInsets margin = layoutSpec == null ? UiInsets.ZERO : layoutSpec.getMargin();
        int availableWidth = Math.max(0, contentWidth - margin.getLeft() - margin.getRight());
        int availableHeight = Math.max(0, contentHeight - margin.getTop() - margin.getBottom());
        if (availableWidth <= 0 || availableHeight <= 0) {
            setBounds(contentLeft, contentTop, 0, 0);
            return;
        }

        int ratioWidth = Math.max(1, Math.round(availableWidth * maxWidthRatio));
        int ratioHeight = Math.max(1, Math.round(availableHeight * maxHeightRatio));

        // 页面建议尺寸更接近“舒适尺寸下限”，而不是硬性最大宽高；
        // 否则大屏下页面会被意外锁死在一个过小的卡片宽度里。
        int resolvedWidth = Math.min(availableWidth, ratioWidth);
        int resolvedHeight = Math.min(availableHeight, ratioHeight);
        resolvedWidth = Math.max(resolvedWidth, Math.min(availableWidth, suggestedWidth));
        resolvedHeight = Math.max(resolvedHeight, Math.min(availableHeight, suggestedHeight));

        UiAnchor anchor = layoutSpec == null ? UiAnchor.TOP_CENTER : layoutSpec.getAnchor();
        int[] anchorPosition = resolveAnchorPosition(anchor, contentLeft + margin.getLeft(), contentTop + margin.getTop(),
                availableWidth, availableHeight, resolvedWidth, resolvedHeight);
        int offsetX = layoutSpec == null ? 0 : layoutSpec.getOffsetX();
        int offsetY = layoutSpec == null ? 0 : layoutSpec.getOffsetY();
        setBounds(anchorPosition[0] + offsetX, anchorPosition[1] + offsetY, resolvedWidth, resolvedHeight);
    }

    private float clampRatio(float ratio) {
        return Math.max(0.05F, Math.min(ratio, 1.0F));
    }
}
