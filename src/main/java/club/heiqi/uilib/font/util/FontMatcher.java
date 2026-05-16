package club.heiqi.uilib.font.util;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.page.GlyphRuntimeTables;

/**
 * 字体匹配器。
 */
public class FontMatcher {

    private static final FontRenderContext FONT_RENDER_CONTEXT = new FontRenderContext(new AffineTransform(), true, true);
    private static final int BLOCK_SHIFT = 8;
    private static final int BLOCK_COUNT = (GlyphRuntimeTables.CODEPOINT_COUNT + (1 << BLOCK_SHIFT) - 1) >> BLOCK_SHIFT;

    private final FontCatalog fontCatalog;
    private final DerivedFontCache derivedFontCache;
    private final AtomicLong cacheHitCount = new AtomicLong(0L);
    private final AtomicLong cacheMissCount = new AtomicLong(0L);
    private final int[] blockHintNormal = createHintArray();
    private final int[] blockHintBold = createHintArray();
    private volatile RuntimeTableBinding runtimeBinding = new RuntimeTableBinding(0, null);
    private volatile int lastNormalFontIndex = GlyphRuntimeTables.FONT_INDEX_UNRESOLVED;
    private volatile int lastBoldFontIndex = GlyphRuntimeTables.FONT_INDEX_UNRESOLVED;

    /**
     * 创建字体匹配器。
     *
     * @param fontCatalog 字体目录
     * @param derivedFontCache 派生字体缓存
     */
    public FontMatcher(FontCatalog fontCatalog, DerivedFontCache derivedFontCache) {
        this.fontCatalog = fontCatalog;
        this.derivedFontCache = derivedFontCache;
    }

    /**
     * 绑定当前字体运行时直索引表。
     *
     * @param runtimeVersion 运行时版本
     * @param runtimeTables 运行时表
     */
    public void setRuntimeTables(int runtimeVersion, GlyphRuntimeTables runtimeTables) {
        runtimeBinding = new RuntimeTableBinding(runtimeVersion, runtimeTables);
    }

    /**
     * 绑定当前字体运行时直索引表。
     *
     * @param runtimeTables 运行时表
     */
    public void setRuntimeTables(GlyphRuntimeTables runtimeTables) {
        setRuntimeTables(runtimeBinding.runtimeVersion, runtimeTables);
    }

    /**
     * 匹配适合指定字符的字体。
     *
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @return 匹配到的字体，未匹配到则返回 null
     */
    public Font match(int runtimeVersion, int codepoint, FontType fontType) {
        RuntimeTableBinding binding = runtimeBinding;
        GlyphRuntimeTables tables = binding.runtimeTables;
        if (!canUseRuntimeTables(runtimeVersion, binding) || !GlyphRuntimeTables.isValidCodepoint(codepoint)) {
            cacheMissCount.incrementAndGet();
            return resolveFontWithoutCache(runtimeVersion, binding, codepoint, fontType);
        }

        int[] matchedFonts = tables.matchedFontArray(fontType);
        int cachedFontIndex = matchedFonts[codepoint];
        if (cachedFontIndex >= 0) {
            Font cachedFont = fontCatalog.getFont(cachedFontIndex);
            if (cachedFont != null) {
                cacheHitCount.incrementAndGet();
                return cachedFont;
            }
        } else if (cachedFontIndex == GlyphRuntimeTables.FONT_INDEX_NONE) {
            cacheHitCount.incrementAndGet();
            return null;
        }
        cacheMissCount.incrementAndGet();

        int matchedFontIndex = resolveFontIndex(runtimeVersion, binding, codepoint, fontType);
        writeMatchedFont(binding, runtimeVersion, codepoint, fontType, matchedFontIndex);
        if (matchedFontIndex >= 0) {
            return fontCatalog.getFont(matchedFontIndex);
        }
        return null;
    }

