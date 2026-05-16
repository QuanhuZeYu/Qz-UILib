package club.heiqi.uilib.font.layout;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.page.GlyphRuntimeTables;
import club.heiqi.uilib.font.util.CodepointTextCache;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontMatcher;
import club.heiqi.uilib.ui.text.TextContentMode;

/**
 * 文本布局与测量服务。
 */
public class TextLayoutService {

    private static final FontRenderContext FONT_RENDER_CONTEXT = new FontRenderContext(new AffineTransform(), true, true);

    private final FontMatcher fontMatcher;
    private final GlyphPageManager glyphPageManager;
    private final DerivedFontCache derivedFontCache;
    private final AtomicLong widthCacheHitCount = new AtomicLong(0L);
    private final AtomicLong widthCacheMissCount = new AtomicLong(0L);
    private volatile int runtimeVersion;

    /**
     * 创建文本布局服务。
     *
     * @param fontMatcher 字体匹配器
     * @param glyphPageManager 字符页管理器
     */
    public TextLayoutService(FontMatcher fontMatcher, GlyphPageManager glyphPageManager,
            DerivedFontCache derivedFontCache) {
        this.fontMatcher = fontMatcher;
        this.glyphPageManager = glyphPageManager;
        this.derivedFontCache = derivedFontCache;
    }

    /**
     * 设置当前运行时版本。
     *
     * @param runtimeVersion 运行时版本
     */
    public void setRuntimeVersion(int runtimeVersion) {
        this.runtimeVersion = runtimeVersion;
    }

