package club.heiqi.uilib.ui.reactive;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 * 响应式数据层基础契约测试。
 * 覆盖：Signal 读写、Effect 自动追踪与重跑、批处理（I9）、dispose、Owner 作用域。
 */
public class ReactiveSystemTest {

    @Before
    public void setUp() { ReactiveScheduler.get().reset(); }

    @After
    public void tearDown() { ReactiveScheduler.get().reset(); }

    // ── Signal ────────────────────────────────────────────────────────────────

    @Test
    public void signalHoldsInitialValue() {
        Assert.assertEquals(Integer.valueOf(42), Signal.create(42).get());
    }

    @Test
    public void signalSetQueuedUntilFlush() {
        Signal<String> s = Signal.create("a");
        s.set("b");
        Assert.assertEquals("a", s.get());          // 未 flush，仍旧值
        ReactiveScheduler.get().flush();
        Assert.assertEquals("b", s.get());
    }

    @Test
    public void signalEqualValueSkipped() {
        Signal<Integer> s = Signal.create(1);
        List<Integer> log = new ArrayList<>();
        Effect.create(() -> log.add(s.get()));
        ReactiveScheduler.get().flush();
        log.clear();

        s.set(1);                                    // 相同值，不排队
        ReactiveScheduler.get().flush();
        Assert.assertTrue(log.isEmpty());
    }

    // ── Effect ────────────────────────────────────────────────────────────────

    @Test
    public void effectRunsOnFirstFlush() {
        Signal<Integer> count = Signal.create(0);
        List<Integer> seen = new ArrayList<>();
        Effect.create(() -> seen.add(count.get()));

        Assert.assertTrue(seen.isEmpty());           // flush 前不运行
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Arrays.asList(0), seen);
    }

    @Test
    public void effectRerunsWhenDependencyChanges() {
        Signal<Integer> x = Signal.create(10);
        List<Integer> seen = new ArrayList<>();
        Effect.create(() -> seen.add(x.get()));
        ReactiveScheduler.get().flush();

        x.set(20);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Arrays.asList(10, 20), seen);
    }

    @Test
    public void effectBatchesMultipleSetsIntoOneRerun() {
        Signal<Integer> a = Signal.create(1);
        Signal<Integer> b = Signal.create(2);
        List<Integer> runs = new ArrayList<>();
        Effect.create(() -> { a.get(); b.get(); runs.add(1); });
        ReactiveScheduler.get().flush();             // 首次：runs=[1]

        a.set(10);
        b.set(20);
        ReactiveScheduler.get().flush();             // I9：两次 set 只触发一次重跑
        Assert.assertEquals(2, runs.size());
    }

    @Test
    public void effectDoesNotRerunIfNotDirty() {
        Signal<Integer> s = Signal.create(5);
        List<Integer> seen = new ArrayList<>();
        Effect.create(() -> seen.add(s.get()));
        ReactiveScheduler.get().flush();

        ReactiveScheduler.get().flush();             // 无变化，不应重跑
        Assert.assertEquals(1, seen.size());
    }

    @Test
    public void effectDynamicallyRewiresDependencies() {
        Signal<Boolean> toggle = Signal.create(true);
        Signal<String> a = Signal.create("A");
        Signal<String> b = Signal.create("B");
        List<String> seen = new ArrayList<>();

        Effect.create(() -> seen.add(toggle.get() ? a.get() : b.get()));
        ReactiveScheduler.get().flush();             // seen=["A"]，订阅 toggle+a

        toggle.set(false);
        ReactiveScheduler.get().flush();             // seen=["A","B"]，重建依赖为 toggle+b

        a.set("A2");                                 // a 已不再被订阅，不触发重跑
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Arrays.asList("A", "B"), seen);
    }

    // ── Dispose ───────────────────────────────────────────────────────────────

    @Test
    public void disposedEffectDoesNotRerun() {
        Signal<Integer> s = Signal.create(0);
        List<Integer> seen = new ArrayList<>();
        Effect e = Effect.create(() -> seen.add(s.get()));
        ReactiveScheduler.get().flush();

        e.dispose();
        s.set(99);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(1, seen.size());
    }

    @Test
    public void disposedEffectReleasesSubscriptions() {
        Signal<Integer> s = Signal.create(0);
        Effect e = Effect.create(() -> s.get());
        ReactiveScheduler.get().flush();

        Assert.assertFalse(s.subscribers.isEmpty());
        e.dispose();
        Assert.assertTrue(s.subscribers.isEmpty());
    }

    // ── Owner ─────────────────────────────────────────────────────────────────

    @Test
    public void ownerDisposesCleansAllEffects() {
        Signal<Integer> s = Signal.create(0);
        List<Integer> seen = new ArrayList<>();
        Owner owner = new Owner();
        owner.createEffect(() -> seen.add(s.get()));
        owner.createEffect(() -> seen.add(s.get() * 10));
        ReactiveScheduler.get().flush();             // seen=[0, 0]

        owner.dispose();
        s.set(5);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("owner.dispose 后无新数据", 2, seen.size());
    }
}
