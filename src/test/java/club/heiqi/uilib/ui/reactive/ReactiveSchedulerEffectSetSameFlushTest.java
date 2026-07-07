package club.heiqi.uilib.ui.reactive;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * ReactiveScheduler 双通道交替到不动点契约守卫（守 I2 / I9）。
 *
 * <p>钉死 Oracle 方案 A 实施后的核心契约：effect 内 {@link Signal#set} 在<b>同一次 flush</b> 内被 drain、
 * 订阅者被 markDirty、下游 effect 在同一 flush 内重跑。写入全程经 {@link ReactiveScheduler#queueWrite}
 * 进入 {@code pendingWrites}，再无绕过调度器的同步路径（撤 {@code setImmediate} 守 I2 单一收口）。
 * 一帧内多轮 drain 合并为单事务（守 I9 批处理）。</p>
 *
 * <p>归属：reactive 层 L2 集成测试——纯调度器内部多 effect/signal 协作，无 UI 渲染依赖，但跨 effect/signal
 * 间的因果传播，属 L2「多单元协作」边界（非纯数学单点）。</p>
 */
public class ReactiveSchedulerEffectSetSameFlushTest {

    @Before
    public void setUp() { ReactiveScheduler.get().reset(); }

    @After
    public void tearDown() { ReactiveScheduler.get().reset(); }

    /**
     * 用例 1：effect 内 {@code signal.set(x)} 后单次 flush，下游 effect <b>同帧</b>重跑读到新值。
     *
     * <p>关键断言：单次 {@link ReactiveScheduler#flush()} 后，下游 effect 的 seen 列表已包含 trigger 写入
     * 产生的新值——证明 effect 内 set 不再延迟一帧。这是撤 {@code setImmediate} 的直接前提。</p>
     */
    @Test
    public void effectSetPropagatesWithinSingleFlush() {
        Signal<Integer> trigger = Signal.create(0);
        Signal<Integer> target = Signal.create(100);
        List<Integer> downstreamSeen = new ArrayList<>();
        // 上游 effect：trigger 变化时 set target
        Effect.create(() -> {
            int t = trigger.get();
            Effect.untrack(() -> target.set(t * 10));
        });
        // 下游 effect：订阅 target，记录每次重跑看到的值
        Effect.create(() -> downstreamSeen.add(target.get()));

        ReactiveScheduler.get().flush();           // 首跑：下游看到 100；上游读 trigger=0 写 target=0
        // 双通道应让 trigger→target 写入在本次 flush 内 drain、下游 effect 同帧重跑看到 0
        boolean seen0 = downstreamSeen.contains(0);

        // 触发新一次传播：trigger 0→5
        downstreamSeen.clear();
        trigger.set(5);
        ReactiveScheduler.get().flush();

        Assert.assertTrue("effect 内 set 应同帧生效：下游 effect 必须在本次 flush 内读到 0",
                seen0);
        Assert.assertTrue("trigger 变化→上游 set target(50)→下游同帧读到 50",
                downstreamSeen.contains(50));
        Assert.assertEquals("单次 flush 后 target 终值同步", Integer.valueOf(50), target.get());
    }

    /**
     * 用例 2：effect 内 {@code signal.set(x)} 的写入<b>入事务日志</b>，且 undo 能回滚该写入。
     *
     * <p>关键断言：① 一次 flush（其内 effect 触发了 set）后事务日志新增一条 entry，target=本帧净变化；
     * ② undo 能把 target 回退到 flush 前的值——证明 effect 内 set 经 TransactionLog，保留审计/撤销能力。
     * 这正是 {@code setImmediate} 不入日志所丧失的能力，撤回后恢复。</p>
     *
     * <p>设计：effect 故意不做任何 tracking 读（仅 set target），使其只在首次 flush 跑一次后不再重跑——
     * 这样 undo 回退 target 时不会被 effect 重新覆盖，能干净地验证「effect 内 set 入事务、可回滚」。</p>
     */
    @Test
    public void effectSetWriteEntersTransactionLogAndIsUndoable() {
        Signal<Integer> target = Signal.create(100);
        // effect 无 tracking 读：仅首次 flush 跑一次后不再重跑（避免 undo 后被 effect 重新覆盖）
        Effect.create(() -> target.set(200));

        int logSizeBefore = ReactiveScheduler.get().transactionLog().size();
        ReactiveScheduler.get().flush();           // effect 首跑：target 100→200 同帧 drain、入事务
        Assert.assertEquals("effect 内 set 已同帧生效", Integer.valueOf(200), target.get());
        Assert.assertEquals("effect 内 set 进入事务日志（log 新增一条）",
                logSizeBefore + 1, ReactiveScheduler.get().transactionLog().size());

        TransactionLog.Entry entry = ReactiveScheduler.get().transactionLog()
                .current().entries().get(0);
        Assert.assertSame("日志条目记录的就是被 effect 写入的 target signal",
                target, entry.signal());
        Assert.assertEquals("before = flush 前的值", 100, entry.before());
        Assert.assertEquals("after = effect 写入的值", 200, entry.after());

        Assert.assertTrue(ReactiveScheduler.get().undo());
        Assert.assertEquals("undo 回退 target 到 100（证明 effect 内 set 经事务、可回滚）",
                Integer.valueOf(100), target.get());
    }

    /**
     * 用例 3：多轮传播收敛——A → effect set B → effect 读 B set C，单次 flush 内三段全部同帧收敛，
     * 且不会无限循环。
     *
     * <p>拓扑：trigger(A) 触发 e1 写 bSignal，e2 读 bSignal 写 cSignal，e3 读 cSignal 记录终值。
     * 单次 flush 后：A 的新值应同步反映到 C，证明多轮 drain→sweep 在同一 flush 内传递到位。</p>
     */
    @Test
    public void chainedEffectSetConvergesInSingleFlushWithoutLoop() {
        Signal<Integer> a = Signal.create(0);
        Signal<Integer> b = Signal.create(0);
        Signal<Integer> c = Signal.create(0);
        List<Integer> cSeen = new ArrayList<>();

        Effect.create(() -> {
            int av = a.get();
            Effect.untrack(() -> b.set(av + 1));   // A → B
        });
        Effect.create(() -> {
            int bv = b.get();
            Effect.untrack(() -> c.set(bv + 1));   // B → C
        });
        Effect.create(() -> cSeen.add(c.get()));   // 订阅 C

        ReactiveScheduler.get().flush();           // 首跑收敛：A=0 → B=1 → C=2

        Assert.assertEquals("链式传播同帧收敛：A=5 → B=6 → C=7", Integer.valueOf(7),
                afterChainedPropagate(a, 5, c));
        // 防无限循环：单次 flush 必须正常返回（若 scheduler 死循环本断言根本到不了）
        Assert.assertEquals("C 终值同步", Integer.valueOf(7), c.get());
    }

    /** 辅助：set a 并 flush，返回 c 的最新值。 */
    private Integer afterChainedPropagate(Signal<Integer> a, int newVal, Signal<Integer> c) {
        a.set(newVal);
        ReactiveScheduler.get().flush();
        return c.get();
    }

    /**
     * 用例 4（额外）：effect 内 set 中间值再回 frame 初值，事务日志无 entry（多轮抖动去重）。
     *
     * <p>守 I9 在双通道版本下的等价物：多轮 drain 的中间值抖动回帧初值，firstBefore/lastAfter 相等去重，
     * 不入事务、effect 不重跑。</p>
     */
    @Test
    public void effectSetJitterBackToFrameInitialProducesNoTransaction() {
        Signal<Integer> trigger = Signal.create(0);
        Signal<Integer> target = Signal.create(50);
        List<Integer> targetSeen = new ArrayList<>();
        Effect.create(() -> {
            trigger.get();
            Effect.untrack(() -> {
                target.set(99);                    // 中间值
                target.set(50);                    // 回到帧初值
            });
        });
        Effect.create(() -> targetSeen.add(target.get()));

        ReactiveScheduler.get().flush();
        // target 经历 50→99→50：firstBefore=50, lastAfter=50，相等去重，不入事务
        Assert.assertEquals("抖动回帧初值无净变化，不入事务日志", 0,
                ReactiveScheduler.get().transactionLog().size());
        Assert.assertEquals("target 仍为帧初值", Integer.valueOf(50), target.get());
    }
}
