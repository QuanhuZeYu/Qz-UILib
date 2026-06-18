package club.heiqi.uilib.ui.reactive;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 调度器「帧末合并写入 + 净变化去重」回归测试（I9）。
 *
 * <p>直击历史 latent bug：去重曾放在 {@link Signal#set(Object)}，用「已 flush 旧值」做比较，
 * 导致「同帧 set 到中间值、再 set 回帧初值」的第二次 set 被误判无变化而丢弃，flush 后落到错误的
 * 中间值。修复后去重移到 flush 阶段1，对比「帧初值」与「合并终值」，仅净变化才生效。</p>
 *
 * <p>覆盖：set 回帧初值无净变化、合并写入只重跑一次、toggle 抖动、单次同值去重、
 * 合并写入的事务日志单条 entry、阶段2 effect 内写入延后到下次 flush、合并写入后 undo/redo。</p>
 */
public class ReactiveSchedulerMergeWriteTest {

    @Before
    public void setUp() { ReactiveScheduler.get().reset(); }

    @After
    public void tearDown() { ReactiveScheduler.get().reset(); }

    /**
     * 核心回归：value=A，同帧 {@code set(B); set(A)} → flush 后终值仍为 A，且订阅 effect 不重跑。
     * 这正是历史 bug 会落到 B 的场景。
     */
    @Test
    public void sameFrameSetBackToInitialKeepsValueAndSkipsEffect() {
        Signal<String> s = Signal.create("A");
        List<String> seen = new ArrayList<>();
        Effect.create(() -> seen.add(s.get()));
        ReactiveScheduler.get().flush();          // 首跑：seen=["A"]
        seen.clear();

        s.set("B");                                // 中间值
        s.set("A");                                // 回到帧初值
        ReactiveScheduler.get().flush();

        Assert.assertEquals("终值应为帧初值 A（历史 bug 会错落到 B）", "A", s.get());
        Assert.assertTrue("无净变化 effect 不应重跑", seen.isEmpty());
    }

    /**
     * 合并语义：同帧 {@code set(B); set(C)} → 终值 C，effect 只重跑一次。
     */
    @Test
    public void sameFrameSetToDifferentValueRunsEffectOnce() {
        Signal<String> s = Signal.create("A");
        List<String> seen = new ArrayList<>();
        Effect.create(() -> seen.add(s.get()));
        ReactiveScheduler.get().flush();
        seen.clear();

        s.set("B");
        s.set("C");                                // 合并 → 终值 C
        ReactiveScheduler.get().flush();

        Assert.assertEquals("C", s.get());
        Assert.assertEquals("合并写入只触发一次重跑", 1, seen.size());
        Assert.assertEquals("C", seen.get(0));
    }

    /**
     * toggle 抖动：同帧 {@code false→true→false} → 终值 false，effect 不重跑。
     */
    @Test
    public void sameFrameToggleJitterSkipsEffect() {
        Signal<Boolean> flag = Signal.create(false);
        List<Boolean> seen = new ArrayList<>();
        Effect.create(() -> seen.add(flag.get()));
        ReactiveScheduler.get().flush();
        seen.clear();

        flag.set(true);
        flag.set(false);                           // 抖动回帧初值
        ReactiveScheduler.get().flush();

        Assert.assertEquals(Boolean.FALSE, flag.get());
        Assert.assertTrue("抖动回原值 effect 不重跑", seen.isEmpty());
    }

    /**
     * 单次 {@code set(同值)}：保留原去重效果——不入事务日志、effect 不重跑。
     */
    @Test
    public void singleSetSameValueProducesNoTransactionAndNoRerun() {
        Signal<Integer> s = Signal.create(5);
        List<Integer> seen = new ArrayList<>();
        Effect.create(() -> seen.add(s.get()));
        ReactiveScheduler.get().flush();
        seen.clear();

        s.set(5);                                  // 同值
        ReactiveScheduler.get().flush();

        Assert.assertTrue("同值不触发重跑", seen.isEmpty());
        Assert.assertEquals("同值不入事务日志", 0,
                ReactiveScheduler.get().transactionLog().size());
    }

    /**
     * 合并写入的事务日志：同帧多次写只记一条 entry，{@code before=帧初值、after=合并终值}。
     */
    @Test
    public void mergedWritesRecordSingleEntryInitialToFinal() {
        Signal<Integer> s = Signal.create(0);
        s.set(1);
        s.set(2);
        s.set(3);                                  // 同帧多次写 → 合并终值 3
        ReactiveScheduler.get().flush();

        TransactionLog log = ReactiveScheduler.get().transactionLog();
        Assert.assertEquals(1, log.size());
        List<TransactionLog.Entry> entries = log.current().entries();
        Assert.assertEquals("同帧多次写合并为一条 entry", 1, entries.size());
        Assert.assertEquals("before = 帧初值", 0, entries.get(0).before());
        Assert.assertEquals("after = 合并终值", 3, entries.get(0).after());
    }

    /**
     * 阶段2 effect 内写入：阶段1 已 clear 待写入表，effect 内 set 进入新的待写入表，
     * 留到下次 flush 生效，不在本次 flush 应用。
     */
    @Test
    public void phase2EffectSetDefersToNextFlush() {
        Signal<Integer> trigger = Signal.create(0);
        Signal<Integer> target = Signal.create(100);
        Effect.create(() -> {
            trigger.get();                         // 仅订阅 trigger，不订阅 target
            target.set(7);                         // 阶段2 effect 内写入
        });

        ReactiveScheduler.get().flush();           // 首次 flush：effect 跑，target.set(7) 进入新待写入表
        Assert.assertEquals("阶段2 内写入不在本次 flush 应用", Integer.valueOf(100), target.get());

        ReactiveScheduler.get().flush();           // 下次 flush 才应用
        Assert.assertEquals(Integer.valueOf(7), target.get());
    }

    /**
     * 合并写入后 undo/redo 仍正确：undo 回到帧初值、redo 回到合并终值。
     */
    @Test
    public void undoRedoCorrectAfterMergedWrites() {
        Signal<Integer> s = Signal.create(0);
        s.set(5);
        s.set(8);                                  // 合并 → 终值 8
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Integer.valueOf(8), s.get());

        Assert.assertTrue(ReactiveScheduler.get().undo());
        Assert.assertEquals("undo 回到帧初值", Integer.valueOf(0), s.get());

        Assert.assertTrue(ReactiveScheduler.get().redo());
        Assert.assertEquals("redo 回到合并终值", Integer.valueOf(8), s.get());
    }
}
