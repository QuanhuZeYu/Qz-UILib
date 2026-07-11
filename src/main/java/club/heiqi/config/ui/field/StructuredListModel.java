package club.heiqi.config.ui.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    /**
     * 当前结构化列表实例的有限 identity 历史。
     *
     * <p>历史只保留仍存活 row key 的唯一 identity。当前唯一 identity 优先，历史 identity
     * 只有在未被当前结果占用且没有歧义时才可复用；这样 reset/reload 恢复旧 identity 时能保留
     * 焦点，而空值、重复值和多 key 冲突始终 fail-closed。</p>
     */
    public static final class IdentityLineage {
        private final String identityMember;
        private final Map<Object, Long> historicalKeys = new HashMap<Object, Long>();
        private final Map<Long, Set<Object>> aliasesByKey = new HashMap<Long, Set<Object>>();
        private final Set<Object> ambiguousIdentities = new HashSet<Object>();

        /**
         * 创建 identity lineage。
         *
         * @param identityMember identity member；null 表示不启用历史匹配
         */
        public IdentityLineage(String identityMember) {
            this.identityMember = identityMember;
        }

        /**
         * 观察当前列表，更新仍存活 row key 的 identity 历史。
         *
         * @param rows 当前 keyed 行
         */
        public void observe(List<Row> rows) {
            if (identityMember == null) return;
            Set<Long> activeKeys = new HashSet<Long>();
            if (rows != null) {
                for (Row row : rows) activeKeys.add(Long.valueOf(row.key()));
            }
            historicalKeys.entrySet().removeIf(entry -> !activeKeys.contains(entry.getValue()));
            aliasesByKey.entrySet().removeIf(entry -> !activeKeys.contains(entry.getKey()));

            Map<Object, Integer> counts = identityCounts(rows, identityMember);
            if (rows == null) return;
            for (Row row : rows) {
                Object identity = identityValue(row.value(), identityMember);
                if (identity == null || !Integer.valueOf(1).equals(counts.get(identity))) continue;
                if (ambiguousIdentities.contains(identity)) continue;
                Long previousKey = historicalKeys.get(identity);
                if (previousKey != null && previousKey.longValue() != row.key()) {
                    ambiguousIdentities.add(identity);
                    historicalKeys.remove(identity);
                    removeAlias(row.key(), identity);
                    removeAlias(previousKey.longValue(), identity);
                    continue;
                }
                historicalKeys.put(identity, Long.valueOf(row.key()));
                Set<Object> aliases = aliasesByKey.get(Long.valueOf(row.key()));
                if (aliases == null) {
                    aliases = new HashSet<Object>();
                    aliasesByKey.put(Long.valueOf(row.key()), aliases);
                }
                aliases.add(identity);
            }
            for (Object identity : counts.keySet()) {
                if (Integer.valueOf(1).equals(counts.get(identity))) continue;
                ambiguousIdentities.add(identity);
                historicalKeys.remove(identity);
            }
        }

        private Long historicalKey(Object identity) {
            if (identity == null || ambiguousIdentities.contains(identity)) return null;
            return historicalKeys.get(identity);
        }

        private void removeAlias(long key, Object identity) {
            Set<Object> aliases = aliasesByKey.get(Long.valueOf(key));
            if (aliases == null) return;
            aliases.remove(identity);
            if (aliases.isEmpty()) aliasesByKey.remove(Long.valueOf(key));
        }
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
        return sync(current, value, objectSpec, objectSpec == null
                ? null : new IdentityLineage(objectSpec.identityMember()));
    }

    /**
     * 按 schema 同步并使用当前列表实例的有限 identity lineage。
     *
     * @param current 当前 keyed 行
     * @param value 新的 List&lt;Map&gt; 值
     * @param objectSpec 列表元素 OBJECT spec，可为 null
     * @param lineage 当前列表实例的 lineage，可为 null
     * @return 新的不可变 keyed 行列表
     */
    public static List<Row> sync(List<Row> current, Object value, ValueSpec objectSpec,
                                 IdentityLineage lineage) {
        List<Row> next = new ArrayList<Row>();
        if (value instanceof List) {
            List<Row> old = current == null ? Collections.<Row>emptyList() : current;
            List<Map<String, Object>> incoming = new ArrayList<Map<String, Object>>();
            for (Object item : (List<?>) value) {
                if (item instanceof Map) incoming.add(mapCopy((Map<?, ?>) item));
            }
            if (objectSpec != null && objectSpec.identityMember() != null) {
                appendByIdentity(next, old, incoming, objectSpec.identityMember(), lineage);
            } else {
                for (int index = 0; index < incoming.size(); index++) {
                    Map<String, Object> item = incoming.get(index);
                    long key = index < old.size() && Objects.equals(old.get(index).value(), item)
                            ? old.get(index).key() : NEXT_KEY.getAndIncrement();
                    next.add(new Row(key, item));
                }
            }
        }
        List<Row> result = immutableRows(next);
        if (lineage != null) lineage.observe(result);
        return result;
    }

    private static void appendByIdentity(List<Row> next, List<Row> old,
                                         List<Map<String, Object>> incoming, String identityMember,
                                         IdentityLineage lineage) {
        Map<Object, Integer> oldCounts = identityCounts(old, identityMember);
        Map<Object, Row> oldByIdentity = new HashMap<Object, Row>();
        for (Row row : old) {
            Object identity = identityValue(row.value(), identityMember);
            if (identity != null && Integer.valueOf(1).equals(oldCounts.get(identity))) {
                oldByIdentity.put(identity, row);
            }
        }
        Map<Object, Integer> incomingCounts = identityCounts(incoming, identityMember);
        List<Row> directMatches = new ArrayList<Row>();
        Set<Long> usedKeys = new HashSet<Long>();
        for (Map<String, Object> item : incoming) {
            Object identity = identityValue(item, identityMember);
            Row oldRow = identity != null && Integer.valueOf(1).equals(incomingCounts.get(identity))
                    ? oldByIdentity.get(identity) : null;
            directMatches.add(oldRow);
            if (oldRow != null) usedKeys.add(Long.valueOf(oldRow.key()));
        }
        // 先保留所有当前唯一 identity 的 key，再处理历史 identity，避免 incoming 顺序改变优先级。
        for (int index = 0; index < incoming.size(); index++) {
            Map<String, Object> item = incoming.get(index);
            Object identity = identityValue(item, identityMember);
            Row oldRow = directMatches.get(index);
            long key;
            if (oldRow != null) {
                key = oldRow.key();
            } else {
                Long historicalKey = identity == null || !Integer.valueOf(1).equals(incomingCounts.get(identity))
                        || lineage == null ? null : lineage.historicalKey(identity);
                if (historicalKey != null && !usedKeys.contains(historicalKey)
                        && containsKey(old, historicalKey.longValue())) {
                    key = historicalKey.longValue();
                    usedKeys.add(historicalKey);
                } else {
                    key = NEXT_KEY.getAndIncrement();
                }
            }
            next.add(new Row(key, item));
        }
    }

    private static boolean containsKey(List<Row> rows, long key) {
        for (Row row : rows) if (row.key() == key) return true;
        return false;
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

    /** 读取指定 keyed row 的 member；行不存在时返回 null。 */
    public static Object memberValue(List<Row> rows, long key, String member) {
        if (rows == null) return null;
        for (Row row : rows) if (row.key() == key) return row.get(member);
        return null;
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

    /**
     * 按 schema 选项顺序生成 choice 多选显示项，并在末尾追加值中首次出现的未知字符串。
     *
     * @param value 当前列表值
     * @param options schema 声明选项
     * @return 去重后的不可变显示项
     */
    public static List<String> choiceDisplayItems(Object value, List<String> options) {
        List<String> result = new ArrayList<String>();
        Set<String> known = new HashSet<String>();
        if (options != null) {
            for (String option : options) {
                if (known.add(option)) result.add(option);
            }
        }
        Set<String> unknown = new HashSet<String>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item instanceof String && !known.contains(item) && unknown.add((String) item)) {
                    result.add((String) item);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** 判断 choice 列表是否包含指定字符串。 */
    public static boolean isChoiceSelected(Object value, String choice) {
        return value instanceof List && ((List<?>) value).contains(choice);
    }

    /**
     * 更新 choice 多选值。已知项按 schema 顺序去重，未知/null/错型值原样透传；
     * 未知字符串只能通过显式取消删除，不能通过勾选新增。
     */
    public static List<Object> updateChoiceSelection(Object value, List<String> options,
                                                     String choice, boolean checked) {
        List<?> source = value instanceof List ? (List<?>) value : Collections.emptyList();
        Set<String> known = new HashSet<String>();
        if (options != null) known.addAll(options);
        boolean targetKnown = known.contains(choice);
        Set<String> selectedKnown = new HashSet<String>();
        for (Object item : source) {
            if (item instanceof String && known.contains(item)) selectedKnown.add((String) item);
        }
        if (targetKnown) {
            if (checked) selectedKnown.add(choice);
            else selectedKnown.remove(choice);
        }

        List<Object> result = new ArrayList<Object>();
        Set<String> emittedKnown = new HashSet<String>();
        if (options != null) {
            for (String option : options) {
                if (selectedKnown.contains(option) && emittedKnown.add(option)) result.add(option);
            }
        }
        for (Object item : source) {
            if (item instanceof String && known.contains(item)) continue;
            if (!targetKnown && !checked && Objects.equals(item, choice)) continue;
            result.add(item);
        }
        return Collections.unmodifiableList(result);
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
