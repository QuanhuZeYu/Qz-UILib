package club.heiqi.uilib.font.glyph;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import club.heiqi.uilib.font.FontRuntimeDiagnostics;
import club.heiqi.uilib.font.util.CodepointTextCache;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontMatcher;

/**
 * 真实字符图像生成器。
 */
public class GlyphGenerator {

    private static final int INK_PADDING = 6;

    private final FontMatcher fontMatcher;
    private final DerivedFontCache derivedFontCache;

    /**
     * 创建字符生成器。
     *
     * @param fontMatcher 字体匹配器
     * @param derivedFontCache 派生字体缓存
     */
    public GlyphGenerator(FontMatcher fontMatcher, DerivedFontCache derivedFontCache) {
        this.fontMatcher = fontMatcher;
        this.derivedFontCache = derivedFontCache;
    }

    /**
     * 生成指定字符的图像与度量信息。
     *
     * @param task 生成任务
     * @return 生成结果，失败时返回 null
     */
    public GlyphGenerationResult generate(GlyphGenerationTask task) {
        int fontIndex = fontMatcher.matchFontIndex(task.getRuntimeVersion(), task.getCodepoint(), task.getFontType());
        if (fontIndex < 0) {
            return null;
        }

        String text = CodepointTextCache.getText(task.getCodepoint());
        Font font = derivedFontCache.getDerivedFont(fontIndex, task.getFontType(), task.getGlyphSize());
        if (font == null) {
            return null;
        }

        BufferedImage contextImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D contextGraphics = contextImage.createGraphics();
        applyRenderingHints(contextGraphics);
        contextGraphics.setFont(font);
        FontRenderContext context = contextGraphics.getFontRenderContext();

        GlyphVector glyphVector = font.createGlyphVector(context, text);
        Rectangle2D visualBounds = glyphVector.getVisualBounds();
        LineMetrics lineMetrics = font.getLineMetrics(text, context);
        TextLayout textLayout = new TextLayout(text, font, context);
        float advance = textLayout.getAdvance();
        int lineBaselineY = Math.max(0, Math.round(task.getGlyphSize() - lineMetrics.getDescent()));
        contextGraphics.dispose();

        ProbeImage probeImage = renderProbeImage(font, text, visualBounds, advance, lineMetrics);
        PixelBounds actualPixelBounds = scanActualPixelBounds(probeImage.image);
        BufferedImage image;
        GlyphInfo glyphInfo;
        if (actualPixelBounds.empty) {
            image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            glyphInfo = new GlyphInfo(
                    task.getCodepoint(),
                    task.getGlyphSize(),
                    task.getGlyphSize(),
                    advance,
                    0.0F,
                    0.0F,
                    0,
                    0,
                    0,
                    0,
                    lineBaselineY,
                    0,
                    0,
                    false,
                    false);
        } else {
            int bearingX = actualPixelBounds.minX - probeImage.baselineX;
            int bearingY = actualPixelBounds.minY - probeImage.baselineY;
            int inkWidth = actualPixelBounds.width();
            int inkHeight = actualPixelBounds.height();
            int atlasBaselineX = Math.max(0, INK_PADDING - bearingX);
            int atlasBaselineY = Math.max(0, INK_PADDING - bearingY);
            int slotWidth = Math.max(1, atlasBaselineX + inkWidth + INK_PADDING);
            int slotHeight = Math.max(1, atlasBaselineY + inkHeight + INK_PADDING);

            image = renderSlotImage(font, text, slotWidth, slotHeight, atlasBaselineX, atlasBaselineY);
            boolean coloredGlyph = containsColoredPixels(image);

            glyphInfo = new GlyphInfo(
                    task.getCodepoint(),
                    task.getGlyphSize(),
                    task.getGlyphSize(),
                    advance,
                    (float) inkWidth,
                    (float) inkHeight,
                    slotWidth,
                    slotHeight,
                    atlasBaselineX,
                    atlasBaselineY,
                    lineBaselineY,
                    bearingX,
                    bearingY,
                    true,
                    coloredGlyph);
        }
        FontRuntimeDiagnostics.logGeneratedGlyph(task, image, glyphInfo);
        return new GlyphGenerationResult(task.getRuntimeVersion(), task.getGenerationId(), task.getCodepoint(),
                task.getFontType(), image, glyphInfo);
    }

    private ProbeImage renderProbeImage(Font font, String text, Rectangle2D visualBounds, float advance,
            LineMetrics lineMetrics) {
        int baselineX = INK_PADDING + Math.max(0, (int) Math.ceil(-visualBounds.getX()));
        int baselineY = INK_PADDING + Math.max(0, (int) Math.ceil(-visualBounds.getY()));
        int rightExtent = Math.max(1, (int) Math.ceil(Math.max(visualBounds.getMaxX(), advance)) + INK_PADDING);
        int bottomExtent = Math.max(1, (int) Math.ceil(Math.max(visualBounds.getMaxY(), lineMetrics.getDescent()))
                + INK_PADDING);
        int width = Math.max(1, baselineX + rightExtent);
        int height = Math.max(1, baselineY + bottomExtent);
        BufferedImage image = renderTextImage(font, text, width, height, baselineX, baselineY);
        return new ProbeImage(image, baselineX, baselineY);
    }

    private BufferedImage renderSlotImage(Font font, String text, int slotWidth, int slotHeight, int atlasBaselineX,
            int atlasBaselineY) {
        return renderTextImage(font, text, slotWidth, slotHeight, atlasBaselineX, atlasBaselineY);
    }

    private BufferedImage renderTextImage(Font font, String text, int width, int height, int baselineX, int baselineY) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        applyRenderingHints(graphics);
        graphics.setFont(font);
        graphics.setColor(Color.WHITE);
        graphics.drawString(text, baselineX, baselineY);
        graphics.dispose();
        return image;
    }

    private void applyRenderingHints(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
    }

    private PixelBounds scanActualPixelBounds(BufferedImage image) {
        PixelBounds bounds = new PixelBounds();
        int width = image.getWidth();
        int height = image.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = (image.getRGB(x, y) >> 24) & 0xFF;
                if (alpha != 0) {
                    bounds.include(x, y);
                }
            }
        }
        return bounds;
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

    private static final class ProbeImage {

        private final BufferedImage image;
        private final int baselineX;
        private final int baselineY;

        private ProbeImage(BufferedImage image, int baselineX, int baselineY) {
            this.image = image;
            this.baselineX = baselineX;
            this.baselineY = baselineY;
        }
    }

    private static final class PixelBounds {

        private boolean empty = true;
        private int minX;
        private int minY;
        private int maxX;
        private int maxY;

        private void include(int x, int y) {
            if (empty) {
                minX = x;
                minY = y;
                maxX = x + 1;
                maxY = y + 1;
                empty = false;
                return;
            }
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x + 1);
            maxY = Math.max(maxY, y + 1);
        }

        private int width() {
            return empty ? 0 : maxX - minX;
        }

        private int height() {
            return empty ? 0 : maxY - minY;
        }
    }
}
