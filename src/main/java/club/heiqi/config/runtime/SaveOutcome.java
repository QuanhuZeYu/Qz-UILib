package club.heiqi.config.runtime;

/**
 * 保存操作结果，不可变。
 *
 * <p>封装 {@link ConfigManager#save(DraftBuffer)} 的三种结局：
 * 成功、校验失败、IO 失败。失败时携带原因，成功时仅状态。</p>
 *
 * <p>本类零依赖 uilib，仅依赖 JDK 与同包 {@link ValidationResult}。</p>
 */
public final class SaveOutcome {

    /** 保存结局状态 */
    public enum Status {
        /** 成功写盘 */
        OK,
        /** 校验未通过，未写盘 */
        INVALID,
        /** 校验通过但写盘失败，已回滚 */
        IO_FAILED
    }

    private final Status status;
    private final String errorMessage;
    private final ValidationResult validation;

    private SaveOutcome(Status status, String errorMessage, ValidationResult validation) {
        this.status = status;
        this.errorMessage = errorMessage;
        this.validation = validation;
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
        return new SaveOutcome(Status.OK, null, null);
    }

    /**
     * 创建校验失败结果。
     *
     * @param result 校验结果，非 null
     * @return INVALID 结局
     */
    public static SaveOutcome invalid(ValidationResult result) {
        if (result == null) {
            throw new IllegalArgumentException("validation result must not be null");
        }
        return new SaveOutcome(Status.INVALID, result.hasErrors() ? "validation failed" : null, result);
    }

    /**
     * 创建 IO 失败结果。
     *
     * @param message 异常信息
     * @return IO_FAILED 结局
     */
    public static SaveOutcome ioFailed(String message) {
        return new SaveOutcome(Status.IO_FAILED, message, null);
    }
}
