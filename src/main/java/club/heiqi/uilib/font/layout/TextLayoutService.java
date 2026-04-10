package club.heiqi.uilib.font.layout;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.glyph.GlyphGenerationPriority;
import club.heiqi.uilib.font.glyph.GlyphGenerationTask;
import club.heiqi.uilib.font.glyph.GlyphInfo;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.util.FontMatcher;

/**
 * 文本布局与测量服务。
 */
public class TextLayoutService {

    private static final FontRenderContext FONT_RENDER_CONTEXT = new FontRenderContext(new AffineTransform(), true, true);

    private final FontMatcher fontMatcher;
    private final GlyphPageManager glyphPageManager;
    private final Map<String, Double> widthCache = new ConcurrentHashMap<String, Double>();
    private final AtomicLong widthCacheHitCount = new AtomicLong(0L);
    private final AtomicLong widthCacheMissCount = new AtomicLong(0L);

    /**
     * 创建文本布局服务。
     *
     * @param fontMatcher 字体匹配器
     * @param glyphPageManager 字符页管理器
     */
    public TextLayoutService(FontMatcher fontMatcher, GlyphPageManager glyphPageManager) {
        this.fontMatcher = fontMatcher;
        this.glyphPageManager = glyphPageManager;
    }

    /**
     * 解析文本为带样式的片段序列。
     *
     * @param text 文本
     * @param baseColor 默认颜色
     * @return 文本片段列表
     */
    public List<TextSegment> parseSegments(String text, int baseColor) {
        List<TextSegment> segments = new ArrayList<TextSegment>();
        if (text == null || text.isEmpty()) {
            return segments;
        }

        TextStyle currentStyle = new TextStyle();
        currentStyle.resetAll(baseColor);
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < text.length();) {
            int codepoint = text.codePointAt(i);
            if (codepoint == '§' && i < text.length() - 1) {
                if (builder.length() > 0) {
                    segments.add(new TextSegment(builder.toString(), currentStyle.copy()));
                    builder.setLength(0);
                }

                i += Character.charCount(codepoint);
                char formatCode = Character.toLowerCase(text.charAt(i));
                currentStyle.applyFormat(formatCode, baseColor);
                i++;
                continue;
            }

            builder.appendCodePoint(codepoint);
            i += Character.charCount(codepoint);
        }

