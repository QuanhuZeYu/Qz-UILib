package club.heiqi.uilib.font.layout;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;

import club.heiqi.uilib.font.ActiveFontGeneration;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.latex.LatexNode;
import club.heiqi.uilib.font.latex.LatexParser;
import club.heiqi.uilib.font.latex.layout.MathBox;
import club.heiqi.uilib.font.latex.layout.MathLayoutService;
import club.heiqi.uilib.font.latex.layout.MathMetrics;
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

    /** 数学布局引擎（无状态，公式宽度度量与渲染共用）。 */
    private static final MathLayoutService MATH_LAYOUT = new MathLayoutService();

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
     * 组合标记堆叠方向（对齐 CCC 完整语义与 GPOS 近似）：
     * <ul>
     *   <li>{@code -1}：下方（Below 系 220/202/200/218/222/233/240、Nukta 7、Virama 9）；</li>
     *   <li>{@code 0}：原位覆盖（Overlay 1、包围标记 Me——居中覆盖基字不偏移）；</li>
     *   <li>{@code 1}：上方（Above 系 230/216/232/234 与无 CCC 默认）；</li>
     *   <li>{@code 2}：右上（Kana Voicing 8，假名浊点）。</li>
     * </ul>
     *
     * @param codepoint Unicode 码点
     * @return 堆叠方向编码
     */
    private static int markStackDirection(int codepoint) {
        if (Character.getType(codepoint) == Character.ENCLOSING_MARK) {
            return 0; // 包围标记：居中覆盖
        }
        int ccc = combiningClass(codepoint);
        switch (ccc) {
            case 1:
                return 0; // Overlay：原位覆盖
            case 7:
            case 9:
                return -1; // Nukta / Virama：下方
            case 8:
                return 2; // Kana Voicing：右上
            case 220:
            case 202:
            case 200:
            case 218: // Below Left Attached
            case 222: // Below Right Attached
            case 233:
            case 240:
                return -1; // Below 系：下方
            default:
                break;
        }
        if (ccc != 0) {
            return 1; // Above 系（230/216/232/234 等）：上方
        }
        // CCC 不可用（JDK9+ 无 sun.text.Normalizer）或为 0（泰语等无 CCC 脚本）：
        // 回落码点白名单（Overlay/假名浊点/Nukta/Virama/下方系常用区间）。
        return markDirectionFallback(codepoint);
    }

    /**
     * CCC 缺失/为 0 环境下的方向白名单：Overlay 原位、假名浊点右上、
     * Nukta/Virama 与下方系向下，其余向上。
     *
     * @param codepoint Unicode 码点
     * @return 堆叠方向编码（-1/0/1/2）
     */
    private static int markDirectionFallback(int codepoint) {
        if (codepoint >= 0x0334 && codepoint <= 0x0338) {
            return 0; // Overlay 系（组合短横/斜线等覆盖线）
        }
        if (codepoint == 0x3099 || codepoint == 0x309A) {
            return 2; // 假名浊点/半浊点（右上）
        }
        if (isNuktaOrViramaCodepoint(codepoint) || isBelowMarkCodepoint(codepoint)) {
            return -1;
        }
        return 1;
    }

    /** 常用印度文字 Nukta/Virama 码点（CCC 7/9，CCC 缺失环境的方向白名单）。 */
    private static boolean isNuktaOrViramaCodepoint(int codepoint) {
        switch (codepoint) {
            case 0x093C: case 0x09BC: case 0x0A3C: case 0x0ABC: case 0x0B3C: case 0x0CBC:
            case 0x094D: case 0x09CD: case 0x0A4D: case 0x0ACD: case 0x0B4D: case 0x0BCD:
            case 0x0C4D: case 0x0D4D: case 0x0E3A:
                return true;
            default:
                return false;
        }
    }

    /**
     * 常用下方附着标记码点白名单（CCC 缺失/为 0 环境下的方向回落）。
     *
     * @param codepoint Unicode 码点
     * @return true 表示下方附着
     */
    private static boolean isBelowMarkCodepoint(int codepoint) {
        int ccc = combiningClass(codepoint);
        if (ccc != 0) {
            return false;
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

    /**
     * 取组合标记的 ink 高度（font 坐标 → 段字号坐标）；字形缺失时回落 1/4 ascent 默认层高。
     *
     * <p>层距用每个标记自身的 ink 高度（贴字形紧实堆叠），而非固定行高比例——
     * 这正是与浏览器观感一致的关键：网页 mark-to-mark 是贴着上一层 ink 摞的。</p>
     *
     * @param glyphVector    段落 glyph 向量（与文本码点序 1:1）
     * @param codePointIndex 标记在段落中的码点序号
     * @param ascent         字体 ascent（font 坐标）
     * @param scale          font 坐标 → 段字号坐标换算
     * @return ink 高度（段字号坐标，恒 > 0）
     */
    private static float resolveMarkInkHeight(java.awt.font.GlyphVector glyphVector, int codePointIndex,
            float ascent, float scale) {
        Rectangle2D bounds = safeGlyphBounds(glyphVector, codePointIndex);
        double height = bounds.getHeight();
        if (height <= 0.0D) {
            return ascent * 0.25F * scale;
        }
        return (float) height * scale;
    }

    /**
     * 下方标记第一层的 origin y：把标记 ink 顶贴到 baseline（ink 在 origin 上方时 inkTop 为负）。
     *
     * @param glyphVector    段落 glyph 向量
     * @param codePointIndex 标记码点序号
     * @param scale          font 坐标 → 段字号坐标换算
     * @return 第一层下方标记的 origin y（段字号坐标，>= 0）
     */
    private static float resolveBelowInkTop(java.awt.font.GlyphVector glyphVector, int codePointIndex,
            float scale) {
        Rectangle2D bounds = safeGlyphBounds(glyphVector, codePointIndex);
        if (bounds.getHeight() <= 0.0D) {
            return 0.0F;
        }
        // ink 顶相对 origin 为负（isolated mark 布局 ink 在 origin 上方）→ 取反贴 baseline
        return (float) Math.max(0.0D, -bounds.getY()) * scale;
    }

    /** 安全取 glyph visual bounds（越界/空字形返回空矩形，防 NPE 与异常几何）。 */
    private static Rectangle2D safeGlyphBounds(java.awt.font.GlyphVector glyphVector, int codePointIndex) {
        try {
            if (codePointIndex >= 0 && codePointIndex < glyphVector.getNumGlyphs()) {
                return glyphVector.getGlyphVisualBounds(codePointIndex).getBounds2D();
            }
        } catch (Exception ignored) {
            // 回落空矩形
        }
        return new Rectangle2D.Float();
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
            if (segment.isLatex()) {
                TextStyle style = segment.getStyle();
                int effectiveSize = style == null ? 0
                        : style.resolveEffectiveFontSizePx((int) currentSettings().getCharSize());
                return measureLatexWidth(segment.getLatexSource(), style, Math.max(1, effectiveSize));
            }
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
            if (segment.isLatex()) {
                TextStyle style = segment.getStyle();
                int effectiveSize = style == null ? fontSizePx : style.resolveEffectiveFontSizePx(fontSizePx);
                return measureLatexWidth(segment.getLatexSource(), style, Math.max(1, effectiveSize));
            }
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
     * 度量 LaTeX 公式宽度（解析 + 布局；渲染侧 {@code DefaultFontRendererAdapter} 同口径）。
     */
    private double measureLatexWidth(String latexSource, TextStyle style, int fontSizePx) {
        List<LatexNode> nodes = LatexParser.parse(latexSource);
        MathBox box = MATH_LAYOUT.layout(nodes, fontSizePx, createMathMetrics(style, fontSizePx));
        return box.getWidth();
    }

    /**
     * 构建数学布局度量注入（公式内文本与普通文本同口径：advance/ascent/descent 复用本服务）。
     *
     * @param style      段落样式（颜色/字体类别继承）
     * @param baseSizePx 公式正文字号
     * @return 度量实现
     */
    public MathMetrics createMathMetrics(final TextStyle style, final int baseSizePx) {
        return new MathMetrics() {
            @Override
            public float advance(String text, float sizePx) {
                double total = 0.0D;
                for (int i = 0; i < text.length(); ) {
                    int codepoint = text.codePointAt(i);
                    total += resolveCodepointAdvance(codepoint, style, Math.max(1, (int) sizePx));
                    i += Character.charCount(codepoint);
                }
                return (float) total;
            }

            @Override
            public float ascent(float sizePx) {
                return getAscent(Math.max(1, (int) sizePx));
            }

            @Override
            public float descent(float sizePx) {
                return getDescent(Math.max(1, (int) sizePx));
            }
        };
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
        if (cls == UnicodeTextClassifier.CharClass.CONTROL) {
            // Cc 控制字符按可见映射（Control Pictures/U+FFFD）测量（CSS3+ 口径）
            return measureCodepointWidth(UnicodeTextClassifier.controlPictureCodepoint(codepoint), fontType);
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
     * CCC 方向<b>紧实堆叠</b>——上方标记向上摞、下方标记向下摞，层距取每个标记自身
     * ink 高度（贴字形摞，与浏览器 mark-to-mark 观感一致，而非固定行高比例摊开）。</p>
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
            java.awt.font.GlyphVector glyphVector = font.createGlyphVector(FONT_RENDER_CONTEXT, text);

            int codePointCount = text.codePointCount(0, text.length());
            float[] result = new float[codePointCount * 2];
            float runningX = 0.0F;
            float baseCenterX = 0.0F;
            float baseAdvance = 0.0F;
            // 紧实堆叠游标：上方堆叠顶（负 y）与下方堆叠底（正 y），
            // 层距取每个标记自身 ink 高度（贴字形摞，不按固定行高比例摊开）。
            float upCursorY = -(ascent * 0.8F) * scale;
            float downCursorY = 0.0F;
            float prevUpInk = 0.0F;
            float prevDownInk = 0.0F;
            int upLayer = 0;
            int downLayer = 0;
            int codePointIndex = 0;
            for (int i = 0; i < text.length() && codePointIndex < codePointCount; ) {
                int codepoint = text.codePointAt(i);
                int charCount = Character.charCount(codepoint);
                if (UnicodeTextClassifier.isClusterContinuation(codepoint)) {
                    // 组合标记：按 CCC 完整语义定位——上方向上摞、下方向下摞（层距=本标记
                    // ink 高度贴字形）；Overlay/包围标记原位覆盖基字（y=0）；假名浊点右上。
                    int direction = markStackDirection(codepoint);
                    float inkHeight = resolveMarkInkHeight(glyphVector, codePointIndex, ascent, scale);
                    if (direction == 0) {
                        result[codePointIndex * 2] = baseCenterX;
                        result[codePointIndex * 2 + 1] = 0.0F;
                    } else if (direction < 0) {
                        if (downLayer == 0) {
                            downCursorY = resolveBelowInkTop(glyphVector, codePointIndex, scale);
                        } else {
                            downCursorY += prevDownInk;
                        }
                        downLayer++;
                        prevDownInk = inkHeight;
                        result[codePointIndex * 2] = baseCenterX;
                        result[codePointIndex * 2 + 1] = downCursorY;
                    } else {
                        if (upLayer == 0) {
                            upCursorY = -(ascent * 0.8F) * scale;
                        } else {
                            upCursorY -= prevUpInk;
                        }
                        upLayer++;
                        prevUpInk = inkHeight;
                        result[codePointIndex * 2] = direction == 2
                                ? baseCenterX + baseAdvance * 0.25F
                                : baseCenterX;
                        result[codePointIndex * 2 + 1] = upCursorY;
                    }
                } else {
                    double advance = measureCodepointWidth(codepoint, style.getFontType(), segmentFontSizePx);
                    result[codePointIndex * 2] = runningX;
                    result[codePointIndex * 2 + 1] = 0.0F;
                    baseCenterX = runningX + (float) advance / 2.0F;
                    baseAdvance = (float) advance;
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
     * {@link #prefixWidthsRaw}（文本域 caret 几何）刻意不规范化——保持码点下标保真。
     * 宽度口径的一致性依据是「组合标记测量零宽 + 预组合字形与基字同 advance 的字体惯例」
     * （注意：这不是 Unicode 不变式——NFC 本身改变码点数，UAX#15 不保证字符级
     * advance 之和相等）。</p>
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
