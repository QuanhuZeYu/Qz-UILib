package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * 动态键值编辑器行数据变更事件。
 */
public final class DocumentKeyValueEditorChangeEvent {

    private final DocumentKeyValueEditorControl source;
    private final ElementNode element;
    private final List<DocumentKeyValueEditorControl.Row> rows;

    DocumentKeyValueEditorChangeEvent(DocumentKeyValueEditorControl source, ElementNode element,
            List<DocumentKeyValueEditorControl.Row> rows) {
        this.source = Objects.requireNonNull(source, "source");
        this.element = Objects.requireNonNull(element, "element");
        this.rows = copyRows(rows);
    }

    /**
     * 返回触发事件的键值编辑器控件。
     *
     * @return 键值编辑器控件
     */
    public DocumentKeyValueEditorControl getSource() {
        return source;
    }

    /**
     * 返回控件根元素。
     *
     * @return 控件根元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 返回当前行数据快照。
     *
     * @return 行数据快照
     */
    public List<DocumentKeyValueEditorControl.Row> getRows() {
        return rows;
    }

    private static List<DocumentKeyValueEditorControl.Row> copyRows(
            List<DocumentKeyValueEditorControl.Row> sourceRows) {
        List<DocumentKeyValueEditorControl.Row> copiedRows = new ArrayList<DocumentKeyValueEditorControl.Row>();
        if (sourceRows != null) {
            for (DocumentKeyValueEditorControl.Row row : sourceRows) {
                copiedRows.add(new DocumentKeyValueEditorControl.Row(row.getKey(), row.getValue(), row.getType()));
            }
        }
        return Collections.unmodifiableList(copiedRows);
    }
}
