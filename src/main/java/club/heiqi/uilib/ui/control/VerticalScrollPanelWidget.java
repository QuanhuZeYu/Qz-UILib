package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 带双轴滚动兜底的滚动面板。
 */
public class VerticalScrollPanelWidget extends ResponsivePanelWidget {

    private static final int SCROLLBAR_TRACK_GAP = 2;
    private static final int SCROLLBAR_TRACK_THICKNESS = 6;
    private static final int SCROLLBAR_MIN_THUMB_SIZE = 24;

    private final VerticalStackWidget content = new VerticalStackWidget();

    private int verticalScrollOffset;
    private int maxVerticalScrollOffset;
    private int horizontalScrollOffset;
    private int maxHorizontalScrollOffset;
    private int scrollStep = 36;

    private boolean hoveredVerticalScrollbar;
    private boolean hoveredVerticalThumb;
    private boolean draggingVerticalThumb;
    private int dragVerticalThumbOffset;

    private boolean hoveredHorizontalScrollbar;
    private boolean hoveredHorizontalThumb;
    private boolean draggingHorizontalThumb;
    private int dragHorizontalThumbOffset;

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
        return verticalScrollOffset;
    }

    public int getMaxScrollOffset() {
        return maxVerticalScrollOffset;
    }

    public int getHorizontalScrollOffset() {
        return horizontalScrollOffset;
    }

    public int getMaxHorizontalScrollOffset() {
        return maxHorizontalScrollOffset;
    }

    public int getVisibleContentWidth() {
        return Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight());
    }

    public int getVisibleContentHeight() {
        return Math.max(0, getHeight() - getPaddingTop() - getPaddingBottom());
    }

    public int getContentWidth() {
        return content.getWidth();
    }

    public int getContentHeight() {
        return content.getHeight();
    }

    public int getContentX() {
        return content.getX();
    }

    public int getContentY() {
        return content.getY();
    }

    /**
     * 将目标子组件滚动到当前可视区域内。
     *
     * @param target 目标组件
     */
    public void scrollDescendantIntoView(Widget target) {
        if (!isDescendantOfContent(target)) {
            return;
        }

        updateContentBounds();
        int viewportLeft = getAbsoluteX() + getPaddingLeft();
        int viewportTop = getAbsoluteY() + getPaddingTop();
        int viewportRight = getAbsoluteX() + getWidth() - getPaddingRight();
        int viewportBottom = getAbsoluteY() + getHeight() - getPaddingBottom();
        int targetLeft = target.getAbsoluteX();
        int targetTop = target.getAbsoluteY();
        int targetRight = targetLeft + target.getWidth();
        int targetBottom = targetTop + target.getHeight();

        if (targetLeft < viewportLeft) {
            setHorizontalScrollOffset(horizontalScrollOffset - (viewportLeft - targetLeft));
        } else if (targetRight > viewportRight) {
            setHorizontalScrollOffset(horizontalScrollOffset + (targetRight - viewportRight));
        }

        if (targetTop < viewportTop) {
            setVerticalScrollOffset(verticalScrollOffset - (viewportTop - targetTop));
        } else if (targetBottom > viewportBottom) {
            setVerticalScrollOffset(verticalScrollOffset + (targetBottom - viewportBottom));
        }
        updateContentBounds();
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
        ScrollbarMetrics verticalMetrics = getVerticalScrollbarMetrics();
        if (verticalMetrics != null) {
            int trackColor = hoveredVerticalScrollbar ? 0x66344155 : 0x552B3647;
            int thumbColor = draggingVerticalThumb ? 0xFFD1E2FF : (hoveredVerticalThumb ? 0xFFB8D0FF : 0xFF8FB3FF);
            context.fillRect(verticalMetrics.trackLeft, verticalMetrics.trackTop, verticalMetrics.trackRight,
                    verticalMetrics.trackBottom, trackColor);
            context.fillRect(verticalMetrics.thumbLeft, verticalMetrics.thumbTop, verticalMetrics.thumbRight,
                    verticalMetrics.thumbBottom, thumbColor);
        }

        ScrollbarMetrics horizontalMetrics = getHorizontalScrollbarMetrics();
        if (horizontalMetrics != null) {
            int trackColor = hoveredHorizontalScrollbar ? 0x66344155 : 0x552B3647;
            int thumbColor = draggingHorizontalThumb ? 0xFFD1E2FF : (hoveredHorizontalThumb ? 0xFFB8D0FF : 0xFF8FB3FF);
            context.fillRect(horizontalMetrics.trackLeft, horizontalMetrics.trackTop, horizontalMetrics.trackRight,
                    horizontalMetrics.trackBottom, trackColor);
            context.fillRect(horizontalMetrics.thumbLeft, horizontalMetrics.thumbTop, horizontalMetrics.thumbRight,
                    horizontalMetrics.thumbBottom, thumbColor);
        }
    }

    @Override
    public boolean onMouseScroll(UiMouseEvent event) {
        updateContentBounds();
        int previousVerticalOffset = verticalScrollOffset;
        int previousHorizontalOffset = horizontalScrollOffset;
        int steps = Math.max(1, Math.round(Math.abs(event.getWheelDelta()) / 120.0F));
        int delta = scrollStep * steps;

        if (maxVerticalScrollOffset > 0) {
            if (event.getWheelDelta() > 0) {
                setVerticalScrollOffset(verticalScrollOffset - delta);
            } else if (event.getWheelDelta() < 0) {
                setVerticalScrollOffset(verticalScrollOffset + delta);
            }
        } else if (maxHorizontalScrollOffset > 0) {
            if (event.getWheelDelta() > 0) {
                setHorizontalScrollOffset(horizontalScrollOffset - delta);
            } else if (event.getWheelDelta() < 0) {
                setHorizontalScrollOffset(horizontalScrollOffset + delta);
            }
        }
        return verticalScrollOffset != previousVerticalOffset || horizontalScrollOffset != previousHorizontalOffset;
    }

    @Override
    public void onMouseMove(UiMouseEvent event) {
        updateContentBounds();
        ScrollbarMetrics verticalMetrics = getVerticalScrollbarMetrics();
        ScrollbarMetrics horizontalMetrics = getHorizontalScrollbarMetrics();

        if (verticalMetrics == null) {
            hoveredVerticalScrollbar = false;
            hoveredVerticalThumb = false;
            draggingVerticalThumb = false;
        } else {
            hoveredVerticalScrollbar = containsInRect(event.getMouseX(), event.getMouseY(), verticalMetrics.trackLeft,
                    verticalMetrics.trackTop, verticalMetrics.trackRight, verticalMetrics.trackBottom);
            hoveredVerticalThumb = containsInRect(event.getMouseX(), event.getMouseY(), verticalMetrics.thumbLeft,
                    verticalMetrics.thumbTop, verticalMetrics.thumbRight, verticalMetrics.thumbBottom);
            if (draggingVerticalThumb) {
                setVerticalScrollFromThumbStart(event.getMouseY() - dragVerticalThumbOffset, verticalMetrics);
                hoveredVerticalScrollbar = true;
                hoveredVerticalThumb = true;
            }
        }

        if (horizontalMetrics == null) {
            hoveredHorizontalScrollbar = false;
            hoveredHorizontalThumb = false;
            draggingHorizontalThumb = false;
        } else {
            hoveredHorizontalScrollbar = containsInRect(event.getMouseX(), event.getMouseY(), horizontalMetrics.trackLeft,
                    horizontalMetrics.trackTop, horizontalMetrics.trackRight, horizontalMetrics.trackBottom);
            hoveredHorizontalThumb = containsInRect(event.getMouseX(), event.getMouseY(), horizontalMetrics.thumbLeft,
                    horizontalMetrics.thumbTop, horizontalMetrics.thumbRight, horizontalMetrics.thumbBottom);
            if (draggingHorizontalThumb) {
                setHorizontalScrollFromThumbStart(event.getMouseX() - dragHorizontalThumbOffset, horizontalMetrics);
                hoveredHorizontalScrollbar = true;
                hoveredHorizontalThumb = true;
            }
        }
    }

    @Override
    public void onMouseDown(UiMouseEvent event) {
        if (event.getButton() != 0) {
            return;
        }

        updateContentBounds();
        ScrollbarMetrics verticalMetrics = getVerticalScrollbarMetrics();
        if (verticalMetrics != null && containsInRect(event.getMouseX(), event.getMouseY(), verticalMetrics.trackLeft,
                verticalMetrics.trackTop, verticalMetrics.trackRight, verticalMetrics.trackBottom)) {
            hoveredVerticalScrollbar = true;
            if (containsInRect(event.getMouseX(), event.getMouseY(), verticalMetrics.thumbLeft, verticalMetrics.thumbTop,
                    verticalMetrics.thumbRight, verticalMetrics.thumbBottom)) {
                draggingVerticalThumb = true;
                dragVerticalThumbOffset = event.getMouseY() - verticalMetrics.thumbTop;
                hoveredVerticalThumb = true;
                return;
            }

            draggingVerticalThumb = true;
            dragVerticalThumbOffset = verticalMetrics.thumbSize / 2;
            setVerticalScrollFromThumbStart(event.getMouseY() - dragVerticalThumbOffset, verticalMetrics);
            hoveredVerticalThumb = true;
            return;
        }

        ScrollbarMetrics horizontalMetrics = getHorizontalScrollbarMetrics();
        if (horizontalMetrics != null && containsInRect(event.getMouseX(), event.getMouseY(), horizontalMetrics.trackLeft,
                horizontalMetrics.trackTop, horizontalMetrics.trackRight, horizontalMetrics.trackBottom)) {
            hoveredHorizontalScrollbar = true;
            if (containsInRect(event.getMouseX(), event.getMouseY(), horizontalMetrics.thumbLeft,
                    horizontalMetrics.thumbTop, horizontalMetrics.thumbRight, horizontalMetrics.thumbBottom)) {
                draggingHorizontalThumb = true;
                dragHorizontalThumbOffset = event.getMouseX() - horizontalMetrics.thumbLeft;
                hoveredHorizontalThumb = true;
                return;
            }

            draggingHorizontalThumb = true;
            dragHorizontalThumbOffset = horizontalMetrics.thumbSize / 2;
            setHorizontalScrollFromThumbStart(event.getMouseX() - dragHorizontalThumbOffset, horizontalMetrics);
            hoveredHorizontalThumb = true;
        }
    }

    @Override
    public void onMouseUp(UiMouseEvent event) {
        if (event.getButton() != 0) {
            return;
        }

        draggingVerticalThumb = false;
        draggingHorizontalThumb = false;
        updateContentBounds();
        ScrollbarMetrics verticalMetrics = getVerticalScrollbarMetrics();
        ScrollbarMetrics horizontalMetrics = getHorizontalScrollbarMetrics();

        hoveredVerticalScrollbar = verticalMetrics != null && containsInRect(event.getMouseX(), event.getMouseY(),
                verticalMetrics.trackLeft, verticalMetrics.trackTop, verticalMetrics.trackRight, verticalMetrics.trackBottom);
        hoveredVerticalThumb = verticalMetrics != null && containsInRect(event.getMouseX(), event.getMouseY(),
                verticalMetrics.thumbLeft, verticalMetrics.thumbTop, verticalMetrics.thumbRight, verticalMetrics.thumbBottom);
        hoveredHorizontalScrollbar = horizontalMetrics != null && containsInRect(event.getMouseX(), event.getMouseY(),
                horizontalMetrics.trackLeft, horizontalMetrics.trackTop, horizontalMetrics.trackRight, horizontalMetrics.trackBottom);
        hoveredHorizontalThumb = horizontalMetrics != null && containsInRect(event.getMouseX(), event.getMouseY(),
                horizontalMetrics.thumbLeft, horizontalMetrics.thumbTop, horizontalMetrics.thumbRight, horizontalMetrics.thumbBottom);
    }

    @Override
    public void onMouseLeave() {
        hoveredVerticalScrollbar = false;
        hoveredVerticalThumb = false;
        hoveredHorizontalScrollbar = false;
        hoveredHorizontalThumb = false;
    }

    private void updateContentBounds() {
        int visibleWidth = getVisibleContentWidth();
        int visibleHeight = getVisibleContentHeight();
        int contentWidth = Math.max(visibleWidth, content.getPreferredWidth());
        int contentHeight = Math.max(visibleHeight, content.getPreferredHeightForWidth(contentWidth));

        maxHorizontalScrollOffset = Math.max(0, contentWidth - visibleWidth);
        maxVerticalScrollOffset = Math.max(0, contentHeight - visibleHeight);
        setHorizontalScrollOffset(horizontalScrollOffset);
        setVerticalScrollOffset(verticalScrollOffset);

        if (maxVerticalScrollOffset <= 0) {
            draggingVerticalThumb = false;
            hoveredVerticalScrollbar = false;
            hoveredVerticalThumb = false;
        }
        if (maxHorizontalScrollOffset <= 0) {
            draggingHorizontalThumb = false;
            hoveredHorizontalScrollbar = false;
            hoveredHorizontalThumb = false;
        }

        content.setBounds(getPaddingLeft() - horizontalScrollOffset, getPaddingTop() - verticalScrollOffset, contentWidth,
                contentHeight);
    }

    private void setVerticalScrollOffset(int scrollOffset) {
        verticalScrollOffset = Math.max(0, Math.min(scrollOffset, maxVerticalScrollOffset));
    }

    private void setHorizontalScrollOffset(int scrollOffset) {
        horizontalScrollOffset = Math.max(0, Math.min(scrollOffset, maxHorizontalScrollOffset));
    }

    private void setVerticalScrollFromThumbStart(int thumbTop, ScrollbarMetrics metrics) {
        int travel = Math.max(0, metrics.trackLength - metrics.thumbSize);
        if (travel <= 0 || maxVerticalScrollOffset <= 0) {
            setVerticalScrollOffset(0);
            return;
        }

        int clampedThumbTop = Math.max(metrics.trackTop, Math.min(thumbTop, metrics.trackBottom - metrics.thumbSize));
        float progress = (clampedThumbTop - metrics.trackTop) / (float) travel;
        setVerticalScrollOffset(Math.round(maxVerticalScrollOffset * progress));
    }

    private void setHorizontalScrollFromThumbStart(int thumbLeft, ScrollbarMetrics metrics) {
        int travel = Math.max(0, metrics.trackLength - metrics.thumbSize);
        if (travel <= 0 || maxHorizontalScrollOffset <= 0) {
            setHorizontalScrollOffset(0);
            return;
        }

        int clampedThumbLeft = Math.max(metrics.trackLeft, Math.min(thumbLeft, metrics.trackRight - metrics.thumbSize));
        float progress = (clampedThumbLeft - metrics.trackLeft) / (float) travel;
        setHorizontalScrollOffset(Math.round(maxHorizontalScrollOffset * progress));
    }

    private ScrollbarMetrics getVerticalScrollbarMetrics() {
        if (maxVerticalScrollOffset <= 0) {
            return null;
        }

        ScrollbarMetrics metrics = new ScrollbarMetrics();
        int horizontalReserve = maxHorizontalScrollOffset > 0 ? SCROLLBAR_TRACK_THICKNESS + SCROLLBAR_TRACK_GAP : 0;
        metrics.trackLeft = getAbsoluteX() + getWidth() - getPaddingRight() + SCROLLBAR_TRACK_GAP;
        metrics.trackRight = metrics.trackLeft + SCROLLBAR_TRACK_THICKNESS;
        metrics.trackTop = getAbsoluteY() + getPaddingTop();
        metrics.trackBottom = getAbsoluteY() + getHeight() - getPaddingBottom() - horizontalReserve;
        metrics.trackLength = Math.max(1, metrics.trackBottom - metrics.trackTop);
        metrics.thumbSize = Math.max(SCROLLBAR_MIN_THUMB_SIZE,
                Math.round(metrics.trackLength * (getVisibleContentHeight() / (float) Math.max(getVisibleContentHeight(), content.getHeight()))));
        int travel = Math.max(0, metrics.trackLength - metrics.thumbSize);
        metrics.thumbTop = metrics.trackTop + Math.round(travel * (verticalScrollOffset / (float) Math.max(1, maxVerticalScrollOffset)));
        metrics.thumbBottom = metrics.thumbTop + metrics.thumbSize;
        metrics.thumbLeft = metrics.trackLeft;
        metrics.thumbRight = metrics.trackRight;
        return metrics;
    }

    private ScrollbarMetrics getHorizontalScrollbarMetrics() {
        if (maxHorizontalScrollOffset <= 0) {
            return null;
        }

        ScrollbarMetrics metrics = new ScrollbarMetrics();
        int verticalReserve = maxVerticalScrollOffset > 0 ? SCROLLBAR_TRACK_THICKNESS + SCROLLBAR_TRACK_GAP : 0;
        metrics.trackLeft = getAbsoluteX() + getPaddingLeft();
        metrics.trackRight = getAbsoluteX() + getWidth() - getPaddingRight() - verticalReserve;
        metrics.trackTop = getAbsoluteY() + getHeight() - getPaddingBottom() + SCROLLBAR_TRACK_GAP;
        metrics.trackBottom = metrics.trackTop + SCROLLBAR_TRACK_THICKNESS;
        metrics.trackLength = Math.max(1, metrics.trackRight - metrics.trackLeft);
        metrics.thumbSize = Math.max(SCROLLBAR_MIN_THUMB_SIZE,
                Math.round(metrics.trackLength * (getVisibleContentWidth() / (float) Math.max(getVisibleContentWidth(), content.getWidth()))));
        int travel = Math.max(0, metrics.trackLength - metrics.thumbSize);
        metrics.thumbLeft = metrics.trackLeft + Math.round(travel * (horizontalScrollOffset / (float) Math.max(1, maxHorizontalScrollOffset)));
        metrics.thumbRight = metrics.thumbLeft + metrics.thumbSize;
        metrics.thumbTop = metrics.trackTop;
        metrics.thumbBottom = metrics.trackBottom;
        return metrics;
    }

    private boolean containsInRect(int mouseX, int mouseY, int left, int top, int right, int bottom) {
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    private boolean isDescendantOfContent(Widget target) {
        Widget current = target;
        while (current != null) {
            if (current == content) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * 滚动条轨道与滑块的即时几何信息。
     */
    private static class ScrollbarMetrics {
        private int trackLeft;
        private int trackTop;
        private int trackRight;
        private int trackBottom;
        private int trackLength;
        private int thumbLeft;
        private int thumbTop;
        private int thumbRight;
        private int thumbBottom;
        private int thumbSize;
    }
}
