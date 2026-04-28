package club.heiqi.uilib.ui.style;

/**
 * 样式声明变更监听器。
 */
public interface UiStyleChangeListener {

    /**
     * 响应样式声明变更。
     *
     * @param impact 变更对文档流水线的影响级别
     */
    void onStyleChanged(UiStyleChangeImpact impact);
}
