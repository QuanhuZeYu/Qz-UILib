package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.layout.UiAnchor;
import club.heiqi.uilib.ui.layout.UiInsets;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 横向堆叠式响应式容器。
 *
 * @deprecated 仅保留兼容旧布局路径，网页化主线应优先使用 {@link DivWidget}。
 */
@Deprecated
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
            total += measureIntrinsic(child).getWidth() + margin.getLeft() + margin.getRight();
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
            tallest = Math.max(tallest, measureIntrinsic(child).getHeight() + margin.getTop() + margin.getBottom());
        }
        return getPaddingTop() + tallest + getPaddingBottom();
    }

    @Override
    public int getPreferredHeightForWidth(int width) {
        int contentWidth = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int tallest = 0;
        int count = getChildren().size();
        int[] resolvedWidths = new int[count];
        int[] minimumWidths = new int[count];
        UiInsets[] margins = new UiInsets[count];
        int totalFixedWidth = 0;

        for (int index = 0; index < count; index++) {
            Widget child = getChildren().get(index);
            UiLayoutSpec layoutSpec = child.getLayoutSpec();
            UiInsets margin = layoutSpec == null ? UiInsets.ZERO : layoutSpec.getMargin();
            margins[index] = margin;
            UiLength childWidth = layoutSpec == null ? UiLength.auto() : layoutSpec.getWidth();
            int resolvedWidth = resolveLength(childWidth, contentWidth, measureIntrinsic(child).getWidth());
            resolvedWidth = fitDimension(
                    resolvedWidth,
                    layoutSpec == null ? 0 : layoutSpec.getMinWidth(),
                    layoutSpec == null ? Integer.MAX_VALUE : layoutSpec.getMaxWidth(),
                    contentWidth);
            resolvedWidths[index] = resolvedWidth;
            minimumWidths[index] = Math.min(resolvedWidth, Math.max(0, Math.min(layoutSpec == null ? 0 : layoutSpec.getMinWidth(), contentWidth)));
            totalFixedWidth += resolvedWidth + margin.getLeft() + margin.getRight();
        }

        if (count > 1) {
            totalFixedWidth += spacing * (count - 1);
        }
        shrinkWidthsToFit(resolvedWidths, minimumWidths, totalFixedWidth, contentWidth);

        for (int index = 0; index < count; index++) {
            Widget child = getChildren().get(index);
            UiInsets margin = margins[index] == null ? UiInsets.ZERO : margins[index];
            int resolvedWidth = resolvedWidths[index];
            tallest = Math.max(tallest, measureForWidth(child, resolvedWidth).getHeight() + margin.getTop() + margin.getBottom());
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
        int[] minimumWidths = new int[childCount];
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
            int resolvedHeight = resolveLength(height, availableHeight, measureIntrinsic(child).getHeight());
            if (layoutSpec != null && layoutSpec.isFill() && height.getType() == UiLength.Type.AUTO) {
                resolvedHeight = availableHeight;
            }

            int minHeight = layoutSpec == null ? 0 : layoutSpec.getMinHeight();
            int maxHeight = layoutSpec == null ? Integer.MAX_VALUE : layoutSpec.getMaxHeight();
            resolvedHeight = fitDimension(resolvedHeight, minHeight, maxHeight, availableHeight);

            int preferredWidth = measureIntrinsic(child).getWidth();
            int resolvedWidth = resolveLength(width, contentWidth, preferredWidth);
            int minWidth = layoutSpec == null ? 0 : layoutSpec.getMinWidth();
            int maxWidth = layoutSpec == null ? Integer.MAX_VALUE : layoutSpec.getMaxWidth();
            resolvedWidth = fitDimension(resolvedWidth, minWidth, maxWidth, contentWidth);

            resolvedWidths[index] = resolvedWidth;
            minimumWidths[index] = Math.min(resolvedWidth, Math.max(0, Math.min(minWidth, contentWidth)));
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

        shrinkWidthsToFit(resolvedWidths, minimumWidths, totalFixedWidth, contentWidth);
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

        for (int index = 0; index < childCount; index++) {
            resolvedHeights[index] = resolveChildHeight(getChildren().get(index), specs[index], margins[index], contentHeight,
                    resolvedWidths[index]);
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

    /**
     * 先尽量保住每个子项的最小宽度，只有在总最小宽度仍放不下时才继续压缩。
     *
     * @param widths 当前子项宽度
     * @param minimumWidths 子项在当前容器宽度下可接受的最小宽度
     * @param totalFixedWidth 当前总宽度
     * @param contentWidth 容器内容宽度
     */
    private void shrinkWidthsToFit(int[] widths, int[] minimumWidths, int totalFixedWidth, int contentWidth) {
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

        overflow = shrinkWidthsByBudget(widths, minimumWidths, overflow);
        if (overflow <= 0) {
            return;
        }

        int[] zeroFloors = new int[widths.length];
        shrinkWidthsByBudget(widths, zeroFloors, overflow);
    }

    private int shrinkWidthsByBudget(int[] widths, int[] floors, int overflow) {
        int shrinkable = 0;
        for (int index = 0; index < widths.length; index++) {
            shrinkable += Math.max(0, widths[index] - floors[index]);
        }
        if (shrinkable <= 0 || overflow <= 0) {
            return overflow;
        }

        int removed = 0;
        int lastShrinkIndex = widths.length - 1;
        for (int i = widths.length - 1; i >= 0; i--) {
            if (widths[i] > floors[i]) {
                lastShrinkIndex = i;
                break;
            }
        }

        for (int index = 0; index < widths.length; index++) {
            int width = widths[index];
            int floor = floors[index];
            int availableShrink = Math.max(0, width - floor);
            if (availableShrink <= 0) {
                continue;
            }
            int cut = index == lastShrinkIndex ? overflow - removed : Math.round((overflow * availableShrink) / (float) shrinkable);
            cut = Math.max(0, Math.min(cut, availableShrink));
            widths[index] -= cut;
            removed += cut;
        }
        return Math.max(0, overflow - removed);
    }

    /**
     * 横向布局在最终宽度确定后，需要按最终宽度重算依赖宽度的高度。
     *
     * @param child 子组件
     * @param layoutSpec 布局规格
     * @param margin 外边距
     * @param contentHeight 容器内容高度
     * @param resolvedWidth 最终宽度
     * @return 最终高度
     */
    private int resolveChildHeight(Widget child, UiLayoutSpec layoutSpec, UiInsets margin, int contentHeight,
            int resolvedWidth) {
        UiLength height = layoutSpec == null ? UiLength.auto() : layoutSpec.getHeight();
        int availableHeight = Math.max(0, contentHeight - margin.getTop() - margin.getBottom());
        int preferredHeight = measureForWidth(child, resolvedWidth).getHeight();
        int resolvedHeight = resolveLength(height, availableHeight, preferredHeight);
        if (layoutSpec != null && layoutSpec.isFill() && height.getType() == UiLength.Type.AUTO) {
            resolvedHeight = availableHeight;
        }

        int minHeight = layoutSpec == null ? 0 : layoutSpec.getMinHeight();
        int maxHeight = layoutSpec == null ? Integer.MAX_VALUE : layoutSpec.getMaxHeight();
        return fitDimension(resolvedHeight, minHeight, maxHeight, availableHeight);
    }
}
