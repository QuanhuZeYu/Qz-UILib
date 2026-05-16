package club.heiqi.uilib.font.util;

import java.awt.Font;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.config.FontConfig;

/**
 * 按字体目录索引缓存 AWT 派生字体。
 */
public class DerivedFontCache {

    private final FontCatalog fontCatalog;
    private final Map<Long, Font> derivedFonts = new HashMap<Long, Font>();
    private final AtomicLong cacheHitCount = new AtomicLong(0L);
    private final AtomicLong cacheMissCount = new AtomicLong(0L);
    private int cachedCatalogVersion = -1;

    /**
     * 创建派生字体缓存。
     *
     * @param fontCatalog 字体目录
     */
    public DerivedFontCache(FontCatalog fontCatalog) {
        this.fontCatalog = fontCatalog;
    }

    /**
     * 获取指定目录字体在当前配置下的派生字体。
     *
     * @param fontIndex 字体目录索引
     * @param fontType 字重类型
     * @param glyphSize 字形格大小
     * @return 派生字体，索引无效时返回 null
     */
    public Font getDerivedFont(int fontIndex, FontType fontType, int glyphSize) {
        Font baseFont = fontCatalog.getFont(fontIndex);
        if (baseFont == null) {
            return null;
        }

        int style = fontType == FontType.BOLD ? Font.BOLD : Font.PLAIN;
        float size = (float) Math.max(glyphSize * FontConfig.fontScale, 6.0D);
        long key = packKey(fontIndex, style, size);
        synchronized (this) {
            refreshIfCatalogChanged();
            Font cachedFont = derivedFonts.get(Long.valueOf(key));
            if (cachedFont != null) {
                cacheHitCount.incrementAndGet();
                return cachedFont;
            }

            cacheMissCount.incrementAndGet();
            Font derivedFont = baseFont.deriveFont(style, size);
            derivedFonts.put(Long.valueOf(key), derivedFont);
            return derivedFont;
        }
    }

    /**
     * 清空已缓存的派生字体。
     */
    public synchronized void clear() {
        derivedFonts.clear();
        cachedCatalogVersion = fontCatalog.getVersion();
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

    private void refreshIfCatalogChanged() {
        int catalogVersion = fontCatalog.getVersion();
        if (catalogVersion == cachedCatalogVersion) {
            return;
        }
        derivedFonts.clear();
        cachedCatalogVersion = catalogVersion;
    }

    private long packKey(int fontIndex, int style, float size) {
        long fontBits = ((long) fontIndex & 0xFFFFFL) << 44;
        long styleBits = ((long) style & 0xFL) << 40;
        long sizeBits = (long) Float.floatToIntBits(size) & 0xFFFFFFFFL;
        return fontBits | styleBits | sizeBits;
    }
}