    /**
     * 按目录索引返回已匹配字体。
     *
     * @param runtimeVersion 运行时版本
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @return 字体目录索引，未匹配时返回 {@link GlyphRuntimeTables#FONT_INDEX_NONE}
     */
    public int matchFontIndex(int runtimeVersion, int codepoint, FontType fontType) {
        RuntimeTableBinding binding = runtimeBinding;
        GlyphRuntimeTables tables = binding.runtimeTables;
        if (!canUseRuntimeTables(runtimeVersion, binding) || !GlyphRuntimeTables.isValidCodepoint(codepoint)) {
            cacheMissCount.incrementAndGet();
            return resolveFontIndex(runtimeVersion, binding, codepoint, fontType);
        }
        int[] matchedFonts = tables.matchedFontArray(fontType);
        int cachedFontIndex = matchedFonts[codepoint];
        if (cachedFontIndex != GlyphRuntimeTables.FONT_INDEX_UNRESOLVED) {
            cacheHitCount.incrementAndGet();
            return cachedFontIndex;
        }
        cacheMissCount.incrementAndGet();
        int matchedFontIndex = resolveFontIndex(runtimeVersion, binding, codepoint, fontType);
        writeMatchedFont(binding, runtimeVersion, codepoint, fontType, matchedFontIndex);
        return matchedFontIndex;
    }

    private Font resolveFontWithoutCache(int runtimeVersion, RuntimeTableBinding binding, int codepoint,
            FontType fontType) {
        int fontIndex = resolveFontIndex(runtimeVersion, binding, codepoint, fontType);
        return fontIndex >= 0 ? fontCatalog.getFont(fontIndex) : null;
    }

    private int resolveFontIndex(int runtimeVersion, RuntimeTableBinding binding, int codepoint, FontType fontType) {
        FontCatalog.Snapshot snapshot = fontCatalog.snapshot();
        List<Font> fonts = snapshot.getFonts();
        if (fonts.isEmpty()) {
            rememberMatch(runtimeVersion, binding, codepoint, fontType, GlyphRuntimeTables.FONT_INDEX_NONE);
            return GlyphRuntimeTables.FONT_INDEX_NONE;
        }

        String text = CodepointTextCache.getText(codepoint);
        int blockHint = resolveBlockHint(runtimeVersion, binding, codepoint, fontType);
        int lastHint = resolveLastHint(runtimeVersion, binding, fontType);

        int firstStrictDisplayIndex = GlyphRuntimeTables.FONT_INDEX_NONE;
        int firstCanDisplayIndex = GlyphRuntimeTables.FONT_INDEX_NONE;
        for (int index = 0; index < fonts.size(); index++) {
            Font font = fonts.get(index);
            if (!font.canDisplay(codepoint)) {
                continue;
            }
            if (firstCanDisplayIndex == GlyphRuntimeTables.FONT_INDEX_NONE) {
                firstCanDisplayIndex = index;
            }
            boolean strictDisplay = isHintCandidate(index, blockHint, lastHint)
                    ? canUseHint(index, font, codepoint, fontType, text)
                    : canDisplay(index, font, codepoint, fontType, text);
            if (!strictDisplay) {
                continue;
            }
            if (matchesWeight(font, fontType)) {
                rememberMatch(runtimeVersion, binding, codepoint, fontType, index);
                return index;
            }
            if (firstStrictDisplayIndex == GlyphRuntimeTables.FONT_INDEX_NONE) {
                firstStrictDisplayIndex = index;
            }
        }
        int fallbackIndex = firstStrictDisplayIndex >= 0 ? firstStrictDisplayIndex : firstCanDisplayIndex;
        rememberMatch(runtimeVersion, binding, codepoint, fontType, fallbackIndex);
        return fallbackIndex;
    }

    /**
     * 清空匹配缓存。
     */
    public void clearCache() {
        GlyphRuntimeTables tables = runtimeBinding.runtimeTables;
        if (tables != null) {
            tables.clearMatchedFontCache();
        }
        Arrays.fill(blockHintNormal, GlyphRuntimeTables.FONT_INDEX_UNRESOLVED);
        Arrays.fill(blockHintBold, GlyphRuntimeTables.FONT_INDEX_UNRESOLVED);
        lastNormalFontIndex = GlyphRuntimeTables.FONT_INDEX_UNRESOLVED;
        lastBoldFontIndex = GlyphRuntimeTables.FONT_INDEX_UNRESOLVED;
        cacheHitCount.set(0L);
        cacheMissCount.set(0L);
    }

    /**
     * 获取缓存命中次数。
     *
     * @return 命中次数
     */
    public long getCacheHitCount() {
        return cacheHitCount.get();
    }

    /**
     * 获取缓存未命中次数。
     *
     * @return 未命中次数
     */
    public long getCacheMissCount() {
        return cacheMissCount.get();
    }

