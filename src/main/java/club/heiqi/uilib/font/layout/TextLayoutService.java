package club.heiqi.uilib.font.layout;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.page.GlyphRuntimeTables;
import club.heiqi.uilib.font.util.CodepointTextCache;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontMatcher;
import club.heiqi.uilib.ui.base.props.UiFontStyle;
import club.heiqi.uilib.ui.base.props.UiFontWeight;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureStyle;

/**
 * 文本布局与测量服务。
 */
public class TextLayoutService {

    private static final FontRenderContext FONT_RENDER_CONTEXT = new FontRenderContext(new AffineTransform(), true, true);

    private final FontMatcher fontMatcher;
    private final GlyphPageManager glyphPageManager;
    private final DerivedFontCache derivedFontCache;
    private final LongAdder widthCacheHitCount = new LongAdder();
    private final LongAdder widthCacheMissCount = new LongAdder();
    private volatile int runtimeVersion;

    /**
     * 创建文本布局服务。
     *
     * @param fontMatcher      字体匹配器
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
     * @param text      文本
     * @param baseColor 默认颜色
     * @return 文本片段列表
     */
    public List<TextSegment> parseSegments(String text, int baseColor) {
        return parseSegments(text, baseColor, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 解析文本为带样式的片段序列。
     *
     * @param text            文本
     * @param baseColor       默认颜色
     * @param textContentMode 文本内容解析模式
     * @return 文本片段列表
     */
    public List<TextSegment> parseSegments(String text, int baseColor, TextContentMode textContentMode) {
        return parseSegments(text, baseColor, textContentMode, null);
    }

    /**
     * 解析文本为带样式的片段序列，并叠加基础字体样式。
     *
     * @param text            文本
     * @param baseColor       默认颜色
     * @param textContentMode 文本内容解析模式
     * @param baseStyle       基础字体样式；为 null 时使用默认普通样式
     * @return 文本片段列表
     */
    public List<TextSegment> parseSegments(String text, int baseColor, TextContentMode textContentMode,
                                           TextStyle baseStyle) {
        List<TextSegment> segments = new ArrayList<TextSegment>();
        if (text == null || text.isEmpty()) {
            return segments;
        }

        TextContentMode resolvedMode = resolveTextContentMode(textContentMode);
        if (resolvedMode == TextContentMode.UILIB_RAW) {
            TextStyle style = createBaseStyle(baseColor, baseStyle);
            segments.add(new TextSegment(text, style));
            return segments;
        }

        TextStyle currentStyle = createBaseStyle(baseColor, baseStyle);
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < text.length(); ) {
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
     * @param text            文本
     * @param textContentMode 文本内容解析模式
     * @return 宽度
     */
    public int getStringWidth(String text, TextContentMode textContentMode) {
        return getStringWidth(text, textContentMode, UiFontWeight.NORMAL, UiFontStyle.NORMAL);
    }

    /**
     * 计算指定解析模式和基础字体样式下的字符串显示宽度。
     *
     * @param text            文本
     * @param textContentMode 文本内容解析模式
     * @param fontWeight      字体粗细
     * @param fontStyle       字体样式
     * @return 宽度
     */
    public int getStringWidth(String text, TextContentMode textContentMode, UiFontWeight fontWeight,
                              UiFontStyle fontStyle) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        double width = 0.0D;
        TextStyle baseStyle = createBaseStyle(0xFFFFFFFF, fontWeight, fontStyle);
        for (TextSegment segment : parseSegments(text, 0xFFFFFFFF, textContentMode, baseStyle)) {
            width += getSegmentWidth(segment);
        }
        return (int) Math.ceil(width);
    }

    /**
     * 计算字符串按码点边界切分的原始前缀宽度向量。
     *
     * <p>仅针对 {@link TextContentMode#UILIB_RAW} 模式（{@code §} 视为可见字面量），逐码点累加原始
     * advance，在每个码点边界取 {@code (int) Math.ceil(累加值)}。返回数组长度为码点数 + 1，元素 0 恒为 0，
     * 末元素与 {@link #getStringWidth(String, TextContentMode, UiFontWeight, UiFontStyle)} 在 {@code UILIB_RAW}
     * 下的整串结果一致；任意中间元素 {@code i} 与对“前 i 个码点子串”单独调用该方法的结果一致。</p>
     *
     * <p>该方法把旧控件每帧逐前缀 {@code substring} 的 O(N²) 测量替换为单趟 O(N) 累加，且保持每个边界值
     * 与逐次测量数值相同，是 {@code TextLayoutEngine} 前缀宽度的底层来源。</p>
     *
     * @param text       文本；为 {@code null} 或空串时返回 {@code {0}}
     * @param fontWeight 字体粗细
     * @param fontStyle  字体样式
     * @return 原始坐标系下的前缀宽度向量
     */
    public int[] prefixWidthsRaw(String text, UiFontWeight fontWeight, UiFontStyle fontStyle) {
        if (text == null || text.isEmpty()) {
            return new int[]{0};
        }
        int codePointCount = text.codePointCount(0, text.length());
        int[] widths = new int[codePointCount + 1];
        widths[0] = 0;
        TextStyle baseStyle = createBaseStyle(0xFFFFFFFF, fontWeight, fontStyle);
        double runningWidth = 0.0D;
        int currentOffset = 0;
        for (int index = 1; index <= codePointCount; index++) {
            int codepoint = text.codePointAt(currentOffset);
            runningWidth += getCodepointWidth(codepoint, baseStyle);
            widths[index] = (int) Math.ceil(runningWidth);
            currentOffset += Character.charCount(codepoint);
        }
        return widths;
    }

    /**
     * 计算指定语义化文本样式下的字符串 UI 像素宽度。
     *
     * @param text  文本
     * @param style 文本样式快照
     * @return UI 像素宽度
     */
    public int getStringWidth(String text, TextMeasureStyle style) {
        TextMeasureStyle resolvedStyle = resolveTextMeasureStyle(style);
        if (text == null || text.isEmpty()) {
            return 0;
        }

        double width = 0.0D;
        TextStyle baseStyle = createBaseStyle(0xFFFFFFFF, resolvedStyle.getFontWeight(), resolvedStyle.getFontStyle());
        for (TextSegment segment : parseSegments(text, 0xFFFFFFFF, resolvedStyle.getTextContentMode(), baseStyle)) {
            width += getSegmentWidth(segment, resolvedStyle.getFontSizePx());
        }
        return (int) Math.ceil(width);
    }

    /**
     * 按宽度裁剪字符串。
     *
     * @param text        原始文本
     * @param targetWidth 目标宽度
     * @return 裁剪结果
     */
    public String trimStringToWidth(String text, int targetWidth) {
        return trimStringToWidth(text, targetWidth, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 按宽度裁剪指定解析模式下的字符串。
     *
     * @param text            原始文本
     * @param targetWidth     目标宽度
     * @param textContentMode 文本内容解析模式
     * @return 裁剪结果
     */
    public String trimStringToWidth(String text, int targetWidth, TextContentMode textContentMode) {
        return trimStringToWidth(text, targetWidth, textContentMode, UiFontWeight.NORMAL, UiFontStyle.NORMAL);
    }

    /**
     * 按宽度裁剪指定解析模式和基础字体样式下的字符串。
     *
     * @param text            原始文本
     * @param targetWidth     目标宽度
     * @param textContentMode 文本内容解析模式
     * @param fontWeight      字体粗细
     * @param fontStyle       字体样式
     * @return 裁剪结果
     */
    public String trimStringToWidth(String text, int targetWidth, TextContentMode textContentMode,
                                    UiFontWeight fontWeight, UiFontStyle fontStyle) {
        if (text == null || text.isEmpty() || targetWidth <= 0) {
            return "";
        }

        if (resolveTextContentMode(textContentMode) == TextContentMode.UILIB_RAW) {
            return trimRawStringToWidth(text, targetWidth, createBaseStyle(0xFFFFFFFF, fontWeight, fontStyle));
        }

        StringBuilder builder = new StringBuilder();
        TextStyle currentStyle = createBaseStyle(0xFFFFFFFF, fontWeight, fontStyle);
        double width = 0.0D;

        for (int i = 0; i < text.length(); ) {
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
     * 按指定语义化文本样式和 UI 像素宽度裁剪字符串。
     *
     * @param text        原始文本
     * @param targetWidth 目标 UI 像素宽度
     * @param style       文本样式快照
     * @return 裁剪结果
     */
    public String trimStringToWidth(String text, int targetWidth, TextMeasureStyle style) {
        TextMeasureStyle resolvedStyle = resolveTextMeasureStyle(style);
        if (text == null || text.isEmpty() || targetWidth <= 0) {
            return "";
        }

        if (resolveTextContentMode(resolvedStyle.getTextContentMode()) == TextContentMode.UILIB_RAW) {
            return trimRawStringToWidth(text, targetWidth, createBaseStyle(0xFFFFFFFF,
                    resolvedStyle.getFontWeight(), resolvedStyle.getFontStyle()), resolvedStyle.getFontSizePx());
        }

        StringBuilder builder = new StringBuilder();
        TextStyle currentStyle = createBaseStyle(0xFFFFFFFF, resolvedStyle.getFontWeight(),
                resolvedStyle.getFontStyle());
        double width = 0.0D;

        for (int i = 0; i < text.length(); ) {
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

            double charWidth = measureCodepointWidth(codepoint, currentStyle.getFontType(),
                    resolvedStyle.getFontSizePx());
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
     * @param text        原始文本
     * @param targetWidth 目标宽度
     * @param reverse     是否从尾部保留
     * @return 裁剪结果
     */
    public String trimStringToWidth(String text, int targetWidth, boolean reverse) {
        return trimStringToWidth(text, targetWidth, reverse, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 按宽度裁剪字符串，可选从尾部保留可见内容。
     *
     * @param text            原始文本
     * @param targetWidth     目标宽度
     * @param reverse         是否从尾部保留
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

        for (int index = text.length(); index > 0; ) {
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
     * @param text      文本
     * @param wrapWidth 换行宽度
     * @return 包含换行符的新文本
     */
    public String wrapFormattedStringToWidth(String text, int wrapWidth) {
        return wrapFormattedStringToWidth(text, wrapWidth, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 按宽度插入换行符。
     *
     * @param text            文本
     * @param wrapWidth       换行宽度
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

        for (int i = 0; i < text.length(); ) {
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
     * @param text      文本
     * @param wrapWidth 最大宽度
     * @return 行列表
     */
    public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
        return listFormattedStringToWidth(text, wrapWidth, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 将文本按宽度拆分为多行。
     *
     * @param text            文本
     * @param wrapWidth       最大宽度
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
     * @param text      文本
     * @param wrapWidth 最大宽度
     * @return 多行文本高度
     */
    public int splitStringWidth(String text, int wrapWidth) {
        return splitStringWidth(text, wrapWidth, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 计算指定解析模式下的多行文本高度。
     *
     * @param text            文本
     * @param wrapWidth       最大宽度
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
        for (int index = 0; index < endExclusive; ) {
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
     * @param text      原始文本
     * @param baseColor 默认颜色
     * @return 文本片段列表
     */
    public List<TextSegment> layoutSegments(String text, int baseColor) {
        return layoutSegments(text, baseColor, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 为未来渲染层提供标准文本片段入口。
     *
     * @param text            原始文本
     * @param baseColor       默认颜色
     * @param textContentMode 文本内容解析模式
     * @return 文本片段列表
     */
    public List<TextSegment> layoutSegments(String text, int baseColor, TextContentMode textContentMode) {
        return layoutSegments(text, baseColor, textContentMode, UiFontWeight.NORMAL, UiFontStyle.NORMAL);
    }

    /**
     * 为未来渲染层提供标准文本片段入口，并叠加基础字体样式。
     *
     * @param text            原始文本
     * @param baseColor       默认颜色
     * @param textContentMode 文本内容解析模式
     * @param fontWeight      字体粗细
     * @param fontStyle       字体样式
     * @return 文本片段列表
     */
    public List<TextSegment> layoutSegments(String text, int baseColor, TextContentMode textContentMode,
                                            UiFontWeight fontWeight, UiFontStyle fontStyle) {
        return parseSegments(text, baseColor, textContentMode, createBaseStyle(baseColor, fontWeight, fontStyle));
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
        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            width += getCodepointWidth(codepoint, segment.getStyle());
            i += Character.charCount(codepoint);
        }
        return width;
    }

    /**
     * 计算单个文本片段在指定 UI 像素字号下的宽度。
     *
     * @param segment    文本片段
     * @param fontSizePx UI 像素字号
     * @return UI 像素宽度
     */
    public double getSegmentWidth(TextSegment segment, int fontSizePx) {
        double width = 0.0D;
        String text = segment.getText();
        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            width += getCodepointWidth(codepoint, segment.getStyle(), fontSizePx);
            i += Character.charCount(codepoint);
        }
        return width;
    }

    /**
     * 获取指定字符在当前样式下的推进宽度。
     *
     * @param codepoint 字符码点
     * @param style     文本样式
     * @return 推进宽度
     */
    public double getCodepointWidth(int codepoint, TextStyle style) {
        return measureCodepointWidth(codepoint, style.getFontType());
    }

    /**
     * 获取指定字符在指定 UI 像素字号下的推进宽度。
     *
     * @param codepoint  字符码点
     * @param style      文本样式
     * @param fontSizePx UI 像素字号
     * @return UI 像素推进宽度
     */
    public double getCodepointWidth(int codepoint, TextStyle style, int fontSizePx) {
        return measureCodepointWidth(codepoint, style.getFontType(), fontSizePx);
    }

    private double measureCodepointWidth(int codepoint, FontType fontType) {
        if (codepoint == ' ') {
            return FontConfig.spaceWidth;
        }

        GlyphRuntimeTables tables = glyphPageManager.getRuntimeTables();
        if (tables == null || !GlyphRuntimeTables.isValidCodepoint(codepoint)) {
            widthCacheMissCount.increment();
            return measureAwtWidth(codepoint, fontType);
        }

        float[] widthCache = tables.widthArray(fontType);
        float cachedWidth = widthCache[codepoint];
        if (!Float.isNaN(cachedWidth)) {
            widthCacheHitCount.increment();
            return cachedWidth;
        }
        widthCacheMissCount.increment();

        float measuredWidth = (float) measureAwtWidth(codepoint, fontType);
        widthCache[codepoint] = measuredWidth;
        return measuredWidth;
    }

    private double measureCodepointWidth(int codepoint, FontType fontType, int fontSizePx) {
        double defaultWidth = measureCodepointWidth(codepoint, fontType);
        return defaultWidth * Math.max(1, fontSizePx) / Math.max(1.0D, FontConfig.charSize);
    }

    /**
     * 获取当前布局层的基准行高。
     *
     * @return 行高
     */
    public int getLineHeight() {
        return getLineHeight((int) FontConfig.charSize);
    }

    /**
     * 获取指定语义化文本样式下的 UI 像素行高。
     *
     * @param style 文本样式快照
     * @return UI 像素行高
     */
    public int getLineHeight(TextMeasureStyle style) {
        TextMeasureStyle resolvedStyle = resolveTextMeasureStyle(style);
        return getLineHeight(resolvedStyle.getFontSizePx());
    }

    private int getLineHeight(int fontSizePx) {
        int safeFontSizePx = Math.max(1, fontSizePx);
        int ascent = getAscent(safeFontSizePx);
        int descent = getDescent(safeFontSizePx);
        int lineGap = getLineGap(safeFontSizePx);
        int fontMetricsHeight = ascent + descent + lineGap;
        if (fontMetricsHeight <= 0) {
            return safeFontSizePx;
        }
        return fontMetricsHeight;
    }

    /**
     * 获取指定 UI 像素字号下的字体上升量。
     *
     * @param fontSizePx UI 像素字号
     * @return UI 像素上升量
     */
    public int getAscent(int fontSizePx) {
        GlyphRuntimeTables tables = glyphPageManager.getRuntimeTables();
        float atlasAscent = tables == null ? 0.0F : tables.ascent(FontType.NORMAL);
        return Math.round(atlasAscent * Math.max(1, fontSizePx) / (float) FontConfig.awtCharSize);
    }

    /**
     * 获取指定 UI 像素字号下的字体下降量。
     *
     * @param fontSizePx UI 像素字号
     * @return UI 像素下降量
     */
    public int getDescent(int fontSizePx) {
        GlyphRuntimeTables tables = glyphPageManager.getRuntimeTables();
        float atlasDescent = tables == null ? 0.0F : tables.descent(FontType.NORMAL);
        return Math.round(atlasDescent * Math.max(1, fontSizePx) / (float) FontConfig.awtCharSize);
    }

    /**
     * 获取指定 UI 像素字号下的字体行间隙。
     *
     * @param fontSizePx UI 像素字号
     * @return UI 像素行间隙
     */
    public int getLineGap(int fontSizePx) {
        GlyphRuntimeTables tables = glyphPageManager.getRuntimeTables();
        float atlasLeading = tables == null ? 0.0F : tables.leading(FontType.NORMAL);
        return Math.round(atlasLeading * Math.max(1, fontSizePx) / (float) FontConfig.awtCharSize);
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

        String text = CodepointTextCache.getText(codepoint);
        double advance = new TextLayout(text, font, FONT_RENDER_CONTEXT).getAdvance();
        if (advance <= 0.0D) {
            return FontConfig.spaceWidth;
        }
        return ((advance / glyphSize) * FontConfig.charSize) + FontConfig.characterSpacing;
    }

    /**
     * 清空宽度缓存。
     */
    public void clearCache() {
        // reload 时已换新表（GlyphPageManager.reset 整体替换 runtimeTables 引用），
        // 旧表自然失效，新表本就全 NaN，无需原地清 widthCache。
        // 仅清零本地计数器，保留统计语义。
        widthCacheHitCount.reset();
        widthCacheMissCount.reset();
    }

    /**
     * 获取宽度缓存命中次数。
     *
     * @return 命中次数
     */
    public long getWidthCacheHitCount() {
        return widthCacheHitCount.sum();
    }

    /**
     * 获取宽度缓存未命中次数。
     *
     * @return 未命中次数
     */
    public long getWidthCacheMissCount() {
        return widthCacheMissCount.sum();
    }

    private TextContentMode resolveTextContentMode(TextContentMode textContentMode) {
        return textContentMode == null ? TextContentMode.MINECRAFT_FORMATTED : textContentMode;
    }

    private TextMeasureStyle resolveTextMeasureStyle(TextMeasureStyle style) {
        TextMeasureStyle resolvedStyle = style == null ? TextMeasureStyle.DEFAULT : style;
        return new TextMeasureStyle(resolvedStyle.getFontSizePx(),
                resolveTextContentMode(resolvedStyle.getTextContentMode()), resolvedStyle.getFontWeight(),
                resolvedStyle.getFontStyle());
    }

    private String trimRawStringToWidth(String text, int targetWidth, TextStyle style) {
        return trimRawStringToWidth(text, targetWidth, style, (int) FontConfig.charSize);
    }

    private String trimRawStringToWidth(String text, int targetWidth, TextStyle style, int fontSizePx) {
        StringBuilder builder = new StringBuilder();
        double width = 0.0D;
        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            double charWidth = getCodepointWidth(codepoint, style, fontSizePx);
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
        for (int index = text.length(); index > 0; ) {
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
        for (int i = 0; i < text.length(); ) {
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

    private TextStyle createBaseStyle(int baseColor, UiFontWeight fontWeight, UiFontStyle fontStyle) {
        TextStyle style = new TextStyle();
        style.resetAll(baseColor);
        if (fontWeight == UiFontWeight.BOLD) {
            style.setFontType(FontType.BOLD);
        }
        if (fontStyle == UiFontStyle.ITALIC) {
            style.setItalic(true);
        }
        return style;
    }

    private TextStyle createBaseStyle(int baseColor, TextStyle baseStyle) {
        TextStyle style = new TextStyle();
        style.resetAll(baseColor);
        if (baseStyle == null) {
            return style;
        }
        style.setFontType(baseStyle.getFontType());
        style.setItalic(baseStyle.isItalic());
        return style;
    }
}
