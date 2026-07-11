package club.heiqi.config.ui.field;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** 结构化列表 keyed 模型的增删、排序、编辑与未知 member 保留测试。 */
public class StructuredListModelTest {

    private static Map<String, Object> row(String id) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("id", id);
        value.put("members", new ArrayList<String>(Arrays.asList(id + "-member")));
        value.put("future", new LinkedHashMap<String, Object>());
        return value;
    }

    @Test
    public void modelSupportsAddRemoveMoveAndMemberEdit() {
        List<StructuredListModel.Row> rows = StructuredListModel.fromValue(
                Arrays.<Object>asList(row("a"), row("b"), row("c")));
        long a = rows.get(0).key();
        long b = rows.get(1).key();
        long c = rows.get(2).key();

        rows = StructuredListModel.moveDown(rows, a);
        assertEquals(Arrays.asList("b", "a", "c"), ids(rows));
        assertEquals(a, rows.get(1).key());
        rows = StructuredListModel.moveUp(rows, c);
        assertEquals(Arrays.asList("b", "c", "a"), ids(rows));
        assertEquals(c, rows.get(1).key());
        rows = StructuredListModel.updateMember(rows, b, "id", "b2");
        assertEquals("b2", rows.get(0).get("id"));
        assertTrue(rows.get(0).value().containsKey("future"));
        rows = StructuredListModel.remove(rows, a);
        assertEquals(Arrays.asList("b2", "c"), ids(rows));
        assertFalse(StructuredListModel.valuesEqual(rows, Arrays.<Object>asList(row("x"))));
    }

    @Test
    public void syncReusesKeysByPositionAndCopiesNestedValues() {
        Map<String, Object> first = row("a");
        List<StructuredListModel.Row> rows = StructuredListModel.fromValue(
                Arrays.<Object>asList(first));
        long key = rows.get(0).key();
        @SuppressWarnings("unchecked")
        List<String> members = (List<String>) first.get("members");
        members.add("outside");

        assertEquals(1, ((List<?>) rows.get(0).get("members")).size());
        rows = StructuredListModel.sync(rows, Arrays.<Object>asList(row("replacement")));
        assertEquals(key, rows.get(0).key());
        assertEquals("replacement", rows.get(0).get("id"));
    }

    private static List<String> ids(List<StructuredListModel.Row> rows) {
        List<String> ids = new ArrayList<String>();
        for (StructuredListModel.Row row : rows) ids.add(String.valueOf(row.get("id")));
        return ids;
    }
}
