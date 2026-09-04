package club.heiqi.uilib.net.core;

import club.heiqi.uilib.net.transport.NetSide;
import club.heiqi.uilib.util.LaunchSide;
import cpw.mods.fml.relauncher.Side;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link MainThreadDispatcher}：真正批次交换、next-drain、FIFO、单任务异常隔离、并发 producer barrier。
 */
public class MainThreadDispatcherTest {

    @Before
    @After
    public void clearQueues() {
        for (int i = 0; i < 8; i++) {
            MainThreadDispatcher.getInstance().drainClient();
            MainThreadDispatcher.getInstance().drainServer();
        }
        MainThreadDispatcher.getInstance().setErrorSink(null);
    }

    /** 三任务中间抛 RuntimeException 仍执行第三。 */
    @Test
    public void middleTaskRuntimeException_stillRunsThird() {
        MainThreadDispatcher d = MainThreadDispatcher.getInstance();
        List<Integer> order = new ArrayList<Integer>();
        AtomicInteger errors = new AtomicInteger();
        d.setErrorSink((side, t) -> errors.incrementAndGet());

        d.enqueue(NetSide.CLIENT, () -> order.add(Integer.valueOf(1)));
        d.enqueue(NetSide.CLIENT, () -> {
            order.add(Integer.valueOf(2));
            throw new RuntimeException("boom-middle");
        });
        d.enqueue(NetSide.CLIENT, () -> order.add(Integer.valueOf(3)));

        d.drainClient();

        assertEquals(3, order.size());
        assertEquals(Integer.valueOf(1), order.get(0));
        assertEquals(Integer.valueOf(2), order.get(1));
        assertEquals(Integer.valueOf(3), order.get(2));
        assertEquals(1, errors.get());
        assertEquals(0, d.clientQueueSize());
    }

    /** AssertionError 不吞：必须回传；未消费尾部前置到 next batch，下一 drain 先旧尾再期间新任务。 */
    @Test
    public void middleTaskAssertionError_propagates_andPrependsRemaining() {
        MainThreadDispatcher d = MainThreadDispatcher.getInstance();
        List<Integer> order = new ArrayList<Integer>();
        AtomicInteger errors = new AtomicInteger();
        d.setErrorSink((side, t) -> errors.incrementAndGet());

        d.enqueue(NetSide.CLIENT, () -> order.add(Integer.valueOf(1)));
        d.enqueue(NetSide.CLIENT, () -> {
            order.add(Integer.valueOf(2));
            // 期间新任务进入 next batch
            d.enqueue(NetSide.CLIENT, () -> order.add(Integer.valueOf(99)));
            throw new AssertionError("assert-middle");
        });
        d.enqueue(NetSide.CLIENT, () -> order.add(Integer.valueOf(3)));

        boolean threw = false;
        try {
            d.drainClient();
        } catch (AssertionError e) {
            threw = true;
            assertTrue(e.getMessage().contains("assert-middle"));
        }
        assertTrue("AssertionError 不得被 drain 吞掉", threw);
        assertEquals(2, order.size()); // 1 与 2 已跑；3 未执行
        assertEquals(Integer.valueOf(1), order.get(0));
        assertEquals(Integer.valueOf(2), order.get(1));
        // 旧尾 3 前置 + 期间新任务 99
        assertEquals(2, d.clientQueueSize());

        d.drainClient();
        assertEquals(4, order.size());
        assertEquals(Integer.valueOf(3), order.get(2)); // 旧尾先
        assertEquals(Integer.valueOf(99), order.get(3)); // 再期间新任务
        assertEquals(0, errors.get()); // 不走 errorSink
        assertEquals(0, d.clientQueueSize());
    }

