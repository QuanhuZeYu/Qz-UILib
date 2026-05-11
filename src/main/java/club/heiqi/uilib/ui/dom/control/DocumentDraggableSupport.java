package club.heiqi.uilib.ui.dom.control;

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
        int nextX = accumulatedX + event.getDeltaDocumentX();
        int nextY = accumulatedY + event.getDeltaDocumentY();
        if (dragAxis == DragAxis.HORIZONTAL || dragAxis == DragAxis.BOTH) {
            accumulatedX = nextX;
            target.style().setLeft(UiStyleLength.px(accumulatedX)).clearRight();
        }
        if (dragAxis == DragAxis.VERTICAL || dragAxis == DragAxis.BOTH) {
            accumulatedY = nextY;
            target.style().setTop(UiStyleLength.px(accumulatedY)).clearBottom();
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
        accumulatedX = resolveInitialOffset(target.style().getLeft());
        accumulatedY = resolveInitialOffset(target.style().getTop());
    }

    private static int resolveInitialOffset(UiStyleLength length) {
        if (length == null || length.getType() != UiStyleLength.Type.PIXEL) {
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
