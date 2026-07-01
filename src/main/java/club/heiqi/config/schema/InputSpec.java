package club.heiqi.config.schema;

import com.github.bsideup.jabel.Desugar;

/**
 * input widget 配置（文本输入框）。无参数，单例即可。
 *
 * <p>通过 {@link #INSTANCE} 获取，或 {@code new InputSpec()} 构造。</p>
 */
@Desugar
public record InputSpec() implements WidgetSpec {
    /** 单例实例，DSL {@code .input()} 默认使用此实例。 */
    public static final InputSpec INSTANCE = new InputSpec();
}
