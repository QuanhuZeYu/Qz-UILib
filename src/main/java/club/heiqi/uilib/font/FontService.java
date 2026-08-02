package club.heiqi.uilib.font;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.event.FontReloadRequest;
import club.heiqi.uilib.font.glyph.GlyphGenerationDispatcher;
import club.heiqi.uilib.font.glyph.GlyphGenerationPriority;
import club.heiqi.uilib.font.glyph.GlyphGenerationTask;
import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.page.GlyphRuntimeTables;
import club.heiqi.uilib.font.render.FontBatchRenderer;
import club.heiqi.uilib.font.shader.FontShaderProgram;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.FontMatcher;
import club.heiqi.uilib.font.util.FontRegistry;
import club.heiqi.uilib.ui.widget.UiLayoutInvalidationRegistry;

/**
 * 字体系统总入口。
 */
public class FontService {

    private static final long RELOAD_QUIET_NANOS = TimeUnit.MILLISECONDS.toNanos(150L);
    private static final long RELOAD_RETRY_BASE_NANOS = TimeUnit.MILLISECONDS.toNanos(250L);
    private static final long RELOAD_RETRY_MAX_NANOS = TimeUnit.SECONDS.toNanos(5L);
    private static final int MAX_RECOVERABLE_SUBMISSIONS_PER_TICK = 64;
    private static final FontService INSTANCE = new FontService();

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean layoutRuntimeReady = new AtomicBoolean(false);
    /**
     * 渲染主线程引用。
     *
     * <p>由 {@link #tickMainThread(int)} 首次调用时填充。外部 reload 只发布 signal，完整 reconcile 与
     * GL 资源释放只能由这条线程在后续 render tick 执行。</p>
     */
    private volatile Thread renderThread;
    private final Object runtimeOwnerToken = new Object();
    private final ReentrantReadWriteLock generationLock = new ReentrantReadWriteLock();
    private final FontCatalog fontCatalog = new FontCatalog();
    private final FontRegistry fontRegistry = new FontRegistry(fontCatalog);
    private final DerivedFontCache derivedFontCache = new DerivedFontCache(fontCatalog);
    private final FontMatcher fontMatcher;
    private final GlyphPageManager glyphPageManager;
    private final GlyphGenerationDispatcher glyphGenerationDispatcher;
    private final TextLayoutService textLayoutService;
    private final FontRuntimeDiagnosticsView runtimeDiagnosticsView;
    private FontBatchRenderer batchRenderer;
    private FontShaderProgram shaderProgram;
    private final Deque<Long> drawStageUploadTimestamps = new ArrayDeque<Long>();
    private final FontReloadSignal reloadSignal;
    private final FontGenerationCandidateFactory generationCandidateFactory;
    private final AtomicReference<ReloadState> reloadState = new AtomicReference<ReloadState>(ReloadState.RUNNING);
    private static final AtomicBoolean NON_RENDER_THREAD_TICK_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean NON_RENDER_THREAD_SHUTDOWN_GL_LOGGED = new AtomicBoolean(false);

    private long lastDrawStageUploadAt = 0L;
    private volatile ActiveFontGeneration activeGeneration;
    private boolean workerRecoveryPending;
    private boolean workerRecoveryFailureLogged;
    private long[] workerRecoveryGlyphs = new long[0];
    private long[] recoverableDemandGlyphs = new long[0];
    private int recoverableDemandOffset;
    private int recoverableDemandRuntimeVersion;
    private boolean recoverableDemandFailureLogged;

    private enum ReloadState {
        RUNNING,
        RELOADING
    }

    private FontService() {
        this(new FontReloadSignal(RELOAD_QUIET_NANOS, RELOAD_RETRY_BASE_NANOS, RELOAD_RETRY_MAX_NANOS,
                System::nanoTime));
    }

    FontService(FontReloadSignal reloadSignal) {
        this(reloadSignal, DefaultFontGenerationCandidateFactory.INSTANCE);
    }

    FontService(FontReloadSignal reloadSignal, FontGenerationCandidateFactory generationCandidateFactory) {
        this(reloadSignal, generationCandidateFactory, new GlyphGenerationDispatcher());
    }

