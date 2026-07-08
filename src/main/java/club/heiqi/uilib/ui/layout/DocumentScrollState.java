package club.heiqi.uilib.ui.layout;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import club.heiqi.uilib.ui.animation.DocumentAnimationTimeline;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.BoxContext;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.RootEntry;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.StackingContextResolver;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.TraversalEntry;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.VisualScene;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.base.values.UiTransform;

/**
 * HTML-like 文档滚动状态。
 *
 * <p>该状态只保存元素级滚动偏移和可滚范围，布局盒本身仍表达未滚动的文档坐标。</p>
 */
public final class DocumentScrollState {

    private static final int DEFAULT_SCROLL_STEP = 36;
    private static final long TRANSIENT_SCROLLBAR_VISIBLE_NANOS = 900_000_000L;
    private static final StackingContextResolver STATIC_STACKING_CONTEXT_RESOLVER =
            new StackingContextResolver() {
                @Override
                public boolean createsStackingContext(DocumentLayoutBox box) {
                    return DocumentEffectChain.resolve(box).createsStackingContext();
                }
            };

    private final Map<ElementNode, ScrollEntry> entries = new HashMap<ElementNode, ScrollEntry>();
    private int scrollStep = DEFAULT_SCROLL_STEP;
    private int version;
    private ScrollbarDrag activeScrollbarDrag;
    private ElementNode lastScrolledElement;

    /**
     * 设置滚轮每步滚动距离。
     *
     * @param scrollStep 滚动步长
     */
    public void setScrollStep(int scrollStep) {
        this.scrollStep = Math.max(8, scrollStep);
    }

    /**
     * 返回当前滚轮每步滚动距离。
     *
     * @return 滚动步长
     */
    public int getScrollStep() {
        return scrollStep;
    }

    /**
     * 返回滚动状态版本。
     *
     * @return 状态版本
     */
    public int getVersion() {
        return version;
    }

    /**
     * 取出最近一次由默认滚动链路改变偏移的元素。
     *
     * @return 最近滚动的元素；没有待分发滚动事件时返回 null
     */
    public ElementNode consumeLastScrolledElement() {
        ElementNode element = lastScrolledElement;
        lastScrolledElement = null;
        return element;
    }

    /**
     * 返回元素横向滚动偏移。
     *
     * @param element 元素
     * @return 横向滚动偏移
     */
    public int getScrollLeft(ElementNode element) {
        ScrollEntry entry = entries.get(element);
        return entry == null ? 0 : entry.horizontalOffset;
    }

    /**
     * 返回元素纵向滚动偏移。
     *
     * @param element 元素
     * @return 纵向滚动偏移
     */
    public int getScrollTop(ElementNode element) {
        ScrollEntry entry = entries.get(element);
        return entry == null ? 0 : entry.verticalOffset;
    }

    /**
     * 返回元素最大横向滚动偏移。
     *
     * @param element 元素
     * @return 最大横向滚动偏移
     */
    public int getMaxScrollLeft(ElementNode element) {
        ScrollEntry entry = entries.get(element);
        return entry == null ? 0 : entry.maxHorizontalOffset;
    }

    /**
     * 返回元素最大纵向滚动偏移。
     *
     * @param element 元素
     * @return 最大纵向滚动偏移
     */
    public int getMaxScrollTop(ElementNode element) {
        ScrollEntry entry = entries.get(element);
        return entry == null ? 0 : entry.maxVerticalOffset;
    }

    /**
     * 返回元素滚动内容总宽度。
     *
     * @param element 元素
     * @return 内容总宽度；未参与当前布局时返回 0
     */
    public int getScrollWidth(ElementNode element) {
        ScrollEntry entry = entries.get(element);
        return entry == null ? 0 : entry.contentWidth;
    }

    /**
     * 返回元素滚动内容总高度。
     *
     * @param element 元素
     * @return 内容总高度；未参与当前布局时返回 0
     */
    public int getScrollHeight(ElementNode element) {
        ScrollEntry entry = entries.get(element);
        return entry == null ? 0 : entry.contentHeight;
    }

    /**
     * 根据最新布局盒树刷新可滚范围，并移除已不存在元素的滚动状态。
     *
     * @param rootBox 根布局盒
     */
    public void updateFromLayout(DocumentLayoutBox rootBox) {
        updateFromLayout(rootBox, Collections.<DocumentLayoutBox>emptyList());
    }

    /**
     * 根据最新普通布局盒树和顶层盒树刷新可滚范围，并移除已不存在元素的滚动状态。
     *
     * @param rootBox 根布局盒
     * @param topLayerBoxes 顶层布局盒；后面的盒位于更上层
     */
    public void updateFromLayout(DocumentLayoutBox rootBox, List<DocumentLayoutBox> topLayerBoxes) {
        updateFromLayout(rootBox, topLayerBoxes, 0L, null);
    }

