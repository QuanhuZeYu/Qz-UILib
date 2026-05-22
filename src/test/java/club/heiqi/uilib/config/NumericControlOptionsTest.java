package club.heiqi.uilib.config;

import org.junit.Assert;
import org.junit.Test;

/**
 * 验证 {@link NumericControlOptions} 值对象的派生与默认值行为。
 */
public class NumericControlOptionsTest {

    /**
     * 默认阈值应为 100，并兜底到 TEXT_INPUT。
     */
    @Test
    public void shouldExposeDefaultThresholdAndFallbackMode() {
        NumericControlOptions options = NumericControlOptions.of(null);
        Assert.assertEquals(NumericControlMode.TEXT_INPUT, options.getMode());
        Assert.assertEquals(NumericControlOptions.DEFAULT_MAX_SLIDER_RANGE, options.getMaxSliderRange(), 0.0D);
        Assert.assertEquals(0.0D, options.getSliderStep(), 0.0D);
        Assert.assertNull(options.getLabelFormat());
    }

    /**
     * 静态工厂应返回作者声明的模式。
     */
    @Test
    public void shouldExposeRequestedModeForFactoryMethods() {
        Assert.assertEquals(NumericControlMode.TEXT_INPUT, NumericControlOptions.textInput().getMode());
        Assert.assertEquals(NumericControlMode.SLIDER, NumericControlOptions.slider().getMode());
        Assert.assertEquals(NumericControlMode.SLIDER_WITH_LABEL,
                NumericControlOptions.sliderWithLabel().getMode());
        Assert.assertEquals(NumericControlMode.SLIDER_WITH_INPUT,
                NumericControlOptions.sliderWithInput().getMode());
    }

    /**
     * 链式 with 方法应返回新实例，并保留其他字段。
     */
    @Test
    public void shouldDeriveImmutableCopiesViaWithMethods() {
        NumericControlOptions base = NumericControlOptions.slider();
        NumericControlOptions withRange = base.withMaxSliderRange(250.0D);
        NumericControlOptions withStep = withRange.withSliderStep(0.5D);
        NumericControlOptions withFormat = withStep.withLabelFormat("%d 项");

        Assert.assertNotSame(base, withRange);
        Assert.assertNotSame(withRange, withStep);
        Assert.assertNotSame(withStep, withFormat);

        Assert.assertEquals(250.0D, withFormat.getMaxSliderRange(), 0.0D);
        Assert.assertEquals(0.5D, withFormat.getSliderStep(), 0.0D);
        Assert.assertEquals("%d 项", withFormat.getLabelFormat());
        Assert.assertEquals(NumericControlMode.SLIDER, withFormat.getMode());

        Assert.assertEquals(NumericControlOptions.DEFAULT_MAX_SLIDER_RANGE, base.getMaxSliderRange(), 0.0D);
        Assert.assertEquals(0.0D, base.getSliderStep(), 0.0D);
    }

    /**
     * 非正阈值应被解释为不限制（POSITIVE_INFINITY）。
     */
    @Test
    public void shouldTreatNonPositiveThresholdAsUnbounded() {
        NumericControlOptions options = NumericControlOptions.slider().withMaxSliderRange(0.0D);
        Assert.assertEquals(Double.POSITIVE_INFINITY, options.getMaxSliderRange(), 0.0D);

        NumericControlOptions negative = NumericControlOptions.slider().withMaxSliderRange(-10.0D);
        Assert.assertEquals(Double.POSITIVE_INFINITY, negative.getMaxSliderRange(), 0.0D);
    }

    /**
     * 负步长应被规整为 0。
     */
    @Test
    public void shouldClampNegativeStepToZero() {
        NumericControlOptions options = NumericControlOptions.slider().withSliderStep(-1.0D);
        Assert.assertEquals(0.0D, options.getSliderStep(), 0.0D);
    }
}
