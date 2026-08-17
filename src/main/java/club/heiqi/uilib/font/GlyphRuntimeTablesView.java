package club.heiqi.uilib.font;

import club.heiqi.uilib.font.page.GlyphPage;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.page.GlyphRuntimeTables;

/**
 * 字形渲染所需的只读 runtime table view。
 *
 * <p>该 view 不暴露 primitive arrays、page manager 或可写 table；仅返回 upload transaction 已发布的
 * GL texture。</p>
 */
public final class GlyphRuntimeTablesView {

    private final GlyphRuntimeTables tables;
    private final GlyphPageManager pageManager;
    private final Object ownerToken;
    private final int runtimeVersion;

    GlyphRuntimeTablesView(GlyphRuntimeTables tables, GlyphPageManager pageManager, Object ownerToken,
            int runtimeVersion) {
        if (tables == null || pageManager == null) {
            throw new IllegalArgumentException("runtime table view 成员不得为 null");
        }
        this.tables = tables;
        this.pageManager = pageManager;
        this.ownerToken = ownerToken;
        this.runtimeVersion = runtimeVersion;
    }

    public int getRuntimeVersion() {
        return runtimeVersion;
    }

    public int getPackedLocation(int codepoint, FontType fontType) {
        return GlyphRuntimeTables.isValidCodepoint(codepoint)
                ? tables.locationArray(fontType)[codepoint] : GlyphRuntimeTables.LOCATION_NOT_READY;
    }

    public byte getFlags(int codepoint, FontType fontType) {
        return GlyphRuntimeTables.isValidCodepoint(codepoint) ? tables.flagsArray(fontType)[codepoint] : 0;
    }

    public int getSlotX(int codepoint, FontType fontType) {
        return getInt(tables.slotXArray(fontType), codepoint);
    }

    public int getSlotY(int codepoint, FontType fontType) {
        return getInt(tables.slotYArray(fontType), codepoint);
    }

    public int getSlotWidth(int codepoint, FontType fontType) {
        return getInt(tables.slotWidthArray(fontType), codepoint);
    }

    public int getSlotHeight(int codepoint, FontType fontType) {
        return getInt(tables.slotHeightArray(fontType), codepoint);
    }

    public int getAtlasBaselineX(int codepoint, FontType fontType) {
        return getInt(tables.atlasBaselineXArray(fontType), codepoint);
    }

    public int getAtlasBaselineY(int codepoint, FontType fontType) {
        return getInt(tables.atlasBaselineYArray(fontType), codepoint);
    }

    public int getLineBaselineY(int codepoint, FontType fontType) {
        return getInt(tables.lineBaselineYArray(fontType), codepoint);
    }

    public int getInkWidth(int codepoint, FontType fontType) {
        return getShort(tables.inkWidthArray(fontType), codepoint);
    }

    public int getInkHeight(int codepoint, FontType fontType) {
        return getShort(tables.inkHeightArray(fontType), codepoint);
    }

    public int getBearingX(int codepoint, FontType fontType) {
        return getShort(tables.bearingXArray(fontType), codepoint);
    }

    public int getBearingY(int codepoint, FontType fontType) {
        return getShort(tables.bearingYArray(fontType), codepoint);
    }

    public int getPageCount(FontType fontType) {
        return tables.pageCount(fontType);
    }

    public boolean isCurrentPage(FontType fontType, int pageIndex) {
        return FontRuntimeAccess.call(ownerToken, () -> {
            GlyphPage page = resolvePage(fontType, pageIndex);
            return Boolean.valueOf(page != null && page.getRuntimeVersion() == runtimeVersion);
        }).booleanValue();
    }

    public int getPageTextureSize(FontType fontType, int pageIndex) {
        return FontRuntimeAccess.call(ownerToken, () -> {
            GlyphPage page = resolvePage(fontType, pageIndex);
            return Integer.valueOf(page == null || page.getRuntimeVersion() != runtimeVersion
                    ? 0 : page.getTextureSize());
        }).intValue();
    }

    public int getPageTextureId(FontType fontType, int pageIndex) {
        return FontRuntimeAccess.call(ownerToken, () -> {
            GlyphPage page = resolvePage(fontType, pageIndex);
            return Integer.valueOf(page == null || page.getRuntimeVersion() != runtimeVersion
                    ? 0 : page.getTextureId());
        }).intValue();
    }

    private GlyphPage resolvePage(FontType fontType, int pageIndex) {
        if (pageIndex < 0 || pageIndex >= tables.pageCount(fontType)) {
            return null;
        }
        return pageManager.getPageByLocation(GlyphRuntimeTables.packLocation(pageIndex, 0), fontType);
    }

    private int getInt(int[] values, int codepoint) {
        return GlyphRuntimeTables.isValidCodepoint(codepoint) ? values[codepoint] : 0;
    }

    private int getShort(short[] values, int codepoint) {
        return GlyphRuntimeTables.isValidCodepoint(codepoint) ? values[codepoint] : 0;
    }
}