        if (builder.length() > 0) {
            segments.add(new TextSegment(builder.toString(), currentStyle.copy()));
        }
        return segments;
    }

    /**
     * 计算字符串显示宽度。
     *
     * @param text 文本
     * @return 宽度
     */
    public int getStringWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        double width = 0.0D;
        for (TextSegment segment : parseSegments(text, 0xFFFFFFFF)) {
            width += getSegmentWidth(segment);
        }
        return (int) Math.ceil(width);
    }

    /**
     * 按宽度裁剪字符串。
     *
     * @param text 原始文本
     * @param targetWidth 目标宽度
     * @return 裁剪结果
     */
    public String trimStringToWidth(String text, int targetWidth) {
        if (text == null || text.isEmpty() || targetWidth <= 0) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        TextStyle currentStyle = new TextStyle();
        currentStyle.resetAll(0xFFFFFFFF);
        double width = 0.0D;

        for (int i = 0; i < text.length();) {
            int codepoint = text.codePointAt(i);
            if (codepoint == '§' && i < text.length() - 1) {
                builder.appendCodePoint(codepoint);
                i += Character.charCount(codepoint);
                char formatCode = text.charAt(i);
                builder.append(formatCode);
                currentStyle.applyFormat(Character.toLowerCase(formatCode), 0xFFFFFFFF);
                i++;
                continue;
            }

            double charWidth = measureCodepointWidth(codepoint, currentStyle.getFontType());
            if (width + charWidth > targetWidth) {
                break;
            }
            width += charWidth;
            builder.appendCodePoint(codepoint);
            i += Character.charCount(codepoint);
        }
        return builder.toString();
    }

    /**
     * 按宽度插入换行符。
     *
     * @param text 文本
     * @param wrapWidth 换行宽度
     * @return 包含换行符的新文本
     */
    public String wrapFormattedStringToWidth(String text, int wrapWidth) {
        if (text == null || text.isEmpty() || wrapWidth <= 0) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        TextStyle currentStyle = new TextStyle();
        currentStyle.resetAll(0xFFFFFFFF);
        double width = 0.0D;

        for (int i = 0; i < text.length();) {
            int codepoint = text.codePointAt(i);
            if (codepoint == '§' && i < text.length() - 1) {
                builder.appendCodePoint(codepoint);
                i += Character.charCount(codepoint);
                char formatCode = text.charAt(i);
                builder.append(formatCode);
                currentStyle.applyFormat(Character.toLowerCase(formatCode), 0xFFFFFFFF);
                i++;
                continue;
            }

            double charWidth = measureCodepointWidth(codepoint, currentStyle.getFontType());
            if (width + charWidth > wrapWidth && builder.length() > 0) {
                builder.append('\n');
                width = 0.0D;
            }
            builder.appendCodePoint(codepoint);
            width += charWidth;
            i += Character.charCount(codepoint);
        }
        return builder.toString();
    }

    /**
     * 将文本按宽度拆分为多行。
     *
     * @param text 文本
     * @param wrapWidth 最大宽度
     * @return 行列表
     */
    public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
        String wrapped = wrapFormattedStringToWidth(text, wrapWidth);
        if (wrapped.isEmpty()) {
            return new ArrayList<String>();
        }
        return Arrays.asList(wrapped.split("\n"));
    }

    /**
     * 计算多行文本高度。
     *
     * @param text 文本
     * @param wrapWidth 最大宽度
     * @return 多行文本高度
     */
    public int splitStringWidth(String text, int wrapWidth) {
        List<String> lines = listFormattedStringToWidth(text, wrapWidth);
        if (lines.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(FontConfig.charSize * lines.size());
    }

    /**
     * 为未来渲染层提供标准文本片段入口。
     *
     * @param text 原始文本
     * @param baseColor 默认颜色
     * @return 文本片段列表
     */
    public List<TextSegment> layoutSegments(String text, int baseColor) {
        return parseSegments(text, baseColor);
    }

    /**
     * 计算单个文本片段宽度。
     *
     * @param segment 文本片段
     * @return 片段宽度
     */
    public double getSegmentWidth(TextSegment segment) {
        double width = 0.0D;
        String text = segment.getText();
        FontType fontType = segment.getStyle().getFontType();
        for (int i = 0; i < text.length();) {
            int codepoint = text.codePointAt(i);
            width += getCodepointWidth(codepoint, segment.getStyle());
            i += Character.charCount(codepoint);
        }
        return width;
    }

    /**
     * 获取指定字符在当前样式下的推进宽度。
     *
     * @param codepoint 字符码点
     * @param style 文本样式
     * @return 推进宽度
     */
    public double getCodepointWidth(int codepoint, TextStyle style) {
        return measureCodepointWidth(codepoint, style.getFontType());
    }

    private double measureCodepointWidth(int codepoint, FontType fontType) {
        if (codepoint == ' ') {
            return FontConfig.spaceWidth;
        }

        String cacheKey = buildWidthCacheKey(codepoint, fontType);
        Double cachedWidth = widthCache.get(cacheKey);
        if (cachedWidth != null) {
            widthCacheHitCount.incrementAndGet();
            return cachedWidth.doubleValue();
        }
        widthCacheMissCount.incrementAndGet();

        GlyphInfo info = glyphPageManager.getGlyphInfo(codepoint, fontType);
        if (info == null) {
            return measureAwtWidth(codepoint, fontType);
        }

        if (info.getWidth() <= 0) {
            return measureAwtWidth(codepoint, fontType);
        }
        double measuredWidth = ((info.getAdvance() / info.getWidth()) * FontConfig.charSize) + FontConfig.characterSpacing;
        widthCache.put(cacheKey, Double.valueOf(measuredWidth));
        return measuredWidth;
    }

    /**
     * 获取当前布局层的基准行高。
     *
     * @return 行高
     */
    public int getLineHeight() {
        return (int) Math.ceil(Math.max(FontConfig.charSize, 8.0D));
    }

    private double measureAwtWidth(int codepoint, FontType fontType) {
        Font font = fontMatcher.match(codepoint, fontType);
        if (font == null) {
            return FontConfig.spaceWidth;
        }

        int glyphSize = Math.max(8, (int) Math.ceil(FontConfig.awtCharSize));
        int style = Font.PLAIN;
        if (fontType == FontType.BOLD) {
            style = Font.BOLD;
        }

        font = font.deriveFont(style, (float) Math.max(glyphSize * FontConfig.fontScale, 6.0D));

        GlyphMetrics glyphMetrics;
        Rectangle2D visualBounds;
        LineMetrics lineMetrics;

        while (true) {
            String text = new String(Character.toChars(codepoint));
            GlyphVector glyphVector = font.createGlyphVector(FONT_RENDER_CONTEXT, text);
            visualBounds = glyphVector.getVisualBounds();
            glyphMetrics = glyphVector.getGlyphMetrics(0);
            lineMetrics = font.getLineMetrics(text, FONT_RENDER_CONTEXT);

            float baselineY = (float) (-lineMetrics.getDescent() + glyphSize);
            double top = baselineY + visualBounds.getY();
            double bottom = baselineY + visualBounds.getMaxY();
            boolean retry = visualBounds.getWidth() > glyphSize
                    || visualBounds.getHeight() > glyphSize
                    || top < 0.0D
                    || bottom > glyphSize;
            if (!retry) {
                break;
            }

            float nextSize = Math.max(6.0F, font.getSize2D() - 0.5F);
            if (nextSize >= font.getSize2D() - 0.001F) {
                break;
            }
            font = font.deriveFont(nextSize);
        }

        double advance = glyphMetrics.getAdvance();
        if (visualBounds.getWidth() > glyphSize / 2.0D) {
            advance = advance - visualBounds.getX() + 2.0D;
        }
        if (advance <= 0.0D) {
            return FontConfig.spaceWidth;
        }
        return ((advance / glyphSize) * FontConfig.charSize) + FontConfig.characterSpacing;
    }

    /**
     * 清空宽度缓存。
     */
    public void clearCache() {
        widthCache.clear();
    }

    /**
     * 获取宽度缓存命中次数。
     *
     * @return 命中次数
     */
    public long getWidthCacheHitCount() {
        return widthCacheHitCount.get();
    }

    /**
     * 获取宽度缓存未命中次数。
     *
     * @return 未命中次数
     */
    public long getWidthCacheMissCount() {
        return widthCacheMissCount.get();
    }

    private String buildWidthCacheKey(int codepoint, FontType fontType) {
        return codepoint + ":" + fontType.name() + ":" + FontConfig.charSize + ":" + FontConfig.characterSpacing;
    }
}
