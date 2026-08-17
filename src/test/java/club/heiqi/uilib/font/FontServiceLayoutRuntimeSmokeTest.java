package club.heiqi.uilib.font;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.event.FontReloadRequest;
import club.heiqi.uilib.font.glyph.GlyphGenerationDispatcher;
import club.heiqi.uilib.font.glyph.GlyphGenerationResultHandler;
import club.heiqi.uilib.font.glyph.GlyphGenerationTask;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.render.FontBatchRenderer;
import club.heiqi.uilib.font.render.GlyphRenderBatch;
import club.heiqi.uilib.font.shader.FontShaderProgram;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontMatcher;
import sun.misc.Unsafe;

/**
 * `FontService` 轻量布局期入口冒烟。
 *
 * <p>该测试只覆盖 {@link FontService#ensureLayoutRuntimeReady()} 这条不触碰字符页/调度器/批渲染器/着色器
 * 的入口；reload signal、fallback 选择与并发 reload 行为分别由
 * {@code FontReloadSignalTest}、{@code FontMatcherRuntimeVersionTest}、
 * {@code GlyphGenerationDispatcherReloadBarrierTest}、
 * {@code GlyphRuntimeVersionIsolationTest} 在更接近代际隔离边界的层级覆盖。</p>
 *
 * <p>本测试默认不会运行 {@code FontService#initialize()}，避免触发 GL 资源初始化。LTS 期间若需要补 reload
 * 全链路冷测，应在能够提供 GL 上下文的集成测试中进行，而不是在 JVM 单元测试里 mock 整个调度链。</p>
 */
public class FontServiceLayoutRuntimeSmokeTest {

    /**
     * 多次调用 {@link FontService#ensureLayoutRuntimeReady} 应当幂等，不会让 measure epoch 异常翻倍。
     */
    @Test
    public void shouldKeepLayoutRuntimeIdempotentAcrossRepeatedEnsureCalls() {
        FontService service = FontService.getInstance();

        service.ensureLayoutRuntimeReady();
        int firstEpoch = service.getTextMeasureEpoch();
        service.ensureLayoutRuntimeReady();
        service.ensureLayoutRuntimeReady();
        int laterEpoch = service.getTextMeasureEpoch();

        Assert.assertTrue("epoch 应当至少在首次 ensure 后非零", firstEpoch >= 0);
        Assert.assertEquals("再次 ensure 不应让 epoch 推进", firstEpoch, laterEpoch);
    }

    /**
     * 在未调用 {@link FontService#initialize} 时，{@link FontService#isInitialized} 仍可能为 true（取决于运行顺序），
     * 但 {@link FontService#getRuntimeVersion} 应单调非递减。
     */
    @Test
    public void shouldExposeMonotonicRuntimeVersionAcrossLayoutRuntimeWarmup() {
        FontService service = FontService.getInstance();

        int versionBeforeWarmup = service.getRuntimeVersion();
        service.ensureLayoutRuntimeReady();
        int versionAfterWarmup = service.getRuntimeVersion();

        Assert.assertTrue("runtime version 应在 warmup 后非递减",
                versionAfterWarmup >= versionBeforeWarmup);
        Assert.assertTrue("runtime version 一定不为负", versionAfterWarmup >= 0);
    }

    /**
     * JVM 退出钩子等非渲染线程不应直接释放 GL 资源，避免关闭游戏时触发 native 崩溃。
     */
    @Test
    public void shouldSkipGlResourceReleaseFromNonRenderThreadShutdown() throws Exception {
        FontService service = newFontService();
        AtomicBoolean initialized = getAtomicBooleanField(service, "initialized");
        TrackingFontShaderProgram shaderProgram = new TrackingFontShaderProgram();

        initialized.set(true);
        setField(service, "shaderProgram", shaderProgram);
        setField(service, "renderThread", new Thread("Client thread"));

        service.shutdown();

        Assert.assertFalse("非渲染线程不应释放 shader", shaderProgram.closed.get());
        Assert.assertFalse("shutdown 后字体系统应标记为未初始化", initialized.get());
    }

    @Test
    public void candidateSchedulerShutdownFailureDoesNotSkipRemainingCleanup() throws Exception {
        ThrowingShutdownCandidateScheduler scheduler = new ThrowingShutdownCandidateScheduler();
        FontService service = new FontService(new FontReloadSignal(0L, 0L, 0L, System::nanoTime),
                DefaultFontGenerationCandidateFactory.INSTANCE, new GlyphGenerationDispatcher(), scheduler);
        service.initialize();
        setField(service, "renderThread", Thread.currentThread());
        service.tickMainThread(0);
        ActiveFontGeneration generation = service.getActiveGeneration();
        Assert.assertEquals(1, generation.getLeaseCount());

        service.shutdown();

        Assert.assertFalse(service.isInitialized());
        Assert.assertEquals(0, generation.getLeaseCount());
        Assert.assertTrue(scheduler.shutdownAttempted.get());
    }

    @Test
    public void reinitializeUsesOldGenerationUntilCandidateSchedulerQuiesces() throws Exception {
        FontReloadSignal signal = new FontReloadSignal(0L, 0L, 0L, System::nanoTime);
        NonQuiescentAfterShutdownScheduler scheduler = new NonQuiescentAfterShutdownScheduler();
        FontService service = new FontService(signal, DefaultFontGenerationCandidateFactory.INSTANCE,
                new GlyphGenerationDispatcher(), scheduler);
        double oldCharSize = FontConfig.charSize;
        try {
            service.initialize();
            ActiveFontGeneration original = service.getActiveGeneration();
            service.shutdown();
            double desiredCharSize = oldCharSize >= 72.0D ? oldCharSize - 1.0D : oldCharSize + 1.0D;
            FontConfig.charSize = desiredCharSize;

            service.initialize();

            Assert.assertTrue(service.isInitialized());
            Assert.assertSame("retiring candidate worker 未退出时先用旧代恢复服务", original,
                    service.getActiveGeneration());
            Assert.assertEquals("settings 差异必须保留为 durable reload signal", 1, signal.getPendingCount());
            Assert.assertTrue(getAtomicBooleanField(service, "layoutRuntimeReady").get());

            setField(service, "renderThread", Thread.currentThread());
            service.tickMainThread(0);

            Assert.assertSame("retiring scheduler 拒绝 candidate 后仍须保留旧代", original,
                    service.getActiveGeneration());
            Assert.assertEquals(1, signal.getPendingCount());
            Assert.assertEquals(1, signal.getConsecutiveFailures());
            Assert.assertEquals(1, scheduler.getRejectedCount());

            scheduler.allowQuiescence();
            service.tickMainThread(0);

            ActiveFontGeneration converged = service.getActiveGeneration();
            Assert.assertNotSame("旧 scheduler 收敛后必须最终提交 desired generation", original, converged);
            Assert.assertEquals(desiredCharSize, converged.getSettings().getCharSize(), 0.0D);
            Assert.assertEquals(0, signal.getPendingCount());
            Assert.assertEquals(1, scheduler.getSubmittedCount());
        } finally {
            FontConfig.charSize = oldCharSize;
            scheduler.allowQuiescence();
            service.shutdown();
        }
    }

