package club.heiqi.uilib.ui.control;

/**
 * 颜色选择器内容变更处理器。
 *
 * <p>处理器通过 {@link DocumentColorPickerControl#setChangeHandler(DocumentColorPickerChangeHandler)}
 * 注册，颜色（ARGB/HEX/RGB）每次变化时回调一次。确认/提交走
 * {@link DocumentColorPickerConfirmHandler}，不在本接口中表达。</p>
 */
public interface DocumentColorPickerChangeHandler {

    /**
     * 颜色发生变化。
     *
     * @param event 变更事件
     */
    void onColorChanged(DocumentColorPickerChangeEvent event);
}
