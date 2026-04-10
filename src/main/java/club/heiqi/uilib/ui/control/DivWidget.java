package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.layout.DivItemStyle;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 类似浏览器 div 的自动排布容器。
 */
public class DivWidget extends Widget {

    /**
     * 主轴方向。
     */
    public enum Direction {
        ROW,
        COLUMN
    }

    /**
     * 交叉轴对齐方式。
     */
    public enum AlignItems {
        START,
        CENTER,
        END,
        STRETCH
    }

    /**
     * 主轴对齐方式。
     */
    public enum JustifyContent {
        START,
        CENTER,
        END,
        SPACE_BETWEEN
    }

    private Direction direction = Direction.COLUMN;
    private AlignItems alignItems = AlignItems.STRETCH;
    private JustifyContent justifyContent = JustifyContent.START;
    private int paddingLeft;
    private int paddingTop;
    private int paddingRight;
    private int paddingBottom;
    private int gap = 12;
    private float widthPercent = -1.0F;
    private float heightPercent = -1.0F;

    @Override
    public void render(UiRenderContext context) {
        layoutChildren();
        super.render(context);
    }

    @Override
    protected void drawSelf(UiRenderContext context) {}

    public DivWidget setDirection(Direction direction) {
        this.direction = direction == null ? Direction.COLUMN : direction;
        return this;
    }

    public DivWidget setAlignItems(AlignItems alignItems) {
        this.alignItems = alignItems == null ? AlignItems.STRETCH : alignItems;
        return this;
    }

    public DivWidget setJustifyContent(JustifyContent justifyContent) {
        this.justifyContent = justifyContent == null ? JustifyContent.START : justifyContent;
        return this;
    }

    public DivWidget setPadding(int padding) {
        return setPadding(padding, padding, padding, padding);
    }

    public DivWidget setPadding(int left, int top, int right, int bottom) {
        this.paddingLeft = Math.max(0, left);
        this.paddingTop = Math.max(0, top);
        this.paddingRight = Math.max(0, right);
        this.paddingBottom = Math.max(0, bottom);
        return this;
    }

    public DivWidget setGap(int gap) {
        this.gap = Math.max(0, gap);
        return this;
    }

    public DivWidget setWidthPercent(float widthPercent) {
        this.widthPercent = clampPercent(widthPercent);
        return this;
    }

    public DivWidget setHeightPercent(float heightPercent) {
        this.heightPercent = clampPercent(heightPercent);
        return this;
    }

    public float getWidthPercent() {
        return widthPercent;
    }

    public float getHeightPercent() {
        return heightPercent;
    }

    public DivWidget addChild(Widget child, DivItemStyle style) {
        if (child != null) {
            child.setDivItemStyle(style);
        }
        super.addChild(child);
        return this;
    }

    @Override
    public int getPreferredWidth() {
        if (getChildren().isEmpty()) {
            return paddingLeft + paddingRight;
        }

        int contentWidth = 0;
        if (direction == Direction.ROW) {
            for (Widget child : getChildren()) {
                contentWidth += child.getSuggestedWidth();
            }
            contentWidth += gap * Math.max(0, getChildren().size() - 1);
        } else {
            for (Widget child : getChildren()) {
                contentWidth = Math.max(contentWidth, child.getSuggestedWidth());
            }
        }
        return paddingLeft + contentWidth + paddingRight;
    }

    @Override
    public int getPreferredHeight() {
        return getPreferredHeightForWidth(Math.max(getPreferredWidth(), getWidth()));
    }

    @Override
    public int getPreferredHeightForWidth(int width) {
        LayoutResult layout = measureLayout(Math.max(0, width), 0, false, false);
        return layout.requiredHeight;
    }

    @Override
    public int getMinContentWidth() {
        if (getChildren().isEmpty()) {
            return paddingLeft + paddingRight;
        }

        int contentWidth = 0;
        if (direction == Direction.ROW) {
            for (Widget child : getChildren()) {
                contentWidth += child.getMinContentWidth();
            }
            contentWidth += gap * Math.max(0, getChildren().size() - 1);
        } else {
            for (Widget child : getChildren()) {
                contentWidth = Math.max(contentWidth, child.getMinContentWidth());
            }
        }
        return paddingLeft + contentWidth + paddingRight;
    }

