package club.heiqi.uilib.ui.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.dom.DocumentElementFocusEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusHandler;
import club.heiqi.uilib.ui.dom.DocumentElementFocusInEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusInHandler;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentScrollState;
import club.heiqi.uilib.ui.style.props.UiVisibility;

/**
 * HTML-like 文档焦点管理器。
 *
 * <p>承载焦点状态、Tab 顺序、focus/focusin 分发与 {@code scrollIntoView} 路径解析。</p>
 */
final class DocumentFocusManager {

    private final UiDocument document;
    private final DocumentScrollState scrollState;
    private final Host host;

    private ElementNode focusedElement;
    private boolean focusedElementFocusVisible;
    private int focusedElementInvalidationVersion;

    DocumentFocusManager(UiDocument document, DocumentScrollState scrollState, Host host) {
        this.document = Objects.requireNonNull(document, "document");
        this.scrollState = Objects.requireNonNull(scrollState, "scrollState");
        this.host = Objects.requireNonNull(host, "host");
    }

    /**
     * 返回当前有效焦点元素。
     *
     * @return 聚焦元素；无有效焦点时返回 null
     */
    ElementNode getFocusedElement() {
        return getActiveFocusedElement();
    }

    /**
     * 返回当前焦点是否处于 focus-visible 状态。
     *
     * @return 是否 focus-visible
     */
    boolean isFocusVisible() {
        return focusedElementFocusVisible;
    }

    /**
     * 程序化请求聚焦目标元素。
     *
     * @param element 目标元素
     * @return 是否成功聚焦
     */
    boolean requestFocus(ElementNode element) {
        if (!isProgrammaticFocusTarget(element)) {
            return false;
        }
        focusElement(element, false);
        scrollElementIntoView(element);
        return getActiveFocusedElement() == element;
    }

    /**
     * 程序化请求目标元素失焦。
     *
     * @param element 目标元素
     * @return 是否执行了失焦
     */
    boolean requestBlur(ElementNode element) {
        if (element == null || element.getOwnerDocument() != document || getActiveFocusedElement() != element) {
            return false;
        }
        focusElement(null, false);
        return true;
    }

    /**
     * 将焦点移动到遍历顺序中的首个或末个元素。
     *
     * @param reverse 是否反向遍历
     * @return 是否存在可聚焦元素
     */
    boolean focusFirstElementInTraversalOrder(boolean reverse) {
        List<ElementNode> focusableElements = collectFocusableElements();
        if (focusableElements.isEmpty()) {
            focusElement(null, false);
            return false;
        }
        focusElement(reverse ? focusableElements.get(focusableElements.size() - 1) : focusableElements.get(0), true);
        return true;
    }

    /**
     * 按 Tab 顺序移动焦点。
     *
     * @param reverse 是否反向遍历
     * @return 是否成功移动焦点
     */
    boolean focusTraversal(boolean reverse) {
        List<ElementNode> focusableElements = collectFocusableElements();
        if (focusableElements.isEmpty()) {
            return false;
        }

        ElementNode activeElement = getActiveFocusedElement();
        int currentIndex = activeElement == null ? -1 : focusableElements.indexOf(activeElement);
        if (currentIndex < 0) {
            focusElement(reverse ? focusableElements.get(focusableElements.size() - 1) : focusableElements.get(0),
                    true);
            return true;
        }

        int nextIndex = reverse ? currentIndex - 1 : currentIndex + 1;
        if (nextIndex < 0 || nextIndex >= focusableElements.size()) {
            return false;
        }
        focusElement(focusableElements.get(nextIndex), true);
        return true;
    }

    /**
     * 判断文档内是否存在可聚焦元素。
     *
     * @return 是否存在可聚焦元素
     */
    boolean hasFocusableElement() {
        return hasFocusableElement(document.getRootElement());
    }

