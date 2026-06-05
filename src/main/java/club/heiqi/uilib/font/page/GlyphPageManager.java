package club.heiqi.uilib.font.page;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.glyph.GlyphGenerationResult;
import club.heiqi.uilib.font.glyph.GlyphInfo;

/**
 * 字符页管理器。
 */
public class GlyphPageManager {

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Queue<PendingGlyphUpload> pendingUploads = new ConcurrentLinkedQueue<PendingGlyphUpload>();
    private final AtomicLong generationIdSequence = new AtomicLong(0L);

    private GlyphRuntimeTables runtimeTables = new GlyphRuntimeTables();
    private int textureSize;
    private int glyphSize;
    private int columnCount;
    private int rowCount;
    private int maintainPageCount = 3;
    private int readyGlyphCount;
    private volatile int runtimeVersion;

    /**
     * 初始化字符页管理器。
     */
    public synchronized void initialize() {
        configurePageGeometry();
        runtimeTables.configureSlotCoordinates(columnCount, rowCount, glyphSize);
        if (initialized.compareAndSet(false, true)) {
            ensureCapacity(FontType.NORMAL);
            ensureCapacity(FontType.BOLD);
        }
    }

    /**
     * 设置当前运行时版本。
     *
     * @param runtimeVersion 运行时版本
     */
    public synchronized void setRuntimeVersion(int runtimeVersion) {
        if (this.runtimeVersion == runtimeVersion) {
            return;
        }
        this.runtimeVersion = runtimeVersion;
        reset();
    }

    /**
     * 获取当前运行时直索引表。
     *
     * @return 运行时表
     */
    public GlyphRuntimeTables getRuntimeTables() {
        return runtimeTables;
    }

    /**
     * 重置字符页状态。
     */
    public synchronized void reset() {
        if (initialized.get()) {
            closePages(runtimeTables.normalPages, runtimeTables.normalPageCount);
            closePages(runtimeTables.boldPages, runtimeTables.boldPageCount);
        }
        pendingUploads.clear();
        runtimeTables = new GlyphRuntimeTables();
        configurePageGeometry();
        runtimeTables.configureSlotCoordinates(columnCount, rowCount, glyphSize);
        readyGlyphCount = 0;
        if (initialized.get()) {
            ensureCapacity(FontType.NORMAL);
            ensureCapacity(FontType.BOLD);
        }
    }

    /**
     * 丢弃当前运行时尚未上传的字形结果。
     */
    public synchronized void discardPendingUploads() {
        pendingUploads.clear();
    }

    /**
     * 尝试将字符切换到生成中状态。
     *
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @return 是否允许开始生成
     */
    public boolean tryMarkGenerating(int codepoint, FontType fontType) {
        return tryMarkGenerating(runtimeVersion, codepoint, fontType);
    }

    /**
     * 尝试将指定运行时版本内的字符切换到生成中状态。
     *
     * @param runtimeVersion 运行时版本
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @return 是否允许开始生成
     */
    public synchronized boolean tryMarkGenerating(int runtimeVersion, int codepoint, FontType fontType) {
        if (runtimeVersion != this.runtimeVersion || !GlyphRuntimeTables.isValidCodepoint(codepoint)) {
            return false;
        }
        byte[] states = runtimeTables.stateArray(fontType);
        byte currentState = states[codepoint];
        if (currentState == GlyphRuntimeTables.STATE_GENERATING
                || currentState == GlyphRuntimeTables.STATE_UPLOAD_PENDING
                || currentState == GlyphRuntimeTables.STATE_READY) {
            return false;
        }

        states[codepoint] = GlyphRuntimeTables.STATE_GENERATING;
        runtimeTables.generationArray(fontType)[codepoint] = generationIdSequence.incrementAndGet();
        return true;
    }

    /**
     * 获取当前字符生成请求编号。
     *
     * @param runtimeVersion 运行时版本
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @return 生成请求编号，未处于生成链路时返回 0
     */
    public synchronized long getGenerationId(int runtimeVersion, int codepoint, FontType fontType) {
        if (runtimeVersion != this.runtimeVersion || !GlyphRuntimeTables.isValidCodepoint(codepoint)) {
            return 0L;
        }
        return runtimeTables.generationArray(fontType)[codepoint];
    }

