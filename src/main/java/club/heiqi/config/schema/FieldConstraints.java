package club.heiqi.config.schema;

import com.github.bsideup.jabel.Desugar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 字段约束，不可变值对象。
 * 不同约束按 {@link FieldType} 语义适用于不同类型：
 * <ul>
 *   <li>NUMBER：{@link #min()} / {@link #max()}</li>
 *   <li>STRING：{@link #maxLength()}</li>
 *   <li>CHOICE：{@link #choices()}</li>
 *   <li>通用：{@link #required()}</li>
 * </ul>
 * 未设置的数值约束取语义默认值：min = 负无穷，max = 正无穷，maxLength = -1（表示不限），choices = null。
 */
@Desugar
public record FieldConstraints(
    double min,
    double max,
    int maxLength,
    List<String> choices,
    boolean required
) {
    /**
     * 紧凑构造器，对 choices 做不可变防御性拷贝。
     */
    public FieldConstraints {
        if (choices != null) {
            choices = Collections.unmodifiableList(new ArrayList<>(choices));
        }
    }

    /**
     * 创建一个无任何约束的默认约束对象。
     *
     * @return 无约束的 FieldConstraints
     */
    public static FieldConstraints none() {
        return new FieldConstraints(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -1, null, false);
    }
}
