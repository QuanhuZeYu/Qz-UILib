package club.heiqi.uilib.ui.reactive;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 中央事务日志与时间旅行契约测试（信条四）。
 * 覆盖：日志记录、按 signal 合并、净变化过滤、有界环形淘汰、游标 undo/redo、
 * 未来分支截断、effect/computed 重算、事务标签（审计路径）、开关与容量。
 */
public class TransactionLogTest {

    private ReactiveScheduler scheduler;
    private TransactionLog log;

    @Before
    public void setUp() {
        scheduler = ReactiveScheduler.get();
        scheduler.reset();
        log = scheduler.transactionLog();
    }

    @After
    public void tearDown() { scheduler.reset(); }

    // ── 日志记录 ────────────────────────────────────────────────────────────────

    @Test
    public void flushRecordsOneTransactionPerFrame() {
        Signal<Integer> a = Signal.create(1);
        Signal<Integer> b = Signal.create(2);
        a.set(10);
        b.set(20);
        scheduler.flush();

        Assert.assertEquals("一帧多次写入合并为一个事务", 1, log.size());
        TransactionLog.Transaction txn = log.current();
        Assert.assertEquals(2, txn.entries().size());
        Assert.assertEquals(1L, txn.sequence());
    }

    @Test
    public void entryCapturesBeforeAndAfter() {
        Signal<String> s = Signal.create("old");
        s.set("new");
        scheduler.flush();

        TransactionLog.Entry e = log.current().entries().get(0);
        Assert.assertSame(s, e.signal());
        Assert.assertEquals("old", e.before());
        Assert.assertEquals("new", e.after());
    }

    @Test
    public void multipleSetsToSameSignalMergeIntoOneEntry() {
        Signal<Integer> s = Signal.create(0);
        s.set(1);
        s.set(2);
        s.set(3);
        scheduler.flush();

        Assert.assertEquals(1, log.size());
        List<TransactionLog.Entry> entries = log.current().entries();
        Assert.assertEquals("同帧同 signal 多次写合并为一条", 1, entries.size());
        Assert.assertEquals(0, entries.get(0).before());   // before = 本帧首次写入前
        Assert.assertEquals(3, entries.get(0).after());     // after = 最终值
    }

    @Test
    public void inFrameDedupRecordsFinalValueOnly() {
        Signal<Integer> s = Signal.create(5);
        s.set(7);
        s.set(5);   // 与「已应用值」5 相等 → 被 Signal.set 去重守卫跳过、不入队（value 直到 flush 才变）
        scheduler.flush();

        // 故同帧内只有 5→7 真正入队生效；记一个事务、一条 entry
        Assert.assertEquals(Integer.valueOf(7), s.get());
        Assert.assertEquals(1, log.size());
        TransactionLog.Entry e = log.current().entries().get(0);
        Assert.assertEquals(5, e.before());
        Assert.assertEquals(7, e.after());
    }

    @Test
    public void emptyFlushProducesNoTransaction() {
        scheduler.flush();
        Assert.assertEquals(0, log.size());
    }

    @Test
    public void sequenceMonotonicAcrossFrames() {
        Signal<Integer> s = Signal.create(0);
        s.set(1); scheduler.flush();
        s.set(2); scheduler.flush();
        s.set(3); scheduler.flush();

        List<TransactionLog.Transaction> all = log.transactions();
        Assert.assertEquals(3, all.size());
        Assert.assertEquals(1L, all.get(0).sequence());
        Assert.assertEquals(2L, all.get(1).sequence());
        Assert.assertEquals(3L, all.get(2).sequence());
    }

    // ── 游标 undo / redo ────────────────────────────────────────────────────────

    @Test
    public void undoRestoresPreviousValue() {
        Signal<Integer> s = Signal.create(0);
        s.set(42); scheduler.flush();
        Assert.assertEquals(Integer.valueOf(42), s.get());

        Assert.assertTrue(scheduler.undo());
        Assert.assertEquals("undo 回退到 before 值", Integer.valueOf(0), s.get());
    }

    @Test
    public void redoReappliesValue() {
        Signal<Integer> s = Signal.create(0);
        s.set(42); scheduler.flush();
        scheduler.undo();

        Assert.assertTrue(scheduler.redo());
        Assert.assertEquals("redo 重新应用 after 值", Integer.valueOf(42), s.get());
    }

    @Test
    public void undoRedoCursorBookkeeping() {
        Signal<Integer> s = Signal.create(0);
        s.set(1); scheduler.flush();
        s.set(2); scheduler.flush();

        Assert.assertEquals(2, log.cursor());
        Assert.assertTrue(log.canUndo());
        Assert.assertFalse(log.canRedo());

        scheduler.undo();
        Assert.assertEquals(1, log.cursor());
        Assert.assertTrue(log.canUndo());
        Assert.assertTrue(log.canRedo());
        Assert.assertEquals(Integer.valueOf(1), s.get());

        scheduler.undo();
        Assert.assertEquals(0, log.cursor());
        Assert.assertFalse(log.canUndo());
        Assert.assertEquals(Integer.valueOf(0), s.get());

        Assert.assertFalse("游标已到底，undo 返回 false", scheduler.undo());
    }

    @Test
    public void redoAtTipReturnsFalse() {
        Signal<Integer> s = Signal.create(0);
        s.set(1); scheduler.flush();
        Assert.assertFalse("游标在末端，无可重做", scheduler.redo());
    }

    @Test
    public void undoRerunsEffects() {
        Signal<Integer> s = Signal.create(0);
        List<Integer> seen = new ArrayList<>();
        Effect.create(() -> seen.add(s.get()));
        scheduler.flush();          // seen=[0]
        s.set(99); scheduler.flush(); // seen=[0,99]

        scheduler.undo();           // 回退 → effect 重跑
        Assert.assertEquals(Arrays.asList(0, 99, 0), seen);
    }

