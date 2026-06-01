package club.heiqi.uilib.ui.document;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.ui.dom.DocumentElementActiveEvent;
import club.heiqi.uilib.ui.dom.DocumentElementActiveHandler;
import club.heiqi.uilib.ui.dom.DocumentElementHoverEvent;
import club.heiqi.uilib.ui.dom.DocumentElementHoverHandler;
import club.heiqi.uilib.ui.dom.DocumentElementMouseDownEvent;
import club.heiqi.uilib.ui.dom.DocumentElementMouseDownHandler;
import club.heiqi.uilib.ui.dom.DocumentElementMouseUpEvent;
import club.heiqi.uilib.ui.dom.DocumentElementMouseUpHandler;
import club.heiqi.uilib.ui.dom.DocumentElementWheelEvent;
import club.heiqi.uilib.ui.dom.DocumentElementWheelHandler;
import club.heiqi.uilib.ui.dom.DocumentEventControl;
import club.heiqi.uilib.ui.dom.DocumentEventPhase;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.event.UiMouseEvent;

/**
 * HTML-like 文档鼠标事件分发器。
 *
 * <p>承载 mousedown、mouseup、active 与 hover 分发逻辑，避免 Widget 主体继续堆叠事件传播细节。</p>
 */
final class DocumentMouseEventDispatcher {

    private DocumentMouseEventDispatcher() {}

