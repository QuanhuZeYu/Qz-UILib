package club.heiqi.uilib.ui.control;

/**
 * HTML-like 滑块数值变更处理器。
 */
public interface DocumentSliderChangeHandler {

    /**
     * 处理滑块数值变化。
     *
     * @param event 滑块数值变更事件
     */
    void onSliderChanged(DocumentSliderChangeEvent event);
}