    @Test
    public void undoRerunsComputed() {
        Signal<Integer> base = Signal.create(2);
        Computed<Integer> doubled = Computed.create(() -> base.get() * 2);
        List<Integer> seen = new ArrayList<>();
        Effect.create(() -> seen.add(doubled.get()));
        scheduler.flush();          // doubled=4, seen=[4]
        base.set(10); scheduler.flush(); // doubled=20, seen=[4,20]

        scheduler.undo();           // base 回退到 2 → computed 重算为 4
        Assert.assertEquals(Integer.valueOf(4), doubled.get());
        Assert.assertEquals(Arrays.asList(4, 20, 4), seen);
    }

    @Test
    public void newWriteTruncatesFutureBranch() {
        Signal<Integer> s = Signal.create(0);
        s.set(1); scheduler.flush();
        s.set(2); scheduler.flush();
        s.set(3); scheduler.flush();
        Assert.assertEquals(3, log.size());

        scheduler.undo();           // 回到 2，游标=2
        scheduler.undo();           // 回到 1，游标=1
        Assert.assertEquals(Integer.valueOf(1), s.get());

        s.set(100); scheduler.flush(); // 在中段提交 → 截断「未来」段(原 2、3)
        Assert.assertEquals("新分支截断旧重做段", 2, log.size());
        Assert.assertFalse(log.canRedo());
        Assert.assertEquals(Integer.valueOf(100), s.get());
    }

    @Test
    public void multiSignalTransactionUndoneAtomically() {
        Signal<String> name = Signal.create("a");
        Signal<Integer> age = Signal.create(1);
        name.set("b");
        age.set(2);
        scheduler.flush();          // 一个事务含两条 entry

        scheduler.undo();           // 整事务一起回退
        Assert.assertEquals("a", name.get());
        Assert.assertEquals(Integer.valueOf(1), age.get());
    }

    // ── 有界环形缓冲 ─────────────────────────────────────────────────────────────

    @Test
    public void ringBufferEvictsOldestBeyondCapacity() {
        log.setCapacity(3);
        Signal<Integer> s = Signal.create(0);
        for (int i = 1; i <= 5; i++) { s.set(i); scheduler.flush(); }

        Assert.assertEquals("容量 3，超出丢最老", 3, log.size());
        List<TransactionLog.Transaction> all = log.transactions();
        // 保留的是最后 3 个事务（序号 3、4、5）
        Assert.assertEquals(3L, all.get(0).sequence());
        Assert.assertEquals(5L, all.get(2).sequence());
    }

    @Test
    public void setCapacityShrinksImmediately() {
        Signal<Integer> s = Signal.create(0);
        for (int i = 1; i <= 5; i++) { s.set(i); scheduler.flush(); }
        Assert.assertEquals(5, log.size());

        log.setCapacity(2);
        Assert.assertEquals("缩容立即淘汰最老", 2, log.size());
        Assert.assertEquals(4L, log.transactions().get(0).sequence());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setCapacityRejectsZero() {
        log.setCapacity(0);
    }

    // ── 事务标签（审计路径） ─────────────────────────────────────────────────────

    @Test
    public void labelAttachedToNextTransaction() {
        Signal<String> q = Signal.create("");
        scheduler.labelNextTransaction("search.query");
        q.set("hello"); scheduler.flush();

        Assert.assertEquals("search.query", log.current().label());
    }

    @Test
    public void labelIsOneShot() {
        Signal<Integer> s = Signal.create(0);
        scheduler.labelNextTransaction("first");
        s.set(1); scheduler.flush();
        s.set(2); scheduler.flush();   // 第二帧无标签

        List<TransactionLog.Transaction> all = log.transactions();
        Assert.assertEquals("first", all.get(0).label());
        Assert.assertNull("标签一次性，不沾染下一事务", all.get(1).label());
    }

    @Test
    public void labelClearedEvenWhenNoNetChange() {
        Signal<Integer> s = Signal.create(5);
        scheduler.labelNextTransaction("noop");
        s.set(5); scheduler.flush();   // 无净变化，不建事务，但标签应被清掉
        s.set(9); scheduler.flush();

        Assert.assertEquals(1, log.size());
        Assert.assertNull("空事务也应清掉待用标签", log.current().label());
    }

    // ── 开关 ────────────────────────────────────────────────────────────────────

    @Test
    public void disabledLogRecordsNothing() {
        log.setEnabled(false);
        Signal<Integer> s = Signal.create(0);
        s.set(1); scheduler.flush();

        Assert.assertEquals("关闭后零记录", 0, log.size());
        Assert.assertEquals("但写入仍正常生效", Integer.valueOf(1), s.get());
    }

    @Test
    public void reenablingLogResumesRecording() {
        log.setEnabled(false);
        Signal<Integer> s = Signal.create(0);
        s.set(1); scheduler.flush();
        log.setEnabled(true);
        s.set(2); scheduler.flush();

        Assert.assertEquals(1, log.size());
        Assert.assertEquals(2, log.current().entries().get(0).after());
    }

    @Test
    public void clearEmptiesLogButKeepsBehaviorIntact() {
        Signal<Integer> s = Signal.create(0);
        s.set(1); scheduler.flush();
        log.clear();

        Assert.assertEquals(0, log.size());
        Assert.assertEquals(0, log.cursor());
        Assert.assertFalse(log.canUndo());
        s.set(2); scheduler.flush();
        Assert.assertEquals(1, log.size());
    }
}
