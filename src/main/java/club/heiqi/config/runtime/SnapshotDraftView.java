package club.heiqi.config.runtime;

import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link DraftView}：仅含 Schema 字段的深度只读快照。
 *
 * <p>由 {@link ConfigManager#save} 在捕获 candidate 后构造；非 Schema raw 子树不进入视图。
 * 值经 {@link ValueCopy#freeze}；循环引用 / 未知可变类型抛 {@link IllegalArgumentException}。</p>
 */
public final class SnapshotDraftView implements DraftView {

    private final Map<String, Object> values;
    private final Collection<String> fieldPaths;

    private SnapshotDraftView(Map<String, Object> values, Collection<String> fieldPaths) {
        this.values = values;
        this.fieldPaths = fieldPaths;
    }

    /**
     * 从已捕获的 schema 字段 candidate 构造只读视图。
     *
     * @param schema            schema
     * @param schemaFieldValues schema path → 已防御拷贝的值（仍会再 freeze）
     * @return 只读视图
     */
    public static SnapshotDraftView ofSchemaFields(ConfigSchema schema,
                                                   Map<String, Object> schemaFieldValues) {
        if (schema == null) {
            throw new IllegalArgumentException("schema must not be null");
        }
        if (schemaFieldValues == null) {
            throw new IllegalArgumentException("schemaFieldValues must not be null");
        }
        List<String> paths = new ArrayList<String>();
        Map<String, Object> frozen = new LinkedHashMap<String, Object>();
        for (FieldSpec field : schema.allFields()) {
            String path = field.path();
            paths.add(path);
            frozen.put(path, ValueCopy.freeze(schemaFieldValues.get(path)));
        }
        return new SnapshotDraftView(
                Collections.unmodifiableMap(frozen),
                Collections.unmodifiableList(paths));
    }

    /**
     * @deprecated 测试辅助：对任意值 freeze（与 {@link ValueCopy#freeze} 相同）
     */
    static Object deepFreeze(Object value) {
        return ValueCopy.freeze(value);
    }

    @Override
    public Object getDraft(String path) {
        return values.get(path);
    }

    @Override
    public Map<String, Object> draftSnapshot() {
        return values;
    }

    @Override
    public Collection<String> fieldPaths() {
        return fieldPaths;
    }
}
