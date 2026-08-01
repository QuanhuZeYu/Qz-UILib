package club.heiqi.uilib.font.page;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.FontRuntimeAccess;
import club.heiqi.uilib.font.FontRuntimeMetrics;
import club.heiqi.uilib.font.FontRuntimeSettings;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.glyph.GlyphGenerationResult;
import club.heiqi.uilib.font.glyph.GlyphInfo;

/**
 * 字符页管理器。
 */
public class GlyphPageManager {

    private final Object ownerToken;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Queue<PendingGlyphUpload> pendingUploads = new ConcurrentLinkedQueue<PendingGlyphUpload>();
    private final List<GlyphPage> retiredPageRetries = new ArrayList<GlyphPage>();
    private final AtomicLong generationIdSequence = new AtomicLong(0L);

    /**
     * 唯一运行时字形表存储。
     *
     * <p>完整 Unicode direct-index arrays 只分配一次；generation commit 在外部读写屏障内撤销旧 lifecycle，
     * 再原地清理并把 storage 所有权转移给新 generation，避免 active/candidate 各持一份 123MiB 表。</p>
     */
    private final GlyphRuntimeTables runtimeTables = new GlyphRuntimeTables();
    private volatile FontRuntimeSettings runtimeSettings = FontRuntimeSettings.capture();
    private int textureSize;
    private int glyphSize;
    private int columnCount;
    private int rowCount;
    private int maintainPageCount = 3;
    private int readyGlyphCount;
    private volatile int runtimeVersion;

    /** 创建未绑定 owner 的独立字符页管理器。 */
    public GlyphPageManager() {
        this(null);
    }

    /**
     * 创建绑定字体 singleton owner 的字符页管理器。
     *
     * @param ownerToken 内部 owner token；独立测试对象可传 null
     */
    public GlyphPageManager(Object ownerToken) {
        this.ownerToken = ownerToken;
    }

    /**
     * 初始化字符页管理器。
     */
    public synchronized void initialize() {
        assertRuntimeAccess();
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
        assertRuntimeAccess();
        setGeneration(runtimeVersion, FontRuntimeSettings.capture());
    }

    /**
     * 在 generation barrier 内转移 table 与 atlas 所有权。
     *
     * @param runtimeVersion 新运行时版本
     * @param settings 新 generation 设置
     */
    public synchronized void setGeneration(int runtimeVersion, FontRuntimeSettings settings) {
        assertRuntimeAccess();
        if (settings == null) {
            throw new IllegalArgumentException("settings 不得为 null");
        }
        setGeneration(runtimeVersion, settings, FontRuntimeMetrics.prepare(settings, null));
    }

    /**
     * 在 generation barrier 内转移 table、atlas 与稳定度量所有权。
     *
     * @param runtimeVersion 新运行时版本
     * @param settings 新 generation 设置
     * @param metrics 新 generation 稳定行度量
     */
    public synchronized void setGeneration(int runtimeVersion, FontRuntimeSettings settings,
            FontRuntimeMetrics metrics) {
        assertRuntimeAccess();
        if (settings == null) {
            throw new IllegalArgumentException("settings 不得为 null");
        }
        if (metrics == null) {
            throw new IllegalArgumentException("metrics 不得为 null");
        }
        if (this.runtimeVersion == runtimeVersion && this.runtimeSettings == settings) {
            return;
        }
        reset(runtimeVersion, settings, metrics);
    }

    /**
     * 获取当前运行时直索引表。
     *
     * @return 运行时表
     */
    public GlyphRuntimeTables getRuntimeTables() {
        assertRuntimeAccess();
        return runtimeTables;
    }

    /**
     * 重置字符页状态。
     */
    public synchronized void reset() {
        assertRuntimeAccess();
        reset(runtimeVersion, runtimeSettings, FontRuntimeMetrics.prepare(runtimeSettings, null));
    }

