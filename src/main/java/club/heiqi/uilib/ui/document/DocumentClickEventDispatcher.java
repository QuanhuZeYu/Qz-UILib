package club.heiqi.uilib.ui.document;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementContextMenuEvent;
import club.heiqi.uilib.ui.dom.DocumentElementContextMenuHandler;
import club.heiqi.uilib.ui.dom.DocumentElementDoubleClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementDoubleClickHandler;
import club.heiqi.uilib.ui.dom.DocumentEventControl;
import club.heiqi.uilib.ui.dom.DocumentEventPhase;
import club.heiqi.uilib.ui.dom.DocumentLinkActivationEvent;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiMouseEvent;

/**
 * HTML-like 文档点击事件分发器。
 *
 * <p>承载 click、dblclick、contextmenu 与链接默认激活逻辑，避免 widget 主体持有点击状态细节。</p>
 */
final class DocumentClickEventDispatcher {

    static final int PRIMARY_BUTTON = 0;
    static final int CONTEXT_MENU_BUTTON = 1;

    private static final long DOUBLE_CLICK_THRESHOLD_NANOS = 500_000_000L;
    private static final int DOUBLE_CLICK_POSITION_THRESHOLD_PX = 4;

    private final UiDocument document;

    private ElementNode lastClickedElement;
    private int lastClickButton = -1;
    private int lastClickDocumentX = Integer.MIN_VALUE;
    private int lastClickDocumentY = Integer.MIN_VALUE;
    private long lastClickTimeNanos = Long.MIN_VALUE;

    DocumentClickEventDispatcher(UiDocument document) {
        this.document = Objects.requireNonNull(document, "document");
    }

    /**
     * 分发标准 click 事件，并在未阻止默认行为时触发最近链接元素的默认激活。
     *
     * @param target 事件目标元素
     * @param event 原始鼠标事件
     * @param absoluteX widget 屏幕左上角 X
     * @param absoluteY widget 屏幕左上角 Y
     * @return 传播是否被停止
     */
    boolean dispatchClick(ElementNode target, UiMouseEvent event, int absoluteX, int absoluteY) {
        if (target == null || event == null) {
            return false;
        }
        return dispatchClick(target, event.getMouseX() - absoluteX, event.getMouseY() - absoluteY,
                event.getButton(), event.getTimeNanos());
    }

    /**
     * 分发不依赖鼠标坐标的合成 click 事件。
     */
    boolean dispatchSyntheticClick(ElementNode target, long timeNanos) {
        if (target == null) {
            return false;
        }
        return dispatchClick(target, -1, -1, PRIMARY_BUTTON, timeNanos);
    }

    /**
     * 根据按下与释放目标解析最终 click target。
     */
    ElementNode resolveClickTarget(ElementNode pressedElement, ElementNode releasedElement) {
        return findNearestCommonInclusiveAncestor(pressedElement, releasedElement);
    }

