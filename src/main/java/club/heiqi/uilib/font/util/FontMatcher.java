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
import club.heiqi.uilib.font.config.FontFaceAssignment;
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
    /** 每字面一份块级提示；索引 = {@code FontType.ordinal()}。 */
    private final int[][] blockHints = createHintArrays();
    private volatile RuntimeTableBinding runtimeBinding;
    /** 每字面一份最近命中字体索引；索引 = {@code FontType.ordinal()}。 */
    private final int[] lastFontIndex = createLastFontIndexArray();

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

        int assignedFontIndex = resolveAssignedFontIndex(binding, codepoint, fontType);
        if (assignedFontIndex >= 0) {
            rememberMatch(runtimeVersion, binding, codepoint, fontType, assignedFontIndex);
            return assignedFontIndex;
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
     * 按用户六槽字体指派解析字体索引（优先于通用字重匹配，次于显式字符规则）。
     *
     * <p>指派命中同一字体族的多个字面（normal/bold/italic 文件）时，优先返回字重/斜体
     * 精确匹配的字面；否则退到该族内首个可显示字面。</p>
     *
     * @return 目录索引；未指派或族内无可显示字面返回 {@link GlyphRuntimeTables#FONT_INDEX_UNRESOLVED}
     */
    private int resolveAssignedFontIndex(RuntimeTableBinding binding, int codepoint, FontType fontType) {
        FontFaceAssignment assignment = binding.settings.getFaceAssignment();
        if (assignment == null || assignment.isEmpty()) {
            return GlyphRuntimeTables.FONT_INDEX_UNRESOLVED;
        }
        String assignedName = assignment.familyFor(codepoint, fontType);
        if (assignedName == null || assignedName.trim().isEmpty()) {
            return GlyphRuntimeTables.FONT_INDEX_UNRESOLVED;
        }
        List<Font> fonts = binding.catalogSnapshot.getFonts();
        String text = CodepointTextCache.getText(codepoint);
        String lookupKey = normalizeFontName(assignedName);
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
        int resolved = firstStrictDisplayIndex >= 0 ? firstStrictDisplayIndex : firstCanDisplayIndex;
        return resolved;
    }

    /**
     * 清空匹配缓存。
     */
    public void clearCache() {
        assertRuntimeAccess();
        // generation barrier 已先原地清空共享 runtimeTables；这里仅清 matcher 自有 hints 与计数器。
        for (int[] hints : blockHints) {
            Arrays.fill(hints, GlyphRuntimeTables.FONT_INDEX_UNRESOLVED);
        }
        Arrays.fill(lastFontIndex, GlyphRuntimeTables.FONT_INDEX_UNRESOLVED);
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

    /**
     * 字面精确匹配：字重（bold）与斜体（italic）两维都与目标字面一致。
     *
     * <p>斜体允许「无真斜体字面」时由 {@link #resolveFontIndex} 的 strict/fallback 分支退到
     * 非斜体字面，再由 {@link DerivedFontCache} 合成倾斜；因此这里只做「是否有更精确字面」的判据。</p>
     */
    private boolean matchesWeight(Font font, FontType fontType) {
        String fontName = font.getName();
        String lower = fontName == null ? "" : fontName.toLowerCase(Locale.ENGLISH);
        boolean isBoldFont = font.isBold() || lower.contains("bold") || lower.contains("black")
                || lower.contains("heavy") || lower.contains("semibold");
        boolean isItalicFont = font.isItalic() || lower.contains("italic") || lower.contains("oblique");
        boolean wantBold = fontType != null && fontType.isBold();
        boolean wantItalic = fontType != null && fontType.isItalic();
        return isBoldFont == wantBold && isItalicFont == wantItalic;
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
        return lastFontIndex[fontType == null ? 0 : fontType.ordinal()];
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
            lastFontIndex[fontType == null ? 0 : fontType.ordinal()] = fontIndex;
        } finally {
            unlockGeneration();
        }
    }

    private int[] hintArray(FontType fontType) {
        return blockHints[fontType == null ? 0 : fontType.ordinal()];
    }

    private boolean canUseRuntimeTables(int runtimeVersion, RuntimeTableBinding binding) {
        return binding.runtimeTables != null && runtimeVersion == binding.runtimeVersion && binding == runtimeBinding
                && (binding.generation == null || binding.generation.isActive());
    }

    /** 捕获指定 generation 的不可变物理字体目录；过期请求不转向当前字体。 */
    public FontCatalog.Snapshot getCatalogSnapshot(int runtimeVersion) {
        lockGeneration();
        try {
            RuntimeTableBinding binding = runtimeBinding;
            return isCurrentGeneration(runtimeVersion, binding) ? binding.catalogSnapshot : null;
        } finally {
            unlockGeneration();
        }
    }

    /** 与目录同代的固定 raster/atlas 设置；调用方保留 token 进入最终发布屏障。 */
    public FontRuntimeSettings getRuntimeSettings(int runtimeVersion) {
        lockGeneration();
        try {
            RuntimeTableBinding binding = runtimeBinding;
            return isCurrentGeneration(runtimeVersion, binding) ? binding.settings : null;
        } finally {
            unlockGeneration();
        }
    }

    private boolean isCurrentGeneration(int runtimeVersion, RuntimeTableBinding binding) {
        return runtimeVersion == binding.runtimeVersion && binding == runtimeBinding
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

    private static int[][] createHintArrays() {
        FontType[] types = FontType.values();
        int[][] arrays = new int[types.length][];
        for (int index = 0; index < types.length; index++) {
            arrays[index] = new int[BLOCK_COUNT];
            Arrays.fill(arrays[index], GlyphRuntimeTables.FONT_INDEX_UNRESOLVED);
        }
        return arrays;
    }

    private static int[] createLastFontIndexArray() {
        int[] values = new int[FontType.values().length];
        Arrays.fill(values, GlyphRuntimeTables.FONT_INDEX_UNRESOLVED);
        return values;
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
