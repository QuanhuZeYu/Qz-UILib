package club.heiqi.uilib.font.layout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.FontMatcher;

/** 文本测量调用必须在 generation storage 转移期间持有完整 read scope。 */
public class TextLayoutGenerationBarrierTest {

    @Test
    public void multiCodepointMeasurementPinsGenerationReadLockForWholeCall() throws Exception {
        FontCatalog catalog = new FontCatalog();
        DerivedFontCache cache = new DerivedFontCache(catalog);
        CountDownLatch matcherEntered = new CountDownLatch(1);
        CountDownLatch matcherRelease = new CountDownLatch(1);
        FontMatcher matcher = new BlockingFontMatcher(catalog, cache, matcherEntered, matcherRelease);
        GlyphPageManager manager = new GlyphPageManager();
        ReentrantReadWriteLock generationLock = new ReentrantReadWriteLock();
        TextLayoutService service = new TextLayoutService(matcher, manager, cache, generationLock.readLock());
        service.setRuntimeVersion(1);
        Thread measureThread = new Thread(() -> service.getStringWidth("AB"),
                "font-layout-generation-barrier-test");
        measureThread.setDaemon(true);

        measureThread.start();
        Assert.assertTrue("测量应进入 matcher", matcherEntered.await(5L, TimeUnit.SECONDS));
        boolean writeLockAcquired = generationLock.writeLock().tryLock();
        if (writeLockAcquired) {
            generationLock.writeLock().unlock();
        }
        Assert.assertFalse("测量未结束前 generation write lock 不得进入", writeLockAcquired);

        matcherRelease.countDown();
        measureThread.join(5000L);
        Assert.assertFalse("测量线程应及时结束", measureThread.isAlive());
        Assert.assertTrue("测量结束后 generation write lock 应恢复可用", generationLock.writeLock().tryLock());
        generationLock.writeLock().unlock();
    }

    private static final class BlockingFontMatcher extends FontMatcher {

        private final CountDownLatch entered;
        private final CountDownLatch release;

        private BlockingFontMatcher(FontCatalog catalog, DerivedFontCache cache, CountDownLatch entered,
                CountDownLatch release) {
            super(catalog, cache);
            this.entered = entered;
            this.release = release;
        }

        @Override
        public int matchFontIndex(int runtimeVersion, int codepoint, FontType fontType) {
            if (codepoint != 'B') {
                return -1;
            }
            entered.countDown();
            try {
                if (!release.await(5L, TimeUnit.SECONDS)) {
                    throw new AssertionError("等待 generation barrier 测试释放超时");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("generation barrier 测试被中断", exception);
            }
            return -1;
        }
    }
}
