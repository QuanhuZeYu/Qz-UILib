package club.heiqi.uilib.ui.control;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * 源码编辑器错误行集合变更事件。
 *
 * <p>当通过 {@link DocumentCodeEditorControl#setErrorLines(Set)} 或
 * {@link DocumentCodeEditorControl#setError(String)} 修改错误行集合，且新集合与旧集合不一致时触发。
 * 事件同时携带错误提示文案，便于 handler 直接展示。</p>
 */
public final class DocumentCodeEditorErrorUpdateEvent {

    private final DocumentCodeEditorControl source;
    private final ElementNode element;
    private final Set<Integer> errorLines;
    private final String errorMessage;

    DocumentCodeEditorErrorUpdateEvent(DocumentCodeEditorControl source, ElementNode element,
            Set<Integer> errorLines, String errorMessage) {
        this.source = Objects.requireNonNull(source, "source");
        this.element = Objects.requireNonNull(element, "element");
        this.errorLines = safeCopy(errorLines);
        this.errorMessage = errorMessage == null ? "" : errorMessage;
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
     * 返回当前错误行集合（不可变，按升序）。
     *
     * @return 错误行集合快照
     */
    public Set<Integer> getErrorLines() {
        return errorLines;
    }

    /**
     * 返回当前错误提示文案，可能为空串。
     *
     * @return 错误提示文案
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    private static Set<Integer> safeCopy(Set<Integer> source) {
        Set<Integer> sorted = new LinkedHashSet<Integer>();
        if (source != null) {
            for (Integer line : source) {
                if (line != null && line >= 0) {
                    sorted.add(line);
                }
            }
        }
        return Collections.unmodifiableSet(sorted);
    }
}
