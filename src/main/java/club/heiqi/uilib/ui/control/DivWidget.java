package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import club.heiqi.uilib.ui.diagnostic.UiPerformanceMonitor;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.layout.UiAlignSelf;
import club.heiqi.uilib.ui.layout.UiConstraints;
import club.heiqi.uilib.ui.layout.UiInsets;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.layout.UiMeasureResult;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.theme.UiSurfaceStyle;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 类似浏览器 div 的自动排布容器。
 */
public class DivWidget extends Widget implements UiScrollHost {

    private final TextMeasureService textMeasureService;

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
     * 多行/多列在交叉轴上的整体分布方式。
     */
    public enum AlignContent {
        START,
        CENTER,
        END,
        SPACE_BETWEEN,
        STRETCH
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
    private AlignContent alignContent = AlignContent.START;
    private Wrap wrap = Wrap.NOWRAP;
    private Overflow overflowX = Overflow.VISIBLE;
    private Overflow overflowY = Overflow.VISIBLE;
    private int paddingLeft;
    private int paddingTop;
    private int paddingRight;
    private int paddingBottom;
    private int rowGap;
    private int columnGap;
    private UiSurfaceStyle surfaceStyle = UiSurfaceStyle.none();
    private final OverflowScrollState scrollState = new OverflowScrollState();
    private UiControlTheme.ScrollbarStyle scrollbarStyle;
    private int cachedLayoutVersion = -1;
    private int cachedLayoutWidth = -1;
    private int cachedLayoutHeight = -1;
    private int cachedHorizontalScrollOffset = Integer.MIN_VALUE;
    private int cachedVerticalScrollOffset = Integer.MIN_VALUE;
    private boolean cachedUseMinHeights;
    private int cachedPureMeasureVersion = -1;
    private int cachedPureMeasureTextMeasureEpoch = -1;
    private int cachedPreferredWidthVersion = -1;
    private int cachedPreferredWidth = -1;
    private int cachedPreferredWidthTextMeasureEpoch = -1;
    private int cachedMinContentWidthVersion = -1;
    private int cachedMinContentWidth = -1;
    private int cachedMinContentWidthTextMeasureEpoch = -1;
    private int cachedLayoutTextMeasureEpoch = -1;
    private final Map<MeasureCacheKey, LayoutResult> pureMeasureCache = new HashMap<MeasureCacheKey, LayoutResult>();

    public DivWidget() {
        this(DefaultTextMeasureService.getInstance());
    }

    public DivWidget(TextMeasureService textMeasureService) {
        this.textMeasureService = Objects.requireNonNull(textMeasureService, "textMeasureService");
    }

    @Override
    public void render(UiRenderContext context) {
        if (!isVisible()) {
            return;
        }
        UiPerformanceMonitor performanceMonitor = UiPerformanceMonitor.getInstance();
        performanceMonitor.enterWidget(this);
        try {
            ensureLayout(false);
            applyHitTestClipEnabled(hasClippedOverflow());

            drawSelf(context);
            int clipDepth = 0;
            boolean clipping = hasClippedOverflow();
            if (clipping) {
                int[] clipRect = resolveClipRect(context);
                context.pushClip(clipRect[0], clipRect[1], clipRect[2], clipRect[3]);
                clipDepth++;
            }
            try {
                for (Widget child : getChildren()) {
                    child.render(context);
                }
            } finally {
                popVisualClips(context, clipDepth);
            }
            drawScrollbars(context);
        } finally {
            performanceMonitor.exitWidget(this);
        }
    }

    @Override
    protected void drawSelf(UiRenderContext context) {
        context.drawSurface(getAbsoluteX(), getAbsoluteY(), getAbsoluteX() + getWidth(), getAbsoluteY() + getHeight(),
                surfaceStyle);
    }

