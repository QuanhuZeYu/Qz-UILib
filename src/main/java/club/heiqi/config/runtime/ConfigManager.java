package club.heiqi.config.runtime;

import club.heiqi.config.ConfigChangeEvent;
import club.heiqi.config.ConfigException;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.schema.FieldType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 配置门面：三阶段乐观保存事务 + 参与式磁盘写前检测 + 三阶段校验后 reload。
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
 * <p>磁盘写前检测（beta）：bootstrap 捕获 {@link ConfigFileSnapshot} 为 expected；save/flushRaw
 * 在 capture 阶段<strong>冻结</strong> expected 基线；commit 复核 manager 当前 expected 仍等于该基线，
 * 且 disk compare/cas 使用该冻结基线——不得在 commit 时无条件改取最新 expected（reload 推进 expected
 * 后，旧 save 必须结构化冲突，禁止拿新 expected 写旧 prepared）。
 * 经 {@link Persistence#casWritePrepared} 与冻结 expected 精确字节比；冲突
 * {@link SaveOutcome.ConflictType#CONFIG_FILE_CHANGED_SINCE_LOAD}（requiresReload=true）。
 * 同 classloader 参与式 writer 串行 + 写前检测已完成外部变更；<b>不</b>承诺阻止外部 writer 的
 * compare→replace 竞态窗口。见 {@link Persistence} 文档。</p>
 *
 * <p>schema 随 bootstrap 冻结（constraints/default/widget）；无 manager 内 schema reload。</p>
 *
 * <h3>reload 三阶段</h3>
 * <ol>
 *   <li><b>capture</b>（manager 锁内）：记录 Authority 深快照 / identity 与 expected 基线，
 *       并取 disk snapshot。</li>
 *   <li><b>validate</b>（锁外）：完整内置+custom 校验；失败抛 {@link ConfigReloadException}
 *      （VALIDATION/IO），零推进零事件。disk 路径按 FieldType 严格 NodeType
 *      （NUMBER 拒绝 quoted 字符串等），与 UI DraftBuffer 的 NUMBER 字符串解析边界分离。</li>
 *   <li><b>commit</b>（manager 锁内 + Persistence 参与式 writer 同一静态写域 monitor）：
 *       复核 Authority 仍等 baseline、expected 未变、当前 disk 仍等 validated snapshot，
 *       再原子更新 Authority/expected 并发 RELOAD。任何变化返回结构化冲突
 *       （AUTHORITY_MODIFIED 或 CONFIG_FILE_CHANGED），零推进零事件。</li>
 * </ol>
 *
 * <p>算法（save）：</p>
 * <ol>
 *   <li>所有权检查（无锁副作用）。</li>
 *   <li>双锁内单次捕获 revision、事务 base 全表、规范化 proposed 全表与 <b>expected 基线</b>；
 *       stale base 立即 {@link SaveOutcome.ConflictType#STALE_DRAFT_BASE}。</li>
 *   <li>完全锁外执行内置/custom 校验，并预制 Authority、draft/base/current 与完整持久化内容。</li>
 *   <li>按相同锁序复锁，复核 revision、Authority==base、expected 仍等 capture 基线；冲突按类型映射。</li>
 *   <li>无冲突时用<strong>冻结 expected 基线</strong>写前检测后写入预制内容，成功后更新 expected，
 *       再以引用交换提交 Authority 与 draft。</li>
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
     * 从磁盘重新加载（三阶段）：capture 基线 → 锁外完整校验 → 写域内 commit。
     *
     * <p><b>不</b>发布 {@code BATCH_SAVE}——本方法是外部 reload，非 save 成功路径。
     * 失败时抛 {@link ConfigReloadException}（VALIDATION / IO / CONFLICT），
     * 旧 Authority / expected 全部不变、零事件。</p>
     *
     * <p>BATCH_SAVE/RELOAD 通知期间调用抛 {@link ConfigConflictException}
     *（{@link SaveOutcome.ConflictType#SAVE_DURING_NOTIFICATION}）。</p>
     *
     * @return 绑定本 manager owner 的新草稿
     * @throws ConfigReloadException 校验/IO/commit 冲突
     * @throws ConfigConflictException 通知期封锁
     * @throws ConfigException 其它兼容路径
     */
    public DraftBuffer reloadDraftFromDisk() throws ConfigException {
        if (isNotificationActive()) {
            throw notificationConflictException("reload");
        }

        // ---- phase 1 capture：manager 锁内记录 Authority 深快照 + expected 基线 + disk snapshot ----
        final Map<String, Object> authorityBaseline;
        final ConfigFileSnapshot expectedBaseline;
        final ConfigFileSnapshot diskSnap;
        synchronized (transactionLock) {
            if (isNotificationActive()) {
                throw notificationConflictException("reload");
            }
            authorityBaseline = authority.deepSnapshotTyped();
            expectedBaseline = expectedDiskSnapshot;
            try {
                diskSnap = ConfigFileSnapshot.capture(persistence.file());
            } catch (ConfigException e) {
                throw new ConfigReloadException(ConfigReloadException.Reason.IO,
                        "reload capture disk failed: " + msg(e), e);
            }
        }

        // ---- phase 2 validate：锁外完整校验（失败不碰 Authority/expected）----
        final Map<String, Object> schemaCandidate;
        final Authority loadedForNonSchema;
        try {
            schemaCandidate = Authority.extractSchemaCandidateForValidation(diskSnap, authority.schema());
            loadedForNonSchema = Authority.load(diskSnap, authority.schema());
        } catch (ConfigException e) {
            // 严格 NodeType 不匹配等：按 VALIDATION 零推进（非 IO）
            String m = msg(e);
            if (m != null && (m.contains("type") || m.contains("类型") || m.contains("NodeType")
                    || m.contains("expected") || m.contains("strict"))) {
                throw new ConfigReloadException(ConfigReloadException.Reason.VALIDATION,
                        "reload type validation failed: " + m, e);
            }
            throw new ConfigReloadException(ConfigReloadException.Reason.IO,
                    "reload parse failed: " + m, e);
        }

        // disk 路径：不在此解析 NUMBER 字符串；仅透传严格提取结果（与 UI DraftBuffer 边界分离）
        Map<String, Object> normalized = copySchemaCandidate(schemaCandidate, authority.schema());

        DraftBuffer probe = DraftBuffer.from(loadedForNonSchema);
        ValidationResult builtIn;
        try {
            builtIn = probe.validateCandidate(normalized);
        } catch (RuntimeException e) {
            throw new ConfigReloadException(ConfigReloadException.Reason.VALIDATION,
                    "reload built-in validation failed: " + msg(e), e);
        }
        ValidationResult custom = runCustomValidatorOnMap(normalized);
        ValidationResult merged = ValidationResult.merge(builtIn, custom);
        if (merged.hasErrors()) {
            throw new ConfigReloadException(ConfigReloadException.Reason.VALIDATION,
                    "reload validation failed: " + merged.summary(120));
        }

        // ---- phase 3 commit：manager 锁 + 写域 monitor 复核后原子更新 ----
        final DraftBuffer[] resultHolder = new DraftBuffer[1];
        final ConfigReloadException[] conflictHolder = new ConfigReloadException[1];
        final boolean[] publishReload = new boolean[] { false };

        synchronized (transactionLock) {
            if (isNotificationActive()) {
                throw notificationConflictException("reload");
            }
            if (!authority.matchesDeepSnapshot(authorityBaseline)) {
                throw new ConfigReloadException(
                        SaveOutcome.ConflictType.AUTHORITY_MODIFIED_DURING_SAVE,
                        "authority was modified during reload validation");
            }
            if (!sameExpectedBaseline(expectedDiskSnapshot, expectedBaseline)) {
                throw new ConfigReloadException(
                        SaveOutcome.ConflictType.AUTHORITY_MODIFIED_DURING_SAVE,
                        "expected disk baseline changed during reload validation");
            }

            Persistence.withWriteDomain(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (!authority.matchesDeepSnapshot(authorityBaseline)) {
                            conflictHolder[0] = new ConfigReloadException(
                                    SaveOutcome.ConflictType.AUTHORITY_MODIFIED_DURING_SAVE,
                                    "authority was modified during reload commit");
                            return;
                        }
                        if (!sameExpectedBaseline(expectedDiskSnapshot, expectedBaseline)) {
                            conflictHolder[0] = new ConfigReloadException(
                                    SaveOutcome.ConflictType.AUTHORITY_MODIFIED_DURING_SAVE,
                                    "expected disk baseline changed during reload commit");
                            return;
                        }
                        if (!Persistence.verifyWriteDomainCurrent(persistence.file(), diskSnap)) {
                            conflictHolder[0] = new ConfigReloadException(
                                    SaveOutcome.ConflictType.CONFIG_FILE_CHANGED_SINCE_LOAD,
                                    "config file changed during reload validation");
                            return;
                        }
                        authority.commitReloadSchemaFields(normalized, loadedForNonSchema);
                        expectedDiskSnapshot = diskSnap;
                        resultHolder[0] = DraftBuffer.from(authority, draftOwnerToken);
                        notificationDepth.incrementAndGet();
                        authority.setMutationGuard(notificationBlockGuard);
                        publishReload[0] = true;
                    } catch (ConfigException e) {
                        conflictHolder[0] = new ConfigReloadException(
                                ConfigReloadException.Reason.IO,
                                "reload commit verify failed: " + msg(e), e);
                    }
                }
            });
        }

        if (conflictHolder[0] != null) {
            throw conflictHolder[0];
        }
        if (publishReload[0]) {
            try {
                eventBus.publish(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.RELOAD));
            } finally {
                endNotification();
            }
        }
        return resultHolder[0];
    }

    private static boolean sameExpectedBaseline(ConfigFileSnapshot a, ConfigFileSnapshot b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.exactBytesEqual(b);
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

        SaveOutcome outcome = verifyWriteAndCommit(draft, capture.candidate, prepared, capture.expectedBaseline);
        if (outcome.isSuccess()) {
            try {
                eventBus.publish(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
            } finally {
                endNotification();
            }
        }
        return outcome;
    }

    /**
     * 第一阶段：按固定锁序捕获唯一 candidate，并冻结 expected 基线。
     * stale draft 立即失败。
     */
    private Capture capture(final DraftBuffer draft) {
        synchronized (transactionLock) {
            if (isNotificationActive()) {
                return Capture.failed(notificationConflict());
            }
            final ConfigFileSnapshot expectedBaseline = expectedDiskSnapshot;
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
                        return Capture.success(candidate, expectedBaseline);
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

    /**
     * 第三阶段：复锁校验、用<strong>冻结 expected 基线</strong>写前检测写盘、无分配引用交换提交。
     *
     * <p>manager 当前 expected 必须仍等于 capture 基线；disk compare 使用该基线，
     * 不得改取最新 expected 写旧 prepared。</p>
     */
    private SaveOutcome verifyWriteAndCommit(
            final DraftBuffer draft,
            final DraftBuffer.TransactionCandidate candidate,
            final PreparedTransaction prepared,
            final ConfigFileSnapshot expectedBaseline) {
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
                    // 冻结基线复核：reload/他 save 推进 expected 后，旧 save 必须结构化冲突
                    if (!sameExpectedBaseline(expectedDiskSnapshot, expectedBaseline)) {
                        return conflict(
                                SaveOutcome.ConflictType.CONFIG_FILE_CHANGED_SINCE_LOAD,
                                "expected disk baseline changed during save (stale prepared write)");
                    }
                    ConfigFileSnapshot newExpected;
                    try {
                        // 使用 capture 冻结的 expectedBaseline，禁止无条件取最新
                        newExpected = persistence.casWritePrepared(prepared.write, expectedBaseline);
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
     * <p>capture 冻结 Authority 深快照 + expected 基线；prepare 锁外；commit 复核两者仍匹配，
     * 并用<strong>冻结 expected 基线</strong>做 cas——不得无条件取最新 expected。</p>
     *
     * <p>通知期抛 {@link ConfigConflictException}（SAVE_DURING_NOTIFICATION）。</p>
     *
     * @throws ConfigConflictException 磁盘与 expected 不等、expected 基线漂移或通知期
     * @throws ConfigException         预制/IO 失败
     */
    public void flushRaw() throws ConfigException {
        if (isNotificationActive()) {
            throw notificationConflictException("flushRaw");
        }
        Map<String, Object> snapshot;
        ConfigFileSnapshot expectedBaseline;
        synchronized (transactionLock) {
            if (isNotificationActive()) {
                throw notificationConflictException("flushRaw");
            }
            snapshot = authority.deepSnapshotTyped();
            expectedBaseline = expectedDiskSnapshot;
        }
        Persistence.PreparedWrite prepared = persistence.prepareWrite(snapshot, authority.schema());
        synchronized (transactionLock) {
            if (isNotificationActive()) {
                throw notificationConflictException("flushRaw");
            }
            if (!authority.matchesDeepSnapshot(snapshot)) {
                throw new ConfigException("authority was modified while preparing flushRaw");
            }
            if (!sameExpectedBaseline(expectedDiskSnapshot, expectedBaseline)) {
                throw new ConfigConflictException(
                        SaveOutcome.ConflictType.CONFIG_FILE_CHANGED_SINCE_LOAD,
                        "expected disk baseline changed while preparing flushRaw");
            }
            try {
                ConfigFileSnapshot newExpected = persistence.casWritePrepared(prepared, expectedBaseline);
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
     * disk/reload 路径：透传严格提取的 schema 候选，<strong>不</strong>解析 NUMBER 字符串。
     * NUMBER 字符串解析仅限 {@link DraftBuffer} / UI 提交边界。
     */
    private static Map<String, Object> copySchemaCandidate(
            Map<String, Object> raw, ConfigSchema schema) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        for (FieldSpec field : schema.allFields()) {
            String path = field.path();
            Object value = raw.get(path);
            // disk 路径：NUMBER 必须已是有限 Number；字符串/错型原样保留供校验拒绝
            if (field.type() == FieldType.NUMBER && value instanceof Number) {
                double n = ((Number) value).doubleValue();
                if (!Double.isNaN(n) && !Double.isInfinite(n)) {
                    out.put(path, Double.valueOf(n));
                    continue;
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
        /** capture 时冻结的 expected 基线；commit cas 必须用此，不得改取最新 */
        private final ConfigFileSnapshot expectedBaseline;
        private final SaveOutcome failure;

        private Capture(DraftBuffer.TransactionCandidate candidate,
                        ConfigFileSnapshot expectedBaseline,
                        SaveOutcome failure) {
            this.candidate = candidate;
            this.expectedBaseline = expectedBaseline;
            this.failure = failure;
        }

        private static Capture success(DraftBuffer.TransactionCandidate candidate,
                                       ConfigFileSnapshot expectedBaseline) {
            return new Capture(candidate, expectedBaseline, null);
        }

        private static Capture failed(SaveOutcome failure) {
            return new Capture(null, null, failure);
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
