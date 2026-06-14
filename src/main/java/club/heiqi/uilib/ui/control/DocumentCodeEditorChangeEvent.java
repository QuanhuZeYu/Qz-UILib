package club.heiqi.uilib.ui.control;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * 源码编辑器内容或语言变更事件。
 *
 * <p>当用户交互或外部调用导致文本内容、当前语言发生变化时由
 * {@link DocumentCodeEditorControl} 触发，并通过
 * {@link DocumentCodeEditorChangeHandler#onContentChanged(DocumentCodeEditorChangeEvent)}
 * 回调通知。事件携带快照形式的文本与语言枚举，回调中拿到的是不可变视图。</p>
 */
public final class DocumentCodeEditorChangeEvent {

    private final DocumentCodeEditorControl source;
    private final ElementNode element;
    private final String text;
    private final DocumentCodeEditorSyntaxSupport.Language language;

    DocumentCodeEditorChangeEvent(DocumentCodeEditorControl source, ElementNode element, String text,
            DocumentCodeEditorSyntaxSupport.Language language) {
        this.source = Objects.requireNonNull(source, "source");
        this.element = Objects.requireNonNull(element, "element");
        this.text = text == null ? "" : text;
        this.language = language == null ? DocumentCodeEditorSyntaxSupport.Language.PLAIN : language;
    }

    /**
     * 返回触发事件的源码编辑器控件。
     *
     * @return 源码编辑器控件
     */
    public DocumentCodeEditorControl getSource() {
        return source;
    }

    /**
     * 返回控件根元素。
     *
     * @return 根元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 返回事件触发时刻的文本内容快照。
     *
     * @return 文本内容快照
     */
    public String getText() {
        return text;
    }

    /**
     * 返回事件触发时刻的语言枚举。
     *
     * @return 语言枚举
     */
    public DocumentCodeEditorSyntaxSupport.Language getLanguage() {
        return language;
    }
}