    private void reset(int nextRuntimeVersion, FontRuntimeSettings nextSettings, FontRuntimeMetrics metrics) {
        retryRetiredPages();
        if (initialized.get()) {
            closePages(runtimeTables.normalPages, runtimeTables.normalPageCount);
            closePages(runtimeTables.boldPages, runtimeTables.boldPageCount);
        }
        pendingUploads.clear();
        runtimeTables.clearWidthCache();
        runtimeTables.clearMatchedFontCache();
        runtimeTables.resetGlyphRuntime();
        runtimeTables.setFontMetrics(metrics);
        runtimeSettings = nextSettings;
        runtimeVersion = nextRuntimeVersion;
        configurePageGeometry(nextSettings);
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
        assertRuntimeAccess();
        pendingUploads.clear();
    }

    /**
     * 尝试将字符切换到生成中状态。
     *
     * @param codepoint 字符码点
     * @param fontType  字重类型
     * @return 是否允许开始生成
     */
    public boolean tryMarkGenerating(int codepoint, FontType fontType) {
        assertRuntimeAccess();
        return tryMarkGenerating(runtimeVersion, codepoint, fontType);
    }

    /**
     * 尝试将指定运行时版本内的字符切换到生成中状态。
     *
     * @param runtimeVersion 运行时版本
     * @param codepoint      字符码点
     * @param fontType       字重类型
     * @return 是否允许开始生成
     */
    public synchronized boolean tryMarkGenerating(int runtimeVersion, int codepoint, FontType fontType) {
        assertRuntimeAccess();
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
     * @param codepoint      字符码点
     * @param fontType       字重类型
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
     * @param codepoint      字符码点
     * @param fontType       字重类型
     */
    public synchronized void markGenerationCancelled(int runtimeVersion, int codepoint, FontType fontType) {
        assertRuntimeAccess();
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
     * @param fontType  字重类型
     */
    public void markFailed(int codepoint, FontType fontType) {
        assertRuntimeAccess();
        markFailed(runtimeVersion, codepoint, fontType);
    }

    /**
     * 标记指定运行时版本内的字符生成失败。
     *
     * @param runtimeVersion 运行时版本
     * @param codepoint      字符码点
     * @param fontType       字重类型
     */
    public synchronized void markFailed(int runtimeVersion, int codepoint, FontType fontType) {
        assertRuntimeAccess();
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
        assertRuntimeAccess();
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
        assertRuntimeAccess();
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

            GlyphInfo glyphInfo = result.getGlyphInfo();
            int[] locations = runtimeTables.locationArray(fontType);
            byte[] flags = runtimeTables.flagsArray(fontType);
            flags[codepoint] = buildGlyphFlags(glyphInfo);
            if (glyphInfo == null || !glyphInfo.hasBitmap()) {
                locations[codepoint] = GlyphRuntimeTables.LOCATION_NO_BITMAP;
            } else {
                GlyphPage glyphPage = allocatePage(fontType, glyphInfo);
                GlyphPage.GlyphSlot slot = glyphPage.allocateSlot(glyphInfo.getSlotWidth(), glyphInfo.getSlotHeight());
                glyphPage.upload(slot, codepoint, fontType, result.getImage());
                locations[codepoint] = GlyphRuntimeTables.packLocation(glyphPage.getPageIndex(), slot.getSlotIndex());
                cacheGlyphGeometry(fontType, codepoint, slot, glyphInfo);
            }
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
     * @param fontType  字重类型
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
     * @param fontType  字重类型
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
     * @param fontType  字重类型
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
     * @param fontType       字重类型
     * @return 字形页
     */
    public GlyphPage getPageByLocation(int packedLocation, FontType fontType) {
        assertRuntimeAccess();
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
        configurePageGeometry(runtimeSettings);
    }

    private void configurePageGeometry(FontRuntimeSettings settings) {
        textureSize = settings.getTextureSize();
        glyphSize = settings.getPageGlyphSize();
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
                    runtimeSettings.getLerpMode());
            runtimeTables.setPage(fontType, nextPageIndex, page);
            availableCount++;
        }
    }

    private GlyphPage allocatePage(FontType fontType, GlyphInfo glyphInfo) {
        GlyphPage[] pages = runtimeTables.pages(fontType);
        int pageCount = runtimeTables.pageCount(fontType);
        for (int index = 0; index < pageCount; index++) {
            GlyphPage page = pages[index];
            if (page == null || page.getRuntimeVersion() != runtimeVersion) {
                continue;
            }
            if (page.canAllocate(glyphInfo.getSlotWidth(), glyphInfo.getSlotHeight())) {
                return page;
            }
        }

        int nextPageIndex = pageCount;
        GlyphPage page = new GlyphPage(runtimeVersion, nextPageIndex, textureSize, glyphSize,
                runtimeSettings.getLerpMode());
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

    private void cacheGlyphGeometry(FontType fontType, int codepoint, GlyphPage.GlyphSlot slot, GlyphInfo glyphInfo) {
        runtimeTables.slotXArray(fontType)[codepoint] = slot.getX();
        runtimeTables.slotYArray(fontType)[codepoint] = slot.getY();
        runtimeTables.slotWidthArray(fontType)[codepoint] = glyphInfo.getSlotWidth();
        runtimeTables.slotHeightArray(fontType)[codepoint] = glyphInfo.getSlotHeight();
        runtimeTables.atlasBaselineXArray(fontType)[codepoint] = glyphInfo.getAtlasBaselineX();
        runtimeTables.atlasBaselineYArray(fontType)[codepoint] = glyphInfo.getAtlasBaselineY();
        runtimeTables.lineBaselineYArray(fontType)[codepoint] = glyphInfo.getLineBaselineY();
        runtimeTables.inkWidthArray(fontType)[codepoint] = (short) glyphInfo.getGlyphWidth();
        runtimeTables.inkHeightArray(fontType)[codepoint] = (short) glyphInfo.getGlyphHeight();
        runtimeTables.bearingXArray(fontType)[codepoint] = (short) glyphInfo.getBearingX();
        runtimeTables.bearingYArray(fontType)[codepoint] = (short) glyphInfo.getBearingY();
    }

    private byte buildGlyphFlags(GlyphInfo glyphInfo) {
        byte flags = 0;
        if (glyphInfo != null && glyphInfo.isColoredGlyph()) {
            flags |= GlyphRuntimeTables.GLYPH_FLAG_COLORED;
        }
        if (glyphInfo != null && glyphInfo.hasBitmap()) {
            flags |= GlyphRuntimeTables.GLYPH_FLAG_HAS_BITMAP;
        }
        return flags;
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
                try {
                    page.close();
                } catch (RuntimeException exception) {
                    retiredPageRetries.add(page);
                    MyMod.LOG.warn("字体 atlas page 退休失败，保留所有权并在后续换代重试: runtimeVersion={} pageIndex={}",
                            Integer.valueOf(page.getRuntimeVersion()), Integer.valueOf(page.getPageIndex()), exception);
                }
            }
        }
    }

    private void retryRetiredPages() {
        Iterator<GlyphPage> iterator = retiredPageRetries.iterator();
        while (iterator.hasNext()) {
            GlyphPage page = iterator.next();
            try {
                page.close();
                iterator.remove();
            } catch (RuntimeException exception) {
                MyMod.LOG.warn("字体 atlas page 退休重试失败，继续保留所有权: runtimeVersion={} pageIndex={}",
                        Integer.valueOf(page.getRuntimeVersion()), Integer.valueOf(page.getPageIndex()), exception);
            }
        }
    }

    private void assertRuntimeAccess() {
        if (!FontRuntimeAccess.isActive(ownerToken)) {
            throw new IllegalStateException("GlyphPageManager 只能由字体 runtime owner 修改或读取内部 storage");
        }
    }
}