    private boolean matchesWeight(Font font, FontType fontType) {
        String fontName = font.getName();
        boolean isBoldFont = font.isBold() || fontName != null && fontName.toLowerCase().contains("bold");
        if (fontType == FontType.BOLD) {
            return isBoldFont;
        }
        return !isBoldFont;
    }

    private int resolveBlockHint(int runtimeVersion, RuntimeTableBinding binding, int codepoint, FontType fontType) {
        if (!canUseRuntimeTables(runtimeVersion, binding) || !GlyphRuntimeTables.isValidCodepoint(codepoint)) {
            return GlyphRuntimeTables.FONT_INDEX_UNRESOLVED;
        }
        return hintArray(fontType)[codepoint >> BLOCK_SHIFT];
    }

    private int resolveLastHint(int runtimeVersion, RuntimeTableBinding binding, FontType fontType) {
        if (!canUseRuntimeTables(runtimeVersion, binding)) {
            return GlyphRuntimeTables.FONT_INDEX_UNRESOLVED;
        }
        return fontType == FontType.BOLD ? lastBoldFontIndex : lastNormalFontIndex;
    }

    private boolean isHintCandidate(int fontIndex, int blockHint, int lastHint) {
        return fontIndex == blockHint || fontIndex == lastHint;
    }

    private boolean canUseHint(int fontIndex, Font font, int codepoint, FontType fontType, String text) {
        return fontIndex >= 0 && canDisplay(fontIndex, font, codepoint, fontType, text);
    }

    private boolean canDisplay(int fontIndex, Font font, int codepoint, FontType fontType, String text) {
        if (!font.canDisplay(codepoint)) {
            return false;
        }

        Font derivedFont = derivedFontCache.getDerivedFont(fontIndex, fontType, currentGlyphSize());
        if (derivedFont == null) {
            return false;
        }
        GlyphVector glyphVector = derivedFont.createGlyphVector(FONT_RENDER_CONTEXT, text);
        int glyphCode = glyphVector.getGlyphCode(0);
        if (glyphCode == 0 || glyphCode == derivedFont.getMissingGlyphCode()) {
            return false;
        }
        return glyphVector.getGlyphOutline(0) != null;
    }

    private void writeMatchedFont(RuntimeTableBinding binding, int runtimeVersion, int codepoint, FontType fontType,
            int fontIndex) {
        if (!canUseRuntimeTables(runtimeVersion, binding) || !GlyphRuntimeTables.isValidCodepoint(codepoint)) {
            return;
        }
        binding.runtimeTables.matchedFontArray(fontType)[codepoint] = fontIndex;
    }

    private void rememberMatch(int runtimeVersion, RuntimeTableBinding binding, int codepoint, FontType fontType,
            int fontIndex) {
        if (!canUseRuntimeTables(runtimeVersion, binding) || !GlyphRuntimeTables.isValidCodepoint(codepoint)) {
            return;
        }
        hintArray(fontType)[codepoint >> BLOCK_SHIFT] = fontIndex;
        if (fontType == FontType.BOLD) {
            lastBoldFontIndex = fontIndex;
        } else {
            lastNormalFontIndex = fontIndex;
        }
    }

    private int[] hintArray(FontType fontType) {
        return fontType == FontType.BOLD ? blockHintBold : blockHintNormal;
    }

    private boolean canUseRuntimeTables(int runtimeVersion, RuntimeTableBinding binding) {
        return binding.runtimeTables != null && runtimeVersion == binding.runtimeVersion && binding == runtimeBinding;
    }

    private int currentGlyphSize() {
        return Math.max(8, (int) Math.ceil(FontConfig.awtCharSize));
    }

    private static int[] createHintArray() {
        int[] hints = new int[BLOCK_COUNT];
        Arrays.fill(hints, GlyphRuntimeTables.FONT_INDEX_UNRESOLVED);
        return hints;
    }

    private static final class RuntimeTableBinding {

        private final int runtimeVersion;
        private final GlyphRuntimeTables runtimeTables;

        private RuntimeTableBinding(int runtimeVersion, GlyphRuntimeTables runtimeTables) {
            this.runtimeVersion = runtimeVersion;
            this.runtimeTables = runtimeTables;
        }
    }

}
