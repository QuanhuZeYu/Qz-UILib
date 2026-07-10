package club.heiqi.config.runtime;

import club.heiqi.config.ConfigChangeEvent;
import club.heiqi.config.ConfigException;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 配置门面：Authority/Legacy 与 draft 双锁串行保存事务。
 *
 * <p>锁顺序：先 {@code transactionLock}，再在 draft 操作内持 {@link DraftBuffer} 实例锁。
 * 其他线程对同一 draft 的编辑在 save 持锁期间阻塞，完成后执行，不会被 candidate 覆盖。</p>
 *
 * <p>算法：</p>
 * <ol>
 *   <li>禁止 reentrant save/flushRaw（同线程 validator 闭包内调用 → 异常 → custom fail-closed）。</li>
 *   <li>捕获 Authority 深快照 + draft candidate（单次）。</li>
 *   <li>内置校验 / custom DraftView 仅用 candidate。</li>
 *   <li>custom 后：若 draft revision 变化 → INVALID，<b>保留</b>闭包产生的 draft 编辑，不 restore；
 *       若 Authority 相对快照变化 → 恢复 Authority 并 INVALID。</li>
 *   <li>合并错误；有错则零副作用返回。</li>
 *   <li>写盘前预制 commit → apply(candidate) → 原子写盘 → 应用预制 commit。</li>
 *   <li>释放 Authority/manager 与 draft 锁后恰发布一次 BATCH_SAVE。</li>
 * </ol>
 */
public final class ConfigManager {

    private final Persistence persistence;
    private final Authority authority;
    private final ConfigEventBus eventBus;
    private final DraftValidator draftValidator;
    private final Object transactionLock;
    private boolean inTransaction;

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

    public DraftBuffer openDraft() {
        synchronized (transactionLock) {
            return DraftBuffer.from(authority);
        }
    }

    /**
     * 保存事务（串行、单 candidate）。
     */
    public SaveOutcome save(DraftBuffer draft) {
        if (draft == null) {
            throw new IllegalArgumentException("draft must not be null");
        }
        SaveOutcome outcome;
        synchronized (transactionLock) {
            if (inTransaction) {
                throw new IllegalStateException("reentrant ConfigManager.save is not allowed");
            }
            inTransaction = true;
            try {
                // 固定锁序：Authority/manager → draft，且 draft 锁贯穿 capture 到 commit
                outcome = draft.withLock(new DraftBuffer.LockedOperation<SaveOutcome>() {
                    @Override
                    public SaveOutcome run() {
                        return saveUnderLocks(draft);
                    }
                });
            } finally {
                inTransaction = false;
            }
        }
        if (outcome.isSuccess()) {
            eventBus.publish(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        }
        return outcome;
    }

    private SaveOutcome saveUnderLocks(DraftBuffer draft) {
        // 0. Authority 旁路基线（深拷贝，含 ConfigNode 序列化重建）
        Map<String, Object> authorityBefore;
        try {
            authorityBefore = authority.deepSnapshotTyped();
        } catch (RuntimeException e) {
            return SaveOutcome.invalid(globalFail("authority snapshot failed: " + msg(e)));
        }

        DraftBuffer.TransactionCandidate candidate;
        try {
            candidate = draft.captureCandidate();
        } catch (RuntimeException e) {
            return SaveOutcome.invalid(globalFail("capture candidate failed: " + msg(e)));
        }

        // 1. 内置校验（candidate schema 字段）
        ValidationResult builtIn = draft.validateCandidate(candidate.schemaFieldValues());

        // 2. custom（只读 DraftView）
        ValidationResult custom = runCustomValidator(candidate);

        // 3a. draft revision：闭包改了 draft → INVALID，保留新编辑，不 restore
        if (!draft.revisionMatches(candidate.revision())) {
            // Authority 若被闭包改过也要恢复
            if (!authority.matchesDeepSnapshot(authorityBefore)) {
                authority.applyAll(authorityBefore);
            }
            return SaveOutcome.invalid(ValidationResult.merge(
                    builtIn,
                    ValidationResult.error(
                            DraftValidator.GLOBAL_ERROR_PATH,
                            "draft was modified during validation")));
        }

        // 3b. Authority 旁路（legacy 等）→ 恢复并 INVALID
        if (!authority.matchesDeepSnapshot(authorityBefore)) {
            authority.applyAll(authorityBefore);
            return SaveOutcome.invalid(ValidationResult.merge(
                    builtIn,
                    ValidationResult.error(
                            DraftValidator.GLOBAL_ERROR_PATH,
                            "authority was modified during validation")));
        }

        ValidationResult merged = ValidationResult.merge(builtIn, custom);
        if (merged.hasErrors()) {
            return SaveOutcome.invalid(merged);
        }

        // 4. 写盘前完成 commit 的全部校验与深拷贝，成功写盘后只做无失败状态替换
        DraftBuffer.PreparedCommit prepared;
        try {
            prepared = draft.prepareCandidateCommit(candidate);
        } catch (RuntimeException e) {
            return SaveOutcome.invalid(globalFail("prepare commit failed: " + msg(e)));
        }

        // 5–6. apply candidate → 写盘 → 应用预制 commit（双锁仍持续持有）
        Map<String, Object> authorityBackup = authority.deepSnapshotTyped();
        authority.applyAll(ValueCopy.copyMapValues(candidate.allDraftValues()));

        try {
            persistence.writeAll(authority.snapshotTyped(), authority.schema());
        } catch (ConfigException e) {
            authority.applyAll(authorityBackup);
            return SaveOutcome.ioFailed(e.getMessage());
        }

        draft.applyPreparedCommit(prepared);
        return SaveOutcome.ok();
    }

    public void flushRaw() throws ConfigException {
        synchronized (transactionLock) {
            if (inTransaction) {
                throw new ConfigException("flushRaw during save transaction is not allowed");
            }
            inTransaction = true;
            try {
                persistence.writeAll(authority.snapshotTyped(), authority.schema());
            } finally {
                inTransaction = false;
            }
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

    private static String msg(Throwable e) {
        String m = e.getMessage();
        if (m == null || m.isEmpty()) {
            return e.getClass().getSimpleName();
        }
        return m;
    }
}
