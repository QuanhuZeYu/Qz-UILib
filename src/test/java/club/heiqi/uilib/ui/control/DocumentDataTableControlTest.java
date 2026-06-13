package club.heiqi.uilib.ui.control;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import club.heiqi.uilib.ui.dom.UiDocument;

/**
 * `DocumentDataTableControl` 的数据操作契约测试。
 */
public class DocumentDataTableControlTest {

    @Test
    public void supportsAddSelectDeleteAndSortRows() {
        final AtomicInteger changeCount = new AtomicInteger(0);
        DocumentDataTableControl table = new DocumentDataTableControl(UiDocument.create(), Arrays.asList("name", "port"))
                .setRows(Arrays.asList(row("name", "beta", "port", "2"), row("name", "alpha", "port", "1")))
                .setChangeHandler(new DocumentDataTableChangeHandler() {
                    @Override
                    public void onTableChanged(DocumentDataTableChangeEvent event) {
                        changeCount.incrementAndGet();
                    }
                });

        table.sortByColumn("name");
        assertEquals("alpha", table.getRowsSnapshot().get(0).get("name"));

        table.addRow();
        assertEquals(3, table.getRowsSnapshot().size());
        table.selectRow(1).deleteSelectedRow();
        assertEquals(2, table.getRowsSnapshot().size());
        assertEquals(3, changeCount.get());
    }

    private static Map<String, String> row(String firstKey, String firstValue, String secondKey, String secondValue) {
        Map<String, String> row = new LinkedHashMap<String, String>();
        row.put(firstKey, firstValue);
        row.put(secondKey, secondValue);
        return row;
    }
}
