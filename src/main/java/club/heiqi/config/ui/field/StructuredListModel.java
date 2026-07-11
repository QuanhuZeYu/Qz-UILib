package club.heiqi.config.ui.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** 结构化列表 renderer 的纯数据模型，供 JVM 测试和 scene 适配层共同使用。 */
public final class StructuredListModel {
    private static final AtomicLong NEXT_KEY = new AtomicLong(1L);

    private StructuredListModel() { }

    /** 带稳定 UI key 的对象行。 */
    public static final class Row {
        private final long key;
        private final Map<String, Object> value;

        private Row(long key, Map<String, Object> value) {
            this.key = key;
            this.value = immutableMap(value);
        }

        /** @return 稳定行 key */
        public long key() { return key; }
        /** @return 对象值只读视图 */
        public Map<String, Object> value() { return value; }
        /** @return 指定 member 值 */
        public Object get(String member) { return value.get(member); }
    }

    /** 将 draft 值转换为带稳定 key 的行列表。 */
    public static List<Row> fromValue(Object value) {
        List<Row> rows = new ArrayList<Row>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item instanceof Map) rows.add(new Row(NEXT_KEY.getAndIncrement(), mapCopy((Map<?, ?>) item)));
            }
        }
        return immutableRows(rows);
    }

    /** 外部 reset/回灌时按位置复用既有 key，新增位置才分配 key。 */
    public static List<Row> sync(List<Row> current, Object value) {
        List<Row> next = new ArrayList<Row>();
        if (value instanceof List) {
            List<Row> old = current == null ? Collections.<Row>emptyList() : current;
            int index = 0;
            for (Object item : (List<?>) value) {
                if (!(item instanceof Map)) continue;
                long key = index < old.size() ? old.get(index).key() : NEXT_KEY.getAndIncrement();
                next.add(new Row(key, mapCopy((Map<?, ?>) item)));
                index++;
            }
        }
        return immutableRows(next);
    }

    /** 添加一个对象行。 */
    public static List<Row> add(List<Row> rows, Map<String, Object> value) {
        List<Row> next = mutableRows(rows);
        next.add(new Row(NEXT_KEY.getAndIncrement(), value == null
                ? Collections.<String, Object>emptyMap() : value));
        return immutableRows(next);
    }

    /** 删除指定 key 的行。 */
    public static List<Row> remove(List<Row> rows, long key) {
        List<Row> next = mutableRows(rows);
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).key() == key) {
                next.remove(i);
                break;
            }
        }
        return immutableRows(next);
    }

    /** 向上移动一行；首行保持不变。 */
    public static List<Row> moveUp(List<Row> rows, long key) {
        List<Row> next = mutableRows(rows);
        int index = indexOf(next, key);
        if (index > 0) Collections.swap(next, index, index - 1);
        return immutableRows(next);
    }

    /** 向下移动一行；末行保持不变。 */
    public static List<Row> moveDown(List<Row> rows, long key) {
        List<Row> next = mutableRows(rows);
        int index = indexOf(next, key);
        if (index >= 0 && index + 1 < next.size()) Collections.swap(next, index, index + 1);
        return immutableRows(next);
    }

    /** 更新一个 row 的 member，保留 row key 和其它 member。 */
    public static List<Row> updateMember(List<Row> rows, long key, String member, Object value) {
        List<Row> next = mutableRows(rows);
        for (int i = 0; i < next.size(); i++) {
            Row row = next.get(i);
            if (row.key() == key) {
                Map<String, Object> updated = new LinkedHashMap<String, Object>(row.value());
                updated.put(member, value);
                next.set(i, new Row(row.key(), updated));
                break;
            }
        }
        return immutableRows(next);
    }

    /** 将行模型投影回 DraftBuffer 的 List<Map> 值树。 */
    public static List<Map<String, Object>> toValue(List<Row> rows) {
        List<Map<String, Object>> value = new ArrayList<Map<String, Object>>();
        if (rows != null) {
            for (Row row : rows) value.add(new LinkedHashMap<String, Object>(row.value()));
        }
        return Collections.unmodifiableList(value);
    }

    /** 值投影相等时跳过 local keyed 列表重建。 */
    public static boolean valuesEqual(List<Row> rows, Object value) {
        return toValue(rows).equals(value instanceof List ? value : Collections.emptyList());
    }

    private static int indexOf(List<Row> rows, long key) {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).key() == key) return i;
        return -1;
    }

    private static List<Row> mutableRows(List<Row> rows) {
        return rows == null ? new ArrayList<Row>() : new ArrayList<Row>(rows);
    }

    private static List<Row> immutableRows(List<Row> rows) {
        return Collections.unmodifiableList(new ArrayList<Row>(rows));
    }

    private static Map<String, Object> mapCopy(Map<?, ?> source) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), deepCopy(entry.getValue()));
        }
        return copy;
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), deepCopy(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Object deepCopy(Object value) {
        if (value instanceof Map) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                copy.put(String.valueOf(entry.getKey()), deepCopy(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List) {
            List<Object> copy = new ArrayList<Object>();
            for (Object item : (List<?>) value) copy.add(deepCopy(item));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