    @Override
    protected int[] getChildClipRect() {
        int viewportLeft = getAbsoluteX() + paddingLeft;
        int viewportTop = getAbsoluteY() + paddingTop;
        int viewportRight = viewportLeft + getVisibleContentWidth();
        int viewportBottom = viewportTop + getVisibleContentHeight();
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
        int viewportRight = viewportLeft + getVisibleContentWidth();
        int viewportBottom = viewportTop + getVisibleContentHeight();
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

    @Override
    public DivWidget setLayoutSpec(UiLayoutSpec layoutSpec) {
        super.setLayoutSpec(layoutSpec);
        return this;
    }

    public DivWidget setDirection(Direction direction) {
        this.direction = direction == null ? Direction.COLUMN : direction;
        requestLayout();
        return this;
    }

    public DivWidget setAlignItems(AlignItems alignItems) {
        this.alignItems = alignItems == null ? AlignItems.STRETCH : alignItems;
        requestLayout();
        return this;
    }

    public DivWidget setJustifyContent(JustifyContent justifyContent) {
        this.justifyContent = justifyContent == null ? JustifyContent.START : justifyContent;
        requestLayout();
        return this;
    }

    public DivWidget setAlignContent(AlignContent alignContent) {
        this.alignContent = alignContent == null ? AlignContent.START : alignContent;
        requestLayout();
        return this;
    }

    public DivWidget setWrap(Wrap wrap) {
        this.wrap = wrap == null ? Wrap.NOWRAP : wrap;
        requestLayout();
        return this;
    }

    public DivWidget setOverflowX(Overflow overflowX) {
        this.overflowX = overflowX == null ? Overflow.VISIBLE : overflowX;
        requestLayout();
        return this;
    }

    public DivWidget setOverflowY(Overflow overflowY) {
        this.overflowY = overflowY == null ? Overflow.VISIBLE : overflowY;
        requestLayout();
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
        requestLayout();
        return this;
    }

    public DivWidget setGap(int gap) {
        int resolvedGap = Math.max(0, gap);
        this.rowGap = resolvedGap;
        this.columnGap = resolvedGap;
        requestLayout();
        return this;
    }

    public DivWidget setRowGap(int rowGap) {
        this.rowGap = Math.max(0, rowGap);
        requestLayout();
        return this;
    }

    public DivWidget setColumnGap(int columnGap) {
        this.columnGap = Math.max(0, columnGap);
        requestLayout();
        return this;
    }

    public DivWidget setSurfaceStyle(UiSurfaceStyle surfaceStyle) {
        this.surfaceStyle = Objects.requireNonNull(surfaceStyle, "surfaceStyle");
        return this;
    }

    protected final void applyScrollbarStyle(UiControlTheme.ScrollbarStyle scrollbarStyle) {
        this.scrollbarStyle = Objects.requireNonNull(scrollbarStyle, "scrollbarStyle");
    }

    public int getHorizontalScrollOffset() {
        return scrollState.getHorizontalOffset();
    }

    public int getVerticalScrollOffset() {
        return scrollState.getVerticalOffset();
    }

    public int getMaxHorizontalScrollOffset() {
        return scrollState.getMaxHorizontalOffset();
    }

    public int getMaxVerticalScrollOffset() {
        return scrollState.getMaxVerticalOffset();
    }

    public int getVisibleContentWidth() {
        if (scrollState.getViewportWidth() > 0 || getWidth() <= 0) {
            return scrollState.getViewportWidth();
        }
        return getViewportWidth();
    }

    public int getVisibleContentHeight() {
        if (scrollState.getViewportHeight() > 0 || getHeight() <= 0) {
            return scrollState.getViewportHeight();
        }
        return getViewportHeight();
    }

    public int getContentWidth() {
        if (scrollState.getContentWidth() > 0 || getChildren().isEmpty()) {
            return scrollState.getContentWidth();
        }
        ensureLayout(false);
        return scrollState.getContentWidth();
    }

    public int getContentHeight() {
        if (scrollState.getContentHeight() > 0 || getChildren().isEmpty()) {
            return scrollState.getContentHeight();
        }
        ensureLayout(false);
        return scrollState.getContentHeight();
    }

    @Override
    public boolean onMouseScroll(UiMouseEvent event) {
        ensureLayout(false);
        return scrollState.handleWheel(event.getWheelDelta(), overflowY == Overflow.AUTO, overflowX == Overflow.AUTO);
    }

    @Override
    public void onMouseMove(UiMouseEvent event) {
        ensureLayout(false);
        scrollState.updatePointer(event.getMouseX(), event.getMouseY(), getAbsoluteX() + paddingLeft,
                getAbsoluteY() + paddingTop);
    }

    @Override
    public void onMouseDown(UiMouseEvent event) {
        if (event.getButton() != 0) {
            return;
        }

        ensureLayout(false);
        scrollState.beginPointerDrag(event.getMouseX(), event.getMouseY(), getAbsoluteX() + paddingLeft,
                getAbsoluteY() + paddingTop);
    }

    @Override
    public void onMouseUp(UiMouseEvent event) {
        if (event.getButton() != 0) {
            return;
        }

        ensureLayout(false);
        scrollState.endPointerDrag(event.getMouseX(), event.getMouseY(), getAbsoluteX() + paddingLeft,
                getAbsoluteY() + paddingTop);
    }

    @Override
    public void onMouseLeave() {
        scrollState.clearHoverState();
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

        ensureLayout(false);
        int viewportLeft = getAbsoluteX() + paddingLeft;
        int viewportTop = getAbsoluteY() + paddingTop;
        int viewportRight = viewportLeft + getVisibleContentWidth();
        int viewportBottom = viewportTop + getVisibleContentHeight();
        int targetLeft = target.getAbsoluteX();
        int targetTop = target.getAbsoluteY();
        int targetRight = targetLeft + target.getWidth();
        int targetBottom = targetTop + target.getHeight();

        scrollState.scrollRectIntoView(viewportLeft, viewportTop, viewportRight, viewportBottom, targetLeft, targetTop,
                targetRight, targetBottom, overflowX == Overflow.AUTO, overflowY == Overflow.AUTO);
    }

    @Override
    public int getPreferredWidth() {
        int currentLayoutVersion = getLayoutVersion();
        int currentTextMeasureEpoch = textMeasureService.getEpoch();
        if (cachedPreferredWidthVersion == currentLayoutVersion
                && cachedPreferredWidthTextMeasureEpoch == currentTextMeasureEpoch
                && cachedPreferredWidth >= 0) {
            return cachedPreferredWidth;
        }
        if (getChildren().isEmpty()) {
            cachedPreferredWidthVersion = currentLayoutVersion;
            cachedPreferredWidthTextMeasureEpoch = currentTextMeasureEpoch;
            cachedPreferredWidth = paddingLeft + paddingRight;
            return cachedPreferredWidth;
        }

        int contentWidth = 0;
        if (direction == Direction.ROW) {
            for (Widget child : getChildren()) {
                contentWidth += computeRowMainSpan(measureIntrinsic(child).getWidth(), resolveMargin(child));
            }
            contentWidth += getMainAxisGap() * Math.max(0, getChildren().size() - 1);
        } else {
            for (Widget child : getChildren()) {
                contentWidth = Math.max(contentWidth, computeColumnCrossSpan(measureIntrinsic(child).getWidth(), resolveMargin(child)));
            }
        }
        cachedPreferredWidthVersion = currentLayoutVersion;
        cachedPreferredWidthTextMeasureEpoch = currentTextMeasureEpoch;
        cachedPreferredWidth = paddingLeft + contentWidth + paddingRight;
        return cachedPreferredWidth;
    }

    @Override
    public int getPreferredHeight() {
        return getPreferredHeightForWidth(Math.max(getPreferredWidth(), getWidth()));
    }

    @Override
    public int getPreferredHeightForWidth(int width) {
        LayoutResult layout = measureLayoutCached(Math.max(0, width), 0, false);
        return layout.requiredHeight;
    }

    @Override
    public UiMeasureResult measure(UiConstraints constraints) {
        UiConstraints effectiveConstraints = constraints == null ? UiConstraints.unbounded() : constraints;
        int containerWidth = effectiveConstraints.hasBoundedWidth() ? effectiveConstraints.getMaxWidth() : getPreferredWidth();
        int containerHeight = effectiveConstraints.hasBoundedHeight() ? effectiveConstraints.getMaxHeight() : 0;
        LayoutResult layout = measureLayoutCached(Math.max(0, containerWidth), Math.max(0, containerHeight), false);
        return new UiMeasureResult(effectiveConstraints.constrainWidth(layout.requiredWidth),
                effectiveConstraints.constrainHeight(layout.requiredHeight));
    }

    @Override
    public int getMinContentWidth() {
        int currentLayoutVersion = getLayoutVersion();
        int currentTextMeasureEpoch = textMeasureService.getEpoch();
        if (cachedMinContentWidthVersion == currentLayoutVersion
                && cachedMinContentWidthTextMeasureEpoch == currentTextMeasureEpoch
                && cachedMinContentWidth >= 0) {
            return cachedMinContentWidth;
        }
        if (getChildren().isEmpty()) {
            cachedMinContentWidthVersion = currentLayoutVersion;
            cachedMinContentWidthTextMeasureEpoch = currentTextMeasureEpoch;
            cachedMinContentWidth = paddingLeft + paddingRight;
            return cachedMinContentWidth;
        }

        if (wrap == Wrap.WRAP) {
            int contentWidth = 0;
            for (Widget child : getChildren()) {
                contentWidth = Math.max(contentWidth, computeColumnCrossSpan(child.getMinContentWidth(), resolveMargin(child)));
            }
            cachedMinContentWidthVersion = currentLayoutVersion;
            cachedMinContentWidthTextMeasureEpoch = currentTextMeasureEpoch;
            cachedMinContentWidth = paddingLeft + contentWidth + paddingRight;
            return cachedMinContentWidth;
        }

        int contentWidth = 0;
        if (direction == Direction.ROW) {
            for (Widget child : getChildren()) {
                contentWidth += computeRowMainSpan(child.getMinContentWidth(), resolveMargin(child));
            }
            contentWidth += getMainAxisGap() * Math.max(0, getChildren().size() - 1);
        } else {
            for (Widget child : getChildren()) {
                contentWidth = Math.max(contentWidth, computeColumnCrossSpan(child.getMinContentWidth(), resolveMargin(child)));
            }
        }
        cachedMinContentWidthVersion = currentLayoutVersion;
        cachedMinContentWidthTextMeasureEpoch = currentTextMeasureEpoch;
        cachedMinContentWidth = paddingLeft + contentWidth + paddingRight;
        return cachedMinContentWidth;
    }

    @Override
    public int getMinContentHeightForWidth(int width) {
        LayoutResult layout = measureLayoutCached(Math.max(0, width), 0, true);
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
        long phaseStartNanos = System.nanoTime();
        try {
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
            UiInsets[] margins = new UiInsets[childCount];
            int totalWidth = 0;

            for (int index = 0; index < childCount; index++) {
                Widget child = getChildren().get(index);
                margins[index] = resolveMargin(child);
                minWidths[index] = resolveChildMinWidth(child, innerWidth);
                widths[index] = Math.max(minWidths[index], resolveRowBaseWidth(child, innerWidth));
                totalWidth += computeRowMainSpan(widths[index], margins[index]);
                heights[index] = resolveRowCrossSize(child, widths[index], innerHeight, useMinHeights, false);
            }
            totalWidth += getMainAxisGap() * Math.max(0, childCount - 1);

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
                tallest = Math.max(tallest, computeRowCrossSpan(heights[index], margins[index]));
            }
            if (innerHeight > 0 && hasStretchChild(0, childCount - 1)) {
                tallest = innerHeight;
                for (int index = 0; index < childCount; index++) {
                    if (resolveChildAlignItems(getChildren().get(index)) == AlignItems.STRETCH) {
                        heights[index] = Math.max(0, innerHeight - margins[index].getTop() - margins[index].getBottom());
                    }
                }
            }

            int extraSpace = Math.max(0, innerWidth - totalWidth);
            int cursor = paddingLeft + resolveLeadingOffset(extraSpace, childCount);
            int dynamicGap = resolveGap(extraSpace, childCount);
            for (int index = 0; index < childCount; index++) {
                Widget child = getChildren().get(index);
                int childY = paddingTop + resolveCrossOffset(resolveChildAlignItems(child), innerHeight,
                        computeRowCrossSpan(heights[index], margins[index]))
                        + margins[index].getTop();
                if (applyBounds) {
                    child.applyLayoutBounds(cursor + margins[index].getLeft() - scrollState.getHorizontalOffset(),
                            childY - scrollState.getVerticalOffset(),
                            widths[index], heights[index]);
                }
                cursor += computeRowMainSpan(widths[index], margins[index]);
                if (index < childCount - 1) {
                    cursor += dynamicGap;
                }
            }

            result.requiredWidth = paddingLeft + totalWidth + paddingRight;
            result.requiredHeight = paddingTop + tallest + paddingBottom;
            return result;
        } finally {
            UiPerformanceMonitor.getInstance().recordPhase("div.measureRow", System.nanoTime() - phaseStartNanos);
        }
    }

