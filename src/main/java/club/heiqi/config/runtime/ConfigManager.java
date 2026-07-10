package club.heiqi.config.runtime;

import club.heiqi.config.ConfigChangeEvent;
import club.heiqi.config.ConfigException;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 配置门面：三阶段乐观保存事务。
 *
 * <p>锁顺序固定为 {@code transactionLock → draft lock}，但只在捕获与最终提交阶段持有。
 * 内置/外部校验和所有可分配的预制工作完全锁外执行。</p>
 *
 * <p>草稿所有权：每实例持有不可伪造 owner token；{@link #openDraft()} 创建绑定该 token 的
 * {@link DraftBuffer}。{@link #save} 在任何 base/validator/persistence 前拒绝 foreign draft
 *（{@link SaveOutcome.ConflictType#DRAFT_OWNER_MISMATCH}，requiresReload=false）。
 * 未绑定 owner 的外部 Draft（如 {@link DraftBuffer#from(Authority)}）不得写任意 manager。</p>
 *
 * <p>算法：</p>
 * <ol>
 *   <li>所有权检查（无锁副作用）。</li>
 *   <li>双锁内单次捕获 revision、事务 base 全表与规范化 proposed 全表；stale base 立即
 *       {@link SaveOutcome.ConflictType#STALE_DRAFT_BASE}。</li>
 *   <li>完全锁外执行内置/custom 校验，并预制 Authority、draft/base/current 与完整持久化内容。</li>
 *   <li>按相同锁序复锁，复核 revision 与 Authority==base；冲突按类型映射并保留真实并发修改。</li>
 *   <li>无冲突时写入预制内容，再以引用交换提交 Authority 与 draft（推进 base/current/draft）。</li>
 *   <li>提交锁内建立 manager 级通知状态，释放全部锁后恰发布一次 BATCH_SAVE；
 *       同一 manager 通知期间任意线程 save 均返回
 *       {@link SaveOutcome.ConflictType#SAVE_DURING_NOTIFICATION}。</li>
 * </ol>
 */
public final class ConfigManager {

    private static final Logger LOG = LogManager.getLogger(ConfigManager.class);

    private final Persistence persistence;
    private final Authority authority;
    private final ConfigEventBus eventBus;
    private final DraftValidator draftValidator;
    private final Object transactionLock;
    private final AtomicInteger batchSaveNotificationDepth = new AtomicInteger(0);
    /**
     * 本 manager 不可伪造的草稿所有权 token（每实例唯一 identity）。
     * 不对外暴露；仅用于 openDraft 绑定与 save 身份比对。
     */
    private final Object draftOwnerToken = new Object();

    private ConfigManager(Persistence persistence, Authority authority, ConfigEventBus eventBus,
                          DraftValidator draftValidator) {
        this.persistence = persistence;
        this.authority = authority;
        this.eventBus = eventBus;
        this.draftValidator = draftValidator;
        this.transactionLock = authority.transactionLock();
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
        Persistence persistence = new Persistence(file, ConfigFormat.YAML);
        Authority authority = Authority.load(file, schema);
        ConfigEventBus eventBus = new ConfigEventBus();
        return new ConfigManager(persistence, authority, eventBus, validator);
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
     * 保存事务（三阶段乐观、单 candidate）。
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
        // 所有权 fail-closed：任何业务逻辑前拒绝 foreign / unbound draft
        if (!draft.isOwnedBy(draftOwnerToken)) {
            return conflict(
                    SaveOutcome.ConflictType.DRAFT_OWNER_MISMATCH,
                    "draft is not owned by this ConfigManager");
        }
        if (isBatchSaveNotificationActive()) {
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
                batchSaveNotificationDepth.decrementAndGet();
            }
        }
        return outcome;
    }

    /** 第一阶段：按固定锁序捕获唯一 candidate，并拒绝已 stale 的 draft。 */
    private Capture capture(final DraftBuffer draft) {
        synchronized (transactionLock) {
            if (isBatchSaveNotificationActive()) {
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

    /** 第三阶段：复锁校验、写盘、无分配引用交换提交。 */
    private SaveOutcome verifyWriteAndCommit(
            final DraftBuffer draft,
            final DraftBuffer.TransactionCandidate candidate,
            final PreparedTransaction prepared) {
        synchronized (transactionLock) {
            if (isBatchSaveNotificationActive()) {
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
                    try {
                        persistence.writePrepared(prepared.write);
                    } catch (ConfigException e) {
                        return SaveOutcome.ioFailed(e.getMessage());
                    }
                    authority.commitPrepared(prepared.authorityState);
                    draft.applyPreparedCommit(prepared.draftCommit);
                    batchSaveNotificationDepth.incrementAndGet();
                    return SaveOutcome.ok();
                }
            });
        }
    }

    public void flushRaw() throws ConfigException {
        Map<String, Object> snapshot;
        synchronized (transactionLock) {
            snapshot = authority.deepSnapshotTyped();
        }
        Persistence.PreparedWrite prepared = persistence.prepareWrite(snapshot, authority.schema());
        synchronized (transactionLock) {
            if (!authority.matchesDeepSnapshot(snapshot)) {
                throw new ConfigException("authority was modified while preparing flushRaw");
            }
            persistence.writePrepared(prepared);
        }
    }

    private ValidationResult runCustomValidator(DraftBuffer.TransactionCandidate candidate) {
        try {
            DraftView view = SnapshotDraftView.ofSchemaFields(
                    authority.schema(), candidate.schemaFieldValues());
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

        Map<String, String> out = new java.util.LinkedHashMap<String, String>();
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

    private static ValidationResult globalFail(String message) {
        return ValidationResult.error(DraftValidator.GLOBAL_ERROR_PATH, message);
    }

    /**
     * 结构化冲突：INVALID + ConflictType；诊断写入 _config，日志仅含类型与内部码（无字段值）。
     */
    private static SaveOutcome conflict(SaveOutcome.ConflictType type, String diagnostic) {
        LOG.warn("config save conflict: type={} detail={}", type, diagnostic);
        if (LOG.isDebugEnabled()) {
            LOG.debug("config save conflict diagnostics: conflictType={}", type);
        }
        // _config 保留内部诊断码供测试/日志，UI 必须读 conflictType 而非匹配本串
        return SaveOutcome.conflict(type, globalFail(diagnostic));
    }

    /** 当前 manager 是否正在发布一次成功保存通知。 */
    private boolean isBatchSaveNotificationActive() {
        return batchSaveNotificationDepth.get() > 0;
    }

    /** 同一 manager 通知期内的保存统一按事务冲突拒绝。 */
    private static SaveOutcome notificationConflict() {
        return conflict(
                SaveOutcome.ConflictType.SAVE_DURING_NOTIFICATION,
                "save during BATCH_SAVE notification is not allowed");
    }

    private static String msg(Throwable e) {
        String m = e.getMessage();
        if (m == null || m.isEmpty()) {
            return e.getClass().getSimpleName();
        }
        return m;
    }

    /** 第一阶段捕获结果。 */
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

    /** 第二阶段预制结果。 */
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
