package club.heiqi.uilib.ui.document;

import java.util.function.IntSupplier;

import club.heiqi.uilib.ui.dom.DocumentElementDragEndHandler;
import club.heiqi.uilib.ui.dom.DocumentElementDragEvent;
import club.heiqi.uilib.ui.dom.DocumentElementDragHandler;
import club.heiqi.uilib.ui.dom.DocumentElementDragOverHandler;
import club.heiqi.uilib.ui.dom.DocumentElementDragStartHandler;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.event.UiMouseEvent;

/**
 * 拖拽控制协作类。
 *
 * <p>从 {@link HtmlLikeDocumentWidget} 提取的 mousedown/move/up 拖拽生命周期管理。当前实现支持两种模式：</p>
 * <ul>
 *     <li>程序化 {@code setDragHandler(...)} 注册：mousedown 时立即询问命中链上首个 drag handler，
 *         经过激活阈值后进入 {@code DRAG} 阶段，mouseup 派发 {@code END} 阶段。</li>
 *     <li>HTML5 {@code draggable="true"} 标记：经阈值后派发 {@code dragstart}，move 阶段沿命中链派发
 *         {@code dragover}，mouseup 派发 {@code dragend}。</li>
 * </ul>
 *
 * <p>本协作类持有自身的拖拽运行态（起点/上次位置/激活标记/HTML5 起源元素），不依赖 widget 字段；通过
 * {@link Host} 回调向 widget 借用绝对坐标、命中查询和按下元素的能力。</p>
 */
final class DocumentDragController {

    /** 拖拽激活的位移阈值（像素），低于该距离视为短点击。 */
    static final int DRAG_ACTIVATION_THRESHOLD_PX = 4;
    private static final int DRAG_ACTIVATION_THRESHOLD_SQUARED =
            DRAG_ACTIVATION_THRESHOLD_PX * DRAG_ACTIVATION_THRESHOLD_PX;

    private final Host host;

    private ElementNode draggingElement;
    private ElementNode htmlDragSourceElement;
    private int dragStartDocumentX;
    private int dragStartDocumentY;
    private int lastDragDocumentX;
    private int lastDragDocumentY;
    private boolean dragActivated;
    private boolean htmlDragStarted;

    DocumentDragController(Host host) {
        this.host = host;
    }

    /**
     * 当前是否处于活动拖拽（已激活阈值或正在 HTML 拖拽中）。
     */
    boolean isActivated() {
        return dragActivated;
    }

