package club.heiqi.uilib.font.page;

import java.util.Arrays;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.FontRuntimeMetrics;

/**
 * 字体运行时按码点直索引表。
 *
 * <p>本结构在 generation write barrier 内原地清理并转移给下一 runtimeVersion；宽度、字体匹配、
 * 字形状态和页槽位定位都按 {@code codepoint + FontType} 拆成 primitive array，避免热路径创建键对象，
 * 也避免换代时并存两份完整 Unicode tables。</p>
 */
public final class GlyphRuntimeTables {

    public static final int CODEPOINT_COUNT = Character.MAX_CODE_POINT + 1;
    public static final int FONT_INDEX_UNRESOLVED = -1;
    public static final int FONT_INDEX_NONE = -2;
    public static final int LOCATION_NOT_READY = -1;
    public static final int LOCATION_NO_BITMAP = -2;

    public static final byte STATE_ABSENT = 0;
    public static final byte STATE_QUEUED = 1;
    public static final byte STATE_RASTERIZING = 2;
    public static final byte STATE_UPLOAD_QUEUED = 3;
    public static final byte STATE_UPLOADING = 4;
    public static final byte STATE_RESIDENT = 5;
    public static final byte STATE_NO_BITMAP = 6;
    public static final byte STATE_FAILED = 7;
    public static final byte STATE_CANCELLED_STALE = 8;

    public static final byte GLYPH_FLAG_COLORED = 1;
    public static final byte GLYPH_FLAG_HAS_BITMAP = 2;

    public final float[] widthNormal = createWidthArray();
    public final float[] widthBold = createWidthArray();
    public final int[] matchedFontNormal = createMatchedFontArray();
    public final int[] matchedFontBold = createMatchedFontArray();
    public final byte[] stateNormal = new byte[CODEPOINT_COUNT];
    public final byte[] stateBold = new byte[CODEPOINT_COUNT];
    public final long[] requestIdNormal = new long[CODEPOINT_COUNT];
    public final long[] requestIdBold = new long[CODEPOINT_COUNT];
    public final int[] locationNormal = createLocationArray();
    public final int[] locationBold = createLocationArray();
    public final byte[] flagsNormal = new byte[CODEPOINT_COUNT];
    public final byte[] flagsBold = new byte[CODEPOINT_COUNT];
    public final int[] slotXNormal = new int[CODEPOINT_COUNT];
    public final int[] slotXBold = new int[CODEPOINT_COUNT];
    public final int[] slotYNormal = new int[CODEPOINT_COUNT];
    public final int[] slotYBold = new int[CODEPOINT_COUNT];
    public final int[] slotWidthNormal = new int[CODEPOINT_COUNT];
    public final int[] slotWidthBold = new int[CODEPOINT_COUNT];
    public final int[] slotHeightNormal = new int[CODEPOINT_COUNT];
    public final int[] slotHeightBold = new int[CODEPOINT_COUNT];
    public final int[] atlasBaselineXNormal = new int[CODEPOINT_COUNT];
    public final int[] atlasBaselineXBold = new int[CODEPOINT_COUNT];
    public final int[] atlasBaselineYNormal = new int[CODEPOINT_COUNT];
    public final int[] atlasBaselineYBold = new int[CODEPOINT_COUNT];
    /**
     * 默认字符格内文本基线 Y，量纲=atlas 像素（awtCharSize 坐标系）。
     */
    public final int[] lineBaselineYNormal = new int[CODEPOINT_COUNT];
    /**
     * 默认字符格内文本基线 Y，量纲=atlas 像素（awtCharSize 坐标系）。
     */
    public final int[] lineBaselineYBold = new int[CODEPOINT_COUNT];
    /**
     * 字体上升量，量纲=atlas 像素（awtCharSize 坐标系）。
     */
    public float ascentNormal;
    /**
     * 字体上升量（粗体），量纲=atlas 像素（awtCharSize 坐标系）。
     */
    public float ascentBold;
    /**
     * 字体下降量，量纲=atlas 像素（awtCharSize 坐标系）。
     */
    public float descentNormal;
    /**
     * 字体下降量（粗体），量纲=atlas 像素（awtCharSize 坐标系）。
     */
    public float descentBold;
    /**
     * 字体行间隙，量纲=atlas 像素（awtCharSize 坐标系）。
     */
    public float leadingNormal;
    /**
     * 字体行间隙（粗体），量纲=atlas 像素（awtCharSize 坐标系）。
     */
    public float leadingBold;
    public final short[] inkWidthNormal = new short[CODEPOINT_COUNT];
    public final short[] inkWidthBold = new short[CODEPOINT_COUNT];
    public final short[] inkHeightNormal = new short[CODEPOINT_COUNT];
    public final short[] inkHeightBold = new short[CODEPOINT_COUNT];
    public final short[] bearingXNormal = new short[CODEPOINT_COUNT];
    public final short[] bearingXBold = new short[CODEPOINT_COUNT];
    public final short[] bearingYNormal = new short[CODEPOINT_COUNT];
    public final short[] bearingYBold = new short[CODEPOINT_COUNT];

