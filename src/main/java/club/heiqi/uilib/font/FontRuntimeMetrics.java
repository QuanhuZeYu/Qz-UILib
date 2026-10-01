package club.heiqi.uilib.font;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

import club.heiqi.uilib.font.util.FontCatalog;

/** 单个 generation 在 glyph worker 启动前冻结的行度量。 */
public final class FontRuntimeMetrics {

    private static final FontRenderContext FONT_RENDER_CONTEXT =
            new FontRenderContext(new AffineTransform(), true, true);
    private static final String METRICS_SAMPLE = "Ag";

    /** 每字面一份行度量；索引 = {@code FontType.ordinal()}。 */
    private final float[] ascent;
    private final float[] descent;
    private final float[] leading;
    private final float[] xHeight;

    private FontRuntimeMetrics(float[] ascent, float[] descent, float[] leading, float[] xHeight) {
        this.ascent = ascent;
        this.descent = descent;
        this.leading = leading;
        this.xHeight = xHeight;
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
        float normalizedHeight = (float) settings.getGlyphGenerationSize();
        FontType[] types = FontType.values();
        float[] ascent = new float[types.length];
        float[] descent = new float[types.length];
        float[] leading = new float[types.length];
        float[] xHeight = new float[types.length];
        for (FontType fontType : types) {
            int awtStyle = awtStyle(fontType);
            LineMetrics metrics = baseFont.deriveFont(awtStyle, size).getLineMetrics(METRICS_SAMPLE,
                    FONT_RENDER_CONTEXT);
            float[] normalized = normalize(metrics, normalizedHeight);
            ascent[fontType.ordinal()] = normalized[0];
            descent[fontType.ordinal()] = normalized[1];
            leading[fontType.ordinal()] = normalized[2];
            xHeight[fontType.ordinal()] = measureXHeight(baseFont, awtStyle, settings);
        }
        return new FontRuntimeMetrics(ascent, descent, leading, xHeight);
    }

    /** 字面 → AWT style 位（粗体/斜体由 {@link FontType} 派生）。 */
    private static int awtStyle(FontType fontType) {
        int style = Font.PLAIN;
        if (fontType.isBold()) {
            style |= Font.BOLD;
        }
        if (fontType.isItalic()) {
            style |= Font.ITALIC;
        }
        return style;
    }

    /** 测量小写 x 的 ink 高度并归一化到 awtCharSize 坐标系（TeX x-height 参数）。 */
    private static float measureXHeight(Font baseFont, int style, FontRuntimeSettings settings) {
        float glyphSize = (float) settings.getGlyphSize();
        Font sized = baseFont.deriveFont(style, glyphSize);
        GlyphVector glyphVector = sized.createGlyphVector(FONT_RENDER_CONTEXT, "x");
        Rectangle2D bounds = glyphVector.getVisualBounds();
        double rawHeight = bounds.getHeight();
        if (rawHeight <= 0.0D) {
            // 字体无法给出 x 字形几何时回退 CM 比例（x-height ≈ 0.431em）
            return (float) (0.431D * settings.getGlyphGenerationSize());
        }
        return (float) (rawHeight / glyphSize * settings.getGlyphGenerationSize());
    }

    private static int faceIndex(FontType fontType) {
        return fontType == null ? FontType.NORMAL.ordinal() : fontType.ordinal();
    }

    public float getAscent(FontType fontType) {
        return ascent[faceIndex(fontType)];
    }

    public float getDescent(FontType fontType) {
        return descent[faceIndex(fontType)];
    }

    public float getLeading(FontType fontType) {
        return leading[faceIndex(fontType)];
    }

    public float getXHeight(FontType fontType) {
        return xHeight[faceIndex(fontType)];
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
