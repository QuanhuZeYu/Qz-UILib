package club.heiqi.uilib.ui.layout;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.style.ComputedStyle;
import club.heiqi.uilib.ui.style.UiOverflow;

/**
 * HTML-like 文档滚动状态。
 *
 * <p>该状态只保存元素级滚动偏移和可滚范围，布局盒本身仍表达未滚动的文档坐标。</p>
 */
public final class DocumentScrollState {

    private static final int DEFAULT_SCROLL_STEP = 36;

    private final Map<ElementNode, ScrollEntry> entries = new HashMap<ElementNode, ScrollEntry>();
    private int scrollStep = DEFAULT_SCROLL_STEP;
    private int version;

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
            if (entry.horizontalOffset != 0 || entry.verticalOffset != 0) {
                version++;
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
        Objects.requireNonNull(rootBox, "rootBox");
        if (wheelDelta == 0) {
            return false;
        }
        updateFromLayout(rootBox);
        DocumentLayoutBox target = findScrollableBoxAt(rootBox, mouseX, mouseY, 0, 0);
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
        return updateOffsets(entry, nextHorizontalOffset, nextVerticalOffset);
    }

    private void collectScrollableMetrics(DocumentLayoutBox box, Set<ElementNode> activeElements) {
        ElementNode element = box.getElement();
        activeElements.add(element);
        ScrollMetrics metrics = computeScrollMetrics(box);
        ScrollEntry entry = entries.get(element);
        if (entry == null) {
            entry = new ScrollEntry();
            entries.put(element, entry);
        }
        entry.maxHorizontalOffset = metrics.maxHorizontalOffset;
        entry.maxVerticalOffset = metrics.maxVerticalOffset;
        updateOffsets(entry, entry.horizontalOffset, entry.verticalOffset);

        for (DocumentLayoutBox child : box.getChildren()) {
            collectScrollableMetrics(child, activeElements);
        }
    }

    private ScrollMetrics computeScrollMetrics(DocumentLayoutBox box) {
        ComputedStyle style = box.getComputedStyle();
        int viewportWidth = box.getContentWidth();
        int viewportHeight = box.getContentHeight();
        int contentRight = box.getContentLeft() + viewportWidth;
        int contentBottom = box.getContentTop() + viewportHeight;

        for (DocumentLayoutTextRun textRun : box.getTextRuns()) {
            contentRight = Math.max(contentRight, textRun.getRight());
            contentBottom = Math.max(contentBottom, textRun.getBottom());
        }
        for (DocumentLayoutBox child : box.getChildren()) {
            contentRight = Math.max(contentRight, child.getMarginBoxRight());
            contentBottom = Math.max(contentBottom, child.getMarginBoxBottom());
        }

        int contentWidth = Math.max(viewportWidth, contentRight - box.getContentLeft());
        int contentHeight = Math.max(viewportHeight, contentBottom - box.getContentTop());
        int maxHorizontalOffset = style.getOverflowX() == UiOverflow.AUTO
                ? Math.max(0, contentWidth - viewportWidth)
                : 0;
        int maxVerticalOffset = style.getOverflowY() == UiOverflow.AUTO
                ? Math.max(0, contentHeight - viewportHeight)
                : 0;
        return new ScrollMetrics(viewportWidth, viewportHeight, contentWidth, contentHeight, maxHorizontalOffset,
                maxVerticalOffset);
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
            int offsetY) {
        int viewportLeft = box.getContentLeft() + offsetX;
        int viewportTop = box.getContentTop() + offsetY;
        int viewportRight = viewportLeft + box.getContentWidth();
        int viewportBottom = viewportTop + box.getContentHeight();
        boolean pointerInViewport = containsInRect(mouseX, mouseY, viewportLeft, viewportTop, viewportRight,
                viewportBottom);
        if (!containsInBorderBox(box, mouseX, mouseY, offsetX, offsetY) && !pointerInViewport) {
            return null;
        }

        ComputedStyle style = box.getComputedStyle();
        boolean clippedChildren = style.getOverflowX() != UiOverflow.VISIBLE
                || style.getOverflowY() != UiOverflow.VISIBLE;
        if (!clippedChildren || pointerInViewport) {
            int childOffsetX = offsetX - getScrollLeft(box.getElement());
            int childOffsetY = offsetY - getScrollTop(box.getElement());
            for (int index = box.getChildren().size() - 1; index >= 0; index--) {
                DocumentLayoutBox child = box.getChildren().get(index);
                DocumentLayoutBox hit = findScrollableBoxAt(child, mouseX, mouseY, childOffsetX, childOffsetY);
                if (hit != null) {
                    return hit;
                }
            }
        }

        if (pointerInViewport && isScrollable(box.getElement())) {
            return box;
        }
        return null;
    }

    private boolean isScrollable(ElementNode element) {
        ScrollEntry entry = entries.get(element);
        return entry != null && (entry.maxHorizontalOffset > 0 || entry.maxVerticalOffset > 0);
    }

    private boolean containsInBorderBox(DocumentLayoutBox box, int mouseX, int mouseY, int offsetX, int offsetY) {
        return containsInRect(mouseX, mouseY, box.getLeft() + offsetX, box.getTop() + offsetY,
                box.getRight() + offsetX, box.getBottom() + offsetY);
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
    }

    /**
     * 由布局盒推导出的可滚几何信息。
     */
    private static final class ScrollMetrics {

        private final int viewportWidth;
        private final int viewportHeight;
        private final int contentWidth;
        private final int contentHeight;
        private final int maxHorizontalOffset;
        private final int maxVerticalOffset;

        private ScrollMetrics(int viewportWidth, int viewportHeight, int contentWidth, int contentHeight,
                int maxHorizontalOffset, int maxVerticalOffset) {
            this.viewportWidth = Math.max(0, viewportWidth);
            this.viewportHeight = Math.max(0, viewportHeight);
            this.contentWidth = Math.max(this.viewportWidth, contentWidth);
            this.contentHeight = Math.max(this.viewportHeight, contentHeight);
            this.maxHorizontalOffset = Math.max(0, maxHorizontalOffset);
            this.maxVerticalOffset = Math.max(0, maxVerticalOffset);
        }
    }
}
