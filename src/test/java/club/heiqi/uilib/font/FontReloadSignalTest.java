package club.heiqi.uilib.font;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.event.FontReloadRequest;

/** `FontReloadSignal` 的 desired/applied 与 single-flight 合同测试。 */
public class FontReloadSignalTest {

    /** signal 必须等待稳定窗口，成功后才推进 applied sequence。 */
    @Test
    public void shouldApplyOnlyAfterSignalBecomesStable() {
        ManualClock clock = new ManualClock(1000L);
        FontReloadSignal signal = new FontReloadSignal(100L, 50L, 200L, clock);

        Assert.assertEquals(1L, signal.signal(new FontReloadRequest("config")));
        clock.set(1099L);
        Assert.assertNull(signal.pollReady());

        clock.set(1100L);
        FontReloadSignal.Ticket ticket = signal.pollReady();
        Assert.assertNotNull(ticket);
        Assert.assertEquals(1L, ticket.getSequence());
        Assert.assertEquals(1L, ticket.getSignalCount());
        Assert.assertEquals("config", ticket.getRequest().getReason());
        Assert.assertTrue(signal.isInFlight());
        Assert.assertEquals(0L, signal.getAppliedSequence());

        Assert.assertTrue(signal.admitCommit(ticket));
        Assert.assertFalse("同一 ticket 只能取得一次 commit admission", signal.admitCommit(ticket));
        Assert.assertTrue(signal.completeSuccess(ticket));
        Assert.assertEquals(1L, signal.getAppliedSequence());
        Assert.assertEquals(0, signal.getPendingCount());
        Assert.assertFalse(signal.isInFlight());
    }

    /** 持续抖动不得由 max delay 强制执行，停止后只领取最新合并状态。 */
    @Test
    public void shouldWaitOutContinuousSignalStormWithoutForcedFlush() {
        ManualClock clock = new ManualClock(0L);
        FontReloadSignal signal = new FontReloadSignal(100L, 50L, 200L, clock);
        long lastSignalAt = 0L;

        for (int index = 0; index < 12; index++) {
            lastSignalAt = index * 90L;
            clock.set(lastSignalAt);
            signal.signal(new FontReloadRequest("resource-" + index));
            clock.set(lastSignalAt + 50L);
            Assert.assertNull(signal.pollReady());
        }

        clock.set(lastSignalAt + 99L);
        Assert.assertNull(signal.pollReady());
        clock.set(lastSignalAt + 100L);
        FontReloadSignal.Ticket ticket = signal.pollReady();
        Assert.assertNotNull(ticket);
        Assert.assertEquals(12L, ticket.getSignalCount());
        Assert.assertEquals("resource-11, coalesced=12", ticket.getRequest().getReason());
    }

    /** flight 中到达的新 signal 必须留给后续 ticket。 */
    @Test
    public void shouldLeaveSignalsPublishedDuringFlightPending() {
        ManualClock clock = new ManualClock(0L);
        FontReloadSignal signal = new FontReloadSignal(100L, 50L, 200L, clock);
        signal.signal(new FontReloadRequest("first"));
        clock.set(100L);
        FontReloadSignal.Ticket first = signal.pollReady();
        Assert.assertNotNull(first);

        clock.set(110L);
        signal.signal(new FontReloadRequest("second"));
        clock.set(1000L);
        Assert.assertNull(signal.pollReady());
        Assert.assertTrue(signal.completeSuccess(first));
        Assert.assertEquals(1, signal.getPendingCount());

        clock.set(209L);
        Assert.assertNull(signal.pollReady());
        clock.set(210L);
        FontReloadSignal.Ticket second = signal.pollReady();
        Assert.assertNotNull(second);
        Assert.assertEquals("second", second.getRequest().getReason());
        Assert.assertTrue(signal.completeSuccess(second));
        Assert.assertEquals(signal.getDesiredSequence(), signal.getAppliedSequence());
    }

    /** 更新 signal 可直接释放旧 flight，不确认旧 sequence，也不注入失败退避。 */
    @Test
    public void shouldReleaseSupersededFlightWithoutAcknowledgingIt() {
        ManualClock clock = new ManualClock(0L);
        FontReloadSignal signal = new FontReloadSignal(0L, 50L, 200L, clock);
        signal.signal(new FontReloadRequest("first"));
        FontReloadSignal.Ticket first = signal.pollReady();
        Assert.assertTrue(signal.isLatest(first));

        signal.signal(new FontReloadRequest("latest"));

        Assert.assertFalse(signal.isLatest(first));
        Assert.assertFalse(signal.admitCommit(first));
        Assert.assertTrue(signal.completeSuperseded(first));
        Assert.assertEquals(0L, signal.getAppliedSequence());
        Assert.assertEquals(2, signal.getPendingCount());
        Assert.assertEquals(0, signal.getConsecutiveFailures());

        FontReloadSignal.Ticket latest = signal.pollReady();
        Assert.assertNotNull(latest);
        Assert.assertEquals(2L, latest.getSequence());
        Assert.assertEquals("latest, coalesced=2", latest.getRequest().getReason());
    }

