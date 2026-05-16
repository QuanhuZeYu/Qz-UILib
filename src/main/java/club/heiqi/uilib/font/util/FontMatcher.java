package club.heiqi.uilib.font.util;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.page.GlyphRuntimeTables;

/**
 * 字体匹配器。
 */
public class FontMatcher {

    private static final FontRenderContext FONT_RENDER_CONTEXT = new FontRenderContext(new AffineTransform(), true, true);

    private final FontCatalog fontCatalog;
    private final AtomicLong cacheHitCount = new AtomicLong(0L);
    private final AtomicLong cacheMissCount = new AtomicLong(0L);
    private volatile GlyphRuntimeTables runtimeTables;

    /**
     * 创建字体匹配器。
     *
     * @param fontCatalog 字体目录
     */
    public FontMatcher(FontCatalog fontCatalog) {
        this.fontCatalog = fontCatalog;
    }

    /**
     * 绑定当前字体运行时直索引表。
     *
     * @param runtimeTables 运行时表
     */
    public void setRuntimeTables(GlyphRuntimeTables runtimeTables) {
        this.runtimeTables = runtimeTables;
    }

    /**
     * 匹配适合指定字符的字体。
     *
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @return 匹配到的字体，未匹配到则返回 null
     */
    public Font match(int runtimeVersion, int codepoint, FontType fontType) {
        GlyphRuntimeTables tables = runtimeTables;
        if (tables == null || !GlyphRuntimeTables.isValidCodepoint(codepoint)) {
            cacheMissCount.incrementAndGet();
            return resolveFontWithoutCache(codepoint, fontType);
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

        int matchedFontIndex = resolveFontIndex(codepoint, fontType);
        matchedFonts[codepoint] = matchedFontIndex;
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
        GlyphRuntimeTables tables = runtimeTables;
        if (tables == null || !GlyphRuntimeTables.isValidCodepoint(codepoint)) {
            cacheMissCount.incrementAndGet();
            return resolveFontIndex(codepoint, fontType);
        }
        int[] matchedFonts = tables.matchedFontArray(fontType);
        int cachedFontIndex = matchedFonts[codepoint];
        if (cachedFontIndex != GlyphRuntimeTables.FONT_INDEX_UNRESOLVED) {
            cacheHitCount.incrementAndGet();
            return cachedFontIndex;
        }
        cacheMissCount.incrementAndGet();
        int matchedFontIndex = resolveFontIndex(codepoint, fontType);
        matchedFonts[codepoint] = matchedFontIndex;
        return matchedFontIndex;
    }

    private Font resolveFontWithoutCache(int codepoint, FontType fontType) {
        int fontIndex = resolveFontIndex(codepoint, fontType);
        return fontIndex >= 0 ? fontCatalog.getFont(fontIndex) : null;
    }

    private int resolveFontIndex(int codepoint, FontType fontType) {
        List<Font> fonts = fontCatalog.getFonts();
        for (int index = 0; index < fonts.size(); index++) {
            Font font = fonts.get(index);
            if (matchesWeight(font, fontType) && canDisplay(font, codepoint)) {
                return index;
            }
        }

        for (int index = 0; index < fonts.size(); index++) {
            Font font = fonts.get(index);
            if (font.canDisplay(codepoint)) {
                return index;
            }
        }
        return GlyphRuntimeTables.FONT_INDEX_NONE;
    }

    /**
     * 清空匹配缓存。
     */
    public void clearCache() {
        GlyphRuntimeTables tables = runtimeTables;
        if (tables != null) {
            tables.clearMatchedFontCache();
        }
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
        boolean isBoldFont = font.getName().toLowerCase().contains("bold") || font.isBold();
        if (fontType == FontType.BOLD) {
            return isBoldFont;
        }
        return !isBoldFont;
    }

    private boolean canDisplay(Font font, int codepoint) {
        if (!font.canDisplay(codepoint)) {
            return false;
        }

        GlyphVector glyphVector = font.createGlyphVector(FONT_RENDER_CONTEXT, new String(Character.toChars(codepoint)));
        int glyphCode = glyphVector.getGlyphCode(0);
        if (glyphCode == 0 || glyphCode == font.getMissingGlyphCode()) {
            return false;
        }
        return glyphVector.getGlyphOutline(0) != null;
    }

}
