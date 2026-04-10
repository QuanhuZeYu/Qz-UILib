package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.layout.UiAnchor;
import club.heiqi.uilib.ui.layout.UiInsets;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 无背景的响应式布局容器。
 */
public class ResponsiveContainerWidget extends RelativePanelWidget {

    public ResponsiveContainerWidget() {
        setClampChildrenInside(false);
    }

    @Override
    public ResponsiveContainerWidget setPadding(int padding) {
        super.setPadding(padding);
        return this;
    }

    @Override
    public ResponsiveContainerWidget setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
        return this;
    }

    @Override
    public ResponsiveContainerWidget setClampChildrenInside(boolean clampChildrenInside) {
        super.setClampChildrenInside(clampChildrenInside);
        return this;
    }

    @Override
    public void render(UiRenderContext context) {
        layoutChildren();
        super.render(context);
    }

    @Override
    public int getPreferredWidth() {
        int contentWidth = 0;
        for (Widget child : getChildren()) {
            UiLayoutSpec layoutSpec = child.getLayoutSpec();
            UiInsets margin = layoutSpec == null ? UiInsets.ZERO : layoutSpec.getMargin();
            int preferredWidth = resolvePreferredWidth(child, layoutSpec);
            contentWidth = Math.max(contentWidth, margin.getLeft() + preferredWidth + margin.getRight());
        }
        return getPaddingLeft() + contentWidth + getPaddingRight();
    }

    @Override
    public int getPreferredHeight() {
        return getPreferredHeightForWidth(Math.max(getPreferredWidth(), getWidth()));
    }

    @Override
    public int getPreferredHeightForWidth(int width) {
        int contentWidth = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int contentHeight = 0;

        for (Widget child : getChildren()) {
            UiLayoutSpec layoutSpec = child.getLayoutSpec();
            UiInsets margin = layoutSpec == null ? UiInsets.ZERO : layoutSpec.getMargin();
            int availableWidth = Math.max(0, contentWidth - margin.getLeft() - margin.getRight());
            int resolvedWidth = resolvePreferredWidth(child, layoutSpec, availableWidth);
            int resolvedHeight = resolvePreferredHeight(child, layoutSpec, resolvedWidth);
            contentHeight = Math.max(contentHeight, margin.getTop() + resolvedHeight + margin.getBottom());
        }
        return getPaddingTop() + contentHeight + getPaddingBottom();
    }

    @Override
    protected void drawSelf(UiRenderContext context) {}

    /**
     * 根据响应式布局规格重新布局子元素。
     */
    protected void layoutChildren() {
        int contentLeft = getPaddingLeft();
        int contentTop = getPaddingTop();
        int contentWidth = Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight());
        int contentHeight = Math.max(0, getHeight() - getPaddingTop() - getPaddingBottom());

        for (Widget child : getChildren()) {
            UiLayoutSpec layoutSpec = child.getLayoutSpec();
            if (layoutSpec == null) {
                continue;
            }

            UiInsets margin = layoutSpec.getMargin();
            int availableWidth = Math.max(0, contentWidth - margin.getLeft() - margin.getRight());
            int availableHeight = Math.max(0, contentHeight - margin.getTop() - margin.getBottom());
            int resolvedWidth = resolveLength(layoutSpec.getWidth(), availableWidth, child.getPreferredWidth());
            if (layoutSpec.isFill() && layoutSpec.getWidth().getType() == UiLength.Type.AUTO) {
                resolvedWidth = availableWidth;
            }

            int preferredHeight = child.getPreferredHeightForWidth(resolvedWidth);
            int resolvedHeight = resolveLength(layoutSpec.getHeight(), availableHeight, preferredHeight);
            if (layoutSpec.isFill() && layoutSpec.getHeight().getType() == UiLength.Type.AUTO) {
                resolvedHeight = availableHeight;
            }

            resolvedWidth = fitDimension(resolvedWidth, layoutSpec.getMinWidth(), layoutSpec.getMaxWidth(), availableWidth);
            resolvedHeight = fitDimension(resolvedHeight, layoutSpec.getMinHeight(), layoutSpec.getMaxHeight(), availableHeight);

            int[] anchorPosition = resolveAnchorPosition(layoutSpec.getAnchor(), contentLeft + margin.getLeft(),
                    contentTop + margin.getTop(), availableWidth, availableHeight, resolvedWidth, resolvedHeight);

            child.setBounds(anchorPosition[0] + layoutSpec.getOffsetX(), anchorPosition[1] + layoutSpec.getOffsetY(),
                    resolvedWidth, resolvedHeight);
        }
    }

    protected int resolveLength(UiLength length, int availableSpace, int fallback) {
        if (length == null || length.getType() == UiLength.Type.AUTO) {
            return fallback;
        }
        if (length.getType() == UiLength.Type.PERCENT) {
            return Math.round(availableSpace * length.getValue());
        }
        return Math.round(length.getValue());
    }

    protected int[] resolveAnchorPosition(UiAnchor anchor, int areaX, int areaY, int areaWidth, int areaHeight,
            int childWidth, int childHeight) {
        int x = areaX;
        int y = areaY;

        switch (anchor) {
            case TOP_CENTER:
                x = areaX + (areaWidth - childWidth) / 2;
                break;
            case TOP_RIGHT:
                x = areaX + areaWidth - childWidth;
                break;
            case CENTER_LEFT:
                y = areaY + (areaHeight - childHeight) / 2;
                break;
            case CENTER:
                x = areaX + (areaWidth - childWidth) / 2;
                y = areaY + (areaHeight - childHeight) / 2;
                break;
            case CENTER_RIGHT:
                x = areaX + areaWidth - childWidth;
                y = areaY + (areaHeight - childHeight) / 2;
                break;
            case BOTTOM_LEFT:
                y = areaY + areaHeight - childHeight;
                break;
            case BOTTOM_CENTER:
                x = areaX + (areaWidth - childWidth) / 2;
                y = areaY + areaHeight - childHeight;
                break;
            case BOTTOM_RIGHT:
                x = areaX + areaWidth - childWidth;
                y = areaY + areaHeight - childHeight;
                break;
            default:
                break;
        }
        return new int[] { x, y };
    }

    protected int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    protected int resolvePreferredWidth(Widget child, UiLayoutSpec layoutSpec) {
        return resolvePreferredWidth(child, layoutSpec, child.getPreferredWidth());
    }

    protected int resolvePreferredWidth(Widget child, UiLayoutSpec layoutSpec, int availableWidth) {
        UiLength width = layoutSpec == null ? UiLength.auto() : layoutSpec.getWidth();
        int preferredWidth = child.getPreferredWidth();
        int resolvedWidth = resolveLength(width, availableWidth, preferredWidth);
        if (layoutSpec != null && layoutSpec.isFill() && width.getType() == UiLength.Type.AUTO) {
            resolvedWidth = availableWidth;
        }
        int minWidth = layoutSpec == null ? 0 : layoutSpec.getMinWidth();
        int maxWidth = layoutSpec == null ? Integer.MAX_VALUE : layoutSpec.getMaxWidth();
        return fitDimension(resolvedWidth, minWidth, maxWidth, availableWidth);
    }

    protected int resolvePreferredHeight(Widget child, UiLayoutSpec layoutSpec, int resolvedWidth) {
        UiLength height = layoutSpec == null ? UiLength.auto() : layoutSpec.getHeight();
        int preferredHeight = child.getPreferredHeightForWidth(resolvedWidth);
        int resolvedHeight = height != null && height.getType() == UiLength.Type.PIXEL
                ? Math.round(height.getValue())
                : preferredHeight;
        int minHeight = layoutSpec == null ? 0 : layoutSpec.getMinHeight();
        int maxHeight = layoutSpec == null ? Integer.MAX_VALUE : layoutSpec.getMaxHeight();
        return fitDimension(resolvedHeight, minHeight, maxHeight, Integer.MAX_VALUE);
    }

    protected int fitDimension(int value, int min, int max, int available) {
        int safeAvailable = Math.max(0, available);
        int safeMax = Math.max(0, Math.min(max, safeAvailable));
        int safeMin = Math.max(0, Math.min(min, safeMax));
        if (safeMax < safeMin) {
            safeMax = safeMin;
        }
        return clamp(value, safeMin, safeMax);
    }
}
