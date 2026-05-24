package club.heiqi.uilib.ui.remote;

/**
 * 远程 HUD 表单提交处理器。
 */
public interface RemoteHudSubmitHandler {

    /**
     * 处理客户端提交的远程 HUD 表单。
     *
     * @param event 提交事件
     */
    void onSubmit(RemoteHudSubmitEvent event);
}
