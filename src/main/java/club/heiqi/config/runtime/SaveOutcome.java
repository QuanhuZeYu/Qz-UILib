package club.heiqi.config.runtime;

/**
 * 保存操作结果，不可变。
 *
 * <p>封装 {@link ConfigManager#save(DraftBuffer)} 的三种结局：
 * 成功、校验/并发冲突失败、IO 失败。失败时携带原因，成功时仅状态。</p>
 *
 * <p>冲突类失败仍使用 {@link Status#INVALID} 并保留 {@link ValidationResult}，
 * 但通过 {@link ConflictType} / {@link #isConflict()} / {@link #requiresReload()}
 * 结构化区分，UI 不得靠英文错误串匹配。</p>
 *
 * <p>本类零依赖 uilib，仅依赖 JDK 与同包 {@link ValidationResult}。</p>
 */
public final class SaveOutcome {

    private static final SaveOutcome OK_OUTCOME =
            new SaveOutcome(Status.OK, null, null, ConflictType.NONE);

    /** 保存结局状态 */
    public enum Status {
        /** 成功写盘 */
        OK,
        /** 校验未通过或乐观事务冲突，未提交本次 candidate */
        INVALID,
        /** 校验通过但预制/写盘失败，内存状态未提交 */
        IO_FAILED
    }

    /**
     * 乐观事务 / 通知期冲突类型。
     *
     * <p>{@link #NONE} 表示非冲突（成功、普通校验失败、IO 失败）。
     * 冲突仍映射为 {@link Status#INVALID}，但 UI 必须读本枚举而非文案。</p>
     */
    public enum ConflictType {
        /** 非冲突 */
        NONE,
        /** 捕获阶段：draft 事务 base 已不等于 Authority（stale draft） */
        STALE_DRAFT_BASE,
        /** 提交复核：save 过程中 draft revision 已变 */
        DRAFT_MODIFIED_DURING_SAVE,
        /** 提交复核：save 过程中 Authority 已偏离 base */
        AUTHORITY_MODIFIED_DURING_SAVE,
        /** 同一 manager 的 BATCH_SAVE 通知期内禁止再 save */
        SAVE_DURING_NOTIFICATION
    }

    private final Status status;
    private final String errorMessage;
    private final ValidationResult validation;
    private final ConflictType conflictType;

    private SaveOutcome(Status status, String errorMessage, ValidationResult validation,
                        ConflictType conflictType) {
        this.status = status;
        this.errorMessage = errorMessage;
        this.validation = validation;
        this.conflictType = conflictType == null ? ConflictType.NONE : conflictType;
    }

    /**
     * @return 结局状态
     */
    public Status status() {
        return status;
    }

    /**
     * 失败时的错误信息。
     *
     * @return IO_FAILED 时为异常信息，其余可能为 null
     */
    public String errorMessage() {
        return errorMessage;
    }

    /**
     * 校验失败时的详细结果。
     *
     * @return INVALID 时非 null，其余为 null
     */
    public ValidationResult validation() {
        return validation;
    }

    /**
     * 结构化冲突类型。
     *
     * @return 非 null；非冲突时为 {@link ConflictType#NONE}
     */
    public ConflictType conflictType() {
        return conflictType;
    }

    /**
     * 是否为乐观事务 / 通知期冲突（非普通字段校验失败）。
     *
     * @return conflictType != NONE
     */
    public boolean isConflict() {
        return conflictType != ConflictType.NONE;
    }

    /**
     * 是否必须丢弃当前草稿并重新从 Authority 加载后才能再保存。
     *
     * <p>仅 {@link ConflictType#STALE_DRAFT_BASE} 与
     * {@link ConflictType#AUTHORITY_MODIFIED_DURING_SAVE} 为 true；
     * 其余冲突可保留草稿重试，不得静默覆盖 Authority。</p>
     *
     * @return 需要显式 reload 时 true
     */
    public boolean requiresReload() {
        return conflictType == ConflictType.STALE_DRAFT_BASE
                || conflictType == ConflictType.AUTHORITY_MODIFIED_DURING_SAVE;
    }

    /**
     * 是否成功。
     *
     * @return status == OK 时 true
     */
    public boolean isSuccess() {
        return status == Status.OK;
    }

    /**
     * 创建成功结果。
     *
     * @return OK 结局
     */
    public static SaveOutcome ok() {
        return OK_OUTCOME;
    }

    /**
     * 创建校验失败结果（非冲突）。
     *
     * @param result 校验结果，非 null
     * @return INVALID 结局，conflictType=NONE
     */
    public static SaveOutcome invalid(ValidationResult result) {
        if (result == null) {
            throw new IllegalArgumentException("validation result must not be null");
        }
        return new SaveOutcome(Status.INVALID,
                result.hasErrors() ? "validation failed" : null,
                result,
                ConflictType.NONE);
    }

    /**
     * 创建结构化冲突结果（仍为 INVALID，并保留 ValidationResult）。
     *
     * @param type   冲突类型，不可为 null/NONE
     * @param result 校验结果（通常含 _config 诊断摘要），非 null
     * @return INVALID 冲突结局
     */
    public static SaveOutcome conflict(ConflictType type, ValidationResult result) {
        if (type == null || type == ConflictType.NONE) {
            throw new IllegalArgumentException("conflict type must be a real conflict");
        }
        if (result == null) {
            throw new IllegalArgumentException("validation result must not be null");
        }
        return new SaveOutcome(Status.INVALID,
                result.hasErrors() ? "validation failed" : null,
                result,
                type);
    }

    /**
     * 创建 IO 失败结果。
     *
     * @param message 异常信息
     * @return IO_FAILED 结局
     */
    public static SaveOutcome ioFailed(String message) {
        return new SaveOutcome(Status.IO_FAILED, message, null, ConflictType.NONE);
    }
}