    /**
     * 标记字符生成被当前运行时取消。
     *
     * @param runtimeVersion 运行时版本
     * @param codepoint 字符码点
     * @param fontType 字重类型
     */
    public synchronized void markGenerationCancelled(int runtimeVersion, int codepoint, FontType fontType) {
        if (runtimeVersion != this.runtimeVersion || !GlyphRuntimeTables.isValidCodepoint(codepoint)) {
            return;
        }
        byte[] states = runtimeTables.stateArray(fontType);
        byte state = states[codepoint];
        if (state == GlyphRuntimeTables.STATE_GENERATING || state == GlyphRuntimeTables.STATE_UPLOAD_PENDING) {
            states[codepoint] = GlyphRuntimeTables.STATE_NEW;
            runtimeTables.generationArray(fontType)[codepoint] = 0L;
        }
    }

    /**
     * 标记字符生成失败。
     *
     * @param codepoint 字符码点
     * @param fontType 字重类型
     */
    public void markFailed(int codepoint, FontType fontType) {
        markFailed(runtimeVersion, codepoint, fontType);
    }

    /**
     * 标记指定运行时版本内的字符生成失败。
     *
     * @param runtimeVersion 运行时版本
     * @param codepoint 字符码点
     * @param fontType 字重类型
     */
    public synchronized void markFailed(int runtimeVersion, int codepoint, FontType fontType) {
        if (runtimeVersion != this.runtimeVersion || !GlyphRuntimeTables.isValidCodepoint(codepoint)) {
            return;
        }
        runtimeTables.generationArray(fontType)[codepoint] = 0L;
        runtimeTables.stateArray(fontType)[codepoint] = GlyphRuntimeTables.STATE_FAILED;
    }

    /**
     * 接收后台线程生成完成的字符结果。
     *
     * @param result 字符生成结果
     */
    public synchronized void queueUpload(GlyphGenerationResult result) {
        if (result == null || result.getRuntimeVersion() != runtimeVersion
                || !GlyphRuntimeTables.isValidCodepoint(result.getCodepoint())) {
            return;
        }
        int codepoint = result.getCodepoint();
        FontType fontType = result.getFontType();
        byte[] states = runtimeTables.stateArray(fontType);
        if (states[codepoint] != GlyphRuntimeTables.STATE_GENERATING) {
            return;
        }
        if (!isCurrentGeneration(codepoint, fontType, result.getGenerationId())) {
            return;
        }
        pendingUploads.add(new PendingGlyphUpload(result.getRuntimeVersion(), result));
        states[codepoint] = GlyphRuntimeTables.STATE_UPLOAD_PENDING;
    }

    /**
     * 在主线程侧刷新待上传队列。
     *
     * @param maxCount 本次最多处理的数量
     */
    public synchronized void flushPendingUploads(int maxCount) {
        int processed = 0;
        while (processed < maxCount && !pendingUploads.isEmpty()) {
            PendingGlyphUpload upload = pendingUploads.poll();
            if (upload == null) {
                break;
            }

            GlyphGenerationResult result = upload.getGenerationResult();
            if (upload.getRuntimeVersion() != runtimeVersion || result.getRuntimeVersion() != runtimeVersion) {
                continue;
            }
            int codepoint = upload.getCodepoint();
            if (!GlyphRuntimeTables.isValidCodepoint(codepoint)) {
                continue;
            }
            FontType fontType = result.getFontType();
            byte[] states = runtimeTables.stateArray(fontType);
            if (states[codepoint] != GlyphRuntimeTables.STATE_UPLOAD_PENDING) {
                continue;
            }
            if (!isCurrentGeneration(codepoint, fontType, upload.getGenerationId())) {
                continue;
            }

            GlyphPage glyphPage = allocatePage(fontType);
            int slotIndex = glyphPage.allocateSlot();
            glyphPage.upload(slotIndex, codepoint, fontType, result.getImage());
            int[] locations = runtimeTables.locationArray(fontType);
            byte[] flags = runtimeTables.flagsArray(fontType);
            float[] widths = runtimeTables.widthArray(fontType);
            locations[codepoint] = GlyphRuntimeTables.packLocation(glyphPage.getPageIndex(), slotIndex);
            flags[codepoint] = buildGlyphFlags(result.getGlyphInfo());
            cacheGeneratedWidth(widths, codepoint, result.getGlyphInfo());
            states[codepoint] = GlyphRuntimeTables.STATE_READY;
            runtimeTables.generationArray(fontType)[codepoint] = 0L;
            readyGlyphCount++;
            processed++;
        }
    }

