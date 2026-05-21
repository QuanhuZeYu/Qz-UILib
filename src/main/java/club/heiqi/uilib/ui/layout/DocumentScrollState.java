package club.heiqi.uilib.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.style.props.UiOverflow;

/**
 * HTML-like 文档滚动状态。
 *
 * <p>该状态只保存元素级滚动偏移和可滚范围，布局盒本身仍表达未滚动的文档坐标。</p>
 */
public final class DocumentScrollState {

    private static final int DEFAULT_SCROLL_STEP = 36;
    private static final long TRANSIENT_SCROLLBAR_VISIBLE_NANOS = 900_000_000L;

    private final Map<ElementNode, ScrollEntry> entries = new HashMap<ElementNode, ScrollEntry>();
    private int scrollStep = DEFAULT_SCROLL_STEP;
    private int version;
    private ScrollbarDrag activeScrollbarDrag;

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
        Objects.requireNonNull(rootBox, "rootBox");
        Set<ElementNode> activeElements = new HashSet<ElementNode>();
        collectScrollableMetrics(rootBox, activeElements);
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
        Objects.requireNonNull(rootBox, "rootBox");
        if (wheelDelta == 0) {
            return false;
        }
        updateFromLayout(rootBox);
        DocumentLayoutBox target = findScrollableBoxAt(rootBox, mouseX, mouseY, 0, 0, true);
        if (target == null) {
            return false;
        }
        ScrollEntry entry = entries.get(target.getElement());
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
        Objects.requireNonNull(rootBox, "rootBox");
        updateFromLayout(rootBox);
        ScrollbarHit hit = findScrollbarHit(rootBox, rootBox, mouseX, mouseY, 0, 0, eventTimeNanos, true);
        if (hit == null) {
            return false;
        }

