package club.heiqi.uilib.ui.control;

/**
 * 统一的 overflow 视口预留与内容尺寸迭代计算。
 */
final class OverflowViewportLayout {

    private OverflowViewportLayout() {}

    static Result compute(int baseViewportWidth, int baseViewportHeight, boolean allowHorizontal, boolean allowVertical,
            ContentMeasurer contentMeasurer) {
        int reservedWidth = 0;
        int reservedHeight = 0;
        ContentSize contentSize = new ContentSize(Math.max(0, baseViewportWidth), Math.max(0, baseViewportHeight));

        for (int pass = 0; pass < 4; pass++) {
            int viewportWidth = Math.max(0, baseViewportWidth - reservedWidth);
            int viewportHeight = Math.max(0, baseViewportHeight - reservedHeight);
            contentSize = contentMeasurer.measure(viewportWidth, viewportHeight);

            int nextReservedWidth = allowVertical && contentSize.contentHeight > viewportHeight
                    ? OverflowScrollState.getScrollbarReserve()
                    : 0;
            int nextReservedHeight = allowHorizontal && contentSize.contentWidth > viewportWidth
                    ? OverflowScrollState.getScrollbarReserve()
                    : 0;
            if (nextReservedWidth == reservedWidth && nextReservedHeight == reservedHeight) {
                break;
            }
            reservedWidth = nextReservedWidth;
            reservedHeight = nextReservedHeight;
        }

        int viewportWidth = Math.max(0, baseViewportWidth - reservedWidth);
        int viewportHeight = Math.max(0, baseViewportHeight - reservedHeight);
        contentSize = contentMeasurer.measure(viewportWidth, viewportHeight);
        return new Result(viewportWidth, viewportHeight, contentSize.contentWidth, contentSize.contentHeight);
    }

    interface ContentMeasurer {

        ContentSize measure(int viewportWidth, int viewportHeight);
    }

    static final class ContentSize {

        final int contentWidth;
        final int contentHeight;

        ContentSize(int contentWidth, int contentHeight) {
            this.contentWidth = Math.max(0, contentWidth);
            this.contentHeight = Math.max(0, contentHeight);
        }
    }

    static final class Result {

        final int viewportWidth;
        final int viewportHeight;
        final int contentWidth;
        final int contentHeight;

        Result(int viewportWidth, int viewportHeight, int contentWidth, int contentHeight) {
            this.viewportWidth = Math.max(0, viewportWidth);
            this.viewportHeight = Math.max(0, viewportHeight);
            this.contentWidth = Math.max(0, contentWidth);
            this.contentHeight = Math.max(0, contentHeight);
        }
    }
}
