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
import static org.junit.Assert.fail;

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

    @Test
    public void identityLineageRestoresOldIdentityWithoutGuessingAnotherKey() {
        ValueSpec element = Values.objectWithIdentity("id",
                Values.member("id", Values.string()),
                Values.member("members", Values.list(Values.string())));
        List<StructuredListModel.Row> rows = StructuredListModel.fromValue(
                Arrays.<Object>asList(row("old")));
        StructuredListModel.IdentityLineage lineage = new StructuredListModel.IdentityLineage("id");
        lineage.observe(rows);
        long originalKey = rows.get(0).key();

        rows = StructuredListModel.updateMember(rows, originalKey, "id", "editing");
        lineage.observe(rows);
        rows = StructuredListModel.sync(rows, Arrays.<Object>asList(row("old")), element, lineage);

        assertEquals("reset/reload 恢复历史唯一 identity 时应保留原 row key",
                originalKey, rows.get(0).key());
    }

    @Test
    public void currentUniqueIdentityWinsRegardlessOfIncomingOrder() {
        ValueSpec element = Values.objectWithIdentity("id",
                Values.member("id", Values.string()),
                Values.member("members", Values.list(Values.string())));
        List<StructuredListModel.Row> rows = StructuredListModel.fromValue(
                Arrays.<Object>asList(row("old")));
        StructuredListModel.IdentityLineage lineage = new StructuredListModel.IdentityLineage("id");
        lineage.observe(rows);
        long key = rows.get(0).key();
        rows = StructuredListModel.updateMember(rows, key, "id", "current");
        lineage.observe(rows);

        rows = StructuredListModel.sync(rows,
                Arrays.<Object>asList(row("old"), row("current")), element, lineage);

        assertEquals("当前唯一 identity 必须优先保留现行 key", "current", rows.get(1).get("id"));
        assertEquals(key, rows.get(1).key());
        assertNotEquals("历史 identity 不得抢占当前唯一 identity 的 key", key, rows.get(0).key());
    }

    @Test
    public void identityThatBecameDuplicateRemainsFailClosedWhenUniqueAgain() {
        ValueSpec element = identityElement();
        List<StructuredListModel.Row> rows = StructuredListModel.fromValue(
                Arrays.<Object>asList(row("shared")));
        StructuredListModel.IdentityLineage lineage = new StructuredListModel.IdentityLineage("id");
        lineage.observe(rows);
        long originalKey = rows.get(0).key();

        rows = StructuredListModel.sync(rows,
                Arrays.<Object>asList(row("shared"), row("shared")), element, lineage);
        rows = StructuredListModel.sync(rows, Arrays.<Object>asList(row("shared")), element, lineage);

        assertNotEquals("曾重复的 identity 重新唯一后仍不得嫁接历史 key",
                originalKey, rows.get(0).key());
    }

    @Test
    public void identityObservedOnTwoKeysNeverGraftsToEitherHistory() {
        ValueSpec element = identityElement();
        List<StructuredListModel.Row> rows = StructuredListModel.fromValue(
                Arrays.<Object>asList(row("shared"), row("other")));
        StructuredListModel.IdentityLineage lineage = new StructuredListModel.IdentityLineage("id");
        lineage.observe(rows);
        long firstKey = rows.get(0).key();
        long secondKey = rows.get(1).key();

        rows = StructuredListModel.updateMember(rows, firstKey, "id", "moved-away");
        lineage.observe(rows);
        rows = StructuredListModel.updateMember(rows, secondKey, "id", "shared");
        lineage.observe(rows);
        rows = StructuredListModel.updateMember(rows, secondKey, "id", "also-moved-away");
        lineage.observe(rows);
        rows = StructuredListModel.sync(rows, Arrays.<Object>asList(row("shared")), element, lineage);

        assertNotEquals("跨 key 的 identity 历史不得嫁接第一个 key", firstKey, rows.get(0).key());
        assertNotEquals("跨 key 的 identity 历史不得嫁接第二个 key", secondKey, rows.get(0).key());
    }

    @Test
    public void deletedRowIdentityGetsFreshKeyWhenRecreated() {
        ValueSpec element = identityElement();
        List<StructuredListModel.Row> rows = StructuredListModel.fromValue(
                Arrays.<Object>asList(row("deleted")));
        StructuredListModel.IdentityLineage lineage = new StructuredListModel.IdentityLineage("id");
        lineage.observe(rows);
        long deletedKey = rows.get(0).key();

        rows = StructuredListModel.remove(rows, deletedKey);
        lineage.observe(rows);
        rows = StructuredListModel.sync(rows, Arrays.<Object>asList(row("deleted")), element, lineage);

        assertNotEquals("删除后的同 identity 行必须分配新 key", deletedKey, rows.get(0).key());
    }

    @Test
    public void choiceHelpersOrderDeduplicateAndPreservePassthroughValues() {
        List<String> options = Arrays.asList("beta", "alpha", "beta");
        List<Object> value = Arrays.<Object>asList("alpha", "removed", null,
                Integer.valueOf(7), "alpha", "removed", "future");

        assertEquals(Arrays.asList("beta", "alpha", "removed", "future"),
                StructuredListModel.choiceDisplayItems(value, options));
        assertTrue(StructuredListModel.isChoiceSelected(value, "alpha"));
        assertFalse(StructuredListModel.isChoiceSelected(value, "beta"));
        assertEquals(Arrays.<Object>asList("beta", "alpha", "removed", null,
                        Integer.valueOf(7), "removed", "future"),
                StructuredListModel.updateChoiceSelection(value, options, "beta", true));
        assertEquals(Arrays.<Object>asList("removed", null, Integer.valueOf(7), "removed", "future"),
                StructuredListModel.updateChoiceSelection(value, options, "alpha", false));
    }

    @Test
    public void unknownChoiceCanOnlyBeDeletedAndEmptyResultIsImmutable() {
        List<String> options = Arrays.asList("alpha", "beta");
        List<Object> value = Arrays.<Object>asList("removed", null, Integer.valueOf(7));
        assertEquals(value, StructuredListModel.updateChoiceSelection(value, options, "future", true));
        assertEquals(Arrays.<Object>asList(null, Integer.valueOf(7)),
                StructuredListModel.updateChoiceSelection(value, options, "removed", false));

        List<Object> empty = StructuredListModel.updateChoiceSelection(
                Arrays.<Object>asList("alpha"), options, "alpha", false);
        assertTrue(empty.isEmpty());
        try {
            empty.add("beta");
            fail("choice result must be immutable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    private static ValueSpec identityElement() {
        return Values.objectWithIdentity("id",
                Values.member("id", Values.string()),
                Values.member("members", Values.list(Values.string())));
    }

    private static List<String> ids(List<StructuredListModel.Row> rows) {
        List<String> ids = new ArrayList<String>();
        for (StructuredListModel.Row row : rows) ids.add(String.valueOf(row.get("id")));
        return ids;
    }
}
