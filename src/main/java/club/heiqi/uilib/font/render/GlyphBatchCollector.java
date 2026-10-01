package club.heiqi.uilib.font.render;

import java.util.Arrays;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.page.GlyphRuntimeTables;

/**
 * 平台中立的字形批次收集器：{@link FontBatchRenderer} 收集侧的无 GL 实现。
 *
 * <p>收集语义与真机批渲染器完全同源（同一 {@code GlyphRenderBatch} 结构、同一
 * {@code resolveGlyphQuadMetrics} 几何口径、同一页激活簿记）；不触碰 LWJGL/GL，
 * 供 headless 软件渲染验收场地直接驱动。渲染出口把已收集批次快照交给
 * {@code club.heiqi.uilib.font.render.software.FontSoftwareRasterizer}。</p>
 */
public final class GlyphBatchCollector implements GlyphCollector {

    /** 字面数量（{@code FontType.values().length}，NORMAL/BOLD/ITALIC/BOLD_ITALIC）。 */
    private static final int FACE_COUNT = FontType.values().length;

    /** 表示本次收集没有记录到任何字形颜色的哨兵值。 */
    public static final int NO_GLYPH_COLOR = -1;

    /** 每字面一份页批次数组；索引 = {@code FontType.ordinal()}。 */
    private final GlyphRenderBatch[][] pageBatches = new GlyphRenderBatch[FACE_COUNT][];
    /** 每字面一份页激活标记；索引 = {@code FontType.ordinal()}。 */
    private final boolean[][] activePages = new boolean[FACE_COUNT][];
    private int[] activePageIndices = new int[8];
    /** 激活页所属字面的 {@code FontType.ordinal()}。 */
    private byte[] activePageTypes = new byte[8];
    private int activePageCount;

    {
        for (int faceIndex = 0; faceIndex < FACE_COUNT; faceIndex++) {
            pageBatches[faceIndex] = new GlyphRenderBatch[4];
            activePages[faceIndex] = new boolean[4];
        }
    }
    private final GlyphRenderBatch decorationBatch = new GlyphRenderBatch();
    private final GlyphRenderBatch markBackgroundBatch = new GlyphRenderBatch();
    private int quadCount;
    private int lastCollectedGlyphColor = NO_GLYPH_COLOR;

    /** 创建空收集器。 */
    public GlyphBatchCollector() {}

