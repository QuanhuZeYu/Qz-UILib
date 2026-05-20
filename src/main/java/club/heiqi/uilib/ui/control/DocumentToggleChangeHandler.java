package club.heiqi.uilib.ui.control;

/**
 * 开关状态变更处理器。
 */
public interface DocumentToggleChangeHandler {

    /**
     * 开关状态变更时触发。
     *
     * @param event 变更事件
     */
    void onToggleChanged(DocumentToggleChangeEvent event);
}
