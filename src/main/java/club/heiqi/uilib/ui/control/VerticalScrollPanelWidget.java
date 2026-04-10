package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 纵向滚动面板。
 */
public class VerticalScrollPanelWidget extends ResponsivePanelWidget {

    private static final int SCROLLBAR_TRACK_GAP = 2;
    private static final int SCROLLBAR_TRACK_WIDTH = 6;
    private static final int SCROLLBAR_MIN_THUMB_HEIGHT = 24;

    private final VerticalStackWidget content = new VerticalStackWidget();

    private int scrollOffset;
    private int maxScrollOffset;
    private int scrollStep = 36;
    private boolean hoveredScrollbar;
    private boolean hoveredThumb;
    private boolean draggingThumb;
    private int dragThumbOffsetY;

    public VerticalScrollPanelWidget() {
        setClampChildrenInside(false);
        setClipChildren(true);
        setClipHitTest(true);
        content.setPadding(0).setSpacing(12).setClampChildrenInside(false);
        addChild(content);
    }

    @Override
    public VerticalScrollPanelWidget setPadding(int padding) {
        super.setPadding(padding);
        return this;
    }

    @Override
    public VerticalScrollPanelWidget setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
        return this;
    }

    public VerticalStackWidget getContent() {
        return content;
    }

    public VerticalScrollPanelWidget setScrollStep(int scrollStep) {
        this.scrollStep = Math.max(8, scrollStep);
        return this;
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public int getMaxScrollOffset() {
        return maxScrollOffset;
    }

    public int getVisibleContentHeight() {
        return Math.max(0, getHeight() - getPaddingTop() - getPaddingBottom());
    }

    public int getContentHeight() {
        return content.getHeight();
    }

    public int getContentY() {
        return content.getY();
    }

    @Override
    protected int[] getChildClipRect() {
        return new int[] {
                getAbsoluteX() + getPaddingLeft(),
                getAbsoluteY() + getPaddingTop(),
                getAbsoluteX() + getWidth() - getPaddingRight(),
                getAbsoluteY() + getHeight() - getPaddingBottom()
        };
    }

    @Override
    public void render(club.heiqi.uilib.ui.render.UiRenderContext context) {
        updateContentBounds();
        super.render(context);
    }

    @Override
    protected void drawSelf(club.heiqi.uilib.ui.render.UiRenderContext context) {
        super.drawSelf(context);
        ScrollbarMetrics metrics = getScrollbarMetrics();
        if (metrics == null) {
            return;
        }

        int thumbColor = draggingThumb ? 0xFFD1E2FF : (hoveredThumb ? 0xFFB8D0FF : 0xFF8FB3FF);
        int trackColor = hoveredScrollbar ? 0x66344155 : 0x552B3647;
        context.fillRect(metrics.trackLeft, metrics.trackTop, metrics.trackRight, metrics.trackBottom, trackColor);
        context.fillRect(metrics.trackLeft, metrics.thumbTop, metrics.trackRight, metrics.thumbBottom, thumbColor);
    }

    @Override
    public void onMouseScroll(UiMouseEvent event) {
        updateContentBounds();
        if (maxScrollOffset <= 0) {
            return;
        }
        int steps = Math.max(1, Math.round(Math.abs(event.getWheelDelta()) / 120.0F));
        int delta = scrollStep * steps;
        if (event.getWheelDelta() > 0) {
            setScrollOffset(scrollOffset - delta);
        } else if (event.getWheelDelta() < 0) {
            setScrollOffset(scrollOffset + delta);
        }
    }

    @Override
    public void onMouseMove(UiMouseEvent event) {
        updateContentBounds();
        ScrollbarMetrics metrics = getScrollbarMetrics();
        if (metrics == null) {
            hoveredScrollbar = false;
            hoveredThumb = false;
            draggingThumb = false;
            return;
        }

        hoveredScrollbar = containsInRect(event.getMouseX(), event.getMouseY(), metrics.trackLeft, metrics.trackTop,
                metrics.trackRight, metrics.trackBottom);
        hoveredThumb = containsInRect(event.getMouseX(), event.getMouseY(), metrics.thumbLeft, metrics.thumbTop,
                metrics.thumbRight, metrics.thumbBottom);
        if (draggingThumb) {
            setScrollFromThumbTop(event.getMouseY() - dragThumbOffsetY, metrics);
            hoveredThumb = true;
            hoveredScrollbar = true;
        }
    }

    @Override
    public void onMouseDown(UiMouseEvent event) {
        if (event.getButton() != 0) {
            return;
        }

        updateContentBounds();
        ScrollbarMetrics metrics = getScrollbarMetrics();
        if (metrics == null || !containsInRect(event.getMouseX(), event.getMouseY(), metrics.trackLeft,
                metrics.trackTop, metrics.trackRight, metrics.trackBottom)) {
            return;
        }

        hoveredScrollbar = true;
        if (containsInRect(event.getMouseX(), event.getMouseY(), metrics.thumbLeft, metrics.thumbTop,
                metrics.thumbRight, metrics.thumbBottom)) {
            draggingThumb = true;
            dragThumbOffsetY = event.getMouseY() - metrics.thumbTop;
            hoveredThumb = true;
            return;
        }

        draggingThumb = true;
        dragThumbOffsetY = metrics.thumbHeight / 2;
        setScrollFromThumbTop(event.getMouseY() - dragThumbOffsetY, metrics);
        hoveredThumb = true;
    }

    @Override
    public void onMouseUp(UiMouseEvent event) {
        if (event.getButton() != 0) {
            return;
        }

        draggingThumb = false;
        updateContentBounds();
        ScrollbarMetrics metrics = getScrollbarMetrics();
        if (metrics == null) {
            hoveredScrollbar = false;
            hoveredThumb = false;
            return;
        }
        hoveredScrollbar = containsInRect(event.getMouseX(), event.getMouseY(), metrics.trackLeft, metrics.trackTop,
                metrics.trackRight, metrics.trackBottom);
        hoveredThumb = containsInRect(event.getMouseX(), event.getMouseY(), metrics.thumbLeft, metrics.thumbTop,
                metrics.thumbRight, metrics.thumbBottom);
    }

    @Override
    public void onMouseLeave() {
        hoveredScrollbar = false;
        hoveredThumb = false;
    }

    private void updateContentBounds() {
        int contentWidth = Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight());
        int visibleHeight = Math.max(0, getHeight() - getPaddingTop() - getPaddingBottom());
        int contentHeight = Math.max(visibleHeight, content.getPreferredHeightForWidth(contentWidth));
        maxScrollOffset = Math.max(0, contentHeight - visibleHeight);
        setScrollOffset(scrollOffset);
        if (maxScrollOffset <= 0) {
            draggingThumb = false;
            hoveredScrollbar = false;
            hoveredThumb = false;
        }
        content.setBounds(getPaddingLeft(), getPaddingTop() - scrollOffset, contentWidth, contentHeight);
    }

    /**
     * 统一限制滚动偏移，避免滚轮与拖拽逻辑各自重复 clamp。
     *
     * @param scrollOffset 目标滚动偏移
     */
    private void setScrollOffset(int scrollOffset) {
        this.scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
    }

    /**
     * 按滚动条滑块顶部位置反推出内容滚动偏移。
     *
     * @param thumbTop 目标滑块顶部
     * @param metrics  当前滚动条几何信息
     */
    private void setScrollFromThumbTop(int thumbTop, ScrollbarMetrics metrics) {
        int travel = Math.max(0, metrics.trackHeight - metrics.thumbHeight);
        if (travel <= 0 || maxScrollOffset <= 0) {
            setScrollOffset(0);
            return;
        }

        int clampedThumbTop = Math.max(metrics.trackTop, Math.min(thumbTop, metrics.trackBottom - metrics.thumbHeight));
        float progress = (clampedThumbTop - metrics.trackTop) / (float) travel;
        setScrollOffset(Math.round(maxScrollOffset * progress));
    }

    private ScrollbarMetrics getScrollbarMetrics() {
        if (maxScrollOffset <= 0) {
            return null;
        }

        ScrollbarMetrics metrics = new ScrollbarMetrics();
        metrics.trackLeft = getAbsoluteX() + getWidth() - getPaddingRight() + SCROLLBAR_TRACK_GAP;
        metrics.trackRight = metrics.trackLeft + SCROLLBAR_TRACK_WIDTH;
        metrics.trackTop = getAbsoluteY() + getPaddingTop();
        metrics.trackBottom = getAbsoluteY() + getHeight() - getPaddingBottom();
        metrics.trackHeight = Math.max(1, metrics.trackBottom - metrics.trackTop);
        metrics.thumbHeight = Math.max(SCROLLBAR_MIN_THUMB_HEIGHT,
                Math.round(metrics.trackHeight * (metrics.trackHeight / (float) Math.max(metrics.trackHeight, content.getHeight()))));
        int travel = Math.max(0, metrics.trackHeight - metrics.thumbHeight);
        metrics.thumbTop = metrics.trackTop + Math.round(travel * (scrollOffset / (float) Math.max(1, maxScrollOffset)));
        metrics.thumbBottom = metrics.thumbTop + metrics.thumbHeight;
        metrics.thumbLeft = metrics.trackLeft;
        metrics.thumbRight = metrics.trackRight;
        return metrics;
    }

    private boolean containsInRect(int mouseX, int mouseY, int left, int top, int right, int bottom) {
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    /**
     * 滚动条轨道与滑块的即时几何信息。
     */
    private static class ScrollbarMetrics {
        private int trackLeft;
        private int trackTop;
        private int trackRight;
        private int trackBottom;
        private int trackHeight;
        private int thumbLeft;
        private int thumbTop;
        private int thumbRight;
        private int thumbBottom;
        private int thumbHeight;
    }
}
