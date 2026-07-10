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
 * 纯数据草稿容器（current / draft），写入口防御拷贝，读出口防御副本，事务快照带 revision。
 *
 * <p>所有 public 读写 / 快照 / mutator 在同一 {@link #lock} 上同步。
 * {@link ConfigManager#save} 只在捕获与提交阶段按 manager → draft 顺序短暂持锁，
 * 外部校验期间不持本类锁；并发编辑通过 revision 冲突检测保留。</p>
 */
public final class DraftBuffer {

    private final ConfigSchema schema;
    private Map<String, Object> currentValues;
    private Map<String, Object> draftValues;
    private final Object lock = new Object();
    private long revision;

    /** 包内短锁作用域回调，供 ConfigManager capture/commit 阶段保持固定锁序。 */
    interface LockedOperation<T> {
        T run();
    }

    /**
     * current 与 draft 各持独立深拷贝，禁止共享 List/Map 别名。
     */
    private DraftBuffer(ConfigSchema schema, Map<String, Object> seedForCurrent,
                        Map<String, Object> seedForDraft) {
        this.schema = schema;
        this.currentValues = new LinkedHashMap<String, Object>(seedForCurrent);
        this.draftValues = new LinkedHashMap<String, Object>(seedForDraft);
        this.revision = 0L;
    }

    /**
     * 从权威态创建草稿：current / draft 各做一次完整深拷贝。
     */
    public static DraftBuffer from(Authority authority) {
        if (authority == null) {
            throw new IllegalArgumentException("authority must not be null");
        }
        Map<String, Object> snap = authority.snapshotTyped();
        Map<String, Object> forCurrent = ValueCopy.copyMapValues(snap);
        Map<String, Object> forDraft = ValueCopy.copyMapValues(snap);
        return new DraftBuffer(authority.schema(), forCurrent, forDraft);
    }

    public long revision() {
        synchronized (lock) {
            return revision;
        }
    }

    /**
     * 取草稿值的防御副本（调用方原地修改不影响内部）。
     */
    public Object getDraft(String path) {
        synchronized (lock) {
            return ValueCopy.copyOf(draftValues.get(path));
        }
    }

    /**
     * 取 current 的防御副本。
     */
    public Object getCurrent(String path) {
        synchronized (lock) {
            return ValueCopy.copyOf(currentValues.get(path));
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
            Object a = ValueCopy.copyOf(value);
            Object b = ValueCopy.copyOf(value);
            draftValues.put(path, a);
            currentValues.put(path, b);
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
     * 草稿全量防御拷贝 Map。
     */
    public Map<String, Object> draftSnapshot() {
        synchronized (lock) {
            return ValueCopy.copyMapValues(draftValues);
        }
    }

    /**
     * 捕获事务 candidate（package 内部使用）。
     *
     * <p>base 为 current 全表，proposed 为 draft 全表；合法 NUMBER 值在 proposed 中统一为
     * {@link Double}，后续内置校验、DraftView、Authority、draft/current 与持久化共用该份候选。</p>
     */
    TransactionCandidate captureCandidate() {
        synchronized (lock) {
            Map<String, Object> base = ValueCopy.copyMapValues(currentValues);
            Map<String, Object> proposed = ValueCopy.copyMapValues(draftValues);
            Map<String, Object> schemaFields = new LinkedHashMap<String, Object>();
            for (FieldSpec field : schema.allFields()) {
                String path = field.path();
                Object normalized = normalizeCandidateValue(field, proposed.get(path));
                proposed.put(path, normalized);
                schemaFields.put(path, ValueCopy.copyOf(normalized));
            }
            return new TransactionCandidate(
                    revision,
                    Collections.unmodifiableMap(base),
                    Collections.unmodifiableMap(schemaFields),
                    Collections.unmodifiableMap(proposed));
        }
    }

    /**
     * 在同一 draft 锁下执行完整操作。
     *
     * @param operation 包内事务操作
     * @return 操作结果
     */
    <T> T withLock(LockedOperation<T> operation) {
        if (operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }
        synchronized (lock) {
            return operation.run();
        }
    }

    public boolean revisionMatches(long expected) {
        synchronized (lock) {
            return revision == expected;
        }
    }

    /**
     * 锁外预制不会再失败的 commit 数据。
     */
    PreparedCommit prepareCandidateCommit(TransactionCandidate candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }
        Map<String, Object> all = ValueCopy.copyMapValues(candidate.proposedValues());
        return new PreparedCommit(all, ValueCopy.copyMapValues(all));
    }

    /**
     * 写盘成功后应用预制 commit；调用方必须持有 draft 锁且已复核 revision。
     */
    void applyPreparedCommit(PreparedCommit prepared) {
        if (prepared == null) {
            throw new IllegalArgumentException("prepared must not be null");
        }
        if (!Thread.holdsLock(lock)) {
            throw new IllegalStateException("draft lock is required for commit");
        }
        draftValues = prepared.draftValues;
        currentValues = prepared.currentValues;
        revision++;
    }

    /**
     * 保存成功：draft/current 均对齐 candidate（兼容包内旧调用）。
     */
    void commitCandidateToCurrent(TransactionCandidate candidate) {
        PreparedCommit prepared = prepareCandidateCommit(candidate);
        synchronized (lock) {
            if (revision != candidate.revision()) {
                throw new IllegalStateException("draft revised during save; cannot commit");
            }
            applyPreparedCommit(prepared);
        }
    }

    /**
     * 兼容旧测试：current = 当前 draft 的防御拷贝。
     * 新事务路径请用 {@link #commitCandidateToCurrent}。
     */
    public void commitDraftToCurrent() {
        synchronized (lock) {
            currentValues = ValueCopy.copyMapValues(draftValues);
            revision++;
        }
    }

    public ConfigSchema schema() {
        return schema;
    }

    /**
     * Schema 字段路径的不可变列表副本。
     */
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
                if (Double.isNaN(v) || Double.isInfinite(v)) {
                    return "值不是有限数字";
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
     * 将可合法解释的 NUMBER 候选统一为 Double；非法值保留给内置校验 fail-closed。
     */
    private static Object normalizeCandidateValue(FieldSpec field, Object value) {
        if (field.type() != FieldType.NUMBER || value == null) {
            return ValueCopy.copyOf(value);
        }
        double number;
        if (value instanceof Number) {
            number = ((Number) value).doubleValue();
        } else {
            try {
                number = Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException e) {
                return ValueCopy.copyOf(value);
            }
        }
        if (Double.isNaN(number) || Double.isInfinite(number)) {
            return ValueCopy.copyOf(value);
        }
        return Double.valueOf(number);
    }

    /**
     * 一次 save 事务的稳定 candidate（package-private，map 不可变）。
     */
    static final class TransactionCandidate {
        private final long revision;
        private final Map<String, Object> baseValues;
        private final Map<String, Object> schemaFieldValues;
        private final Map<String, Object> proposedValues;

        TransactionCandidate(long revision,
                             Map<String, Object> baseValues,
                             Map<String, Object> schemaFieldValues,
                             Map<String, Object> proposedValues) {
            this.revision = revision;
            this.baseValues = baseValues;
            this.schemaFieldValues = schemaFieldValues;
            this.proposedValues = proposedValues;
        }

        long revision() {
            return revision;
        }

        Map<String, Object> baseValues() {
            return baseValues;
        }

        Map<String, Object> schemaFieldValues() {
            return schemaFieldValues;
        }

        Map<String, Object> proposedValues() {
            return proposedValues;
        }
    }

    /** 写盘前已完成全部深拷贝的 commit 数据。 */
    static final class PreparedCommit {
        private final Map<String, Object> draftValues;
        private final Map<String, Object> currentValues;

        PreparedCommit(Map<String, Object> draftValues, Map<String, Object> currentValues) {
            this.draftValues = draftValues;
            this.currentValues = currentValues;
        }
    }
}
