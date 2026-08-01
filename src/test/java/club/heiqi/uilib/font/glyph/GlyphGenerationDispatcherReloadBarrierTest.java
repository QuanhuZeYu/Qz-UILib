package club.heiqi.uilib.font.glyph;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.page.GlyphPageManager;
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
