package club.heiqi.uilib.ui.reactive;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * P1-3 守卫：{@code ReactiveScheduler} 的 scratch 复用与 dirty-effect 早退。
 *
 * <p>结构计数守卫（确定性，不依赖 JVM 分配行为）：</p>
 * <ul>
 *   <li>无 pending 写且无脏 effect 的 flush <b>不得扫描任何 effect</b>（O(1) 早退，
 *       消除 E≈2 万时每帧最大的一笔空转）；</li>
 *   <li>早退绝不能吞掉真脏的 effect（守 I2/I9：不动点语义逐位不变）；</li>
 *   <li>dirty 计数的不变量（= 登记表内脏 effect 数）在 reset / 注销 / 迟到 dispose 交叠下不被带偏——
 *       计数偏低会让早退跳过真脏 effect，属正确性事故，必须结构性钉死。</li>
 * </ul>
 */
public class ReactiveSchedulerAllocationGuardTest {

    @Before
    public void setUp() { ReactiveScheduler.get().reset(); }

    @After
    public void tearDown() { ReactiveScheduler.get().reset(); }

    /** 无工作 flush：不扫描任何 effect（早退命中）。 */
    @Test
    public void flushWithoutDirtyEffectsDoesNotScanEffectTable() {
        Signal<Integer> signal = Signal.create(0);
        Effect.create(() -> signal.get());
        ReactiveScheduler.get().flush();
        Assert.assertEquals("首跑后应无脏 effect", 0, ReactiveScheduler.get().__dirtyEffectCount());

        ReactiveScheduler.get().flush();

        Assert.assertEquals("无写入无脏 effect 的 flush 不得扫描 effect 表",
                0, ReactiveScheduler.get().__lastSweepScannedCount());
    }

    /** 早退不得吞掉真脏 effect：只订阅变化的那个 signal 的 effect 必须重跑。 */
    @Test
    public void earlyExitNeverSkipsDirtyEffect() {
        Signal<Integer> first = Signal.create(0);
        Signal<Integer> second = Signal.create(0);
        List<Integer> firstSeen = new ArrayList<Integer>();
        List<Integer> secondSeen = new ArrayList<Integer>();
        Effect.create(() -> firstSeen.add(first.get()));
        Effect.create(() -> secondSeen.add(second.get()));
        ReactiveScheduler.get().flush();
        int firstBefore = firstSeen.size();
        int secondBefore = secondSeen.size();

        second.set(5);
        ReactiveScheduler.get().flush();

        Assert.assertEquals("未变化的订阅者不得重跑", firstBefore, firstSeen.size());
        Assert.assertEquals("变化的订阅者必须重跑（早退不得吞）", secondBefore + 1, secondSeen.size());
        Assert.assertEquals("重跑后计数回零", 0, ReactiveScheduler.get().__dirtyEffectCount());
    }

    /** 计数不变量：dirty 计数恒等于登记表内脏 effect 数。 */
    @Test
    public void dirtyCountTracksRegisteredDirtyEffects() {
        Signal<Integer> signal = Signal.create(0);
        Effect dirty = Effect.create(() -> signal.get());
        Assert.assertEquals("注册即脏（首跑待执行）", 1, ReactiveScheduler.get().__dirtyEffectCount());

        dirty.dispose();
        Assert.assertEquals("注销脏 effect 必须同步回收计数", 0, ReactiveScheduler.get().__dirtyEffectCount());

        Effect second = Effect.create(() -> signal.get());
        ReactiveScheduler.get().flush();
        Assert.assertEquals(0, ReactiveScheduler.get().__dirtyEffectCount());
        // 注意：Signal.set 只是排队（信条四：写入在 flush 内 drain），此时计数仍为 0 且不变量成立
        signal.set(1);
        Assert.assertTrue(ReactiveScheduler.get().__hasPendingWrites());
        Assert.assertEquals("排队写入尚未标脏订阅者，计数不得提前变化",
                0, ReactiveScheduler.get().__dirtyEffectCount());
        ReactiveScheduler.get().flush();
        Assert.assertEquals("flush 内 drain→sweep 后计数必须回零",
                0, ReactiveScheduler.get().__dirtyEffectCount());
        Assert.assertTrue(second.isRegistered());
    }

    /** reset 与迟到 dispose 交叠：计数不得被带偏，后续新 effect 仍能正常跑。 */
    @Test
    public void resetThenLateDisposeKeepsCounterConsistent() {
        Signal<Integer> signal = Signal.create(0);
        Effect stale = Effect.create(() -> signal.get());
        ReactiveScheduler.get().reset();
        Assert.assertEquals(0, ReactiveScheduler.get().__dirtyEffectCount());

        stale.dispose();

        Assert.assertEquals("reset 之后的迟到 dispose 不得把计数带偏",
                0, ReactiveScheduler.get().__dirtyEffectCount());
        Assert.assertFalse(stale.isRegistered());

        Signal<Integer> fresh = Signal.create(0);
        List<Integer> seen = new ArrayList<Integer>();
        Effect.create(() -> seen.add(fresh.get()));
        fresh.set(7);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("reset 之后新建的 effect 必须正常跑（早退不得误判）", 1, seen.size());
        Assert.assertEquals(Integer.valueOf(7), fresh.get());
    }

    /** 单轮 sweep 期间新建的 effect 在下一轮被纳入（快照语义不变）。 */
    @Test
    public void effectRegisteredDuringSweepRunsInSameFlush() {
        Signal<Integer> trigger = Signal.create(0);
        List<Integer> lateSeen = new ArrayList<Integer>();
        Signal<Integer> late = Signal.create(0);
        Effect.create(() -> {
            trigger.get();
            Effect.untrack(() -> Effect.create(() -> lateSeen.add(late.get())));
        });
        ReactiveScheduler.get().flush();

        Assert.assertEquals("sweep 内新建的 effect 必须进入不动点循环（同一 flush 内跑）",
                1, lateSeen.size());
    }

    /** 多轮不动点：drain 与 sweep 交替推进，计数在多轮后仍回零。 */
    @Test
    public void multiPassFixpointKeepsCounterBalanced() {
        Signal<Integer> a = Signal.create(0);
        Signal<Integer> b = Signal.create(0);
        Signal<Integer> c = Signal.create(0);
        Effect.create(() -> {
            int value = a.get();
            Effect.untrack(() -> b.set(value + 1));
        });
        Effect.create(() -> {
            int value = b.get();
            Effect.untrack(() -> c.set(value + 1));
        });
        List<Integer> seen = new ArrayList<Integer>();
        Effect.create(() -> seen.add(c.get()));
        ReactiveScheduler.get().flush();

        a.set(5);
        ReactiveScheduler.get().flush();

        Assert.assertEquals("链式传播同帧收敛", Integer.valueOf(7), c.get());
        Assert.assertEquals("多轮不动点后计数必须回零", 0, ReactiveScheduler.get().__dirtyEffectCount());
        Assert.assertTrue(seen.contains(7));
    }
}
