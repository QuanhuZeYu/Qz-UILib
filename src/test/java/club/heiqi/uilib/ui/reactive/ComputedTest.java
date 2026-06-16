package club.heiqi.uilib.ui.reactive;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@link Computed} 派生 signal 契约测试。
 * 覆盖：派生值物化、上游变化重算、记忆化（输出不变不传播）、链式传播（signal→computed→effect、
 * computed→computed）、dispose、批处理（I9）。
 */
public class ComputedTest {

    @Before
    public void setUp() { ReactiveScheduler.get().reset(); }

    @After
    public void tearDown() { ReactiveScheduler.get().reset(); }

    @Test
    public void computedMaterializesOnFirstFlush() {
        Signal<Integer> a = Signal.create(2);
        Computed<Integer> doubled = Computed.create(() -> a.get() * 2);

        Assert.assertNull("flush 前派生值未物化", doubled.get());
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Integer.valueOf(4), doubled.get());
    }

    @Test
    public void computedRecomputesWhenUpstreamChanges() {
        Signal<Integer> a = Signal.create(3);
        Computed<Integer> squared = Computed.create(() -> a.get() * a.get());
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Integer.valueOf(9), squared.get());

        a.set(5);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Integer.valueOf(25), squared.get());
    }

    @Test
    public void computedDrivesDownstreamEffectInSameFlush() {
        Signal<Integer> a = Signal.create(1);
        Computed<Integer> plusTen = Computed.create(() -> a.get() + 10);
        List<Integer> seen = new ArrayList<>();
        Effect.create(() -> seen.add(plusTen.get()));
        ReactiveScheduler.get().flush();          // seen=[11]
        Assert.assertEquals(Arrays.asList(11), seen);

        a.set(2);
        ReactiveScheduler.get().flush();          // 单次 flush 内 signal→computed→effect 链式传播
        Assert.assertEquals(Arrays.asList(11, 12), seen);
    }

    @Test
    public void computedMemoizesAndSkipsDownstreamWhenOutputUnchanged() {
        Signal<Integer> n = Signal.create(4);
        Computed<Boolean> isEven = Computed.create(() -> n.get() % 2 == 0);
        List<Boolean> seen = new ArrayList<>();
        Effect.create(() -> seen.add(isEven.get()));
        ReactiveScheduler.get().flush();          // seen=[true]

        n.set(6);                                  // 仍偶数，派生输出不变
        ReactiveScheduler.get().flush();
        Assert.assertEquals("输出不变时下游不应重跑", Arrays.asList(Boolean.TRUE), seen);

        n.set(7);                                  // 变奇数，输出变化
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Arrays.asList(Boolean.TRUE, Boolean.FALSE), seen);
    }

    @Test
    public void computedChainsThroughAnotherComputed() {
        Signal<Integer> a = Signal.create(1);
        Computed<Integer> plusOne = Computed.create(() -> a.get() + 1);
        Computed<Integer> timesTen = Computed.create(() -> plusOne.get() * 10);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Integer.valueOf(20), timesTen.get());

        a.set(4);
        ReactiveScheduler.get().flush();          // a→plusOne→timesTen 链式传播
        Assert.assertEquals(Integer.valueOf(50), timesTen.get());
    }

    @Test
    public void computedTracksMultipleUpstreams() {
        Signal<Integer> x = Signal.create(1);
        Signal<Integer> y = Signal.create(2);
        Computed<Integer> sum = Computed.create(() -> x.get() + y.get());
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Integer.valueOf(3), sum.get());

        x.set(10);
        y.set(20);
        ReactiveScheduler.get().flush();          // I9：两上游同帧变化合并为一次重算
        Assert.assertEquals(Integer.valueOf(30), sum.get());
    }

    @Test
    public void disposedComputedStopsRecomputing() {
        Signal<Integer> a = Signal.create(1);
        Computed<Integer> c = Computed.create(() -> a.get() + 1);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Integer.valueOf(2), c.get());

        c.dispose();
        a.set(100);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("dispose 后不再重算", Integer.valueOf(2), c.get());
    }
}
