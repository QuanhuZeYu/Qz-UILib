package club.heiqi.uilib.ui.control;

/**
 * 统一的溢出滚动状态与滚动条几何计算。
 */
final class OverflowScrollState {

    private static final int SCROLLBAR_TRACK_GAP = 2;
    private static final int SCROLLBAR_TRACK_THICKNESS = 6;
    private static final int SCROLLBAR_MIN_THUMB_SIZE = 24;

    static int getScrollbarReserve() {
        return SCROLLBAR_TRACK_THICKNESS + SCROLLBAR_TRACK_GAP;
    }

    private int verticalOffset;
    private int maxVerticalOffset;
    private int horizontalOffset;
    private int maxHorizontalOffset;
    private int scrollStep = 36;
    private int viewportWidth;
    private int viewportHeight;
    private int contentWidth;
    private int contentHeight;

    private boolean hoveredVerticalScrollbar;
    private boolean hoveredVerticalThumb;
    private boolean draggingVerticalThumb;
    private int dragVerticalThumbOffset;

    private boolean hoveredHorizontalScrollbar;
    private boolean hoveredHorizontalThumb;
    private boolean draggingHorizontalThumb;
    private int dragHorizontalThumbOffset;

    public void setScrollStep(int scrollStep) {
        this.scrollStep = Math.max(8, scrollStep);
    }

    public int getScrollStep() {
        return scrollStep;
    }

    public int getVerticalOffset() {
        return verticalOffset;
    }

    public int getMaxVerticalOffset() {
        return maxVerticalOffset;
    }

    public int getHorizontalOffset() {
        return horizontalOffset;
    }

    public int getMaxHorizontalOffset() {
        return maxHorizontalOffset;
    }

    public int getViewportWidth() {
        return viewportWidth;
    }

    public int getViewportHeight() {
        return viewportHeight;
    }

    public int getContentWidth() {
        return contentWidth;
    }

    public int getContentHeight() {
        return contentHeight;
    }

    public boolean isHoveredVerticalScrollbar() {
        return hoveredVerticalScrollbar;
    }

    public boolean isHoveredVerticalThumb() {
        return hoveredVerticalThumb;
    }

    public boolean isDraggingVerticalThumb() {
        return draggingVerticalThumb;
    }

    public boolean isHoveredHorizontalScrollbar() {
        return hoveredHorizontalScrollbar;
    }

    public boolean isHoveredHorizontalThumb() {
        return hoveredHorizontalThumb;
    }

    public boolean isDraggingHorizontalThumb() {
        return draggingHorizontalThumb;
    }

    public void updateState(int viewportWidth, int viewportHeight, int contentWidth, int contentHeight,
            boolean allowHorizontal, boolean allowVertical) {
        this.viewportWidth = Math.max(0, viewportWidth);
        this.viewportHeight = Math.max(0, viewportHeight);
        this.contentWidth = Math.max(this.viewportWidth, contentWidth);
        this.contentHeight = Math.max(this.viewportHeight, contentHeight);
        maxHorizontalOffset = allowHorizontal ? Math.max(0, this.contentWidth - this.viewportWidth) : 0;
        maxVerticalOffset = allowVertical ? Math.max(0, this.contentHeight - this.viewportHeight) : 0;
        setHorizontalOffset(horizontalOffset);
        setVerticalOffset(verticalOffset);

        if (maxVerticalOffset <= 0) {
            hoveredVerticalScrollbar = false;
            hoveredVerticalThumb = false;
            draggingVerticalThumb = false;
        }
        if (maxHorizontalOffset <= 0) {
            hoveredHorizontalScrollbar = false;
            hoveredHorizontalThumb = false;
            draggingHorizontalThumb = false;
        }
    }

