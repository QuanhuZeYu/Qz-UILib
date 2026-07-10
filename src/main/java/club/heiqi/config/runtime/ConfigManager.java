package club.heiqi.config.runtime;

import club.heiqi.config.ConfigChangeEvent;
import club.heiqi.config.ConfigException;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 配置门面：三阶段乐观保存事务 + 参与式磁盘写前检测 + 校验后 reload。
 *
 * <p>锁顺序固定为 {@code transactionLock → draft lock}，但只在捕获与最终提交阶段持有。
 * 内置/外部校验和所有可分配的预制工作完全锁外执行。</p>
 *
 * <p>草稿所有权：每实例持有不可伪造 owner token；{@link #openDraft()} 创建绑定该 token 的
 * {@link DraftBuffer}。{@link #save} 在任何 base/validator/persistence 前拒绝 foreign draft
 *（{@link SaveOutcome.ConflictType#DRAFT_OWNER_MISMATCH}，requiresReload=false）。
 * 未绑定 owner 的外部 Draft（如 {@link DraftBuffer#from(Authority)}）不得写任意 manager。
 * {@link #owns(DraftBuffer)} 公开身份查询且不泄露 token。</p>
 *
 * <p>磁盘写前检测（beta）：bootstrap 捕获 {@link ConfigFileSnapshot} 为 expected；save 最终阶段
 * 经 {@link Persistence#casWritePrepared} 与 expected 精确字节比；冲突
 * {@link SaveOutcome.ConflictType#CONFIG_FILE_CHANGED_SINCE_LOAD}（requiresReload=true）。
 * 同 classloader 参与式 writer 串行 + 写前检测已完成外部变更；<b>不</b>承诺阻止外部 writer 的
 * compare→replace 竞态窗口。见 {@link Persistence} 文档。</p>
 *
 * <p>schema 随 bootstrap 冻结（constraints/default/widget）；无 manager 内 schema reload。
 * {@link #reloadDraftFromDisk()} 从磁盘构造候选，在任何 Authority/expected 更新前执行完整
 * schema 内置校验 + 三参 custom {@link DraftValidator}（冻结 {@link DraftView}）；成功后
 * 原子更新 Authority/expected，锁外发布一次 {@link ConfigChangeEvent.ChangeType#RELOAD}
 *（<b>不得</b>伪装 {@code BATCH_SAVE}）。非法/validator 异常/IO 时旧 Authority/expected 全部不变。</p>
 *
 * <p>算法（save）：</p>
 * <ol>
 *   <li>所有权检查（无锁副作用）。</li>
 *   <li>双锁内单次捕获 revision、事务 base 全表与规范化 proposed 全表；stale base 立即
 *       {@link SaveOutcome.ConflictType#STALE_DRAFT_BASE}。</li>
 *   <li>完全锁外执行内置/custom 校验，并预制 Authority、draft/base/current 与完整持久化内容。</li>
 *   <li>按相同锁序复锁，复核 revision 与 Authority==base；冲突按类型映射并保留真实并发修改。</li>
 *   <li>无冲突时写前检测后写入预制内容，成功后更新 expected，再以引用交换提交 Authority 与 draft。</li>
 *   <li>提交锁内建立 manager 级通知状态并封锁 Authority mutation，释放全部锁后恰发布一次
 *       BATCH_SAVE 或 RELOAD；通知期间 save/flushRaw/reload 与 Legacy mutation 均 fail-closed。</li>
 * </ol>
 */
public final class ConfigManager {

    private static final Logger LOG = LogManager.getLogger(ConfigManager.class);

    private final Persistence persistence;
    private final Authority authority;
    private final ConfigEventBus eventBus;
    private final DraftValidator draftValidator;
    private final Object transactionLock;
    private final AtomicInteger notificationDepth = new AtomicInteger(0);
    /**
     * 本 manager 不可伪造的草稿所有权 token（每实例唯一 identity）。
     * 不对外暴露；仅用于 openDraft 绑定与 save 身份比对。
     */
    private final Object draftOwnerToken = new Object();
    /** 磁盘 expected 快照：bootstrap / 成功写 / reloadFromDisk 后更新 */
    private ConfigFileSnapshot expectedDiskSnapshot;

    /** 通知期 mutation 封锁：抛 SAVE_DURING_NOTIFICATION。 */
    private final AuthorityMutationGuard notificationBlockGuard = new AuthorityMutationGuard() {
        @Override
        public void assertWritable() throws ConfigConflictException {
            throw new ConfigConflictException(
                    SaveOutcome.ConflictType.SAVE_DURING_NOTIFICATION,
                    "authority mutation during BATCH_SAVE/RELOAD notification is not allowed");
        }
    };

    private ConfigManager(Persistence persistence, Authority authority, ConfigEventBus eventBus,
                          DraftValidator draftValidator, ConfigFileSnapshot expectedDiskSnapshot) {
        this.persistence = persistence;
        this.authority = authority;
        this.eventBus = eventBus;
        this.draftValidator = draftValidator;
        this.transactionLock = authority.transactionLock();
        this.expectedDiskSnapshot = expectedDiskSnapshot;
    }

    public static ConfigManager bootstrap(File file, ConfigSchema schema) throws ConfigException {
        return bootstrap(file, schema, DraftValidator.noop());
    }

    public static ConfigManager bootstrap(File file, ConfigSchema schema, DraftValidator validator)
            throws ConfigException {
        if (schema == null) {
            throw new IllegalArgumentException("schema must not be null");
        }
        if (validator == null) {
            throw new IllegalArgumentException("validator must not be null; use DraftValidator.noop()");
        }
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        ConfigFileSnapshot snap = ConfigFileSnapshot.capture(file);
        Persistence persistence = new Persistence(file, ConfigFormat.YAML);
        Authority authority = Authority.load(snap, schema);
        ConfigEventBus eventBus = new ConfigEventBus();
        return new ConfigManager(persistence, authority, eventBus, validator, snap);
    }

    public Authority authority() {
        return authority;
    }

    public ConfigSchema schema() {
        return authority.schema();
    }

    public ConfigEventBus eventBus() {
        return eventBus;
    }

    public DraftValidator draftValidator() {
        return draftValidator;
    }

    /**
     * 当前磁盘 expected 快照（防御视图；测试/诊断用）。
     *
     * @return expected 快照
     */
    ConfigFileSnapshot expectedDiskSnapshot() {
        synchronized (transactionLock) {
            return expectedDiskSnapshot;
        }
    }

    /**
     * 判断草稿是否由本 manager 拥有（不泄露 owner token）。
     *
     * @param draft 草稿，可为 null
     * @return 本 manager openDraft/reload 产生的草稿为 true
     */
    public boolean owns(DraftBuffer draft) {
        return draft != null && draft.isOwnedBy(draftOwnerToken);
    }

    /**
     * 打开绑定本 manager owner 的草稿。
     *
     * @return 新草稿（hasSameOwner 与同 manager 其他 openDraft 结果为 true）
     */
    public DraftBuffer openDraft() {
        synchronized (transactionLock) {
            return DraftBuffer.from(authority, draftOwnerToken);
        }
    }

    /**
     * 从磁盘重新加载：构造候选 → 完整内置+custom 校验 → 成功才原子更新 Authority+expected，
     * 返回同 owner 新 {@link DraftBuffer}；锁外发布一次 {@link ConfigChangeEvent.ChangeType#RELOAD}。
     *
     * <p><b>不</b>发布 {@code BATCH_SAVE}——本方法是外部 reload，非 save 成功路径。
     * 失败（IO / 非法 / validator 拒绝/异常）时保持原 Authority / expected 不变。</p>
     *
     * <p>BATCH_SAVE/RELOAD 通知期间调用抛 {@link ConfigConflictException}
     *（{@link SaveOutcome.ConflictType#SAVE_DURING_NOTIFICATION}）。</p>
     *
     * @return 绑定本 manager owner 的新草稿
     * @throws ConfigException 读盘/非普通文件/解析失败/校验失败/通知期封锁
     */
    public DraftBuffer reloadDraftFromDisk() throws ConfigException {
        // 通知期入口 fail-closed（锁外快速路径 + 锁内复核）
        if (isNotificationActive()) {
            throw notificationConflictException("reload");
        }

        // ---- 锁外：capture 快照 + 提取候选 + 内置/custom 校验（失败不碰 Authority/expected）----
        final ConfigFileSnapshot snap;
        final Map<String, Object> schemaCandidate;
        final Authority loadedForNonSchema;
        try {
            snap = ConfigFileSnapshot.capture(persistence.file());
            schemaCandidate = Authority.extractSchemaCandidateForValidation(snap, authority.schema());
            loadedForNonSchema = Authority.load(snap, authority.schema());
        } catch (ConfigException e) {
            throw e;
        }

        // 规范化 NUMBER 合法值为 Double（与 save candidate 一致）
        Map<String, Object> normalized = normalizeSchemaCandidate(schemaCandidate, authority.schema());

        // 用临时 unbound draft 的 schema 做内置校验（不改任何 manager 状态）
        DraftBuffer probe = DraftBuffer.from(loadedForNonSchema); // unbound, only for validateCandidate
        ValidationResult builtIn;
        try {
            builtIn = probe.validateCandidate(normalized);
        } catch (RuntimeException e) {
            throw new ConfigException("reload built-in validation failed: " + msg(e), e);
        }
        ValidationResult custom = runCustomValidatorOnMap(normalized);
        ValidationResult merged = ValidationResult.merge(builtIn, custom);
        if (merged.hasErrors()) {
            throw new ConfigException("reload validation failed: " + merged.summary(120));
        }

        // ---- 锁内：通知期复核 + 原子提交 Authority/expected + 开通知深度 ----
        DraftBuffer result;
        boolean publishReload = false;
        synchronized (transactionLock) {
            if (isNotificationActive()) {
                throw notificationConflictException("reload");
            }
            authority.commitReloadSchemaFields(normalized, loadedForNonSchema);
            expectedDiskSnapshot = snap;
            result = DraftBuffer.from(authority, draftOwnerToken);
            notificationDepth.incrementAndGet();
            authority.setMutationGuard(notificationBlockGuard);
            publishReload = true;
        }
        if (publishReload) {
            try {
                eventBus.publish(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.RELOAD));
            } finally {
                endNotification();
            }
        }
        return result;
    }

    /**
     * 保存事务（三阶段乐观、单 candidate、写前检测）。
     *
     * <p>任何 base/validator/persistence 前拒绝 foreign draft（owner 不匹配或未绑定）。</p>
     *
     * @param draft 草稿，非 null
     * @return 保存结局（冲突时带结构化 {@link SaveOutcome.ConflictType}）
     */
    public SaveOutcome save(DraftBuffer draft) {
        if (draft == null) {
            throw new IllegalArgumentException("draft must not be null");
        }
        if (!draft.isOwnedBy(draftOwnerToken)) {
            return conflict(
                    SaveOutcome.ConflictType.DRAFT_OWNER_MISMATCH,
                    "draft is not owned by this ConfigManager");
        }
        if (isNotificationActive()) {
            return notificationConflict();
        }

        Capture capture = capture(draft);
        if (capture.failure != null) {
            return capture.failure;
        }

        PreparedTransaction prepared = validateAndPrepare(draft, capture.candidate);
        if (prepared.failure != null) {
            return prepared.failure;
        }

        SaveOutcome outcome = verifyWriteAndCommit(draft, capture.candidate, prepared);
        if (outcome.isSuccess()) {
            try {
                eventBus.publish(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
            } finally {
                endNotification();
            }
        }
        return outcome;
    }

    /** 第一阶段：按固定锁序捕获唯一 candidate，并拒绝已 stale 的 draft。 */
    private Capture capture(final DraftBuffer draft) {
        synchronized (transactionLock) {
            if (isNotificationActive()) {
                return Capture.failed(notificationConflict());
            }
            return draft.withLock(new DraftBuffer.LockedOperation<Capture>() {
                @Override
                public Capture run() {
                    try {
                        DraftBuffer.TransactionCandidate candidate = draft.captureCandidate();
                        if (!authority.matchesDeepSnapshot(candidate.baseValues())) {
                            return Capture.failed(conflict(
                                    SaveOutcome.ConflictType.STALE_DRAFT_BASE,
                                    "draft base no longer matches authority"));
                        }
                        return Capture.success(candidate);
                    } catch (RuntimeException e) {
                        return Capture.failed(SaveOutcome.invalid(
                                globalFail("capture candidate failed: " + msg(e))));
                    }
                }
            });
        }
    }

    /** 第二阶段：锁外校验并完成所有可能分配或失败的预制工作。 */
    private PreparedTransaction validateAndPrepare(
            DraftBuffer draft, DraftBuffer.TransactionCandidate candidate) {
        ValidationResult builtIn;
        try {
            builtIn = draft.validateCandidate(candidate.schemaFieldValues());
        } catch (RuntimeException e) {
            return PreparedTransaction.failed(SaveOutcome.invalid(
                    globalFail("built-in validation failed: " + msg(e))));
        }
        ValidationResult custom = runCustomValidator(candidate);
        ValidationResult merged = ValidationResult.merge(builtIn, custom);
        if (merged.hasErrors()) {
            return PreparedTransaction.failed(SaveOutcome.invalid(merged));
        }

        DraftBuffer.PreparedCommit draftCommit;
        Authority.PreparedState authorityState;
        try {
            draftCommit = draft.prepareCandidateCommit(candidate);
            authorityState = authority.prepareState(candidate.proposedValues());
        } catch (RuntimeException e) {
            return PreparedTransaction.failed(SaveOutcome.invalid(
                    globalFail("prepare commit failed: " + msg(e))));
        }

        Persistence.PreparedWrite write;
        try {
            write = persistence.prepareWrite(candidate.proposedValues(), authority.schema());
        } catch (ConfigException e) {
            return PreparedTransaction.failed(SaveOutcome.ioFailed(e.getMessage()));
        }
        return PreparedTransaction.success(draftCommit, authorityState, write);
    }

    /** 第三阶段：复锁校验、写前检测写盘、无分配引用交换提交。 */
    private SaveOutcome verifyWriteAndCommit(
            final DraftBuffer draft,
            final DraftBuffer.TransactionCandidate candidate,
            final PreparedTransaction prepared) {
        synchronized (transactionLock) {
            if (isNotificationActive()) {
                return notificationConflict();
            }
            return draft.withLock(new DraftBuffer.LockedOperation<SaveOutcome>() {
                @Override
                public SaveOutcome run() {
                    if (!draft.revisionMatches(candidate.revision())) {
                        return conflict(
                                SaveOutcome.ConflictType.DRAFT_MODIFIED_DURING_SAVE,
                                "draft was modified during save");
                    }
                    if (!authority.matchesDeepSnapshot(candidate.baseValues())) {
                        return conflict(
                                SaveOutcome.ConflictType.AUTHORITY_MODIFIED_DURING_SAVE,
                                "authority was modified during save");
                    }
                    ConfigFileSnapshot newExpected;
                    try {
                        newExpected = persistence.casWritePrepared(prepared.write, expectedDiskSnapshot);
                    } catch (ConfigConflictException e) {
                        return conflict(
                                e.conflictType(),
                                e.getMessage() == null ? "disk write-domain conflict" : e.getMessage());
                    } catch (ConfigException e) {
                        return SaveOutcome.ioFailed(e.getMessage());
                    }
                    expectedDiskSnapshot = newExpected;
                    authority.commitPrepared(prepared.authorityState);
                    draft.applyPreparedCommit(prepared.draftCommit);
                    notificationDepth.incrementAndGet();
                    authority.setMutationGuard(notificationBlockGuard);
                    return SaveOutcome.ok();
                }
            });
        }
    }

    /**
     * 将 Authority 当前值 flush 到磁盘（走同一写前检测路径）。
     *
     * <p>通知期抛 {@link ConfigConflictException}（SAVE_DURING_NOTIFICATION）。</p>
     *
     * @throws ConfigConflictException 磁盘与 expected 不等或通知期
     * @throws ConfigException         预制/IO 失败
     */
    public void flushRaw() throws ConfigException {
        if (isNotificationActive()) {
            throw notificationConflictException("flushRaw");
        }
        Map<String, Object> snapshot;
        ConfigFileSnapshot expected;
        synchronized (transactionLock) {
            if (isNotificationActive()) {
                throw notificationConflictException("flushRaw");
            }
            snapshot = authority.deepSnapshotTyped();
            expected = expectedDiskSnapshot;
        }
        Persistence.PreparedWrite prepared = persistence.prepareWrite(snapshot, authority.schema());
        synchronized (transactionLock) {
            if (isNotificationActive()) {
                throw notificationConflictException("flushRaw");
            }
            if (!authority.matchesDeepSnapshot(snapshot)) {
                throw new ConfigException("authority was modified while preparing flushRaw");
            }
            expected = expectedDiskSnapshot;
            try {
                ConfigFileSnapshot newExpected = persistence.casWritePrepared(prepared, expected);
                expectedDiskSnapshot = newExpected;
            } catch (ConfigConflictException e) {
                throw e;
            }
        }
    }

    private ValidationResult runCustomValidator(DraftBuffer.TransactionCandidate candidate) {
        return runCustomValidatorOnMap(candidate.schemaFieldValues());
    }

    private ValidationResult runCustomValidatorOnMap(Map<String, Object> schemaFieldValues) {
        try {
            DraftView view = SnapshotDraftView.ofSchemaFields(
                    authority.schema(), schemaFieldValues);
            ValidationResult result = draftValidator.validate(view);
            if (result == null) {
                return ValidationResult.error(
                        DraftValidator.GLOBAL_ERROR_PATH,
                        "DraftValidator returned null");
            }
            return sanitizeCustomPaths(result);
        } catch (RuntimeException e) {
            return ValidationResult.error(
                    DraftValidator.GLOBAL_ERROR_PATH,
                    "DraftValidator failed: " + msg(e));
        }
    }

    private ValidationResult sanitizeCustomPaths(ValidationResult result) {
        if (result == null || !result.hasErrors()) {
            return result == null ? ValidationResult.ok() : result;
        }
        Set<String> allowed = new HashSet<String>();
        for (FieldSpec f : authority.schema().allFields()) {
            allowed.add(f.path());
        }
        allowed.add(DraftValidator.GLOBAL_ERROR_PATH);

        Map<String, String> out = new LinkedHashMap<String, String>();
        StringBuilder globalExtra = new StringBuilder();
        for (Map.Entry<String, String> e : result.errors().entrySet()) {
            String path = e.getKey();
            String message = e.getValue();
            if (path != null && allowed.contains(path)) {
                out.put(path, message);
            } else {
                if (globalExtra.length() > 0) {
                    globalExtra.append("; ");
                }
                globalExtra.append(path == null ? "?" : path).append(": ").append(message);
            }
        }
        if (globalExtra.length() > 0) {
            String existing = out.get(DraftValidator.GLOBAL_ERROR_PATH);
            String combined = existing == null
                    ? "unknown validation path: " + globalExtra
                    : existing + "; unknown path: " + globalExtra;
            out.put(DraftValidator.GLOBAL_ERROR_PATH, combined);
        }
        return ValidationResult.of(out);
    }

    /**
     * 将 schema 候选中合法 NUMBER 统一为 Double（与 DraftBuffer.captureCandidate 一致）。
     */
    private static Map<String, Object> normalizeSchemaCandidate(
            Map<String, Object> raw, ConfigSchema schema) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        for (FieldSpec field : schema.allFields()) {
            String path = field.path();
            Object value = raw.get(path);
            if (field.type() == club.heiqi.config.schema.FieldType.NUMBER && value != null) {
                if (value instanceof Number) {
                    double n = ((Number) value).doubleValue();
                    if (!Double.isNaN(n) && !Double.isInfinite(n)) {
                        out.put(path, Double.valueOf(n));
                        continue;
                    }
                } else {
                    try {
                        double n = Double.parseDouble(String.valueOf(value));
                        if (!Double.isNaN(n) && !Double.isInfinite(n)) {
                            out.put(path, Double.valueOf(n));
                            continue;
                        }
                    } catch (NumberFormatException ignored) {
                        // keep raw for validation reject
                    }
                }
            }
            out.put(path, ValueCopy.copyOf(value));
        }
        return out;
    }

    private static ValidationResult globalFail(String message) {
        return ValidationResult.error(DraftValidator.GLOBAL_ERROR_PATH, message);
    }

    private static SaveOutcome conflict(SaveOutcome.ConflictType type, String diagnostic) {
        LOG.warn("config save conflict: type={} detail={}", type, diagnostic);
        if (LOG.isDebugEnabled()) {
            LOG.debug("config save conflict diagnostics: conflictType={}", type);
        }
        return SaveOutcome.conflict(type, globalFail(diagnostic));
    }

    private boolean isNotificationActive() {
        return notificationDepth.get() > 0;
    }

    private void endNotification() {
        synchronized (transactionLock) {
            int left = notificationDepth.decrementAndGet();
            if (left <= 0) {
                if (left < 0) {
                    notificationDepth.set(0);
                }
                authority.setMutationGuard(AuthorityMutationGuard.ALLOW);
            }
        }
    }

    private static SaveOutcome notificationConflict() {
        return conflict(
                SaveOutcome.ConflictType.SAVE_DURING_NOTIFICATION,
                "save/flush/reload during BATCH_SAVE/RELOAD notification is not allowed");
    }

    private static ConfigConflictException notificationConflictException(String op) {
        return new ConfigConflictException(
                SaveOutcome.ConflictType.SAVE_DURING_NOTIFICATION,
                op + " during BATCH_SAVE/RELOAD notification is not allowed");
    }

    private static String msg(Throwable e) {
        String m = e.getMessage();
        if (m == null || m.isEmpty()) {
            return e.getClass().getSimpleName();
        }
        return m;
    }

    private static final class Capture {
        private final DraftBuffer.TransactionCandidate candidate;
        private final SaveOutcome failure;

        private Capture(DraftBuffer.TransactionCandidate candidate, SaveOutcome failure) {
            this.candidate = candidate;
            this.failure = failure;
        }

        private static Capture success(DraftBuffer.TransactionCandidate candidate) {
            return new Capture(candidate, null);
        }

        private static Capture failed(SaveOutcome failure) {
            return new Capture(null, failure);
        }
    }

    private static final class PreparedTransaction {
        private final DraftBuffer.PreparedCommit draftCommit;
        private final Authority.PreparedState authorityState;
        private final Persistence.PreparedWrite write;
        private final SaveOutcome failure;

        private PreparedTransaction(DraftBuffer.PreparedCommit draftCommit,
                                    Authority.PreparedState authorityState,
                                    Persistence.PreparedWrite write,
                                    SaveOutcome failure) {
            this.draftCommit = draftCommit;
            this.authorityState = authorityState;
            this.write = write;
            this.failure = failure;
        }

        private static PreparedTransaction success(DraftBuffer.PreparedCommit draftCommit,
                                                   Authority.PreparedState authorityState,
                                                   Persistence.PreparedWrite write) {
            return new PreparedTransaction(draftCommit, authorityState, write, null);
        }

        private static PreparedTransaction failed(SaveOutcome failure) {
            return new PreparedTransaction(null, null, null, failure);
        }
    }
}
