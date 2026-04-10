package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.layout.UiAnchor;
import club.heiqi.uilib.ui.layout.UiInsets;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 纵向堆叠式响应式容器。
 */
public class VerticalStackWidget extends ResponsiveContainerWidget {

    private int spacing = 10;

    @Override
    public VerticalStackWidget setPadding(int padding) {
        super.setPadding(padding);
        return this;
    }

    @Override
    public VerticalStackWidget setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
        return this;
    }

    public VerticalStackWidget setSpacing(int spacing) {
        this.spacing = Math.max(0, spacing);
        return this;
    }

    @Override
    public int getPreferredWidth() {
        int widest = 0;
        for (Widget child : getChildren()) {
            UiLayoutSpec layoutSpec = child.getLayoutSpec();
            UiInsets margin = layoutSpec == null ? UiInsets.ZERO : layoutSpec.getMargin();
            widest = Math.max(widest, child.getPreferredWidth() + margin.getLeft() + margin.getRight());
        }
        return getPaddingLeft() + widest + getPaddingRight();
    }

    @Override
    public int getPreferredHeight() {
        int total = getPaddingTop() + getPaddingBottom();
        int count = 0;
        for (Widget child : getChildren()) {
            UiLayoutSpec layoutSpec = child.getLayoutSpec();
            UiInsets margin = layoutSpec == null ? UiInsets.ZERO : layoutSpec.getMargin();
            total += child.getPreferredHeight() + margin.getTop() + margin.getBottom();
            count++;
        }
        if (count > 1) {
            total += spacing * (count - 1);
        }
        return total;
    }

    @Override
    public int getPreferredHeightForWidth(int width) {
        int contentWidth = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int total = getPaddingTop() + getPaddingBottom();
        int count = 0;
        for (Widget child : getChildren()) {
            UiLayoutSpec layoutSpec = child.getLayoutSpec();
            UiInsets margin = layoutSpec == null ? UiInsets.ZERO : layoutSpec.getMargin();
            UiLength childWidth = layoutSpec == null ? UiLength.auto() : layoutSpec.getWidth();
            int availableWidth = Math.max(0, contentWidth - margin.getLeft() - margin.getRight());
            int resolvedWidth = resolveLength(childWidth, availableWidth, child.getPreferredWidth());
            if (layoutSpec != null && layoutSpec.isFill() && childWidth.getType() == UiLength.Type.AUTO) {
                resolvedWidth = availableWidth;
            }
            resolvedWidth = fitDimension(
                    resolvedWidth,
                    layoutSpec == null ? 0 : layoutSpec.getMinWidth(),
                    layoutSpec == null ? Integer.MAX_VALUE : layoutSpec.getMaxWidth(),
                    availableWidth);
            total += child.getPreferredHeightForWidth(resolvedWidth) + margin.getTop() + margin.getBottom();
            count++;
        }
        if (count > 1) {
            total += spacing * (count - 1);
        }
        return total;
    }

    @Override
    protected void layoutChildren() {
        int contentLeft = getPaddingLeft();
        int contentTop = getPaddingTop();
        int contentWidth = Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight());
        int contentHeight = Math.max(0, getHeight() - getPaddingTop() - getPaddingBottom());
        int childCount = getChildren().size();
        int[] resolvedWidths = new int[childCount];
        int[] resolvedHeights = new int[childCount];
        UiInsets[] margins = new UiInsets[childCount];
        UiAnchor[] anchors = new UiAnchor[childCount];
        UiLayoutSpec[] specs = new UiLayoutSpec[childCount];
        float totalGrow = 0.0F;
        int totalFixedHeight = 0;
        int lastGrowIndex = -1;

        for (int index = 0; index < childCount; index++) {
            Widget child = getChildren().get(index);
            UiLayoutSpec layoutSpec = child.getLayoutSpec();
            specs[index] = layoutSpec;
            margins[index] = layoutSpec == null ? UiInsets.ZERO : layoutSpec.getMargin();
            anchors[index] = layoutSpec == null ? UiAnchor.TOP_LEFT : layoutSpec.getAnchor();
            UiLength width = layoutSpec == null ? UiLength.auto() : layoutSpec.getWidth();
            UiLength height = layoutSpec == null ? UiLength.auto() : layoutSpec.getHeight();

            int availableWidth = Math.max(0, contentWidth - margins[index].getLeft() - margins[index].getRight());
            int resolvedWidth = resolveLength(width, availableWidth, child.getPreferredWidth());
            if (layoutSpec != null && layoutSpec.isFill() && width.getType() == UiLength.Type.AUTO) {
                resolvedWidth = availableWidth;
            }

            int minWidth = layoutSpec == null ? 0 : layoutSpec.getMinWidth();
            int maxWidth = layoutSpec == null ? Integer.MAX_VALUE : layoutSpec.getMaxWidth();
            resolvedWidth = fitDimension(resolvedWidth, minWidth, maxWidth, availableWidth);

            int preferredHeight = child.getPreferredHeightForWidth(resolvedWidth);
            int resolvedHeight = resolveLength(height, contentHeight, preferredHeight);
            int minHeight = layoutSpec == null ? 0 : layoutSpec.getMinHeight();
            int maxHeight = layoutSpec == null ? Integer.MAX_VALUE : layoutSpec.getMaxHeight();
            resolvedHeight = fitDimension(resolvedHeight, minHeight, maxHeight, contentHeight);

            resolvedWidths[index] = resolvedWidth;
            resolvedHeights[index] = resolvedHeight;
            totalFixedHeight += resolvedHeight + margins[index].getTop() + margins[index].getBottom();
            totalGrow += layoutSpec == null ? 0.0F : layoutSpec.getGrow();
            if (layoutSpec != null && layoutSpec.getGrow() > 0.0F) {
                lastGrowIndex = index;
            }
        }

        if (childCount > 1) {
            totalFixedHeight += spacing * (childCount - 1);
        }

        int remainingHeight = Math.max(0, contentHeight - totalFixedHeight);
        if (remainingHeight > 0 && totalGrow > 0.0F) {
            int distributed = 0;
            for (int index = 0; index < childCount; index++) {
                UiLayoutSpec layoutSpec = specs[index];
                if (layoutSpec == null || layoutSpec.getGrow() <= 0.0F) {
                    continue;
                }
                int extra = index == lastGrowIndex ? remainingHeight - distributed
                        : Math.round((remainingHeight * layoutSpec.getGrow()) / totalGrow);
                resolvedHeights[index] += Math.max(0, extra);
                distributed += Math.max(0, extra);
            }
        }

        int currentY = contentTop;

        for (int index = 0; index < childCount; index++) {
            Widget child = getChildren().get(index);
            UiInsets margin = margins[index];
            UiAnchor anchor = anchors[index];
            int resolvedWidth = resolvedWidths[index];
            int resolvedHeight = resolvedHeights[index];
            int availableWidth = Math.max(0, contentWidth - margin.getLeft() - margin.getRight());

            int x = contentLeft + margin.getLeft();
            if (anchor == UiAnchor.TOP_CENTER || anchor == UiAnchor.CENTER || anchor == UiAnchor.BOTTOM_CENTER) {
                x = contentLeft + margin.getLeft() + (availableWidth - resolvedWidth) / 2;
            } else if (anchor == UiAnchor.TOP_RIGHT || anchor == UiAnchor.CENTER_RIGHT || anchor == UiAnchor.BOTTOM_RIGHT) {
                x = contentLeft + margin.getLeft() + availableWidth - resolvedWidth;
            }

            int y = currentY + margin.getTop();
            int offsetX = specs[index] == null ? 0 : specs[index].getOffsetX();
            int offsetY = specs[index] == null ? 0 : specs[index].getOffsetY();
            child.setBounds(x + offsetX, y + offsetY, resolvedWidth, resolvedHeight);
            currentY = y + resolvedHeight + margin.getBottom() + spacing;
        }
    }
}