    @Override
    public int getMinContentHeightForWidth(int width) {
        LayoutResult layout = measureLayout(Math.max(0, width), 0, false, true);
        return layout.requiredHeight;
    }

    private void layoutChildren() {
        measureLayout(getWidth(), getHeight(), true, false);
    }

    private LayoutResult measureLayout(int containerWidth, int containerHeight, boolean applyBounds, boolean useMinHeights) {
        return direction == Direction.ROW
                ? measureRowLayout(containerWidth, containerHeight, applyBounds, useMinHeights)
                : measureColumnLayout(containerWidth, containerHeight, applyBounds, useMinHeights);
    }

    private LayoutResult measureRowLayout(int containerWidth, int containerHeight, boolean applyBounds, boolean useMinHeights) {
        LayoutResult result = new LayoutResult();
        int childCount = getChildren().size();
        if (childCount == 0) {
            result.requiredWidth = paddingLeft + paddingRight;
            result.requiredHeight = paddingTop + paddingBottom;
            return result;
        }

        int innerWidth = Math.max(0, containerWidth - paddingLeft - paddingRight);
        int innerHeight = Math.max(0, containerHeight - paddingTop - paddingBottom);
        int[] widths = new int[childCount];
        int[] minWidths = new int[childCount];
        int[] heights = new int[childCount];
        int totalWidth = 0;

        for (int index = 0; index < childCount; index++) {
            Widget child = getChildren().get(index);
            minWidths[index] = child.getMinContentWidth();
            widths[index] = Math.max(minWidths[index], resolveRowBaseWidth(child, innerWidth));
            totalWidth += widths[index];
            heights[index] = useMinHeights ? child.getMinContentHeightForWidth(widths[index]) : child.getSuggestedHeightForWidth(widths[index]);
        }
        totalWidth += gap * Math.max(0, childCount - 1);

        if (innerWidth > 0) {
            if (totalWidth < innerWidth) {
                totalWidth += distributeGrowth(widths, innerWidth - totalWidth);
            } else if (totalWidth > innerWidth) {
                totalWidth -= distributeShrink(widths, minWidths, totalWidth - innerWidth);
            }
        }

        int tallest = 0;
        for (int index = 0; index < childCount; index++) {
            Widget child = getChildren().get(index);
            heights[index] = resolveRowCrossSize(child, widths[index], innerHeight, useMinHeights, applyBounds);
            tallest = Math.max(tallest, heights[index]);
        }

        int extraSpace = Math.max(0, innerWidth - totalWidth);
        int cursor = paddingLeft + resolveLeadingOffset(extraSpace, childCount);
        int dynamicGap = resolveGap(extraSpace, childCount);
        for (int index = 0; index < childCount; index++) {
            Widget child = getChildren().get(index);
            int childY = paddingTop + resolveCrossOffset(innerHeight, heights[index]);
            if (applyBounds) {
                child.setBounds(cursor, childY, widths[index], heights[index]);
            }
            cursor += widths[index];
            if (index < childCount - 1) {
                cursor += dynamicGap;
            }
        }

        result.requiredWidth = paddingLeft + totalWidth + paddingRight;
        result.requiredHeight = paddingTop + tallest + paddingBottom;
        return result;
    }