    /** 自定义非致命 Error 隔离；VirtualMachineError 不吞。 */
    @Test
    public void customError_isolated_vmErrorPropagates() {
        MainThreadDispatcher d = MainThreadDispatcher.getInstance();
        List<Integer> order = new ArrayList<Integer>();
        AtomicInteger errors = new AtomicInteger();
        d.setErrorSink((side, t) -> errors.incrementAndGet());

        d.enqueue(NetSide.CLIENT, () -> order.add(Integer.valueOf(1)));
        d.enqueue(NetSide.CLIENT, () -> {
            order.add(Integer.valueOf(2));
            throw new Error("non-fatal-custom-error");
        });
        d.enqueue(NetSide.CLIENT, () -> order.add(Integer.valueOf(3)));
        d.drainClient();
        assertEquals(3, order.size());
        assertEquals(1, errors.get());

        order.clear();
        d.enqueue(NetSide.CLIENT, () -> order.add(Integer.valueOf(1)));
        d.enqueue(NetSide.CLIENT, () -> {
            throw new OutOfMemoryError("simulated-oom");
        });
        d.enqueue(NetSide.CLIENT, () -> order.add(Integer.valueOf(3)));
        boolean threw = false;
        try {
            d.drainClient();
        } catch (OutOfMemoryError e) {
            threw = true;
        }
        assertTrue("VirtualMachineError 不得吞掉", threw);
        d.drainClient();
    }

    /**
     * 批次交换：任务内 enqueue 的新任务绝不本次消费，下一 tick drain 才跑。
     */
    @Test
    public void drainBudget_tasksEnqueuedDuringDrain_runOnNextDrainOnly() {
        MainThreadDispatcher d = MainThreadDispatcher.getInstance();
        List<Integer> order = new ArrayList<Integer>();

        d.enqueue(NetSide.CLIENT, () -> {
            order.add(Integer.valueOf(1));
            d.enqueue(NetSide.CLIENT, () -> order.add(Integer.valueOf(99)));
        });
        d.enqueue(NetSide.CLIENT, () -> order.add(Integer.valueOf(2)));

        assertEquals(2, d.clientQueueSize());
        d.drainClient();
        assertEquals(2, order.size());
        assertEquals(Integer.valueOf(1), order.get(0));
        assertEquals(Integer.valueOf(2), order.get(1));
        assertEquals(1, d.clientQueueSize());

        d.drainClient();
        assertEquals(3, order.size());
        assertEquals(Integer.valueOf(99), order.get(2));
        assertEquals(0, d.clientQueueSize());
    }

    /**
     * 入口 [coordinator, producer]：第一次 drain coordinator 恰 1 次且新 pending 留队；
     * 第二 tick drain 才应用。
     */
    @Test
    public void coordinatorThenProducer_firstDrainOnce_nextDrainApplies() {
        MainThreadDispatcher d = MainThreadDispatcher.getInstance();
        AtomicInteger coordinatorRuns = new AtomicInteger();
        AtomicInteger producerRuns = new AtomicInteger();
        AtomicInteger nestedFromCoordinator = new AtomicInteger();

        d.enqueue(NetSide.CLIENT, () -> {
            coordinatorRuns.incrementAndGet();
            d.enqueue(NetSide.CLIENT, nestedFromCoordinator::incrementAndGet);
        });
        d.enqueue(NetSide.CLIENT, producerRuns::incrementAndGet);

        assertEquals(2, d.clientQueueSize());
        d.drainClient();
        assertEquals("第一次 drain coordinator 恰 1 次", 1, coordinatorRuns.get());
        assertEquals("入口 producer 同预算内执行", 1, producerRuns.get());
        assertEquals("coordinator 新 enqueue 留 next-drain", 0, nestedFromCoordinator.get());
        assertEquals(1, d.clientQueueSize());

        d.drainClient();
        assertEquals(1, nestedFromCoordinator.get());
        assertEquals(0, d.clientQueueSize());
        assertEquals(1, coordinatorRuns.get());
    }

