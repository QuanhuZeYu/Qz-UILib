package club.heiqi.uilib.font.page;

import java.awt.image.BufferedImage;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.glyph.GlyphInfo;
import club.heiqi.uilib.font.glyph.GlyphRequestToken;

/**
 * headless 字符页装配器（testkit）：真 skyline 槽位分配 + 真上传路径，
 * 仅把 GL 门面换成软件纹理实现，字形像素保留在 CPU 侧。
 *
 * <p>与真机 {@code GlyphPageManager} 的分页/槽位/上传/度量逻辑同源，
 * 供软件渲染验收场地把 {@code GlyphRuntimeTables} 装配到「页数据就绪」状态。</p>
 */
public final class SoftwareGlyphPageAssembler {

    private SoftwareGlyphPageAssembler() {}

    /** 创建绑定软件 GL 门面的字符页。 */
    public static GlyphPage createPage(int runtimeVersion, int pageIndex, int textureSize, int glyphSize,
            int lerpMode, GlApi gl) {
        return new GlyphPage(runtimeVersion, pageIndex, textureSize, glyphSize, lerpMode, gl);
    }

    /**
     * 把一个已生成字形发布到页与运行时表（真机 upload commit 的 headless 等价路径）。
     *
     * @param fontType  字重
     * @param page      目标字符页（须可容纳 slot）
     * @param tables    运行时表（写入槽位几何/度量/状态）
     * @param codepoint 码点
     * @param info      生成度量
     * @param image     字形图像
     * @param token     请求 token
     * @return 分配的槽位
     */
    public static GlyphPage.GlyphSlot publish(FontType fontType, GlyphPage page, GlyphRuntimeTables tables,
            int codepoint, GlyphInfo info, BufferedImage image, GlyphRequestToken token) {
        GlyphPage.GlyphSlot slot = page.allocateSlot(info.getSlotWidth(), info.getSlotHeight());
        page.upload(slot, token, image);
        tables.locationArray(fontType)[codepoint] = GlyphRuntimeTables.packLocation(page.getPageIndex(),
                slot.getSlotIndex());
        tables.stateArray(fontType)[codepoint] = GlyphRuntimeTables.STATE_RESIDENT;
        byte flags = GlyphRuntimeTables.GLYPH_FLAG_HAS_BITMAP;
        if (info.isColoredGlyph()) {
            flags |= GlyphRuntimeTables.GLYPH_FLAG_COLORED;
        }
        tables.flagsArray(fontType)[codepoint] = flags;
        tables.slotXArray(fontType)[codepoint] = slot.getX();
        tables.slotYArray(fontType)[codepoint] = slot.getY();
        tables.slotWidthArray(fontType)[codepoint] = slot.getWidth();
        tables.slotHeightArray(fontType)[codepoint] = slot.getHeight();
        tables.atlasBaselineXArray(fontType)[codepoint] = info.getAtlasBaselineX();
        tables.atlasBaselineYArray(fontType)[codepoint] = info.getAtlasBaselineY();
        tables.lineBaselineYArray(fontType)[codepoint] = info.getLineBaselineY();
        tables.inkWidthArray(fontType)[codepoint] = (short) Math.max(0, Math.round(info.getGlyphWidth()));
        tables.inkHeightArray(fontType)[codepoint] = (short) Math.max(0, Math.round(info.getGlyphHeight()));
        tables.bearingXArray(fontType)[codepoint] = (short) info.getBearingX();
        tables.bearingYArray(fontType)[codepoint] = (short) info.getBearingY();
        // ink 数据就绪 → 递增就绪代（真机 cacheGlyphGeometry 同口径）
        tables.bumpInkEpoch();
        return slot;
    }
}