    private LayoutResult measureColumnLayout(int containerWidth, int containerHeight, boolean applyBounds, boolean useMinHeights) {
        LayoutResult result = new LayoutResult();
        int childCount = getChildren().size();
        if (childCount == 0) {
            result.requiredWidth = paddingLeft + paddingRight;
            result.requiredHeight = paddingTop + paddingBottom;
            return result;
        }

        int innerWidth = Math.max(0, containerWidth - paddingLeft - paddingRight);
        int innerHeight = Math.max(0, containerHeight - paddingTop - paddingBottom);
        int[] widths = new int[childCount];
        int[] heights = new int[childCount];
        int[] minHeights = new int[childCount];
        int widest = 0;
        int totalHeight = 0;

        for (int index = 0; index < childCount; index++) {
            Widget child = getChildren().get(index);
            widths[index] = resolveColumnCrossSize(child, innerWidth);
            heights[index] = resolveColumnBaseHeight(child, widths[index], innerHeight, false);
            minHeights[index] = resolveColumnBaseHeight(child, widths[index], innerHeight, true);
            widest = Math.max(widest, widths[index]);
            totalHeight += heights[index];
        }
        totalHeight += gap * Math.max(0, childCount - 1);

        if (innerHeight > 0) {
            if (totalHeight < innerHeight) {
                totalHeight += distributeGrowth(heights, innerHeight - totalHeight);
            } else if (totalHeight > innerHeight) {
                totalHeight -= distributeShrink(heights, minHeights, totalHeight - innerHeight);
            }
        }

        int extraSpace = Math.max(0, innerHeight - totalHeight);
        int cursor = paddingTop + resolveLeadingOffset(extraSpace, childCount);
        int dynamicGap = resolveGap(extraSpace, childCount);
        for (int index = 0; index < childCount; index++) {
            Widget child = getChildren().get(index);
            int childX = paddingLeft + resolveCrossOffset(innerWidth, widths[index]);
            if (applyBounds) {
                child.setBounds(childX, cursor, widths[index], heights[index]);
            }
            cursor += heights[index];
            if (index < childCount - 1) {
                cursor += dynamicGap;
            }
        }

        result.requiredWidth = paddingLeft + widest + paddingRight;
        result.requiredHeight = paddingTop + totalHeight + paddingBottom;
        return result;
    }

    private int resolveRowBaseWidth(Widget child, int innerWidth) {
        int preferredWidth = child.getSuggestedWidth();
        if (child instanceof DivWidget) {
            DivWidget divChild = (DivWidget) child;
            if (divChild.widthPercent >= 0.0F && innerWidth > 0) {
                preferredWidth = Math.round(innerWidth * divChild.widthPercent);
            }
        }
        return preferredWidth;
    }

    private int resolveRowCrossSize(Widget child, int childWidth, int innerHeight, boolean useMinHeight, boolean actualLayout) {
        int resolvedHeight = useMinHeight ? child.getMinContentHeightForWidth(childWidth) : child.getSuggestedHeightForWidth(childWidth);
        if (actualLayout && child instanceof DivWidget) {
            DivWidget divChild = (DivWidget) child;
            if (divChild.heightPercent >= 0.0F && innerHeight > 0) {
                resolvedHeight = Math.round(innerHeight * divChild.heightPercent);
            }
        }
        if (actualLayout && alignItems == AlignItems.STRETCH && innerHeight > 0) {
            resolvedHeight = Math.max(resolvedHeight, innerHeight);
        }
        return resolvedHeight;
    }

    private int resolveColumnCrossSize(Widget child, int innerWidth) {
        int preferredWidth = child.getSuggestedWidth();
        if (child instanceof DivWidget) {
            DivWidget divChild = (DivWidget) child;
            if (divChild.widthPercent >= 0.0F && innerWidth > 0) {
                preferredWidth = Math.round(innerWidth * divChild.widthPercent);
            }
        } else if (alignItems == AlignItems.STRETCH && innerWidth > 0) {
            preferredWidth = innerWidth;
        }
        if (alignItems == AlignItems.STRETCH && innerWidth > 0 && !(child instanceof DivWidget && ((DivWidget) child).widthPercent >= 0.0F)) {
            preferredWidth = innerWidth;
        }
        return Math.max(child.getMinContentWidth(), preferredWidth);
    }