    public boolean handleWheel(int wheelDelta, boolean allowVertical, boolean allowHorizontal) {
        int previousVerticalOffset = verticalOffset;
        int previousHorizontalOffset = horizontalOffset;
        int steps = Math.max(1, Math.round(Math.abs(wheelDelta) / 120.0F));
        int delta = scrollStep * steps;

        if (allowVertical && maxVerticalOffset > 0) {
            if (wheelDelta > 0) {
                setVerticalOffset(verticalOffset - delta);
            } else if (wheelDelta < 0) {
                setVerticalOffset(verticalOffset + delta);
            }
        } else if (allowHorizontal && maxHorizontalOffset > 0) {
            if (wheelDelta > 0) {
                setHorizontalOffset(horizontalOffset - delta);
            } else if (wheelDelta < 0) {
                setHorizontalOffset(horizontalOffset + delta);
            }
        }
        return verticalOffset != previousVerticalOffset || horizontalOffset != previousHorizontalOffset;
    }

    public void updatePointer(int mouseX, int mouseY, int viewportLeft, int viewportTop) {
        ScrollbarMetrics verticalMetrics = getVerticalScrollbarMetrics(viewportLeft, viewportTop);
        ScrollbarMetrics horizontalMetrics = getHorizontalScrollbarMetrics(viewportLeft, viewportTop);

        if (verticalMetrics == null) {
            hoveredVerticalScrollbar = false;
            hoveredVerticalThumb = false;
            draggingVerticalThumb = false;
        } else {
            hoveredVerticalScrollbar = containsInRect(mouseX, mouseY, verticalMetrics.trackLeft, verticalMetrics.trackTop,
                    verticalMetrics.trackRight, verticalMetrics.trackBottom);
            hoveredVerticalThumb = containsInRect(mouseX, mouseY, verticalMetrics.thumbLeft, verticalMetrics.thumbTop,
                    verticalMetrics.thumbRight, verticalMetrics.thumbBottom);
            if (draggingVerticalThumb) {
                setVerticalFromThumbStart(mouseY - dragVerticalThumbOffset, verticalMetrics);
                hoveredVerticalScrollbar = true;
                hoveredVerticalThumb = true;
            }
        }

        if (horizontalMetrics == null) {
            hoveredHorizontalScrollbar = false;
            hoveredHorizontalThumb = false;
            draggingHorizontalThumb = false;
        } else {
            hoveredHorizontalScrollbar = containsInRect(mouseX, mouseY, horizontalMetrics.trackLeft,
                    horizontalMetrics.trackTop, horizontalMetrics.trackRight, horizontalMetrics.trackBottom);
            hoveredHorizontalThumb = containsInRect(mouseX, mouseY, horizontalMetrics.thumbLeft,
                    horizontalMetrics.thumbTop, horizontalMetrics.thumbRight, horizontalMetrics.thumbBottom);
            if (draggingHorizontalThumb) {
                setHorizontalFromThumbStart(mouseX - dragHorizontalThumbOffset, horizontalMetrics);
                hoveredHorizontalScrollbar = true;
                hoveredHorizontalThumb = true;
            }
        }
    }

    public void beginPointerDrag(int mouseX, int mouseY, int viewportLeft, int viewportTop) {
        ScrollbarMetrics verticalMetrics = getVerticalScrollbarMetrics(viewportLeft, viewportTop);
        if (verticalMetrics != null && containsInRect(mouseX, mouseY, verticalMetrics.trackLeft, verticalMetrics.trackTop,
                verticalMetrics.trackRight, verticalMetrics.trackBottom)) {
            hoveredVerticalScrollbar = true;
            if (containsInRect(mouseX, mouseY, verticalMetrics.thumbLeft, verticalMetrics.thumbTop,
                    verticalMetrics.thumbRight, verticalMetrics.thumbBottom)) {
                draggingVerticalThumb = true;
                dragVerticalThumbOffset = mouseY - verticalMetrics.thumbTop;
                hoveredVerticalThumb = true;
                return;
            }

            draggingVerticalThumb = true;
            dragVerticalThumbOffset = verticalMetrics.thumbSize / 2;
            setVerticalFromThumbStart(mouseY - dragVerticalThumbOffset, verticalMetrics);
            hoveredVerticalThumb = true;
            return;
        }

        ScrollbarMetrics horizontalMetrics = getHorizontalScrollbarMetrics(viewportLeft, viewportTop);
        if (horizontalMetrics != null && containsInRect(mouseX, mouseY, horizontalMetrics.trackLeft,
                horizontalMetrics.trackTop, horizontalMetrics.trackRight, horizontalMetrics.trackBottom)) {
            hoveredHorizontalScrollbar = true;
            if (containsInRect(mouseX, mouseY, horizontalMetrics.thumbLeft, horizontalMetrics.thumbTop,
                    horizontalMetrics.thumbRight, horizontalMetrics.thumbBottom)) {
                draggingHorizontalThumb = true;
                dragHorizontalThumbOffset = mouseX - horizontalMetrics.thumbLeft;
                hoveredHorizontalThumb = true;
                return;
            }

            draggingHorizontalThumb = true;
            dragHorizontalThumbOffset = horizontalMetrics.thumbSize / 2;
            setHorizontalFromThumbStart(mouseX - dragHorizontalThumbOffset, horizontalMetrics);
            hoveredHorizontalThumb = true;
        }
    }