    private LayoutResult measureColumnLayout(int containerWidth, int containerHeight, boolean applyBounds, boolean useMinHeights) {
        long phaseStartNanos = System.nanoTime();
        try {
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
            UiInsets[] margins = new UiInsets[childCount];
            int widest = 0;
            int totalHeight = 0;

            for (int index = 0; index < childCount; index++) {
                Widget child = getChildren().get(index);
                margins[index] = resolveMargin(child);
                widths[index] = resolveColumnCrossSize(child, innerWidth);
                heights[index] = resolveColumnBaseHeight(child, widths[index], innerHeight, false);
                minHeights[index] = resolveChildMinHeight(child, widths[index], innerHeight);
                widest = Math.max(widest, computeColumnCrossSpan(widths[index], margins[index]));
                totalHeight += computeColumnMainSpan(heights[index], margins[index]);
            }
            totalHeight += getMainAxisGap() * Math.max(0, childCount - 1);

            if (innerHeight > 0) {
                if (totalHeight < innerHeight) {
                    totalHeight += distributeGrowth(heights, innerHeight - totalHeight, 0, childCount - 1);
                } else if (totalHeight > innerHeight) {
                    totalHeight -= distributeShrink(heights, minHeights, totalHeight - innerHeight, 0, childCount - 1);
                }
            }

            if (innerWidth > 0 && hasStretchChild(0, childCount - 1)) {
                widest = innerWidth;
                for (int index = 0; index < childCount; index++) {
                    if (resolveChildAlignItems(getChildren().get(index)) == AlignItems.STRETCH) {
                        widths[index] = Math.max(0, innerWidth - margins[index].getLeft() - margins[index].getRight());
                    }
                }
            }

            int extraSpace = Math.max(0, innerHeight - totalHeight);
            int cursor = paddingTop + resolveLeadingOffset(extraSpace, childCount);
            int dynamicGap = resolveGap(extraSpace, childCount);
            for (int index = 0; index < childCount; index++) {
                Widget child = getChildren().get(index);
                int childX = paddingLeft + resolveCrossOffset(resolveChildAlignItems(child), innerWidth,
                        computeColumnCrossSpan(widths[index], margins[index]))
                        + margins[index].getLeft();
                if (applyBounds) {
                    child.applyLayoutBounds(childX - scrollState.getHorizontalOffset(), cursor - scrollState.getVerticalOffset(),
                            widths[index], heights[index]);
                }
                cursor += computeColumnMainSpan(heights[index], margins[index]);
                if (index < childCount - 1) {
                    cursor += dynamicGap;
                }
            }

            result.requiredWidth = paddingLeft + widest + paddingRight;
            result.requiredHeight = paddingTop + totalHeight + paddingBottom;
            return result;
        } finally {
            UiPerformanceMonitor.getInstance().recordPhase("div.measureColumn", System.nanoTime() - phaseStartNanos);
        }
    }