    /** 失败不得 acknowledge，并按上限受控的指数 backoff 重试。 */
    @Test
    public void shouldRetryFailedTicketWithBoundedBackoff() {
        ManualClock clock = new ManualClock(0L);
        FontReloadSignal signal = new FontReloadSignal(0L, 10L, 25L, clock);
        signal.signal(new FontReloadRequest("failure"));

        FontReloadSignal.Ticket first = signal.pollReady();
        Assert.assertTrue(signal.completeFailure(first));
        Assert.assertEquals(1, signal.getConsecutiveFailures());
        Assert.assertEquals(0L, signal.getAppliedSequence());
        clock.set(9L);
        Assert.assertNull(signal.pollReady());

        clock.set(10L);
        FontReloadSignal.Ticket second = signal.pollReady();
        Assert.assertTrue(signal.completeFailure(second));
        Assert.assertEquals(2, signal.getConsecutiveFailures());
        clock.set(29L);
        Assert.assertNull(signal.pollReady());

        clock.set(30L);
        FontReloadSignal.Ticket third = signal.pollReady();
        Assert.assertTrue(signal.completeFailure(third));
        Assert.assertEquals(3, signal.getConsecutiveFailures());
        clock.set(54L);
        Assert.assertNull(signal.pollReady());
        clock.set(55L);
        Assert.assertNotNull(signal.pollReady());
    }

    /** lifecycle reset 后旧 ticket 不能确认新 lifecycle。 */
    @Test
    public void shouldRejectTicketFromPreviousLifecycle() {
        ManualClock clock = new ManualClock(0L);
        FontReloadSignal signal = new FontReloadSignal(0L, 0L, 0L, clock);
        signal.signal(new FontReloadRequest("old"));
        FontReloadSignal.Ticket oldTicket = signal.pollReady();

        signal.reset();
        Assert.assertFalse(signal.completeSuccess(oldTicket));
        Assert.assertEquals(0, signal.getPendingCount());

        clock.set(1L);
        signal.signal(new FontReloadRequest("new"));
        FontReloadSignal.Ticket newTicket = signal.pollReady();
        Assert.assertNotNull(newTicket);
        Assert.assertFalse(signal.completeFailure(oldTicket));
        Assert.assertTrue(signal.completeSuccess(newTicket));
    }

    /** closed lifecycle 不得重新积累旧 intent，显式 open 后从干净状态接收。 */
    @Test
    public void shouldRejectSignalsWhileLifecycleIsClosed() {
        ManualClock clock = new ManualClock(0L);
        FontReloadSignal signal = new FontReloadSignal(0L, 0L, 0L, clock);
        signal.signal(new FontReloadRequest("before-close"));

        signal.closeLifecycle();
        clock.set(1L);
        Assert.assertEquals(-1L, signal.signal(new FontReloadRequest("rejected")));
        Assert.assertEquals(0, signal.getPendingCount());

        signal.openLifecycle();
        clock.set(2L);
        Assert.assertTrue(signal.signal(new FontReloadRequest("new-lifecycle")) > 0L);
        Assert.assertEquals(1, signal.getPendingCount());
    }

    /** 多个并发 poller 最多只能领取一个 reconcile ticket。 */
    @Test
    public void shouldAllowOnlyOneConcurrentTicketOwner() throws Exception {
        ManualClock clock = new ManualClock(0L);
        final FontReloadSignal signal = new FontReloadSignal(0L, 0L, 0L, clock);
        signal.signal(new FontReloadRequest("concurrent"));
        ExecutorService executor = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<FontReloadSignal.Ticket>> futures = new ArrayList<Future<FontReloadSignal.Ticket>>();
        try {
            for (int index = 0; index < 12; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return signal.pollReady();
                }));
            }
            start.countDown();

            int owners = 0;
            for (Future<FontReloadSignal.Ticket> future : futures) {
                if (future.get(5L, TimeUnit.SECONDS) != null) {
                    owners++;
                }
            }
            Assert.assertEquals(1, owners);
            Assert.assertTrue(signal.isInFlight());
        } finally {
            executor.shutdownNow();
            Assert.assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    /** 时钟采样与 signal 发布必须共享同一线性化锁，旧采样不能覆盖更新发布。 */
    @Test
    public void shouldSampleClockInsideSignalLinearization() throws Exception {
        BlockingClock clock = new BlockingClock();
        FontReloadSignal signal = new FontReloadSignal(100L, 0L, 0L, clock);
        Thread first = new Thread(() -> signal.signal(new FontReloadRequest("first")), "first-signal");
        CountDownLatch secondStarted = new CountDownLatch(1);
        Thread second = new Thread(() -> {
            secondStarted.countDown();
            signal.signal(new FontReloadRequest("second"));
        }, "second-signal");
        first.setDaemon(true);
        second.setDaemon(true);

        first.start();
        Assert.assertTrue(clock.firstSampleEntered.await(5L, TimeUnit.SECONDS));
        second.start();
        Assert.assertTrue(secondStarted.await(5L, TimeUnit.SECONDS));
        clock.releaseFirstSample.countDown();
        first.join(5000L);
        second.join(5000L);

        Assert.assertFalse(first.isAlive());
        Assert.assertFalse(second.isAlive());
        FontReloadSignal.Ticket ticket = signal.pollReady();
        Assert.assertNotNull(ticket);
        Assert.assertEquals("second, coalesced=2", ticket.getRequest().getReason());
    }

    private static final class ManualClock implements LongSupplier {

        private final AtomicLong now;

        private ManualClock(long initialValue) {
            now = new AtomicLong(initialValue);
        }

        private void set(long value) {
            now.set(value);
        }

        @Override
        public long getAsLong() {
            return now.get();
        }
    }

    private static final class BlockingClock implements LongSupplier {

        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch firstSampleEntered = new CountDownLatch(1);
        private final CountDownLatch releaseFirstSample = new CountDownLatch(1);

        @Override
        public long getAsLong() {
            int call = calls.getAndIncrement();
            if (call == 0) {
                firstSampleEntered.countDown();
                try {
                    if (!releaseFirstSample.await(5L, TimeUnit.SECONDS)) {
                        throw new AssertionError("等待释放首个时钟采样超时");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("首个时钟采样被中断", exception);
                }
                return 100L;
            }
            return call == 1 ? 200L : 300L;
        }
    }
}
