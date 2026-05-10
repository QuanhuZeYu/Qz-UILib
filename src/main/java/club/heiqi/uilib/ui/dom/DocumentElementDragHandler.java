package club.heiqi.uilib.ui.dom;

/**
 * 元素拖拽处理器。
 */
public interface DocumentElementDragHandler {

    /**
     * 处理拖拽事件。
     *
     * @param event 拖拽事件
     * @return 是否消费
     */
    boolean onDrag(DocumentElementDragEvent event);
}
