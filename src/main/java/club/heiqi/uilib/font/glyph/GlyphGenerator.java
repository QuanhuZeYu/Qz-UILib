package club.heiqi.uilib.font.glyph;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.util.FontMatcher;

/**
 * 真实字符图像生成器。
 */
public class GlyphGenerator {

    private final FontMatcher fontMatcher;

    /**
     * 创建字符生成器。
     *
     * @param fontMatcher 字体匹配器
     */
    public GlyphGenerator(FontMatcher fontMatcher) {
        this.fontMatcher = fontMatcher;
    }

    /**
     * 生成指定字符的图像与度量信息。
     *
     * @param task 生成任务
     * @return 生成结果，失败时返回 null
     */
    public GlyphGenerationResult generate(GlyphGenerationTask task) {
        Font baseFont = fontMatcher.match(task.getCodepoint(), task.getFontType());
        if (baseFont == null) {
            return null;
        }

        String text = new String(Character.toChars(task.getCodepoint()));
        Font font = deriveFont(baseFont, task.getFontType(), task.getGlyphSize());

        BufferedImage tempImage = new BufferedImage(task.getGlyphSize(), task.getGlyphSize(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D tempGraphics = tempImage.createGraphics();
        FontRenderContext context = tempGraphics.getFontRenderContext();

        Rectangle2D visualBounds;
        GlyphMetrics glyphMetrics;
        LineMetrics lineMetrics;
        boolean retry;

        do {
            GlyphVector glyphVector = font.createGlyphVector(context, text);
            visualBounds = glyphVector.getVisualBounds();
            glyphMetrics = glyphVector.getGlyphMetrics(0);
            lineMetrics = font.getLineMetrics(text, context);

            float baselineY = (float) (-lineMetrics.getDescent() + task.getGlyphSize());
            double top = baselineY + visualBounds.getY();
            double bottom = baselineY + visualBounds.getMaxY();
            retry = visualBounds.getWidth() > task.getGlyphSize()
                    || visualBounds.getHeight() > task.getGlyphSize()
                    || top < 0.0D
                    || bottom > task.getGlyphSize();
            if (retry) {
                font = font.deriveFont(Math.max(6.0F, font.getSize2D() - 0.5F));
            }
        } while (retry);

        BufferedImage image = new BufferedImage(task.getGlyphSize(), task.getGlyphSize(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
        graphics.setFont(font);
        graphics.setColor(Color.WHITE);

        float x = 0.0F;
        float advance = glyphMetrics.getAdvance();
        if (visualBounds.getWidth() > task.getGlyphSize() / 2.0D) {
            x = (float) (-visualBounds.getX() + 2.0D);
            advance = (float) (advance - visualBounds.getX() + 2.0D);
        }
        float y = (float) (-lineMetrics.getDescent() + task.getGlyphSize());
        graphics.drawString(text, x, y);
        graphics.dispose();
        tempGraphics.dispose();

        boolean coloredGlyph = containsColoredPixels(image);

        GlyphInfo glyphInfo = new GlyphInfo(
                task.getCodepoint(),
                task.getGlyphSize(),
                task.getGlyphSize(),
                advance,
                (float) visualBounds.getWidth(),
                (float) visualBounds.getHeight(),
                coloredGlyph);
        return new GlyphGenerationResult(task.getCodepoint(), task.getFontType(), image, glyphInfo);
    }

    private Font deriveFont(Font baseFont, FontType fontType, int glyphSize) {
        int style = Font.PLAIN;
        if (fontType == FontType.BOLD) {
            style = Font.BOLD;
        }
        return baseFont.deriveFont(style, (float) Math.max(glyphSize * FontConfig.fontScale, 6.0D));
    }

    private boolean containsColoredPixels(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getRGB(x, y);
                int alpha = (pixel >> 24) & 0xFF;
                if (alpha == 0) {
                    continue;
                }

                int red = (pixel >> 16) & 0xFF;
                int green = (pixel >> 8) & 0xFF;
                int blue = pixel & 0xFF;
                if (red != green || green != blue) {
                    return true;
                }
            }
        }
        return false;
    }
}
