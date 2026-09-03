package club.heiqi.uilib.font.glyph;

import java.awt.Font;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.page.GlyphState;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.FontMatcher;

/**
 * 字形生成调度器重载屏障测试。
 */
public class GlyphGenerationDispatcherReloadBarrierTest {

    /**
     * 验证暂停后的生成请求会被重载屏障丢弃，不会污染字符状态。
     */
    @Test
    public void shouldDropSubmitWhileReloading() {
        GlyphPageManager pageManager = new GlyphPageManager();
        FontCatalog fontCatalog = new FontCatalog();
        DerivedFontCache derivedFontCache = new DerivedFontCache(fontCatalog);
        pageManager.setRuntimeVersion(1);
        GlyphGenerationDispatcher dispatcher = new GlyphGenerationDispatcher();
        dispatcher.setRuntimeVersion(1);
        dispatcher.initialize(new FontMatcher(fontCatalog, derivedFontCache), pageManager, derivedFontCache,
                pageManager::queueUpload);

        dispatcher.pause();
        dispatcher.submit(new GlyphGenerationTask(1, '中', FontType.NORMAL, 16, GlyphGenerationPriority.HIGH));

        Assert.assertTrue(dispatcher.isReloading());
        Assert.assertEquals(0, pageManager.snapshotRecoverableRequests().length);
    }

    /**
     * 验证 reset 后重新 initialize 会重新打开生成入口。
     */
    @Test
    public void shouldReopenSubmitGateAfterInitialize() {
        GlyphPageManager pageManager = new GlyphPageManager();
        FontCatalog fontCatalog = new FontCatalog();
        DerivedFontCache derivedFontCache = new DerivedFontCache(fontCatalog);
        pageManager.setRuntimeVersion(1);
        GlyphGenerationDispatcher dispatcher = new GlyphGenerationDispatcher();
        dispatcher.setRuntimeVersion(1);
        dispatcher.initialize(new FontMatcher(fontCatalog, derivedFontCache), pageManager, derivedFontCache,
                pageManager::queueUpload);

        dispatcher.reset();
        Assert.assertTrue(dispatcher.isReloading());

        dispatcher.setRuntimeVersion(2);
        pageManager.setRuntimeVersion(2);
        dispatcher.initialize(new FontMatcher(fontCatalog, derivedFontCache), pageManager, derivedFontCache,
                pageManager::queueUpload);

        Assert.assertFalse(dispatcher.isReloading());
    }

    /** worker 未终止时 reset 必须失败，不能让调用方继续转移唯一 generation storage。 */
    @Test
    public void shouldFailResetWhenExecutorDoesNotTerminate() throws Exception {
        GlyphGenerationDispatcher dispatcher = new GlyphGenerationDispatcher();
        Field executorField = GlyphGenerationDispatcher.class.getDeclaredField("executorService");
        executorField.setAccessible(true);
        NonTerminatingExecutorService stoppingExecutor = new NonTerminatingExecutorService();
        executorField.set(dispatcher, stoppingExecutor);

        try {
            dispatcher.reset();
            Assert.fail("未终止 executor 必须阻断 reset");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("未完全关停"));
        }
        Assert.assertTrue(dispatcher.isReloading());
        Assert.assertFalse(dispatcher.isInitialized());
        Assert.assertSame("超时后必须继续持有 retiring executor，禁止 initialize 创建替代池",
                stoppingExecutor, executorField.get(dispatcher));
        Assert.assertEquals(1, stoppingExecutor.awaitCount);