    FontService(FontReloadSignal reloadSignal, FontGenerationCandidateFactory generationCandidateFactory,
            GlyphGenerationDispatcher glyphGenerationDispatcher) {
        if (reloadSignal == null) {
            throw new IllegalArgumentException("reloadSignal 不得为 null");
        }
        if (generationCandidateFactory == null) {
            throw new IllegalArgumentException("generationCandidateFactory 不得为 null");
        }
        if (glyphGenerationDispatcher == null) {
            throw new IllegalArgumentException("glyphGenerationDispatcher 不得为 null");
        }
        this.reloadSignal = reloadSignal;
        this.generationCandidateFactory = generationCandidateFactory;
        this.glyphPageManager = new GlyphPageManager(runtimeOwnerToken);
        this.fontMatcher = new FontMatcher(fontCatalog, derivedFontCache, generationLock.readLock(),
                runtimeOwnerToken);
        this.textLayoutService = FontRuntimeAccess.call(runtimeOwnerToken,
                () -> new TextLayoutService(fontMatcher, glyphPageManager, derivedFontCache,
                        generationLock.readLock(), runtimeOwnerToken));
        this.glyphGenerationDispatcher = glyphGenerationDispatcher;
        this.glyphGenerationDispatcher.bindOwner(runtimeOwnerToken);
        this.runtimeDiagnosticsView = new FontRuntimeDiagnosticsView(this);
    }

    /**
     * 获取字体系统单例。
     *
     * @return 字体系统实例
     */
    public static FontService getInstance() {
        return INSTANCE;
    }

    /**
     * 确保布局期文本测量所需的轻量运行时已就绪。
     *
     * <p>该入口只准备 CPU catalog、generation envelope 与布局缓存，不创建 atlas texture、worker、
     * 批渲染器或着色器。</p>
     */
    public void ensureLayoutRuntimeReady() {
        if (layoutRuntimeReady.get()) {
            return;
        }

        synchronized (this) {
            if (layoutRuntimeReady.get()) {
                return;
            }
            FontRuntimeAccess.run(runtimeOwnerToken, () -> {
                if (activeGeneration == null) {
                    FontGenerationCandidate candidate = prepareNextGenerationCandidate();
                    ActiveFontGeneration generation = publishGenerationLocked(candidate);
                    completeCatalogPublicationBestEffort(candidate);
                    MyMod.LOG.info("字体布局测量运行时初始化完成：version={} settings={}",
                            Integer.valueOf(generation.getRuntimeVersion()), FontConfig.buildSummary());
                }
                layoutRuntimeReady.set(true);
            });
        }
    }

    /**
     * 初始化字体系统基础骨架。
     */
    public void initialize() {
        if (initialized.get()) {
            return;
        }

        synchronized (this) {
            if (initialized.get()) {
                return;
            }
            FontRuntimeAccess.run(runtimeOwnerToken, () -> {
                reloadSignal.openLifecycle();
                FontRuntimeSettings desiredSettings = FontRuntimeSettings.capture();
                if (activeGeneration == null
                        || !activeGeneration.matchesDesiredSettings(desiredSettings)) {
                    FontGenerationCandidate candidate = prepareNextGenerationCandidate();
                    publishGenerationLocked(candidate);
                    completeCatalogPublicationBestEffort(candidate);
                }
                ActiveFontGeneration generation = activeGeneration;
                glyphPageManager.initialize();
                glyphGenerationDispatcher.setRuntimeVersion(generation.getRuntimeVersion());
                glyphGenerationDispatcher.initialize(fontMatcher, glyphPageManager, generation.getDerivedFontCache(),
                        glyphPageManager::queueUpload);
                initialized.set(true);
            });
        }

        MyMod.LOG.info("字体系统骨架初始化完成：{}", FontConfig.buildSummary());
    }

    /**
     * 发布字体系统需要与最新 desired state 对齐的 signal。
     *
     * <p>该入口不初始化运行时、不等待 worker，也不释放 GL；任意线程只推进 durable signal。
     * 唯一完整 reconcile 入口是后续 render-thread {@link #tickMainThread(int)}。</p>
     *
     * @param request 重载请求
     */
    public void reload(FontReloadRequest request) {
        long desiredSequence = reloadSignal.signal(request);
        if (desiredSequence < 0L) {
            return;
        }
        if (club.heiqi.uilib.Config.fontRuntimeDebug) {
            MyMod.LOG.info("字体 reload signal 已发布：sequence={} reason={} pending={}",
                    Long.valueOf(desiredSequence), request == null ? "<null>" : request.getReason(),
                    Integer.valueOf(reloadSignal.getPendingCount()));
        }
    }

    /**
     * 刷新字体系统主线程状态。
     *
     * <p>同时绑定唯一 reconcile owner；错误线程调用不会执行 reload 或 upload。</p>
     *
     * @param maxUploadCount 本次最多处理的待上传数量
     */
    public void tickMainThread(int maxUploadCount) {
        synchronized (this) {
            FontRuntimeAccess.run(runtimeOwnerToken, () -> tickMainThreadLocked(maxUploadCount));
        }
    }

    private void tickMainThreadLocked(int maxUploadCount) {
        if (!initialized.get()) {
            return;
        }
        if (!captureOrVerifyRenderThreadLocked()) {
            logNonRenderThreadTickOnce();
            return;
        }
        if (!recoverWorkerIfPendingLocked() || !reconcileReloadIfReadyLocked()) {
            return;
        }

        drainRecoverableGlyphsLocked();
        glyphPageManager.flushPendingUploads(maxUploadCount);
        debugLogStats("render_tick");
    }

