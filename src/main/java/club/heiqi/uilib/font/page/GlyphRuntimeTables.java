package club.heiqi.uilib.font.page;

import java.util.Arrays;

import club.heiqi.uilib.font.FontType;

/**
 * 字体运行时按码点直索引表。
 *
 * <p>本结构只服务当前 runtimeVersion，宽度、字体匹配、字形状态和页槽位定位都按
 * {@code codepoint + FontType} 拆成 primitive array，避免渲染热路径创建键对象或查询多层 Map。</p>
 */
public final class GlyphRuntimeTables {

    public static final int CODEPOINT_COUNT = Character.MAX_CODE_POINT + 1;
    public static final int FONT_INDEX_UNRESOLVED = -1;
    public static final int FONT_INDEX_NONE = -2;
    public static final int LOCATION_NOT_READY = -1;

    public static final byte STATE_NEW = 0;
    public static final byte STATE_GENERATING = 1;
    public static final byte STATE_UPLOAD_PENDING = 2;
    public static final byte STATE_READY = 3;
    public static final byte STATE_FAILED = 4;

    public static final byte GLYPH_FLAG_COLORED = 1;

    public final float[] widthNormal = createWidthArray();
    public final float[] widthBold = createWidthArray();
    public final int[] matchedFontNormal = createMatchedFontArray();
    public final int[] matchedFontBold = createMatchedFontArray();
    public final byte[] stateNormal = new byte[CODEPOINT_COUNT];
    public final byte[] stateBold = new byte[CODEPOINT_COUNT];
    public final long[] generationNormal = new long[CODEPOINT_COUNT];
    public final long[] generationBold = new long[CODEPOINT_COUNT];
    public final int[] locationNormal = createLocationArray();
    public final int[] locationBold = createLocationArray();
    public final byte[] flagsNormal = new byte[CODEPOINT_COUNT];
    public final byte[] flagsBold = new byte[CODEPOINT_COUNT];

    public GlyphPage[] normalPages = new GlyphPage[4];
    public GlyphPage[] boldPages = new GlyphPage[4];
    public int normalPageCount;
    public int boldPageCount;
    public short[] slotXByIndex = new short[0];
    public short[] slotYByIndex = new short[0];
    public int slotsPerPage;

    /**
     * 判断码点是否可作为 direct-index 下标。
     *
     * @param codepoint 字符码点
     * @return 是否有效
     */
    public static boolean isValidCodepoint(int codepoint) {
        return codepoint >= 0 && codepoint < CODEPOINT_COUNT;
    }

    /**
     * 将页索引和槽位索引打包为单个 int。
     *
     * @param pageIndex 页索引
     * @param slotIndex 槽位索引
     * @return packed location
     */
    public static int packLocation(int pageIndex, int slotIndex) {
        return (pageIndex << 16) | (slotIndex & 0xFFFF);
    }

    public static int unpackPageIndex(int packedLocation) {
        return packedLocation >>> 16;
    }

    public static int unpackSlotIndex(int packedLocation) {
        return packedLocation & 0xFFFF;
    }

    public float[] widthArray(FontType fontType) {
        return fontType == FontType.BOLD ? widthBold : widthNormal;
    }

    public int[] matchedFontArray(FontType fontType) {
        return fontType == FontType.BOLD ? matchedFontBold : matchedFontNormal;
    }

    public byte[] stateArray(FontType fontType) {
        return fontType == FontType.BOLD ? stateBold : stateNormal;
    }

    public long[] generationArray(FontType fontType) {
        return fontType == FontType.BOLD ? generationBold : generationNormal;
    }

    public int[] locationArray(FontType fontType) {
        return fontType == FontType.BOLD ? locationBold : locationNormal;
    }

    public byte[] flagsArray(FontType fontType) {
        return fontType == FontType.BOLD ? flagsBold : flagsNormal;
    }

    public GlyphPage[] pages(FontType fontType) {
        return fontType == FontType.BOLD ? boldPages : normalPages;
    }

    public int pageCount(FontType fontType) {
        return fontType == FontType.BOLD ? boldPageCount : normalPageCount;
    }