    private int resolveColumnBaseHeight(Widget child, int childWidth, int innerHeight, boolean useMinHeight) {
        if (child instanceof DivWidget) {
            DivWidget divChild = (DivWidget) child;
            if (divChild.heightPercent >= 0.0F && innerHeight > 0) {
                return Math.max(1, Math.round(innerHeight * divChild.heightPercent));
            }
        }
        return useMinHeight ? child.getMinContentHeightForWidth(childWidth) : child.getSuggestedHeightForWidth(childWidth);
    }

    private int distributeGrowth(int[] sizes, int extra) {
        if (extra <= 0 || sizes.length == 0) {
            return 0;
        }

        int growableCount = 0;
        for (Widget child : getChildren()) {
            DivItemStyle style = child.getDivItemStyle();
            if (style == null || style.isGrow()) {
                growableCount++;
            }
        }
        if (growableCount <= 0) {
            return 0;
        }

        int applied = 0;
        int growIndex = 0;
        for (int index = 0; index < sizes.length; index++) {
            Widget child = getChildren().get(index);
            DivItemStyle style = child.getDivItemStyle();
            if (style != null && !style.isGrow()) {
                continue;
            }
            growIndex++;
            int addition = growIndex == growableCount ? extra - applied : extra / growableCount;
            addition = Math.max(0, addition);
            sizes[index] += addition;
            applied += addition;
        }
        return applied;
    }

    private int distributeShrink(int[] sizes, int[] minSizes, int overflow) {
        if (overflow <= 0 || sizes.length == 0) {
            return 0;
        }

        int shrinkable = 0;
        for (int index = 0; index < sizes.length; index++) {
            Widget child = getChildren().get(index);
            DivItemStyle style = child.getDivItemStyle();
            if (style != null && !style.isShrink()) {
                continue;
            }
            shrinkable += Math.max(0, sizes[index] - minSizes[index]);
        }
        if (shrinkable <= 0) {
            return 0;
        }

        int removed = 0;
        int remainingShrinkable = 0;
        for (int index = 0; index < sizes.length; index++) {
            Widget child = getChildren().get(index);
            DivItemStyle style = child.getDivItemStyle();
            if (style != null && !style.isShrink()) {
                continue;
            }
            if (sizes[index] > minSizes[index]) {
                remainingShrinkable++;
            }
        }

        int shrinkIndex = 0;
        for (int index = 0; index < sizes.length; index++) {
            Widget child = getChildren().get(index);
            DivItemStyle style = child.getDivItemStyle();
            if (style != null && !style.isShrink()) {
                continue;
            }
            int availableShrink = Math.max(0, sizes[index] - minSizes[index]);
            if (availableShrink <= 0) {
                continue;
            }
            shrinkIndex++;
            int cut = shrinkIndex == remainingShrinkable
                    ? overflow - removed
                    : Math.round((overflow * availableShrink) / (float) shrinkable);
            cut = Math.max(0, Math.min(cut, availableShrink));
            sizes[index] -= cut;
            removed += cut;
        }
        return removed;
    }

    private int resolveLeadingOffset(int extraSpace, int childCount) {
        if (extraSpace <= 0) {
            return 0;
        }
        switch (justifyContent) {
            case CENTER:
                return extraSpace / 2;
            case END:
                return extraSpace;
            case SPACE_BETWEEN:
                return 0;
            default:
                return 0;
        }
    }

    private int resolveGap(int extraSpace, int childCount) {
        if (justifyContent != JustifyContent.SPACE_BETWEEN || childCount <= 1 || extraSpace <= 0) {
            return gap;
        }
        return gap + extraSpace / (childCount - 1);
    }

    private int resolveCrossOffset(int innerCrossSize, int childCrossSize) {
        if (innerCrossSize <= 0) {
            return 0;
        }
        switch (alignItems) {
            case CENTER:
                return (innerCrossSize - childCrossSize) / 2;
            case END:
                return innerCrossSize - childCrossSize;
            default:
                return 0;
        }
    }

    private float clampPercent(float percent) {
        return percent < 0.0F ? -1.0F : Math.max(0.0F, Math.min(percent, 1.0F));
    }

    private static class LayoutResult {
        private int requiredWidth;
        private int requiredHeight;
    }
}