    /**
     * 在 drawString 阶段尝试限速执行字符页上传。
     *
     * @param maxUploadCount 本次最多处理的待上传数量
     */
    public void tickDrawStage(int maxUploadCount) {
        synchronized (this) {
            FontRuntimeAccess.run(runtimeOwnerToken, () -> tickDrawStageLocked(maxUploadCount));
        }
    }

    private void tickDrawStageLocked(int maxUploadCount) {
        if (renderThread != Thread.currentThread() || !initialized.get() || maxUploadCount <= 0
                || !canRunDrawStageUpload()) {
            return;
        }

        try {
            glyphPageManager.flushPendingUploads(maxUploadCount);
        } finally {
            long now = System.nanoTime();
            lastDrawStageUploadAt = now;
            drawStageUploadTimestamps.addLast(Long.valueOf(now));
        }
        debugLogStats("draw_stage");
    }

    /**
     * 判断字体系统是否已初始化。
     *
     * @return 是否已初始化
     */
    public boolean isInitialized() {
        return initialized.get();
    }

    /**
     * 获取字体运行时版本号。
     *
     * @return 当前字体运行时版本号
     */
    public int getRuntimeVersion() {
        ActiveFontGeneration generation = activeGeneration;
        return generation == null ? 0 : generation.getRuntimeVersion();
    }

    /**
     * 判断字体系统是否正在执行重载屏障。
     *
     * @return 是否正在重载
     */
    public boolean isReloading() {
        return reloadState.get() == ReloadState.RELOADING;
    }

    /**
     * 获取文本测量缓存失效纪元。
     *
     * <p>该纪元在布局期轻量初始化与完整运行时重载共享：只要字体注册、匹配缓存或文本布局缓存基础发生变化，就会递增。</p>
     *
     * @return 文本测量缓存失效纪元
     */
    public int getTextMeasureEpoch() {
        ensureLayoutRuntimeReady();
        return activeGeneration.getTextMeasureEpoch();
    }

    /**
     * 获取当前完整字体 generation 快照。
     *
     * @return active generation
     */
    ActiveFontGeneration getActiveGeneration() {
        ensureLayoutRuntimeReady();
        return activeGeneration;
    }

    /**
     * 获取当前 generation 的不可变设置快照。
     *
     * @return runtime settings
     */
    public FontRuntimeSettings getRuntimeSettings() {
        ensureLayoutRuntimeReady();
        return activeGeneration.getSettings();
    }

    /**
     * 获取字体 runtime 的只读诊断 facade。
     *
     * @return 只读诊断 facade
     */
    public FontRuntimeDiagnosticsView getRuntimeDiagnostics() {
        return runtimeDiagnosticsView;
    }

    /**
     * 获取当前 generation 的只读 glyph render view。
     *
     * @return glyph render view
     */
    public GlyphRuntimeTablesView getGlyphRuntimeTablesView() {
        ensureLayoutRuntimeReady();
        synchronized (this) {
            return FontRuntimeAccess.call(runtimeOwnerToken,
                    () -> new GlyphRuntimeTablesView(glyphPageManager.getRuntimeTables(), glyphPageManager,
                            runtimeOwnerToken, activeGeneration.getRuntimeVersion()));
        }
    }

    /**
     * 提交当前 generation 的 glyph demand，不暴露 dispatcher lifecycle control。
     *
     * @param task glyph demand
     */
    public void submitGlyphGeneration(GlyphGenerationTask task) {
        if (task == null) {
            return;
        }
        FontRuntimeAccess.run(runtimeOwnerToken, () -> glyphGenerationDispatcher.submit(task));
    }

    /**
     * 获取字符页管理器的诊断对象。
     *
     * <p>singleton 实例的写入口与 raw table getter 受 runtime owner scope 保护。</p>
     *
     * @return 字符页管理器
     */
    public GlyphPageManager getGlyphPageManager() {
        return glyphPageManager;
    }

    /**
     * 获取字体匹配器的诊断对象。
     *
     * <p>singleton 实例的 generation binding 写入口受 runtime owner scope 保护。</p>
     *
     * @return 字体匹配器
     */
    public FontMatcher getFontMatcher() {
        return fontMatcher;
    }

    /**
     * 获取字符生成调度器的诊断对象。
     *
     * <p>singleton 实例的 lifecycle control 受 runtime owner scope 保护。</p>
     *
     * @return 字符生成调度器
     */
    public GlyphGenerationDispatcher getGlyphGenerationDispatcher() {
        return glyphGenerationDispatcher;
    }

    /**
     * 获取文本布局服务。
     *
     * @return 文本布局服务
     */
    public TextLayoutService getTextLayoutService() {
        ensureLayoutRuntimeReady();
        return textLayoutService;
    }

