package club.heiqi.uilib.ui.scene.control;

import static club.heiqi.uilib.ui.scene.control.SceneTextGeometry.nullSafe;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SceneKeyValueMapValidation —— 键值对编辑器校验算法层（纯静态工具）。
 *
 * <h3>定位：校验算法层，对标 Flutter validator 外置 + Web Zod 校验独立化</h3>
 * <p>从 {@link SceneKeyValueMap} 抽出校验主算法（空 key / 含点 / 重复 key 三规则）与派生状态
 * {@link ValidationState}，收成静态工具方法。数据模型（{@code KeyValueRow}/{@code ValidationError}/
 * {@code ValidationErrorType}）留主类守公共 API 兼容，校验逻辑独立成可单测的纯函数。</p>
 *
 * <h3>为何放 control 包（守 R12 / I6）</h3>
 * <p>校验只依赖主类的公共数据模型（纯 POJO），不触碰 runtime/input/node/paint，放 control 包与
 * 宿主 {@link SceneKeyValueMap} 同包，可直接以简单名引用其嵌套公共类型，不引入任何新的非法依赖方向。
 * 本类只承担「校验算法」，不夹控件渲染或状态核心，守 R12。</p>
 *
 * <h3>守 R1（静态工具零实例字段）</h3>
 * <p>类为 {@code public final} + {@code private} 构造器，零实例字段、零静态可变状态。唯一的成员类
 * {@link ValidationState} 是不可变值对象（构造后字段不再变更），不属于工具类自身的实例状态。</p>
 *
 * <h3>校验规则（三规则，读源码确认）</h3>
 * <ul>
 *   <li>空 key：{@code key.trim().isEmpty()} → {@code EMPTY_KEY}</li>
 *   <li>含点：{@code key.indexOf('.') >= 0} → {@code KEY_CONTAINS_DOT}</li>
 *   <li>重复 key：同 key 计数 &gt; 1 → {@code DUPLICATE_KEY}</li>
 * </ul>
 * <p>规则按行顺序短路：每行只命中第一个匹配规则；首个错误按行遍历顺序锁定。所有命中规则的行 id
 * 并入 {@code invalidRowIds}，供行高亮批量染色。</p>
 *
 * <h3>理想 L2 单测目标</h3>
 * <p>{@link #validateRows(List)} 是纯函数（输入行列表 → 输出校验状态，无副作用），是理想的 L2 纯数学
 * 边界单测目标：可直接断言三规则各自的命中、首错锁定顺序、invalidRowIds 聚合，无需挂 runtime。</p>
 *
 * @see SceneKeyValueMap
 */
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
    public static SceneKeyValueMap.ValidationError firstError(List<SceneKeyValueMap.KeyValueRow> rows) {
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
    public static ValidationState validateRows(List<SceneKeyValueMap.KeyValueRow> rows) {
        List<SceneKeyValueMap.KeyValueRow> safe = SceneKeyValueMap.safeRows(rows);
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (SceneKeyValueMap.KeyValueRow row : safe) {
            String key = nullSafe(row.getKey());
            counts.put(key, Integer.valueOf(counts.containsKey(key) ? counts.get(key).intValue() + 1 : 1));
        }
        Set<Long> ids = new HashSet<Long>();
        SceneKeyValueMap.ValidationError firstError = SceneKeyValueMap.ValidationError.none();
        for (int i = 0; i < safe.size(); i++) {
            SceneKeyValueMap.KeyValueRow row = safe.get(i);
            String key = nullSafe(row.getKey());
            if (key.trim().isEmpty()) {
                ids.add(Long.valueOf(row.getRowId()));
                if (firstError.getType() == SceneKeyValueMap.ValidationErrorType.NONE) {
                    firstError = new SceneKeyValueMap.ValidationError(
                        SceneKeyValueMap.ValidationErrorType.EMPTY_KEY, i, key);
                }
            } else if (key.indexOf('.') >= 0) {
                ids.add(Long.valueOf(row.getRowId()));
                if (firstError.getType() == SceneKeyValueMap.ValidationErrorType.NONE) {
                    firstError = new SceneKeyValueMap.ValidationError(
                        SceneKeyValueMap.ValidationErrorType.KEY_CONTAINS_DOT, i, key);
                }
            } else if (counts.get(key).intValue() > 1) {
                ids.add(Long.valueOf(row.getRowId()));
                if (firstError.getType() == SceneKeyValueMap.ValidationErrorType.NONE) {
                    firstError = new SceneKeyValueMap.ValidationError(
                        SceneKeyValueMap.ValidationErrorType.DUPLICATE_KEY, i, key);
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
     * {@link SceneKeyValueMap.ValidationError}。</p>
     */
    static final class ValidationState {
        /** 首个校验错误。 */
        private final SceneKeyValueMap.ValidationError validationError;
        /** 错误行 id 集合。 */
        private final Set<Long> invalidRowIds;

        /**
         * 创建校验派生状态。
         *
         * @param validationError 首个校验错误
         * @param invalidRowIds   错误行 id 集合
         */
        ValidationState(SceneKeyValueMap.ValidationError validationError, Set<Long> invalidRowIds) {
            this.validationError = validationError == null ? SceneKeyValueMap.ValidationError.none() : validationError;
            this.invalidRowIds = Collections.unmodifiableSet(new HashSet<Long>(invalidRowIds));
        }

        /**
         * 获取首个校验错误。
         *
         * @return 首个校验错误
         */
        SceneKeyValueMap.ValidationError validationError() {
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
