package club.heiqi.uilib.ui.scene.control;

/**
 * 校验错误类型。
 */
public enum ValidationErrorType {
    /**
     * key 为空。
     */
    EMPTY_KEY,
    /**
     * key 含点号。
     */
    KEY_CONTAINS_DOT,
    /**
     * key 重复。
     */
    DUPLICATE_KEY,
    /**
     * 校验通过。
     */
    NONE
}
