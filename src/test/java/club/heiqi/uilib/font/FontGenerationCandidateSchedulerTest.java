package club.heiqi.uilib.font;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;

/** Production candidate scheduler 的线程隔离与非阻塞 publication 合同。 */
public class FontGenerationCandidateSchedulerTest {

    @Test
    public void candidatePreparationRunsOnDedicatedSingleWorker() throws Exception {
        AsyncFontGenerationCandidateScheduler scheduler = new AsyncFontGenerationCandidateScheduler();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> preparationThread = new AtomicReference<Thread>();
        try {
            FontGenerationCandidateScheduler.PendingCandidate pending = scheduler.submit(() -> {
                preparationThread.set(Thread.currentThread());
                entered.countDown();
                if (!release.await(5L, TimeUnit.SECONDS)) {
                    throw new AssertionError("等待 candidate 测试释放超时");
                }
                throw new IllegalStateException("expected candidate failure");
            });

            Assert.assertTrue(entered.await(5L, TimeUnit.SECONDS));
            Assert.assertNull("render owner poll 不得等待仍在运行的 candidate", pending.poll());
            Assert.assertNotSame(Thread.currentThread(), preparationThread.get());
            Assert.assertTrue(preparationThread.get().getName().startsWith("QzFontGenerationBuilder-"));

            release.countDown();
            CandidateResult result = awaitResult(pending);
            Assert.assertTrue(result.getFailure() instanceof IllegalStateException);
            Assert.assertEquals("expected candidate failure", result.getFailure().getMessage());
        } finally {
            release.countDown();
            scheduler.shutdown();
        }
    }

    @Test
    public void cancellingQueuedCandidateMakesRoomForLatestReplacement() throws Exception {
        AsyncFontGenerationCandidateScheduler scheduler = new AsyncFontGenerationCandidateScheduler();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            FontGenerationCandidateScheduler.PendingCandidate running = scheduler.submit(() -> {
                entered.countDown();
                release.await(5L, TimeUnit.SECONDS);
                throw new IllegalStateException("running finished");
            });
            Assert.assertTrue(entered.await(5L, TimeUnit.SECONDS));
            FontGenerationCandidateScheduler.PendingCandidate superseded = scheduler.submit(() -> {
                throw new AssertionError("superseded queued candidate 不得运行");
            });

            superseded.cancel();
            FontGenerationCandidateScheduler.PendingCandidate latest = scheduler.submit(() -> {
                throw new IllegalStateException("latest finished");
            });
            release.countDown();

            Assert.assertEquals("running finished", awaitResult(running).getFailure().getMessage());
            Assert.assertEquals("latest finished", awaitResult(latest).getFailure().getMessage());
        } finally {
            release.countDown();
            scheduler.shutdown();
        }
    }

    @Test
    public void runningAndQueuedCandidatesRejectAThirdSubmission() throws Exception {
        AsyncFontGenerationCandidateScheduler scheduler = new AsyncFontGenerationCandidateScheduler();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FontGenerationCandidateScheduler.PendingCandidate queued = null;
        try {
            scheduler.submit(() -> {
                entered.countDown();
                release.await(5L, TimeUnit.SECONDS);
                throw new IllegalStateException("running finished");
            });
            Assert.assertTrue(entered.await(5L, TimeUnit.SECONDS));
            queued = scheduler.submit(() -> {
                throw new IllegalStateException("queued finished");
            });

            try {
                scheduler.submit(() -> {
                    throw new AssertionError("第三项 candidate 不得运行");
                });
                Assert.fail("一项运行加一项排队时必须拒绝第三项 candidate");
            } catch (RejectedExecutionException expected) {
                // bounded queue contract
            }
        } finally {
            if (queued != null) {
                queued.cancel();
            }
            release.countDown();
            scheduler.shutdown();
        }
    }

    @Test
    public void shutdownRejectsReplacementUntilRetiringExecutorTerminates() throws Exception {
        AsyncFontGenerationCandidateScheduler scheduler = new AsyncFontGenerationCandidateScheduler();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean interruptionObserved = new AtomicBoolean();
        try {
            scheduler.submit(() -> {
                entered.countDown();
                while (release.getCount() > 0L) {
                    try {
                        release.await(5L, TimeUnit.SECONDS);
                    } catch (InterruptedException expected) {
                        interruptionObserved.set(true);
                    }
                }
                throw new IllegalStateException("retiring finished");
            });
            Assert.assertTrue(entered.await(5L, TimeUnit.SECONDS));

            scheduler.shutdown();

            Assert.assertFalse("仍在退出的 executor 不得被视为 quiescent", scheduler.isQuiescent());
            try {
                scheduler.submit(() -> {
                    throw new AssertionError("retiring executor 存活时不得运行 replacement");
                });
                Assert.fail("retiring executor 存活时必须拒绝 replacement");
            } catch (IllegalStateException expected) {
                Assert.assertTrue(expected.getMessage().contains("尚未终止"));
            }

            release.countDown();
            awaitQuiescence(scheduler);
            Assert.assertTrue("shutdownNow 必须中断 retiring worker", interruptionObserved.get());

            FontGenerationCandidateScheduler.PendingCandidate replacement = scheduler.submit(() -> {
                throw new IllegalStateException("replacement finished");
            });
            Assert.assertEquals("replacement finished", awaitResult(replacement).getFailure().getMessage());
        } finally {
            release.countDown();
            scheduler.shutdown();
        }
    }

    private void awaitQuiescence(AsyncFontGenerationCandidateScheduler scheduler)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (!scheduler.isQuiescent() && System.nanoTime() < deadline) {
            Thread.sleep(1L);
        }
        Assert.assertTrue("retiring executor 应在期限内终止", scheduler.isQuiescent());
    }

    private CandidateResult awaitResult(FontGenerationCandidateScheduler.PendingCandidate pending)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        CandidateResult result;
        while ((result = pending.poll()) == null && System.nanoTime() < deadline) {
            Thread.sleep(1L);
        }
        Assert.assertNotNull("candidate 应在期限内发布完成态", result);
        return result;
    }
}