        try {
            dispatcher.reset();
            Assert.fail("retiring executor 未结束前仍须阻断 reset");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("未完全关停"));
        }
        Assert.assertEquals("后续 render tick 只能非阻塞探测同一 retiring executor", 1,
                stoppingExecutor.awaitCount);
    }

    @Test
    public void matcherExceptionSettlesTokenAndRemovesInFlightHandle() throws Exception {
        DispatcherFixture fixture = new DispatcherFixture(new MatcherFactory() {

            @Override
            public FontMatcher create(FontCatalog catalog, DerivedFontCache cache) {
                return new ThrowingMatcher(catalog, cache, 1);
            }
        }, result -> true);

        fixture.dispatcher.submit(task('A'));

        awaitState(fixture.pageManager, 'A', GlyphState.FAILED);
        awaitInFlightCount(fixture.dispatcher, 0);
        fixture.dispatcher.reset();
    }

    @Test
    public void rasterizerExceptionSettlesTokenAndRemovesInFlightHandle() throws Exception {
        DispatcherFixture fixture = new DispatcherFixture(new MatcherFactory() {

            @Override
            public FontMatcher create(FontCatalog catalog, DerivedFontCache cache) {
                return new ThrowingMatcher(catalog, cache, 2);
            }
        }, result -> true);

        fixture.dispatcher.submit(task('B'));

        awaitState(fixture.pageManager, 'B', GlyphState.FAILED);
        awaitInFlightCount(fixture.dispatcher, 0);
        fixture.dispatcher.reset();
    }

    @Test
    public void resultHandlerExceptionSettlesTokenAndRemovesInFlightHandle() throws Exception {
        DispatcherFixture fixture = new DispatcherFixture(new MatcherFactory() {

            @Override
            public FontMatcher create(FontCatalog catalog, DerivedFontCache cache) {
                return new StableMatcher(catalog, cache);
            }
        }, result -> {
            throw new IllegalStateException("handler failure");
        });

        fixture.dispatcher.submit(task('C'));

        awaitState(fixture.pageManager, 'C', GlyphState.FAILED);
        awaitInFlightCount(fixture.dispatcher, 0);
        fixture.dispatcher.reset();
    }

    @Test
    public void matcherErrorSettlesTokenAndRemovesInFlightHandle() throws Exception {
        DispatcherFixture fixture = new DispatcherFixture(new MatcherFactory() {

            @Override
            public FontMatcher create(FontCatalog catalog, DerivedFontCache cache) {
                return new ErrorMatcher(catalog, cache);
            }
        }, result -> true);

        fixture.dispatcher.submit(task('E'));

        awaitState(fixture.pageManager, 'E', GlyphState.FAILED);
        awaitInFlightCount(fixture.dispatcher, 0);
        fixture.dispatcher.reset();
    }

    @Test
    public void resetWaitsForConcurrentClaimAdmission() throws Exception {
        BlockingClaimPageManager pageManager = new BlockingClaimPageManager();
        FontCatalog catalog = new FontCatalog();
        DerivedFontCache cache = new DerivedFontCache(catalog);
        GlyphGenerationDispatcher dispatcher = new GlyphGenerationDispatcher();
        pageManager.setRuntimeVersion(1);
        dispatcher.setRuntimeVersion(1);
        dispatcher.initialize(new StableMatcher(catalog, cache), pageManager, cache, pageManager::queueUpload);
        AtomicReference<Throwable> submitFailure = new AtomicReference<Throwable>();
        AtomicReference<Throwable> resetFailure = new AtomicReference<Throwable>();
        CountDownLatch resetStarted = new CountDownLatch(1);
        CountDownLatch resetFinished = new CountDownLatch(1);

        Thread submitThread = new Thread(() -> {
            try {
                dispatcher.submit(task('F'));
            } catch (Throwable throwable) {
                submitFailure.set(throwable);
            }
        }, "glyph-submit-test");
        submitThread.start();
        Assert.assertTrue(pageManager.claimed.await(5L, TimeUnit.SECONDS));
        Thread resetThread = new Thread(() -> {
            resetStarted.countDown();
            try {
                dispatcher.reset();
            } catch (Throwable throwable) {
                resetFailure.set(throwable);
            } finally {
                resetFinished.countDown();
            }
        }, "glyph-reset-test");
        resetThread.start();
        Assert.assertTrue(resetStarted.await(5L, TimeUnit.SECONDS));
        // 正向证据：reset 必须停在 dispatcher 内部的 reset 帧上等待线性化。
        // 旧的 assertFalse(finished.await(100ms)) 是时长反证：满载 runner 上"慢"会被当成"在等"，
        // 反过来"根本没等"也可能因为跑得快而蒙混过去。
        assertParkedAt(resetThread, "reset", "reset 必须等待 claim 与 executor admission 完成线性化");

        pageManager.releaseClaim.countDown();
        submitThread.join(TimeUnit.SECONDS.toMillis(5L));
        resetThread.join(TimeUnit.SECONDS.toMillis(5L));

        Assert.assertFalse("submit 线程应已结束", submitThread.isAlive());
        Assert.assertFalse("reset 线程应已结束", resetThread.isAlive());
        Assert.assertNull(submitFailure.get());
        Assert.assertNull(resetFailure.get());
        Assert.assertEquals("in-flight 应清零：count=" + dispatcher.getInFlightTaskCount()
                + " admitted=" + dispatcher.getActiveDemandCount()
                + " stateF=" + pageManager.getState('F', FontType.NORMAL),
                0, dispatcher.getInFlightTaskCount());
        Assert.assertFalse("'F' 终态不应仍为 active：stateF=" + pageManager.getState('F', FontType.NORMAL)
                + " inFlight=" + dispatcher.getInFlightTaskCount()
                + " admitted=" + dispatcher.getActiveDemandCount()
                + "（reset 声称完成却留着 active 需求 = reset 没等到 admission 线性化，"
                + "或 release 之后同一码点又被重派）",
                isActive(pageManager.getState('F', FontType.NORMAL)));
    }

    @Test
    public void pauseWaitsForConcurrentClaimAdmission() throws Exception {
        BlockingClaimPageManager pageManager = new BlockingClaimPageManager();
        FontCatalog catalog = new FontCatalog();
        DerivedFontCache cache = new DerivedFontCache(catalog);
        GlyphGenerationDispatcher dispatcher = new GlyphGenerationDispatcher();
        pageManager.setRuntimeVersion(1);
        dispatcher.setRuntimeVersion(1);
        dispatcher.initialize(new StableMatcher(catalog, cache), pageManager, cache, pageManager::queueUpload);
        CountDownLatch pauseStarted = new CountDownLatch(1);
        CountDownLatch pauseFinished = new CountDownLatch(1);

        Thread submitThread = new Thread(() -> dispatcher.submit(task('G')), "glyph-pause-submit-test");
        submitThread.start();
        Assert.assertTrue(pageManager.claimed.await(5L, TimeUnit.SECONDS));
        Thread pauseThread = new Thread(() -> {
            pauseStarted.countDown();
            dispatcher.pause();
            pauseFinished.countDown();
        }, "glyph-pause-test");
        pauseThread.start();
        Assert.assertTrue(pauseStarted.await(5L, TimeUnit.SECONDS));
        // 正向证据（同上）：pause 必须停在 dispatcher 的 pause 帧上等 admission 落定。
        assertParkedAt(pauseThread, "pause", "pause 返回前必须包含已经通过 gate 的 admission");

        pageManager.releaseClaim.countDown();
        submitThread.join(TimeUnit.SECONDS.toMillis(5L));
        pauseThread.join(TimeUnit.SECONDS.toMillis(5L));

        Assert.assertFalse(submitThread.isAlive());
        Assert.assertFalse(pauseThread.isAlive());
        dispatcher.reset();
        pageManager.discardPendingUploads();
    }

    @Test
    public void admittedWorkerContinuesAfterPauseUntilResetBarrier() throws Exception {
        GlyphPageManager pageManager = new GlyphPageManager();
        FontCatalog catalog = new FontCatalog();
        DerivedFontCache cache = new DerivedFontCache(catalog);
        PauseBlockingMatcher matcher = new PauseBlockingMatcher(catalog, cache);
        GlyphGenerationDispatcher dispatcher = new GlyphGenerationDispatcher();
        pageManager.setRuntimeVersion(1);
        dispatcher.setRuntimeVersion(1);
        dispatcher.initialize(matcher, pageManager, cache, pageManager::queueUpload);

        dispatcher.submit(task('H'));
        Assert.assertTrue(matcher.entered.await(5L, TimeUnit.SECONDS));
        dispatcher.pause();
        matcher.release.countDown();

        awaitState(pageManager, 'H', GlyphState.UPLOAD_QUEUED);
        awaitInFlightCount(dispatcher, 0);
        Assert.assertEquals(1, pageManager.snapshotRecoverableRequests().length);
        dispatcher.reset();
        pageManager.discardPendingUploads();
    }

    @Test
    public void oldWorkerFinallyDoesNotRemoveNewHandleForSameGlyph() throws Exception {
        GlyphPageManager pageManager = new GlyphPageManager();
        FontCatalog catalog = new FontCatalog();
        DerivedFontCache cache = new DerivedFontCache(catalog);
        ExactRemovalHandler handler = new ExactRemovalHandler(pageManager);
        GlyphGenerationDispatcher dispatcher = new GlyphGenerationDispatcher();
        pageManager.setRuntimeVersion(1);
        dispatcher.setRuntimeVersion(1);
        dispatcher.initialize(new StableMatcher(catalog, cache), pageManager, cache, handler);

        dispatcher.submit(task('D'));
        Assert.assertTrue("旧请求应先在 handler 中结算", handler.firstSettled.await(5L, TimeUnit.SECONDS));
        dispatcher.submit(task('D'));
        handler.releaseFirst.countDown();
        Assert.assertTrue("新请求应进入同一 key 的 handler", handler.secondEntered.await(5L, TimeUnit.SECONDS));

        Assert.assertEquals("旧 worker finally 必须按 exact task remove", 1, dispatcher.getInFlightTaskCount());
        handler.releaseSecond.countDown();
        awaitState(pageManager, 'D', GlyphState.UPLOAD_QUEUED);
        awaitInFlightCount(dispatcher, 0);
        dispatcher.reset();
    }

    @Test
    public void boundedAdmissionKeepsVisibleReserveAndOneWorker() throws Exception {
        ManualClock clock = new ManualClock();
        GlyphPageManager pageManager = new GlyphPageManager();
        FontCatalog catalog = new FontCatalog();
        DerivedFontCache cache = new DerivedFontCache(catalog);
        OrderingMatcher matcher = new OrderingMatcher(catalog, cache);
        GlyphGenerationDispatcher dispatcher = new GlyphGenerationDispatcher(4, 1, 100L, clock);
        pageManager.setRuntimeVersion(1);
        dispatcher.setRuntimeVersion(1);
        dispatcher.initialize(matcher, pageManager, cache, result -> true);

        dispatcher.submit(task('X', GlyphDemandLevel.WARMUP));
        Assert.assertTrue(matcher.firstEntered.await(5L, TimeUnit.SECONDS));
        dispatcher.submit(task('A', GlyphDemandLevel.WARMUP));
        dispatcher.submit(task('B', GlyphDemandLevel.PREFETCH));
        dispatcher.submit(task('C', GlyphDemandLevel.FOREGROUND));
        dispatcher.submit(task('V', GlyphDemandLevel.VISIBLE));
        dispatcher.submit(task('W', GlyphDemandLevel.VISIBLE));

        Assert.assertEquals(4, dispatcher.getActiveDemandCount());
        Assert.assertEquals(4, dispatcher.getDemandHighWaterMark());
        Assert.assertEquals(2L, dispatcher.getRejectedDemandCount());
        Assert.assertEquals(GlyphState.ABSENT, pageManager.getState('C', FontType.NORMAL));
        Assert.assertEquals(GlyphState.ABSENT, pageManager.getState('W', FontType.NORMAL));
        Assert.assertEquals(GlyphState.QUEUED, pageManager.getState('V', FontType.NORMAL));

        matcher.releaseFirst.countDown();
        awaitInFlightCount(dispatcher, 0);
        Assert.assertEquals(1, matcher.maxConcurrent.get());
        dispatcher.reset();
    }

    @Test
    public void duplicatePromotionKeepsOneClaimAndReordersDrain() throws Exception {
        ManualClock clock = new ManualClock();
        CountingClaimPageManager pageManager = new CountingClaimPageManager('A');
        FontCatalog catalog = new FontCatalog();
        DerivedFontCache cache = new DerivedFontCache(catalog);
        OrderingMatcher matcher = new OrderingMatcher(catalog, cache);
        GlyphGenerationDispatcher dispatcher = new GlyphGenerationDispatcher(8, 2, 100L, clock);
        pageManager.setRuntimeVersion(1);
        dispatcher.setRuntimeVersion(1);
        dispatcher.initialize(matcher, pageManager, cache, result -> true);

        dispatcher.submit(task('X', GlyphDemandLevel.VISIBLE));
        Assert.assertTrue(matcher.firstEntered.await(5L, TimeUnit.SECONDS));
        dispatcher.submit(task('A', GlyphDemandLevel.WARMUP));
        dispatcher.submit(task('B', GlyphDemandLevel.FOREGROUND));
        dispatcher.submit(task('A', GlyphDemandLevel.VISIBLE));

        Assert.assertEquals(1, pageManager.countedClaims.get());
        Assert.assertEquals(3, dispatcher.getActiveDemandCount());
        Assert.assertEquals(1L, dispatcher.getPromotedDemandCount());
        matcher.releaseFirst.countDown();
        awaitInFlightCount(dispatcher, 0);

        Assert.assertEquals(asList('X', 'A', 'B'), matcher.order);
        dispatcher.reset();
    }

    @Test
    public void promotionWaitsForConcurrentQueueSelection() throws Exception {
        // 相位不再靠"数第几次 clock 调用"来押：clock 由 AgingDemandQueue#bestIndex 每次选择时
        // 读取，调用次数受 executor 节奏（首任务直跑不进队、空队阻塞不读表）影响，押死的数字
        // 只是概率成立 —— 一旦错位，被提升的需求早已结算，合并前提蒸发，表现成偶发"多出一个 A"。
        // 这里按<b>队列内容</b>停车：只剩一个候选且它尚未被匹配，那一刻它必然仍在队列里、
        // worker 必然持有队列锁，promotion 只能排队等待，合并与等待都由构造保证。
        LastCandidateSelectionClock clock = new LastCandidateSelectionClock('A');
        GlyphPageManager pageManager = new GlyphPageManager();
        FontCatalog catalog = new FontCatalog();
        DerivedFontCache cache = new DerivedFontCache(catalog);
        OrderingMatcher matcher = new OrderingMatcher(catalog, cache);
        GlyphGenerationDispatcher dispatcher = new GlyphGenerationDispatcher(8, 2, 100L, clock);
        pageManager.setRuntimeVersion(1);
        dispatcher.setRuntimeVersion(1);
        dispatcher.initialize(matcher, pageManager, cache, result -> true);
        clock.attach(queueOf(dispatcher), matcher.order);
        // A 一旦被 worker 取走就停在 RASTERIZING 不结算：合并窗口由此张开，不再靠抢时序。
        matcher.parkCodepoint('A');

        dispatcher.submit(task('X', GlyphDemandLevel.VISIBLE));
        Assert.assertTrue(matcher.firstEntered.await(5L, TimeUnit.SECONDS));
        dispatcher.submit(task('A', GlyphDemandLevel.WARMUP));
        dispatcher.submit(task('B', GlyphDemandLevel.FOREGROUND));
        matcher.releaseFirst.countDown();

        Assert.assertTrue("始终没等到目标 selection（队列只剩一个未匹配候选）的那一刻；observations="
                + clock.observations(), clock.entered.tryAcquire(5L, TimeUnit.SECONDS));

        CountDownLatch promotionStarted = new CountDownLatch(1);
        AtomicReference<Throwable> promotionFailure = new AtomicReference<Throwable>();
        CountDownLatch promotionSettled = new CountDownLatch(1);
        Thread promoter = new Thread(() -> {
            promotionStarted.countDown();
            try {
                dispatcher.submit(task('A', GlyphDemandLevel.VISIBLE));
            } catch (Throwable throwable) {
                promotionFailure.set(throwable);
            } finally {
                promotionSettled.countDown();
            }
        }, "glyph-promotion-linearization-test");
        promoter.start();
        Assert.assertTrue(promotionStarted.await(5L, TimeUnit.SECONDS));

        // 正向证据取代"100ms 内没返回"这种时长反证：必须看到 promoter 确实停在
        // AgingDemandQueue#promote 的队列锁上 —— 那才是"等待在飞的 selection"的机理本身。
        assertParkedAt(promoter, "promote", "promotion 必须等待正在进行的 queue selection");

        clock.proceed.release();
        awaitTrue(() -> dispatcher.getPromotedDemandCount() >= 1L,
                "promotion 必须与在飞的 WARMUP 需求合并（promoted 计数应 >=1）");
        Assert.assertNull(promotionFailure.get());
        matcher.releaseParked();

        promoter.join(TimeUnit.SECONDS.toMillis(5L));
        Assert.assertFalse("promoter 应已结束", promoter.isAlive());
        awaitInFlightCount(dispatcher, 0);
        Assert.assertEquals("匹配顺序须为 X,B,A：多出一个 A = 同码点双活；少一个 = 合并过头"
                        + "。promoted=" + dispatcher.getPromotedDemandCount()
                        + " admitted=" + dispatcher.getActiveDemandCount()
                        + " rejected=" + dispatcher.getRejectedDemandCount()
                        + " inFlight=" + dispatcher.getInFlightTaskCount(),
                asList('X', 'B', 'A'), matcher.order);
        dispatcher.reset();
    }

    @Test
    public void clockFailureBeforeClaimDoesNotLeakDemand() {
        GlyphPageManager pageManager = new GlyphPageManager();
        FontCatalog catalog = new FontCatalog();
        DerivedFontCache cache = new DerivedFontCache(catalog);
        GlyphGenerationDispatcher dispatcher = new GlyphGenerationDispatcher(8, 2, 100L, () -> {
            throw new IllegalStateException("clock failure");
        });
        pageManager.setRuntimeVersion(1);
        dispatcher.setRuntimeVersion(1);
        dispatcher.initialize(new StableMatcher(catalog, cache), pageManager, cache, result -> true);

        try {
            dispatcher.submit(task('T'));
            Assert.fail("clock 异常必须传播");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("clock failure", expected.getMessage());
        }

        Assert.assertEquals(GlyphState.ABSENT, pageManager.getState('T', FontType.NORMAL));
        Assert.assertEquals(0, dispatcher.getActiveDemandCount());
        dispatcher.reset();
    }

    @Test
    public void queueIteratorRemoveDeletesLiveQueuedTask() throws Exception {
        GlyphPageManager pageManager = new GlyphPageManager();
        FontCatalog catalog = new FontCatalog();
        DerivedFontCache cache = new DerivedFontCache(catalog);
        OrderingMatcher matcher = new OrderingMatcher(catalog, cache);
        GlyphGenerationDispatcher dispatcher = new GlyphGenerationDispatcher(8, 2, 100L, System::nanoTime);
        pageManager.setRuntimeVersion(1);
        dispatcher.setRuntimeVersion(1);
        dispatcher.initialize(matcher, pageManager, cache, result -> true);

        dispatcher.submit(task('X', GlyphDemandLevel.VISIBLE));
        Assert.assertTrue(matcher.firstEntered.await(5L, TimeUnit.SECONDS));
        dispatcher.submit(task('A', GlyphDemandLevel.WARMUP));
        Field executorField = GlyphGenerationDispatcher.class.getDeclaredField("executorService");
        executorField.setAccessible(true);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) executorField.get(dispatcher);
        BlockingQueue<Runnable> queue = executor.getQueue();

        Iterator<Runnable> iterator = queue.iterator();
        Assert.assertTrue(iterator.hasNext());
        iterator.next();
        iterator.remove();

        Assert.assertEquals(0, queue.size());
        matcher.releaseFirst.countDown();
        dispatcher.reset();
        Assert.assertEquals(0, dispatcher.getActiveDemandCount());
    }

    @Test
    public void agingReordersOnlyAtExactManualClockBoundary() throws Exception {
        Assert.assertEquals(asList('X', 'V', 'A'), runAgingOrder(299L));
        Assert.assertEquals(asList('X', 'A', 'V'), runAgingOrder(300L));
    }

    private static List<Integer> runAgingOrder(long releaseNanos) throws Exception {
        ManualClock clock = new ManualClock();
        GlyphPageManager pageManager = new GlyphPageManager();
        FontCatalog catalog = new FontCatalog();
        DerivedFontCache cache = new DerivedFontCache(catalog);
        OrderingMatcher matcher = new OrderingMatcher(catalog, cache);
        GlyphGenerationDispatcher dispatcher = new GlyphGenerationDispatcher(8, 2, 100L, clock);
        pageManager.setRuntimeVersion(1);
        dispatcher.setRuntimeVersion(1);
        dispatcher.initialize(matcher, pageManager, cache, result -> true);

        dispatcher.submit(task('X', GlyphDemandLevel.VISIBLE));
        Assert.assertTrue(matcher.firstEntered.await(5L, TimeUnit.SECONDS));
        dispatcher.submit(task('A', GlyphDemandLevel.WARMUP));
        clock.set(releaseNanos);
        dispatcher.submit(task('V', GlyphDemandLevel.VISIBLE));
        matcher.releaseFirst.countDown();
        awaitInFlightCount(dispatcher, 0);
        List<Integer> order = new ArrayList<Integer>(matcher.order);
        dispatcher.reset();
        return order;
    }

    /** 反射取被测 executor 的工作队列（同一个 AgingDemandQueue），供内容驱动型闸门观察队列状态。 */
    private static BlockingQueue<Runnable> queueOf(GlyphGenerationDispatcher dispatcher) throws Exception {
        Field executorField = GlyphGenerationDispatcher.class.getDeclaredField("executorService");
        executorField.setAccessible(true);
        return ((ThreadPoolExecutor) executorField.get(dispatcher)).getQueue();
    }

    /**
     * 正向证据：目标线程必须停在方法名为 {@code expectedFrameMethod} 的栈帧里。
     *
     * <p>取代 {@code assertFalse(finished.await(100ms))} 这类"固定时长反证"：时长窗口在满载
     * runner 上会把"调度慢"误判成"确实在等"，也会把"根本没等"漏过去；栈帧才是"它在等什么"的
     * 直接证据。</p>
     */
    private static void assertParkedAt(Thread thread, String expectedFrameMethod, String claim)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        StackTraceElement[] trace = new StackTraceElement[0];
        while (System.nanoTime() < deadline) {
            if (!thread.isAlive()) {
                break;
            }
            trace = thread.getStackTrace();
            Thread.State state = thread.getState();
            if (state != Thread.State.RUNNABLE) {
                for (StackTraceElement frame : trace) {
                    if (expectedFrameMethod.equals(frame.getMethodName())) {
                        // 帧在 + 不在运行态 = 它确实停在某处等人，而不是正跑得快
                        return;
                    }
                }
            }
            Thread.sleep(1L);
        }
        Assert.fail(claim + "：5s 内未观测到线程停在 " + expectedFrameMethod + " 帧上；"
                + "alive=" + thread.isAlive() + " state=" + thread.getState() + " 栈=" + describe(trace));
    }

    private static String describe(StackTraceElement[] trace) {
        StringBuilder sb = new StringBuilder("[");
        for (int index = 0; index < trace.length && index < 8; index++) {
            if (index > 0) {
                sb.append(", ");
            }
            sb.append(trace[index].getMethodName());
        }
        return sb.append(']').toString();
    }

    /** 有期限地正向等待条件成立；超时即失败并带现场。 */
    private static void awaitTrue(java.util.function.BooleanSupplier condition, String claim)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(1L);
        }
        Assert.assertTrue(claim + "（5s 内未成立）", condition.getAsBoolean());
    }

    private static List<Integer> asList(int first, int second, int third) {
        List<Integer> values = new ArrayList<Integer>();
        values.add(Integer.valueOf(first));
        values.add(Integer.valueOf(second));
        values.add(Integer.valueOf(third));
        return values;
    }

    private static GlyphGenerationTask task(int codepoint) {
        return new GlyphGenerationTask(1, codepoint, FontType.NORMAL, 32, GlyphGenerationPriority.HIGH);
    }

    private static GlyphGenerationTask task(int codepoint, GlyphDemandLevel level) {
        return new GlyphGenerationTask(1, codepoint, FontType.NORMAL, 32, level);
    }

    private static void awaitState(GlyphPageManager manager, int codepoint, GlyphState expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (System.nanoTime() < deadline) {
            if (manager.getState(codepoint, FontType.NORMAL) == expected) {
                return;
            }
            Thread.sleep(10L);
        }
        Assert.fail("等待 glyph 状态超时，expected=" + expected + " actual="
                + manager.getState(codepoint, FontType.NORMAL));
    }

    private static void awaitInFlightCount(GlyphGenerationDispatcher dispatcher, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (System.nanoTime() < deadline) {
            if (dispatcher.getInFlightTaskCount() == expected) {
                return;
            }
            Thread.sleep(10L);
        }
        Assert.fail("等待 in-flight 数量超时，expected=" + expected + " actual="
                + dispatcher.getInFlightTaskCount());
    }

    private static boolean isActive(GlyphState state) {
        return state == GlyphState.QUEUED || state == GlyphState.RASTERIZING
                || state == GlyphState.UPLOAD_QUEUED || state == GlyphState.UPLOADING;
    }

    private interface MatcherFactory {

        FontMatcher create(FontCatalog catalog, DerivedFontCache cache);
    }

    private static final class DispatcherFixture {

        private final GlyphPageManager pageManager = new GlyphPageManager();
        private final GlyphGenerationDispatcher dispatcher = new GlyphGenerationDispatcher();

        private DispatcherFixture(MatcherFactory matcherFactory, GlyphGenerationResultHandler handler) {
            FontCatalog catalog = new FontCatalog();
            DerivedFontCache cache = new DerivedFontCache(catalog);
            pageManager.setRuntimeVersion(1);
            dispatcher.setRuntimeVersion(1);
            dispatcher.initialize(matcherFactory.create(catalog, cache), pageManager, cache, handler);
        }
    }

    private static class StableMatcher extends FontMatcher {

        private StableMatcher(FontCatalog catalog, DerivedFontCache cache) {
            super(catalog, cache);
        }

        @Override
        public int matchFontIndex(int runtimeVersion, int codepoint, FontType fontType) {
            return 0;
        }

        @Override
        public Font getDerivedFont(int runtimeVersion, int fontIndex, FontType fontType, int glyphSize) {
            return new Font("Dialog", Font.PLAIN, glyphSize);
        }
    }

    private static final class ThrowingMatcher extends StableMatcher {

        private final int throwOnInvocation;
        private final AtomicInteger invocationCount = new AtomicInteger(0);

        private ThrowingMatcher(FontCatalog catalog, DerivedFontCache cache, int throwOnInvocation) {
            super(catalog, cache);
            this.throwOnInvocation = throwOnInvocation;
        }

        @Override
        public int matchFontIndex(int runtimeVersion, int codepoint, FontType fontType) {
            if (invocationCount.incrementAndGet() == throwOnInvocation) {
                throw new IllegalStateException("matcher failure " + throwOnInvocation);
            }
            return 0;
        }
    }

    private static final class ErrorMatcher extends StableMatcher {

        private ErrorMatcher(FontCatalog catalog, DerivedFontCache cache) {
            super(catalog, cache);
        }

        @Override
        public int matchFontIndex(int runtimeVersion, int codepoint, FontType fontType) {
            throw new AssertionError("matcher error");
        }
    }

    private static final class PauseBlockingMatcher extends StableMatcher {

        private final AtomicInteger invocationCount = new AtomicInteger(0);
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private PauseBlockingMatcher(FontCatalog catalog, DerivedFontCache cache) {
            super(catalog, cache);
        }

        @Override
        public int matchFontIndex(int runtimeVersion, int codepoint, FontType fontType) {
            if (invocationCount.incrementAndGet() == 1) {
                entered.countDown();
                try {
                    if (!release.await(5L, TimeUnit.SECONDS)) {
                        throw new AssertionError("等待 matcher 释放超时");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("等待 matcher 释放被中断", exception);
                }
            }
            return 0;
        }
    }

    private static final class BlockingClaimPageManager extends GlyphPageManager {

        private final CountDownLatch claimed = new CountDownLatch(1);
        private final CountDownLatch releaseClaim = new CountDownLatch(1);

        @Override
        public synchronized GlyphRequestToken claimRequest(int generation, int codepoint, FontType fontType,
                int demandPriority) {
            claimed.countDown();
            try {
                if (!releaseClaim.await(5L, TimeUnit.SECONDS)) {
                    throw new AssertionError("等待释放 claim 超时");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("等待释放 claim 被中断", exception);
            }
            return super.claimRequest(generation, codepoint, fontType, demandPriority);
        }
    }

    private static final class CountingClaimPageManager extends GlyphPageManager {

        private final int countedCodepoint;
        private final AtomicInteger countedClaims = new AtomicInteger(0);

        private CountingClaimPageManager(int countedCodepoint) {
            this.countedCodepoint = countedCodepoint;
        }

        @Override
        public synchronized GlyphRequestToken claimRequest(int generation, int codepoint, FontType fontType,
                int demandPriority) {
            if (codepoint == countedCodepoint) {
                countedClaims.incrementAndGet();
            }
            return super.claimRequest(generation, codepoint, fontType, demandPriority);
        }
    }

    private static final class OrderingMatcher extends FontMatcher {

        private final CountDownLatch firstEntered = new CountDownLatch(1);
        private final CountDownLatch releaseFirst = new CountDownLatch(1);
        private final AtomicInteger invocationCount = new AtomicInteger(0);
        private final AtomicInteger concurrent = new AtomicInteger(0);
        private final AtomicInteger maxConcurrent = new AtomicInteger(0);
        private final List<Integer> order = new CopyOnWriteArrayList<Integer>();
        /**
         * 需要 park 的码点：命中的字形在匹配入口停下、<b>不结算</b>，于是它长时间保持 RASTERIZING
         * （active）。本场地 matcher 平时立即返回 -1 → 需求很快结算为 FAILED → 同码点再 claim
         * 是合法的，"与在飞需求合并"这个前提就随调度蒸发 —— 这正是旧夹具偶发的根因。
         */
        private final Set<Integer> parkOn = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
        private final CountDownLatch parked = new CountDownLatch(1);
        private final CountDownLatch parkRelease = new CountDownLatch(1);

        private void parkCodepoint(int codepoint) {
            parkOn.add(Integer.valueOf(codepoint));
        }

        private void releaseParked() {
            parkRelease.countDown();
        }

        private OrderingMatcher(FontCatalog catalog, DerivedFontCache cache) {
            super(catalog, cache);
        }

        @Override
        public int matchFontIndex(int runtimeVersion, int codepoint, FontType fontType) {
            int active = concurrent.incrementAndGet();
            updateMax(maxConcurrent, active);
            try {
                if (invocationCount.incrementAndGet() == 1) {
                    firstEntered.countDown();
                    if (!releaseFirst.await(5L, TimeUnit.SECONDS)) {
                        throw new AssertionError("等待首个 ordering demand 释放超时");
                    }
                }
                if (parkOn.contains(Integer.valueOf(codepoint))) {
                    parked.countDown();
                    if (!parkRelease.await(5L, TimeUnit.SECONDS)) {
                        throw new AssertionError("等待 parked demand 释放超时: " + codepoint);
                    }
                }
                order.add(Integer.valueOf(codepoint));
                return -1;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("ordering matcher 被中断", exception);
            } finally {
                concurrent.decrementAndGet();
            }
        }

        private static void updateMax(AtomicInteger maximum, int candidate) {
            while (true) {
                int previous = maximum.get();
                if (candidate <= previous || maximum.compareAndSet(previous, candidate)) {
                    return;
                }
            }
        }
    }

    private static final class ManualClock implements LongSupplier {

        private final AtomicLong now = new AtomicLong(0L);

        @Override
        public long getAsLong() {
            return now.get();
        }

        private void set(long nowNanos) {
            now.set(nowNanos);
        }
    }

    /**
     * 内容驱动的 selection 闸门时钟：只在"队列里只剩一个候选、且它还没被匹配过"的那一刻把
     * 正在进行的 selection 停住。
     *
     * <p>取代旧的"阻塞第 N 次 clock 调用"写法。{@code AgingDemandQueue#bestIndex} 每次选择读一次
     * 时钟，但调用次数还受 executor 节奏影响（首个任务直跑不入队、空队阻塞时不读表、被提升的
     * 需求可能已结算并二次入队），所以任何被写死的 N 都只是概率成立：错位时合并前提已蒸发，
     * 测试以"多出一个 A"的形式偶发失败，看着像产品竞态。按队列内容停车与调用次数无关。</p>
     */
    /** 字体 worker 线程名前缀（由 FontWorkerThreadFactory 决定），用于区分"选择读表"与"提交读表"。 */
    private static final String FONT_WORKER_THREAD_PREFIX = "QzFontWorker";

    private static final class LastCandidateSelectionClock implements LongSupplier {

        private final int watchedCodepoint;
        private final AtomicInteger observations = new AtomicInteger(0);
        /** 目标相位成立时 release 一个许可；测试取到它才继续。 */
        private final Semaphore entered = new Semaphore(0);
        /** 测试放行 selection 时 release；放行前 worker 一直持着队列锁。 */
        private final Semaphore proceed = new Semaphore(0);
        private volatile BlockingQueue<Runnable> queue;
        private volatile List<Integer> matchedOrder;

        private LastCandidateSelectionClock(int watchedCodepoint) {
            this.watchedCodepoint = watchedCodepoint;
        }

        /** 装配工作队列与匹配顺序（两者都来自被测场地自身，不是复制出来的平行状态）。 */
        private void attach(BlockingQueue<Runnable> queue, List<Integer> matchedOrder) {
            this.queue = queue;
            this.matchedOrder = matchedOrder;
        }

        private int observations() {
            return observations.get();
        }

        @Override
        public long getAsLong() {
            observations.incrementAndGet();
            BlockingQueue<Runnable> currentQueue = queue;
            List<Integer> currentMatched = matchedOrder;
            // 只有 worker 线程上的读表才是"selection"。dispatcher 在 submit 里也读同一个时钟
            // （登记 enqueuedNanos，GlyphGenerationDispatcher:196），那是提交路径的读表，不是选择；
            // 旧夹具把两者一起计数，正是"该阻塞第几次"要押 4 而不是 3 的来源。按线程名区分后，
            // 相位判据只由队列内容决定，与读表次数无关。
            if (!Thread.currentThread().getName().startsWith(FONT_WORKER_THREAD_PREFIX)) {
                return 0L;
            }
            if (currentQueue != null && currentMatched != null
                    && currentQueue.size() == 1
                    && !currentMatched.contains(Integer.valueOf(watchedCodepoint))) {
                entered.release();
                try {
                    if (!proceed.tryAcquire(5L, TimeUnit.SECONDS)) {
                        throw new AssertionError("等待目标 selection 放行超时：watched=" + watchedCodepoint
                                + " observations=" + observations.get());
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("等待目标 selection 放行被中断", exception);
                }
            }
            return 0L;
        }
    }

    private static final class ExactRemovalHandler implements GlyphGenerationResultHandler {

        private final GlyphPageManager pageManager;
        private final AtomicInteger invocationCount = new AtomicInteger(0);
        private final CountDownLatch firstSettled = new CountDownLatch(1);
        private final CountDownLatch releaseFirst = new CountDownLatch(1);
        private final CountDownLatch secondEntered = new CountDownLatch(1);
        private final CountDownLatch releaseSecond = new CountDownLatch(1);

        private ExactRemovalHandler(GlyphPageManager pageManager) {
            this.pageManager = pageManager;
        }

        @Override
        public boolean handle(GlyphGenerationResult result) {
            int invocation = invocationCount.incrementAndGet();
            if (invocation == 1) {
                Assert.assertTrue(pageManager.markFailed(result.getToken(), GlyphState.RASTERIZING));
                firstSettled.countDown();
                await(releaseFirst);
                return false;
            }
            secondEntered.countDown();
            await(releaseSecond);
            return pageManager.queueUpload(result);
        }

        private void await(CountDownLatch latch) {
            try {
                if (!latch.await(5L, TimeUnit.SECONDS)) {
                    throw new AssertionError("等待测试 latch 超时");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("等待测试 latch 被中断", exception);
            }
        }
    }

    private static final class NonTerminatingExecutorService extends AbstractExecutorService {

        private boolean shutdown;
        private int awaitCount;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            awaitCount++;
            return false;
        }

        @Override
        public void execute(Runnable command) {
            // 测试 fake 不执行任务。
        }
    }
}
