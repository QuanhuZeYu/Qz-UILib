package club.heiqi.uilib.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;

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
        return hitTestBox(rootBox, scrollState, documentX, documentY, 0, 0, true);
    }

    private static ElementNode hitTestBox(DocumentLayoutBox box, DocumentScrollState scrollState, int documentX,
            int documentY, int offsetX, int offsetY, boolean searchStackingContext) {
        if (isHitTestHidden(box.getElement())) {
            return null;
        }
        int baseOffsetX = box.isFixedPositioned() ? 0 : offsetX;
        int baseOffsetY = box.isFixedPositioned() ? 0 : offsetY;
        int boxOffsetX = baseOffsetX + box.getPositionOffsetX();
        int boxOffsetY = baseOffsetY + box.getPositionOffsetY();
        boolean insideBorderBox = containsInRect(documentX, documentY, box.getLeft() + boxOffsetX,
                box.getTop() + boxOffsetY, box.getRight() + boxOffsetX, box.getBottom() + boxOffsetY);
        if (canHitTestChildren(box, documentX, documentY, boxOffsetX, boxOffsetY)) {
            int childOffsetX = boxOffsetX - getScrollLeft(scrollState, box);
            int childOffsetY = boxOffsetY - getScrollTop(scrollState, box);
            ElementNode childHit = searchStackingContext
                    ? hitStackingContextChildren(box, scrollState, documentX, documentY, childOffsetX, childOffsetY)
                    : hitNormalFlowChildren(box, scrollState, documentX, documentY, childOffsetX, childOffsetY);
            if (childHit != null) {
                return childHit;
            }
            ElementNode inlineTextHit = hitTextRuns(box, documentX, documentY, childOffsetX, childOffsetY);
            if (inlineTextHit != null) {
                return inlineTextHit;
            }
            ElementNode inlineFragmentHit = hitInlineFragments(box, documentX, documentY, childOffsetX, childOffsetY);
            if (inlineFragmentHit != null) {
                return inlineFragmentHit;
            }
        }
        return insideBorderBox ? box.getElement() : null;
    }

    private static ElementNode hitStackingContextChildren(DocumentLayoutBox contextRoot,
            DocumentScrollState scrollState, int documentX, int documentY, int childOffsetX, int childOffsetY) {
        ElementNode hit = hitStackingPhaseItems(contextRoot, scrollState, documentX, documentY, childOffsetX,
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

    private static ElementNode hitNormalFlowChildren(DocumentLayoutBox box, DocumentScrollState scrollState,
            int documentX, int documentY, int childOffsetX, int childOffsetY) {
        List<DocumentLayoutBox> children = box.getChildren();
        for (int index = children.size() - 1; index >= 0; index--) {
            DocumentLayoutBox child = children.get(index);
            if (child.getStackingPhase() != DocumentStackingPhase.NORMAL_FLOW) {
                continue;
            }
            boolean childStackingContext = shouldSearchAsStackingContext(child);
            ElementNode hit = hitTestBox(child, scrollState, documentX, documentY, childOffsetX, childOffsetY,
                    childStackingContext);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private static ElementNode hitStackingPhaseItems(DocumentLayoutBox contextRoot,
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
            ElementNode hit = hitTestBox(item.box, scrollState, documentX, documentY, item.offsetX,
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
            int grandChildOffsetX = resolveChildOffsetX(childOffsetX, scrollState, child);
            int grandChildOffsetY = resolveChildOffsetY(childOffsetY, scrollState, child);
            collectStackingPhaseItems(child, items, scrollState, grandChildOffsetX, grandChildOffsetY, phase);
        }
    }

    private static boolean canHitTestChildren(DocumentLayoutBox box, int documentX, int documentY, int offsetX,
            int offsetY) {
        return DocumentEffectChain.resolve(box).canReachChildrenAt(documentX, documentY, offsetX, offsetY);
    }

    private static ElementNode hitTextRuns(DocumentLayoutBox box, int documentX, int documentY, int offsetX,
            int offsetY) {
        if (isHitTestHidden(box.getElement())) {
            return null;
        }
        List<DocumentLayoutTextRun> textRuns = box.getTextRuns();
        for (int index = textRuns.size() - 1; index >= 0; index--) {
            DocumentLayoutTextRun textRun = textRuns.get(index);
            if (containsInRect(documentX, documentY, textRun.getLeft() + offsetX, textRun.getTop() + offsetY,
                    textRun.getRight() + offsetX, textRun.getBottom() + offsetY)) {
                return textRun.getOwnerElement();
            }
        }
        return null;
    }

    private static ElementNode hitInlineFragments(DocumentLayoutBox box, int documentX, int documentY, int offsetX,
            int offsetY) {
        if (isHitTestHidden(box.getElement())) {
            return null;
        }
        List<DocumentLayoutInlineFragment> inlineFragments = box.getInlineFragments();
        for (int index = inlineFragments.size() - 1; index >= 0; index--) {
            DocumentLayoutInlineFragment inlineFragment = inlineFragments.get(index);
            if (containsInRect(documentX, documentY, inlineFragment.getLeft() + offsetX,
                    inlineFragment.getTop() + offsetY, inlineFragment.getRight() + offsetX,
                    inlineFragment.getBottom() + offsetY)) {
                return inlineFragment.getOwnerElement();
            }
        }
        return null;
    }

    private static int getScrollLeft(DocumentScrollState scrollState, DocumentLayoutBox box) {
        return scrollState == null ? 0 : scrollState.getScrollLeft(box.getElement());
    }

    private static int getScrollTop(DocumentScrollState scrollState, DocumentLayoutBox box) {
        return scrollState == null ? 0 : scrollState.getScrollTop(box.getElement());
    }

    private static int resolveChildOffsetX(int childOffsetX, DocumentScrollState scrollState, DocumentLayoutBox child) {
        int baseOffsetX = child.isFixedPositioned() ? 0 : childOffsetX;
        return baseOffsetX + child.getPositionOffsetX() - getScrollLeft(scrollState, child);
    }

    private static int resolveChildOffsetY(int childOffsetY, DocumentScrollState scrollState, DocumentLayoutBox child) {
        int baseOffsetY = child.isFixedPositioned() ? 0 : childOffsetY;
        return baseOffsetY + child.getPositionOffsetY() - getScrollTop(scrollState, child);
    }

    private static boolean shouldSearchAsStackingContext(DocumentLayoutBox box) {
        return DocumentEffectChain.resolve(box).isStackingBoundary();
    }

    private static boolean isHitTestHidden(ElementNode element) {
        return element != null && "true".equals(element.getAttribute("data-hit-test-hidden"));
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
