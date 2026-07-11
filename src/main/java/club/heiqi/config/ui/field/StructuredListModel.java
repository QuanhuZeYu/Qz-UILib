package club.heiqi.config.ui.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import club.heiqi.config.schema.ValueSpec;

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

    /**
     * 外部 reset/回灌时按 schema 声明的 identity 复用内部 key；无 identity 时仅复用同位置深值相等的行。
     * 旧签名保留，表示没有 identity 声明。
     */
    public static List<Row> sync(List<Row> current, Object value) {
        return sync(current, value, null);
    }

    /**
     * 按结构化对象 schema 同步行模型。
     *
     * <p>业务 identity 只用于匹配，scene 仍使用 {@link Row#key()} 的内部 long key。identity 为空或
     * 重复时该行不参与匹配；没有 identity 声明时也不跨位置猜测，只在同位置深值相等时复用。</p>
     *
     * @param current 当前 keyed 行
     * @param value 新的 List&lt;Map&gt; 值
     * @param objectSpec 列表元素 OBJECT spec，可为 null
     * @return 新的不可变 keyed 行列表
     */
    public static List<Row> sync(List<Row> current, Object value, ValueSpec objectSpec) {
        List<Row> next = new ArrayList<Row>();
        if (value instanceof List) {
            List<Row> old = current == null ? Collections.<Row>emptyList() : current;
            List<Map<String, Object>> incoming = new ArrayList<Map<String, Object>>();
            for (Object item : (List<?>) value) {
                if (item instanceof Map) incoming.add(mapCopy((Map<?, ?>) item));
            }
            if (objectSpec != null && objectSpec.identityMember() != null) {
                appendByIdentity(next, old, incoming, objectSpec.identityMember());
            } else {
                for (int index = 0; index < incoming.size(); index++) {
                    Map<String, Object> item = incoming.get(index);
                    long key = index < old.size() && Objects.equals(old.get(index).value(), item)
                            ? old.get(index).key() : NEXT_KEY.getAndIncrement();
                    next.add(new Row(key, item));
                }
            }
        }
        return immutableRows(next);
    }

    private static void appendByIdentity(List<Row> next, List<Row> old,
                                         List<Map<String, Object>> incoming, String identityMember) {
        Map<Object, Integer> oldCounts = identityCounts(old, identityMember);
        Map<Object, Row> oldByIdentity = new HashMap<Object, Row>();
        for (Row row : old) {
            Object identity = identityValue(row.value(), identityMember);
            if (identity != null && Integer.valueOf(1).equals(oldCounts.get(identity))) {
                oldByIdentity.put(identity, row);
            }
        }
        Map<Object, Integer> incomingCounts = identityCounts(incoming, identityMember);
        for (Map<String, Object> item : incoming) {
            Object identity = identityValue(item, identityMember);
            Row oldRow = identity != null && Integer.valueOf(1).equals(incomingCounts.get(identity))
                    ? oldByIdentity.get(identity) : null;
            long key = oldRow == null ? NEXT_KEY.getAndIncrement() : oldRow.key();
            next.add(new Row(key, item));
        }
    }

    private static Map<Object, Integer> identityCounts(List<?> rows, String identityMember) {
        Map<Object, Integer> counts = new HashMap<Object, Integer>();
        for (Object value : rows) {
            Map<?, ?> map = value instanceof Row
                    ? ((Row) value).value() : (value instanceof Map ? (Map<?, ?>) value : null);
            Object identity = identityValue(map, identityMember);
            if (identity != null) {
                Integer count = counts.get(identity);
                counts.put(identity, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
            }
        }
        return counts;
    }

    private static Object identityValue(Map<?, ?> value, String identityMember) {
        if (value == null || !value.containsKey(identityMember)) return null;
        Object identity = value.get(identityMember);
        if (identity == null || identity instanceof Map || identity instanceof List) return null;
        if (identity instanceof String && ((String) identity).trim().isEmpty()) return null;
        if (identity instanceof Number) {
            double number = ((Number) identity).doubleValue();
            if (Double.isNaN(number) || Double.isInfinite(number)) return null;
        }
        return identity;
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
