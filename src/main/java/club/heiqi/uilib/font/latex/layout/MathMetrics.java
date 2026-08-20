package club.heiqi.uilib.font.latex.layout;

/**
 * 数学布局度量注入接口：布局层不直接依赖字体引擎，度量经此接口进入（纯 JVM 可测）。
 *
 * <p>实现（M3）由 TextLayoutService 适配（advance 复用 measureCodepointWidth、
 * ascent/descent 复用内部度量路径）。</p>
 */
public interface MathMetrics {

    /**
     * 文本推进宽度（逐码点求和；空格等零宽符按现有分类语义）。
     *
     * @param text   显示文本（可多个码点）
     * @param sizePx 像素字号
     * @return 推进宽度
     */
    float advance(String text, float sizePx);

    /**
     * 指定字号的字体 ascent（基线上方高度）。
     */
    float ascent(float sizePx);

    /**
     * 指定字号的字体 descent（基线下方深度）。
     */
    float descent(float sizePx);
}
