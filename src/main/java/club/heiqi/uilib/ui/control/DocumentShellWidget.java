package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.layout.UiInsets;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 文档内容壳，负责在视口内提供受 min/max 宽度约束的页面主内容区域。
 */
public class DocumentShellWidget extends ScrollViewportWidget {

    private int minContentWidth = 640;
    private int minContentHeight = 420;
    private int maxContentWidth = Integer.MAX_VALUE;
    private float maxViewportFillWidth = 0.80F;
    private float maxViewportFillHeight = 0.84F;

    @Override
    public DocumentShellWidget setPadding(int padding) {
        super.setPadding(padding);
        return this;
    }

    @Override
    public DocumentShellWidget setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
        return this;
    }

    @Override
    public DocumentShellWidget setFillColor(int fillColor) {
        super.setFillColor(fillColor);
        return this;
    }

    @Override
    public DocumentShellWidget setBorderColor(int borderColor) {
        super.setBorderColor(borderColor);
        return this;
    }

    /**
     * 设置文档内容壳的宽度区间，语义接近网页容器的 min-width 与 max-width。
     *
     * @param minContentWidth 最小内容宽度
     * @param maxContentWidth 最大内容宽度
     * @return 当前页面
     */
    public DocumentShellWidget setContentWidthRange(int minContentWidth, int maxContentWidth) {
        this.minContentWidth = Math.max(1, minContentWidth);
        this.maxContentWidth = Math.max(this.minContentWidth, maxContentWidth);
        return this;
    }

    /**
     * 设置内容壳的最小高度保护。
     *
     * @param minContentHeight 最小内容高度
     * @return 当前页面
     */
    public DocumentShellWidget setMinContentHeight(int minContentHeight) {
        this.minContentHeight = Math.max(1, minContentHeight);
        return this;
    }

    /**
     * 设置内容壳相对视口的最大填充占比。
     *
     * @param maxViewportFillWidth 最大宽度占比
     * @param maxViewportFillHeight 最大高度占比
     * @return 当前页面
     */
    public DocumentShellWidget setViewportFillRatio(float maxViewportFillWidth, float maxViewportFillHeight) {
        this.maxViewportFillWidth = clampRatio(maxViewportFillWidth);
        this.maxViewportFillHeight = clampRatio(maxViewportFillHeight);
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
        if (parent instanceof ViewportWidget) {
            ViewportWidget panel = (ViewportWidget) parent;
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

        int ratioWidth = Math.max(1, Math.round(availableWidth * maxViewportFillWidth));
        int ratioHeight = Math.max(1, Math.round(availableHeight * maxViewportFillHeight));

        // 内容壳宽度更接近网页里的 width:100% + max-width + min-width 组合。
        int resolvedWidth = Math.min(availableWidth, Math.min(ratioWidth, maxContentWidth));
        int resolvedHeight = Math.min(availableHeight, ratioHeight);
        resolvedWidth = Math.max(resolvedWidth, Math.min(availableWidth, minContentWidth));
        resolvedHeight = Math.max(resolvedHeight, Math.min(availableHeight, minContentHeight));

        // 网页主线中的页面壳更接近块级内容容器：横向居中、纵向从顶部开始，
        // 不再依赖 legacy anchor / offset 语义参与布局。
        int resolvedX = contentLeft + margin.getLeft() + Math.max(0, (availableWidth - resolvedWidth) / 2);
        int resolvedY = contentTop + margin.getTop();
        setBounds(resolvedX, resolvedY, resolvedWidth, resolvedHeight);
    }

    private float clampRatio(float ratio) {
        return Math.max(0.05F, Math.min(ratio, 1.0F));
    }
}
