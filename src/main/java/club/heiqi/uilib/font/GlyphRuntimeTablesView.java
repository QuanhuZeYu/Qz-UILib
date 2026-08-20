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
    private final int[] textureIdNormal;
    private final int[] textureIdBold;
    private final int[] textureSizeNormal;
    private final int[] textureSizeBold;

    GlyphRuntimeTablesView(GlyphRuntimeTables tables, GlyphPageManager pageManager, Object ownerToken,
            int runtimeVersion) {
        if (tables == null || pageManager == null) {
            throw new IllegalArgumentException("runtime table view 成员不得为 null");
        }
        this.tables = tables;
        this.pageManager = pageManager;
        this.ownerToken = ownerToken;
        this.runtimeVersion = runtimeVersion;
        // 渲染热路径帧级快照：构造时刻冻结页表纹理 ID/尺寸，绘制循环内零 FontRuntimeAccess 开销。
        // 上传只发生在 RenderTick START 稳定阶段（渲染前），快照与帧内绘制天然一致。
        this.textureIdNormal = snapshotTextureIds(FontType.NORMAL);
        this.textureIdBold = snapshotTextureIds(FontType.BOLD);
        this.textureSizeNormal = snapshotTextureSizes(FontType.NORMAL);
        this.textureSizeBold = snapshotTextureSizes(FontType.BOLD);
    }

    /**
     * 创建无 owner 的只读快照（headless 软件渲染验收场地）。
     *
     * <p>与真机快照同构（构造时刻冻结页纹理 ID/尺寸）；无 owner 场景下
     * {@link #getPageTextureId}/{@link #getPageTextureSize}/{@link #isCurrentPage}
     * 走直接执行路径，渲染热路径只依赖快照 getter。</p>
     *
     * @param tables         字形运行时表（页已装配）
     * @param pageManager    页管理器（供动态页查询；不可为 null）
     * @param runtimeVersion 运行时版本
     * @return 只读快照
     */
    public static GlyphRuntimeTablesView snapshot(GlyphRuntimeTables tables, GlyphPageManager pageManager,
            int runtimeVersion) {
        return new GlyphRuntimeTablesView(tables, pageManager, null, runtimeVersion);
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

    /**
     * 快照版页纹理 ID：构造时刻冻结的页表直读，渲染热路径无 FontRuntimeAccess 开销。
     *
     * <p>页无效（不存在/版本不匹配/未分配纹理/分配关闭）返回 0，与 {@link #getPageTextureId}
     * 的 0 语义一致，可直接作为渲染门控。</p>
     */
    public int getPageTextureIdSnapshot(FontType fontType, int pageIndex) {
        int[] snapshot = fontType == FontType.BOLD ? textureIdBold : textureIdNormal;
        return pageIndex >= 0 && pageIndex < snapshot.length ? snapshot[pageIndex] : 0;
    }

    /**
     * 快照版页纹理边长：构造时刻冻结的页表直读，渲染热路径无 FontRuntimeAccess 开销。
     *
     * <p>页无效（不存在/版本不匹配）返回 0，与 {@link #getPageTextureSize} 的 0 语义一致。</p>
     */
    public int getPageTextureSizeSnapshot(FontType fontType, int pageIndex) {
        int[] snapshot = fontType == FontType.BOLD ? textureSizeBold : textureSizeNormal;
        return pageIndex >= 0 && pageIndex < snapshot.length ? snapshot[pageIndex] : 0;
    }

    private int[] snapshotTextureIds(FontType fontType) {
        GlyphPage[] pages = tables.pages(fontType);
        int pageCount = tables.pageCount(fontType);
        int[] snapshot = new int[pageCount];
        for (int index = 0; index < pageCount; index++) {
            GlyphPage page = pages[index];
            snapshot[index] = page == null || page.getRuntimeVersion() != runtimeVersion
                    ? 0 : page.getTextureId();
        }
        return snapshot;
    }

    private int[] snapshotTextureSizes(FontType fontType) {
        GlyphPage[] pages = tables.pages(fontType);
        int pageCount = tables.pageCount(fontType);
        int[] snapshot = new int[pageCount];
        for (int index = 0; index < pageCount; index++) {
            GlyphPage page = pages[index];
            snapshot[index] = page == null || page.getRuntimeVersion() != runtimeVersion
                    ? 0 : page.getTextureSize();
        }
        return snapshot;
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
