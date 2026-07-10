package club.heiqi.config.runtime;

import club.heiqi.config.ConfigException;

/**
 * 从磁盘 reload 失败时的结构化原因（UI 按类型显示，禁止英文诊断串匹配）。
 *
 * <p>继承 {@link ConfigException} 保持公开签名兼容；{@link #reason()} 为结构化入口。</p>
 */
public final class ConfigReloadException extends ConfigException {

    /**
     * reload 失败分类。
     */
    public enum Reason {
        /** 内置/custom 校验失败（含类型不一致、越界、validator 拒绝） */
        VALIDATION,
        /** 读盘/解析/非普通文件等 IO 类失败 */
        IO,
        /**
         * 三阶段 commit 复核冲突：Authority 已变、expected 已变、或当前盘不再等于 validated 快照。
         * 零推进、零事件。
         */
        CONFLICT
    }

    private final Reason reason;
    private final SaveOutcome.ConflictType conflictType;

    /**
     * @param reason  失败分类，非 null
     * @param message 诊断信息
     */
    public ConfigReloadException(Reason reason, String message) {
        this(reason, message, null, SaveOutcome.ConflictType.NONE);
    }

    /**
     * @param reason  失败分类，非 null
     * @param message 诊断信息
     * @param cause   原因
     */
    public ConfigReloadException(Reason reason, String message, Throwable cause) {
        this(reason, message, cause, SaveOutcome.ConflictType.NONE);
    }

    /**
     * 冲突类 reload 失败。
     *
     * @param conflictType 结构化冲突类型（AUTHORITY_MODIFIED / CONFIG_FILE_CHANGED 等）
     * @param message      诊断信息
     */
    public ConfigReloadException(SaveOutcome.ConflictType conflictType, String message) {
        this(Reason.CONFLICT, message, null, conflictType);
    }

    private ConfigReloadException(Reason reason, String message, Throwable cause,
                                  SaveOutcome.ConflictType conflictType) {
        super(message, cause);
        if (reason == null) {
            throw new IllegalArgumentException("reason must not be null");
        }
        this.reason = reason;
        this.conflictType = conflictType == null ? SaveOutcome.ConflictType.NONE : conflictType;
    }

    /**
     * @return 失败分类，非 null
     */
    public Reason reason() {
        return reason;
    }

    /**
     * 冲突类时的结构化类型；非 CONFLICT 时为 {@link SaveOutcome.ConflictType#NONE}。
     *
     * @return 冲突类型
     */
    public SaveOutcome.ConflictType conflictType() {
        return conflictType;
    }

    /**
     * 是否须保留 requiresReload 冲突态（CONFLICT 时 true）。
     *
     * @return CONFLICT 时 true
     */
    public boolean keepsConflictState() {
        return reason == Reason.CONFLICT;
    }
}
