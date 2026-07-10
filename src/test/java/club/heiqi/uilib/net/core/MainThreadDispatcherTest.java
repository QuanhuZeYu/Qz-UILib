package club.heiqi.uilib.net.core;

import club.heiqi.uilib.net.transport.NetSide;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
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

    /** AssertionError 不吞：必须回传（测试 hook / JUnit 可见）。 */
    @Test
    public void middleTaskAssertionError_propagates() {
        MainThreadDispatcher d = MainThreadDispatcher.getInstance();
        List<Integer> order = new ArrayList<Integer>();
        AtomicInteger errors = new AtomicInteger();
        d.setErrorSink((side, t) -> errors.incrementAndGet());

        d.enqueue(NetSide.CLIENT, () -> order.add(Integer.valueOf(1)));
        d.enqueue(NetSide.CLIENT, () -> {
            order.add(Integer.valueOf(2));
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
        assertEquals(2, order.size()); // 1 与 2 已跑；3 在 batch 内未执行（异常中断）
        // 清残：第三任务仍在已 swap 出的 batch 内，随异常丢失——属 AssertionError 传播语义
        d.drainClient();
        assertEquals(0, errors.get()); // 不走 errorSink
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
}
