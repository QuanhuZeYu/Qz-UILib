package club.heiqi.uilib.ui.layout;

import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.props.UiOverflow;

/**
 * HTML-like 文档可滚范围计算器。
 *
 * <p>根据布局盒树测量内容边界，并推导元素当前视口对应的最大横向/纵向滚动偏移。</p>
 */
final class DocumentScrollMetricsCalculator {

    private DocumentScrollMetricsCalculator() {}

    /**
     * 根据布局盒计算可滚范围。
     *
     * @param box 布局盒
     * @return 可滚范围信息
     */
    static Metrics compute(DocumentLayoutBox box) {
        ComputedStyle style = box.getComputedStyle();
        int viewportWidth = box.getContentWidth();
        int viewportHeight = box.getContentHeight();
        ContentBounds contentBounds = measureContentBounds(box, 0, 0);
        int contentRight = Math.max(box.getContentLeft() + viewportWidth, contentBounds.right);
        int contentBottom = Math.max(box.getContentTop() + viewportHeight, contentBounds.bottom);

        int contentWidth = Math.max(viewportWidth, contentRight - box.getContentLeft());
        int contentHeight = Math.max(viewportHeight, contentBottom - box.getContentTop());
        int maxHorizontalOffset = isScrollableOverflow(style.getOverflowX())
                ? Math.max(0, contentWidth - viewportWidth)
                : 0;
        int maxVerticalOffset = isScrollableOverflow(style.getOverflowY())
                ? Math.max(0, contentHeight - viewportHeight)
                : 0;
        return new Metrics(viewportWidth, viewportHeight, contentWidth, contentHeight, maxHorizontalOffset,
                maxVerticalOffset);
    }

    private static ContentBounds measureContentBounds(DocumentLayoutBox box, int offsetX, int offsetY) {
        int baseOffsetX = box.isFixedPositioned() ? 0 : offsetX;
        int baseOffsetY = box.isFixedPositioned() ? 0 : offsetY;
        int boxOffsetX = baseOffsetX + box.getPositionOffsetX();
        int boxOffsetY = baseOffsetY + box.getPositionOffsetY();
        int right = box.getContentLeft() + boxOffsetX + box.getContentWidth();
        int bottom = box.getContentTop() + boxOffsetY + box.getContentHeight();

        for (DocumentLayoutTextRun textRun : box.getTextRuns()) {
            right = Math.max(right, textRun.getRight() + boxOffsetX);
            bottom = Math.max(bottom, textRun.getBottom() + boxOffsetY);
        }

        int childOffsetX = boxOffsetX;
        int childOffsetY = boxOffsetY;
        for (DocumentLayoutBox child : box.getChildren()) {
            if (child.isFixedPositioned()) {
                continue;
            }
            right = Math.max(right, child.getMarginBoxRight() + childOffsetX);
            bottom = Math.max(bottom, child.getMarginBoxBottom() + childOffsetY);
            ContentBounds childBounds = measureContentBounds(child, childOffsetX, childOffsetY);
            right = Math.max(right, childBounds.right);
            bottom = Math.max(bottom, childBounds.bottom);
        }
        return new ContentBounds(right, bottom);
    }

    private static boolean isScrollableOverflow(UiOverflow overflow) {
        return overflow == UiOverflow.AUTO || overflow == UiOverflow.SCROLL;
    }

    /**
     * 由布局盒推导出的可滚几何信息。
     */
    static final class Metrics {

        final int viewportWidth;
        final int viewportHeight;
        final int contentWidth;
        final int contentHeight;
        final int maxHorizontalOffset;
        final int maxVerticalOffset;

        private Metrics(int viewportWidth, int viewportHeight, int contentWidth, int contentHeight,
                int maxHorizontalOffset, int maxVerticalOffset) {
            this.viewportWidth = Math.max(0, viewportWidth);
            this.viewportHeight = Math.max(0, viewportHeight);
            this.contentWidth = Math.max(this.viewportWidth, contentWidth);
            this.contentHeight = Math.max(this.viewportHeight, contentHeight);
            this.maxHorizontalOffset = Math.max(0, maxHorizontalOffset);
            this.maxVerticalOffset = Math.max(0, maxVerticalOffset);
        }
    }

    private static final class ContentBounds {

        private final int right;
        private final int bottom;

        private ContentBounds(int right, int bottom) {
            this.right = right;
            this.bottom = bottom;
        }
    }
}
