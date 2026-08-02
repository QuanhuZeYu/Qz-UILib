package club.heiqi.uilib.font.glyph;

import java.awt.Font;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

        Assert.assertFalse(submitThread.isAlive());
        Assert.assertFalse(resetThread.isAlive());
        Assert.assertNull(submitFailure.get());
        Assert.assertNull(resetFailure.get());
        Assert.assertEquals(0, dispatcher.getInFlightTaskCount());
        Assert.assertFalse(isActive(pageManager.getState('F', FontType.NORMAL)));
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

    private static GlyphGenerationTask task(int codepoint) {
        return new GlyphGenerationTask(1, codepoint, FontType.NORMAL, 32, GlyphGenerationPriority.HIGH);
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
        public synchronized GlyphRequestToken claimRequest(int generation, int codepoint, FontType fontType) {
            claimed.countDown();
            try {
                if (!releaseClaim.await(5L, TimeUnit.SECONDS)) {
                    throw new AssertionError("等待释放 claim 超时");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("等待释放 claim 被中断", exception);
            }
            return super.claimRequest(generation, codepoint, fontType);
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