    private LayoutResult measureWrappedRows(int containerWidth, int containerHeight, boolean applyBounds, boolean useMinHeights) {
        long phaseStartNanos = System.nanoTime();
        try {
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
            UiInsets[] margins = new UiInsets[childCount];
            int[] itemMainSpans = new int[childCount];
            for (int index = 0; index < childCount; index++) {
                Widget child = getChildren().get(index);
                margins[index] = resolveMargin(child);
                minWidths[index] = resolveChildMinWidth(child, innerWidth);
                widths[index] = Math.max(minWidths[index], resolveRowBaseWidth(child, innerWidth));
                heights[index] = resolveRowCrossSize(child, widths[index], 0, useMinHeights, false);
                itemMainSpans[index] = computeRowMainSpan(widths[index], margins[index]);
            }

            List<AxisGroup> groups = buildWrappedGroups(itemMainSpans, innerWidth);
            int maxLineWidth = 0;
            int totalHeight = 0;
            for (AxisGroup group : groups) {
                int lineWidth = computeTotal(itemMainSpans, group.start, group.end);
                if (innerWidth > 0) {
                    if (lineWidth < innerWidth) {
                        lineWidth += distributeGrowth(widths, innerWidth - lineWidth, group.start, group.end);
                    } else if (lineWidth > innerWidth) {
                        lineWidth -= distributeShrink(widths, minWidths, lineWidth - innerWidth, group.start, group.end);
                    }
                }
                group.mainSize = computeMainSpanTotalForRows(widths, margins, group.start, group.end);
                group.crossSize = 0;
                for (int index = group.start; index <= group.end; index++) {
                    heights[index] = resolveRowCrossSize(getChildren().get(index), widths[index], 0, useMinHeights, false);
                    group.crossSize = Math.max(group.crossSize, computeRowCrossSpan(heights[index], margins[index]));
                }
                stretchWrappedRowGroupIfNeeded(group, heights, margins);
                maxLineWidth = Math.max(maxLineWidth, group.mainSize);
                totalHeight += group.crossSize;
            }
            totalHeight += getCrossAxisGap() * Math.max(0, groups.size() - 1);

            int extraCrossSpace = Math.max(0, Math.max(0, containerHeight - paddingTop - paddingBottom) - totalHeight);
            if (alignContent == AlignContent.STRETCH && extraCrossSpace > 0) {
                totalHeight += distributeGroupCrossGrowth(groups, extraCrossSpace);
                for (AxisGroup group : groups) {
                    stretchWrappedRowGroupIfNeeded(group, heights, margins);
                }
                extraCrossSpace = 0;
            }
            int cursorY = paddingTop + resolveContentLeadingOffset(extraCrossSpace, groups.size());
            int lineGap = resolveContentGap(extraCrossSpace, groups.size());
            for (AxisGroup group : groups) {
                int count = group.end - group.start + 1;
                int extraSpace = Math.max(0, innerWidth - group.mainSize);
                int cursorX = paddingLeft + resolveLeadingOffset(extraSpace, count);
                int dynamicGap = resolveGap(extraSpace, count);
                for (int index = group.start; index <= group.end; index++) {
                    int childY = cursorY + resolveCrossOffset(resolveChildAlignItems(getChildren().get(index)), group.crossSize,
                            computeRowCrossSpan(heights[index], margins[index]))
                            + margins[index].getTop();
                    if (applyBounds) {
                        getChildren().get(index).applyLayoutBounds(cursorX + margins[index].getLeft() - scrollState.getHorizontalOffset(),
                                childY - scrollState.getVerticalOffset(),
                                widths[index], heights[index]);
                    }
                    cursorX += computeRowMainSpan(widths[index], margins[index]);
                    if (index < group.end) {
                        cursorX += dynamicGap;
                    }
                }
                cursorY += group.crossSize + lineGap;
            }

            result.requiredWidth = paddingLeft + maxLineWidth + paddingRight;
            result.requiredHeight = paddingTop + totalHeight + paddingBottom;
            return result;
        } finally {
            UiPerformanceMonitor.getInstance().recordPhase("div.measureWrappedRows", System.nanoTime() - phaseStartNanos);
        }
    }

