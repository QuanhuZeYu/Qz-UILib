package club.heiqi.config.runtime;

import club.heiqi.config.ConfigNode;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link DraftView} 的深度只读快照实现。
 *
 * <p>由 {@link ConfigManager#save} 在内置校验之后、调用 {@link DraftValidator} 之前构造。
 * 对 List / Collection / Map / 数组做递归 defensive copy 并包成 unmodifiable；
 * 标量（null、String、Number、Boolean、Character、Enum）按引用复用；
 * 非 Schema 顶层 {@link ConfigNode} 子树按 Authority 契约视为只读，按引用透传。
 * 未知可变类型 fail-closed 抛 {@link IllegalArgumentException}（由 Manager 转为 INVALID），
 * 避免校验器经原地修改污染原 {@link DraftBuffer}。</p>
 *
 * <p>{@link #schema()} 返回的 {@link ConfigSchema} 本身不可变（sections/fields 已 unmodifiable）。</p>
 */
public final class SnapshotDraftView implements DraftView {

    private final ConfigSchema schema;
    private final Map<String, Object> values;
    private final Collection<String> fieldPaths;

    private SnapshotDraftView(ConfigSchema schema, Map<String, Object> values,
                              Collection<String> fieldPaths) {
        this.schema = schema;
        this.values = values;
        this.fieldPaths = fieldPaths;
    }

    /**
     * 从草稿容器深度只读拷贝值映射，构造视图。
     *
     * @param draft 草稿，不可 null
     * @return 深度只读快照
     * @throws IllegalArgumentException draft 为 null，或存在无法安全冻结的未知可变类型
     */
    public static SnapshotDraftView from(DraftBuffer draft) {
        if (draft == null) {
            throw new IllegalArgumentException("draft must not be null");
        }
        ConfigSchema schema = draft.schema();
        Map<String, Object> source = draft.draftSnapshot();
        Map<String, Object> frozen = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> e : source.entrySet()) {
            frozen.put(e.getKey(), deepFreeze(e.getValue()));
        }
        List<String> paths = new ArrayList<String>();
        for (FieldSpec field : schema.allFields()) {
            paths.add(field.path());
        }
        return new SnapshotDraftView(
                schema,
                Collections.unmodifiableMap(frozen),
                Collections.unmodifiableList(paths));
    }

    /**
     * 递归冻结配置值：容器 deep-copy + unmodifiable；标量复用；未知可变类型拒绝。
     *
     * @param value 任意草稿值
     * @return 深度只读等价物
     */
    static Object deepFreeze(Object value) {
        if (value == null) {
            return null;
        }
        if (isImmutableScalar(value)) {
            return value;
        }
        // 非 Schema 顶层子树：Authority 约定 ConfigNode 只读，透传引用（不 deep-copy 节点树）
        if (value instanceof ConfigNode) {
            return value;
        }
        if (value instanceof Map) {
            Map<?, ?> raw = (Map<?, ?>) value;
            Map<Object, Object> copy = new LinkedHashMap<Object, Object>(raw.size());
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                copy.put(deepFreeze(e.getKey()), deepFreeze(e.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List) {
            List<?> raw = (List<?>) value;
            List<Object> copy = new ArrayList<Object>(raw.size());
            for (Object item : raw) {
                copy.add(deepFreeze(item));
            }
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Collection) {
            Collection<?> raw = (Collection<?>) value;
            List<Object> copy = new ArrayList<Object>(raw.size());
            for (Object item : raw) {
                copy.add(deepFreeze(item));
            }
            return Collections.unmodifiableList(copy);
        }
        if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            List<Object> copy = new ArrayList<Object>(len);
            for (int i = 0; i < len; i++) {
                copy.add(deepFreeze(Array.get(value, i)));
            }
            return Collections.unmodifiableList(copy);
        }
        throw new IllegalArgumentException(
                "DraftView cannot freeze mutable type: " + value.getClass().getName());
    }

    private static boolean isImmutableScalar(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum;
    }

    @Override
    public Object getDraft(String path) {
        return values.get(path);
    }

    @Override
    public Map<String, Object> draftSnapshot() {
        return values;
    }

    /**
     * {@link ConfigSchema} 构造后 sections / byPath / FieldSpec 均为不可变视图，可安全暴露。
     */
    @Override
    public ConfigSchema schema() {
        return schema;
    }

    @Override
    public Collection<String> fieldPaths() {
        return fieldPaths;
    }
}
