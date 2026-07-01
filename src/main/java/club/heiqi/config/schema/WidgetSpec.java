package club.heiqi.config.schema;

import com.github.bsideup.jabel.Desugar;

/**
 * NUMBER 字段的 widget 声明载体。
 *
 * <p>由 schema DSL 显式声明字段使用何种 widget：
 * {@link SliderSpec} 表示用 slider，{@link InputSpec} 表示用文本输入框。
 * 未声明（{@code null}）时 {@code NumberFieldRenderer} 默认走 input。</p>
 *
 * <p>实现类限定为 {@link SliderSpec} / {@link InputSpec}（record），
 * 渲染层用 {@code instanceof} 模式匹配分发。
 * 原计划用 Java 17 密封接口，但 Jabel desugar 不支持 sealed 关键字，
 * 故退为普通接口 + record 实现，语义等价。</p>
 */
@Desugar
public interface WidgetSpec {
}
