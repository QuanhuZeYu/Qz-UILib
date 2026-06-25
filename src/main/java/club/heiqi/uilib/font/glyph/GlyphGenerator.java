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

    private static final int INK_PADDING = 8;

    /**
     * ink 边缘羽化半径（atlas 像素）。在 ink 子区外烘焙半透明白色过渡带，
     * 让 mipmap 降采样时 UV 外扩采到的 padding 像素有真实 alpha 渐变，避免硬裁边。
     */
    private static final int INK_FEATHER_RADIUS = 1;

    private final FontMatcher fontMatcher;
    private final DerivedFontCache derivedFontCache;

    /**
     * 创建字符生成器。
     *
     * @param fontMatcher      字体匹配器
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
        float ascent = lineMetrics.getAscent();
        float descent = lineMetrics.getDescent();
        float leading = lineMetrics.getLeading();
        TextLayout textLayout = new TextLayout(text, font, context);
        float advance = textLayout.getAdvance();
        int lineBaselineY = Math.max(0, Math.round(task.getGlyphSize() - descent));
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
                    ascent,
                    descent,
                    leading,
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
            int inkLeftInSlot = atlasBaselineX + bearingX;
            int inkTopInSlot = atlasBaselineY + bearingY;
            int slotWidth = Math.max(1, inkLeftInSlot + inkWidth + INK_PADDING);
            int slotHeight = Math.max(1, inkTopInSlot + inkHeight + INK_PADDING);

            image = renderSlotImage(font, text, slotWidth, slotHeight, atlasBaselineX, atlasBaselineY);
            boolean coloredGlyph = containsColoredPixels(image);
            // 彩色字形（emoji）走 shader 彩色路径直接用纹理 RGB，烘焙白色羽化会使边缘 RGB 向白色偏移，故跳过
            if (!coloredGlyph) {
                // 在 ink 子区外烘焙 alpha 过渡带，为 UV 外扩采样提供真实渐变
                bakeInkEdgeFeather(image, inkLeftInSlot, inkTopInSlot, inkWidth, inkHeight);
            }

            glyphInfo = new GlyphInfo(
                    task.getCodepoint(),
                    task.getGlyphSize(),
                    task.getGlyphSize(),
                    advance,
                    ascent,
                    descent,
                    leading,
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

    /**
     * 在 ink 子区外、距离 ink 边界 ≤ {@link #INK_FEATHER_RADIUS} 像素的 padding 区烘焙半透明白色过渡带。
     *
     * <p>羽化像素 RGB 固定为白色，alpha 按到 ink 子区的切比雪夫距离线性衰减；只写入 ink 子区外且
     * 原本比羽化值更透明的像素，不破坏 ink 子区内已有像素。目的：让 mipmap 高 mip 级 texel 跨
     * slot 混合时，ink 边缘有真实 alpha 渐变而非纯透明硬墙。</p>
     *
     * <p>当前 {@code INK_FEATHER_RADIUS=1}，distance=1 时 alpha=127（单层羽化单值），并非真正多级渐变；
     * 仅在 ink 子区外紧邻 1 像素 padding 圈写入。后续若调大半径才会出现多级衰减。</p>
     *
     * @param image          slot 图像
     * @param inkLeftInSlot  ink 子区在 slot 内的左边界 X
     * @param inkTopInSlot   ink 子区在 slot 内的上边界 Y
     * @param inkWidth       ink 子区宽度
     * @param inkHeight      ink 子区高度
     */
    void bakeInkEdgeFeather(BufferedImage image, int inkLeftInSlot, int inkTopInSlot, int inkWidth,
                                    int inkHeight) {
        if (inkWidth <= 0 || inkHeight <= 0) {
            return;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int inkLeft = inkLeftInSlot;
        int inkTop = inkTopInSlot;
        int inkRight = inkLeft + inkWidth;
        int inkBottom = inkTop + inkHeight;
        int featherRadius = INK_FEATHER_RADIUS;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // 跳过 ink 子区内像素，保留 AWT 已绘制的字形
                if (x >= inkLeft && x < inkRight && y >= inkTop && y < inkBottom) {
                    continue;
                }
                // 计算到 ink 子区的切比雪夫距离（按像素网格）
                int dx = 0;
                if (x < inkLeft) {
                    dx = inkLeft - x;
                } else if (x >= inkRight) {
                    dx = x - (inkRight - 1);
                }
                int dy = 0;
                if (y < inkTop) {
                    dy = inkTop - y;
                } else if (y >= inkBottom) {
                    dy = y - (inkBottom - 1);
                }
                int distance = Math.max(dx, dy);
                if (distance > featherRadius) {
                    continue;
                }
                // 线性衰减：distance=1 → alpha≈128
                int alpha = (int) (255.0 * (1.0 - (double) distance / (double) (featherRadius + 1)));
                if (alpha <= 0) {
                    continue;
                }
                int existingPixel = image.getRGB(x, y);
                int existingAlpha = (existingPixel >> 24) & 0xFF;
                // 不覆盖更不透明的已有像素
                if (existingAlpha >= alpha) {
                    continue;
                }
                // 白色 RGB + 羽化 alpha
                image.setRGB(x, y, (alpha << 24) | 0x00FFFFFF);
            }
        }
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

    boolean containsColoredPixels(BufferedImage image) {
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