    /**
     * 清空按码点宽度缓存。
     */
    public void clearWidthCache() {
        Arrays.fill(widthNormal, Float.NaN);
        Arrays.fill(widthBold, Float.NaN);
    }

    /**
     * 清空按码点字体匹配缓存。
     */
    public void clearMatchedFontCache() {
        Arrays.fill(matchedFontNormal, FONT_INDEX_UNRESOLVED);
        Arrays.fill(matchedFontBold, FONT_INDEX_UNRESOLVED);
    }

    /**
     * 清空字形生命周期、位置和页引用。
     */
    public void resetGlyphRuntime() {
        Arrays.fill(stateNormal, STATE_NEW);
        Arrays.fill(stateBold, STATE_NEW);
        Arrays.fill(generationNormal, 0L);
        Arrays.fill(generationBold, 0L);
        Arrays.fill(locationNormal, LOCATION_NOT_READY);
        Arrays.fill(locationBold, LOCATION_NOT_READY);
        Arrays.fill(flagsNormal, (byte) 0);
        Arrays.fill(flagsBold, (byte) 0);
        clearPageReferences();
    }

    /**
     * 根据当前字形页规格预计算槽位坐标。
     *
     * @param columnCount 每页列数
     * @param rowCount 每页行数
     * @param glyphSize 字形格大小
     */
    public void configureSlotCoordinates(int columnCount, int rowCount, int glyphSize) {
        int safeColumnCount = Math.max(1, columnCount);
        int safeRowCount = Math.max(1, rowCount);
        int slotCount = safeColumnCount * safeRowCount;
        if (slotXByIndex.length != slotCount) {
            slotXByIndex = new short[slotCount];
            slotYByIndex = new short[slotCount];
        }
        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            slotXByIndex[slotIndex] = (short) ((slotIndex % safeColumnCount) * glyphSize);
            slotYByIndex[slotIndex] = (short) ((slotIndex / safeColumnCount) * glyphSize);
        }
        slotsPerPage = slotCount;
    }

    /**
     * 确保指定字重的页数组容量足够。
     *
     * @param fontType 字重类型
     * @param minCapacity 最小容量
     */
    public void ensurePageArrayCapacity(FontType fontType, int minCapacity) {
        if (fontType == FontType.BOLD) {
            boldPages = ensureCapacity(boldPages, minCapacity);
            return;
        }
        normalPages = ensureCapacity(normalPages, minCapacity);
    }

    public void setPage(FontType fontType, int index, GlyphPage page) {
        ensurePageArrayCapacity(fontType, index + 1);
        if (fontType == FontType.BOLD) {
            boldPages[index] = page;
            boldPageCount = Math.max(boldPageCount, index + 1);
            return;
        }
        normalPages[index] = page;
        normalPageCount = Math.max(normalPageCount, index + 1);
    }

    private void clearPageReferences() {
        Arrays.fill(normalPages, 0, normalPageCount, null);
        Arrays.fill(boldPages, 0, boldPageCount, null);
        normalPageCount = 0;
        boldPageCount = 0;
    }

    private static float[] createWidthArray() {
        float[] widths = new float[CODEPOINT_COUNT];
        Arrays.fill(widths, Float.NaN);
        return widths;
    }

    private static int[] createMatchedFontArray() {
        int[] matchedFonts = new int[CODEPOINT_COUNT];
        Arrays.fill(matchedFonts, FONT_INDEX_UNRESOLVED);
        return matchedFonts;
    }

    private static int[] createLocationArray() {
        int[] locations = new int[CODEPOINT_COUNT];
        Arrays.fill(locations, LOCATION_NOT_READY);
        return locations;
    }

    private static GlyphPage[] ensureCapacity(GlyphPage[] pages, int minCapacity) {
        if (pages.length >= minCapacity) {
            return pages;
        }
        int nextCapacity = pages.length;
        while (nextCapacity < minCapacity) {
            nextCapacity *= 2;
        }
        GlyphPage[] expandedPages = new GlyphPage[nextCapacity];
        System.arraycopy(pages, 0, expandedPages, 0, pages.length);
        return expandedPages;
    }
}