    private boolean dispatchClick(ElementNode target, int documentX, int documentY, int button, long timeNanos) {
        DocumentEventControl eventControl = new DocumentEventControl();
        List<ElementNode> path = buildAncestorPath(target);

        eventControl.setEventPhase(DocumentEventPhase.CAPTURING);
        for (int index = path.size() - 1; index > 0; index--) {
            if (eventControl.isPropagationStopped()) {
                break;
            }
            ElementNode currentElement = path.get(index);
            DocumentElementClickHandler captureHandler = currentElement.getCaptureClickHandler();
            if (captureHandler != null) {
                DocumentElementClickEvent clickEvent = new DocumentElementClickEvent(target, currentElement,
                        documentX, documentY, button, timeNanos, eventControl);
                if (captureHandler.onClick(clickEvent)) {
                    eventControl.stopPropagation();
                }
            }
        }

        if (!eventControl.isPropagationStopped()) {
            eventControl.setEventPhase(DocumentEventPhase.AT_TARGET);
            DocumentElementClickHandler targetCaptureHandler = target.getCaptureClickHandler();
            if (targetCaptureHandler != null) {
                DocumentElementClickEvent clickEvent = new DocumentElementClickEvent(target, target,
                        documentX, documentY, button, timeNanos, eventControl);
                if (targetCaptureHandler.onClick(clickEvent)) {
                    eventControl.stopPropagation();
                }
            }
            if (!eventControl.isImmediatePropagationStopped()) {
                DocumentElementClickHandler targetHandler = target.getClickHandler();
                if (targetHandler != null) {
                    DocumentElementClickEvent clickEvent = new DocumentElementClickEvent(target, target,
                            documentX, documentY, button, timeNanos, eventControl);
                    if (targetHandler.onClick(clickEvent)) {
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
            DocumentElementClickHandler clickHandler = currentElement.getClickHandler();
            if (clickHandler == null) {
                continue;
            }
            DocumentElementClickEvent clickEvent = new DocumentElementClickEvent(target, currentElement,
                    documentX, documentY, button, timeNanos, eventControl);
            if (clickHandler.onClick(clickEvent)) {
                eventControl.stopPropagation();
            }
        }
        if (!eventControl.isDefaultPrevented()) {
            activateNearestLink(target, timeNanos);
        }
        return eventControl.isPropagationStopped();
    }

    /**
     * 分发 click 后置事件，包括 dblclick 识别与 contextmenu 回退。
     *
     * @param target 事件目标元素
     * @param event 原始鼠标事件
     * @param absoluteX widget 屏幕左上角 X
     * @param absoluteY widget 屏幕左上角 Y
     */
    void dispatchPostClickEvents(ElementNode target, UiMouseEvent event, int absoluteX, int absoluteY) {
        if (target == null || event == null) {
            return;
        }
        int documentX = event.getMouseX() - absoluteX;
        int documentY = event.getMouseY() - absoluteY;
        if (event.getButton() == CONTEXT_MENU_BUTTON) {
            dispatchContextMenu(target, documentX, documentY, event);
        }
        if (shouldDispatchDoubleClick(target, event, documentX, documentY)) {
            dispatchDoubleClick(target, documentX, documentY, event);
            clearLastClickState();
            return;
        }
        rememberLastClick(target, event.getButton(), documentX, documentY, event.getTimeNanos());
    }

    /**
     * 分发 contextmenu 事件。
     *
     * @param target 事件目标元素
     * @param event 原始鼠标事件
     * @param absoluteX widget 屏幕左上角 X
     * @param absoluteY widget 屏幕左上角 Y
     * @return 传播是否被停止
     */
    boolean dispatchContextMenu(ElementNode target, UiMouseEvent event, int absoluteX, int absoluteY) {
        if (target == null || event == null) {
            return false;
        }
        return dispatchContextMenu(target, event.getMouseX() - absoluteX, event.getMouseY() - absoluteY, event);
    }

    /** 清理双击识别所需的上一次点击状态。 */
    void clearLastClickState() {
        lastClickedElement = null;
        lastClickButton = -1;
        lastClickDocumentX = Integer.MIN_VALUE;
        lastClickDocumentY = Integer.MIN_VALUE;
        lastClickTimeNanos = Long.MIN_VALUE;
    }

    private void activateNearestLink(ElementNode target, long timeNanos) {
        ElementNode linkElement = findNearestLinkElement(target);
        if (linkElement == null) {
            return;
        }
        String href = normalizeLinkHref(linkElement.getAttribute("href"));
        if (href.isEmpty()) {
            return;
        }
        if (href.startsWith("#")) {
            String id = href.substring(1).trim();
            if (!id.isEmpty()) {
                ElementNode fragmentTarget = document.getElementById(id);
                if (fragmentTarget != null) {
                    fragmentTarget.scrollIntoView();
                }
            }
        }
        DocumentLinkActivationEvent activationEvent = new DocumentLinkActivationEvent(linkElement, href,
                linkElement.getAttribute("target"), timeNanos);
        document.__dispatchLinkActivation(activationEvent);
    }

    private boolean shouldDispatchDoubleClick(ElementNode target, UiMouseEvent event, int documentX, int documentY) {
        if (target == null || event == null || event.getButton() != PRIMARY_BUTTON || lastClickedElement != target
                || lastClickButton != event.getButton()) {
            return false;
        }
        long elapsedNanos = event.getTimeNanos() - lastClickTimeNanos;
        if (elapsedNanos < 0L || elapsedNanos > DOUBLE_CLICK_THRESHOLD_NANOS) {
            return false;
        }
        int deltaX = documentX - lastClickDocumentX;
        int deltaY = documentY - lastClickDocumentY;
        return deltaX * deltaX + deltaY * deltaY
                <= DOUBLE_CLICK_POSITION_THRESHOLD_PX * DOUBLE_CLICK_POSITION_THRESHOLD_PX;
    }

    private void rememberLastClick(ElementNode target, int button, int documentX, int documentY, long timeNanos) {
        lastClickedElement = target;
        lastClickButton = button;
        lastClickDocumentX = documentX;
        lastClickDocumentY = documentY;
        lastClickTimeNanos = timeNanos;
    }

    private boolean dispatchDoubleClick(ElementNode target, int documentX, int documentY, UiMouseEvent event) {
        DocumentEventControl eventControl = new DocumentEventControl();
        List<ElementNode> path = buildAncestorPath(target);

        eventControl.setEventPhase(DocumentEventPhase.CAPTURING);
        for (int index = path.size() - 1; index > 0; index--) {
            if (eventControl.isPropagationStopped()) {
                break;
            }
            ElementNode currentElement = path.get(index);
            DocumentElementDoubleClickHandler captureHandler = currentElement.getCaptureDoubleClickHandler();
            if (captureHandler == null) {
                continue;
            }
            DocumentElementDoubleClickEvent doubleClickEvent = new DocumentElementDoubleClickEvent(target,
                    currentElement, documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
            if (captureHandler.onDoubleClick(doubleClickEvent)) {
                eventControl.stopPropagation();
            }
        }

        if (!eventControl.isPropagationStopped()) {
            eventControl.setEventPhase(DocumentEventPhase.AT_TARGET);
            DocumentElementDoubleClickHandler targetCaptureHandler = target.getCaptureDoubleClickHandler();
            if (targetCaptureHandler != null) {
                DocumentElementDoubleClickEvent doubleClickEvent = new DocumentElementDoubleClickEvent(target, target,
                        documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
                if (targetCaptureHandler.onDoubleClick(doubleClickEvent)) {
                    eventControl.stopPropagation();
                }
            }
            if (!eventControl.isImmediatePropagationStopped()) {
                DocumentElementDoubleClickHandler targetHandler = target.getDoubleClickHandler();
                if (targetHandler != null) {
                    DocumentElementDoubleClickEvent doubleClickEvent = new DocumentElementDoubleClickEvent(target,
                            target, documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
                    if (targetHandler.onDoubleClick(doubleClickEvent)) {
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
            DocumentElementDoubleClickHandler handler = currentElement.getDoubleClickHandler();
            if (handler == null) {
                continue;
            }
            DocumentElementDoubleClickEvent doubleClickEvent = new DocumentElementDoubleClickEvent(target,
                    currentElement, documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
            if (handler.onDoubleClick(doubleClickEvent)) {
                eventControl.stopPropagation();
            }
        }
        return eventControl.isPropagationStopped();
    }

    private boolean dispatchContextMenu(ElementNode target, int documentX, int documentY, UiMouseEvent event) {
        DocumentEventControl eventControl = new DocumentEventControl();
        List<ElementNode> path = buildAncestorPath(target);

        eventControl.setEventPhase(DocumentEventPhase.CAPTURING);
        for (int index = path.size() - 1; index > 0; index--) {
            if (eventControl.isPropagationStopped()) {
                break;
            }
            ElementNode currentElement = path.get(index);
            DocumentElementContextMenuHandler captureHandler = currentElement.getCaptureContextMenuHandler();
            if (captureHandler == null) {
                continue;
            }
            DocumentElementContextMenuEvent contextMenuEvent = new DocumentElementContextMenuEvent(target,
                    currentElement, documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
            if (captureHandler.onContextMenu(contextMenuEvent)) {
                eventControl.stopPropagation();
            }
        }

        if (!eventControl.isPropagationStopped()) {
            eventControl.setEventPhase(DocumentEventPhase.AT_TARGET);
            DocumentElementContextMenuHandler targetCaptureHandler = target.getCaptureContextMenuHandler();
            if (targetCaptureHandler != null) {
                DocumentElementContextMenuEvent contextMenuEvent = new DocumentElementContextMenuEvent(target,
                        target, documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
                if (targetCaptureHandler.onContextMenu(contextMenuEvent)) {
                    eventControl.stopPropagation();
                }
            }
            if (!eventControl.isImmediatePropagationStopped()) {
                DocumentElementContextMenuHandler targetHandler = target.getContextMenuHandler();
                if (targetHandler != null) {
                    DocumentElementContextMenuEvent contextMenuEvent = new DocumentElementContextMenuEvent(target,
                            target, documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
                    if (targetHandler.onContextMenu(contextMenuEvent)) {
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
            DocumentElementContextMenuHandler handler = currentElement.getContextMenuHandler();
            if (handler == null) {
                continue;
            }
            DocumentElementContextMenuEvent contextMenuEvent = new DocumentElementContextMenuEvent(target,
                    currentElement, documentX, documentY, event.getButton(), event.getTimeNanos(), eventControl);
            if (handler.onContextMenu(contextMenuEvent)) {
                eventControl.stopPropagation();
            }
        }
        return eventControl.isPropagationStopped();
    }

    private ElementNode findNearestLinkElement(ElementNode target) {
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            ElementNode element = (ElementNode) current;
            if ("a".equals(element.getTagName())) {
                return element;
            }
        }
        return null;
    }

    private static String normalizeLinkHref(String href) {
        if (href == null) {
            return "";
        }
        String trimmed = href.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    private static List<ElementNode> buildAncestorPath(ElementNode target) {
        List<ElementNode> path = new ArrayList<ElementNode>();
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            path.add((ElementNode) current);
        }
        return path;
    }

    private static ElementNode findNearestCommonInclusiveAncestor(ElementNode first, ElementNode second) {
        if (first == null || second == null) {
            return null;
        }
        List<ElementNode> secondPath = buildAncestorPath(second);
        for (DocumentNode current = first; current instanceof ElementNode; current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            for (ElementNode candidate : secondPath) {
                if (candidate == currentElement) {
                    return currentElement;
                }
            }
        }
        return null;
    }
}
