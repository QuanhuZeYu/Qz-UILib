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
 * 纯数据草稿容器（transaction base / current / draft），写入口防御拷贝，读出口防御副本，事务快照带 revision。
 *
 * <p>三份表语义：</p>
 * <ul>
 *   <li>{@code baseValues}：打开草稿时从 Authority 冻结的事务基线；{@link #captureCandidate()} 只取这里做乐观比较</li>
 *   <li>{@code currentValues}：UI dirty 对照基线（用户编辑前的「当前已提交到本草稿视角」的值）</li>
 *   <li>{@code draftValues}：用户编辑中的草稿</li>
 * </ul>
 *
 * <p>所有权：{@link ConfigManager#openDraft()} 绑定该 manager 不可伪造的 owner token；
 * 仅 {@link #from(Authority)} 公开工厂产生的草稿 owner 为 null（兼容测试/工具路径），
 * 不得通过任意 {@link ConfigManager#save} 写盘（{@link SaveOutcome.ConflictType#DRAFT_OWNER_MISMATCH}）。</p>
 *
 * <p>所有 public 读写 / 快照 / mutator 在同一 {@link #lock} 上同步。
 * {@link ConfigManager#save} 只在捕获与提交阶段按 manager → draft 顺序短暂持锁，
 * 外部校验期间不持本类锁；并发编辑通过 revision 冲突检测保留。</p>
 */
public final class DraftBuffer {

    private final ConfigSchema schema;
    /**
     * 绑定的 ConfigManager owner token；{@code null} 表示未绑定（公开 {@link #from(Authority)}）。
     * 不对外暴露对象本身；仅 {@link #hasSameOwner(DraftBuffer)} / 包内 identity 比对。
     */
    private final Object ownerToken;
    /** 事务基线（open 时 Authority 深拷贝）；capture/commit 乐观比较用，不被 prefill/setDraftAndCurrent 改写 */
    private Map<String, Object> baseValues;
    private Map<String, Object> currentValues;
    private Map<String, Object> draftValues;
    private final Object lock = new Object();
    private long revision;

    /** 包内短锁作用域回调，供 ConfigManager capture/commit 阶段保持固定锁序。 */
    interface LockedOperation<T> {
        T run();
    }

    /**
     * base / current / draft 各持独立深拷贝，禁止共享 List/Map 别名。
     */
    private DraftBuffer(ConfigSchema schema,
                        Object ownerToken,
                        Map<String, Object> seedForBase,
                        Map<String, Object> seedForCurrent,
                        Map<String, Object> seedForDraft) {
        this.schema = schema;
        this.ownerToken = ownerToken;
        this.baseValues = new LinkedHashMap<String, Object>(seedForBase);
        this.currentValues = new LinkedHashMap<String, Object>(seedForCurrent);
        this.draftValues = new LinkedHashMap<String, Object>(seedForDraft);
        this.revision = 0L;
    }

    /**
     * 从权威态创建草稿：base / current / draft 各做一次完整深拷贝。
     *
     * <p>公开工厂：owner 未绑定（{@code null}）。此类草稿可用于纯数据测试与 UI 装配；
     * 不得写入任意 {@link ConfigManager}（save 将返回 {@code DRAFT_OWNER_MISMATCH}）。
     * 生产路径请用 {@link ConfigManager#openDraft()} 获得绑定 owner 的草稿。</p>
     *
     * @param authority 权威态，非 null
     * @return 新草稿（owner 未绑定）
     */
    public static DraftBuffer from(Authority authority) {
        return from(authority, null);
    }

    /**
     * 从权威态创建草稿并绑定 owner token（包内 / ConfigManager 使用）。
     *
     * @param authority  权威态，非 null
     * @param ownerToken manager 持有的不可伪造 token；null 表示未绑定
     * @return 新草稿
     */
    static DraftBuffer from(Authority authority, Object ownerToken) {
        if (authority == null) {
            throw new IllegalArgumentException("authority must not be null");
        }
        Map<String, Object> snap = authority.snapshotTyped();
        Map<String, Object> forBase = ValueCopy.copyMapValues(snap);
        Map<String, Object> forCurrent = ValueCopy.copyMapValues(snap);
        Map<String, Object> forDraft = ValueCopy.copyMapValues(snap);
        return new DraftBuffer(authority.schema(), ownerToken, forBase, forCurrent, forDraft);
    }

    /**
     * 是否与另一草稿绑定同一 owner identity（不泄露 token 对象）。
     *
     * <p>两侧均未绑定（token 皆 null）时返回 false——未绑定草稿不得互相冒充「同 manager」。</p>
     *
     * @param other 另一草稿，可为 null
     * @return 同一非 null owner identity 时 true
     */
    public boolean hasSameOwner(DraftBuffer other) {
        if (other == null) {
            return false;
        }
        Object a = this.ownerToken;
        Object b = other.ownerToken;
        return a != null && a == b;
    }

    /**
     * 包内：是否由给定 owner token 拥有。
     *
     * @param expectedToken manager 的 token
     * @return 匹配 true
     */
    boolean isOwnedBy(Object expectedToken) {
        return expectedToken != null && expectedToken == ownerToken;
    }

    long revision() {
        synchronized (lock) {
            return revision;
        }
    }

    /**
     * 取草稿值的防御副本（调用方原地修改不影响内部）。
     *
     * @param path 字段 path
     * @return 防御副本，可能为 null
     */
    public Object getDraft(String path) {
        synchronized (lock) {
            return ValueCopy.copyOf(draftValues.get(path));
        }
    }

    /**
     * 取 current 的防御副本。
     *
     * @param path 字段 path
     * @return 防御副本，可能为 null
     */
    public Object getCurrent(String path) {
        synchronized (lock) {
            return ValueCopy.copyOf(currentValues.get(path));
        }
    }

    /**
     * 取事务 base 的防御副本（包内 / 测试探针）。
     *
     * @param path 字段 path
     * @return 防御副本，可能为 null
     */
    Object getBase(String path) {
        synchronized (lock) {
            return ValueCopy.copyOf(baseValues.get(path));
        }
    }

    /**
     * 写入草稿值（深拷贝），bump revision。
     *
     * @param path  字段 path
     * @param value 新值
     */
    public void setDraft(String path, Object value) {
        synchronized (lock) {
            draftValues.put(path, ValueCopy.copyOf(value));
            revision++;
        }
    }

    /**
     * 同时写 draft 与 current（不写事务 base），使该字段 dirty=false。
     *
     * <p><b>已弃用</b>：会破坏「current 仅表示已提交对照」与发现态 prefill 语义。
     * 展示态预填充请用 UI 层局部只读初值（不写 DraftBuffer）；
     * 事务 base 仅在 open / 成功 commit 时推进。</p>
     *
     * @param path  字段 path
     * @param value 新值
     * @deprecated 使用展示层局部 prefill；勿再靠本方法抹平 dirty 来绕过事务 base
     */
    @Deprecated
    public void setDraftAndCurrent(String path, Object value) {
        synchronized (lock) {
            Object a = ValueCopy.copyOf(value);
            Object b = ValueCopy.copyOf(value);
            draftValues.put(path, a);
            currentValues.put(path, b);
            // 刻意不改 baseValues：事务基线仍对齐 open 时 Authority
            revision++;
        }
    }

    /**
     * 字段是否脏（draft != current）。
     *
     * @param path 字段 path
     * @return 脏时 true
     */
    public boolean isDirty(String path) {
        synchronized (lock) {
            return !Objects.equals(draftValues.get(path), currentValues.get(path));
        }
    }

    /**
     * 是否任一 schema 字段脏。
     *
     * @return 任一脏时 true
     */
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

    /**
     * 单字段内置校验错误文案。
     *
     * @param path 字段 path
     * @return 错误文案或 null
     */
    public String error(String path) {
        return validateAll().errorFor(path);
    }

    /**
     * 是否存在内置校验错误。
     *
     * @return 有错 true
     */
    public boolean hasError() {
        return validateAll().hasErrors();
    }

    /**
     * 全字段内置校验。
     *
     * @return 校验结果
     */
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
     * 对给定 candidate 做内置校验（不持锁读内部表）。
     *
     * @param candidateValues 候选全表
     * @return 校验结果
     */
    ValidationResult validateCandidate(Map<String, Object> candidateValues) {
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

    /**
     * 将 draft 重置为 current（不改 base）。
     */
    public void resetToCurrent() {
        synchronized (lock) {
            draftValues.clear();
            for (Map.Entry<String, Object> e : currentValues.entrySet()) {
                draftValues.put(e.getKey(), ValueCopy.copyOf(e.getValue()));
            }
            revision++;
        }
    }

    /**
     * 将单字段 draft 重置为 schema 默认值。
     *
     * @param path 字段 path
     */
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
     *
     * @return 深拷贝
     */
    public Map<String, Object> draftSnapshot() {
        synchronized (lock) {
            return ValueCopy.copyMapValues(draftValues);
        }
    }

    /**
     * 捕获事务 candidate（package 内部使用）。
     *
     * <p>base 取自 {@link #baseValues}（open 时 Authority），proposed 为 draft 全表；
     * 合法 NUMBER 值在 proposed 中统一为 {@link Double}。</p>
     *
     * @return 事务 candidate
     */
    TransactionCandidate captureCandidate() {
        synchronized (lock) {
            Map<String, Object> base = ValueCopy.copyMapValues(baseValues);
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
     * @param <T>      返回类型
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

    /**
     * revision 是否仍等于捕获时。
     *
     * @param expected 期望 revision
     * @return 匹配 true
     */
    boolean revisionMatches(long expected) {
        synchronized (lock) {
            return revision == expected;
        }
    }

    /**
     * 锁外预制不会再失败的 commit 数据（成功后 base/current/draft 三份对齐 candidate）。
     *
     * @param candidate 事务 candidate
     * @return 预制 commit
     */
    PreparedCommit prepareCandidateCommit(TransactionCandidate candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }
        Map<String, Object> all = ValueCopy.copyMapValues(candidate.proposedValues());
        return new PreparedCommit(
                ValueCopy.copyMapValues(all),
                ValueCopy.copyMapValues(all),
                ValueCopy.copyMapValues(all));
    }

    /**
     * 写盘成功后应用预制 commit；调用方必须持有 draft 锁且已复核 revision。
     *
     * <p>同步推进 base / current / draft 三份。</p>
     *
     * @param prepared 预制 commit
     */
    void applyPreparedCommit(PreparedCommit prepared) {
        if (prepared == null) {
            throw new IllegalArgumentException("prepared must not be null");
        }
        if (!Thread.holdsLock(lock)) {
            throw new IllegalStateException("draft lock is required for commit");
        }
        baseValues = prepared.baseValues;
        draftValues = prepared.draftValues;
        currentValues = prepared.currentValues;
        revision++;
    }

    /**
     * 保存成功：base/draft/current 均对齐 candidate（兼容包内旧调用）。
     *
     * @param candidate 事务 candidate
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
     * 兼容旧测试：current = 当前 draft 的防御拷贝，并推进 base 以保持事务一致。
     * 新事务路径请用 {@link #commitCandidateToCurrent}。
     */
    public void commitDraftToCurrent() {
        synchronized (lock) {
            currentValues = ValueCopy.copyMapValues(draftValues);
            baseValues = ValueCopy.copyMapValues(draftValues);
            revision++;
        }
    }

    /**
     * @return 关联 schema
     */
    public ConfigSchema schema() {
        return schema;
    }

    /**
     * Schema 字段路径的不可变列表副本。
     *
     * @return 路径列表
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

        if (field.type() == FieldType.SIMPLE_LIST && !(value instanceof List)) {
            return "值必须是字符串列表";
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
            case SIMPLE_LIST: {
                for (Object item : (List<?>) value) {
                    if (item != null && !(item instanceof String)) {
                        return "列表元素必须是字符串";
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

    /** 写盘前已完成全部深拷贝的 commit 数据（成功后三份表对齐）。 */
    static final class PreparedCommit {
        private final Map<String, Object> baseValues;
        private final Map<String, Object> draftValues;
        private final Map<String, Object> currentValues;

        PreparedCommit(Map<String, Object> baseValues,
                       Map<String, Object> draftValues,
                       Map<String, Object> currentValues) {
            this.baseValues = baseValues;
            this.draftValues = draftValues;
            this.currentValues = currentValues;
        }
    }
}
