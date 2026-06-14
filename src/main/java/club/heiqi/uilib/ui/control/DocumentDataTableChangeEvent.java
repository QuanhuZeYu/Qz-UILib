package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * 数据表格行数据变更事件。
 */
public final class DocumentDataTableChangeEvent {

    private final DocumentDataTableControl source;
    private final ElementNode element;
    private final List<Map<String, String>> rows;

    DocumentDataTableChangeEvent(DocumentDataTableControl source, ElementNode element,
            List<Map<String, String>> rows) {
        this.source = Objects.requireNonNull(source, "source");
        this.element = Objects.requireNonNull(element, "element");
        this.rows = copyRows(rows);
    }

    /**
     * 返回触发事件的数据表格控件。
     *
     * @return 数据表格控件
     */
    public DocumentDataTableControl getSource() {
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
    public List<Map<String, String>> getRows() {
        return rows;
    }

    private static List<Map<String, String>> copyRows(List<Map<String, String>> sourceRows) {
        List<Map<String, String>> copiedRows = new ArrayList<Map<String, String>>();
        if (sourceRows != null) {
            for (Map<String, String> sourceRow : sourceRows) {
                copiedRows.add(Collections.unmodifiableMap(new LinkedHashMap<String, String>(sourceRow)));
            }
        }
        return Collections.unmodifiableList(copiedRows);
    }
}
