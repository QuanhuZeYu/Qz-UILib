package club.heiqi.uilib.ui.layout;

import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiScrollbarWidth;

/**
 * HTML-like 文档滚动条几何计算器。
 *
 * <p>只负责根据布局盒、滚动范围和当前偏移计算 track/thumb 几何，不持有滚动状态或交互状态。</p>
 */
final class DocumentScrollbarGeometry {

    private static final int SCROLLBAR_TRACK_GAP = 2;
    private static final int SCROLLBAR_TRACK_THICKNESS = 6;
    private static final int SCROLLBAR_THIN_TRACK_THICKNESS = 4;
    private static final int SCROLLBAR_MIN_THUMB_SIZE = 24;

    private DocumentScrollbarGeometry() {}

    /**
     * 计算纵向滚动条几何。
     *
     * @param box 布局盒
     * @param offsetX 当前盒子的视觉 X 偏移
     * @param offsetY 当前盒子的视觉 Y 偏移
     * @param reserveHorizontal 是否为横向滚动条预留右下角区域
     * @param maxVerticalOffset 最大纵向滚动偏移
     * @param verticalOffset 当前纵向滚动偏移
     * @return 滚动条几何；无需绘制时返回 null
     */
    static DocumentScrollState.ScrollbarMetrics getVerticalScrollbarMetrics(DocumentLayoutBox box, int offsetX,
            int offsetY, boolean reserveHorizontal, int maxVerticalOffset, int verticalOffset) {
        if (maxVerticalOffset <= 0 || !isScrollableOverflow(box.getComputedStyle().getOverflowY())
                || box.getContentWidth() <= 0 || box.getContentHeight() <= 0) {
            return null;
        }

        int trackThickness = resolveTrackThickness(box);
        if (trackThickness <= 0) {
            return null;
        }

        int contentTop = box.getContentTop() + offsetY;
        int contentRight = box.getContentLeft() + offsetX + box.getContentWidth();
        int contentBottom = box.getContentTop() + offsetY + box.getContentHeight()
                - (reserveHorizontal ? trackThickness + SCROLLBAR_TRACK_GAP : 0);
        int trackRight = contentRight - SCROLLBAR_TRACK_GAP;
        int trackLeft = trackRight - trackThickness;
        int trackTop = contentTop + SCROLLBAR_TRACK_GAP;
        int trackBottom = contentBottom - SCROLLBAR_TRACK_GAP;
        if (trackRight <= trackLeft || trackBottom <= trackTop) {
            return null;
        }
        return createVerticalMetrics(trackLeft, trackTop, trackRight, trackBottom, box.getContentHeight(),
                maxVerticalOffset, verticalOffset);
    }

    /**
     * 计算横向滚动条几何。
     *
     * @param box 布局盒
     * @param offsetX 当前盒子的视觉 X 偏移
     * @param offsetY 当前盒子的视觉 Y 偏移
     * @param reserveVertical 是否为纵向滚动条预留右下角区域
     * @param maxHorizontalOffset 最大横向滚动偏移
     * @param horizontalOffset 当前横向滚动偏移
     * @return 滚动条几何；无需绘制时返回 null
     */
    static DocumentScrollState.ScrollbarMetrics getHorizontalScrollbarMetrics(DocumentLayoutBox box, int offsetX,
            int offsetY, boolean reserveVertical, int maxHorizontalOffset, int horizontalOffset) {
        if (maxHorizontalOffset <= 0 || !isScrollableOverflow(box.getComputedStyle().getOverflowX())
                || box.getContentWidth() <= 0 || box.getContentHeight() <= 0) {
            return null;
        }

        int trackThickness = resolveTrackThickness(box);
        if (trackThickness <= 0) {
            return null;
        }

        int contentLeft = box.getContentLeft() + offsetX;
        int contentRight = box.getContentLeft() + offsetX + box.getContentWidth()
                - (reserveVertical ? trackThickness + SCROLLBAR_TRACK_GAP : 0);
        int contentBottom = box.getContentTop() + offsetY + box.getContentHeight();
        int trackLeft = contentLeft + SCROLLBAR_TRACK_GAP;
        int trackRight = contentRight - SCROLLBAR_TRACK_GAP;
        int trackBottom = contentBottom - SCROLLBAR_TRACK_GAP;
        int trackTop = trackBottom - trackThickness;
        if (trackRight <= trackLeft || trackBottom <= trackTop) {
            return null;
        }
        return createHorizontalMetrics(trackLeft, trackTop, trackRight, trackBottom, box.getContentWidth(),
                maxHorizontalOffset, horizontalOffset);
    }

    private static DocumentScrollState.ScrollbarMetrics createVerticalMetrics(int trackLeft, int trackTop,
            int trackRight, int trackBottom, int viewportLength, int maxScrollOffset, int scrollOffset) {
        int trackLength = trackBottom - trackTop;
        int contentLength = Math.max(1, viewportLength + maxScrollOffset);
        int thumbSize = Math.min(trackLength, Math.max(SCROLLBAR_MIN_THUMB_SIZE,
                Math.round(trackLength * (viewportLength / (float) Math.max(viewportLength, contentLength)))));
        int travel = Math.max(0, trackLength - thumbSize);
        int thumbTop = trackTop + Math.round(travel * (scrollOffset / (float) Math.max(1, maxScrollOffset)));
        return new DocumentScrollState.ScrollbarMetrics(true, trackLeft, trackTop, trackRight, trackBottom,
                trackLength, trackLeft, thumbTop, trackRight, thumbTop + thumbSize, thumbSize);
    }

    private static DocumentScrollState.ScrollbarMetrics createHorizontalMetrics(int trackLeft, int trackTop,
            int trackRight, int trackBottom, int viewportLength, int maxScrollOffset, int scrollOffset) {
        int trackLength = trackRight - trackLeft;
        int contentLength = Math.max(1, viewportLength + maxScrollOffset);
        int thumbSize = Math.min(trackLength, Math.max(SCROLLBAR_MIN_THUMB_SIZE,
                Math.round(trackLength * (viewportLength / (float) Math.max(viewportLength, contentLength)))));
        int travel = Math.max(0, trackLength - thumbSize);
        int thumbLeft = trackLeft + Math.round(travel * (scrollOffset / (float) Math.max(1, maxScrollOffset)));
        return new DocumentScrollState.ScrollbarMetrics(false, trackLeft, trackTop, trackRight, trackBottom,
                trackLength, thumbLeft, trackTop, thumbLeft + thumbSize, trackBottom, thumbSize);
    }

    private static int resolveTrackThickness(DocumentLayoutBox box) {
        UiScrollbarWidth scrollbarWidth = box.getComputedStyle().getScrollbarWidth();
        if (scrollbarWidth == UiScrollbarWidth.NONE) {
            return 0;
        }
        if (scrollbarWidth == UiScrollbarWidth.THIN) {
            return SCROLLBAR_THIN_TRACK_THICKNESS;
        }
        return SCROLLBAR_TRACK_THICKNESS;
    }

    private static boolean isScrollableOverflow(UiOverflow overflow) {
        return overflow == UiOverflow.AUTO || overflow == UiOverflow.SCROLL;
    }
}