    /** worker 只发布 reload signal，draw-stage 不得推进 reconcile，shutdown 清除旧 lifecycle。 */
    @Test
    public void shouldKeepReloadSignalOutOfWorkerAndDrawExecutionPaths() throws Exception {
        AtomicLong now = new AtomicLong(0L);
        FontReloadSignal signal = new FontReloadSignal(100L, 50L, 200L, now::get);
        FontService service = new FontService(signal);
        int initialRuntimeVersion = service.getRuntimeVersion();

        Thread worker = new Thread(
                () -> service.reload(new FontReloadRequest("worker-signal")), "font-reload-test-worker");
        worker.setDaemon(true);
        worker.start();
        worker.join(5000L);

        Assert.assertFalse("reload signal worker 应及时结束", worker.isAlive());
        Assert.assertFalse("signal 不应初始化字体运行时", service.isInitialized());
        Assert.assertEquals("signal 不应同步推进 runtime version", initialRuntimeVersion,
                service.getRuntimeVersion());
        Assert.assertEquals(1, signal.getPendingCount());
        Assert.assertFalse(signal.isInFlight());

        getAtomicBooleanField(service, "initialized").set(true);
        setField(service, "renderThread", Thread.currentThread());
        now.set(1000L);
        service.tickDrawStage(1);
        Assert.assertEquals("draw-stage 只允许 upload，不得 reconcile", 1, signal.getPendingCount());
        Assert.assertFalse(signal.isInFlight());

        service.shutdown();
        Assert.assertEquals("shutdown 必须清除旧 lifecycle signal", 0, signal.getPendingCount());
    }

    @Test
    public void drawStageUploadExceptionStillConsumesRateLimitAttempt() throws Exception {
        FontService service = new FontService(new FontReloadSignal(0L, 0L, 0L, System::nanoTime));
        ThrowingFlushPageManager pageManager = new ThrowingFlushPageManager();
        setField(service, "glyphPageManager", pageManager);
        getAtomicBooleanField(service, "initialized").set(true);
        setField(service, "renderThread", Thread.currentThread());
        double previousInterval = FontConfig.drawStageUploadIntervalMs;
        int previousLimit = FontConfig.drawStageUploadLimitPerSecond;
        FontConfig.drawStageUploadIntervalMs = 60000.0D;
        FontConfig.drawStageUploadLimitPerSecond = 20;
        try {
            try {
                service.tickDrawStage(1);
                Assert.fail("首次 upload 异常应传播");
            } catch (IllegalStateException expected) {
                Assert.assertEquals("flush failure", expected.getMessage());
            }

            service.tickDrawStage(1);

            Assert.assertEquals("异常尝试必须进入 draw-stage 限速账本", 1, pageManager.flushCount.get());
            Assert.assertTrue((Long) getField(service, "lastDrawStageUploadAt") > 0L);
        } finally {
            FontConfig.drawStageUploadIntervalMs = previousInterval;
            FontConfig.drawStageUploadLimitPerSecond = previousLimit;
            service.shutdown();
        }
    }

    /** 非客户端线程不能抢占 owner；shutdown 后新 lifecycle 仍须由明确 Client thread 绑定。 */
    @Test
    public void shouldBindOnlyExplicitClientRenderThreadAcrossLifecycles() throws Exception {
        AtomicLong now = new AtomicLong(0L);
        FontReloadSignal signal = new FontReloadSignal(0L, 0L, 0L, now::get);
        FontService service = new FontService(signal);
        getAtomicBooleanField(service, "initialized").set(true);

        service.tickMainThread(0);
        Assert.assertNull("普通测试线程不得抢占 render owner", getField(service, "renderThread"));

        Thread firstOwner = runRenderTick(service, "Client thread-font-owner-1");
        Assert.assertSame(firstOwner, getField(service, "renderThread"));
        service.shutdown();
        Assert.assertNull(getField(service, "renderThread"));

        service.initialize();
        Thread wrong = runRenderTick(service, "font-owner-impostor");
        Assert.assertNull("错误线程不得在新 lifecycle 抢占 owner", getField(service, "renderThread"));
        Assert.assertFalse(wrong.isAlive());

        Thread secondOwner = runRenderTick(service, "Client thread-font-owner-2");
        Assert.assertSame(secondOwner, getField(service, "renderThread"));
        service.shutdown();
    }

