package club.heiqi.uilib.ui.control;

/**
 * 源码编辑器内容/语言变更处理器。
 *
 * <p>处理器通过 {@link DocumentCodeEditorControl#setChangeHandler(DocumentCodeEditorChangeHandler)}
 * 注册，每次文本或语言变化时回调一次。错误行集合的变更走
 * {@link DocumentCodeEditorErrorHandler}，不在本接口中表达。</p>
 */
public interface DocumentCodeEditorChangeHandler {

    /**
     * 源码编辑器内容或语言发生变化。
     *
     * @param event 变更事件
     */
    void onContentChanged(DocumentCodeEditorChangeEvent event);
}
