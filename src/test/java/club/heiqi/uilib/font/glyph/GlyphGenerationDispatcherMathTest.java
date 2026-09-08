package club.heiqi.uilib.font.glyph;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.latex.layout.MathGlyphRef;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.page.GlyphState;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.FontMatcher;

/** 数学 demand 复用普通队列；手动 dequeue 消除线程时序对合并和提升断言的影响。 */
public class GlyphGenerationDispatcherMathTest {

    @Test
    public void keyUsesCompleteValueIdentity() {
        MathGlyphRef ref = MathGlyphRef.forFontGlyph("face-a", 7);
        MathGlyphKey key = new MathGlyphKey(1, ref, 16, 0);
        MathGlyphKey equal = new MathGlyphKey(1, MathGlyphRef.forFontGlyph("face-a", 7), 16, 0);
        Assert.assertEquals(key, equal);
        Assert.assertEquals(key.hashCode(), equal.hashCode());
        Assert.assertEquals(1, key.getGeneration());
        Assert.assertEquals(ref, key.getMathGlyphRef());
        Assert.assertEquals(16, key.getRasterSize());
        Assert.assertEquals(0, key.getTileIndex());
        Assert.assertNotEquals(key, new MathGlyphKey(2, ref, 16, 0));
        Assert.assertNotEquals(key, new MathGlyphKey(1, ref, 17, 0));
        Assert.assertNotEquals(key, new MathGlyphKey(1, ref, 16, 1));
        Assert.assertNotEquals(key, new MathGlyphKey(1, MathGlyphRef.forFontGlyph("face-b", 7), 16, 0));
        Assert.assertNotEquals(key, Long.valueOf(7));
        expectInvalid(() -> new MathGlyphKey(1, null, 16, 0));
        expectInvalid(() -> new MathGlyphKey(1, ref, 0, 0));
        expectInvalid(() -> new MathGlyphKey(1, ref, 16, -1));
    }

    @Test
    public void duplicatePromotesSameTokenAndReselectsExistingQueue() throws Exception {
        try (Fixture f = new Fixture(8, 1)) {
            f.dispatcher.submit(math("first", 16, 0, GlyphDemandLevel.PREFETCH));
            f.dispatcher.submit(math("second", 16, 0, GlyphDemandLevel.FOREGROUND));
            GlyphRequestToken first = f.manager.claimed.get(0);
            f.dispatcher.submit(math("first", 16, 0, GlyphDemandLevel.VISIBLE));
            Assert.assertEquals(2, f.manager.claimed.size());
            Assert.assertEquals(2, f.dispatcher.getActiveDemandCount());
            Assert.assertEquals(1L, f.dispatcher.getPromotedDemandCount());
            f.runNext();
            Assert.assertSame(first, f.generated.get(0).getToken());
            Assert.assertEquals(GlyphDemandLevel.VISIBLE, f.generated.get(0).getDemandLevel());
            Assert.assertEquals(GlyphState.FAILED, f.manager.getTokenState(first));
            Assert.assertEquals(1, f.dispatcher.getActiveDemandCount());
        }
    }

    @Test
    public void faceSizeTileAndCodepointDemandsStayDistinct() throws Exception {
        try (Fixture f = new Fixture(8, 1)) {
            f.dispatcher.submit(math("a", 16, 0, GlyphDemandLevel.VISIBLE));
            f.dispatcher.submit(math("b", 16, 0, GlyphDemandLevel.VISIBLE));
            f.dispatcher.submit(math("a", 17, 0, GlyphDemandLevel.VISIBLE));
            f.dispatcher.submit(math("a", 16, 1, GlyphDemandLevel.VISIBLE));
            f.dispatcher.submit(new GlyphGenerationTask(1, 7, FontType.NORMAL, 16, GlyphGenerationPriority.HIGH));
            Assert.assertEquals(5, f.dispatcher.getActiveDemandCount());
            Assert.assertEquals(4, f.manager.claimed.size());
        }
    }

