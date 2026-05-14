package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素 dragstart 处理器。
 */
public interface DocumentElementDragStartHandler {

    /**
     * 处理 dragstart 事件。
     *
     * @param event 拖拽事件
     * @return 是否消费
     */
    boolean onDragStart(DocumentElementDragEvent event);
}