    /**
     * 持续 producer 每 drain 有界：任务内不断 enqueue，单次 drain 只消费 swap 时旧 batch。
     */
    @Test
    public void continuousProducer_eachDrainBoundedByEntrySize() {
        MainThreadDispatcher d = MainThreadDispatcher.getInstance();
        AtomicInteger runs = new AtomicInteger();
        final int[] remaining = new int[] { 20 };
        Runnable selfProducer = new Runnable() {
            @Override
            public void run() {
                runs.incrementAndGet();
                if (remaining[0] > 0) {
                    remaining[0]--;
                    d.enqueue(NetSide.CLIENT, this);
                }
            }
        };
        d.enqueue(NetSide.CLIENT, selfProducer);
        d.enqueue(NetSide.CLIENT, selfProducer);
        d.enqueue(NetSide.CLIENT, selfProducer);

        d.drainClient();
        assertEquals("单次 drain 仅入口 3", 3, runs.get());
        assertTrue("应有 next-drain 残留", d.clientQueueSize() > 0);

        int afterFirst = runs.get();
        int sizeBeforeSecond = d.clientQueueSize();
        d.drainClient();
        assertEquals("第二 drain 恰消费入口 size", sizeBeforeSecond, runs.get() - afterFirst);
    }

    /** FIFO：同侧顺序稳定。 */
    @Test
    public void fifoOrderPreserved() {
        MainThreadDispatcher d = MainThreadDispatcher.getInstance();
        List<Integer> order = new ArrayList<Integer>();
        for (int i = 0; i < 5; i++) {
            final int v = i;
            d.enqueue(NetSide.CLIENT, () -> order.add(Integer.valueOf(v)));
        }
        d.drainClient();
        assertEquals(5, order.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(Integer.valueOf(i), order.get(i));
        }
    }