    /**
     * 快照当前仍需要在 reload 后恢复的字符请求。
     *
     * @return 可恢复字符请求 packed 快照
     */
    public synchronized long[] snapshotRecoverableRequests() {
        int requestCount = countRecoverableRequests(runtimeTables.stateNormal)
                + countRecoverableRequests(runtimeTables.stateBold);
        if (requestCount <= 0) {
            return new long[0];
        }
        long[] requests = new long[requestCount];
        int offset = collectRecoverableRequests(requests, 0, runtimeTables.stateNormal, FontType.NORMAL);
        collectRecoverableRequests(requests, offset, runtimeTables.stateBold, FontType.BOLD);
        return requests;
    }

    /**
     * 查询字符是否已可用。
     *
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @return 是否可用
     */
    public synchronized boolean isReady(int codepoint, FontType fontType) {
        return GlyphRuntimeTables.isValidCodepoint(codepoint)
                && runtimeTables.stateArray(fontType)[codepoint] == GlyphRuntimeTables.STATE_READY;
    }

    /**
     * 获取字符状态。
     *
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @return 字符状态
     */
    public synchronized GlyphState getState(int codepoint, FontType fontType) {
        if (!GlyphRuntimeTables.isValidCodepoint(codepoint)) {
            return GlyphState.FAILED;
        }
        return toGlyphState(runtimeTables.stateArray(fontType)[codepoint]);
    }

    /**
     * 获取字形 packed location。
     *
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @return packed location，未就绪时返回 -1
     */
    public int getPackedLocation(int codepoint, FontType fontType) {
        if (!GlyphRuntimeTables.isValidCodepoint(codepoint)) {
            return GlyphRuntimeTables.LOCATION_NOT_READY;
        }
        return runtimeTables.locationArray(fontType)[codepoint];
    }

    /**
     * 根据 packed location 获取字形页。
     *
     * @param packedLocation packed location
     * @param fontType 字重类型
     * @return 字形页
     */
    public GlyphPage getPageByLocation(int packedLocation, FontType fontType) {
        if (packedLocation == GlyphRuntimeTables.LOCATION_NOT_READY) {
            return null;
        }
        int pageIndex = GlyphRuntimeTables.unpackPageIndex(packedLocation);
        GlyphPage[] pages = runtimeTables.pages(fontType);
        if (pageIndex < 0 || pageIndex >= runtimeTables.pageCount(fontType)) {
            return null;
        }
        GlyphPage page = pages[pageIndex];
        if (page == null || page.getRuntimeVersion() != runtimeVersion) {
            return null;
        }
        return page;
    }

    /**
     * 判断是否已初始化。
     *
     * @return 是否已初始化
     */
    public synchronized boolean isInitialized() {
        return initialized.get();
    }

    /**
     * 获取待上传数量。
     *
     * @return 待上传数量
     */
    public synchronized int getPendingUploadCount() {
        return pendingUploads.size();
    }

    /**
     * 获取已就绪字符数量。
     *
     * @return 已就绪字符数量
     */
    public synchronized int getReadyGlyphCount() {
        return readyGlyphCount;
    }

    /**
     * 获取普通字符页数量。
     *
     * @return 普通字符页数量
     */
    public synchronized int getNormalPageCount() {
        return runtimeTables.normalPageCount;
    }

    /**
     * 获取粗体字符页数量。
     *
     * @return 粗体字符页数量
     */
    public synchronized int getBoldPageCount() {
        return runtimeTables.boldPageCount;
    }

    private void configurePageGeometry() {
        textureSize = Math.max(64, (int) (FontConfig.awtCharSize * 64));
        glyphSize = Math.max(8, (int) FontConfig.awtCharSize);
        columnCount = Math.max(1, textureSize / glyphSize);
        rowCount = Math.max(1, textureSize / glyphSize);
    }

    private void ensureCapacity(FontType fontType) {
        int availableCount = 0;
        GlyphPage[] pages = runtimeTables.pages(fontType);
        int pageCount = runtimeTables.pageCount(fontType);
        for (int index = 0; index < pageCount; index++) {
            GlyphPage page = pages[index];
            if (page != null && page.canAllocate()) {
                availableCount++;
            }
        }

        while (availableCount < maintainPageCount) {
            int nextPageIndex = runtimeTables.pageCount(fontType);
            GlyphPage page = new GlyphPage(runtimeVersion, nextPageIndex, textureSize, glyphSize,
                    runtimeTables.slotXByIndex, runtimeTables.slotYByIndex);
            runtimeTables.setPage(fontType, nextPageIndex, page);
            availableCount++;
        }
    }

