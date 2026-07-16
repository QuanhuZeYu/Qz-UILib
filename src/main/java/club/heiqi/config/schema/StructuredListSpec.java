package club.heiqi.config.schema;

import com.github.bsideup.jabel.Desugar;

/**
 * 结构化列表字段的不可变 UI 元数据。
 *
 * @param viewportHeight 当前字段的列表视口首选高度（logical px）
 */
@Desugar
public record StructuredListSpec(int viewportHeight) implements WidgetSpec {
    /** 未显式声明时使用的结构化列表视口高度。 */
    public static final int DEFAULT_VIEWPORT_HEIGHT = 320;

    /** 校验视口高度。 */
    public StructuredListSpec {
        if (viewportHeight <= 0) {
            throw new IllegalArgumentException("StructuredListSpec.viewportHeight must be positive");
        }
    }
}
