package club.heiqi.uilib.font;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;

import club.heiqi.uilib.font.util.FontCatalog;

/** 单个 generation 在 glyph worker 启动前冻结的行度量。 */
public final class FontRuntimeMetrics {

    private static final FontRenderContext FONT_RENDER_CONTEXT =
            new FontRenderContext(new AffineTransform(), true, true);
    private static final String METRICS_SAMPLE = "Ag";

    private final float ascentNormal;
    private final float descentNormal;
    private final float leadingNormal;
    private final float ascentBold;
    private final float descentBold;
    private final float leadingBold;

    private FontRuntimeMetrics(float ascentNormal, float descentNormal, float leadingNormal, float ascentBold,
            float descentBold, float leadingBold) {
        this.ascentNormal = ascentNormal;
        this.descentNormal = descentNormal;
        this.leadingNormal = leadingNormal;
        this.ascentBold = ascentBold;
        this.descentBold = descentBold;
        this.leadingBold = leadingBold;
    }

    /**
     * 从已准备 catalog 预计算稳定行度量。
     *
     * @param settings generation 设置
     * @param catalogSnapshot generation 字体目录
     * @return 不可变度量快照
     */
    public static FontRuntimeMetrics prepare(FontRuntimeSettings settings, FontCatalog.Snapshot catalogSnapshot) {
        if (settings == null) {
            throw new IllegalArgumentException("settings 不得为 null");
        }
        Font baseFont = catalogSnapshot == null ? null : catalogSnapshot.getFont(0);
        if (baseFont == null) {
            baseFont = new Font("Dialog", Font.PLAIN, settings.getGlyphSize());
        }
        float size = (float) settings.getGlyphSize();
        LineMetrics normal = baseFont.deriveFont(Font.PLAIN, size).getLineMetrics(METRICS_SAMPLE,
                FONT_RENDER_CONTEXT);
        LineMetrics bold = baseFont.deriveFont(Font.BOLD, size).getLineMetrics(METRICS_SAMPLE,
                FONT_RENDER_CONTEXT);
        float[] normalizedNormal = normalize(normal, (float) settings.getAwtCharSize());
        float[] normalizedBold = normalize(bold, (float) settings.getAwtCharSize());
        return new FontRuntimeMetrics(normalizedNormal[0], normalizedNormal[1], normalizedNormal[2],
                normalizedBold[0], normalizedBold[1], normalizedBold[2]);
    }

    public float getAscent(FontType fontType) {
        return fontType == FontType.BOLD ? ascentBold : ascentNormal;
    }

    public float getDescent(FontType fontType) {
        return fontType == FontType.BOLD ? descentBold : descentNormal;
    }

    public float getLeading(FontType fontType) {
        return fontType == FontType.BOLD ? leadingBold : leadingNormal;
    }

    private static float[] normalize(LineMetrics metrics, float targetHeight) {
        float total = metrics.getAscent() + metrics.getDescent() + metrics.getLeading();
        if (total <= 0.0F) {
            return new float[]{targetHeight, 0.0F, 0.0F};
        }
        float scale = targetHeight / total;
        return new float[]{metrics.getAscent() * scale, metrics.getDescent() * scale,
                metrics.getLeading() * scale};
    }
}
