package club.heiqi.config.schema;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link FieldSpec.Builder} 的 widget 声明便捷方法测试：
 * {@code .slider()} / {@code .slider(step)} / {@code .input()} 及默认 null 行为。
 */
public class FieldSpecWidgetTest {

    /** 不调用任何 widget 方法时，widget 为 null（默认 input）。 */
    @Test
    public void defaultWidgetIsNull() {
        ConfigSchema s = ConfigSchema.builder("t")
                .section("a")
                    .number("k").defaultValue(1.0).build()
                .endSection()
                .build();
        FieldSpec f = s.field("a.k");
        assertNull("未声明 widget 时为 null", f.widget());
    }

    /** .slider() 声明连续 slider（step=0）。 */
    @Test
    public void sliderNoArgSetsContinuousSlider() {
        ConfigSchema s = ConfigSchema.builder("t")
                .section("a")
                    .number("k").defaultValue(1.0).range(0, 10).slider().build()
                .endSection()
                .build();
        FieldSpec f = s.field("a.k");
        assertTrue("widget 是 SliderSpec", f.widget() instanceof SliderSpec);
        SliderSpec spec = (SliderSpec) f.widget();
        assertEquals("连续 slider step=0", 0.0, spec.step(), 0.0);
    }

    /** .slider(0.1) 声明量化步进 slider。 */
    @Test
    public void sliderWithStepSetsQuantizedSlider() {
        ConfigSchema s = ConfigSchema.builder("t")
                .section("a")
                    .number("k").defaultValue(0.5).range(0, 1).slider(0.1).build()
                .endSection()
                .build();
        FieldSpec f = s.field("a.k");
        assertTrue("widget 是 SliderSpec", f.widget() instanceof SliderSpec);
        assertEquals("step 透传", 0.1, ((SliderSpec) f.widget()).step(), 0.0);
    }

    /** .input() 显式声明 input widget。 */
    @Test
    public void inputSetsInputSpec() {
        ConfigSchema s = ConfigSchema.builder("t")
                .section("a")
                    .number("k").defaultValue(1.0).range(0, 10).input().build()
                .endSection()
                .build();
        FieldSpec f = s.field("a.k");
        assertTrue("widget 是 InputSpec", f.widget() instanceof InputSpec);
        assertSame("input 单例", InputSpec.INSTANCE, f.widget());
    }

    /** SliderSpec 负 step 抛 IllegalArgumentException。 */
    @Test
    public void sliderNegativeStepThrows() {
        try {
            new SliderSpec(-0.1);
            fail("负 step 应抛异常");
        } catch (IllegalArgumentException expected) {
            // 预期
        }
    }

    /** SliderSpec.continuous() 返回 step=0 实例。 */
    @Test
    public void continuousFactoryReturnsZeroStep() {
        SliderSpec s = SliderSpec.continuous();
        assertEquals(0.0, s.step(), 0.0);
    }
}
