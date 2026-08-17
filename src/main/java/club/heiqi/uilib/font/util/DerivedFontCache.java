package club.heiqi.uilib.font.util;

import java.awt.Font;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

import club.heiqi.uilib.font.FontType;

/**
 * 按字体目录索引缓存 AWT 派生字体。
 */
public class DerivedFontCache {

    private final FontCatalog fontCatalog;
    private final FontCatalog.Snapshot fixedSnapshot;
    private final Map<Long, Font> derivedFonts = new HashMap<Long, Font>();
    private final LongAdder cacheHitCount = new LongAdder();
    private final LongAdder cacheMissCount = new LongAdder();
    private int cachedCatalogVersion = -1;

    /**
     * 创建派生字体缓存。
     *
     * @param fontCatalog 字体目录
     */
    public DerivedFontCache(FontCatalog fontCatalog) {
        if (fontCatalog == null) {
            throw new IllegalArgumentException("fontCatalog 不得为 null");
        }
        this.fontCatalog = fontCatalog;
        this.fixedSnapshot = null;
    }

    /**
     * 创建只读取单个 generation 目录快照的派生字体缓存。
     *
     * @param snapshot generation 目录快照
     */
    public DerivedFontCache(FontCatalog.Snapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot 不得为 null");
        }
        this.fontCatalog = null;
        this.fixedSnapshot = snapshot;
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
        return getDerivedFont(currentSnapshot(), fontIndex, fontType, glyphSize);
    }

    /**
     * 从调用方已经捕获的 catalog snapshot 派生字体。
     *
     * @param snapshot generation 目录快照
     * @param fontIndex 字体目录索引
     * @param fontType 字重类型
     * @param glyphSize 字形格大小
     * @return 派生字体
     */
    public Font getDerivedFont(FontCatalog.Snapshot snapshot, int fontIndex, FontType fontType, int glyphSize) {
        if (snapshot == null) {
            return null;
        }
        int style = fontType == FontType.BOLD ? Font.BOLD : Font.PLAIN;
        float size = (float) Math.max(glyphSize, 6.0D);
        long key = packKey(fontIndex, style, size);
        synchronized (this) {
            refreshIfCatalogChanged(snapshot.getVersion());
            Font cachedFont = derivedFonts.get(Long.valueOf(key));
            if (cachedFont != null) {
                cacheHitCount.increment();
                return cachedFont;
            }

            Font baseFont = snapshot.getFont(fontIndex);
            if (baseFont == null) {
                return null;
            }

            cacheMissCount.increment();
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
        cachedCatalogVersion = currentSnapshot().getVersion();
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

    private void refreshIfCatalogChanged(int catalogVersion) {
        if (catalogVersion == cachedCatalogVersion) {
            return;
        }
        derivedFonts.clear();
        cachedCatalogVersion = catalogVersion;
    }

    private FontCatalog.Snapshot currentSnapshot() {
        return fixedSnapshot == null ? fontCatalog.snapshot() : fixedSnapshot;
    }

    private long packKey(int fontIndex, int style, float size) {
        long fontBits = ((long) fontIndex & 0xFFFFFL) << 44;
        long styleBits = ((long) style & 0xFL) << 40;
        long sizeBits = (long) Float.floatToIntBits(size) & 0xFFFFFFFFL;
        return fontBits | styleBits | sizeBits;
    }
}
