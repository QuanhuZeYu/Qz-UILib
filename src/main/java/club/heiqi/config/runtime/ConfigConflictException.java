package club.heiqi.config.runtime;

import club.heiqi.config.ConfigException;

/**
 * 配置磁盘 CAS / 乐观写冲突异常。
 *
 * <p>继承 {@link ConfigException} 以兼容 {@code flushRaw() throws ConfigException} 签名；
 * 调用方可按类型捕获并读取 {@link #conflictType()}，不得旁路 CAS 后静默写盘。</p>
 *
 * <p>save 路径将同类冲突映射为 {@link SaveOutcome} 结构化类型，不抛本异常。</p>
 */
public final class ConfigConflictException extends ConfigException {

    private final SaveOutcome.ConflictType conflictType;

    /**
     * @param conflictType 冲突类型，不可为 null/NONE
     * @param message      诊断信息
     */
    public ConfigConflictException(SaveOutcome.ConflictType conflictType, String message) {
        super(message);
        if (conflictType == null || conflictType == SaveOutcome.ConflictType.NONE) {
            throw new IllegalArgumentException("conflictType must be a real conflict");
        }
        this.conflictType = conflictType;
    }

    /**
     * @param conflictType 冲突类型，不可为 null/NONE
     * @param message      诊断信息
     * @param cause        原因
     */
    public ConfigConflictException(SaveOutcome.ConflictType conflictType, String message, Throwable cause) {
        super(message, cause);
        if (conflictType == null || conflictType == SaveOutcome.ConflictType.NONE) {
            throw new IllegalArgumentException("conflictType must be a real conflict");
        }
        this.conflictType = conflictType;
    }

    /**
     * @return 结构化冲突类型
     */
    public SaveOutcome.ConflictType conflictType() {
        return conflictType;
    }
}
