package club.heiqi.uilib.ui.scene.control;

import org.junit.Assert;
import org.junit.Test;

/**
 * TextEditHistory 单元测试：undo/redo 往返、caret 恢复、受控漂移清空、
 * 连续输入合并窗口与历史上限。
 */
public class TextEditHistoryTest {

    private static final long T = 1000L;

    @Test
    public void recordUndoRedoRoundTrip() {
        TextEditHistory h = new TextEditHistory();
        h.record("", "a", 0, 1, false, T);
        h.record("a", "ab", 1, 2, false, T + 1);

        Assert.assertEquals("两条记录", 2, h.undoSize());
        Assert.assertEquals("初始无 redo", 0, h.redoSize());

        TextEditHistory.Entry u1 = h.undo("ab");
        Assert.assertNotNull("undo 1", u1);
        Assert.assertEquals("undo 1 回 a", "a", u1.before());
        Assert.assertEquals("undo 1 caret 回 1", 1, u1.caretBefore());

        TextEditHistory.Entry u2 = h.undo("a");
        Assert.assertNotNull("undo 2", u2);
        Assert.assertEquals("undo 2 回空", "", u2.before());
        Assert.assertEquals("undo 2 caret 回 0", 0, u2.caretBefore());

        Assert.assertNull("栈空 undo 返回 null", h.undo(""));
        Assert.assertEquals("两条 redo", 2, h.redoSize());

        TextEditHistory.Entry r1 = h.redo("");
        Assert.assertNotNull("redo 1", r1);
        Assert.assertEquals("redo 1 到 a", "a", r1.after());
        Assert.assertEquals("redo 1 caret 到 1", 1, r1.caretAfter());

        TextEditHistory.Entry r2 = h.redo("a");
        Assert.assertNotNull("redo 2", r2);
        Assert.assertEquals("redo 2 到 ab", "ab", r2.after());
        Assert.assertEquals("redo 2 caret 到 2", 2, r2.caretAfter());
    }

    @Test
    public void newEditClearsRedoStack() {
        TextEditHistory h = new TextEditHistory();
        h.record("", "a", 0, 1, false, T);
        h.undo("a");
        Assert.assertEquals("undo 后 redo 1 条", 1, h.redoSize());

        h.record("", "x", 0, 1, false, T + 1);
        Assert.assertEquals("新编辑清空 redo", 0, h.redoSize());
        Assert.assertNull("redo 无可重做", h.redo("x"));
        Assert.assertEquals("新编辑入 undo 栈", 1, h.undoSize());
    }

    @Test
    public void mergeConsecutiveInputWithinWindow() {
        TextEditHistory h = new TextEditHistory();
        h.record("", "a", 0, 1, true, T);
        h.record("a", "ab", 1, 2, true, T + 499_000_000L); // 499ms ≤ 500ms 窗
        Assert.assertEquals("窗内连续输入合并为一条", 1, h.undoSize());

        TextEditHistory.Entry u = h.undo("ab");
        Assert.assertNotNull(u);
        Assert.assertEquals("合并条目 before 为首条", "", u.before());
        Assert.assertEquals("合并条目 caretBefore 为首条", 0, u.caretBefore());
        Assert.assertEquals("合并条目 after 为末条", "ab", u.after());
        Assert.assertEquals("合并条目 caretAfter 为末条", 2, u.caretAfter());
    }

    @Test
    public void noMergeOutsideWindowOrNonMergeable() {
        TextEditHistory h = new TextEditHistory();
        h.record("", "a", 0, 1, true, T);
        // 超窗：501ms > 500ms
        h.record("a", "ab", 1, 2, true, T + 501_000_000L);
        Assert.assertEquals("超窗不合并", 2, h.undoSize());
        // 非 mergeable（粘贴/删除）：即使窗内也不合并
        h.record("ab", "abc", 2, 3, false, T + 501_000_001L);
        Assert.assertEquals("非 mergeable 不合并", 3, h.undoSize());
    }

    @Test
    public void externalDriftClearsHistoryOnUndoAndRecord() {
        TextEditHistory h = new TextEditHistory();
        h.record("", "a", 0, 1, false, T);
        // 外部把 value 改成 z：undo 惰性校验失败 → 清空
        Assert.assertNull("外部写入后 undo 失效", h.undo("z"));
        Assert.assertEquals("历史已清空", 0, h.undoSize());

        h.record("z", "za", 1, 2, false, T + 1);
        // 外部再改：record 时 before != 栈顶 after → 清空重记
        h.record("q", "qa", 1, 2, false, T + 2);
        Assert.assertEquals("漂移后重新记录只保留新条目", 1, h.undoSize());
        TextEditHistory.Entry u = h.undo("qa");
        Assert.assertNotNull(u);
        Assert.assertEquals("新条目 before 为漂移后值", "q", u.before());
    }

    @Test
    public void limitDropsOldestEntry() {
        TextEditHistory h = new TextEditHistory(3);
        h.record("", "0", 0, 1, false, T);
        h.record("0", "01", 1, 2, false, T + 1);
        h.record("01", "012", 2, 3, false, T + 2);
        h.record("012", "0123", 3, 4, false, T + 3);
        Assert.assertEquals("超上限丢最旧", 3, h.undoSize());

        h.undo("0123");
        h.undo("012");
        h.undo("01");
        Assert.assertNull("最旧条目已被丢弃", h.undo("0"));
        Assert.assertEquals("最终停在 0（不可再撤）", 0, h.undoSize());
    }

    @Test
    public void redoDriftClearsHistory() {
        TextEditHistory h = new TextEditHistory();
        h.record("", "a", 0, 1, false, T);
        h.undo("a");
        // 外部写值：redo 校验 before != current → 清空
        Assert.assertNull("外部写入后 redo 失效", h.redo("zzz"));
        Assert.assertEquals(0, h.redoSize());
        Assert.assertEquals(0, h.undoSize());
    }
}
