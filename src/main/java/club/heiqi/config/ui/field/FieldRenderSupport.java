package club.heiqi.config.ui.field;

import club.heiqi.config.schema.FieldSpec;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;

/**
 * 字段渲染器共享静态工具：收敛 8 个 {@link FieldRenderer} 实现间重复的样板片段——
 * 标题回退（{@code labelOf}）与 draftSignal 类型转换（String / Double / 数字文本显示）。
 *
 * <p><b>config 层专用工具</b>：本类吃 {@link FieldSpec} / {@link ReadableSignal} 等 config 与
 * reactive 类型，<b>不得下沉到 {@code uilib.ui.scene.form}</b>——后者对外承诺零 config 依赖
 * （守 U1 / form 子包 {@code package-info} 零业务依赖契约 / scene 边界门禁断言 3）。
 * 把本类放进 {@code config.ui.field} 而非 {@code uilib}，正是为了让 FormFieldShell 保持
 * 只吃 {@code String}/{@code ReadableSignal}/{@code Supplier}/{@code FormTheme} 的纯泛型组合层语义。</p>
 *
 * <p>纯静态方法、无实例字段，守 R1（控件契约零内部状态）的近似延伸：renderer 自身仍是无状态工厂，
 * 本工具仅为其提供无状态函数。</p>
 */
public final class FieldRenderSupport {

    /** 工具类，禁止实例化 */
    private FieldRenderSupport() {
    }

    /**
     * 标题回退：{@code spec.label()} 为 {@code null} 或空串时回退 {@code spec.path()}。
     *
     * <p>原 7 个 renderer 各自重复实现该方法（实现完全一致），此处收口为单一实现。</p>
     *
     * @param spec 字段元数据
     * @return 显示用标题文本
     */
    public static String labelOf(FieldSpec spec) {
        String label = spec.label();
        return label == null || label.isEmpty() ? spec.path() : label;
    }

    /**
     * {@code draftSignal<Object>} → {@code ReadableSignal<String>}：STRING 字段适配用。
     *
     * <p>语义复刻 {@code StringFieldRenderer} 原内联 {@code Computed}：
     * {@code null} → {@code ""}，其余调 {@link String#valueOf(Object)}。</p>
     *
     * @param source draft 原始 Object signal
     * @return 派生 String signal（null 安全为空串）
     */
    public static ReadableSignal<String> toStringSignal(ReadableSignal<Object> source) {
        return Computed.create(() -> {
            Object v = source.get();
            return v == null ? "" : String.valueOf(v);
        });
    }

    /**
     * {@code draftSignal<Object>} → {@code ReadableSignal<String>}：NUMBER 字段文本输入场景。
     *
     * <p>语义复刻 {@code NumberFieldRenderer.renderTextInput} 原内联 {@code Computed}：
     * {@code null} → {@code ""}；{@link Number} 经 {@link #formatReadout(double)} 去整{@code .0}，
     * 其余调 {@link String#valueOf(Object)}（parse 失败存原 String 透出，由 DraftBuffer 校验报错）。</p>
     *
     * @param source draft 原始 Object signal
     * @return 派生 String signal（数字格式化 / 非 Number 透传 valueOf）
     */
    public static ReadableSignal<String> toNumberStringSignal(ReadableSignal<Object> source) {
        return Computed.create(() -> {
            Object v = source.get();
            if (v == null) {
                return "";
            }
            if (v instanceof Number) {
                return formatReadout(((Number) v).doubleValue());
            }
            return String.valueOf(v);
        });
    }

    /**
     * {@code draftSignal<Object>} → {@code ReadableSignal<Double>}：NUMBER slider 场景。
     *
     * <p>语义复刻 {@code NumberFieldRenderer.renderSlider} 原内联 {@code Computed}：
     * 经 {@link #toDouble(Object)} 安全转换，无法解析返回 {@code 0.0}。</p>
     *
     * @param source draft 原始 Object signal
     * @return 派生 Double signal（无法解析为 0.0）
     */
    public static ReadableSignal<Double> toDoubleSignal(ReadableSignal<Object> source) {
        return Computed.create(() -> Double.valueOf(toDouble(source.get())));
    }

    /**
     * 把 Object 安全转为 {@code double}。
     *
     * <p>{@link Number} 直接取 {@code doubleValue()}；其余经 {@link String#valueOf(Object)}
     * 再 {@link Double#parseDouble(String)}，失败返回 {@code 0.0}（语义：缺失 / 非法 → 0，
     * slider 不崩、文本框由 DraftBuffer 校验报错）。</p>
     *
     * @param v 原始值
     * @return double 值，无法解析返回 0.0
     */
    public static double toDouble(Object v) {
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * 格式化数字读数：整数值去 {@code .0}（如 {@code 5.0} → {@code "5"}），浮点保留原值。
     *
     * <p>用于 slider 读数文本与 NUMBER 文本输入显示，复刻原
     * {@code NumberFieldRenderer.formatReadout} 语义。</p>
     *
     * @param v 当前值
     * @return 读数字符串（整数去 {@code .0}）
     */
    public static String formatReadout(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return Long.toString((long) v);
        }
        return Double.toString(v);
    }
}