    /**
     * mousedown 入口：首先询问命中链 drag handler，再回退 HTML5 {@code draggable} 元素。
     */
    void beginDragIfNeeded(ElementNode target, UiMouseEvent event) {
        clearDragState();
        if (target == null || event == null || event.getButton() != 0) {
            return;
        }
        int documentX = event.getMouseX() - host.getAbsoluteX();
        int documentY = event.getMouseY() - host.getAbsoluteY();
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            DocumentElementDragHandler dragHandler = currentElement.getDragHandler();
            if (dragHandler == null) {
                continue;
            }
            dragStartDocumentX = documentX;
            dragStartDocumentY = documentY;
            lastDragDocumentX = documentX;
            lastDragDocumentY = documentY;
            DocumentElementDragEvent dragEvent = new DocumentElementDragEvent(target, currentElement, documentX,
                    documentY, documentX, documentY, 0, 0, event.getButton(), event.getTimeNanos(),
                    DocumentElementDragEvent.DragPhase.START);
            if (dragHandler.onDrag(dragEvent)) {
                draggingElement = currentElement;
                dragActivated = false;
                return;
            }
        }
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            if (!"true".equals(currentElement.getAttribute("draggable"))) {
                continue;
            }
            dragStartDocumentX = documentX;
            dragStartDocumentY = documentY;
            lastDragDocumentX = documentX;
            lastDragDocumentY = documentY;
            draggingElement = currentElement;
            htmlDragSourceElement = currentElement;
            dragActivated = false;
            htmlDragStarted = false;
            return;
        }
    }

    /**
     * mousemove 入口：达到位移阈值后转入活动拖拽，按当前模式派发对应 drag 事件。
     */
    boolean dispatchDragMove(UiMouseEvent event) {
        if (draggingElement == null || event == null) {
            return false;
        }
        int documentX = event.getMouseX() - host.getAbsoluteX();
        int documentY = event.getMouseY() - host.getAbsoluteY();
        if (!dragActivated) {
            int distanceX = documentX - dragStartDocumentX;
            int distanceY = documentY - dragStartDocumentY;
            if (distanceX * distanceX + distanceY * distanceY < DRAG_ACTIVATION_THRESHOLD_SQUARED) {
                return false;
            }
            dragActivated = true;
        }
        int deltaDocumentX = documentX - lastDragDocumentX;
        int deltaDocumentY = documentY - lastDragDocumentY;
        lastDragDocumentX = documentX;
        lastDragDocumentY = documentY;
        if (htmlDragSourceElement != null) {
            if (deltaDocumentX == 0 && deltaDocumentY == 0) {
                deltaDocumentX = documentX - dragStartDocumentX;
                deltaDocumentY = documentY - dragStartDocumentY;
            }
            dispatchHtmlDragStartIfNeeded(event, documentX, documentY, deltaDocumentX, deltaDocumentY);
            dispatchHtmlDragOver(event, documentX, documentY, deltaDocumentX, deltaDocumentY);
            return true;
        }
        return dispatchDragEvent(draggingElement, host.getPressedElement(), event, documentX, documentY,
                deltaDocumentX, deltaDocumentY, DocumentElementDragEvent.DragPhase.DRAG);
    }

    /**
     * mouseup 入口：派发对应的 {@code dragend} 或 {@code DRAG.END} 阶段后清理状态。
     *
     * @return 短点击应保留 click 链路时返回 false；激活的拖拽已消费 mouseup 时返回 true
     */
    boolean dispatchDragEnd(UiMouseEvent event) {
        if (draggingElement == null || event == null) {
            clearDragState();
            return false;
        }
        int documentX = event.getMouseX() - host.getAbsoluteX();
        int documentY = event.getMouseY() - host.getAbsoluteY();
        int deltaDocumentX = documentX - lastDragDocumentX;
        int deltaDocumentY = documentY - lastDragDocumentY;
        ElementNode dragTarget = host.getPressedElement();
        ElementNode dragHandlerTarget = draggingElement;
        boolean activated = dragActivated;
        ElementNode htmlDragSource = htmlDragSourceElement;
        clearDragState();
        if (htmlDragSource != null) {
            dispatchHtmlDragEnd(htmlDragSource, event, documentX, documentY, deltaDocumentX, deltaDocumentY);
            return activated;
        }
        boolean handled = dispatchDragEvent(dragHandlerTarget, dragTarget, event, documentX, documentY,
                deltaDocumentX, deltaDocumentY, DocumentElementDragEvent.DragPhase.END);
        return activated && handled;
    }

    /**
     * 强制清理拖拽状态（用于切换鼠标按钮、命中失败等中断场景）。
     */
    void clearDragState() {
        draggingElement = null;
        htmlDragSourceElement = null;
        dragActivated = false;
        htmlDragStarted = false;
    }

    private void dispatchHtmlDragStartIfNeeded(UiMouseEvent event, int documentX, int documentY,
            int deltaDocumentX, int deltaDocumentY) {
        if (htmlDragStarted) {
            return;
        }
        htmlDragStarted = true;
        DocumentElementDragStartHandler dragStartHandler = htmlDragSourceElement.getDragStartHandler();
        if (dragStartHandler == null) {
            return;
        }
        dragStartHandler.onDragStart(new DocumentElementDragEvent(htmlDragSourceElement, htmlDragSourceElement,
                dragStartDocumentX, dragStartDocumentY, documentX, documentY, deltaDocumentX, deltaDocumentY,
                event.getButton(), event.getTimeNanos(), DocumentElementDragEvent.DragPhase.START));
    }

    private boolean dispatchHtmlDragOver(UiMouseEvent event, int documentX, int documentY,
            int deltaDocumentX, int deltaDocumentY) {
        ElementNode target = host.findElementAt(event.getMouseX(), event.getMouseY());
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            DocumentElementDragOverHandler dragOverHandler = currentElement.getDragOverHandler();
            if (dragOverHandler == null) {
                continue;
            }
            if (dragOverHandler.onDragOver(new DocumentElementDragEvent(htmlDragSourceElement, currentElement,
                    dragStartDocumentX, dragStartDocumentY, documentX, documentY, deltaDocumentX, deltaDocumentY,
                    event.getButton(), event.getTimeNanos(), DocumentElementDragEvent.DragPhase.DRAG))) {
                return true;
            }
        }
        return false;
    }

    private void dispatchHtmlDragEnd(ElementNode htmlDragSource, UiMouseEvent event, int documentX, int documentY,
            int deltaDocumentX, int deltaDocumentY) {
        DocumentElementDragEndHandler dragEndHandler = htmlDragSource.getDragEndHandler();
        if (dragEndHandler == null) {
            return;
        }
        dragEndHandler.onDragEnd(new DocumentElementDragEvent(htmlDragSource, htmlDragSource, dragStartDocumentX,
                dragStartDocumentY, documentX, documentY, deltaDocumentX, deltaDocumentY, event.getButton(),
                event.getTimeNanos(), DocumentElementDragEvent.DragPhase.END));
    }

    private boolean dispatchDragEvent(ElementNode dragHandlerTarget, ElementNode dragTarget, UiMouseEvent event,
            int documentX, int documentY, int deltaDocumentX, int deltaDocumentY,
            DocumentElementDragEvent.DragPhase phase) {
        if (dragHandlerTarget == null || event == null) {
            return false;
        }
        DocumentElementDragHandler dragHandler = dragHandlerTarget.getDragHandler();
        if (dragHandler == null) {
            return false;
        }
        ElementNode resolvedTarget = dragTarget != null ? dragTarget : dragHandlerTarget;
        return dragHandler.onDrag(new DocumentElementDragEvent(resolvedTarget, dragHandlerTarget,
                dragStartDocumentX, dragStartDocumentY, documentX, documentY, deltaDocumentX, deltaDocumentY,
                event.getButton(), event.getTimeNanos(), phase));
    }

    /**
     * 拖拽控制器需要从 widget 借用的最小能力集合。
     */
    interface Host {

        /** widget 在屏幕坐标系下的左上角 X 坐标。 */
        int getAbsoluteX();

        /** widget 在屏幕坐标系下的左上角 Y 坐标。 */
        int getAbsoluteY();

        /** 当前 mousedown 命中并仍按住的元素，可能为 null。 */
        ElementNode getPressedElement();

        /** 在屏幕坐标处执行命中测试，返回顶层文档元素，可能为 null。 */
        ElementNode findElementAt(int screenX, int screenY);
    }

    /**
     * 内部使用：用于测试或外部脚本以独立坐标供给方式构造 host 适配器。
     */
    static Host hostOf(IntSupplier absoluteX, IntSupplier absoluteY, java.util.function.Supplier<ElementNode> pressed,
            java.util.function.BiFunction<Integer, Integer, ElementNode> hitTester) {
        return new Host() {
            @Override
            public int getAbsoluteX() {
                return absoluteX.getAsInt();
            }

            @Override
            public int getAbsoluteY() {
                return absoluteY.getAsInt();
            }

            @Override
            public ElementNode getPressedElement() {
                return pressed.get();
            }

            @Override
            public ElementNode findElementAt(int screenX, int screenY) {
                return hitTester.apply(screenX, screenY);
            }
        };
    }
}