    @Test
    public void capacityRejectsBeforeMathClaimAndReservesVisibleDemand() throws Exception {
        try (Fixture f = new Fixture(2, 1)) {
            f.dispatcher.submit(math("a", 16, 0, GlyphDemandLevel.PREFETCH));
            f.dispatcher.submit(math("b", 16, 0, GlyphDemandLevel.PREFETCH));
            Assert.assertEquals(1, f.manager.claimed.size());
            Assert.assertEquals(1L, f.dispatcher.getRejectedDemandCount());
            f.dispatcher.submit(math("b", 16, 0, GlyphDemandLevel.VISIBLE));
            Assert.assertEquals(2, f.manager.claimed.size());
            Assert.assertEquals(2, f.dispatcher.getDemandHighWaterMark());
        }
    }

    @Test
    public void activeManagerDemandCoalescesWithoutDispatcherClaim() throws Exception {
        try (Fixture f = new Fixture(8, 1)) {
            MathGlyphRef ref = MathGlyphRef.forFontGlyph("a", 7);
            GlyphRequestToken token = f.manager.claimMathRequest(1, ref, 16, 0, 0);
            Assert.assertTrue(f.manager.markRasterizing(token));
            f.dispatcher.submit(math("a", 16, 0, GlyphDemandLevel.VISIBLE));
            Assert.assertEquals(1, f.manager.claimed.size());
            Assert.assertEquals(0, f.dispatcher.getActiveDemandCount());
            Assert.assertTrue(f.queue.isEmpty());
        }
    }

    @Test
    public void generatedMathResultUsesExistingMailboxAndCoalescesAfterWorkerRemoval() throws Exception {
        try (Fixture f = new Fixture(8, 1)) {
            f.produceResult = true;
            f.dispatcher.submit(math("a", 16, 0, GlyphDemandLevel.PREFETCH));
            f.runNext();
            GlyphRequestToken token = f.manager.claimed.get(0);
            Assert.assertEquals(GlyphState.UPLOAD_QUEUED, f.manager.getTokenState(token));
            Assert.assertEquals(0, f.dispatcher.getActiveDemandCount());
            f.dispatcher.submit(math("a", 16, 0, GlyphDemandLevel.VISIBLE));
            f.dispatcher.submit(math("a", 16, 0, GlyphDemandLevel.VISIBLE));
            Assert.assertEquals(1, f.manager.claimed.size());
            Assert.assertEquals(1, f.manager.getPendingUploadCount());
            Assert.assertEquals(1L, f.dispatcher.getPromotedDemandCount());
            Assert.assertTrue(f.queue.isEmpty());
        }
    }

    @Test
    public void pausedAndStaleMathDemandsNeverClaim() throws Exception {
        try (Fixture f = new Fixture(8, 1)) {
            f.dispatcher.pause();
            f.dispatcher.submit(math("a", 16, 0, GlyphDemandLevel.VISIBLE));
            f.dispatcher.resume();
            f.dispatcher.submit(GlyphGenerationTask.forMathGlyph(2, MathGlyphRef.forFontGlyph("a", 7),
                    16, 0, GlyphGenerationPriority.HIGH));
            Assert.assertTrue(f.manager.claimed.isEmpty());
            Assert.assertTrue(f.queue.isEmpty());
        }
    }

    @Test
    public void mathSkipsMatcherAndRunsGeneratorEvenWithoutMatcher() throws Exception {
        try (Fixture f = new Fixture(8, 1)) {
            set(f.dispatcher, "fontMatcher", null);
            f.dispatcher.submit(math("a", 16, 0, GlyphDemandLevel.VISIBLE));
            f.runNext();
            Assert.assertEquals(1, f.generated.size());
            GlyphGenerationTask generated = f.generated.get(0);
            Assert.assertEquals(GlyphRequestToken.Kind.MATH_GLYPH, generated.getKind());
            Assert.assertEquals(16, generated.getRasterSize());
            Assert.assertEquals(0, generated.getTileIndex());
            Assert.assertEquals(GlyphState.FAILED, f.manager.getTokenState(generated.getToken()));
            Assert.assertEquals(0, f.dispatcher.getInFlightTaskCount());
        }
    }