    /** candidate 阶段 RuntimeException/Error 都必须释放 ticket，并完整保留旧 active generation。 */
    @Test
    public void shouldReleaseReloadTicketWhenCommitFails() throws Exception {
        AtomicLong now = new AtomicLong(0L);
        AtomicReference<Throwable> nextFailure = new AtomicReference<Throwable>();
        FontReloadSignal signal = new FontReloadSignal(0L, 10L, 20L, now::get);
        FontGenerationCandidateFactory candidateFactory = (fontRegistry, request) -> {
            Throwable failure = nextFailure.getAndSet(null);
            if (failure instanceof RuntimeException) {
                throw (RuntimeException) failure;
            }
            if (failure instanceof Error) {
                throw (Error) failure;
            }
            return DefaultFontGenerationCandidateFactory.INSTANCE.prepare(fontRegistry, request);
        };
        FontService service = new FontService(signal, candidateFactory);
        service.ensureLayoutRuntimeReady();
        ActiveFontGeneration oldGeneration = service.getActiveGeneration();
        Object oldRuntimeTables = oldGeneration.getRuntimeTables();
        getAtomicBooleanField(service, "initialized").set(true);
        setField(service, "renderThread", Thread.currentThread());

        nextFailure.set(new IllegalStateException("font reload runtime failure"));
        service.reload(new FontReloadRequest("runtime-failure"));
        service.tickMainThread(0);
        Assert.assertEquals(1, signal.getPendingCount());
        Assert.assertEquals(0L, signal.getAppliedSequence());
        Assert.assertEquals(1, signal.getConsecutiveFailures());
        Assert.assertFalse(signal.isInFlight());
        Assert.assertSame(oldGeneration, service.getActiveGeneration());
        Assert.assertTrue(oldGeneration.isActive());
        Assert.assertSame(oldRuntimeTables, service.getActiveGeneration().getRuntimeTables());

        now.set(10L);
        nextFailure.set(new AssertionError("font reload error"));
        try {
            service.tickMainThread(0);
            Assert.fail("AssertionError 必须在释放 ticket 后继续传播");
        } catch (AssertionError expected) {
            Assert.assertEquals("font reload error", expected.getMessage());
        }
        Assert.assertEquals(1, signal.getPendingCount());
        Assert.assertEquals(0L, signal.getAppliedSequence());
        Assert.assertEquals(2, signal.getConsecutiveFailures());
        Assert.assertFalse(signal.isInFlight());
        Assert.assertSame(oldGeneration, service.getActiveGeneration());
        Assert.assertTrue(oldGeneration.isActive());
        Assert.assertSame(oldRuntimeTables, service.getActiveGeneration().getRuntimeTables());

        service.shutdown();
    }

    /** 成功换代只发布一个完整 envelope，并原地转移唯一 direct table storage。 */
    @Test
    public void shouldPublishGenerationAndReuseRuntimeTableStorage() throws Exception {
        AtomicLong now = new AtomicLong(0L);
        FontReloadSignal signal = new FontReloadSignal(0L, 0L, 0L, now::get);
        FontService service = new FontService(signal, changingResourceCandidateFactory());
        service.ensureLayoutRuntimeReady();
        ActiveFontGeneration oldGeneration = service.getActiveGeneration();
        getAtomicBooleanField(service, "initialized").set(true);
        setField(service, "renderThread", Thread.currentThread());

        service.reload(new FontReloadRequest("generation-publication"));
        service.tickMainThread(0);

        ActiveFontGeneration newGeneration = service.getActiveGeneration();
        Assert.assertNotSame(oldGeneration, newGeneration);
        Assert.assertFalse(oldGeneration.isActive());
        Assert.assertTrue(newGeneration.isActive());
        Assert.assertEquals(oldGeneration.getRuntimeVersion() + 1, newGeneration.getRuntimeVersion());
        Assert.assertEquals(oldGeneration.getTextMeasureEpoch() + 1, newGeneration.getTextMeasureEpoch());
        Assert.assertSame(oldGeneration.getRuntimeTables(), newGeneration.getRuntimeTables());
        Assert.assertEquals(0, signal.getPendingCount());

        service.shutdown();
    }

    @Test
    public void shouldAcknowledgeUnchangedFingerprintWithoutReplacingGeneration() throws Exception {
        FontReloadSignal signal = new FontReloadSignal(0L, 0L, 0L, System::nanoTime);
        FontService service = new FontService(signal);
        try {
            service.initialize();
            setField(service, "renderThread", Thread.currentThread());
            ActiveFontGeneration original = service.getActiveGeneration();

            service.reload(new FontReloadRequest("unchanged-resource-fingerprint"));
            service.tickMainThread(0);

            Assert.assertSame(original, service.getActiveGeneration());
            Assert.assertEquals(0, signal.getPendingCount());
            Assert.assertEquals(original.getRuntimeVersion(), service.getRuntimeVersion());
        } finally {
            service.shutdown();
        }
    }

    @Test
    public void shouldAcknowledgeUnchangedFingerprintWhileRetirementIsDeferred() throws Exception {
        FontReloadSignal signal = new FontReloadSignal(0L, 0L, 0L, System::nanoTime);
        FontService service = new FontService(signal);
        GlyphPageManager originalPageManager = null;
        try {
            service.initialize();
            setField(service, "renderThread", Thread.currentThread());
            ActiveFontGeneration original = service.getActiveGeneration();
            originalPageManager = (GlyphPageManager) getField(service, "glyphPageManager");
            RetirementDeferredPageManager deferredPageManager = new RetirementDeferredPageManager();
            setField(service, "glyphPageManager", deferredPageManager);

            service.reload(new FontReloadRequest("unchanged-with-retiring-page"));
            service.tickMainThread(0);

            Assert.assertSame(original, service.getActiveGeneration());
            Assert.assertEquals(0, signal.getPendingCount());
            Assert.assertEquals(1, deferredPageManager.retirementChecks.get());
        } finally {
            if (originalPageManager != null) {
                setField(service, "glyphPageManager", originalPageManager);
            }
            service.shutdown();
        }
    }

    @Test
    public void shouldDiscardCandidateWhenNewSignalArrivesAtCommitAdmission() throws Exception {
        FontReloadSignal signal = new FontReloadSignal(0L, 0L, 0L, System::nanoTime);
        SignalingResetDispatcher dispatcher = new SignalingResetDispatcher(signal);
        FontService service = new FontService(signal, changingResourceCandidateFactory(), dispatcher);
        try {
            service.initialize();
            setField(service, "renderThread", Thread.currentThread());
            ActiveFontGeneration original = service.getActiveGeneration();
            dispatcher.signalOnNextReset();

            service.reload(new FontReloadRequest("candidate-before-commit-admission"));
            service.tickMainThread(0);

            Assert.assertSame(original, service.getActiveGeneration());
            Assert.assertFalse(signal.isInFlight());
            Assert.assertEquals(2, signal.getPendingCount());
            Assert.assertTrue(dispatcher.isInitialized());

            service.tickMainThread(0);

            Assert.assertEquals(original.getRuntimeVersion() + 1, service.getRuntimeVersion());
            Assert.assertEquals(0, signal.getPendingCount());
        } finally {
            service.shutdown();
        }
    }