    public void endPointerDrag(int mouseX, int mouseY, int viewportLeft, int viewportTop) {
        draggingVerticalThumb = false;
        draggingHorizontalThumb = false;
        ScrollbarMetrics verticalMetrics = getVerticalScrollbarMetrics(viewportLeft, viewportTop);
        ScrollbarMetrics horizontalMetrics = getHorizontalScrollbarMetrics(viewportLeft, viewportTop);

        hoveredVerticalScrollbar = verticalMetrics != null && containsInRect(mouseX, mouseY, verticalMetrics.trackLeft,
                verticalMetrics.trackTop, verticalMetrics.trackRight, verticalMetrics.trackBottom);
        hoveredVerticalThumb = verticalMetrics != null && containsInRect(mouseX, mouseY, verticalMetrics.thumbLeft,
                verticalMetrics.thumbTop, verticalMetrics.thumbRight, verticalMetrics.thumbBottom);
        hoveredHorizontalScrollbar = horizontalMetrics != null && containsInRect(mouseX, mouseY,
                horizontalMetrics.trackLeft, horizontalMetrics.trackTop, horizontalMetrics.trackRight,
                horizontalMetrics.trackBottom);
        hoveredHorizontalThumb = horizontalMetrics != null && containsInRect(mouseX, mouseY,
                horizontalMetrics.thumbLeft, horizontalMetrics.thumbTop, horizontalMetrics.thumbRight,
                horizontalMetrics.thumbBottom);
    }

    public void clearHoverState() {
        hoveredVerticalScrollbar = false;
        hoveredVerticalThumb = false;
        hoveredHorizontalScrollbar = false;
        hoveredHorizontalThumb = false;
    }

    public void scrollRectIntoView(int viewportLeft, int viewportTop, int viewportRight, int viewportBottom, int targetLeft,
            int targetTop, int targetRight, int targetBottom, boolean allowHorizontal, boolean allowVertical) {
        if (allowHorizontal) {
            if (targetLeft < viewportLeft) {
                setHorizontalOffset(horizontalOffset - (viewportLeft - targetLeft));
            } else if (targetRight > viewportRight) {
                setHorizontalOffset(horizontalOffset + (targetRight - viewportRight));
            }
        }

        if (allowVertical) {
            if (targetTop < viewportTop) {
                setVerticalOffset(verticalOffset - (viewportTop - targetTop));
            } else if (targetBottom > viewportBottom) {
                setVerticalOffset(verticalOffset + (targetBottom - viewportBottom));
            }
        }
    }

    public ScrollbarMetrics getVerticalScrollbarMetrics(int viewportLeft, int viewportTop) {
        if (maxVerticalOffset <= 0) {
            return null;
        }

        int trackLeft = viewportLeft + viewportWidth + SCROLLBAR_TRACK_GAP;
        int trackRight = trackLeft + SCROLLBAR_TRACK_THICKNESS;
        int trackTop = viewportTop;
        int trackBottom = trackTop + viewportHeight;
        int trackLength = Math.max(1, trackBottom - trackTop);
        int thumbSize = Math.max(SCROLLBAR_MIN_THUMB_SIZE,
                Math.round(trackLength * (viewportHeight / (float) Math.max(viewportHeight, contentHeight))));
        int travel = Math.max(0, trackLength - thumbSize);
        int thumbTop = trackTop + Math.round(travel * (verticalOffset / (float) Math.max(1, maxVerticalOffset)));
        return new ScrollbarMetrics(trackLeft, trackTop, trackRight, trackBottom, trackLength, trackLeft, thumbTop,
                trackRight, thumbTop + thumbSize, thumbSize);
    }