    @Test
    public void resetCancelsMathAndOldScheduledTaskCannotGenerate() throws Exception {
        try (Fixture f = new Fixture(8, 1)) {
            f.dispatcher.submit(math("a", 16, 0, GlyphDemandLevel.VISIBLE));
            Runnable old = f.queue.poll();
            GlyphRequestToken token = f.manager.claimed.get(0);
            f.dispatcher.reset();
            Assert.assertEquals(GlyphState.CANCELLED_STALE, f.manager.getTokenState(token));
            Assert.assertEquals(0, f.dispatcher.getActiveDemandCount());
            old.run();
            Assert.assertTrue(f.generated.isEmpty());
            Assert.assertEquals(0, f.dispatcher.getActiveDemandCount());
        }
    }

    private static GlyphGenerationTask math(String face, int size, int tile, GlyphDemandLevel level) {
        return GlyphGenerationTask.forMathGlyph(1, MathGlyphRef.forFontGlyph(face, 7), size, tile, level);
    }

    private static void expectInvalid(Runnable operation) {
        try {
            operation.run();
            Assert.fail("invalid math key accepted");
        } catch (IllegalArgumentException expected) {
            // 必须在进入共享 map 前拒绝无效身份。
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = GlyphGenerationDispatcher.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = GlyphGenerationDispatcher.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class RecordingManager extends GlyphPageManager {
        final List<GlyphRequestToken> claimed = new ArrayList<GlyphRequestToken>();

        @Override
        public GlyphRequestToken claimMathRequest(int generation, MathGlyphRef ref, int size, int tile, int priority) {
            GlyphRequestToken token = super.claimMathRequest(generation, ref, size, tile, priority);
            if (token != null) { claimed.add(token); }
            return token;
        }
    }

    private static final class Fixture implements AutoCloseable {
        final RecordingManager manager = new RecordingManager();
        final GlyphGenerationDispatcher dispatcher;
        final List<GlyphGenerationTask> generated = new ArrayList<GlyphGenerationTask>();
        final BlockingQueue<Runnable> queue;
        boolean produceResult;

        @SuppressWarnings("unchecked")
        Fixture(int capacity, int reserve) throws Exception {
            dispatcher = new GlyphGenerationDispatcher(capacity, reserve, 100L, () -> 0L);
            manager.setRuntimeVersion(1);
            dispatcher.setRuntimeVersion(1);
            FontCatalog catalog = new FontCatalog();
            DerivedFontCache cache = new DerivedFontCache(catalog);
            FontMatcher matcher = new FontMatcher(catalog, cache) {
                @Override
                public int matchFontIndex(int generation, int codepoint, FontType type) {
                    throw new AssertionError("math demand must not enter codepoint matcher");
                }
            };
            dispatcher.initialize(matcher, manager, cache, manager::queueUpload);
            ExecutorService original = (ExecutorService) get(dispatcher, "executorService");
            original.shutdownNow();
            Assert.assertTrue(original.awaitTermination(1L, TimeUnit.SECONDS));
            queue = (BlockingQueue<Runnable>) get(dispatcher, "demandQueue");
            set(dispatcher, "executorService", new ManualExecutor(queue));
            set(dispatcher, "glyphGenerator", new GlyphGenerator(matcher, cache) {
                @Override
                public GlyphGenerationResult generate(GlyphGenerationTask task) {
                    generated.add(task);
                    if (!produceResult) { return null; }
                    GlyphInfo info = new GlyphInfo(task.getMathGlyphRef(), task.getRasterSize(), task.getRasterSize(),
                            0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0, 0, 0, 0, 0, 0, 0, false, false);
                    return GlyphGenerationResult.forMathGlyph(task.getToken(), null, info);
                }
            });
        }

        void runNext() {
            Runnable next = queue.poll();
            Assert.assertNotNull(next);
            next.run();
        }

        @Override
        public void close() { dispatcher.reset(); }
    }

    private static final class ManualExecutor extends AbstractExecutorService {
        final BlockingQueue<Runnable> queue;
        boolean shutdown;
        ManualExecutor(BlockingQueue<Runnable> queue) { this.queue = queue; }
        @Override public void execute(Runnable task) { queue.add(task); }
        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() {
            shutdown = true;
            queue.clear();
            return Collections.emptyList();
        }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return shutdown; }
    }
}
