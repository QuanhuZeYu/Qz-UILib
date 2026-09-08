package club.heiqi.uilib.font.glyph;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;

import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import club.heiqi.uilib.font.FontRuntimeDiagnostics;
import club.heiqi.uilib.font.FontRuntimeSettings;
import club.heiqi.uilib.font.latex.layout.MathGlyphMetrics;
import club.heiqi.uilib.font.latex.layout.MathGlyphRef;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.util.CodepointTextCache;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontMatcher;

/**
 * 真实字符图像生成器。
 */
public class GlyphGenerator {

    private final FontMatcher fontMatcher;

    /**
     * 创建字符生成器。
     *
     * @param fontMatcher      字体匹配器
     * @param derivedFontCache 派生字体缓存
     */
    public GlyphGenerator(FontMatcher fontMatcher, DerivedFontCache derivedFontCache) {
        this.fontMatcher = fontMatcher;
    }

    /**
     * 生成指定字符的图像与度量信息。
     *
     * @param task 生成任务
     * @return 生成结果，失败时返回 null
     */
    public GlyphGenerationResult generate(GlyphGenerationTask task) {
        GlyphRequestToken token = task.getToken();
        if (token == null) {
            throw new IllegalStateException("GlyphGenerator 只接受已领取 token 的 worker task");
        }
        if (token.getKind() == GlyphRequestToken.Kind.MATH_GLYPH) {
            return generateMathGlyph(task);
        }
        int fontIndex = fontMatcher.matchFontIndex(task.getRuntimeVersion(), task.getCodepoint(), task.getFontType());
        if (fontIndex < 0) {
            return null;
        }

        String text = CodepointTextCache.getText(task.getCodepoint());
        Font font = fontMatcher.getDerivedFont(task.getRuntimeVersion(), fontIndex, task.getFontType(),
                task.getGlyphSize());
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

        int inkPadding = effectiveInkPadding();
        ProbeImage probeImage = renderProbeImage(font, text, visualBounds, advance, lineMetrics, inkPadding);
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
            int atlasBaselineX = Math.max(0, inkPadding - bearingX);
            int atlasBaselineY = Math.max(0, inkPadding - bearingY);
            int inkLeftInSlot = atlasBaselineX + bearingX;
            int inkTopInSlot = atlasBaselineY + bearingY;
            int slotWidth = Math.max(1, inkLeftInSlot + inkWidth + inkPadding);
            int slotHeight = Math.max(1, inkTopInSlot + inkHeight + inkPadding);

            image = renderSlotImage(font, text, slotWidth, slotHeight, atlasBaselineX, atlasBaselineY);
            boolean coloredGlyph = containsColoredPixels(image);

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
        return new GlyphGenerationResult(token, image, glyphInfo);
    }

    private GlyphGenerationResult generateMathGlyph(GlyphGenerationTask task) {
        GlyphRequestToken token = task.getToken();
        int size = token.getRasterSize();
        if (task.getGlyphSize() != size) {
            throw new IllegalArgumentException("数学任务字号必须与 token 一致");
        }
        FontCatalog.Snapshot snapshot = fontMatcher.getCatalogSnapshot(token.getGeneration());
        FontRuntimeSettings settings = fontMatcher.getRuntimeSettings(token.getGeneration());
        if (snapshot == null || settings == null || snapshot.getMathFontSupport() == null) {
            return null;
        }
        MathGlyphRef ref = token.getMathGlyphRef();
        MathGlyphMetrics metrics = snapshot.getMathFontSupport().measure(ref, size);
        if (metrics == null) {
            return null;
        }
        GlyphVector vector = null;
        Shape shape = null;
        if (ref.getKind() == MathGlyphRef.Kind.FONT_GLYPH) {
            Font font = snapshot.getMathPhysicalFont(ref, size);
            if (font == null || ref.getGlyphId() >= font.getNumGlyphs()) {
                return null;
            }
            BufferedImage contextImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = contextImage.createGraphics();
            try {
                applyRenderingHints(graphics);
                vector = font.createGlyphVector(graphics.getFontRenderContext(), new int[] { ref.getGlyphId() });
            } finally {
                graphics.dispose();
            }
            if (vector.getGlyphCode(0) != ref.getGlyphId()) {
                throw new IllegalArgumentException("物理字体拒绝请求的 glyph-id");
            }
        } else {
            shape = ProceduralAccentShape.create(ref.getProceduralAccent(), size);
        }
        MathGlyphRasterPlan plan = new MathGlyphRasterPlan(metrics, settings.getTextureSize(), settings.getGlyphInkPadding());
        return rasterMathTile(token, metrics, vector, shape, plan);
    }