    @Test
    public void shouldDiscardNoOpCandidateWhenNewSignalArrivesAtCommitAdmission() throws Exception {
        FontReloadSignal signal = new FontReloadSignal(0L, 0L, 0L, System::nanoTime);
        ManualCandidateScheduler scheduler = new ManualCandidateScheduler();
        FontService service = new FontService(signal, DefaultFontGenerationCandidateFactory.INSTANCE,
                new GlyphGenerationDispatcher(), scheduler);
        try {
            service.initialize();
            setField(service, "renderThread", Thread.currentThread());
            ActiveFontGeneration original = service.getActiveGeneration();

            service.reload(new FontReloadRequest("no-op-before-commit-admission"));
            service.tickMainThread(0);
            scheduler.runNext("font-no-op-admission-worker-1");
            scheduler.runBeforeNextCompletedPoll(
                    () -> service.reload(new FontReloadRequest("new-signal-at-no-op-admission")));
            service.tickMainThread(0);

            Assert.assertSame(original, service.getActiveGeneration());
            Assert.assertFalse(signal.isInFlight());
            Assert.assertEquals(2, signal.getPendingCount());

            service.tickMainThread(0);
            scheduler.runNext("font-no-op-admission-worker-2");
            service.tickMainThread(0);

            Assert.assertSame(original, service.getActiveGeneration());
            Assert.assertEquals(0, signal.getPendingCount());
        } finally {
            service.shutdown();
        }
    }

    @Test
    public void shouldRetainCandidateWhenLayoutReadScopeBlocksPublication() throws Exception {
        FontReloadSignal signal = new FontReloadSignal(0L, 0L, 0L, System::nanoTime);
        AtomicInteger prepareCount = new AtomicInteger();
        FontGenerationCandidateFactory factory = (fontRegistry, request) -> {
            prepareCount.incrementAndGet();
            FontGenerationCandidate candidate = DefaultFontGenerationCandidateFactory.INSTANCE.prepare(fontRegistry,
                    request);
            return request.getDesiredSequence() > 0L
                    ? candidate.withResourceFingerprint(FontResourceFingerprint.synthetic("layout-lock-defer"))
                    : candidate;
        };
        ControllableFailureDispatcher dispatcher = new ControllableFailureDispatcher();
        FontService service = new FontService(signal, factory, dispatcher);
        ReentrantReadWriteLock generationLock = (ReentrantReadWriteLock) getField(service, "generationLock");
        try {
            service.initialize();
            setField(service, "renderThread", Thread.currentThread());
            ActiveFontGeneration original = service.getActiveGeneration();
            int prepareCountBeforeReload = prepareCount.get();
            generationLock.readLock().lock();
            try {
                service.reload(new FontReloadRequest("layout-read-scope-defer"));
                service.tickMainThread(0);
            } finally {
                generationLock.readLock().unlock();
            }

            Assert.assertSame(original, service.getActiveGeneration());
            Assert.assertTrue(signal.isInFlight());
            Assert.assertEquals(prepareCountBeforeReload + 1, prepareCount.get());
            Assert.assertEquals("read scope 忙时不得先破坏旧 worker", 0, dispatcher.resetAttempts.get());

            service.tickMainThread(0);

            Assert.assertEquals(original.getRuntimeVersion() + 1, service.getRuntimeVersion());
            Assert.assertEquals("write tryLock defer 不得重做 candidate", prepareCountBeforeReload + 1,
                    prepareCount.get());
            Assert.assertEquals(1, dispatcher.resetAttempts.get());
        } finally {
            service.shutdown();
        }
    }

    /** pre-commit worker stop 失败保留旧代；post-commit worker 启动失败由后续 tick 原地恢复。 */
    @Test
    public void shouldSeparatePreCommitFailureFromDurablePostCommitWorkerRecovery() throws Exception {
        AtomicLong now = new AtomicLong(0L);
        FontReloadSignal signal = new FontReloadSignal(0L, 0L, 0L, now::get);
        ControllableFailureDispatcher dispatcher = new ControllableFailureDispatcher();
        AtomicInteger candidatePrepareCount = new AtomicInteger();
        AtomicBoolean returnStaleCandidate = new AtomicBoolean();
        FontGenerationCandidateFactory candidateFactory = (fontRegistry, request) -> {
            candidatePrepareCount.incrementAndGet();
            FontGenerationCandidate candidate = DefaultFontGenerationCandidateFactory.INSTANCE.prepare(fontRegistry,
                    request);
            if (returnStaleCandidate.compareAndSet(true, false)) {
                return new FontGenerationCandidate(request.getRuntimeVersion() - 1,
                        request.getTextMeasureEpoch() - 1,
                        candidate.getSettings(), candidate.getPreparedCatalog(), candidate.getMetrics());
            }
            return request.getDesiredSequence() > 0L
                    ? candidate.withResourceFingerprint(FontResourceFingerprint.synthetic(
                            "service-failure-test-" + request.getDesiredSequence()))
                    : candidate;
        };
        FontService service = new FontService(signal, candidateFactory, dispatcher);
        service.initialize();
        setField(service, "renderThread", Thread.currentThread());
        ActiveFontGeneration oldGeneration = service.getActiveGeneration();
        Object oldRuntimeTables = oldGeneration.getRuntimeTables();
        int prepareCountBeforeReload = candidatePrepareCount.get();

        returnStaleCandidate.set(true);
        service.reload(new FontReloadRequest("invalid-candidate-successor"));
        service.tickMainThread(0);

        Assert.assertSame(oldGeneration, service.getActiveGeneration());
        Assert.assertTrue(oldGeneration.isActive());
        Assert.assertSame(oldRuntimeTables, service.getActiveGeneration().getRuntimeTables());
        Assert.assertEquals("无效 successor 必须在 dispatcher reset 前被拒绝", 0, dispatcher.resetAttempts.get());
        Assert.assertEquals(prepareCountBeforeReload + 1, candidatePrepareCount.get());
        Assert.assertEquals(1, signal.getPendingCount());

        setField(service, "recoverableDemandGlyphs", new long[] { recoverable('R') });
        setField(service, "recoverableDemandRuntimeVersion", Integer.valueOf(oldGeneration.getRuntimeVersion()));
        dispatcher.failNextReset();
        service.tickMainThread(0);

        Assert.assertSame(oldGeneration, service.getActiveGeneration());
        Assert.assertTrue(oldGeneration.isActive());
        Assert.assertSame(oldRuntimeTables, service.getActiveGeneration().getRuntimeTables());
        Assert.assertEquals("失败发生前必须已经完成 CPU candidate prepare", prepareCountBeforeReload + 2,
                candidatePrepareCount.get());
        Assert.assertEquals(1, signal.getPendingCount());
        Assert.assertTrue("旧 worker 应在失败后恢复", dispatcher.isInitialized());
        Assert.assertEquals(1, ((long[]) getField(service, "recoverableDemandGlyphs")).length);
        Assert.assertEquals(oldGeneration.getRuntimeVersion(),
                ((Integer) getField(service, "recoverableDemandRuntimeVersion")).intValue());

        dispatcher.failNextSetRuntimeVersion();
        service.tickMainThread(0);

        ActiveFontGeneration committedGeneration = service.getActiveGeneration();
        Assert.assertNotSame(oldGeneration, committedGeneration);
        Assert.assertFalse(oldGeneration.isActive());
        Assert.assertTrue(committedGeneration.isActive());
        Assert.assertEquals(0, signal.getPendingCount());
        Assert.assertFalse("post-commit worker 版本绑定按测试注入失败", dispatcher.isInitialized());
        Assert.assertTrue(((Boolean) getField(service, "workerRecoveryPending")).booleanValue());
        Assert.assertEquals(1, ((long[]) getField(service, "workerRecoveryGlyphs")).length);

        int committedVersion = committedGeneration.getRuntimeVersion();
        service.tickMainThread(0);

        Assert.assertSame("worker recovery 不得重做 generation", committedGeneration, service.getActiveGeneration());
        Assert.assertEquals(committedVersion, service.getRuntimeVersion());
        Assert.assertTrue(dispatcher.isInitialized());
        Assert.assertTrue(dispatcher.initializeAttempts.get() >= 3);
        Assert.assertFalse(((Boolean) getField(service, "workerRecoveryPending")).booleanValue());
        Assert.assertEquals(0, ((long[]) getField(service, "recoverableDemandGlyphs")).length);

        dispatcher.failNextInitialize();
        service.reload(new FontReloadRequest("post-commit-initialize-failure"));
        service.tickMainThread(0);

        ActiveFontGeneration initializeFailureGeneration = service.getActiveGeneration();
        Assert.assertNotSame(committedGeneration, initializeFailureGeneration);
        Assert.assertEquals(committedVersion + 1, initializeFailureGeneration.getRuntimeVersion());
        Assert.assertEquals(0, signal.getPendingCount());
        Assert.assertFalse(dispatcher.isInitialized());

        service.tickMainThread(0);

        Assert.assertSame(initializeFailureGeneration, service.getActiveGeneration());
        Assert.assertTrue(dispatcher.isInitialized());
        service.shutdown();
    }