    /**
     * 获取批渲染器。
     *
     * @return 批渲染器
     */
    public FontBatchRenderer getBatchRenderer() {
        if (batchRenderer == null) {
            batchRenderer = new FontBatchRenderer();
        }
        return batchRenderer;
    }

    /**
     * 获取着色器程序封装。
     *
     * @return 着色器程序
     */
    public FontShaderProgram getShaderProgram() {
        if (shaderProgram == null) {
            shaderProgram = new FontShaderProgram();
        }
        return shaderProgram;
    }

    /**
     * 获取当前字体系统运行时统计。
     *
     * @return 运行时统计快照
     */
    public FontRuntimeStats getRuntimeStats() {
        return FontRuntimeAccess.call(runtimeOwnerToken, this::buildRuntimeStats);
    }

    private FontRuntimeStats buildRuntimeStats() {
        ActiveFontGeneration generation = activeGeneration;
        DerivedFontCache generationCache = generation == null ? derivedFontCache : generation.getDerivedFontCache();
        int quadCount = batchRenderer == null ? 0 : batchRenderer.getQuadCount();
        int lastFlushPageSubmitCount = batchRenderer == null ? 0 : batchRenderer.getLastFlushPageSubmitCount();
        int lastFlushDrawCallCount = batchRenderer == null ? 0 : batchRenderer.getLastFlushDrawCallCount();
        int lastFlushTextureBindCount = batchRenderer == null ? 0 : batchRenderer.getLastFlushTextureBindCount();
        return new FontRuntimeStats(
                glyphPageManager.getPendingUploadCount(),
                glyphPageManager.getReadyGlyphCount(),
                glyphPageManager.getNormalPageCount(),
                glyphPageManager.getBoldPageCount(),
                GlyphRuntimeTables.CODEPOINT_COUNT,
                glyphPageManager.getRuntimeTables().slotsPerPage,
                drawStageUploadTimestamps.size(),
                quadCount,
                lastFlushPageSubmitCount,
                lastFlushDrawCallCount,
                lastFlushTextureBindCount,
                fontMatcher.getCacheHitCount(),
                fontMatcher.getCacheMissCount(),
                generationCache.getCacheHitCount(),
                generationCache.getCacheMissCount(),
                textLayoutService.getWidthCacheHitCount(),
                textLayoutService.getWidthCacheMissCount(),
                glyphGenerationDispatcher.getActiveDemandCount(),
                glyphGenerationDispatcher.getMaxDemandCount(),
                glyphGenerationDispatcher.getDemandHighWaterMark(),
                glyphGenerationDispatcher.getRejectedDemandCount(),
                glyphGenerationDispatcher.getPromotedDemandCount(),
                glyphPageManager.getPendingBitmapBytes(),
                glyphPageManager.getMaxPendingBitmapBytes(),
                glyphPageManager.getPendingUploadHighWaterMark(),
                glyphPageManager.getPendingBitmapBytesHighWaterMark(),
                glyphPageManager.getBlockedPublisherCount(),
                glyphPageManager.getMailboxBackpressureCount(),
                glyphPageManager.getMailboxRejectedCount());
    }

    /**
     * 关停字体系统，释放调度器线程池，并在安全线程上释放批渲染器与着色器。
     *
     * <p>JVM shutdown hook 不持有有效 OpenGL context，不能在该线程直接删除 VAO / VBO / shader 等
     * GL 资源。非渲染线程关停时只停止字体生成线程池，GL 资源交给客户端退出流程销毁底层 context。</p>
     */
    public void shutdown() {
        synchronized (this) {
            FontRuntimeAccess.run(runtimeOwnerToken, this::shutdownLocked);
        }
    }

    private void shutdownLocked() {
        reloadSignal.closeLifecycle();
        if (!initialized.get()) {
            clearWorkerRecoveryLocked();
            clearRecoverableGlyphsLocked();
            renderThread = null;
            return;
        }
        try {
            glyphGenerationDispatcher.pause();
            glyphGenerationDispatcher.reset();
        } catch (RuntimeException exception) {
            MyMod.LOG.warn("字体调度器关停异常", exception);
        }
        if (isCurrentThreadAllowedToReleaseGlResources()) {
            try {
                clearRenderResources();
            } catch (RuntimeException exception) {
                MyMod.LOG.warn("字体渲染资源关停异常", exception);
            }
        } else {
            logNonRenderThreadShutdownGlSkippedOnce();
        }
        initialized.set(false);
        layoutRuntimeReady.set(false);
        drawStageUploadTimestamps.clear();
        lastDrawStageUploadAt = 0L;
        clearWorkerRecoveryLocked();
        clearRecoverableGlyphsLocked();
        renderThread = null;
    }

