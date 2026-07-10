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
 * 配置门面：独占串行保存事务。
 *
 * <p>save 算法：</p>
 * <ol>
 *   <li>在 manager 锁上串行；禁止重入。</li>
 *   <li>{@link DraftBuffer#captureCandidate()} 一次捕获稳定 candidate（revision + 值拷贝）。</li>
 *   <li>内置校验 / custom {@link DraftView} / apply / write / commit 全部使用该 candidate。</li>
 *   <li>事务中途 draft revision 变化 → fail-closed INVALID，恢复 candidate 捕获时的 draft/current。</li>
 *   <li>custom 错误 path 必须为 schema 字段或 {@code _config}，否则映射为 {@code _config}。</li>
 * </ol>
 */
public final class ConfigManager {

    private final Persistence persistence;
    private final Authority authority;
    private final ConfigEventBus eventBus;
    private final DraftValidator draftValidator;
    private final Object transactionLock = new Object();
    private boolean inTransaction;

    private ConfigManager(Persistence persistence, Authority authority, ConfigEventBus eventBus,
                          DraftValidator draftValidator) {
        this.persistence = persistence;
        this.authority = authority;
        this.eventBus = eventBus;
        this.draftValidator = draftValidator;
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
        return DraftBuffer.from(authority);
    }

    /**
     * 保存事务（串行、单 candidate）。
     */
    public SaveOutcome save(DraftBuffer draft) {
        if (draft == null) {
            throw new IllegalArgumentException("draft must not be null");
        }
        synchronized (transactionLock) {
            if (inTransaction) {
                // 抛异常使同线程 validator 闭包内的重入被 custom fail-closed 捕获，外层亦 INVALID
                throw new IllegalStateException("reentrant ConfigManager.save is not allowed");
            }
            inTransaction = true;
            try {
                return saveUnderLock(draft);
            } finally {
                inTransaction = false;
            }
        }
    }

    private SaveOutcome saveUnderLock(DraftBuffer draft) {
        DraftBuffer.TransactionCandidate candidate;
        try {
            candidate = draft.captureCandidate();
        } catch (RuntimeException e) {
            return SaveOutcome.invalid(globalFail("capture candidate failed: " + msg(e)));
        }

        // 1. 内置校验（仅 candidate schema 字段）
        ValidationResult builtIn = draft.validateCandidate(candidate.schemaFieldValues());

        // 2. custom（只读 DraftView 仅 schema 字段）
        ValidationResult custom = runCustomValidator(candidate);

        // 3. revision 守卫：validator 闭包若改了原 draft → fail-closed 并恢复
        if (!draft.revisionMatches(candidate.revision())) {
            draft.restoreFromCandidate(candidate);
            return SaveOutcome.invalid(ValidationResult.merge(
                    builtIn,
                    ValidationResult.error(
                            DraftValidator.GLOBAL_ERROR_PATH,
                            "draft was modified during validation")));
        }

        ValidationResult merged = ValidationResult.merge(builtIn, custom);
        if (merged.hasErrors()) {
            return SaveOutcome.invalid(merged);
        }

        // 4. 备份 Authority
        Map<String, Object> authorityBackup = authority.snapshotTyped();

        // 5. apply candidate 全量 draft（含非 schema raw 子树）
        authority.applyAll(ValueCopy.copyMapValues(candidate.allDraftValues()));

        // 6. 写盘；失败回滚 Authority（draft 保持 candidate 用户编辑态，不强制 restore）
        try {
            persistence.writeAll(authority.snapshotTyped(), authority.schema());
        } catch (ConfigException e) {
            authority.applyAll(authorityBackup);
            return SaveOutcome.ioFailed(e.getMessage());
        }

        // 7. 再次 revision 守卫后 commit candidate → current
        if (!draft.revisionMatches(candidate.revision())) {
            // 磁盘已写成功但 draft 被并发改写：Authority 已是 candidate；回滚 Authority 与磁盘语义
            // 保守：回滚 Authority，返回 INVALID（磁盘可能已是新内容——见残余风险）
            authority.applyAll(authorityBackup);
            try {
                persistence.writeAll(authority.snapshotTyped(), authority.schema());
            } catch (ConfigException ignored) {
                // 尽力回写
            }
            draft.restoreFromCandidate(candidate);
            return SaveOutcome.invalid(ValidationResult.error(
                    DraftValidator.GLOBAL_ERROR_PATH,
                    "draft was modified after validation before commit"));
        }

        try {
            draft.commitCandidateToCurrent(candidate);
        } catch (RuntimeException e) {
            authority.applyAll(authorityBackup);
            try {
                persistence.writeAll(authority.snapshotTyped(), authority.schema());
            } catch (ConfigException ignored) {
            }
            draft.restoreFromCandidate(candidate);
            return SaveOutcome.invalid(globalFail("commit failed: " + msg(e)));
        }

        eventBus.publish(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
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

    /**
     * 未知 path → 合并进 {@code _config}，保证 UI 计数可见。
     */
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
