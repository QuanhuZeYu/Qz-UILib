package club.heiqi.uilib.font.page;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

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

    static final int PRIORITY_VISIBLE = 3;
    private static final int DEFAULT_DEMAND_PRIORITY = 2;
    private static final int DEFAULT_MAX_PENDING_UPLOADS = 256;
    private static final int DEFAULT_VISIBLE_RECORD_RESERVE = 32;
    private static final long DEFAULT_MAX_PENDING_BITMAP_BYTES = 16L * 1024L * 1024L;
    private static final long DEFAULT_VISIBLE_BITMAP_RESERVE = 4L * 1024L * 1024L;
    private static final long DEFAULT_MAILBOX_AGING_STEP_NANOS = 500L * 1000L * 1000L;
    private static final int DEFAULT_MAX_RESIDENT_ATLAS_PAGES = 8;
    private static final long DEFAULT_MAX_RESIDENT_ATLAS_BYTES = 512L * 1024L * 1024L;
    private static final long DEFAULT_UPLOAD_DRAIN_TIME_NANOS = TimeUnit.MILLISECONDS.toNanos(2L);
    private static final long DEFAULT_UPLOAD_DRAIN_BITMAP_BYTES = 2L * 1024L * 1024L;

    private final Object ownerToken;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Object mailboxLock = new Object();
    private final GlyphDemandRegistry demands = new GlyphDemandRegistry();
    private final List<GlyphPage> retiredPageRetries = new ArrayList<GlyphPage>();
    private final Set<GlyphPage> retainedAtlasOwnerships = new HashSet<GlyphPage>();
    private final BitSet normalAtlasPressureGlyphs = new BitSet(GlyphRuntimeTables.CODEPOINT_COUNT);
    private final BitSet boldAtlasPressureGlyphs = new BitSet(GlyphRuntimeTables.CODEPOINT_COUNT);
    private final AtomicLong requestIdSequence = new AtomicLong(0L);
    private final LongSupplier nanoTime;
    private final int maxResidentAtlasPages;
    private final long maxResidentAtlasBytes;
    private final long uploadDrainTimeBudgetNanos;
    private final long uploadDrainBitmapByteBudget;
    private int blockedPublisherCount;
    private int residentAtlasPageCount;
    private int retainedAtlasPageCount;
    private boolean normalAtlasPressure;
    private boolean boldAtlasPressure;
    private final GlyphStats stats = new GlyphStats();
    private final GlyphMailbox mailbox;

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
        this(ownerToken, DEFAULT_MAX_PENDING_UPLOADS, DEFAULT_MAX_PENDING_BITMAP_BYTES,
                DEFAULT_VISIBLE_RECORD_RESERVE, DEFAULT_VISIBLE_BITMAP_RESERVE,
                DEFAULT_MAILBOX_AGING_STEP_NANOS, DEFAULT_MAX_RESIDENT_ATLAS_PAGES,
                DEFAULT_MAX_RESIDENT_ATLAS_BYTES, DEFAULT_UPLOAD_DRAIN_TIME_NANOS,
                DEFAULT_UPLOAD_DRAIN_BITMAP_BYTES, System::nanoTime);
    }

    GlyphPageManager(int maxPendingUploads, long maxPendingBitmapBytes, int visibleRecordReserve,
            long visibleBitmapReserve, long mailboxAgingStepNanos, LongSupplier nanoTime) {
        this(null, maxPendingUploads, maxPendingBitmapBytes, visibleRecordReserve, visibleBitmapReserve,
                mailboxAgingStepNanos, DEFAULT_MAX_RESIDENT_ATLAS_PAGES, DEFAULT_MAX_RESIDENT_ATLAS_BYTES,
                DEFAULT_UPLOAD_DRAIN_TIME_NANOS, DEFAULT_UPLOAD_DRAIN_BITMAP_BYTES, nanoTime);
    }

    GlyphPageManager(int maxPendingUploads, long maxPendingBitmapBytes, int visibleRecordReserve,
            long visibleBitmapReserve, long mailboxAgingStepNanos, int maxResidentAtlasPages,
            long uploadDrainTimeBudgetNanos, long uploadDrainBitmapByteBudget, LongSupplier nanoTime) {
        this(null, maxPendingUploads, maxPendingBitmapBytes, visibleRecordReserve, visibleBitmapReserve,
                mailboxAgingStepNanos, maxResidentAtlasPages, DEFAULT_MAX_RESIDENT_ATLAS_BYTES,
                uploadDrainTimeBudgetNanos,
                uploadDrainBitmapByteBudget, nanoTime);
    }

    GlyphPageManager(int maxPendingUploads, long maxPendingBitmapBytes, int visibleRecordReserve,
            long visibleBitmapReserve, long mailboxAgingStepNanos, int maxResidentAtlasPages,
            long maxResidentAtlasBytes, long uploadDrainTimeBudgetNanos, long uploadDrainBitmapByteBudget,
            LongSupplier nanoTime) {
        this(null, maxPendingUploads, maxPendingBitmapBytes, visibleRecordReserve, visibleBitmapReserve,
                mailboxAgingStepNanos, maxResidentAtlasPages, maxResidentAtlasBytes, uploadDrainTimeBudgetNanos,
                uploadDrainBitmapByteBudget, nanoTime);
    }

    private GlyphPageManager(Object ownerToken, int maxPendingUploads, long maxPendingBitmapBytes,
            int visibleRecordReserve, long visibleBitmapReserve, long mailboxAgingStepNanos,
            int maxResidentAtlasPages, long maxResidentAtlasBytes, long uploadDrainTimeBudgetNanos,
            long uploadDrainBitmapByteBudget, LongSupplier nanoTime) {
        if (maxPendingUploads <= 0 || visibleRecordReserve < 0 || visibleRecordReserve >= maxPendingUploads
                || maxPendingBitmapBytes <= 0L || visibleBitmapReserve < 0L
                || visibleBitmapReserve >= maxPendingBitmapBytes || mailboxAgingStepNanos <= 0L
                || maxResidentAtlasPages <= 0 || maxResidentAtlasBytes <= 0L || uploadDrainTimeBudgetNanos <= 0L
                || uploadDrainBitmapByteBudget <= 0L || nanoTime == null) {
            throw new IllegalArgumentException("mailbox capacity/reserve/clock 配置无效");
        }
        this.ownerToken = ownerToken;
        this.mailbox = new GlyphMailbox(stats, maxPendingUploads, visibleRecordReserve, maxPendingBitmapBytes,
                visibleBitmapReserve, mailboxAgingStepNanos);
        this.maxResidentAtlasPages = maxResidentAtlasPages;
        this.maxResidentAtlasBytes = maxResidentAtlasBytes;
        this.uploadDrainTimeBudgetNanos = uploadDrainTimeBudgetNanos;
        this.uploadDrainBitmapByteBudget = uploadDrainBitmapByteBudget;
        this.nanoTime = nanoTime;
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
        if (!retiredPageRetries.isEmpty()) {
            throw new IllegalStateException("上一 retiring generation 仍持有 atlas page，拒绝退休当前 active generation");
        }
        if (initialized.get()) {
            closePages(runtimeTables.normalPages, runtimeTables.normalPageCount);
            closePages(runtimeTables.boldPages, runtimeTables.boldPageCount);
        }
        discardPendingUploads();
        demands.clear();
        runtimeTables.resetGlyphLifecycle();
        runtimeTables.setFontMetrics(metrics);
        runtimeSettings = nextSettings;
        runtimeVersion = nextRuntimeVersion;
        configurePageGeometry(nextSettings);
        runtimeTables.configureSlotCoordinates(columnCount, rowCount, glyphSize);
        readyGlyphCount = 0;
        residentAtlasPageCount = 0;
        normalAtlasPressure = false;
        boldAtlasPressure = false;
        normalAtlasPressureGlyphs.clear();
        boldAtlasPressureGlyphs.clear();
        if (initialized.get()) {
            ensureCapacity(FontType.NORMAL);
            ensureCapacity(FontType.BOLD);
        }
    }

    /**
     * 丢弃当前运行时尚未上传的字形结果。
     */
    public void discardPendingUploads() {
        assertRuntimeAccess();
        List<PendingGlyphUpload> discardedQueued;
        List<PendingGlyphUpload> discardedInFlight;
        synchronized (mailboxLock) {
            mailbox.advanceEpoch();
            discardedQueued = mailbox.snapshotPending();
            discardedInFlight = mailbox.snapshotInFlight();
            mailbox.clearAll();
            mailboxLock.notifyAll();
        }
        for (PendingGlyphUpload upload : discardedQueued) {
            markCancelled(upload.getToken(), GlyphState.UPLOAD_QUEUED);
        }
        for (PendingGlyphUpload upload : discardedInFlight) {
            if (!markCancelled(upload.getToken(), GlyphState.UPLOADING)) {
                markCancelled(upload.getToken(), GlyphState.UPLOAD_QUEUED);
            }
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
        return claimRequest(generation, codepoint, fontType, DEFAULT_DEMAND_PRIORITY);
    }

    /** 由 dispatcher 在同一次 claim 中登记内部 demand priority。 */
    public synchronized GlyphRequestToken claimRequest(int generation, int codepoint, FontType fontType,
            int demandPriority) {
        assertRuntimeAccess();
        if (generation != runtimeVersion || fontType == null || !GlyphRuntimeTables.isValidCodepoint(codepoint)
                || !isValidDemandPriority(demandPriority) || pressureGlyphs(fontType).get(codepoint)) {
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
        GlyphRequestToken token = new GlyphRequestToken(generation, requestId, codepoint, fontType);
        demands.put(generation, codepoint, fontType, token, demandPriority);
        return token;
    }

    /**
     * 仅提升当前 active token 的 priority；同级/降级或无 active token 时返回 null。
     */
    public GlyphRequestToken promoteDemand(int generation, int codepoint, FontType fontType, int demandPriority) {
        assertRuntimeAccess();
        if (fontType == null || !isValidDemandPriority(demandPriority)) {
            return null;
        }
        GlyphDemandRegistry.ActiveGlyphDemand demand;
        synchronized (this) {
            demand = demands.get(generation, codepoint, fontType);
            if (demand == null || !matchesActiveDemand(demand)
                    || demand.priority.get() >= demandPriority) {
                return null;
            }
            demand.priority.set(demandPriority);
        }
        synchronized (mailboxLock) {
            mailboxLock.notifyAll();
        }
        return demand.token;
    }

    /** 判断同 key 是否已有 active token，用于 dispatcher 在 claim 前 coalesce。 */
    public synchronized boolean hasActiveDemand(int generation, int codepoint, FontType fontType) {
        if (fontType == null) {
            return false;
        }
        GlyphDemandRegistry.ActiveGlyphDemand demand = demands.get(generation, codepoint, fontType);
        return demand != null && matchesActiveDemand(demand);
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
    public boolean queueUpload(GlyphGenerationResult result) {
        assertRuntimeAccess();
        if (result == null || result.getToken() == null) {
            return false;
        }
        GlyphRequestToken token = result.getToken();
        GlyphDemandRegistry.ActiveGlyphDemand demand;
        synchronized (this) {
            if (!matches(token, GlyphState.RASTERIZING)) {
                FontRuntimeDiagnostics.logGlyphTokenRejection(token, "result", GlyphState.RASTERIZING,
                        getTokenState(token), "UPLOAD_QUEUE_REJECTED");
                return false;
            }
            demand = demands.get(token);
            if (demand == null || !demand.token.equals(token)) {
                return false;
            }
        }

        long bitmapBytes = estimateUploadPlanBytes(result);
        int admissionPriority;
        String rejectionReason = null;
        synchronized (this) {
            if (!matches(token, GlyphState.RASTERIZING) || !matchesActiveDemand(demand)) {
                return false;
            }
            admissionPriority = demand.priority.get();
            long priorityByteLimit = admissionPriority == PRIORITY_VISIBLE
                    ? mailbox.getMaxPendingBitmapBytes()
                    : mailbox.getMaxPendingBitmapBytes() - mailbox.getVisibleBitmapReserve();
            if (bitmapBytes > priorityByteLimit) {
                rejectionReason = admissionPriority == PRIORITY_VISIBLE
                        ? "BITMAP_OVERSIZED_REJECTED" : "BITMAP_EXCEEDS_NON_VISIBLE_PARTITION";
                transition(token, GlyphState.RASTERIZING, GlyphState.FAILED);
            }
        }
        if (rejectionReason != null) {
            synchronized (mailboxLock) {
                stats.recordMailboxRejection();
            }
            FontRuntimeDiagnostics.logGlyphCapacityEvent(token, null, "result_admission",
                    priorityName(admissionPriority), getPendingUploadCount(), mailbox.getMaxPendingUploads(),
                    bitmapBytes, mailbox.getMaxPendingBitmapBytes(), rejectionReason);
            return false;
        }

        long enqueueSequence;
        synchronized (mailboxLock) {
            enqueueSequence = mailbox.nextSequence();
        }
        long enqueuedNanos = nanoTime.getAsLong();
        long reservedEpoch;
        boolean waitLogged = false;
        synchronized (mailboxLock) {
            reservedEpoch = mailbox.getEpoch();
        }
        while (true) {
            boolean rejectNonVisible = false;
            synchronized (mailboxLock) {
                if (reservedEpoch != mailbox.getEpoch() || !demand.active.get()) {
                    return false;
                }
                int currentPriority = demand.priority.get();
                if (mailbox.hasCapacity(currentPriority, bitmapBytes)) {
                    mailbox.reserve(bitmapBytes);
                    break;
                }
                if (currentPriority != PRIORITY_VISIBLE) {
                    rejectNonVisible = true;
                } else {
                    stats.recordMailboxBackpressure();
                    blockedPublisherCount++;
                    if (!waitLogged) {
                        waitLogged = true;
                        FontRuntimeDiagnostics.logGlyphCapacityEvent(token, null, "result_backpressure",
                                priorityName(currentPriority), mailbox.getReservedUploadCount(),
                                mailbox.getMaxPendingUploads(), mailbox.getReservedBytes(),
                                mailbox.getMaxPendingBitmapBytes(), "MAILBOX_WAIT");
                    }
                    try {
                        mailboxLock.wait();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return false;
                    } finally {
                        blockedPublisherCount--;
                    }
                }
            }
            if (rejectNonVisible) {
                int rejectedPriority;
                synchronized (this) {
                    if (reservedEpoch != mailbox.getEpoch() || !matches(token, GlyphState.RASTERIZING)
                            || !matchesActiveDemand(demand)) {
                        return false;
                    }
                    rejectedPriority = demand.priority.get();
                    if (rejectedPriority == PRIORITY_VISIBLE) {
                        continue;
                    }
                    if (!transition(token, GlyphState.RASTERIZING, GlyphState.FAILED)) {
                        return false;
                    }
                }
                synchronized (mailboxLock) {
                    stats.recordMailboxRejection();
                }
                FontRuntimeDiagnostics.logGlyphCapacityEvent(token, null, "result_admission",
                        priorityName(rejectedPriority), getPendingUploadCount(), mailbox.getMaxPendingUploads(),
                        bitmapBytes, mailbox.getMaxPendingBitmapBytes(), "NON_VISIBLE_CAPACITY_REJECTED");
                return false;
            }
        }

        final GlyphUploadPlan uploadPlan;
        try {
            uploadPlan = GlyphUploadPlan.from(result);
        } catch (RuntimeException exception) {
            releaseMailboxReservation(reservedEpoch, bitmapBytes);
            throw exception;
        } catch (Error error) {
            releaseMailboxReservation(reservedEpoch, bitmapBytes);
            throw error;
        }
        if (uploadPlan.getBitmapBytes() != bitmapBytes) {
            releaseMailboxReservation(reservedEpoch, bitmapBytes);
            throw new IllegalStateException("glyph upload plan bytes 在 admission 后发生变化");
        }
        final PendingGlyphUpload upload = new PendingGlyphUpload(uploadPlan, demand.priority,
                enqueueSequence, enqueuedNanos, reservedEpoch);

        boolean transitioned;
        synchronized (this) {
            transitioned = reservedEpoch == mailbox.getEpoch()
                    && transition(token, GlyphState.RASTERIZING, GlyphState.UPLOAD_QUEUED);
        }
        if (!transitioned) {
            releaseMailboxReservation(reservedEpoch, bitmapBytes);
            return false;
        }

        try {
            synchronized (mailboxLock) {
                if (reservedEpoch != mailbox.getEpoch()) {
                    // discard 已按 epoch 原子清零旧 reservation，不能再扣减新 epoch 的计数。
                } else {
                    mailbox.enqueue(upload);
                    mailboxLock.notifyAll();
                    return true;
                }
            }
        } catch (RuntimeException exception) {
            settleMailboxPublicationFailure(token, reservedEpoch, bitmapBytes, exception);
            throw exception;
        } catch (Error error) {
            settleMailboxPublicationFailure(token, reservedEpoch, bitmapBytes, error);
            throw error;
        }
        markCancelled(token, GlyphState.UPLOAD_QUEUED);
        FontRuntimeDiagnostics.logGlyphTokenEvent(token, "result", GlyphState.UPLOAD_QUEUED,
                getTokenState(token), "MAILBOX_EPOCH_CANCELLED");
        return false;
    }

    private void settleMailboxPublicationFailure(GlyphRequestToken token, long reservedEpoch, long bitmapBytes,
            Throwable throwable) {
        releaseMailboxReservation(reservedEpoch, bitmapBytes);
        GlyphState actualState = getTokenState(token);
        boolean settled = markFailed(token, GlyphState.UPLOAD_QUEUED);
        FontRuntimeDiagnostics.logGlyphPipelineFailure(token, "result_publish", GlyphState.UPLOAD_QUEUED,
                actualState, settled ? "MAILBOX_PUBLICATION_SETTLED" : "MAILBOX_PUBLICATION_STALE", throwable);
    }

    /**
     * 在主线程侧刷新待上传队列。
     *
     * @param maxCount 本次最多处理的数量
     */
    public synchronized void flushPendingUploads(int maxCount) {
        assertRuntimeAccess();
        if (maxCount <= 0 && ownerToken != null) {
            retryRetiredPages();
            if (!retiredPageRetries.isEmpty()) {
                throw new RejectedExecutionException("retiring generation 仍持有 atlas page");
            }
            return;
        }
        long startedNanos = nanoTime.getAsLong();
        int attempts = 0;
        long attemptedBitmapBytes = 0L;
        String stopReason = "EMPTY";
        GlyphRequestToken lastToken = null;
        Map<GlyphPage, FontType> batchPages = new HashMap<GlyphPage, FontType>();
        try {
            while (true) {
                if (attempts >= Math.max(0, maxCount)) {
                    stopReason = "ATTEMPT_BUDGET";
                    stats.recordUploadAttemptBudgetExhausted();
                    break;
                }
                if (attempts > 0 && elapsedNanos(startedNanos, nanoTime.getAsLong()) >= uploadDrainTimeBudgetNanos) {
                    stopReason = "TIME_BUDGET";
                    stats.recordUploadTimeBudgetExhausted();
                    break;
                }
                GlyphMailbox.Poll poll = pollNextUpload(attemptedBitmapBytes, attempts == 0);
                if (poll.stopReason != null) {
                    stopReason = poll.stopReason;
                    if ("BYTE_BUDGET".equals(stopReason)) {
                        stats.recordUploadByteBudgetExhausted();
                    }
                    break;
                }
                PendingGlyphUpload upload = poll.upload;
                GlyphRequestToken token = upload.getToken();
                lastToken = token;
                attempts++;
                attemptedBitmapBytes = saturatedAdd(attemptedBitmapBytes, upload.getBitmapBytes());
                UploadAttemptContext context = new UploadAttemptContext(attempts, attemptedBitmapBytes,
                        maxCount, uploadDrainBitmapByteBudget, uploadDrainTimeBudgetNanos);
                if (!transition(token, GlyphState.UPLOAD_QUEUED, GlyphState.UPLOADING)) {
                    FontRuntimeDiagnostics.logGlyphTokenRejection(token, "upload_dequeue", GlyphState.UPLOAD_QUEUED,
                            getTokenState(token), "STALE_UPLOAD_RECORD");
                    completeUploadLease(upload);
                    continue;
                }

                try {
                    commitUpload(upload, context, batchPages);
                    completeUploadLease(upload);
                } catch (RuntimeException exception) {
                    completeUploadLease(upload);
                    GlyphState actualState = getTokenState(token);
                    boolean settled = markFailed(token, GlyphState.UPLOADING);
                    FontRuntimeDiagnostics.logGlyphPipelineFailure(token, "upload", GlyphState.UPLOADING, actualState,
                            settled ? "UPLOAD_TRANSACTION_ROLLED_BACK" : "UPLOAD_EXCEPTION_STALE", exception,
                            context.describe());
                    stopReason = "ERROR";
                    throw exception;
                } catch (Error error) {
                    completeUploadLease(upload);
                    GlyphState actualState = getTokenState(token);
                    boolean settled = markFailed(token, GlyphState.UPLOADING);
                    FontRuntimeDiagnostics.logGlyphPipelineFailure(token, "upload", GlyphState.UPLOADING, actualState,
                            settled ? "UPLOAD_TRANSACTION_ROLLED_BACK" : "UPLOAD_ERROR_STALE", error,
                            context.describe());
                    stopReason = "ERROR";
                    throw error;
                }
            }
        } finally {
            closeUploadBatches(batchPages);
            long elapsedNanos = elapsedNanos(startedNanos, nanoTime.getAsLong());
            FontRuntimeDiagnostics.logGlyphUploadDrain(lastToken, attempts, attemptedBitmapBytes, maxCount,
                    uploadDrainBitmapByteBudget, elapsedNanos, uploadDrainTimeBudgetNanos, atlasOwnedPageCount(),
                    maxResidentAtlasPages, atlasPressureName(), stopReason, stats.getUploadRollbackCount(),
                    stats.getAtlasPressureCount(), stats.getUploadAttemptBudgetExhaustedCount(),
                    stats.getUploadByteBudgetExhaustedCount(), stats.getUploadTimeBudgetExhaustedCount());
        }
    }

    /**
     * 结束本次 flush 涉及的全部批上传页；页结算失败（mipmap/校验/状态恢复异常）时执行页级灾难恢复。
     */
    private void closeUploadBatches(Map<GlyphPage, FontType> batchPages) {
        for (Map.Entry<GlyphPage, FontType> entry : batchPages.entrySet()) {
            GlyphPage page = entry.getKey();
            try {
                page.endBatchUpload();
            } catch (RuntimeException exception) {
                quarantineBatchFailedPage(page, entry.getValue(), exception);
            } catch (Error error) {
                quarantineBatchFailedPage(page, entry.getValue(), error);
            }
        }
    }

    /** 批结算失败的页整体失效：清 residency、关闭纹理并按现有压力机制重新 demand。 */
    private void quarantineBatchFailedPage(GlyphPage page, FontType fontType, Throwable cause) {
        MyMod.LOG.warn("字体 atlas 批上传结算失败，整页失效: runtimeVersion={} pageIndex={}",
                Integer.valueOf(page.getRuntimeVersion()), Integer.valueOf(page.getPageIndex()), cause);
        try {
            quarantineAtlasPage(fontType, page);
        } catch (RuntimeException quarantineFailure) {
            cause.addSuppressed(quarantineFailure);
        } catch (Error quarantineFailure) {
            cause.addSuppressed(quarantineFailure);
        }
    }

    /**
     * 快照当前仍需要在 reload 后恢复的字符请求。
     *
     * @return 可恢复字符请求 packed 快照
     */
    public synchronized long[] snapshotRecoverableRequests() {
        int requestCount = countRecoverableRequests(runtimeTables.stateNormal)
                + countRecoverableRequests(runtimeTables.stateBold)
                + normalAtlasPressureGlyphs.cardinality() + boldAtlasPressureGlyphs.cardinality();
        if (requestCount <= 0) {
            return new long[0];
        }
        long[] requests = new long[requestCount];
        int offset = collectRecoverableRequests(requests, 0, runtimeTables.stateNormal, FontType.NORMAL);
        offset = collectRecoverableRequests(requests, offset, runtimeTables.stateBold, FontType.BOLD);
        offset = collectPressureRequests(requests, offset, normalAtlasPressureGlyphs, FontType.NORMAL);
        collectPressureRequests(requests, offset, boldAtlasPressureGlyphs, FontType.BOLD);
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
        int slotIndex = GlyphRuntimeTables.unpackSlotIndex(packedLocation);
        if (page == null || page.getRuntimeVersion() != runtimeVersion
                || page.isAllocationClosed() || slotIndex < 0 || slotIndex >= page.getCommittedSlotCount()) {
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
    public int getPendingUploadCount() {
        synchronized (mailboxLock) {
            return mailbox.pendingCount();
        }
    }

    int getInFlightUploadCount() {
        synchronized (mailboxLock) {
            return mailbox.inFlightCount();
        }
    }

    public long getPendingBitmapBytes() {
        synchronized (mailboxLock) {
            return mailbox.getReservedBytes();
        }
    }

    public int getMaxPendingUploadCount() {
        return mailbox.getMaxPendingUploads();
    }

    public long getMaxPendingBitmapBytes() {
        return mailbox.getMaxPendingBitmapBytes();
    }

    public int getPendingUploadHighWaterMark() {
        synchronized (mailboxLock) {
            return (int) stats.getPendingUploadHighWaterMark();
        }
    }

    public long getPendingBitmapBytesHighWaterMark() {
        synchronized (mailboxLock) {
            return stats.getPendingBitmapBytesHighWaterMark();
        }
    }

    public int getBlockedPublisherCount() {
        synchronized (mailboxLock) {
            return blockedPublisherCount;
        }
    }

    public long getMailboxBackpressureCount() {
        synchronized (mailboxLock) {
            return stats.getMailboxBackpressureCount();
        }

    }

    public long getMailboxRejectedCount() {
        synchronized (mailboxLock) {
            return stats.getMailboxRejectedCount();
        }
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

    synchronized int getResidentAtlasPageCount() {
        return residentAtlasPageCount + retainedAtlasPageCount;
    }

    /**
     * 当前各页最大已分配槽位数（紧密排列后的实际统计口径）。
     *
     * <p>skyline 装箱下单页槽位数量随字形尺寸分布变化，不再由网格预算固定；
     * 本方法遍历 active 页取 committed slot 峰值，供 {@code FontRuntimeStats} 快照。</p>
     *
     * @return 最大已分配槽位数，无页时为 0
     */
    public synchronized int getMaxCommittedSlotsPerPage() {
        assertRuntimeAccess();
        int maxSlots = 0;
        for (FontType fontType : FontType.values()) {
            GlyphPage[] pages = runtimeTables.pages(fontType);
            int pageCount = runtimeTables.pageCount(fontType);
            for (int index = 0; index < pageCount; index++) {
                GlyphPage page = pages[index];
                if (page != null) {
                    maxSlots = Math.max(maxSlots, page.getCommittedSlotCount());
                }
            }
        }
        return maxSlots;
    }

    synchronized boolean isAtlasPressure(FontType fontType) {
        return fontType == FontType.BOLD ? boldAtlasPressure : normalAtlasPressure;
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

    private AtlasReservation reserveAtlasSlot(GlyphRequestToken token, GlyphInfo glyphInfo,
            UploadAttemptContext context) {
        FontType fontType = token.getFontType();
        int slotWidth = glyphInfo.getSlotWidth();
        int slotHeight = glyphInfo.getSlotHeight();
        if (slotWidth > textureSize || slotHeight > textureSize) {
            throw new IllegalArgumentException("glyph slot 尺寸超过 atlas texture");
        }
        GlyphPage[] pages = runtimeTables.pages(fontType);
        int pageCount = runtimeTables.pageCount(fontType);
        for (int index = 0; index < pageCount; index++) {
            GlyphPage page = pages[index];
            if (page == null || page.getRuntimeVersion() != runtimeVersion
                    || page.getCommittedSlotCount() == 0 && !page.hasTextureOwnership()) {
                continue;
            }
            if (page.canAllocate(slotWidth, slotHeight)) {
                return new AtlasReservation(fontType, page, page.reserveSlot(slotWidth, slotHeight), false, false);
            }
        }

        String activationPressure = atlasActivationPressureReason();
        if (activationPressure != null) {
            markAtlasPressure(token, context, activationPressure);
            return null;
        }
        for (int index = 0; index < pageCount; index++) {
            GlyphPage page = pages[index];
            if (page == null || page.getRuntimeVersion() != runtimeVersion || page.getCommittedSlotCount() != 0) {
                continue;
            }
            if (page.canAllocate(slotWidth, slotHeight)) {
                return new AtlasReservation(fontType, page, page.reserveSlot(slotWidth, slotHeight), false, true);
            }
        }

        int nextPageIndex = pageCount;
        GlyphPage page = new GlyphPage(runtimeVersion, nextPageIndex, textureSize, glyphSize,
                runtimeSettings.getLerpMode());
        return new AtlasReservation(fontType, page, page.reserveSlot(slotWidth, slotHeight), true, true);
    }

    private UploadOutcome commitUpload(PendingGlyphUpload upload, UploadAttemptContext context,
            Map<GlyphPage, FontType> batchPages) {
        GlyphUploadPlan plan = upload.getUploadPlan();
        GlyphRequestToken token = plan.getToken();
        GlyphInfo glyphInfo = plan.getGlyphInfo();
        FontType fontType = token.getFontType();
        int codepoint = token.getCodepoint();
        byte flags = buildGlyphFlags(glyphInfo);
        if (!glyphInfo.hasBitmap()) {
            synchronized (mailboxLock) {
                if (isUploadLeaseCurrentLocked(upload) && matches(token, GlyphState.UPLOADING)) {
                    runtimeTables.flagsArray(fontType)[codepoint] = flags;
                    runtimeTables.locationArray(fontType)[codepoint] = GlyphRuntimeTables.LOCATION_NO_BITMAP;
                    runtimeTables.stateArray(fontType)[codepoint] = GlyphRuntimeTables.STATE_NO_BITMAP;
                    removeActiveDemand(token);
                    readyGlyphCount++;
                    return UploadOutcome.COMMITTED;
                }
            }
            markCancelled(token, GlyphState.UPLOADING);
            FontRuntimeDiagnostics.logGlyphTokenRejection(token, "upload_commit", GlyphState.UPLOADING,
                    getTokenState(token), "NO_BITMAP_COMMIT_REJECTED");
            return UploadOutcome.STALE;
        }

        AtlasReservation reservation = reserveAtlasSlot(token, glyphInfo, context);
        if (reservation == null) {
            synchronized (mailboxLock) {
                if (isUploadLeaseCurrentLocked(upload) && settleAtlasPressure(token)) {
                    return UploadOutcome.ATLAS_PRESSURE;
                }
            }
            markCancelled(token, GlyphState.UPLOADING);
            return UploadOutcome.STALE;
        }
        context.setSlot(reservation.page.getPageIndex(), reservation.slotReservation.getSlot().getSlotIndex());
        try {
            GlyphPage uploadPage = reservation.page;
            if (!uploadPage.isBatchActive()) {
                uploadPage.beginBatchUpload();
                batchPages.put(uploadPage, fontType);
            }
            uploadPage.upload(reservation.slotReservation.getSlot(), plan);
            boolean committed = false;
            boolean leaseCurrent;
            synchronized (mailboxLock) {
                leaseCurrent = isUploadLeaseCurrentLocked(upload);
                if (leaseCurrent && matches(token, GlyphState.UPLOADING)) {
                    reservation.commit();
                    GlyphPage.GlyphSlot slot = reservation.slotReservation.getSlot();
                    cacheGlyphGeometry(fontType, codepoint, slot, glyphInfo);
                    runtimeTables.flagsArray(fontType)[codepoint] = flags;
                    runtimeTables.locationArray(fontType)[codepoint] =
                            GlyphRuntimeTables.packLocation(reservation.page.getPageIndex(), slot.getSlotIndex());
                    reservation.seal();
                    runtimeTables.stateArray(fontType)[codepoint] = GlyphRuntimeTables.STATE_RESIDENT;
                    removeActiveDemand(token);
                    readyGlyphCount++;
                    committed = true;
                }
            }
            if (!committed) {
                context.rollbackReason = leaseCurrent ? "TOKEN_STALE_AFTER_GL" : "MAILBOX_EPOCH_STALE_AFTER_GL";
                uploadPage.rollbackUploadedRegion(reservation.slotReservation.getSlot());
                rollbackUpload(reservation, fontType, codepoint, context, null);
                markCancelled(token, GlyphState.UPLOADING);
                FontRuntimeDiagnostics.logGlyphTokenRejection(token, "upload_commit", GlyphState.UPLOADING,
                        getTokenState(token), "RESIDENT_COMMIT_REJECTED");
                FontRuntimeDiagnostics.logGlyphUploadTransaction(token, "upload_rollback", context.pageIndex,
                        context.slotIndex, context.attempt, context.attemptedBitmapBytes,
                        context.maxAttempts, context.maxBitmapBytes, context.maxNanos,
                        atlasPressureName(), context.rollbackReason);
                return UploadOutcome.STALE;
            }
            return UploadOutcome.COMMITTED;
        } catch (RuntimeException exception) {
            context.rollbackReason = uploadRollbackReason(exception);
            if (!reservation.isRolledBack()) {
                rollbackUpload(reservation, fontType, codepoint, context, exception);
            }
            throw exception;
        } catch (Error error) {
            context.rollbackReason = uploadRollbackReason(error);
            if (!reservation.isRolledBack()) {
                rollbackUpload(reservation, fontType, codepoint, context, error);
            }
            throw error;
        }
    }

    private void markAtlasPressure(GlyphRequestToken token, UploadAttemptContext context, String reason) {
        FontType fontType = token.getFontType();
        if (fontType == FontType.BOLD) {
            boldAtlasPressure = true;
        } else {
            normalAtlasPressure = true;
        }
        stats.recordAtlasPressure();
        context.pressure = reason;
        context.rollbackReason = "ATLAS_PRESSURE_RETRY";
        FontRuntimeDiagnostics.logGlyphUploadTransaction(token, "atlas_reservation", -1, -1,
                context.attempt, context.attemptedBitmapBytes, context.maxAttempts, context.maxBitmapBytes,
                context.maxNanos, atlasPressureName(), context.rollbackReason);
    }

    private boolean settleAtlasPressure(GlyphRequestToken token) {
        if (!matches(token, GlyphState.UPLOADING)) {
            return false;
        }
        pressureGlyphs(token.getFontType()).set(token.getCodepoint());
        runtimeTables.stateArray(token.getFontType())[token.getCodepoint()] = GlyphRuntimeTables.STATE_ABSENT;
        removeActiveDemand(token);
        return true;
    }

    private BitSet pressureGlyphs(FontType fontType) {
        return fontType == FontType.BOLD ? boldAtlasPressureGlyphs : normalAtlasPressureGlyphs;
    }

    private void rollbackUpload(AtlasReservation reservation, FontType fontType, int codepoint,
            UploadAttemptContext context, Throwable originalFailure) {
        clearGlyphResidency(fontType, codepoint);
        Throwable rollbackFailure = null;
        boolean firstRollback = !reservation.isRolledBack();
        try {
            reservation.rollback();
        } catch (RuntimeException exception) {
            rollbackFailure = exception;
        } catch (Error error) {
            rollbackFailure = error;
        }
        if (!reservation.activatesResidentPage && reservation.page.isAllocationClosed()) {
            try {
                quarantineAtlasPage(fontType, reservation.page);
            } catch (RuntimeException exception) {
                rollbackFailure = appendFailure(rollbackFailure, exception);
            } catch (Error error) {
                rollbackFailure = appendFailure(rollbackFailure, error);
            }
        }
        if (firstRollback) {
            stats.recordUploadRollback();
        }
        if (rollbackFailure != null) {
            if (originalFailure != null) {
                originalFailure.addSuppressed(rollbackFailure);
            } else {
                throwUnchecked(rollbackFailure);
            }
        }
    }

    private void quarantineAtlasPage(FontType fontType, GlyphPage page) {
        byte[] states = runtimeTables.stateArray(fontType);
        int[] locations = runtimeTables.locationArray(fontType);
        int pageIndex = page.getPageIndex();
        for (int codepoint = 0; codepoint < states.length; codepoint++) {
            if (states[codepoint] == GlyphRuntimeTables.STATE_RESIDENT
                    && GlyphRuntimeTables.unpackPageIndex(locations[codepoint]) == pageIndex) {
                clearGlyphResidency(fontType, codepoint);
                states[codepoint] = GlyphRuntimeTables.STATE_ABSENT;
                readyGlyphCount--;
            }
        }
        page.close();
        if (residentAtlasPageCount > 0) {
            residentAtlasPageCount--;
        }
        releaseAtlasPressureIfCapacityAvailable();
    }

    private void releaseAtlasPressureIfCapacityAvailable() {
        if (atlasActivationPressureReason() != null) {
            return;
        }
        normalAtlasPressureGlyphs.clear();
        boldAtlasPressureGlyphs.clear();
        normalAtlasPressure = false;
        boldAtlasPressure = false;
    }

    private String uploadRollbackReason(Throwable throwable) {
        if (throwable instanceof GlyphPage.GlyphUploadException) {
            GlyphPage.GlyphUploadException uploadException = (GlyphPage.GlyphUploadException) throwable;
            return "GL_" + uploadException.getPhase() + '_' + uploadException.getGlError();
        }
        return "JAVA_" + throwable.getClass().getSimpleName();
    }

    private String atlasPressureName() {
        if (normalAtlasPressure && boldAtlasPressure) {
            return "NORMAL+BOLD";
        }
        if (normalAtlasPressure) {
            return "NORMAL";
        }
        if (boldAtlasPressure) {
            return "BOLD";
        }
        return "NONE";
    }

    private int atlasOwnedPageCount() {
        return residentAtlasPageCount + retainedAtlasPageCount;
    }

    private String atlasActivationPressureReason() {
        if (atlasOwnedPageCount() >= maxResidentAtlasPages) {
            return "RESIDENT_PAGE_LIMIT";
        }
        long newPageBytes = atlasPageBytes(textureSize);
        long ownedBytes = atlasOwnedTextureBytes();
        if (newPageBytes > maxResidentAtlasBytes || ownedBytes > maxResidentAtlasBytes - newPageBytes) {
            return "RESIDENT_BYTE_LIMIT";
        }
        return null;
    }

    private long atlasOwnedTextureBytes() {
        Set<GlyphPage> countedPages = new HashSet<GlyphPage>();
        long ownedBytes = collectAtlasBytes(runtimeTables.normalPages, runtimeTables.normalPageCount, countedPages);
        ownedBytes = saturatedAdd(ownedBytes,
                collectAtlasBytes(runtimeTables.boldPages, runtimeTables.boldPageCount, countedPages));
        for (GlyphPage page : retainedAtlasOwnerships) {
            if (countedPages.add(page)) {
                ownedBytes = saturatedAdd(ownedBytes, atlasPageBytes(page.getTextureSize()));
            }
        }
        return ownedBytes;
    }

    private long collectAtlasBytes(GlyphPage[] pages, int pageCount, Set<GlyphPage> countedPages) {
        long bytes = 0L;
        for (int index = 0; index < pageCount; index++) {
            GlyphPage page = pages[index];
            if (page != null && (page.getCommittedSlotCount() > 0 || page.hasTextureOwnership())
                    && countedPages.add(page)) {
                bytes = saturatedAdd(bytes, atlasPageBytes(page.getTextureSize()));
            }
        }
        return bytes;
    }

    static long atlasPageBytes(int pageTextureSize) {
        long side = Math.max(0, pageTextureSize);
        long bytes = 0L;
        while (side > 0L) {
            long pixels = side * side;
            long levelBytes = pixels > Long.MAX_VALUE / 4L ? Long.MAX_VALUE : pixels * 4L;
            bytes = saturatedAdd(bytes, levelBytes);
            if (side == 1L || bytes == Long.MAX_VALUE) {
                break;
            }
            side = Math.max(1L, side / 2L);
        }
        return bytes;
    }

    private void detachLastPage(FontType fontType, GlyphPage page) {
        int pageIndex = page.getPageIndex();
        if (fontType == FontType.BOLD) {
            if (runtimeTables.boldPageCount != pageIndex + 1 || runtimeTables.boldPages[pageIndex] != page) {
                throw new IllegalStateException("无法回滚非末尾 bold atlas page publication");
            }
            runtimeTables.boldPages[pageIndex] = null;
            runtimeTables.boldPageCount = pageIndex;
            return;
        }
        if (runtimeTables.normalPageCount != pageIndex + 1 || runtimeTables.normalPages[pageIndex] != page) {
            throw new IllegalStateException("无法回滚非末尾 normal atlas page publication");
        }
        runtimeTables.normalPages[pageIndex] = null;
        runtimeTables.normalPageCount = pageIndex;
    }

    private static long elapsedNanos(long startedNanos, long currentNanos) {
        long elapsed = currentNanos - startedNanos;
        return elapsed < 0L ? 0L : elapsed;
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static void throwUnchecked(Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        }
        if (throwable instanceof Error) {
            throw (Error) throwable;
        }
        throw new AssertionError("unexpected checked throwable", throwable);
    }

    private boolean transition(GlyphRequestToken token, GlyphState expectedState, GlyphState nextState) {
        if (!matches(token, expectedState)) {
            return false;
        }
        runtimeTables.stateArray(token.getFontType())[token.getCodepoint()] = stateByte(nextState);
        if (!isActiveState(nextState)) {
            removeActiveDemand(token);
        }
        return true;
    }

    private void releaseMailboxReservation(long reservedEpoch, long bitmapBytes) {
        synchronized (mailboxLock) {
            if (mailbox.releaseReservation(reservedEpoch, bitmapBytes)) {
                mailboxLock.notifyAll();
            }
        }
    }

    private GlyphMailbox.Poll pollNextUpload(long attemptedBitmapBytes, boolean allowOversizedFirst) {
        synchronized (mailboxLock) {
            return mailbox.pollBest(nanoTime.getAsLong(), attemptedBitmapBytes, uploadDrainBitmapByteBudget,
                    allowOversizedFirst);
        }
    }

    private void completeUploadLease(PendingGlyphUpload upload) {
        synchronized (mailboxLock) {
            if (mailbox.completeLease(upload)) {
                mailboxLock.notifyAll();
            }
        }
    }

    /** 调用方必须持有 mailboxLock。 */
    private boolean isUploadLeaseCurrentLocked(PendingGlyphUpload upload) {
        return mailbox.isLeaseCurrentLocked(upload);
    }

    private long estimateUploadPlanBytes(GlyphGenerationResult result) {
        GlyphInfo glyphInfo = result.getGlyphInfo();
        GlyphRequestToken token = result.getToken();
        if (glyphInfo == null || token == null || glyphInfo.getCodepoint() != token.getCodepoint()) {
            throw new IllegalArgumentException("glyph result 的 token 与 glyphInfo 不一致");
        }
        if (!glyphInfo.hasBitmap()) {
            return 0L;
        }
        if (glyphInfo.getSlotWidth() <= 0 || glyphInfo.getSlotHeight() <= 0) {
            throw new IllegalArgumentException("bitmap glyph 的 slot 尺寸无效");
        }
        return (long) glyphInfo.getSlotWidth() * (long) glyphInfo.getSlotHeight() * 4L;
    }

    private boolean matchesActiveDemand(GlyphDemandRegistry.ActiveGlyphDemand demand) {
        if (demand == null || !isCurrentToken(demand.token)) {
            return false;
        }
        byte state = runtimeTables.stateArray(demand.token.getFontType())[demand.token.getCodepoint()];
        return isActiveState(state);
    }

    private void removeActiveDemand(GlyphRequestToken token) {
        if (token == null) {
            return;
        }
        GlyphDemandRegistry.ActiveGlyphDemand demand = demands.get(token);
        if (demand != null && demand.token.equals(token)) {
            demand.active.set(false);
            demands.remove(token);
            synchronized (mailboxLock) {
                mailboxLock.notifyAll();
            }
        }
    }

    private boolean isValidDemandPriority(int demandPriority) {
        return demandPriority >= 0 && demandPriority <= PRIORITY_VISIBLE;
    }

    private String priorityName(int demandPriority) {
        switch (demandPriority) {
            case 3:
                return "VISIBLE";
            case 2:
                return "FOREGROUND";
            case 1:
                return "PREFETCH";
            default:
                return "WARMUP";
        }
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

    private int collectPressureRequests(long[] requests, int offset, BitSet pressuredCodepoints,
            FontType fontType) {
        int writeIndex = offset;
        for (int codepoint = pressuredCodepoints.nextSetBit(0); codepoint >= 0;
                codepoint = pressuredCodepoints.nextSetBit(codepoint + 1)) {
            requests[writeIndex++] = packRecoverableRequest(codepoint, fontType);
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
                    retainPageForRetry(page);
                    MyMod.LOG.warn("字体 atlas page 退休失败，保留所有权并在后续换代重试: runtimeVersion={} pageIndex={}",
                            Integer.valueOf(page.getRuntimeVersion()), Integer.valueOf(page.getPageIndex()), exception);
                }
            }
        }
    }

    private void retryRetiredPages() {
        boolean releasedOwnership = false;
        Iterator<GlyphPage> iterator = retiredPageRetries.iterator();
        while (iterator.hasNext()) {
            GlyphPage page = iterator.next();
            try {
                page.close();
                iterator.remove();
                if (retainedAtlasOwnerships.remove(page)) {
                    retainedAtlasPageCount--;
                    releasedOwnership = true;
                }
            } catch (RuntimeException exception) {
                releasedOwnership |= releaseRetainedCountIfOwnershipGone(page);
                if (!page.hasTextureOwnership()) {
                    iterator.remove();
                    continue;
                }
                if (club.heiqi.uilib.Config.fontRuntimeDebug) {
                    MyMod.LOG.debug("字体 atlas page 退休重试失败，继续保留所有权: runtimeVersion={} pageIndex={}",
                            Integer.valueOf(page.getRuntimeVersion()), Integer.valueOf(page.getPageIndex()), exception);
                }
            }
        }
        if (releasedOwnership) {
            releaseAtlasPressureIfCapacityAvailable();
        }
    }

    private void retainPageForRetry(GlyphPage page) {
        if (page == null || retiredPageRetries.contains(page)) {
            return;
        }
        retiredPageRetries.add(page);
        if (page.hasTextureOwnership() && retainedAtlasOwnerships.add(page)) {
            retainedAtlasPageCount++;
        }
    }

    private boolean releaseRetainedCountIfOwnershipGone(GlyphPage page) {
        if (!page.hasTextureOwnership() && retainedAtlasOwnerships.remove(page)) {
            retainedAtlasPageCount--;
            return true;
        }
        return false;
    }

    private final class AtlasReservation {

        private final FontType fontType;
        private final GlyphPage page;
        private final GlyphPage.SlotReservation slotReservation;
        private final boolean newPage;
        private final boolean activatesResidentPage;
        private boolean pagePublished;
        private boolean residentCountCommitted;
        private boolean sealed;
        private boolean rolledBack;

        private AtlasReservation(FontType fontType, GlyphPage page,
                GlyphPage.SlotReservation slotReservation, boolean newPage, boolean activatesResidentPage) {
            this.fontType = fontType;
            this.page = page;
            this.slotReservation = slotReservation;
            this.newPage = newPage;
            this.activatesResidentPage = activatesResidentPage;
        }

        private void commit() {
            if (sealed || rolledBack) {
                throw new IllegalStateException("atlas reservation 已结算");
            }
            try {
                slotReservation.commit();
                if (newPage) {
                    runtimeTables.setPage(fontType, page.getPageIndex(), page);
                    pagePublished = true;
                }
                if (activatesResidentPage) {
                    residentAtlasPageCount++;
                    residentCountCommitted = true;
                }
            } catch (RuntimeException exception) {
                rollbackAfterCommitFailure(exception);
                throw exception;
            } catch (Error error) {
                rollbackAfterCommitFailure(error);
                throw error;
            }
        }

        private void seal() {
            if (sealed || rolledBack) {
                throw new IllegalStateException("atlas reservation 已结算");
            }
            slotReservation.seal();
            sealed = true;
        }

        private void rollback() {
            if (rolledBack) {
                return;
            }
            if (sealed) {
                throw new IllegalStateException("已发布 atlas reservation 不能回滚");
            }
            Throwable failure = null;
            if (residentCountCommitted) {
                residentAtlasPageCount--;
                residentCountCommitted = false;
            }
            if (pagePublished) {
                try {
                    detachLastPage(fontType, page);
                    pagePublished = false;
                } catch (RuntimeException exception) {
                    failure = exception;
                } catch (Error error) {
                    failure = error;
                }
            }
            try {
                slotReservation.rollback();
            } catch (RuntimeException exception) {
                failure = appendFailure(failure, exception);
            } catch (Error error) {
                failure = appendFailure(failure, error);
            }
            if (activatesResidentPage && page.getCommittedSlotCount() == 0) {
                try {
                    page.close();
                } catch (RuntimeException exception) {
                    failure = appendFailure(failure, exception);
                    retainFailedCloseOwnership();
                } catch (Error error) {
                    failure = appendFailure(failure, error);
                    retainFailedCloseOwnership();
                }
            }
            rolledBack = true;
            if (failure != null) {
                throwUnchecked(failure);
            }
        }

        private void rollbackAfterCommitFailure(Throwable originalFailure) {
            try {
                rollback();
            } catch (RuntimeException rollbackFailure) {
                originalFailure.addSuppressed(rollbackFailure);
            } catch (Error rollbackFailure) {
                originalFailure.addSuppressed(rollbackFailure);
            }
        }

        private boolean isRolledBack() {
            return rolledBack;
        }

        private void retainFailedCloseOwnership() {
            if (!page.hasTextureOwnership()) {
                return;
            }
            if (newPage && !pagePublished) {
                retainPageForRetry(page);
            } else if (activatesResidentPage && !residentCountCommitted) {
                residentAtlasPageCount++;
                residentCountCommitted = true;
            }
        }
    }

    private enum UploadOutcome {
        COMMITTED,
        STALE,
        ATLAS_PRESSURE
    }

    private static final class UploadAttemptContext {

        private final int attempt;
        private final long attemptedBitmapBytes;
        private final int maxAttempts;
        private final long maxBitmapBytes;
        private final long maxNanos;
        private int pageIndex = -1;
        private int slotIndex = -1;
        private String pressure = "NONE";
        private String rollbackReason = "NONE";

        private UploadAttemptContext(int attempt, long attemptedBitmapBytes, int maxAttempts,
                long maxBitmapBytes, long maxNanos) {
            this.attempt = attempt;
            this.attemptedBitmapBytes = attemptedBitmapBytes;
            this.maxAttempts = Math.max(0, maxAttempts);
            this.maxBitmapBytes = maxBitmapBytes;
            this.maxNanos = maxNanos;
        }

        private void setSlot(int pageIndex, int slotIndex) {
            this.pageIndex = pageIndex;
            this.slotIndex = slotIndex;
        }

        private String describe() {
            return "page=" + pageIndex + " slot=" + slotIndex + " attempt=" + attempt + '/' + maxAttempts
                    + " bytes=" + attemptedBitmapBytes + '/' + maxBitmapBytes + " nanos=" + maxNanos
                    + " pressure=" + pressure + " rollback=" + rollbackReason;
        }
    }

    private static Throwable appendFailure(Throwable primary, Throwable additional) {
        if (primary == null) {
            return additional;
        }
        primary.addSuppressed(additional);
        return primary;
    }

    private void assertRuntimeAccess() {
        if (!FontRuntimeAccess.isActive(ownerToken)) {
            throw new IllegalStateException("GlyphPageManager 只能由字体 runtime owner 修改或读取内部 storage");
        }
    }
}
