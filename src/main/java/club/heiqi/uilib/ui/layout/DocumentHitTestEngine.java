package club.heiqi.uilib.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.style.ComputedStyle;
import club.heiqi.uilib.ui.style.UiOverflow;

/**
 * HTML-like 布局盒命中测试引擎。
 */
public final class DocumentHitTestEngine {

    private DocumentHitTestEngine() {}

    /**
     * 在布局盒树中查找命中的最深元素。
     *
     * @param rootBox 根布局盒
     * @param scrollState 滚动状态；为 null 时按无滚动处理
     * @param documentX 文档局部 X
     * @param documentY 文档局部 Y
     * @return 命中的最深元素；未命中时返回 null
     */
    public static ElementNode hitTest(DocumentLayoutBox rootBox, DocumentScrollState scrollState, int documentX,
            int documentY) {
        Objects.requireNonNull(rootBox, "rootBox");
        DocumentLayoutBox hitBox = hitTestBox(rootBox, scrollState, documentX, documentY, 0, 0, true);
        return hitBox == null ? null : hitBox.getElement();
    }

    private static DocumentLayoutBox hitTestBox(DocumentLayoutBox box, DocumentScrollState scrollState, int documentX,
            int documentY, int offsetX, int offsetY, boolean searchStackingContext) {
        int boxOffsetX = offsetX + box.getPositionOffsetX();
        int boxOffsetY = offsetY + box.getPositionOffsetY();
        boolean insideBorderBox = containsInRect(documentX, documentY, box.getLeft() + boxOffsetX,
                box.getTop() + boxOffsetY, box.getRight() + boxOffsetX, box.getBottom() + boxOffsetY);
        if (canHitTestChildren(box, documentX, documentY, boxOffsetX, boxOffsetY)) {
            int childOffsetX = boxOffsetX - getScrollLeft(scrollState, box);
            int childOffsetY = boxOffsetY - getScrollTop(scrollState, box);
            DocumentLayoutBox childHit = searchStackingContext
                    ? hitStackingContextChildren(box, scrollState, documentX, documentY, childOffsetX, childOffsetY)
                    : hitNormalFlowChildren(box, scrollState, documentX, documentY, childOffsetX, childOffsetY);
            if (childHit != null) {
                return childHit;
            }
        }
        return insideBorderBox ? box : null;
    }

    private static DocumentLayoutBox hitStackingContextChildren(DocumentLayoutBox contextRoot,
            DocumentScrollState scrollState, int documentX, int documentY, int childOffsetX, int childOffsetY) {
        DocumentLayoutBox hit = hitStackingPhaseItems(contextRoot, scrollState, documentX, documentY, childOffsetX,
                childOffsetY, DocumentStackingPhase.POSITIVE_POSITIONED);
        if (hit != null) {
            return hit;
        }
        hit = hitStackingPhaseItems(contextRoot, scrollState, documentX, documentY, childOffsetX, childOffsetY,
                DocumentStackingPhase.POSITIONED_AUTO_OR_ZERO);
        if (hit != null) {
            return hit;
        }
        hit = hitNormalFlowChildren(contextRoot, scrollState, documentX, documentY, childOffsetX, childOffsetY);
        if (hit != null) {
            return hit;
        }
        return hitStackingPhaseItems(contextRoot, scrollState, documentX, documentY, childOffsetX, childOffsetY,
                DocumentStackingPhase.NEGATIVE_POSITIONED);
    }

