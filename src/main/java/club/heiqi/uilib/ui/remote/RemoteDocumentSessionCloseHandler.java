package club.heiqi.uilib.ui.remote;

/**
 * 远程文档页面服务端 session 移除回调。
 */
public interface RemoteDocumentSessionCloseHandler {

    /**
     * 远程页面 session 被关闭、替换或过期时触发。
     *
     * @param player session 所属玩家
     * @param sessionId 远程页面 session 标识
     */
    void onClosed(Object player, String sessionId);
}
