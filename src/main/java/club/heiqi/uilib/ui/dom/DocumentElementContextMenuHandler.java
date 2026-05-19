package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素右键菜单处理器。
 */
public interface DocumentElementContextMenuHandler {

    /**
     * 处理元素右键菜单事件。
     *
     * @param event 右键菜单事件
     * @return 是否消费事件；返回 false 时事件会继续向父元素冒泡
     */
    boolean onContextMenu(DocumentElementContextMenuEvent event);
}