    @Test
    public void recoverableGlyphTailSurvivesDispatcherCapacityRejection() throws Exception {
        CapacityRecordingDispatcher dispatcher = new CapacityRecordingDispatcher(2);
        FontService service = new FontService(new FontReloadSignal(0L, 0L, 0L, System::nanoTime),
                changingResourceCandidateFactory(), dispatcher);
        service.initialize();
        ActiveFontGeneration generation = service.getActiveGeneration();
        setField(service, "renderThread", Thread.currentThread());
        setField(service, "recoverableDemandGlyphs", new long[] {
                recoverable('A'), recoverable('B'), recoverable('C'), recoverable('D'), recoverable('E') });
        setField(service, "recoverableDemandRuntimeVersion", Integer.valueOf(generation.getRuntimeVersion()));

        service.tickMainThread(0);

        Assert.assertEquals(2, dispatcher.acceptedCodepoints.size());
        Assert.assertEquals(2, ((Integer) getField(service, "recoverableDemandOffset")).intValue());
        Assert.assertEquals(1L, dispatcher.rejected.get());

        service.reload(new FontReloadRequest("merge-rejected-recovery-tail"));
        service.tickMainThread(0);

        ActiveFontGeneration reloadedGeneration = service.getActiveGeneration();
        Assert.assertEquals(generation.getRuntimeVersion() + 1, reloadedGeneration.getRuntimeVersion());
        Assert.assertEquals(2, dispatcher.acceptedCodepoints.size());
        Assert.assertEquals(0, ((Integer) getField(service, "recoverableDemandOffset")).intValue());
        Assert.assertEquals(3, ((long[]) getField(service, "recoverableDemandGlyphs")).length);
        Assert.assertEquals(reloadedGeneration.getRuntimeVersion(),
                ((Integer) getField(service, "recoverableDemandRuntimeVersion")).intValue());
        Assert.assertEquals(2L, dispatcher.rejected.get());

        dispatcher.capacity = 5;
        service.tickMainThread(0);

        Assert.assertEquals(5, dispatcher.acceptedCodepoints.size());
        Assert.assertEquals(Integer.valueOf((int) 'A'), dispatcher.acceptedCodepoints.get(0));
        Assert.assertEquals(Integer.valueOf((int) 'E'), dispatcher.acceptedCodepoints.get(4));
        Assert.assertEquals(0, ((long[]) getField(service, "recoverableDemandGlyphs")).length);

        setField(service, "recoverableDemandGlyphs", new long[] { recoverable('F') });
        setField(service, "recoverableDemandRuntimeVersion",
                Integer.valueOf(reloadedGeneration.getRuntimeVersion()));
        service.shutdown();
        Assert.assertEquals(0, ((long[]) getField(service, "recoverableDemandGlyphs")).length);
    }

    @Test
    public void readOnlyRuntimeViewDoesNotActivateEmptyAtlasPage() {
        GlyphPageManager manager = new GlyphPageManager();
        manager.setRuntimeVersion(1);
        manager.initialize();
        GlyphRuntimeTablesView view = new GlyphRuntimeTablesView(manager.getRuntimeTables(), manager, null, 1);

        Assert.assertTrue(view.getPageCount(FontType.NORMAL) > 0);
        Assert.assertEquals(0, view.getPageTextureId(FontType.NORMAL, 0));
        Assert.assertEquals(0, manager.getRuntimeTables().normalPages[0].getTextureId());
    }

