package club.heiqi.uilib.ui.control;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import club.heiqi.uilib.ui.dom.UiDocument;

/**
 * `DocumentKeyValueEditorControl` 的行操作契约测试。
 */
public class DocumentKeyValueEditorControlTest {

    @Test
    public void supportsAddUpdateSelectAndDeleteRows() {
        final AtomicInteger changeCount = new AtomicInteger(0);
        DocumentKeyValueEditorControl editor = new DocumentKeyValueEditorControl(UiDocument.create())
                .setRows(Arrays.asList(new DocumentKeyValueEditorControl.Row("alpha", "1",
                        DocumentKeyValueEditorControl.ValueType.NUMBER)))
                .setChangeHandler(new DocumentKeyValueEditorChangeHandler() {
                    @Override
                    public void onRowsChanged(DocumentKeyValueEditorChangeEvent event) {
                        changeCount.incrementAndGet();
                    }
                });

        editor.addRow();
        editor.updateRow(1, "enabled", "true", DocumentKeyValueEditorControl.ValueType.BOOLEAN);
        editor.selectRow(0).deleteSelectedRow();

        assertEquals(1, editor.getRowsSnapshot().size());
        assertEquals("enabled", editor.getRowsSnapshot().get(0).getKey());
        assertEquals(DocumentKeyValueEditorControl.ValueType.BOOLEAN, editor.getRowsSnapshot().get(0).getType());
        assertEquals(3, changeCount.get());
    }
}
