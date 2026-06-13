package club.heiqi.uilib.ui.control;

/**
 * 颜色选择器确认/提交处理器。
 *
 * <p>处理器通过 {@link DocumentColorPickerControl#setConfirmHandler(DocumentColorPickerConfirmHandler)}
 * 注册，仅在用户失焦或回车时触发。频繁编辑通知走
 * {@link DocumentColorPickerChangeHandler}。</p>
 */
public interface DocumentColorPickerConfirmHandler {

    /**
     * 颜色选择器值被提交。
     *
     * @param event 提交事件
     */
    void onColorConfirmed(DocumentColorPickerConfirmEvent event);
}
