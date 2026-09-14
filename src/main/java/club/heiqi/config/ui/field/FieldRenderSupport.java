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
 *
 * <h3>编辑期原文机制（所有文本型字段共用一套）</h3>
 * <p>{@code SceneTextInput} 是受控控件：显示文本只从外部 value 派生、自己不缓存原文，而「值 ↔ 文本」
 * 映射普遍有损——{@code "0."} 与 {@code 0} 同值、{@code "40E6FF"} 与 {@code "#40E6FF"} 同值。
 * 故文本型字段渲染器统一自持编辑期原文（字段层一个 {@code Signal<String> editText}），
 * 显示文本 = {@code isUnfinishedText(原文, draft 值, 编解码) ? 原文 : 编解码.format(draft 值)}，
 * 失焦时原文归位到规范写法。{@link ValueTextCodec} 是这套机制的编解码契约：NUMBER 用
 * {@link #isUnfinishedNumberText} / {@link #numberTextOf}（内部走私有 NUMBER 实现），
 * 十六进制颜色字段由 {@code config.ui.field} 里它自己的渲染器提供实现——
 * <b>新增文本型字段只提供编解码，不另造第二套编辑期原文机制</b>。</p>
 */
public final class FieldRenderSupport {

    /** 工具类，禁止实例化 */
    private FieldRenderSupport() {
    }

    /**
     * 「值 ↔ 文本」的编解码契约：{@link #format(Object)} 给出值的规范写法，
     * {@link #parse(String)} 把用户原文读回值（读不出返回 {@code null}，不做近似兜底）。
     *
     * <p>它是 {@link #isUnfinishedText} 的输入，不是控件/外观抽象：实现必须是纯函数、无状态，
     * 且不得依赖 scene 或主题（编解码只描述文本形态，与观感无关）。</p>
     */
    public interface ValueTextCodec {

        /**
         * 值 → 规范显示文本。
         *
         * @param value draft 当前值，可为 null
         * @return 显示文本，恒非 null
         */
        String format(Object value);

        /**
         * 原文 → 值。
         *
         * @param text 输入框原文，可为 null
         * @return 解析结果；非法 / 读不出返回 {@code null}（调用方据此保留原文并交给草稿校验报错）
         */
        Object parse(String text);
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
        return Computed.create(() -> numberTextOf(source.get()));
    }

    /**
     * draft 值 → NUMBER 字段显示文本（{@link #toNumberStringSignal} 的同步同口径版本）：
     * {@code null} → {@code ""}；{@link Number} → {@link #formatReadout(double)}（整数值去 {@code .0}）；
     * 其余 {@link String#valueOf(Object)}（draft 里 parse 失败的原文原样透出）。
     *
     * <p><b>为何要有同步版本</b>：{@code Computed.create(Supplier)} 的初值是 {@code null}，
     * 首次 flush 前读不到派生值。数值输入框要在构建期就把「值的规范写法」注入控件 value
     * 与编辑期原文信号（读控件 value 的同步路径不止 flush 一处），故把这段纯转换提成静态方法。</p>
     *
     * @param draftValue draft 当前值，可为 null
     * @return 显示文本（恒非 null）
     */
    public static String numberTextOf(Object draftValue) {
        return NUMBER_TEXT.format(draftValue);
    }

    /**
     * NUMBER 文本形态的编解码（{@link #numberTextOf} 与 {@link #isUnfinishedNumberText} 的共用实现）。
     */
    private static final ValueTextCodec NUMBER_TEXT = new ValueTextCodec() {
        @Override
        public String format(Object value) {
            if (value == null) {
                return "";
            }
            if (value instanceof Number) {
                return formatReadout(((Number) value).doubleValue());
            }
            return String.valueOf(value);
        }

        @Override
        public Object parse(String text) {
            return parseNumberOrNull(text);
        }
    };

    /**
     * 判断输入框原文是否仍是 draft 当前值的「未完成写法」——原文能解析成数、且解析结果正是该值，
     * 但写法尚未规范化（如 {@code 0.} / {@code 5.} / {@code .5} / {@code 1e2} / {@code 1.50}）。
     *
     * <p><b>用途（数值输入框编辑期原文判据）</b>：值 ↔ 文本的映射是有损的——{@code "0."} 与
     * {@code "0"} 同值、{@code ".5"} 与 {@code "0.5"} 同值，而受控文本控件只从外部 value 派生
     * 显示文本、自己不缓存原文。故输入框必须在编辑期自己持有原文，并只在「原文仍代表当前值」
     * 时显示原文；一旦值被外部改写（重置 / 撤销 / 其它控件写同字段）就不再命中，回落规范写法。</p>
     *
     * <p>原文解析失败不属于未完成写法：那种原文会作为 String 落进 draft（{@code "abc"}、
     * {@code "-"}），此时原文与显示文本逐字相等，本方法按「String 型 draft 值」分支判为命中，
     * 由 draft 校验按既有规则报错。</p>
     *
     * @param text       输入框原文（可为 null）
     * @param draftValue draft 当前值，可为 null
     * @return true 表示原文仍是该值的未完成写法（应继续显示原文）
     */
    public static boolean isUnfinishedNumberText(String text, Object draftValue) {
        return isUnfinishedText(text, draftValue, NUMBER_TEXT);
    }

    /**
     * 判断输入框原文是否仍是 draft 当前值的「未完成写法」——原文经编解码读回的<b>就是</b>当前值，
     * 只是写法尚未规范化（{@code "0."} 与 {@code 0}、{@code "40E6FF"} 与 {@code "#40E6FF"}）。
     *
     * <p><b>用途（文本型字段编辑期原文判据）</b>：值 ↔ 文本有损，受控文本控件只从外部 value 派生
     * 显示文本、自己不缓存原文。故字段渲染器在编辑期自持原文，并只在「原文仍代表当前值」时显示
     * 原文；一旦值被外部改写（重置 / 撤销 / 其它控件写同字段），判据不再命中，显示回落规范写法。</p>
     *
     * <p>原文读不出值不属于未完成写法：那种原文会作为 String 落进 draft，此时原文与显示文本逐字
     * 相等，本方法按「String 型 draft 值」分支判为命中，由 draft 校验按既有规则报错。</p>
     *
     * @param text       输入框原文（可为 null）
     * @param draftValue draft 当前值，可为 null
     * @param codec      该字段的「值 ↔ 文本」编解码
     * @return true 表示原文仍是该值的未完成写法（应继续显示原文）
     */
    public static boolean isUnfinishedText(String text, Object draftValue, ValueTextCodec codec) {
        if (draftValue instanceof Number) {
            Object parsed = codec.parse(text);
            return parsed instanceof Number
                    && Double.compare(((Number) parsed).doubleValue(), ((Number) draftValue).doubleValue()) == 0;
        }
        return text == null ? draftValue == null : text.equals(codec.format(draftValue));
    }

    /**
     * 严格解析数值文本：解析失败 / null / 空串返回 {@code null}（调用方据此区分「不是数」）。
     *
     * <p>与 {@link #toDouble} 的区别：后者把解析失败静默降级为 {@code 0.0}（slider 场景需要
     * 一个可用数值），本方法保留「解析不出」这一信息，供编辑期原文判据使用。</p>
     *
     * @param text 待解析文本（可为 null）
     * @return 解析结果，或 null 表示解析失败
     */
    private static Double parseNumberOrNull(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(Double.parseDouble(text));
        } catch (NumberFormatException e) {
            return null;
        }
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