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
import club.heiqi.uilib.font.FontRuntimeDiagnostics;
import club.heiqi.uilib.font.FontRuntimeMetrics;
import club.heiqi.uilib.font.FontRuntimeSettings;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.glyph.GlyphGenerationResult;
import club.heiqi.uilib.font.glyph.GlyphInfo;
import club.heiqi.uilib.font.glyph.GlyphRequestToken;

/**
 * 字符页管理器。
 */
public class GlyphPageManager {

    private final Object ownerToken;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Queue<PendingGlyphUpload> pendingUploads = new ConcurrentLinkedQueue<PendingGlyphUpload>();
    private final List<GlyphPage> retiredPageRetries = new ArrayList<GlyphPage>();
    private final AtomicLong requestIdSequence = new AtomicLong(0L);

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
        discardPendingUploads();
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
        PendingGlyphUpload upload;
        while ((upload = pendingUploads.poll()) != null) {
            markCancelled(upload.getToken(), GlyphState.UPLOAD_QUEUED);
        }
    }

    /**
     * 尝试领取当前 generation 的字符请求。
     *
     * @param codepoint 字符码点
     * @param fontType  字重类型
     * @return 原子领取的 token；已有活动请求或 glyph 已就绪时返回 null
     */
    public GlyphRequestToken claimRequest(int codepoint, FontType fontType) {
        assertRuntimeAccess();
        return claimRequest(runtimeVersion, codepoint, fontType);
    }

    /**
     * 尝试领取指定 generation 的字符请求。
     *
     * @param generation 字体运行时代际
     * @param codepoint      字符码点
     * @param fontType       字重类型
     * @return 原子领取的 token；generation 过期、已有活动请求或 glyph 已就绪时返回 null
     */
    public synchronized GlyphRequestToken claimRequest(int generation, int codepoint, FontType fontType) {
        assertRuntimeAccess();
        if (generation != runtimeVersion || fontType == null || !GlyphRuntimeTables.isValidCodepoint(codepoint)) {
            return null;
        }
        byte[] states = runtimeTables.stateArray(fontType);
        byte currentState = states[codepoint];
        if (isActiveState(currentState) || isReadyState(currentState)) {
            return null;
        }

        long requestId = nextRequestId();
        clearGlyphResidency(fontType, codepoint);
        runtimeTables.requestIdArray(fontType)[codepoint] = requestId;
        states[codepoint] = GlyphRuntimeTables.STATE_QUEUED;
        return new GlyphRequestToken(generation, requestId, codepoint, fontType);
    }

    /**
     * 将已入 worker 队列的请求切换到光栅化状态。
     *
     * @param token 请求 token
     * @return 是否由 QUEUED 成功切换为 RASTERIZING
     */
    public synchronized boolean markRasterizing(GlyphRequestToken token) {
        assertRuntimeAccess();
        return transition(token, GlyphState.QUEUED, GlyphState.RASTERIZING);
    }

    /**
     * 仅在 token 与 expected state 同时匹配时取消请求。
     *
     * @param token 请求 token
     * @param expectedState 调用方预期状态
     * @return 是否完成取消结算
     */
    public synchronized boolean markCancelled(GlyphRequestToken token, GlyphState expectedState) {
        assertRuntimeAccess();
        return isActiveState(expectedState) && transition(token, expectedState, GlyphState.CANCELLED_STALE);
    }

    /**
     * 仅在 token 与 expected state 同时匹配时标记请求失败。
     *
     * @param token 请求 token
     * @param expectedState 调用方预期状态
     * @return 是否完成失败结算
     */
    public synchronized boolean markFailed(GlyphRequestToken token, GlyphState expectedState) {
        assertRuntimeAccess();
        return isActiveState(expectedState) && transition(token, expectedState, GlyphState.FAILED);
    }

    /**
     * 接收后台线程生成完成的字符结果。
     *
     * @param result 字符生成结果
     * @return 是否接受当前 token 的结果
     */
    public synchronized boolean queueUpload(GlyphGenerationResult result) {
        assertRuntimeAccess();
        if (result == null || result.getToken() == null) {
            return false;
        }
        GlyphRequestToken token = result.getToken();
        if (!matches(token, GlyphState.RASTERIZING)) {
            FontRuntimeDiagnostics.logGlyphTokenRejection(token, "result", GlyphState.RASTERIZING,
                    getTokenState(token), "UPLOAD_QUEUE_REJECTED");
            return false;
        }
        pendingUploads.add(new PendingGlyphUpload(result));
        runtimeTables.stateArray(token.getFontType())[token.getCodepoint()] =
                GlyphRuntimeTables.STATE_UPLOAD_QUEUED;
        return true;
    }

    /**
     * 在主线程侧刷新待上传队列。
     *
     * @param maxCount 本次最多处理的数量
     */
    public synchronized void flushPendingUploads(int maxCount) {
        assertRuntimeAccess();
        int processed = 0;
        while (processed < maxCount) {
            PendingGlyphUpload upload = pendingUploads.poll();
            if (upload == null) {
                break;
            }
            processed++;

            GlyphGenerationResult result = upload.getGenerationResult();
            GlyphRequestToken token = upload.getToken();
            if (!transition(token, GlyphState.UPLOAD_QUEUED, GlyphState.UPLOADING)) {
                FontRuntimeDiagnostics.logGlyphTokenRejection(token, "upload_dequeue", GlyphState.UPLOAD_QUEUED,
                        getTokenState(token), "STALE_UPLOAD_RECORD");
                continue;
            }

            try {
                commitUpload(result);
            } catch (RuntimeException exception) {
                GlyphState actualState = getTokenState(token);
                boolean settled = markFailed(token, GlyphState.UPLOADING);
                FontRuntimeDiagnostics.logGlyphPipelineFailure(token, "upload", GlyphState.UPLOADING, actualState,
                        settled ? "UPLOAD_EXCEPTION_SETTLED" : "UPLOAD_EXCEPTION_STALE", exception);
                throw exception;
            } catch (Error error) {
                GlyphState actualState = getTokenState(token);
                boolean settled = markFailed(token, GlyphState.UPLOADING);
                FontRuntimeDiagnostics.logGlyphPipelineFailure(token, "upload", GlyphState.UPLOADING, actualState,
                        settled ? "UPLOAD_ERROR_SETTLED" : "UPLOAD_ERROR_STALE", error);
                throw error;
            }
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
                && isReadyState(runtimeTables.stateArray(fontType)[codepoint]);
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
     * 查询 token 当前状态；旧 token 或其他 generation 返回 null。
     *
     * @param token 请求 token
     * @return token 当前状态，token 已过期时返回 null
     */
    public synchronized GlyphState getTokenState(GlyphRequestToken token) {
        if (!isCurrentToken(token)) {
            return null;
        }
        return toGlyphState(runtimeTables.stateArray(token.getFontType())[token.getCodepoint()]);
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

    private void commitUpload(GlyphGenerationResult result) {
        GlyphRequestToken token = result.getToken();
        GlyphInfo glyphInfo = result.getGlyphInfo();
        if (glyphInfo == null || glyphInfo.getCodepoint() != token.getCodepoint()) {
            throw new IllegalArgumentException("glyph result 的 token 与 glyphInfo 不一致");
        }

        FontType fontType = token.getFontType();
        int codepoint = token.getCodepoint();
        byte flags = buildGlyphFlags(glyphInfo);
        if (!glyphInfo.hasBitmap()) {
            if (!matches(token, GlyphState.UPLOADING)) {
                FontRuntimeDiagnostics.logGlyphTokenRejection(token, "upload_commit", GlyphState.UPLOADING,
                        getTokenState(token), "NO_BITMAP_COMMIT_REJECTED");
                return;
            }
            runtimeTables.flagsArray(fontType)[codepoint] = flags;
            runtimeTables.locationArray(fontType)[codepoint] = GlyphRuntimeTables.LOCATION_NO_BITMAP;
            runtimeTables.stateArray(fontType)[codepoint] = GlyphRuntimeTables.STATE_NO_BITMAP;
            readyGlyphCount++;
            return;
        }

        if (result.getImage() == null || glyphInfo.getSlotWidth() <= 0 || glyphInfo.getSlotHeight() <= 0
                || result.getImage().getWidth() != glyphInfo.getSlotWidth()
                || result.getImage().getHeight() != glyphInfo.getSlotHeight()) {
            throw new IllegalArgumentException("bitmap glyph 的图像与 slot 尺寸不一致");
        }
        GlyphPage glyphPage = allocatePage(fontType, glyphInfo);
        GlyphPage.GlyphSlot slot = glyphPage.allocateSlot(glyphInfo.getSlotWidth(), glyphInfo.getSlotHeight());
        glyphPage.upload(slot, token, result.getImage());
        if (!matches(token, GlyphState.UPLOADING)) {
            FontRuntimeDiagnostics.logGlyphTokenRejection(token, "upload_commit", GlyphState.UPLOADING,
                    getTokenState(token), "RESIDENT_COMMIT_REJECTED");
            return;
        }
        cacheGlyphGeometry(fontType, codepoint, slot, glyphInfo);
        runtimeTables.flagsArray(fontType)[codepoint] = flags;
        runtimeTables.locationArray(fontType)[codepoint] =
                GlyphRuntimeTables.packLocation(glyphPage.getPageIndex(), slot.getSlotIndex());
        runtimeTables.stateArray(fontType)[codepoint] = GlyphRuntimeTables.STATE_RESIDENT;
        readyGlyphCount++;
    }

    private boolean transition(GlyphRequestToken token, GlyphState expectedState, GlyphState nextState) {
        if (!matches(token, expectedState)) {
            return false;
        }
        runtimeTables.stateArray(token.getFontType())[token.getCodepoint()] = stateByte(nextState);
        return true;
    }

    private boolean matches(GlyphRequestToken token, GlyphState expectedState) {
        return expectedState != null && isCurrentToken(token)
                && runtimeTables.stateArray(token.getFontType())[token.getCodepoint()] == stateByte(expectedState);
    }

    private boolean isCurrentToken(GlyphRequestToken token) {
        return token != null && token.getGeneration() == runtimeVersion
                && GlyphRuntimeTables.isValidCodepoint(token.getCodepoint())
                && runtimeTables.requestIdArray(token.getFontType())[token.getCodepoint()] == token.getRequestId();
    }

    private long nextRequestId() {
        long requestId = requestIdSequence.incrementAndGet();
        if (requestId == 0L) {
            requestId = requestIdSequence.incrementAndGet();
        }
        return requestId;
    }

    private void clearGlyphResidency(FontType fontType, int codepoint) {
        runtimeTables.locationArray(fontType)[codepoint] = GlyphRuntimeTables.LOCATION_NOT_READY;
        runtimeTables.flagsArray(fontType)[codepoint] = 0;
        runtimeTables.slotXArray(fontType)[codepoint] = 0;
        runtimeTables.slotYArray(fontType)[codepoint] = 0;
        runtimeTables.slotWidthArray(fontType)[codepoint] = 0;
        runtimeTables.slotHeightArray(fontType)[codepoint] = 0;
        runtimeTables.atlasBaselineXArray(fontType)[codepoint] = 0;
        runtimeTables.atlasBaselineYArray(fontType)[codepoint] = 0;
        runtimeTables.lineBaselineYArray(fontType)[codepoint] = 0;
        runtimeTables.inkWidthArray(fontType)[codepoint] = 0;
        runtimeTables.inkHeightArray(fontType)[codepoint] = 0;
        runtimeTables.bearingXArray(fontType)[codepoint] = 0;
        runtimeTables.bearingYArray(fontType)[codepoint] = 0;
    }

    private boolean isActiveState(byte state) {
        return state == GlyphRuntimeTables.STATE_QUEUED
                || state == GlyphRuntimeTables.STATE_RASTERIZING
                || state == GlyphRuntimeTables.STATE_UPLOAD_QUEUED
                || state == GlyphRuntimeTables.STATE_UPLOADING;
    }

    private boolean isActiveState(GlyphState state) {
        return state == GlyphState.QUEUED || state == GlyphState.RASTERIZING
                || state == GlyphState.UPLOAD_QUEUED || state == GlyphState.UPLOADING;
    }

    private boolean isReadyState(byte state) {
        return state == GlyphRuntimeTables.STATE_RESIDENT || state == GlyphRuntimeTables.STATE_NO_BITMAP;
    }

    private byte stateByte(GlyphState state) {
        switch (state) {
            case QUEUED:
                return GlyphRuntimeTables.STATE_QUEUED;
            case RASTERIZING:
                return GlyphRuntimeTables.STATE_RASTERIZING;
            case UPLOAD_QUEUED:
                return GlyphRuntimeTables.STATE_UPLOAD_QUEUED;
            case UPLOADING:
                return GlyphRuntimeTables.STATE_UPLOADING;
            case RESIDENT:
                return GlyphRuntimeTables.STATE_RESIDENT;
            case NO_BITMAP:
                return GlyphRuntimeTables.STATE_NO_BITMAP;
            case FAILED:
                return GlyphRuntimeTables.STATE_FAILED;
            case CANCELLED_STALE:
                return GlyphRuntimeTables.STATE_CANCELLED_STALE;
            default:
                return GlyphRuntimeTables.STATE_ABSENT;
        }
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
            if (isActiveState(state)) {
                count++;
            }
        }
        return count;
    }

    private int collectRecoverableRequests(long[] requests, int offset, byte[] states, FontType fontType) {
        int writeIndex = offset;
        for (int codepoint = 0; codepoint < states.length; codepoint++) {
            byte state = states[codepoint];
            if (isActiveState(state)) {
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
            case GlyphRuntimeTables.STATE_QUEUED:
                return GlyphState.QUEUED;
            case GlyphRuntimeTables.STATE_RASTERIZING:
                return GlyphState.RASTERIZING;
            case GlyphRuntimeTables.STATE_UPLOAD_QUEUED:
                return GlyphState.UPLOAD_QUEUED;
            case GlyphRuntimeTables.STATE_UPLOADING:
                return GlyphState.UPLOADING;
            case GlyphRuntimeTables.STATE_RESIDENT:
                return GlyphState.RESIDENT;
            case GlyphRuntimeTables.STATE_NO_BITMAP:
                return GlyphState.NO_BITMAP;
            case GlyphRuntimeTables.STATE_FAILED:
                return GlyphState.FAILED;
            case GlyphRuntimeTables.STATE_CANCELLED_STALE:
                return GlyphState.CANCELLED_STALE;
            default:
                return GlyphState.ABSENT;
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
