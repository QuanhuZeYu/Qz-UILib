package club.heiqi.uilib.config;

/**
 * 数值类型属性的展示模式。
 *
 * <p>作者通过 {@link ForgeConfigTemplateScreen.Spec#setNumericControlOptions(String, String,
 * NumericControlOptions)} 显式指定，未声明时默认沿用文本输入框。</p>
 */
public enum NumericControlMode {

    /** 始终使用文本输入框。无论是否有上下界都不会切换为滑块。 */
    TEXT_INPUT,

    /** 使用滑块。若属性缺少上下界或范围超出阈值则自动降级为 {@link #TEXT_INPUT}。 */
    SLIDER,

    /** 使用滑块并在右侧显示当前值标签。同样在缺少边界或超出阈值时降级为 {@link #TEXT_INPUT}。 */
    SLIDER_WITH_LABEL
}