    public ScrollbarMetrics getHorizontalScrollbarMetrics(int viewportLeft, int viewportTop) {
        if (maxHorizontalOffset <= 0) {
            return null;
        }

        int trackLeft = viewportLeft;
        int trackRight = trackLeft + viewportWidth;
        int trackTop = viewportTop + viewportHeight + SCROLLBAR_TRACK_GAP;
        int trackBottom = trackTop + SCROLLBAR_TRACK_THICKNESS;
        int trackLength = Math.max(1, trackRight - trackLeft);
        int thumbSize = Math.max(SCROLLBAR_MIN_THUMB_SIZE,
                Math.round(trackLength * (viewportWidth / (float) Math.max(viewportWidth, contentWidth))));
        int travel = Math.max(0, trackLength - thumbSize);
        int thumbLeft = trackLeft + Math.round(travel * (horizontalOffset / (float) Math.max(1, maxHorizontalOffset)));
        return new ScrollbarMetrics(trackLeft, trackTop, trackRight, trackBottom, trackLength, thumbLeft, trackTop,
                thumbLeft + thumbSize, trackBottom, thumbSize);
    }

    private void setVerticalFromThumbStart(int thumbTop, ScrollbarMetrics metrics) {
        int travel = Math.max(0, metrics.trackLength - metrics.thumbSize);
        if (travel <= 0 || maxVerticalOffset <= 0) {
            setVerticalOffset(0);
            return;
        }

        int clampedThumbTop = Math.max(metrics.trackTop, Math.min(thumbTop, metrics.trackBottom - metrics.thumbSize));
        float progress = (clampedThumbTop - metrics.trackTop) / (float) travel;
        setVerticalOffset(Math.round(maxVerticalOffset * progress));
    }

    private void setHorizontalFromThumbStart(int thumbLeft, ScrollbarMetrics metrics) {
        int travel = Math.max(0, metrics.trackLength - metrics.thumbSize);
        if (travel <= 0 || maxHorizontalOffset <= 0) {
            setHorizontalOffset(0);
            return;
        }

        int clampedThumbLeft = Math.max(metrics.trackLeft, Math.min(thumbLeft, metrics.trackRight - metrics.thumbSize));
        float progress = (clampedThumbLeft - metrics.trackLeft) / (float) travel;
        setHorizontalOffset(Math.round(maxHorizontalOffset * progress));
    }

    private void setVerticalOffset(int scrollOffset) {
        verticalOffset = Math.max(0, Math.min(scrollOffset, maxVerticalOffset));
    }

    private void setHorizontalOffset(int scrollOffset) {
        horizontalOffset = Math.max(0, Math.min(scrollOffset, maxHorizontalOffset));
    }

    private boolean containsInRect(int mouseX, int mouseY, int left, int top, int right, int bottom) {
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    /**
     * 滚动条轨道与滑块的即时几何信息。
     */
    static final class ScrollbarMetrics {

        final int trackLeft;
        final int trackTop;
        final int trackRight;
        final int trackBottom;
        final int trackLength;
        final int thumbLeft;
        final int thumbTop;
        final int thumbRight;
        final int thumbBottom;
        final int thumbSize;

        private ScrollbarMetrics(int trackLeft, int trackTop, int trackRight, int trackBottom, int trackLength,
                int thumbLeft, int thumbTop, int thumbRight, int thumbBottom, int thumbSize) {
            this.trackLeft = trackLeft;
            this.trackTop = trackTop;
            this.trackRight = trackRight;
            this.trackBottom = trackBottom;
            this.trackLength = trackLength;
            this.thumbLeft = thumbLeft;
            this.thumbTop = thumbTop;
            this.thumbRight = thumbRight;
            this.thumbBottom = thumbBottom;
            this.thumbSize = thumbSize;
        }
    }
}