    static void dispatchActive(ElementNode target, boolean active, UiMouseEvent event) {
        if (target == null || event == null) {
            return;
        }
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            DocumentElementActiveHandler activeHandler = currentElement.getActiveHandler();
            if (activeHandler == null) {
                continue;
            }
            DocumentElementActiveEvent activeEvent = new DocumentElementActiveEvent(target, currentElement, active,
                    event.getButton(), event.getTimeNanos());
            activeHandler.onActiveChanged(activeEvent);
        }
    }

    static boolean dispatchMouseDown(ElementNode target, UiMouseEvent event, int absoluteX, int absoluteY) {
        if (target == null || event == null) {
            return false;
        }
        int documentX = event.getMouseX() - absoluteX;
        int documentY = event.getMouseY() - absoluteY;
        DocumentEventControl eventControl = new DocumentEventControl();
        List<ElementNode> path = buildAncestorPath(target);

        eventControl.setEventPhase(DocumentEventPhase.CAPTURING);
        for (int index = path.size() - 1; index > 0; index--) {
            if (eventControl.isPropagationStopped()) {
                break;
            }
            ElementNode currentElement = path.get(index);
            DocumentElementMouseDownHandler captureHandler = currentElement.getCaptureMouseDownHandler();
            if (captureHandler != null) {
                DocumentElementMouseDownEvent mouseDownEvent = new DocumentElementMouseDownEvent(target,
                        currentElement, documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
                if (captureHandler.onMouseDown(mouseDownEvent)) {
                    eventControl.stopPropagation();
                }
            }
        }

        if (!eventControl.isPropagationStopped()) {
            eventControl.setEventPhase(DocumentEventPhase.AT_TARGET);
            DocumentElementMouseDownHandler targetCaptureHandler = target.getCaptureMouseDownHandler();
            if (targetCaptureHandler != null) {
                DocumentElementMouseDownEvent mouseDownEvent = new DocumentElementMouseDownEvent(target, target,
                        documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
                if (targetCaptureHandler.onMouseDown(mouseDownEvent)) {
                    eventControl.stopPropagation();
                }
            }
            if (!eventControl.isImmediatePropagationStopped()) {
                DocumentElementMouseDownHandler targetHandler = target.getMouseDownHandler();
                if (targetHandler != null) {
                    DocumentElementMouseDownEvent mouseDownEvent = new DocumentElementMouseDownEvent(target, target,
                            documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
                    if (targetHandler.onMouseDown(mouseDownEvent)) {
                        eventControl.stopPropagation();
                    }
                }
            }
        }

        eventControl.setEventPhase(DocumentEventPhase.BUBBLING);
        for (int index = 1; index < path.size(); index++) {
            if (eventControl.isPropagationStopped()) {
                break;
            }
            ElementNode currentElement = path.get(index);
            DocumentElementMouseDownHandler handler = currentElement.getMouseDownHandler();
            if (handler == null) {
                continue;
            }
            DocumentElementMouseDownEvent mouseDownEvent = new DocumentElementMouseDownEvent(target, currentElement,
                    documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
            if (handler.onMouseDown(mouseDownEvent)) {
                eventControl.stopPropagation();
            }
        }
        return eventControl.isPropagationStopped();
    }

    static boolean dispatchMouseUp(ElementNode target, UiMouseEvent event, int absoluteX, int absoluteY) {
        if (target == null || event == null) {
            return false;
        }
        int documentX = event.getMouseX() - absoluteX;
        int documentY = event.getMouseY() - absoluteY;
        DocumentEventControl eventControl = new DocumentEventControl();
        List<ElementNode> path = buildAncestorPath(target);

        eventControl.setEventPhase(DocumentEventPhase.CAPTURING);
        for (int index = path.size() - 1; index > 0; index--) {
            if (eventControl.isPropagationStopped()) {
                break;
            }
            ElementNode currentElement = path.get(index);
            DocumentElementMouseUpHandler captureHandler = currentElement.getCaptureMouseUpHandler();
            if (captureHandler != null) {
                DocumentElementMouseUpEvent mouseUpEvent = new DocumentElementMouseUpEvent(target, currentElement,
                        documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
                if (captureHandler.onMouseUp(mouseUpEvent)) {
                    eventControl.stopPropagation();
                }
            }
        }

        if (!eventControl.isPropagationStopped()) {
            eventControl.setEventPhase(DocumentEventPhase.AT_TARGET);
            DocumentElementMouseUpHandler targetCaptureHandler = target.getCaptureMouseUpHandler();
            if (targetCaptureHandler != null) {
                DocumentElementMouseUpEvent mouseUpEvent = new DocumentElementMouseUpEvent(target, target,
                        documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
                if (targetCaptureHandler.onMouseUp(mouseUpEvent)) {
                    eventControl.stopPropagation();
                }
            }
            if (!eventControl.isImmediatePropagationStopped()) {
                DocumentElementMouseUpHandler targetHandler = target.getMouseUpHandler();
                if (targetHandler != null) {
                    DocumentElementMouseUpEvent mouseUpEvent = new DocumentElementMouseUpEvent(target, target,
                            documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
                    if (targetHandler.onMouseUp(mouseUpEvent)) {
                        eventControl.stopPropagation();
                    }
                }
            }
        }

        eventControl.setEventPhase(DocumentEventPhase.BUBBLING);
        for (int index = 1; index < path.size(); index++) {
            if (eventControl.isPropagationStopped()) {
                break;
            }
            ElementNode currentElement = path.get(index);
            DocumentElementMouseUpHandler handler = currentElement.getMouseUpHandler();
            if (handler == null) {
                continue;
            }
            DocumentElementMouseUpEvent mouseUpEvent = new DocumentElementMouseUpEvent(target, currentElement,
                    documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
            if (handler.onMouseUp(mouseUpEvent)) {
                eventControl.stopPropagation();
            }
        }
        return eventControl.isPropagationStopped();
    }

    static WheelDispatchResult dispatchWheel(ElementNode target, UiMouseEvent event, int absoluteX, int absoluteY) {
        if (target == null || event == null) {
            return WheelDispatchResult.notConsumed();
        }
        int documentX = event.getMouseX() - absoluteX;
        int documentY = event.getMouseY() - absoluteY;
        DocumentEventControl eventControl = new DocumentEventControl();
        List<ElementNode> path = buildAncestorPath(target);

        eventControl.setEventPhase(DocumentEventPhase.CAPTURING);
        for (int index = path.size() - 1; index > 0; index--) {
            if (eventControl.isPropagationStopped()) {
                break;
            }
            ElementNode currentElement = path.get(index);
            DocumentElementWheelHandler captureHandler = currentElement.getCaptureWheelHandler();
            if (captureHandler != null) {
                DocumentElementWheelEvent wheelEvent = new DocumentElementWheelEvent(target, currentElement,
                        documentX, documentY, event.getWheelDelta(), event.getTimeNanos(), eventControl);
                if (captureHandler.onWheel(wheelEvent)) {
                    eventControl.stopPropagation();
                }
            }
        }

        if (!eventControl.isPropagationStopped()) {
            eventControl.setEventPhase(DocumentEventPhase.AT_TARGET);
            DocumentElementWheelHandler targetCaptureHandler = target.getCaptureWheelHandler();
            if (targetCaptureHandler != null) {
                DocumentElementWheelEvent wheelEvent = new DocumentElementWheelEvent(target, target, documentX,
                        documentY, event.getWheelDelta(), event.getTimeNanos(), eventControl);
                if (targetCaptureHandler.onWheel(wheelEvent)) {
                    eventControl.stopPropagation();
                }
            }
            if (!eventControl.isImmediatePropagationStopped()) {
                DocumentElementWheelHandler targetHandler = target.getWheelHandler();
                if (targetHandler != null) {
                    DocumentElementWheelEvent wheelEvent = new DocumentElementWheelEvent(target, target, documentX,
                            documentY, event.getWheelDelta(), event.getTimeNanos(), eventControl);
                    if (targetHandler.onWheel(wheelEvent)) {
                        eventControl.stopPropagation();
                    }
                }
            }
        }

        eventControl.setEventPhase(DocumentEventPhase.BUBBLING);
        for (int index = 1; index < path.size(); index++) {
            if (eventControl.isPropagationStopped()) {
                break;
            }
            ElementNode currentElement = path.get(index);
            DocumentElementWheelHandler handler = currentElement.getWheelHandler();
            if (handler == null) {
                continue;
            }
            DocumentElementWheelEvent wheelEvent = new DocumentElementWheelEvent(target, currentElement, documentX,
                    documentY, event.getWheelDelta(), event.getTimeNanos(), eventControl);
            if (handler.onWheel(wheelEvent)) {
                eventControl.stopPropagation();
            }
        }
        return new WheelDispatchResult(eventControl.isPropagationStopped(), eventControl.isDefaultPrevented());
    }

    static void dispatchHoverChangedWithAncestorAwareness(ElementNode target, boolean hovered,
            ElementNode otherElement, UiMouseEvent event, int absoluteX, int absoluteY) {
        if (target == null) {
            return;
        }
        int documentX = event == null ? -1 : event.getMouseX() - absoluteX;
        int documentY = event == null ? -1 : event.getMouseY() - absoluteY;
        long timeNanos = event == null ? 0L : event.getTimeNanos();
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            if (DocumentCursorResolver.isAncestorOrSelf(currentElement, otherElement)) {
                continue;
            }
            DocumentElementHoverHandler hoverHandler = currentElement.getHoverHandler();
            if (hoverHandler == null) {
                continue;
            }
            DocumentElementHoverEvent hoverEvent = new DocumentElementHoverEvent(target, currentElement, hovered,
                    documentX, documentY, timeNanos);
            hoverHandler.onHoverChanged(hoverEvent);
        }
    }

    private static List<ElementNode> buildAncestorPath(ElementNode target) {
        List<ElementNode> path = new ArrayList<ElementNode>();
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            path.add((ElementNode) current);
        }
        return path;
    }

    static final class WheelDispatchResult {

        private final boolean propagationStopped;
        private final boolean defaultPrevented;

        private WheelDispatchResult(boolean propagationStopped, boolean defaultPrevented) {
            this.propagationStopped = propagationStopped;
            this.defaultPrevented = defaultPrevented;
        }

        private static WheelDispatchResult notConsumed() {
            return new WheelDispatchResult(false, false);
        }

        boolean isPropagationStopped() {
            return propagationStopped;
        }

        boolean isDefaultPrevented() {
            return defaultPrevented;
        }
    }
}
