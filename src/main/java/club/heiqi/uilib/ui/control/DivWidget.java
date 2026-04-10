package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.layout.DivItemStyle;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 类似浏览器 div 的自动排布容器。
 */
public class DivWidget extends Widget {

    private static final int SCROLLBAR_TRACK_GAP = 2;
    private static final int SCROLLBAR_TRACK_THICKNESS = 6;
    private static final int SCROLLBAR_MIN_THUMB_SIZE = 24;

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

    /**
     * 是否允许主轴换行。
     */
    public enum Wrap {
        NOWRAP,
        WRAP
    }

    /**
     * 溢出处理方式。
     */
    public enum Overflow {
        VISIBLE,
        AUTO,
        HIDDEN
    }

    private Direction direction = Direction.COLUMN;
    private AlignItems alignItems = AlignItems.STRETCH;
    private JustifyContent justifyContent = JustifyContent.START;
    private Wrap wrap = Wrap.NOWRAP;
    private Overflow overflowX = Overflow.VISIBLE;
    private Overflow overflowY = Overflow.VISIBLE;
    private int paddingLeft;
    private int paddingTop;
    private int paddingRight;
    private int paddingBottom;
    private int gap = 12;
    private float widthPercent = -1.0F;
    private float heightPercent = -1.0F;
    private int scrollStep = 36;

    private int horizontalScrollOffset;
    private int verticalScrollOffset;
    private int maxHorizontalScrollOffset;
    private int maxVerticalScrollOffset;

    private boolean hoveredVerticalScrollbar;
    private boolean hoveredVerticalThumb;
    private boolean draggingVerticalThumb;
    private int dragVerticalThumbOffset;

    private boolean hoveredHorizontalScrollbar;
    private boolean hoveredHorizontalThumb;
    private boolean draggingHorizontalThumb;
    private int dragHorizontalThumbOffset;

    @Override
    public void render(UiRenderContext context) {
        if (!isVisible()) {
            return;
        }

        LayoutResult layout = measureLayout(getWidth(), getHeight(), false, false);
        updateScrollState(layout);
        setClipHitTest(hasClippedOverflow());
        measureLayout(getWidth(), getHeight(), true, false);

        drawSelf(context);
        boolean clipping = hasClippedOverflow();
        if (clipping) {
            int[] clipRect = resolveClipRect(context);
            context.pushClip(clipRect[0], clipRect[1], clipRect[2], clipRect[3]);
        }
        try {
            for (Widget child : getChildren()) {
                child.render(context);
            }
        } finally {
            if (clipping) {
                context.popClip();
            }
        }
        drawScrollbars(context);
    }

    @Override
    protected void drawSelf(UiRenderContext context) {}

    @Override
    protected int[] getChildClipRect() {
        int viewportLeft = getAbsoluteX() + paddingLeft;
        int viewportTop = getAbsoluteY() + paddingTop;
        int viewportRight = getAbsoluteX() + getWidth() - paddingRight;
        int viewportBottom = getAbsoluteY() + getHeight() - paddingBottom;
        return new int[] {
                overflowX == Overflow.VISIBLE ? Integer.MIN_VALUE / 4 : viewportLeft,
                overflowY == Overflow.VISIBLE ? Integer.MIN_VALUE / 4 : viewportTop,
                overflowX == Overflow.VISIBLE ? Integer.MAX_VALUE / 4 : viewportRight,
                overflowY == Overflow.VISIBLE ? Integer.MAX_VALUE / 4 : viewportBottom
        };
    }

    private int[] resolveClipRect(UiRenderContext context) {
        int viewportLeft = getAbsoluteX() + paddingLeft;
        int viewportTop = getAbsoluteY() + paddingTop;
        int viewportRight = getAbsoluteX() + getWidth() - paddingRight;
        int viewportBottom = getAbsoluteY() + getHeight() - paddingBottom;
        int clipLeft = overflowX == Overflow.VISIBLE ? 0 : viewportLeft;
        int clipRight = overflowX == Overflow.VISIBLE ? context.getScreenWidth() : viewportRight;
        int clipTop = overflowY == Overflow.VISIBLE ? 0 : viewportTop;
        int clipBottom = overflowY == Overflow.VISIBLE ? context.getScreenHeight() : viewportBottom;
        return new int[] { clipLeft, clipTop, clipRight, clipBottom };
    }

    @Override
    public DivWidget addChild(Widget child) {
        super.addChild(child);
        return this;
    }

    public DivWidget addChild(Widget child, DivItemStyle style) {
        if (child != null) {
            child.setDivItemStyle(style);
        }
        super.addChild(child);
        return this;
    }

    @Override
    public DivWidget setLayoutSpec(UiLayoutSpec layoutSpec) {
        super.setLayoutSpec(layoutSpec);
        return this;
    }

    @Override
    public DivWidget setSuggestedSize(int suggestedWidth, int suggestedHeight) {
        super.setSuggestedSize(suggestedWidth, suggestedHeight);
        return this;
    }