    /** layout-only warmup 后配置再变化，完整 initialize 必须重新捕获 desired settings。 */
    @Test
    public void shouldRecaptureDesiredSettingsWhenInitializingAfterLayoutWarmup() throws Exception {
        double oldCharSize = FontConfig.charSize;
        FontReloadSignal signal = new FontReloadSignal(0L, 0L, 0L, System::nanoTime);
        FontService service = new FontService(signal);
        try {
            service.ensureLayoutRuntimeReady();
            ActiveFontGeneration layoutGeneration = service.getActiveGeneration();
            double desiredCharSize = oldCharSize >= 72.0D ? oldCharSize - 1.0D : oldCharSize + 1.0D;
            FontConfig.charSize = desiredCharSize;

            service.initialize();

            Assert.assertSame("initialize 不得在未知调用线程同步替换 generation", layoutGeneration,
                    service.getActiveGeneration());
            Assert.assertEquals(1, signal.getPendingCount());

            setField(service, "renderThread", Thread.currentThread());
            service.tickMainThread(0);

            ActiveFontGeneration initializedGeneration = service.getActiveGeneration();
            Assert.assertNotSame(layoutGeneration, initializedGeneration);
            Assert.assertEquals(layoutGeneration.getRuntimeVersion() + 1,
                    initializedGeneration.getRuntimeVersion());
            Assert.assertEquals(desiredCharSize, initializedGeneration.getSettings().getCharSize(), 0.0D);
        } finally {
            FontConfig.charSize = oldCharSize;
            service.shutdown();
        }
    }

    @Test
    public void reinitializeRechecksResourcesEvenWhenSettingsAreUnchanged() throws Exception {
        FontReloadSignal signal = new FontReloadSignal(0L, 0L, 0L, System::nanoTime);
        FontService service = new FontService(signal, changingResourceCandidateFactory());
        try {
            service.initialize();
            ActiveFontGeneration original = service.getActiveGeneration();
            service.shutdown();

            service.initialize();

            Assert.assertSame(original, service.getActiveGeneration());
            Assert.assertEquals("reinitialize 必须保留 resource-only 复核 intent", 1,
                    signal.getPendingCount());

            setField(service, "renderThread", Thread.currentThread());
            service.tickMainThread(0);

            Assert.assertNotSame(original, service.getActiveGeneration());
            Assert.assertEquals(original.getRuntimeVersion() + 1, service.getRuntimeVersion());
            Assert.assertEquals(0, signal.getPendingCount());
        } finally {
            service.shutdown();
        }
    }

    @Test
    public void shouldDiscardSupersededBackgroundCandidateAndCommitLatestFlight() throws Exception {
        AtomicLong now = new AtomicLong(0L);
        FontReloadSignal signal = new FontReloadSignal(0L, 0L, 0L, now::get);
        ManualCandidateScheduler scheduler = new ManualCandidateScheduler();
        AtomicInteger prepareCount = new AtomicInteger();
        List<Thread> reloadPreparationThreads = new ArrayList<Thread>();
        FontGenerationCandidateFactory factory = (fontRegistry, request) -> {
            prepareCount.incrementAndGet();
            if (request.getDesiredSequence() > 0L) {
                synchronized (reloadPreparationThreads) {
                    reloadPreparationThreads.add(Thread.currentThread());
                }
            }
            FontGenerationCandidate candidate = DefaultFontGenerationCandidateFactory.INSTANCE.prepare(fontRegistry,
                    request);
            return request.getDesiredSequence() > 0L
                    ? candidate.withResourceFingerprint(FontResourceFingerprint.synthetic(
                            "async-flight-test-" + request.getDesiredSequence()))
                    : candidate;
        };
        FontService service = new FontService(signal, factory, new GlyphGenerationDispatcher(), scheduler);
        try {
            service.initialize();
            setField(service, "renderThread", Thread.currentThread());
            ActiveFontGeneration original = service.getActiveGeneration();

            service.reload(new FontReloadRequest("first-background-candidate"));
            service.tickMainThread(0);

            Assert.assertSame(original, service.getActiveGeneration());
            Assert.assertTrue(signal.isInFlight());
            Assert.assertEquals(1, scheduler.getSubmittedCount());

            service.reload(new FontReloadRequest("latest-background-candidate"));
            scheduler.runNext("font-candidate-test-worker-1");
            service.tickMainThread(0);

            Assert.assertSame("更新 signal 必须丢弃旧 candidate", original, service.getActiveGeneration());
            Assert.assertFalse(signal.isInFlight());
            Assert.assertEquals(2, signal.getPendingCount());

            service.tickMainThread(0);
            Assert.assertEquals(2, scheduler.getSubmittedCount());
            scheduler.runNext("font-candidate-test-worker-2");
            service.tickMainThread(0);

            Assert.assertEquals(original.getRuntimeVersion() + 1, service.getRuntimeVersion());
            Assert.assertEquals(0, signal.getPendingCount());
            Assert.assertEquals("cold init + 两个 reload candidate", 3, prepareCount.get());
            synchronized (reloadPreparationThreads) {
                Assert.assertEquals(2, reloadPreparationThreads.size());
                Assert.assertNotSame(Thread.currentThread(), reloadPreparationThreads.get(0));
                Assert.assertNotSame(Thread.currentThread(), reloadPreparationThreads.get(1));
            }
        } finally {
            service.shutdown();
        }
    }

    @Test
    public void shouldKeepCompletedCandidateUntilFrameLeaseIsReleased() throws Exception {
        FontReloadSignal signal = new FontReloadSignal(0L, 0L, 0L, System::nanoTime);
        AtomicInteger prepareCount = new AtomicInteger();
        FontGenerationCandidateFactory factory = (fontRegistry, request) -> {
            prepareCount.incrementAndGet();
            FontGenerationCandidate candidate = DefaultFontGenerationCandidateFactory.INSTANCE.prepare(fontRegistry,
                    request);
            return request.getDesiredSequence() > 0L
                    ? candidate.withResourceFingerprint(FontResourceFingerprint.synthetic(
                            "lease-test-" + request.getDesiredSequence()))
                    : candidate;
        };
        FontService service = new FontService(signal, factory);
        ActiveFontGeneration.GenerationLease heldLease = null;
        try {
            service.initialize();
            setField(service, "renderThread", Thread.currentThread());
            ActiveFontGeneration original = service.getActiveGeneration();
            heldLease = original.tryAcquireFrameLease();
            int prepareCountBeforeReload = prepareCount.get();

            service.reload(new FontReloadRequest("lease-delayed-publication"));
            service.tickMainThread(0);

            Assert.assertSame(original, service.getActiveGeneration());
            Assert.assertTrue(signal.isInFlight());
            Assert.assertEquals(prepareCountBeforeReload + 1, prepareCount.get());

            heldLease.close();
            heldLease = null;
            service.tickMainThread(0);

            Assert.assertEquals(original.getRuntimeVersion() + 1, service.getRuntimeVersion());
            Assert.assertEquals("lease defer 不得重做 CPU candidate", prepareCountBeforeReload + 1,
                    prepareCount.get());
            Assert.assertEquals(0, signal.getPendingCount());
        } finally {
            if (heldLease != null) {
                heldLease.close();
            }
            service.shutdown();
        }
    }

