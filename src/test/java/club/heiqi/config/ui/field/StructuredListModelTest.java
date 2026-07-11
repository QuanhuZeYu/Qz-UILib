package club.heiqi.config.ui.field;

import club.heiqi.config.schema.ValueSpec;
import club.heiqi.config.schema.Values;

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
    public void syncWithoutIdentityOnlyReusesEqualRowsAtSameDepth() {
        Map<String, Object> first = row("a");
        List<StructuredListModel.Row> rows = StructuredListModel.fromValue(
                Arrays.<Object>asList(first));
        long key = rows.get(0).key();
        @SuppressWarnings("unchecked")
        List<String> members = (List<String>) first.get("members");
        members.add("outside");

        assertEquals(1, ((List<?>) rows.get(0).get("members")).size());
        rows = StructuredListModel.sync(rows, Arrays.<Object>asList(row("replacement")));
        assertNotEquals("无 identity 时不同值不可按位置猜测身份", key, rows.get(0).key());
        assertEquals("replacement", rows.get(0).get("id"));

        long replacementKey = rows.get(0).key();
        rows = StructuredListModel.sync(rows, Arrays.<Object>asList(row("replacement")));
        assertEquals("同位置深值相等时可证明仍是同一行", replacementKey, rows.get(0).key());
    }

    @Test
    public void syncByDeclaredIdentityReusesAcrossInsertDeleteAndReorder() {
        ValueSpec element = Values.objectWithIdentity("id",
                Values.member("id", Values.string()),
                Values.member("members", Values.list(Values.string())));
        List<StructuredListModel.Row> rows = StructuredListModel.fromValue(
                Arrays.<Object>asList(row("a"), row("b"), row("c")));
        long a = rows.get(0).key();
        long b = rows.get(1).key();
        long c = rows.get(2).key();

        rows = StructuredListModel.sync(rows,
                Arrays.<Object>asList(row("c"), row("x"), row("a")), element);
        assertEquals(Arrays.asList("c", "x", "a"), ids(rows));
        assertEquals(c, rows.get(0).key());
        assertNotEquals("首部插入的新 identity 不得借用旧 key", b, rows.get(1).key());
        assertEquals(a, rows.get(2).key());

        rows = StructuredListModel.sync(rows, Arrays.<Object>asList(row("a"), row("c")), element);
        assertEquals(a, rows.get(0).key());
        assertEquals(c, rows.get(1).key());
    }

    @Test
    public void duplicateOrEmptyIdentityGetsFreshKeyAndNeverStealsFocusIdentity() {
        ValueSpec element = Values.objectWithIdentity("id",
                Values.member("id", Values.string()),
                Values.member("members", Values.list(Values.string())));
        Map<String, Object> duplicateA = row("dup");
        Map<String, Object> duplicateB = row("dup");
        List<StructuredListModel.Row> rows = StructuredListModel.fromValue(
                Arrays.<Object>asList(duplicateA, duplicateB, row("stable")));
        long oldDuplicateA = rows.get(0).key();
        long oldDuplicateB = rows.get(1).key();
        long stable = rows.get(2).key();

        rows = StructuredListModel.sync(rows,
                Arrays.<Object>asList(row("dup"), row("dup"), row("stable")), element);
        assertNotEquals("重复 identity 不可猜测第一行归属", oldDuplicateA, rows.get(0).key());
        assertNotEquals("重复 identity 不可猜测第二行归属", oldDuplicateB, rows.get(1).key());
        assertEquals(stable, rows.get(2).key());

        Map<String, Object> empty = row("");
        List<StructuredListModel.Row> emptyRows = StructuredListModel.fromValue(
                Arrays.<Object>asList(empty));
        long oldEmpty = emptyRows.get(0).key();
        emptyRows = StructuredListModel.sync(emptyRows, Arrays.<Object>asList(row("")), element);
        assertNotEquals("空 identity 不可复用旧 key", oldEmpty, emptyRows.get(0).key());

        Map<String, Object> missing = row("missing");
        missing.remove("id");
        List<StructuredListModel.Row> missingRows = StructuredListModel.fromValue(
                Arrays.<Object>asList(missing));
        long oldMissing = missingRows.get(0).key();
        Map<String, Object> stillMissing = row("replacement");
        stillMissing.remove("id");
        missingRows = StructuredListModel.sync(missingRows,
                Arrays.<Object>asList(stillMissing), element);
        assertNotEquals("缺失 identity 不可复用旧 key", oldMissing, missingRows.get(0).key());

        Map<String, Object> nullIdentity = row("null");
        nullIdentity.put("id", null);
        List<StructuredListModel.Row> nullRows = StructuredListModel.fromValue(
                Arrays.<Object>asList(nullIdentity));
        long oldNull = nullRows.get(0).key();
        Map<String, Object> stillNull = row("replacement");
        stillNull.put("id", null);
        nullRows = StructuredListModel.sync(nullRows,
                Arrays.<Object>asList(stillNull), element);
        assertNotEquals("null identity 不可复用旧 key", oldNull, nullRows.get(0).key());
    }

    private static List<String> ids(List<StructuredListModel.Row> rows) {
        List<String> ids = new ArrayList<String>();
        for (StructuredListModel.Row row : rows) ids.add(String.valueOf(row.get("id")));
        return ids;
    }
}
