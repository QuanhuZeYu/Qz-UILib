package club.heiqi.uilib.ui.remote;

/**
 * 远程文档表单提交处理器。
 */
public interface RemoteDocumentSubmitHandler {

    /**
     * 处理客户端提交的远程页面表单。
     *
     * @param event 提交事件
     */
    void onSubmit(RemoteDocumentSubmitEvent event);
}
