package club.heiqi.config.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 校验结果，不可变；错误映射保序（{@link LinkedHashMap}）。
 *
 * <p>path / message 均规范化：null 或空白 path 拒绝；null/空白 message 替换为稳定占位。</p>
 */
public final class ValidationResult {

    private static final ValidationResult EMPTY =
            new ValidationResult(Collections.<String, String>emptyMap());

    private static final String EMPTY_MESSAGE_PLACEHOLDER = "(empty message)";

    private final Map<String, String> errors;

    private ValidationResult(Map<String, String> errors) {
        this.errors = errors;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public String errorFor(String path) {
        return errors.get(path);
    }

    public Map<String, String> errors() {
        return Collections.unmodifiableMap(errors);
    }

    public static ValidationResult ok() {
        return EMPTY;
    }

    /**
     * 单字段错误。
     *
     * @param path    非 null 非空白
     * @param message 可为 null/空 → 占位文案
     */
    public static ValidationResult error(String path, String message) {
        String p = requirePath(path);
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put(p, normalizeMessage(message));
        return new ValidationResult(map);
    }

    /**
     * 由错误映射创建；跳过 null/空白 path；规范化 message。
     */
    public static ValidationResult of(Map<String, String> errors) {
        if (errors == null || errors.isEmpty()) {
            return EMPTY;
        }
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> e : errors.entrySet()) {
            String path = e.getKey();
            if (path == null || path.trim().isEmpty()) {
                throw new IllegalArgumentException("error path must not be null or blank");
            }
            map.put(path, normalizeMessage(e.getValue()));
        }
        if (map.isEmpty()) {
            return EMPTY;
        }
        return new ValidationResult(map);
    }

    /**
     * 合并；同 path 保留 first；保序（first 键序 + second 新增键序）。
     */
    public static ValidationResult merge(ValidationResult first, ValidationResult second) {
        boolean firstEmpty = first == null || !first.hasErrors();
        boolean secondEmpty = second == null || !second.hasErrors();
        if (firstEmpty && secondEmpty) {
            return EMPTY;
        }
        if (firstEmpty) {
            return second;
        }
        if (secondEmpty) {
            return first;
        }
        Map<String, String> map = new LinkedHashMap<String, String>(first.errors);
        for (Map.Entry<String, String> e : second.errors.entrySet()) {
            if (!map.containsKey(e.getKey())) {
                map.put(e.getKey(), e.getValue());
            }
        }
        return new ValidationResult(map);
    }

    /**
     * 确定性摘要：按插入序首条消息 + 错误数。
     */
    public String summary(int maxMessageLen) {
        if (errors.isEmpty()) {
            return "";
        }
        String firstMsg = null;
        for (String msg : errors.values()) {
            if (msg != null && !msg.isEmpty()) {
                firstMsg = msg;
                break;
            }
        }
        if (firstMsg == null) {
            firstMsg = "校验未通过";
        }
        if (maxMessageLen > 0 && firstMsg.length() > maxMessageLen) {
            firstMsg = firstMsg.substring(0, maxMessageLen) + "…";
        }
        int n = errors.size();
        if (n == 1) {
            return firstMsg;
        }
        return n + " 项：" + firstMsg;
    }

    private static String requirePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("path must not be null or blank");
        }
        return path;
    }

    private static String normalizeMessage(String message) {
        if (message == null || message.isEmpty()) {
            return EMPTY_MESSAGE_PLACEHOLDER;
        }
        return message;
    }
}
