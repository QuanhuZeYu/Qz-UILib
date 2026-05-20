package club.heiqi.uilib.ui.dom;

/**
 * 文档级链接激活处理器。
 */
public interface DocumentLinkActivationHandler {

    /**
     * 处理链接激活。
     *
     * @param event 链接激活事件
     */
    void onLinkActivated(DocumentLinkActivationEvent event);
}
