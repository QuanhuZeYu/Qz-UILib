package club.heiqi.uilib.font.layout;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;

import club.heiqi.uilib.font.ActiveFontGeneration;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.FontRuntimeAccess;
import club.heiqi.uilib.font.FontRuntimeSettings;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.util.UnicodeTextClassifier;
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

    /** CCC（canonical combining class）反射入口：JDK8 sun.text.Normalizer；不可用时为 null（全部按上方标记处理）。 */
    private static final java.lang.reflect.Method CCC_METHOD = resolveCccMethod();

    private static java.lang.reflect.Method resolveCccMethod() {
        try {
            java.lang.reflect.Method method = Class.forName("sun.text.Normalizer")
                    .getMethod("getCombiningClass", int.class);
            method.setAccessible(true);
            return method;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 取码点 CCC（canonical combining class）；反射不可用时返回 0（视作上方标记）。
     *
     * <p>CCC 决定组合标记的附着方向：Below 系（220/202/200/218/222/233/240）向下堆叠，
     * 其余（230/216/232/234/0 等）向上堆叠。</p>
     *
     * @param codepoint Unicode 码点
     * @return canonical combining class（0..255）
     */
    private static int combiningClass(int codepoint) {
        if (CCC_METHOD == null) {
            return 0;
        }
        try {
            return ((Integer) CCC_METHOD.invoke(null, Integer.valueOf(codepoint))).intValue();
        } catch (Exception ignored) {
            return 0;
        }
    }

    /**
     * 是否下方附着标记：优先 CCC（Below 系：220 Below / 202 Below Attached / 200 Below Left /
     * 218 Below Right / 222 Below Left / 233 Double Below / 240 Iota Subscript）；
     * CCC 不可用（JDK9+ 无 sun.text.Normalizer）或为 0（泰语等无 CCC 脚本）时
     * 回落常用下方标记码点白名单（泰语下方元音/阿拉伯下方系/拉丁下方系常用区间）。
     *
     * @param codepoint Unicode 码点
     * @return true 表示向下堆叠
     */
    private static boolean isBelowMark(int codepoint) {
        int ccc = combiningClass(codepoint);
        if (ccc != 0) {
            return ccc == 220 || ccc == 202 || ccc == 200 || ccc == 218 || ccc == 222 || ccc == 233 || ccc == 240;
        }
        // 泰语下方元音（SARA U/UU 等）
        if (codepoint >= 0x0E36 && codepoint <= 0x0E39) {
            return true;
        }
        // 阿拉伯下方系常用码点
        if (codepoint == 0x0650 || (codepoint >= 0x0653 && codepoint <= 0x0655)
                || codepoint == 0x065B || codepoint == 0x065F
                || (codepoint >= 0x06E3 && codepoint <= 0x06E4) || codepoint == 0x06EA || codepoint == 0x06ED
                || (codepoint >= 0x08F0 && codepoint <= 0x08F2)) {
            return true;
        }
        // 拉丁/通用下方系常用区间（DOT BELOW..TILDE BELOW、CEDILLA/OGONEK 系等）
        if (codepoint >= 0x0323 && codepoint <= 0x0334) {
            return true;
        }
        if (codepoint >= 0x0339 && codepoint <= 0x033E) {
            return true;
        }
        if (codepoint == 0x0345 || codepoint == 0x0347 || codepoint == 0x034C || codepoint == 0x034E) {
            return true;
        }
        if (codepoint >= 0x0353 && codepoint <= 0x0356) {
            return true;
        }
        if (codepoint >= 0x0358 && codepoint <= 0x035B) {
            return true;
        }
        if (codepoint >= 0x035D && codepoint <= 0x035F) {
            return true;
        }
        return codepoint == 0x0362;
    }

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
    private final TextContentModeStrategy rawStrategy;
    private final TextContentModeStrategy minecraftStrategy;
    private final TextContentModeStrategy richStrategy;

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
        this.rawStrategy = new RawTextContentStrategy(this);
        this.minecraftStrategy = new MinecraftTextContentStrategy(this);
        this.richStrategy = new RichTextContentStrategy(this);
    }

    /** 返回指定内容模式的 trim/wrap 策略。 */
    private TextContentModeStrategy strategyFor(TextContentMode mode) {
        TextContentMode resolved = resolveTextContentMode(mode);
        if (resolved == TextContentMode.UILIB_RAW) {
            return rawStrategy;
        }
        if (resolved == TextContentMode.RICH_TAGS) {
            return richStrategy;
        }
        return minecraftStrategy;
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
        // 显示/解析路径统一 NFC 规范化：组合序列（e+U+0301）合并为预组合字符（é），
        // 已规范化文本零分配快路径原样返回。
        text = normalizeNfc(text);

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
            line = normalizeNfc(line);
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
            text = normalizeNfc(text);

            return strategyFor(resolveTextContentMode(textContentMode)).trim(text, targetWidth,
                    createBaseStyle(0xFFFFFFFF, fontWeight, fontStyle),
                    (int) currentSettings().getCharSize());
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
            text = normalizeNfc(text);

            return strategyFor(resolvedStyle.getTextContentMode()).trim(text, targetWidth,
                    createBaseStyle(0xFFFFFFFF, resolvedStyle.getFontWeight(), resolvedStyle.getFontStyle()),
                    resolvedStyle.getFontSizePx());
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
            text = normalizeNfc(text);

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
            text = normalizeNfc(text);

            return strategyFor(resolveTextContentMode(textContentMode)).wrap(text, wrapWidth,
                    createBaseStyle(0xFFFFFFFF, UiFontWeight.NORMAL, UiFontStyle.NORMAL),
                    (int) currentSettings().getCharSize());
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

    double measureCodepointWidth(int codepoint, FontType fontType) {
        // 控制字符统一口径（UnicodeTextClassifier 单处真相）：零宽类（换行/剥离/软断行/
        // 连字控制/变体选择符）恒 0 宽；tab 固定 4 空格列宽；其余走字形表/回退测量。
        UnicodeTextClassifier.CharClass cls = UnicodeTextClassifier.classify(codepoint);
        if (UnicodeTextClassifier.isZeroWidth(codepoint)) {
            return 0.0D;
        }
        if (cls == UnicodeTextClassifier.CharClass.COMBINING_MARK) {
            // 组合标记附着基字：advance 归 0（GPOS mark 定位下位置由渲染层锚点决定），
            // 独立出现时同样零宽（不再回退空格宽豆腐块推进）。
            return 0.0D;
        }
        if (cls == UnicodeTextClassifier.CharClass.TAB) {
            return currentSettings().getSpaceWidth() * UnicodeTextClassifier.TAB_WIDTH_SPACES;
        }
        if (codepoint == ' ') {
            return currentSettings().getSpaceWidth();
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

    double measureCodepointWidth(int codepoint, FontType fontType, int fontSizePx) {
        double defaultWidth = measureCodepointWidth(codepoint, fontType);
        return defaultWidth * Math.max(1, fontSizePx) / Math.max(1.0D, currentSettings().getCharSize());
    }

    /**
     * 码点推进宽度追加字符间距：每个非零宽码点之后追加段样式 letterSpacing（可为负）。
     *
     * @param charWidth 码点推进宽度
     * @param codepoint 码点
     * @param style     段样式
     * @return 含字距的推进宽度
     */
    private double advanceWithSpacing(double charWidth, int codepoint, TextStyle style) {
        if (style == null || UnicodeTextClassifier.isZeroWidth(codepoint)) {
            return charWidth;
        }
        return charWidth + style.getLetterSpacing();
    }

    /**
     * 码点推进宽度唯一原语：逐码点测量 + 非零宽码点追加段样式 letterSpacing。
     *
     * <p>全部 trim/wrap/token/segment 测量循环必须经本方法取推进宽度，
     * 禁止自行拼装 {@code measureCodepointWidth + advanceWithSpacing}（测量与渲染口径漂移的温床）。
     * {@code effectiveSize <= 0} 时回落基准字号测量（保持旧 getSegmentWidth 无字号路径语义）。</p>
     */
    /**
     * 码点推进宽度（含字距）公共入口：render 侧与测量侧同源取推进宽度，
     * 保证 rendered measuredWidths 累加与 getStringWidth/trim/wrap 口径一致。
     *
     * <p>{@code fontSizePx} 为基准字号；本方法按样式（sup/sub）解析有效字号后测量，
     * 与 {@link #getSegmentWidth(TextSegment, int)} 同口径。</p>
     */
    public double resolveAdvance(int codepoint, TextStyle style, int fontSizePx) {
        lockGeneration();
        try {
            int effectiveSize = style == null ? fontSizePx : style.resolveEffectiveFontSizePx(fontSizePx);
            return resolveCodepointAdvance(codepoint, style, effectiveSize);
        } finally {
            unlockGeneration();
        }
    }

    double resolveCodepointAdvance(int codepoint, TextStyle style, int effectiveSize) {
        double charWidth = effectiveSize > 0
                ? measureCodepointWidth(codepoint, style.getFontType(), effectiveSize)
                : measureCodepointWidth(codepoint, style.getFontType());
        return advanceWithSpacing(charWidth, codepoint, style);
    }

    /**
     * 组合标记段落的位置计划（组合附加符堆叠挡 2，渲染层专用）。
     *
     * <p>对含簇延续字符（变体选择符/组合标记）的文本，生成逐码点位置：
     * 常规字符按测量 advance 顺序排布（y=0，基线），组合标记吸附最近基字中心并按
     * CCC 方向逐层堆叠——<b>上方标记向上摞、下方标记向下摞</b>（每层半 ascent），
     * 使多层组合标记像金字塔一样上下成支。</p>
     *
     * <p>说明：Java AWT 的 {@code createGlyphVector} 不执行 OpenType shaping（无 GPOS
     * mark-to-base 数据源，实测 mark 位置 y=0），故采用按字形几何的<b>近似堆叠</b>——
     * 视觉成立（逐层上摞），精确字体锚点不在范围。</p>
     *
     * <p>返回长度 = 2 × 码点数的数组（逐码点 {@code [x, y]}，y 相对基线向上为负），
     * 坐标为 UI 像素（段有效字号、相对段落起点；调用方自行乘 renderScale）。
     * 文本无簇延续字符或字体不可用时返回 {@code null}（调用方走零偏移快路径）。</p>
     *
     * <p>属于引擎内部流通数据，不构成稳定公共 API 承诺。</p>
     *
     * @param text             文本内容（非 null）
     * @param style            段落样式（非 null）
     * @param segmentFontSizePx 段有效字号（>=1）
     * @return 逐码点位置数组；无簇延续字符或字体不可用时返回 null
     */
    public float[] resolveMarkPositions(String text, TextStyle style, int segmentFontSizePx) {
        lockGeneration();
        try {
            if (text == null || text.isEmpty()) {
                return null;
            }
            boolean hasCluster = false;
            int anchorCodepoint = -1;
            for (int i = 0; i < text.length(); ) {
                int codepoint = text.codePointAt(i);
                if (UnicodeTextClassifier.isClusterContinuation(codepoint)) {
                    hasCluster = true;
                } else if (anchorCodepoint < 0) {
                    anchorCodepoint = codepoint;
                }
                i += Character.charCount(codepoint);
            }
            if (!hasCluster) {
                return null;
            }
            if (anchorCodepoint < 0) {
                // 全标记行（无常规字符锚点）：以空格为字体锚，堆叠从行首开始
                anchorCodepoint = ' ';
            }

            FontRuntimeSettings settings = currentSettings();
            int glyphSize = settings.getGlyphSize();
            int fontIndex = fontMatcher.matchFontIndex(runtimeVersion, anchorCodepoint, style.getFontType());
            if (fontIndex < 0) {
                return null;
            }
            Font font = fontMatcher.getDerivedFont(runtimeVersion, fontIndex, style.getFontType(), glyphSize);
            if (font == null) {
                return null;
            }
            LineMetrics metrics = font.getLineMetrics(text, FONT_RENDER_CONTEXT);
            float ascent = metrics.getAscent();
            float scale = (float) Math.max(1, segmentFontSizePx) / (float) Math.max(1, glyphSize);
            float layerStep = ascent * 0.5F * scale;  // 每层组合标记的上抬量（段字号坐标）

            int codePointCount = text.codePointCount(0, text.length());
            float[] result = new float[codePointCount * 2];
            float runningX = 0.0F;
            float baseCenterX = 0.0F;
            int upLayer = 0;
            int downLayer = 0;
            int codePointIndex = 0;
            for (int i = 0; i < text.length() && codePointIndex < codePointCount; ) {
                int codepoint = text.codePointAt(i);
                int charCount = Character.charCount(codepoint);
                if (UnicodeTextClassifier.isClusterContinuation(codepoint)) {
                    // 组合标记：吸附最近基字中心，按 CCC 方向逐层堆叠——
                    // 上方标记（Above 系）向上摞、下方标记（Below 系）向下摞（金字塔上下两支）。
                    result[codePointIndex * 2] = baseCenterX;
                    if (isBelowMark(codepoint)) {
                        downLayer++;
                        result[codePointIndex * 2 + 1] = layerStep * downLayer;
                    } else {
                        upLayer++;
                        result[codePointIndex * 2 + 1] = -(layerStep * upLayer);
                    }
                } else {
                    double advance = measureCodepointWidth(codepoint, style.getFontType(), segmentFontSizePx);
                    result[codePointIndex * 2] = runningX;
                    result[codePointIndex * 2 + 1] = 0.0F;
                    baseCenterX = runningX + (float) advance / 2.0F;
                    runningX += advance;
                    upLayer = 0;
                    downLayer = 0;
                }
                codePointIndex++;
                i += charCount;
            }
            return result;
        } finally {
            unlockGeneration();
        }
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
            text = normalizeNfc(text);
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
     * NFC 规范化（组合附加符堆叠挡 1）：显示/换行/裁剪/链接路径统一把
     * 「基字 + 组合标记」序列合并为预组合字符（如 {@code e + U+0301 → é}），
     * 使常见重音字符在无 mark 定位渲染的字体链上也能正确显示。
     *
     * <p>已规范化文本走 {@link Normalizer#isNormalized} 零分配快路径原样返回；
     * {@link #prefixWidthsRaw}（文本域 caret 几何）刻意不规范化——保持码点下标保真，
     * NFC 等价序列的 advance 不变，宽度口径仍然一致。</p>
     *
     * @param text 原始文本（可为 null）
     * @return NFC 规范化后的文本
     */
    private static String normalizeNfc(String text) {
        if (text == null || text.isEmpty() || Normalizer.isNormalized(text, Normalizer.Form.NFC)) {
            return text;
        }
        return Normalizer.normalize(text, Normalizer.Form.NFC);
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
