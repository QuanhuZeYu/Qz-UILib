package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素键盘按键处理器。
 */
public interface DocumentElementKeyHandler {

    /**
     * 处理元素键盘按键事件。
     *
     * @param event 键盘按键事件
     * @return 是否消费该事件；true 会停止向父元素冒泡
     */
    boolean onKey(DocumentElementKeyEvent event);
}
