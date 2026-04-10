package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.layout.UiAnchor;
import club.heiqi.uilib.ui.layout.UiInsets;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 横向堆叠式响应式容器。
 */
public class HorizontalStackWidget extends ResponsiveContainerWidget {

    private int spacing = 10;

    @Override
    public HorizontalStackWidget setPadding(int padding) {
        super.setPadding(padding);
        return this;
    }

    @Override
    public HorizontalStackWidget setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
        return this;
    }

    public HorizontalStackWidget setSpacing(int spacing) {
        this.spacing = Math.max(0, spacing);
        return this;
    }

    @Override
    public int getPreferredWidth() {
        int total = getPaddingLeft() + getPaddingRight();
        int count = 0;
        for (Widget child : getChildren()) {
            UiLayoutSpec layoutSpec = child.getLayoutSpec();
            UiInsets margin = layoutSpec == null ? UiInsets.ZERO : layoutSpec.getMargin();
            total += child.getPreferredWidth() + margin.getLeft() + margin.getRight();
            count++;
        }
        if (count > 1) {
            total += spacing * (count - 1);
        }
        return total;
    }

    @Override
    public int getPreferredHeight() {
        int tallest = 0;
        for (Widget child : getChildren()) {
            UiLayoutSpec layoutSpec = child.getLayoutSpec();
            UiInsets margin = layoutSpec == null ? UiInsets.ZERO : layoutSpec.getMargin();
            tallest = Math.max(tallest, child.getPreferredHeight() + margin.getTop() + margin.getBottom());
        }
        return getPaddingTop() + tallest + getPaddingBottom();
    }

    @Override
    public int getPreferredHeightForWidth(int width) {
        int contentWidth = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int tallest = 0;
        int count = getChildren().size();
        int[] resolvedWidths = new int[count];
        UiInsets[] margins = new UiInsets[count];
        int totalFixedWidth = 0;

        for (int index = 0; index < count; index++) {
            Widget child = getChildren().get(index);
            UiLayoutSpec layoutSpec = child.getLayoutSpec();
            UiInsets margin = layoutSpec == null ? UiInsets.ZERO : layoutSpec.getMargin();
            margins[index] = margin;
            UiLength childWidth = layoutSpec == null ? UiLength.auto() : layoutSpec.getWidth();
            int resolvedWidth = resolveLength(childWidth, contentWidth, child.getPreferredWidth());
            resolvedWidth = fitDimension(
                    resolvedWidth,
                    layoutSpec == null ? 0 : layoutSpec.getMinWidth(),
                    layoutSpec == null ? Integer.MAX_VALUE : layoutSpec.getMaxWidth(),
                    contentWidth);
            resolvedWidths[index] = resolvedWidth;
            totalFixedWidth += resolvedWidth + margin.getLeft() + margin.getRight();
        }

        if (count > 1) {
            totalFixedWidth += spacing * (count - 1);
        }
        shrinkWidthsToFit(resolvedWidths, margins, totalFixedWidth, contentWidth);

        for (int index = 0; index < count; index++) {
            Widget child = getChildren().get(index);
            UiInsets margin = margins[index] == null ? UiInsets.ZERO : margins[index];
            int resolvedWidth = resolvedWidths[index];
            tallest = Math.max(tallest, child.getPreferredHeightForWidth(resolvedWidth) + margin.getTop() + margin.getBottom());
        }
        return getPaddingTop() + tallest + getPaddingBottom();
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
        int totalFixedWidth = 0;
        int lastGrowIndex = -1;

        for (int index = 0; index < childCount; index++) {
            Widget child = getChildren().get(index);
            UiLayoutSpec layoutSpec = child.getLayoutSpec();
            specs[index] = layoutSpec;
            margins[index] = layoutSpec == null ? UiInsets.ZERO : layoutSpec.getMargin();
            anchors[index] = layoutSpec == null ? UiAnchor.TOP_LEFT : layoutSpec.getAnchor();
            UiLength width = layoutSpec == null ? UiLength.auto() : layoutSpec.getWidth();
            UiLength height = layoutSpec == null ? UiLength.auto() : layoutSpec.getHeight();

            int availableHeight = Math.max(0, contentHeight - margins[index].getTop() - margins[index].getBottom());
            int resolvedHeight = resolveLength(height, availableHeight, child.getPreferredHeight());
            if (layoutSpec != null && layoutSpec.isFill() && height.getType() == UiLength.Type.AUTO) {
                resolvedHeight = availableHeight;
            }

            int minHeight = layoutSpec == null ? 0 : layoutSpec.getMinHeight();
            int maxHeight = layoutSpec == null ? Integer.MAX_VALUE : layoutSpec.getMaxHeight();
            resolvedHeight = fitDimension(resolvedHeight, minHeight, maxHeight, availableHeight);

            int preferredWidth = child.getPreferredWidth();
            int resolvedWidth = resolveLength(width, contentWidth, preferredWidth);
            int minWidth = layoutSpec == null ? 0 : layoutSpec.getMinWidth();
            int maxWidth = layoutSpec == null ? Integer.MAX_VALUE : layoutSpec.getMaxWidth();
            resolvedWidth = fitDimension(resolvedWidth, minWidth, maxWidth, contentWidth);

            resolvedWidths[index] = resolvedWidth;
            resolvedHeights[index] = resolvedHeight;
            totalFixedWidth += resolvedWidth + margins[index].getLeft() + margins[index].getRight();
            totalGrow += layoutSpec == null ? 0.0F : layoutSpec.getGrow();
            if (layoutSpec != null && layoutSpec.getGrow() > 0.0F) {
                lastGrowIndex = index;
            }
        }

        if (childCount > 1) {
            totalFixedWidth += spacing * (childCount - 1);
        }

        shrinkWidthsToFit(resolvedWidths, margins, totalFixedWidth, contentWidth);
        totalFixedWidth = 0;
        for (int index = 0; index < childCount; index++) {
            totalFixedWidth += resolvedWidths[index] + margins[index].getLeft() + margins[index].getRight();
        }
        if (childCount > 1) {
            totalFixedWidth += spacing * (childCount - 1);
        }

        int remainingWidth = Math.max(0, contentWidth - totalFixedWidth);
        if (remainingWidth > 0 && totalGrow > 0.0F) {
            int distributed = 0;
            for (int index = 0; index < childCount; index++) {
                UiLayoutSpec layoutSpec = specs[index];
                if (layoutSpec == null || layoutSpec.getGrow() <= 0.0F) {
                    continue;
                }
                int extra = index == lastGrowIndex ? remainingWidth - distributed
                        : Math.round((remainingWidth * layoutSpec.getGrow()) / totalGrow);
                resolvedWidths[index] += Math.max(0, extra);
                distributed += Math.max(0, extra);
            }
        }

        int currentX = contentLeft;

        for (int index = 0; index < childCount; index++) {
            Widget child = getChildren().get(index);
            UiInsets margin = margins[index];
            UiAnchor anchor = anchors[index];
            int resolvedWidth = resolvedWidths[index];
            int resolvedHeight = resolvedHeights[index];
            int availableHeight = Math.max(0, contentHeight - margin.getTop() - margin.getBottom());

            int y = contentTop + margin.getTop();
            if (anchor == UiAnchor.CENTER_LEFT || anchor == UiAnchor.CENTER || anchor == UiAnchor.CENTER_RIGHT) {
                y = contentTop + margin.getTop() + (availableHeight - resolvedHeight) / 2;
            } else if (anchor == UiAnchor.BOTTOM_LEFT || anchor == UiAnchor.BOTTOM_CENTER || anchor == UiAnchor.BOTTOM_RIGHT) {
                y = contentTop + margin.getTop() + availableHeight - resolvedHeight;
            }

            int x = currentX + margin.getLeft();
            int offsetX = specs[index] == null ? 0 : specs[index].getOffsetX();
            int offsetY = specs[index] == null ? 0 : specs[index].getOffsetY();
            child.setBounds(x + offsetX, y + offsetY, resolvedWidth, resolvedHeight);
            currentX = x + resolvedWidth + margin.getRight() + spacing;
        }
    }

    private void shrinkWidthsToFit(int[] widths, UiInsets[] margins, int totalFixedWidth, int contentWidth) {
        int overflow = totalFixedWidth - contentWidth;
        if (overflow <= 0 || widths.length == 0) {
            return;
        }

        int shrinkable = 0;
        for (int width : widths) {
            shrinkable += Math.max(0, width);
        }
        if (shrinkable <= 0) {
            return;
        }

        int removed = 0;
        int lastShrinkIndex = widths.length - 1;
        for (int i = widths.length - 1; i >= 0; i--) {
            if (widths[i] > 0) {
                lastShrinkIndex = i;
                break;
            }
        }

        for (int index = 0; index < widths.length; index++) {
            int width = widths[index];
            if (width <= 0) {
                continue;
            }
            int cut = index == lastShrinkIndex ? overflow - removed : Math.round((overflow * width) / (float) shrinkable);
            cut = Math.max(0, Math.min(cut, widths[index]));
            widths[index] -= cut;
            removed += cut;
        }
    }
}
