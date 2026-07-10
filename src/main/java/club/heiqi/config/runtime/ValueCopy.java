package club.heiqi.config.runtime;

import club.heiqi.config.ConfigNode;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 配置值防御拷贝 / 深度冻结工具。
 *
 * <p>用于 {@link DraftBuffer} 种子与写入、{@link SnapshotDraftView} 提交前只读视图。
 * 白名单不可变标量、List/Collection/Map/数组（递归）；
 * 非 Schema 顶层 {@link ConfigNode} 在 copyOf 时按引用透传（Authority 只读契约），
 * freeze（DraftView）路径拒绝 ConfigNode，避免泄漏给 validator。</p>
 */
public final class ValueCopy {

    private ValueCopy() {
    }

    /**
     * 防御可变副本（供 DraftBuffer 持有；容器可被后续 setDraft 替换，但入口值已与源隔离）。
     *
     * @param value 源值
     * @return 深拷贝
     */
    public static Object copyOf(Object value) {
        return copyInternal(value, newIdentitySet(), false);
    }

    /**
     * 深度冻结为只读结构（容器 unmodifiable；标量复用）。
     *
     * @param value 源值
     * @return 只读深拷贝
     */
    public static Object freeze(Object value) {
        return copyInternal(value, newIdentitySet(), true);
    }

    /**
     * 对 Map 的每个 value 做 {@link #copyOf}，返回新 LinkedHashMap（键按字符串保留）。
     *
     * @param source 源映射，不可 null
     * @return 新 Map
     */
    public static Map<String, Object> copyMapValues(Map<String, Object> source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>(source.size());
        for (Map.Entry<String, Object> e : source.entrySet()) {
            out.put(e.getKey(), copyOf(e.getValue()));
        }
        return out;
    }

    /**
     * 对 Map 的每个 value 做 {@link #freeze}，返回 unmodifiable LinkedHashMap。
     *
     * @param source 源映射
     * @return 只读 Map
     */
    public static Map<String, Object> freezeMapValues(Map<String, Object> source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>(source.size());
        for (Map.Entry<String, Object> e : source.entrySet()) {
            out.put(e.getKey(), freeze(e.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }

    private static Set<Object> newIdentitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    }

    private static Object copyInternal(Object value, Set<Object> visiting, boolean freeze) {
        if (value == null) {
            return null;
        }
        if (isImmutableScalar(value)) {
            return value;
        }
        // ConfigNode：copyOf 经 YAML 序列化重建（断别名）；freeze 拒绝（不进 DraftView）
        if (value instanceof ConfigNode) {
            if (freeze) {
                throw new IllegalArgumentException("ConfigNode must not appear in DraftView");
            }
            return copyConfigNode((ConfigNode) value);
        }
        if (value instanceof Number && !isSafeNumber(value)) {
            throw new IllegalArgumentException(
                    "mutable or unsupported Number: " + value.getClass().getName());
        }
        // 容器循环检测
        if (value instanceof Map || value instanceof Collection || value.getClass().isArray()) {
            if (!visiting.add(value)) {
                throw new IllegalArgumentException("cyclic structure in config value");
            }
            try {
                if (value instanceof Map) {
                    Map<?, ?> raw = (Map<?, ?>) value;
                    Map<Object, Object> copy = new LinkedHashMap<Object, Object>(raw.size());
                    for (Map.Entry<?, ?> e : raw.entrySet()) {
                        Object k = e.getKey();
                        // map key 仅允许不可变标量（配置 path 值树中 key 多为 String）
                        if (k != null && !isImmutableScalar(k) && !(k instanceof Number && isSafeNumber(k))) {
                            throw new IllegalArgumentException(
                                    "unsupported map key type: " + k.getClass().getName());
                        }
                        copy.put(k, copyInternal(e.getValue(), visiting, freeze));
                    }
                    return freeze ? Collections.unmodifiableMap(copy) : copy;
                }
                if (value instanceof List || value instanceof Collection) {
                    Collection<?> raw = (Collection<?>) value;
                    List<Object> copy = new ArrayList<Object>(raw.size());
                    for (Object item : raw) {
                        copy.add(copyInternal(item, visiting, freeze));
                    }
                    return freeze ? Collections.unmodifiableList(copy) : copy;
                }
                // array → List（配置域统一 list 语义）
                int len = Array.getLength(value);
                List<Object> copy = new ArrayList<Object>(len);
                for (int i = 0; i < len; i++) {
                    copy.add(copyInternal(Array.get(value, i), visiting, freeze));
                }
                return freeze ? Collections.unmodifiableList(copy) : copy;
            } finally {
                visiting.remove(value);
            }
        }
        throw new IllegalArgumentException(
                "unsupported mutable config value type: " + value.getClass().getName());
    }

    private static ConfigNode copyConfigNode(ConfigNode node) {
        try {
            String yaml = club.heiqi.config.ConfigSerializer.toString(
                    node, club.heiqi.config.ConfigFormat.YAML);
            return club.heiqi.config.ConfigSerializer.parse(
                    yaml, club.heiqi.config.ConfigFormat.YAML);
        } catch (club.heiqi.config.ConfigException e) {
            throw new IllegalArgumentException("cannot deep-copy ConfigNode: " + e.getMessage(), e);
        }
    }

    static boolean isImmutableScalar(Object value) {
        return value instanceof String
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum
                || isSafeNumber(value);
    }

    /**
     * 白名单不可变 Number（排除 Atomic* 与未知 Number 子类）。
     */
    static boolean isSafeNumber(Object value) {
        return value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof BigInteger
                || value instanceof BigDecimal;
    }
}
