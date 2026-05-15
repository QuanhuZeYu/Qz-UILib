package club.heiqi.uilib.font.util;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import club.heiqi.uilib.font.FontType;

/**
 * 字体匹配器。
 */
public class FontMatcher {

    private static final FontRenderContext FONT_RENDER_CONTEXT = new FontRenderContext(new AffineTransform(), true, true);

    private final FontCatalog fontCatalog;
    private final Map<String, Font> matchCache = new ConcurrentHashMap<String, Font>();
    private final AtomicLong cacheHitCount = new AtomicLong(0L);
    private final AtomicLong cacheMissCount = new AtomicLong(0L);

    /**
     * 创建字体匹配器。
     *
     * @param fontCatalog 字体目录
     */
    public FontMatcher(FontCatalog fontCatalog) {
        this.fontCatalog = fontCatalog;
    }

    /**
     * 匹配适合指定字符的字体。
     *
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @return 匹配到的字体，未匹配到则返回 null
     */
    public Font match(int runtimeVersion, int codepoint, FontType fontType) {
        String cacheKey = buildCacheKey(runtimeVersion, codepoint, fontType);
        Font cachedFont = matchCache.get(cacheKey);
        if (cachedFont != null) {
            cacheHitCount.incrementAndGet();
            return cachedFont;
        }
        cacheMissCount.incrementAndGet();

        for (Font font : fontCatalog.getFonts()) {
            if (matchesWeight(font, fontType) && canDisplay(font, codepoint)) {
                matchCache.put(cacheKey, font);
                return font;
            }
        }

        for (Font font : fontCatalog.getFonts()) {
            if (font.canDisplay(codepoint)) {
                matchCache.put(cacheKey, font);
                return font;
            }
        }
        return null;
    }

    /**
     * 清空匹配缓存。
     */
    public void clearCache() {
        matchCache.clear();
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

    private String buildCacheKey(int runtimeVersion, int codepoint, FontType fontType) {
        return runtimeVersion + ":" + codepoint + ":" + fontType.name();
    }
}
