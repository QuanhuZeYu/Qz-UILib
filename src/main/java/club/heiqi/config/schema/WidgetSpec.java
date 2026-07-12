package club.heiqi.config.schema;

import com.github.bsideup.jabel.Desugar;

/**
 * 配置 schema 的不可变 UI widget 元数据标记。
 *
 * <p>既有 {@link SliderSpec}/{@link InputSpec} 用于 NUMBER 字段；
 * {@link SearchPickerSpec} 可附着到递归 {@link ValueSpec}。实现不得依赖 Minecraft、GL 或 scene，
 * 元数据不属于持久化值语义。普通接口用于兼容 Jabel 不支持 sealed 的限制。</p>
 */
@Desugar
public interface WidgetSpec {
}