    /**
     * 追加固定子项，不参与主轴伸缩。
     *
     * @param child 子组件
     * @return 当前容器
     */
    public DivWidget addFixedChild(Widget child) {
        return addChild(child, DivItemStyle.fixed());
    }

    /**
     * 追加不增长的子项，可按需压缩。
     *
     * @param child 子组件
     * @return 当前容器
     */
    public DivWidget addNoGrowChild(Widget child) {
        return addChild(child, DivItemStyle.noGrow());
    }

    /**
     * 追加柔性子项，允许按主轴空间自动伸缩。
     *
     * @param child 子组件
     * @return 当前容器
     */
    public DivWidget addFlexChild(Widget child) {
        return addChild(child, DivItemStyle.flex());
    }

    /**
     * 将当前 Div 设为页面主内容列。
     *
     * @return 当前容器
     */
    public DivWidget setPageColumn() {
        return setDirection(Direction.COLUMN)
                .setAlignItems(AlignItems.STRETCH)
                .setJustifyContent(JustifyContent.START)
                .setWrap(Wrap.NOWRAP)
                .setGap(16)
                .setOverflowX(Overflow.VISIBLE)
                .setOverflowY(Overflow.VISIBLE)
                .setFillLayout();
    }

    /**
     * 将当前 Div 设为卡片内部常用的纵向区块。
     *
     * @return 当前容器
     */
    public DivWidget setSectionColumn() {
        return setDirection(Direction.COLUMN)
                .setAlignItems(AlignItems.STRETCH)
                .setJustifyContent(JustifyContent.START)
                .setWrap(Wrap.NOWRAP)
                .setGap(12)
                .setOverflowX(Overflow.VISIBLE)
                .setOverflowY(Overflow.VISIBLE)
                .setFillLayout();
    }

    /**
     * 将当前 Div 设为网页常见的卡片流，适合多个区块横向排布并在窄屏下换行。
     *
     * @return 当前容器
     */
    public DivWidget setContentFlow() {
        return setDirection(Direction.ROW)
                .setAlignItems(AlignItems.STRETCH)
                .setJustifyContent(JustifyContent.START)
                .setWrap(Wrap.WRAP)
                .setGap(16)
                .setOverflowX(Overflow.VISIBLE)
                .setOverflowY(Overflow.VISIBLE)
                .setFillLayout();
    }

    /**
     * 将当前 Div 设为网页常见的行内流，元素按行排列并在空间不足时自然换行。
     *
     * @return 当前容器
     */
    public DivWidget setInlineFlow() {
        return setDirection(Direction.ROW)
                .setAlignItems(AlignItems.CENTER)
                .setJustifyContent(JustifyContent.START)
                .setWrap(Wrap.WRAP)
                .setGap(10)
                .setOverflowX(Overflow.VISIBLE)
                .setOverflowY(Overflow.VISIBLE)
                .setFillLayout();
    }

    /**
     * 将当前 Div 设为表单行，标签与控件会在窄屏下自动换行。
     *
     * @return 当前容器
     */
    public DivWidget setFormRow() {
        return setDirection(Direction.ROW)
                .setAlignItems(AlignItems.CENTER)
                .setJustifyContent(JustifyContent.START)
                .setWrap(Wrap.WRAP)
                .setGap(16)
                .setOverflowX(Overflow.VISIBLE)
                .setOverflowY(Overflow.VISIBLE)
                .setFillLayout();
    }

    /**
     * 将当前 Div 设为按钮流，适合操作按钮组自动换行。
     *
     * @return 当前容器
     */
    public DivWidget setButtonFlow() {
        return setDirection(Direction.ROW)
                .setAlignItems(AlignItems.STRETCH)
                .setJustifyContent(JustifyContent.START)
                .setWrap(Wrap.WRAP)
                .setGap(12)
                .setOverflowX(Overflow.VISIBLE)
                .setOverflowY(Overflow.VISIBLE)
                .setFillLayout();
    }

    /**
     * 在外层响应式容器中占满可用宽度，并保持高度自适应。
     *
     * @return 当前容器
     */
    public DivWidget setFillLayout() {
        ensureLayoutSpec().setWidth(UiLength.percent(1.0F)).setHeight(UiLength.auto()).setFill(true);
        return setWidthPercent(1.0F);
    }

    /**
     * 设置当前 Div 在外层响应式容器中的纵向增长系数。
     *
     * @param grow 增长系数
     * @return 当前容器
     */
    public DivWidget setGrowLayout(float grow) {
        ensureLayoutSpec().setGrow(grow);
        return this;
    }

    /**
     * 设置当前 Div 在外层响应式容器中的最小高度约束。
     *
     * @param minHeight 最小高度
     * @return 当前容器
     */
    public DivWidget setMinHeightLayout(int minHeight) {
        ensureLayoutSpec().setMinHeight(minHeight);
        return this;
    }

    /**
     * 将当前 Div 设为仅纵向滚动，横向溢出直接裁切。
     *
     * @return 当前容器
     */
    public DivWidget setVerticalScrollOnly() {
        return setOverflowX(Overflow.HIDDEN).setOverflowY(Overflow.AUTO);
    }

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