    /**
     * 从命中元素向祖先查找最近可聚焦元素。
     *
     * @param target 命中元素
     * @return 可聚焦元素；不存在时返回 null
     */
    ElementNode resolveFocusableElement(ElementNode target) {
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            if (currentElement.isFocusable() && !currentElement.isDisabled()) {
                return currentElement;
            }
        }
        return null;
    }

    /**
     * 切换焦点元素。
     *
     * @param nextFocusedElement 下一个焦点元素
     * @param focusVisible 是否以 focus-visible 状态聚焦
     */
    void focusElement(ElementNode nextFocusedElement, boolean focusVisible) {
        ElementNode resolvedElement = nextFocusedElement != null && nextFocusedElement.isFocusable()
                && isElementAttachedToDocument(nextFocusedElement) ? nextFocusedElement : null;
        boolean resolvedFocusVisible = resolvedElement != null && focusVisible;
        if (focusedElement == resolvedElement && focusedElementFocusVisible == resolvedFocusVisible) {
            return;
        }

        ElementNode previousElement = focusedElement;
        boolean previousFocusVisible = focusedElementFocusVisible;
        focusedElement = resolvedElement;
        focusedElementFocusVisible = resolvedFocusVisible;
        focusedElementInvalidationVersion = focusedElement == null ? 0 : focusedElement.getFocusInvalidationVersion();
        if (previousElement != null && previousElement != focusedElement) {
            host.clearNativeButtonState(previousElement);
        }
        if (previousElement != focusedElement) {
            dispatchFocusChanged(previousElement, false, false);
        }
        if (focusedElement != null
                && (previousElement != focusedElement || previousFocusVisible != focusedElementFocusVisible)) {
            dispatchFocusChanged(focusedElement, true, focusedElementFocusVisible);
        }
        if (focusedElement != null && focusedElementFocusVisible && focusedElement.isFocusable()
                && isElementAttachedToDocument(focusedElement)) {
            ensureFocusedElementVisible();
        }
        host.syncCursorFromHoveredElement();
    }

    /**
     * 将目标元素滚动到可见区域内。
     *
     * @param target 目标元素
     * @return 是否完成处理
     */
    boolean scrollElementIntoView(ElementNode target) {
        if (target == null || host.getWidth() <= 0 || host.getHeight() <= 0) {
            return false;
        }
        for (int remainingPasses = 16; remainingPasses > 0; remainingPasses--) {
            List<LayoutPathEntry> path = resolveElementLayoutPath(target);
            if (!isVisibleLayoutPath(path)) {
                return false;
            }
            int firstFixedIndex = findFirstFixedIndex(path);
            boolean changed = false;
            for (int index = path.size() - 2; index >= 0; index--) {
                if (firstFixedIndex >= 0 && index < firstFixedIndex) {
                    break;
                }
                if (scrollAncestorToRevealTarget(path.get(index), path.get(path.size() - 1))) {
                    changed = true;
                    break;
                }
            }
            if (!changed) {
                return true;
            }
        }
        return true;
    }

    /**
     * 判断目标元素当前是否拥有可见布局路径。
     *
     * @param target 目标元素
     * @return 是否可见
     */
    boolean isVisibleLayoutTarget(ElementNode target) {
        return isVisibleLayoutPath(resolveElementLayoutPath(target));
    }

    /**
     * 判断目标是否可被程序化聚焦。
     *
     * @param element 目标元素
     * @return 是否可程序化聚焦
     */
    boolean isProgrammaticFocusTarget(ElementNode element) {
        return element != null && element.isFocusable() && !element.isDisabled() && isVisibleLayoutTarget(element);
    }

    private ElementNode getActiveFocusedElement() {
        if (focusedElement != null && (!focusedElement.isFocusable() || !isElementAttachedToDocument(focusedElement))) {
            focusElement(null, false);
        }
        if (focusedElement != null
                && focusedElement.getFocusInvalidationVersion() != focusedElementInvalidationVersion) {
            focusElement(null, false);
        }
        return focusedElement;
    }

    private void ensureFocusedElementVisible() {
        if (focusedElement == null || !focusedElementFocusVisible || host.getWidth() <= 0 || host.getHeight() <= 0) {
            return;
        }
        scrollElementIntoView(focusedElement);
    }

    private List<ElementNode> collectFocusableElements() {
        if (host.getWidth() <= 0 || host.getHeight() <= 0) {
            return Collections.emptyList();
        }
        List<ElementNode> focusableElements = new ArrayList<ElementNode>();
        collectFocusableElements(host.resolveInteractiveLayoutBox(), focusableElements);
        sortFocusableElementsByTabIndex(focusableElements);
        return focusableElements;
    }

    private void collectFocusableElements(DocumentLayoutBox box, List<ElementNode> focusableElements) {
        ElementNode element = box.getElement();
        if (isSequentiallyFocusable(element) && box.getWidth() > 0 && box.getHeight() > 0) {
            focusableElements.add(element);
        }
        for (DocumentLayoutBox child : box.getChildren()) {
            collectFocusableElements(child, focusableElements);
        }
    }

    private List<LayoutPathEntry> resolveElementLayoutPath(ElementNode target) {
        if (target == null || !isElementAttachedToDocument(target)) {
            return Collections.emptyList();
        }
        List<LayoutPathEntry> path = new ArrayList<LayoutPathEntry>();
        if (!collectLayoutPath(host.resolveInteractiveLayoutBox(), target, 0, 0, path)) {
            return Collections.emptyList();
        }
        return path;
    }

    private boolean isVisibleLayoutPath(List<LayoutPathEntry> path) {
        if (path.isEmpty()) {
            return false;
        }
        for (LayoutPathEntry entry : path) {
            if (entry.box.getComputedStyle().getVisibility() == UiVisibility.HIDDEN) {
                return false;
            }
        }
        DocumentLayoutBox targetBox = path.get(path.size() - 1).box;
        return targetBox.getWidth() > 0 && targetBox.getHeight() > 0;
    }

    private boolean collectLayoutPath(DocumentLayoutBox box, ElementNode target, int offsetX, int offsetY,
            List<LayoutPathEntry> path) {
        int baseOffsetX = box.isFixedPositioned() ? 0 : offsetX;
        int baseOffsetY = box.isFixedPositioned() ? 0 : offsetY;
        int boxOffsetX = baseOffsetX + box.getPositionOffsetX();
        int boxOffsetY = baseOffsetY + box.getPositionOffsetY();
        path.add(new LayoutPathEntry(box, boxOffsetX, boxOffsetY));
        if (box.getElement() == target) {
            return true;
        }
        int childOffsetX = boxOffsetX - scrollState.getScrollLeft(box.getElement());
        int childOffsetY = boxOffsetY - scrollState.getScrollTop(box.getElement());
        for (DocumentLayoutBox child : box.getChildren()) {
            if (collectLayoutPath(child, target, childOffsetX, childOffsetY, path)) {
                return true;
            }
        }
        path.remove(path.size() - 1);
        return false;
    }

    private int findFirstFixedIndex(List<LayoutPathEntry> path) {
        for (int index = 1; index < path.size(); index++) {
            if (path.get(index).box.isFixedPositioned()) {
                return index;
            }
        }
        return -1;
    }

    private boolean scrollAncestorToRevealTarget(LayoutPathEntry ancestorEntry, LayoutPathEntry targetEntry) {
        ElementNode ancestorElement = ancestorEntry.box.getElement();
        int currentScrollLeft = scrollState.getScrollLeft(ancestorElement);
        int currentScrollTop = scrollState.getScrollTop(ancestorElement);
        int nextScrollLeft = resolveScrollOffsetForTarget(currentScrollLeft,
                ancestorEntry.box.getContentLeft() + ancestorEntry.boxOffsetX,
                ancestorEntry.box.getContentLeft() + ancestorEntry.boxOffsetX + ancestorEntry.box.getContentWidth(),
                targetEntry.box.getLeft() + targetEntry.boxOffsetX,
                targetEntry.box.getRight() + targetEntry.boxOffsetX,
                scrollState.getMaxScrollLeft(ancestorElement));
        int nextScrollTop = resolveScrollOffsetForTarget(currentScrollTop,
                ancestorEntry.box.getContentTop() + ancestorEntry.boxOffsetY,
                ancestorEntry.box.getContentTop() + ancestorEntry.boxOffsetY + ancestorEntry.box.getContentHeight(),
                targetEntry.box.getTop() + targetEntry.boxOffsetY,
                targetEntry.box.getBottom() + targetEntry.boxOffsetY,
                scrollState.getMaxScrollTop(ancestorElement));
        if (currentScrollLeft == nextScrollLeft && currentScrollTop == nextScrollTop) {
            return false;
        }
        if (!scrollState.setScrollOffset(ancestorElement, nextScrollLeft, nextScrollTop)) {
            return false;
        }
        host.dispatchScroll(ancestorElement, System.nanoTime());
        return true;
    }

    private int resolveScrollOffsetForTarget(int currentOffset, int viewportStart, int viewportEnd, int targetStart,
            int targetEnd, int maxOffset) {
        if (maxOffset <= 0 || (targetStart >= viewportStart && targetEnd <= viewportEnd)) {
            return currentOffset;
        }
        int nextOffset = currentOffset;
        if (targetStart < viewportStart) {
            nextOffset -= viewportStart - targetStart;
        } else if (targetEnd > viewportEnd) {
            nextOffset += targetEnd - viewportEnd;
        }
        return Math.max(0, Math.min(nextOffset, maxOffset));
    }

    private void dispatchFocusChanged(ElementNode target, boolean focused, boolean focusVisible) {
        if (target == null) {
            return;
        }
        DocumentElementFocusHandler focusHandler = target.getFocusHandler();
        if (focusHandler != null) {
            focusHandler.onFocusChanged(new DocumentElementFocusEvent(target, focused, focusVisible));
        }
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            DocumentElementFocusInHandler focusInHandler = currentElement.getFocusInHandler();
            if (focusInHandler == null) {
                continue;
            }
            DocumentElementFocusInEvent focusInEvent = new DocumentElementFocusInEvent(target, currentElement,
                    focused, focusVisible);
            if (focusInHandler.onFocusIn(focusInEvent)) {
                break;
            }
        }
    }

    private boolean isElementAttachedToDocument(ElementNode element) {
        return host.isElementAttachedToDocument(element);
    }

    private boolean hasFocusableElement(DocumentNode node) {
        if (node instanceof ElementNode && ((ElementNode) node).isFocusable()) {
            return true;
        }
        for (DocumentNode child : node.getChildren()) {
            if (hasFocusableElement(child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSequentiallyFocusable(ElementNode element) {
        if (element == null || !element.isFocusable()) {
            return false;
        }
        if (element.isDisabled()) {
            return false;
        }
        Integer tabIndex = element.getTabIndex();
        return tabIndex == null || tabIndex.intValue() >= 0;
    }

    private static void sortFocusableElementsByTabIndex(List<ElementNode> focusableElements) {
        Collections.sort(focusableElements, new java.util.Comparator<ElementNode>() {
            @Override
            public int compare(ElementNode first, ElementNode second) {
                int firstIndex = positiveTabIndexOrZero(first);
                int secondIndex = positiveTabIndexOrZero(second);
                if (firstIndex == secondIndex) {
                    return 0;
                }
                if (firstIndex == 0) {
                    return 1;
                }
                if (secondIndex == 0) {
                    return -1;
                }
                return Integer.compare(firstIndex, secondIndex);
            }
        });
    }

    private static int positiveTabIndexOrZero(ElementNode element) {
        Integer tabIndex = element.getTabIndex();
        if (tabIndex == null || tabIndex.intValue() <= 0) {
            return 0;
        }
        return tabIndex.intValue();
    }

    private static final class LayoutPathEntry {

        private final DocumentLayoutBox box;
        private final int boxOffsetX;
        private final int boxOffsetY;

        private LayoutPathEntry(DocumentLayoutBox box, int boxOffsetX, int boxOffsetY) {
            this.box = box;
            this.boxOffsetX = boxOffsetX;
            this.boxOffsetY = boxOffsetY;
        }
    }

    /** 焦点管理器需要从 widget 借用的最小能力集合。 */
    interface Host {

        /** widget 当前宽度。 */
        int getWidth();

        /** widget 当前高度。 */
        int getHeight();

        /** 解析当前交互布局盒。 */
        DocumentLayoutBox resolveInteractiveLayoutBox();

        /** 判断元素是否仍挂载在当前文档内。 */
        boolean isElementAttachedToDocument(ElementNode element);

        /** 分发滚动事件。 */
        void dispatchScroll(ElementNode target, long timeNanos);

        /** 清理指定元素的原生按钮键盘状态。 */
        void clearNativeButtonState(ElementNode element);

        /** 焦点变化后同步系统光标。 */
        void syncCursorFromHoveredElement();
    }
}