    @Test
    public void shouldKeepFrameLeaseWhileBatchStillContainsQuads() throws Exception {
        FontReloadSignal signal = new FontReloadSignal(0L, 0L, 0L, System::nanoTime);
        AtomicInteger prepareCount = new AtomicInteger();
        FontGenerationCandidateFactory factory = (fontRegistry, request) -> {
            prepareCount.incrementAndGet();
            FontGenerationCandidate candidate = DefaultFontGenerationCandidateFactory.INSTANCE.prepare(fontRegistry,
                    request);
            return request.getDesiredSequence() > 0L
                    ? candidate.withResourceFingerprint(FontResourceFingerprint.synthetic(
                            "frame-batch-lease-" + request.getDesiredSequence()))
                    : candidate;
        };
        FontService service = new FontService(signal, factory);
        FontBatchRenderer batchRenderer = allocateWithoutConstructor(FontBatchRenderer.class);
        try {
            service.initialize();
            setField(service, "renderThread", Thread.currentThread());
            setField(service, "batchRenderer", batchRenderer);
            setDeclaredField(batchRenderer, FontBatchRenderer.class, "decorationBatch", new GlyphRenderBatch());
            setDeclaredField(batchRenderer, FontBatchRenderer.class, "quadCount", Integer.valueOf(1));
            service.tickMainThread(0);
            ActiveFontGeneration original = service.getActiveGeneration();
            int prepareCountBeforeReload = prepareCount.get();

            service.reload(new FontReloadRequest("batch-keeps-frame-lease"));
            service.tickMainThread(0);

            Assert.assertSame(original, service.getActiveGeneration());
            Assert.assertEquals(1, original.getLeaseCount());
            Assert.assertEquals(prepareCountBeforeReload + 1, prepareCount.get());

            batchRenderer.clearFrame();
            service.tickMainThread(0);

            Assert.assertEquals(original.getRuntimeVersion() + 1, service.getRuntimeVersion());
            Assert.assertEquals("batch lease defer 不得重做 candidate", prepareCountBeforeReload + 1,
                    prepareCount.get());
        } finally {
            batchRenderer.clearFrame();
            setField(service, "batchRenderer", null);
            service.shutdown();
        }
    }

    private Thread runRenderTick(FontService service, String threadName) throws Exception {
        Thread thread = new Thread(() -> service.tickMainThread(0), threadName);
        thread.setDaemon(true);
        thread.start();
        thread.join(5000L);
        Assert.assertFalse("render tick 测试线程应及时结束", thread.isAlive());
        return thread;
    }

    private FontGenerationCandidateFactory changingResourceCandidateFactory() {
        return (fontRegistry, request) -> {
            FontGenerationCandidate candidate = DefaultFontGenerationCandidateFactory.INSTANCE.prepare(fontRegistry,
                    request);
            return request.getDesiredSequence() > 0L
                    ? candidate.withResourceFingerprint(FontResourceFingerprint.synthetic(
                            "changing-test-resource-" + request.getDesiredSequence()))
                    : candidate;
        };
    }