    /**
     * 根据最新普通布局盒树、顶层盒树和动画运行态刷新可滚范围，并移除已不存在元素的滚动状态。
     *
     * @param rootBox 根布局盒
     * @param topLayerBoxes 顶层布局盒；后面的盒位于更上层
     * @param currentTimeNanos 当前动画时间
     * @param animationTimeline 动画时间线；为 null 时只使用 computed style
     */
    public void updateFromLayout(DocumentLayoutBox rootBox, List<DocumentLayoutBox> topLayerBoxes,
            long currentTimeNanos, DocumentAnimationTimeline animationTimeline) {
        Objects.requireNonNull(rootBox, "rootBox");
        Set<ElementNode> activeElements = new HashSet<ElementNode>();
        collectScrollableMetrics(rootBox, activeElements, currentTimeNanos, animationTimeline);
        if (topLayerBoxes != null) {
            for (DocumentLayoutBox topLayerBox : topLayerBoxes) {
                if (topLayerBox != null) {
                    collectScrollableMetrics(topLayerBox, activeElements, currentTimeNanos, animationTimeline);
                }
            }
        }
        Iterator<Map.Entry<ElementNode, ScrollEntry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ElementNode, ScrollEntry> mapEntry = iterator.next();
            if (activeElements.contains(mapEntry.getKey())) {
                continue;
            }
            ScrollEntry entry = mapEntry.getValue();
            iterator.remove();
            if (activeScrollbarDrag != null && activeScrollbarDrag.element == mapEntry.getKey()) {
                activeScrollbarDrag = null;
            }
            if (entry.horizontalOffset != 0 || entry.verticalOffset != 0) {
                version++;
            }
        }
        if (activeScrollbarDrag != null) {
            ScrollEntry entry = entries.get(activeScrollbarDrag.element);
            if (entry == null || (activeScrollbarDrag.vertical && entry.maxVerticalOffset <= 0)
                    || (!activeScrollbarDrag.vertical && entry.maxHorizontalOffset <= 0)) {
                activeScrollbarDrag = null;
            }
        }
    }

    /**
     * 设置指定元素的滚动偏移，偏移会被限制在当前可滚范围内。
     *
     * @param element 元素
     * @param horizontalOffset 横向偏移
     * @param verticalOffset 纵向偏移
     * @return 滚动偏移是否发生变化
     */
    public boolean setScrollOffset(ElementNode element, int horizontalOffset, int verticalOffset) {
        ScrollEntry entry = entries.get(element);
        if (entry == null) {
            return false;
        }
        return updateOffsets(entry, horizontalOffset, verticalOffset);
    }

    /**
     * 按鼠标位置和滚轮增量滚动命中的最深层可滚元素。
     *
     * @param rootBox 根布局盒
     * @param mouseX 文档局部鼠标 X
     * @param mouseY 文档局部鼠标 Y
     * @param wheelDelta 滚轮增量
     * @return 是否消费滚轮事件
     */
    public boolean handleWheel(DocumentLayoutBox rootBox, int mouseX, int mouseY, int wheelDelta) {
        return handleWheel(rootBox, mouseX, mouseY, wheelDelta, System.nanoTime());
    }

    /**
     * 按鼠标位置和滚轮增量滚动命中的最深层可滚元素，并记录滚动发生时间。
     *
     * @param rootBox 根布局盒
     * @param mouseX 文档局部鼠标 X
     * @param mouseY 文档局部鼠标 Y
     * @param wheelDelta 滚轮增量
     * @param eventTimeNanos 滚轮事件时间戳
     * @return 是否消费滚轮事件
     */
    public boolean handleWheel(DocumentLayoutBox rootBox, int mouseX, int mouseY, int wheelDelta,
            long eventTimeNanos) {
        return handleWheel(rootBox, Collections.<DocumentLayoutBox>emptyList(), mouseX, mouseY, wheelDelta,
                eventTimeNanos);
    }

    /**
     * 按鼠标位置和滚轮增量滚动命中的最深层可滚元素，顶层盒优先于普通文档树。
     *
     * @param rootBox 根布局盒
     * @param topLayerBoxes 顶层布局盒；后面的盒位于更上层
     * @param mouseX 文档局部鼠标 X
     * @param mouseY 文档局部鼠标 Y
     * @param wheelDelta 滚轮增量
     * @param eventTimeNanos 滚轮事件时间戳
     * @return 是否消费滚轮事件
     */
    public boolean handleWheel(DocumentLayoutBox rootBox, List<DocumentLayoutBox> topLayerBoxes, int mouseX,
            int mouseY, int wheelDelta, long eventTimeNanos) {
        return handleWheel(rootBox, topLayerBoxes, mouseX, mouseY, wheelDelta, eventTimeNanos, 0L, null);
    }

    /**
     * 按鼠标位置和滚轮增量滚动命中的最深层可滚元素，并应用动画运行态 transform。
     *
     * @param rootBox 根布局盒
     * @param topLayerBoxes 顶层布局盒；后面的盒位于更上层
     * @param mouseX 文档局部鼠标 X
     * @param mouseY 文档局部鼠标 Y
     * @param wheelDelta 滚轮增量
     * @param eventTimeNanos 滚轮事件时间戳
     * @param currentTimeNanos 当前动画时间
     * @param animationTimeline 动画时间线；为 null 时只使用 computed style
     * @return 是否消费滚轮事件
     */
    public boolean handleWheel(DocumentLayoutBox rootBox, List<DocumentLayoutBox> topLayerBoxes, int mouseX,
            int mouseY, int wheelDelta, long eventTimeNanos, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline) {
        Objects.requireNonNull(rootBox, "rootBox");
        if (wheelDelta == 0) {
            return false;
        }
        updateFromLayout(rootBox, topLayerBoxes, currentTimeNanos, animationTimeline);
        BoxContext target = findScrollableBoxAt(DocumentVisualTraversal.resolveVisualScene(rootBox, topLayerBoxes, this,
                currentTimeNanos, animationTimeline), mouseX, mouseY, currentTimeNanos, animationTimeline);
        if (target == null) {
            return false;
        }
        ScrollEntry entry = entries.get(target.getBox().getElement());
        if (entry == null) {
            return false;
        }

        int steps = Math.max(1, Math.round(Math.abs(wheelDelta) / 120.0F));
        int delta = scrollStep * steps;
        int nextHorizontalOffset = entry.horizontalOffset;
        int nextVerticalOffset = entry.verticalOffset;
        if (entry.maxVerticalOffset > 0) {
            nextVerticalOffset += wheelDelta > 0 ? -delta : delta;
        } else if (entry.maxHorizontalOffset > 0) {
            nextHorizontalOffset += wheelDelta > 0 ? -delta : delta;
        }
        boolean changed = updateOffsets(entry, nextHorizontalOffset, nextVerticalOffset);
        if (changed) {
            lastScrolledElement = target.getBox().getElement();
            markScrollbarInteraction(entry, eventTimeNanos);
        }
        return changed;
    }

    /**
     * 开始拖拽或点击 HTML-like 滚动条。
     *
     * @param rootBox 根布局盒
     * @param mouseX 文档局部鼠标 X
     * @param mouseY 文档局部鼠标 Y
     * @return 是否命中并消费滚动条操作
     */
    public boolean beginScrollbarDrag(DocumentLayoutBox rootBox, int mouseX, int mouseY) {
        return beginScrollbarDrag(rootBox, mouseX, mouseY, System.nanoTime());
    }

    /**
     * 开始拖拽或点击 HTML-like 滚动条，并记录交互时间。
     *
     * @param rootBox 根布局盒
     * @param mouseX 文档局部鼠标 X
     * @param mouseY 文档局部鼠标 Y
     * @param eventTimeNanos 事件时间戳
     * @return 是否命中并消费滚动条操作
     */
    public boolean beginScrollbarDrag(DocumentLayoutBox rootBox, int mouseX, int mouseY, long eventTimeNanos) {
        return beginScrollbarDrag(rootBox, Collections.<DocumentLayoutBox>emptyList(), mouseX, mouseY,
                eventTimeNanos);
    }

    /**
     * 开始拖拽或点击 HTML-like 滚动条，顶层盒优先于普通文档树。
     *
     * @param rootBox 根布局盒
     * @param topLayerBoxes 顶层布局盒；后面的盒位于更上层
     * @param mouseX 文档局部鼠标 X
     * @param mouseY 文档局部鼠标 Y
     * @param eventTimeNanos 事件时间戳
     * @return 是否命中并消费滚动条操作
     */
    public boolean beginScrollbarDrag(DocumentLayoutBox rootBox, List<DocumentLayoutBox> topLayerBoxes, int mouseX,
            int mouseY, long eventTimeNanos) {
        return beginScrollbarDrag(rootBox, topLayerBoxes, mouseX, mouseY, eventTimeNanos, 0L, null);
    }

    /**
     * 开始拖拽或点击 HTML-like 滚动条，并应用动画运行态 transform。
     *
     * @param rootBox 根布局盒
     * @param topLayerBoxes 顶层布局盒；后面的盒位于更上层
     * @param mouseX 文档局部鼠标 X
     * @param mouseY 文档局部鼠标 Y
     * @param eventTimeNanos 事件时间戳
     * @param currentTimeNanos 当前动画时间
     * @param animationTimeline 动画时间线；为 null 时只使用 computed style
     * @return 是否命中并消费滚动条操作
     */
    public boolean beginScrollbarDrag(DocumentLayoutBox rootBox, List<DocumentLayoutBox> topLayerBoxes, int mouseX,
            int mouseY, long eventTimeNanos, long currentTimeNanos, DocumentAnimationTimeline animationTimeline) {
        Objects.requireNonNull(rootBox, "rootBox");
        updateFromLayout(rootBox, topLayerBoxes, currentTimeNanos, animationTimeline);
        ScrollbarHit hit = findScrollbarHit(DocumentVisualTraversal.resolveVisualScene(rootBox, topLayerBoxes, this,
                currentTimeNanos, animationTimeline), mouseX, mouseY, eventTimeNanos, currentTimeNanos,
                animationTimeline);
        if (hit == null) {
            return false;
        }

        ScrollEntry entry = entries.get(hit.box.getElement());
        if (entry == null) {
            return false;
        }
        int pointerPosition = Math.round(hit.vertical ? hit.pointerY : hit.pointerX);
        int thumbStart = hit.vertical ? hit.metrics.thumbTop : hit.metrics.thumbLeft;
        int pointerOffset = hit.metrics.containsThumb(hit.pointerX, hit.pointerY)
                ? pointerPosition - thumbStart
                : hit.metrics.thumbSize / 2;
        activeScrollbarDrag = new ScrollbarDrag(hit.box.getElement(), hit.vertical, pointerOffset);
        markScrollbarInteraction(entry, eventTimeNanos);
        if (!hit.metrics.containsThumb(hit.pointerX, hit.pointerY)) {
            if (updateOffsetFromScrollbarPointer(entry, hit.metrics, pointerPosition, pointerOffset, eventTimeNanos)) {
                lastScrolledElement = hit.box.getElement();
            }
        }
        return true;
    }

    /**
     * 根据鼠标位置更新正在拖拽的滚动条。
     *
     * @param rootBox 根布局盒
     * @param mouseX 文档局部鼠标 X
     * @param mouseY 文档局部鼠标 Y
     * @return 当前是否处于滚动条拖拽流程
     */
    public boolean updateScrollbarDrag(DocumentLayoutBox rootBox, int mouseX, int mouseY) {
        return updateScrollbarDrag(rootBox, mouseX, mouseY, System.nanoTime());
    }

    /**
     * 根据鼠标位置更新正在拖拽的滚动条，并记录交互时间。
     *
     * @param rootBox 根布局盒
     * @param mouseX 文档局部鼠标 X
     * @param mouseY 文档局部鼠标 Y
     * @param eventTimeNanos 事件时间戳
     * @return 当前是否处于滚动条拖拽流程
     */
    public boolean updateScrollbarDrag(DocumentLayoutBox rootBox, int mouseX, int mouseY, long eventTimeNanos) {
        return updateScrollbarDrag(rootBox, Collections.<DocumentLayoutBox>emptyList(), mouseX, mouseY,
                eventTimeNanos);
    }

    /**
     * 根据鼠标位置更新正在拖拽的滚动条，顶层盒与普通文档树共同维护滚动状态。
     *
     * @param rootBox 根布局盒
     * @param topLayerBoxes 顶层布局盒；后面的盒位于更上层
     * @param mouseX 文档局部鼠标 X
     * @param mouseY 文档局部鼠标 Y
     * @param eventTimeNanos 事件时间戳
     * @return 当前是否处于滚动条拖拽流程
     */
    public boolean updateScrollbarDrag(DocumentLayoutBox rootBox, List<DocumentLayoutBox> topLayerBoxes, int mouseX,
            int mouseY, long eventTimeNanos) {
        return updateScrollbarDrag(rootBox, topLayerBoxes, mouseX, mouseY, eventTimeNanos, 0L, null);
    }

    /**
     * 根据鼠标位置更新正在拖拽的滚动条，并应用动画运行态 transform。
     *
     * @param rootBox 根布局盒
     * @param topLayerBoxes 顶层布局盒；后面的盒位于更上层
     * @param mouseX 文档局部鼠标 X
     * @param mouseY 文档局部鼠标 Y
     * @param eventTimeNanos 事件时间戳
     * @param currentTimeNanos 当前动画时间
     * @param animationTimeline 动画时间线；为 null 时只使用 computed style
     * @return 当前是否处于滚动条拖拽流程
     */
    public boolean updateScrollbarDrag(DocumentLayoutBox rootBox, List<DocumentLayoutBox> topLayerBoxes, int mouseX,
            int mouseY, long eventTimeNanos, long currentTimeNanos, DocumentAnimationTimeline animationTimeline) {
        Objects.requireNonNull(rootBox, "rootBox");
        if (activeScrollbarDrag == null) {
            return false;
        }
        updateFromLayout(rootBox, topLayerBoxes, currentTimeNanos, animationTimeline);
        ScrollbarHit hit = findActiveScrollbar(DocumentVisualTraversal.resolveVisualScene(rootBox, topLayerBoxes, this,
                currentTimeNanos, animationTimeline), mouseX, mouseY, eventTimeNanos, currentTimeNanos,
                animationTimeline);
        if (hit == null) {
            activeScrollbarDrag = null;
            return false;
        }

        ScrollEntry entry = entries.get(activeScrollbarDrag.element);
        if (entry == null) {
            activeScrollbarDrag = null;
            return false;
        }
        int pointerPosition = Math.round(activeScrollbarDrag.vertical ? hit.pointerY : hit.pointerX);
        if (updateOffsetFromScrollbarPointer(entry, hit.metrics, pointerPosition,
                activeScrollbarDrag.pointerOffset, eventTimeNanos)) {
            lastScrolledElement = activeScrollbarDrag.element;
        }
        return true;
    }

    /**
     * 结束当前滚动条拖拽流程。
     *
     * @return 是否结束了一个正在进行的滚动条拖拽
     */
    public boolean endScrollbarDrag() {
        return endScrollbarDrag(System.nanoTime());
    }

    /**
     * 结束当前滚动条拖拽流程，并记录结束时间。
     *
     * @param eventTimeNanos 事件时间戳
     * @return 是否结束了一个正在进行的滚动条拖拽
     */
    public boolean endScrollbarDrag(long eventTimeNanos) {
        if (activeScrollbarDrag == null) {
            return false;
        }
        ScrollEntry entry = entries.get(activeScrollbarDrag.element);
        if (entry != null) {
            markScrollbarInteraction(entry, eventTimeNanos);
        }
        activeScrollbarDrag = null;
        return true;
    }

    /**
     * 返回当前是否正在拖拽 HTML-like 滚动条。
     *
     * @return 是否正在拖拽滚动条
     */
    public boolean isDraggingScrollbar() {
        return activeScrollbarDrag != null;
    }

    /**
     * 判断指定元素的临时滚动条是否仍应显示。
     *
     * @param element 元素
     * @param currentTimeNanos 当前时间戳
     * @return 临时滚动条是否处于可见窗口内
     */
    public boolean shouldShowTransientScrollbar(ElementNode element, long currentTimeNanos) {
        ScrollEntry entry = entries.get(element);
        if (activeScrollbarDrag != null && activeScrollbarDrag.element == element) {
            return true;
        }
        return entry != null && isTransientScrollbarVisible(entry, currentTimeNanos);
    }

    /**
     * 判断当前是否存在仍处于可见窗口内的临时滚动条。
     *
     * @param currentTimeNanos 当前时间戳
     * @return 是否存在可见的临时滚动条
     */
    public boolean hasActiveTransientScrollbars(long currentTimeNanos) {
        if (activeScrollbarDrag != null) {
            return true;
        }
        for (ScrollEntry entry : entries.values()) {
            if (isTransientScrollbarVisible(entry, currentTimeNanos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回指定布局盒的纵向滚动条几何。
     *
     * @param box 布局盒
     * @param offsetX 当前盒子的视觉 X 偏移
     * @param offsetY 当前盒子的视觉 Y 偏移
     * @param reserveHorizontal 是否为横向滚动条预留右下角区域
     * @return 滚动条几何；无需绘制时返回 null
     */
    public ScrollbarMetrics getVerticalScrollbarMetrics(DocumentLayoutBox box, int offsetX, int offsetY,
            boolean reserveHorizontal) {
        Objects.requireNonNull(box, "box");
        ScrollEntry entry = entries.get(box.getElement());
        if (entry == null) {
            return null;
        }
        return DocumentScrollbarGeometry.getVerticalScrollbarMetrics(box, offsetX, offsetY, reserveHorizontal,
                entry.maxVerticalOffset, entry.verticalOffset);
    }

    /**
     * 返回指定布局盒的横向滚动条几何。
     *
     * @param box 布局盒
     * @param offsetX 当前盒子的视觉 X 偏移
     * @param offsetY 当前盒子的视觉 Y 偏移
     * @param reserveVertical 是否为纵向滚动条预留右下角区域
     * @return 滚动条几何；无需绘制时返回 null
     */
    public ScrollbarMetrics getHorizontalScrollbarMetrics(DocumentLayoutBox box, int offsetX, int offsetY,
            boolean reserveVertical) {
        Objects.requireNonNull(box, "box");
        ScrollEntry entry = entries.get(box.getElement());
        if (entry == null) {
            return null;
        }
        return DocumentScrollbarGeometry.getHorizontalScrollbarMetrics(box, offsetX, offsetY, reserveVertical,
                entry.maxHorizontalOffset, entry.horizontalOffset);
    }

    private void collectScrollableMetrics(DocumentLayoutBox box, Set<ElementNode> activeElements,
            long currentTimeNanos, DocumentAnimationTimeline animationTimeline) {
        ElementNode element = box.getElement();
        activeElements.add(element);
        DocumentScrollMetricsCalculator.Metrics metrics = DocumentScrollMetricsCalculator.compute(box,
                currentTimeNanos, animationTimeline);
        ScrollEntry entry = entries.get(element);
        if (entry == null) {
            entry = new ScrollEntry();
            entries.put(element, entry);
        }
        entry.maxHorizontalOffset = metrics.maxHorizontalOffset;
        entry.maxVerticalOffset = metrics.maxVerticalOffset;
        entry.contentWidth = metrics.contentWidth;
        entry.contentHeight = metrics.contentHeight;
        updateOffsets(entry, entry.horizontalOffset, entry.verticalOffset);

        for (DocumentLayoutBox child : box.getChildren()) {
            collectScrollableMetrics(child, activeElements, currentTimeNanos, animationTimeline);
        }
    }

    private boolean updateOffsets(ScrollEntry entry, int horizontalOffset, int verticalOffset) {
        int nextHorizontalOffset = clamp(horizontalOffset, entry.maxHorizontalOffset);
        int nextVerticalOffset = clamp(verticalOffset, entry.maxVerticalOffset);
        if (entry.horizontalOffset == nextHorizontalOffset && entry.verticalOffset == nextVerticalOffset) {
            return false;
        }
        entry.horizontalOffset = nextHorizontalOffset;
        entry.verticalOffset = nextVerticalOffset;
        version++;
        return true;
    }

    private BoxContext findScrollableBoxAt(BoxContext boxContext, float mouseX, float mouseY,
            boolean searchStackingContext, long currentTimeNanos, DocumentAnimationTimeline animationTimeline,
            StackingContextResolver resolver) {
        DocumentLayoutBox box = boxContext.getBox();
        if (DocumentHitTestEngine.isHitTestSubtreeSuppressed(box.getElement())) {
            return null;
        }
        UiTransform.Point inversePoint = DocumentVisualHitTransforms.inverseTransformPoint(box,
                boxContext.getBoxOffsetX(), boxContext.getBoxOffsetY(), mouseX, mouseY, currentTimeNanos,
                animationTimeline);
        if (inversePoint == null) {
            return null;
        }
        float hitX = inversePoint.getX();
        float hitY = inversePoint.getY();
        boolean insideAncestorClipChain = DocumentVisualTraversal.isPointInsideClipChain(boxContext, hitX, hitY);
        if (!insideAncestorClipChain) {
            return null;
        }
        int boxOffsetX = boxContext.getBoxOffsetX();
        int boxOffsetY = boxContext.getBoxOffsetY();
        int viewportLeft = box.getContentLeft() + boxOffsetX;
        int viewportTop = box.getContentTop() + boxOffsetY;
        int viewportRight = viewportLeft + box.getContentWidth();
        int viewportBottom = viewportTop + box.getContentHeight();
        boolean pointerInViewport = containsInRect(hitX, hitY, viewportLeft, viewportTop, viewportRight,
                viewportBottom);
        boolean childrenReachable = DocumentVisualTraversal.canReachChildren(boxContext, hitX, hitY);
        if (childrenReachable) {
            BoxContext hit = searchStackingContext
                    ? findScrollableInStackingContext(boxContext, hitX, hitY, currentTimeNanos, animationTimeline,
                            resolver)
                    : findScrollableInNormalFlow(boxContext, hitX, hitY, currentTimeNanos, animationTimeline,
                            resolver);
            if (hit != null) {
                return hit;
            }
        }

        if (pointerInViewport && isScrollable(box.getElement())
                && DocumentHitTestEngine.isSelfHitTestVisible(box.getElement())
                && DocumentHitTestEngine.isPointerEventsEnabled(box.getElement())) {
            return boxContext;
        }
        return null;
    }

    private BoxContext findScrollableInStackingContext(BoxContext contextRootContext, float mouseX, float mouseY,
            long currentTimeNanos, DocumentAnimationTimeline animationTimeline, StackingContextResolver resolver) {
        BoxContext hit = findScrollableInStackingPhase(contextRootContext, mouseX, mouseY,
                DocumentStackingPhase.POSITIVE_POSITIONED, currentTimeNanos, animationTimeline, resolver);
        if (hit != null) {
            return hit;
        }
        hit = findScrollableInStackingPhase(contextRootContext, mouseX, mouseY,
                DocumentStackingPhase.POSITIONED_AUTO_OR_ZERO, currentTimeNanos, animationTimeline, resolver);
        if (hit != null) {
            return hit;
        }
        hit = findScrollableInNormalFlow(contextRootContext, mouseX, mouseY, currentTimeNanos, animationTimeline,
                resolver);
        if (hit != null) {
            return hit;
        }
        return findScrollableInStackingPhase(contextRootContext, mouseX, mouseY,
                DocumentStackingPhase.NEGATIVE_POSITIONED, currentTimeNanos, animationTimeline, resolver);
    }

    private BoxContext findScrollableInNormalFlow(BoxContext contextRootContext, float mouseX, float mouseY,
            long currentTimeNanos, DocumentAnimationTimeline animationTimeline, StackingContextResolver resolver) {
        List<TraversalEntry> children = DocumentVisualTraversal.getNormalFlowEntries(contextRootContext.getBox(),
                contextRootContext, this, resolver, true);
        for (TraversalEntry child : children) {
            BoxContext hit = findScrollableBoxAt(child.getBoxContext(), mouseX, mouseY, child.isStackingContext(),
                    currentTimeNanos, animationTimeline, resolver);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private BoxContext findScrollableInStackingPhase(BoxContext contextRootContext, float mouseX, float mouseY,
            DocumentStackingPhase phase, long currentTimeNanos, DocumentAnimationTimeline animationTimeline,
            StackingContextResolver resolver) {
        List<TraversalEntry> items = DocumentVisualTraversal.collectStackingPhaseEntries(contextRootContext.getBox(),
                contextRootContext, this, resolver, phase);
        for (int index = items.size() - 1; index >= 0; index--) {
            TraversalEntry item = items.get(index);
            BoxContext hit = findScrollableBoxAt(item.getBoxContext(), mouseX, mouseY, item.isStackingContext(),
                    currentTimeNanos, animationTimeline, resolver);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private boolean isScrollable(ElementNode element) {
        ScrollEntry entry = entries.get(element);
        return entry != null && (entry.maxHorizontalOffset > 0 || entry.maxVerticalOffset > 0);
    }

    private BoxContext findScrollableBoxAt(VisualScene scene, int mouseX, int mouseY, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline) {
        if (scene == null) {
            return null;
        }
        StackingContextResolver resolver = createStackingContextResolver(currentTimeNanos, animationTimeline);
        List<RootEntry> rootEntries = scene.getRootEntries();
        for (int index = rootEntries.size() - 1; index >= 0; index--) {
            BoxContext hit = findScrollableBoxAt(rootEntries.get(index).getRootContext(), mouseX, mouseY, true,
                    currentTimeNanos, animationTimeline, resolver);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private ScrollbarHit findScrollbarHit(VisualScene scene, int mouseX, int mouseY, long eventTimeNanos,
            long currentTimeNanos, DocumentAnimationTimeline animationTimeline) {
        if (scene == null) {
            return null;
        }
        StackingContextResolver resolver = createStackingContextResolver(currentTimeNanos, animationTimeline);
        List<RootEntry> rootEntries = scene.getRootEntries();
        for (int index = rootEntries.size() - 1; index >= 0; index--) {
            RootEntry rootEntry = rootEntries.get(index);
            ScrollbarHit hit = findScrollbarHit(rootEntry.getRootContext(), rootEntry.getRootBox(), mouseX, mouseY,
                    eventTimeNanos, true, currentTimeNanos, animationTimeline, resolver);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private ScrollbarHit findActiveScrollbar(VisualScene scene, int mouseX, int mouseY, long eventTimeNanos,
            long currentTimeNanos, DocumentAnimationTimeline animationTimeline) {
        if (scene == null) {
            return null;
        }
        List<RootEntry> rootEntries = scene.getRootEntries();
        for (int index = rootEntries.size() - 1; index >= 0; index--) {
            RootEntry rootEntry = rootEntries.get(index);
            ScrollbarHit hit = findActiveScrollbar(rootEntry.getRootContext(), rootEntry.getRootBox(), mouseX, mouseY,
                    eventTimeNanos, currentTimeNanos, animationTimeline);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private ScrollbarHit findScrollbarHit(BoxContext boxContext, DocumentLayoutBox rootBox, float mouseX,
            float mouseY, long eventTimeNanos, boolean searchStackingContext, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline, StackingContextResolver resolver) {
        DocumentLayoutBox box = boxContext.getBox();
        if (DocumentHitTestEngine.isHitTestSubtreeSuppressed(box.getElement())) {
            return null;
        }
        UiTransform.Point inversePoint = DocumentVisualHitTransforms.inverseTransformPoint(box,
                boxContext.getBoxOffsetX(), boxContext.getBoxOffsetY(), mouseX, mouseY, currentTimeNanos,
                animationTimeline);
        if (inversePoint == null) {
            return null;
        }
        float hitX = inversePoint.getX();
        float hitY = inversePoint.getY();
        if (!DocumentVisualTraversal.isPointInsideClipChain(boxContext, hitX, hitY)) {
            return null;
        }
        ScrollbarHit currentHit = findCurrentScrollbarHit(rootBox, boxContext, hitX, hitY, eventTimeNanos);
        if (currentHit != null) {
            return currentHit;
        }
        if (!DocumentVisualTraversal.canReachChildren(boxContext, hitX, hitY)) {
            return null;
        }

        return searchStackingContext
                ? findScrollbarHitInStackingContext(boxContext, rootBox, hitX, hitY, eventTimeNanos,
                        currentTimeNanos, animationTimeline, resolver)
                : findScrollbarHitInNormalFlow(boxContext, rootBox, hitX, hitY, eventTimeNanos,
                        currentTimeNanos, animationTimeline, resolver);
    }

    private ScrollbarHit findScrollbarHitInStackingContext(BoxContext contextRootContext, DocumentLayoutBox rootBox,
            float mouseX, float mouseY, long eventTimeNanos, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline, StackingContextResolver resolver) {
        ScrollbarHit hit = findScrollbarHitInStackingPhase(contextRootContext, rootBox, mouseX, mouseY,
                eventTimeNanos, DocumentStackingPhase.POSITIVE_POSITIONED, currentTimeNanos, animationTimeline,
                resolver);
        if (hit != null) {
            return hit;
        }
        hit = findScrollbarHitInStackingPhase(contextRootContext, rootBox, mouseX, mouseY, eventTimeNanos,
                DocumentStackingPhase.POSITIONED_AUTO_OR_ZERO, currentTimeNanos, animationTimeline, resolver);
        if (hit != null) {
            return hit;
        }
        hit = findScrollbarHitInNormalFlow(contextRootContext, rootBox, mouseX, mouseY, eventTimeNanos,
                currentTimeNanos, animationTimeline, resolver);
        if (hit != null) {
            return hit;
        }
        return findScrollbarHitInStackingPhase(contextRootContext, rootBox, mouseX, mouseY, eventTimeNanos,
                DocumentStackingPhase.NEGATIVE_POSITIONED, currentTimeNanos, animationTimeline, resolver);
    }

    private ScrollbarHit findScrollbarHitInNormalFlow(BoxContext contextRootContext, DocumentLayoutBox rootBox,
            float mouseX, float mouseY, long eventTimeNanos, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline, StackingContextResolver resolver) {
        List<TraversalEntry> children = DocumentVisualTraversal.getNormalFlowEntries(contextRootContext.getBox(),
                contextRootContext, this, resolver, true);
        for (TraversalEntry child : children) {
            ScrollbarHit hit = findScrollbarHit(child.getBoxContext(), rootBox, mouseX, mouseY, eventTimeNanos,
                    child.isStackingContext(), currentTimeNanos, animationTimeline, resolver);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private ScrollbarHit findScrollbarHitInStackingPhase(BoxContext contextRootContext, DocumentLayoutBox rootBox,
            float mouseX, float mouseY, long eventTimeNanos, DocumentStackingPhase phase, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline, StackingContextResolver resolver) {
        List<TraversalEntry> items = DocumentVisualTraversal.collectStackingPhaseEntries(contextRootContext.getBox(),
                contextRootContext, this, resolver, phase);
        for (int index = items.size() - 1; index >= 0; index--) {
            TraversalEntry item = items.get(index);
            ScrollbarHit hit = findScrollbarHit(item.getBoxContext(), rootBox, mouseX, mouseY, eventTimeNanos,
                    item.isStackingContext(), currentTimeNanos, animationTimeline, resolver);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private ScrollbarHit findCurrentScrollbarHit(DocumentLayoutBox rootBox, BoxContext boxContext, float mouseX,
            float mouseY, long eventTimeNanos) {
        DocumentLayoutBox box = boxContext.getBox();
        if (!DocumentHitTestEngine.isSelfHitTestVisible(box.getElement())
                || !DocumentHitTestEngine.isPointerEventsEnabled(box.getElement())) {
            return null;
        }
        if (box != rootBox && !shouldShowTransientScrollbar(box.getElement(), eventTimeNanos)) {
            return null;
        }
        boolean hasVerticalScrollbar = getMaxScrollTop(box.getElement()) > 0
                && isScrollableOverflow(box.getComputedStyle().getOverflowY());
        boolean hasHorizontalScrollbar = getMaxScrollLeft(box.getElement()) > 0
                && isScrollableOverflow(box.getComputedStyle().getOverflowX());
        ScrollbarMetrics verticalMetrics = getVerticalScrollbarMetrics(box, boxContext.getBoxOffsetX(),
                boxContext.getBoxOffsetY(), hasHorizontalScrollbar);
        if (verticalMetrics != null && verticalMetrics.containsTrack(mouseX, mouseY)) {
            return new ScrollbarHit(box, verticalMetrics, true, mouseX, mouseY);
        }
        ScrollbarMetrics horizontalMetrics = getHorizontalScrollbarMetrics(box, boxContext.getBoxOffsetX(),
                boxContext.getBoxOffsetY(),
                hasVerticalScrollbar);
        if (horizontalMetrics != null && horizontalMetrics.containsTrack(mouseX, mouseY)) {
            return new ScrollbarHit(box, horizontalMetrics, false, mouseX, mouseY);
        }
        return null;
    }

    private ScrollbarHit findActiveScrollbar(BoxContext boxContext, DocumentLayoutBox rootBox, float mouseX,
            float mouseY, long eventTimeNanos, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline) {
        if (activeScrollbarDrag == null) {
            return null;
        }
        DocumentLayoutBox box = boxContext.getBox();
        UiTransform.Point inversePoint = DocumentVisualHitTransforms.inverseTransformPoint(box,
                boxContext.getBoxOffsetX(), boxContext.getBoxOffsetY(), mouseX, mouseY, currentTimeNanos,
                animationTimeline);
        if (inversePoint == null) {
            return null;
        }
        float hitX = inversePoint.getX();
        float hitY = inversePoint.getY();
        if (box.getElement() == activeScrollbarDrag.element) {
            boolean hasVerticalScrollbar = getMaxScrollTop(box.getElement()) > 0
                    && isScrollableOverflow(box.getComputedStyle().getOverflowY());
            boolean hasHorizontalScrollbar = getMaxScrollLeft(box.getElement()) > 0
                    && isScrollableOverflow(box.getComputedStyle().getOverflowX());
            ScrollbarMetrics metrics = activeScrollbarDrag.vertical
                    ? getVerticalScrollbarMetrics(box, boxContext.getBoxOffsetX(), boxContext.getBoxOffsetY(),
                            hasHorizontalScrollbar)
                    : getHorizontalScrollbarMetrics(box, boxContext.getBoxOffsetX(), boxContext.getBoxOffsetY(),
                            hasVerticalScrollbar);
            return metrics == null ? null : new ScrollbarHit(box, metrics, activeScrollbarDrag.vertical,
                    hitX, hitY);
        }

        for (DocumentLayoutBox child : box.getChildren()) {
            ScrollbarHit hit = findActiveScrollbar(DocumentVisualTraversal.resolveChildBoxContext(boxContext, child,
                    this), rootBox, hitX, hitY, eventTimeNanos, currentTimeNanos, animationTimeline);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private boolean updateOffsetFromScrollbarPointer(ScrollEntry entry, ScrollbarMetrics metrics, int pointerPosition,
            int pointerOffset, long eventTimeNanos) {
        boolean changed = metrics.vertical
                ? setVerticalFromThumbStart(entry, pointerPosition - pointerOffset, metrics)
                : setHorizontalFromThumbStart(entry, pointerPosition - pointerOffset, metrics);
        if (changed) {
            markScrollbarInteraction(entry, eventTimeNanos);
        }
        return changed;
    }

    private boolean setVerticalFromThumbStart(ScrollEntry entry, int thumbTop, ScrollbarMetrics metrics) {
        int travel = Math.max(0, metrics.trackLength - metrics.thumbSize);
        if (travel <= 0 || entry.maxVerticalOffset <= 0) {
            return updateOffsets(entry, entry.horizontalOffset, 0);
        }

        int clampedThumbTop = Math.max(metrics.trackTop, Math.min(thumbTop, metrics.trackBottom - metrics.thumbSize));
        float progress = (clampedThumbTop - metrics.trackTop) / (float) travel;
        return updateOffsets(entry, entry.horizontalOffset, Math.round(entry.maxVerticalOffset * progress));
    }

    private boolean setHorizontalFromThumbStart(ScrollEntry entry, int thumbLeft, ScrollbarMetrics metrics) {
        int travel = Math.max(0, metrics.trackLength - metrics.thumbSize);
        if (travel <= 0 || entry.maxHorizontalOffset <= 0) {
            return updateOffsets(entry, 0, entry.verticalOffset);
        }

        int clampedThumbLeft = Math.max(metrics.trackLeft, Math.min(thumbLeft, metrics.trackRight - metrics.thumbSize));
        float progress = (clampedThumbLeft - metrics.trackLeft) / (float) travel;
        return updateOffsets(entry, Math.round(entry.maxHorizontalOffset * progress), entry.verticalOffset);
    }

    private void markScrollbarInteraction(ScrollEntry entry, long eventTimeNanos) {
        entry.lastScrollNanos = Math.max(1L, eventTimeNanos);
    }

    private boolean isTransientScrollbarVisible(ScrollEntry entry, long currentTimeNanos) {
        if (entry.lastScrollNanos <= 0L || (entry.maxHorizontalOffset <= 0 && entry.maxVerticalOffset <= 0)) {
            return false;
        }
        if (currentTimeNanos < entry.lastScrollNanos) {
            return true;
        }
        return currentTimeNanos - entry.lastScrollNanos <= TRANSIENT_SCROLLBAR_VISIBLE_NANOS;
    }

    private static boolean containsInRect(int mouseX, int mouseY, int left, int top, int right, int bottom) {
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    private static boolean containsInRect(float mouseX, float mouseY, int left, int top, int right, int bottom) {
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    private static StackingContextResolver createStackingContextResolver(final long currentTimeNanos,
            final DocumentAnimationTimeline animationTimeline) {
        if (animationTimeline == null) {
            return STATIC_STACKING_CONTEXT_RESOLVER;
        }
        return new StackingContextResolver() {
            @Override
            public boolean createsStackingContext(DocumentLayoutBox box) {
                return DocumentVisualTraversal.createsRuntimeStackingContext(box, currentTimeNanos,
                        animationTimeline);
            }
        };
    }

    private static int clamp(int value, int maxValue) {
        return Math.max(0, Math.min(value, Math.max(0, maxValue)));
    }

    /**
     * 单个元素的滚动条目。
     */
    private static final class ScrollEntry {

        private int horizontalOffset;
        private int verticalOffset;
        private int maxHorizontalOffset;
        private int maxVerticalOffset;
        private int contentWidth;
        private int contentHeight;
        private long lastScrollNanos;
    }

    private static final class ContentBounds {

        private final int right;
        private final int bottom;

        private ContentBounds(int right, int bottom) {
            this.right = right;
            this.bottom = bottom;
        }
    }

    /**
     * 当前滚动条拖拽状态。
     */
    private static final class ScrollbarDrag {

        private final ElementNode element;
        private final boolean vertical;
        private final int pointerOffset;

        private ScrollbarDrag(ElementNode element, boolean vertical, int pointerOffset) {
            this.element = element;
            this.vertical = vertical;
            this.pointerOffset = Math.max(0, pointerOffset);
        }
    }

    /**
     * 命中的滚动条。
     */
    private static final class ScrollbarHit {

        private final DocumentLayoutBox box;
        private final ScrollbarMetrics metrics;
        private final boolean vertical;
        private final float pointerX;
        private final float pointerY;

        private ScrollbarHit(DocumentLayoutBox box, ScrollbarMetrics metrics, boolean vertical, float pointerX,
                float pointerY) {
            this.box = box;
            this.metrics = metrics;
            this.vertical = vertical;
            this.pointerX = pointerX;
            this.pointerY = pointerY;
        }
    }

    /**
     * 滚动条轨道与滑块几何信息。
     */
    public static final class ScrollbarMetrics {

        private final boolean vertical;
        private final int trackLeft;
        private final int trackTop;
        private final int trackRight;
        private final int trackBottom;
        private final int trackLength;
        private final int thumbLeft;
        private final int thumbTop;
        private final int thumbRight;
        private final int thumbBottom;
        private final int thumbSize;

        ScrollbarMetrics(boolean vertical, int trackLeft, int trackTop, int trackRight, int trackBottom,
                int trackLength, int thumbLeft, int thumbTop, int thumbRight, int thumbBottom, int thumbSize) {
            this.vertical = vertical;
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

        public int getTrackLeft() {
            return trackLeft;
        }

        public int getTrackTop() {
            return trackTop;
        }

        public int getTrackRight() {
            return trackRight;
        }

        public int getTrackBottom() {
            return trackBottom;
        }

        public int getThumbLeft() {
            return thumbLeft;
        }

        public int getThumbTop() {
            return thumbTop;
        }

        public int getThumbRight() {
            return thumbRight;
        }

        public int getThumbBottom() {
            return thumbBottom;
        }

        private boolean containsTrack(float mouseX, float mouseY) {
            return containsInRect(mouseX, mouseY, trackLeft, trackTop, trackRight, trackBottom);
        }

        private boolean containsThumb(float mouseX, float mouseY) {
            return containsInRect(mouseX, mouseY, thumbLeft, thumbTop, thumbRight, thumbBottom);
        }

        private static boolean containsInRect(float mouseX, float mouseY, int left, int top, int right, int bottom) {
            return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
        }
    }

    /**
     * 判断 overflow 值是否表示可滚动（AUTO 或 SCROLL）。
     */
    private static boolean isScrollableOverflow(UiOverflow overflow) {
        return overflow == UiOverflow.AUTO || overflow == UiOverflow.SCROLL;
    }
}