    private static DocumentLayoutBox hitNormalFlowChildren(DocumentLayoutBox box, DocumentScrollState scrollState,
            int documentX, int documentY, int childOffsetX, int childOffsetY) {
        List<DocumentLayoutBox> children = box.getChildren();
        for (int index = children.size() - 1; index >= 0; index--) {
            DocumentLayoutBox child = children.get(index);
            if (child.getStackingPhase() != DocumentStackingPhase.NORMAL_FLOW) {
                continue;
            }
            boolean childStackingContext = shouldSearchAsStackingContext(child);
            DocumentLayoutBox hit = hitTestBox(child, scrollState, documentX, documentY, childOffsetX, childOffsetY,
                    childStackingContext);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private static DocumentLayoutBox hitStackingPhaseItems(DocumentLayoutBox contextRoot,
            DocumentScrollState scrollState, int documentX, int documentY, int childOffsetX, int childOffsetY,
            DocumentStackingPhase phase) {
        List<StackingHitItem> items = new ArrayList<StackingHitItem>();
        collectStackingPhaseItems(contextRoot, items, scrollState, childOffsetX, childOffsetY, phase);
        if (phase == DocumentStackingPhase.NEGATIVE_POSITIONED
                || phase == DocumentStackingPhase.POSITIVE_POSITIONED) {
            Collections.sort(items, new Comparator<StackingHitItem>() {
                @Override
                public int compare(StackingHitItem first, StackingHitItem second) {
                    return Integer.compare(first.box.getStackingZIndex(), second.box.getStackingZIndex());
                }
            });
        }
        for (int index = items.size() - 1; index >= 0; index--) {
            StackingHitItem item = items.get(index);
            DocumentLayoutBox hit = hitTestBox(item.box, scrollState, documentX, documentY, item.offsetX,
                    item.offsetY, item.searchStackingContext);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private static void collectStackingPhaseItems(DocumentLayoutBox currentBox, List<StackingHitItem> items,
            DocumentScrollState scrollState, int childOffsetX, int childOffsetY, DocumentStackingPhase phase) {
        for (DocumentLayoutBox child : currentBox.getChildren()) {
            boolean childStackingContext = shouldSearchAsStackingContext(child);
            if (child.getStackingPhase() == phase) {
                items.add(new StackingHitItem(child, childOffsetX, childOffsetY, childStackingContext));
            }
            if (childStackingContext) {
                continue;
            }
            int grandChildOffsetX = childOffsetX + child.getPositionOffsetX() - getScrollLeft(scrollState, child);
            int grandChildOffsetY = childOffsetY + child.getPositionOffsetY() - getScrollTop(scrollState, child);
            collectStackingPhaseItems(child, items, scrollState, grandChildOffsetX, grandChildOffsetY, phase);
        }
    }

    private static boolean canHitTestChildren(DocumentLayoutBox box, int documentX, int documentY, int offsetX,
            int offsetY) {
        ComputedStyle style = box.getComputedStyle();
        if (style.getOverflowX() == UiOverflow.VISIBLE && style.getOverflowY() == UiOverflow.VISIBLE) {
            return true;
        }

        int left = style.getOverflowX() == UiOverflow.VISIBLE
                ? Integer.MIN_VALUE / 4
                : box.getLeft() + box.getBorder().getLeft() + offsetX;
        int right = style.getOverflowX() == UiOverflow.VISIBLE
                ? Integer.MAX_VALUE / 4
                : box.getRight() - box.getBorder().getRight() + offsetX;
        int top = style.getOverflowY() == UiOverflow.VISIBLE
                ? Integer.MIN_VALUE / 4
                : box.getTop() + box.getBorder().getTop() + offsetY;
        int bottom = style.getOverflowY() == UiOverflow.VISIBLE
                ? Integer.MAX_VALUE / 4
                : box.getBottom() - box.getBorder().getBottom() + offsetY;
        return containsInRect(documentX, documentY, left, top, right, bottom);
    }

    private static int getScrollLeft(DocumentScrollState scrollState, DocumentLayoutBox box) {
        return scrollState == null ? 0 : scrollState.getScrollLeft(box.getElement());
    }

    private static int getScrollTop(DocumentScrollState scrollState, DocumentLayoutBox box) {
        return scrollState == null ? 0 : scrollState.getScrollTop(box.getElement());
    }

    private static boolean shouldSearchAsStackingContext(DocumentLayoutBox box) {
        return box.createsStackingContext() || clipsChildren(box);
    }

    private static boolean clipsChildren(DocumentLayoutBox box) {
        ComputedStyle style = box.getComputedStyle();
        if (style.getOverflowX() == UiOverflow.VISIBLE && style.getOverflowY() == UiOverflow.VISIBLE) {
            return false;
        }
        return !box.getChildren().isEmpty() || !box.getTextRuns().isEmpty()
                || box.getElement().getCustomRenderer() != null;
    }

    private static boolean containsInRect(int x, int y, int left, int top, int right, int bottom) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    /**
     * 最近 stacking context 中可被阶段排序的命中项。
     */
    private static final class StackingHitItem {

        private final DocumentLayoutBox box;
        private final int offsetX;
        private final int offsetY;
        private final boolean searchStackingContext;

        private StackingHitItem(DocumentLayoutBox box, int offsetX, int offsetY, boolean searchStackingContext) {
            this.box = box;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.searchStackingContext = searchStackingContext;
        }
    }
}
