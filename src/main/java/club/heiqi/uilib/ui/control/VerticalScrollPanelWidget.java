package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 带双轴滚动兜底的滚动面板。
 */
public class VerticalScrollPanelWidget extends ResponsivePanelWidget {

    private final VerticalStackWidget content = new VerticalStackWidget();
    private final OverflowScrollState scrollState = new OverflowScrollState();

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
        scrollState.setScrollStep(scrollStep);
        return this;
    }

    public int getScrollOffset() {
        return scrollState.getVerticalOffset();
    }

    public int getMaxScrollOffset() {
        return scrollState.getMaxVerticalOffset();
    }

    public int getHorizontalScrollOffset() {
        return scrollState.getHorizontalOffset();
    }

    public int getMaxHorizontalScrollOffset() {
        return scrollState.getMaxHorizontalOffset();
    }

    public int getVisibleContentWidth() {
        if (scrollState.getViewportWidth() > 0 || getWidth() <= 0) {
            return scrollState.getViewportWidth();
        }
        return Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight());
    }

    public int getVisibleContentHeight() {
        if (scrollState.getViewportHeight() > 0 || getHeight() <= 0) {
            return scrollState.getViewportHeight();
        }
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
        int viewportRight = viewportLeft + getVisibleContentWidth();
        int viewportBottom = viewportTop + getVisibleContentHeight();
        int targetLeft = target.getAbsoluteX();
        int targetTop = target.getAbsoluteY();
        int targetRight = targetLeft + target.getWidth();
        int targetBottom = targetTop + target.getHeight();

        scrollState.scrollRectIntoView(viewportLeft, viewportTop, viewportRight, viewportBottom, targetLeft, targetTop,
                targetRight, targetBottom, true, true);
        updateContentBounds();
    }

    @Override
    protected int[] getChildClipRect() {
        int viewportLeft = getAbsoluteX() + getPaddingLeft();
        int viewportTop = getAbsoluteY() + getPaddingTop();
        return new int[] {
                viewportLeft,
                viewportTop,
                viewportLeft + getVisibleContentWidth(),
                viewportTop + getVisibleContentHeight()
        };
    }

    @Override
    public void render(club.heiqi.uilib.ui.render.UiRenderContext context) {
        updateContentBounds();
        super.render(context);
    }

    @Override
    protected void layoutChildren() {
        // 滚动面板的内部内容区域由 updateContentBounds() 专门负责定位与定尺寸，
        // 不能再让父类按普通响应式子项重新布局，否则会覆盖滚动偏移和真实内容高度。
    }

    @Override
    protected void drawSelf(club.heiqi.uilib.ui.render.UiRenderContext context) {
        super.drawSelf(context);
        OverflowScrollState.ScrollbarMetrics verticalMetrics = getVerticalScrollbarMetrics();
        if (verticalMetrics != null) {
            int trackColor = scrollState.isHoveredVerticalScrollbar() ? 0x66344155 : 0x552B3647;
            int thumbColor = scrollState.isDraggingVerticalThumb() ? 0xFFD1E2FF
                    : (scrollState.isHoveredVerticalThumb() ? 0xFFB8D0FF : 0xFF8FB3FF);
            context.fillRect(verticalMetrics.trackLeft, verticalMetrics.trackTop, verticalMetrics.trackRight,
                    verticalMetrics.trackBottom, trackColor);
            context.fillRect(verticalMetrics.thumbLeft, verticalMetrics.thumbTop, verticalMetrics.thumbRight,
                    verticalMetrics.thumbBottom, thumbColor);
        }

        OverflowScrollState.ScrollbarMetrics horizontalMetrics = getHorizontalScrollbarMetrics();
        if (horizontalMetrics != null) {
            int trackColor = scrollState.isHoveredHorizontalScrollbar() ? 0x66344155 : 0x552B3647;
            int thumbColor = scrollState.isDraggingHorizontalThumb() ? 0xFFD1E2FF
                    : (scrollState.isHoveredHorizontalThumb() ? 0xFFB8D0FF : 0xFF8FB3FF);
            context.fillRect(horizontalMetrics.trackLeft, horizontalMetrics.trackTop, horizontalMetrics.trackRight,
                    horizontalMetrics.trackBottom, trackColor);
            context.fillRect(horizontalMetrics.thumbLeft, horizontalMetrics.thumbTop, horizontalMetrics.thumbRight,
                    horizontalMetrics.thumbBottom, thumbColor);
        }
    }

    @Override
    public boolean onMouseScroll(UiMouseEvent event) {
        updateContentBounds();
        return scrollState.handleWheel(event.getWheelDelta(), true, true);
    }

    @Override
    public void onMouseMove(UiMouseEvent event) {
        updateContentBounds();
        scrollState.updatePointer(event.getMouseX(), event.getMouseY(), getAbsoluteX() + getPaddingLeft(),
                getAbsoluteY() + getPaddingTop());
    }

    @Override
    public void onMouseDown(UiMouseEvent event) {
        if (event.getButton() != 0) {
            return;
        }

        updateContentBounds();
        scrollState.beginPointerDrag(event.getMouseX(), event.getMouseY(), getAbsoluteX() + getPaddingLeft(),
                getAbsoluteY() + getPaddingTop());
    }

    @Override
    public void onMouseUp(UiMouseEvent event) {
        if (event.getButton() != 0) {
            return;
        }

        updateContentBounds();
        scrollState.endPointerDrag(event.getMouseX(), event.getMouseY(), getAbsoluteX() + getPaddingLeft(),
                getAbsoluteY() + getPaddingTop());
    }

    @Override
    public void onMouseLeave() {
        scrollState.clearHoverState();
    }

    private void updateContentBounds() {
        int baseVisibleWidth = Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight());
        int baseVisibleHeight = Math.max(0, getHeight() - getPaddingTop() - getPaddingBottom());
        int reservedWidth = 0;
        int reservedHeight = 0;
        int contentWidth = baseVisibleWidth;
        int contentHeight = baseVisibleHeight;

        for (int pass = 0; pass < 4; pass++) {
            int currentViewportWidth = Math.max(0, baseVisibleWidth - reservedWidth);
            int currentViewportHeight = Math.max(0, baseVisibleHeight - reservedHeight);
            contentWidth = Math.max(currentViewportWidth, content.getMinContentWidth());
            contentHeight = Math.max(currentViewportHeight, content.getPreferredHeightForWidth(contentWidth));

            int nextReservedWidth = contentHeight > currentViewportHeight ? OverflowScrollState.getScrollbarReserve() : 0;
            int nextReservedHeight = contentWidth > currentViewportWidth ? OverflowScrollState.getScrollbarReserve() : 0;
            if (nextReservedWidth == reservedWidth && nextReservedHeight == reservedHeight) {
                break;
            }
            reservedWidth = nextReservedWidth;
            reservedHeight = nextReservedHeight;
        }

        int viewportWidth = Math.max(0, baseVisibleWidth - reservedWidth);
        int viewportHeight = Math.max(0, baseVisibleHeight - reservedHeight);
        contentWidth = Math.max(viewportWidth, content.getMinContentWidth());
        contentHeight = Math.max(viewportHeight, content.getPreferredHeightForWidth(contentWidth));

        scrollState.updateState(viewportWidth, viewportHeight, contentWidth, contentHeight, true, true);
        content.setBounds(getPaddingLeft() - scrollState.getHorizontalOffset(), getPaddingTop() - scrollState.getVerticalOffset(),
                contentWidth, contentHeight);
    }

    private OverflowScrollState.ScrollbarMetrics getVerticalScrollbarMetrics() {
        return scrollState.getVerticalScrollbarMetrics(getAbsoluteX() + getPaddingLeft(), getAbsoluteY() + getPaddingTop());
    }

    private OverflowScrollState.ScrollbarMetrics getHorizontalScrollbarMetrics() {
        return scrollState.getHorizontalScrollbarMetrics(getAbsoluteX() + getPaddingLeft(), getAbsoluteY() + getPaddingTop());
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

}
