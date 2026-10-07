package club.heiqi.uilib.ui.scene.control;

import static club.heiqi.uilib.ui.scene.control.SceneTextUtils.nullSafe;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public final class SceneKeyValueMapValidation {

    /**
     * 纯静态工具类，禁止实例化。
     */
    private SceneKeyValueMapValidation() {
    }

    /**
     * 计算首个校验错误。
     *
     * <p>主类 {@link SceneKeyValueMap#firstValidationError} 的薄委托目标。</p>
     *
     * @param rows 行列表
     * @return 首个校验错误或 none
     */
    public static ValidationError firstError(List<KeyValueRow> rows) {
        return validateRows(rows).validationError();
    }

    /**
     * 计算校验状态（首错 + 错误行 id 集合）。
     *
     * <p>校验主算法：遍历行列表，按空 key / 含点 / 重复 key 三规则判定，命中即并入错误行集合，
     * 并在首错尚未锁定时记录该行的错误类型。规则按行顺序短路：同一行只命中第一个匹配规则。</p>
     *
     * @param rows 行列表
     * @return 校验状态
     */
    public static ValidationState validateRows(List<KeyValueRow> rows) {
        List<KeyValueRow> safe = SceneKeyValueMap.safeRows(rows);
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (KeyValueRow row : safe) {
            String key = nullSafe(row.getKey());
            counts.put(key, Integer.valueOf(counts.containsKey(key) ? counts.get(key).intValue() + 1 : 1));
        }
        Set<Long> ids = new HashSet<Long>();
        ValidationError firstError = ValidationError.none();
        for (int i = 0; i < safe.size(); i++) {
            KeyValueRow row = safe.get(i);
            String key = nullSafe(row.getKey());
            if (key.trim().isEmpty()) {
                ids.add(Long.valueOf(row.getRowId()));
                if (firstError.getType() == ValidationErrorType.NONE) {
                    firstError = new ValidationError(
                        ValidationErrorType.EMPTY_KEY, i, key);
                }
            } else if (key.indexOf('.') >= 0) {
                ids.add(Long.valueOf(row.getRowId()));
                if (firstError.getType() == ValidationErrorType.NONE) {
                    firstError = new ValidationError(
                        ValidationErrorType.KEY_CONTAINS_DOT, i, key);
                }
            } else if (counts.get(key).intValue() > 1) {
                ids.add(Long.valueOf(row.getRowId()));
                if (firstError.getType() == ValidationErrorType.NONE) {
                    firstError = new ValidationError(
                        ValidationErrorType.DUPLICATE_KEY, i, key);
                }
            }
        }
        return new ValidationState(firstError, ids);
    }

    /**
     * 校验派生状态（不可变值对象）。
     *
     * <p>包级可见：仅供同包宿主 {@link SceneKeyValueMap} 在 {@code create}/{@code buildRow}/
     * {@code notifyValidation} 中引用首错与错误行集合；外部不应依赖此类，公共出口是
     * {@link ValidationError}。</p>
     */
    static final class ValidationState {
        /** 首个校验错误。 */
        private final ValidationError validationError;
        /** 错误行 id 集合。 */
        private final Set<Long> invalidRowIds;

        /**
         * 创建校验派生状态。
         *
         * @param validationError 首个校验错误
         * @param invalidRowIds   错误行 id 集合
         */
        ValidationState(ValidationError validationError, Set<Long> invalidRowIds) {
            this.validationError = validationError == null ? ValidationError.none() : validationError;
            this.invalidRowIds = Collections.unmodifiableSet(new HashSet<Long>(invalidRowIds));
        }

        /**
         * 获取首个校验错误。
         *
         * @return 首个校验错误
         */
        ValidationError validationError() {
            return validationError;
        }

        /**
         * 获取错误行 id 集合。
         *
         * @return 错误行 id 集合
         */
        Set<Long> invalidRowIds() {
            return invalidRowIds;
        }
    }
}
