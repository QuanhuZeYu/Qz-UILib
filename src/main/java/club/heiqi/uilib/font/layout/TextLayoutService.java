package club.heiqi.uilib.font.layout;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;

import club.heiqi.uilib.font.ActiveFontGeneration;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.FontRuntimeAccess;
import club.heiqi.uilib.font.FontRuntimeSettings;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.page.GlyphRuntimeTables;
import club.heiqi.uilib.font.util.CodepointTextCache;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontMatcher;
import club.heiqi.uilib.ui.base.props.UiFontStyle;
import club.heiqi.uilib.ui.base.props.UiFontWeight;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextLinkRegion;
import club.heiqi.uilib.ui.text.TextMeasureStyle;

/**
 * 文本布局与测量服务。
 */
public class TextLayoutService {

    private static final FontRenderContext FONT_RENDER_CONTEXT = new FontRenderContext(new AffineTransform(), true, true);
    private static final long WIDTH_MISS_BUDGET_WINDOW_NANOS = 16L * 1000L * 1000L;

    private final FontMatcher fontMatcher;
    private final DerivedFontCache derivedFontCache;
    private final LongAdder widthCacheHitCount = new LongAdder();
    private final LongAdder widthCacheMissCount = new LongAdder();
    private final LongAdder widthCacheBudgetRejectedCount = new LongAdder();
    private final Lock generationReadLock;
    private final Object ownerToken;
    private volatile ActiveFontGeneration activeGeneration;
    private volatile GlyphRuntimeTables runtimeTables;
    private volatile int runtimeVersion;
    private long widthMissBudgetWindowStartNanos;
    private int widthMissBudgetRemaining;

    /**
     * 创建文本布局服务。
     *
     * @param fontMatcher      字体匹配器
     * @param glyphPageManager 字符页管理器
     */
    public TextLayoutService(FontMatcher fontMatcher, GlyphPageManager glyphPageManager,
                             DerivedFontCache derivedFontCache) {
        this(fontMatcher, glyphPageManager, derivedFontCache, null);
    }

    /**
     * 创建受 generation read barrier 保护的文本布局服务。
     *
     * @param fontMatcher 字体匹配器
     * @param glyphPageManager 字符页管理器
     * @param derivedFontCache legacy 派生字体缓存
     * @param generationReadLock generation 读锁；独立测试可传 null
     */
    public TextLayoutService(FontMatcher fontMatcher, GlyphPageManager glyphPageManager,
            DerivedFontCache derivedFontCache, Lock generationReadLock) {
        this(fontMatcher, glyphPageManager, derivedFontCache, generationReadLock, null);
    }

    /**
     * 创建绑定字体 singleton owner 的文本布局服务。
     *
     * @param fontMatcher 字体匹配器
     * @param glyphPageManager 字符页管理器
     * @param derivedFontCache 派生字体缓存
     * @param generationReadLock generation 读锁
     * @param ownerToken 内部 owner token；独立测试对象可传 null
     */
    public TextLayoutService(FontMatcher fontMatcher, GlyphPageManager glyphPageManager,
            DerivedFontCache derivedFontCache, Lock generationReadLock, Object ownerToken) {
        this.fontMatcher = fontMatcher;
        this.derivedFontCache = derivedFontCache;
        this.generationReadLock = generationReadLock;
        this.ownerToken = ownerToken;
        this.runtimeTables = glyphPageManager.getRuntimeTables();
    }

    /**
     * 设置当前运行时版本。
     *
     * @param runtimeVersion 运行时版本
     */
    public void setRuntimeVersion(int runtimeVersion) {
        assertRuntimeAccess();
        this.runtimeVersion = runtimeVersion;
    }

