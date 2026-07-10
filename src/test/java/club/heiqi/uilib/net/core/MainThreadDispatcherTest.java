package club.heiqi.uilib.net.core;

import club.heiqi.uilib.net.transport.NetSide;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link MainThreadDispatcher}：入口快照预算、next-drain、FIFO、单任务异常隔离。
 */
public class MainThreadDispatcherTest {

    @Before
    @After
    public void clearQueues() {
        // 多轮 drain 清空 next-drain 残留
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

    /** AssertionError 隔离并继续。 */
    @Test
    public void middleTaskAssertionError_stillRunsThird() {
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
        d.drainClient();

        assertEquals(3, order.size());
        assertEquals(1, errors.get());
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

        // 清空后验证 VirtualMachineError 传播
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
        // 清残队列（预算后可能仍有）
        d.drainClient();
    }

    /**
     * 入口预算：任务内 enqueue 的新任务绝不本次消费，下一 tick drain 才跑。
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
        // 入口预算 2：执行 1、2；期间入队的 99 留队
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
     * 第二 tick drain 才应用；持续 producer 每 drain 有界。
     */
    @Test
    public void coordinatorThenProducer_firstDrainOnce_nextDrainApplies() {
        MainThreadDispatcher d = MainThreadDispatcher.getInstance();
        AtomicInteger coordinatorRuns = new AtomicInteger();
        AtomicInteger producerRuns = new AtomicInteger();
        AtomicInteger nestedFromCoordinator = new AtomicInteger();

        // 模拟 coordinator：执行时再 enqueue 一个「新 pending 调度」
        d.enqueue(NetSide.CLIENT, () -> {
            coordinatorRuns.incrementAndGet();
            d.enqueue(NetSide.CLIENT, nestedFromCoordinator::incrementAndGet);
        });
        // 入口已有的 producer-submit 任务
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
        // coordinator 仍只 1 次（未自旋）
        assertEquals(1, coordinatorRuns.get());
    }

    /**
     * 持续 producer 每 drain 有界：任务内不断 enqueue，单次 drain 只消费入口预算。
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
        // 入口 3 个
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
}
