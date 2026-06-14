package club.heiqi.uilib.ui.control;

/**
 * 源码编辑器错误行集合变更处理器。
 *
 * <p>处理器通过 {@link DocumentCodeEditorControl#setErrorHandler(DocumentCodeEditorErrorHandler)}
 * 注册，仅在错误行集合或错误文案实际变化时触发一次，避免普通文本编辑产生抖动。</p>
 */
public interface DocumentCodeEditorErrorHandler {

    /**
     * 错误行集合或错误提示文案发生变化。
     *
     * @param event 错误更新事件
     */
    void onErrorsUpdated(DocumentCodeEditorErrorUpdateEvent event);
}
