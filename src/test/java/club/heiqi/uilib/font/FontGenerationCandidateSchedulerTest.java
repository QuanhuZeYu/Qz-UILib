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
        // 中断证据：worker 的第一个 park 用一把永不 signal 的闩 —— 它<b>只有被中断</b>这一条出路。
        // 旧写法拿 release 同时当"park 的闸口"和"循环退出条件"，于是"被 release 放行走掉"成了
        // 与"被 shutdownNow 中断"并列的第二条合法出路；而 await 与 interrupt 并发时 AQS 允许
        // "转入 sync queue 后被信号接管"，await 可以正常返回且不上抛 InterruptedException ——
        // 那条出路真的会发生（Linux runner 2/6 复现，本机 15/15 绿），终态断言于是在掷硬币。
        AtomicBoolean interruptionObserved = new AtomicBoolean();
        // 中断之后 worker 仍要留在退休中，才能验"退休未完成时拒绝 replacement"；由测试显式放行。
        CountDownLatch retireGate = new CountDownLatch(1);
        try {
            scheduler.submit(() -> {
                entered.countDown();
                try {
                    new CountDownLatch(1).await(5L, TimeUnit.SECONDS);
                } catch (InterruptedException expected) {
                    interruptionObserved.set(true);
                }
                try {
                    retireGate.await(5L, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
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

            // 正向等"中断确实到达"（有 deadline 的轮询），而不是用固定时长反证"还没完成"。
            awaitTrue(interruptionObserved, "shutdownNow 必须中断 retiring worker");

            retireGate.countDown();
            awaitQuiescence(scheduler);
            Assert.assertTrue("shutdownNow 必须中断 retiring worker", interruptionObserved.get());

            FontGenerationCandidateScheduler.PendingCandidate replacement = scheduler.submit(() -> {
                throw new IllegalStateException("replacement finished");
            });
            Assert.assertEquals("replacement finished", awaitResult(replacement).getFailure().getMessage());
        } finally {
            retireGate.countDown();
            scheduler.shutdown();
        }
    }

    /** 有期限地正向等待一个标志位；超时即失败并带上现场，不再拿固定时长窗口当反向判据。 */
    private void awaitTrue(AtomicBoolean flag, String claim) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (!flag.get() && System.nanoTime() < deadline) {
            Thread.sleep(1L);
        }
        Assert.assertTrue(claim + "（5s 内未观测到）", flag.get());
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
