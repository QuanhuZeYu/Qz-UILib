package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素 dragend 处理器。
 */
public interface DocumentElementDragEndHandler {

    /**
     * 处理 dragend 事件。
     *
     * @param event 拖拽事件
     * @return 是否消费
     */
    boolean onDragEnd(DocumentElementDragEvent event);
}
