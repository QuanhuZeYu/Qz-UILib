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
 * {@link MainThreadDispatcher} 单任务异常隔离：中间任务抛异常仍继续 drain 后续任务。
 */
public class MainThreadDispatcherTest {

    @Before
    @After
    public void clearQueues() {
        MainThreadDispatcher.getInstance().drainClient();
        MainThreadDispatcher.getInstance().drainServer();
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
        // 清残队列
        d.drainClient();
    }
}
