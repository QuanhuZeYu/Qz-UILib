package club.heiqi.config.runtime;

import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link DraftView} 的不可变快照实现。
 *
 * <p>由 {@link ConfigManager#save} 在内置校验之后、调用 {@link DraftValidator} 之前构造；
 * 值映射为拷贝后的 unmodifiable Map，校验器无法写回原 {@link DraftBuffer}。</p>
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
     * 从草稿容器深拷贝值映射，构造只读视图。
     *
     * @param draft 草稿，不可 null
     * @return 只读快照
     */
    public static SnapshotDraftView from(DraftBuffer draft) {
        if (draft == null) {
            throw new IllegalArgumentException("draft must not be null");
        }
        ConfigSchema schema = draft.schema();
        Map<String, Object> copy = new HashMap<String, Object>(draft.draftSnapshot());
        List<String> paths = new ArrayList<String>();
        for (FieldSpec field : schema.allFields()) {
            paths.add(field.path());
        }
        return new SnapshotDraftView(
                schema,
                Collections.unmodifiableMap(copy),
                Collections.unmodifiableList(paths));
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
    public ConfigSchema schema() {
        return schema;
    }

    @Override
    public Collection<String> fieldPaths() {
        return fieldPaths;
    }
}
