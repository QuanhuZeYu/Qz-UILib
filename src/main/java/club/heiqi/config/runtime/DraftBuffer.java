package club.heiqi.config.runtime;

import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldConstraints;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.schema.FieldType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 纯数据草稿容器（current / draft），写入口防御拷贝，事务快照带 revision。
 *
 * <p>所有 mutator 与 snapshot 在同一实例锁上同步；{@link #revision()} 在每次成功写入后递增，
 * 供 {@link ConfigManager#save} 检测 validator 闭包或并发修改。</p>
 */
public final class DraftBuffer {

    private final ConfigSchema schema;
    private final Map<String, Object> currentValues;
    private final Map<String, Object> draftValues;
    private final Object lock = new Object();
    private long revision;

    private DraftBuffer(ConfigSchema schema, Map<String, Object> seedCopied) {
        this.schema = schema;
        this.currentValues = new LinkedHashMap<String, Object>(seedCopied);
        this.draftValues = new LinkedHashMap<String, Object>(seedCopied);
        this.revision = 0L;
    }

    /**
     * 从权威态防御拷贝创建草稿（仅复制 schema 字段 + 非 schema 顶层 key 的引用隔离拷贝）。
     */
    public static DraftBuffer from(Authority authority) {
        if (authority == null) {
            throw new IllegalArgumentException("authority must not be null");
        }
        Map<String, Object> seed = ValueCopy.copyMapValues(authority.snapshotTyped());
        return new DraftBuffer(authority.schema(), seed);
    }

    /**
     * @return 单调 revision（每次 mutator 成功后 +1）
     */
    public long revision() {
        synchronized (lock) {
            return revision;
        }
    }

    public Object getDraft(String path) {
        synchronized (lock) {
            return draftValues.get(path);
        }
    }

    public Object getCurrent(String path) {
        synchronized (lock) {
            return currentValues.get(path);
        }
    }

    public void setDraft(String path, Object value) {
        synchronized (lock) {
            draftValues.put(path, ValueCopy.copyOf(value));
            revision++;
        }
    }

    public void setDraftAndCurrent(String path, Object value) {
        synchronized (lock) {
            Object copied = ValueCopy.copyOf(value);
            // draft 与 current 各持独立拷贝，避免共享可变容器
            draftValues.put(path, copied);
            currentValues.put(path, ValueCopy.copyOf(copied));
            revision++;
        }
    }

    public boolean isDirty(String path) {
        synchronized (lock) {
            return !Objects.equals(draftValues.get(path), currentValues.get(path));
        }
    }

    public boolean isDirtyAny() {
        synchronized (lock) {
            for (FieldSpec field : schema.allFields()) {
                if (!Objects.equals(draftValues.get(field.path()), currentValues.get(field.path()))) {
                    return true;
                }
            }
            return false;
        }
    }

    public String error(String path) {
        return validateAll().errorFor(path);
    }

    public boolean hasError() {
        return validateAll().hasErrors();
    }

    public ValidationResult validateAll() {
        synchronized (lock) {
            Map<String, String> errors = new LinkedHashMap<String, String>();
            for (FieldSpec field : schema.allFields()) {
                String msg = validateField(field, draftValues.get(field.path()));
                if (msg != null) {
                    errors.put(field.path(), msg);
                }
            }
            return ValidationResult.of(errors);
        }
    }

    /**
     * 对给定 candidate 值映射做内置校验（不读实时 draft）。
     *
     * @param candidateValues schema path → 值
     * @return 校验结果
     */
    public ValidationResult validateCandidate(Map<String, Object> candidateValues) {
        if (candidateValues == null) {
            throw new IllegalArgumentException("candidateValues must not be null");
        }
        Map<String, String> errors = new LinkedHashMap<String, String>();
        for (FieldSpec field : schema.allFields()) {
            String msg = validateField(field, candidateValues.get(field.path()));
            if (msg != null) {
                errors.put(field.path(), msg);
            }
        }
        return ValidationResult.of(errors);
    }

    public void resetToCurrent() {
        synchronized (lock) {
            draftValues.clear();
            for (Map.Entry<String, Object> e : currentValues.entrySet()) {
                draftValues.put(e.getKey(), ValueCopy.copyOf(e.getValue()));
            }
            revision++;
        }
    }

    public void resetFieldToDefault(String path) {
        synchronized (lock) {
            FieldSpec field = schema.field(path);
            if (field == null) {
                return;
            }
            Object def = Authority.normalizeDefault(field.defaultValue(), field.type());
            draftValues.put(path, ValueCopy.copyOf(def));
            revision++;
        }
    }

    /**
     * 草稿浅层 Map 拷贝（value 再 {@link ValueCopy#copyOf}）。
     */
    public Map<String, Object> draftSnapshot() {
        synchronized (lock) {
            return ValueCopy.copyMapValues(draftValues);
        }
    }

    /**
     * 捕获事务 candidate：schema 字段深拷贝 + 全量 draft 键（含非 schema）深拷贝，并记录 revision。
     */
    public TransactionCandidate captureCandidate() {
        synchronized (lock) {
            Map<String, Object> schemaFields = new LinkedHashMap<String, Object>();
            for (FieldSpec field : schema.allFields()) {
                String path = field.path();
                schemaFields.put(path, ValueCopy.copyOf(draftValues.get(path)));
            }
            Map<String, Object> all = ValueCopy.copyMapValues(draftValues);
            Map<String, Object> currentSnap = ValueCopy.copyMapValues(currentValues);
            return new TransactionCandidate(revision, schemaFields, all, currentSnap);
        }
    }

    /**
     * 若 revision 已变则 fail：用于事务中途检测。
     *
     * @param expected 捕获时 revision
     * @return 是否仍匹配
     */
    public boolean revisionMatches(long expected) {
        synchronized (lock) {
            return revision == expected;
        }
    }

    /**
     * 用 candidate 的 draft 全量覆盖 draftValues，current 恢复为捕获时 current（事务失败回滚 draft 态）。
     */
    public void restoreFromCandidate(TransactionCandidate candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }
        synchronized (lock) {
            draftValues.clear();
            draftValues.putAll(ValueCopy.copyMapValues(candidate.allDraftValues()));
            currentValues.clear();
            currentValues.putAll(ValueCopy.copyMapValues(candidate.currentSnapshot()));
            revision++;
        }
    }

    /**
     * 保存成功：current = candidate 的 draft 全量（非实时 draft）。
     */
    public void commitCandidateToCurrent(TransactionCandidate candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }
        synchronized (lock) {
            if (revision != candidate.revision()) {
                throw new IllegalStateException("draft revised during save; cannot commit");
            }
            draftValues.clear();
            draftValues.putAll(ValueCopy.copyMapValues(candidate.allDraftValues()));
            currentValues.clear();
            currentValues.putAll(ValueCopy.copyMapValues(candidate.allDraftValues()));
            revision++;
        }
    }

    /**
     * @deprecated 使用 {@link #commitCandidateToCurrent}；保留仅兼容旧测试直接调用
     */
    public void commitDraftToCurrent() {
        synchronized (lock) {
            currentValues.clear();
            for (Map.Entry<String, Object> e : draftValues.entrySet()) {
                currentValues.put(e.getKey(), ValueCopy.copyOf(e.getValue()));
            }
            revision++;
        }
    }

    public ConfigSchema schema() {
        return schema;
    }

    public Collection<String> fieldPaths() {
        List<String> paths = new ArrayList<String>();
        for (FieldSpec field : schema.allFields()) {
            paths.add(field.path());
        }
        return Collections.unmodifiableList(paths);
    }

    private String validateField(FieldSpec field, Object value) {
        FieldConstraints c = field.constraints();

        if (c != null && c.required()) {
            if (value == null) {
                return "字段必填";
            }
            if (value instanceof String && ((String) value).isEmpty()) {
                return "字段必填";
            }
        }

        if (value == null) {
            return null;
        }

        switch (field.type()) {
            case NUMBER: {
                double v;
                if (value instanceof Number) {
                    v = ((Number) value).doubleValue();
                } else {
                    try {
                        v = Double.parseDouble(String.valueOf(value));
                    } catch (NumberFormatException e) {
                        return "值不是有效数字";
                    }
                }
                if (c != null) {
                    if (v < c.min()) {
                        return "数值 " + v + " 小于下限 " + c.min();
                    }
                    if (v > c.max()) {
                        return "数值 " + v + " 大于上限 " + c.max();
                    }
                }
                break;
            }
            case STRING: {
                if (c != null && c.maxLength() >= 0) {
                    int len = String.valueOf(value).length();
                    if (len > c.maxLength()) {
                        return "长度 " + len + " 超过上限 " + c.maxLength();
                    }
                }
                break;
            }
            case CHOICE: {
                if (c != null && c.choices() != null && !c.choices().isEmpty()) {
                    String str = String.valueOf(value);
                    if (!c.choices().contains(str)) {
                        return "值 " + str + " 不在可选范围";
                    }
                }
                break;
            }
            case BOOLEAN:
            default:
                break;
        }
        return null;
    }

    /**
     * 一次 save 事务的稳定 candidate。
     */
    public static final class TransactionCandidate {
        private final long revision;
        private final Map<String, Object> schemaFieldValues;
        private final Map<String, Object> allDraftValues;
        private final Map<String, Object> currentSnapshot;

        TransactionCandidate(long revision,
                             Map<String, Object> schemaFieldValues,
                             Map<String, Object> allDraftValues,
                             Map<String, Object> currentSnapshot) {
            this.revision = revision;
            this.schemaFieldValues = schemaFieldValues;
            this.allDraftValues = allDraftValues;
            this.currentSnapshot = currentSnapshot;
        }

        public long revision() {
            return revision;
        }

        /** schema path → 值（已 copy） */
        public Map<String, Object> schemaFieldValues() {
            return schemaFieldValues;
        }

        /** 全量 draft 键（含非 schema），供 Authority.applyAll */
        public Map<String, Object> allDraftValues() {
            return allDraftValues;
        }

        Map<String, Object> currentSnapshot() {
            return currentSnapshot;
        }
    }
}