    public GlyphPage[] normalPages = new GlyphPage[4];
    public GlyphPage[] boldPages = new GlyphPage[4];
    public int normalPageCount;
    public int boldPageCount;
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

    public long[] requestIdArray(FontType fontType) {
        return fontType == FontType.BOLD ? requestIdBold : requestIdNormal;
    }

    public int[] locationArray(FontType fontType) {
        return fontType == FontType.BOLD ? locationBold : locationNormal;
    }

    public byte[] flagsArray(FontType fontType) {
        return fontType == FontType.BOLD ? flagsBold : flagsNormal;
    }

    public int[] slotXArray(FontType fontType) {
        return fontType == FontType.BOLD ? slotXBold : slotXNormal;
    }

    public int[] slotYArray(FontType fontType) {
        return fontType == FontType.BOLD ? slotYBold : slotYNormal;
    }

    public int[] slotWidthArray(FontType fontType) {
        return fontType == FontType.BOLD ? slotWidthBold : slotWidthNormal;
    }

    public int[] slotHeightArray(FontType fontType) {
        return fontType == FontType.BOLD ? slotHeightBold : slotHeightNormal;
    }

    public int[] atlasBaselineXArray(FontType fontType) {
        return fontType == FontType.BOLD ? atlasBaselineXBold : atlasBaselineXNormal;
    }

    public int[] atlasBaselineYArray(FontType fontType) {
        return fontType == FontType.BOLD ? atlasBaselineYBold : atlasBaselineYNormal;
    }

    public int[] lineBaselineYArray(FontType fontType) {
        return fontType == FontType.BOLD ? lineBaselineYBold : lineBaselineYNormal;
    }

    public float ascent(FontType fontType) {
        return fontType == FontType.BOLD ? ascentBold : ascentNormal;
    }

    public float descent(FontType fontType) {
        return fontType == FontType.BOLD ? descentBold : descentNormal;
    }

    public float leading(FontType fontType) {
        return fontType == FontType.BOLD ? leadingBold : leadingNormal;
    }

    public short[] inkWidthArray(FontType fontType) {
        return fontType == FontType.BOLD ? inkWidthBold : inkWidthNormal;
    }

    public short[] inkHeightArray(FontType fontType) {
        return fontType == FontType.BOLD ? inkHeightBold : inkHeightNormal;
    }

    public short[] bearingXArray(FontType fontType) {
        return fontType == FontType.BOLD ? bearingXBold : bearingXNormal;
    }

    public short[] bearingYArray(FontType fontType) {
        return fontType == FontType.BOLD ? bearingYBold : bearingYNormal;
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
        Arrays.fill(stateNormal, STATE_ABSENT);
        Arrays.fill(stateBold, STATE_ABSENT);
        Arrays.fill(requestIdNormal, 0L);
        Arrays.fill(requestIdBold, 0L);
        Arrays.fill(locationNormal, LOCATION_NOT_READY);
        Arrays.fill(locationBold, LOCATION_NOT_READY);
        Arrays.fill(flagsNormal, (byte) 0);
        Arrays.fill(flagsBold, (byte) 0);
        ascentNormal = 0.0F;
        ascentBold = 0.0F;
        descentNormal = 0.0F;
        descentBold = 0.0F;
        leadingNormal = 0.0F;
        leadingBold = 0.0F;
        clearGlyphGeometry();
        clearPageReferences();
    }