    @Override
    public void collectBaselineAlignedGlyph(FontType fontType, int pageIndex, int textureId, int textureSize,
            int slotX, int slotY, int slotWidth, int slotHeight, int atlasBaselineX, int atlasBaselineY,
            int lineBaselineY, int defaultGlyphSize, int inkWidth, int inkHeight, int bearingX, int bearingY,
            float x, float y, float charSize, int color, boolean italic, byte glyphFlags, float baseCharSize) {
        collectBaselineAlignedGlyphClipped(fontType, pageIndex, textureId, textureSize, slotX, slotY, slotWidth, slotHeight,
                atlasBaselineX, atlasBaselineY, lineBaselineY, defaultGlyphSize, inkWidth, inkHeight, bearingX, bearingY,
                x, y, charSize, color, italic, glyphFlags, baseCharSize,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
    }

    @Override
    public void collectBaselineAlignedGlyphClipped(FontType fontType, int pageIndex, int textureId, int textureSize,
            int slotX, int slotY, int slotWidth, int slotHeight, int atlasBaselineX, int atlasBaselineY,
            int lineBaselineY, int defaultGlyphSize, int inkWidth, int inkHeight, int bearingX, int bearingY,
            float x, float y, float charSize, int color, boolean italic, byte glyphFlags, float baseCharSize,
            float clipLeft, float clipTop, float clipRight, float clipBottom) {
        if (pageIndex < 0 || textureId <= 0 || textureSize <= 0 || slotWidth <= 0 || slotHeight <= 0
                || inkWidth <= 0 || inkHeight <= 0) {
            return;
        }
        FontBatchRenderer.GlyphQuadMetrics metrics = FontBatchRenderer.resolveGlyphQuadMetrics(textureSize, slotX, slotY, slotWidth,
                slotHeight, atlasBaselineX, atlasBaselineY, lineBaselineY, defaultGlyphSize, inkWidth, inkHeight,
                bearingX, bearingY, x, y, charSize, baseCharSize, glyphFlags);
        metrics = FontBatchRenderer.clipGlyphQuad(metrics, clipLeft, clipTop, clipRight, clipBottom);
        if (metrics == null) { return; }

        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;
        float z = (float) FontConfig.renderOffset;

        float renderType = (glyphFlags & GlyphRuntimeTables.GLYPH_FLAG_COLORED) != 0
                ? GlyphRenderBatch.RENDER_TYPE_COLORED_GLYPH
                : GlyphRenderBatch.RENDER_TYPE_MONOCHROME_GLYPH;
        GlyphRenderBatch batch = obtainPageBatch(fontType, pageIndex, textureId);
        batch.addQuad(metrics.quadX, metrics.quadY, z, metrics.renderWidth, metrics.renderHeight, italic, metrics.u0,
                metrics.u1, metrics.v0, metrics.v1, metrics.clipU0, metrics.clipU1, metrics.clipV0, metrics.clipV1,
                red, green, blue, alpha, renderType, baseCharSize);
        quadCount++;
        lastCollectedGlyphColor = color;
    }

    @Override
    public void collectDecoration(float x, float y, float width, float height, int color) {
        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;
        float z = (float) FontConfig.renderOffset;

        decorationBatch.addRectangleQuad(x, y, z, width, height, red, green, blue, alpha,
                GlyphRenderBatch.RENDER_TYPE_DECORATION);
        quadCount++;
    }

    @Override
    public void collectMarkBackground(float x, float y, float width, float height, int color) {
        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;
        float z = (float) FontConfig.renderOffset;

        markBackgroundBatch.addRectangleQuad(x, y, z, width, height, red, green, blue, alpha,
                GlyphRenderBatch.RENDER_TYPE_DECORATION);
        quadCount++;
    }

    /** 高亮背景批次（绘制顺序第一）。 */
    public GlyphRenderBatch getMarkBackgroundBatch() {
        return markBackgroundBatch;
    }

    /** 装饰线批次（绘制顺序最后）。 */
    public GlyphRenderBatch getDecorationBatch() {
        return decorationBatch;
    }

    /** 已激活的字符页批次数量。 */
    public int getActivePageCount() {
        return activePageCount;
    }

    /** 按激活顺序取字符页批次；越界返回 null。 */
    public GlyphRenderBatch getActiveBatch(int activeIndex) {
        if (activeIndex < 0 || activeIndex >= activePageCount) {
            return null;
        }
        int pageIndex = activePageIndices[activeIndex];
        GlyphRenderBatch[] batches = pageBatches[activePageTypes[activeIndex]];
        if (pageIndex < 0 || pageIndex >= batches.length) {
            return null;
        }
        return batches[pageIndex];
    }

    /** 当前帧四边形总数。 */
    public int getQuadCount() {
        return quadCount;
    }

    /** 当前帧收集侧最后一个字形 quad 的 ARGB 颜色；无字形为 {@link #NO_GLYPH_COLOR}。 */
    public int getLastCollectedGlyphColor() {
        return lastCollectedGlyphColor;
    }

    /** 清空当前帧并复用批次数组。 */
    public void clearFrame() {
        for (int index = 0; index < activePageCount; index++) {
            GlyphRenderBatch batch = getActiveBatch(index);
            if (batch != null) {
                batch.clear();
            }
            clearActiveMarker(index);
        }
        activePageCount = 0;
        decorationBatch.clear();
        markBackgroundBatch.clear();
        quadCount = 0;
        lastCollectedGlyphColor = NO_GLYPH_COLOR;
    }

    private static int faceIndex(FontType fontType) {
        return fontType == null ? FontType.NORMAL.ordinal() : fontType.ordinal();
    }

    private GlyphRenderBatch obtainPageBatch(FontType fontType, int pageIndex, int textureId) {
        int faceIndex = faceIndex(fontType);
        GlyphRenderBatch[] batches = ensurePageBatchCapacity(faceIndex, pageIndex + 1);
        GlyphRenderBatch batch = batches[pageIndex];
        if (batch == null) {
            batch = new GlyphRenderBatch();
            batches[pageIndex] = batch;
        }
        if (markPageActive(faceIndex, pageIndex)) {
            appendActivePage(faceIndex, pageIndex);
        }
        batch.setTextureId(textureId);
        return batch;
    }

    private GlyphRenderBatch[] ensurePageBatchCapacity(int faceIndex, int minCapacity) {
        if (pageBatches[faceIndex].length < minCapacity) {
            pageBatches[faceIndex] = grow(pageBatches[faceIndex], minCapacity);
            activePages[faceIndex] = grow(activePages[faceIndex], minCapacity);
        }
        return pageBatches[faceIndex];
    }

    private boolean markPageActive(int faceIndex, int pageIndex) {
        boolean[] faceActivePages = activePages[faceIndex];
        if (faceActivePages[pageIndex]) {
            return false;
        }
        faceActivePages[pageIndex] = true;
        return true;
    }

    private void appendActivePage(int faceIndex, int pageIndex) {
        if (activePageCount >= activePageIndices.length) {
            activePageIndices = grow(activePageIndices, activePageCount + 1);
            activePageTypes = grow(activePageTypes, activePageCount + 1);
        }
        activePageIndices[activePageCount] = pageIndex;
        activePageTypes[activePageCount] = (byte) faceIndex;
        activePageCount++;
    }

    private void clearActiveMarker(int activeIndex) {
        int pageIndex = activePageIndices[activeIndex];
        boolean[] faceActivePages = activePages[activePageTypes[activeIndex]];
        if (pageIndex >= 0 && pageIndex < faceActivePages.length) {
            faceActivePages[pageIndex] = false;
        }
    }

    private static GlyphRenderBatch[] grow(GlyphRenderBatch[] original, int minCapacity) {
        return Arrays.copyOf(original, grownCapacity(original.length, minCapacity));
    }

    private static int[] grow(int[] original, int minCapacity) {
        return Arrays.copyOf(original, grownCapacity(original.length, minCapacity));
    }

    private static byte[] grow(byte[] original, int minCapacity) {
        return Arrays.copyOf(original, grownCapacity(original.length, minCapacity));
    }

    private static boolean[] grow(boolean[] original, int minCapacity) {
        return Arrays.copyOf(original, grownCapacity(original.length, minCapacity));
    }

    private static int grownCapacity(int current, int minCapacity) {
        int nextCapacity = current;
        while (nextCapacity < minCapacity) {
            nextCapacity *= 2;
        }
        return nextCapacity;
    }
}
