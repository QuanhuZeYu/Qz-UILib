package club.heiqi.uilib.ui.scene.control;

import static club.heiqi.uilib.ui.scene.control.SceneTextUtils.nullSafe;

/**
 * 校验错误回调载荷。
 */
public final class ValidationError {
    /**
     * 错误类型。
     */
    private final ValidationErrorType type;
    /**
     * 错误行下标；无错误时为 -1。
     */
    private final int rowIndex;
    /**
     * 错误 key；无错误时为空串。
     */
    private final String key;

    /**
     * 创建校验错误。
     *
     * @param type     错误类型
     * @param rowIndex 错误行下标
     * @param key      错误 key
     */
    public ValidationError(ValidationErrorType type, int rowIndex, String key) {
        this.type = type == null ? ValidationErrorType.NONE : type;
        this.rowIndex = rowIndex;
        this.key = nullSafe(key);
    }

    /**
     * 创建校验通过载荷。
     *
     * @return 校验通过载荷
     */
    public static ValidationError none() {
        return new ValidationError(ValidationErrorType.NONE, -1, "");
    }

    /**
     * 获取错误类型。
     *
     * @return 错误类型
     */
    public ValidationErrorType getType() {
        return type;
    }

    /**
     * 获取错误行下标。
     *
     * @return 错误行下标
     */
    public int getRowIndex() {
        return rowIndex;
    }

    /**
     * 获取错误 key。
     *
     * @return 错误 key
     */
    public String getKey() {
        return key;
    }
}