    /**
     * 并发 producer 精确 barrier：swap 建立批次边界——
     * drain 期间并发 enqueue 的任务只进新队列，不得进入当前 batch，不得丢失。
     */
    @Test(timeout = 15000L)
    public void concurrentProducer_batchSwapBarrier_noLossNoCrossBatch() throws Exception {
        MainThreadDispatcher d = MainThreadDispatcher.getInstance();
        final int producers = 4;
        final int perProducer = 50;
        final AtomicInteger batch1Runs = new AtomicInteger();
        final AtomicInteger batch2Runs = new AtomicInteger();
        final AtomicInteger seenInFirstDrain = new AtomicInteger();
        final CountDownLatch firstTaskStarted = new CountDownLatch(1);
        final CountDownLatch producersDone = new CountDownLatch(producers);
        final CyclicBarrier releaseFirst = new CyclicBarrier(2);

        // batch1 首任务：卡住直到 producer 全部 enqueue 完毕
        d.enqueue(NetSide.CLIENT, () -> {
            batch1Runs.incrementAndGet();
            firstTaskStarted.countDown();
            try {
                releaseFirst.await(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        // batch1 另 2 个
        d.enqueue(NetSide.CLIENT, batch1Runs::incrementAndGet);
        d.enqueue(NetSide.CLIENT, batch1Runs::incrementAndGet);

        Thread drainer = new Thread(() -> {
            d.drainClient();
            seenInFirstDrain.set(batch1Runs.get());
        }, "batch-drain");
        drainer.start();
        assertTrue(firstTaskStarted.await(5, TimeUnit.SECONDS));

        // drain 已 swap，current 为空；并发 producer 只能进新队列
        for (int p = 0; p < producers; p++) {
            new Thread(() -> {
                try {
                    for (int i = 0; i < perProducer; i++) {
                        d.enqueue(NetSide.CLIENT, batch2Runs::incrementAndGet);
                    }
                } finally {
                    producersDone.countDown();
                }
            }, "producer-" + p).start();
        }
        assertTrue(producersDone.await(5, TimeUnit.SECONDS));
        // 首 batch 仍只应有 3 个（producer 不得进入当前 batch）
        assertEquals(1, batch1Runs.get()); // 仅首任务已跑，另 2 还在旧 batch 等待
        assertEquals(0, batch2Runs.get());

        releaseFirst.await(5, TimeUnit.SECONDS);
        drainer.join(5000);
        assertEquals(Thread.State.TERMINATED, drainer.getState());
        assertEquals("第一 drain 恰 3（旧 batch）", 3, seenInFirstDrain.get());
        assertEquals(3, batch1Runs.get());
        assertEquals(0, batch2Runs.get());
        assertEquals(producers * perProducer, d.clientQueueSize());

        d.drainClient();
        assertEquals(producers * perProducer, batch2Runs.get());
        assertEquals(0, d.clientQueueSize());
    }

    /** SERVER 侧同样批次交换。 */
    @Test
    public void serverSide_batchSwap_nextDrainOnly() {
        MainThreadDispatcher d = MainThreadDispatcher.getInstance();
        AtomicInteger a = new AtomicInteger();
        AtomicInteger nested = new AtomicInteger();
        d.enqueue(NetSide.SERVER, () -> {
            a.incrementAndGet();
            d.enqueue(NetSide.SERVER, nested::incrementAndGet);
        });
        d.drainServer();
        assertEquals(1, a.get());
        assertEquals(0, nested.get());
        assertEquals(1, d.serverQueueSize());
        d.drainServer();
        assertEquals(1, nested.get());
        assertEquals(0, d.serverQueueSize());
    }

    /**
     * ErrorSink 抛 AssertionError：与任务体 Assertion 同路径——尾重排 + rethrow；
     * 旧 batch 未消费尾部不得丢。
     */
    @Test
    public void errorSinkThrowsAssertion_prependsRemaining_andRethrows() {
        MainThreadDispatcher d = MainThreadDispatcher.getInstance();
        List<Integer> order = new ArrayList<Integer>();
        d.setErrorSink((side, t) -> {
            throw new AssertionError("sink-assert-" + t.getMessage());
        });

        d.enqueue(NetSide.CLIENT, () -> order.add(Integer.valueOf(1)));
        d.enqueue(NetSide.CLIENT, () -> {
            order.add(Integer.valueOf(2));
            d.enqueue(NetSide.CLIENT, () -> order.add(Integer.valueOf(99)));
            throw new RuntimeException("boom-for-sink");
        });
        d.enqueue(NetSide.CLIENT, () -> order.add(Integer.valueOf(3)));

        boolean threw = false;
        try {
            d.drainClient();
        } catch (AssertionError e) {
            threw = true;
            assertTrue(e.getMessage().contains("sink-assert-"));
            assertTrue(e.getMessage().contains("boom-for-sink"));
        }
        assertTrue("ErrorSink AssertionError 不得被 drain 吞掉", threw);
        assertEquals(2, order.size());
        assertEquals(Integer.valueOf(1), order.get(0));
        assertEquals(Integer.valueOf(2), order.get(1));
        // 旧尾 3 前置 + 期间新任务 99
        assertEquals(2, d.clientQueueSize());

        d.setErrorSink(null);
        d.drainClient();
        assertEquals(4, order.size());
        assertEquals(Integer.valueOf(3), order.get(2));
        assertEquals(Integer.valueOf(99), order.get(3));
        assertEquals(0, d.clientQueueSize());
    }

    /**
     * 并发第二 drainer：per-side drain owner CAS，第二者返回 0 且不消费。
     */
    @Test(timeout = 15000L)
    public void concurrentSecondDrainer_rejectedReturnsZero_noDoubleConsume() throws Exception {
        MainThreadDispatcher d = MainThreadDispatcher.getInstance();
        final AtomicInteger runs = new AtomicInteger();
        final CountDownLatch firstStarted = new CountDownLatch(1);
        final CountDownLatch releaseFirst = new CountDownLatch(1);
        final AtomicInteger secondDrainResult = new AtomicInteger(-1);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        d.enqueue(NetSide.CLIENT, () -> {
            runs.incrementAndGet();
            firstStarted.countDown();
            try {
                assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        d.enqueue(NetSide.CLIENT, runs::incrementAndGet);
        d.enqueue(NetSide.CLIENT, runs::incrementAndGet);

        Thread primary = new Thread(() -> {
            try {
                int n = d.drainClient();
                assertEquals(3, n);
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        }, "primary-drain");
        primary.start();
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
        assertTrue("primary 应持 drain owner", d.isClientDrainOwned());

        Thread second = new Thread(() -> {
            try {
                secondDrainResult.set(d.drainClient());
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        }, "second-drain");
        second.start();
        second.join(5000);
        assertEquals(Thread.State.TERMINATED, second.getState());
        assertEquals("第二 drainer 必须返回 0", 0, secondDrainResult.get());
        // 第二者不得消费：仍只有首任务跑了
        assertEquals(1, runs.get());

        releaseFirst.countDown();
        primary.join(5000);
        assertEquals(Thread.State.TERMINATED, primary.getState());
        assertNull("worker 异常: " + failure.get(), failure.get());
        assertEquals(3, runs.get());
        assertFalse(d.isClientDrainOwned());
        assertEquals(0, d.clientQueueSize());
    }

    /**
     * 专用服务端拒绝 CLIENT 入队（#71 同族审计 B5）。
     *
     * <p>drainClient 的唯一驱动是 ClientTickEvent，服务端永远不会发。照旧入队的后果是
     * "承诺执行但永不执行"+ 无人消费的队列无界增长。这里锁三件事：任务没进队列、
     * 任务没被执行、SERVER 侧派发不受牵连。</p>
     */
    @Test
    public void dedicatedServer_rejectsClientEnqueue_andKeepsServerSideWorking() {
        MainThreadDispatcher d = MainThreadDispatcher.getInstance();
        try {
            MainThreadDispatcher.overrideLaunchSideForTests(LaunchSide.forSide(Side.SERVER));
            AtomicInteger clientRan = new AtomicInteger();
            AtomicInteger serverRan = new AtomicInteger();

            d.enqueue(NetSide.CLIENT, clientRan::incrementAndGet);
            d.enqueue(NetSide.CLIENT, clientRan::incrementAndGet);

            assertEquals("服务端上 CLIENT 队列没有排空通道，必须拒绝入队而不是留着它增长",
                    0, d.clientQueueSize());
            assertEquals(0, clientRan.get());
            d.drainClient();
            assertEquals("被拒绝的任务不得凭空冒出来执行", 0, clientRan.get());

            d.enqueue(NetSide.SERVER, serverRan::incrementAndGet);
            d.drainServer();
            assertEquals("同侧的 SERVER 派发照常", 1, serverRan.get());
        } finally {
            MainThreadDispatcher.overrideLaunchSideForTests(null);
        }
    }

    /**
     * 判据只针对明确的 SERVER：客户端与侧别未知环境不得被顺手关掉功能（fail-open 方向）。
     */
    @Test
    public void nonServerLaunchSides_stillAcceptClientEnqueue() {
        MainThreadDispatcher d = MainThreadDispatcher.getInstance();
        try {
            Side[] sides = {Side.CLIENT, null};
            for (int i = 0; i < sides.length; i++) {
                AtomicInteger ran = new AtomicInteger();
                MainThreadDispatcher.overrideLaunchSideForTests(LaunchSide.forSide(sides[i]));
                d.enqueue(NetSide.CLIENT, ran::incrementAndGet);
                assertEquals("侧别 " + sides[i] + " 下 CLIENT 任务必须正常入队", 1, d.clientQueueSize());
                d.drainClient();
                assertEquals("侧别 " + sides[i] + " 下 CLIENT 任务必须被执行", 1, ran.get());
            }
        } finally {
            MainThreadDispatcher.overrideLaunchSideForTests(null);
        }
    }

    /**
     * asExecutor 与 enqueue 共用同一判据：不能留一条绕行入口。
     */
    @Test
    public void dedicatedServer_clientExecutorDoesNotBypassGate() {
        MainThreadDispatcher d = MainThreadDispatcher.getInstance();
        try {
            MainThreadDispatcher.overrideLaunchSideForTests(LaunchSide.forSide(Side.SERVER));
            AtomicInteger ran = new AtomicInteger();
            Executor executor = d.asExecutor(NetSide.CLIENT);
            executor.execute(ran::incrementAndGet);
            assertEquals(0, d.clientQueueSize());
            assertEquals(0, ran.get());
        } finally {
            MainThreadDispatcher.overrideLaunchSideForTests(null);
        }
    }
}