    private boolean canRunDrawStageUpload() {
        long now = System.nanoTime();

        while (!drawStageUploadTimestamps.isEmpty()
                && elapsedNanos(drawStageUploadTimestamps.peekFirst().longValue(), now) >= TimeUnit.SECONDS.toNanos(1L)) {
            drawStageUploadTimestamps.pollFirst();
        }
        long intervalNanos = drawStageUploadIntervalNanos();
        if (lastDrawStageUploadAt != 0L && elapsedNanos(lastDrawStageUploadAt, now) < intervalNanos) {
            return false;
        }
        return drawStageUploadTimestamps.size() < FontConfig.drawStageUploadLimitPerSecond;
    }

    private long drawStageUploadIntervalNanos() {
        double intervalNanos = FontConfig.drawStageUploadIntervalMs * 1000.0D * 1000.0D;
        if (Double.isNaN(intervalNanos) || intervalNanos <= 0.0D) {
            return 0L;
        }
        return intervalNanos >= (double) Long.MAX_VALUE ? Long.MAX_VALUE : (long) intervalNanos;
    }

    private long elapsedNanos(long startedNanos, long currentNanos) {
        long elapsed = currentNanos - startedNanos;
        return elapsed < 0L ? 0L : elapsed;
    }

    private boolean reconcileReloadIfReadyLocked() {
        if (!reloadSignal.hasPending()) {
            return true;
        }
        // 任意完整 reload 都会重建 worker/atlas；Splash 活跃时统一保留 signal，避免来源交错破坏 GL。
        if (FontSplashReloadGuard.shouldDeferFontReload()) {
            return true;
        }
        FontReloadSignal.Ticket ticket = reloadSignal.pollReady();
        if (ticket == null) {
            return true;
        }
        CommittedReload committedReload;
        try {
            FontGenerationCandidate candidate = prepareNextGenerationCandidate();
            committedReload = commitReloadLocked(candidate);
        } catch (RuntimeException exception) {
            reloadSignal.completeFailure(ticket);
            int failureCount = reloadSignal.getConsecutiveFailures();
            if (failureCount <= 1) {
                MyMod.LOG.error("字体 reload reconcile 失败，signal 保持 pending 并进入退避: reason={}",
                        ticket.getRequest().getReason(), exception);
            } else if (club.heiqi.uilib.Config.fontRuntimeDebug) {
                MyMod.LOG.debug("字体 reload reconcile 重试失败: reason={} failures={}",
                        ticket.getRequest().getReason(), Integer.valueOf(failureCount), exception);
            }
            return false;
        } catch (Error error) {
            reloadSignal.completeFailure(ticket);
            throw error;
        }
        if (!reloadSignal.completeSuccess(ticket)) {
            MyMod.LOG.error("字体 reload 已提交，但 signal ticket 不再属于当前 lifecycle: sequence={}",
                    Long.valueOf(ticket.getSequence()));
            return false;
        }
        finishCommittedReload(ticket.getRequest(), committedReload);
        return true;
    }

    /** candidate 成功后，在唯一 render barrier 内转移大型 table storage 并发布新 envelope。 */
    private CommittedReload commitReloadLocked(FontGenerationCandidate candidate) {
        validateCandidateForPublication(candidate);
        reloadState.set(ReloadState.RELOADING);
        ActiveFontGeneration previous = activeGeneration;
        long[] recoverableGlyphs = new long[0];
        try {
            glyphGenerationDispatcher.pause();
            recoverableGlyphs = mergeRecoverableGlyphs(glyphPageManager.snapshotRecoverableRequests(),
                    snapshotPendingRecoverableGlyphsLocked(previous));
            clearRecoverableGlyphsLocked();
            glyphGenerationDispatcher.reset();
            glyphPageManager.discardPendingUploads();
            if (batchRenderer != null) {
                batchRenderer.clearFrame();
            }
            ActiveFontGeneration generation = publishGenerationLocked(candidate);
            drawStageUploadTimestamps.clear();
            lastDrawStageUploadAt = 0L;
            scheduleWorkerRecoveryLocked(recoverableGlyphs);
            try {
                glyphGenerationDispatcher.setRuntimeVersion(generation.getRuntimeVersion());
                glyphGenerationDispatcher.initialize(fontMatcher, glyphPageManager,
                        generation.getDerivedFontCache(), glyphPageManager::queueUpload);
                clearWorkerRecoveryLocked();
            } catch (RuntimeException exception) {
                MyMod.LOG.error("字体 generation 已发布，但 worker 恢复失败；后续 render tick 将继续恢复", exception);
            }
            completeCatalogPublicationBestEffort(candidate);
            return new CommittedReload(generation, recoverableGlyphs, !workerRecoveryPending);
        } catch (RuntimeException exception) {
            if (activeGeneration == previous && previous != null && previous.isActive()) {
                if (restoreDispatcherBestEffort(previous)) {
                    scheduleRecoverableGlyphsLocked(recoverableGlyphs, previous);
                } else {
                    scheduleWorkerRecoveryLocked(recoverableGlyphs);
                }
            }
            throw exception;
        } finally {
            reloadState.set(ReloadState.RUNNING);
        }
    }