    /**
     * 原子绑定当前字体 generation。
     *
     * @param generation active generation
     * @param generationRuntimeTables generation 的唯一 direct tables
     */
    public void setGeneration(ActiveFontGeneration generation, GlyphRuntimeTables generationRuntimeTables) {
        assertRuntimeAccess();
        if (generation == null || generationRuntimeTables == null) {
            throw new IllegalArgumentException("generation binding 成员不得为 null");
        }
        activeGeneration = generation;
        runtimeTables = generationRuntimeTables;
        runtimeVersion = generation.getRuntimeVersion();
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
        if (resolvedMode == TextContentMode.RICH_TAGS) {
            return RichTextTagParser.parse(text, createBaseStyle(baseColor, baseStyle));
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
        lockGeneration();
        try {
            if (text == null || text.isEmpty()) {
                return 0;
            }

            double width = 0.0D;
            TextStyle baseStyle = createBaseStyle(0xFFFFFFFF, fontWeight, fontStyle);
            for (TextSegment segment : parseSegments(text, 0xFFFFFFFF, textContentMode, baseStyle)) {
                width += getSegmentWidth(segment);
            }
            return (int) Math.ceil(width);
        } finally {
            unlockGeneration();
        }
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
        lockGeneration();
        try {
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
        } finally {
            unlockGeneration();
        }
    }

    /**
     * 计算指定语义化文本样式下的字符串 UI 像素宽度。
     *
     * @param text  文本
     * @param style 文本样式快照
     * @return UI 像素宽度
     */
    public int getStringWidth(String text, TextMeasureStyle style) {
        lockGeneration();
        try {
            TextMeasureStyle resolvedStyle = resolveTextMeasureStyle(style);
            if (text == null || text.isEmpty()) {
                return 0;
            }

            double width = 0.0D;
            TextStyle baseStyle = createBaseStyle(0xFFFFFFFF, resolvedStyle.getFontWeight(),
                    resolvedStyle.getFontStyle());
            for (TextSegment segment : parseSegments(text, 0xFFFFFFFF, resolvedStyle.getTextContentMode(), baseStyle)) {
                width += getSegmentWidth(segment, resolvedStyle.getFontSizePx());
            }
            return (int) Math.ceil(width);
        } finally {
            unlockGeneration();
        }
    }

    public java.util.List<TextLinkRegion> getLinkRegions(String line, TextMeasureStyle style) {
        lockGeneration();
        try {
            TextMeasureStyle resolvedStyle = resolveTextMeasureStyle(style);
            if (line == null || line.isEmpty()
                    || resolveTextContentMode(resolvedStyle.getTextContentMode()) != TextContentMode.RICH_TAGS) {
                return java.util.Collections.emptyList();
            }
            TextStyle baseStyle = createBaseStyle(0xFFFFFFFF, resolvedStyle.getFontWeight(),
                    resolvedStyle.getFontStyle());
            java.util.List<TextLinkRegion> regions = new ArrayList<TextLinkRegion>();
            double running = 0.0D;
            for (TextSegment segment : RichTextTagParser.parse(line, baseStyle)) {
                double segmentWidth = getSegmentWidth(segment, resolvedStyle.getFontSizePx());
                String url = segment.getStyle().getLink();
                if (url != null && segmentWidth > 0.0D) {
                    regions.add(new TextLinkRegion((int) Math.round(running),
                            (int) Math.round(segmentWidth), url));
                }
                running += segmentWidth;
            }
            return regions;
        } finally {
            unlockGeneration();
        }
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
        lockGeneration();
        try {
            if (text == null || text.isEmpty() || targetWidth <= 0) {
                return "";
            }

            TextContentMode resolvedMode = resolveTextContentMode(textContentMode);
            if (resolvedMode == TextContentMode.UILIB_RAW) {
                return trimRawStringToWidth(text, targetWidth, createBaseStyle(0xFFFFFFFF, fontWeight, fontStyle));
            }
            if (resolvedMode == TextContentMode.RICH_TAGS) {
                return trimRichStringToWidth(text, targetWidth,
                        createBaseStyle(0xFFFFFFFF, fontWeight, fontStyle),
                        (int) currentSettings().getCharSize());
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
        } finally {
            unlockGeneration();
        }
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
        lockGeneration();
        try {
            TextMeasureStyle resolvedStyle = resolveTextMeasureStyle(style);
            if (text == null || text.isEmpty() || targetWidth <= 0) {
                return "";
            }

            if (resolveTextContentMode(resolvedStyle.getTextContentMode()) == TextContentMode.UILIB_RAW) {
                return trimRawStringToWidth(text, targetWidth, createBaseStyle(0xFFFFFFFF,
                        resolvedStyle.getFontWeight(), resolvedStyle.getFontStyle()), resolvedStyle.getFontSizePx());
            }
            if (resolveTextContentMode(resolvedStyle.getTextContentMode()) == TextContentMode.RICH_TAGS) {
                return trimRichStringToWidth(text, targetWidth, createBaseStyle(0xFFFFFFFF,
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
        } finally {
            unlockGeneration();
        }
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
        lockGeneration();
        try {
            if (!reverse) {
                return trimStringToWidth(text, targetWidth, textContentMode);
            }
            if (text == null || text.isEmpty() || targetWidth <= 0) {
                return "";
            }

            if (resolveTextContentMode(textContentMode) == TextContentMode.UILIB_RAW) {
                return trimRawStringToWidthFromTail(text, targetWidth);
            }
            if (resolveTextContentMode(textContentMode) == TextContentMode.RICH_TAGS) {
                return trimRichStringToWidthFromTail(text, targetWidth,
                        createBaseStyle(0xFFFFFFFF, UiFontWeight.NORMAL, UiFontStyle.NORMAL),
                        (int) currentSettings().getCharSize());
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
        } finally {
            unlockGeneration();
        }
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
        lockGeneration();
        try {
            if (text == null || text.isEmpty() || wrapWidth <= 0) {
                return "";
            }

            if (resolveTextContentMode(textContentMode) == TextContentMode.UILIB_RAW) {
                return wrapRawStringToWidth(text, wrapWidth);
            }
            if (resolveTextContentMode(textContentMode) == TextContentMode.RICH_TAGS) {
                return wrapRichStringToWidth(text, wrapWidth,
                        createBaseStyle(0xFFFFFFFF, UiFontWeight.NORMAL, UiFontStyle.NORMAL));
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
        } finally {
            unlockGeneration();
        }
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
        lockGeneration();
        try {
            List<String> lines = listFormattedStringToWidth(text, wrapWidth, textContentMode);
            if (lines.isEmpty()) {
                return 0;
            }
            return getLineHeight() * lines.size();
        } finally {
            unlockGeneration();
        }
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
        lockGeneration();
        try {
            double width = 0.0D;
            String text = segment.getText();
            TextStyle style = segment.getStyle();
            int effectiveSize = style == null ? 0
                    : style.resolveEffectiveFontSizePx((int) currentSettings().getCharSize());
            for (int i = 0; i < text.length(); ) {
                int codepoint = text.codePointAt(i);
                width += resolveCodepointAdvance(codepoint, style, effectiveSize);
                i += Character.charCount(codepoint);
            }
            return width;
        } finally {
            unlockGeneration();
        }
    }

    /**
     * 计算单个文本片段在指定 UI 像素字号下的宽度。
     *
     * @param segment    文本片段
     * @param fontSizePx UI 像素字号
     * @return UI 像素宽度
     */
    public double getSegmentWidth(TextSegment segment, int fontSizePx) {
        lockGeneration();
        try {
            double width = 0.0D;
            String text = segment.getText();
            TextStyle style = segment.getStyle();
            int effectiveSize = style == null ? fontSizePx : style.resolveEffectiveFontSizePx(fontSizePx);
            for (int i = 0; i < text.length(); ) {
                int codepoint = text.codePointAt(i);
                width += resolveCodepointAdvance(codepoint, style, effectiveSize);
                i += Character.charCount(codepoint);
            }
            return width;
        } finally {
            unlockGeneration();
        }
    }

    /**
     * 获取指定字符在当前样式下的推进宽度。
     *
     * @param codepoint 字符码点
     * @param style     文本样式
     * @return 推进宽度
     */
    public double getCodepointWidth(int codepoint, TextStyle style) {
        lockGeneration();
        try {
            return measureCodepointWidth(codepoint, style.getFontType());
        } finally {
            unlockGeneration();
        }
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
        lockGeneration();
        try {
            return measureCodepointWidth(codepoint, style.getFontType(), fontSizePx);
        } finally {
            unlockGeneration();
        }
    }

    private double measureCodepointWidth(int codepoint, FontType fontType) {
        if (codepoint == ' ') {
            return currentSettings().getSpaceWidth();
        }
        if (codepoint == '\n' || codepoint == '\r') {
            return 0.0D;
        }

        GlyphRuntimeTables tables = currentRuntimeTables();
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
        if (!tryAcquireWidthMissBudget()) {
            widthCacheBudgetRejectedCount.increment();
            return currentSettings().getSpaceWidth();
        }

        float measuredWidth = (float) measureAwtWidth(codepoint, fontType);
        widthCache[codepoint] = measuredWidth;
        return measuredWidth;
    }

    /**
     * 尝试领取本时间窗内的宽度测量 miss 预算；预算耗尽时返回 false，
     * 调用方以近似宽度顺延到下一窗口再测量。
     */
    private synchronized boolean tryAcquireWidthMissBudget() {
        int budget = FontConfig.widthCacheMissBudgetPerWindow;
        if (budget <= 0) {
            return true;
        }
        long now = System.nanoTime();
        if (now - widthMissBudgetWindowStartNanos >= WIDTH_MISS_BUDGET_WINDOW_NANOS) {
            widthMissBudgetWindowStartNanos = now;
            widthMissBudgetRemaining = budget;
        }
        if (widthMissBudgetRemaining <= 0) {
            return false;
        }
        widthMissBudgetRemaining--;
        return true;
    }

    private double measureCodepointWidth(int codepoint, FontType fontType, int fontSizePx) {
        double defaultWidth = measureCodepointWidth(codepoint, fontType);
        return defaultWidth * Math.max(1, fontSizePx) / Math.max(1.0D, currentSettings().getCharSize());
    }

    /**
     * 码点推进宽度追加字符间距：每个非换行码点之后追加段样式 letterSpacing（可为负）。
     *
     * @param charWidth 码点推进宽度
     * @param codepoint 码点
     * @param style     段样式
     * @return 含字距的推进宽度
     */
    private double advanceWithSpacing(double charWidth, int codepoint, TextStyle style) {
        if (style == null || codepoint == '\n' || codepoint == '\r') {
            return charWidth;
        }
        return charWidth + style.getLetterSpacing();
    }

    /**
     * 码点推进宽度唯一原语：逐码点测量 + 非换行码点追加段样式 letterSpacing。
     *
     * <p>全部 trim/wrap/token/segment 测量循环必须经本方法取推进宽度，
     * 禁止自行拼装 {@code measureCodepointWidth + advanceWithSpacing}（测量与渲染口径漂移的温床）。
     * {@code effectiveSize <= 0} 时回落基准字号测量（保持旧 getSegmentWidth 无字号路径语义）。</p>
     */
    /**
     * 码点推进宽度（含字距）公共入口：render 侧与测量侧同源取推进宽度，
     * 保证 rendered measuredWidths 累加与 getStringWidth/trim/wrap 口径一致。
     */
    public double resolveAdvance(int codepoint, TextStyle style, int fontSizePx) {
        lockGeneration();
        try {
            return resolveCodepointAdvance(codepoint, style, fontSizePx);
        } finally {
            unlockGeneration();
        }
    }

    private double resolveCodepointAdvance(int codepoint, TextStyle style, int effectiveSize) {
        double charWidth = effectiveSize > 0
                ? measureCodepointWidth(codepoint, style.getFontType(), effectiveSize)
                : measureCodepointWidth(codepoint, style.getFontType());
        return advanceWithSpacing(charWidth, codepoint, style);
    }

    /**
     * 获取当前布局层的基准行高。
     *
     * @return 行高
     */
    public int getLineHeight() {
        lockGeneration();
        try {
            return getLineHeight((int) currentSettings().getCharSize());
        } finally {
            unlockGeneration();
        }
    }

    /**
     * 获取指定语义化文本样式下的 UI 像素行高。
     *
     * @param style 文本样式快照
     * @return UI 像素行高
     */
    public int getLineHeight(TextMeasureStyle style) {
        lockGeneration();
        try {
            TextMeasureStyle resolvedStyle = resolveTextMeasureStyle(style);
            return getLineHeight(resolvedStyle.getFontSizePx());
        } finally {
            unlockGeneration();
        }
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
     * 计算指定文本在语义化样式下的行高（富文本感知：显式字号段按最大字号计）。
     *
     * @param text  文本内容；为 null/空或非富文本模式时回落到样式字号行高
     * @param style 文本样式快照
     * @return UI 像素行高
     */
    public int getLineHeight(String text, TextMeasureStyle style) {
        lockGeneration();
        try {
            TextMeasureStyle resolvedStyle = resolveTextMeasureStyle(style);
            if (text == null || text.isEmpty()
                    || resolveTextContentMode(resolvedStyle.getTextContentMode()) != TextContentMode.RICH_TAGS) {
                return getLineHeight(resolvedStyle);
            }
            TextStyle baseStyle = createBaseStyle(0xFFFFFFFF, resolvedStyle.getFontWeight(),
                    resolvedStyle.getFontStyle());
            int maxFontSizePx = resolvedStyle.getFontSizePx();
            for (TextSegment segment : RichTextTagParser.parse(text, baseStyle)) {
                int fontSizePx = segment.getStyle().resolveEffectiveFontSizePx(resolvedStyle.getFontSizePx());
                if (fontSizePx > maxFontSizePx) {
                    maxFontSizePx = fontSizePx;
                }
            }
            return getLineHeight(maxFontSizePx);
        } finally {
            unlockGeneration();
        }
    }

    /**
     * 获取指定 UI 像素字号下的字体上升量。
     *
     * @param fontSizePx UI 像素字号
     * @return UI 像素上升量
     */
    public int getAscent(int fontSizePx) {
        lockGeneration();
        try {
            GlyphRuntimeTables tables = currentRuntimeTables();
            float atlasAscent = tables == null ? 0.0F : tables.ascent(FontType.NORMAL);
            return Math.round(atlasAscent * Math.max(1, fontSizePx)
                    / (float) currentSettings().getAwtCharSize());
        } finally {
            unlockGeneration();
        }
    }

    /**
     * 获取指定 UI 像素字号下的字体下降量。
     *
     * @param fontSizePx UI 像素字号
     * @return UI 像素下降量
     */
    public int getDescent(int fontSizePx) {
        lockGeneration();
        try {
            GlyphRuntimeTables tables = currentRuntimeTables();
            float atlasDescent = tables == null ? 0.0F : tables.descent(FontType.NORMAL);
            return Math.round(atlasDescent * Math.max(1, fontSizePx)
                    / (float) currentSettings().getAwtCharSize());
        } finally {
            unlockGeneration();
        }
    }

    /**
     * 获取指定 UI 像素字号下的字体行间隙。
     *
     * @param fontSizePx UI 像素字号
     * @return UI 像素行间隙
     */
    public int getLineGap(int fontSizePx) {
        lockGeneration();
        try {
            GlyphRuntimeTables tables = currentRuntimeTables();
            float atlasLeading = tables == null ? 0.0F : tables.leading(FontType.NORMAL);
            return Math.round(atlasLeading * Math.max(1, fontSizePx)
                    / (float) currentSettings().getAwtCharSize());
        } finally {
            unlockGeneration();
        }
    }

    private double measureAwtWidth(int codepoint, FontType fontType) {
        FontRuntimeSettings settings = currentSettings();
        int glyphSize = settings.getGlyphSize();
        int fontIndex = fontMatcher.matchFontIndex(runtimeVersion, codepoint, fontType);
        if (fontIndex < 0) {
            return settings.getSpaceWidth();
        }

        Font font = fontMatcher.getDerivedFont(runtimeVersion, fontIndex, fontType, glyphSize);
        if (font == null) {
            return settings.getSpaceWidth();
        }

        String text = CodepointTextCache.getText(codepoint);
        double advance = new TextLayout(text, font, FONT_RENDER_CONTEXT).getAdvance();
        if (advance <= 0.0D) {
            return settings.getSpaceWidth();
        }
        return ((advance / glyphSize) * settings.getCharSize()) + settings.getCharacterSpacing();
    }

    /**
     * 清空宽度缓存。
     */
    public void clearCache() {
        assertRuntimeAccess();
        // generation barrier 已先原地清空共享 runtimeTables；这里仅清零本地计数器。
        widthCacheHitCount.reset();
        widthCacheMissCount.reset();
        widthCacheBudgetRejectedCount.reset();
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

    /**
     * 获取因 miss 预算耗尽而被顺延的测量次数。
     *
     * @return 预算拒绝次数
     */
    public long getWidthCacheBudgetRejectedCount() {
        return widthCacheBudgetRejectedCount.sum();
    }

    private void assertRuntimeAccess() {
        if (!FontRuntimeAccess.isActive(ownerToken)) {
            throw new IllegalStateException("TextLayoutService 只能由字体 runtime owner 修改 generation binding");
        }
    }

    private FontRuntimeSettings currentSettings() {
        ActiveFontGeneration generation = activeGeneration;
        return generation == null ? FontRuntimeSettings.capture() : generation.getSettings();
    }

    private GlyphRuntimeTables currentRuntimeTables() {
        return runtimeTables;
    }

    private void lockGeneration() {
        if (generationReadLock != null) {
            generationReadLock.lock();
        }
    }

    private void unlockGeneration() {
        if (generationReadLock != null) {
            generationReadLock.unlock();
        }
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
        return trimRawStringToWidth(text, targetWidth, style, (int) currentSettings().getCharSize());
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

    /**
     * 按宽度裁剪富文本（正向保留前缀），结果以标签文本重建（被裁部分样式不泄漏）。
     *
     * @param text           富文本
     * @param targetWidth    目标宽度
     * @param baseStyle      基准样式
     * @param baseFontSizePx 未显式指定字号段落的基准字号
     * @return 裁剪后的标签文本
     */
    private String trimRichStringToWidth(String text, int targetWidth, TextStyle baseStyle, int baseFontSizePx) {
        List<TextSegment> segments = RichTextTagParser.parse(text, baseStyle);
        List<TextSegment> kept = new ArrayList<TextSegment>();
        double width = 0.0D;
        int safeBaseSize = Math.max(1, baseFontSizePx);
        for (TextSegment segment : segments) {
            TextStyle style = segment.getStyle();
            String segmentText = segment.getText();
            int effectiveSize = style.resolveEffectiveFontSizePx(safeBaseSize);
            StringBuilder keptText = new StringBuilder();
            for (int i = 0; i < segmentText.length(); ) {
                int codepoint = segmentText.codePointAt(i);
                double charWidth = resolveCodepointAdvance(codepoint, style, effectiveSize);
                if (width + charWidth > targetWidth) {
                    break;
                }
                width += charWidth;
                keptText.appendCodePoint(codepoint);
                i += Character.charCount(codepoint);
            }
            if (keptText.length() > 0) {
                kept.add(new TextSegment(keptText.toString(), style));
            }
            if (keptText.length() < segmentText.length()) {
                break;
            }
        }
        return RichTextTagParser.serialize(kept, baseStyle);
    }

    /**
     * 按宽度裁剪富文本（反向保留尾部）。
     *
     * @param text           富文本
     * @param targetWidth    目标宽度
     * @param baseStyle      基准样式
     * @param baseFontSizePx 未显式指定字号段落的基准字号
     * @return 裁剪后的标签文本
     */
    private String trimRichStringToWidthFromTail(String text, int targetWidth, TextStyle baseStyle,
            int baseFontSizePx) {
        List<TextSegment> segments = RichTextTagParser.parse(text, baseStyle);
        List<TextSegment> kept = new ArrayList<TextSegment>();
        double width = 0.0D;
        boolean truncated = false;
        int safeBaseSize = Math.max(1, baseFontSizePx);
        for (int segmentIndex = segments.size() - 1; segmentIndex >= 0 && !truncated; segmentIndex--) {
            TextSegment segment = segments.get(segmentIndex);
            TextStyle style = segment.getStyle();
            String segmentText = segment.getText();
            int effectiveSize = style.resolveEffectiveFontSizePx(safeBaseSize);
            int end = segmentText.length();
            int start = end;
            while (start > 0) {
                int codepoint = segmentText.codePointBefore(start);
                double charWidth = resolveCodepointAdvance(codepoint, style, effectiveSize);
                if (width + charWidth > targetWidth) {
                    truncated = true;
                    break;
                }
                width += charWidth;
                start -= Character.charCount(codepoint);
            }
            String keptText = segmentText.substring(start, end);
            if (!keptText.isEmpty()) {
                kept.add(0, new TextSegment(keptText, style));
            }
        }
        return RichTextTagParser.serialize(kept, baseStyle);
    }

    /**
     * 按宽度对富文本插入换行（硬换行符优先，软换行按 token 宽度累计）。
     *
     * <p>软换行采用现代 word-break 语义：CJK 单字为独立 token，任意字间可断；
     * 其余字符聚成不可拆词 token，优先整词折行；词宽超过行宽时按字符硬断。
     * 行尾空白折叠移除、行首空白丢弃。切点落在样式片段中间时，行文本经
     * {@link RichTextTagParser#serialize} 重建：行尾显式闭合、行首按样式差异自动重开，
     * 跨行样式续传零特判。</p>
     *
     * @param text      富文本
     * @param wrapWidth 最大宽度
     * @param baseStyle 基准样式
     * @return 含换行符的标签文本
     */
    private String wrapRichStringToWidth(String text, int wrapWidth, TextStyle baseStyle) {
        int safeBaseSize = Math.max(1, (int) currentSettings().getCharSize());
        List<TextSegment> segments = RichTextTagParser.parse(text, baseStyle);
        List<String> lines = new ArrayList<String>();
        List<TextSegment> currentLine = new ArrayList<TextSegment>();
        double width = 0.0D;
        boolean lineHasVisibleContent = false;
        for (TextSegment segment : segments) {
            TextStyle style = segment.getStyle();
            String remaining = segment.getText();
            while (!remaining.isEmpty()) {
                int codepoint = remaining.codePointAt(0);
                int codepointLength = Character.charCount(codepoint);
                if (codepoint == '\n' || codepoint == '\r') {
                    flushRichLine(lines, currentLine, baseStyle, true);
                    width = 0.0D;
                    lineHasVisibleContent = false;
                    if (codepoint == '\r' && remaining.length() > codepointLength
                            && remaining.charAt(codepointLength) == '\n') {
                        remaining = remaining.substring(codepointLength + 1);
                    } else {
                        remaining = remaining.substring(codepointLength);
                    }
                    continue;
                }
                int tokenEnd = findRichTokenEnd(remaining);
                String token = remaining.substring(0, tokenEnd);
                boolean tokenIsSpace = isBreakSpace(codepoint);
                double tokenWidth = measureTokenWidth(token, style, safeBaseSize);
                if (width + tokenWidth > wrapWidth && lineHasVisibleContent) {
                    flushRichLine(lines, currentLine, baseStyle, false);
                    width = 0.0D;
                    lineHasVisibleContent = false;
                }
                if (tokenIsSpace) {
                    if (lineHasVisibleContent) {
                        appendTokenToRichLine(currentLine, token, style);
                        width += tokenWidth;
                    }
                    remaining = remaining.substring(tokenEnd);
                    continue;
                }
                if (width + tokenWidth > wrapWidth && !lineHasVisibleContent) {
                    // 空行放不下整词：按字符硬断，填满一行折一行
                    int effectiveSize = style.resolveEffectiveFontSizePx(safeBaseSize);
                    for (int i = 0; i < token.length(); ) {
                        int tokenCodepoint = token.codePointAt(i);
                        double charWidth = resolveCodepointAdvance(tokenCodepoint, style, effectiveSize);
                        if (width + charWidth > wrapWidth && lineHasVisibleContent) {
                            flushRichLine(lines, currentLine, baseStyle, false);
                            width = 0.0D;
                            lineHasVisibleContent = false;
                        }
                        appendToRichLine(currentLine, tokenCodepoint, style);
                        width += charWidth;
                        lineHasVisibleContent = true;
                        i += Character.charCount(tokenCodepoint);
                    }
                    remaining = remaining.substring(tokenEnd);
                    continue;
                }
                appendTokenToRichLine(currentLine, token, style);
                width += tokenWidth;
                lineHasVisibleContent = true;
                remaining = remaining.substring(tokenEnd);
            }
        }
        flushRichLine(lines, currentLine, baseStyle, false);
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line);
        }
        return builder.toString();
    }

    /**
     * 把一个码点追加到行片段列表尾部（样式一致时合并入末段）。
     *
     * @param line      行片段列表
     * @param codepoint 码点
     * @param style     码点样式
     */
    private void appendToRichLine(List<TextSegment> line, int codepoint, TextStyle style) {
        String glyphText = new String(Character.toChars(codepoint));
        if (!line.isEmpty()) {
            TextSegment last = line.get(line.size() - 1);
            if (sameRichStyle(last.getStyle(), style)) {
                line.set(line.size() - 1, new TextSegment(last.getText() + glyphText, style));
                return;
            }
        }
        line.add(new TextSegment(glyphText, style));
    }

    /**
     * 把一个完整 token 追加到行片段列表尾部（逐码点合并，样式一致时并入末段）。
     *
     * @param line  行片段列表
     * @param token token 文本
     * @param style token 样式
     */
    private void appendTokenToRichLine(List<TextSegment> line, String token, TextStyle style) {
        for (int i = 0; i < token.length(); ) {
            int codepoint = token.codePointAt(i);
            appendToRichLine(line, codepoint, style);
            i += Character.charCount(codepoint);
        }
    }

    /**
     * 折叠行尾空白并落行：移除行片段末尾的空格/tab/全角空格后序列化入行列表。
     *
     * @param lines        行结果列表
     * @param line         当前行片段
     * @param baseStyle    基准样式
     * @param keepEmptyLine 折叠后为空时是否仍落一个空行（硬换行场景保留空行语义）
     */
    private void flushRichLine(List<String> lines, List<TextSegment> line, TextStyle baseStyle,
            boolean keepEmptyLine) {
        removeTrailingSpaces(line);
        if (line.isEmpty()) {
            if (keepEmptyLine) {
                lines.add("");
            }
            return;
        }
        lines.add(RichTextTagParser.serialize(line, baseStyle));
        line.clear();
    }

    /**
     * 移除行片段列表末段的尾部空白字符（空格/tab/全角空格），跨段回溯直至无空白。
     *
     * @param line 行片段列表
     */
    private void removeTrailingSpaces(List<TextSegment> line) {
        while (!line.isEmpty()) {
            TextSegment last = line.get(line.size() - 1);
            String text = last.getText();
            int end = text.length();
            while (end > 0 && isBreakSpace(text.charAt(end - 1))) {
                end--;
            }
            if (end == text.length()) {
                return;
            }
            if (end == 0) {
                line.remove(line.size() - 1);
            } else {
                line.set(line.size() - 1, new TextSegment(text.substring(0, end), last.getStyle()));
                return;
            }
        }
    }

    /**
     * 从文本首字符起提取一个 word-break token：连续空白、单个 CJK 字符或连续非空白非 CJK 字符。
     * token 不跨换行符。
     *
     * @param text 文本（首字符非换行符）
     * @return token 结束下标（按 UTF-16 单元）
     */
    private int findRichTokenEnd(String text) {
        int first = text.codePointAt(0);
        int end = Character.charCount(first);
        if (isBreakSpace(first)) {
            while (end < text.length() && isBreakSpace(text.codePointAt(end))) {
                end += Character.charCount(text.codePointAt(end));
            }
            return end;
        }
        if (isCjk(first)) {
            return end;
        }
        while (end < text.length()) {
            int codepoint = text.codePointAt(end);
            if (codepoint == '\n' || codepoint == '\r' || isBreakSpace(codepoint) || isCjk(codepoint)) {
                break;
            }
            end += Character.charCount(codepoint);
        }
        return end;
    }

    /**
     * 计算 token 宽度（逐码点按段字号测量后累加）。
     *
     * @param token        token 文本
     * @param style        token 样式
     * @param safeBaseSize 基准字号
     * @return token 宽度
     */
    private double measureTokenWidth(String token, TextStyle style, int safeBaseSize) {
        double total = 0.0D;
        int effectiveSize = style.resolveEffectiveFontSizePx(safeBaseSize);
        for (int i = 0; i < token.length(); ) {
            int codepoint = token.codePointAt(i);
            total += resolveCodepointAdvance(codepoint, style, effectiveSize);
            i += Character.charCount(codepoint);
        }
        return total;
    }

    /**
     * 判断码点是否为可折叠断行空白（ASCII 空格、tab、全角空格）。
     *
     * @param codepoint 码点
     * @return 空白标记
     */
    private static boolean isBreakSpace(int codepoint) {
        return codepoint == ' ' || codepoint == '\t' || codepoint == 0x3000;
    }

    /**
     * 判断码点是否属于 CJK 书写体系（字间任意位置可断行）。
     *
     * @param codepoint 码点
     * @return CJK 标记
     */
    private static boolean isCjk(int codepoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codepoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL
                || script == Character.UnicodeScript.BOPOMOFO;
    }

    /**
     * 比较两个富文本片段样式是否一致（不含随机样式标记）。
     *
     * @param left  左样式
     * @param right 右样式
     * @return 一致标记
     */
    private boolean sameRichStyle(TextStyle left, TextStyle right) {
        return left.getColor() == right.getColor()
                && left.isColorExplicit() == right.isColorExplicit()
                && left.getFontType() == right.getFontType()
                && left.isItalic() == right.isItalic()
                && left.isUnderline() == right.isUnderline()
                && left.isStrikethrough() == right.isStrikethrough()
                && left.getFontSizePx() == right.getFontSizePx()
                && left.getMarkColor() == right.getMarkColor()
                && left.isSuperscript() == right.isSuperscript()
                && left.isSubscript() == right.isSubscript()
                && left.getLetterSpacing() == right.getLetterSpacing()
                && java.util.Objects.equals(left.getLink(), right.getLink());
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
