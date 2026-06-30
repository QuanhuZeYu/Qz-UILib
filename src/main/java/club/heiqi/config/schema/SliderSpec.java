package club.heiqi.config.schema;

import com.github.bsideup.jabel.Desugar;

/**
 * slider widget 配置。{@code step} 表示 slider 离散量化步进，
 * {@code step=0} 表示连续不量化（由 slider 自身决定精度），{@code step>0} 表示量化步进。
 *
 * <p>通过 {@link #continuous()} 获取连续 slider（step=0），
 * 或 {@code new SliderSpec(0.1)} 指定步进。</p>
 */
@Desugar
public record SliderSpec(double step) implements WidgetSpec {
    /**
     * 紧凑构造器：校验 step 非负。
     *
     * @throws IllegalArgumentException step 为负
     */
    public SliderSpec {
        if (step < 0) {
            throw new IllegalArgumentException("step 不能为负: " + step);
        }
    }

    /**
     * 连续 slider（step=0，不量化）。
     *
     * @return 连续 SliderSpec
     */
    public static SliderSpec continuous() {
        return new SliderSpec(0);
    }
}