    /** commit 后的可恢复收尾不得反向触发整代重试。 */
    private void finishCommittedReload(FontReloadRequest request, CommittedReload committedReload) {
        ActiveFontGeneration generation = committedReload.generation;
        long[] recoverableGlyphs = committedReload.recoverableGlyphs;
        if (committedReload.workerReady) {
            scheduleRecoverableGlyphsLocked(recoverableGlyphs, generation);
        }
        int invalidatedRootCount = 0;
        try {
            invalidatedRootCount = UiLayoutInvalidationRegistry.invalidateAll();
        } catch (RuntimeException exception) {
            MyMod.LOG.warn("字体 reload 已提交，但主动布局失效失败；textMeasureEpoch 仍会驱动按需重测", exception);
        }
        MyMod.LOG.info("字体系统重载完成，原因：{}，布局树已失效：{}，运行时版本：{}，恢复请求：{}",
                request.getReason(), Integer.valueOf(invalidatedRootCount),
                Integer.valueOf(generation.getRuntimeVersion()),
                Integer.valueOf(recoverableGlyphs.length));
    }

    private FontGenerationCandidate prepareNextGenerationCandidate() {
        ActiveFontGeneration current = activeGeneration;
        int nextRuntimeVersion = current == null ? 1 : current.getRuntimeVersion() + 1;
        int nextTextMeasureEpoch = current == null ? 1 : current.getTextMeasureEpoch() + 1;
        return generationCandidateFactory.prepare(fontRegistry, nextRuntimeVersion, nextTextMeasureEpoch);
    }

    /** 必须在 service monitor 内调用；大表原地清理受 generation write lock 独占保护。 */
    private ActiveFontGeneration publishGenerationLocked(FontGenerationCandidate candidate) {
        validateCandidateForPublication(candidate);
        ActiveFontGeneration generation = new ActiveFontGeneration(candidate.getRuntimeVersion(),
                candidate.getTextMeasureEpoch(), candidate.getSettings(),
                candidate.getPreparedCatalog().getCatalogSnapshot(),
                candidate.getPreparedCatalog().getOrderSnapshot().getResolvedFontNames(),
                glyphPageManager.getRuntimeTables(), candidate.getMetrics());
        generationLock.writeLock().lock();
        try {
            ActiveFontGeneration previous = activeGeneration;
            if (previous != null) {
                previous.retire();
            }
            glyphPageManager.setGeneration(candidate.getRuntimeVersion(), candidate.getSettings(),
                    candidate.getMetrics());
            fontRegistry.publishValidated(candidate.getPreparedCatalog());
            fontMatcher.setGeneration(generation, glyphPageManager.getRuntimeTables(),
                    generation.getDerivedFontCache());
            fontMatcher.clearCache();
            textLayoutService.setGeneration(generation, glyphPageManager.getRuntimeTables());
            textLayoutService.clearCache();
            activeGeneration = generation;
            layoutRuntimeReady.set(true);
            return generation;
        } finally {
            generationLock.writeLock().unlock();
        }
    }

    private void completeCatalogPublicationBestEffort(FontGenerationCandidate candidate) {
        try {
            fontRegistry.completePublication(candidate.getPreparedCatalog());
        } catch (RuntimeException exception) {
            MyMod.LOG.warn("字体 generation 已发布，但配置展示态收尾失败", exception);
        }
    }

    private boolean restoreDispatcherBestEffort(ActiveFontGeneration generation) {
        try {
            glyphGenerationDispatcher.setRuntimeVersion(generation.getRuntimeVersion());
            glyphGenerationDispatcher.initialize(fontMatcher, glyphPageManager, generation.getDerivedFontCache(),
                    glyphPageManager::queueUpload);
            return true;
        } catch (RuntimeException restoreFailure) {
            MyMod.LOG.error("字体 reload 在 commit 前失败，旧 worker 恢复失败", restoreFailure);
            return false;
        }
    }

    private void scheduleWorkerRecoveryLocked(long[] recoverableGlyphs) {
        workerRecoveryPending = true;
        workerRecoveryFailureLogged = false;
        workerRecoveryGlyphs = recoverableGlyphs == null ? new long[0] : recoverableGlyphs.clone();
    }

    private void clearWorkerRecoveryLocked() {
        workerRecoveryPending = false;
        workerRecoveryFailureLogged = false;
        workerRecoveryGlyphs = new long[0];
    }

