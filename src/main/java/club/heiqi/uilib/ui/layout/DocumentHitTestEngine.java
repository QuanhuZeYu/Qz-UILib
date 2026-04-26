package club.heiqi.uilib.ui.layout;

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
        DocumentLayoutBox hitBox = hitTestBox(rootBox, scrollState, documentX, documentY, 0, 0);
        return hitBox == null ? null : hitBox.getElement();
    }

    private static DocumentLayoutBox hitTestBox(DocumentLayoutBox box, DocumentScrollState scrollState, int documentX,
            int documentY, int offsetX, int offsetY) {
        boolean insideBorderBox = containsInRect(documentX, documentY, box.getLeft() + offsetX, box.getTop() + offsetY,
                box.getRight() + offsetX, box.getBottom() + offsetY);
        if (canHitTestChildren(box, documentX, documentY, offsetX, offsetY)) {
            int childOffsetX = offsetX - getScrollLeft(scrollState, box);
            int childOffsetY = offsetY - getScrollTop(scrollState, box);
            for (int index = box.getChildren().size() - 1; index >= 0; index--) {
                DocumentLayoutBox child = box.getChildren().get(index);
                DocumentLayoutBox childHit = hitTestBox(child, scrollState, documentX, documentY, childOffsetX,
                        childOffsetY);
                if (childHit != null) {
                    return childHit;
                }
            }
        }
        return insideBorderBox ? box : null;
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

    private static boolean containsInRect(int x, int y, int left, int top, int right, int bottom) {
        return x >= left && x < right && y >= top && y < bottom;
    }
}