    private LayoutResult measureWrappedColumns(int containerWidth, int containerHeight, boolean applyBounds, boolean useMinHeights) {
        long phaseStartNanos = System.nanoTime();
        try {
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
            UiInsets[] margins = new UiInsets[childCount];
            int[] itemMainSpans = new int[childCount];
            for (int index = 0; index < childCount; index++) {
                Widget child = getChildren().get(index);
                margins[index] = resolveMargin(child);
                widths[index] = resolveColumnCrossSize(child, innerWidth);
                heights[index] = resolveColumnBaseHeight(child, widths[index], innerHeight, false);
                minHeights[index] = resolveChildMinHeight(child, widths[index], innerHeight);
                itemMainSpans[index] = computeColumnMainSpan(heights[index], margins[index]);
            }

            List<AxisGroup> groups = buildWrappedGroups(itemMainSpans, innerHeight);
            int totalWidth = 0;
            int tallestColumn = 0;
            for (AxisGroup group : groups) {
                int columnHeight = computeTotal(itemMainSpans, group.start, group.end);
                if (innerHeight > 0) {
                    if (columnHeight < innerHeight) {
                        columnHeight += distributeGrowth(heights, innerHeight - columnHeight, group.start, group.end);
                    } else if (columnHeight > innerHeight) {
                        columnHeight -= distributeShrink(heights, minHeights, columnHeight - innerHeight, group.start, group.end);
                    }
                }
                group.mainSize = computeMainSpanTotalForColumns(heights, margins, group.start, group.end);
                group.crossSize = 0;
                for (int index = group.start; index <= group.end; index++) {
                    group.crossSize = Math.max(group.crossSize, computeColumnCrossSpan(widths[index], margins[index]));
                }
                stretchWrappedColumnGroupIfNeeded(group, widths, margins);
                totalWidth += group.crossSize;
                tallestColumn = Math.max(tallestColumn, group.mainSize);
            }
            totalWidth += getCrossAxisGap() * Math.max(0, groups.size() - 1);

            int extraCrossSpace = Math.max(0, innerWidth - totalWidth);
            if (alignContent == AlignContent.STRETCH && extraCrossSpace > 0) {
                totalWidth += distributeGroupCrossGrowth(groups, extraCrossSpace);
                for (AxisGroup group : groups) {
                    stretchWrappedColumnGroupIfNeeded(group, widths, margins);
                }
                extraCrossSpace = 0;
            }
            int cursorX = paddingLeft + resolveContentLeadingOffset(extraCrossSpace, groups.size());
            int columnGap = resolveContentGap(extraCrossSpace, groups.size());
            for (AxisGroup group : groups) {
                int count = group.end - group.start + 1;
                int extraSpace = Math.max(0, innerHeight - group.mainSize);
                int cursorY = paddingTop + resolveLeadingOffset(extraSpace, count);
                int dynamicGap = resolveGap(extraSpace, count);
                for (int index = group.start; index <= group.end; index++) {
                    int childX = cursorX + resolveCrossOffset(resolveChildAlignItems(getChildren().get(index)), group.crossSize,
                            computeColumnCrossSpan(widths[index], margins[index]))
                            + margins[index].getLeft();
                    if (applyBounds) {
                        getChildren().get(index).applyLayoutBounds(childX - scrollState.getHorizontalOffset(),
                                cursorY + margins[index].getTop() - scrollState.getVerticalOffset(),
                                widths[index], heights[index]);
                    }
                    cursorY += computeColumnMainSpan(heights[index], margins[index]);
                    if (index < group.end) {
                        cursorY += dynamicGap;
                    }
                }
                cursorX += group.crossSize + columnGap;
            }

            result.requiredWidth = paddingLeft + totalWidth + paddingRight;
            result.requiredHeight = paddingTop + tallestColumn + paddingBottom;
            return result;
        } finally {
            UiPerformanceMonitor.getInstance().recordPhase("div.measureWrappedColumns", System.nanoTime() - phaseStartNanos);
        }
    }