    private boolean recoverWorkerIfPendingLocked() {
        if (!workerRecoveryPending) {
            return true;
        }
        ActiveFontGeneration generation = activeGeneration;
        if (generation == null || !generation.isActive()) {
            return false;
        }
        try {
            glyphGenerationDispatcher.setRuntimeVersion(generation.getRuntimeVersion());
            glyphGenerationDispatcher.initialize(fontMatcher, glyphPageManager, generation.getDerivedFontCache(),
                    glyphPageManager::queueUpload);
        } catch (RuntimeException recoveryFailure) {
            if (!workerRecoveryFailureLogged) {
                workerRecoveryFailureLogged = true;
                MyMod.LOG.error("字体 worker 恢复仍失败；保持 durable recovery intent", recoveryFailure);
            } else if (club.heiqi.uilib.Config.fontRuntimeDebug) {
                MyMod.LOG.debug("字体 worker 恢复重试失败", recoveryFailure);
            }
            return false;
        }
        long[] recoverableGlyphs = workerRecoveryGlyphs;
        clearWorkerRecoveryLocked();
        scheduleRecoverableGlyphsLocked(recoverableGlyphs, generation);
        MyMod.LOG.info("字体 worker 已在后续 render tick 恢复，运行时版本：{}，恢复请求：{}",
                Integer.valueOf(generation.getRuntimeVersion()), Integer.valueOf(recoverableGlyphs.length));
        return true;
    }

    private void validateCandidateForPublication(FontGenerationCandidate candidate) {
        if (candidate == null || candidate.getSettings() == null || candidate.getMetrics() == null
                || candidate.getPreparedCatalog() == null) {
            throw new IllegalArgumentException("generation candidate 成员不得为 null");
        }
        ActiveFontGeneration current = activeGeneration;
        int expectedRuntimeVersion = current == null ? 1 : current.getRuntimeVersion() + 1;
        int expectedTextMeasureEpoch = current == null ? 1 : current.getTextMeasureEpoch() + 1;
        if (candidate.getRuntimeVersion() != expectedRuntimeVersion
                || candidate.getTextMeasureEpoch() != expectedTextMeasureEpoch) {
            throw new IllegalStateException("generation candidate version/epoch 不是 active 的严格后继");
        }
        fontRegistry.validate(candidate.getPreparedCatalog());
    }

    private void clearRenderResources() {
        if (batchRenderer != null) {
            batchRenderer.dispose();
            batchRenderer = null;
        }
        if (shaderProgram != null) {
            shaderProgram.close();
            shaderProgram = null;
        }
    }

    private boolean isCurrentThreadAllowedToReleaseGlResources() {
        Thread current = Thread.currentThread();
        Thread captured = renderThread;
        if (captured == null) {
            String name = current.getName();
            return name != null && name.startsWith("Client thread");
        }
        return current == captured;
    }

    /** 必须在 service monitor 内调用。 */
    private boolean captureOrVerifyRenderThreadLocked() {
        Thread current = Thread.currentThread();
        if (renderThread == null) {
            String name = current.getName();
            if (name == null || !name.startsWith("Client thread")) {
                return false;
            }
            renderThread = current;
        }
        return renderThread == current;
    }

    private void logNonRenderThreadTickOnce() {
        if (NON_RENDER_THREAD_TICK_LOGGED.compareAndSet(false, true)) {
            MyMod.LOG.warn(
                    "FontService.tickMainThread 已拒绝非 owner 线程，避免跨线程 reconcile/upload。thread={}",
                    Thread.currentThread().getName());
        }
    }

    private void logNonRenderThreadShutdownGlSkippedOnce() {
        if ((batchRenderer == null && shaderProgram == null)
                || !NON_RENDER_THREAD_SHUTDOWN_GL_LOGGED.compareAndSet(false, true)) {
            return;
        }
        MyMod.LOG.warn(
                "FontService.shutdown 已跳过非渲染线程上的 GL 资源释放，避免 JVM 退出阶段触发 native 崩溃。"
                        + " thread={}",
                Thread.currentThread().getName());
    }

    private void scheduleRecoverableGlyphsLocked(long[] recoverableGlyphs, ActiveFontGeneration generation) {
        if (recoverableGlyphs == null || recoverableGlyphs.length == 0) {
            return;
        }

        byte[] requestedFlags = new byte[Character.MAX_CODE_POINT + 1];
        long[] uniqueGlyphs = new long[recoverableGlyphs.length];
        int uniqueCount = 0;
        for (long glyph : recoverableGlyphs) {
            int codepoint = GlyphPageManager.unpackRecoverableCodepoint(glyph);
            if (codepoint < 0 || codepoint > Character.MAX_CODE_POINT) {
                continue;
            }
            FontType fontType = GlyphPageManager.unpackRecoverableFontType(glyph);
            byte typeFlag = fontType == FontType.BOLD ? (byte) 2 : (byte) 1;
            if ((requestedFlags[codepoint] & typeFlag) != 0) {
                continue;
            }
            requestedFlags[codepoint] = (byte) (requestedFlags[codepoint] | typeFlag);
            uniqueGlyphs[uniqueCount++] = glyph;
        }
        if (uniqueCount == 0) {
            return;
        }
        recoverableDemandGlyphs = Arrays.copyOf(uniqueGlyphs, uniqueCount);
        recoverableDemandOffset = 0;
        recoverableDemandRuntimeVersion = generation.getRuntimeVersion();
        recoverableDemandFailureLogged = false;
    }

