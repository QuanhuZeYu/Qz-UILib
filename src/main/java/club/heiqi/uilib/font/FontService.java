package club.heiqi.uilib.font;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
    private final FontCatalog fontCatalog = new FontCatalog();
    private final FontRegistry fontRegistry = new FontRegistry(fontCatalog);
    private final DerivedFontCache derivedFontCache = new DerivedFontCache(fontCatalog);
    private final FontMatcher fontMatcher = new FontMatcher(fontCatalog, derivedFontCache);
    private final GlyphPageManager glyphPageManager = new GlyphPageManager();
    private final GlyphGenerationDispatcher glyphGenerationDispatcher = new GlyphGenerationDispatcher();
    private final TextLayoutService textLayoutService = new TextLayoutService(fontMatcher, glyphPageManager,
            derivedFontCache);
    private FontBatchRenderer batchRenderer;
    private FontShaderProgram shaderProgram;
    private final Deque<Long> drawStageUploadTimestamps = new ArrayDeque<Long>();
    private final FontReloadSignal reloadSignal;
    private final AtomicReference<ReloadState> reloadState = new AtomicReference<ReloadState>(ReloadState.RUNNING);
    private static final AtomicBoolean NON_RENDER_THREAD_TICK_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean NON_RENDER_THREAD_SHUTDOWN_GL_LOGGED = new AtomicBoolean(false);

    private long lastDrawStageUploadAt = 0L;
    private volatile int runtimeVersion;
    private volatile int textMeasureEpoch;

    private enum ReloadState {
        RUNNING,
        RELOADING
    }

    private FontService() {
        this(new FontReloadSignal(RELOAD_QUIET_NANOS, RELOAD_RETRY_BASE_NANOS, RELOAD_RETRY_MAX_NANOS,
                System::nanoTime));
    }

    FontService(FontReloadSignal reloadSignal) {
        if (reloadSignal == null) {
            throw new IllegalArgumentException("reloadSignal 不得为 null");
        }
        this.reloadSignal = reloadSignal;
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
     * <p>该入口只准备字体注册、匹配缓存与布局缓存，不触碰字符页、调度器、批渲染器或着色器等重型渲染运行时。</p>
     */
    public void ensureLayoutRuntimeReady() {
        if (layoutRuntimeReady.get()) {
            return;
        }

        synchronized (this) {
            if (layoutRuntimeReady.get()) {
                return;
            }

            if (runtimeVersion == 0) {
                runtimeVersion++;
                glyphPageManager.setRuntimeVersion(runtimeVersion);
                glyphGenerationDispatcher.setRuntimeVersion(runtimeVersion);
                textLayoutService.setRuntimeVersion(runtimeVersion);
            }
            refreshTextMeasureRuntime();
            MyMod.LOG.info("字体布局测量运行时初始化完成：{}", FontConfig.buildSummary());
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

            reloadSignal.openLifecycle();
            int targetRuntimeVersion = runtimeVersion == 0 ? 1 : runtimeVersion;
            runtimeVersion = targetRuntimeVersion;
            glyphPageManager.setRuntimeVersion(targetRuntimeVersion);
            glyphGenerationDispatcher.setRuntimeVersion(targetRuntimeVersion);
            textLayoutService.setRuntimeVersion(targetRuntimeVersion);
            refreshTextMeasureRuntime(targetRuntimeVersion);
            glyphPageManager.initialize();
            glyphGenerationDispatcher.initialize(fontMatcher, glyphPageManager, derivedFontCache,
                    glyphPageManager::queueUpload);
            initialized.set(true);
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
            if (!initialized.get()) {
                return;
            }
            if (!captureOrVerifyRenderThreadLocked()) {
                logNonRenderThreadTickOnce();
                return;
            }
            if (!reconcileReloadIfReadyLocked()) {
                return;
            }

            glyphPageManager.flushPendingUploads(maxUploadCount);
            debugLogStats("render_tick");
        }
    }

    /**
     * 在 drawString 阶段尝试限速执行字符页上传。
     *
     * @param maxUploadCount 本次最多处理的待上传数量
     */
    public void tickDrawStage(int maxUploadCount) {
        synchronized (this) {
            if (renderThread != Thread.currentThread() || !initialized.get() || maxUploadCount <= 0) {
                return;
            }
            if (!canRunDrawStageUpload()) {
                return;
            }

            glyphPageManager.flushPendingUploads(maxUploadCount);
            long now = System.currentTimeMillis();
            lastDrawStageUploadAt = now;
            drawStageUploadTimestamps.addLast(Long.valueOf(now));
            debugLogStats("draw_stage");
        }
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
        return runtimeVersion;
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
        return textMeasureEpoch;
    }

    /**
     * 获取字符页管理器。
     *
     * @return 字符页管理器
     */
    public GlyphPageManager getGlyphPageManager() {
        return glyphPageManager;
    }

    /**
     * 获取字体匹配器。
     *
     * @return 字体匹配器
     */
    public FontMatcher getFontMatcher() {
        return fontMatcher;
    }

    /**
     * 获取字符生成调度器。
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
                derivedFontCache.getCacheHitCount(),
                derivedFontCache.getCacheMissCount(),
                textLayoutService.getWidthCacheHitCount(),
                textLayoutService.getWidthCacheMissCount());
    }

    /**
     * 关停字体系统，释放调度器线程池，并在安全线程上释放批渲染器与着色器。
     *
     * <p>JVM shutdown hook 不持有有效 OpenGL context，不能在该线程直接删除 VAO / VBO / shader 等
     * GL 资源。非渲染线程关停时只停止字体生成线程池，GL 资源交给客户端退出流程销毁底层 context。</p>
     */
    public void shutdown() {
        synchronized (this) {
            reloadSignal.closeLifecycle();
            if (!initialized.get()) {
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
            renderThread = null;
        }
    }

    private boolean canRunDrawStageUpload() {
        long now = System.currentTimeMillis();

        while (!drawStageUploadTimestamps.isEmpty()
                && now - drawStageUploadTimestamps.peekFirst().longValue() >= 1000L) {
            drawStageUploadTimestamps.pollFirst();
        }
        if (now - lastDrawStageUploadAt < (long) FontConfig.drawStageUploadIntervalMs) {
            return false;
        }
        return drawStageUploadTimestamps.size() < FontConfig.drawStageUploadLimitPerSecond;
    }

    /**
     * 刷新文本测量所依赖的基础状态。
     */
    private void refreshTextMeasureRuntime() {
        refreshTextMeasureRuntime(runtimeVersion);
    }

    /**
     * 按指定运行时版本刷新文本测量基础状态。
     *
     * @param targetRuntimeVersion 目标运行时版本
     */
    private void refreshTextMeasureRuntime(int targetRuntimeVersion) {
        fontMatcher.setRuntimeTables(targetRuntimeVersion, null);
        fontRegistry.reload();
        derivedFontCache.clear();
        fontMatcher.setRuntimeTables(targetRuntimeVersion, glyphPageManager.getRuntimeTables());
        fontMatcher.clearCache();
        textLayoutService.clearCache();
        layoutRuntimeReady.set(true);
        textMeasureEpoch++;
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
        long[] recoverableGlyphs;
        try {
            recoverableGlyphs = commitReloadLocked();
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
        finishCommittedReload(ticket.getRequest(), recoverableGlyphs);
        return true;
    }

    /** 在 runtimeVersion 发布处完成不可逆的当前代 commit。 */
    private long[] commitReloadLocked() {
        reloadState.set(ReloadState.RELOADING);
        int nextRuntimeVersion = runtimeVersion + 1;
        long[] recoverableGlyphs = glyphPageManager.snapshotRecoverableRequests();
        try {
            glyphGenerationDispatcher.pause();
            glyphGenerationDispatcher.reset();
            glyphPageManager.discardPendingUploads();
            clearRenderResources();
            glyphPageManager.setRuntimeVersion(nextRuntimeVersion);
            glyphGenerationDispatcher.setRuntimeVersion(nextRuntimeVersion);
            textLayoutService.setRuntimeVersion(nextRuntimeVersion);
            refreshTextMeasureRuntime(nextRuntimeVersion);
            drawStageUploadTimestamps.clear();
            lastDrawStageUploadAt = 0L;
            glyphGenerationDispatcher.initialize(fontMatcher, glyphPageManager, derivedFontCache,
                    glyphPageManager::queueUpload);
            runtimeVersion = nextRuntimeVersion;
            return recoverableGlyphs;
        } finally {
            reloadState.set(ReloadState.RUNNING);
        }
    }

    /** commit 后的可恢复收尾不得反向触发整代重试。 */
    private void finishCommittedReload(FontReloadRequest request, long[] recoverableGlyphs) {
        try {
            resubmitRecoverableGlyphs(recoverableGlyphs, runtimeVersion);
        } catch (RuntimeException exception) {
            MyMod.LOG.warn("字体 reload 已提交，但恢复 glyph demand 失败", exception);
        }
        int invalidatedRootCount = 0;
        try {
            invalidatedRootCount = UiLayoutInvalidationRegistry.invalidateAll();
        } catch (RuntimeException exception) {
            MyMod.LOG.warn("字体 reload 已提交，但主动布局失效失败；textMeasureEpoch 仍会驱动按需重测", exception);
        }
        MyMod.LOG.info("字体系统重载完成，原因：{}，布局树已失效：{}，运行时版本：{}，恢复请求：{}",
                request.getReason(), Integer.valueOf(invalidatedRootCount), Integer.valueOf(runtimeVersion),
                Integer.valueOf(recoverableGlyphs.length));
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

    private void resubmitRecoverableGlyphs(long[] recoverableGlyphs, int targetRuntimeVersion) {
        if (recoverableGlyphs == null || recoverableGlyphs.length == 0) {
            return;
        }

        byte[] requestedFlags = new byte[Character.MAX_CODE_POINT + 1];
        int glyphSize = Math.max(8, (int) Math.ceil(FontConfig.awtCharSize));
        int submittedCount = 0;
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
            glyphGenerationDispatcher.submit(new GlyphGenerationTask(targetRuntimeVersion, codepoint, fontType,
                    glyphSize, GlyphGenerationPriority.HIGH));
            submittedCount++;
        }
        if (club.heiqi.uilib.Config.fontRuntimeDebug) {
            MyMod.LOG.info("字体系统重载后已恢复字形生成请求：{}", Integer.valueOf(submittedCount));
        }
    }

    private void debugLogStats(String source) {
        if (!FontRuntimeDiagnostics.shouldLogRenderTickStats()) {
            return;
        }
        MyMod.LOG.debug("字体运行统计[{}]: {}", source, getRuntimeStats());
    }
}