    // 每次只分配有限核心 probe；padding 是全局路径邻域，仅供采样，adapter 只输出 core quad。
    GlyphGenerationResult rasterMathTile(GlyphRequestToken token, MathGlyphMetrics metrics,
            GlyphVector vector, Shape shape, MathGlyphRasterPlan plan) {
        int tile = token.getTileIndex();
        int left = plan.getLeft(tile);
        int top = plan.getTop();
        BufferedImage probe = new BufferedImage(plan.getWidth(tile), plan.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = probe.createGraphics();
        try {
            applyRenderingHints(graphics);
            graphics.setColor(Color.WHITE);
            graphics.translate(-left, -top);
            if (vector != null) {
                graphics.drawGlyphVector(vector, 0, 0);
            } else {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                graphics.fill(shape);
            }
        } finally {
            graphics.dispose();
        }
        PixelBounds bounds = scanActualPixelBounds(probe);
        int size = token.getRasterSize();
        float ascent = Math.max(0, -metrics.getInkTop());
        float descent = Math.max(0, metrics.getInkBottom());
        if (bounds.empty) {
            GlyphInfo info = new GlyphInfo(token.getMathGlyphRef(), size, size, metrics.getAdvance(),
                    ascent, descent, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false);
            return GlyphGenerationResult.forMathGlyph(token, null, info);
        }
        int padding = plan.getPadding();
        // 数学核心范围固定为 plan 的完整整数矩形，含透明像素；采样时相邻 quad 严格相接。
        int width = plan.getWidth(tile);
        int height = plan.getHeight();
        int bearingX = left;
        int bearingY = top;
        BufferedImage image = new BufferedImage(width + 2 * padding, height + 2 * padding,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D slotGraphics = image.createGraphics();
        try {
            applyRenderingHints(slotGraphics);
            slotGraphics.setColor(Color.WHITE);
            slotGraphics.translate(padding - bearingX, padding - bearingY);
            if (vector != null) {
                slotGraphics.drawGlyphVector(vector, 0, 0);
            } else {
                slotGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                slotGraphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                slotGraphics.fill(shape);
            }
        } finally {
            slotGraphics.dispose();
        }
        // 用已扫描 core 的原始覆盖率覆盖核心，避免第二次路径 clip 改变接缝像素。
        int[] pixels = probe.getRGB(0, 0, width, height, null, 0, width);
        image.setRGB(padding, padding, width, height, pixels, 0, width);
        GlyphInfo info = new GlyphInfo(token.getMathGlyphRef(), size, size, metrics.getAdvance(),
                ascent, descent, 0, width, height, image.getWidth(), image.getHeight(),
                padding - bearingX, padding - bearingY, 0, bearingX, bearingY, true, containsColoredPixels(image));
        return GlyphGenerationResult.forMathGlyph(token, image, info);
    }

    /** 有效 ink 留白：0..32 截断（超出 mipmap 隔离余量上限无意义）。 */
    private static int effectiveInkPadding() {
        return Math.max(0, Math.min(32, FontConfig.glyphInkPadding));
    }

    private ProbeImage renderProbeImage(Font font, String text, Rectangle2D visualBounds, float advance,
                                        LineMetrics lineMetrics, int inkPadding) {
        int baselineX = inkPadding + Math.max(0, (int) Math.ceil(-visualBounds.getX()));
        int baselineY = inkPadding + Math.max(0, (int) Math.ceil(-visualBounds.getY()));
        int rightExtent = Math.max(1, (int) Math.ceil(Math.max(visualBounds.getMaxX(), advance)) + inkPadding);
        int bottomExtent = Math.max(1, (int) Math.ceil(Math.max(visualBounds.getMaxY(), lineMetrics.getDescent()))
                + inkPadding);
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
