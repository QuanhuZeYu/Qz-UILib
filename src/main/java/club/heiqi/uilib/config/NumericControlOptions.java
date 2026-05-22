package club.heiqi.uilib.config;

/**
 * 数值类型属性使用滑块/输入框时的细化配置。
 *
 * <p>本类只承载作者声明的偏好，不做任何运行时回退判断；运行时由
 * {@code NumericPropertyBinding} 综合属性的实际上下界、当前
 * {@link NumericControlMode} 与 {@link #getMaxSliderRange()} 阈值决定最终展示形态。</p>
 *
 * <p>实例为不可变值对象，使用链式 {@code with*} 方法派生新实例。</p>
 */
public final class NumericControlOptions {

    /** 滑块默认允许的最大数值跨度，超过则自动降级为输入框。 */
    public static final double DEFAULT_MAX_SLIDER_RANGE = 100.0D;

    private final NumericControlMode mode;
    private final double maxSliderRange;
    private final double sliderStep;
    private final String labelFormat;

    private NumericControlOptions(NumericControlMode mode, double maxSliderRange, double sliderStep,
            String labelFormat) {
        this.mode = mode;
        this.maxSliderRange = maxSliderRange;
        this.sliderStep = sliderStep;
        this.labelFormat = labelFormat;
    }

    /**
     * 创建仅指定模式的配置；其余参数使用默认值。
     *
     * @param mode 展示模式
     * @return 新配置
     */
    public static NumericControlOptions of(NumericControlMode mode) {
        return new NumericControlOptions(mode == null ? NumericControlMode.TEXT_INPUT : mode,
                DEFAULT_MAX_SLIDER_RANGE, 0.0D, null);
    }

    /**
     * 强制使用文本输入框。
     *
     * @return 新配置
     */
    public static NumericControlOptions textInput() {
        return of(NumericControlMode.TEXT_INPUT);
    }

    /**
     * 优先使用滑块，必要时降级为输入框。
     *
     * @return 新配置
     */
    public static NumericControlOptions slider() {
        return of(NumericControlMode.SLIDER);
    }

    /**
     * 优先使用滑块并显示数值标签，必要时降级为输入框。
     *
     * @return 新配置
     */
    public static NumericControlOptions sliderWithLabel() {
        return of(NumericControlMode.SLIDER_WITH_LABEL);
    }

    /**
     * 优先使用滑块并附带可编辑数值输入框，必要时降级为单独输入框。
     *
     * @return 新配置
     */
    public static NumericControlOptions sliderWithInput() {
        return of(NumericControlMode.SLIDER_WITH_INPUT);
    }

    /**
     * 替换最大可滑动范围阈值；超出则降级为输入框。
     *
     * @param maxSliderRange 最大允许范围；非正值视为不限制
     * @return 新配置
     */
    public NumericControlOptions withMaxSliderRange(double maxSliderRange) {
        double resolved = maxSliderRange <= 0.0D ? Double.POSITIVE_INFINITY : maxSliderRange;
        return new NumericControlOptions(mode, resolved, sliderStep, labelFormat);
    }

    /**
     * 设置滑块步长；为 0 表示由控件按属性类型推断。
     *
     * @param sliderStep 步长
     * @return 新配置
     */
    public NumericControlOptions withSliderStep(double sliderStep) {
        return new NumericControlOptions(mode, maxSliderRange, Math.max(0.0D, sliderStep), labelFormat);
    }

    /**
     * 设置数值标签使用的 {@link String#format} 模板。
     *
     * @param labelFormat 模板；为 null 时使用控件默认格式
     * @return 新配置
     */
    public NumericControlOptions withLabelFormat(String labelFormat) {
        return new NumericControlOptions(mode, maxSliderRange, sliderStep, labelFormat);
    }

    public NumericControlMode getMode() {
        return mode;
    }

    public double getMaxSliderRange() {
        return maxSliderRange;
    }

    public double getSliderStep() {
        return sliderStep;
    }

    public String getLabelFormat() {
        return labelFormat;
    }
}