    public DivWidget setWrap(Wrap wrap) {
        this.wrap = wrap == null ? Wrap.NOWRAP : wrap;
        return this;
    }

    public DivWidget setOverflowX(Overflow overflowX) {
        this.overflowX = overflowX == null ? Overflow.VISIBLE : overflowX;
        return this;
    }

    public DivWidget setOverflowY(Overflow overflowY) {
        this.overflowY = overflowY == null ? Overflow.VISIBLE : overflowY;
        return this;
    }

    public DivWidget setScrollStep(int scrollStep) {
        this.scrollStep = Math.max(8, scrollStep);
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
        UiLayoutSpec layoutSpec = ensureLayoutSpec();
        layoutSpec.setWidth(this.widthPercent >= 0.0F ? UiLength.percent(this.widthPercent) : UiLength.auto());
        return this;
    }

    public DivWidget setHeightPercent(float heightPercent) {
        this.heightPercent = clampPercent(heightPercent);
        UiLayoutSpec layoutSpec = ensureLayoutSpec();
        layoutSpec.setHeight(this.heightPercent >= 0.0F ? UiLength.percent(this.heightPercent) : UiLength.auto());
        return this;
    }

    public float getWidthPercent() {
        return widthPercent;
    }

    public float getHeightPercent() {
        return heightPercent;
    }

    public int getHorizontalScrollOffset() {
        return horizontalScrollOffset;
    }

    public int getVerticalScrollOffset() {
        return verticalScrollOffset;
    }

    public int getMaxHorizontalScrollOffset() {
        return maxHorizontalScrollOffset;
    }

    public int getMaxVerticalScrollOffset() {
        return maxVerticalScrollOffset;
    }

    public int getVisibleContentWidth() {
        return getViewportWidth();
    }

    public int getVisibleContentHeight() {
        return getViewportHeight();
    }

    public int getContentWidth() {
        return measureContentWidth();
    }

    public int getContentHeight() {
        return measureContentHeight();
    }

    @Override
    public boolean onMouseScroll(UiMouseEvent event) {
        LayoutResult layout = measureLayout(getWidth(), getHeight(), false, false);
        updateScrollState(layout);
        int previousHorizontalOffset = horizontalScrollOffset;
        int previousVerticalOffset = verticalScrollOffset;
        int steps = Math.max(1, Math.round(Math.abs(event.getWheelDelta()) / 120.0F));
        int delta = scrollStep * steps;

        if (maxVerticalScrollOffset > 0 && overflowY == Overflow.AUTO) {
            if (event.getWheelDelta() > 0) {
                setVerticalScrollOffset(verticalScrollOffset - delta);
            } else if (event.getWheelDelta() < 0) {
                setVerticalScrollOffset(verticalScrollOffset + delta);
            }
        } else if (maxHorizontalScrollOffset > 0 && overflowX == Overflow.AUTO) {
            if (event.getWheelDelta() > 0) {
                setHorizontalScrollOffset(horizontalScrollOffset - delta);
            } else if (event.getWheelDelta() < 0) {
                setHorizontalScrollOffset(horizontalScrollOffset + delta);
            }
        }
        return previousHorizontalOffset != horizontalScrollOffset || previousVerticalOffset != verticalScrollOffset;
    }

