package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.ui.diagnostic.UiPerformanceMonitor;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.layout.UiInsets;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 支持 overflow 滚动的视口容器。
 *
 * <p>页面壳一类的父视口框体策略属于派生层语义，基础滚动容器仅通过受保护的框体配置钩子承载。</p>
 */
public class ScrollViewportWidget extends ViewportWidget implements UiScrollHost {

    /**
     * 父视口中的框体对齐方式。
     */
    public enum FrameAlign {
        START,
        CENTER,
        END
    }

    private final DivWidget content = new DivWidget();
    private final OverflowScrollState scrollState = new OverflowScrollState();
    private boolean parentViewportFrameEnabled;
    private int minFrameWidth;
    private int minFrameHeight;
    private int maxFrameWidth = Integer.MAX_VALUE;
    private float maxParentFillWidth = 1.0F;
    private float maxParentFillHeight = 1.0F;
    private FrameAlign parentHorizontalAlign = FrameAlign.START;
    private FrameAlign parentVerticalAlign = FrameAlign.START;
    private int cachedFrameLayoutVersion = -1;
    private int cachedParentWidth = -1;
    private int cachedParentHeight = -1;
    private int cachedParentPaddingLeft = -1;
    private int cachedParentPaddingTop = -1;
    private int cachedParentPaddingRight = -1;
    private int cachedParentPaddingBottom = -1;
    private int cachedContentLayoutVersion = -1;
    private int cachedContentWidth = -1;
    private int cachedContentHeight = -1;
    private int cachedHorizontalScrollOffset = Integer.MIN_VALUE;
    private int cachedVerticalScrollOffset = Integer.MIN_VALUE;
    private int cachedContentFontRuntimeVersion = -1;

    public ScrollViewportWidget() {
        setClipChildren(true);
        setClipHitTest(true);
        content.setDirection(DivWidget.Direction.COLUMN)
                .setAlignItems(DivWidget.AlignItems.STRETCH)
                .setJustifyContent(DivWidget.JustifyContent.START)
                .setWrap(DivWidget.Wrap.NOWRAP)
                .setOverflowX(DivWidget.Overflow.VISIBLE)
                .setOverflowY(DivWidget.Overflow.VISIBLE)
                .setPadding(0);
        addChild(content);
    }

    @Override
    public ScrollViewportWidget setPadding(int padding) {
        super.setPadding(padding);
        return this;
    }