        ScrollEntry entry = entries.get(hit.box.getElement());
        if (entry == null) {
            return false;
        }
        int pointerPosition = hit.vertical ? mouseY : mouseX;
        int thumbStart = hit.vertical ? hit.metrics.thumbTop : hit.metrics.thumbLeft;
        int pointerOffset = hit.metrics.containsThumb(mouseX, mouseY)
                ? pointerPosition - thumbStart
                : hit.metrics.thumbSize / 2;
        activeScrollbarDrag = new ScrollbarDrag(hit.box.getElement(), hit.vertical, pointerOffset);
        markScrollbarInteraction(entry, eventTimeNanos);
        if (!hit.metrics.containsThumb(mouseX, mouseY)) {
            updateOffsetFromScrollbarPointer(entry, hit.metrics, pointerPosition, pointerOffset, eventTimeNanos);
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
        Objects.requireNonNull(rootBox, "rootBox");
        if (activeScrollbarDrag == null) {
            return false;
        }
        updateFromLayout(rootBox);
        ScrollbarHit hit = findActiveScrollbar(rootBox, rootBox, 0, 0, eventTimeNanos);
        if (hit == null) {
            activeScrollbarDrag = null;
            return false;
        }

        ScrollEntry entry = entries.get(activeScrollbarDrag.element);
        if (entry == null) {
            activeScrollbarDrag = null;
            return false;
        }
        int pointerPosition = activeScrollbarDrag.vertical ? mouseY : mouseX;
        updateOffsetFromScrollbarPointer(entry, hit.metrics, pointerPosition,
                activeScrollbarDrag.pointerOffset, eventTimeNanos);
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

    private void collectScrollableMetrics(DocumentLayoutBox box, Set<ElementNode> activeElements) {
        ElementNode element = box.getElement();
        activeElements.add(element);
        DocumentScrollMetricsCalculator.Metrics metrics = DocumentScrollMetricsCalculator.compute(box);
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
            collectScrollableMetrics(child, activeElements);
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

    private DocumentLayoutBox findScrollableBoxAt(DocumentLayoutBox box, int mouseX, int mouseY, int offsetX,
            int offsetY, boolean searchStackingContext) {
        int baseOffsetX = box.isFixedPositioned() ? 0 : offsetX;
        int baseOffsetY = box.isFixedPositioned() ? 0 : offsetY;
        int boxOffsetX = baseOffsetX + box.getPositionOffsetX();
        int boxOffsetY = baseOffsetY + box.getPositionOffsetY();
        int viewportLeft = box.getContentLeft() + boxOffsetX;
        int viewportTop = box.getContentTop() + boxOffsetY;
        int viewportRight = viewportLeft + box.getContentWidth();
        int viewportBottom = viewportTop + box.getContentHeight();
        boolean pointerInViewport = containsInRect(mouseX, mouseY, viewportLeft, viewportTop, viewportRight,
                viewportBottom);
        boolean childrenReachable = canReachChildren(box, mouseX, mouseY, boxOffsetX, boxOffsetY);
        if (childrenReachable) {
            int childOffsetX = boxOffsetX - getScrollLeft(box.getElement());
            int childOffsetY = boxOffsetY - getScrollTop(box.getElement());
            DocumentLayoutBox hit = searchStackingContext
                    ? findScrollableInStackingContext(box, mouseX, mouseY, childOffsetX, childOffsetY)
                    : findScrollableInNormalFlow(box, mouseX, mouseY, childOffsetX, childOffsetY);
            if (hit != null) {
                return hit;
            }
        }

        if (pointerInViewport && isScrollable(box.getElement())) {
            return box;
        }
        return null;
    }

    private DocumentLayoutBox findScrollableInStackingContext(DocumentLayoutBox contextRoot, int mouseX, int mouseY,
            int childOffsetX, int childOffsetY) {
        DocumentLayoutBox hit = findScrollableInStackingPhase(contextRoot, mouseX, mouseY, childOffsetX,
                childOffsetY, DocumentStackingPhase.POSITIVE_POSITIONED);
        if (hit != null) {
            return hit;
        }
        hit = findScrollableInStackingPhase(contextRoot, mouseX, mouseY, childOffsetX, childOffsetY,
                DocumentStackingPhase.POSITIONED_AUTO_OR_ZERO);
        if (hit != null) {
            return hit;
        }
        hit = findScrollableInNormalFlow(contextRoot, mouseX, mouseY, childOffsetX, childOffsetY);
        if (hit != null) {
            return hit;
        }
        return findScrollableInStackingPhase(contextRoot, mouseX, mouseY, childOffsetX, childOffsetY,
                DocumentStackingPhase.NEGATIVE_POSITIONED);
    }

    private DocumentLayoutBox findScrollableInNormalFlow(DocumentLayoutBox box, int mouseX, int mouseY,
            int childOffsetX, int childOffsetY) {
        List<DocumentLayoutBox> children = box.getChildren();
        for (int index = children.size() - 1; index >= 0; index--) {
            DocumentLayoutBox child = children.get(index);
            if (child.getStackingPhase() != DocumentStackingPhase.NORMAL_FLOW) {
                continue;
            }
            DocumentLayoutBox hit = findScrollableBoxAt(child, mouseX, mouseY, childOffsetX, childOffsetY,
                    shouldSearchAsStackingContext(child));
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private DocumentLayoutBox findScrollableInStackingPhase(DocumentLayoutBox contextRoot, int mouseX, int mouseY,
            int childOffsetX, int childOffsetY, DocumentStackingPhase phase) {
        List<StackingScrollItem> items = new ArrayList<StackingScrollItem>();
        collectStackingPhaseItems(contextRoot, items, childOffsetX, childOffsetY, phase);
        sortStackingItemsIfNeeded(items, phase);
        for (int index = items.size() - 1; index >= 0; index--) {
            StackingScrollItem item = items.get(index);
            DocumentLayoutBox hit = findScrollableBoxAt(item.box, mouseX, mouseY, item.offsetX, item.offsetY,
                    item.searchStackingContext);
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

    private ScrollbarHit findScrollbarHit(DocumentLayoutBox rootBox, DocumentLayoutBox box, int mouseX, int mouseY,
            int offsetX, int offsetY, long currentTimeNanos, boolean searchStackingContext) {
        int baseOffsetX = box.isFixedPositioned() ? 0 : offsetX;
        int baseOffsetY = box.isFixedPositioned() ? 0 : offsetY;
        int boxOffsetX = baseOffsetX + box.getPositionOffsetX();
        int boxOffsetY = baseOffsetY + box.getPositionOffsetY();
        ScrollbarHit currentHit = findCurrentScrollbarHit(rootBox, box, mouseX, mouseY, boxOffsetX, boxOffsetY,
                currentTimeNanos);
        if (currentHit != null) {
            return currentHit;
        }
        if (!canReachChildren(box, mouseX, mouseY, boxOffsetX, boxOffsetY)) {
            return null;
        }

        int childOffsetX = boxOffsetX - getScrollLeft(box.getElement());
        int childOffsetY = boxOffsetY - getScrollTop(box.getElement());
        return searchStackingContext
                ? findScrollbarHitInStackingContext(rootBox, box, mouseX, mouseY, childOffsetX, childOffsetY,
                        currentTimeNanos)
                : findScrollbarHitInNormalFlow(rootBox, box, mouseX, mouseY, childOffsetX, childOffsetY,
                        currentTimeNanos);
    }

    private ScrollbarHit findScrollbarHitInStackingContext(DocumentLayoutBox rootBox, DocumentLayoutBox contextRoot,
            int mouseX, int mouseY, int childOffsetX, int childOffsetY, long currentTimeNanos) {
        ScrollbarHit hit = findScrollbarHitInStackingPhase(rootBox, contextRoot, mouseX, mouseY, childOffsetX,
                childOffsetY, currentTimeNanos, DocumentStackingPhase.POSITIVE_POSITIONED);
        if (hit != null) {
            return hit;
        }
        hit = findScrollbarHitInStackingPhase(rootBox, contextRoot, mouseX, mouseY, childOffsetX, childOffsetY,
                currentTimeNanos, DocumentStackingPhase.POSITIONED_AUTO_OR_ZERO);
        if (hit != null) {
            return hit;
        }
        hit = findScrollbarHitInNormalFlow(rootBox, contextRoot, mouseX, mouseY, childOffsetX, childOffsetY,
                currentTimeNanos);
        if (hit != null) {
            return hit;
        }
        return findScrollbarHitInStackingPhase(rootBox, contextRoot, mouseX, mouseY, childOffsetX, childOffsetY,
                currentTimeNanos, DocumentStackingPhase.NEGATIVE_POSITIONED);
    }

    private ScrollbarHit findScrollbarHitInNormalFlow(DocumentLayoutBox rootBox, DocumentLayoutBox box, int mouseX,
            int mouseY, int childOffsetX, int childOffsetY, long currentTimeNanos) {
        List<DocumentLayoutBox> children = box.getChildren();
        for (int index = children.size() - 1; index >= 0; index--) {
            DocumentLayoutBox child = children.get(index);
            if (child.getStackingPhase() != DocumentStackingPhase.NORMAL_FLOW) {
                continue;
            }
            ScrollbarHit hit = findScrollbarHit(rootBox, child, mouseX, mouseY, childOffsetX, childOffsetY,
                    currentTimeNanos, shouldSearchAsStackingContext(child));
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private ScrollbarHit findScrollbarHitInStackingPhase(DocumentLayoutBox rootBox, DocumentLayoutBox contextRoot,
            int mouseX, int mouseY, int childOffsetX, int childOffsetY, long currentTimeNanos,
            DocumentStackingPhase phase) {
        List<StackingScrollItem> items = new ArrayList<StackingScrollItem>();
        collectStackingPhaseItems(contextRoot, items, childOffsetX, childOffsetY, phase);
        sortStackingItemsIfNeeded(items, phase);
        for (int index = items.size() - 1; index >= 0; index--) {
            StackingScrollItem item = items.get(index);
            ScrollbarHit hit = findScrollbarHit(rootBox, item.box, mouseX, mouseY, item.offsetX, item.offsetY,
                    currentTimeNanos, item.searchStackingContext);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private ScrollbarHit findCurrentScrollbarHit(DocumentLayoutBox rootBox, DocumentLayoutBox box, int mouseX,
            int mouseY, int offsetX, int offsetY, long currentTimeNanos) {
        if (box != rootBox && !shouldShowTransientScrollbar(box.getElement(), currentTimeNanos)) {
            return null;
        }
        boolean hasVerticalScrollbar = getMaxScrollTop(box.getElement()) > 0
                && isScrollableOverflow(box.getComputedStyle().getOverflowY());
        boolean hasHorizontalScrollbar = getMaxScrollLeft(box.getElement()) > 0
                && isScrollableOverflow(box.getComputedStyle().getOverflowX());
        ScrollbarMetrics verticalMetrics = getVerticalScrollbarMetrics(box, offsetX, offsetY, hasHorizontalScrollbar);
        if (verticalMetrics != null && verticalMetrics.containsTrack(mouseX, mouseY)) {
            return new ScrollbarHit(box, verticalMetrics, true);
        }
        ScrollbarMetrics horizontalMetrics = getHorizontalScrollbarMetrics(box, offsetX, offsetY,
                hasVerticalScrollbar);
        if (horizontalMetrics != null && horizontalMetrics.containsTrack(mouseX, mouseY)) {
            return new ScrollbarHit(box, horizontalMetrics, false);
        }
        return null;
    }

    private ScrollbarHit findActiveScrollbar(DocumentLayoutBox rootBox, DocumentLayoutBox box, int offsetX,
            int offsetY, long currentTimeNanos) {
        if (activeScrollbarDrag == null) {
            return null;
        }
        int baseOffsetX = box.isFixedPositioned() ? 0 : offsetX;
        int baseOffsetY = box.isFixedPositioned() ? 0 : offsetY;
        int boxOffsetX = baseOffsetX + box.getPositionOffsetX();
        int boxOffsetY = baseOffsetY + box.getPositionOffsetY();
        if (box.getElement() == activeScrollbarDrag.element) {
            boolean hasVerticalScrollbar = getMaxScrollTop(box.getElement()) > 0
                    && isScrollableOverflow(box.getComputedStyle().getOverflowY());
            boolean hasHorizontalScrollbar = getMaxScrollLeft(box.getElement()) > 0
                    && isScrollableOverflow(box.getComputedStyle().getOverflowX());
            ScrollbarMetrics metrics = activeScrollbarDrag.vertical
                    ? getVerticalScrollbarMetrics(box, boxOffsetX, boxOffsetY, hasHorizontalScrollbar)
                    : getHorizontalScrollbarMetrics(box, boxOffsetX, boxOffsetY, hasVerticalScrollbar);
            return metrics == null ? null : new ScrollbarHit(box, metrics, activeScrollbarDrag.vertical);
        }

        int childOffsetX = boxOffsetX - getScrollLeft(box.getElement());
        int childOffsetY = boxOffsetY - getScrollTop(box.getElement());
        for (DocumentLayoutBox child : box.getChildrenInStackingOrder()) {
            ScrollbarHit hit = findActiveScrollbar(rootBox, child, resolveDescendantBaseOffsetX(childOffsetX, child),
                    resolveDescendantBaseOffsetY(childOffsetY, child), currentTimeNanos);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private void collectStackingPhaseItems(DocumentLayoutBox currentBox, List<StackingScrollItem> items,
            int childOffsetX, int childOffsetY, DocumentStackingPhase phase) {
        for (DocumentLayoutBox child : currentBox.getChildren()) {
            boolean childStackingContext = shouldSearchAsStackingContext(child);
            if (child.getStackingPhase() == phase) {
                items.add(new StackingScrollItem(child, childOffsetX, childOffsetY, childStackingContext));
            }
            if (childStackingContext) {
                continue;
            }
            int grandChildOffsetX = resolveChildOffsetX(childOffsetX, child);
            int grandChildOffsetY = resolveChildOffsetY(childOffsetY, child);
            collectStackingPhaseItems(child, items, grandChildOffsetX, grandChildOffsetY, phase);
        }
    }

    private int resolveDescendantBaseOffsetX(int offsetX, DocumentLayoutBox child) {
        return child.isFixedPositioned() ? 0 : offsetX;
    }

    private int resolveDescendantBaseOffsetY(int offsetY, DocumentLayoutBox child) {
        return child.isFixedPositioned() ? 0 : offsetY;
    }

    private int resolveChildOffsetX(int childOffsetX, DocumentLayoutBox child) {
        int baseOffsetX = child.isFixedPositioned() ? 0 : childOffsetX;
        return baseOffsetX + child.getPositionOffsetX() - getScrollLeft(child.getElement());
    }

    private int resolveChildOffsetY(int childOffsetY, DocumentLayoutBox child) {
        int baseOffsetY = child.isFixedPositioned() ? 0 : childOffsetY;
        return baseOffsetY + child.getPositionOffsetY() - getScrollTop(child.getElement());
    }

    private void sortStackingItemsIfNeeded(List<StackingScrollItem> items, DocumentStackingPhase phase) {
        if (phase != DocumentStackingPhase.NEGATIVE_POSITIONED
                && phase != DocumentStackingPhase.POSITIVE_POSITIONED) {
            return;
        }
        Collections.sort(items, new Comparator<StackingScrollItem>() {
            @Override
            public int compare(StackingScrollItem first, StackingScrollItem second) {
                return Integer.compare(first.box.getStackingZIndex(), second.box.getStackingZIndex());
            }
        });
    }

    private boolean shouldSearchAsStackingContext(DocumentLayoutBox box) {
        return DocumentEffectChain.resolve(box).isStackingBoundary();
    }

    private boolean canReachChildren(DocumentLayoutBox box, int mouseX, int mouseY, int offsetX, int offsetY) {
        return DocumentEffectChain.resolve(box).canReachChildrenAt(mouseX, mouseY, offsetX, offsetY);
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

        private ScrollbarHit(DocumentLayoutBox box, ScrollbarMetrics metrics, boolean vertical) {
            this.box = box;
            this.metrics = metrics;
            this.vertical = vertical;
        }
    }

    /**
     * 最近 stacking context 中可被阶段排序的滚动命中项。
     */
    private static final class StackingScrollItem {

        private final DocumentLayoutBox box;
        private final int offsetX;
        private final int offsetY;
        private final boolean searchStackingContext;

        private StackingScrollItem(DocumentLayoutBox box, int offsetX, int offsetY, boolean searchStackingContext) {
            this.box = box;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.searchStackingContext = searchStackingContext;
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

        private boolean containsTrack(int mouseX, int mouseY) {
            return containsInRect(mouseX, mouseY, trackLeft, trackTop, trackRight, trackBottom);
        }

        private boolean containsThumb(int mouseX, int mouseY) {
            return containsInRect(mouseX, mouseY, thumbLeft, thumbTop, thumbRight, thumbBottom);
        }

        private static boolean containsInRect(int mouseX, int mouseY, int left, int top, int right, int bottom) {
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
