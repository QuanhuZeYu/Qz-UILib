package club.heiqi.uilib.font.util;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;

import club.heiqi.uilib.font.ActiveFontGeneration;
import club.heiqi.uilib.font.FontRuntimeAccess;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.FontRuntimeSettings;
import club.heiqi.uilib.font.config.FontCharacterRuleSet;
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
    private final Lock generationReadLock;
    private final Object ownerToken;
    private final LongAdder cacheHitCount = new LongAdder();
    private final LongAdder cacheMissCount = new LongAdder();
    private final int[] blockHintNormal = createHintArray();
    private final int[] blockHintBold = createHintArray();
    private volatile RuntimeTableBinding runtimeBinding;
    private volatile int lastNormalFontIndex = GlyphRuntimeTables.FONT_INDEX_UNRESOLVED;
    private volatile int lastBoldFontIndex = GlyphRuntimeTables.FONT_INDEX_UNRESOLVED;

    /**
     * 创建字体匹配器。
     *
     * @param fontCatalog 字体目录
     * @param derivedFontCache 派生字体缓存
     */
    public FontMatcher(FontCatalog fontCatalog, DerivedFontCache derivedFontCache) {
        this(fontCatalog, derivedFontCache, null);
    }

    /**
     * 创建仅在最终 cache publication 时进入 generation read barrier 的 matcher。
     *
     * @param fontCatalog legacy 字体目录
     * @param derivedFontCache legacy 派生字体缓存
     * @param generationReadLock generation 读锁
     */
    public FontMatcher(FontCatalog fontCatalog, DerivedFontCache derivedFontCache, Lock generationReadLock) {
        this(fontCatalog, derivedFontCache, generationReadLock, null);
    }

    /**
     * 创建绑定字体 singleton owner 的 matcher。
     *
     * @param fontCatalog 字体目录
     * @param derivedFontCache 派生字体缓存
     * @param generationReadLock generation 读锁
     * @param ownerToken 内部 owner token；独立测试对象可传 null
     */
    public FontMatcher(FontCatalog fontCatalog, DerivedFontCache derivedFontCache, Lock generationReadLock,
            Object ownerToken) {
        if (fontCatalog == null || derivedFontCache == null) {
            throw new IllegalArgumentException("字体 matcher 依赖不得为 null");
        }
        this.fontCatalog = fontCatalog;
        this.derivedFontCache = derivedFontCache;
        this.generationReadLock = generationReadLock;
        this.ownerToken = ownerToken;
        this.runtimeBinding = new RuntimeTableBinding(0, null, fontCatalog.snapshot(),
                FontRuntimeSettings.capture(), derivedFontCache, null);
    }

    /**
     * 绑定当前字体运行时直索引表。
     *
     * @param runtimeVersion 运行时版本
     * @param runtimeTables 运行时表
     */
    public void setRuntimeTables(int runtimeVersion, GlyphRuntimeTables runtimeTables) {
        assertRuntimeAccess();
        runtimeBinding = new RuntimeTableBinding(runtimeVersion, runtimeTables, fontCatalog.snapshot(),
                FontRuntimeSettings.capture(), derivedFontCache, null);
    }

    /**
     * 原子绑定一个完整字体 generation。
     *
     * @param generation active generation
     * @param runtimeTables generation 的唯一 direct tables
     * @param generationDerivedFontCache generation 派生字体缓存
     */
    public void setGeneration(ActiveFontGeneration generation, GlyphRuntimeTables runtimeTables,
            DerivedFontCache generationDerivedFontCache) {
        assertRuntimeAccess();
        if (generation == null || runtimeTables == null || generationDerivedFontCache == null) {
            throw new IllegalArgumentException("generation binding 成员不得为 null");
        }
        runtimeBinding = new RuntimeTableBinding(generation.getRuntimeVersion(), runtimeTables,
                generation.getCatalogSnapshot(), generation.getSettings(), generationDerivedFontCache,
                generation);
    }

    /**
     * 从与 matcher binding 相同的 catalog snapshot 取得派生字体。
     *
     * @param runtimeVersion 运行时版本
     * @param fontIndex 字体索引
     * @param fontType 字重
     * @param glyphSize 字形格大小
     * @return 派生字体；stale runtime 返回 null
     */
    public Font getDerivedFont(int runtimeVersion, int fontIndex, FontType fontType, int glyphSize) {
        RuntimeTableBinding binding = runtimeBinding;
        if (runtimeVersion != binding.runtimeVersion || binding.generation != null && !binding.generation.isActive()) {
            return null;
        }
        return binding.derivedFontCache.getDerivedFont(binding.catalogSnapshot, fontIndex, fontType, glyphSize);
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
        if (runtimeVersion != binding.runtimeVersion || !GlyphRuntimeTables.isValidCodepoint(codepoint)) {
            cacheMissCount.increment();
            return null;
        }
        GlyphRuntimeTables tables = binding.runtimeTables;
        if (!canUseRuntimeTables(runtimeVersion, binding)) {
            cacheMissCount.increment();
            return resolveFontWithoutCache(runtimeVersion, binding, codepoint, fontType);
        }

        int[] matchedFonts = tables.matchedFontArray(fontType);
        int cachedFontIndex = matchedFonts[codepoint];
        if (cachedFontIndex >= 0) {
            Font cachedFont = binding.catalogSnapshot.getFont(cachedFontIndex);
            if (cachedFont != null) {
                cacheHitCount.increment();
                return cachedFont;
            }
        } else if (cachedFontIndex == GlyphRuntimeTables.FONT_INDEX_NONE) {
            cacheHitCount.increment();
            return null;
        }
        cacheMissCount.increment();

        int matchedFontIndex = resolveFontIndex(runtimeVersion, binding, codepoint, fontType);
        writeMatchedFont(binding, runtimeVersion, codepoint, fontType, matchedFontIndex);
        if (matchedFontIndex >= 0) {
            return binding.catalogSnapshot.getFont(matchedFontIndex);
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
        if (runtimeVersion != binding.runtimeVersion || !GlyphRuntimeTables.isValidCodepoint(codepoint)) {
            cacheMissCount.increment();
            return GlyphRuntimeTables.FONT_INDEX_NONE;
        }
        GlyphRuntimeTables tables = binding.runtimeTables;
        if (!canUseRuntimeTables(runtimeVersion, binding)) {
            cacheMissCount.increment();
            return resolveFontIndex(runtimeVersion, binding, codepoint, fontType);
        }
        int[] matchedFonts = tables.matchedFontArray(fontType);
        int cachedFontIndex = matchedFonts[codepoint];
        if (cachedFontIndex != GlyphRuntimeTables.FONT_INDEX_UNRESOLVED) {
            cacheHitCount.increment();
            return cachedFontIndex;
        }
        cacheMissCount.increment();
        int matchedFontIndex = resolveFontIndex(runtimeVersion, binding, codepoint, fontType);
        writeMatchedFont(binding, runtimeVersion, codepoint, fontType, matchedFontIndex);
        return matchedFontIndex;
    }

    private Font resolveFontWithoutCache(int runtimeVersion, RuntimeTableBinding binding, int codepoint,
            FontType fontType) {
        int fontIndex = resolveFontIndex(runtimeVersion, binding, codepoint, fontType);
        return fontIndex >= 0 ? binding.catalogSnapshot.getFont(fontIndex) : null;
    }

    private int resolveFontIndex(int runtimeVersion, RuntimeTableBinding binding, int codepoint, FontType fontType) {
        FontCatalog.Snapshot snapshot = binding.catalogSnapshot;
        List<Font> fonts = snapshot.getFonts();
        if (fonts.isEmpty()) {
            rememberMatch(runtimeVersion, binding, codepoint, fontType, GlyphRuntimeTables.FONT_INDEX_NONE);
            return GlyphRuntimeTables.FONT_INDEX_NONE;
        }

        int configuredFontIndex = resolveConfiguredFontIndex(binding, codepoint, fontType);
        if (configuredFontIndex >= 0) {
            rememberMatch(runtimeVersion, binding, codepoint, fontType, configuredFontIndex);
            return configuredFontIndex;
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
                    ? canUseHint(binding, index, font, codepoint, fontType, text)
                    : canDisplay(binding, index, font, codepoint, fontType, text);
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

    private int resolveConfiguredFontIndex(RuntimeTableBinding binding, int codepoint, FontType fontType) {
        FontCatalog.Snapshot snapshot = binding.catalogSnapshot;
        FontCharacterRuleSet ruleSet = binding.settings.getCharacterRuleSet();
        if (ruleSet.isEmpty()) {
            return GlyphRuntimeTables.FONT_INDEX_UNRESOLVED;
        }
        String configuredFontName = ruleSet.resolveFontName(codepoint);
        if (configuredFontName == null || configuredFontName.trim().isEmpty()) {
            return GlyphRuntimeTables.FONT_INDEX_UNRESOLVED;
        }

        List<Font> fonts = snapshot.getFonts();
        String text = CodepointTextCache.getText(codepoint);
        String lookupKey = normalizeFontName(configuredFontName);
        int firstStrictDisplayIndex = GlyphRuntimeTables.FONT_INDEX_NONE;
        int firstCanDisplayIndex = GlyphRuntimeTables.FONT_INDEX_NONE;
        for (int index = 0; index < fonts.size(); index++) {
            Font font = fonts.get(index);
            if (!matchesConfiguredFontName(font, lookupKey) || !font.canDisplay(codepoint)) {
                continue;
            }
            if (firstCanDisplayIndex == GlyphRuntimeTables.FONT_INDEX_NONE) {
                firstCanDisplayIndex = index;
            }
            if (!canDisplay(binding, index, font, codepoint, fontType, text)) {
                continue;
            }
            if (matchesWeight(font, fontType)) {
                return index;
            }
            if (firstStrictDisplayIndex == GlyphRuntimeTables.FONT_INDEX_NONE) {
                firstStrictDisplayIndex = index;
            }
        }
        return firstStrictDisplayIndex >= 0 ? firstStrictDisplayIndex : firstCanDisplayIndex;
    }

    /**
     * 清空匹配缓存。
     */
    public void clearCache() {
        assertRuntimeAccess();
        // generation barrier 已先原地清空共享 runtimeTables；这里仅清 matcher 自有 hints 与计数器。
        Arrays.fill(blockHintNormal, GlyphRuntimeTables.FONT_INDEX_UNRESOLVED);
        Arrays.fill(blockHintBold, GlyphRuntimeTables.FONT_INDEX_UNRESOLVED);
        lastNormalFontIndex = GlyphRuntimeTables.FONT_INDEX_UNRESOLVED;
        lastBoldFontIndex = GlyphRuntimeTables.FONT_INDEX_UNRESOLVED;
        cacheHitCount.reset();
        cacheMissCount.reset();
    }

    /**
     * 获取缓存命中次数。
     *
     * @return 命中次数
     */
    public long getCacheHitCount() {
        return cacheHitCount.sum();
    }

    /**
     * 获取缓存未命中次数。
     *
     * @return 未命中次数
     */
    public long getCacheMissCount() {
        return cacheMissCount.sum();
    }

    private boolean matchesWeight(Font font, FontType fontType) {
        String fontName = font.getName();
        boolean isBoldFont = font.isBold() || fontName != null && fontName.toLowerCase().contains("bold");
        if (fontType == FontType.BOLD) {
            return isBoldFont;
        }
        return !isBoldFont;
    }

    private boolean matchesConfiguredFontName(Font font, String lookupKey) {
        if (font == null || lookupKey == null || lookupKey.isEmpty()) {
            return false;
        }
        return normalizeFontName(font.getName()).equals(lookupKey);
    }

    private String normalizeFontName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
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

    private boolean canUseHint(RuntimeTableBinding binding, int fontIndex, Font font, int codepoint,
            FontType fontType, String text) {
        return fontIndex >= 0 && canDisplay(binding, fontIndex, font, codepoint, fontType, text);
    }

    private boolean canDisplay(RuntimeTableBinding binding, int fontIndex, Font font, int codepoint, FontType fontType,
            String text) {
        if (!font.canDisplay(codepoint)) {
            return false;
        }
        if (Character.isWhitespace(codepoint) || Character.isSpaceChar(codepoint)) {
            return true;
        }

        Font derivedFont = binding.derivedFontCache.getDerivedFont(binding.catalogSnapshot, fontIndex, fontType,
                binding.settings.getGlyphSize());
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
        lockGeneration();
        try {
            if (!canUseRuntimeTables(runtimeVersion, binding) || !GlyphRuntimeTables.isValidCodepoint(codepoint)) {
                return;
            }
            binding.runtimeTables.matchedFontArray(fontType)[codepoint] = fontIndex;
        } finally {
            unlockGeneration();
        }
    }

    private void rememberMatch(int runtimeVersion, RuntimeTableBinding binding, int codepoint, FontType fontType,
            int fontIndex) {
        lockGeneration();
        try {
            if (!canUseRuntimeTables(runtimeVersion, binding) || !GlyphRuntimeTables.isValidCodepoint(codepoint)) {
                return;
            }
            hintArray(fontType)[codepoint >> BLOCK_SHIFT] = fontIndex;
            if (fontType == FontType.BOLD) {
                lastBoldFontIndex = fontIndex;
            } else {
                lastNormalFontIndex = fontIndex;
            }
        } finally {
            unlockGeneration();
        }
    }

    private int[] hintArray(FontType fontType) {
        return fontType == FontType.BOLD ? blockHintBold : blockHintNormal;
    }

    private boolean canUseRuntimeTables(int runtimeVersion, RuntimeTableBinding binding) {
        return binding.runtimeTables != null && runtimeVersion == binding.runtimeVersion && binding == runtimeBinding
                && (binding.generation == null || binding.generation.isActive());
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

    private void assertRuntimeAccess() {
        if (!FontRuntimeAccess.isActive(ownerToken)) {
            throw new IllegalStateException("FontMatcher 只能由字体 runtime owner 修改 generation binding");
        }
    }

    private static int[] createHintArray() {
        int[] hints = new int[BLOCK_COUNT];
        Arrays.fill(hints, GlyphRuntimeTables.FONT_INDEX_UNRESOLVED);
        return hints;
    }

    private static final class RuntimeTableBinding {

        private final int runtimeVersion;
        private final GlyphRuntimeTables runtimeTables;
        private final FontCatalog.Snapshot catalogSnapshot;
        private final FontRuntimeSettings settings;
        private final DerivedFontCache derivedFontCache;
        private final ActiveFontGeneration generation;

        private RuntimeTableBinding(int runtimeVersion, GlyphRuntimeTables runtimeTables,
                FontCatalog.Snapshot catalogSnapshot, FontRuntimeSettings settings,
                DerivedFontCache derivedFontCache, ActiveFontGeneration generation) {
            this.runtimeVersion = runtimeVersion;
            this.runtimeTables = runtimeTables;
            this.catalogSnapshot = catalogSnapshot;
            this.settings = settings;
            this.derivedFontCache = derivedFontCache;
            this.generation = generation;
        }
    }

}