    private GlyphPage allocatePage(FontType fontType) {
        GlyphPage[] pages = runtimeTables.pages(fontType);
        int pageCount = runtimeTables.pageCount(fontType);
        for (int index = 0; index < pageCount; index++) {
            GlyphPage page = pages[index];
            if (page == null || page.getRuntimeVersion() != runtimeVersion) {
                continue;
            }
            if (page.canAllocate()) {
                return page;
            }
        }

        int nextPageIndex = pageCount;
        GlyphPage page = new GlyphPage(runtimeVersion, nextPageIndex, textureSize, glyphSize,
                runtimeTables.slotXByIndex, runtimeTables.slotYByIndex);
        runtimeTables.setPage(fontType, nextPageIndex, page);
        if (club.heiqi.uilib.Config.fontRuntimeDebug) {
            MyMod.LOG.info("字符页容量扩展，type={} pageIndex={}", fontType, Integer.valueOf(page.getPageIndex()));
        }
        ensureCapacity(fontType);
        return page;
    }

    private boolean isCurrentGeneration(int codepoint, FontType fontType, long generationId) {
        return runtimeTables.generationArray(fontType)[codepoint] == generationId;
    }

    private void cacheGeneratedWidth(float[] widths, int codepoint, GlyphInfo glyphInfo) {
        if (glyphInfo == null || glyphInfo.getWidth() <= 0 || !GlyphRuntimeTables.isValidCodepoint(codepoint)) {
            return;
        }
        widths[codepoint] = (float) (((glyphInfo.getAdvance() / glyphInfo.getWidth()) * FontConfig.charSize)
                + FontConfig.characterSpacing);
    }

    private byte buildGlyphFlags(GlyphInfo glyphInfo) {
        if (glyphInfo != null && glyphInfo.isColoredGlyph()) {
            return GlyphRuntimeTables.GLYPH_FLAG_COLORED;
        }
        return 0;
    }

    private int countRecoverableRequests(byte[] states) {
        int count = 0;
        for (byte state : states) {
            if (state == GlyphRuntimeTables.STATE_GENERATING || state == GlyphRuntimeTables.STATE_UPLOAD_PENDING) {
                count++;
            }
        }
        return count;
    }

    private int collectRecoverableRequests(long[] requests, int offset, byte[] states, FontType fontType) {
        int writeIndex = offset;
        for (int codepoint = 0; codepoint < states.length; codepoint++) {
            byte state = states[codepoint];
            if (state == GlyphRuntimeTables.STATE_GENERATING || state == GlyphRuntimeTables.STATE_UPLOAD_PENDING) {
                requests[writeIndex++] = packRecoverableRequest(codepoint, fontType);
            }
        }
        return writeIndex;
    }

    private static long packRecoverableRequest(int codepoint, FontType fontType) {
        long typeBit = fontType == FontType.BOLD ? 1L : 0L;
        return ((long) codepoint & 0x1FFFFFL) << 1 | typeBit;
    }

    /**
     * 从 packed 请求中解出码点。
     *
     * @param packedRequest packed 请求
     * @return 字符码点
     */
    public static int unpackRecoverableCodepoint(long packedRequest) {
        return (int) ((packedRequest >>> 1) & 0x1FFFFFL);
    }

    /**
     * 从 packed 请求中解出字重类型。
     *
     * @param packedRequest packed 请求
     * @return 字重类型
     */
    public static FontType unpackRecoverableFontType(long packedRequest) {
        return (packedRequest & 1L) != 0L ? FontType.BOLD : FontType.NORMAL;
    }

    private GlyphState toGlyphState(byte state) {
        switch (state) {
            case GlyphRuntimeTables.STATE_GENERATING:
                return GlyphState.GENERATING;
            case GlyphRuntimeTables.STATE_UPLOAD_PENDING:
                return GlyphState.UPLOAD_PENDING;
            case GlyphRuntimeTables.STATE_READY:
                return GlyphState.READY;
            case GlyphRuntimeTables.STATE_FAILED:
                return GlyphState.FAILED;
            default:
                return GlyphState.NEW;
        }
    }

    private void closePages(GlyphPage[] pages, int pageCount) {
        for (int index = 0; index < pageCount; index++) {
            GlyphPage page = pages[index];
            if (page != null) {
                page.close();
            }
        }
    }
}