    @Override
    public void onMouseMove(UiMouseEvent event) {
        LayoutResult layout = measureLayout(getWidth(), getHeight(), false, false);
        updateScrollState(layout);
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

        LayoutResult layout = measureLayout(getWidth(), getHeight(), false, false);
        updateScrollState(layout);
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
        LayoutResult layout = measureLayout(getWidth(), getHeight(), false, false);
        updateScrollState(layout);
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

    /**
     * 将目标子组件滚动到当前可视区域内。
     *
     * @param target 目标组件
     */
    public void scrollDescendantIntoView(Widget target) {
        if (!isDescendant(target)) {
            return;
        }

        LayoutResult layout = measureLayout(getWidth(), getHeight(), false, false);
        updateScrollState(layout);
        int viewportLeft = getAbsoluteX() + paddingLeft;
        int viewportTop = getAbsoluteY() + paddingTop;
        int viewportRight = getAbsoluteX() + getWidth() - paddingRight;
        int viewportBottom = getAbsoluteY() + getHeight() - paddingBottom;
        int targetLeft = target.getAbsoluteX();
        int targetTop = target.getAbsoluteY();
        int targetRight = targetLeft + target.getWidth();
        int targetBottom = targetTop + target.getHeight();

        if (targetLeft < viewportLeft && overflowX == Overflow.AUTO) {
            setHorizontalScrollOffset(horizontalScrollOffset - (viewportLeft - targetLeft));
        } else if (targetRight > viewportRight && overflowX == Overflow.AUTO) {
            setHorizontalScrollOffset(horizontalScrollOffset + (targetRight - viewportRight));
        }

        if (targetTop < viewportTop && overflowY == Overflow.AUTO) {
            setVerticalScrollOffset(verticalScrollOffset - (viewportTop - targetTop));
        } else if (targetBottom > viewportBottom && overflowY == Overflow.AUTO) {
            setVerticalScrollOffset(verticalScrollOffset + (targetBottom - viewportBottom));
        }
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

        if (wrap == Wrap.WRAP) {
            int contentWidth = 0;
            for (Widget child : getChildren()) {
                contentWidth = Math.max(contentWidth, child.getMinContentWidth());
            }
            return paddingLeft + contentWidth + paddingRight;
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

    private LayoutResult measureLayout(int containerWidth, int containerHeight, boolean applyBounds, boolean useMinHeights) {
        if (wrap == Wrap.WRAP) {
            if (direction == Direction.ROW) {
                return measureWrappedRows(containerWidth, containerHeight, applyBounds, useMinHeights);
            }
            return measureWrappedColumns(containerWidth, containerHeight, applyBounds, useMinHeights);
        }
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
            minWidths[index] = resolveChildMinWidth(child, innerWidth);
            widths[index] = Math.max(minWidths[index], resolveRowBaseWidth(child, innerWidth));
            totalWidth += widths[index];
            heights[index] = resolveRowCrossSize(child, widths[index], innerHeight, useMinHeights, false);
        }
        totalWidth += gap * Math.max(0, childCount - 1);

        if (innerWidth > 0) {
            if (totalWidth < innerWidth) {
                totalWidth += distributeGrowth(widths, innerWidth - totalWidth, 0, childCount - 1);
            } else if (totalWidth > innerWidth) {
                totalWidth -= distributeShrink(widths, minWidths, totalWidth - innerWidth, 0, childCount - 1);
            }
        }

        int tallest = 0;
        for (int index = 0; index < childCount; index++) {
            heights[index] = resolveRowCrossSize(getChildren().get(index), widths[index], innerHeight, useMinHeights, true);
            tallest = Math.max(tallest, heights[index]);
        }
        if (alignItems == AlignItems.STRETCH && innerHeight > 0) {
            tallest = Math.max(tallest, innerHeight);
            for (int index = 0; index < childCount; index++) {
                heights[index] = tallest;
            }
        }

        int extraSpace = Math.max(0, innerWidth - totalWidth);
        int cursor = paddingLeft + resolveLeadingOffset(extraSpace, childCount);
        int dynamicGap = resolveGap(extraSpace, childCount);
        for (int index = 0; index < childCount; index++) {
            Widget child = getChildren().get(index);
            int childY = paddingTop + resolveCrossOffset(innerHeight, heights[index]);
            if (applyBounds) {
                child.setBounds(cursor - horizontalScrollOffset, childY - verticalScrollOffset, widths[index], heights[index]);
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
            minHeights[index] = resolveChildMinHeight(child, widths[index], innerHeight);
            widest = Math.max(widest, widths[index]);
            totalHeight += heights[index];
        }
        totalHeight += gap * Math.max(0, childCount - 1);

        if (innerHeight > 0) {
            if (totalHeight < innerHeight) {
                totalHeight += distributeGrowth(heights, innerHeight - totalHeight, 0, childCount - 1);
            } else if (totalHeight > innerHeight) {
                totalHeight -= distributeShrink(heights, minHeights, totalHeight - innerHeight, 0, childCount - 1);
            }
        }

        if (alignItems == AlignItems.STRETCH && innerWidth > 0) {
            widest = Math.max(widest, innerWidth);
            for (int index = 0; index < childCount; index++) {
                widths[index] = widest;
            }
        }

        int extraSpace = Math.max(0, innerHeight - totalHeight);
        int cursor = paddingTop + resolveLeadingOffset(extraSpace, childCount);
        int dynamicGap = resolveGap(extraSpace, childCount);
        for (int index = 0; index < childCount; index++) {
            Widget child = getChildren().get(index);
            int childX = paddingLeft + resolveCrossOffset(innerWidth, widths[index]);
            if (applyBounds) {
                child.setBounds(childX - horizontalScrollOffset, cursor - verticalScrollOffset, widths[index], heights[index]);
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

    private LayoutResult measureWrappedRows(int containerWidth, int containerHeight, boolean applyBounds, boolean useMinHeights) {
        LayoutResult result = new LayoutResult();
        int childCount = getChildren().size();
        if (childCount == 0) {
            result.requiredWidth = paddingLeft + paddingRight;
            result.requiredHeight = paddingTop + paddingBottom;
            return result;
        }

        int innerWidth = Math.max(0, containerWidth - paddingLeft - paddingRight);
        int[] widths = new int[childCount];
        int[] minWidths = new int[childCount];
        int[] heights = new int[childCount];
        for (int index = 0; index < childCount; index++) {
            Widget child = getChildren().get(index);
            minWidths[index] = resolveChildMinWidth(child, innerWidth);
            widths[index] = Math.max(minWidths[index], resolveRowBaseWidth(child, innerWidth));
            heights[index] = resolveRowCrossSize(child, widths[index], 0, useMinHeights, false);
        }

        List<AxisGroup> groups = buildWrappedGroups(widths, innerWidth);
        int maxLineWidth = 0;
        int totalHeight = 0;
        for (AxisGroup group : groups) {
            int lineWidth = computeTotal(widths, group.start, group.end);
            if (innerWidth > 0) {
                if (lineWidth < innerWidth) {
                    lineWidth += distributeGrowth(widths, innerWidth - lineWidth, group.start, group.end);
                } else if (lineWidth > innerWidth) {
                    lineWidth -= distributeShrink(widths, minWidths, lineWidth - innerWidth, group.start, group.end);
                }
            }
            group.mainSize = computeTotal(widths, group.start, group.end);
            group.crossSize = 0;
            for (int index = group.start; index <= group.end; index++) {
                heights[index] = resolveRowCrossSize(getChildren().get(index), widths[index], 0, useMinHeights, false);
                group.crossSize = Math.max(group.crossSize, heights[index]);
            }
            if (alignItems == AlignItems.STRETCH) {
                for (int index = group.start; index <= group.end; index++) {
                    heights[index] = group.crossSize;
                }
            }
            maxLineWidth = Math.max(maxLineWidth, group.mainSize);
            totalHeight += group.crossSize;
        }
        totalHeight += gap * Math.max(0, groups.size() - 1);

        int cursorY = paddingTop;
        for (AxisGroup group : groups) {
            int count = group.end - group.start + 1;
            int extraSpace = Math.max(0, innerWidth - group.mainSize);
            int cursorX = paddingLeft + resolveLeadingOffset(extraSpace, count);
            int dynamicGap = resolveGap(extraSpace, count);
            for (int index = group.start; index <= group.end; index++) {
                int childY = cursorY + resolveCrossOffset(group.crossSize, heights[index]);
                if (applyBounds) {
                    getChildren().get(index).setBounds(cursorX - horizontalScrollOffset, childY - verticalScrollOffset,
                            widths[index], heights[index]);
                }
                cursorX += widths[index];
                if (index < group.end) {
                    cursorX += dynamicGap;
                }
            }
            cursorY += group.crossSize + gap;
        }

        result.requiredWidth = paddingLeft + maxLineWidth + paddingRight;
        result.requiredHeight = paddingTop + totalHeight + paddingBottom;
        return result;
    }

    private LayoutResult measureWrappedColumns(int containerWidth, int containerHeight, boolean applyBounds, boolean useMinHeights) {
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
        for (int index = 0; index < childCount; index++) {
            Widget child = getChildren().get(index);
            widths[index] = resolveColumnCrossSize(child, innerWidth);
            heights[index] = resolveColumnBaseHeight(child, widths[index], innerHeight, false);
            minHeights[index] = resolveChildMinHeight(child, widths[index], innerHeight);
        }

        List<AxisGroup> groups = buildWrappedGroups(heights, innerHeight);
        int totalWidth = 0;
        int tallestColumn = 0;
        for (AxisGroup group : groups) {
            int columnHeight = computeTotal(heights, group.start, group.end);
            if (innerHeight > 0) {
                if (columnHeight < innerHeight) {
                    columnHeight += distributeGrowth(heights, innerHeight - columnHeight, group.start, group.end);
                } else if (columnHeight > innerHeight) {
                    columnHeight -= distributeShrink(heights, minHeights, columnHeight - innerHeight, group.start, group.end);
                }
            }
            group.mainSize = computeTotal(heights, group.start, group.end);
            group.crossSize = 0;
            for (int index = group.start; index <= group.end; index++) {
                group.crossSize = Math.max(group.crossSize, widths[index]);
            }
            if (alignItems == AlignItems.STRETCH) {
                for (int index = group.start; index <= group.end; index++) {
                    widths[index] = group.crossSize;
                }
            }
            totalWidth += group.crossSize;
            tallestColumn = Math.max(tallestColumn, group.mainSize);
        }
        totalWidth += gap * Math.max(0, groups.size() - 1);

        int cursorX = paddingLeft;
        for (AxisGroup group : groups) {
            int count = group.end - group.start + 1;
            int extraSpace = Math.max(0, innerHeight - group.mainSize);
            int cursorY = paddingTop + resolveLeadingOffset(extraSpace, count);
            int dynamicGap = resolveGap(extraSpace, count);
            for (int index = group.start; index <= group.end; index++) {
                int childX = cursorX + resolveCrossOffset(group.crossSize, widths[index]);
                if (applyBounds) {
                    getChildren().get(index).setBounds(childX - horizontalScrollOffset, cursorY - verticalScrollOffset,
                            widths[index], heights[index]);
                }
                cursorY += heights[index];
                if (index < group.end) {
                    cursorY += dynamicGap;
                }
            }
            cursorX += group.crossSize + gap;
        }

        result.requiredWidth = paddingLeft + totalWidth + paddingRight;
        result.requiredHeight = paddingTop + tallestColumn + paddingBottom;
        return result;
    }

    private List<AxisGroup> buildWrappedGroups(int[] sizes, int availableMainSize) {
        List<AxisGroup> groups = new ArrayList<AxisGroup>();
        if (sizes.length == 0) {
            return groups;
        }

        int start = 0;
        int currentSize = 0;
        for (int index = 0; index < sizes.length; index++) {
            int candidate = currentSize == 0 ? sizes[index] : currentSize + gap + sizes[index];
            if (availableMainSize > 0 && index > start && candidate > availableMainSize) {
                groups.add(new AxisGroup(start, index - 1));
                start = index;
                currentSize = sizes[index];
            } else {
                currentSize = candidate;
            }
        }
        groups.add(new AxisGroup(start, sizes.length - 1));
        return groups;
    }

    private int resolveRowBaseWidth(Widget child, int innerWidth) {
        return resolveConfiguredWidth(child, innerWidth, child.getSuggestedWidth(), false);
    }

    private int resolveRowCrossSize(Widget child, int childWidth, int innerHeight, boolean useMinHeight, boolean actualLayout) {
        int fallback = useMinHeight ? child.getMinContentHeightForWidth(childWidth) : child.getSuggestedHeightForWidth(childWidth);
        return resolveConfiguredHeight(child, childWidth, innerHeight, fallback, actualLayout);
    }

    private int resolveColumnCrossSize(Widget child, int innerWidth) {
        boolean hasConfiguredWidth = hasConfiguredWidth(child);
        int preferredWidth = resolveConfiguredWidth(child, innerWidth, child.getSuggestedWidth(), alignItems == AlignItems.STRETCH);
        if (alignItems == AlignItems.STRETCH && innerWidth > 0 && !hasConfiguredWidth) {
            preferredWidth = innerWidth;
        }
        return Math.max(resolveChildMinWidth(child, innerWidth), preferredWidth);
    }

    private int resolveColumnBaseHeight(Widget child, int childWidth, int innerHeight, boolean useMinHeight) {
        int fallback = useMinHeight ? child.getMinContentHeightForWidth(childWidth) : child.getSuggestedHeightForWidth(childWidth);
        return resolveConfiguredHeight(child, childWidth, innerHeight, fallback, false);
    }

    private int resolveChildMinWidth(Widget child, int availableWidth) {
        UiLayoutSpec layoutSpec = child.getLayoutSpec();
        int minWidth = hasConfiguredWidth(child) ? 0 : child.getMinContentWidth();
        if (layoutSpec != null) {
            minWidth = Math.max(minWidth, layoutSpec.getMinWidth());
            minWidth = clampToBounds(minWidth, layoutSpec.getMinWidth(), layoutSpec.getMaxWidth());
        }
        return Math.max(0, minWidth);
    }

    private int resolveChildMinHeight(Widget child, int childWidth, int availableHeight) {
        UiLayoutSpec layoutSpec = child.getLayoutSpec();
        int minHeight = child.getMinContentHeightForWidth(childWidth);
        if (hasConfiguredHeight(child)) {
            minHeight = resolveConfiguredHeight(child, childWidth, availableHeight, minHeight, false);
        }
        if (layoutSpec != null) {
            minHeight = Math.max(minHeight, layoutSpec.getMinHeight());
            minHeight = clampToBounds(minHeight, layoutSpec.getMinHeight(), layoutSpec.getMaxHeight());
        }
        return Math.max(0, minHeight);
    }

    private int resolveConfiguredWidth(Widget child, int availableWidth, int fallback, boolean allowFill) {
        UiLayoutSpec layoutSpec = child.getLayoutSpec();
        UiLength width = resolveWidthLength(child, layoutSpec);
        int resolvedWidth = fallback;
        if (width.getType() != UiLength.Type.AUTO) {
            resolvedWidth = resolveLengthValue(width, availableWidth, fallback);
        } else if (allowFill && layoutSpec != null && layoutSpec.isFill() && availableWidth > 0) {
            resolvedWidth = availableWidth;
        }
        return clampToBounds(resolvedWidth, layoutSpec == null ? 0 : layoutSpec.getMinWidth(),
                layoutSpec == null ? Integer.MAX_VALUE : layoutSpec.getMaxWidth());
    }

    private int resolveConfiguredHeight(Widget child, int childWidth, int availableHeight, int fallback, boolean allowFill) {
        UiLayoutSpec layoutSpec = child.getLayoutSpec();
        UiLength height = resolveHeightLength(child, layoutSpec);
        int resolvedHeight = fallback;
        if (height.getType() != UiLength.Type.AUTO) {
            resolvedHeight = resolveLengthValue(height, availableHeight, fallback);
        } else if (allowFill && layoutSpec != null && layoutSpec.isFill() && availableHeight > 0) {
            resolvedHeight = availableHeight;
        }
        return clampToBounds(resolvedHeight, layoutSpec == null ? 0 : layoutSpec.getMinHeight(),
                layoutSpec == null ? Integer.MAX_VALUE : layoutSpec.getMaxHeight());
    }

    private UiLength resolveWidthLength(Widget child, UiLayoutSpec layoutSpec) {
        if (layoutSpec != null && layoutSpec.getWidth().getType() != UiLength.Type.AUTO) {
            return layoutSpec.getWidth();
        }
        if (child instanceof DivWidget) {
            DivWidget divChild = (DivWidget) child;
            if (divChild.widthPercent >= 0.0F) {
                return UiLength.percent(divChild.widthPercent);
            }
        }
        return UiLength.auto();
    }

    private UiLength resolveHeightLength(Widget child, UiLayoutSpec layoutSpec) {
        if (layoutSpec != null && layoutSpec.getHeight().getType() != UiLength.Type.AUTO) {
            return layoutSpec.getHeight();
        }
        if (child instanceof DivWidget) {
            DivWidget divChild = (DivWidget) child;
            if (divChild.heightPercent >= 0.0F) {
                return UiLength.percent(divChild.heightPercent);
            }
        }
        return UiLength.auto();
    }

    private boolean hasConfiguredWidth(Widget child) {
        UiLayoutSpec layoutSpec = child.getLayoutSpec();
        return (layoutSpec != null && layoutSpec.getWidth().getType() != UiLength.Type.AUTO)
                || (child instanceof DivWidget && ((DivWidget) child).widthPercent >= 0.0F);
    }

    private boolean hasConfiguredHeight(Widget child) {
        UiLayoutSpec layoutSpec = child.getLayoutSpec();
        return (layoutSpec != null && layoutSpec.getHeight().getType() != UiLength.Type.AUTO)
                || (child instanceof DivWidget && ((DivWidget) child).heightPercent >= 0.0F);
    }

    private int resolveLengthValue(UiLength length, int availableSpace, int fallback) {
        if (length == null || length.getType() == UiLength.Type.AUTO) {
            return fallback;
        }
        if (length.getType() == UiLength.Type.PERCENT) {
            return Math.round(Math.max(0, availableSpace) * length.getValue());
        }
        return Math.round(length.getValue());
    }

    private int clampToBounds(int value, int min, int max) {
        int safeMin = Math.max(0, min);
        int safeMax = max <= 0 ? 0 : Math.max(safeMin, max);
        return Math.max(safeMin, Math.min(value, safeMax));
    }

    private int distributeGrowth(int[] sizes, int extra, int start, int end) {
        if (extra <= 0 || start > end) {
            return 0;
        }

        int growableCount = 0;
        for (int index = start; index <= end; index++) {
            DivItemStyle style = getChildren().get(index).getDivItemStyle();
            if (style == null || style.isGrow()) {
                growableCount++;
            }
        }
        if (growableCount <= 0) {
            return 0;
        }

        int applied = 0;
        int growIndex = 0;
        for (int index = start; index <= end; index++) {
            DivItemStyle style = getChildren().get(index).getDivItemStyle();
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

    private int distributeShrink(int[] sizes, int[] minSizes, int overflow, int start, int end) {
        if (overflow <= 0 || start > end) {
            return 0;
        }

        int shrinkable = 0;
        for (int index = start; index <= end; index++) {
            DivItemStyle style = getChildren().get(index).getDivItemStyle();
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
        for (int index = start; index <= end; index++) {
            DivItemStyle style = getChildren().get(index).getDivItemStyle();
            if (style != null && !style.isShrink()) {
                continue;
            }
            if (sizes[index] > minSizes[index]) {
                remainingShrinkable++;
            }
        }

        int shrinkIndex = 0;
        for (int index = start; index <= end; index++) {
            DivItemStyle style = getChildren().get(index).getDivItemStyle();
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

    private int computeTotal(int[] sizes, int start, int end) {
        int total = 0;
        for (int index = start; index <= end; index++) {
            total += sizes[index];
        }
        total += gap * Math.max(0, end - start);
        return total;
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

    private int resolveCrossOffset(int availableCrossSize, int childCrossSize) {
        if (availableCrossSize <= 0) {
            return 0;
        }
        switch (alignItems) {
            case CENTER:
                return (availableCrossSize - childCrossSize) / 2;
            case END:
                return availableCrossSize - childCrossSize;
            default:
                return 0;
        }
    }

    private void updateScrollState(LayoutResult layout) {
        int viewportWidth = getViewportWidth();
        int viewportHeight = getViewportHeight();
        maxHorizontalScrollOffset = overflowX == Overflow.VISIBLE ? 0 : Math.max(0, layout.requiredWidth - paddingLeft - paddingRight - viewportWidth);
        maxVerticalScrollOffset = overflowY == Overflow.VISIBLE ? 0 : Math.max(0, layout.requiredHeight - paddingTop - paddingBottom - viewportHeight);
        setHorizontalScrollOffset(horizontalScrollOffset);
        setVerticalScrollOffset(verticalScrollOffset);

        if (maxHorizontalScrollOffset <= 0) {
            draggingHorizontalThumb = false;
            hoveredHorizontalScrollbar = false;
            hoveredHorizontalThumb = false;
        }
        if (maxVerticalScrollOffset <= 0) {
            draggingVerticalThumb = false;
            hoveredVerticalScrollbar = false;
            hoveredVerticalThumb = false;
        }
    }

    private void drawScrollbars(UiRenderContext context) {
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

    private ScrollbarMetrics getVerticalScrollbarMetrics() {
        if (overflowY != Overflow.AUTO || maxVerticalScrollOffset <= 0) {
            return null;
        }

        ScrollbarMetrics metrics = new ScrollbarMetrics();
        int horizontalReserve = maxHorizontalScrollOffset > 0 && overflowX == Overflow.AUTO ? SCROLLBAR_TRACK_THICKNESS + SCROLLBAR_TRACK_GAP : 0;
        metrics.trackRight = getAbsoluteX() + getWidth() - paddingRight;
        metrics.trackLeft = metrics.trackRight - SCROLLBAR_TRACK_THICKNESS;
        metrics.trackTop = getAbsoluteY() + paddingTop;
        metrics.trackBottom = getAbsoluteY() + getHeight() - paddingBottom - horizontalReserve;
        metrics.trackLength = Math.max(1, metrics.trackBottom - metrics.trackTop);
        metrics.thumbSize = Math.max(SCROLLBAR_MIN_THUMB_SIZE,
                Math.round(metrics.trackLength * (getViewportHeight() / (float) Math.max(getViewportHeight(), measureContentHeight()))));
        int travel = Math.max(0, metrics.trackLength - metrics.thumbSize);
        metrics.thumbTop = metrics.trackTop + Math.round(travel * (verticalScrollOffset / (float) Math.max(1, maxVerticalScrollOffset)));
        metrics.thumbBottom = metrics.thumbTop + metrics.thumbSize;
        metrics.thumbLeft = metrics.trackLeft;
        metrics.thumbRight = metrics.trackRight;
        return metrics;
    }

    private ScrollbarMetrics getHorizontalScrollbarMetrics() {
        if (overflowX != Overflow.AUTO || maxHorizontalScrollOffset <= 0) {
            return null;
        }

        ScrollbarMetrics metrics = new ScrollbarMetrics();
        int verticalReserve = maxVerticalScrollOffset > 0 && overflowY == Overflow.AUTO ? SCROLLBAR_TRACK_THICKNESS + SCROLLBAR_TRACK_GAP : 0;
        metrics.trackLeft = getAbsoluteX() + paddingLeft;
        metrics.trackRight = getAbsoluteX() + getWidth() - paddingRight - verticalReserve;
        metrics.trackBottom = getAbsoluteY() + getHeight() - paddingBottom;
        metrics.trackTop = metrics.trackBottom - SCROLLBAR_TRACK_THICKNESS;
        metrics.trackLength = Math.max(1, metrics.trackRight - metrics.trackLeft);
        metrics.thumbSize = Math.max(SCROLLBAR_MIN_THUMB_SIZE,
                Math.round(metrics.trackLength * (getViewportWidth() / (float) Math.max(getViewportWidth(), measureContentWidth()))));
        int travel = Math.max(0, metrics.trackLength - metrics.thumbSize);
        metrics.thumbLeft = metrics.trackLeft + Math.round(travel * (horizontalScrollOffset / (float) Math.max(1, maxHorizontalScrollOffset)));
        metrics.thumbRight = metrics.thumbLeft + metrics.thumbSize;
        metrics.thumbTop = metrics.trackTop;
        metrics.thumbBottom = metrics.trackBottom;
        return metrics;
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

    private void setHorizontalScrollOffset(int scrollOffset) {
        horizontalScrollOffset = Math.max(0, Math.min(scrollOffset, maxHorizontalScrollOffset));
    }

    private void setVerticalScrollOffset(int scrollOffset) {
        verticalScrollOffset = Math.max(0, Math.min(scrollOffset, maxVerticalScrollOffset));
    }

    private boolean hasClippedOverflow() {
        return overflowX != Overflow.VISIBLE || overflowY != Overflow.VISIBLE;
    }

    private int getViewportWidth() {
        return Math.max(0, getWidth() - paddingLeft - paddingRight);
    }

    private int getViewportHeight() {
        return Math.max(0, getHeight() - paddingTop - paddingBottom);
    }

    private int measureContentWidth() {
        LayoutResult layout = measureLayout(getWidth(), getHeight(), false, false);
        return Math.max(0, layout.requiredWidth - paddingLeft - paddingRight);
    }

    private int measureContentHeight() {
        LayoutResult layout = measureLayout(getWidth(), getHeight(), false, false);
        return Math.max(0, layout.requiredHeight - paddingTop - paddingBottom);
    }

    private boolean containsInRect(int mouseX, int mouseY, int left, int top, int right, int bottom) {
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    private boolean isDescendant(Widget target) {
        Widget current = target;
        while (current != null) {
            if (current == this) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private float clampPercent(float percent) {
        return percent < 0.0F ? -1.0F : Math.max(0.0F, Math.min(percent, 1.0F));
    }

    private UiLayoutSpec ensureLayoutSpec() {
        UiLayoutSpec layoutSpec = getLayoutSpec();
        if (layoutSpec == null) {
            layoutSpec = new UiLayoutSpec();
            super.setLayoutSpec(layoutSpec);
        }
        return layoutSpec;
    }

    private static class AxisGroup {
        private final int start;
        private final int end;
        private int mainSize;
        private int crossSize;

        private AxisGroup(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

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

    private static class LayoutResult {
        private int requiredWidth;
        private int requiredHeight;
    }
}