    /**
     * 解析文本为带样式的片段序列。
     *
     * @param text 文本
     * @param baseColor 默认颜色
     * @return 文本片段列表
     */
    public List<TextSegment> parseSegments(String text, int baseColor) {
        return parseSegments(text, baseColor, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 解析文本为带样式的片段序列。
     *
     * @param text 文本
     * @param baseColor 默认颜色
     * @param textContentMode 文本内容解析模式
     * @return 文本片段列表
     */
    public List<TextSegment> parseSegments(String text, int baseColor, TextContentMode textContentMode) {
        List<TextSegment> segments = new ArrayList<TextSegment>();
        if (text == null || text.isEmpty()) {
            return segments;
        }

        TextContentMode resolvedMode = resolveTextContentMode(textContentMode);
        if (resolvedMode == TextContentMode.UILIB_RAW) {
            TextStyle style = new TextStyle();
            style.resetAll(baseColor);
            segments.add(new TextSegment(text, style));
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
        return getStringWidth(text, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 计算指定解析模式下的字符串显示宽度。
     *
     * @param text 文本
     * @param textContentMode 文本内容解析模式
     * @return 宽度
     */
    public int getStringWidth(String text, TextContentMode textContentMode) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        double width = 0.0D;
        for (TextSegment segment : parseSegments(text, 0xFFFFFFFF, textContentMode)) {
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
        return trimStringToWidth(text, targetWidth, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 按宽度裁剪指定解析模式下的字符串。
     *
     * @param text 原始文本
     * @param targetWidth 目标宽度
     * @param textContentMode 文本内容解析模式
     * @return 裁剪结果
     */
    public String trimStringToWidth(String text, int targetWidth, TextContentMode textContentMode) {
        if (text == null || text.isEmpty() || targetWidth <= 0) {
            return "";
        }

        if (resolveTextContentMode(textContentMode) == TextContentMode.UILIB_RAW) {
            return trimRawStringToWidth(text, targetWidth);
        }

        StringBuilder builder = new StringBuilder();
        TextStyle currentStyle = new TextStyle();
        currentStyle.resetAll(0xFFFFFFFF);
        double width = 0.0D;
        boolean lineHasVisibleContent = false;

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
     * 按宽度裁剪字符串，可选从尾部保留可见内容。
     *
     * @param text 原始文本
     * @param targetWidth 目标宽度
     * @param reverse 是否从尾部保留
     * @return 裁剪结果
     */
    public String trimStringToWidth(String text, int targetWidth, boolean reverse) {
        return trimStringToWidth(text, targetWidth, reverse, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 按宽度裁剪字符串，可选从尾部保留可见内容。
     *
     * @param text 原始文本
     * @param targetWidth 目标宽度
     * @param reverse 是否从尾部保留
     * @param textContentMode 文本内容解析模式
     * @return 裁剪结果
     */
    public String trimStringToWidth(String text, int targetWidth, boolean reverse, TextContentMode textContentMode) {
        if (!reverse) {
            return trimStringToWidth(text, targetWidth, textContentMode);
        }
        if (text == null || text.isEmpty() || targetWidth <= 0) {
            return "";
        }

        if (resolveTextContentMode(textContentMode) == TextContentMode.UILIB_RAW) {
            return trimRawStringToWidthFromTail(text, targetWidth);
        }

        StringBuilder visibleBuilder = new StringBuilder();
        double width = 0.0D;
        int startIndex = text.length();

        for (int index = text.length(); index > 0;) {
            int codepoint = text.codePointBefore(index);
            int codepointLength = Character.charCount(codepoint);
            int codepointStart = index - codepointLength;
            if (codepointLength == 1 && codepointStart > 0 && text.charAt(codepointStart - 1) == '§') {
                index = codepointStart - 1;
                continue;
            }

            TextStyle style = resolveStyleAt(text, codepointStart, 0xFFFFFFFF);
            double charWidth = measureCodepointWidth(codepoint, style.getFontType());
            if (width + charWidth > targetWidth) {
                break;
            }
            width += charWidth;
            visibleBuilder.insert(0, text.substring(codepointStart, index));
            startIndex = codepointStart;
            index = codepointStart;
        }

        if (visibleBuilder.length() == 0) {
            return "";
        }

        TextStyle prefixStyle = resolveStyleAt(text, startIndex, 0xFFFFFFFF);
        String suffix = text.substring(startIndex);
        return prefixStyle.toFormattingCodes(0xFFFFFFFF) + stripLeadingFormatCodes(suffix);
    }

    /**
     * 按宽度插入换行符。
     *
     * @param text 文本
     * @param wrapWidth 换行宽度
     * @return 包含换行符的新文本
     */
    public String wrapFormattedStringToWidth(String text, int wrapWidth) {
        return wrapFormattedStringToWidth(text, wrapWidth, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 按宽度插入换行符。
     *
     * @param text 文本
     * @param wrapWidth 换行宽度
     * @param textContentMode 文本内容解析模式
     * @return 包含换行符的新文本
     */
    public String wrapFormattedStringToWidth(String text, int wrapWidth, TextContentMode textContentMode) {
        if (text == null || text.isEmpty() || wrapWidth <= 0) {
            return "";
        }

        if (resolveTextContentMode(textContentMode) == TextContentMode.UILIB_RAW) {
            return wrapRawStringToWidth(text, wrapWidth);
        }

        StringBuilder builder = new StringBuilder();
        TextStyle currentStyle = new TextStyle();
        currentStyle.resetAll(0xFFFFFFFF);
        double width = 0.0D;
        boolean lineHasVisibleContent = false;

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

            if (codepoint == '\r' || codepoint == '\n') {
                i += Character.charCount(codepoint);
                if (codepoint == '\r' && i < text.length() && text.charAt(i) == '\n') {
                    i++;
                }
                builder.append('\n');
                if (i < text.length()) {
                    builder.append(currentStyle.toFormattingCodes(0xFFFFFFFF));
                }
                width = 0.0D;
                lineHasVisibleContent = false;
                continue;
            }

            double charWidth = measureCodepointWidth(codepoint, currentStyle.getFontType());
            if (width + charWidth > wrapWidth && lineHasVisibleContent) {
                builder.append('\n');
                builder.append(currentStyle.toFormattingCodes(0xFFFFFFFF));
                width = 0.0D;
                lineHasVisibleContent = false;
            }
            builder.appendCodePoint(codepoint);
            width += charWidth;
            lineHasVisibleContent = true;
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
        return listFormattedStringToWidth(text, wrapWidth, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 将文本按宽度拆分为多行。
     *
     * @param text 文本
     * @param wrapWidth 最大宽度
     * @param textContentMode 文本内容解析模式
     * @return 行列表
     */
    public List<String> listFormattedStringToWidth(String text, int wrapWidth, TextContentMode textContentMode) {
        String wrapped = wrapFormattedStringToWidth(text, wrapWidth, textContentMode);
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
        return splitStringWidth(text, wrapWidth, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 计算指定解析模式下的多行文本高度。
     *
     * @param text 文本
     * @param wrapWidth 最大宽度
     * @param textContentMode 文本内容解析模式
     * @return 多行文本高度
     */
    public int splitStringWidth(String text, int wrapWidth, TextContentMode textContentMode) {
        List<String> lines = listFormattedStringToWidth(text, wrapWidth, textContentMode);
        if (lines.isEmpty()) {
            return 0;
        }
        return getLineHeight() * lines.size();
    }

    private TextStyle resolveStyleAt(String text, int endExclusive, int baseColor) {
        TextStyle style = new TextStyle();
        style.resetAll(baseColor);
        for (int index = 0; index < endExclusive;) {
            int codepoint = text.codePointAt(index);
            if (codepoint == '§' && index < endExclusive - 1) {
                index += Character.charCount(codepoint);
                char formatCode = text.charAt(index);
                style.applyFormat(Character.toLowerCase(formatCode), baseColor);
                index++;
                continue;
            }
            index += Character.charCount(codepoint);
        }
        return style;
    }

    private String stripLeadingFormatCodes(String text) {
        int index = 0;
        while (index < text.length() - 1 && text.charAt(index) == '§') {
            index += 2;
        }
        return text.substring(index);
    }

    /**
     * 为未来渲染层提供标准文本片段入口。
     *
     * @param text 原始文本
     * @param baseColor 默认颜色
     * @return 文本片段列表
     */
    public List<TextSegment> layoutSegments(String text, int baseColor) {
        return layoutSegments(text, baseColor, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 为未来渲染层提供标准文本片段入口。
     *
     * @param text 原始文本
     * @param baseColor 默认颜色
     * @param textContentMode 文本内容解析模式
     * @return 文本片段列表
     */
    public List<TextSegment> layoutSegments(String text, int baseColor, TextContentMode textContentMode) {
        return parseSegments(text, baseColor, textContentMode);
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

        GlyphRuntimeTables tables = glyphPageManager.getRuntimeTables();
        if (tables == null || !GlyphRuntimeTables.isValidCodepoint(codepoint)) {
            widthCacheMissCount.incrementAndGet();
            return measureAwtWidth(codepoint, fontType);
        }

        float[] widthCache = tables.widthArray(fontType);
        float cachedWidth = widthCache[codepoint];
        if (!Float.isNaN(cachedWidth)) {
            widthCacheHitCount.incrementAndGet();
            return cachedWidth;
        }
        widthCacheMissCount.incrementAndGet();

        float measuredWidth = (float) measureAwtWidth(codepoint, fontType);
        widthCache[codepoint] = measuredWidth;
        return measuredWidth;
    }

    /**
     * 获取当前布局层的基准行高。
     *
     * @return 行高
     */
    public int getLineHeight() {
        double lineSpacingRatio = Math.max(FontConfig.lineSpacing, -0.9D);
        double rawLineHeight = Math.max(FontConfig.charSize * (1.0D + lineSpacingRatio), 1.0D);
        return (int) Math.ceil(rawLineHeight);
    }

    private double measureAwtWidth(int codepoint, FontType fontType) {
        int glyphSize = Math.max(8, (int) Math.ceil(FontConfig.awtCharSize));
        int fontIndex = fontMatcher.matchFontIndex(runtimeVersion, codepoint, fontType);
        if (fontIndex < 0) {
            return FontConfig.spaceWidth;
        }

        Font font = derivedFontCache.getDerivedFont(fontIndex, fontType, glyphSize);
        if (font == null) {
            return FontConfig.spaceWidth;
        }

        GlyphMetrics glyphMetrics;
        Rectangle2D visualBounds;
        LineMetrics lineMetrics;
        String text = CodepointTextCache.getText(codepoint);

        while (true) {
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
        GlyphRuntimeTables tables = glyphPageManager.getRuntimeTables();
        if (tables != null) {
            tables.clearWidthCache();
        }
        widthCacheHitCount.set(0L);
        widthCacheMissCount.set(0L);
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

    private TextContentMode resolveTextContentMode(TextContentMode textContentMode) {
        return textContentMode == null ? TextContentMode.MINECRAFT_FORMATTED : textContentMode;
    }

    private String trimRawStringToWidth(String text, int targetWidth) {
        StringBuilder builder = new StringBuilder();
        TextStyle style = new TextStyle();
        style.resetAll(0xFFFFFFFF);
        double width = 0.0D;
        for (int i = 0; i < text.length();) {
            int codepoint = text.codePointAt(i);
            double charWidth = getCodepointWidth(codepoint, style);
            if (width + charWidth > targetWidth) {
                break;
            }
            width += charWidth;
            builder.appendCodePoint(codepoint);
            i += Character.charCount(codepoint);
        }
        return builder.toString();
    }

    private String trimRawStringToWidthFromTail(String text, int targetWidth) {
        TextStyle style = new TextStyle();
        style.resetAll(0xFFFFFFFF);
        StringBuilder builder = new StringBuilder();
        double width = 0.0D;
        for (int index = text.length(); index > 0;) {
            int codepoint = text.codePointBefore(index);
            int codepointLength = Character.charCount(codepoint);
            int codepointStart = index - codepointLength;
            double charWidth = getCodepointWidth(codepoint, style);
            if (width + charWidth > targetWidth) {
                break;
            }
            width += charWidth;
            builder.insert(0, text.substring(codepointStart, index));
            index = codepointStart;
        }
        return builder.toString();
    }

    private String wrapRawStringToWidth(String text, int wrapWidth) {
        StringBuilder builder = new StringBuilder();
        TextStyle style = new TextStyle();
        style.resetAll(0xFFFFFFFF);
        double width = 0.0D;
        boolean lineHasVisibleContent = false;
        for (int i = 0; i < text.length();) {
            int codepoint = text.codePointAt(i);
            if (codepoint == '\r' || codepoint == '\n') {
                i += Character.charCount(codepoint);
                if (codepoint == '\r' && i < text.length() && text.charAt(i) == '\n') {
                    i++;
                }
                builder.append('\n');
                width = 0.0D;
                lineHasVisibleContent = false;
                continue;
            }

            double charWidth = getCodepointWidth(codepoint, style);
            if (width + charWidth > wrapWidth && lineHasVisibleContent) {
                builder.append('\n');
                width = 0.0D;
                lineHasVisibleContent = false;
            }
            builder.appendCodePoint(codepoint);
            width += charWidth;
            lineHasVisibleContent = true;
            i += Character.charCount(codepoint);
        }
        return builder.toString();
    }
}