    private FontService newFontService() throws Exception {
        Constructor<FontService> constructor = FontService.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private AtomicBoolean getAtomicBooleanField(FontService service, String fieldName) throws Exception {
        return (AtomicBoolean) getField(service, fieldName);
    }

    private Object getField(FontService service, String fieldName) throws Exception {
        Field field = FontService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(service);
    }

    private void setField(FontService service, String fieldName, Object value) throws Exception {
        Field field = FontService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }

    private void setDeclaredField(Object target, Class<?> declaringType, String fieldName, Object value)
            throws Exception {
        Field field = declaringType.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private <T> T allocateWithoutConstructor(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static long recoverable(int codepoint) {
        return ((long) codepoint & 0x1FFFFFL) << 1;
    }

    private static final class ManualCandidateScheduler implements FontGenerationCandidateScheduler {

        private final List<ManualPendingCandidate> submitted = new ArrayList<ManualPendingCandidate>();
        private int nextToRun;

        @Override
        public synchronized PendingCandidate submit(Callable<FontGenerationCandidate> preparation) {
            ManualPendingCandidate pending = new ManualPendingCandidate(preparation);
            submitted.add(pending);
            return pending;
        }

        @Override
        public synchronized boolean isQuiescent() {
            return submitted.isEmpty();
        }

        @Override
        public synchronized void shutdown() {
            for (ManualPendingCandidate pending : submitted) {
                pending.cancel();
            }
        }

        private synchronized int getSubmittedCount() {
            return submitted.size();
        }

        private synchronized void runBeforeNextCompletedPoll(Runnable operation) {
            if (submitted.isEmpty()) {
                throw new AssertionError("没有 generation candidate 可安装 poll 接缝");
            }
            submitted.get(submitted.size() - 1).runBeforeNextCompletedPoll(operation);
        }

        private void runNext(String threadName) throws Exception {
            final ManualPendingCandidate pending;
            synchronized (this) {
                if (nextToRun >= submitted.size()) {
                    throw new AssertionError("没有待运行的 generation candidate");
                }
                pending = submitted.get(nextToRun++);
            }
            Thread worker = new Thread(pending::run, threadName);
            worker.setDaemon(true);
            worker.start();
            worker.join(30000L);
            Assert.assertFalse("generation candidate 测试 worker 应及时结束", worker.isAlive());
        }
    }

    private static final class ThrowingShutdownCandidateScheduler
            implements FontGenerationCandidateScheduler {

        private final AtomicBoolean shutdownAttempted = new AtomicBoolean();

        @Override
        public PendingCandidate submit(Callable<FontGenerationCandidate> preparation) {
            throw new AssertionError("该测试不应提交异步 candidate");
        }

        @Override
        public boolean isQuiescent() {
            return true;
        }

        @Override
        public void shutdown() {
            shutdownAttempted.set(true);
            throw new IllegalStateException("candidate scheduler shutdown failure");
        }
    }

    private static final class NonQuiescentAfterShutdownScheduler
            implements FontGenerationCandidateScheduler {

        private final AtomicBoolean quiescent = new AtomicBoolean(true);
        private final AtomicInteger submittedCount = new AtomicInteger();
        private final AtomicInteger rejectedCount = new AtomicInteger();

        @Override
        public PendingCandidate submit(Callable<FontGenerationCandidate> preparation) {
            if (!quiescent.get()) {
                rejectedCount.incrementAndGet();
                throw new IllegalStateException("retiring candidate scheduler 尚未收敛");
            }
            submittedCount.incrementAndGet();
            ManualPendingCandidate pending = new ManualPendingCandidate(preparation);
            pending.run();
            return pending;
        }

        @Override
        public boolean isQuiescent() {
            return quiescent.get();
        }

        @Override
        public void shutdown() {
            quiescent.set(false);
        }

        private void allowQuiescence() {
            quiescent.set(true);
        }

        private int getSubmittedCount() {
            return submittedCount.get();
        }

        private int getRejectedCount() {
            return rejectedCount.get();
        }
    }

    private static final class ManualPendingCandidate
            implements FontGenerationCandidateScheduler.PendingCandidate {

        private final Callable<FontGenerationCandidate> preparation;
        private volatile CandidateResult result;
        private Runnable beforeNextCompletedPoll;

        private ManualPendingCandidate(Callable<FontGenerationCandidate> preparation) {
            this.preparation = preparation;
        }

        private void run() {
            try {
                result = CandidateResult.success(preparation.call());
            } catch (Throwable throwable) {
                result = CandidateResult.failure(throwable);
            }
        }

        @Override
        public CandidateResult poll() {
            CandidateResult current = result;
            Runnable operation = null;
            synchronized (this) {
                if (current != null) {
                    operation = beforeNextCompletedPoll;
                    beforeNextCompletedPoll = null;
                }
            }
            if (operation != null) {
                operation.run();
            }
            return current;
        }

        @Override
        public void cancel() {
            if (result == null) {
                result = CandidateResult.failure(new IllegalStateException("candidate cancelled"));
            }
        }

        private synchronized void runBeforeNextCompletedPoll(Runnable operation) {
            beforeNextCompletedPoll = operation;
        }
    }

    private static class TrackingFontShaderProgram extends FontShaderProgram {

        private final AtomicBoolean closed = new AtomicBoolean(false);

        @Override
        public void close() {
            closed.set(true);
            throw new AssertionError("close 不应在 shutdown hook 线程执行");
        }
    }

    private static final class ThrowingFlushPageManager extends GlyphPageManager {

        private final AtomicInteger flushCount = new AtomicInteger(0);

        @Override
        public synchronized void flushPendingUploads(int maxCount) {
            flushCount.incrementAndGet();
            throw new IllegalStateException("flush failure");
        }
    }

    private static final class RetirementDeferredPageManager extends GlyphPageManager {

        private final AtomicInteger retirementChecks = new AtomicInteger();

        @Override
        public synchronized void flushPendingUploads(int maxCount) {
            if (maxCount <= 0) {
                retirementChecks.incrementAndGet();
                throw new RejectedExecutionException("retiring generation 仍持有 atlas page");
            }
            super.flushPendingUploads(maxCount);
        }
    }

    private static final class ControllableFailureDispatcher extends GlyphGenerationDispatcher {

        private final AtomicInteger initializeAttempts = new AtomicInteger();
        private final AtomicInteger resetAttempts = new AtomicInteger();
        private final AtomicBoolean failNextInitialize = new AtomicBoolean();
        private final AtomicBoolean failNextReset = new AtomicBoolean();
        private final AtomicBoolean failNextSetRuntimeVersion = new AtomicBoolean();

        @Override
        public void initialize(FontMatcher fontMatcher, GlyphPageManager glyphPageManager,
                DerivedFontCache derivedFontCache, GlyphGenerationResultHandler resultHandler) {
            initializeAttempts.incrementAndGet();
            if (failNextInitialize.compareAndSet(true, false)) {
                throw new IllegalStateException("worker initialize failure");
            }
            super.initialize(fontMatcher, glyphPageManager, derivedFontCache, resultHandler);
        }

        @Override
        public void setRuntimeVersion(int runtimeVersion) {
            if (failNextSetRuntimeVersion.compareAndSet(true, false)) {
                throw new IllegalStateException("worker runtime version failure");
            }
            super.setRuntimeVersion(runtimeVersion);
        }

        @Override
        public void reset() {
            resetAttempts.incrementAndGet();
            if (failNextReset.compareAndSet(true, false)) {
                throw new IllegalStateException("worker reset failure");
            }
            super.reset();
        }

        private void failNextReset() {
            failNextReset.set(true);
        }

        private void failNextInitialize() {
            failNextInitialize.set(true);
        }

        private void failNextSetRuntimeVersion() {
            failNextSetRuntimeVersion.set(true);
        }
    }

    private static final class SignalingResetDispatcher extends GlyphGenerationDispatcher {

        private final FontReloadSignal signal;
        private final AtomicBoolean signalOnNextReset = new AtomicBoolean();

        private SignalingResetDispatcher(FontReloadSignal signal) {
            this.signal = signal;
        }

        @Override
        public void reset() {
            super.reset();
            if (signalOnNextReset.compareAndSet(true, false)) {
                signal.signal(new FontReloadRequest("signal-during-commit-admission"));
            }
        }

        private void signalOnNextReset() {
            signalOnNextReset.set(true);
        }
    }

    private static final class CapacityRecordingDispatcher extends GlyphGenerationDispatcher {

        private final List<Integer> acceptedCodepoints = new ArrayList<Integer>();
        private final AtomicLong rejected = new AtomicLong();
        private int capacity;

        private CapacityRecordingDispatcher(int capacity) {
            this.capacity = capacity;
        }

        @Override
        public synchronized void submit(GlyphGenerationTask task) {
            if (acceptedCodepoints.size() >= capacity) {
                rejected.incrementAndGet();
                return;
            }
            acceptedCodepoints.add(Integer.valueOf(task.getCodepoint()));
        }

        @Override
        public boolean isInitialized() {
            return true;
        }

        @Override
        public boolean isReloading() {
            return false;
        }

        @Override
        public long getRejectedDemandCount() {
            return rejected.get();
        }
    }

}
