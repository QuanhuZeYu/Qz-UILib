package club.heiqi.uilib.ui.dom;

/**
 * 文档运行时顶层元素被 DOM detach 生命周期强制清理时的内部回调。
 */
public interface DocumentTopLayerDetachHandler {

    /**
     * 处理顶层元素被强制脱离运行时顶层的场景。
     *
     * @param topLayerElement 被清理的顶层元素
     */
    void onTopLayerDetached(ElementNode topLayerElement);
}