    /**
     * 惰性清理 generation 生命周期门控所需的表项。
     *
     * <p>只清渲染与请求路径直接作为门控读取的四类数组——state（生命周期）、location
     * （渲染侧直读的定位门控）、width/matchedFont（跨 generation 缓存语义）——加上稳定行度量
     * 与页引用；其余几何/标志数组（slot 系列/baseline/ink/bearing/flags/requestId）保持原值，
     * 靠 location 门控与 generation 校验惰性失效：
     * <ul>
     * <li>渲染侧仅在 location 有效时读取几何数组，而 location 有效必然伴随同批
     *     {@code cacheGlyphGeometry} 先行写入；</li>
     * <li>requestId 由单调递增序列覆写，旧 token 已先被 generation 校验拦截。</li>
     * </ul>
     * 与 {@link #resetGlyphRuntime()}（全量清零）相比，fill 量从约 123MiB 降至约 29MiB，
     * 用于 reload 时避免主线程大数组清理停顿。</p>
     */
    public void resetGlyphLifecycle() {
        Arrays.fill(stateNormal, STATE_ABSENT);
        Arrays.fill(stateBold, STATE_ABSENT);
        Arrays.fill(locationNormal, LOCATION_NOT_READY);
        Arrays.fill(locationBold, LOCATION_NOT_READY);
        Arrays.fill(widthNormal, Float.NaN);
        Arrays.fill(widthBold, Float.NaN);
        Arrays.fill(matchedFontNormal, FONT_INDEX_UNRESOLVED);
        Arrays.fill(matchedFontBold, FONT_INDEX_UNRESOLVED);
        ascentNormal = 0.0F;
        ascentBold = 0.0F;
        descentNormal = 0.0F;
        descentBold = 0.0F;
        leadingNormal = 0.0F;
        leadingBold = 0.0F;
        clearPageReferences();
    }

    /**
     * 根据当前字形页规格预计算槽位坐标。
     *
     * @param columnCount 每页列数
     * @param rowCount    每页行数
     * @param glyphSize   字形格大小
     */
    public void configureSlotCoordinates(int columnCount, int rowCount, int glyphSize) {
        int safeColumnCount = Math.max(1, columnCount);
        int safeRowCount = Math.max(1, rowCount);
        slotsPerPage = safeColumnCount * safeRowCount;
    }

    /**
     * 发布 generation 构建期已经冻结的稳定行度量。
     *
     * @param metrics generation 行度量
     */
    public void setFontMetrics(FontRuntimeMetrics metrics) {
        ascentNormal = metrics.getAscent(FontType.NORMAL);
        descentNormal = metrics.getDescent(FontType.NORMAL);
        leadingNormal = metrics.getLeading(FontType.NORMAL);
        ascentBold = metrics.getAscent(FontType.BOLD);
        descentBold = metrics.getDescent(FontType.BOLD);
        leadingBold = metrics.getLeading(FontType.BOLD);
    }

    /**
     * 确保指定字重的页数组容量足够。
     *
     * @param fontType    字重类型
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

    private void clearGlyphGeometry() {
        Arrays.fill(slotXNormal, 0);
        Arrays.fill(slotXBold, 0);
        Arrays.fill(slotYNormal, 0);
        Arrays.fill(slotYBold, 0);
        Arrays.fill(slotWidthNormal, 0);
        Arrays.fill(slotWidthBold, 0);
        Arrays.fill(slotHeightNormal, 0);
        Arrays.fill(slotHeightBold, 0);
        Arrays.fill(atlasBaselineXNormal, 0);
        Arrays.fill(atlasBaselineXBold, 0);
        Arrays.fill(atlasBaselineYNormal, 0);
        Arrays.fill(atlasBaselineYBold, 0);
        Arrays.fill(lineBaselineYNormal, 0);
        Arrays.fill(lineBaselineYBold, 0);
        Arrays.fill(inkWidthNormal, (short) 0);
        Arrays.fill(inkWidthBold, (short) 0);
        Arrays.fill(inkHeightNormal, (short) 0);
        Arrays.fill(inkHeightBold, (short) 0);
        Arrays.fill(bearingXNormal, (short) 0);
        Arrays.fill(bearingXBold, (short) 0);
        Arrays.fill(bearingYNormal, (short) 0);
        Arrays.fill(bearingYBold, (short) 0);
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