    @Override
    public ScrollViewportWidget setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
        return this;
    }

    @Override
    public ScrollViewportWidget setFillColor(int fillColor) {
        super.setFillColor(fillColor);
        return this;
    }

    @Override
    public ScrollViewportWidget setBorderColor(int borderColor) {
        super.setBorderColor(borderColor);
        return this;
    }

    public DivWidget getContent() {
        return content;
    }

    public ScrollViewportWidget setScrollStep(int scrollStep) {
        scrollState.setScrollStep(scrollStep);
        return this;
    }

    /**
     * 启用父视口约束框体，并设置宽度区间。
     *
     * @param minFrameWidth 最小宽度
     * @param maxFrameWidth 最大宽度
     * @return 当前视口
     */
    protected final ScrollViewportWidget setViewportFrameWidthRange(int minFrameWidth, int maxFrameWidth) {
        parentViewportFrameEnabled = true;
        this.minFrameWidth = Math.max(1, minFrameWidth);
        this.maxFrameWidth = Math.max(this.minFrameWidth, maxFrameWidth);
        requestLayout();
        return this;
    }

    /**
     * 设置父视口约束框体的最小高度。
     *
     * @param minFrameHeight 最小高度
     * @return 当前视口
     */
    protected final ScrollViewportWidget setViewportFrameMinHeight(int minFrameHeight) {
        parentViewportFrameEnabled = true;
        this.minFrameHeight = Math.max(1, minFrameHeight);
        requestLayout();
        return this;
    }

    /**
     * 设置父视口约束框体相对父可视区的最大填充比例。
     *
     * @param maxParentFillWidth 最大宽度占比
     * @param maxParentFillHeight 最大高度占比
     * @return 当前视口
     */
    protected final ScrollViewportWidget setViewportFrameFillRatio(float maxParentFillWidth, float maxParentFillHeight) {
        parentViewportFrameEnabled = true;
        this.maxParentFillWidth = clampRatio(maxParentFillWidth);
        this.maxParentFillHeight = clampRatio(maxParentFillHeight);
        requestLayout();
        return this;
    }

    /**
     * 设置父视口中的框体对齐方式。
     *
     * @param horizontalAlign 横向对齐
     * @param verticalAlign 纵向对齐
     * @return 当前视口
     */
    protected final ScrollViewportWidget setViewportFrameAlignment(FrameAlign horizontalAlign, FrameAlign verticalAlign) {
        parentViewportFrameEnabled = true;
        parentHorizontalAlign = horizontalAlign == null ? FrameAlign.START : horizontalAlign;
        parentVerticalAlign = verticalAlign == null ? FrameAlign.START : verticalAlign;
        requestLayout();
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
        updateViewportFrameBounds();
        updateContentBounds();
        super.render(context);
    }

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
        int currentLayoutVersion = getLayoutVersion();
        int currentFontRuntimeVersion = FontService.getInstance().getRuntimeVersion();
        int currentWidth = getWidth();
        int currentHeight = getHeight();
        int currentHorizontalOffset = scrollState.getHorizontalOffset();
        int currentVerticalOffset = scrollState.getVerticalOffset();
        if (cachedContentLayoutVersion == currentLayoutVersion
                && cachedContentWidth == currentWidth
                && cachedContentHeight == currentHeight
                && cachedHorizontalScrollOffset == currentHorizontalOffset
                && cachedVerticalScrollOffset == currentVerticalOffset
                && cachedContentFontRuntimeVersion == currentFontRuntimeVersion) {
            return;
        }

        long phaseStartNanos = System.nanoTime();

        int baseVisibleWidth = Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight());
        int baseVisibleHeight = Math.max(0, getHeight() - getPaddingTop() - getPaddingBottom());
        OverflowViewportLayout.Result layout = OverflowViewportLayout.compute(baseVisibleWidth, baseVisibleHeight, true,
                true, new OverflowViewportLayout.ContentMeasurer() {
                    @Override
                    public OverflowViewportLayout.ContentSize measure(int viewportWidth, int viewportHeight) {
                        int contentWidth = Math.max(viewportWidth, content.getMinContentWidth());
                        int contentHeight = Math.max(viewportHeight, content.getPreferredHeightForWidth(contentWidth));
                        return new OverflowViewportLayout.ContentSize(contentWidth, contentHeight);
                    }
                });

        scrollState.updateState(layout.viewportWidth, layout.viewportHeight, layout.contentWidth, layout.contentHeight,
                true, true);
        content.applyLayoutBounds(getPaddingLeft() - scrollState.getHorizontalOffset(), getPaddingTop() - scrollState.getVerticalOffset(),
                layout.contentWidth, layout.contentHeight);
        cachedContentLayoutVersion = currentLayoutVersion;
        cachedContentWidth = currentWidth;
        cachedContentHeight = currentHeight;
        cachedHorizontalScrollOffset = scrollState.getHorizontalOffset();
        cachedVerticalScrollOffset = scrollState.getVerticalOffset();
        cachedContentFontRuntimeVersion = currentFontRuntimeVersion;
        UiPerformanceMonitor.getInstance().recordPhase("viewport.contentBounds", System.nanoTime() - phaseStartNanos);
    }

    private void updateViewportFrameBounds() {
        if (!parentViewportFrameEnabled) {
            return;
        }

        Widget parent = getParent();
        if (parent == null) {
            return;
        }

        int currentLayoutVersion = getLayoutVersion();
        int parentPaddingLeft = 0;
        int parentPaddingTop = 0;
        int parentPaddingRight = 0;
        int parentPaddingBottom = 0;
        if (parent instanceof ViewportWidget) {
            ViewportWidget viewport = (ViewportWidget) parent;
            parentPaddingLeft = viewport.getPaddingLeft();
            parentPaddingTop = viewport.getPaddingTop();
            parentPaddingRight = viewport.getPaddingRight();
            parentPaddingBottom = viewport.getPaddingBottom();
        }
        if (cachedFrameLayoutVersion == currentLayoutVersion
                && cachedParentWidth == parent.getWidth()
                && cachedParentHeight == parent.getHeight()
                && cachedParentPaddingLeft == parentPaddingLeft
                && cachedParentPaddingTop == parentPaddingTop
                && cachedParentPaddingRight == parentPaddingRight
                && cachedParentPaddingBottom == parentPaddingBottom) {
            return;
        }

        long phaseStartNanos = System.nanoTime();
        try {
            int frameLeft = 0;
            int frameTop = 0;
            int frameWidth = parent.getWidth();
            int frameHeight = parent.getHeight();
            if (parent instanceof ViewportWidget) {
                ViewportWidget viewport = (ViewportWidget) parent;
                frameLeft = parentPaddingLeft;
                frameTop = parentPaddingTop;
                frameWidth = Math.max(0, parent.getWidth() - parentPaddingLeft - parentPaddingRight);
                frameHeight = Math.max(0, parent.getHeight() - parentPaddingTop - parentPaddingBottom);
            }

            UiLayoutSpec layoutSpec = getLayoutSpec();
            UiInsets margin = layoutSpec == null ? UiInsets.ZERO : layoutSpec.getMargin();
            int availableWidth = Math.max(0, frameWidth - margin.getLeft() - margin.getRight());
            int availableHeight = Math.max(0, frameHeight - margin.getTop() - margin.getBottom());
            if (availableWidth <= 0 || availableHeight <= 0) {
                applyLayoutBounds(frameLeft, frameTop, 0, 0);
                return;
            }

            int ratioWidth = Math.max(1, Math.round(availableWidth * maxParentFillWidth));
            int ratioHeight = Math.max(1, Math.round(availableHeight * maxParentFillHeight));
            int resolvedWidth = Math.min(availableWidth, Math.min(ratioWidth, maxFrameWidth));
            int resolvedHeight = Math.min(availableHeight, ratioHeight);
            resolvedWidth = Math.max(resolvedWidth, Math.min(availableWidth, minFrameWidth));
            resolvedHeight = Math.max(resolvedHeight, Math.min(availableHeight, minFrameHeight));

            int resolvedX = frameLeft + margin.getLeft()
                    + resolveAlignedOffset(availableWidth, resolvedWidth, parentHorizontalAlign);
            int resolvedY = frameTop + margin.getTop()
                    + resolveAlignedOffset(availableHeight, resolvedHeight, parentVerticalAlign);
            applyLayoutBounds(resolvedX, resolvedY, resolvedWidth, resolvedHeight);
            cachedFrameLayoutVersion = currentLayoutVersion;
            cachedParentWidth = parent.getWidth();
            cachedParentHeight = parent.getHeight();
            cachedParentPaddingLeft = parentPaddingLeft;
            cachedParentPaddingTop = parentPaddingTop;
            cachedParentPaddingRight = parentPaddingRight;
            cachedParentPaddingBottom = parentPaddingBottom;
        } finally {
            UiPerformanceMonitor.getInstance().recordPhase("viewport.frameBounds", System.nanoTime() - phaseStartNanos);
        }
    }

    private int resolveAlignedOffset(int availableSize, int contentSize, FrameAlign align) {
        int remaining = Math.max(0, availableSize - contentSize);
        if (align == FrameAlign.END) {
            return remaining;
        }
        if (align == FrameAlign.CENTER) {
            return remaining / 2;
        }
        return 0;
    }

    private float clampRatio(float ratio) {
        return Math.max(0.05F, Math.min(ratio, 1.0F));
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
