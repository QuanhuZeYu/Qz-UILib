package club.heiqi.config.runtime;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 校验结果，不可变。
 *
 * <p>记录每个出错字段的 path → 错误信息映射。无错误时 {@link #hasErrors()} 返回 false。
 * 用于 {@link SaveOutcome#invalid(ValidationResult)} 与 {@link DraftBuffer#validateAll()}。</p>
 *
 * <p>本类零依赖 uilib，仅依赖 JDK。</p>
 */
public final class ValidationResult {

    /** 空结果单例，表示无任何校验错误 */
    private static final ValidationResult EMPTY = new ValidationResult(Collections.<String, String>emptyMap());

    private final Map<String, String> errors;

    private ValidationResult(Map<String, String> errors) {
        this.errors = errors;
    }

    /**
     * 是否存在校验错误。
     *
     * @return 有任意字段出错返回 true
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * 取指定字段的错误信息。
     *
     * @param path 字段全路径
     * @return 错误信息，无错或字段不存在返回 null
     */
    public String errorFor(String path) {
        return errors.get(path);
    }

    /**
     * 取全部错误的不可修改视图。
     *
     * @return path → 错误信息映射，不可修改
     */
    public Map<String, String> errors() {
        return Collections.unmodifiableMap(errors);
    }

    /**
     * 创建无错误结果。
     *
     * @return 空校验结果
     */
    public static ValidationResult ok() {
        return EMPTY;
    }

    /**
     * 创建单字段错误结果。
     *
     * @param path    出错字段路径
     * @param message 错误信息
     * @return 校验结果
     */
    public static ValidationResult error(String path, String message) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        Map<String, String> map = new HashMap<String, String>();
        map.put(path, message);
        return new ValidationResult(map);
    }

    /**
     * 由错误映射创建结果，内部拷贝。
     *
     * @param errors path → 错误信息映射，可为 null 或空
     * @return 校验结果
     */
    public static ValidationResult of(Map<String, String> errors) {
        if (errors == null || errors.isEmpty()) {
            return EMPTY;
        }
        return new ValidationResult(new HashMap<String, String>(errors));
    }
}
