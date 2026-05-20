package club.heiqi.uilib.ui.control;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.DocumentElementDragEvent;
import club.heiqi.uilib.ui.dom.DocumentElementDragHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.style.UiPosition;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * 通用 HTML-like 元素拖拽辅助器。
 */
public final class DocumentDraggableSupport {

    private final ElementNode target;
    private final ElementNode handle;
    private final DragAxis dragAxis;
    private boolean dragging;
    private int accumulatedX;
    private int accumulatedY;
    private boolean useRightAnchor;
    private boolean useBottomAnchor;
    private boolean initialized;

    private DocumentDraggableSupport(ElementNode target, ElementNode handle, DragAxis dragAxis) {
        this.target = Objects.requireNonNull(target, "target");
        this.handle = Objects.requireNonNull(handle, "handle");
        this.dragAxis = dragAxis == null ? DragAxis.BOTH : dragAxis;
    }

    /**
     * 让目标元素可通过自身被拖拽。
     *
     * @param target 目标元素
     * @return 拖拽辅助器
     */
    public static DocumentDraggableSupport attach(ElementNode target) {
        return attach(target, target, DragAxis.BOTH);
    }

    /**
     * 让目标元素可通过指定把手被拖拽。
     *
     * @param target 目标元素
     * @param handle 拖拽把手
     * @param dragAxis 拖拽轴
     * @return 拖拽辅助器
     */
    public static DocumentDraggableSupport attach(ElementNode target, ElementNode handle, DragAxis dragAxis) {
        DocumentDraggableSupport support = new DocumentDraggableSupport(target, handle, dragAxis);
        support.install();
        return support;
    }

    private void install() {
        handle.setDragHandler(new DocumentElementDragHandler() {
            @Override
            public boolean onDrag(DocumentElementDragEvent event) {
                return handleDrag(event);
            }
        });
    }

    private boolean handleDrag(DocumentElementDragEvent event) {
        if (event == null) {
            return false;
        }
        if (event.getPhase() == DocumentElementDragEvent.DragPhase.START) {
            if (event.getButton() != 0) {
                return false;
            }
            ensureInitialized();
            return true;
        }
        if (!dragging) {
            if (event.getPhase() == DocumentElementDragEvent.DragPhase.DRAG) {
                dragging = true;
            }
        }
        if (!dragging) {
            return false;
        }
        if (event.getPhase() == DocumentElementDragEvent.DragPhase.END) {
            dragging = false;
            return true;
        }
        if (event.getPhase() != DocumentElementDragEvent.DragPhase.DRAG) {
            return false;
        }
        if (dragAxis == DragAxis.HORIZONTAL || dragAxis == DragAxis.BOTH) {
            int nextX = accumulatedX + (useRightAnchor ? -event.getDeltaDocumentX() : event.getDeltaDocumentX());
            accumulatedX = nextX;
            if (useRightAnchor) {
                target.style().setRight(UiStyleLength.px(accumulatedX)).clearLeft();
            } else {
                target.style().setLeft(UiStyleLength.px(accumulatedX)).clearRight();
            }
        }
        if (dragAxis == DragAxis.VERTICAL || dragAxis == DragAxis.BOTH) {
            int nextY = accumulatedY + (useBottomAnchor ? -event.getDeltaDocumentY() : event.getDeltaDocumentY());
            accumulatedY = nextY;
            if (useBottomAnchor) {
                target.style().setBottom(UiStyleLength.px(accumulatedY)).clearTop();
            } else {
                target.style().setTop(UiStyleLength.px(accumulatedY)).clearBottom();
            }
        }
        return true;
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        if (target.style().getPosition() == null) {
            target.style().setPosition(UiPosition.ABSOLUTE);
        }
        useRightAnchor = shouldUseRightAnchor();
        useBottomAnchor = shouldUseBottomAnchor();
        accumulatedX = resolveInitialOffset(useRightAnchor ? target.style().getRight() : target.style().getLeft());
        accumulatedY = resolveInitialOffset(useBottomAnchor ? target.style().getBottom() : target.style().getTop());
    }

    private boolean shouldUseRightAnchor() {
        return target.style().getLeft() == null && isPixelLength(target.style().getRight());
    }

    private boolean shouldUseBottomAnchor() {
        return target.style().getTop() == null && isPixelLength(target.style().getBottom());
    }

    private static boolean isPixelLength(UiStyleLength length) {
        return length != null && length.getType() == UiStyleLength.Type.PIXEL;
    }

    private static int resolveInitialOffset(UiStyleLength length) {
        if (!isPixelLength(length)) {
            return 0;
        }
        return Math.round(length.getValue());
    }

    /**
     * 拖拽轴。
     */
    public enum DragAxis {
        HORIZONTAL,
        VERTICAL,
        BOTH
    }
}
