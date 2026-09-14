package club.heiqi.config.schema;

import com.github.bsideup.jabel.Desugar;

/**
 * color widget 配置：NUMBER 字段以 {@code #RRGGBB} 文本输入框呈现。
 *
 * <p><b>值语义不变</b>：颜色仍是 {@link FieldType#NUMBER}（整数 {@code 0xRRGGBB}，值域
 * {@code [0, 0xFFFFFF]} 由 {@link FieldConstraints} 的 min/max 表达），因此校验、草稿、持久化、
 * schema 兼容判定全部沿用既有 NUMBER 通道，本 widget 只声明「用什么控件、按什么文本形态显示」。
 * 与 {@link SliderSpec}/{@link InputSpec} 同一层级：{@link WidgetSpec} 是不参与值语义的 UI 元数据。
 * 文本与值互转规则见 {@link HexColorCodec}，本类不持有规则。</p>
 *
 * <p><b>值域</b>：DSL 的 {@code color()}（{@link SectionSpec.Builder#color(String)} /
 * {@link FieldSpec.Builder#color()}）在未显式声明 range 时补齐 {@code [0, 0xFFFFFF]}；
 * 直接构造 {@link FieldSpec} 时由调用方自己声明 range。</p>
 *
 * <p>通过 {@link #INSTANCE} 获取，或 {@code new ColorSpec()} 构造。</p>
 */
@Desugar
public record ColorSpec() implements WidgetSpec {
    /** 单例实例，DSL {@code .color()} 默认使用此实例。 */
    public static final ColorSpec INSTANCE = new ColorSpec();

    /** 占位提示文本：既是规范显示形态，也是输入语法示例（语言中立，不入语言表）。 */
    public static final String PLACEHOLDER = "#RRGGBB";
}
