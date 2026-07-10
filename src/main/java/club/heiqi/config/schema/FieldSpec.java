package club.heiqi.config.schema;

import com.github.bsideup.jabel.Desugar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 单字段元数据，不可变。
 * 描述一个配置字段的路径、类型、默认值、约束以及 UI 显示信息。
 */
@Desugar
public record FieldSpec(
    /** 字段全路径，点号分隔，例如 "general.scale" */
    String path,
    /** 字段类型 */
    FieldType type,
    /** 类型安全的默认值，类型需与 {@link #type()} 匹配 */
    Object defaultValue,
    /** 字段约束 */
    FieldConstraints constraints,
    /** UI 显示标签，可选，未设置时为 null */
    String label,
    /** UI 帮助文本，可选，未设置时为 null */
    String helper,
    /** NUMBER 字段的 widget 声明，null 表示默认走 input；非 NUMBER 字段忽略 */
    WidgetSpec widget
) {
    /**
     * 紧凑构造器，做基本非空校验。
     */
    public FieldSpec {
        if (path == null) {
            throw new IllegalArgumentException("FieldSpec.path 不能为 null");
        }
        if (type == null) {
            throw new IllegalArgumentException("FieldSpec.type 不能为 null");
        }
        if (constraints == null) {
            constraints = FieldConstraints.none();
        }
        if (type == FieldType.SIMPLE_LIST && defaultValue != null) {
            if (!(defaultValue instanceof List)) {
                throw new IllegalArgumentException("SIMPLE_LIST 默认值必须是 List");
            }
            List<Object> frozen = new ArrayList<Object>(((List<?>) defaultValue).size());
            for (Object item : (List<?>) defaultValue) {
                if (item != null && !(item instanceof String)) {
                    throw new IllegalArgumentException("SIMPLE_LIST 默认值元素必须是 String");
                }
                frozen.add(item);
            }
            defaultValue = Collections.unmodifiableList(frozen);
        }
    }

    /**
     * 字段构建器，提供链式设置默认值、标签、帮助文本与约束的 DSL。
     * {@link #build()} 时做类型校验，校验失败抛 {@link IllegalArgumentException}；
     * 构建完成后返回父 {@link SectionSpec.Builder}，回到分类作用域。
     *
     * @param <T> 默认值类型，由 {@link SectionSpec.Builder#string}/{@link #number}/
     *            {@link #bool}/{@link #choice} 工厂方法在编译期绑定，
     *            使 {@link #defaultValue(Object)} 获得编译期类型检查。
     *            range/slider/maxLength/options 等约束方法暂留基类做运行时按 type 校验。
     */
    public static final class Builder<T> {
        private final SectionSpec.Builder parent;
        private final String path;
        private final FieldType type;

        private T defaultValue;
        private boolean hasDefault = false;
        private String label;
        private String helper;

        // widget 声明（NUMBER 专用），null = 默认 input
        private WidgetSpec widget;

        // 约束相关
        private Double min;       // null = 未设
        private Double max;       // null = 未设
        private int maxLength = -1;
        private List<String> choices;
        private boolean required = false;

        /**
         * 构造字段构建器。
         *
         * @param parent 父分类构建器
         * @param path   字段全路径
         * @param type   字段类型
         */
        Builder(SectionSpec.Builder parent, String path, FieldType type) {
            this.parent = parent;
            this.path = path;
            this.type = type;
        }

        /**
         * 设置默认值。类型需与 {@link #type()} 匹配，否则在 {@link #build()} 时抛异常。
         * 泛型化后，T 由工厂方法在编译期绑定，调用方传入不匹配的类型会直接编译报错；
         * 运行期 {@link #validateType(Object)} 仍保留作为双校验兜底。
         *
         * @param value 默认值
         * @return 当前构建器
         */
        public Builder<T> defaultValue(T value) {
            this.defaultValue = value;
            this.hasDefault = true;
            return this;
        }

        /**
         * 设置 UI 显示标签。
         *
         * @param label 标签
         * @return 当前构建器
         */
        public Builder<T> label(String label) {
            this.label = label;
            return this;
        }

        /**
         * 设置 UI 帮助文本。
         *
         * @param helper 帮助文本
         * @return 当前构建器
         */
        public Builder<T> helper(String helper) {
            this.helper = helper;
            return this;
        }

        /**
         * NUMBER 专用：设置数值范围。
         *
         * @param min 最小值
         * @param max 最大值
         * @return 当前构建器
         */
        public Builder<T> range(double min, double max) {
            this.min = min;
            this.max = max;
            return this;
        }

        /**
         * STRING 专用：设置最大长度。
         *
         * @param maxLength 最大长度
         * @return 当前构建器
         */
        public Builder<T> maxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        /**
         * CHOICE 专用：设置可选项列表。
         *
         * @param opts 可选项
         * @return 当前构建器
         */
        public Builder<T> options(String... opts) {
            this.choices = new ArrayList<String>(Arrays.asList(opts));
            return this;
        }

        /**
         * 通用约束：标记字段为必填。
         *
         * @return 当前构建器
         */
        public Builder<T> required() {
            this.required = true;
            return this;
        }

        /**
         * NUMBER 专用：声明字段使用 slider widget（连续，step=0 不量化）。
         *
         * @return 当前构建器
         */
        public Builder<T> slider() {
            this.widget = SliderSpec.continuous();
            return this;
        }

        /**
         * NUMBER 专用：声明字段使用 slider widget 并指定量化步进。
         *
         * @param step 量化步进，{@code step=0} 表示连续不量化，{@code step>0} 表示量化步进，不能为负
         * @return 当前构建器
         */
        public Builder<T> slider(double step) {
            this.widget = new SliderSpec(step);
            return this;
        }

        /**
         * NUMBER 专用：显式声明字段使用 input widget（文本输入框）。
         * 与不调用任何 widget 方法（widget=null）效果一致，用于显式表达意图。
         *
         * @return 当前构建器
         */
        public Builder<T> input() {
            this.widget = InputSpec.INSTANCE;
            return this;
        }

        /**
         * 构建字段元数据，做类型校验，并返回父分类构建器。
         *
         * @return 父分类构建器
         * @throws IllegalArgumentException 默认值类型不匹配或 CHOICE 默认值不在 options 内
         */
        public SectionSpec.Builder build() {
            Object resolved = resolveDefault();
            FieldConstraints constraints = new FieldConstraints(
                min == null ? Double.NEGATIVE_INFINITY : min,
                max == null ? Double.POSITIVE_INFINITY : max,
                maxLength,
                choices,
                required
            );
            validateConstraints(resolved, constraints);
            parent.addField(new FieldSpec(path, type, resolved, constraints, label, helper, widget));
            return parent;
        }

        /**
         * 解析默认值：已设置则校验类型，未设置则按类型给合理默认。
         *
         * @return 默认值
         */
        private Object resolveDefault() {
            if (hasDefault) {
                validateType(defaultValue);
                return defaultValue;
            }
            switch (type) {
                case STRING:
                    return "";
                case NUMBER:
                    return Double.valueOf(0.0);
                case BOOLEAN:
                    return Boolean.FALSE;
                case CHOICE:
                    if (choices == null || choices.isEmpty()) {
                        throw new IllegalArgumentException(
                            "CHOICE 字段 " + path + " 未声明 options，无法推断默认值");
                    }
                    return choices.get(0);
                case SIMPLE_LIST:
                    return new ArrayList<String>();
                default:
                    throw new IllegalArgumentException("未知字段类型: " + type);
            }
        }

        /**
         * 校验默认值类型与字段类型是否匹配。
         *
         * @param value 默认值
         * @throws IllegalArgumentException 类型不匹配
         */
        private void validateType(Object value) {
            switch (type) {
                case STRING:
                    if (!(value instanceof String)) {
                        throw new IllegalArgumentException(
                            "STRING 字段 " + path + " 的默认值必须是 String，实际: " + className(value));
                    }
                    break;
                case NUMBER:
                    if (!(value instanceof Number)) {
                        throw new IllegalArgumentException(
                            "NUMBER 字段 " + path + " 的默认值必须是 Number，实际: " + className(value));
                    }
                    break;
                case BOOLEAN:
                    if (!(value instanceof Boolean)) {
                        throw new IllegalArgumentException(
                            "BOOLEAN 字段 " + path + " 的默认值必须是 Boolean，实际: " + className(value));
                    }
                    break;
                case CHOICE:
                    if (!(value instanceof String)) {
                        throw new IllegalArgumentException(
                            "CHOICE 字段 " + path + " 的默认值必须是 String，实际: " + className(value));
                    }
                    if (choices == null || !choices.contains(value)) {
                        throw new IllegalArgumentException(
                            "CHOICE 字段 " + path + " 的默认值 " + value + " 不在 options 内");
                    }
                    break;
                case SIMPLE_LIST:
                    if (!(value instanceof List)) {
                        throw new IllegalArgumentException(
                            "SIMPLE_LIST 字段 " + path + " 的默认值必须是 List，实际: " + className(value));
                    }
                    break;
                default:
                    throw new IllegalArgumentException("未知字段类型: " + type);
            }
        }

        /**
         * 校验默认值是否满足约束（range/maxLength/choices）。
         * 避免 build 阶段产生"默认值不合法"的死锁场景。
         *
         * @param value       默认值
         * @param constraints 约束
         * @throws IllegalArgumentException 默认值违反约束
         */
        private void validateConstraints(Object value, FieldConstraints constraints) {
            if (value == null) {
                return;
            }
            switch (type) {
                case NUMBER:
                    if (value instanceof Number) {
                        double v = ((Number) value).doubleValue();
                        if (v < constraints.min()) {
                            throw new IllegalArgumentException(
                                "NUMBER 字段 " + path + " 的默认值 " + v + " 小于下限 " + constraints.min());
                        }
                        if (v > constraints.max()) {
                            throw new IllegalArgumentException(
                                "NUMBER 字段 " + path + " 的默认值 " + v + " 大于上限 " + constraints.max());
                        }
                    }
                    break;
                case STRING:
                    if (value instanceof String && constraints.maxLength() >= 0) {
                        int len = ((String) value).length();
                        if (len > constraints.maxLength()) {
                            throw new IllegalArgumentException(
                                "STRING 字段 " + path + " 的默认值长度 " + len + " 超过上限 " + constraints.maxLength());
                        }
                    }
                    break;
                case CHOICE:
                    if (value instanceof String && constraints.choices() != null) {
                        if (!constraints.choices().contains(value)) {
                            throw new IllegalArgumentException(
                                "CHOICE 字段 " + path + " 的默认值 " + value + " 不在 options 内");
                        }
                    }
                    break;
                default:
                    break;
            }
        }

        private static String className(Object value) {
            return value == null ? "null" : value.getClass().getName();
        }
    }
}
