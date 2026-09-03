package club.heiqi.uilib.font.glyph;

import java.awt.Font;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
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
        Assert.assertFalse("reset 必须等待 claim 与 executor admission 完成线性化",
                resetFinished.await(100L, TimeUnit.MILLISECONDS));

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
        Assert.assertFalse("pause 返回前必须包含已经通过 gate 的 admission",
                pauseFinished.await(100L, TimeUnit.MILLISECONDS));

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
        // 相位假设（已实测，勿凭直觉改）：4 是「本来会选走 A」的那一次 clock 调用 —— 那一刻
        // A 仍在队列里（才可被合并）且 worker 正持队列锁（promotion 才会真的等待）。试过改成 3：
        // 该测试在 Windows 上变为确定性失败，说明 executor 的实际调用节奏里还有一格选择，4 才对。
        // 残余风险：这仍是"押相位"的夹具，偶发（本机 1/60）会在 A 已结算后才轮到 promoter，
        // 合并前提自然不成立 —— 已改成下面的显式前提断言，失效时会以"fixture 前提未成立"失败，
        // 不再伪装成产品竞态。彻底确定化需要在 offer 与 bestIndex 之间开注入点（属产品可测性改动）。
        BlockingSelectionClock clock = new BlockingSelectionClock(4);
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
        dispatcher.submit(task('B', GlyphDemandLevel.FOREGROUND));
        matcher.releaseFirst.countDown();
        Assert.assertTrue(clock.selectionEntered.await(5L, TimeUnit.SECONDS));

        CountDownLatch promotionStarted = new CountDownLatch(1);
        CountDownLatch promotionFinished = new CountDownLatch(1);
        AtomicReference<Throwable> promotionFailure = new AtomicReference<Throwable>();
        Thread promoter = new Thread(() -> {
            promotionStarted.countDown();
            try {
                dispatcher.submit(task('A', GlyphDemandLevel.VISIBLE));
            } catch (Throwable throwable) {
                promotionFailure.set(throwable);
            } finally {
                promotionFinished.countDown();
            }
        }, "glyph-promotion-linearization-test");
        promoter.start();
        Assert.assertTrue(promotionStarted.await(5L, TimeUnit.SECONDS));
        Assert.assertFalse("promotion 返回前必须等待正在进行的 queue selection",
                promotionFinished.await(100L, TimeUnit.MILLISECONDS));

        clock.releaseSelection.countDown();
        promoter.join(TimeUnit.SECONDS.toMillis(5L));
        Assert.assertFalse(promoter.isAlive());
        Assert.assertNull(promotionFailure.get());
        // 先断言 fixture 前提本身：promotion 必须真的与在飞需求合并过（promoted>=1）。
        // 上一轮带仪表的本地样本给出 promoted=0 + 多出一个 A —— 说明 WARMUP 的 A 已匹配完并
        // 结算为失败（本场地 matcher 恒返回 -1），此时对同一码点重新 claim 是产品的正确行为，
        // 于是这条测试的"押在第 4 次 selection 上"的隐含前提已经悄悄断了。前提断了却去比匹配顺序，
        // 表现就是一个偶发的"多一个 A"，看着像产品竞态。把前提写成断言后，它只会以
        // "前提未成立"的名义失败，不再伪装成调度竞态。
        Assert.assertTrue("fixture 前提未成立：promotion 没有与在飞需求合并（promoted="
                + dispatcher.getPromotedDemandCount() + "）。这说明被提升的 WARMUP 需求已经结算，"
                + "本测试依赖的 selection 相位已丢失，属于夹具问题而非产品竞态",
                dispatcher.getPromotedDemandCount() >= 1L);
        Assert.assertEquals("匹配顺序须为 X,B,A；多出一个 A 说明 promotion 没与在飞的 queue selection "
                + "线性化。promoted=" + dispatcher.getPromotedDemandCount()
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

    private static final class BlockingSelectionClock implements LongSupplier {

        private final int blockingInvocation;
        private final AtomicInteger invocationCount = new AtomicInteger(0);
        private final CountDownLatch selectionEntered = new CountDownLatch(1);
        private final CountDownLatch releaseSelection = new CountDownLatch(1);

        private BlockingSelectionClock(int blockingInvocation) {
            this.blockingInvocation = blockingInvocation;
        }

        @Override
        public long getAsLong() {
            if (invocationCount.incrementAndGet() == blockingInvocation) {
                selectionEntered.countDown();
                try {
                    if (!releaseSelection.await(5L, TimeUnit.SECONDS)) {
                        throw new AssertionError("等待 queue selection 释放超时");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("等待 queue selection 释放被中断", exception);
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