    private void drainRecoverableGlyphsLocked() {
        if (recoverableDemandOffset >= recoverableDemandGlyphs.length) {
            return;
        }
        ActiveFontGeneration generation = activeGeneration;
        if (generation == null || !generation.isActive()
                || generation.getRuntimeVersion() != recoverableDemandRuntimeVersion) {
            clearRecoverableGlyphsLocked();
            return;
        }
        if (!glyphGenerationDispatcher.isInitialized() || glyphGenerationDispatcher.isReloading()) {
            return;
        }

        int submittedCount = 0;
        int glyphSize = generation.getSettings().getGlyphSize();
        while (recoverableDemandOffset < recoverableDemandGlyphs.length
                && submittedCount < MAX_RECOVERABLE_SUBMISSIONS_PER_TICK) {
            long glyph = recoverableDemandGlyphs[recoverableDemandOffset];
            int codepoint = GlyphPageManager.unpackRecoverableCodepoint(glyph);
            FontType fontType = GlyphPageManager.unpackRecoverableFontType(glyph);
            try {
                synchronized (glyphGenerationDispatcher) {
                    long rejectedBefore = glyphGenerationDispatcher.getRejectedDemandCount();
                    glyphGenerationDispatcher.submit(new GlyphGenerationTask(recoverableDemandRuntimeVersion,
                            codepoint, fontType, glyphSize, GlyphGenerationPriority.NORMAL));
                    if (glyphGenerationDispatcher.getRejectedDemandCount() != rejectedBefore) {
                        return;
                    }
                }
            } catch (RuntimeException exception) {
                if (!recoverableDemandFailureLogged) {
                    recoverableDemandFailureLogged = true;
                    MyMod.LOG.warn("字体恢复 glyph demand 提交失败；保留 durable recovery 尾部", exception);
                }
                return;
            }
            recoverableDemandFailureLogged = false;
            recoverableDemandOffset++;
            submittedCount++;
        }
        if (recoverableDemandOffset >= recoverableDemandGlyphs.length) {
            int recoveredCount = recoverableDemandGlyphs.length;
            clearRecoverableGlyphsLocked();
            if (club.heiqi.uilib.Config.fontRuntimeDebug) {
                MyMod.LOG.info("字体系统重载后已恢复字形生成请求：{}", Integer.valueOf(recoveredCount));
            }
        }
    }

    private long[] snapshotPendingRecoverableGlyphsLocked(ActiveFontGeneration generation) {
        if (generation == null || recoverableDemandRuntimeVersion != generation.getRuntimeVersion()
                || recoverableDemandOffset >= recoverableDemandGlyphs.length) {
            return new long[0];
        }
        return Arrays.copyOfRange(recoverableDemandGlyphs, recoverableDemandOffset,
                recoverableDemandGlyphs.length);
    }

    private long[] mergeRecoverableGlyphs(long[] first, long[] second) {
        int firstLength = first == null ? 0 : first.length;
        int secondLength = second == null ? 0 : second.length;
        if (firstLength == 0) {
            return secondLength == 0 ? new long[0] : second.clone();
        }
        if (secondLength == 0) {
            return first.clone();
        }
        long[] merged = Arrays.copyOf(first, firstLength + secondLength);
        System.arraycopy(second, 0, merged, firstLength, secondLength);
        return merged;
    }

    private void clearRecoverableGlyphsLocked() {
        recoverableDemandGlyphs = new long[0];
        recoverableDemandOffset = 0;
        recoverableDemandRuntimeVersion = 0;
        recoverableDemandFailureLogged = false;
    }

    private void debugLogStats(String source) {
        if (!FontRuntimeDiagnostics.shouldLogRenderTickStats()) {
            return;
        }
        MyMod.LOG.debug("字体运行统计[{}]: {}", source, getRuntimeStats());
    }

    private static final class CommittedReload {

        private final ActiveFontGeneration generation;
        private final long[] recoverableGlyphs;
        private final boolean workerReady;

        private CommittedReload(ActiveFontGeneration generation, long[] recoverableGlyphs, boolean workerReady) {
            this.generation = generation;
            this.recoverableGlyphs = recoverableGlyphs;
            this.workerReady = workerReady;
        }
    }
}