    private List<AxisGroup> buildWrappedGroups(int[] sizes, int availableMainSize) {
        List<AxisGroup> groups = new ArrayList<AxisGroup>();
        if (sizes.length == 0) {
            return groups;
        }

        int start = 0;
        int currentSize = 0;
        for (int index = 0; index < sizes.length; index++) {
            int candidate = currentSize == 0 ? sizes[index] : currentSize + getMainAxisGap() + sizes[index];
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

    private UiInsets resolveMargin(Widget child) {
        UiLayoutSpec layoutSpec = child.getLayoutSpec();
        return layoutSpec == null ? UiInsets.ZERO : layoutSpec.getMargin();
    }

    private int computeRowMainSpan(int width, UiInsets margin) {
        return margin.getLeft() + width + margin.getRight();
    }

    private int computeRowCrossSpan(int height, UiInsets margin) {
        return margin.getTop() + height + margin.getBottom();
    }

    private int computeColumnMainSpan(int height, UiInsets margin) {
        return margin.getTop() + height + margin.getBottom();
    }

    private int computeColumnCrossSpan(int width, UiInsets margin) {
        return margin.getLeft() + width + margin.getRight();
    }

    private int computeMainSpanTotalForRows(int[] widths, UiInsets[] margins, int start, int end) {
        int total = 0;
        for (int index = start; index <= end; index++) {
            total += computeRowMainSpan(widths[index], margins[index]);
        }
        total += getMainAxisGap() * Math.max(0, end - start);
        return total;
    }

    private int computeMainSpanTotalForColumns(int[] heights, UiInsets[] margins, int start, int end) {
        int total = 0;
        for (int index = start; index <= end; index++) {
            total += computeColumnMainSpan(heights[index], margins[index]);
        }
        total += getMainAxisGap() * Math.max(0, end - start);
        return total;
    }

    private int resolveRowBaseWidth(Widget child, int innerWidth) {
        UiLayoutSpec layoutSpec = child.getLayoutSpec();
        UiLength flexBasis = layoutSpec == null ? UiLength.auto() : layoutSpec.getFlexBasis();
        if (flexBasis.getType() != UiLength.Type.AUTO) {
            return clampToBounds(resolveLengthValue(flexBasis, innerWidth, measureIntrinsic(child).getWidth()),
                    layoutSpec == null ? 0 : layoutSpec.getMinWidth(),
                    layoutSpec == null ? Integer.MAX_VALUE : layoutSpec.getMaxWidth());
        }
        return resolveConfiguredWidth(child, innerWidth, measureIntrinsic(child).getWidth());
    }

    private int resolveRowCrossSize(Widget child, int childWidth, int innerHeight, boolean useMinHeight, boolean actualLayout) {
        int fallback = useMinHeight ? child.getMinContentHeightForWidth(childWidth) : measureForWidth(child, childWidth).getHeight();
        return resolveConfiguredHeight(child, childWidth, innerHeight, fallback);
    }

    private int resolveColumnCrossSize(Widget child, int innerWidth) {
        boolean hasConfiguredWidth = hasConfiguredWidth(child);
        int preferredWidth = resolveConfiguredWidth(child, innerWidth, measureIntrinsic(child).getWidth());
        if (alignItems == AlignItems.STRETCH && innerWidth > 0 && !hasConfiguredWidth) {
            preferredWidth = innerWidth;
        }
        return Math.max(resolveChildMinWidth(child, innerWidth), preferredWidth);
    }

    private int resolveColumnBaseHeight(Widget child, int childWidth, int innerHeight, boolean useMinHeight) {
        UiLayoutSpec layoutSpec = child.getLayoutSpec();
        int fallback = useMinHeight ? child.getMinContentHeightForWidth(childWidth) : measureForWidth(child, childWidth).getHeight();
        UiLength flexBasis = layoutSpec == null ? UiLength.auto() : layoutSpec.getFlexBasis();
        if (flexBasis.getType() != UiLength.Type.AUTO) {
            int basisHeight = clampToBounds(resolveLengthValue(flexBasis, innerHeight, fallback),
                    layoutSpec == null ? 0 : layoutSpec.getMinHeight(),
                    layoutSpec == null ? Integer.MAX_VALUE : layoutSpec.getMaxHeight());
            if (!useMinHeight) {
                fallback = basisHeight;
            } else {
                fallback = Math.max(fallback, basisHeight);
            }
        }
        return resolveConfiguredHeight(child, childWidth, innerHeight, fallback);
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
            minHeight = resolveConfiguredHeight(child, childWidth, availableHeight, minHeight);
        }
        if (layoutSpec != null) {
            minHeight = Math.max(minHeight, layoutSpec.getMinHeight());
            minHeight = clampToBounds(minHeight, layoutSpec.getMinHeight(), layoutSpec.getMaxHeight());
        }
        return Math.max(0, minHeight);
    }

    private int resolveConfiguredWidth(Widget child, int availableWidth, int fallback) {
        UiLayoutSpec layoutSpec = child.getLayoutSpec();
        UiLength width = resolveWidthLength(child, layoutSpec);
        int resolvedWidth = fallback;
        if (width.getType() != UiLength.Type.AUTO) {
            resolvedWidth = resolveLengthValue(width, availableWidth, fallback);
        }
        return clampToBounds(resolvedWidth, layoutSpec == null ? 0 : layoutSpec.getMinWidth(),
                layoutSpec == null ? Integer.MAX_VALUE : layoutSpec.getMaxWidth());
    }

    private int resolveConfiguredHeight(Widget child, int childWidth, int availableHeight, int fallback) {
        UiLayoutSpec layoutSpec = child.getLayoutSpec();
        UiLength height = resolveHeightLength(child, layoutSpec);
        int resolvedHeight = fallback;
        if (height.getType() != UiLength.Type.AUTO) {
            resolvedHeight = resolveLengthValue(height, availableHeight, fallback);
        }
        return clampToBounds(resolvedHeight, layoutSpec == null ? 0 : layoutSpec.getMinHeight(),
                layoutSpec == null ? Integer.MAX_VALUE : layoutSpec.getMaxHeight());
    }

    private UiLength resolveWidthLength(Widget child, UiLayoutSpec layoutSpec) {
        return layoutSpec != null ? layoutSpec.getWidth() : UiLength.auto();
    }

    private UiLength resolveHeightLength(Widget child, UiLayoutSpec layoutSpec) {
        return layoutSpec != null ? layoutSpec.getHeight() : UiLength.auto();
    }

    private boolean hasConfiguredWidth(Widget child) {
        UiLayoutSpec layoutSpec = child.getLayoutSpec();
        return layoutSpec != null && layoutSpec.getWidth().getType() != UiLength.Type.AUTO;
    }

    private boolean hasConfiguredHeight(Widget child) {
        UiLayoutSpec layoutSpec = child.getLayoutSpec();
        return layoutSpec != null && layoutSpec.getHeight().getType() != UiLength.Type.AUTO;
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

    private UiMeasureResult measureIntrinsic(Widget child) {
        return child.measure(UiConstraints.unbounded());
    }

    private UiMeasureResult measureForWidth(Widget child, int width) {
        return child.measure(UiConstraints.fixedWidth(Math.max(0, width)));
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

        float totalGrowWeight = 0.0F;
        int growableCount = 0;
        for (int index = start; index <= end; index++) {
            float growWeight = resolveGrowWeight(getChildren().get(index));
            if (growWeight > 0.0F) {
                totalGrowWeight += growWeight;
                growableCount++;
            }
        }
        if (growableCount <= 0 || totalGrowWeight <= 0.0F) {
            return 0;
        }

        int applied = 0;
        int growIndex = 0;
        for (int index = start; index <= end; index++) {
            float growWeight = resolveGrowWeight(getChildren().get(index));
            if (growWeight <= 0.0F) {
                continue;
            }
            growIndex++;
            int addition = growIndex == growableCount ? extra - applied
                    : Math.round((extra * growWeight) / totalGrowWeight);
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

        float totalShrinkWeight = 0.0F;
        for (int index = start; index <= end; index++) {
            totalShrinkWeight += resolveShrinkWeight(getChildren().get(index), sizes[index], minSizes[index]);
        }
        if (totalShrinkWeight <= 0.0F) {
            return 0;
        }

        int removed = 0;
        int remainingShrinkable = 0;
        for (int index = start; index <= end; index++) {
            if (resolveShrinkWeight(getChildren().get(index), sizes[index], minSizes[index]) > 0.0F) {
                remainingShrinkable++;
            }
        }

        int shrinkIndex = 0;
        for (int index = start; index <= end; index++) {
            int availableShrink = Math.max(0, sizes[index] - minSizes[index]);
            float shrinkWeight = resolveShrinkWeight(getChildren().get(index), sizes[index], minSizes[index]);
            if (availableShrink <= 0 || shrinkWeight <= 0.0F) {
                continue;
            }
            shrinkIndex++;
            int cut = shrinkIndex == remainingShrinkable
                    ? overflow - removed
                    : Math.round((overflow * shrinkWeight) / totalShrinkWeight);
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
        total += getMainAxisGap() * Math.max(0, end - start);
        return total;
    }

    private float resolveGrowWeight(Widget child) {
        UiLayoutSpec layoutSpec = child.getLayoutSpec();
        return layoutSpec == null ? 0.0F : Math.max(0.0F, layoutSpec.getGrow());
    }

    private float resolveShrinkWeight(Widget child, int size, int minSize) {
        int availableShrink = Math.max(0, size - minSize);
        if (availableShrink <= 0) {
            return 0.0F;
        }
        UiLayoutSpec layoutSpec = child.getLayoutSpec();
        float shrinkFactor = layoutSpec == null ? 1.0F : layoutSpec.getShrink();
        return availableShrink * shrinkFactor;
    }

    private boolean hasStretchChild(int start, int end) {
        for (int index = start; index <= end; index++) {
            if (resolveChildAlignItems(getChildren().get(index)) == AlignItems.STRETCH) {
                return true;
            }
        }
        return false;
    }

    private void stretchWrappedRowGroupIfNeeded(AxisGroup group, int[] heights, UiInsets[] margins) {
        if (!hasStretchChild(group.start, group.end)) {
            return;
        }
        for (int index = group.start; index <= group.end; index++) {
            if (resolveChildAlignItems(getChildren().get(index)) == AlignItems.STRETCH) {
                heights[index] = Math.max(0, group.crossSize - margins[index].getTop() - margins[index].getBottom());
            }
        }
    }

    private void stretchWrappedColumnGroupIfNeeded(AxisGroup group, int[] widths, UiInsets[] margins) {
        if (!hasStretchChild(group.start, group.end)) {
            return;
        }
        for (int index = group.start; index <= group.end; index++) {
            if (resolveChildAlignItems(getChildren().get(index)) == AlignItems.STRETCH) {
                widths[index] = Math.max(0, group.crossSize - margins[index].getLeft() - margins[index].getRight());
            }
        }
    }

    private int distributeGroupCrossGrowth(List<AxisGroup> groups, int extraCrossSpace) {
        if (extraCrossSpace <= 0 || groups.isEmpty()) {
            return 0;
        }
        int applied = 0;
        for (int index = 0; index < groups.size(); index++) {
            AxisGroup group = groups.get(index);
            int addition = index == groups.size() - 1 ? extraCrossSpace - applied : extraCrossSpace / groups.size();
            addition = Math.max(0, addition);
            group.crossSize += addition;
            applied += addition;
        }
        return applied;
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
            return getMainAxisGap();
        }
        return getMainAxisGap() + extraSpace / (childCount - 1);
    }

    private int resolveContentLeadingOffset(int extraSpace, int groupCount) {
        if (extraSpace <= 0) {
            return 0;
        }
        switch (alignContent) {
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

    private int resolveContentGap(int extraSpace, int groupCount) {
        if (alignContent != AlignContent.SPACE_BETWEEN || groupCount <= 1 || extraSpace <= 0) {
            return getCrossAxisGap();
        }
        return getCrossAxisGap() + extraSpace / (groupCount - 1);
    }

    private int getMainAxisGap() {
        return direction == Direction.ROW ? columnGap : rowGap;
    }

    private int getCrossAxisGap() {
        return direction == Direction.ROW ? rowGap : columnGap;
    }

    private AlignItems resolveChildAlignItems(Widget child) {
        UiLayoutSpec layoutSpec = child.getLayoutSpec();
        if (layoutSpec == null) {
            return alignItems;
        }
        UiAlignSelf alignSelf = layoutSpec.getAlignSelf();
        if (alignSelf == null || alignSelf == UiAlignSelf.AUTO) {
            return alignItems;
        }
        switch (alignSelf) {
            case START:
                return AlignItems.START;
            case CENTER:
                return AlignItems.CENTER;
            case END:
                return AlignItems.END;
            case STRETCH:
                return AlignItems.STRETCH;
            default:
                return alignItems;
        }
    }

    private int resolveCrossOffset(AlignItems resolvedAlignItems, int availableCrossSize, int childCrossSize) {
        if (availableCrossSize <= 0) {
            return 0;
        }
        switch (resolvedAlignItems) {
            case CENTER:
                return (availableCrossSize - childCrossSize) / 2;
            case END:
                return availableCrossSize - childCrossSize;
            default:
                return 0;
        }
    }

    private void prepareOverflowLayout(boolean useMinHeights) {
        long phaseStartNanos = System.nanoTime();
        try {
            int baseViewportWidth = Math.max(0, getWidth() - paddingLeft - paddingRight);
            int baseViewportHeight = Math.max(0, getHeight() - paddingTop - paddingBottom);
            OverflowViewportLayout.Result overflowLayout = OverflowViewportLayout.compute(baseViewportWidth, baseViewportHeight,
                    overflowX == Overflow.AUTO, overflowY == Overflow.AUTO, new OverflowViewportLayout.ContentMeasurer() {
                        @Override
                        public OverflowViewportLayout.ContentSize measure(int viewportWidth, int viewportHeight) {
                            LayoutResult layout = measureLayoutCached(viewportWidth + paddingLeft + paddingRight,
                                    viewportHeight + paddingTop + paddingBottom, useMinHeights);
                            return new OverflowViewportLayout.ContentSize(
                                    Math.max(0, layout.requiredWidth - paddingLeft - paddingRight),
                                    Math.max(0, layout.requiredHeight - paddingTop - paddingBottom));
                        }
                    });
            scrollState.updateState(overflowLayout.viewportWidth, overflowLayout.viewportHeight, overflowLayout.contentWidth,
                    overflowLayout.contentHeight, overflowX == Overflow.AUTO, overflowY == Overflow.AUTO);
        } finally {
            UiPerformanceMonitor.getInstance().recordPhase("div.prepareOverflow", System.nanoTime() - phaseStartNanos);
        }
    }

    private void ensureLayout(boolean useMinHeights) {
        int currentLayoutVersion = getLayoutVersion();
        int currentTextMeasureEpoch = textMeasureService.getEpoch();
        int currentWidth = getWidth();
        int currentHeight = getHeight();
        int currentHorizontalOffset = scrollState.getHorizontalOffset();
        int currentVerticalOffset = scrollState.getVerticalOffset();
        if (cachedLayoutVersion == currentLayoutVersion
                && cachedLayoutWidth == currentWidth
                && cachedLayoutHeight == currentHeight
                && cachedHorizontalScrollOffset == currentHorizontalOffset
                && cachedVerticalScrollOffset == currentVerticalOffset
                && cachedLayoutTextMeasureEpoch == currentTextMeasureEpoch
                && cachedUseMinHeights == useMinHeights) {
            return;
        }

        prepareOverflowLayout(useMinHeights);
        applyOverflowLayout(useMinHeights);

        cachedLayoutVersion = currentLayoutVersion;
        cachedLayoutWidth = currentWidth;
        cachedLayoutHeight = currentHeight;
        cachedHorizontalScrollOffset = scrollState.getHorizontalOffset();
        cachedVerticalScrollOffset = scrollState.getVerticalOffset();
        cachedLayoutTextMeasureEpoch = currentTextMeasureEpoch;
        cachedUseMinHeights = useMinHeights;
    }

    private LayoutResult measureLayoutCached(int containerWidth, int containerHeight, boolean useMinHeights) {
        ensurePureMeasureCacheValid();
        MeasureCacheKey key = new MeasureCacheKey(containerWidth, containerHeight, useMinHeights);
        LayoutResult cached = pureMeasureCache.get(key);
        if (cached != null) {
            return copyLayoutResult(cached);
        }

        LayoutResult measured = measureLayout(containerWidth, containerHeight, false, useMinHeights);
        LayoutResult snapshot = copyLayoutResult(measured);
        pureMeasureCache.put(key, snapshot);
        return copyLayoutResult(snapshot);
    }

    private void ensurePureMeasureCacheValid() {
        int currentLayoutVersion = getLayoutVersion();
        int currentTextMeasureEpoch = textMeasureService.getEpoch();
        if (cachedPureMeasureVersion == currentLayoutVersion
                && cachedPureMeasureTextMeasureEpoch == currentTextMeasureEpoch) {
            return;
        }
        pureMeasureCache.clear();
        cachedPureMeasureVersion = currentLayoutVersion;
        cachedPureMeasureTextMeasureEpoch = currentTextMeasureEpoch;
        cachedPreferredWidthVersion = -1;
        cachedPreferredWidth = -1;
        cachedPreferredWidthTextMeasureEpoch = -1;
        cachedMinContentWidthVersion = -1;
        cachedMinContentWidth = -1;
        cachedMinContentWidthTextMeasureEpoch = -1;
    }

    private LayoutResult copyLayoutResult(LayoutResult source) {
        LayoutResult copy = new LayoutResult();
        copy.requiredWidth = source.requiredWidth;
        copy.requiredHeight = source.requiredHeight;
        return copy;
    }

    private void applyOverflowLayout(boolean useMinHeights) {
        long phaseStartNanos = System.nanoTime();
        try {
            measureLayout(getVisibleContentWidth() + paddingLeft + paddingRight, getVisibleContentHeight() + paddingTop + paddingBottom,
                    true, useMinHeights);
        } finally {
            UiPerformanceMonitor.getInstance().recordPhase("div.applyOverflow", System.nanoTime() - phaseStartNanos);
        }
    }

    private void drawScrollbars(UiRenderContext context) {
        OverflowScrollState.ScrollbarMetrics verticalMetrics = getVerticalScrollbarMetrics();
        if (verticalMetrics != null) {
            drawScrollbar(context, verticalMetrics, scrollState.isHoveredVerticalScrollbar(),
                    scrollState.isHoveredVerticalThumb(), scrollState.isDraggingVerticalThumb());
        }

        OverflowScrollState.ScrollbarMetrics horizontalMetrics = getHorizontalScrollbarMetrics();
        if (horizontalMetrics != null) {
            drawScrollbar(context, horizontalMetrics, scrollState.isHoveredHorizontalScrollbar(),
                    scrollState.isHoveredHorizontalThumb(), scrollState.isDraggingHorizontalThumb());
        }
    }

    private void drawScrollbar(UiRenderContext context, OverflowScrollState.ScrollbarMetrics metrics,
            boolean hoveredTrack, boolean hoveredThumb, boolean draggingThumb) {
        int trackColor = hoveredTrack ? scrollbarStyle.hoveredTrackColor : scrollbarStyle.trackColor;
        int thumbColor = draggingThumb ? scrollbarStyle.draggingThumbColor
                : (hoveredThumb ? scrollbarStyle.hoveredThumbColor : scrollbarStyle.thumbColor);
        context.fillRect(metrics.trackLeft, metrics.trackTop, metrics.trackRight, metrics.trackBottom, trackColor);
        context.fillRect(metrics.thumbLeft, metrics.thumbTop, metrics.thumbRight, metrics.thumbBottom, thumbColor);
    }

    private OverflowScrollState.ScrollbarMetrics getVerticalScrollbarMetrics() {
        if (overflowY != Overflow.AUTO) {
            return null;
        }
        return scrollState.getVerticalScrollbarMetrics(getAbsoluteX() + paddingLeft, getAbsoluteY() + paddingTop);
    }

    private OverflowScrollState.ScrollbarMetrics getHorizontalScrollbarMetrics() {
        if (overflowX != Overflow.AUTO) {
            return null;
        }
        return scrollState.getHorizontalScrollbarMetrics(getAbsoluteX() + paddingLeft, getAbsoluteY() + paddingTop);
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

    private static class LayoutResult {
        private int requiredWidth;
        private int requiredHeight;
    }

    private static final class MeasureCacheKey {
        private final int containerWidth;
        private final int containerHeight;
        private final boolean useMinHeights;

        private MeasureCacheKey(int containerWidth, int containerHeight, boolean useMinHeights) {
            this.containerWidth = containerWidth;
            this.containerHeight = containerHeight;
            this.useMinHeights = useMinHeights;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof MeasureCacheKey)) {
                return false;
            }
            MeasureCacheKey other = (MeasureCacheKey) obj;
            return containerWidth == other.containerWidth
                    && containerHeight == other.containerHeight
                    && useMinHeights == other.useMinHeights;
        }

        @Override
        public int hashCode() {
            int result = containerWidth;
            result = 31 * result + containerHeight;
            result = 31 * result + (useMinHeights ? 1 : 0);
            return result;
        }
    }
}
