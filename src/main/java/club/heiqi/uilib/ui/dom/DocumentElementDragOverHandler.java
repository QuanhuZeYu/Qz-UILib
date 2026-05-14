package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素 dragover 处理器。
 */
public interface DocumentElementDragOverHandler {

    /**
     * 处理 dragover 事件。
     *
     * @param event 拖拽事件
     * @return 是否消费
     */
    boolean onDragOver(DocumentElementDragEvent event);
}
