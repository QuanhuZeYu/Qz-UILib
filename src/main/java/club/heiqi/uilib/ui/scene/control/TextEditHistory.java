package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.List;

import com.github.bsideup.jabel.Desugar;

/**
 * 文本控件编辑历史（undo/redo 栈）。
 *
 * <p>scene 文本控件（TextInput/TextArea）的本地 UI 态：记录受控文本每次编辑的
 * before/after 快照与编辑前后 caret 码点索引，供 Ctrl+Z/Ctrl+Y（含 Ctrl+Shift+Z）
 * 回退/重做。撤销/重做同样经 onChange 上抛，遵守受控 value 契约。</p>
 *
 * <h3>受控协调（§E1 规则）</h3>
 * <p>外部 value 写入（非本历史产生的 after/before）使历史失效：record/undo/redo
 * 时惰性校验「当前值 == 栈顶 after」，不一致即清空历史——外部写入、撤销后新编辑
 * 等场景自然清空，无需观察 value 信号。</p>
 *
 * <h3>合并（§E1 增强）</h3>
 * <p>连续 mergeable 编辑（TEXT_INPUT）在 {@value #MERGE_WINDOW_NANOS} 纳秒时间窗内
 * 合并为一条：after/caretAfter/时间戳推进，before/caretBefore 保留首条，避免逐字符
 * 灌满栈。粘贴等整体编辑不参与合并。</p>
 *
 * <p>历史上限默认 {@value #DEFAULT_LIMIT} 条（构造可注入），超出丢最旧。</p>
 */
public final class TextEditHistory {

    /** 默认历史上限（条）。 */
    public static final int DEFAULT_LIMIT = 100;
    /** TEXT_INPUT 合并时间窗（纳秒）：连续输入间隔 ≤500ms 合并为一条。 */
    public static final long MERGE_WINDOW_NANOS = 500_000_000L;

    /**
     * 单条编辑快照（不可变）。
     *
     * @param before      编辑前文本
     * @param after       编辑后文本（已上抛/即将上抛的新值）
     * @param caretBefore 编辑前 caret 码点索引（编辑发生位置）
     * @param caretAfter  编辑后 caret 码点索引
     * @param mergeable   是否参与连续输入合并（TEXT_INPUT 为 true，粘贴/删除等为 false）
     * @param timeNanos   事件时间戳（纳秒，合并窗判定用）
     */
    @Desugar
    public record Entry(
            String before,
            String after,
            int caretBefore,
            int caretAfter,
            boolean mergeable,
            long timeNanos
    ) {
        public Entry {
            before = before == null ? "" : before;
            after = after == null ? "" : after;
        }
    }

    private final int limit;
    private final List<Entry> undoStack = new ArrayList<>();
    private final List<Entry> redoStack = new ArrayList<>();

    /**
     * 创建默认上限（{@value #DEFAULT_LIMIT}）的编辑历史。
     */
    public TextEditHistory() {
        this(DEFAULT_LIMIT);
    }

    /**
     * 创建指定上限的编辑历史。
     *
     * @param limit 历史上限（条），≤0 时按 1 处理
     */
    public TextEditHistory(int limit) {
        this.limit = Math.max(1, limit);
    }

    /**
     * 记录一次编辑。
     *
     * <p>惰性受控协调：当前值（before）与栈顶 after 不一致视为外部写入，先清空历史再记录。
     * 连续 mergeable 编辑在时间窗内合并为一条；任何记录都清空 redo 栈（新编辑使重做失效）。</p>
     *
     * @param before      编辑前文本（handler 读到的当前值）
     * @param after       编辑后文本
     * @param caretBefore 编辑前 caret 码点索引
     * @param caretAfter  编辑后 caret 码点索引
     * @param mergeable   是否参与连续输入合并
     * @param timeNanos   事件时间戳（纳秒）
     */
    public void record(String before, String after, int caretBefore, int caretAfter,
                       boolean mergeable, long timeNanos) {
        Entry safe = new Entry(before, after, caretBefore, caretAfter, mergeable, timeNanos);
        if (!undoStack.isEmpty()) {
            Entry top = undoStack.get(undoStack.size() - 1);
            if (!safe.before().equals(top.after())) {
                // 外部写入/状态漂移 → 历史失效
                clear();
            } else if (safe.mergeable() && top.mergeable()
                    && safe.timeNanos() >= top.timeNanos()
                    && safe.timeNanos() - top.timeNanos() <= MERGE_WINDOW_NANOS) {
                // 合并：after/caretAfter/时间戳推进，before/caretBefore 保留首条
                undoStack.set(undoStack.size() - 1, new Entry(top.before(), safe.after(),
                        top.caretBefore(), safe.caretAfter(), true, safe.timeNanos()));
                return;
            }
        }
        undoStack.add(safe);
        redoStack.clear();
        if (undoStack.size() > limit) {
            undoStack.remove(0);
        }
    }

    /**
     * 撤销栈顶编辑。
     *
     * <p>当前值与栈顶 after 不一致（外部写入）时清空历史并返回 null。</p>
     *
     * @param current 当前受控值
     * @return 被撤销的编辑快照（调用方上抛 before 并恢复 caretBefore）；无可撤销返回 null
     */
    public Entry undo(String current) {
        if (undoStack.isEmpty()) {
            return null;
        }
        Entry top = undoStack.get(undoStack.size() - 1);
        if (!current.equals(top.after())) {
            clear();
            return null;
        }
        undoStack.remove(undoStack.size() - 1);
        redoStack.add(top);
        return top;
    }

    /**
     * 重做栈顶编辑。
     *
     * <p>当前值与栈顶 before 不一致（外部写入）时清空历史并返回 null。</p>
     *
     * @param current 当前受控值
     * @return 被重做的编辑快照（调用方上抛 after 并恢复 caretAfter）；无可重做返回 null
     */
    public Entry redo(String current) {
        if (redoStack.isEmpty()) {
            return null;
        }
        Entry top = redoStack.get(redoStack.size() - 1);
        if (!current.equals(top.before())) {
            clear();
            return null;
        }
        redoStack.remove(redoStack.size() - 1);
        undoStack.add(top);
        return top;
    }

    /** 清空全部历史。 */
    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    /** @return 可撤销条数（测试/状态探针） */
    public int undoSize() {
        return undoStack.size();
    }

    /** @return 可重做条数（测试/状态探针） */
    public int redoSize() {
        return redoStack.size();
    }
}
