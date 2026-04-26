package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素文本输入处理器。
 */
public interface DocumentElementTextInputHandler {

    /**
     * 处理元素文本输入事件。
     *
     * @param event 文本输入事件
     * @return 是否消费该事件；true 会停止向父元素冒泡
     */
    boolean onTextInput(DocumentElementTextInputEvent event);
}
